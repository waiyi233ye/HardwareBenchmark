package com.hwbench.bukkit;

import com.hwbench.HardwareBenchmarkPlugin;
import com.hwbench.core.BenchmarkResult;
import com.hwbench.core.CPUBenchmark;
import com.hwbench.core.DiskBenchmark;
import com.hwbench.core.HardwareDetector;
import com.hwbench.core.LibraryManager;
import com.hwbench.core.MemoryBenchmark;
import com.hwbench.core.ResultReporter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 跑分命令处理器
 * 处理 /hwbench 命令的所有子命令
 * 使用Spigot通用API，兼容1.7.10~1.20.1
 */
public class BenchCommand implements CommandExecutor, TabCompleter {

    private final HardwareBenchmarkPlugin plugin;
    private final ServerController serverController;
    private final HardwareDetector hardwareDetector;
    private final LibraryManager libraryManager;
    private final ResultReporter resultReporter;
    private final FileConfiguration config;

    private static final List<String> SUB_COMMANDS = Arrays.asList(
            "detect", "cpu", "mem", "disk", "all", "libs", "lock", "unlock", "help"
    );

    public BenchCommand(HardwareBenchmarkPlugin plugin, ServerController serverController,
                        HardwareDetector hardwareDetector, LibraryManager libraryManager,
                        ResultReporter resultReporter, FileConfiguration config) {
        this.plugin = plugin;
        this.serverController = serverController;
        this.hardwareDetector = hardwareDetector;
        this.libraryManager = libraryManager;
        this.resultReporter = resultReporter;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "detect":
                handleDetect(sender);
                break;
            case "cpu":
                handleBenchmark(sender, "cpu");
                break;
            case "mem":
                handleBenchmark(sender, "mem");
                break;
            case "disk":
                handleBenchmark(sender, "disk");
                break;
            case "all":
                handleBenchmark(sender, "all");
                break;
            case "libs":
                handleLibs(sender);
                break;
            case "lock":
                handleLock(sender);
                break;
            case "unlock":
                handleUnlock(sender);
                break;
            case "help":
                sendHelp(sender);
                break;
            default:
                sender.sendMessage(ChatColor.RED + "未知子命令。使用 /hwbench help 查看帮助");
        }
        return true;
    }

    private void handleDetect(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "[HWBench] 正在检测硬件信息...");
        plugin.getLogger().info("[HWBench-Detect] 正在检测硬件信息...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (hardwareDetector == null) {
                sender.sendMessage(ChatColor.RED + "[HWBench] 硬件检测库未加载，无法检测硬件信息");
                plugin.getLogger().warning("[HWBench-Detect] 硬件检测库未加载，无法检测硬件信息");
                return;
            }
            BenchmarkResult result = new BenchmarkResult();
            hardwareDetector.detectAll(result);
            String report = hardwareDetector.generateReport(result);
            plugin.getLogger().info("[HWBench-Detect] 硬件检测完成，报告如下：");
            for (String line : report.split("\n")) {
                plugin.getLogger().info("[HWBench-Detect] " + line);
                sender.sendMessage(line);
            }
            sender.sendMessage(ChatColor.GREEN + "硬件检测完成！");
        });
    }

    private void handleBenchmark(CommandSender sender, String type) {
        if (serverController.isLocked()) {
            sender.sendMessage(ChatColor.RED + "[HWBench] 服务器已在跑分中，请等待完成");
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "[HWBench] 即将开始" + getBenchmarkName(type) + "...");
        sender.sendMessage(ChatColor.YELLOW + "[HWBench] 服务器将锁定，所有玩家将在" +
                config.getInt("server-control.kick-delay", 3) + "秒后被踢出");

        serverController.lock(() -> {
            sender.sendMessage(ChatColor.YELLOW + "[HWBench] 服务器已锁定，开始跑分...");
            sender.sendMessage(ChatColor.GRAY + "[HWBench] 跑分期间请勿关闭控制台窗口");
            runBenchmarkAsync(sender, type);
        });
    }

    private void runBenchmarkAsync(CommandSender sender, String type) {
        CompletableFuture.runAsync(() -> {
            try {
                BenchmarkResult result = new BenchmarkResult();
                String hardwareReport = "";

                // 硬件检测
                if (hardwareDetector != null) {
                    sender.sendMessage(ChatColor.GRAY + "[HWBench] 正在检测硬件信息...");
                    hardwareDetector.detectAll(result);
                    hardwareReport = hardwareDetector.generateReport(result);
                }

                // CPU跑分
                if (type.equals("cpu") || type.equals("all")) {
                    sender.sendMessage(ChatColor.GRAY + "[HWBench] 正在运行CPU跑分（甜甜圈渲染+计算）...");
                    plugin.getLogger().info("[HWBench-CPU] 正在运行CPU跑分...");
                    CPUBenchmark cpuBench = new CPUBenchmark(
                            config.getInt("benchmark.cpu.donut-frames", 300),
                            config.getInt("benchmark.cpu.compute-iterations", 5),
                            config.getInt("benchmark.cpu.matrix-size", 512),
                            config.getBoolean("benchmark.cpu.show-donut-animation", true)
                    );
                    BenchmarkResult.TestResult cpuResult = cpuBench.runAll();
                    plugin.getLogger().info(String.format(
                            "[HWBench-CPU] CPU跑分完成: %.2f分, 耗时 %dms",
                            cpuResult.getScore(), cpuResult.getDurationMs()));
                    result.addTestResult("cpu", cpuResult);
                }

                // 内存跑分
                if (type.equals("mem") || type.equals("all")) {
                    sender.sendMessage(ChatColor.GRAY + "[HWBench] 正在运行内存跑分...");
                    plugin.getLogger().info("[HWBench-Mem] 正在运行内存跑分...");
                    MemoryBenchmark memBench = new MemoryBenchmark(
                            config.getInt("benchmark.memory.array-size-mb", 64),
                            config.getInt("benchmark.memory.iterations", 3)
                    );
                    BenchmarkResult.TestResult memResult = memBench.runAll();
                    plugin.getLogger().info(String.format(
                            "[HWBench-Mem] 内存跑分完成: %.2f分, 耗时 %dms",
                            memResult.getScore(), memResult.getDurationMs()));
                    result.addTestResult("memory", memResult);
                }

                // 磁盘跑分
                if (type.equals("disk") || type.equals("all")) {
                    sender.sendMessage(ChatColor.GRAY + "[HWBench] 正在运行磁盘跑分...");
                    plugin.getLogger().info("[HWBench-Disk] 正在运行磁盘跑分...");
                    File testDir = new File(plugin.getDataFolder(), "bench-tmp");
                    DiskBenchmark diskBench = new DiskBenchmark(
                            config.getInt("benchmark.disk.file-size-mb", 512),
                            config.getInt("benchmark.disk.block-size-kb", 64),
                            config.getInt("benchmark.disk.random-io-count", 5000),
                            testDir
                    );
                    BenchmarkResult.TestResult diskResult = diskBench.runAll();
                    plugin.getLogger().info(String.format(
                            "[HWBench-Disk] 磁盘跑分完成: %.2f分, 耗时 %dms",
                            diskResult.getScore(), diskResult.getDurationMs()));
                    result.addTestResult("disk", diskResult);
                }

                // 生成报告
                String report = resultReporter.generateReport(result, hardwareReport);
                resultReporter.printToConsole(report);

                File savedFile = resultReporter.saveReport(result, report);
                if (savedFile != null) {
                    sender.sendMessage(ChatColor.GREEN + "[HWBench] 报告已保存到: " + savedFile.getAbsolutePath());
                }

                sender.sendMessage(ChatColor.GREEN + "[HWBench] 跑分完成！综合得分: " +
                        String.format("%.2f", result.getOverallScore()));

                for (BenchmarkResult.TestResult tr : result.getTestResults().values()) {
                    sender.sendMessage(String.format(ChatColor.AQUA + "  %s: %.2f分 (%dms)",
                            tr.getName(), tr.getScore(), tr.getDurationMs()));
                }

            } catch (Throwable e) {
                sender.sendMessage(ChatColor.RED + "[HWBench] 跑分失败: " + e.getMessage());
                plugin.getLogger().severe("[HWBench] 跑分失败: " + e);
                e.printStackTrace();
            } finally {
                if (serverController.isAutoUnlock()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        serverController.unlock();
                        sender.sendMessage(ChatColor.GREEN + "[HWBench] 服务器已解锁，玩家可以重新进入");
                    });
                } else {
                    sender.sendMessage(ChatColor.YELLOW + "[HWBench] 服务器仍处于锁定状态，使用 /hwbench unlock 解锁");
                }
            }
        });
    }

    private void handleLibs(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "[HWBench] 正在检查Linux运行库...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String report = libraryManager.checkAndInstall();
            for (String line : report.split("\n")) {
                sender.sendMessage(line);
            }
        });
    }

    private void handleLock(CommandSender sender) {
        if (serverController.isLocked()) {
            sender.sendMessage(ChatColor.RED + "[HWBench] 服务器已处于锁定状态");
            return;
        }
        serverController.lock(() -> {
            sender.sendMessage(ChatColor.GREEN + "[HWBench] 服务器已锁定，所有玩家已踢出");
        });
    }

    private void handleUnlock(CommandSender sender) {
        if (!serverController.isLocked()) {
            sender.sendMessage(ChatColor.RED + "[HWBench] 服务器未锁定");
            return;
        }
        serverController.unlock();
        sender.sendMessage(ChatColor.GREEN + "[HWBench] 服务器已解锁，玩家可以重新进入");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        sender.sendMessage(ChatColor.GOLD + "        HardwareBenchmark 硬件跑分插件");
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        sender.sendMessage(ChatColor.YELLOW + "/hwbench detect " + ChatColor.GRAY + "- 检测硬件信息");
        sender.sendMessage(ChatColor.YELLOW + "/hwbench cpu " + ChatColor.GRAY + "- CPU甜甜圈跑分（渲染+计算）");
        sender.sendMessage(ChatColor.YELLOW + "/hwbench mem " + ChatColor.GRAY + "- 内存读写跑分");
        sender.sendMessage(ChatColor.YELLOW + "/hwbench disk " + ChatColor.GRAY + "- 磁盘IO跑分");
        sender.sendMessage(ChatColor.YELLOW + "/hwbench all " + ChatColor.GRAY + "- 运行全部跑分");
        sender.sendMessage(ChatColor.YELLOW + "/hwbench libs " + ChatColor.GRAY + "- 检查并补全Linux运行库");
        sender.sendMessage(ChatColor.YELLOW + "/hwbench lock " + ChatColor.GRAY + "- 手动锁定服务器");
        sender.sendMessage(ChatColor.YELLOW + "/hwbench unlock " + ChatColor.GRAY + "- 手动解锁服务器");
        sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        sender.sendMessage(ChatColor.GRAY + "跑分时服务器将自动锁定并踢出所有玩家");
        sender.sendMessage(ChatColor.GRAY + "控制台窗口将保持运行不关闭");
    }

    private String getBenchmarkName(String type) {
        switch (type) {
            case "cpu": return "CPU跑分";
            case "mem": return "内存跑分";
            case "disk": return "磁盘跑分";
            case "all": return "全部跑分";
            default: return "跑分";
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String label, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            for (String sub : SUB_COMMANDS) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
            return completions;
        }
        return new ArrayList<>();
    }
}

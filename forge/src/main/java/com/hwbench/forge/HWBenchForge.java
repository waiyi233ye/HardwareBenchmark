package com.hwbench.forge;

import com.hwbench.core.BenchConfig;
import com.hwbench.core.BenchmarkResult;
import com.hwbench.core.CPUBenchmark;
import com.hwbench.core.DiskBenchmark;
import com.hwbench.core.HardwareDetector;
import com.hwbench.core.LibraryManager;
import com.hwbench.core.MemoryBenchmark;
import com.hwbench.core.ResultReporter;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * HardwareBenchmark Forge mod 入口（1.16.5+ 通用版）
 *
 * 跨版本兼容性通过反射处理 Component 创建与 sendSuccess/disconnect 调用：
 *  - 1.19+: Component.literal(String)
 *  - 1.18.x: new TextComponent(String)
 *  - 1.16.x: new StringTextComponent(String)
 *
 * 1.7.10/1.12.2 由于 Forge/MCP API 差异较大，单独使用 HWBenchForgeLegacy.java。
 */
@Mod(HWBenchForge.MOD_ID)
public class HWBenchForge {
    public static final String MOD_ID = "hwbench";
    private static final Logger LOGGER = LoggerFactory.getLogger("HWBench");

    private final AtomicBoolean serverLocked = new AtomicBoolean(false);
    private final String lockMessage = "§c服务器正在执行硬件跑分，暂时关闭，请稍后再来。";

    public HWBenchForge() {
        LOGGER.info("[HardwareBenchmark] Forge mod 加载，等待服务器启动。");
    }

    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[HardwareBenchmark] 服务器启动，注册 /hwbench 命令");
    }

    public void onRegisterCommands(RegisterCommandsEvent event) {
        try {
            CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
            dispatcher.register(Commands.literal("hwbench")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.literal("detect").executes(ctx -> runDetect(ctx.getSource())))
                    .then(Commands.literal("cpu").executes(ctx -> runCpu(ctx.getSource())))
                    .then(Commands.literal("mem").executes(ctx -> runMem(ctx.getSource())))
                    .then(Commands.literal("disk").executes(ctx -> runDisk(ctx.getSource())))
                    .then(Commands.literal("all").executes(ctx -> runAll(ctx.getSource())))
                    .then(Commands.literal("libs").executes(ctx -> runLibs(ctx.getSource())))
                    .then(Commands.literal("lock").executes(ctx -> lockServer(ctx.getSource())))
                    .then(Commands.literal("unlock").executes(ctx -> unlockServer(ctx.getSource())))
                    .then(Commands.literal("help").executes(ctx -> help(ctx.getSource())))
                    .executes(ctx -> help(ctx.getSource()))
            );
        } catch (Exception e) {
            LOGGER.error("注册 /hwbench 命令失败", e);
        }
    }

    /** 玩家登录时检查锁定状态 */
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (serverLocked.get() && event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            kickPlayer(player);
        }
    }

    private int help(CommandSourceStack src) {
        send(src, "§6=== HardwareBenchmark Forge ===");
        send(src, "/hwbench detect - 检测硬件信息");
        send(src, "/hwbench cpu - CPU甜甜圈跑分");
        send(src, "/hwbench mem - 内存读写跑分");
        send(src, "/hwbench disk - 磁盘IO跑分");
        send(src, "/hwbench all - 运行全部跑分");
        send(src, "/hwbench libs - 检查并补全Linux运行库");
        send(src, "/hwbench lock - 手动锁定服务器");
        send(src, "/hwbench unlock - 手动解锁服务器");
        return 1;
    }

    private int runDetect(CommandSourceStack src) {
        send(src, "§e正在检测硬件信息...");
        new Thread(() -> {
            try {
                String report = detectViaProc();
                for (String line : report.split("\n")) {
                    send(src, line);
                }
                send(src, "§a硬件检测完成");
            } catch (Throwable e) {
                send(src, "§c硬件检测失败: " + e.getMessage());
                e.printStackTrace(System.err);
            } finally {
                serverLocked.set(false);
            }
        }, "HWBench-Detect").start();
        return 1;
    }

    /**
     * 通过 /proc 和 /sys 文件系统检测硬件信息（不依赖 JNA/OSHI）。
     *
     * Forge 1.18+ 自带 OSHI 5.x/6.x 和 JNA 5.x，与 mod 所需的 OSHI 6.6.5/JNA 5.15.0
     * 存在 JPMS 模块冲突（split package）。因此 detect 命令直接解析 Linux /proc 文件系统。
     */
    private String detectViaProc() throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════\n");
        sb.append("              硬件信息检测报告\n");
        sb.append("═══════════════════════════════════════════════════\n\n");

        // === 操作系统 ===
        sb.append("【操作系统】\n");
        String osName = readFirstLine("/etc/os-release", "PRETTY_NAME");
        if (osName == null || osName.isEmpty()) osName = System.getProperty("os.name", "Linux");
        sb.append(String.format("  系统: %s (%s)\n", osName, System.getProperty("os.arch", "unknown")));
        String uptime = readUptime();
        sb.append(String.format("  运行时间: %s\n\n", uptime));

        // === CPU ===
        sb.append("【CPU 处理器】\n");
        String cpuName = readCpuInfoField("model name");
        if (cpuName == null) cpuName = readCpuInfoField("Hardware");
        if (cpuName == null) cpuName = "未知";
        sb.append(String.format("  型号: %s\n", cpuName));
        int physicalCores = readCpuInfoFieldInt("cpu cores");
        int logicalCores = Runtime.getRuntime().availableProcessors();
        if (physicalCores <= 0) physicalCores = logicalCores;
        sb.append(String.format("  物理核心: %d\n", physicalCores));
        sb.append(String.format("  逻辑线程: %d\n", logicalCores));
        String maxFreq = readCpuFreq("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq");
        sb.append(String.format("  最大频率: %s MHz\n", maxFreq != null ? maxFreq : "N/A"));
        String curFreq = readCpuFreq("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq");
        sb.append(String.format("  当前频率: %s MHz\n\n", curFreq != null ? curFreq : "N/A"));

        // === 内存 ===
        sb.append("【内存】\n");
        long[] mem = readMemInfo();
        long memTotal = mem[0], memAvail = mem[1];
        long memUsed = memTotal - memAvail;
        double memUsage = memTotal > 0 ? (double) memUsed / memTotal * 100 : 0;
        sb.append(String.format("  总内存: %s\n", formatBytes(memTotal)));
        sb.append(String.format("  可用内存: %s\n", formatBytes(memAvail)));
        sb.append(String.format("  已用内存: %s\n", formatBytes(memUsed)));
        sb.append(String.format("  使用率: %.1f%%\n\n", memUsage));

        // === 磁盘 ===
        sb.append("【磁盘存储】\n");
        File cwd = new File(".");
        long diskTotal = cwd.getTotalSpace();
        long diskFree = cwd.getFreeSpace();
        long diskUsed = diskTotal - diskFree;
        double diskUsage = diskTotal > 0 ? (double) diskUsed / diskTotal * 100 : 0;
        sb.append(String.format("  工作目录: %s\n", cwd.getCanonicalPath()));
        sb.append(String.format("  总容量: %s\n", formatBytes(diskTotal)));
        sb.append(String.format("  已用: %s (%.1f%%)\n", formatBytes(diskUsed), diskUsage));
        sb.append(String.format("  可用: %s\n\n", formatBytes(diskFree)));

        // === Java ===
        sb.append("【Java运行时】\n");
        Runtime rt = Runtime.getRuntime();
        sb.append(String.format("  Java版本: %s\n", System.getProperty("java.version")));
        sb.append(String.format("  JVM供应商: %s\n", System.getProperty("java.vendor")));
        sb.append(String.format("  JVM内存: %s / %s\n",
                formatBytes(rt.totalMemory()), formatBytes(rt.maxMemory())));

        return sb.toString();
    }

    private String readFirstLine(String path, String key) {
        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (key != null) {
                    if (line.startsWith(key + "=") || line.startsWith(key + "=\"")) {
                        String val = line.substring(line.indexOf('=') + 1);
                        if (val.startsWith("\"") && val.endsWith("\"")) {
                            val = val.substring(1, val.length() - 1);
                        }
                        return val;
                    }
                }
            }
        } catch (Exception e) { /* ignore */ }
        return null;
    }

    private String readCpuInfoField(String field) {
        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.FileReader("/proc/cpuinfo"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith(field + ":")) {
                    return line.substring(line.indexOf(':') + 1).trim();
                }
            }
        } catch (Exception e) { /* ignore */ }
        return null;
    }

    private int readCpuInfoFieldInt(String field) {
        String val = readCpuInfoField(field);
        if (val != null) {
            try { return Integer.parseInt(val.trim()); } catch (Exception e) { /* ignore */ }
        }
        return 0;
    }

    private String readCpuFreq(String path) {
        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.FileReader(path))) {
            String line = br.readLine();
            if (line != null) {
                long khz = Long.parseLong(line.trim());
                return String.valueOf(khz / 1000);
            }
        } catch (Exception e) { /* ignore */ }
        return null;
    }

    private String readUptime() {
        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.FileReader("/proc/uptime"))) {
            String line = br.readLine();
            if (line != null) {
                String[] parts = line.split("\\s+");
                long secs = (long) Double.parseDouble(parts[0]);
                long days = secs / 86400;
                long hours = (secs % 86400) / 3600;
                long mins = (secs % 3600) / 60;
                return String.format("%d天%d小时%d分钟", days, hours, mins);
            }
        } catch (Exception e) { /* ignore */ }
        return "N/A";
    }

    private long[] readMemInfo() {
        long total = 0, avail = 0;
        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.FileReader("/proc/meminfo"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("MemTotal:")) {
                    total = parseMemInfoLine(line);
                } else if (line.startsWith("MemAvailable:")) {
                    avail = parseMemInfoLine(line);
                }
            }
        } catch (Exception e) { /* ignore */ }
        if (avail == 0) avail = total / 2; // fallback
        return new long[]{total, avail};
    }

    private long parseMemInfoLine(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length >= 2) {
            try { return Long.parseLong(parts[1]) * 1024; } catch (Exception e) { /* ignore */ }
        }
        return 0;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private int runCpu(CommandSourceStack src) {
        send(src, "§e开始 CPU 跑分，服务器可能卡顿...");
        // 在跑分线程外部（主线程）加载配置，传入线程内部使用
        final BenchConfig config = BenchConfig.load(new File("."));
        new Thread(() -> {
            try {
                CPUBenchmark bench = new CPUBenchmark(
                        config.cpuDonutFrames, config.cpuComputeIterations,
                        config.cpuMatrixSize, config.cpuShowAnimation,
                        config.cpuPrimeRange, config.cpuFloatIterations,
                        config.cpuTimeoutSeconds);
                BenchmarkResult.TestResult r = bench.runAll();
                send(src, String.format("§aCPU跑分完成: 得分 %.2f, 耗时 %dms", r.getScore(), r.getDurationMs()));
                for (String line : r.getDetails().split("\n")) {
                    send(src, line);
                }
                // 写入服务端 logs/ 目录
                if (config.reportWriteToServerLogs) {
                    BenchmarkResult result = new BenchmarkResult();
                    result.addTestResult("CPU", r);
                    String hardwareInfo;
                    try {
                        hardwareInfo = detectViaProc();
                    } catch (Exception e) {
                        hardwareInfo = "";
                    }
                    ResultReporter reporter = new ResultReporter(
                            config.reportSaveToFile, config.reportOutputDir, config.reportVerboseConsole);
                    String report = reporter.generateReport(result, hardwareInfo);
                    File logsFile = reporter.saveReportToServerLogs(result, report, new File("logs"));
                    if (logsFile != null) {
                        send(src, "§a报告已写入服务端日志: " + logsFile.getPath());
                    }
                }
            } catch (Throwable e) {
                send(src, "§cCPU跑分失败: " + e.getMessage());
            } finally {
                serverLocked.set(false);
            }
        }, "HWBench-CPU").start();
        return 1;
    }

    private int runMem(CommandSourceStack src) {
        send(src, "§e开始内存跑分...");
        // 在跑分线程外部（主线程）加载配置，传入线程内部使用
        final BenchConfig config = BenchConfig.load(new File("."));
        new Thread(() -> {
            try {
                MemoryBenchmark bench = new MemoryBenchmark(
                        config.memArraySizeMB, config.memIterations, config.memRandomAccessCount,
                        config.memTimeoutSeconds);
                BenchmarkResult.TestResult r = bench.runAll();
                send(src, String.format("§a内存跑分完成: 得分 %.2f, 耗时 %dms", r.getScore(), r.getDurationMs()));
                // 写入服务端 logs/ 目录
                if (config.reportWriteToServerLogs) {
                    BenchmarkResult result = new BenchmarkResult();
                    result.addTestResult("Memory", r);
                    String hardwareInfo;
                    try {
                        hardwareInfo = detectViaProc();
                    } catch (Exception e) {
                        hardwareInfo = "";
                    }
                    ResultReporter reporter = new ResultReporter(
                            config.reportSaveToFile, config.reportOutputDir, config.reportVerboseConsole);
                    String report = reporter.generateReport(result, hardwareInfo);
                    File logsFile = reporter.saveReportToServerLogs(result, report, new File("logs"));
                    if (logsFile != null) {
                        send(src, "§a报告已写入服务端日志: " + logsFile.getPath());
                    }
                }
            } catch (Throwable e) {
                send(src, "§c内存跑分失败: " + e.getMessage());
            } finally {
                serverLocked.set(false);
            }
        }, "HWBench-Mem").start();
        return 1;
    }

    private int runDisk(CommandSourceStack src) {
        send(src, "§e开始磁盘跑分...");
        // 在跑分线程外部（主线程）加载配置，传入线程内部使用
        final BenchConfig config = BenchConfig.load(new File("."));
        new Thread(() -> {
            try {
                DiskBenchmark bench = new DiskBenchmark(
                        config.diskFileSizeMB, config.diskBlockSizeKB,
                        config.diskRandomIOCount, new File("."),
                        config.diskTimeoutSeconds);
                BenchmarkResult.TestResult r = bench.runAll();
                send(src, String.format("§a磁盘跑分完成: 得分 %.2f, 耗时 %dms", r.getScore(), r.getDurationMs()));
                // 写入服务端 logs/ 目录
                if (config.reportWriteToServerLogs) {
                    BenchmarkResult result = new BenchmarkResult();
                    result.addTestResult("Disk", r);
                    String hardwareInfo;
                    try {
                        hardwareInfo = detectViaProc();
                    } catch (Exception e) {
                        hardwareInfo = "";
                    }
                    ResultReporter reporter = new ResultReporter(
                            config.reportSaveToFile, config.reportOutputDir, config.reportVerboseConsole);
                    String report = reporter.generateReport(result, hardwareInfo);
                    File logsFile = reporter.saveReportToServerLogs(result, report, new File("logs"));
                    if (logsFile != null) {
                        send(src, "§a报告已写入服务端日志: " + logsFile.getPath());
                    }
                }
            } catch (Throwable e) {
                send(src, "§c磁盘跑分失败: " + e.getMessage());
            } finally {
                serverLocked.set(false);
            }
        }, "HWBench-Disk").start();
        return 1;
    }

    private int runAll(CommandSourceStack src) {
        send(src, "§e=== 运行全部跑分 ===");
        send(src, "§c注意：跑分期间服务器会卡顿，建议先 /hwbench lock");
        // 在跑分线程外部（主线程）加载配置，传入线程内部使用
        final BenchConfig config = BenchConfig.load(new File("."));
        new Thread(() -> {
            try {
                // 硬件检测（在单一线程内顺序执行，结果用于报告）
                String hardwareInfo;
                try {
                    hardwareInfo = detectViaProc();
                    for (String line : hardwareInfo.split("\n")) {
                        send(src, line);
                    }
                } catch (Exception e) {
                    hardwareInfo = "";
                    send(src, "§c硬件检测失败: " + e.getMessage());
                }
                // 在单一线程内顺序跑 CPU+Mem+Disk，结果汇入同一个 BenchmarkResult
                BenchmarkResult result = new BenchmarkResult();

                try {
                    CPUBenchmark cpuBench = new CPUBenchmark(
                            config.cpuDonutFrames, config.cpuComputeIterations,
                            config.cpuMatrixSize, config.cpuShowAnimation,
                            config.cpuPrimeRange, config.cpuFloatIterations,
                            config.cpuTimeoutSeconds);
                    BenchmarkResult.TestResult cpuR = cpuBench.runAll();
                    result.addTestResult("CPU", cpuR);
                    send(src, String.format("§aCPU跑分完成: 得分 %.2f, 耗时 %dms",
                            cpuR.getScore(), cpuR.getDurationMs()));
                } catch (Throwable e) {
                    send(src, "§cCPU跑分失败: " + e.getMessage());
                }

                try {
                    MemoryBenchmark memBench = new MemoryBenchmark(
                            config.memArraySizeMB, config.memIterations, config.memRandomAccessCount,
                            config.memTimeoutSeconds);
                    BenchmarkResult.TestResult memR = memBench.runAll();
                    result.addTestResult("Memory", memR);
                    send(src, String.format("§a内存跑分完成: 得分 %.2f, 耗时 %dms",
                            memR.getScore(), memR.getDurationMs()));
                } catch (Throwable e) {
                    send(src, "§c内存跑分失败: " + e.getMessage());
                }

                try {
                    DiskBenchmark diskBench = new DiskBenchmark(
                            config.diskFileSizeMB, config.diskBlockSizeKB,
                            config.diskRandomIOCount, new File("."),
                            config.diskTimeoutSeconds);
                    BenchmarkResult.TestResult diskR = diskBench.runAll();
                    result.addTestResult("Disk", diskR);
                    send(src, String.format("§a磁盘跑分完成: 得分 %.2f, 耗时 %dms",
                            diskR.getScore(), diskR.getDurationMs()));
                } catch (Throwable e) {
                    send(src, "§c磁盘跑分失败: " + e.getMessage());
                }

                // 写一份综合报告到 logs/
                if (config.reportWriteToServerLogs) {
                    ResultReporter reporter = new ResultReporter(
                            config.reportSaveToFile, config.reportOutputDir, config.reportVerboseConsole);
                    String report = reporter.generateReport(result, hardwareInfo);
                    File logsFile = reporter.saveReportToServerLogs(result, report, new File("logs"));
                    if (logsFile != null) {
                        send(src, "§a综合报告已写入服务端日志: " + logsFile.getPath());
                    }
                }
                send(src, "§a=== 全部跑分完成 ===");
            } catch (Throwable e) {
                send(src, "§c跑分失败: " + e.getMessage());
            } finally {
                serverLocked.set(false);
            }
        }, "HWBench-All").start();
        return 1;
    }

    private int runLibs(CommandSourceStack src) {
        send(src, "§e检查并补全Linux运行库...");
        new Thread(() -> {
            try {
                List<String> libs = Arrays.asList("lshw", "lm-sensors", "pciutils", "smartmontools");
                LibraryManager mgr = new LibraryManager(libs, false, "auto");
                String report = mgr.checkAndInstall();
                for (String line : report.split("\n")) {
                    send(src, line);
                }
            } catch (Throwable e) {
                send(src, "§c库检查失败: " + e.getMessage());
            } finally {
                serverLocked.set(false);
            }
        }, "HWBench-Libs").start();
        return 1;
    }

    private int lockServer(CommandSourceStack src) {
        if (serverLocked.compareAndSet(false, true)) {
            send(src, "§c服务器已锁定。新玩家将无法加入。");
            // 踢出现有玩家
            try {
                Object server = src.getServer();
                if (server != null) {
                    Method getPlayerList = findMethod(server.getClass(), "getPlayerList");
                    if (getPlayerList != null) {
                        Object playerList = getPlayerList.invoke(server);
                        if (playerList != null) {
                            Method getPlayers = findMethod(playerList.getClass(), "getPlayers");
                            if (getPlayers != null) {
                                @SuppressWarnings("unchecked")
                                Iterable<Object> players = (Iterable<Object>) getPlayers.invoke(playerList);
                                for (Object player : players) {
                                    kickPlayer(player);
                                }
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                LOGGER.warn("[HWBench] 踢出玩家失败: " + e.getMessage());
            }
        } else {
            send(src, "§e服务器已经处于锁定状态。");
        }
        return 1;
    }

    private int unlockServer(CommandSourceStack src) {
        if (serverLocked.compareAndSet(true, false)) {
            send(src, "§a服务器已解锁。玩家可以正常加入。");
        } else {
            send(src, "§e服务器当前未锁定。");
        }
        return 1;
    }

    /** 通过反射踢出玩家，兼容 1.16+ 各版本 */
    private void kickPlayer(Object player) {
        try {
            Field connField = findField(player.getClass(), "connection");
            if (connField == null) return;
            Object conn = connField.get(player);
            if (conn == null) return;
            Object comp = makeComponent(lockMessage);
            if (comp == null) return;
            Method disconnect = findMethod(conn.getClass(), "disconnect");
            if (disconnect != null && disconnect.getParameterCount() == 1) {
                disconnect.invoke(conn, comp);
            }
        } catch (Throwable e) {
            LOGGER.warn("[HWBench] kick 失败: " + e.getMessage());
        }
    }

    /** 跨版本创建文本组件：1.19+ Component.literal / 1.18 new TextComponent / 1.16 new StringTextComponent */
    private Object makeComponent(String text) {
        // 1.19+: Component.literal(String)
        try {
            Class<?> compClass = Class.forName("net.minecraft.network.chat.Component");
            try {
                Method m = compClass.getMethod("literal", String.class);
                return m.invoke(null, text);
            } catch (NoSuchMethodException e) { /* fall through */ }
        } catch (Throwable e) { /* fall through */ }
        // 1.18.x: new TextComponent(String)
        try {
            Class<?> cls = Class.forName("net.minecraft.network.chat.TextComponent");
            Constructor<?> ctor = cls.getConstructor(String.class);
            return ctor.newInstance(text);
        } catch (Throwable e) { /* fall through */ }
        // 1.16.x: new StringTextComponent(String)
        try {
            Class<?> cls = Class.forName("net.minecraft.network.chat.StringTextComponent");
            Constructor<?> ctor = cls.getConstructor(String.class);
            return ctor.newInstance(text);
        } catch (Throwable e) { /* fall through */ }
        return null;
    }

    /** 跨版本发送消息到 CommandSourceStack（同时记录到日志以便RCON异步结果可追踪） */
    private void send(CommandSourceStack src, String message) {
        LOGGER.info("[HWBench] " + message);
        try {
            Object comp = makeComponent(message);
            if (comp == null) {
                return;
            }
            // 1.20+: sendSuccess(Supplier<Component>, boolean)
            try {
                Method m = CommandSourceStack.class.getMethod("sendSuccess", Supplier.class, boolean.class);
                m.invoke(src, (Supplier<Object>) () -> comp, false);
                return;
            } catch (NoSuchMethodException e) { /* fall through */ }
            // 1.19-: sendSuccess(Component, boolean)
            try {
                Method m = findMethod(CommandSourceStack.class, "sendSuccess", comp.getClass());
                if (m != null) {
                    m.invoke(src, comp, false);
                    return;
                }
            } catch (Throwable e) { /* fall through */ }
        } catch (Throwable t) {
            /* 已记录到日志 */
        }
    }

    private static Method findMethod(Class<?> cls, String name, Class<?>... paramTypes) {
        Class<?> c = cls;
        while (c != null) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && (paramTypes.length == 0 || matches(m.getParameterTypes(), paramTypes))) {
                    m.setAccessible(true);
                    return m;
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static boolean matches(Class<?>[] declared, Class<?>[] given) {
        if (declared.length != given.length) return false;
        for (int i = 0; i < declared.length; i++) {
            if (!declared[i].isAssignableFrom(given[i])) return false;
        }
        return true;
    }

    private static Field findField(Class<?> cls, String name) {
        Class<?> c = cls;
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }
}

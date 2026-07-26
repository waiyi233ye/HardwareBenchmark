package com.hwbench.forge;

import com.hwbench.core.BenchmarkResult;
import com.hwbench.core.CPUBenchmark;
import com.hwbench.core.DiskBenchmark;
import com.hwbench.core.HardwareDetector;
import com.hwbench.core.LibraryManager;
import com.hwbench.core.MemoryBenchmark;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;

/**
 * HardwareBenchmark Forge mod 入口（1.12.2 专用）
 *
 * 1.12.2 Forge API（无 Brigadier，用 CommandBase）：
 *  - @Mod + @EventHandler + FMLServerStartingEvent
 *  - 命令继承 CommandBase，通过 server.registerServerCommand 注册
 *  - 文本: TextComponentString
 *  - 玩家: EntityPlayerMP
 */
@Mod(modid = HWBenchForge1122.MODID, name = "HardwareBenchmark", version = "2.0.0", acceptableRemoteVersions = "*")
public class HWBenchForge1122 {
    public static final String MODID = "hwbench";
    private static final Logger LOGGER = LogManager.getLogger("HWBench");

    private final AtomicBoolean serverLocked = new AtomicBoolean(false);

    @Mod.EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        LOGGER.info("[HardwareBenchmark] Forge mod (1.12.2) 服务器启动，注册命令");
        fixShadedLibraryClassloading();
        // 注意：不在此处预加载 JNA/OSHI 类。
        // Forge 1.12.2 自带 JNA 4.4.0（libraries/net/java/dev/jna/jna/4.4.0/），
        // 若通过 ModClassLoader（parent-first）加载 com.sun.jna.Native，会从 JNA 4.4.0 加载，
        // 触发其静态初始化器加载 native library (jnidispatch.so v5.1.0)。
        // 之后隔离 ClassLoader 的 JNA 5.15.0 无法重新加载 native library（dlopen 缓存），
        // 导致 "Expected: 7.0.2, Found: 5.1.0" 版本冲突。
        // detect 命令通过隔离 ClassLoader（parent-last）加载 JNA 5.15.0，避免此问题。
        MinecraftForge.EVENT_BUS.register(this);
        event.registerServerCommand(new HWBenchCommand(this));
    }

    /**
     * Forge 的 LaunchClassLoader 会对所有加载的类运行反混淆 Transformer。
     * shaded 进来的第三方库（oshi/jna/slf4j）不是 Minecraft 类，Transformer
     * 处理它们时会失败，导致类被加入 invalidClasses 缓存，之后永远无法加载。
     *
     * 修复：将这些包加入 transformerExceptions（跳过 Transformer），并清除
     * 可能已缓存的 invalidClasses。
     */
    private void fixShadedLibraryClassloading() {
        ClassLoader cl = getClass().getClassLoader();
        if (!(cl instanceof LaunchClassLoader)) {
            LOGGER.info("[HardwareBenchmark] ClassLoader 不是 LaunchClassLoader (" + cl + ")，跳过修复");
            return;
        }
        LaunchClassLoader lcl = (LaunchClassLoader) cl;
        lcl.addTransformerExclusion("org.slf4j.");
        lcl.addTransformerExclusion("oshi.");
        lcl.addTransformerExclusion("com.sun.jna.");
        // 排除 com.hwbench 包，防止 Forge 的反混淆 Transformer 破坏
        // benchmark 类中的 varargs/autoboxing 字节码（导致 "f != java.lang.Long" 验证错误）
        lcl.addTransformerExclusion("com.hwbench.");
        try {
            Field f = LaunchClassLoader.class.getDeclaredField("invalidClasses");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<String> invalid = (Set<String>) f.get(lcl);
            int count = invalid.size();
            invalid.clear();
            LOGGER.info("[HardwareBenchmark] 已清除 " + count + " 个 invalidClasses 缓存条目");
        } catch (Throwable t) {
            LOGGER.warn("[HardwareBenchmark] 清除 invalidClasses 失败: " + t.getMessage());
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (serverLocked.get() && event.player instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            player.connection.disconnect(new TextComponentString(
                    "§c服务器正在执行硬件跑分，暂时关闭，请稍后再来。"));
        }
    }

    public boolean isLocked() {
        return serverLocked.get();
    }

    public boolean lock() {
        return serverLocked.compareAndSet(false, true);
    }

    public boolean unlock() {
        return serverLocked.compareAndSet(true, false);
    }

    /** HWBench 命令处理器（1.12.2 CommandBase 风格） */
    public static class HWBenchCommand extends CommandBase {
        private final HWBenchForge1122 mod;

        public HWBenchCommand(HWBenchForge1122 mod) {
            this.mod = mod;
        }

        @Override
        public String getName() {
            return "hwbench";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/hwbench <detect|cpu|mem|disk|all|libs|lock|unlock>";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 2;
        }

        @Override
        public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
            if (args.length == 1) {
                return getListOfStringsMatchingLastWord(args, "detect", "cpu", "mem", "disk", "all", "libs", "lock", "unlock");
            }
            return Collections.emptyList();
        }

        @Override
        public List<String> getAliases() {
            return Collections.emptyList();
        }

        @Override
        public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
            return sender.canUseCommand(getRequiredPermissionLevel(), "hwbench");
        }

        @Override
        public boolean isUsernameIndex(String[] args, int index) {
            return false;
        }

        @Override
        public int compareTo(ICommand o) {
            return getName().compareTo(o.getName());
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            String sub = args.length == 0 ? "help" : args[0];
            switch (sub) {
                case "detect":
                    runDetect(sender);
                    break;
                case "cpu":
                    runCpu(sender);
                    break;
                case "mem":
                    runMem(sender);
                    break;
                case "disk":
                    runDisk(sender);
                    break;
                case "all":
                    runAll(sender);
                    break;
                case "libs":
                    runLibs(sender);
                    break;
                case "lock":
                    lockServer(server, sender);
                    break;
                case "unlock":
                    unlockServer(sender);
                    break;
                default:
                    help(sender);
            }
        }

        private void send(ICommandSender sender, String message) {
            try {
                sender.sendMessage(new TextComponentString(message));
            } catch (Throwable ignored) { /* not on main thread */ }
            // System.err.println 被 Forge 的 log4j2 捕获到 logs/latest.log（以 [STDERR] 标记）。
            String tagged = "[HWBench] " + message;
            System.out.println(tagged);
            System.err.println(tagged);
            try {
                LOGGER.info(tagged);
            } catch (Throwable ignored) { /* log4j may fail off-main-thread */ }
        }

        /** 启动跑分线程，带未捕获异常处理器确保错误一定写入日志 */
        private void startBenchThread(String name, Runnable task) {
            Thread t = new Thread(() -> {
                try {
                    task.run();
                } catch (Throwable e) {
                    System.err.println("[HWBench] " + name + " 线程未捕获异常: " + e);
                    e.printStackTrace(System.err);
                }
            }, name);
            t.setUncaughtExceptionHandler((thread, throwable) -> {
                System.err.println("[HWBench] " + thread.getName() + " 未捕获异常: " + throwable);
                throwable.printStackTrace(System.err);
            });
            t.start();
        }

        private void help(ICommandSender sender) {
            send(sender, "§6=== HardwareBenchmark Forge (1.12.2) ===");
            send(sender, "/hwbench detect - 检测硬件信息");
            send(sender, "/hwbench cpu - CPU甜甜圈跑分");
            send(sender, "/hwbench mem - 内存读写跑分");
            send(sender, "/hwbench disk - 磁盘IO跑分");
            send(sender, "/hwbench all - 运行全部跑分");
            send(sender, "/hwbench libs - 检查并补全Linux运行库");
            send(sender, "/hwbench lock - 手动锁定服务器");
            send(sender, "/hwbench unlock - 手动解锁服务器");
        }

        private void runDetect(ICommandSender sender) {
            send(sender, "§e正在检测硬件信息...");
            startBenchThread("HWBench-Detect", () -> {
                try {
                    String report = detectViaProc();
                    for (String line : report.split("\n")) {
                        send(sender, line);
                    }
                    send(sender, "§a硬件检测完成");
                } catch (Throwable e) {
                    send(sender, "§c硬件检测失败: " + e.getMessage());
                    e.printStackTrace(System.err);
                }
            });
        }

        /**
         * 通过 /proc 和 /sys 文件系统检测硬件信息（不依赖 JNA/OSHI）。
         *
         * Forge 1.12.2 自带 JNA 4.4.0，与 mod 所需的 JNA 5.15.0 存在 native library 冲突
         *（dlopen 按 soname 缓存，无法在同一 JVM 中加载两个版本）。
         * 因此 detect 命令直接解析 Linux /proc 文件系统，避免使用 JNA。
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
            sb.append(String.format("  当前频率: %s MHz\n", curFreq != null ? curFreq : "N/A"));
            String temp = readCpuTemp();
            sb.append(String.format("  温度: %s°C\n\n", temp != null ? temp : "N/A"));

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

            // === GPU ===
            sb.append("【显卡 GPU】\n");
            List<String> gpus = detectGpuViaLspci();
            if (gpus.isEmpty()) {
                sb.append("  未检测到独立显卡\n");
            } else {
                for (String g : gpus) sb.append("  ").append(g).append("\n");
            }
            sb.append("\n");

            // === 网络 ===
            sb.append("【网络接口】\n");
            List<String> nets = detectNetworkInterfaces();
            for (String n : nets) sb.append("  ").append(n).append("\n");
            sb.append("\n");

            // === Java ===
            sb.append("【Java运行时】\n");
            Runtime rt = Runtime.getRuntime();
            sb.append(String.format("  Java版本: %s\n", System.getProperty("java.version")));
            sb.append(String.format("  JVM供应商: %s\n", System.getProperty("java.vendor")));
            sb.append(String.format("  JVM内存: %s / %s\n\n",
                    formatBytes(rt.totalMemory()), formatBytes(rt.maxMemory())));

            return sb.toString();
        }

        private String readFirstLine(String path, String key) {
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream(path), "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (key != null) {
                        if (line.startsWith(key + "=") || line.startsWith(key + ":")) {
                            String val = line.substring(line.indexOf(line.contains("=") ? "=" : ":") + 1).trim();
                            if (val.startsWith("\"") && val.endsWith("\"")) val = val.substring(1, val.length() - 1);
                            return val;
                        }
                    }
                }
            } catch (Exception ignored) {}
            return null;
        }

        private String readCpuInfoField(String field) {
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream("/proc/cpuinfo"), "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith(field + ":")) {
                        return line.substring(line.indexOf(":") + 1).trim();
                    }
                }
            } catch (Exception ignored) {}
            return null;
        }

        private int readCpuInfoFieldInt(String field) {
            String val = readCpuInfoField(field);
            if (val == null) return 0;
            try { return Integer.parseInt(val.trim()); } catch (Exception e) { return 0; }
        }

        private String readCpuFreq(String path) {
            try {
                byte[] data = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path));
                long khz = Long.parseLong(new String(data).trim());
                return String.format("%.0f", khz / 1000.0);
            } catch (Exception ignored) {}
            return null;
        }

        private String readCpuTemp() {
            // 尝试 /sys/class/thermal/thermal_zone0/temp (单位: 毫摄氏度)
            for (int i = 0; i < 5; i++) {
                try {
                    byte[] data = java.nio.file.Files.readAllBytes(
                            java.nio.file.Paths.get("/sys/class/thermal/thermal_zone" + i + "/temp"));
                    long milli = Long.parseLong(new String(data).trim());
                    return String.format("%.1f", milli / 1000.0);
                } catch (Exception ignored) {}
            }
            return null;
        }

        private String readUptime() {
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream("/proc/uptime"), "UTF-8"))) {
                String line = br.readLine();
                if (line != null) {
                    String[] parts = line.trim().split("\\s+");
                    long sec = (long) Double.parseDouble(parts[0]);
                    long h = sec / 3600, m = (sec % 3600) / 60;
                    return String.format("%d小时%d分钟", h, m);
                }
            } catch (Exception ignored) {}
            return "未知";
        }

        private long[] readMemInfo() {
            long total = 0, avail = 0;
            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new java.io.FileInputStream("/proc/meminfo"), "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith("MemTotal:")) {
                        total = parseMemLine(line);
                    } else if (line.startsWith("MemAvailable:")) {
                        avail = parseMemLine(line);
                    }
                }
            } catch (Exception ignored) {}
            if (avail == 0 && total > 0) avail = total / 2;  // fallback
            return new long[]{total, avail};
        }

        private long parseMemLine(String line) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 2) {
                try { return Long.parseLong(parts[1]) * 1024; } catch (Exception e) { return 0; }
            }
            return 0;
        }

        private List<String> detectGpuViaLspci() {
            List<String> gpus = new ArrayList<>();
            try {
                Process p = new ProcessBuilder("lspci").redirectErrorStream(true).start();
                try (java.io.BufferedReader br = new java.io.BufferedReader(
                        new java.io.InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String low = line.toLowerCase();
                        if (low.contains("vga") || low.contains("3d") || low.contains("display")) {
                            gpus.add(line.trim());
                        }
                    }
                }
                p.waitFor();
            } catch (Exception ignored) {}
            return gpus;
        }

        private List<String> detectNetworkInterfaces() {
            List<String> nets = new ArrayList<>();
            try {
                java.util.Enumeration<java.net.NetworkInterface> ifaces =
                        java.net.NetworkInterface.getNetworkInterfaces();
                while (ifaces.hasMoreElements()) {
                    java.net.NetworkInterface ni = ifaces.nextElement();
                    if (ni.isLoopback() || ni.isVirtual() || !ni.isUp()) continue;
                    StringBuilder sb = new StringBuilder();
                    sb.append(ni.getDisplayName());
                    byte[] mac = ni.getHardwareAddress();
                    sb.append(" | MAC: ");
                    if (mac != null) {
                        for (int i = 0; i < mac.length; i++) {
                            sb.append(String.format("%02X%s", mac[i], i < mac.length - 1 ? ":" : ""));
                        }
                    } else { sb.append("无"); }
                    sb.append(" | MTU: ").append(ni.getMTU());
                    nets.add(sb.toString());
                }
            } catch (Exception ignored) {}
            if (nets.isEmpty()) nets.add("未检测到网络接口");
            return nets;
        }

        private String formatBytes(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }

        private void runCpu(ICommandSender sender) {
            send(sender, "§e开始 CPU 跑分，服务器可能卡顿...");
            startBenchThread("HWBench-CPU", () -> {
                try {
                    CPUBenchmark bench = new CPUBenchmark(100, 3, 512, false);
                    BenchmarkResult.TestResult r = bench.runAll();
                    send(sender, String.format("§aCPU跑分完成: 得分 %.2f, 耗时 %dms", r.getScore(), r.getDurationMs()));
                    for (String line : r.getDetails().split("\n")) {
                        send(sender, line);
                    }
                } catch (Throwable e) {
                    send(sender, "§cCPU跑分失败: " + e.getMessage());
                }
            });
        }

        private void runMem(ICommandSender sender) {
            send(sender, "§e开始内存跑分...");
            startBenchThread("HWBench-Mem", () -> {
                try {
                    // 降低数组大小避免 OOM（服务器堆仅 768m/1024m）
                    MemoryBenchmark bench = new MemoryBenchmark(64, 3);
                    BenchmarkResult.TestResult r = bench.runAll();
                    send(sender, String.format("§a内存跑分完成: 得分 %.2f, 耗时 %dms", r.getScore(), r.getDurationMs()));
                } catch (Throwable e) {
                    send(sender, "§c内存跑分失败: " + e.getMessage());
                }
            });
        }

        private void runDisk(ICommandSender sender) {
            send(sender, "§e开始磁盘跑分...");
            startBenchThread("HWBench-Disk", () -> {
                try {
                    DiskBenchmark bench = new DiskBenchmark(64, 4, 5, new File("."));
                    BenchmarkResult.TestResult r = bench.runAll();
                    send(sender, String.format("§a磁盘跑分完成: 得分 %.2f, 耗时 %dms", r.getScore(), r.getDurationMs()));
                } catch (Throwable e) {
                    send(sender, "§c磁盘跑分失败: " + e.getMessage());
                }
            });
        }

        private void runAll(ICommandSender sender) {
            send(sender, "§e=== 运行全部跑分 ===");
            send(sender, "§c注意：跑分期间服务器会卡顿，建议先 /hwbench lock");
            startBenchThread("HWBench-All", () -> {
                try {
                    runDetect(sender);
                    Thread.sleep(500);
                    runCpu(sender);
                    Thread.sleep(500);
                    runMem(sender);
                    Thread.sleep(500);
                    runDisk(sender);
                    send(sender, "§a=== 全部跑分完成 ===");
                } catch (Throwable e) {
                    send(sender, "§c跑分失败: " + e.getMessage());
                }
            });
        }

        private void runLibs(ICommandSender sender) {
            send(sender, "§e检查并补全Linux运行库...");
            startBenchThread("HWBench-Libs", () -> {
                try {
                    List<String> libs = Arrays.asList("lshw", "lm-sensors", "pciutils", "smartmontools");
                    LibraryManager mgr = new LibraryManager(libs, false, "auto");
                    String report = mgr.checkAndInstall();
                    for (String line : report.split("\n")) {
                        send(sender, line);
                    }
                    send(sender, "§a库检查完成");
                } catch (Throwable e) {
                    send(sender, "§c库检查失败: " + e.getMessage());
                }
            });
        }

        private void lockServer(MinecraftServer server, ICommandSender sender) {
            if (mod.lock()) {
                send(sender, "§c服务器已锁定。新玩家将无法加入。");
                // 踢出现有玩家
                for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
                    player.connection.disconnect(new TextComponentString(
                            "§c服务器正在执行硬件跑分，暂时关闭，请稍后再来。"));
                }
            } else {
                send(sender, "§e服务器已经处于锁定状态。");
            }
        }

        private void unlockServer(ICommandSender sender) {
            if (mod.unlock()) {
                send(sender, "§a服务器已解锁。玩家可以正常加入。");
            } else {
                send(sender, "§e服务器当前未锁定。");
            }
        }
    }
}

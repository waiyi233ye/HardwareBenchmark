package com.hwbench.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.net.NetworkInterface;

/**
 * /proc-based HardwareDetector stub for Forge 1.18+ (JPMS environments).
 *
 * Forge 1.18+ uses the Java Platform Module System (JPMS). Shading OSHI/JNA
 * into the mod JAR causes split-package conflicts with Forge's bundled
 * versions (ResolutionException). This stub provides the same API as the
 * OSHI-based HardwareDetector but reads /proc and /sys directly, avoiding
 * any native library dependencies.
 *
 * API compatibility: constructor, detectAll(BenchmarkResult), generateReport(BenchmarkResult)
 */
public class HardwareDetector {

    public HardwareDetector() {
        // no-op; no OSHI initialization
    }

    public void detectAll(BenchmarkResult result) {
        detectOS(result);
        detectCPU(result);
        detectMemory(result);
        detectDisk(result);
        detectGPU(result);
        detectNetwork(result);
        detectJavaRuntime(result);
    }

    private void detectOS(BenchmarkResult result) {
        String osName = readFirstLine("/etc/os-release", "PRETTY_NAME");
        if (osName == null || osName.isEmpty()) osName = System.getProperty("os.name", "Linux");
        result.addHardwareInfo("os.name", osName);
        result.addHardwareInfo("os.version", System.getProperty("os.version", "unknown"));
        result.addHardwareInfo("os.arch", System.getProperty("os.arch", "unknown"));
        result.addHardwareInfo("os.uptime", readUptime());
    }

    private void detectCPU(BenchmarkResult result) {
        String cpuName = readCpuInfoField("model name");
        if (cpuName == null) cpuName = readCpuInfoField("Hardware");
        if (cpuName == null) cpuName = "未知";
        result.addHardwareInfo("cpu.name", cpuName);

        int physicalCores = readCpuInfoFieldInt("cpu cores");
        int logicalCores = Runtime.getRuntime().availableProcessors();
        if (physicalCores <= 0) physicalCores = logicalCores;
        result.addHardwareInfo("cpu.physicalCores", String.valueOf(physicalCores));
        result.addHardwareInfo("cpu.logicalCores", String.valueOf(logicalCores));

        String maxFreq = readCpuFreq("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq");
        result.addHardwareInfo("cpu.maxFreq", maxFreq != null ? maxFreq : "N/A");
        String curFreq = readCpuFreq("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq");
        result.addHardwareInfo("cpu.currentFreq", curFreq != null ? curFreq : "N/A");
        result.addHardwareInfo("cpu.temperature", readCpuTemp());
    }

    private void detectMemory(BenchmarkResult result) {
        long[] mem = readMemInfo();
        long memTotal = mem[0], memAvail = mem[1];
        long memUsed = memTotal - memAvail;
        double memUsage = memTotal > 0 ? (double) memUsed / memTotal * 100 : 0;
        result.addHardwareInfo("mem.total", formatBytes(memTotal));
        result.addHardwareInfo("mem.available", formatBytes(memAvail));
        result.addHardwareInfo("mem.used", formatBytes(memUsed));
        result.addHardwareInfo("mem.usage", String.format("%.1f", memUsage));
    }

    private void detectDisk(BenchmarkResult result) {
        File cwd = new File(".");
        long diskTotal = cwd.getTotalSpace();
        long diskFree = cwd.getFreeSpace();
        long diskUsed = diskTotal - diskFree;
        double diskUsage = diskTotal > 0 ? (double) diskUsed / diskTotal * 100 : 0;
        List<String> disks = new ArrayList<>();
        try {
            disks.add(String.format("%s  总:%s 已用:%s(%.1f%%) 可用:%s",
                    cwd.getCanonicalPath(), formatBytes(diskTotal),
                    formatBytes(diskUsed), diskUsage, formatBytes(diskFree)));
        } catch (Exception e) {
            disks.add("工作目录: . 总:" + formatBytes(diskTotal));
        }
        result.addHardwareInfo("disk.list", disks);
    }

    private void detectGPU(BenchmarkResult result) {
        List<String> gpus = new ArrayList<>();
        try {
            Process p = new ProcessBuilder("lspci").redirectErrorStream(true).start();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
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
        if (gpus.isEmpty()) gpus.add("未检测到独立显卡");
        result.addHardwareInfo("gpu.list", gpus);
    }

    private void detectNetwork(BenchmarkResult result) {
        List<String> nets = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
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
        result.addHardwareInfo("net.list", nets);
    }

    private void detectJavaRuntime(BenchmarkResult result) {
        Runtime rt = Runtime.getRuntime();
        result.addHardwareInfo("java.version", System.getProperty("java.version", "unknown"));
        result.addHardwareInfo("java.vendor", System.getProperty("java.vendor", "unknown"));
        result.addHardwareInfo("java.heap.used", formatBytes(rt.totalMemory() - rt.freeMemory()));
        result.addHardwareInfo("java.heap.total", formatBytes(rt.totalMemory()));
        result.addHardwareInfo("java.heap.max", formatBytes(rt.maxMemory()));
    }

    public String generateReport(BenchmarkResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════\n");
        sb.append("              硬件信息检测报告\n");
        sb.append("═══════════════════════════════════════════════════\n\n");

        sb.append("【操作系统】\n");
        sb.append(String.format("  系统: %s %s (%s)\n",
                result.getHardwareInfo().get("os.name"),
                result.getHardwareInfo().get("os.version"),
                result.getHardwareInfo().get("os.arch")));
        sb.append(String.format("  运行时间: %s\n\n", result.getHardwareInfo().get("os.uptime")));

        sb.append("【CPU 处理器】\n");
        sb.append(String.format("  型号: %s\n", result.getHardwareInfo().get("cpu.name")));
        sb.append(String.format("  物理核心: %s\n", result.getHardwareInfo().get("cpu.physicalCores")));
        sb.append(String.format("  逻辑线程: %s\n", result.getHardwareInfo().get("cpu.logicalCores")));
        sb.append(String.format("  最大频率: %s MHz\n", result.getHardwareInfo().get("cpu.maxFreq")));
        sb.append(String.format("  当前频率: %s MHz\n", result.getHardwareInfo().get("cpu.currentFreq")));
        sb.append(String.format("  温度: %s°C\n\n", result.getHardwareInfo().get("cpu.temperature")));

        sb.append("【内存】\n");
        sb.append(String.format("  总内存: %s\n", result.getHardwareInfo().get("mem.total")));
        sb.append(String.format("  可用内存: %s\n", result.getHardwareInfo().get("mem.available")));
        sb.append(String.format("  已用内存: %s\n", result.getHardwareInfo().get("mem.used")));
        sb.append(String.format("  使用率: %s%%\n\n", result.getHardwareInfo().get("mem.usage")));

        sb.append("【磁盘存储】\n");
        Object disks = result.getHardwareInfo().get("disk.list");
        if (disks instanceof List) {
            for (Object d : (List<?>) disks) {
                sb.append("  ").append(d).append("\n");
            }
        }
        sb.append("\n");

        sb.append("【显卡 GPU】\n");
        Object gpus = result.getHardwareInfo().get("gpu.list");
        if (gpus instanceof List) {
            for (Object g : (List<?>) gpus) {
                sb.append("  ").append(g).append("\n");
            }
        }
        sb.append("\n");

        sb.append("【网络接口】\n");
        Object nets = result.getHardwareInfo().get("net.list");
        if (nets instanceof List) {
            for (Object n : (List<?>) nets) {
                sb.append("  ").append(n).append("\n");
            }
        }
        sb.append("\n");

        sb.append("【Java运行时】\n");
        sb.append(String.format("  Java版本: %s\n", result.getHardwareInfo().get("java.version")));
        sb.append(String.format("  JVM供应商: %s\n", result.getHardwareInfo().get("java.vendor")));
        sb.append(String.format("  JVM内存: %s / %s\n",
                result.getHardwareInfo().get("java.heap.total"),
                result.getHardwareInfo().get("java.heap.max")));

        return sb.toString();
    }

    // === /proc and /sys helpers ===

    private String readFirstLine(String path, String key) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(path), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (key != null) {
                    if (line.startsWith(key + "=") || line.startsWith(key + ":")) {
                        String sep = line.contains("=") ? "=" : ":";
                        String val = line.substring(line.indexOf(sep) + 1).trim();
                        if (val.startsWith("\"") && val.endsWith("\"")) {
                            val = val.substring(1, val.length() - 1);
                        }
                        return val;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String readCpuInfoField(String field) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream("/proc/cpuinfo"), "UTF-8"))) {
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
            byte[] data = Files.readAllBytes(Paths.get(path));
            long khz = Long.parseLong(new String(data).trim());
            return String.format("%.0f", khz / 1000.0);
        } catch (Exception ignored) {}
        return null;
    }

    private String readCpuTemp() {
        for (int i = 0; i < 5; i++) {
            try {
                byte[] data = Files.readAllBytes(
                        Paths.get("/sys/class/thermal/thermal_zone" + i + "/temp"));
                long milli = Long.parseLong(new String(data).trim());
                return String.format("%.1f", milli / 1000.0);
            } catch (Exception ignored) {}
        }
        return "N/A";
    }

    private String readUptime() {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream("/proc/uptime"), "UTF-8"))) {
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
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream("/proc/meminfo"), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("MemTotal:")) {
                    total = parseMemLine(line);
                } else if (line.startsWith("MemAvailable:")) {
                    avail = parseMemLine(line);
                }
            }
        } catch (Exception ignored) {}
        if (avail == 0 && total > 0) avail = total / 2;
        return new long[]{total, avail};
    }

    private long parseMemLine(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length >= 2) {
            try { return Long.parseLong(parts[1]) * 1024; } catch (Exception e) { return 0; }
        }
        return 0;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}

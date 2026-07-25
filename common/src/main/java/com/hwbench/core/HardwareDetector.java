package com.hwbench.core;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.GraphicsCard;
import oshi.hardware.HWDiskStore;
import oshi.hardware.NetworkIF;
import oshi.software.os.OperatingSystem;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 硬件检测模块
 * 使用OSHI库检测CPU、内存、磁盘、GPU、网络等硬件信息
 */
public class HardwareDetector {

    private final SystemInfo systemInfo;
    private final HardwareAbstractionLayer hal;

    public HardwareDetector() {
        this.systemInfo = new SystemInfo();
        this.hal = systemInfo.getHardware();
    }

    /**
     * 检测全部硬件信息，填充到BenchmarkResult
     */
    public void detectAll(BenchmarkResult result) {
        detectOS(result);
        detectCPU(result);
        detectMemory(result);
        detectDisk(result);
        detectGPU(result);
        detectNetwork(result);
        detectJavaRuntime(result);
    }

    /**
     * 生成硬件信息文本报告
     */
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
            List<?> diskList = (List<?>) disks;
            for (Object d : diskList) {
                if (d instanceof String) {
                    sb.append("  ").append(d).append("\n");
                }
            }
        }
        sb.append("\n");

        sb.append("【显卡 GPU】\n");
        Object gpus = result.getHardwareInfo().get("gpu.list");
        if (gpus instanceof List) {
            List<?> gpuList = (List<?>) gpus;
            if (gpuList.isEmpty()) {
                sb.append("  未检测到独立显卡\n");
            }
            for (Object g : gpuList) {
                if (g instanceof String) {
                    sb.append("  ").append(g).append("\n");
                }
            }
        }
        sb.append("\n");

        sb.append("【网络接口】\n");
        Object nets = result.getHardwareInfo().get("net.list");
        if (nets instanceof List) {
            List<?> netList = (List<?>) nets;
            for (Object n : netList) {
                if (n instanceof String) {
                    sb.append("  ").append(n).append("\n");
                }
            }
        }
        sb.append("\n");

        sb.append("【Java运行时】\n");
        sb.append(String.format("  Java版本: %s\n", result.getHardwareInfo().get("java.version")));
        sb.append(String.format("  JVM供应商: %s\n", result.getHardwareInfo().get("java.vendor")));
        sb.append(String.format("  JVM内存: %s\n\n", result.getHardwareInfo().get("java.memory")));

        return sb.toString();
    }

    private void detectOS(BenchmarkResult result) {
        OperatingSystem os = systemInfo.getOperatingSystem();
        result.addHardwareInfo("os.name", os.getFamily() + " " + os.getVersionInfo().getVersion());
        result.addHardwareInfo("os.version", os.getVersionInfo().getVersion());
        result.addHardwareInfo("os.arch", os.getBitness() + "-bit");

        long uptimeSec = os.getSystemUptime();
        long hours = uptimeSec / 3600;
        long mins = (uptimeSec % 3600) / 60;
        result.addHardwareInfo("os.uptime", String.format("%d小时%d分钟", hours, mins));
    }

    private void detectCPU(BenchmarkResult result) {
        CentralProcessor cpu = hal.getProcessor();
        result.addHardwareInfo("cpu.name", cpu.getProcessorIdentifier().getName());
        result.addHardwareInfo("cpu.physicalCores", cpu.getPhysicalProcessorCount());
        result.addHardwareInfo("cpu.logicalCores", cpu.getLogicalProcessorCount());

        // 最大频率（部分系统可能不支持）
        try {
            long maxFreq = cpu.getMaxFreq();
            result.addHardwareInfo("cpu.maxFreq", String.format("%.0f", maxFreq / 1_000_000.0));
        } catch (Exception e) {
            result.addHardwareInfo("cpu.maxFreq", "N/A");
        }

        // 当前频率（部分系统可能不支持）
        try {
            long[] currentFreqs = cpu.getCurrentFreq();
            double avgFreq = 0;
            for (long freq : currentFreqs) {
                avgFreq += freq;
            }
            avgFreq = currentFreqs.length > 0 ? (avgFreq / currentFreqs.length) / 1_000_000.0 : 0;
            result.addHardwareInfo("cpu.currentFreq", String.format("%.0f", avgFreq));
        } catch (Exception e) {
            result.addHardwareInfo("cpu.currentFreq", "N/A");
        }

        // 温度检测（可能不支持）
        try {
            double temp = hal.getSensors().getCpuTemperature();
            result.addHardwareInfo("cpu.temperature", String.format("%.1f", temp > 0 ? temp : 0));
        } catch (Exception e) {
            result.addHardwareInfo("cpu.temperature", "N/A");
        }
    }

    private void detectMemory(BenchmarkResult result) {
        GlobalMemory memory = hal.getMemory();
        long total = memory.getTotal();
        long available = memory.getAvailable();
        long used = total - available;
        double usage = (double) used / total * 100;

        result.addHardwareInfo("mem.total", formatBytes(total));
        result.addHardwareInfo("mem.available", formatBytes(available));
        result.addHardwareInfo("mem.used", formatBytes(used));
        result.addHardwareInfo("mem.usage", String.format("%.1f", usage));
    }

    private void detectDisk(BenchmarkResult result) {
        List<HWDiskStore> diskStores = hal.getDiskStores();
        List<String> diskList = new ArrayList<>();

        for (HWDiskStore disk : diskStores) {
            String info = String.format("%s | %s | 总容量: %s | 序列号: %s",
                    disk.getName(),
                    disk.getModel().isEmpty() ? "未知型号" : disk.getModel(),
                    formatBytes(disk.getSize()),
                    disk.getSerial().isEmpty() ? "未知" : disk.getSerial());
            diskList.add(info);
        }

        if (diskList.isEmpty()) {
            diskList.add("未检测到磁盘设备");
        }

        result.addHardwareInfo("disk.list", diskList);
    }

    private void detectGPU(BenchmarkResult result) {
        List<GraphicsCard> graphicsCards = hal.getGraphicsCards();
        List<String> gpuList = new ArrayList<>();

        for (GraphicsCard gpu : graphicsCards) {
            String info = String.format("%s | VRAM: %s | 驱动: %s",
                    gpu.getName(),
                    formatBytes(gpu.getVRam()),
                    gpu.getVersionInfo().isEmpty() ? "未知" : gpu.getVersionInfo());
            gpuList.add(info);
        }

        // 如果OSHI未检测到GPU，尝试使用lspci
        if (gpuList.isEmpty()) {
            List<String> lspciGPUs = detectGPUViaLspci();
            gpuList.addAll(lspciGPUs);
        }

        result.addHardwareInfo("gpu.list", gpuList);
    }

    /**
     * 通过lspci命令检测GPU（Linux备用方案）
     */
    private List<String> detectGPUViaLspci() {
        List<String> gpus = new ArrayList<>();
        try {
            Process process = new ProcessBuilder("lspci").redirectErrorStream(true).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.toLowerCase().contains("vga") || line.toLowerCase().contains("3d") ||
                        line.toLowerCase().contains("display")) {
                        gpus.add(line.trim());
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
            // lspci不可用
        }
        return gpus;
    }

    private void detectNetwork(BenchmarkResult result) {
        List<NetworkIF> networkIFs = hal.getNetworkIFs();
        List<String> netList = new ArrayList<>();

        for (NetworkIF net : networkIFs) {
            List<String> ipv4List = new ArrayList<>();
            for (String addr : net.getIPv4addr()) {
                ipv4List.add(addr);
            }
            String info = String.format("%s | MAC: %s | IP: %s | 速率: %s",
                    net.getName(),
                    net.getMacaddr(),
                    ipv4List.isEmpty() ? "无" : String.join(", ", ipv4List),
                    net.getSpeed() > 0 ? (net.getSpeed() / 1_000_000) + " Mbps" : "未知");
            netList.add(info);
        }

        if (netList.isEmpty()) {
            netList.add("未检测到网络接口");
        }

        result.addHardwareInfo("net.list", netList);
    }

    private void detectJavaRuntime(BenchmarkResult result) {
        Runtime runtime = Runtime.getRuntime();
        result.addHardwareInfo("java.version", System.getProperty("java.version"));
        result.addHardwareInfo("java.vendor", System.getProperty("java.vendor"));
        result.addHardwareInfo("java.memory",
                formatBytes(runtime.totalMemory()) + " / " + formatBytes(runtime.maxMemory()));
    }

    /**
     * 格式化字节大小
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}

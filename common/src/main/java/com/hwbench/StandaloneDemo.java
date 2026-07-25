package com.hwbench;

import com.hwbench.core.BenchmarkResult;
import com.hwbench.core.CPUBenchmark;
import com.hwbench.core.HardwareDetector;
import com.hwbench.core.LibraryManager;
import com.hwbench.util.DonutRenderer;

import java.util.Arrays;

/**
 * 独立运行演示 - 不需要Bukkit服务器
 * 验证甜甜圈渲染、硬件检测和跑分功能
 */
public class StandaloneDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("     HardwareBenchmark 独立运行演示");
        System.out.println("═══════════════════════════════════════════════════\n");

        // 1. 甜甜圈渲染演示
        System.out.println("【1. 甜甜圈渲染演示】\n");
        DonutRenderer renderer = new DonutRenderer();
        // 渲染3帧展示动画效果
        for (int i = 0; i < 3; i++) {
            System.out.print("\u001b[H\u001b[2J"); // ANSI清屏
            System.out.println(renderer.renderFrame());
            Thread.sleep(200);
        }
        System.out.println("渲染统计: " + renderer.getFrameCount() + " 帧, " +
                renderer.getOperationsCount() + " 次运算\n");

        // 2. 硬件检测
        System.out.println("【2. 硬件信息检测】\n");
        HardwareDetector detector = new HardwareDetector();
        BenchmarkResult hwResult = new BenchmarkResult();
        detector.detectAll(hwResult);
        String hwReport = detector.generateReport(hwResult);
        System.out.println(hwReport);

        // 3. CPU跑分（小规模）
        System.out.println("【3. CPU跑分（小规模演示）】\n");
        CPUBenchmark cpuBench = new CPUBenchmark(30, 2, 256, false);
        BenchmarkResult.TestResult cpuResult = cpuBench.runAll();
        System.out.println("CPU跑分得分: " + String.format("%.2f", cpuResult.getScore()));
        System.out.println("耗时: " + cpuResult.getDurationMs() + "ms");
        System.out.println(cpuResult.getDetails());

        // 4. Linux库检查
        System.out.println("\n【4. Linux运行库检查】\n");
        LibraryManager libManager = new LibraryManager(
                Arrays.asList("lshw", "lm-sensors", "pciutils", "smartmontools"),
                false, "auto"
        );
        String libReport = libManager.checkAndInstall();
        System.out.println(libReport);

        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("     演示完成！插件功能正常");
        System.out.println("═══════════════════════════════════════════════════");
    }
}

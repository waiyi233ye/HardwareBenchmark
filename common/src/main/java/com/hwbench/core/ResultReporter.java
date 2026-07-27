package com.hwbench.core;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 跑分结果报告生成器
 * 生成格式化文本报告并保存到文件
 */
public class ResultReporter {

    private final boolean saveToFile;
    private final File outputDir;
    private final boolean verboseConsole;

    public ResultReporter(boolean saveToFile, String outputDir, boolean verboseConsole) {
        this.saveToFile = saveToFile;
        this.outputDir = new File(outputDir);
        this.verboseConsole = verboseConsole;
    }

    /**
     * 生成完整跑分报告
     */
    public String generateReport(BenchmarkResult result, String hardwareReport) {
        StringBuilder sb = new StringBuilder();

        sb.append("═══════════════════════════════════════════════════\n");
        sb.append("        硬件跑分测试报告\n");
        sb.append("═══════════════════════════════════════════════════\n");
        sb.append(String.format("测试时间: %s\n", result.getFormattedTimestamp()));
        sb.append(String.format("综合得分: %.2f 分\n\n", result.getOverallScore()));

        // 硬件信息
        if (hardwareReport != null && !hardwareReport.isEmpty()) {
            sb.append(hardwareReport);
        }

        // 跑分结果
        sb.append("═══════════════════════════════════════════════════\n");
        sb.append("                 跑分测试结果\n");
        sb.append("═══════════════════════════════════════════════════\n\n");

        for (BenchmarkResult.TestResult testResult : result.getTestResults().values()) {
            sb.append(String.format("【%s】 得分: %.2f %s | 耗时: %dms\n",
                    testResult.getName(), testResult.getScore(),
                    testResult.getUnit(), testResult.getDurationMs()));
            if (testResult.getThroughput() > 0) {
                sb.append(String.format("  平均吞吐量: %.1f MB/s\n", testResult.getThroughput()));
            }
            if (testResult.getDetails() != null && !testResult.getDetails().isEmpty()) {
                sb.append(testResult.getDetails()).append("\n");
            }
            sb.append("\n");
        }

        // 综合评级
        sb.append("═══════════════════════════════════════════════════\n");
        sb.append("                 综合评级\n");
        sb.append("═══════════════════════════════════════════════════\n");
        sb.append(getRating(result.getOverallScore()));

        return sb.toString();
    }

    /**
     * 根据得分生成评级
     */
    private String getRating(double score) {
        String level;
        String description;

        if (score >= 100) {
            level = "S+ 旗舰级";
            description = "顶级硬件配置，适合大型服务器和高负载场景";
        } else if (score >= 60) {
            level = "S 高性能";
            description = "高性能硬件，适合中型服务器和大多数场景";
        } else if (score >= 30) {
            level = "A 优秀";
            description = "性能良好，适合中小型服务器";
        } else if (score >= 15) {
            level = "B 合格";
            description = "基本满足小型服务器需求";
        } else if (score >= 5) {
            level = "C 一般";
            description = "性能一般，建议优化或升级";
        } else {
            level = "D 较低";
            description = "性能较低，不建议作为生产服务器";
        }

        return String.format("  评级: %s\n  说明: %s\n", level, description);
    }

    /**
     * 保存报告到文件
     * @return 保存的文件路径，失败返回null
     */
    public File saveReport(BenchmarkResult result, String reportContent) {
        if (!saveToFile) return null;

        try {
            outputDir.mkdirs();
            String fileName = String.format("hwbench_%s.txt",
                    result.getTimestamp().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")));
            File reportFile = new File(outputDir, fileName);

            try (PrintWriter writer = new PrintWriter(new FileWriter(reportFile, false))) {
                writer.println(reportContent);
            }

            return reportFile;
        } catch (IOException e) {
            System.err.println("[HWBench] 保存报告失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 将报告写入服务端 logs/hwbench/ 目录下的独立文件。
     * 所有平台（Bukkit/Forge/Fabric）统一调用此方法。
     *
     * @param result 跑分结果（用于生成文件名时间戳）
     * @param reportContent 已格式化的报告文本（由 generateReport 生成）
     * @param serverLogsDir 服务端 logs/ 目录（如 new File("logs") 或 new File(serverRoot, "logs")）
     * @return 写入的文件对象，失败返回 null
     */
    public File saveReportToServerLogs(BenchmarkResult result, String reportContent, File serverLogsDir) {
        try {
            File hwbenchDir = new File(serverLogsDir, "hwbench");
            if (!hwbenchDir.exists() && !hwbenchDir.mkdirs()) {
                System.err.println("[HWBench] 无法创建目录: " + hwbenchDir.getAbsolutePath());
                return null;
            }
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String fileName = "hwbench_" + timestamp + ".txt";
            File reportFile = new File(hwbenchDir, fileName);
            try (PrintWriter writer = new PrintWriter(new FileWriter(reportFile, false))) {
                writer.println(reportContent);
            }
            return reportFile;
        } catch (Exception e) {
            System.err.println("[HWBench] 保存到 logs/hwbench/ 失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 输出报告到控制台
     */
    public void printToConsole(String reportContent) {
        if (verboseConsole) {
            System.out.println(reportContent);
        }
    }
}

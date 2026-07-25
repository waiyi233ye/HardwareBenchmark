package com.hwbench.core;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 跑分结果数据模型
 */
public class BenchmarkResult {

    private final LocalDateTime timestamp;
    private final Map<String, Object> hardwareInfo;
    private final Map<String, TestResult> testResults;

    public BenchmarkResult() {
        this.timestamp = LocalDateTime.now();
        this.hardwareInfo = new LinkedHashMap<>();
        this.testResults = new LinkedHashMap<>();
    }

    public void addHardwareInfo(String key, Object value) {
        hardwareInfo.put(key, value);
    }

    public void addTestResult(String testName, TestResult result) {
        testResults.put(testName, result);
    }

    public Map<String, TestResult> getTestResults() {
        return testResults;
    }

    public Map<String, Object> getHardwareInfo() {
        return hardwareInfo;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * 计算综合得分
     */
    public double getOverallScore() {
        double total = 0;
        int count = 0;
        for (TestResult result : testResults.values()) {
            if (result.getScore() > 0) {
                total += result.getScore();
                count++;
            }
        }
        return count > 0 ? total / count : 0;
    }

    public String getFormattedTimestamp() {
        return timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 单项测试结果
     */
    public static class TestResult {
        private final String name;
        private final double score;
        private final String unit;
        private final long durationMs;
        private final String details;
        private final double throughput; // MB/s or ops/s

        public TestResult(String name, double score, String unit, long durationMs,
                          String details, double throughput) {
            this.name = name;
            this.score = score;
            this.unit = unit;
            this.durationMs = durationMs;
            this.details = details;
            this.throughput = throughput;
        }

        public String getName() { return name; }
        public double getScore() { return score; }
        public String getUnit() { return unit; }
        public long getDurationMs() { return durationMs; }
        public String getDetails() { return details; }
        public double getThroughput() { return throughput; }
    }
}

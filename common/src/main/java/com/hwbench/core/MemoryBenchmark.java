package com.hwbench.core;

import java.util.Random;

/**
 * 内存读写跑分模块
 * 测试内存顺序读写、随机访问性能
 */
public class MemoryBenchmark {

    private final int arraySizeMB;
    private final int iterations;

    public MemoryBenchmark(int arraySizeMB, int iterations) {
        this.arraySizeMB = arraySizeMB;
        this.iterations = iterations;
    }

    /**
     * 运行完整内存跑分
     */
    public BenchmarkResult.TestResult runAll() {
        long totalStart = System.nanoTime();
        StringBuilder details = new StringBuilder();

        int elementCount = (arraySizeMB * 1024 * 1024) / 8; // long = 8 bytes
        long[] array = new long[elementCount];

        // 1. 顺序写入测试
        double writeScore = 0;
        double writeThroughput = 0;
        for (int iter = 0; iter < iterations; iter++) {
            long start = System.nanoTime();
            for (int i = 0; i < elementCount; i++) {
                array[i] = i * 7L;
            }
            long duration = System.nanoTime() - start;
            double ms = duration / 1_000_000.0;
            double throughput = (arraySizeMB / (duration / 1_000_000_000.0)); // MB/s
            writeThroughput += throughput;
            writeScore += (elementCount / (duration / 1_000_000_000.0)) / 1_000_000.0; // 百万次/秒
        }
        writeThroughput /= iterations;
        writeScore /= iterations;
        details.append(String.format("  顺序写入: %.1f MB/s, %.1f 百万次/秒\n", writeThroughput, writeScore));

        // 2. 顺序读取测试
        double readScore = 0;
        double readThroughput = 0;
        for (int iter = 0; iter < iterations; iter++) {
            long checksum = 0;
            long start = System.nanoTime();
            for (int i = 0; i < elementCount; i++) {
                checksum += array[i];
            }
            long duration = System.nanoTime() - start;
            double ms = duration / 1_000_000.0;
            double throughput = (arraySizeMB / (duration / 1_000_000_000.0));
            readThroughput += throughput;
            readScore += (elementCount / (duration / 1_000_000_000.0)) / 1_000_000.0;
            // 防止优化
            if (checksum == Long.MIN_VALUE) System.out.println(checksum);
        }
        readThroughput /= iterations;
        readScore /= iterations;
        details.append(String.format("  顺序读取: %.1f MB/s, %.1f 百万次/秒\n", readThroughput, readScore));

        // 3. 随机访问测试
        Random random = new Random(42);
        int[] indices = new int[1_000_000];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = random.nextInt(elementCount);
        }

        double randomScore = 0;
        double randomThroughput = 0;
        for (int iter = 0; iter < iterations; iter++) {
            long checksum = 0;
            long start = System.nanoTime();
            for (int idx : indices) {
                checksum += array[idx];
            }
            long duration = System.nanoTime() - start;
            double throughput = ((indices.length * 8.0) / (1024 * 1024)) / (duration / 1_000_000_000.0);
            randomThroughput += throughput;
            randomScore += (indices.length / (duration / 1_000_000_000.0)) / 1_000_000.0;
            if (checksum == Long.MIN_VALUE) System.out.println(checksum);
        }
        randomThroughput /= iterations;
        randomScore /= iterations;
        details.append(String.format("  随机访问: %.1f MB/s, %.1f 百万次/秒\n", randomThroughput, randomScore));

        // 4. 内存复制测试
        long[] copy = new long[elementCount];
        double copyThroughput = 0;
        for (int iter = 0; iter < iterations; iter++) {
            long start = System.nanoTime();
            System.arraycopy(array, 0, copy, 0, elementCount);
            long duration = System.nanoTime() - start;
            copyThroughput += (arraySizeMB / (duration / 1_000_000_000.0));
        }
        copyThroughput /= iterations;
        details.append(String.format("  内存复制: %.1f MB/s\n", copyThroughput));

        long totalDuration = System.nanoTime() - totalStart;
        double avgScore = (writeScore + readScore + randomScore) / 3.0;

        return new BenchmarkResult.TestResult(
                "内存跑分", avgScore, "分", totalDuration / 1_000_000,
                details.toString().trim(), (writeThroughput + readThroughput) / 2.0
        );
    }
}

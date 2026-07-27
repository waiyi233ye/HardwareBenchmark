package com.hwbench.core;

import java.util.Random;

/**
 * 内存读写跑分模块
 * 测试内存顺序读写、随机访问性能
 */
public class MemoryBenchmark {

    private final int arraySizeMB;
    private final int iterations;
    private final int randomAccessCount;
    private final int timeoutSeconds;

    /**
     * 完整构造：所有跑分参数从外部传入（通常由 BenchConfig 提供）。
     *
     * @param arraySizeMB       测试数组大小（MB）
     * @param iterations        每项测试的迭代次数
     * @param randomAccessCount 随机访问的索引数量
     * @param timeoutSeconds    整体跑分超时阈值（秒）
     */
    public MemoryBenchmark(int arraySizeMB, int iterations, int randomAccessCount, int timeoutSeconds) {
        this.arraySizeMB = arraySizeMB;
        this.iterations = iterations;
        this.randomAccessCount = randomAccessCount;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * 向后兼容构造：保留旧的 3 参数签名，超时使用默认值。
     */
    public MemoryBenchmark(int arraySizeMB, int iterations, int randomAccessCount) {
        this(arraySizeMB, iterations, randomAccessCount, 60);
    }

    /**
     * 向后兼容构造：保留旧的 2 参数签名，随机访问数量与超时使用默认值。
     */
    public MemoryBenchmark(int arraySizeMB, int iterations) {
        this(arraySizeMB, iterations, 1_000_000, 60);
    }

    /**
     * 运行完整内存跑分
     *
     * <p>超时机制：以 {@link #timeoutSeconds} 为整体预算，每个子阶段开始前检查已用时间，
     * 超时则跳过后续阶段并在结果中标注「超时」。avgScore 按实际完成的阶段数计算，
     * 避免未跑阶段把整体分数拉低到 0。</p>
     */
    public BenchmarkResult.TestResult runAll() {
        long totalStart = System.nanoTime();
        long timeoutNanos = (long) timeoutSeconds * 1_000_000_000L;

        StringBuilder details = new StringBuilder();
        boolean timedOut = false;

        int elementCount = (arraySizeMB * 1024 * 1024) / 8; // long = 8 bytes
        long[] array = new long[elementCount];

        double writeScore = 0;
        double writeThroughput = 0;
        double readScore = 0;
        double readThroughput = 0;
        double randomScore = 0;
        double randomThroughput = 0;
        double copyThroughput = 0;
        int completedStages = 0;

        // 1. 顺序写入测试
        if (timeoutNanos > 0 && System.nanoTime() - totalStart > timeoutNanos) {
            timedOut = true;
        }
        if (!timedOut) {
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
            completedStages++;
        }

        // 2. 顺序读取测试
        if (!timedOut && System.nanoTime() - totalStart > timeoutNanos) {
            timedOut = true;
        }
        if (!timedOut) {
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
            completedStages++;
        }

        // 3. 随机访问测试
        if (!timedOut && System.nanoTime() - totalStart > timeoutNanos) {
            timedOut = true;
        }
        if (!timedOut) {
            Random random = new Random(42);
            int[] indices = new int[this.randomAccessCount];
            for (int i = 0; i < indices.length; i++) {
                indices[i] = random.nextInt(elementCount);
            }

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
            completedStages++;
        }

        // 4. 内存复制测试
        if (!timedOut && System.nanoTime() - totalStart > timeoutNanos) {
            timedOut = true;
        }
        if (!timedOut) {
            long[] copy = new long[elementCount];
            for (int iter = 0; iter < iterations; iter++) {
                long start = System.nanoTime();
                System.arraycopy(array, 0, copy, 0, elementCount);
                long duration = System.nanoTime() - start;
                copyThroughput += (arraySizeMB / (duration / 1_000_000_000.0));
            }
            copyThroughput /= iterations;
            details.append(String.format("  内存复制: %.1f MB/s\n", copyThroughput));
            completedStages++;
        }

        long totalDuration = System.nanoTime() - totalStart;
        // 按实际完成阶段数计算均分，避免未跑阶段把分数拉到 0
        double totalScore = writeScore + readScore + randomScore;
        double avgScore = completedStages > 0 ? totalScore / Math.min(completedStages, 3) : 0;

        if (timedOut) {
            details.append("  ⚠ 已达超时阈值（")
                   .append(timeoutSeconds)
                   .append("秒），后续阶段被跳过\n");
        }

        String testName = timedOut ? "内存跑分（超时）" : "内存跑分";
        return new BenchmarkResult.TestResult(
                testName, avgScore, "分", totalDuration / 1_000_000,
                details.toString().trim(), (writeThroughput + readThroughput) / 2.0
        );
    }
}

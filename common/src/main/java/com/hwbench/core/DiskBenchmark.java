package com.hwbench.core;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Random;

/**
 * 磁盘IO跑分模块
 * 测试磁盘顺序读写、随机读写性能
 */
public class DiskBenchmark {

    private final int fileSizeMB;
    private final int blockSizeKB;
    private final int randomIOCount;
    private final File testFile;
    private final int timeoutSeconds;

    /**
     * 完整构造：所有跑分参数从外部传入（通常由 BenchConfig 提供）。
     *
     * @param fileSizeMB     测试文件大小（MB）
     * @param blockSizeKB    单块大小（KB）
     * @param randomIOCount  随机 IO 次数
     * @param testDir        测试目录
     * @param timeoutSeconds 整体跑分超时阈值（秒）
     */
    public DiskBenchmark(int fileSizeMB, int blockSizeKB, int randomIOCount, File testDir, int timeoutSeconds) {
        this.fileSizeMB = fileSizeMB;
        this.blockSizeKB = blockSizeKB;
        this.randomIOCount = randomIOCount;
        this.testFile = new File(testDir, "hwbench_testfile.tmp");
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * 向后兼容构造：保留旧的 4 参数签名，超时使用默认值。
     */
    public DiskBenchmark(int fileSizeMB, int blockSizeKB, int randomIOCount, File testDir) {
        this(fileSizeMB, blockSizeKB, randomIOCount, testDir, 120);
    }

    /**
     * 运行完整磁盘跑分
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

        int blockSize = blockSizeKB * 1024;
        long totalBytes = (long) fileSizeMB * 1024 * 1024;
        int blockCount = (int) (totalBytes / blockSize);

        try {
            // 确保测试目录存在
            testFile.getParentFile().mkdirs();

            double seqWriteThroughput = 0;
            double seqReadThroughput = 0;
            double[] randWriteResult = null;
            double[] randReadResult = null;
            int completedStages = 0;

            // 1. 顺序写入测试
            if (timeoutNanos > 0 && System.nanoTime() - totalStart > timeoutNanos) {
                timedOut = true;
            }
            if (!timedOut) {
                seqWriteThroughput = sequentialWrite(blockSize, blockCount);
                details.append(String.format("  顺序写入: %.1f MB/s\n", seqWriteThroughput));
                completedStages++;
            }

            // 2. 顺序读取测试
            if (!timedOut && System.nanoTime() - totalStart > timeoutNanos) {
                timedOut = true;
            }
            if (!timedOut) {
                seqReadThroughput = sequentialRead(blockSize, blockCount);
                details.append(String.format("  顺序读取: %.1f MB/s\n", seqReadThroughput));
                completedStages++;
            }

            // 3. 随机写入测试
            if (!timedOut && System.nanoTime() - totalStart > timeoutNanos) {
                timedOut = true;
            }
            if (!timedOut) {
                randWriteResult = randomWrite(blockSize, blockCount);
                details.append(String.format("  随机写入: %.1f MB/s, %.0f IOPS\n",
                        randWriteResult[0], randWriteResult[1]));
                completedStages++;
            }

            // 4. 随机读取测试
            if (!timedOut && System.nanoTime() - totalStart > timeoutNanos) {
                timedOut = true;
            }
            if (!timedOut) {
                randReadResult = randomRead(blockSize, blockCount);
                details.append(String.format("  随机读取: %.1f MB/s, %.0f IOPS\n",
                        randReadResult[0], randReadResult[1]));
                completedStages++;
            }

            // 清理测试文件
            testFile.delete();

            long totalDuration = System.nanoTime() - totalStart;
            // 按实际完成阶段数计算均分，避免未跑阶段把分数拉到 0
            double avgScore = 0;
            if (completedStages > 0) {
                double total = seqWriteThroughput + seqReadThroughput;
                avgScore = total / Math.min(completedStages, 2);
            }

            if (timedOut) {
                details.append("  ⚠ 已达超时阈值（")
                       .append(timeoutSeconds)
                       .append("秒），后续阶段被跳过\n");
            }

            String testName = timedOut ? "磁盘跑分（超时）" : "磁盘跑分";
            return new BenchmarkResult.TestResult(
                    testName, avgScore / 100.0, "分", totalDuration / 1_000_000,
                    details.toString().trim(), avgScore
            );

        } catch (IOException e) {
            return new BenchmarkResult.TestResult(
                    "磁盘跑分", 0, "分", 0,
                    "  磁盘跑分失败: " + e.getMessage(), 0
            );
        }
    }

    private double sequentialWrite(int blockSize, int blockCount) throws IOException {
        byte[] block = new byte[blockSize];
        for (int i = 0; i < blockSize; i++) {
            block[i] = (byte) (i & 0xFF);
        }

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "rw")) {
            raf.setLength((long) blockCount * blockSize);

            long start = System.nanoTime();
            for (int i = 0; i < blockCount; i++) {
                raf.write(block);
            }
            raf.getFD().sync();
            long duration = System.nanoTime() - start;

            return (fileSizeMB / (duration / 1_000_000_000.0));
        }
    }

    private double sequentialRead(int blockSize, int blockCount) throws IOException {
        byte[] block = new byte[blockSize];

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            long start = System.nanoTime();
            long checksum = 0;
            for (int i = 0; i < blockCount; i++) {
                raf.readFully(block);
                checksum += block[0];
            }
            long duration = System.nanoTime() - start;

            if (checksum == Long.MIN_VALUE) System.out.println(checksum);

            return (fileSizeMB / (duration / 1_000_000_000.0));
        }
    }

    private double[] randomWrite(int blockSize, int blockCount) throws IOException {
        byte[] block = new byte[blockSize];
        Random random = new Random(123);
        for (int i = 0; i < blockSize; i++) {
            block[i] = (byte) random.nextInt();
        }

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "rw")) {
            long start = System.nanoTime();
            for (int i = 0; i < randomIOCount; i++) {
                long pos = ((long) random.nextInt(blockCount)) * blockSize;
                raf.seek(pos);
                raf.write(block);
            }
            raf.getFD().sync();
            long duration = System.nanoTime() - start;

            double seconds = duration / 1_000_000_000.0;
            double throughput = ((long) randomIOCount * blockSize / (1024.0 * 1024.0)) / seconds;
            double iops = randomIOCount / seconds;

            return new double[]{throughput, iops};
        }
    }

    private double[] randomRead(int blockSize, int blockCount) throws IOException {
        byte[] block = new byte[blockSize];
        Random random = new Random(456);

        try (RandomAccessFile raf = new RandomAccessFile(testFile, "r")) {
            long start = System.nanoTime();
            long checksum = 0;
            for (int i = 0; i < randomIOCount; i++) {
                long pos = ((long) random.nextInt(blockCount)) * blockSize;
                raf.seek(pos);
                raf.readFully(block);
                checksum += block[0];
            }
            long duration = System.nanoTime() - start;

            if (checksum == Long.MIN_VALUE) System.out.println(checksum);

            double seconds = duration / 1_000_000_000.0;
            double throughput = ((long) randomIOCount * blockSize / (1024.0 * 1024.0)) / seconds;
            double iops = randomIOCount / seconds;

            return new double[]{throughput, iops};
        }
    }
}

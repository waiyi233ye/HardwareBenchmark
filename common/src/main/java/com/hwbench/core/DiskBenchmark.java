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

    public DiskBenchmark(int fileSizeMB, int blockSizeKB, int randomIOCount, File testDir) {
        this.fileSizeMB = fileSizeMB;
        this.blockSizeKB = blockSizeKB;
        this.randomIOCount = randomIOCount;
        this.testFile = new File(testDir, "hwbench_testfile.tmp");
    }

    /**
     * 运行完整磁盘跑分
     */
    public BenchmarkResult.TestResult runAll() {
        long totalStart = System.nanoTime();
        StringBuilder details = new StringBuilder();

        int blockSize = blockSizeKB * 1024;
        long totalBytes = (long) fileSizeMB * 1024 * 1024;
        int blockCount = (int) (totalBytes / blockSize);

        try {
            // 确保测试目录存在
            testFile.getParentFile().mkdirs();

            // 1. 顺序写入测试
            double seqWriteThroughput = sequentialWrite(blockSize, blockCount);
            details.append(String.format("  顺序写入: %.1f MB/s\n", seqWriteThroughput));

            // 2. 顺序读取测试
            double seqReadThroughput = sequentialRead(blockSize, blockCount);
            details.append(String.format("  顺序读取: %.1f MB/s\n", seqReadThroughput));

            // 3. 随机写入测试
            double[] randWriteResult = randomWrite(blockSize, blockCount);
            details.append(String.format("  随机写入: %.1f MB/s, %.0f IOPS\n",
                    randWriteResult[0], randWriteResult[1]));

            // 4. 随机读取测试
            double[] randReadResult = randomRead(blockSize, blockCount);
            details.append(String.format("  随机读取: %.1f MB/s, %.0f IOPS\n",
                    randReadResult[0], randReadResult[1]));

            // 清理测试文件
            testFile.delete();

            long totalDuration = System.nanoTime() - totalStart;
            double avgScore = (seqWriteThroughput + seqReadThroughput) / 2.0;

            return new BenchmarkResult.TestResult(
                    "磁盘跑分", avgScore / 100.0, "分", totalDuration / 1_000_000,
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

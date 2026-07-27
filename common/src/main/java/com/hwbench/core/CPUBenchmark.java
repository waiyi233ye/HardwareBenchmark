package com.hwbench.core;

import com.hwbench.util.DonutRenderer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CPU跑分模块
 * 包含甜甜圈渲染跑分、多线程矩阵乘法、整数运算、浮点运算
 */
public class CPUBenchmark {

    private final int donutFrames;
    private final int computeIterations;
    private final int matrixSize;
    private final boolean showAnimation;
    private final int primeRange;
    private final int floatIterations;
    private final int timeoutSeconds;

    /**
     * 完整构造：所有跑分参数从外部传入（通常由 BenchConfig 提供）。
     *
     * @param donutFrames      甜甜圈渲染帧数
     * @param computeIterations 矩阵乘法迭代次数
     * @param matrixSize       矩阵边长
     * @param showAnimation    是否显示渲染动画
     * @param primeRange       质数筛上界
     * @param floatIterations  浮点循环迭代次数
     * @param timeoutSeconds   整体跑分超时阈值（秒）
     */
    public CPUBenchmark(int donutFrames, int computeIterations, int matrixSize, boolean showAnimation,
                       int primeRange, int floatIterations, int timeoutSeconds) {
        this.donutFrames = donutFrames;
        this.computeIterations = computeIterations;
        this.matrixSize = matrixSize;
        this.showAnimation = showAnimation;
        this.primeRange = primeRange;
        this.floatIterations = floatIterations;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * 向后兼容构造：保留旧的 4 参数签名，质数筛/浮点迭代/超时使用默认值。
     */
    public CPUBenchmark(int donutFrames, int computeIterations, int matrixSize, boolean showAnimation) {
        this(donutFrames, computeIterations, matrixSize, showAnimation,
             10_000_000, 50_000_000, 60);
    }

    /**
     * 运行完整CPU跑分
     *
     * <p>超时机制：以 {@link #timeoutSeconds} 为整体预算，每个子阶段开始前检查已用时间，
     * 超时则跳过后续阶段并在结果中标注「超时」。avgScore 按实际完成的阶段数计算，
     * 避免未跑阶段把整体分数拉低到 0。</p>
     */
    public BenchmarkResult.TestResult runAll() {
        long totalStart = System.nanoTime();
        long timeoutNanos = (long) timeoutSeconds * 1_000_000_000L;

        StringBuilder details = new StringBuilder();
        double totalScore = 0;
        int completedStages = 0;
        boolean timedOut = false;

        // 1. 甜甜圈渲染跑分
        DonutResult donutResult = runDonutBenchmark();
        double donutScore = donutResult.score;
        totalScore += donutScore;
        completedStages++;
        details.append(String.format("  甜甜圈渲染: %d帧, %d次运算, 耗时%.1fms, 得分%.1f\n",
                donutResult.frames, donutResult.operations,
                donutResult.durationMs, donutScore));

        // 2. 多线程矩阵乘法跑分
        if (!timedOut && System.nanoTime() - totalStart > timeoutNanos) {
            timedOut = true;
        }
        if (!timedOut) {
            MatrixResult matrixResult = runMatrixBenchmark();
            double matrixScore = matrixResult.score;
            totalScore += matrixScore;
            completedStages++;
            details.append(String.format("  多线程矩阵乘法: %dx%d, %d线程, 耗时%.1fms, 得分%.1f\n",
                    matrixSize, matrixSize, matrixResult.threads,
                    matrixResult.durationMs, matrixScore));
        }

        // 3. 整数运算跑分（质数筛）
        if (!timedOut && System.nanoTime() - totalStart > timeoutNanos) {
            timedOut = true;
        }
        if (!timedOut) {
            PrimeResult primeResult = runPrimeBenchmark();
            double primeScore = primeResult.score;
            totalScore += primeScore;
            completedStages++;
            details.append(String.format("  整数运算(质数筛): 范围0-%d, 找到%d个质数, 耗时%.1fms, 得分%.1f\n",
                    primeResult.range, primeResult.count,
                    primeResult.durationMs, primeScore));
        }

        // 4. 浮点运算跑分
        if (!timedOut && System.nanoTime() - totalStart > timeoutNanos) {
            timedOut = true;
        }
        if (!timedOut) {
            FloatResult floatResult = runFloatBenchmark();
            double floatScore = floatResult.score;
            totalScore += floatScore;
            completedStages++;
            details.append(String.format("  浮点运算: %d次迭代, 耗时%.1fms, 得分%.1f\n",
                    floatResult.iterations, floatResult.durationMs, floatScore));
        }

        long totalDuration = System.nanoTime() - totalStart;
        // 按实际完成阶段数计算均分，避免未跑阶段把分数拉到 0
        double avgScore = completedStages > 0 ? totalScore / completedStages : 0;

        if (timedOut) {
            details.append("  ⚠ 已达超时阈值（")
                   .append(timeoutSeconds)
                   .append("秒），后续阶段被跳过\n");
        }

        String testName = timedOut ? "CPU跑分（超时）" : "CPU跑分";
        return new BenchmarkResult.TestResult(
                testName, avgScore, "分", totalDuration / 1_000_000,
                details.toString().trim(), 0
        );
    }

    /**
     * 甜甜圈渲染跑分 - 测试CPU单线程浮点和三角函数性能
     */
    private DonutResult runDonutBenchmark() {
        DonutRenderer renderer = new DonutRenderer();
        long duration = renderer.renderFrames(donutFrames, showAnimation);
        double durationMs = duration / 1_000_000.0;

        // 得分 = 运算次数 / 耗时(秒) / 1_000_000 (百万次运算/秒)
        double score = (renderer.getOperationsCount() / (duration / 1_000_000_000.0)) / 1_000_000.0;

        return new DonutResult(donutFrames, renderer.getOperationsCount(), durationMs, score);
    }

    /**
     * 多线程矩阵乘法跑分 - 测试CPU多核并行计算能力
     */
    private MatrixResult runMatrixBenchmark() {
        int threads = Runtime.getRuntime().availableProcessors();
        double[][] a = generateMatrix(matrixSize);
        double[][] b = generateMatrix(matrixSize);
        double[][] result = new double[matrixSize][matrixSize];

        long start = System.nanoTime();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        int rowsPerThread = matrixSize / threads;
        for (int t = 0; t < threads; t++) {
            final int startRow = t * rowsPerThread;
            final int endRow = (t == threads - 1) ? matrixSize : (t + 1) * rowsPerThread;
            pool.submit(() -> {
                for (int i = startRow; i < endRow; i++) {
                    for (int j = 0; j < matrixSize; j++) {
                        double sum = 0;
                        for (int k = 0; k < matrixSize; k++) {
                            sum += a[i][k] * b[k][j];
                        }
                        result[i][j] = sum;
                    }
                }
                latch.countDown();
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        pool.shutdown();

        long duration = System.nanoTime() - start;
        double durationMs = duration / 1_000_000.0;

        // 得分 = (2 * N^3) / 耗时(秒) / 1_000_000_000 (GFLOPS)
        double flops = 2.0 * Math.pow(matrixSize, 3) * computeIterations;
        double score = (flops / (duration / 1_000_000_000.0)) / 1_000_000_000.0;

        return new MatrixResult(threads, durationMs, score);
    }

    /**
     * 整数运算跑分 - 埃拉托斯特尼筛法
     */
    private PrimeResult runPrimeBenchmark() {
        int range = this.primeRange;
        long start = System.nanoTime();

        boolean[] isPrime = new boolean[range + 1];
        java.util.Arrays.fill(isPrime, true);
        isPrime[0] = isPrime[1] = false;

        for (int i = 2; i * i <= range; i++) {
            if (isPrime[i]) {
                for (int j = i * i; j <= range; j += i) {
                    isPrime[j] = false;
                }
            }
        }

        int count = 0;
        for (int i = 2; i <= range; i++) {
            if (isPrime[i]) count++;
        }

        long duration = System.nanoTime() - start;
        double durationMs = duration / 1_000_000.0;

        // 得分 = 范围 / 耗时(秒) / 1_000_000 (百万次操作/秒)
        double score = (range / (duration / 1_000_000_000.0)) / 1_000_000.0;

        return new PrimeResult(range, count, durationMs, score);
    }

    /**
     * 浮点运算跑分 - 大量浮点乘加运算
     */
    private FloatResult runFloatBenchmark() {
        int iterations = this.floatIterations;
        long start = System.nanoTime();

        double x = 1.0;
        double y = 0.1;
        double z = 0.0;
        for (int i = 0; i < iterations; i++) {
            z += x * y + Math.sqrt(x * x + y * y);
            x = Math.sin(i * 0.001) + 1.0;
            y = Math.cos(i * 0.001) + 0.1;
        }

        // 防止JVM优化掉循环
        if (z < 0) System.out.println(z);

        long duration = System.nanoTime() - start;
        double durationMs = duration / 1_000_000.0;

        // 得分 = 迭代次数 / 耗时(秒) / 1_000_000 (百万次/秒)
        double score = (iterations / (duration / 1_000_000_000.0)) / 1_000_000.0;

        return new FloatResult(iterations, durationMs, score);
    }

    private double[][] generateMatrix(int size) {
        double[][] m = new double[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                m[i][j] = Math.random() * 2 - 1;
            }
        }
        return m;
    }

    // 结果内部类（兼容Java 11，不使用record）
    private static class DonutResult {
        final int frames;
        final long operations;
        final double durationMs;
        final double score;
        DonutResult(int frames, long operations, double durationMs, double score) {
            this.frames = frames;
            this.operations = operations;
            this.durationMs = durationMs;
            this.score = score;
        }
    }

    private static class MatrixResult {
        final int threads;
        final double durationMs;
        final double score;
        MatrixResult(int threads, double durationMs, double score) {
            this.threads = threads;
            this.durationMs = durationMs;
            this.score = score;
        }
    }

    private static class PrimeResult {
        final int range;
        final int count;
        final double durationMs;
        final double score;
        PrimeResult(int range, int count, double durationMs, double score) {
            this.range = range;
            this.count = count;
            this.durationMs = durationMs;
            this.score = score;
        }
    }

    private static class FloatResult {
        final int iterations;
        final double durationMs;
        final double score;
        FloatResult(int iterations, double durationMs, double score) {
            this.iterations = iterations;
            this.durationMs = durationMs;
            this.score = score;
        }
    }
}

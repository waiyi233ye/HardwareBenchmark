package com.hwbench.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CPU跑分模块测试
 */
class CPUBenchmarkTest {

    @Test
    @DisplayName("CPU跑分应返回有效结果")
    void testRunAll() {
        // 使用较小参数加快测试速度
        CPUBenchmark benchmark = new CPUBenchmark(10, 1, 128, false);
        BenchmarkResult.TestResult result = benchmark.runAll();

        assertNotNull(result);
        assertEquals("CPU跑分", result.getName());
        assertTrue(result.getScore() > 0, "跑分得分应大于0");
        assertTrue(result.getDurationMs() > 0, "耗时应大于0");
        assertNotNull(result.getDetails());
        assertTrue(result.getDetails().contains("甜甜圈渲染"));
        assertTrue(result.getDetails().contains("多线程矩阵乘法"));
        assertTrue(result.getDetails().contains("整数运算"));
        assertTrue(result.getDetails().contains("浮点运算"));
    }

    @Test
    @DisplayName("甜甜圈跑分应在合理时间内完成")
    void testDonutBenchmarkTiming() {
        CPUBenchmark benchmark = new CPUBenchmark(5, 1, 64, false);
        BenchmarkResult.TestResult result = benchmark.runAll();

        // 5帧+小矩阵应该在30秒内完成
        assertTrue(result.getDurationMs() < 30000, "跑分应在合理时间内完成");
    }
}

package com.hwbench.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 内存跑分模块测试
 */
class MemoryBenchmarkTest {

    @Test
    @DisplayName("内存跑分应返回有效结果")
    void testRunAll() {
        // 使用较小参数加快测试
        MemoryBenchmark benchmark = new MemoryBenchmark(32, 2);
        BenchmarkResult.TestResult result = benchmark.runAll();

        assertNotNull(result);
        assertEquals("内存跑分", result.getName());
        assertTrue(result.getScore() > 0, "跑分得分应大于0");
        assertTrue(result.getDurationMs() > 0, "耗时应大于0");
        assertNotNull(result.getDetails());
        assertTrue(result.getDetails().contains("顺序写入"));
        assertTrue(result.getDetails().contains("顺序读取"));
        assertTrue(result.getDetails().contains("随机访问"));
        assertTrue(result.getDetails().contains("内存复制"));
    }

    @Test
    @DisplayName("内存跑分吞吐量应大于0")
    void testThroughput() {
        MemoryBenchmark benchmark = new MemoryBenchmark(16, 1);
        BenchmarkResult.TestResult result = benchmark.runAll();

        assertTrue(result.getThroughput() > 0, "吞吐量应大于0");
    }
}

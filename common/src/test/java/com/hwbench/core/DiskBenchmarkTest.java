package com.hwbench.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 磁盘跑分模块测试
 */
class DiskBenchmarkTest {

    @TempDir
    File tempDir;

    @Test
    @DisplayName("磁盘跑分应返回有效结果")
    void testRunAll() {
        // 使用较小参数加快测试
        DiskBenchmark benchmark = new DiskBenchmark(32, 64, 100, tempDir);
        BenchmarkResult.TestResult result = benchmark.runAll();

        assertNotNull(result);
        assertEquals("磁盘跑分", result.getName());
        assertTrue(result.getDurationMs() >= 0, "耗时应非负");
        assertNotNull(result.getDetails());
    }

    @Test
    @DisplayName("磁盘跑分后测试文件应被清理")
    void testFileCleanup() {
        DiskBenchmark benchmark = new DiskBenchmark(16, 64, 50, tempDir);
        benchmark.runAll();

        File testFile = new File(tempDir, "hwbench_testfile.tmp");
        assertFalse(testFile.exists(), "测试文件应被删除");
    }
}

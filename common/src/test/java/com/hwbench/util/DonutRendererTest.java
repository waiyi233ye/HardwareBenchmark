package com.hwbench.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 甜甜圈渲染器测试
 */
class DonutRendererTest {

    @Test
    @DisplayName("渲染单帧应产生非空输出")
    void testRenderFrameProducesOutput() {
        DonutRenderer renderer = new DonutRenderer();
        String frame = renderer.renderFrame();

        assertNotNull(frame);
        assertFalse(frame.isEmpty());
        assertTrue(frame.contains("\n"), "帧应包含多行");
    }

    @Test
    @DisplayName("渲染帧应包含ASCII字符（不仅仅是空格）")
    void testRenderFrameContainsAsciiArt() {
        DonutRenderer renderer = new DonutRenderer();
        // 渲染多帧确保有内容
        String frame = "";
        for (int i = 0; i < 5; i++) {
            frame = renderer.renderFrame();
        }

        boolean hasNonSpace = false;
        for (char c : frame.toCharArray()) {
            if (c != ' ' && c != '\n') {
                hasNonSpace = true;
                break;
            }
        }
        assertTrue(hasNonSpace, "渲染帧应包含非空格字符");
    }

    @Test
    @DisplayName("连续渲染应更新帧计数和运算计数")
    void testFrameAndOperationCount() {
        DonutRenderer renderer = new DonutRenderer();

        assertEquals(0, renderer.getFrameCount());
        assertEquals(0, renderer.getOperationsCount());

        renderer.renderFrame();
        assertEquals(1, renderer.getFrameCount());
        assertTrue(renderer.getOperationsCount() > 0, "运算计数应大于0");

        renderer.renderFrame();
        assertEquals(2, renderer.getFrameCount());
    }

    @Test
    @DisplayName("reset应清零所有计数器")
    void testReset() {
        DonutRenderer renderer = new DonutRenderer();
        renderer.renderFrame();
        renderer.renderFrame();

        renderer.reset();

        assertEquals(0, renderer.getFrameCount());
        assertEquals(0, renderer.getOperationsCount());
    }

    @Test
    @DisplayName("渲染多帧应测量耗时")
    void testRenderFramesWithTiming() {
        DonutRenderer renderer = new DonutRenderer();
        long duration = renderer.renderFrames(10, false);

        assertTrue(duration > 0, "耗时应大于0");
        assertEquals(10, renderer.getFrameCount());
    }
}

package com.hwbench.util;

/**
 * 甜甜圈渲染器 - 经典donut.c的Java移植版
 * 渲染旋转的3D圆环面(torus)到ASCII字符，同时作为CPU浮点性能压力测试
 */
public class DonutRenderer {

    private static final int WIDTH = 80;
    private static final int HEIGHT = 22;
    private static final String LUMINANCE = ".,-~:;=!*#$@";

    private double A = 0; // X轴旋转角度
    private double B = 0; // Z轴旋转角度
    private long frameCount = 0;
    private long operationsCount = 0;

    /**
     * 渲染一帧甜甜圈
     * @return ASCII字符串
     */
    public String renderFrame() {
        char[] buffer = new char[WIDTH * HEIGHT];
        double[] zBuffer = new double[WIDTH * HEIGHT];
        java.util.Arrays.fill(buffer, ' ');
        java.util.Arrays.fill(zBuffer, 0);

        long opsThisFrame = 0;

        for (double j = 0; j < 6.28; j += 0.07) {
            for (double i = 0; i < 6.28; i += 0.02) {
                double c = Math.sin(i);
                double d = Math.cos(j);
                double e = Math.sin(A);
                double f = Math.sin(j);
                double g = Math.cos(A);
                double h = d + 2;
                double D = 1.0 / (c * h * e + f * g + 5);
                double l = Math.cos(i);
                double m = Math.cos(B);
                double n = Math.sin(B);
                double t = c * h * g - f * e;

                int x = (int) (40 + 30 * D * (l * h * m - t * n));
                int y = (int) (12 + 15 * D * (l * h * n + t * m));

                int N = (int) (8 * ((f * e - c * d * g) * m - c * d * e - f * g - l * d * n));

                opsThisFrame += 16; // 每个点约16次三角函数和算术运算

                if (y >= 0 && y < HEIGHT && x >= 0 && x < WIDTH && D > zBuffer[x + WIDTH * y]) {
                    zBuffer[x + WIDTH * y] = D;
                    int idx = Math.max(0, Math.min(11, N));
                    buffer[x + WIDTH * y] = LUMINANCE.charAt(idx);
                }
            }
        }

        A += 0.04;
        B += 0.02;
        frameCount++;
        operationsCount += opsThisFrame;

        StringBuilder sb = new StringBuilder();
        for (int row = 0; row < HEIGHT; row++) {
            sb.append(new String(buffer, row * WIDTH, WIDTH));
            if (row < HEIGHT - 1) sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * 渲染多帧并返回总耗时
     * @param frames 帧数
     * @param showAnimation 是否在控制台显示动画
     * @return 总耗时(纳秒)
     */
    public long renderFrames(int frames, boolean showAnimation) {
        long startTime = System.nanoTime();
        for (int f = 0; f < frames; f++) {
            String frame = renderFrame();
            if (showAnimation) {
                // ANSI清屏并定位光标到左上角
                System.out.print("\u001b[H\u001b[2J");
                System.out.println(frame);
                System.out.flush();
                try {
                    Thread.sleep(16); // ~60fps
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return System.nanoTime() - startTime;
    }

    public long getFrameCount() {
        return frameCount;
    }

    public long getOperationsCount() {
        return operationsCount;
    }

    public void reset() {
        A = 0;
        B = 0;
        frameCount = 0;
        operationsCount = 0;
    }
}

package com.hwbench.bukkit;

/**
 * MRJAR 探针类：Java 17 覆盖版本（major version 61），位于 META-INF/versions/17/。
 *
 * 在 Java 17+ JVM 上加载，替代 JAR 根目录的基础版本。
 * 实现体使用 Java 10+ 的 {@code var} 关键字，确保字节码级别 &gt; 8。
 */
public final class MrjarProbe {

    private MrjarProbe() {}

    /**
     * 返回当前实际加载的字节码层级标识。
     * @return "java17-overlay"
     */
    public static String level() {
        var label = "java17-overlay";
        return label;
    }
}

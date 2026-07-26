package com.hwbench.bukkit;

/**
 * MRJAR 探针类：Java 21 覆盖版本（major version 65），位于 META-INF/versions/21/。
 *
 * 在 Java 21+ JVM 上加载，替代基础版本与 Java 17 覆盖版本。
 * 实现体使用 Java 16+ 的局部 {@code record}，确保字节码级别 &gt; 17。
 */
public final class MrjarProbe {

    private MrjarProbe() {}

    /**
     * 返回当前实际加载的字节码层级标识。
     * @return "java21-overlay"
     */
    public static String level() {
        record Probe(String value) {}
        var p = new Probe("java21-overlay");
        return p.value();
    }
}

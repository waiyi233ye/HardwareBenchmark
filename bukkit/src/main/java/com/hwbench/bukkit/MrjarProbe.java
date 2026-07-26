package com.hwbench.bukkit;

/**
 * MRJAR 探针类：用于验证 Multi-Release JAR 在不同 JVM 下的覆盖加载机制。
 *
 * 基础版本（Java 8 字节码，major version 52），位于 JAR 根目录，
 * 在 Java 8~16 JVM 上加载。Java 17+ JVM 会加载 META-INF/versions/17/ 下的覆盖版本，
 * Java 21+ JVM 会加载 META-INF/versions/21/ 下的覆盖版本。
 *
 * 三个版本保持相同的包名/类名/公共方法签名，仅实现体不同，符合 MRJAR 规范。
 */
public final class MrjarProbe {

    private MrjarProbe() {}

    /**
     * 返回当前实际加载的字节码层级标识。
     * @return "java8-base"
     */
    public static String level() {
        return "java8-base";
    }
}

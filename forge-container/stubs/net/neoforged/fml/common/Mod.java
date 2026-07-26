package net.neoforged.fml.common;

import java.lang.annotation.*;

/**
 * Stub for compilation only. At runtime, the real @net.neoforged.fml.common.Mod
 * from NeoForge 1.20.2+ is used (lazy annotation resolution).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Mod {
    String value() default "";
}

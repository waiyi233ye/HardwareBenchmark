package net.minecraftforge.fml.common;

import java.lang.annotation.*;

/**
 * Stub for compilation only. At runtime, the real @net.minecraftforge.fml.common.Mod
 * from Forge 1.12.2-1.20.1 is used (lazy annotation resolution).
 *
 * This stub has BOTH value() (used by 1.16.5+) AND modid()/name()/version()/
 * acceptableRemoteVersions() (used by 1.12.2). At runtime, the real annotation
 * type is used, and any fields not present in the real type are silently ignored.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Mod {
    String value() default "";
    String modid() default "";
    String name() default "";
    String version() default "";
    String dependencies() default "";
    boolean useMetadata() default false;
    String acceptableRemoteVersions() default "";
    String acceptableSaveVersions() default "";
    String modLanguage() default "java";
    String modLanguageAdapter() default "";
    boolean canBeDeactivated() default false;
    String guiFactory() default "";
    String certificateFingerprint() default "";
    boolean clientSideOnly() default false;

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface EventHandler {
    }
}

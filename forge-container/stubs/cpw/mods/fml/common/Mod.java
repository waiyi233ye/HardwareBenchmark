package cpw.mods.fml.common;

import java.lang.annotation.*;

/**
 * Stub for compilation only. At runtime, the real @cpw.mods.fml.common.Mod
 * from Forge 1.7.10 is used (lazy annotation resolution).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Mod {
    String modid();
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

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface EventHandler {
    }
}

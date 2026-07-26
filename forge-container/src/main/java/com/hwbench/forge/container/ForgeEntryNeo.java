package com.hwbench.forge.container;

/**
 * Forge Universal Container Entry for NeoForge 1.20.2+.
 *
 * <p>This class carries ONLY the {@code @net.neoforged.fml.common.Mod}
 * annotation. On NeoForge 1.20.2+, this annotation type exists on the
 * classpath, so Forge's ASM scanner recognises it and loads this class
 * as the mod entry point.
 *
 * <p>On Forge 1.7.10 / 1.12.2 - 1.20.1 (where {@code net.neoforged.fml}
 * doesn't exist), Forge's ASM scanner doesn't recognise the
 * {@code @net.neoforged.fml.common.Mod} annotation descriptor, so this
 * class is never loaded as a mod — {@link ForgeEntryLegacy} or
 * {@link ForgeEntryClassic} takes over instead.
 *
 * <p><b>No {@code @Mod.EventHandler} methods:</b> NeoForge 1.20.2+
 * removed the {@code @Mod.EventHandler} lifecycle in favour of
 * {@code FMLJavaModLoadingContext} / event-bus self-registration. The
 * sub-JAR's delegate ({@code HWBenchForge}) self-registers on
 * {@code MinecraftForge.EVENT_BUS} in its own constructor, so no event
 * forwarding is needed from the container. The constructor's
 * {@link #initContainer()} call detects the version, loads the sub-JAR,
 * and instantiates the delegate — that's sufficient for NeoForge.
 *
 * <p>Compiled with Java 8 (temurin-8) so the .class file loads on any
 * JVM. (NeoForge 1.20.2+ runs on Java 17+, which can load Java 8 bytecode.)
 */
@net.neoforged.fml.common.Mod("hwbench")
public class ForgeEntryNeo extends ForgeContainerBase {

    public ForgeEntryNeo() {
        initContainer();
    }
}

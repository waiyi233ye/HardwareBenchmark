package com.hwbench.forge.container;

/**
 * Forge Universal Container Entry for Forge 1.7.10 (cpw.mods.fml era).
 *
 * <p>This class carries ONLY the {@code @cpw.mods.fml.common.Mod} annotation
 * and an {@code @Mod.EventHandler} method whose parameter type is
 * {@code cpw.mods.fml.common.event.FMLServerStartingEvent}. On Forge 1.7.10,
 * both the annotation type and the event type exist on the classpath, so
 * {@link Class#getDeclaredMethods()} resolves cleanly.
 *
 * <p>On Forge 1.12.2+ (where {@code cpw.mods.fml} doesn't exist), Forge's
 * ASM scanner doesn't recognise the {@code @cpw.mods.fml.common.Mod}
 * annotation descriptor, so this class is never loaded as a mod — the
 * era-appropriate sibling ({@link ForgeEntryClassic} or {@link ForgeEntryNeo})
 * takes over instead.
 *
 * <p>Compiled with Java 8 (temurin-8) so the .class file loads on Java 8 JVMs.
 */
@cpw.mods.fml.common.Mod(
        modid = "hwbench",
        name = "HardwareBenchmark",
        version = "2.1.0",
        acceptableRemoteVersions = "*"
)
public class ForgeEntryLegacy extends ForgeContainerBase {

    public ForgeEntryLegacy() {
        initContainer();
    }

    /**
     * Forge 1.7.10 server-starting event handler.
     * Forwards {@code FMLServerStartingEvent} to the sub-JAR's
     * {@code HWBenchForge1710.onServerStarting}.
     */
    @cpw.mods.fml.common.Mod.EventHandler
    public void onServerStarting(cpw.mods.fml.common.event.FMLServerStartingEvent event) {
        log("1.7.10 FMLServerStartingEvent received, forwarding to delegate");
        forwardEvent(event);
    }
}

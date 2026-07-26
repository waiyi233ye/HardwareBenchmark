package com.hwbench.forge.container;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Shared base class for Forge universal container entry points.
 *
 * <p>This class contains ALL the version-detection, sub-JAR loading, and
 * event-forwarding logic. It has NO {@code @Mod} annotation and NO
 * {@code @Mod.EventHandler} methods, so Forge's mod scanner never loads
 * it directly. Each era-specific subclass ({@link ForgeEntryLegacy},
 * {@link ForgeEntryClassic}, {@link ForgeEntryNeo}) declares exactly ONE
 * {@code @Mod} annotation and (where needed) exactly ONE
 * {@code @Mod.EventHandler} method whose parameter type matches the
 * Forge era it targets.
 *
 * <p><b>Why split into multiple entry classes?</b>
 * Java 8's {@link Class#getDeclaredMethods()} eagerly resolves ALL
 * parameter types referenced by every declared method. If a single class
 * declares event-handler methods for multiple Forge eras (each with a
 * different {@code FMLServerStartingEvent} type), {@code getDeclaredMethods()}
 * throws {@code NoClassDefFoundError} on Forge versions where the other
 * eras' event types don't exist. By splitting into per-era subclasses,
 * each subclass only references its own era's event type, so
 * {@code getDeclaredMethods()} never sees a missing type.
 *
 * <p>Compiled with Java 8 (temurin-8) so the .class files load on Java 8
 * JVMs (required by Forge 1.7.10 / 1.12.2). The stub annotation types used
 * at compile time are NOT included in the final JAR; at runtime, the real
 * annotation types from Forge take precedence (lazy annotation resolution).
 */
public abstract class ForgeContainerBase {

    public static final String MODID = "hwbench";
    public static final String VERSION = "2.0.0";

    /** The sub-JAR's entry class instance (delegation target). */
    protected Object delegate;

    /** Detected MC version family, e.g. "1.7.10", "1.12.2", "1.16.5", "1.18plus", "neoforge". */
    protected String detectedVersion;

    /**
     * Classloader used to load the delegate class. May be the runtime
     * classloader (if addURL succeeded) or a dedicated URLClassLoader
     * wrapping the sub-JAR (Forge 1.16+ fallback).
     */
    protected ClassLoader delegateLoader;

    /**
     * Called by subclass constructors (which are in turn called by Forge's
     * mod loader after spotting the era-specific {@code @Mod} annotation).
     */
    protected void initContainer() {
        try {
            init();
        } catch (Throwable t) {
            log("Initialization failed: " + t);
            try {
                t.printStackTrace(System.err);
            } catch (Throwable ignored) { /* ignore */ }
        }
    }

    // ------------------------------------------------------------------
    // Initialization: detect version, load sub-JAR, instantiate delegate
    // ------------------------------------------------------------------

    private void init() throws Exception {
        detectedVersion = detectVersion();
        log("Detected MC/Forge version family: " + detectedVersion);

        String subJarName = selectSubJar(detectedVersion);
        String delegateClassName = selectDelegateClass(detectedVersion);

        if (subJarName == null || delegateClassName == null) {
            log("No sub-JAR available for version " + detectedVersion
                    + " — mod will be inactive on this server");
            return;
        }

        // Extract sub-JAR from this container JAR's META-INF/jars/ directory
        File subJarFile = extractSubJar(subJarName);
        if (subJarFile == null) {
            log("Sub-JAR " + subJarName + " not found inside container JAR");
            return;
        }
        log("Extracted sub-JAR to: " + subJarFile.getAbsolutePath());

        // Try multiple strategies to make the sub-JAR's classes available:
        //   Strategy A: addToClassloader (addURL) — works for Forge 1.7.10/1.12.2
        //                (LaunchClassLoader has addURL)
        //   Strategy B: injectClassesViaDefineClass — works for Forge 1.16+
        //                (TransformingClassLoader has no addURL, but defineClass
        //                 reflection injects classes directly into its class table,
        //                 making them visible to child classloaders like Forge's
        //                 ASMEventHandler$ASMClassLoader)
        //   Strategy C: URLClassLoader fallback — last resort; only works if the
        //                delegate doesn't use @SubscribeEvent (which requires the
        //                class to be visible from Forge's runtime classloader)
        ClassLoader cl = getClass().getClassLoader();
        log("Runtime classloader: " + cl.getClass().getName());
        boolean addedToRuntime = false;

        // ---- Strategy A: addURL ----
        try {
            addToClassloader(subJarFile);
            log("Added sub-JAR to runtime classloader via addURL: " + subJarFile.getName());
            delegateLoader = cl;
            addedToRuntime = true;
        } catch (Throwable t) {
            log("Cannot add sub-JAR via addURL (" + t
                    + ") — trying defineClass injection");
        }

        // ---- Strategy B: defineClass injection ----
        if (!addedToRuntime) {
            try {
                int count = injectClassesViaDefineClass(subJarFile, cl);
                if (count > 0) {
                    log("Injected " + count + " classes into runtime classloader via defineClass");
                    delegateLoader = cl;
                    addedToRuntime = true;
                }
            } catch (Throwable t) {
                log("defineClass injection failed (" + t
                        + ") — falling back to URLClassLoader");
            }
        }

        // ---- Strategy C: URLClassLoader fallback (last resort) ----
        if (!addedToRuntime) {
            URL subJarUrl = subJarFile.toURI().toURL();
            delegateLoader = new URLClassLoader(new URL[]{subJarUrl}, cl);
            log("Created URLClassLoader for sub-JAR (fallback): " + subJarUrl);
            log("WARNING: URLClassLoader fallback may fail for @SubscribeEvent listeners");
        }

        // Load and instantiate the delegate entry class using the chosen classloader.
        Class<?> delegateClass = Class.forName(delegateClassName, true, delegateLoader);
        delegate = delegateClass.getDeclaredConstructor().newInstance();
        log("Delegate instantiated: " + delegateClassName + " (instance: " + delegate + ")");
    }

    /**
     * Injects all .class entries from a sub-JAR directly into the runtime
     * classloader's class table using {@link ClassLoader#defineClass} via
     * reflection.
     *
     * <p>This is the Forge 1.16+ workaround for TransformingClassLoader which
     * has no public {@code addURL} method and doesn't extend URLClassLoader.
     * Forge's event bus (ASMEventHandler$ASMClassLoader) delegates class
     * loading to its parent (TransformingClassLoader), so classes defined
     * here become visible to event bus listeners and Forge's mod loading
     * infrastructure.
     *
     * <p><b>Multi-pass strategy</b>: A class's direct superclass must be
     * defined in the same classloader before the subclass can be defined
     * (JVM throws {@code NoClassDefFoundError} otherwise). We iterate over
     * all classes multiple times, defining whatever we can in each pass.
     * Failed classes are retried once their superclasses/interfaces are
     * available. We stop when an iteration makes no progress.
     *
     * @param subJarFile the sub-JAR file to read classes from
     * @param runtimeCL  the runtime classloader to inject classes into
     * @return number of classes successfully defined
     */
    private int injectClassesViaDefineClass(File subJarFile, ClassLoader runtimeCL) throws Exception {
        Method defineClass = ClassLoader.class.getDeclaredMethod(
                "defineClass", String.class, byte[].class, int.class, int.class);
        defineClass.setAccessible(true);

        // Load all class bytes upfront (avoids re-reading the JAR each pass)
        java.util.Map<String, byte[]> pending = new java.util.LinkedHashMap<>();
        ZipFile zf = null;
        try {
            zf = new ZipFile(subJarFile);
            java.util.Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.endsWith(".class")) {
                    continue;
                }
                // Skip module-info.class (JPMS metadata, not a regular class)
                if (name.endsWith("module-info.class")) {
                    continue;
                }
                String className = name.substring(0, name.length() - 6).replace('/', '.');

                // Skip if class is already loaded/defined in the runtime classloader
                try {
                    Class.forName(className, false, runtimeCL);
                    continue;
                } catch (ClassNotFoundException cnfe) {
                    // Not yet defined — proceed
                }

                // Read class bytes
                byte[] bytes;
                InputStream is = null;
                try {
                    is = zf.getInputStream(entry);
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = is.read(buf)) > 0) {
                        baos.write(buf, 0, n);
                    }
                    bytes = baos.toByteArray();
                } finally {
                    if (is != null) try { is.close(); } catch (Throwable ignored) { }
                }
                pending.put(className, bytes);
            }
        } finally {
            if (zf != null) try { zf.close(); } catch (Throwable ignored) { }
        }

        log("Classes pending injection: " + pending.size());

        // Multi-pass defineClass: define whatever we can, retry failures
        int count = 0;
        int passNum = 0;
        int firstFailureLogged = 0;
        while (!pending.isEmpty()) {
            passNum++;
            int passStart = count;
            java.util.Iterator<java.util.Map.Entry<String, byte[]>> it =
                    pending.entrySet().iterator();
            while (it.hasNext()) {
                java.util.Map.Entry<String, byte[]> e = it.next();
                String className = e.getKey();
                byte[] bytes = e.getValue();
                try {
                    defineClass.invoke(runtimeCL, className, bytes, 0, bytes.length);
                    count++;
                    it.remove();
                } catch (java.lang.reflect.InvocationTargetException ite) {
                    Throwable cause = ite.getCause();
                    // NoClassDefFoundError / VerifyError: superclass or interface
                    // not yet defined — leave in pending for next pass.
                    // LinkageError (already defined): can't redefine, remove.
                    if (cause instanceof LinkageError
                            && !(cause instanceof NoClassDefFoundError)
                            && !(cause instanceof VerifyError)) {
                        // Already defined in this classloader — remove from pending
                        it.remove();
                        if (firstFailureLogged < 3) {
                            log("Class already defined in runtimeCL (skipping): "
                                    + className + " — " + cause.getMessage());
                            firstFailureLogged++;
                        }
                    } else {
                        // NoClassDefFoundError or VerifyError — keep for next pass
                        if (passNum == 1 && firstFailureLogged < 5) {
                            log("Pass 1 failed for " + className + ": "
                                    + cause.getClass().getSimpleName() + ": "
                                    + cause.getMessage());
                            firstFailureLogged++;
                        }
                    }
                } catch (Throwable t) {
                    // Unexpected error — remove to avoid infinite loop
                    it.remove();
                    log("defineClass threw " + t.getClass().getName()
                            + " for " + className + ": " + t.getMessage());
                }
            }
            if (count == passStart) {
                // No progress in this pass — remaining classes have unresolved
                // dependencies that aren't in the JAR. Log and exit.
                log("Pass " + passNum + ": no progress. " + pending.size()
                        + " classes could not be defined (missing superclass/interface).");
                if (!pending.isEmpty()) {
                    // Log first 5 unresolved classes for diagnosis
                    int shown = 0;
                    for (String cn : pending.keySet()) {
                        if (shown++ >= 5) break;
                        log("  Unresolved: " + cn);
                    }
                }
                break;
            }
            log("Pass " + passNum + ": defined " + (count - passStart)
                    + " classes (total: " + count + "/" + (count + pending.size()) + ")");
        }
        return count;
    }

    /**
     * Detects the MC/Forge version family by probing for version-specific classes.
     *
     * Detection order (most specific first):
     *   1. cpw.mods.fml.common.Loader          -> Forge 1.7.10
     *   2. net.minecraftforge.fml.common.Loader -> Forge 1.12.2
     *   3. net.neoforged.fml.ModList            -> NeoForge 1.20.2+
     *   4. net.minecraftforge.fml.ModList       -> Forge 1.16.5 or 1.18+
     *      - net.minecraft.command.CommandSource     -> 1.16.5 (MCP mappings)
     *      - net.minecraft.commands.CommandSourceStack -> 1.18+ (Mojang mappings)
     */
    private String detectVersion() {
        // 1.7.10 — cpw.mods.fml package
        if (classExists("cpw.mods.fml.common.Loader")) {
            return "1.7.10";
        }
        // 1.12.2 — net.minecraftforge.fml.common.Loader (no ModList yet)
        if (classExists("net.minecraftforge.fml.common.Loader")) {
            return "1.12.2";
        }
        // NeoForge 1.20.2+ — net.neoforged.fml.ModList
        if (classExists("net.neoforged.fml.ModList")) {
            return "neoforge";
        }
        // Forge 1.16.5 / 1.18+ — net.minecraftforge.fml.ModList
        if (classExists("net.minecraftforge.fml.ModList")) {
            // Distinguish 1.16.5 (MCP mappings) from 1.18+ (Mojang mappings)
            if (classExists("net.minecraft.command.CommandSource")) {
                return "1.16.5";
            }
            if (classExists("net.minecraft.commands.CommandSourceStack")) {
                return "1.18plus";
            }
            // Unknown Forge 1.13+ — assume 1.18+ (broader compatibility via reflection)
            return "1.18plus";
        }
        return "unknown";
    }

    /** Maps a detected version family to the sub-JAR file name inside META-INF/jars/. */
    private String selectSubJar(String version) {
        if (version == null) return null;
        switch (version) {
            case "1.7.10":   return "forge-1.7.10.jar";
            case "1.12.2":   return "forge-1.12.2.jar";
            case "1.16.5":   return "forge-1.16.5.jar";
            case "1.18plus": return "forge-1.18plus.jar";
            case "neoforge": return "forge-1.18plus.jar"; // best-effort (may fail)
            default:         return null;
        }
    }

    /** Maps a detected version family to the sub-JAR's entry class FQN. */
    private String selectDelegateClass(String version) {
        if (version == null) return null;
        switch (version) {
            case "1.7.10":   return "com.hwbench.forge.HWBenchForge1710";
            case "1.12.2":   return "com.hwbench.forge.HWBenchForge1122";
            case "1.16.5":   return "com.hwbench.forge.HWBenchForgeLegacy";
            case "1.18plus": return "com.hwbench.forge.HWBenchForge";
            case "neoforge": return "com.hwbench.forge.HWBenchForge"; // best-effort
            default:         return null;
        }
    }

    // ------------------------------------------------------------------
    // Sub-JAR extraction
    // ------------------------------------------------------------------

    /**
     * Extracts a sub-JAR entry from this container JAR's META-INF/jars/ directory
     * to a temporary file on disk.
     *
     * <p>This method tries TWO strategies in order:
     * <ol>
     *   <li><b>ClassLoader resource stream</b> (preferred, protocol-agnostic):
     *       Uses {@link ClassLoader#getResourceAsStream(String)} which works for
     *       all URL protocols (including Forge 1.16+ {@code modjar:},
     *       NeoForge's nested-JAR handler, LaunchClassLoader, etc.).</li>
     *   <li><b>codeSource URL → ZipFile</b> (fallback for older Forge versions
     *       where the resource stream may not be available): parses the
     *       container JAR's file path from the class's codeSource URL and
     *       opens it as a {@link ZipFile}.</li>
     * </ol>
     */
    private File extractSubJar(String subJarName) throws Exception {
        String entryName = "META-INF/jars/" + subJarName;

        // ---- Strategy 1: ClassLoader resource stream (protocol-agnostic) ----
        // This works for modjar:/hwbench (Forge 1.16+), jar:file: (standard Java),
        // file: (LaunchClassLoader), and nested-JAR protocols (NeoForge).
        ClassLoader cl = getClass().getClassLoader();
        InputStream ris = null;
        try {
            ris = cl.getResourceAsStream(entryName);
        } catch (Throwable t) {
            log("getResourceAsStream failed for " + entryName + ": " + t);
        }
        if (ris != null) {
            log("Loading sub-JAR via classloader resource: " + entryName);
            File tempFile = File.createTempFile(
                    "hwbench-" + subJarName.replace(".jar", "") + "-", ".jar");
            tempFile.deleteOnExit();
            OutputStream os = null;
            try {
                os = new java.io.FileOutputStream(tempFile);
                byte[] buf = new byte[8192];
                int n;
                while ((n = ris.read(buf)) > 0) {
                    os.write(buf, 0, n);
                }
                return tempFile;
            } finally {
                if (ris != null) try { ris.close(); } catch (Throwable ignored) { }
                if (os != null) try { os.close(); } catch (Throwable ignored) { }
            }
        }

        // ---- Strategy 2: codeSource URL → ZipFile (fallback) ----
        URL codeSource = getClass().getProtectionDomain().getCodeSource().getLocation();
        if (codeSource == null) {
            log("Cannot determine container JAR location (codeSource is null)");
            return null;
        }

        // The codeSource URL for a class inside a JAR may be in one of these forms:
        //   jar:file:/path/to/container.jar!/com/hwbench/Class.class   (standard Java)
        //   file:/path/to/container.jar!/com/hwbench/Class.class       (Forge 1.7.10 LaunchClassLoader)
        //   file:/path/to/container.jar                                (class at JAR root, rare)
        //   file:/path/to/classes/                                     (directory, development mode)
        //   modjar:/hwbench                                            (Forge 1.16+ — CANNOT resolve to file path)
        // We need to extract just the JAR file path (everything before "!").
        String urlStr = codeSource.toString();
        log("codeSource URL: " + urlStr);

        // modjar: and other non-file protocols — Strategy 2 cannot resolve them
        if (urlStr.startsWith("modjar:") || (!urlStr.startsWith("jar:")
                && !urlStr.startsWith("file:"))) {
            log("Unsupported codeSource protocol for Strategy 2: " + urlStr
                    + " — sub-JAR extraction skipped");
            return null;
        }

        int bangIdx = urlStr.indexOf("!");
        if (bangIdx >= 0) {
            urlStr = urlStr.substring(0, bangIdx);
        }
        // Strip "jar:" protocol prefix if present
        if (urlStr.startsWith("jar:")) {
            urlStr = urlStr.substring(4);
        }

        File containerJar;
        try {
            URL cleanUrl = new URL(urlStr);
            containerJar = new File(cleanUrl.toURI());
        } catch (Exception e) {
            // Fallback: strip "file:" prefix and use path directly
            String path = urlStr;
            if (path.startsWith("file:")) {
                path = path.substring(5);
            }
            containerJar = new File(path);
        }
        if (!containerJar.isFile()) {
            log("Container is not a JAR file: " + containerJar
                    + " — sub-JAR extraction skipped (development mode)");
            return null;
        }

        ZipFile zf = null;
        try {
            zf = new ZipFile(containerJar);
            ZipEntry entry = zf.getEntry(entryName);
            if (entry == null) {
                log("Entry " + entryName + " not found in " + containerJar.getName());
                return null;
            }

            File tempFile = File.createTempFile(
                    "hwbench-" + subJarName.replace(".jar", "") + "-", ".jar");
            tempFile.deleteOnExit();

            InputStream is = null;
            OutputStream os = null;
            try {
                is = zf.getInputStream(entry);
                os = new java.io.FileOutputStream(tempFile);
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) {
                    os.write(buf, 0, n);
                }
            } finally {
                if (is != null) try { is.close(); } catch (Throwable ignored) { }
                if (os != null) try { os.close(); } catch (Throwable ignored) { }
            }
            return tempFile;
        } finally {
            if (zf != null) try { zf.close(); } catch (Throwable ignored) { }
        }
    }

    // ------------------------------------------------------------------
    // Classloader manipulation (reflection-based, version-agnostic)
    // ------------------------------------------------------------------

    /**
     * Adds a JAR file to the runtime classloader.
     *
     * Handles three classloader types across Forge versions:
     *   - net.minecraft.launchwrapper.LaunchClassLoader  (1.7.10, 1.12.2)
     *   - cpw.mods.modlauncher.TransformingClassLoader    (1.16.5 - 1.20.1)
     *   - io.github.zekerzhayard.forgewrap.loading.ForgeWrapClassLoader (NeoForge wrapper)
     *   - URLClassLoader subclasses (fallback)
     *
     * All of these expose addURL(URL) either directly or via URLClassLoader.
     */
    private void addToClassloader(File jarFile) throws Exception {
        URL url = jarFile.toURI().toURL();
        ClassLoader cl = getClass().getClassLoader();
        log("Looking for addURL method on classloader: " + cl.getClass().getName());

        // Walk the classloader hierarchy to find an addURL method
        Method addURL = findAddURLMethod(cl);
        if (addURL == null) {
            throw new RuntimeException("Cannot find addURL method on classloader: " + cl.getClass().getName());
        }
        log("Found addURL method: " + addURL.getDeclaringClass().getName() + ".addURL");
        addURL.setAccessible(true);
        addURL.invoke(cl, url);
    }

    /**
     * Searches the classloader's class hierarchy for an addURL(URL) method.
     * Returns null if no such method is found AND the classloader is not a
     * URLClassLoader (so the caller can fall back to creating a separate
     * URLClassLoader for the sub-JAR).
     */
    private Method findAddURLMethod(ClassLoader cl) {
        // Walk the class hierarchy looking for a declared addURL(URL) method.
        // This covers LaunchClassLoader (1.7.10/1.12.2) and any classloader
        // that directly declares addURL.
        Class<?> c = cl.getClass();
        while (c != null) {
            try {
                Method m = c.getDeclaredMethod("addURL", URL.class);
                return m;
            } catch (NoSuchMethodException e) {
                // continue up the hierarchy
            }
            c = c.getSuperclass();
        }
        // URLClassLoader fallback — ONLY valid if cl is actually a URLClassLoader
        // (otherwise invoking URLClassLoader.addURL on a non-URLClassLoader throws
        // IllegalArgumentException "object is not an instance of declaring class").
        if (cl instanceof URLClassLoader) {
            try {
                return URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            } catch (NoSuchMethodException e) {
                return null;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Event forwarding (for 1.7.10 / 1.12.2 where the sub-JAR's entry class
    // uses @Mod.EventHandler instead of self-registering on the event bus)
    // ------------------------------------------------------------------

    /**
     * Forwards an event object to the delegate's onServerStarting method via reflection.
     * The delegate's method parameter type is resolved against the real Forge event class
     * (which is on the classpath since the sub-JAR was loaded).
     */
    protected void forwardEvent(Object event) {
        if (delegate == null) {
            log("Cannot forward event — delegate is null (init may have failed)");
            return;
        }
        try {
            Class<?> eventClass = event.getClass();
            // Search the delegate's class hierarchy for an onServerStarting method
            // whose single parameter is assignable from the event class.
            Class<?> c = delegate.getClass();
            while (c != null) {
                for (Method m : c.getDeclaredMethods()) {
                    if (!m.getName().equals("onServerStarting")) continue;
                    if (m.getParameterCount() != 1) continue;
                    try {
                        Class<?>[] params = m.getParameterTypes();
                        if (params[0].isAssignableFrom(eventClass)) {
                            m.setAccessible(true);
                            m.invoke(delegate, event);
                            return;
                        }
                    } catch (Throwable t) {
                        // Parameter type resolution may fail for methods whose param type
                        // doesn't exist on this classpath — skip those silently.
                    }
                }
                c = c.getSuperclass();
            }
            log("No matching onServerStarting method found on delegate for event " + eventClass.getName());
        } catch (Throwable t) {
            log("Failed to forward event to delegate: " + t);
            try {
                t.printStackTrace(System.err);
            } catch (Throwable ignored) { /* ignore */ }
        }
    }

    // ------------------------------------------------------------------
    // Utilities
    // ------------------------------------------------------------------

    /** Checks whether a class is loadable on the current classpath (no side effects). */
    protected boolean classExists(String fqn) {
        try {
            Class.forName(fqn, false, getClass().getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Logs a message to System.out (captured by Forge's log4j as [STDOUT])
     * and System.err (captured as [STDERR]). This is the only logging channel
     * available before the sub-JAR's logger is initialized and works across
     * all Forge versions without depending on any specific logging API.
     */
    protected void log(String message) {
        String tagged = "[HWBench-Container] " + message;
        try {
            System.out.println(tagged);
        } catch (Throwable ignored) { /* ignore */ }
        try {
            System.err.println(tagged);
        } catch (Throwable ignored) { /* ignore */ }
    }
}

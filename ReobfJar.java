import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/**
 * Reobfuscate a Forge 1.12.2 mod JAR from MCP names to SRG names.
 *
 * Forge 1.12.2 runtime expects SRG names (func_xxx, field_xxx).
 * Our compiled JAR has MCP names (getName, getUsage, etc.).
 * This tool renames the relevant CommandBase methods to their SRG equivalents.
 */
public class ReobfJar {
    // MCP method name -> SRG method name (1.12.2 stable_39 mappings)
    // Covers CommandBase/ICommand declarations AND runtime method calls
    // to Minecraft classes (ICommandSender, MinecraftServer, PlayerList, etc.)
    static final Map<String, String> METHOD_REMAP = new HashMap<>();
    static {
        // ICommand interface methods (1.12.2 SRG names) - method DECLARATIONS
        METHOD_REMAP.put("getName", "func_71517_b");
        METHOD_REMAP.put("getUsage", "func_71518_a");
        METHOD_REMAP.put("getRequiredPermissionLevel", "func_82362_a");
        METHOD_REMAP.put("getTabCompletions", "func_184883_a");
        METHOD_REMAP.put("getAliases", "func_71514_a");
        METHOD_REMAP.put("checkPermission", "func_184882_a");
        METHOD_REMAP.put("isUsernameIndex", "func_82373_a");
        METHOD_REMAP.put("compareTo", "func_82393_a");
        METHOD_REMAP.put("execute", "func_184881_a");
        // CommandBase additional methods
        METHOD_REMAP.put("getListOfStringsMatchingLastWord", "func_71530_a");

        // Method CALLS to Minecraft classes (not just declarations)
        // ICommandSender methods
        METHOD_REMAP.put("canUseCommand", "func_70003_b");       // ICommandSender.canUseCommand(int, String) -> check permission
        METHOD_REMAP.put("sendMessage", "func_145747_a");        // ICommandSender.sendMessage(ITextComponent)
        // MinecraftServer methods
        METHOD_REMAP.put("getPlayerList", "func_184103_al");     // MinecraftServer.getPlayerList() -> PlayerList
        // PlayerList methods
        METHOD_REMAP.put("getPlayers", "func_181057_v");         // PlayerList.getPlayers() -> List<EntityPlayerMP>
        // NetHandlerPlayServer methods
        METHOD_REMAP.put("disconnect", "func_194028_b");         // NetHandlerPlayServer.disconnect(ITextComponent)
    }

    // Minecraft class owners whose method calls need remapping
    // (in addition to our own class and ICommand/CommandBase)
    static final Set<String> EXTRA_OWNERS = new HashSet<>(Arrays.asList(
        "net/minecraft/command/ICommandSender",
        "net/minecraft/server/MinecraftServer",
        "net/minecraft/server/management/PlayerList",
        "net/minecraft/network/NetHandlerPlayServer",
        "net/minecraft/entity/player/EntityPlayerMP",
        "net/minecraft/network/play/server/SPacketDisconnect"
    ));

    // MCP field name -> SRG field name (1.12.2 stable_39 mappings)
    // Covers field accesses on Minecraft classes.
    static final Map<String, String> FIELD_REMAP = new HashMap<>();
    static {
        // EntityPlayerMP.connection (NetHandlerPlayServer)
        FIELD_REMAP.put("connection", "field_71135_a");
        // PlayerEvent.player (inherited from EntityEvent) - Forge uses this in events
        // Note: player field on PlayerEvent is exposed via Forge patch, but bytecode still
        // references the SRG name when running in production.
        FIELD_REMAP.put("player", "field_756_b");  // actually parent class field
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: ReobfJar <input.jar> <output.jar>");
            System.exit(1);
        }
        File inJar = new File(args[0]);
        File outJar = new File(args[1]);

        System.out.println("[ReobfJar] Input:  " + inJar);
        System.out.println("[ReobfJar] Output: " + outJar);

        Remapper remapper = new Remapper() {
            // Only remap methods on the specific CommandBase subclass and the ICommand interface.
            // Other classes (BenchmarkResult$TestResult.getName(), etc.) must NOT be remapped
            // because their getName() is a regular Java getter, not Minecraft's ICommand.getName().
            private boolean shouldRemapMethod(String owner) {
                if (owner == null) return false;
                return owner.equals("com/hwbench/forge/HWBenchForge1122$HWBenchCommand") ||
                       owner.equals("net/minecraft/command/ICommand") ||
                       owner.equals("net/minecraft/command/CommandBase") ||
                       EXTRA_OWNERS.contains(owner);
            }

            private boolean shouldRemapField(String owner) {
                if (owner == null) return false;
                return owner.equals("net/minecraft/entity/player/EntityPlayerMP") ||
                       owner.equals("net/minecraftforge/event/entity/player/PlayerEvent") ||
                       owner.equals("net/minecraftforge/event/entity/EntityEvent") ||
                       owner.equals("net/minecraft/entity/Entity");
            }

            @Override
            public String mapMethodName(String owner, String name, String desc) {
                if (shouldRemapMethod(owner)) {
                    String mapped = METHOD_REMAP.get(name);
                    if (mapped != null) {
                        System.out.println("[ReobfJar]   method " + owner + "." + name + desc + " -> " + mapped);
                        return mapped;
                    }
                }
                return name;
            }

            @Override
            public String mapFieldName(String owner, String name, String desc) {
                if (shouldRemapField(owner)) {
                    String mapped = FIELD_REMAP.get(name);
                    if (mapped != null) {
                        System.out.println("[ReobfJar]   field " + owner + "." + name + " : " + desc + " -> " + mapped);
                        return mapped;
                    }
                }
                return name;
            }

            @Override
            public String mapInvokeDynamicMethodName(String name, String desc) {
                return name;
            }
        };

        try (JarFile jf = new JarFile(inJar);
             JarOutputStream jos = new JarOutputStream(new FileOutputStream(outJar))) {

            Enumeration<JarEntry> entries = jf.entries();
            int processed = 0, renamed = 0, skipped = 0;
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                // Strip Multi-Release JAR module-info.class - Forge 1.12.2's ASM 5.2 cannot read it
                // and throws IllegalArgumentException, polluting logs with "corrupt zip" warnings.
                if (name.equals("module-info.class") ||
                    name.startsWith("META-INF/versions/") && name.endsWith("module-info.class")) {
                    skipped++;
                    System.out.println("[ReobfJar]   strip " + name);
                    continue;
                }
                if (name.endsWith(".class") && name.contains("com/hwbench/")) {
                    byte[] bytes = readAll(jf.getInputStream(entry));
                    ClassReader cr = new ClassReader(bytes);
                    ClassWriter cw = new ClassWriter(0);
                    ClassRemapper cr_adapter = new ClassRemapper(cw, remapper);
                    cr.accept(cr_adapter, 0);
                    byte[] out = cw.toByteArray();
                    JarEntry ne = new JarEntry(name);
                    jos.putNextEntry(ne);
                    jos.write(out);
                    jos.closeEntry();
                    processed++;
                    if (out.length != bytes.length) renamed++;
                } else {
                    JarEntry ne = new JarEntry(name);
                    jos.putNextEntry(ne);
                    if (!entry.isDirectory()) {
                        byte[] bytes = readAll(jf.getInputStream(entry));
                        jos.write(bytes);
                    }
                    jos.closeEntry();
                }
            }
            System.out.println("[ReobfJar] Processed " + processed + " com/hwbench classes, " +
                               renamed + " changed size, " + skipped + " stripped");
        }
        System.out.println("[ReobfJar] Done: " + outJar.length() + " bytes");
    }

    private static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }
}

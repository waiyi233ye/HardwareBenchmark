package com.hwbench.forge;

import com.hwbench.core.BenchmarkResult;
import com.hwbench.core.CPUBenchmark;
import com.hwbench.core.DiskBenchmark;
import com.hwbench.core.HardwareDetector;
import com.hwbench.core.LibraryManager;
import com.hwbench.core.MemoryBenchmark;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HardwareBenchmark Forge mod 入口（1.7.10 专用）
 *
 * 1.7.10 Forge/FML API（最老版本，无 Brigadier，FML 包名为 cpw.mods.fml）：
 *  - @Mod + @EventHandler + FMLServerStartingEvent
 *  - 命令继承 CommandBase，通过 event.registerServerCommand 注册
 *  - 文本: ChatComponentText / IChatComponent
 *  - 玩家: EntityPlayerMP，踢出: playerNetServerHandler.kickPlayerFromServer
 *  - 登录事件: cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent
 *    （需在 FMLCommonHandler.instance().bus() 上注册）
 */
@Mod(modid = HWBenchForge1710.MODID, name = "HardwareBenchmark", version = "1.2.0")
public class HWBenchForge1710 {
    public static final String MODID = "hwbench";
    private static final Logger LOGGER = LogManager.getLogger("HWBench");

    private final AtomicBoolean serverLocked = new AtomicBoolean(false);

    @Mod.EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        LOGGER.info("[HardwareBenchmark] Forge mod (1.7.10) 服务器启动，注册命令与事件");
        fixShadedLibraryClassloading();
        FMLCommonHandler.instance().bus().register(this);
        event.registerServerCommand(new HWBenchCommand(this));
    }

    /**
     * Forge 的 LaunchClassLoader 会对所有加载的类运行反混淆 Transformer。
     * shaded 进来的第三方库（oshi/jna/slf4j）不是 Minecraft 类，Transformer
     * 处理它们时会失败，导致类被加入 invalidClasses 缓存，之后永远无法加载。
     *
     * 修复：将这些包加入 transformerExceptions（跳过 Transformer，直接用
     * URLClassLoader.findClass 加载），并清除可能已缓存的 invalidClasses。
     */
    private void fixShadedLibraryClassloading() {
        ClassLoader cl = getClass().getClassLoader();
        if (!(cl instanceof LaunchClassLoader)) {
            LOGGER.info("[HardwareBenchmark] ClassLoader 不是 LaunchClassLoader (" + cl + ")，跳过修复");
            return;
        }
        LaunchClassLoader lcl = (LaunchClassLoader) cl;
        lcl.addTransformerExclusion("org.slf4j.");
        lcl.addTransformerExclusion("oshi.");
        lcl.addTransformerExclusion("com.sun.jna.");
        // 排除 com.hwbench 包，防止 Forge 的反混淆 Transformer 破坏
        // benchmark 类中的 varargs/autoboxing 字节码（导致 "f != java.lang.Long" 验证错误）
        lcl.addTransformerExclusion("com.hwbench.");
        // 清除之前可能因 Transformer 失败而缓存的 invalidClasses
        try {
            Field f = LaunchClassLoader.class.getDeclaredField("invalidClasses");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<String> invalid = (Set<String>) f.get(lcl);
            int count = invalid.size();
            invalid.clear();
            LOGGER.info("[HardwareBenchmark] 已清除 " + count + " 个 invalidClasses 缓存条目");
        } catch (Throwable t) {
            LOGGER.warn("[HardwareBenchmark] 清除 invalidClasses 失败: " + t.getMessage());
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (serverLocked.get() && event.player instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            player.playerNetServerHandler.kickPlayerFromServer(
                    "§c服务器正在执行硬件跑分，暂时关闭，请稍后再来。");
        }
    }

    public boolean lock() {
        return serverLocked.compareAndSet(false, true);
    }

    public boolean unlock() {
        return serverLocked.compareAndSet(true, false);
    }

    /** HWBench 命令处理器（1.7.10 CommandBase 风格） */
    public static class HWBenchCommand extends CommandBase {
        private final HWBenchForge1710 mod;

        public HWBenchCommand(HWBenchForge1710 mod) {
            this.mod = mod;
        }

        @Override
        public String getCommandName() {
            return "hwbench";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/hwbench <detect|cpu|mem|disk|all|libs|lock|unlock>";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 2;
        }

        @Override
        public int compareTo(Object o) {
            if (o instanceof net.minecraft.command.ICommand) {
                return compareTo((net.minecraft.command.ICommand) o);
            }
            return 0;
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            String sub = args.length == 0 ? "help" : args[0];
            switch (sub) {
                case "detect":
                    runDetect(sender);
                    break;
                case "cpu":
                    runCpu(sender);
                    break;
                case "mem":
                    runMem(sender);
                    break;
                case "disk":
                    runDisk(sender);
                    break;
                case "all":
                    runAll(sender);
                    break;
                case "libs":
                    runLibs(sender);
                    break;
                case "lock":
                    lockServer(sender);
                    break;
                case "unlock":
                    unlockServer(sender);
                    break;
                default:
                    help(sender);
            }
        }

        private void send(ICommandSender sender, String message) {
            try {
                sender.addChatMessage(new ChatComponentText(message));
            } catch (Throwable ignored) { /* not on main thread */ }
            // System.err.println 被 Forge 的 log4j2 捕获到 logs/latest.log（以 [STDERR] 标记）。
            // System.out 也被捕获（以 [STDOUT] 标记）。两者都写，确保日志可见。
            String tagged = "[HWBench] " + message;
            System.out.println(tagged);
            System.err.println(tagged);
            try {
                LOGGER.info(tagged);
            } catch (Throwable ignored) { /* log4j may fail off-main-thread */ }
        }

        /** 启动跑分线程，带未捕获异常处理器确保错误一定写入日志 */
        private void startBenchThread(String name, Runnable task) {
            Thread t = new Thread(() -> {
                try {
                    task.run();
                } catch (Throwable e) {
                    // 兜底：即使 catch 内部 send() 也失败，也要把错误写进日志
                    System.err.println("[HWBench] " + name + " 线程未捕获异常: " + e);
                    e.printStackTrace(System.err);
                }
            }, name);
            t.setUncaughtExceptionHandler((thread, throwable) -> {
                System.err.println("[HWBench] " + thread.getName() + " 未捕获异常: " + throwable);
                throwable.printStackTrace(System.err);
            });
            t.start();
        }

        private void help(ICommandSender sender) {
            send(sender, "§6=== HardwareBenchmark Forge (1.7.10) ===");
            send(sender, "/hwbench detect - 检测硬件信息");
            send(sender, "/hwbench cpu - CPU甜甜圈跑分");
            send(sender, "/hwbench mem - 内存读写跑分");
            send(sender, "/hwbench disk - 磁盘IO跑分");
            send(sender, "/hwbench all - 运行全部跑分");
            send(sender, "/hwbench libs - 检查并补全Linux运行库");
            send(sender, "/hwbench lock - 手动锁定服务器");
            send(sender, "/hwbench unlock - 手动解锁服务器");
        }

        private void runDetect(final ICommandSender sender) {
            send(sender, "§e正在检测硬件信息...");
            startBenchThread("HWBench-Detect", () -> {
                try {
                    HardwareDetector detector = new HardwareDetector();
                    BenchmarkResult result = new BenchmarkResult();
                    detector.detectAll(result);
                    String report = detector.generateReport(result);
                    for (String line : report.split("\n")) {
                        send(sender, line);
                    }
                    send(sender, "§a硬件检测完成");
                } catch (Throwable e) {
                    send(sender, "§c硬件检测失败: " + e.getMessage());
                }
            });
        }

        private void runCpu(final ICommandSender sender) {
            send(sender, "§e开始 CPU 跑分，服务器可能卡顿...");
            startBenchThread("HWBench-CPU", () -> {
                try {
                    CPUBenchmark bench = new CPUBenchmark(100, 3, 512, false);
                    BenchmarkResult.TestResult r = bench.runAll();
                    send(sender, String.format("§aCPU跑分完成: 得分 %.2f, 耗时 %dms", r.getScore(), r.getDurationMs()));
                    for (String line : r.getDetails().split("\n")) {
                        send(sender, line);
                    }
                } catch (Throwable e) {
                    send(sender, "§cCPU跑分失败: " + e.getMessage());
                }
            });
        }

        private void runMem(final ICommandSender sender) {
            send(sender, "§e开始内存跑分...");
            startBenchThread("HWBench-Mem", () -> {
                try {
                    // 降低数组大小避免 OOM（服务器堆仅 768m/1024m）
                    MemoryBenchmark bench = new MemoryBenchmark(64, 3);
                    BenchmarkResult.TestResult r = bench.runAll();
                    send(sender, String.format("§a内存跑分完成: 得分 %.2f, 耗时 %dms", r.getScore(), r.getDurationMs()));
                } catch (Throwable e) {
                    send(sender, "§c内存跑分失败: " + e.getMessage());
                }
            });
        }

        private void runDisk(final ICommandSender sender) {
            send(sender, "§e开始磁盘跑分...");
            startBenchThread("HWBench-Disk", () -> {
                try {
                    DiskBenchmark bench = new DiskBenchmark(64, 4, 5, new File("."));
                    BenchmarkResult.TestResult r = bench.runAll();
                    send(sender, String.format("§a磁盘跑分完成: 得分 %.2f, 耗时 %dms", r.getScore(), r.getDurationMs()));
                } catch (Throwable e) {
                    send(sender, "§c磁盘跑分失败: " + e.getMessage());
                }
            });
        }

        private void runAll(final ICommandSender sender) {
            send(sender, "§e=== 运行全部跑分 ===");
            send(sender, "§c注意：跑分期间服务器会卡顿，建议先 /hwbench lock");
            startBenchThread("HWBench-All", () -> {
                try {
                    runDetect(sender);
                    Thread.sleep(500);
                    runCpu(sender);
                    Thread.sleep(500);
                    runMem(sender);
                    Thread.sleep(500);
                    runDisk(sender);
                    send(sender, "§a=== 全部跑分完成 ===");
                } catch (Throwable e) {
                    send(sender, "§c跑分失败: " + e.getMessage());
                }
            });
        }

        private void runLibs(final ICommandSender sender) {
            send(sender, "§e检查并补全Linux运行库...");
            startBenchThread("HWBench-Libs", () -> {
                try {
                    List<String> libs = Arrays.asList("lshw", "lm-sensors", "pciutils", "smartmontools");
                    LibraryManager mgr = new LibraryManager(libs, false, "auto");
                    String report = mgr.checkAndInstall();
                    for (String line : report.split("\n")) {
                        send(sender, line);
                    }
                    send(sender, "§a库检查完成");
                } catch (Throwable e) {
                    send(sender, "§c库检查失败: " + e.getMessage());
                }
            });
        }

        private void lockServer(ICommandSender sender) {
            if (mod.lock()) {
                send(sender, "§c服务器已锁定。新玩家将无法加入。");
                // 踢出现有玩家
                MinecraftServer server = MinecraftServer.getServer();
                if (server != null && server.getConfigurationManager() != null) {
                    for (Object obj : server.getConfigurationManager().playerEntityList) {
                        if (obj instanceof EntityPlayerMP) {
                            EntityPlayerMP player = (EntityPlayerMP) obj;
                            player.playerNetServerHandler.kickPlayerFromServer(
                                    "§c服务器正在执行硬件跑分，暂时关闭，请稍后再来。");
                        }
                    }
                }
            } else {
                send(sender, "§e服务器已经处于锁定状态。");
            }
        }

        private void unlockServer(ICommandSender sender) {
            if (mod.unlock()) {
                send(sender, "§a服务器已解锁。玩家可以正常加入。");
            } else {
                send(sender, "§e服务器当前未锁定。");
            }
        }
    }
}

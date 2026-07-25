package com.hwbench.forge;

import com.hwbench.core.BenchmarkResult;
import com.hwbench.core.CPUBenchmark;
import com.hwbench.core.DiskBenchmark;
import com.hwbench.core.HardwareDetector;
import com.hwbench.core.LibraryManager;
import com.hwbench.core.MemoryBenchmark;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * HardwareBenchmark Forge mod 入口（1.16.5 MCP mappings 专用）
 *
 * 1.16.5 使用 MCP mappings，类名与 official mappings 不同：
 *  - CommandSource (而非 CommandSourceStack)
 *  - ServerPlayerEntity (而非 ServerPlayer)
 *  - ITextComponent / StringTextComponent (而非 Component / TextComponent)
 */
@Mod(HWBenchForgeLegacy.MOD_ID)
public class HWBenchForgeLegacy {
    public static final String MOD_ID = "hwbench";
    private static final Logger LOGGER = LogManager.getLogger("HWBench");

    private final AtomicBoolean serverLocked = new AtomicBoolean(false);
    private final String lockMessage = "§c服务器正在执行硬件跑分，暂时关闭，请稍后再来。";

    public HWBenchForgeLegacy() {
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("[HardwareBenchmark] Forge mod (1.16.5) 加载。");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[HardwareBenchmark] 服务器启动，注册 /hwbench 命令");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSource> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("hwbench")
                .requires(src -> src.hasPermissionLevel(2))
                .then(Commands.literal("detect").executes(ctx -> runDetect(ctx.getSource())))
                .then(Commands.literal("cpu").executes(ctx -> runCpu(ctx.getSource())))
                .then(Commands.literal("mem").executes(ctx -> runMem(ctx.getSource())))
                .then(Commands.literal("disk").executes(ctx -> runDisk(ctx.getSource())))
                .then(Commands.literal("all").executes(ctx -> runAll(ctx.getSource())))
                .then(Commands.literal("libs").executes(ctx -> runLibs(ctx.getSource())))
                .then(Commands.literal("lock").executes(ctx -> lockServer(ctx.getSource())))
                .then(Commands.literal("unlock").executes(ctx -> unlockServer(ctx.getSource())))
                .executes(ctx -> help(ctx.getSource()))
        );
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (serverLocked.get() && event.getEntity() instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) event.getEntity();
            kickPlayer(player);
        }
    }

    private int help(CommandSource src) {
        send(src, "§6=== HardwareBenchmark Forge (1.16.5) ===");
        send(src, "/hwbench detect - 检测硬件信息");
        send(src, "/hwbench cpu - CPU甜甜圈跑分");
        send(src, "/hwbench mem - 内存读写跑分");
        send(src, "/hwbench disk - 磁盘IO跑分");
        send(src, "/hwbench all - 运行全部跑分");
        send(src, "/hwbench libs - 检查并补全Linux运行库");
        send(src, "/hwbench lock - 手动锁定服务器");
        send(src, "/hwbench unlock - 手动解锁服务器");
        return 1;
    }

    private int runDetect(CommandSource src) {
        send(src, "§e正在检测硬件信息...");
        new Thread(() -> {
            try {
                HardwareDetector detector = new HardwareDetector();
                BenchmarkResult result = new BenchmarkResult();
                detector.detectAll(result);
                String report = detector.generateReport(result);
                for (String line : report.split("\n")) {
                    send(src, line);
                }
            } catch (Throwable e) {
                send(src, "§c硬件检测失败: " + e.getMessage());
            }
        }, "HWBench-Detect").start();
        return 1;
    }

    private int runCpu(CommandSource src) {
        send(src, "§e开始 CPU 跑分，服务器可能卡顿...");
        new Thread(() -> {
            try {
                CPUBenchmark bench = new CPUBenchmark(100, 3, 512, false);
                BenchmarkResult.TestResult r = bench.runAll();
                send(src, String.format("§aCPU跑分完成: 得分 %.2f, 耗时 %dms", r.getScore(), r.getDurationMs()));
                for (String line : r.getDetails().split("\n")) {
                    send(src, line);
                }
            } catch (Throwable e) {
                send(src, "§cCPU跑分失败: " + e.getMessage());
            }
        }, "HWBench-CPU").start();
        return 1;
    }

    private int runMem(CommandSource src) {
        send(src, "§e开始内存跑分...");
        new Thread(() -> {
            try {
                MemoryBenchmark bench = new MemoryBenchmark(256, 5);
                BenchmarkResult.TestResult r = bench.runAll();
                send(src, String.format("§a内存跑分完成: 得分 %.2f, 耗时 %dms", r.getScore(), r.getDurationMs()));
            } catch (Throwable e) {
                send(src, "§c内存跑分失败: " + e.getMessage());
            }
        }, "HWBench-Mem").start();
        return 1;
    }

    private int runDisk(CommandSource src) {
        send(src, "§e开始磁盘跑分...");
        new Thread(() -> {
            try {
                DiskBenchmark bench = new DiskBenchmark(64, 4, 5, new File("."));
                BenchmarkResult.TestResult r = bench.runAll();
                send(src, String.format("§a磁盘跑分完成: 得分 %.2f, 耗时 %dms", r.getScore(), r.getDurationMs()));
            } catch (Throwable e) {
                send(src, "§c磁盘跑分失败: " + e.getMessage());
            }
        }, "HWBench-Disk").start();
        return 1;
    }

    private int runAll(CommandSource src) {
        send(src, "§e=== 运行全部跑分 ===");
        send(src, "§c注意：跑分期间服务器会卡顿，建议先 /hwbench lock");
        new Thread(() -> {
            try {
                runDetect(src);
                Thread.sleep(500);
                runCpu(src);
                Thread.sleep(500);
                runMem(src);
                Thread.sleep(500);
                runDisk(src);
                send(src, "§a=== 全部跑分完成 ===");
            } catch (Throwable e) {
                send(src, "§c跑分失败: " + e.getMessage());
            }
        }, "HWBench-All").start();
        return 1;
    }

    private int runLibs(CommandSource src) {
        send(src, "§e检查并补全Linux运行库...");
        new Thread(() -> {
            try {
                List<String> libs = Arrays.asList("lshw", "lm-sensors", "pciutils", "smartmontools");
                LibraryManager mgr = new LibraryManager(libs, false, "auto");
                String report = mgr.checkAndInstall();
                for (String line : report.split("\n")) {
                    send(src, line);
                }
            } catch (Throwable e) {
                send(src, "§c库检查失败: " + e.getMessage());
            }
        }, "HWBench-Libs").start();
        return 1;
    }

    private int lockServer(CommandSource src) {
        if (serverLocked.compareAndSet(false, true)) {
            send(src, "§c服务器已锁定。新玩家将无法加入。");
            try {
                Object server = src.getServer();
                if (server != null) {
                    Method getPlayerList = findMethod(server.getClass(), "getPlayerList");
                    if (getPlayerList != null) {
                        Object playerList = getPlayerList.invoke(server);
                        if (playerList != null) {
                            Method getPlayers = findMethod(playerList.getClass(), "getPlayers");
                            if (getPlayers != null) {
                                @SuppressWarnings("unchecked")
                                Iterable<Object> players = (Iterable<Object>) getPlayers.invoke(playerList);
                                for (Object player : players) {
                                    kickPlayer(player);
                                }
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                LOGGER.warn("[HWBench] 踢出玩家失败: " + e.getMessage());
            }
        } else {
            send(src, "§e服务器已经处于锁定状态。");
        }
        return 1;
    }

    private int unlockServer(CommandSource src) {
        if (serverLocked.compareAndSet(true, false)) {
            send(src, "§a服务器已解锁。玩家可以正常加入。");
        } else {
            send(src, "§e服务器当前未锁定。");
        }
        return 1;
    }

    private void kickPlayer(Object player) {
        try {
            Field connField = findField(player.getClass(), "connection");
            if (connField == null) return;
            Object conn = connField.get(player);
            if (conn == null) return;
            Object comp = makeComponent(lockMessage);
            if (comp == null) return;
            Method disconnect = findMethod(conn.getClass(), "disconnect");
            if (disconnect != null && disconnect.getParameterCount() == 1) {
                disconnect.invoke(conn, comp);
            }
        } catch (Throwable e) {
            LOGGER.warn("[HWBench] kick 失败: " + e.getMessage());
        }
    }

    /** 1.16.5: new StringTextComponent(String) */
    private Object makeComponent(String text) {
        try {
            Class<?> cls = Class.forName("net.minecraft.util.text.StringTextComponent");
            Constructor<?> ctor = cls.getConstructor(String.class);
            return ctor.newInstance(text);
        } catch (Throwable e) { /* ignore */ }
        return null;
    }

    private void send(CommandSource src, String message) {
        LOGGER.info("[HWBench] " + message);
        try {
            Object comp = makeComponent(message);
            if (comp == null) {
                return;
            }
            // 1.16.5: sendFeedback(ITextComponent, boolean)
            try {
                Method m = findMethod(CommandSource.class, "sendFeedback", comp.getClass());
                if (m != null) {
                    m.invoke(src, comp, false);
                    return;
                }
            } catch (Throwable e) { /* fall through */ }
            try {
                Method m = findMethod(CommandSource.class, "sendFeedback", ITextComponent.class);
                if (m != null) {
                    m.invoke(src, comp, false);
                    return;
                }
            } catch (Throwable e) { /* fall through */ }
        } catch (Throwable t) {
            /* 已记录到日志 */
        }
    }

    private static Method findMethod(Class<?> cls, String name, Class<?>... paramTypes) {
        Class<?> c = cls;
        while (c != null) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && (paramTypes.length == 0 || matches(m.getParameterTypes(), paramTypes))) {
                    m.setAccessible(true);
                    return m;
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static boolean matches(Class<?>[] declared, Class<?>[] given) {
        if (declared.length != given.length) return false;
        for (int i = 0; i < declared.length; i++) {
            if (!declared[i].isAssignableFrom(given[i])) return false;
        }
        return true;
    }

    private static Field findField(Class<?> cls, String name) {
        Class<?> c = cls;
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        return null;
    }
}

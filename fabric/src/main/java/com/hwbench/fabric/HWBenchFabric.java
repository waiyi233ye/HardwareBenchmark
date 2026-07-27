package com.hwbench.fabric;

import com.hwbench.core.BenchConfig;
import com.hwbench.core.BenchmarkResult;
import com.hwbench.core.CPUBenchmark;
import com.hwbench.core.DiskBenchmark;
import com.hwbench.core.HardwareDetector;
import com.hwbench.core.LibraryManager;
import com.hwbench.core.MemoryBenchmark;
import com.hwbench.core.ResultReporter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import com.mojang.brigadier.context.CommandContext;

import static net.minecraft.server.command.CommandManager.literal;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HardwareBenchmark Fabric mod 入口
 *
 * 兼容 Fabric 1.16.5+ 的 API。
 */
public class HWBenchFabric implements ModInitializer {
    public static final String MOD_ID = "hwbench";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final AtomicBoolean serverLocked = new AtomicBoolean(false);
    private final String lockMessage = "§c服务器正在执行硬件跑分，暂时关闭，请稍后再来。";

    @Override
    public void onInitialize() {
        LOGGER.info("[HardwareBenchmark] Fabric mod 初始化");

        // 注册命令（使用 v1 API 以兼容 1.16.5+）
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            try {
                dispatcher.register(literal("hwbench")
                        .requires(src -> src.hasPermissionLevel(2))
                        .then(literal("detect").executes(this::runDetect))
                        .then(literal("cpu").executes(this::runCpu))
                        .then(literal("mem").executes(this::runMem))
                        .then(literal("disk").executes(this::runDisk))
                        .then(literal("all").executes(this::runAll))
                        .then(literal("libs").executes(this::runLibs))
                        .then(literal("lock").executes(this::lockServer))
                        .then(literal("unlock").executes(this::unlockServer))
                        .then(literal("help").executes(this::help))
                        .executes(this::help)
                );
            } catch (Exception e) {
                LOGGER.error("注册 /hwbench 命令失败", e);
            }
        });

        // 拦截玩家登录（锁定时拒绝）
        ServerPlayConnectionEvents.INIT.register((handler, server) -> {
            if (serverLocked.get()) {
                handler.disconnect((Text) makeText(lockMessage));
            }
        });
    }

    private int help(CommandContext<ServerCommandSource> ctx) {
        send(ctx.getSource(), "§6=== HardwareBenchmark Fabric ===");
        send(ctx.getSource(), "/hwbench detect - 检测硬件信息");
        send(ctx.getSource(), "/hwbench cpu - CPU甜甜圈跑分");
        send(ctx.getSource(), "/hwbench mem - 内存读写跑分");
        send(ctx.getSource(), "/hwbench disk - 磁盘IO跑分");
        send(ctx.getSource(), "/hwbench all - 运行全部跑分");
        send(ctx.getSource(), "/hwbench libs - 检查并补全Linux运行库");
        send(ctx.getSource(), "/hwbench lock - 手动锁定服务器");
        send(ctx.getSource(), "/hwbench unlock - 手动解锁服务器");
        return 1;
    }

    private int runDetect(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        send(src, "§e正在检测硬件信息...");
        startBenchThread("HWBench-Detect", src, () -> {
            HardwareDetector detector = new HardwareDetector();
            BenchmarkResult result = new BenchmarkResult();
            detector.detectAll(result);
            String report = detector.generateReport(result);
            for (String line : report.split("\n")) {
                send(src, line);
            }
            send(src, "§a硬件检测完成");
        }, "硬件检测失败");
        return 1;
    }

    private int runCpu(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        // 在线程外部加载 BenchConfig 单例，避免线程安全问题
        BenchConfig config = BenchConfig.load(new File("."));
        send(src, "§e开始 CPU 跑分，服务器可能卡顿...");
        startBenchThread("HWBench-CPU", src, () -> {
            BenchmarkResult result = new BenchmarkResult();
            String hardwareInfo = "";
            try {
                HardwareDetector detector = new HardwareDetector();
                detector.detectAll(result);
                hardwareInfo = detector.generateReport(result);
            } catch (Throwable ignored) { }

            CPUBenchmark bench = new CPUBenchmark(
                    config.cpuDonutFrames, config.cpuComputeIterations, config.cpuMatrixSize,
                    config.cpuShowAnimation, config.cpuPrimeRange, config.cpuFloatIterations,
                    config.cpuTimeoutSeconds
            );
            BenchmarkResult.TestResult r = bench.runAll();
            result.addTestResult("cpu", r);
            send(src, String.format("§aCPU跑分完成: 得分 %.2f, 耗时 %dms", r.getScore(), r.getDurationMs()));
            for (String line : r.getDetails().split("\n")) {
                send(src, line);
            }

            // 写入服务端 logs/hwbench/ 目录
            if (config.reportWriteToServerLogs) {
                ResultReporter reporter = new ResultReporter(true, "hwbench-reports", false);
                String report = reporter.generateReport(result, hardwareInfo);
                File logsFile = reporter.saveReportToServerLogs(result, report, new File("logs"));
                if (logsFile != null) {
                    send(src, "§a报告已写入服务端日志: " + logsFile.getPath());
                }
            }
        }, "CPU跑分失败");
        return 1;
    }

    private int runMem(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        // 在线程外部加载 BenchConfig 单例，避免线程安全问题
        BenchConfig config = BenchConfig.load(new File("."));
        send(src, "§e开始内存跑分...");
        startBenchThread("HWBench-Mem", src, () -> {
            BenchmarkResult result = new BenchmarkResult();
            String hardwareInfo = "";
            try {
                HardwareDetector detector = new HardwareDetector();
                detector.detectAll(result);
                hardwareInfo = detector.generateReport(result);
            } catch (Throwable ignored) { }

            MemoryBenchmark bench = new MemoryBenchmark(
                    config.memArraySizeMB, config.memIterations, config.memRandomAccessCount,
                    config.memTimeoutSeconds
            );
            BenchmarkResult.TestResult r = bench.runAll();
            result.addTestResult("memory", r);
            send(src, String.format("§a内存跑分完成: 得分 %.2f, 耗时 %dms", r.getScore(), r.getDurationMs()));

            // 写入服务端 logs/hwbench/ 目录
            if (config.reportWriteToServerLogs) {
                ResultReporter reporter = new ResultReporter(true, "hwbench-reports", false);
                String report = reporter.generateReport(result, hardwareInfo);
                File logsFile = reporter.saveReportToServerLogs(result, report, new File("logs"));
                if (logsFile != null) {
                    send(src, "§a报告已写入服务端日志: " + logsFile.getPath());
                }
            }
        }, "内存跑分失败");
        return 1;
    }

    private int runDisk(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        // 在线程外部加载 BenchConfig 单例，避免线程安全问题
        BenchConfig config = BenchConfig.load(new File("."));
        send(src, "§e开始磁盘跑分...");
        startBenchThread("HWBench-Disk", src, () -> {
            BenchmarkResult result = new BenchmarkResult();
            String hardwareInfo = "";
            try {
                HardwareDetector detector = new HardwareDetector();
                detector.detectAll(result);
                hardwareInfo = detector.generateReport(result);
            } catch (Throwable ignored) { }

            DiskBenchmark bench = new DiskBenchmark(
                    config.diskFileSizeMB, config.diskBlockSizeKB, config.diskRandomIOCount, new File("."),
                    config.diskTimeoutSeconds
            );
            BenchmarkResult.TestResult r = bench.runAll();
            result.addTestResult("disk", r);
            send(src, String.format("§a磁盘跑分完成: 得分 %.2f, 耗时 %dms", r.getScore(), r.getDurationMs()));

            // 写入服务端 logs/hwbench/ 目录
            if (config.reportWriteToServerLogs) {
                ResultReporter reporter = new ResultReporter(true, "hwbench-reports", false);
                String report = reporter.generateReport(result, hardwareInfo);
                File logsFile = reporter.saveReportToServerLogs(result, report, new File("logs"));
                if (logsFile != null) {
                    send(src, "§a报告已写入服务端日志: " + logsFile.getPath());
                }
            }
        }, "磁盘跑分失败");
        return 1;
    }

    /**
     * 启动跑分后台线程，统一处理异常捕获和日志输出。
     * 设置 UncaughtExceptionHandler 以确保 OOM 等 Error 也能被记录到日志。
     */
    private void startBenchThread(String name, ServerCommandSource src,
                                  Runnable task, String errorPrefix) {
        Thread t = new Thread(() -> {
            try {
                task.run();
            } catch (Throwable e) {
                try {
                    send(src, "§c" + errorPrefix + ": " + e.getMessage());
                } catch (Throwable ignored) {
                    // OOM 时 send 本身可能失败，用 System.err 兜底
                    System.err.println("[HWBench] " + errorPrefix + ": " + e);
                }
            }
        }, name);
        t.setUncaughtExceptionHandler((t1, e) -> {
            // 兜底：捕获 try-catch 未能处理的异常（如 OOM 在 catch 块中再次抛出）
            System.err.println("[HWBench] 线程未捕获异常: " + t1.getName() + " - " + e);
        });
        t.start();
    }

    private int runAll(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        // 在线程外部加载 BenchConfig 单例，避免线程安全问题
        BenchConfig config = BenchConfig.load(new File("."));
        send(src, "§e=== 运行全部跑分 ===");
        send(src, "§c注意：跑分期间服务器会卡顿，建议先 /hwbench lock");
        startBenchThread("HWBench-All", src, () -> {
            BenchmarkResult result = new BenchmarkResult();
            String hardwareInfo = "";

            // 硬件检测
            send(src, "§e正在检测硬件信息...");
            try {
                HardwareDetector detector = new HardwareDetector();
                detector.detectAll(result);
                hardwareInfo = detector.generateReport(result);
                for (String line : hardwareInfo.split("\n")) {
                    send(src, line);
                }
            } catch (Throwable e) {
                send(src, "§c硬件检测失败: " + e.getMessage());
            }
            sleepQuiet(500);

            // CPU跑分
            send(src, "§e开始 CPU 跑分...");
            try {
                CPUBenchmark cpuBench = new CPUBenchmark(
                        config.cpuDonutFrames, config.cpuComputeIterations, config.cpuMatrixSize,
                        config.cpuShowAnimation, config.cpuPrimeRange, config.cpuFloatIterations,
                        config.cpuTimeoutSeconds
                );
                BenchmarkResult.TestResult r = cpuBench.runAll();
                result.addTestResult("cpu", r);
                send(src, String.format("§aCPU跑分完成: 得分 %.2f, 耗时 %dms", r.getScore(), r.getDurationMs()));
            } catch (Throwable e) {
                send(src, "§cCPU跑分失败: " + e.getMessage());
            }
            sleepQuiet(500);

            // 内存跑分
            send(src, "§e开始内存跑分...");
            try {
                MemoryBenchmark memBench = new MemoryBenchmark(
                        config.memArraySizeMB, config.memIterations, config.memRandomAccessCount,
                        config.memTimeoutSeconds
                );
                BenchmarkResult.TestResult r = memBench.runAll();
                result.addTestResult("memory", r);
                send(src, String.format("§a内存跑分完成: 得分 %.2f, 耗时 %dms", r.getScore(), r.getDurationMs()));
            } catch (Throwable e) {
                send(src, "§c内存跑分失败: " + e.getMessage());
            }
            sleepQuiet(500);

            // 磁盘跑分
            send(src, "§e开始磁盘跑分...");
            try {
                DiskBenchmark diskBench = new DiskBenchmark(
                        config.diskFileSizeMB, config.diskBlockSizeKB, config.diskRandomIOCount, new File("."),
                        config.diskTimeoutSeconds
                );
                BenchmarkResult.TestResult r = diskBench.runAll();
                result.addTestResult("disk", r);
                send(src, String.format("§a磁盘跑分完成: 得分 %.2f, 耗时 %dms", r.getScore(), r.getDurationMs()));
            } catch (Throwable e) {
                send(src, "§c磁盘跑分失败: " + e.getMessage());
            }

            send(src, "§a=== 全部跑分完成 ===");

            // 生成综合报告并写入服务端 logs/hwbench/ 目录
            if (config.reportWriteToServerLogs) {
                ResultReporter reporter = new ResultReporter(true, "hwbench-reports", false);
                String report = reporter.generateReport(result, hardwareInfo);
                File logsFile = reporter.saveReportToServerLogs(result, report, new File("logs"));
                if (logsFile != null) {
                    send(src, "§a综合报告已写入服务端日志: " + logsFile.getPath());
                }
            }
        }, "跑分失败");
        return 1;
    }

    private static void sleepQuiet(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }

    private int runLibs(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        send(src, "§e检查并补全Linux运行库...");
        startBenchThread("HWBench-Libs", src, () -> {
            List<String> libs = Arrays.asList("lshw", "lm-sensors", "pciutils", "smartmontools");
            LibraryManager mgr = new LibraryManager(libs, false, "auto");
            String report = mgr.checkAndInstall();
            for (String line : report.split("\n")) {
                send(src, line);
            }
            send(src, "§a库检查完成");
        }, "库检查失败");
        return 1;
    }

    private int lockServer(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        if (serverLocked.compareAndSet(false, true)) {
            send(src, "§c服务器已锁定。新玩家将无法加入。");
            // 踢出现有玩家（跨版本：1.16.5 getMinecraftServer()，1.18+ getServer()）
            try {
                Object server = getServer(src);
                if (server != null) {
                    Object playerManager = invoke(server, "getPlayerManager");
                    if (playerManager != null) {
                        Object players = invoke(playerManager, "getPlayerList");
                        if (players instanceof Iterable) {
                            for (Object player : (Iterable<?>) players) {
                                Object handler = getField(player, "networkHandler");
                                if (handler != null) {
                                    Object text = makeText(lockMessage);
                                    if (text != null) {
                                        invoke(handler, "disconnect", text);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("[HWBench] 踢出玩家失败: " + e.getMessage());
            }
        } else {
            send(src, "§e服务器已经处于锁定状态。");
        }
        return 1;
    }

    /** 跨版本获取 MinecraftServer：1.18+ getServer()，1.16.5 getMinecraftServer() */
    private static Object getServer(Object src) {
        Object s = invoke(src, "getServer");
        if (s != null) return s;
        return invoke(src, "getMinecraftServer");
    }

    private static Object invoke(Object target, String methodName, Object... args) {
        try {
            for (java.lang.reflect.Method m : target.getClass().getMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == args.length) {
                    boolean ok = true;
                    for (int i = 0; i < args.length; i++) {
                        if (!m.getParameterTypes()[i].isInstance(args[i])) {
                            ok = false;
                            break;
                        }
                    }
                    if (ok) return m.invoke(target, args);
                }
            }
        } catch (Exception e) { /* ignore */ }
        return null;
    }

    private static Object getField(Object target, String fieldName) {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private int unlockServer(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        if (serverLocked.compareAndSet(true, false)) {
            send(src, "§a服务器已解锁。玩家可以正常加入。");
        } else {
            send(src, "§e服务器当前未锁定。");
        }
        return 1;
    }

    private void send(ServerCommandSource src, String message) {
        // 始终写入日志，确保异步跑分结果即使 RCON 断开也能被 test_runner 检测到。
        // Fabric 1.16.5 没有 SLF4J binding（NOP logger），LOGGER.info() 会被静默丢弃，
        // 因此额外写入 System.out（被 test_runner 的 console.log 捕获）。
        LOGGER.info("[HWBench] " + message);
        System.out.println("[HWBench] " + message);
        try {
            Object text = makeText(message);
            if (text == null) {
                return;
            }
            // 跨版本兼容：在 1.16.5 中 Fabric 运行时使用 intermediary 映射（方法名被重映射），
            // 因此不能用方法名反射查找。改为遍历所有方法，按参数类型匹配：
            //   - sendFeedback(Supplier<Text>, boolean)  -> 1.19+
            //   - sendFeedback(Text, boolean)            -> 1.16-1.18
            for (java.lang.reflect.Method m : ServerCommandSource.class.getMethods()) {
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 2 && params[1] == boolean.class) {
                    // Check if first param is Supplier or Text
                    if (params[0] == java.util.function.Supplier.class) {
                        // 1.19+ sendFeedback(Supplier, boolean)
                        try {
                            m.invoke(src, (java.util.function.Supplier<Object>) () -> text, false);
                            return;
                        } catch (Throwable ignored) { /* try next */ }
                    } else if (params[0].isAssignableFrom(text.getClass())) {
                        // 1.16-1.18 sendFeedback(Text, boolean)
                        try {
                            m.invoke(src, text, false);
                            return;
                        } catch (Throwable ignored) { /* try next */ }
                    }
                }
            }
        } catch (Throwable t) {
            // 日志已写入，忽略 sendFeedback 失败
        }
    }

    /**
     * 跨版本创建文本组件：1.19+ Text.literal / 1.14-1.18.2 new LiteralText
     *
     * Fabric 运行时使用 intermediary 映射，类名/方法名被重映射：
     *   - Text       → class_2568
     *   - LiteralText → class_2585
     *   - Text.literal(String) → class_2568.method_30107(String)
     *
     * Loom 只重映射源码中的直接引用（如 Text.class），不重映射字符串常量。
     * 因此反射时必须使用 intermediary 名（method_30107 / class_2585），
     * 这些名在所有 Fabric 版本（1.14–1.21.3）中保持稳定。
     *
     * 尝试顺序：
     *   1. method_30107（1.19+ 生产环境 intermediary 名）
     *   2. literal（1.19+ 开发环境 yarn 名）
     *   3. new class_2585(String)（1.14–1.18.2 LiteralText 构造器）
     */
    private Object makeText(String message) {
        // 使用已导入的 Text.class（Loom 在构建时重映射为 intermediary 类 class_2568）
        Class<?> textClass = net.minecraft.text.Text.class;
        // 1.19+: Text.literal(String) — 同时尝试 intermediary 名和 yarn 名
        for (String methodName : new String[]{"method_30107", "literal"}) {
            try {
                java.lang.reflect.Method m = textClass.getMethod(methodName, String.class);
                return m.invoke(null, message);
            } catch (Exception e) { /* try next */ }
        }
        // 1.14-1.18.2: new LiteralText(String) — intermediary 类名 class_2585（跨版本稳定）
        try {
            Class<?> cls = Class.forName("net.minecraft.class_2585");
            return cls.getConstructor(String.class).newInstance(message);
        } catch (Throwable e) { /* fall through */ }
        return null;
    }

    private static java.lang.reflect.Method findMethod(Class<?> cls, String name, Class<?>... paramTypes) {
        Class<?> c = cls;
        while (c != null) {
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
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
}

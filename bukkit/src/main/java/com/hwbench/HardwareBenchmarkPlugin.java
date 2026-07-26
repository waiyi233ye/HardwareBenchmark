package com.hwbench;

import com.hwbench.bukkit.BenchCommand;
import com.hwbench.bukkit.LoginListener;
import com.hwbench.bukkit.ServerController;
import com.hwbench.core.HardwareDetector;
import com.hwbench.core.LibraryManager;
import com.hwbench.core.ResultReporter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * HardwareBenchmark 主插件类
 * MC Java版服务端硬件检测与跑分插件
 * 兼容版本: 1.7.10 ~ 1.20.1
 */
public class HardwareBenchmarkPlugin extends JavaPlugin {

    private ServerController serverController;
    private HardwareDetector hardwareDetector;
    private LibraryManager libraryManager;
    private ResultReporter resultReporter;
    private FileConfiguration config;

    @Override
    public void onEnable() {
        // 保存默认配置
        saveDefaultConfig();
        config = getConfig();

        // 初始化硬件检测器（带容错：OSHI可能在Java 8环境加载失败）
        try {
            hardwareDetector = new HardwareDetector();
            getLogger().info("OSHI硬件检测库加载成功");
        } catch (Throwable e) {
            getLogger().warning("OSHI硬件检测库加载失败，将使用基础检测模式: " + e.getMessage());
            hardwareDetector = null;
        }

        // 初始化服务器控制器
        serverController = new ServerController(
                this,
                config.getString("server-control.kick-message", "&c[硬件跑分] 服务器正在进行硬件跑分测试，请稍后重新连接。"),
                config.getString("server-control.lock-message", "&c[硬件跑分] 服务器正在跑分中，暂时无法进入。"),
                config.getBoolean("server-control.auto-unlock", true),
                config.getInt("server-control.kick-delay", 3)
        );

        // 初始化库管理器
        libraryManager = new LibraryManager(
                config.getStringList("library-manager.required-libraries"),
                config.getBoolean("library-manager.auto-install", true),
                config.getString("library-manager.package-manager", "auto")
        );

        // 初始化结果报告器
        resultReporter = new ResultReporter(
                config.getBoolean("report.save-to-file", true),
                getDataFolder().getAbsolutePath() + File.separator +
                        config.getString("report.output-dir", "hwbench-reports"),
                config.getBoolean("report.verbose-console", true)
        );

        // 注册登录监听器（所有版本通用）
        getServer().getPluginManager().registerEvents(
                new LoginListener(serverController), this);

        // 尝试注册异步预登录监听器（仅1.8+，1.7.10自动跳过）
        registerAsyncPreLoginListener();

        // 注册命令
        BenchCommand command = new BenchCommand(this, serverController, hardwareDetector,
                libraryManager, resultReporter, config);
        getCommand("hwbench").setExecutor(command);
        getCommand("hwbench").setTabCompleter(command);

        getLogger().info("HardwareBenchmark 插件已启用！");
        getLogger().info("MC版本: " + getServer().getVersion()
                + " (解析: " + com.hwbench.bukkit.VersionCompat.getMinecraftVersion() + ")");
        getLogger().info("Java版本: " + System.getProperty("java.version")
                + " (MRJAR: " + com.hwbench.bukkit.MrjarProbe.level() + ")");
        getLogger().info("使用 /hwbench 查看帮助");

        // 在服务器启动时异步检查Linux库
        if (config.getBoolean("library-manager.enabled", true)) {
            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                String report = libraryManager.checkAndInstall();
                getLogger().info("\n" + report);
            });
        }
    }

    /**
     * 使用反射注册AsyncPreLoginListener（仅1.8+可用）
     * 1.7.10没有AsyncPlayerPreLoginEvent，跳过注册
     */
    private void registerAsyncPreLoginListener() {
        try {
            // 检查AsyncPlayerPreLoginEvent是否存在
            Class.forName("org.bukkit.event.player.AsyncPlayerPreLoginEvent");

            // 加载AsyncPreLoginListener类
            Class<?> listenerClass = Class.forName("com.hwbench.bukkit.AsyncPreLoginListener");
            java.lang.reflect.Constructor<?> ctor = listenerClass.getConstructor(ServerController.class);
            Object listener = ctor.newInstance(serverController);
            getServer().getPluginManager().registerEvents((org.bukkit.event.Listener) listener, this);
            getLogger().info("AsyncPreLoginListener已注册 (1.8+特性)");
        } catch (ClassNotFoundException e) {
            getLogger().info("MC < 1.8 检测到，使用PlayerLoginEvent拦截登录");
        } catch (Exception e) {
            getLogger().warning("AsyncPreLoginListener注册失败: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (serverController != null && serverController.isLocked()) {
            serverController.unlock();
        }
        getLogger().info("HardwareBenchmark 插件已禁用！");
    }

    public ServerController getServerController() {
        return serverController;
    }

    public HardwareDetector getHardwareDetector() {
        return hardwareDetector;
    }

    public ResultReporter getResultReporter() {
        return resultReporter;
    }

    public FileConfiguration getPluginConfig() {
        return config;
    }
}

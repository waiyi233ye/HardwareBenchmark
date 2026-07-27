package com.hwbench.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 服务器控制器
 * 在跑分期间锁定服务器：踢出所有玩家、阻止新玩家进入
 * 同时保持服务器控制台运行不关闭
 */
public class ServerController {

    private final Plugin plugin;
    private final AtomicBoolean locked = new AtomicBoolean(false);
    private final String kickMessage;
    private final String lockMessage;
    private final boolean autoUnlock;
    private final int kickDelay;

    public ServerController(Plugin plugin, String kickMessage, String lockMessage,
                            boolean autoUnlock, int kickDelay) {
        this.plugin = plugin;
        this.kickMessage = ChatColor.translateAlternateColorCodes('&', kickMessage);
        this.lockMessage = ChatColor.translateAlternateColorCodes('&', lockMessage);
        this.autoUnlock = autoUnlock;
        this.kickDelay = kickDelay;
    }

    /**
     * 锁定服务器
     * - 踢出所有玩家
     * - 阻止新玩家登录
     * - 服务器保持运行
     * @param callback 锁定完成后的回调
     */
    public void lock(final Runnable callback) {
        if (!locked.compareAndSet(false, true)) {
            callback.run();
            return;
        }

        plugin.getLogger().info("[HWBench] 正在锁定服务器...");

        // 提示在线玩家
        for (Player player : VersionCompat.getOnlinePlayers()) {
            player.sendMessage(ChatColor.YELLOW + "[硬件跑分] 服务器将在" + kickDelay + "秒后开始硬件跑分测试，届时所有玩家将被踢出。");
        }

        // 延迟踢出所有玩家
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player player : VersionCompat.getOnlinePlayers()) {
                VersionCompat.kickPlayer(player, kickMessage);
            }
            plugin.getLogger().info("[HWBench] 所有玩家已踢出，服务器已锁定");
            callback.run();
        }, kickDelay * 20L);
    }

    /**
     * 解锁服务器
     */
    public void unlock() {
        if (!locked.get()) return;

        locked.set(false);
        plugin.getLogger().info("[HWBench] 服务器已解锁，玩家可以重新进入");
    }

    /**
     * 服务器是否已锁定
     */
    public boolean isLocked() {
        return locked.get();
    }

    /**
     * 获取锁定消息（已转换颜色代码）
     */
    public String getLockMessage() {
        return lockMessage;
    }

    /**
     * 是否自动解锁
     */
    public boolean isAutoUnlock() {
        return autoUnlock;
    }
}

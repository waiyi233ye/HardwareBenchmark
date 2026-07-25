package com.hwbench.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 版本兼容工具类
 * 处理不同MC版本之间的API差异（1.7.10 ~ 1.20.1）
 */
public class VersionCompat {

    private VersionCompat() {}

    /**
     * 安全获取在线玩家列表（兼容1.7.10返回Player[]和1.8+返回Collection）
     */
    @SuppressWarnings("unchecked")
    public static List<Player> getOnlinePlayers() {
        try {
            Collection<? extends Player> players = (Collection<? extends Player>) Bukkit.class
                    .getMethod("getOnlinePlayers").invoke(null);
            return new ArrayList<>(players);
        } catch (ClassCastException e) {
            // 1.7.10可能返回Player[]
            try {
                Object result = Bukkit.class.getMethod("getOnlinePlayers").invoke(null);
                if (result instanceof Player[]) {
                    Player[] arr = (Player[]) result;
                    List<Player> list = new ArrayList<>(arr.length);
                    for (Player p : arr) {
                        list.add(p);
                    }
                    return list;
                }
            } catch (Exception ex) {
                // ignore
            }
            return new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 检查是否支持AsyncPlayerPreLoginEvent (1.8+)
     */
    public static boolean supportsAsyncPreLogin() {
        try {
            Class.forName("org.bukkit.event.player.AsyncPlayerPreLoginEvent");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 踢出玩家（兼容所有版本）
     */
    public static void kickPlayer(Player player, String message) {
        player.kickPlayer(message);
    }

    /**
     * 发送消息（兼容所有版本）
     */
    public static void sendMessage(org.bukkit.command.CommandSender sender, String message) {
        sender.sendMessage(message);
    }
}

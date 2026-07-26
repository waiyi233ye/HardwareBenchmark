package com.hwbench.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 版本兼容工具类
 * 处理不同MC版本之间的API差异（1.7.10 ~ 1.21.3）
 */
public class VersionCompat {

    /** 匹配 Bukkit.getVersion() 中的 x.y[.z] 版本号，如 "1.7.10-R0.1-SNAPSHOT" → "1.7.10"。 */
    private static final Pattern MC_VERSION_PATTERN = Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?)");

    private VersionCompat() {}

    /**
     * 解析当前服务端的 Minecraft 版本号。
     * 兼容多种 Bukkit.getVersion() 格式：
     *   - "1.7.10-R0.1-SNAPSHOT"
     *   - "1.20.1-R0.1-SNAPSHOT"
     *   - "1.21.3-R0.1-SNAPSHOT"
     *   - "git-Spigot-xxxx (MC: 1.20.1)"
     * @return 形如 "1.7.10"/"1.20.1"/"1.21.3" 的版本字符串；解析失败时返回原始 getVersion() 结果
     */
    public static String getMinecraftVersion() {
        String raw = Bukkit.getVersion();
        Matcher m = MC_VERSION_PATTERN.matcher(raw);
        if (m.find()) {
            return m.group(1);
        }
        return raw;
    }

    /**
     * 判断当前服务端 MC 版本是否 &gt;= 指定目标版本。
     * @param target 形如 "1.7.10"/"1.20.1"/"1.21.3"
     * @return 当前版本 &gt;= target 时返回 true
     */
    public static boolean isAtLeast(String target) {
        return compareVersions(getMinecraftVersion(), target) >= 0;
    }

    /**
     * 语义化版本号比较（仅取前三段，缺失补 0）。
     * @return 负数表示 a&lt;b，0 表示相等，正数表示 a&gt;b
     */
    private static int compareVersions(String a, String b) {
        int[] va = parseVersionParts(a);
        int[] vb = parseVersionParts(b);
        for (int i = 0; i < 3; i++) {
            if (va[i] != vb[i]) {
                return Integer.compare(va[i], vb[i]);
            }
        }
        return 0;
    }

    private static int[] parseVersionParts(String v) {
        int[] parts = new int[]{0, 0, 0};
        if (v == null) {
            return parts;
        }
        String[] segs = v.split("\\.");
        for (int i = 0; i < Math.min(segs.length, 3); i++) {
            try {
                parts[i] = Integer.parseInt(segs[i]);
            } catch (NumberFormatException ignored) {
                // 非数字段视为 0
            }
        }
        return parts;
    }

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

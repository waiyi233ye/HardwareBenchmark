package com.hwbench.bukkit;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

/**
 * 异步预登录监听器（仅1.8+）
 * 在PreLogin阶段就拒绝连接，比PlayerLoginEvent更早拦截
 * 
 * 注意: 此类在1.7.10编译时会被排除（AsyncPlayerPreLoginEvent不存在）
 */
public class AsyncPreLoginListener implements Listener {

    private final ServerController serverController;

    public AsyncPreLoginListener(ServerController serverController) {
        this.serverController = serverController;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (serverController.isLocked()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, serverController.getLockMessage());
        }
    }
}

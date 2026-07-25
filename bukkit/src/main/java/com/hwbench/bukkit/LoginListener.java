package com.hwbench.bukkit;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

/**
 * 登录监听器（通用版，所有MC版本兼容）
 * 在服务器锁定期间阻止新玩家进入
 * 
 * 注意: AsyncPlayerPreLoginEvent的拦截由AsyncPreLoginListener处理（1.8+）
 */
public class LoginListener implements Listener {

    private final ServerController serverController;

    public LoginListener(ServerController serverController) {
        this.serverController = serverController;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLogin(PlayerLoginEvent event) {
        if (serverController.isLocked()) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, serverController.getLockMessage());
        }
    }
}

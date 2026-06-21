package com.freshwater.report.companion;

import com.freshwater.report.common.Protocol;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

/**
 * 监听玩家切换子服，主动把真实子服位置上报给上游 Velocity，
 * 使举报记录中的服务器字段保持准确。
 */
public final class LocationTracker implements Listener {

    private final FreshwaterReportCompanion plugin;

    public LocationTracker(FreshwaterReportCompanion plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onServerConnected(ServerConnectedEvent event) {
        if (event.getServer() == null) {
            return;
        }
        String uuid = event.getPlayer().getUniqueId().toString();
        String server = event.getServer().getInfo().getName();
        CompanionMessageListener.sendUpstream(event.getPlayer(),
                Protocol.encode(Protocol.LOCATION_UPDATE, uuid, server));

        // 借机向主插件请求最新配置（带冷却，避免频繁请求）
        plugin.getConfigSync().requestIfStale(event.getPlayer());
    }
}

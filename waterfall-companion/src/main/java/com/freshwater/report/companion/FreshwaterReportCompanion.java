package com.freshwater.report.companion;

import com.freshwater.report.common.Channels;
import net.md_5.bungee.api.plugin.Plugin;

/**
 * Waterfall(BungeeCord) 伴生插件：在 Velocity 前 + Waterfall 后的嵌套拓扑下，
 * 实现子服精确传送与玩家真实子服上报。
 */
public final class FreshwaterReportCompanion extends Plugin {

    private ConfigSyncManager configSync;

    @Override
    public void onEnable() {
        this.configSync = new ConfigSyncManager(this);
        getProxy().registerChannel(Channels.REPORT);
        getProxy().getPluginManager().registerListener(this, new CompanionMessageListener(this));
        getProxy().getPluginManager().registerListener(this, new LocationTracker(this));
        getLogger().info("FreshwaterReportV 伴生插件已启用，通道: " + Channels.REPORT);
    }

    public ConfigSyncManager getConfigSync() {
        return configSync;
    }

    @Override
    public void onDisable() {
        getProxy().unregisterChannel(Channels.REPORT);
        getLogger().info("FreshwaterReportV 伴生插件已停用。");
    }
}

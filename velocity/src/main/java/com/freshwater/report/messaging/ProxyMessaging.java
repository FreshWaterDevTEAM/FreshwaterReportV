package com.freshwater.report.messaging;

import com.freshwater.report.common.Channels;
import com.freshwater.report.common.Protocol;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理 {@code freshwater:report} 插件消息通道：
 * 接收 Waterfall 伴生插件上报的真实子服位置，并向其下发查询/传送请求。
 */
public final class ProxyMessaging {

    public static final MinecraftChannelIdentifier IDENTIFIER =
            MinecraftChannelIdentifier.create(Channels.REPORT_NAMESPACE, Channels.REPORT_NAME);

    private final ProxyServer proxy;
    private final Logger logger;
    private final ConcurrentHashMap<UUID, String> realServerCache = new ConcurrentHashMap<>();

    public ProxyMessaging(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    public void register() {
        proxy.getChannelRegistrar().register(IDENTIFIER);
    }

    public void unregister() {
        proxy.getChannelRegistrar().unregister(IDENTIFIER);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!IDENTIFIER.equals(event.getIdentifier())) {
            return;
        }
        // 本通道的数据不应转发给客户端
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        if (!(event.getSource() instanceof ServerConnection)) {
            return;
        }
        try {
            Protocol.Message message = Protocol.decode(event.getData());
            switch (message.type()) {
                case Protocol.LOCATION, Protocol.LOCATION_UPDATE -> {
                    UUID uuid = parseUuid(message.arg(0));
                    String server = message.arg(1);
                    if (uuid != null) {
                        if (server == null || server.isBlank()) {
                            realServerCache.remove(uuid);
                        } else {
                            realServerCache.put(uuid, server);
                        }
                    }
                }
                default -> {
                    // 其它类型为 Velocity -> Waterfall 方向，代理端忽略
                }
            }
        } catch (Exception e) {
            logger.warn("处理跨代理插件消息失败", e);
        }
    }

    /** 向被举报者所在的 Waterfall 查询其真实子服。 */
    public boolean sendQueryLocation(Player target) {
        return sendVia(target, Protocol.encode(Protocol.QUERY_LOCATION, target.getUniqueId().toString()));
    }

    /** 让处理者连接的 Waterfall 把其传送到被举报者所在子服。 */
    public boolean sendTpToPlayer(Player staff, UUID targetUuid) {
        return sendVia(staff, Protocol.encode(Protocol.TP_TO_PLAYER,
                staff.getUniqueId().toString(), targetUuid.toString()));
    }

    private boolean sendVia(Player player, byte[] data) {
        Optional<ServerConnection> connection = player.getCurrentServer();
        if (connection.isEmpty()) {
            return false;
        }
        return connection.get().sendPluginMessage(IDENTIFIER, data);
    }

    public Optional<String> getRealServer(UUID uuid) {
        return Optional.ofNullable(realServerCache.get(uuid));
    }

    public void clear(UUID uuid) {
        realServerCache.remove(uuid);
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

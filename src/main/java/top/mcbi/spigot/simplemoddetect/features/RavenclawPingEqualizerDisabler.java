package top.mcbi.spigot.simplemoddetect.features;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.DiscardedPayload;
import net.minecraft.resources.Identifier;
import org.bukkit.entity.Player;
import top.mcbi.spigot.simplemoddetect.SimpleModDetect;
import top.mcbi.spigot.simplemoddetect.nms.NMSVersionAdapter;
import top.mcbi.spigot.simplemoddetect.nms.VersionAdapterManager;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class RavenclawPingEqualizerDisabler {
    private static final String CONTROL_CHANNEL = "ravenclawspingequalizer:control";
    private static final String STATE_QUERY_CHANNEL = "ravenclawspingequalizer:state_query";
    private static final String STATE_RESPONSE_CHANNEL = "ravenclawspingequalizer:state_response";

    private final SimpleModDetect plugin;
    private final NMSVersionAdapter versionAdapter;
    private final AtomicInteger requestIdCounter = new AtomicInteger(1);

    public RavenclawPingEqualizerDisabler(SimpleModDetect plugin) {
        this.plugin = plugin;
        this.versionAdapter = VersionAdapterManager.getAdapter();
    }

    public void handleIncomingPacket(Player player, Object packet) {
        if (!versionAdapter.isCustomPayloadPacket(packet)) {
            return;
        }

        Object payloadObj = versionAdapter.getPacketPayload(packet);
        if (!(payloadObj instanceof DiscardedPayload discardedPayload)) {
            return;
        }

        String channelName = versionAdapter.getChannelName(discardedPayload);
        if (!STATE_RESPONSE_CHANNEL.equals(channelName)) {
            return;
        }

        byte[] rawData = discardedPayload.data();
        try {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(rawData));
            int requestId = buf.readVarInt();
            boolean serverEnabled = buf.readBoolean();
            String mode = buf.readUtf();
            int currentDelayMs = buf.readVarInt();
            int basePingMs = buf.readVarInt();
            int totalPingMs = buf.readVarInt();
            buf.release();

            if (plugin.getConfigManager().isDebugMode()) {
                plugin.getLogger().info("[RavenclawPingEqualizerDisabler] 玩家 " + player.getName()
                        + " state_response: requestId=" + requestId
                        + ", serverEnabled=" + serverEnabled
                        + ", mode=" + mode
                        + ", currentDelayMs=" + currentDelayMs
                        + ", basePingMs=" + basePingMs
                        + ", totalPingMs=" + totalPingMs);
            }

            if (serverEnabled && plugin.getConfigManager().isDisableRavenclawPingEqualizer()) {
                plugin.getLogger().warning("[RavenclawPingEqualizerDisabler] 玩家 " + player.getName()
                        + " 仍报告 Ping Equalizer 未被禁用，将重新发送禁用包。");
                sendDisablePacket(player, false);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[RavenclawPingEqualizerDisabler] 解析 state_response 失败", e);
        }
    }

    public void sendDisablePacket(Player player) {
        sendDisablePacket(player, true);
    }

    public void sendDisablePacket(Player player, boolean queryState) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Channel channel = versionAdapter.getPlayerChannel(player);
                if (channel == null) {
                    plugin.getLogger().warning("[RavenclawPingEqualizerDisabler] 无法获取玩家 "
                            + player.getName() + " 的网络通道来发送禁用包。");
                    return;
                }

                FriendlyByteBuf controlBuf = new FriendlyByteBuf(Unpooled.buffer());
                controlBuf.writeBoolean(false);
                byte[] controlData = new byte[controlBuf.readableBytes()];
                controlBuf.readBytes(controlData);
                controlBuf.release();

                CustomPacketPayload controlPayload = createCustomPayload(CONTROL_CHANNEL, controlData);
                if (controlPayload == null) {
                    plugin.getLogger().warning("[RavenclawPingEqualizerDisabler] 无法为通道 "
                            + CONTROL_CHANNEL + " 创建载荷对象。");
                    return;
                }

                channel.writeAndFlush(new ClientboundCustomPayloadPacket(controlPayload)).addListener(future -> {
                    if (future.isSuccess()) {
                        if (plugin.getConfigManager().isDebugMode()) {
                            plugin.getLogger().info("[RavenclawPingEqualizerDisabler] 已向玩家 "
                                    + player.getName() + " 发送 Ping Equalizer 禁用请求。");
                        }
                        if (queryState) {
                            sendStateQuery(player, channel);
                        }
                    } else {
                        plugin.getLogger().warning("[RavenclawPingEqualizerDisabler] 向玩家 "
                                + player.getName() + " 发送禁用请求失败: " + future.cause().getMessage());
                    }
                });
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[RavenclawPingEqualizerDisabler] 发送禁用数据包时出错", e);
            }
        });
    }

    private void sendStateQuery(Player player, Channel channel) {
        try {
            int requestId = requestIdCounter.getAndIncrement();
            FriendlyByteBuf queryBuf = new FriendlyByteBuf(Unpooled.buffer());
            queryBuf.writeVarInt(requestId);
            byte[] queryData = new byte[queryBuf.readableBytes()];
            queryBuf.readBytes(queryData);
            queryBuf.release();

            CustomPacketPayload queryPayload = createCustomPayload(STATE_QUERY_CHANNEL, queryData);
            if (queryPayload == null) {
                return;
            }

            channel.writeAndFlush(new ClientboundCustomPayloadPacket(queryPayload)).addListener(future -> {
                if (future.isSuccess()) {
                    if (plugin.getConfigManager().isDebugMode()) {
                        plugin.getLogger().info("[RavenclawPingEqualizerDisabler] 已向玩家 "
                                + player.getName() + " 发送 state_query (requestId=" + requestId + ")");
                    }
                } else if (plugin.getConfigManager().isDebugMode()) {
                    plugin.getLogger().warning("[RavenclawPingEqualizerDisabler] 发送 state_query 失败: "
                            + future.cause().getMessage());
                }
            });
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[RavenclawPingEqualizerDisabler] 发送 state_query 时出错", e);
        }
    }

    private CustomPacketPayload createCustomPayload(String channel, byte[] data) {
        try {
            Identifier id = Identifier.parse(channel);
            return new DiscardedPayload(id, data);
        } catch (Exception e) {
            plugin.getLogger().warning("[RavenclawPingEqualizerDisabler] 无法通过 DiscardedPayload 创建载荷: " + e);
            return null;
        }
    }
}

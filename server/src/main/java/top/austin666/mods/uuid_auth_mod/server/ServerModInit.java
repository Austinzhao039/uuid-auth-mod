package top.austin666.mods.uuid_auth_mod.server;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ServerModInit implements ModInitializer {

    // 登录阶段通道
    private static final Identifier CHALLENGE_PACKET = new Identifier("uuid_auth_mod", "challenge");
    private static final Identifier RESPONSE_PACKET  = new Identifier("uuid_auth_mod", "response");

    // 游戏内二次验证通道
    private static final Identifier CHALLENGE_INGAME = new Identifier("uuid_auth_mod", "challenge_ingame");
    private static final Identifier RESPONSE_INGAME  = new Identifier("uuid_auth_mod", "response_ingame");

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor();

    // 记录等待游戏内验证的玩家（player -> originalChallenge）
    private static final Map<ServerPlayerEntity, String> PENDING_INGAME = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        // ================= 登录阶段验证 =================
        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) -> {
            byte[] challengeBytes = new byte[32];
            RANDOM.nextBytes(challengeBytes);
            String challengeStr = HexFormat.of().formatHex(challengeBytes);

            var buf = PacketByteBufs.create();
            buf.writeString(challengeStr);
            sender.sendPacket(CHALLENGE_PACKET, buf);
        });

        ServerLoginNetworking.registerGlobalReceiver(RESPONSE_PACKET, (server, handler, understood, buf, synchronizer, responseSender) -> {
            if (!understood) {
                handler.disconnect(Text.literal("未加载正确模组"));
                return;
            }
            String challenge = buf.readString(32767);
            String clientResponse = buf.readString(32767);

            boolean allowed = false;
            for (String expectedUUID : ServerConfig.getInstance().serverUUIDs) {
                if (sha256(challenge + expectedUUID).equals(clientResponse)) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                handler.disconnect(Text.literal("未加载正确模组"));
            } else {
                System.out.println("[UUID Auth Server] 登录阶段验证通过：" + handler.getConnectionInfo());
            }
        });

        // ================= 游戏内二次验证 =================
        // 玩家进入世界后触发
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();

            byte[] challengeBytes = new byte[32];
            RANDOM.nextBytes(challengeBytes);
            String challenge = HexFormat.of().formatHex(challengeBytes);

            PENDING_INGAME.put(player, challenge);

            var buf = PacketByteBufs.create();
            buf.writeString(challenge);
            sender.sendPacket(CHALLENGE_INGAME, buf);

            // 5 秒后超时检查
            SCHEDULER.schedule(() -> {
                if (PENDING_INGAME.containsKey(player)) {
                    // 未收到有效应答，踢出
                    player.networkHandler.disconnect(
                        Text.literal("安全模块存在问题，请确认游戏文件没有修改过或者开了挂。")
                    );
                }
            }, 5, TimeUnit.SECONDS);
        });

        // 接收客户端游戏内应答
        ServerPlayNetworking.registerGlobalReceiver(RESPONSE_INGAME, (server, player, handler, buf, responseSender) -> {
            if (!PENDING_INGAME.containsKey(player)) {
                return; // 已经验证过或超时被踢
            }

            String originalChallenge = PENDING_INGAME.get(player);
            String receivedChallenge = buf.readString(32767);
            String clientResponse = buf.readString(32767);

            // 挑战码必须一致
            if (!originalChallenge.equals(receivedChallenge)) {
                player.networkHandler.disconnect(
                    Text.literal("安全模块存在问题，请确认游戏文件没有修改过或者开了挂。")
                );
                PENDING_INGAME.remove(player);
                return;
            }

            boolean allowed = false;
            for (String expectedUUID : ServerConfig.getInstance().serverUUIDs) {
                if (sha256(originalChallenge + expectedUUID).equals(clientResponse)) {
                    allowed = true;
                    break;
                }
            }

            if (!allowed) {
                player.networkHandler.disconnect(
                    Text.literal("安全模块存在问题，请确认游戏文件没有修改过或者开了挂。")
                );
            } else {
                System.out.println("[UUID Auth Server] 游戏内二次验证通过：" + player.getName().getString());
                PENDING_INGAME.remove(player);
            }
        });

        System.out.println("[UUID Auth Server] 挑战-应答验证机制已启动（含游戏内二次验证）。");
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("[UUID Auth Server] SHA-256 计算失败", e);
        }
    }
}
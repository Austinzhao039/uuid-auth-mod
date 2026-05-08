package top.austin666.mods.uuid_auth_mod.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;

public class ClientModInit implements ModInitializer, ClientModInitializer {

    // 登录阶段的通道
    private static final Identifier CHALLENGE_PACKET = new Identifier("uuid_auth_mod", "challenge");
    private static final Identifier RESPONSE_PACKET  = new Identifier("uuid_auth_mod", "response");

    // 游戏内的二次验证通道
    private static final Identifier CHALLENGE_INGAME = new Identifier("uuid_auth_mod", "challenge_ingame");
    private static final Identifier RESPONSE_INGAME  = new Identifier("uuid_auth_mod", "response_ingame");

    @Override
    public void onInitialize() {
    }

    @Override
    public void onInitializeClient() {
        System.out.println("[UUID Auth Client] 挑战-应答模式已就绪。");

        // ----- 登录阶段验证 -----
        ClientLoginNetworking.registerGlobalReceiver(CHALLENGE_PACKET, (client, handler, buf, listenerAdder) -> {
            String challenge = buf.readString(32767);
            String myUUID = ClientConfig.getInstance().clientUUID;
            String response = sha256(challenge + myUUID);

            PacketByteBuf responseBuf = PacketByteBufs.create();
            responseBuf.writeString(challenge);
            responseBuf.writeString(response);
            return CompletableFuture.completedFuture(responseBuf);
        });

        // ----- 游戏内二次验证 -----
        ClientPlayNetworking.registerGlobalReceiver(CHALLENGE_INGAME, (client, handler, buf, responseSender) -> {
            String challenge = buf.readString(32767);
            String myUUID = ClientConfig.getInstance().clientUUID;
            String response = sha256(challenge + myUUID);

            PacketByteBuf responseBuf = PacketByteBufs.create();
            responseBuf.writeString(challenge);
            responseBuf.writeString(response);
            responseSender.sendPacket(RESPONSE_INGAME, responseBuf);
        });
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("[UUID Auth Client] SHA-256 计算失败", e);
        }
    }
}
package mc.slidingplatforms;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import io.netty.buffer.Unpooled;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Compatibility wrapper: old Identifier+PacketByteBuf API → 1.21.1 CustomPayload API.
 *
 * ALL channels MUST be pre-registered via Net.register(id) during onInitialize()
 * BEFORE the world starts (PayloadTypeRegistry is frozen after init).
 * The codec length-prefixes the payload bytes so the decoder consumes exactly
 * the right number of bytes and leaves no "extra bytes" in the packet stream.
 */
public final class Net {

    private Net() {}

    private static final Map<String, CustomPayload.Id<BufPayload>> IDS = new HashMap<>();

    /** Call once per channel in onInitialize / onInitializeClient. */
    public static CustomPayload.Id<BufPayload> register(Identifier channel) {
        CustomPayload.Id<BufPayload> pid = new CustomPayload.Id<>(channel);
        IDS.put(channel.toString(), pid);

        // Codec: length-prefixed raw bytes so decoder reads exactly len bytes
        PacketCodec<RegistryByteBuf, BufPayload> codec = PacketCodec.of(
                (payload, buf) -> {
                    byte[] bytes = new byte[payload.buf().readableBytes()];
                    payload.buf().getBytes(payload.buf().readerIndex(), bytes);
                    buf.writeVarInt(bytes.length);
                    buf.writeBytes(bytes);
                },
                buf -> {
                    int len = buf.readVarInt();
                    byte[] bytes = new byte[len];
                    buf.readBytes(bytes);
                    return new BufPayload(IDS.get(channel.toString()),
                            new PacketByteBuf(Unpooled.wrappedBuffer(bytes)));
                }
        );

        PayloadTypeRegistry.playC2S().register(pid, codec);
        PayloadTypeRegistry.playS2C().register(pid, codec);
        return pid;
    }

    public static CustomPayload.Id<BufPayload> id(Identifier channel) {
        CustomPayload.Id<BufPayload> pid = IDS.get(channel.toString());
        if (pid == null) throw new IllegalStateException(
                "Channel not pre-registered: " + channel + ". Call Net.register() in onInitialize().");
        return pid;
    }

    // ── server → client ───────────────────────────────────────────────────────
    public static void send(ServerPlayerEntity player, Identifier channel, PacketByteBuf buf) {
        ServerPlayNetworking.send(player, new BufPayload(id(channel), buf));
    }

    // ── server receive ────────────────────────────────────────────────────────
    public static void registerServerReceiver(Identifier channel,
            BiConsumer<ServerPlayerEntity, PacketByteBuf> handler) {
        ServerPlayNetworking.registerGlobalReceiver(id(channel),
                (payload, ctx) -> {
                    PacketByteBuf buf = payload.buf();
                    ctx.server().execute(() -> handler.accept(ctx.player(), buf));
                });
    }

    // ── payload record ────────────────────────────────────────────────────────
    public record BufPayload(CustomPayload.Id<BufPayload> id, PacketByteBuf buf)
            implements CustomPayload {
        @Override public CustomPayload.Id<BufPayload> getId() { return id; }
    }
}

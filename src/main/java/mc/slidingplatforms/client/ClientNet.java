package mc.slidingplatforms.client;

import mc.slidingplatforms.Net;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.function.Consumer;

/**
 * Client-side networking shim for 1.21.1.
 * All channels must have been pre-registered via Net.register() during server init.
 */
public final class ClientNet {

    private ClientNet() {}

    /** Send buf from client → server. */
    public static void send(Identifier channel, PacketByteBuf buf) {
        ClientPlayNetworking.send(new Net.BufPayload(Net.id(channel), buf));
    }

    /** Register handler for server → client packets. */
    public static void registerClientReceiver(Identifier channel, Consumer<PacketByteBuf> handler) {
        ClientPlayNetworking.registerGlobalReceiver(Net.id(channel),
                (payload, ctx) -> {
                    PacketByteBuf buf = payload.buf();
                    ctx.client().execute(() -> handler.accept(buf));
                });
    }
}

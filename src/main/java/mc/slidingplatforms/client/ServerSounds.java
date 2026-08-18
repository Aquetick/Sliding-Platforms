package mc.slidingplatforms.client;

import mc.slidingplatforms.SlidingPlatforms;
import mc.slidingplatforms.SoundFileUtil;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerSounds {

    private static final Map<String, String> known = new HashMap<>();

    private static final Set<String> uploading = ConcurrentHashMap.newKeySet();

    private static final Map<String, String> pendingRenames = new LinkedHashMap<>();

    private static boolean gotList;

    private static volatile boolean dirty;

    private ServerSounds() {}

    public static void onJoin(net.minecraft.client.network.ClientPlayNetworkHandler handler) {
        reset();
        MinecraftClient client = MinecraftClient.getInstance();

        if (!(handler.getConnection().getAddress() instanceof InetSocketAddress isa)) return;
        String host = isa.getHostString();
        if (host == null || host.isBlank()) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(host, 255);
        ClientPlayNetworking.send(SlidingPlatforms.SOUND_HELLO, buf);
    }

    public static void onDisconnect() {
        reset();
    }

    private static void reset() {
        known.clear();
        uploading.clear();
        synchronized (pendingRenames) { pendingRenames.clear(); }
        gotList = false;
        dirty = false;
    }

    public static void onList(PacketByteBuf buf) {
        int n = buf.readVarInt();
        Map<String, String> fresh = new HashMap<>();
        for (int i = 0; i < n; i++) {
            String base = buf.readString(80);
            String h = buf.readString(8);
            fresh.put(base, h);
        }
        MinecraftClient.getInstance().execute(() -> {
            known.clear();
            known.putAll(fresh);
            for (String base : fresh.keySet()) uploading.remove(base);
            gotList = true;
            dirty = true;
        });
    }

    public static void onAck(PacketByteBuf buf) {
        String orig = buf.readString(80);
        String finalBase = buf.readString(80);
        boolean ok = buf.readBoolean();
        MinecraftClient.getInstance().execute(() -> {
            uploading.remove(orig);
            if (ok && !finalBase.isEmpty() && !finalBase.equals(orig)) {
                synchronized (pendingRenames) { pendingRenames.put(orig, finalBase); }
            }
            dirty = true;
        });
    }

    public static boolean serverSilent() {
        return !gotList;
    }

    public static boolean isLocalOnly(String base) {
        return gotList && !known.containsKey(base);
    }

    public static boolean isUploading(String base) {
        return uploading.contains(base);
    }

    public static boolean consumeDirty() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    public static List<Map.Entry<String, String>> drainRenames() {
        List<Map.Entry<String, String>> out = new ArrayList<>();
        synchronized (pendingRenames) {
            out.addAll(new ArrayList<>(pendingRenames.entrySet()));
            pendingRenames.clear();
        }
        return out;
    }

    public static void enqueueUpload(String base) {
        if (!isLocalOnly(base) || uploading.contains(base)) return;
        Path f = UserSoundLibrary.fileFor(base);
        if (f == null) return;
        uploading.add(base);
        dirty = true;
        new Thread(() -> {
            try {
                byte[] data = Files.readAllBytes(f);
                String sha = SoundFileUtil.sha1Hex(data);
                MinecraftClient.getInstance().execute(() -> {
                    PacketByteBuf begin = PacketByteBufs.create();
                    begin.writeString(base, 80);
                    begin.writeInt(data.length);
                    begin.writeString(sha, 40);
                    ClientPlayNetworking.send(SlidingPlatforms.SOUND_UP_BEGIN, begin);
                    for (int off = 0; off < data.length; off += 8000) {
                        int len = Math.min(8000, data.length - off);
                        PacketByteBuf chunk = PacketByteBufs.create();
                        chunk.writeByteArray(java.util.Arrays.copyOfRange(data, off, off + len));
                        ClientPlayNetworking.send(SlidingPlatforms.SOUND_UP_CHUNK, chunk);
                    }
                });
            } catch (IOException e) {
                uploading.remove(base);
                dirty = true;
            }
        }, "sd-sound-upload").start();
    }
}

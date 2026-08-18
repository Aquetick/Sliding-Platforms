package mc.slidingplatforms;

import com.sun.net.httpserver.HttpServer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ResourcePackSendS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class SoundPackService {

    private static final Logger LOG = LoggerFactory.getLogger("Sliding Platforms/SoundPack");

    static final String ZIP_NAME = "_server_pack.zip";

    private static final int MAX_FILE = 1 << 20;

    private static final int MAX_TOTAL = 24 << 20;

    private static final int CHUNK = 8000;

    private static final int MIRROR_CHUNK = 16_000;

    private static final int MIRROR_PER_TICK = 24;

    private static final int POLL_TICKS = 600;

    private static final int UPLOADS_PER_WINDOW = 24;
    private static final long WINDOW_MS = 10 * 60_000L;

    private static MinecraftServer serverRef;
    private static HttpServer http;
    private static ExecutorService httpExec;

    private static final Map<UUID, String> hellos = new HashMap<>();

    private static Map<String, String> lastList = Map.of();

    private static volatile byte[] packBytes;
    private static volatile String packSha1 = "";

    private static Path packFile;
    private static long folderStamp = Long.MIN_VALUE;
    private static int pollCounter = 0;

    private static class UploadSession {
        String base; byte[] data; int off; String sha;
        long windowStart; int windowCount;
    }
    private static final Map<UUID, UploadSession> uploads = new HashMap<>();

    private static class MirrorSession { byte[] data; int off; }
    private static final Map<UUID, MirrorSession> mirrors = new HashMap<>();

    private static final Map<UUID, String> mirrorPushed = new HashMap<>();

    static MinecraftServer server() { return serverRef; }

    private SoundPackService() {}

    private static Path soundsDir() {
        return FabricLoader.getInstance().getGameDir().resolve(SoundFileUtil.USER_DIR_NAME);
    }

    private static boolean enabled() {
        return SlidingPlatformsConfig.VALUES.soundPack && SlidingPlatformsConfig.VALUES.soundPackPort > 0;
    }

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            serverRef = s;
            packFile = soundsDir().resolve(ZIP_NAME);
            try { Files.createDirectories(soundsDir()); } catch (IOException ignored) {}
            folderStamp = Long.MIN_VALUE;
            rebuildIfChanged(true);
            startHttp();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            stopHttp();
            serverRef = null;
            hellos.clear();
            uploads.clear();
            mirrors.clear();
            mirrorPushed.clear();
            lastList = Map.of();
            packBytes = null;
            packSha1 = "";
        });
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (++pollCounter >= POLL_TICKS) {
                pollCounter = 0;
                rebuildIfChanged(false);
            }
            pumpPackMirrors();
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) -> {

            if (!SlidingPlatformsConfig.VALUES.soundPackHost.trim().isEmpty()) {
                offer(handler.player);
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, srv) -> {
            hellos.remove(handler.player.getUuid());
            uploads.remove(handler.player.getUuid());
            mirrors.remove(handler.player.getUuid());
            mirrorPushed.remove(handler.player.getUuid());
            ClientLanguages.remove(handler.player.getUuid());
        });
    }

    public static void reloadHttp() {
        MinecraftServer s = serverRef;
        if (s != null) s.execute(SoundPackService::startHttp);
    }

    private static void startHttp() {
        stopHttp();
        if (!enabled()) {
            LOG.info("Автораспространение звуков выключено (soundPack/soundPackPort в config/slidingplatforms.json)");
            return;
        }
        int port = SlidingPlatformsConfig.VALUES.soundPackPort;
        try {
            http = HttpServer.create(new InetSocketAddress(port), 4);
        } catch (IOException e) {
            LOG.warn("Не смогли открыть порт {} для раздачи звукового пака: {} "
                    + "(звуки останутся локальными; займите порт в soundPackPort)", port, e.toString());
            http = null;
            return;
        }
        httpExec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "slidingplatforms-soundpack-http");
            t.setDaemon(true);
            return t;
        });
        http.setExecutor(httpExec);
        http.createContext("/" + ZIP_NAME, exchange -> {
            try (exchange) {
                byte[] data = packBytes;
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                if (data == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, data.length);
                exchange.getResponseBody().write(data);
            }
        });
        http.start();
        LOG.info("Звуковой пак Sliding Platforms раздаётся на порту {} (путь /{})", port, ZIP_NAME);
    }

    private static void stopHttp() {
        if (http != null) {
            http.stop(0);
            http = null;
        }
        if (httpExec != null) {
            httpExec.shutdown();
            httpExec = null;
        }
    }

    private static String packUrl(String host) {
        String cfg = SlidingPlatformsConfig.VALUES.soundPackHost.trim();
        String base;
        if (!cfg.isEmpty()) {
            base = cfg.startsWith("http://") || cfg.startsWith("https://") ? cfg : "http://" + cfg;
            String rest = base.substring(base.indexOf("://") + 3);
            if (!rest.contains(":") && !rest.contains("/")) {
                base += ":" + SlidingPlatformsConfig.VALUES.soundPackPort;
            }
        } else {
            if (host == null || host.isBlank()) return null;
            base = "http://" + host + ":" + SlidingPlatformsConfig.VALUES.soundPackPort;
        }
        return base + "/" + ZIP_NAME;
    }

    private static void offer(ServerPlayerEntity player) {
        if (!enabled() || packBytes == null || player.networkHandler == null) return;
        String url = packUrl(hellos.get(player.getUuid()));
        if (url == null) return;
        player.networkHandler.sendPacket(new ResourcePackSendS2CPacket(url, packSha1, false,
                Text.translatable("message.slidingplatforms.soundpack_prompt")));
    }

    public static void onHello(ServerPlayerEntity player, String host) {
        hellos.put(player.getUuid(), host == null ? "" : host);
        sendSoundList(player);
        offer(player);
    }

    private static void rebuildIfChanged(boolean force) {
        long stamp = folderStamp();
        if (!force && stamp == folderStamp) return;
        folderStamp = stamp;
        rebuild();
    }

    private static long folderStamp() {
        Path dir = soundsDir();
        if (!Files.isDirectory(dir)) return -1;
        StringBuilder sb = new StringBuilder();
        try (var s = Files.list(dir)) {
            for (Path p : s.filter(Files::isRegularFile).sorted().toList()) {
                try {
                    sb.append(p.getFileName()).append('#').append(Files.size(p))
                            .append('#').append(Files.getLastModifiedTime(p).toMillis()).append(';');
                } catch (IOException ignored) {}
            }
        } catch (IOException e) {
            return -1;
        }
        return sb.toString().hashCode();
    }

    private static synchronized void rebuild() {
        List<String> bad = new ArrayList<>();
        Map<String, Path> files = SoundFileUtil.scan(soundsDir(), bad);
        if (!bad.isEmpty()) {
            LOG.warn("Пропущены не-Vorbis файлы в {}: {}", SoundFileUtil.USER_DIR_NAME, bad);
        }

        long total = 0;
        try {
            for (Path p : files.values()) total += Files.size(p);
        } catch (IOException ignored) {}
        if (total > MAX_TOTAL) {
            LOG.warn("Папка звуков слишком тяжёлая ({} байт > {}), пак не собираем", total, MAX_TOTAL);
            boolean had = packBytes != null;
            packBytes = null;
            packSha1 = "";
            lastList = Map.of();
            if (had && serverRef != null) broadcastSoundList();
            return;
        }

        Map<String, String> newList = new LinkedHashMap<>();
        String newSha = "";
        byte[] newBytes = null;
        if (!files.isEmpty()) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream(64 << 10);
                try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
                    putEntry(zos, "pack.mcmeta",
                            ("{\"pack\":{\"pack_format\":15,\"description\":\"Sliding Platforms: звуки платформ с этого сервера (автосборка из "
                                    + SoundFileUtil.USER_DIR_NAME + "/)\"}}").getBytes(StandardCharsets.UTF_8));
                    StringBuilder sj = new StringBuilder("{\n");
                    int i = 0;
                    for (String base : files.keySet()) {
                        sj.append("  \"").append(base).append("\": {\"sounds\": [{\"name\": \"")
                                .append(SoundFileUtil.NAMESPACE).append(':').append(base)
                                .append("\", \"stream\": true}]}");
                        if (++i < files.size()) sj.append(',');
                        sj.append('\n');
                    }
                    sj.append("}\n");
                    putEntry(zos, "assets/" + SoundFileUtil.NAMESPACE + "/sounds.json",
                            sj.toString().getBytes(StandardCharsets.UTF_8));
                    for (Map.Entry<String, Path> e : files.entrySet()) {
                        byte[] data = Files.readAllBytes(e.getValue());
                        newList.put(e.getKey(), SoundFileUtil.sha1Hex(data).substring(0, 8));
                        putEntry(zos, "assets/" + SoundFileUtil.NAMESPACE + "/sounds/"
                                + e.getKey() + ".ogg", data);
                    }
                }
                newBytes = baos.toByteArray();
                newSha = SoundFileUtil.sha1Hex(newBytes);
            } catch (IOException e) {
                LOG.warn("Не смогли собрать звуковой пак: {}", e.toString());
                return;
            }
        }
        boolean changed = !newSha.equals(packSha1);
        packBytes = newBytes;
        packSha1 = newSha;
        lastList = newList;
        try {
            if (newBytes != null && packFile != null) {
                Path tmp = packFile.resolveSibling(ZIP_NAME + ".tmp");
                Files.write(tmp, newBytes);
                Files.move(tmp, packFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {}
        if (changed) {
            LOG.info("Звуковой пак пересобран: {} звуков, sha1 {}", newList.size(),
                    newSha.isEmpty() ? "—" : newSha.substring(0, Math.min(8, newSha.length())));
            if (serverRef != null) {
                for (ServerPlayerEntity p : serverRef.getPlayerManager().getPlayerList()) {
                    if (hellos.containsKey(p.getUuid())) offer(p);
                }
                broadcastSoundList();
            }
        }
    }

    private static void putEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setTime(0);
        zos.putNextEntry(e);
        zos.write(data);
        zos.closeEntry();
    }

    private static void sendSoundList(ServerPlayerEntity player) {
        if (!enabled()) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(lastList.size());
        lastList.forEach((base, h) -> {
            buf.writeString(base, 80);
            buf.writeString(h, 8);
        });
        ServerPlayNetworking.send(player, SlidingPlatforms.SOUND_LIST, buf);
    }

    private static void broadcastSoundList() {
        if (serverRef == null) return;
        for (ServerPlayerEntity p : serverRef.getPlayerManager().getPlayerList()) {
            if (hellos.containsKey(p.getUuid())) sendSoundList(p);
        }
    }

    public static void onUploadBegin(ServerPlayerEntity player, String base, int size, String sha) {
        if (!enabled()) {
            player.sendMessage(Text.translatable("message.slidingplatforms.soundpack_off"), true);
            sendAck(player, base, base, false);
            return;
        }
        base = SoundFileUtil.sanitizeBase(base);
        long now = System.currentTimeMillis();
        UploadSession st = uploads.computeIfAbsent(player.getUuid(), u -> new UploadSession());
        if (now - st.windowStart > WINDOW_MS) { st.windowStart = now; st.windowCount = 0; }
        if (base.isEmpty() || size <= 0 || size > MAX_FILE || sha.length() != 40
                || st.windowCount >= UPLOADS_PER_WINDOW || lastList.size() >= SoundFileUtil.MAX_FILES) {
            player.sendMessage(Text.translatable("message.slidingplatforms.sound_upload_failed",
                    base, size > MAX_FILE ? ">1 MB" : "denied"), true);
            st.base = null;
            sendAck(player, base, base, false);
            return;
        }
        st.base = base;
        st.data = new byte[size];
        st.off = 0;
        st.sha = sha;
        st.windowCount++;
    }

    public static void onUploadChunk(ServerPlayerEntity player, byte[] chunk) {
        UploadSession st = uploads.get(player.getUuid());
        if (st == null || st.base == null || st.off >= st.data.length) return;
        if (chunk.length > CHUNK || st.off + chunk.length > st.data.length) {
            st.base = null;
            return;
        }
        System.arraycopy(chunk, 0, st.data, st.off, chunk.length);
        st.off += chunk.length;
        if (st.off == st.data.length) {
            String orig = st.base;
            String finalBase = finishUpload(player, st);
            st.base = null;
            sendAck(player, orig, finalBase == null ? "" : finalBase, finalBase != null);
        }
    }

    private static String finishUpload(ServerPlayerEntity player, UploadSession st) {
        if (!SoundFileUtil.sha1Hex(st.data).equals(st.sha) || !SoundFileUtil.isVorbis(st.data, st.data.length)) {
            player.sendMessage(Text.translatable("message.slidingplatforms.sound_upload_failed",
                    st.base, "bad file"), true);
            return null;
        }
        String finalBase = st.base;
        try {
            Path existing = SoundFileUtil.scan(soundsDir(), null).get(finalBase);
            if (existing != null) {
                byte[] have = Files.readAllBytes(existing);
                if (!SoundFileUtil.sha1Hex(have).equals(st.sha)) {

                    String alt = finalBase + "_" + st.sha.substring(0, 8);
                    finalBase = alt.length() > 64 ? alt.substring(0, 64) : alt;
                } else {
                    Files.write(soundsDir().resolve(finalBase + ".ogg"), st.data);
                    afterStored(player, finalBase);
                    return finalBase;
                }
            }
            Files.write(soundsDir().resolve(finalBase + ".ogg"), st.data);
            afterStored(player, finalBase);
            return finalBase;
        } catch (IOException e) {
            LOG.warn("Не смогли сохранить залитый звук {}: {}", st.base, e.toString());
            player.sendMessage(Text.translatable("message.slidingplatforms.sound_upload_failed",
                    st.base, "io"), true);
            return null;
        }
    }

    private static void afterStored(ServerPlayerEntity player, String finalBase) {
        player.sendMessage(Text.translatable("message.slidingplatforms.sound_uploaded",
                SoundFileUtil.NAMESPACE + ":" + finalBase), true);
        rebuild();
    }

    private static void sendAck(ServerPlayerEntity player, String origBase, String finalBase, boolean ok) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(origBase, 80);
        buf.writeString(finalBase == null ? "" : finalBase, 80);
        buf.writeBoolean(ok);
        ServerPlayNetworking.send(player, SlidingPlatforms.SOUND_ACK, buf);
    }

    public static void onPackDownloadFailed(ServerPlayerEntity player) {
        MinecraftServer s = serverRef;
        if (s == null) return;
        UUID uuid = player.getUuid();
        s.execute(() -> {
            ServerPlayerEntity p = s.getPlayerManager().getPlayer(uuid);
            if (p == null) return;
            if (!enabled()) return;

            if (!SlidingPlatformsConfig.VALUES.soundPackFallback) return;
            byte[] data = packBytes;
            String sha = packSha1;
            if (data == null || sha.isEmpty()) return;
            if (!hellos.containsKey(uuid)) return;
            if (sha.equals(mirrorPushed.get(uuid))) return;
            if (mirrors.containsKey(uuid)) return;
            mirrorPushed.put(uuid, sha);
            MirrorSession st = new MirrorSession();
            st.data = data;
            st.off = 0;
            mirrors.put(uuid, st);
            PacketByteBuf begin = PacketByteBufs.create();
            begin.writeVarInt(data.length);
            begin.writeString(sha, 40);
            ServerPlayNetworking.send(p, SlidingPlatforms.SOUND_PACK_BEGIN, begin);
            LOG.info("HTTP-закачка звукового пака у {} сорвалась — довозим чанками ({} байт)",
                    p.getName().getString(), data.length);
        });
    }

    private static void pumpPackMirrors() {
        if (mirrors.isEmpty() || serverRef == null) return;
        var it = mirrors.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            ServerPlayerEntity p = serverRef.getPlayerManager().getPlayer(e.getKey());
            MirrorSession st = e.getValue();
            if (p == null) { it.remove(); continue; }
            int sent = 0;
            while (sent < MIRROR_PER_TICK && st.off < st.data.length) {
                int len = Math.min(MIRROR_CHUNK, st.data.length - st.off);
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeVarInt(len);
                buf.writeBytes(st.data, st.off, len);
                ServerPlayNetworking.send(p, SlidingPlatforms.SOUND_PACK_CHUNK, buf);
                st.off += len;
                sent++;
            }
            if (st.off >= st.data.length) {
                it.remove();
                LOG.info("Звуковой пак для {} довезён чанками", p.getName().getString());
            }
        }
    }
}

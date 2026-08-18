package mc.slidingplatforms.client;

import mc.slidingplatforms.SoundFileUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ServerPackMirror {

    private static final Logger LOG = LoggerFactory.getLogger("Sliding Platforms/SoundPackMirror");
    private static final String DIR_NAME = "slidingplatforms_server_sounds";
    private static final String PACK_ID = "file/" + DIR_NAME;
    private static final String HASH_FILE = "_sha1.txt";

    private static final int MAX_PACK = 24 << 20;

    private static byte[] buf;
    private static int off;
    private static String sha = "";

    private ServerPackMirror() {}

    public static void onBegin(int total, String hash) {
        if (total <= 0 || total > MAX_PACK) { buf = null; return; }
        buf = new byte[total];
        off = 0;
        sha = hash == null ? "" : hash;
    }

    public static void reset() { buf = null; off = 0; sha = ""; }

    public static void onChunk(MinecraftClient client, byte[] part) {
        byte[] cur = buf;
        if (cur == null || part.length > 20_000 || off + part.length > cur.length) {
            buf = null;
            return;
        }
        System.arraycopy(part, 0, cur, off, part.length);
        off += part.length;
        if (off != cur.length) return;
        byte[] done = cur;
        buf = null;
        String expected = sha;
        client.execute(() -> install(client, done, expected));
    }

    private static void install(MinecraftClient client, byte[] zipBytes, String expected) {
        try {
            String actual = SoundFileUtil.sha1Hex(zipBytes);
            if (!actual.equals(expected)) {
                LOG.warn("Чанковая копия звукового пака битая (sha1 {} вместо {}) — пропускаем",
                        actual.substring(0, 8), expected.substring(0, Math.min(8, expected.length())));
                return;
            }
            Path dir = client.runDirectory.toPath().resolve("resourcepacks").resolve(DIR_NAME);
            Path hashFile = dir.resolve(HASH_FILE);
            List<String> packs = client.options.resourcePacks;
            boolean enabled = packs.contains(PACK_ID);
            if (enabled && Files.isRegularFile(hashFile)
                    && Files.readString(hashFile, StandardCharsets.UTF_8).trim().equals(expected)) {
                return;
            }

            if (Files.isDirectory(dir)) {
                try (var s = Files.walk(dir)) {
                    s.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
                }
            }
            Files.createDirectories(dir);
            try (ZipInputStream zis = new ZipInputStream(
                    new ByteArrayInputStream(zipBytes), StandardCharsets.UTF_8)) {
                ZipEntry e;
                while ((e = zis.getNextEntry()) != null) {
                    String name = e.getName().replace('\\', '/');
                    if (name.contains("..") || name.startsWith("/")) continue;
                    Path out = dir.resolve(name).normalize();
                    if (!out.startsWith(dir)) continue;
                    if (e.isDirectory()) { Files.createDirectories(out); continue; }
                    if (out.getParent() != null) Files.createDirectories(out.getParent());
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.writeString(hashFile, expected, StandardCharsets.UTF_8);
            packs.remove(PACK_ID);
            packs.add(PACK_ID);
            client.options.write();
            if (client.player != null) {
                client.player.sendMessage(
                        Text.translatable("message.slidingplatforms.soundpack_synced"), false);
            }
            client.reloadResources();
            LOG.info("Серверный звуковой пак довезён чанками ({} байт) и включён", zipBytes.length);
        } catch (IOException | RuntimeException ex) {
            LOG.warn("Не смогли установить звуковой пак из чанков: {}", ex.toString());
        }
    }
}

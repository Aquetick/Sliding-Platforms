package mc.slidingplatforms.client;

import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class UserSoundLibrary {

    private static final Logger LOG = LoggerFactory.getLogger("Sliding Platforms/UserSounds");

    public static final String NAMESPACE = "slidingplatformssfx";

    public static final String USER_DIR_NAME = "slidingplatforms_sounds";

    private static final String PACK_DIR_NAME = "slidingplatforms_user_sounds";

    private static final String PACK_OPTION = "file/" + PACK_DIR_NAME;

    private static final int MAX_FILES = 128;

    private UserSoundLibrary() {}

    private static Path userDir() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve(USER_DIR_NAME);
    }

    private static Path packDir() {
        return MinecraftClient.getInstance().runDirectory.toPath()
                .resolve("resourcepacks").resolve(PACK_DIR_NAME);
    }

    public static String idFor(String base) {
        return NAMESPACE + ":" + base;
    }

    public static void bootstrap() {
        try {
            Files.createDirectories(userDir());
            sync();
            ensurePackEnabled();
        } catch (Exception e) {
            LOG.warn("Не смогли подготовить папку пользовательских звуков: {}", e.toString());
        }
    }

    public static boolean sync() {
        boolean changed = false;
        try {
            Path user = userDir();
            Files.createDirectories(user);
            Path packSounds = packDir().resolve("assets").resolve(NAMESPACE).resolve("sounds");
            Files.createDirectories(packSounds);

            List<String> bases = list();

            try (Stream<Path> have = Files.list(packSounds)) {
                for (Path p : have.toList()) {
                    String fn = p.getFileName().toString();
                    boolean stillThere = fn.endsWith(".ogg")
                            && bases.contains(fn.substring(0, fn.length() - 4));
                    if (!stillThere) { Files.deleteIfExists(p); changed = true; }
                }
            }

            for (String base : bases) {
                Path src = resolveUserFile(base);
                if (src == null) continue;
                Path dst = packSounds.resolve(base + ".ogg");
                if (!Files.exists(dst) || Files.size(dst) != Files.size(src)
                        || Files.getLastModifiedTime(dst).compareTo(Files.getLastModifiedTime(src)) < 0) {
                    Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                    changed = true;
                }
            }

            Path mcmeta = packDir().resolve("pack.mcmeta");
            if (!Files.exists(mcmeta)) {
                try (Writer w = Files.newBufferedWriter(mcmeta, StandardCharsets.UTF_8)) {
                    w.write("{\"pack\":{\"pack_format\":15,"
                            + "\"description\":\"Ваши звуки для Сдвижных платформ — из папки slidingplatforms_sounds. Сгенерировано автоматически.\"}}");
                }
            }

            StringBuilder sb = new StringBuilder("{\n");
            for (int i = 0; i < bases.size(); i++) {
                sb.append("  \"").append(bases.get(i)).append("\": {\"sounds\": [{\"name\": \"")
                        .append(NAMESPACE).append(':').append(bases.get(i))
                        .append("\", \"stream\": true}]}");
                if (i < bases.size() - 1) sb.append(',');
                sb.append('\n');
            }
            sb.append("}\n");
            Path jsonPath = packDir().resolve("assets").resolve(NAMESPACE).resolve("sounds.json");
            String json = sb.toString();

            String before = Files.exists(jsonPath)
                    ? Files.readString(jsonPath, StandardCharsets.UTF_8) : "";
            if (!json.equals(before)) {
                try (Writer w = Files.newBufferedWriter(jsonPath, StandardCharsets.UTF_8)) {
                    w.write(json);
                }
                if (!before.isEmpty()) changed = true;
            }
        } catch (Exception e) {
            LOG.warn("Синхронизация пользовательских звуков не удалась: {}", e.toString());
        }
        return changed;
    }

    public static List<String> list() {
        List<String> bases = new ArrayList<>();
        synchronized (BAD_FILES) { BAD_FILES.clear(); }
        Path user = userDir();
        if (!Files.isDirectory(user)) return bases;
        try (Stream<Path> s = Files.list(user)) {
            List<Path> oggFiles = s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg"))
                    .sorted()
                    .limit(MAX_FILES)
                    .toList();
            for (Path f : oggFiles) {
                String raw = f.getFileName().toString();
                String nm = raw.substring(0, raw.length() - 4);
                if (!isVorbis(f)) {
                    synchronized (BAD_FILES) { BAD_FILES.add(raw); }
                    continue;
                }
                String base = sanitizeBase(nm);
                if (!base.isEmpty() && !bases.contains(base)) bases.add(base);
            }
        } catch (IOException e) {
            LOG.warn("Не смогли прочитать папку звуков: {}", e.toString());
        }
        return bases;
    }

    public static Path fileFor(String base) {
        return resolveUserFile(base);
    }

    private static Path resolveUserFile(String base) {
        Path user = userDir();
        try (Stream<Path> s = Files.list(user)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                String n = p.getFileName().toString();
                if (n.toLowerCase(Locale.ROOT).endsWith(".ogg")
                        && sanitizeBase(n.substring(0, n.length() - 4)).equals(base)) return p;
            }
        } catch (IOException ignored) {

        }
        return null;
    }

    private static final List<String> BAD_FILES = new ArrayList<>();

    public static List<String> badFiles() {
        synchronized (BAD_FILES) { return List.copyOf(BAD_FILES); }
    }

    private static boolean isVorbis(Path f) {
        byte[] head = new byte[8192];
        int n;
        try (java.io.InputStream in = Files.newInputStream(f)) {
            n = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            return false;
        }
        byte[] sig = {1, 'v', 'o', 'r', 'b', 'i', 's'};
        for (int i = 0; i + sig.length <= n; i++) {
            boolean match = true;
            for (int j = 0; j < sig.length; j++) {
                if (head[i + j] != sig[j]) { match = false; break; }
            }
            if (match) return true;
        }
        return false;
    }

    private static String sanitizeBase(String raw) {
        StringBuilder sb = new StringBuilder(64);
        for (char c : raw.trim().toLowerCase(Locale.ROOT).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-') {
                sb.append(c);
            } else {
                sb.append('_');
            }
            if (sb.length() >= 64) break;
        }
        return sb.toString();
    }

    private static void ensurePackEnabled() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null) return;
        if (!client.options.resourcePacks.contains(PACK_OPTION)) {
            client.options.resourcePacks.add(PACK_OPTION);
            client.options.write();
            client.reloadResources();
            LOG.info("Активирован автопак пользовательских звуков ({})", PACK_DIR_NAME);
        }
    }
}

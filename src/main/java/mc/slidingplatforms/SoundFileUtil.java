package mc.slidingplatforms;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public final class SoundFileUtil {

    public static final String NAMESPACE = "slidingplatformssfx";

    public static final String USER_DIR_NAME = "slidingplatforms_sounds";

    public static final int MAX_FILES = 128;

    private SoundFileUtil() {}

    public static String sanitizeBase(String raw) {
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

    public static boolean isVorbis(byte[] head, int n) {
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

    public static boolean isVorbis(Path f) {
        byte[] head = new byte[8192];
        int n;
        try (InputStream in = Files.newInputStream(f)) {
            n = in.readNBytes(head, 0, head.length);
        } catch (IOException e) {
            return false;
        }
        return isVorbis(head, n);
    }

    public static String sha1Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(data);
            StringBuilder sb = new StringBuilder(40);
            for (byte b : d) sb.append(Character.forDigit((b >> 4) & 0xF, 16))
                    .append(Character.forDigit(b & 0xF, 16));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, Path> scan(Path dir, List<String> bad) {
        Map<String, Path> out = new LinkedHashMap<>();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> files = s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".ogg"))
                    .sorted()
                    .limit(MAX_FILES)
                    .toList();
            for (Path f : files) {
                String raw = f.getFileName().toString();
                String base = sanitizeBase(raw.substring(0, raw.length() - 4));
                if (base.isEmpty()) continue;
                if (!isVorbis(f)) {
                    if (bad != null && !bad.contains(raw)) bad.add(raw);
                    continue;
                }
                out.putIfAbsent(base, f);
            }
        } catch (IOException ignored) {

        }
        return out;
    }
}

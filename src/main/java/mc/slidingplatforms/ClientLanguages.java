package mc.slidingplatforms;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientLanguages {

    private static final Map<UUID, String> LANGS = new ConcurrentHashMap<>();

    private ClientLanguages() {}

    public static void put(UUID uuid, String lang) {
        if (lang != null && !lang.isBlank()) LANGS.put(uuid, lang.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public static void remove(UUID uuid) { LANGS.remove(uuid); }

    public static String of(UUID uuid) { return LANGS.get(uuid); }

    public static String localizedWord(UUID uuid, String ru, String en) {
        String lang = of(uuid);
        return lang != null && !lang.startsWith("ru") ? en : ru;
    }
}

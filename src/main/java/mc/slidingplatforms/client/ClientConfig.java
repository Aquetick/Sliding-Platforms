package mc.slidingplatforms.client;

import mc.slidingplatforms.SlidingPlatformsConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ClientConfig {

    private static volatile SlidingPlatformsConfig.Values v = new SlidingPlatformsConfig.Values();

    private ClientConfig() {}

    public static SlidingPlatformsConfig.Values get() { return v; }

    public static void applyJson(String json) {
        SlidingPlatformsConfig.Values parsed;
        try {
            parsed = new com.google.gson.Gson().fromJson(json, SlidingPlatformsConfig.Values.class);
        } catch (Exception e) {
            return;
        }
        if (parsed != null) v = SlidingPlatformsConfig.sanitize(parsed);
    }
}

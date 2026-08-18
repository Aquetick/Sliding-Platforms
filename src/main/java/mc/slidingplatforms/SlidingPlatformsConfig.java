package mc.slidingplatforms;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class SlidingPlatformsConfig {

    public static class Values {

        public int maxWidth = 12;

        public int maxHeight = 12;

        public int maxDepth = 3;

        public double maxSpeed = 1.0;

        public int maxOffset = 64;

        public int rideMaxPath = 128;

        public double speed = 0.15;

        public boolean defaultSounds = true;

        public boolean defaultLampGlow = true;

        public boolean soundPack = true;

        public int soundPackPort = 24466;

        public String soundPackHost = "";

        public boolean soundPackFallback = true;

        public boolean debugLogs = false;

        public Values copy() {
            Values v = new Values();
            v.maxWidth = maxWidth; v.maxHeight = maxHeight; v.maxDepth = maxDepth;
            v.maxSpeed = maxSpeed; v.maxOffset = maxOffset; v.rideMaxPath = rideMaxPath;
            v.speed = speed; v.defaultSounds = defaultSounds; v.defaultLampGlow = defaultLampGlow;
            v.soundPack = soundPack; v.soundPackPort = soundPackPort;
            v.soundPackHost = soundPackHost; v.soundPackFallback = soundPackFallback;
            v.debugLogs = debugLogs;
            return v;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Values VALUES = new Values();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("slidingplatforms.json");
    }

    public static void load() {
        try {
            if (Files.exists(file())) {
                try (Reader reader = Files.newBufferedReader(file())) {
                    Values loaded = GSON.fromJson(reader, Values.class);
                    if (loaded != null) VALUES = sanitize(loaded);
                }
            } else {
                save();
            }
        } catch (IOException e) {

            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(file().getParent());
            try (Writer writer = Files.newBufferedWriter(file())) {
                GSON.toJson(VALUES, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean applyJson(String json) {
        try {
            Values v = GSON.fromJson(json, Values.class);
            if (v == null) return false;
            VALUES = sanitize(v);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String toJson() {
        return GSON.toJson(VALUES);
    }

    public static Values sanitize(Values v) {
        v.maxWidth = clamp(v.maxWidth, 1, 512);
        v.maxHeight = clamp(v.maxHeight, 1, 512);
        v.maxDepth = clamp(v.maxDepth, 1, 64);
        v.maxSpeed = clamp(v.maxSpeed, 0.05, 4096.0);
        v.maxOffset = clamp(v.maxOffset, 1, 65536);
        v.rideMaxPath = clamp(v.rideMaxPath, 8, 131072);
        v.speed = clamp(v.speed, 0.01, Math.min(v.maxSpeed, 4096.0));
        v.soundPackPort = clamp(v.soundPackPort, 0, 65535);
        if (v.soundPackHost == null) v.soundPackHost = "";
        return v;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}

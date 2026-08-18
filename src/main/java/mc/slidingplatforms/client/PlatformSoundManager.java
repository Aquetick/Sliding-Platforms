package mc.slidingplatforms.client;

import mc.slidingplatforms.SlidingPlatformEntity;
import mc.slidingplatforms.SlidingPlatforms;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MovingSoundInstance;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class PlatformSoundManager {

    public static final Identifier HUM = new Identifier(SlidingPlatforms.MOD_ID, "platform_hum");

    public static final String DEFAULT_START = "minecraft:block.piston.extend";
    public static final String DEFAULT_STOP = "minecraft:block.piston.contract";
    public static final String DEFAULT_ARRIVE = "minecraft:block.note_block.pling";

    private static final int ARRIVE_DELAY_TICKS = 3;

    private static final float VOL_HUM = 0.55f;
    private static final float VOL_START = 0.8f;
    private static final float VOL_STOP = 0.8f;
    private static final float VOL_ARRIVE = 0.9f;

    private static final Map<Integer, PlatformHumSound> hums = new HashMap<>();

    private record SeenPlatform(SlidingPlatformEntity platform, long lastSeen) {}
    private static final Map<Integer, SeenPlatform> known = new HashMap<>();

    private record PendingArrive(long atWorldTime, String soundId,
                                 double x, double y, double z) {}
    private static final List<PendingArrive> pending = new ArrayList<>();

    private PlatformSoundManager() {}

    public static void onWorldTick(ClientWorld world) {
        if (world == null) { clear(); return; }
        known.values().removeIf(v -> world.getTime() - v.lastSeen() > 40);

        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (Entity e : world.getEntities()) {
            if (!(e instanceof SlidingPlatformEntity platform)) continue;
            seen.add(platform.getId());
            known.put(platform.getId(), new SeenPlatform(platform, world.getTime()));
            if (!platform.isTravellingNow()) continue;
            PlatformHumSound hum = hums.get(platform.getId());

            if (hum == null && platform.soundProfileKnown() && platform.soundsEnabled()
                    && platform.hasHumSound()) {
                playOnce(resolve(platform.getSndStart(), DEFAULT_START), VOL_START,
                        platform.getX(), platform.getY(), platform.getZ());
                hum = new PlatformHumSound(platform);
                MinecraftClient.getInstance().getSoundManager().play(hum);
                hums.put(platform.getId(), hum);
            }
        }

        Iterator<Map.Entry<Integer, PlatformHumSound>> it = hums.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, PlatformHumSound> en = it.next();
            PlatformHumSound hum = en.getValue();
            SlidingPlatformEntity platform = hum.platform;
            boolean finishedNaturally = seen.contains(platform.getId()) && !platform.isRemoved()
                    && !platform.isTravellingNow();
            if (platform.isRemoved() || !seen.contains(platform.getId()) || finishedNaturally) {
                hum.stop();
                it.remove();
                if (finishedNaturally && platform.hasTailSound()) {
                    playOnce(resolve(platform.getSndStop(), DEFAULT_STOP), VOL_STOP,
                            platform.getX(), platform.getY(), platform.getZ());
                    pending.add(new PendingArrive(world.getTime() + ARRIVE_DELAY_TICKS,
                            resolve(platform.getSndArrive(), DEFAULT_ARRIVE),
                            platform.getX(), platform.getY(), platform.getZ()));
                }

                if (finishedNaturally) known.remove(platform.getId());
            }
        }

        if (!pending.isEmpty()) {
            long now = world.getTime();
            pending.removeIf(p -> {
                if (p.atWorldTime() > now) return false;
                playOnce(p.soundId(), VOL_ARRIVE, p.x(), p.y(), p.z());
                return true;
            });
        }
    }

    public static void onPlatformArrived(int entityId, BlockPos at, boolean loud) {
        PlatformHumSound hum = hums.remove(entityId);
        if (hum != null) hum.stop();
        SlidingPlatformEntity platform;
        if (hum != null) {
            platform = hum.platform;
        } else {

            SeenPlatform seen = known.remove(entityId);
            if (seen == null) return;
            platform = seen.platform();
        }
        if (!platform.soundsEnabled() || !platform.hasTailSound()) return;
        double x = at.getX() + 0.5, y = at.getY() + 0.5, z = at.getZ() + 0.5;
        playOnce(resolve(platform.getSndStop(), DEFAULT_STOP), VOL_STOP, x, y, z);
        MinecraftClient client = MinecraftClient.getInstance();
        if (loud && client.world != null) {
            pending.add(new PendingArrive(client.world.getTime() + ARRIVE_DELAY_TICKS,
                    resolve(platform.getSndArrive(), DEFAULT_ARRIVE), x, y, z));
        }
    }

    public static void clear() {
        for (PlatformHumSound hum : hums.values()) hum.stop();
        hums.clear();
        pending.clear();
        known.clear();
    }

    private static String resolve(String custom, String fallback) {
        return custom == null || custom.isEmpty() ? fallback : custom;
    }

    private static void playOnce(String id, float volume, double x, double y, double z) {
        Identifier ident = Identifier.tryParse(id);
        if (ident == null) return;
        SoundManager sm = MinecraftClient.getInstance().getSoundManager();
        sm.play(new PositionedSoundInstance(SoundEvent.of(ident), SoundCategory.BLOCKS,
                volume, 1.0f, Random.create(), x, y, z));
    }

    static final class PlatformHumSound extends MovingSoundInstance {
        final SlidingPlatformEntity platform;

        PlatformHumSound(SlidingPlatformEntity platform) {
            super(SoundEvent.of(Identifier.tryParse(platform.getSndHum().isEmpty()
                        ? HUM.toString() : platform.getSndHum())),
                  SoundCategory.BLOCKS, Random.create());
            this.platform = platform;
            this.repeat = true;
            this.repeatDelay = 0;
            this.volume = VOL_HUM;
            this.pitch = 0.96f + Random.create().nextFloat() * 0.08f;
            follow();
        }

        @Override
        public void tick() {
            if (platform.isRemoved()) { setDone(); return; }
            follow();
        }

        private void follow() {
            this.x = (float) platform.getX();
            this.y = (float) platform.getY();
            this.z = (float) platform.getZ();
        }

        void stop() {
            setDone();
        }
    }
}

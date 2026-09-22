package mc.slidingplatforms.client;

import mc.slidingplatforms.client.ClientNet;

import mc.slidingplatforms.ModEntities;
import mc.slidingplatforms.ModScreens;
import mc.slidingplatforms.SlidingPlatformEntity;
import mc.slidingplatforms.SlidingPlatforms;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SlidingPlatformsClient implements ClientModInitializer {

    private static final Map<Integer, NbtCompound> pending = new HashMap<>();

    @Override
    public void onInitializeClient() {

        EntityRendererRegistry.register(ModEntities.SLIDING_PLATFORM, SlidingPlatformRenderer::new);

        HandledScreens.register(ModScreens.PLATFORM_CONTROLLER, PlatformControllerScreen::new);
        HandledScreens.register(ModScreens.REMOTE_SWITCH, RemoteSwitchScreen::new);
        HandledScreens.register(ModScreens.FLOOR_SELECT, FloorSelectScreen::new);
        HandledScreens.register(ModScreens.SCREEN_SELECT, ScreenSelectScreen::new);
        HandledScreens.register(ModScreens.SCREEN_SETTINGS, ScreenSettingsScreen::new);
        HandledScreens.register(ModScreens.PLATFORM_SOUNDS, PlatformSoundsScreen::new);
        HandledScreens.register(ModScreens.PLATFORM_SENSOR, PlatformSensorScreen::new);
        HandledScreens.register(ModScreens.PLATFORM_LOCK, PlatformLockScreen::new);
        HandledScreens.register(ModScreens.PLATFORM_CASCADE, PlatformCascadeScreen::new);
        HandledScreens.register(ModScreens.CONFIG, ModConfigScreen::new);

        ClientNet.registerClientReceiver(SlidingPlatforms.CFG_SYNC, (buf) -> { var client = net.minecraft.client.MinecraftClient.getInstance();
            String json = buf.readString();
            client.execute(() -> ClientConfig.applyJson(json));
        });

        ClientNet.registerClientReceiver(SlidingPlatforms.RELOAD, (buf) -> {
            net.minecraft.client.MinecraftClient.getInstance().execute(
                () -> net.minecraft.client.MinecraftClient.getInstance().reloadResources());
        });

        net.minecraft.client.render.block.entity.BlockEntityRendererFactories.register(
                mc.slidingplatforms.ModBlocks.ELEVATOR_SCREEN_BE, ElevatorScreenRenderer::new);

        ClientNet.registerClientReceiver(SlidingPlatformEntity.DATA_PACKET, (buf) -> { var client = net.minecraft.client.MinecraftClient.getInstance();
                    int entityId = buf.readVarInt();
                    NbtCompound data = buf.readNbt();
                    client.execute(() -> {
                        ClientWorld world = client.world;
                        if (world == null || data == null) return;
                        Entity entity = world.getEntityById(entityId);
                        if (entity instanceof SlidingPlatformEntity platform) {
                            platform.acceptSpawnData(data);
                            SlidingPlatforms.LOGGER.info("Данные платформы #{} приняты: блоков {}",
                                    entityId, data.getIntArray("x").length);
                        } else {
                            pending.put(entityId, data);
                        }
                    });
                });

        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            NbtCompound data = pending.remove(entity.getId());
            if (data != null && entity instanceof SlidingPlatformEntity platform) {
                platform.acceptSpawnData(data);
            }
        });

        ClientNet.registerClientReceiver(SlidingPlatformEntity.ARRIVE_PACKET, (buf) -> { var client = net.minecraft.client.MinecraftClient.getInstance();
                    int entityId = buf.readVarInt();
                    long at = buf.readLong();
                    boolean loud = buf.readBoolean();
                    client.execute(() ->
                            PlatformSoundManager.onPlatformArrived(entityId, BlockPos.fromLong(at), loud));
                });

        ClientNet.registerClientReceiver(SlidingPlatforms.SELECTION_SYNC, (buf) -> { var client = net.minecraft.client.MinecraftClient.getInstance();
                    boolean isActive = buf.readBoolean();
                    int count = buf.readVarInt();
                    List<BlockPos> positions = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) positions.add(buf.readBlockPos());
                    client.execute(() -> {
                        SelectionHighlight.apply(isActive, positions);
                        if (!isActive) BoxSelection.reset();
                    });
                });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(SelectionHighlight::render);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            SelectionHighlight.clear();
            ServerPackMirror.reset();

            var langBuf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
            langBuf.writeString(client.getLanguageManager().getLanguage(), 8);
            ClientNet.send(SlidingPlatforms.CLIENT_LANG, langBuf);
            ServerSounds.onJoin(handler);
        });

        ClientNet.registerClientReceiver(SlidingPlatforms.SOUND_LIST, (buf) -> ServerSounds.onList(buf));
        ClientNet.registerClientReceiver(SlidingPlatforms.SOUND_ACK, (buf) -> ServerSounds.onAck(buf));

        ClientNet.registerClientReceiver(SlidingPlatforms.SOUND_PACK_BEGIN, (buf) -> { var client = net.minecraft.client.MinecraftClient.getInstance();
                    int total = buf.readVarInt();
                    String sha = buf.readString(40);
                    ServerPackMirror.onBegin(total, sha);
                });
        ClientNet.registerClientReceiver(SlidingPlatforms.SOUND_PACK_CHUNK, (buf) -> { var client = net.minecraft.client.MinecraftClient.getInstance();
                    int len = buf.readVarInt();
                    if (len < 0 || len > 20_000) { ServerPackMirror.reset(); return; }
                    byte[] part = new byte[len];
                    buf.readBytes(part);
                    ServerPackMirror.onChunk(client, part);
                });

        BoxSelection.init();

        ClientNet.registerClientReceiver(SlidingPlatforms.ZONE_SYNC, (buf) -> { var client = net.minecraft.client.MinecraftClient.getInstance();
                    boolean isActive = buf.readBoolean();
                    BlockPos ctrl = buf.readBlockPos();
                    boolean hasZone = buf.readBoolean();
                    BlockPos min = hasZone ? buf.readBlockPos() : null;
                    BlockPos max = hasZone ? buf.readBlockPos() : null;
                    client.execute(() -> {
                        if (isActive) ZoneSelection.apply(true, ctrl, min, max);
                        else ZoneSelection.reset();
                    });
                });
        ZoneSelection.init();

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_WORLD_TICK
                .register(world -> {
                    if (world != null) {
                        mc.slidingplatforms.PlatformCollisionHandler.groundRiders(world);
                        PlatformSoundManager.onWorldTick(world);
                    }
                });

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK
                .register(client -> {
                    if (!bootSounds && client.world != null) {
                        bootSounds = true;
                        UserSoundLibrary.bootstrap();
                    }
                });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            PlatformSoundManager.clear();
            ServerSounds.onDisconnect();
        });
    }

    private static boolean bootSounds = false;
}

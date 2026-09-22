package mc.slidingplatforms;

import mc.slidingplatforms.Net;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SlidingPlatforms implements ModInitializer {
    public static final String MOD_ID = "slidingplatforms";
    public static final Logger LOGGER = LoggerFactory.getLogger("Sliding Platforms");

    public static void dbg(String msg, Object... args) {
        if (SlidingPlatformsConfig.VALUES.debugLogs) LOGGER.info("[dbg] " + msg, args);
    }

    public static final Identifier GUI_APPLY = Identifier.of(MOD_ID, "gui_apply");
    public static final Identifier GUI_MANUAL = Identifier.of(MOD_ID, "gui_manual");
    public static final Identifier SWITCH_TOGGLE = Identifier.of(MOD_ID, "switch_toggle");
    public static final Identifier SELECTION_SYNC = Identifier.of(MOD_ID, "selection_sync");
    public static final Identifier BOX_SELECT = Identifier.of(MOD_ID, "box_select");
    public static final Identifier GUI_SCREENS = Identifier.of(MOD_ID, "gui_screens");
    public static final Identifier BIND_SCREEN = Identifier.of(MOD_ID, "bind_screen");
    public static final Identifier RIDE_TO = Identifier.of(MOD_ID, "ride_to");
    public static final Identifier SCREEN_NAME = Identifier.of(MOD_ID, "screen_name");
    public static final Identifier SCREEN_FLOOR_NUM = Identifier.of(MOD_ID, "screen_floor_num");
    public static final Identifier SCREEN_FLOOR_DEL = Identifier.of(MOD_ID, "screen_floor_del");
    public static final Identifier CHAIN_RENAME = Identifier.of(MOD_ID, "chain_rename");
    public static final Identifier SCREEN_CALL = Identifier.of(MOD_ID, "screen_call");
    public static final Identifier CHAIN_LINK = Identifier.of(MOD_ID, "chain_link");
    public static final Identifier PLATFORM_SOUNDS_GUI = Identifier.of(MOD_ID, "platform_sounds_gui");
    public static final Identifier PLATFORM_SOUNDS = Identifier.of(MOD_ID, "platform_sounds");
    public static final Identifier PLATFORM_MAIN_GUI = Identifier.of(MOD_ID, "platform_main_gui");
    public static final Identifier PLATFORM_SENSOR_GUI = Identifier.of(MOD_ID, "platform_sensor_gui");
    public static final Identifier PLATFORM_SENSOR = Identifier.of(MOD_ID, "platform_sensor");
    public static final Identifier GUI_SENSOR_ZONE = Identifier.of(MOD_ID, "gui_sensor_zone");
    public static final Identifier ZONE_SELECT = Identifier.of(MOD_ID, "zone_select");
    public static final Identifier ZONE_CLEAR = Identifier.of(MOD_ID, "zone_clear");
    public static final Identifier ZONE_SYNC = Identifier.of(MOD_ID, "zone_sync");
    public static final Identifier PLATFORM_LOCK_GUI = Identifier.of(MOD_ID, "platform_lock_gui");
    public static final Identifier PLATFORM_LOCK_SET = Identifier.of(MOD_ID, "platform_lock_set");
    public static final Identifier PLATFORM_CASCADE_GUI = Identifier.of(MOD_ID, "platform_cascade_gui");
    public static final Identifier PLATFORM_CASCADE_SET = Identifier.of(MOD_ID, "platform_cascade_set");
    public static final Identifier CFG_SET = Identifier.of(MOD_ID, "cfg_set");
    public static final Identifier CFG_SYNC = Identifier.of(MOD_ID, "cfg_sync");
    public static final Identifier RELOAD = Identifier.of(MOD_ID, "reload");
    public static final Identifier SOUND_HELLO = Identifier.of(MOD_ID, "sound_hello");
    public static final Identifier SOUND_LIST = Identifier.of(MOD_ID, "sound_list");
    public static final Identifier SOUND_UP_BEGIN = Identifier.of(MOD_ID, "sound_up_begin");
    public static final Identifier SOUND_UP_CHUNK = Identifier.of(MOD_ID, "sound_up_chunk");
    public static final Identifier SOUND_ACK = Identifier.of(MOD_ID, "sound_ack");
    public static final Identifier CLIENT_LANG = Identifier.of(MOD_ID, "client_lang");
    public static final Identifier SOUND_PACK_BEGIN = Identifier.of(MOD_ID, "sound_pack_begin");
    public static final Identifier SOUND_PACK_CHUNK = Identifier.of(MOD_ID, "sound_pack_chunk");

    public static final int GUI_MAX_OFFSET = 64;

    private static final Map<UUID, BlockPos> selecting = new HashMap<>();

    private static final Map<UUID, BlockPos> zoneSelecting = new HashMap<>();

    /** Pre-register all network channels so PayloadTypeRegistry is set up at init time. */
    private static void registerAllChannels() {
        Net.register(GUI_APPLY);
        Net.register(GUI_MANUAL);
        Net.register(SWITCH_TOGGLE);
        Net.register(SELECTION_SYNC);
        Net.register(BOX_SELECT);
        Net.register(GUI_SCREENS);
        Net.register(BIND_SCREEN);
        Net.register(RIDE_TO);
        Net.register(SCREEN_NAME);
        Net.register(SCREEN_FLOOR_NUM);
        Net.register(SCREEN_FLOOR_DEL);
        Net.register(CHAIN_RENAME);
        Net.register(SCREEN_CALL);
        Net.register(CHAIN_LINK);
        Net.register(PLATFORM_SOUNDS_GUI);
        Net.register(PLATFORM_SOUNDS);
        Net.register(PLATFORM_MAIN_GUI);
        Net.register(PLATFORM_SENSOR_GUI);
        Net.register(PLATFORM_SENSOR);
        Net.register(GUI_SENSOR_ZONE);
        Net.register(ZONE_SELECT);
        Net.register(ZONE_CLEAR);
        Net.register(ZONE_SYNC);
        Net.register(PLATFORM_LOCK_GUI);
        Net.register(PLATFORM_LOCK_SET);
        Net.register(PLATFORM_CASCADE_GUI);
        Net.register(PLATFORM_CASCADE_SET);
        Net.register(CFG_SET);
        Net.register(CFG_SYNC);
        Net.register(RELOAD);
        Net.register(SOUND_HELLO);
        Net.register(SOUND_LIST);
        Net.register(SOUND_UP_BEGIN);
        Net.register(SOUND_UP_CHUNK);
        Net.register(SOUND_ACK);
        Net.register(CLIENT_LANG);
        Net.register(SOUND_PACK_BEGIN);
        Net.register(SOUND_PACK_CHUNK);
        Net.register(SlidingPlatformEntity.DATA_PACKET);
        Net.register(SlidingPlatformEntity.ARRIVE_PACKET);
    }

    public void onInitialize() {
        registerAllChannels();
        SlidingPlatformsConfig.load();
        ModBlocks.register();
        ModEntities.register();
        ModScreens.register();
        SoundPackService.init();

        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
                dispatcher.register(net.minecraft.server.command.CommandManager.literal("slidingplatformscfg")
                        .requires(src -> canEditConfig(src.getEntity(), src.getServer()))
                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
                            openConfigMenu(player);
                            return 1;
                        })));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(SlidingPlatformsConfig.toJson());
            Net.send(handler.player, CFG_SYNC, buf);
        });

        Net.registerServerReceiver(CFG_SET, (player, buf) -> { MinecraftServer server = player.getServer();
            String json = buf.readString();
            server.execute(() -> {
                if (!canEditConfig(player, server)) return;
                if (!SlidingPlatformsConfig.applyJson(json)) return;
                SlidingPlatformsConfig.save();
                dbg("config applied by {}: {}", player.getName().getString(), SlidingPlatformsConfig.toJson());
                SoundPackService.reloadHttp();
                PacketByteBuf out = PacketByteBufs.create();
                out.writeString(SlidingPlatformsConfig.toJson());
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    Net.send(p, CFG_SYNC, new PacketByteBuf(out.copy()));
                }
            });
        });

        registerNetworking();
        registerEvents();
    }

    public static BlockPos finishSelection(PlayerEntity player) {
        return selecting.remove(player.getUuid());
    }

    static void denyOwner(PlayerEntity player) {
        player.sendMessage(Text.translatable("message.slidingplatforms.lock_owner_only"), true);
    }

    public static BlockPos finishZoneSelection(PlayerEntity player) {
        return zoneSelecting.remove(player.getUuid());
    }

    public static void sendZoneSync(PlayerEntity player, BlockPos ctrlPos, boolean active) {
        if (!(player instanceof ServerPlayerEntity spe)) return;
        BlockPos min = null, max = null;
        if (ctrlPos != null && player.getWorld().getBlockEntity(ctrlPos) instanceof PlatformControllerBlockEntity be) {
            min = be.getZoneMin();
            max = be.getZoneMax();
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(active);
        buf.writeBlockPos(ctrlPos != null ? ctrlPos : BlockPos.ORIGIN);
        buf.writeBoolean(min != null);
        if (min != null) {
            buf.writeBlockPos(min);
            buf.writeBlockPos(max);
        }
        Net.send(spe, ZONE_SYNC, buf);
    }

    public static void sendSelectionSync(PlayerEntity player, BlockPos controllerPos,
                                         List<BlockPos> blocks) {
        if (!(player instanceof ServerPlayerEntity spe)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(controllerPos != null);
        buf.writeVarInt(blocks.size());
        for (BlockPos p : blocks) buf.writeBlockPos(p);
        Net.send(spe, SELECTION_SYNC, buf);
    }

    private void registerNetworking() {

        Net.registerServerReceiver(GUI_APPLY, (player, buf) -> { MinecraftServer server = player.getServer();
            BlockPos pos = buf.readBlockPos();
            int axisId = buf.readByte();
            boolean positive = buf.readBoolean();
            int offset = buf.readInt();
            float spd = buf.readFloat();
            String name = buf.readString(24);
            int rsMode = buf.readByte() & 0xFF;

            boolean hasLampGlow = buf.isReadable();
            boolean lampGlow = hasLampGlow && buf.readBoolean();
            server.execute(() -> {
                if (!pos.isWithinDistance(player.getBlockPos(), 8)) return;
                if (player.getWorld().getBlockEntity(pos) instanceof PlatformControllerBlockEntity be) {
                    if (!be.canConfigureLocked(player)) { denyOwner(player); return; }
                    Direction.Axis[] axesAll = Direction.Axis.values();
                    Direction.Axis axis = (axisId >= 0 && axisId < axesAll.length)
                            ? axesAll[axisId] : Direction.Axis.X;

                    boolean dirOk = be.applySettings(axis, positive, offset, spd, name, rsMode);
                    if (hasLampGlow) be.applyLampGlow(lampGlow);
                    if (!dirOk) {
                        player.sendMessage(Text.translatable("message.slidingplatforms.must_be_closed"), true);
                    }
                }
            });
        });

        Net.registerServerReceiver(BOX_SELECT, (player, buf) -> { MinecraftServer server = player.getServer();
            BlockPos a = buf.readBlockPos();
            BlockPos b = buf.readBlockPos();
            server.execute(() -> {
                BlockPos controllerPos = selecting.get(player.getUuid());
                if (controllerPos == null) return;

                long dx = Math.abs(a.getX() - b.getX()) + 1L;
                long dy = Math.abs(a.getY() - b.getY()) + 1L;
                long dz = Math.abs(a.getZ() - b.getZ()) + 1L;
                if (dx * dy * dz > 512) {
                    player.sendMessage(Text.translatable("message.slidingplatforms.box_too_big"), true);
                    return;
                }
                BlockEntity be = player.getWorld().getBlockEntity(controllerPos);
                if (be instanceof PlatformControllerBlockEntity cbe) {
                    int count = cbe.manualToggleRange(a, b);
                    if (count == -2) {
                        player.sendMessage(Text.translatable("message.slidingplatforms.must_be_closed"), true);
                    } else if (count == -1) {
                        player.sendMessage(Text.translatable("message.slidingplatforms.select_full"), true);
                    } else {
                        player.sendMessage(Text.translatable("message.slidingplatforms.select_count", count), true);
                        sendSelectionSync(player, controllerPos, cbe.currentManualPositions());
                    }
                }
            });
        });

        Net.registerServerReceiver(GUI_MANUAL, (player, buf) -> { MinecraftServer server = player.getServer();
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                if (!pos.isWithinDistance(player.getBlockPos(), 8)) return;
                if (player.getWorld().getBlockEntity(pos) instanceof PlatformControllerBlockEntity be) {
                    if (be.isOpen()) {
                        player.sendMessage(Text.translatable("message.slidingplatforms.must_be_closed"), true);
                        return;
                    }
                    selecting.put(player.getUuid(), pos);
                    player.closeHandledScreen();
                    sendSelectionSync(player, pos, be.currentManualPositions());
                }
            });
        });

        Net.registerServerReceiver(GUI_SCREENS, (player, buf) -> { MinecraftServer server = player.getServer();
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                if (!pos.isWithinDistance(player.getBlockPos(), 8)) return;
                if (!(player.getWorld().getBlockEntity(pos) instanceof PlatformControllerBlockEntity)) return;
                openScreenBindMenu(player, pos);
            });
        });

        Net.registerServerReceiver(PLATFORM_SOUNDS_GUI, (player, buf) -> { MinecraftServer server = player.getServer();
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                if (!pos.isWithinDistance(player.getBlockPos(), 8)) return;
                if (player.getWorld().getBlockEntity(pos) instanceof PlatformControllerBlockEntity be
                        && !be.canConfigureLocked(player)) { denyOwner(player); return; }
                openSoundsMenu(player, pos);
            });
        });

        Net.registerServerReceiver(PLATFORM_SOUNDS, (player, buf) -> { MinecraftServer server = player.getServer();
            BlockPos pos = buf.readBlockPos();
            boolean enabled = buf.readBoolean();
            String start = buf.readString(48);
            String stop = buf.readString(48);
            String arrive = buf.readString(48);
            String hum = buf.readString(48);
            server.execute(() -> {
                if (!pos.isWithinDistance(player.getBlockPos(), 8)) return;
                if (player.getWorld().getBlockEntity(pos) instanceof PlatformControllerBlockEntity be) {
                    if (!be.canConfigureLocked(player)) { denyOwner(player); return; }
                    be.applySoundSettings(enabled, start, stop, arrive, hum);
                }
            });
        });

        Net.registerServerReceiver(PLATFORM_MAIN_GUI, (player, buf) -> { MinecraftServer server = player.getServer();
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                if (!pos.isWithinDistance(player.getBlockPos(), 8)) return;
                if (player.getWorld().getBlockEntity(pos) instanceof PlatformControllerBlockEntity be) {
                    if (!be.canConfigureLocked(player)) { denyOwner(player); return; }
                    player.openHandledScreen(be);
                }
            });
        });

        Net.registerServerReceiver(PLATFORM_SENSOR_GUI, (player, buf) -> { MinecraftServer server = player.getServer();
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                if (!pos.isWithinDistance(player.getBlockPos(), 8)) return;
                if (player.getWorld().getBlockEntity(pos) instanceof PlatformControllerBlockEntity be
                        && !be.canConfigureLocked(player)) { denyOwner(player); return; }
                openSensorMenu(player, pos);
            });
        });

        Net.registerServerReceiver(PLATFORM_SENSOR, (player, buf) -> { MinecraftServer server = player.getServer();
            BlockPos pos = buf.readBlockPos();
            boolean on = buf.readBoolean();
            int radius = buf.readVarInt();
            boolean players = buf.readBoolean();
            boolean mobs = buf.readBoolean();
            boolean invert = buf.readBoolean();
            String names = buf.readString(96);
            int autoClose = buf.readVarInt();
            server.execute(() -> {
                if (!pos.isWithinDistance(player.getBlockPos(), 8)) return;
                if (player.getWorld().getBlockEntity(pos) instanceof PlatformControllerBlockEntity be) {
                    if (!be.canConfigureLocked(player)) { denyOwner(player); return; }
                    be.applySensorSettings(on, radius, players, mobs, invert, names, autoClose);
                }
            });
        });

        Net.registerServerReceiver(PLATFORM_LOCK_GUI, (player, buf) -> { MinecraftServer server = player.getServer();
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                if (!pos.isWithinDistance(player.getBlockPos(), 8)) return;
                if (!(player.getWorld().getBlockEntity(pos) instanceof PlatformControllerBlockEntity)) return;
                openLockMenu(player, pos);
            });
        });

        Net.registerServerReceiver(PLATFORM_LOCK_SET, (player, buf) -> { MinecraftServer server = player.getServer();
            BlockPos pos = buf.readBlockPos();
            boolean on = buf.readBoolean();
            String owner = buf.readString(24);
            String trusted = buf.readString(96);
            server.execute(() -> {
                if (!pos.isWithinDistance(player.getBlockPos(), 8)) return;
                if (player.getWorld().getBlockEntity(pos) instanceof PlatformControllerBlockEntity be) {
                    String self = player.getName().getString();

                    if (be.isLockOn() && !be.getLockOwner().isEmpty() && !be.isLockOwner(self)) {
                        denyOwner(player);
                        return;
                    }

                    String ownerToApply = owner;
                    if (be.isLockOn() && be.getLockOwner().isEmpty() && !be.isLockOwner(self)) {
                        String clean = PlatformControllerBlockEntity.sanitizeNames(owner)
                                .replace(",", " ").trim().split("\\s+")[0];
                        if (!clean.equalsIgnoreCase(self)
                                && server.getUserCache().findByName(clean).isEmpty()) {
                            ownerToApply = be.getLockOwner();
                        }
                    }
                    be.applyLockSettings(on, ownerToApply, trusted);
                }
            });
        });

        Net.registerServerReceiver(PLATFORM_CASCADE_GUI, (player, buf) -> { MinecraftServer server = player.getServer();
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                if (!pos.isWithinDistance(player.getBlockPos(), 8)) return;
                if (player.getWorld().getBlockEntity(pos) instanceof PlatformControllerBlockEntity be
                        && !be.canConfigureLocked(player)) { denyOwner(player); return; }
                openCascadeMenu(player, pos);
            });
        });

        Net.registerServerReceiver(PLATFORM_CASCADE_SET, (player, buf) -> { MinecraftServer server = player.getServer();
            BlockPos pos = buf.readBlockPos();
            boolean on = buf.readBoolean();
            int delay = buf.readVarInt();
            Boolean invert = buf.isReadable() ? buf.readBoolean() : null;
            server.execute(() -> {
                if (!pos.isWithinDistance(player.getBlockPos(), 8)) return;
                if (player.getWorld().getBlockEntity(pos) instanceof PlatformControllerBlockEntity be) {
                    if (!be.canConfigureLocked(player)) { denyOwner(player); return; }

                    be.applyCascadeSettings(on, delay, invert != null ? invert : be.isCascadeInvert());
                }
            });
        });

        Net.registerServerReceiver(GUI_SENSOR_ZONE, (player, buf) -> { MinecraftServer server = player.getServer();
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                if (!pos.isWithinDistance(player.getBlockPos(), 8)) return;
                if (player.getWorld().getBlockEntity(pos) instanceof PlatformControllerBlockEntity be0
                        && !be0.canConfigureLocked(player)) { denyOwner(player); return; }
                zoneSelecting.put(player.getUuid(), pos);
                player.closeHandledScreen();
                sendZoneSync(player, pos, true);
                player.sendMessage(Text.translatable("message.slidingplatforms.zone_pick"), true);
            });
        });

        Net.registerServerReceiver(ZONE_SELECT, (player, buf) -> { MinecraftServer server = player.getServer();
            BlockPos ctrlPos = buf.readBlockPos();
            BlockPos a = buf.readBlockPos();
            BlockPos b = buf.readBlockPos();
            server.execute(() -> {
                BlockPos expected = zoneSelecting.get(player.getUuid());
                if (expected == null || !expected.equals(ctrlPos)) return;
                if (!ctrlPos.isWithinDistance(player.getBlockPos(), 32)) return;

                long dx = Math.abs(a.getX() - b.getX()) + 1L;
                long dy = Math.abs(a.getY() - b.getY()) + 1L;
                long dz = Math.abs(a.getZ() - b.getZ()) + 1L;
                if (dx * dy * dz > 512) {
                    player.sendMessage(Text.translatable("message.slidingplatforms.box_too_big"), true);
                    return;
                }
                if (player.getWorld().getBlockEntity(ctrlPos) instanceof PlatformControllerBlockEntity be) {
                    if (!be.canConfigureLocked(player)) { denyOwner(player); return; }
                    be.applySensorZone(a, b);
                    int[] d = be.zoneDims();
                    player.sendMessage(Text.translatable("message.slidingplatforms.zone_set", d[0], d[1], d[2]), true);
                    sendZoneSync(player, ctrlPos, true);
                }
            });
        });

        Net.registerServerReceiver(ZONE_CLEAR, (player, buf) -> { MinecraftServer server = player.getServer();
            BlockPos pos = buf.readBlockPos();
            server.execute(() -> {
                if (!pos.isWithinDistance(player.getBlockPos(), 8)) return;
                if (player.getWorld().getBlockEntity(pos) instanceof PlatformControllerBlockEntity be) {
                    if (!be.canConfigureLocked(player)) { denyOwner(player); return; }
                    be.clearSensorZone();
                }
            });
        });

        Net.registerServerReceiver(BIND_SCREEN, (player, buf) -> { MinecraftServer server = player.getServer();
            BlockPos controllerPos = buf.readBlockPos();
            String key = buf.readString(64);
            server.execute(() -> {
                if (!controllerPos.isWithinDistance(player.getBlockPos(), 8)) return;
                if (player.getWorld().getBlockEntity(controllerPos) instanceof PlatformControllerBlockEntity be) {
                    if (!be.canConfigureLocked(player)) { denyOwner(player); return; }
                    be.bindScreen(key, player);
                }
            });
        });

        Net.registerServerReceiver(RIDE_TO, (player, buf) -> { MinecraftServer server = player.getServer();
            BlockPos screenPos = buf.readBlockPos();
            BlockPos targetCtrl = buf.readBlockPos();
            server.execute(() -> {
                if (!screenPos.isWithinDistance(player.getBlockPos(), 16)) return;
                if (player.getWorld().getBlockEntity(screenPos) instanceof ElevatorScreenBlockEntity be) {
                    be.requestRide(targetCtrl, player);
                }
            });
        });

        Net.registerServerReceiver(SCREEN_NAME, (player, buf) -> { MinecraftServer server = player.getServer();
            BlockPos screenPos = buf.readBlockPos();
            String name = buf.readString(24);
            server.execute(() -> {
                if (!screenPos.isWithinDistance(player.getBlockPos(), 8)) return;
                if (player.getWorld().getBlockEntity(screenPos) instanceof ElevatorScreenBlockEntity be) {
                    be.setScreenName(name);
                }
            });
        });

        Net.registerServerReceiver(SCREEN_FLOOR_NUM, (player, buf) -> { MinecraftServer server = player.getServer();
            BlockPos screenPos = buf.readBlockPos();
            BlockPos ctrlPos = buf.readBlockPos();
            int num = buf.readVarInt();
            server.execute(() -> {
                if (!screenPos.isWithinDistance(player.getBlockPos(), 8)) return;
                if (player.getWorld().getBlockEntity(screenPos) instanceof ElevatorScreenBlockEntity be) {
                    be.setFloorNumber(ctrlPos, num);
                }
            });
        });

        Net.registerServerReceiver(SCREEN_FLOOR_DEL, (player, buf) -> { MinecraftServer server = player.getServer();
            BlockPos screenPos = buf.readBlockPos();
            BlockPos ctrlPos = buf.readBlockPos();
            server.execute(() -> {
                if (!screenPos.isWithinDistance(player.getBlockPos(), 8)) return;
                if (player.getWorld().getBlockEntity(screenPos) instanceof ElevatorScreenBlockEntity be) {
                    String nm = PlatformRegistry.nameOf(player.getWorld(), ctrlPos);
                    be.removeFloor(ctrlPos);
                    player.sendMessage(Text.translatable("message.slidingplatforms.floor_removed",
                            nm != null ? nm : ctrlPos.toShortString()), true);
                    openScreenSettings(player, be, false);
                }
            });
        });

        Net.registerServerReceiver(CHAIN_RENAME, (player, buf) -> { MinecraftServer server = player.getServer();
            BlockPos screenPos = buf.readBlockPos();
            String name = buf.readString(24);
            server.execute(() -> {
                if (!screenPos.isWithinDistance(player.getBlockPos(), 8)) return;
                if (player.getWorld().getBlockEntity(screenPos) instanceof ElevatorScreenBlockEntity be) {
                    if (name.isEmpty() && be.getChain().isEmpty()) return;
                    be.setChainName(name);
                    openScreenSettings(player, be, true);
                }
            });
        });

        Net.registerServerReceiver(SCREEN_CALL, (player, buf) -> { MinecraftServer server = player.getServer();
            BlockPos screenPos = buf.readBlockPos();
            int num = buf.readVarInt();
            server.execute(() -> {
                if (!screenPos.isWithinDistance(player.getBlockPos(), 8)) return;
                if (player.getWorld().getBlockEntity(screenPos) instanceof ElevatorScreenBlockEntity be) {
                    int ok = 0;
                    if (num > 0) {
                        for (ElevatorScreenBlockEntity.Floor f : be.floors()) {
                            if (f.number() == num) { ok = num; break; }
                        }
                    }
                    be.setCallFloor(ok);
                }
            });
        });

        Net.registerServerReceiver(CHAIN_LINK, (player, buf) -> { MinecraftServer server = player.getServer();
            BlockPos screenPos = buf.readBlockPos();
            String target = buf.readString(64);
            server.execute(() -> {
                if (!screenPos.isWithinDistance(player.getBlockPos(), 8)) return;
                if (player.getWorld().getBlockEntity(screenPos) instanceof ElevatorScreenBlockEntity be) {
                    if (target.isEmpty()) {
                        be.leaveChain();
                        player.sendMessage(Text.translatable("message.slidingplatforms.chain_left"), true);
                    } else {
                        String before = be.getChain();
                        be.linkTo(target);
                        String nm = be.getChainName();
                        if (!be.getChain().equals(before) && !nm.isEmpty()) {
                            player.sendMessage(Text.translatable("message.slidingplatforms.chain_joined", nm), true);
                        }
                    }
                    openScreenSettings(player, be, true);
                }
            });
        });

        Net.registerServerReceiver(CLIENT_LANG, (player, buf) -> { MinecraftServer server = player.getServer();
            String lang = buf.readString(8);
            server.execute(() -> ClientLanguages.put(player.getUuid(), lang));
        });

        Net.registerServerReceiver(SOUND_HELLO, (player, buf) -> { MinecraftServer server = player.getServer();
            String host = buf.readString(255);
            server.execute(() -> SoundPackService.onHello(player, host));
        });

        Net.registerServerReceiver(SOUND_UP_BEGIN, (player, buf) -> { MinecraftServer server = player.getServer();
            String base = buf.readString(80);
            int size = buf.readInt();
            String sha = buf.readString(40);
            server.execute(() -> SoundPackService.onUploadBegin(player, base, size, sha));
        });
        Net.registerServerReceiver(SOUND_UP_CHUNK, (player, buf) -> { MinecraftServer server = player.getServer();
            byte[] chunk = buf.readByteArray(8200);
            server.execute(() -> SoundPackService.onUploadChunk(player, chunk));
        });

        Net.registerServerReceiver(SWITCH_TOGGLE, (player, buf) -> { MinecraftServer server = player.getServer();
            BlockPos switchPos = buf.readBlockPos();
            BlockPos controllerPos = buf.readBlockPos();
            server.execute(() -> {
                if (!switchPos.isWithinDistance(player.getBlockPos(), 8)) return;
                if (player.getWorld().getBlockEntity(switchPos) instanceof RemoteSwitchBlockEntity be) {
                    be.toggleTarget(controllerPos);
                }
            });
        });
    }

    public static void openSoundsMenu(PlayerEntity player, BlockPos ctrlPos) {
        player.openHandledScreen(new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<net.minecraft.network.PacketByteBuf>() {
            @Override
            public net.minecraft.network.PacketByteBuf getScreenOpeningData(ServerPlayerEntity p) {
                net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
                buf.writeBlockPos(ctrlPos);
                boolean en = true;
                String s1 = "", s2 = "", s3 = "", s4 = "";
                if (player.getWorld().getBlockEntity(ctrlPos) instanceof PlatformControllerBlockEntity be) {
                    en = be.soundsEnabled();
                    s1 = be.getSndStart();
                    s2 = be.getSndStop();
                    s3 = be.getSndArrive();
                    s4 = be.getSndHum();
                }
                buf.writeBoolean(en);
                buf.writeString(s1);
                buf.writeString(s2);
                buf.writeString(s3);
                buf.writeString(s4);
                return buf;
            }

            @Override
            public Text getDisplayName() {
                return Text.translatable("gui.slidingplatforms.sounds.title");
            }

            @Override
            public net.minecraft.screen.ScreenHandler createMenu(int syncId,
                                                                 net.minecraft.entity.player.PlayerInventory inv,
                                                                 PlayerEntity p2) {
                return new PlatformSoundsScreenHandler(syncId, ctrlPos, true, "", "", "", "");
            }
        });
    }

    public static void openSensorMenu(PlayerEntity player, BlockPos ctrlPos) {
        player.openHandledScreen(new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<net.minecraft.network.PacketByteBuf>() {
            @Override
            public net.minecraft.network.PacketByteBuf getScreenOpeningData(ServerPlayerEntity p) {
                net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
                buf.writeBlockPos(ctrlPos);
                boolean on = false, players = true, mobs = true, invert = false;
                int radius = 3;
                String names = "";
                int[] zone = {0, 0, 0};
                if (player.getWorld().getBlockEntity(ctrlPos) instanceof PlatformControllerBlockEntity be) {
                    on = be.sensorOn(); radius = be.sensorRadius();
                    players = be.sensorPlayers(); mobs = be.sensorMobs();
                    invert = be.sensorInvert(); names = be.sensorNames();
                    zone = be.zoneDims();
                }
                int autoClose = 0;
                if (player.getWorld().getBlockEntity(ctrlPos) instanceof PlatformControllerBlockEntity be) {
                    autoClose = be.getAutoClose();
                }
                buf.writeBoolean(on);
                buf.writeVarInt(radius);
                buf.writeBoolean(players);
                buf.writeBoolean(mobs);
                buf.writeBoolean(invert);
                buf.writeString(names);
                buf.writeVarInt(zone[0]);
                buf.writeVarInt(zone[1]);
                buf.writeVarInt(zone[2]);
                buf.writeVarInt(autoClose);
                return buf;
            }

            @Override
            public Text getDisplayName() {
                return Text.translatable("gui.slidingplatforms.sensor.title");
            }

            @Override
            public net.minecraft.screen.ScreenHandler createMenu(int syncId,
                                                                 net.minecraft.entity.player.PlayerInventory inv,
                                                                 PlayerEntity p2) {
                return new PlatformSensorScreenHandler(syncId, ctrlPos, false, 3, true, true, false, "", 0, 0, 0, 0);
            }
        });
    }

    public static void openLockMenu(PlayerEntity player, BlockPos ctrlPos) {
        player.openHandledScreen(new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<net.minecraft.network.PacketByteBuf>() {
            @Override
            public net.minecraft.network.PacketByteBuf getScreenOpeningData(ServerPlayerEntity p) {
                net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
                buf.writeBlockPos(ctrlPos);
                boolean on = false;
                String owner = "", trusted = "";
                if (player.getWorld().getBlockEntity(ctrlPos) instanceof PlatformControllerBlockEntity be) {
                    on = be.isLockOn();
                    owner = be.getLockOwner();
                    trusted = be.getLockTrusted();
                }
                buf.writeBoolean(on);
                buf.writeString(owner);
                buf.writeString(trusted);
                return buf;
            }

            @Override
            public Text getDisplayName() {
                return Text.translatable("gui.slidingplatforms.lock.title");
            }

            @Override
            public net.minecraft.screen.ScreenHandler createMenu(int syncId,
                                                                 net.minecraft.entity.player.PlayerInventory inv,
                                                                 PlayerEntity p2) {
                return new PlatformLockScreenHandler(syncId, ctrlPos, false, "", "");
            }
        });
    }

    public static void openCascadeMenu(PlayerEntity player, BlockPos ctrlPos) {
        player.openHandledScreen(new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<net.minecraft.network.PacketByteBuf>() {
            @Override
            public net.minecraft.network.PacketByteBuf getScreenOpeningData(ServerPlayerEntity p) {
                net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
                buf.writeBlockPos(ctrlPos);
                boolean on = false;
                int delay = 2;
                boolean invert = false;
                if (player.getWorld().getBlockEntity(ctrlPos) instanceof PlatformControllerBlockEntity be) {
                    on = be.isCascadeOn();
                    delay = be.getCascadeDelay();
                    invert = be.isCascadeInvert();
                }
                buf.writeBoolean(on);
                buf.writeVarInt(delay);
                buf.writeBoolean(invert);
                return buf;
            }

            @Override
            public Text getDisplayName() {
                return Text.translatable("gui.slidingplatforms.cascade.title");
            }

            @Override
            public net.minecraft.screen.ScreenHandler createMenu(int syncId,
                                                                 net.minecraft.entity.player.PlayerInventory inv,
                                                                 PlayerEntity p2) {
                return new PlatformCascadeScreenHandler(syncId, ctrlPos, false, 2, false);
            }
        });
    }

    public static boolean canEditConfig(net.minecraft.entity.Entity actor,
                                        net.minecraft.server.MinecraftServer server) {
        if (actor == null) return false;
        if (actor.hasPermissionLevel(2)) return true;
        return actor instanceof ServerPlayerEntity sp && server.isHost(sp.getGameProfile());
    }

    public static void openConfigMenu(ServerPlayerEntity player) {
        player.openHandledScreen(new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<net.minecraft.network.PacketByteBuf>() {
            @Override
            public net.minecraft.network.PacketByteBuf getScreenOpeningData(ServerPlayerEntity p) {
                net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
                buf.writeString(SlidingPlatformsConfig.toJson());
                return buf;
            }

            @Override
            public Text getDisplayName() {
                return Text.translatable("gui.slidingplatforms.cfg.title");
            }

            @Override
            public net.minecraft.screen.ScreenHandler createMenu(int syncId,
                                                                 net.minecraft.entity.player.PlayerInventory inv,
                                                                 PlayerEntity p2) {
                return new ConfigScreenHandler(syncId, SlidingPlatformsConfig.toJson());
            }
        });
    }

    public static void openFloorsMenu(PlayerEntity player, ElevatorScreenBlockEntity screen) {
        player.openHandledScreen(new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<net.minecraft.network.PacketByteBuf>() {
            @Override
            public net.minecraft.network.PacketByteBuf getScreenOpeningData(ServerPlayerEntity p) {
                net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
                buf.writeBlockPos(screen.getPos());
                java.util.List<ElevatorScreenBlockEntity.Floor> fs = screen.floors();
                buf.writeVarInt(fs.size());
                for (ElevatorScreenBlockEntity.Floor f : fs) {
                    buf.writeVarInt(f.number());
                    buf.writeBlockPos(f.ctrlPos());
                    buf.writeString(f.name());
                    boolean cabin = player.getWorld()
                            .getBlockEntity(f.ctrlPos()) instanceof PlatformControllerBlockEntity c
                            && c.isCabinPresent();
                    buf.writeBoolean(cabin);
                }
                return buf;
            }

            @Override
            public Text getDisplayName() {
                return Text.literal(screen.getScreenName());
            }

            @Override
            public net.minecraft.screen.ScreenHandler createMenu(int syncId,
                                                                 net.minecraft.entity.player.PlayerInventory inv,
                                                                 PlayerEntity p2) {
                return new FloorSelectScreenHandler(syncId, screen.getPos());
            }
        });
    }

    public static void openScreenSettings(PlayerEntity player, ElevatorScreenBlockEntity screen) {
        openScreenSettings(player, screen, false);
    }

    public static void openScreenSettings(PlayerEntity player, ElevatorScreenBlockEntity screen,
                                          boolean chainTab) {
        player.openHandledScreen(new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<net.minecraft.network.PacketByteBuf>() {
            @Override
            public net.minecraft.network.PacketByteBuf getScreenOpeningData(ServerPlayerEntity p) {
                net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
                net.minecraft.world.World world = screen.getWorld();
                String selfPosKey = Long.toString(screen.getPos().asLong());
                String selfChain = screen.getChain();

                buf.writeBlockPos(screen.getPos());
                buf.writeString(screen.getScreenName());
                buf.writeString(screen.getChainName());

                java.util.List<ElevatorScreenBlockEntity.Floor> fs = screen.floors();
                buf.writeVarInt(fs.size());
                for (ElevatorScreenBlockEntity.Floor f : fs) {
                    buf.writeBlockPos(f.ctrlPos());
                    buf.writeVarInt(f.number());
                    buf.writeString(f.name());
                }

                java.util.List<ScreenSettingsScreenHandler.LinkRow> links = new java.util.ArrayList<>();
                for (ChainRegistry.Entry c : ChainRegistry.list(world)) {
                    if (!c.id().equals(selfChain)) links.add(
                            new ScreenSettingsScreenHandler.LinkRow("chain:" + c.id(), c.name()));
                }
                for (ScreenRegistry.Entry s : ScreenRegistry.list(world)) {
                    String key = Long.toString(s.pos().asLong());
                    if (key.equals(selfPosKey)) continue;
                    if (ChainRegistry.chainOfScreen(world, key) != null) continue;
                    links.add(new ScreenSettingsScreenHandler.LinkRow(key, s.name()));
                }
                buf.writeVarInt(links.size());
                for (ScreenSettingsScreenHandler.LinkRow l : links) {
                    buf.writeString(l.key());
                    buf.writeString(l.name());
                }
                buf.writeBoolean(chainTab);
                buf.writeVarInt(screen.getCallFloor());
                return buf;
            }

            @Override
            public Text getDisplayName() {
                return Text.translatable("gui.slidingplatforms.settings_title");
            }

            @Override
            public net.minecraft.screen.ScreenHandler createMenu(int syncId,
                                                                 net.minecraft.entity.player.PlayerInventory inv,
                                                                 PlayerEntity p2) {
                return new ScreenSettingsScreenHandler(syncId, screen.getPos(),
                        screen.getScreenName(), screen.getChainName(), screen.getCallFloor());
            }
        });
    }

    private static void openScreenBindMenu(ServerPlayerEntity player, BlockPos controllerPos) {
        net.minecraft.world.World world = player.getWorld();
        player.openHandledScreen(new net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<net.minecraft.network.PacketByteBuf>() {
            @Override
            public net.minecraft.network.PacketByteBuf getScreenOpeningData(ServerPlayerEntity p) {
                net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
                buf.writeBlockPos(controllerPos);
                java.util.List<ScreenSelectScreenHandler.ScreenRow> rows = new java.util.ArrayList<>();
                for (ChainRegistry.Entry c : ChainRegistry.list(world)) {
                    rows.add(new ScreenSelectScreenHandler.ScreenRow("chain:" + c.id(), c.name()));
                }
                for (ScreenRegistry.Entry s : ScreenRegistry.list(world)) {
                    String key = Long.toString(s.pos().asLong());
                    if (ChainRegistry.chainOfScreen(world, key) != null) continue;
                    rows.add(new ScreenSelectScreenHandler.ScreenRow(key, s.name()));
                }
                buf.writeVarInt(rows.size());
                for (ScreenSelectScreenHandler.ScreenRow r : rows) {
                    buf.writeString(r.key());
                    buf.writeString(r.name());
                }
                return buf;
            }

            @Override
            public Text getDisplayName() {
                return Text.translatable("gui.slidingplatforms.screens_title");
            }

            @Override
            public net.minecraft.screen.ScreenHandler createMenu(int syncId,
                                                                 net.minecraft.entity.player.PlayerInventory inv,
                                                                 PlayerEntity p2) {
                return new ScreenSelectScreenHandler(syncId, controllerPos);
            }
        });
    }

    private void registerEvents() {

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient || hand != Hand.MAIN_HAND) return ActionResult.PASS;
            BlockPos controllerPos = selecting.get(player.getUuid());
            if (controllerPos == null) return ActionResult.PASS;
            if (player.isSneaking()) return ActionResult.PASS;
            if (!player.getMainHandStack().isEmpty()) return ActionResult.PASS;

            if (hit.getBlockPos().equals(controllerPos)) return ActionResult.SUCCESS;

            BlockEntity be = world.getBlockEntity(controllerPos);
            if (be instanceof PlatformControllerBlockEntity cbe) {
                int count = cbe.manualToggle(hit.getBlockPos());
                if (count == -2) {
                    player.sendMessage(Text.translatable("message.slidingplatforms.must_be_closed"), true);
                } else if (count == -1) {
                    player.sendMessage(Text.translatable("message.slidingplatforms.select_full"), true);
                } else {
                    player.sendMessage(Text.translatable("message.slidingplatforms.select_count", count), true);
                    sendSelectionSync(player, controllerPos, cbe.currentManualPositions());
                }
            }
            return ActionResult.SUCCESS;
        });

        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> {
                    selecting.remove(handler.player.getUuid());
                    zoneSelecting.remove(handler.player.getUuid());
                });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            if (world.isClient) return true;
            if (be instanceof PlatformControllerBlockEntity platform && !platform.canConfigureLocked(player)) {
                player.sendMessage(Text.translatable("message.slidingplatforms.lock_break"), true);
                return false;
            }
            return true;
        });

        EntityTrackingEvents.START_TRACKING.register(SlidingPlatformEntity::onStartTracking);
    }
}

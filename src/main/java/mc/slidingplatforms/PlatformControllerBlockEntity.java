package mc.slidingplatforms;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PlatformControllerBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory {

    private static class Scan {
        BlockPos min;
        Vec3i size;
        List<BlockPos> cells;
        int extentAlongSlide;
    }

    private @Nullable Direction slideDir;
    private boolean open = false;
    private int openDistance = 0;
    private boolean wasPowered = false;

    private @Nullable Direction anchorDir;

    private String name = "";

    private String boundScreen = "";

    private int slideOffset = 0;
    private float speed = 0f;

    private boolean soundsEnabled = SlidingPlatformsConfig.VALUES.defaultSounds;
    private String sndStart = "";
    private String sndStop = "";
    private String sndArrive = "";
    private String sndHum = "";

    private boolean cascadeOn = false;
    private int cascadeDelay = 2;
    private boolean cascadeInvert = false;

    private boolean sensOn = false;
    private int sensRadius = 3;

    private int autoClose = 0;
    private long openedAtTick = 0;

    private boolean lampGlow = SlidingPlatformsConfig.VALUES.defaultLampGlow;
    private final java.util.Set<Long> litLamps = new java.util.HashSet<>();

    private boolean lockOn = false;
    private String lockOwner = "";
    private String lockTrusted = "";
    private boolean sensPlayers = true;
    private boolean sensMobs = true;
    private boolean sensInvert = false;
    private String sensNames = "";

    private BlockPos zoneMin = null;
    private BlockPos zoneMax = null;

    public static final int RS_IMPULSE = 0, RS_LEVEL = 1, RS_LOCK = 2, RS_OFF = 3;
    private int redstoneMode = RS_IMPULSE;
    private boolean wasBusy = false;

    private int[] manualOffsets = new int[0];
    private BlockPos manualMin = BlockPos.ORIGIN;

    public static final int MANUAL_CAP = 432;

    private static int manualCap() {
        SlidingPlatformsConfig.Values c = SlidingPlatformsConfig.VALUES;
        return Math.max(MANUAL_CAP, c.maxWidth * c.maxHeight * c.maxDepth);
    }

    public PlatformControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.PLATFORM_CONTROLLER_BE, pos, state);
    }

    private transient String placementWord;

    public void usePlacementWord(String word) { this.placementWord = word; }

    public String getPlatformName() {
        if (getWorld() instanceof ServerWorld sw && (name == null || name.isBlank())) {
            name = placementWord != null
                    ? PlatformRegistry.ensureName(sw, pos, name, placementWord)
                    : PlatformRegistry.ensureName(sw, pos, name);
            markDirty();
        } else if (getWorld() instanceof ServerWorld sw) {
            name = PlatformRegistry.ensureName(sw, pos, name);
        }
        return name;
    }

    public void setPlatformName(String newName) {
        if (newName == null || newName.isBlank()) return;
        this.name = newName.trim();
        if (this.name.length() > 24) this.name = this.name.substring(0, 24);
        if (getWorld() instanceof ServerWorld sw) PlatformRegistry.rename(sw, pos, this.name);
        markDirty();
    }

    public void onBroken() {
        if (getWorld() instanceof ServerWorld sw) {
            LampGlow.clear(sw, litLamps);
        }
        if (getWorld() != null) {
            PlatformRegistry.unregister(getWorld(), pos);
            detachFromChainIfAny();
        }
    }

    public String getBoundScreen() { return boundScreen; }

    public void setBoundScreen(String key) {
        boundScreen = key == null ? "" : key;
        markDirty();
    }

    public void detachFromChainIfAny() {
        World world = getWorld();
        if (world == null || !boundScreen.startsWith("chain:")) return;
        String id = boundScreen.substring(6);
        ChainRegistry.leave(world, id, Long.toString(pos.asLong()));
        ChainRegistry.forgetFloor(world, id, Long.toString(pos.asLong()));
    }

    public void bindScreen(@Nullable String key, @Nullable PlayerEntity player) {
        World world = getWorld();

        detachFromChainIfAny();
        this.boundScreen = key == null ? "" : key;
        markDirty();
        if (key == null || key.isEmpty() || world == null) {
            say(player, "message.slidingplatforms.unbound_screen");
            return;
        }

        if (key.startsWith("chain:")) {
            String id = key.substring(6);
            String chainName = ChainRegistry.nameOf(world, id);
            if (chainName == null) {
                this.boundScreen = "";
                markDirty();
                say(player, "message.slidingplatforms.screen_missing");
                return;
            }
            String ctrlKey = Long.toString(pos.asLong());
            ChainRegistry.join(world, id, ctrlKey);

            for (String mk : ChainRegistry.members(world, id)) {
                if (mk.startsWith("chain:")) continue;
                try {
                    if (world.getBlockEntity(BlockPos.fromLong(Long.parseLong(mk)))
                            instanceof ElevatorScreenBlockEntity member) {
                        member.trackController(pos);
                        break;
                    }
                } catch (NumberFormatException ignored) {  }
            }
            say(player, "message.slidingplatforms.bound_chain", chainName);
            return;
        }

        BlockPos screenPos;
        try {
            screenPos = BlockPos.fromLong(Long.parseLong(key));
        } catch (NumberFormatException bad) {
            this.boundScreen = "";
            markDirty();
            say(player, "message.slidingplatforms.screen_missing");
            return;
        }
        if (!(world.getBlockEntity(screenPos) instanceof ElevatorScreenBlockEntity screen)) {
            this.boundScreen = "";
            markDirty();
            say(player, "message.slidingplatforms.screen_missing");
            return;
        }
        screen.trackController(pos);
        say(player, "message.slidingplatforms.bound_screen", screen.getScreenName());
    }

    public String boundScreenName() {
        if (boundScreen.isEmpty() || getWorld() == null) return "";
        if (boundScreen.startsWith("chain:")) {
            String n = ChainRegistry.nameOf(getWorld(), boundScreen.substring(6));
            return n == null ? "" : "⟟ " + n;
        }
        try {
            String n = ScreenRegistry.nameOf(getWorld(),
                    BlockPos.fromLong(Long.parseLong(boundScreen)));
            return n != null ? n : "";
        } catch (NumberFormatException bad) {
            return "";
        }
    }

    public record LocalDir(Direction.Axis axis, boolean positive) {}

    public Direction getAnchorDir() {
        if (anchorDir == null) {

            Direction facing = getCachedState().get(PlatformControllerBlock.FACING);
            anchorDir = facing.getAxis().isHorizontal() ? facing.getOpposite() : Direction.NORTH;
        }
        return anchorDir;
    }

    public void setAnchorDir(Direction playerHorizontalFacing) {
        if (playerHorizontalFacing != null && playerHorizontalFacing.getAxis().isHorizontal()) {
            this.anchorDir = playerHorizontalFacing;
            markDirty();
        }
    }

    public static Direction localToWorld(Direction.Axis axis, boolean positive, Direction anchor) {
        return switch (axis) {
            case Y -> positive ? Direction.UP : Direction.DOWN;
            case Z -> positive ? anchor : anchor.getOpposite();
            case X -> positive ? anchor.rotateYClockwise() : anchor.rotateYCounterclockwise();
        };
    }

    public static LocalDir worldToLocal(Direction worldDir, Direction anchor) {
        if (worldDir == Direction.UP) return new LocalDir(Direction.Axis.Y, true);
        if (worldDir == Direction.DOWN) return new LocalDir(Direction.Axis.Y, false);
        if (worldDir == anchor) return new LocalDir(Direction.Axis.Z, true);
        if (worldDir == anchor.getOpposite()) return new LocalDir(Direction.Axis.Z, false);
        if (worldDir == anchor.rotateYClockwise()) return new LocalDir(Direction.Axis.X, true);
        return new LocalDir(Direction.Axis.X, false);
    }

    public Direction getSlideDir() {
        if (slideDir == null) {
            slideDir = getAnchorDir().rotateYClockwise();
        }
        return slideDir;
    }

    public int getSlideOffset() { return slideOffset; }
    public boolean isOpen() { return open; }

    public float getSpeed() {
        return speed > 0.0 ? speed : (float) SlidingPlatformsConfig.VALUES.speed;
    }

    public boolean applySettings(Direction.Axis localAxis, boolean positive,
                                 int offset, float newSpeed, String newName, int rsMode) {
        Direction world = localToWorld(localAxis, positive, getAnchorDir());
        boolean dirAccepted = true;
        if (open && world != getSlideDir()) {
            dirAccepted = false;
        } else {
            this.slideDir = world;
        }

        this.slideOffset = Math.max(0, Math.min(offset, SlidingPlatformsConfig.VALUES.maxOffset));
        this.speed = Math.max(0f, Math.min(newSpeed, (float) SlidingPlatformsConfig.VALUES.maxSpeed));

        this.redstoneMode = (rsMode >= RS_IMPULSE && rsMode <= RS_OFF) ? rsMode : RS_IMPULSE;
        setPlatformName(newName);
        markDirty();
        SlidingPlatforms.dbg("controller {} settings: offset={} speed={} rs={} name='{}'",
                pos.toShortString(), slideOffset, speed, redstoneMode, name);
        return dirAccepted;
    }

    public int getRedstoneMode() { return redstoneMode; }

    public void applySoundSettings(boolean enabled, String start, String stop, String arrive, String hum) {
        this.soundsEnabled = enabled;
        this.sndStart = sanitizeSoundId(start);
        this.sndStop = sanitizeSoundId(stop);
        this.sndArrive = sanitizeSoundId(arrive);
        this.sndHum = sanitizeSoundId(hum);
        markDirty();
    }

    public void applySensorSettings(boolean on, int radius, boolean players, boolean mobs,
                                    boolean invert, String names, int newAutoClose) {
        this.sensOn = on;
        this.sensRadius = Math.max(1, Math.min(16, radius));
        this.sensPlayers = players;
        this.sensMobs = mobs;
        this.sensInvert = invert;
        this.sensNames = sanitizeNames(names);
        this.autoClose = Math.max(0, Math.min(60, newAutoClose));
        markDirty();
    }

    public int getAutoClose() { return autoClose; }

    public void applyLockSettings(boolean on, String owner, String trusted) {
        this.lockOn = on;
        this.lockOwner = sanitizeNames(owner).replace(",", " ").trim().split("\\s+")[0];
        this.lockTrusted = sanitizeNames(trusted);
        markDirty();
    }

    public boolean isLockOn() { return lockOn; }
    public String getLockOwner() { return lockOwner; }
    public String getLockTrusted() { return lockTrusted; }

    public boolean isLockOwner(String name) {
        return !lockOwner.isEmpty() && lockOwner.equalsIgnoreCase(name);
    }

    public boolean isLockTrusted(String name) {
        if (name == null || name.isEmpty()) return false;
        for (String n : lockTrusted.split(",")) {
            if (n.trim().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    public boolean canOperateLocked(@Nullable PlayerEntity player) {
        if (!lockOn || player == null) return true;

        if (lockOwner.isEmpty() && lockTrusted.isEmpty()) return true;
        String n = player.getName().getString();
        return isLockOwner(n) || isLockTrusted(n);
    }

    public boolean canConfigureLocked(@Nullable PlayerEntity player) {
        if (!lockOn || player == null) return true;

        if (lockOwner.isEmpty()) return true;
        return isLockOwner(player.getName().getString());
    }

    public static String sanitizeNames(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder(96);
        for (char c : raw.trim().toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '_' || c == ',' || c == ' ') sb.append(c);
            if (sb.length() >= 96) break;
        }
        return sb.toString();
    }

    public boolean sensorOn() { return sensOn; }
    public int sensorRadius() { return sensRadius; }
    public boolean sensorPlayers() { return sensPlayers; }
    public boolean sensorMobs() { return sensMobs; }
    public boolean sensorInvert() { return sensInvert; }
    public String sensorNames() { return sensNames; }

    public void applyLampGlow(boolean on) {
        if (this.lampGlow == on) return;
        this.lampGlow = on;
        if (!on && getWorld() instanceof ServerWorld sw) LampGlow.clear(sw, litLamps);
        markDirty();
    }

    public boolean isLampGlow() { return lampGlow; }

    public void applyCascadeSettings(boolean on, int delay, boolean invert) {
        this.cascadeOn = on;
        this.cascadeDelay = Math.max(1, Math.min(4, delay));
        this.cascadeInvert = invert;
        markDirty();
    }

    public boolean isCascadeOn() { return cascadeOn; }
    public int getCascadeDelay() { return cascadeDelay; }
    public boolean isCascadeInvert() { return cascadeInvert; }

    private transient Box lastGlowBox = null;

    private void updateGlow(ServerWorld sw) {
        if (!lampGlow && litLamps.isEmpty()) { lastGlowBox = null; return; }
        Box box = currentPlatformBox(sw);
        boolean changed = box == null ? lastGlowBox != null : !box.equals(lastGlowBox);
        if (!changed && ((sw.getTime() + pos.hashCode()) & 15) != 0) return;
        LampGlow.update(sw, pos, box, lampGlow, litLamps);
        lastGlowBox = box;
    }

    private @Nullable Box currentPlatformBox(ServerWorld sw) {
        if (manualOffsets.length < 3) return null;

        Box union = null;
        for (SlidingPlatformEntity e : sw.getEntitiesByType(ModEntities.SLIDING_PLATFORM,
                new Box(pos).expand(192), e -> e.isAlive() && pos.equals(e.getControllerPos()))) {
            union = union == null ? e.getBoundingBox() : union.union(e.getBoundingBox());
        }
        if (union != null) return union;
        int maxX = 0, maxY = 0, maxZ = 0;
        for (int i = 0; i + 2 < manualOffsets.length; i += 3) {
            maxX = Math.max(maxX, manualOffsets[i]);
            maxY = Math.max(maxY, manualOffsets[i + 1]);
            maxZ = Math.max(maxZ, manualOffsets[i + 2]);
        }
        BlockPos home = cabinHome();
        return new Box(home.getX(), home.getY(), home.getZ(),
                home.getX() + maxX + 1.0, home.getY() + maxY + 1.0, home.getZ() + maxZ + 1.0);
    }

    public void applySensorZone(BlockPos a, BlockPos b) {
        zoneMin = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        zoneMax = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
        markDirty();
    }

    public void clearSensorZone() { zoneMin = null; zoneMax = null; markDirty(); }
    public boolean hasSensorZone() { return zoneMin != null; }
    public BlockPos getZoneMin() { return zoneMin; }
    public BlockPos getZoneMax() { return zoneMax; }

    public int[] zoneDims() {
        if (zoneMin == null) return new int[]{0, 0, 0};
        return new int[]{zoneMax.getX() - zoneMin.getX() + 1,
                zoneMax.getY() - zoneMin.getY() + 1,
                zoneMax.getZ() - zoneMin.getZ() + 1};
    }

    public static void serverTick(World world, BlockPos pos, PlatformControllerBlockEntity be) {
        if (world.isClient || !(world instanceof ServerWorld swTick)) return;

        be.updateGlow(swTick);

        if (((world.getTime() + pos.hashCode()) & 3) != 0) return;

        boolean busy = be.isBusy();
        if (busy != be.wasBusy) {
            be.wasBusy = busy;
            world.updateComparators(pos, be.getCachedState().getBlock());
        }
        if (busy) return;

        boolean powered = be.redstoneMode != RS_OFF && world.isReceivingRedstonePower(pos);

        if (be.redstoneMode == RS_LOCK && powered) {
            if (be.open) be.doTrigger(null);
            return;
        }

        boolean sensorWant = false;
        if (be.sensOn) {
            boolean found = be.detectTarget(world);
            sensorWant = be.sensInvert ? !found : found;
        }

        if (be.autoClose > 0 && be.open) {
            boolean heldOpen = (be.redstoneMode == RS_LEVEL && powered) || sensorWant;
            if (!heldOpen && world.getTime() - be.openedAtTick >= be.autoClose * 20L) {
                be.trigger(null);
                return;
            }
        }

        boolean want;
        if (be.redstoneMode == RS_LEVEL) want = powered || sensorWant;
        else if (be.sensOn) want = sensorWant;
        else return;
        if (want != be.open) be.trigger(null);
    }

    private boolean detectTarget(World world) {

        Box box = zoneMin != null ? new Box(zoneMin, zoneMax) : new Box(pos).expand(sensRadius);
        if (sensPlayers) {
            java.util.List<PlayerEntity> players = world.getEntitiesByClass(PlayerEntity.class, box,
                    e -> e.isAlive() && !e.isSpectator() && !e.isInvisible() && matchesWhitelist(e));
            if (!players.isEmpty()) return true;
        }
        if (sensMobs) {
            java.util.List<LivingEntity> mobs = world.getEntitiesByClass(LivingEntity.class, box,
                    e -> e.isAlive() && !(e instanceof PlayerEntity) && !e.isInvisible()
                            && !e.isSpectator() && !(e instanceof net.minecraft.entity.decoration.ArmorStandEntity));
            if (!mobs.isEmpty()) return true;
        }
        return false;
    }

    private boolean matchesWhitelist(PlayerEntity player) {
        if (sensNames.isEmpty()) return true;
        String nick = player.getGameProfile().getName();
        for (String name : sensNames.split(",")) {
            if (name.trim().equalsIgnoreCase(nick)) return true;
        }
        return false;
    }

    public static String sanitizeSoundId(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder(48);
        for (char c : raw.trim().toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '.' || c == '/' || c == '-' || c == ':') sb.append(c);
            if (sb.length() >= 48) break;
        }
        return sb.toString();
    }

    public boolean soundsEnabled() { return soundsEnabled; }
    public String getSndStart() { return sndStart; }
    public String getSndStop() { return sndStop; }
    public String getSndArrive() { return sndArrive; }
    public String getSndHum() { return sndHum; }

    @Override
    public Text getDisplayName() {
        return getCachedState().getBlock().getName();
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new PlatformControllerScreenHandler(syncId, this);
    }

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeByte(getSlideDir().ordinal());
        buf.writeInt(slideOffset);
        buf.writeFloat(getSpeed());
        buf.writeString(getPlatformName());
        buf.writeByte(getAnchorDir().ordinal());
        buf.writeString(boundScreenName());
        buf.writeByte(redstoneMode);
        buf.writeBoolean(lampGlow);
    }

    public void onRedstoneUpdate(boolean powered) {
        if (powered && !wasPowered && redstoneMode == RS_IMPULSE) trigger(null);
        if (powered != wasPowered) {
            wasPowered = powered;
            markDirty();
        }
    }

    public void trigger(@Nullable PlayerEntity player) {
        World world = getWorld();
        if (world == null || world.isClient) return;

        if (!canOperateLocked(player)) {
            say(player, "message.slidingplatforms.lock_denied");
            return;
        }

        if (redstoneMode == RS_LOCK && world.isReceivingRedstonePower(pos)) {
            say(player, "message.slidingplatforms.locked");
            return;
        }
        doTrigger(player);
    }

    private void doTrigger(@Nullable PlayerEntity player) {
        World world = getWorld();
        if (isBusy()) { say(player, "message.slidingplatforms.busy"); return; }

        Direction facing = getCachedState().get(PlatformControllerBlock.FACING);
        Direction dir = getSlideDir();

        if (!open) {

            if (!hasManual() && !boundScreen.isEmpty()) {
                say(player, "message.slidingplatforms.cabin_elsewhere");
                return;
            }
            if (!hasManual()) {
                Scan scan = scanPlatform(world, pos.offset(facing), dir, facing);
                if (scan == null) { say(player, "message.slidingplatforms.not_found"); return; }
                rebuildManual(scan.cells);
            }
            int distance = slideOffset > 0 ? slideOffset : listExtentAlong(dir);
            if (distance <= 0) distance = 1;

            distance = clampDistanceForFacing(dir, distance);
            if (distance <= 0) { say(player, "message.slidingplatforms.cant_that_way"); return; }

            Obstacle obstacle = findObstacle(world, manualMin, dir, distance);
            if (obstacle != null) { reportObstacle(player, obstacle); return; }

            startSliding(world, manualMin, distance, dir, true, player);
        } else {

            if (!hasManual()) { say(player, "message.slidingplatforms.not_found"); open = false; markDirty(); return; }
            BlockPos base = manualMin.offset(dir, openDistance);

            Obstacle obstacle = findObstacle(world, base, dir.getOpposite(), openDistance);
            if (obstacle != null) { reportObstacle(player, obstacle); return; }
            startSliding(world, base, openDistance, dir.getOpposite(), false, player);
        }
    }

    private int clampDistanceForFacing(Direction dir, int distance) {
        Direction facing = getCachedState().get(PlatformControllerBlock.FACING);
        Direction forbidden = facing.getOpposite();
        if (dir != forbidden) return distance;

        int best = Integer.MAX_VALUE;
        for (int i = 0; i + 2 < manualOffsets.length; i += 3) {
            BlockPos cell = manualMin.add(manualOffsets[i], manualOffsets[i + 1], manualOffsets[i + 2]);

            int p = (cell.getX() - pos.getX()) * dir.getOffsetX()
                    + (cell.getY() - pos.getY()) * dir.getOffsetY()
                    + (cell.getZ() - pos.getZ()) * dir.getOffsetZ();
            if (p <= 0) best = Math.min(best, -p - 1);
        }
        if (best == Integer.MAX_VALUE) return distance;
        return Math.min(distance, best);
    }

    private record Obstacle(BlockState state, BlockPos pos) {}

    private @Nullable Obstacle findObstacle(World world, BlockPos baseMin,
                                            Direction moveDir, int distance) {

        java.util.Set<BlockPos> own = new java.util.HashSet<>();
        for (int i = 0; i + 2 < manualOffsets.length; i += 3) {
            BlockPos p = baseMin.add(manualOffsets[i], manualOffsets[i + 1], manualOffsets[i + 2]);
            if (isPlatformMaterial(world, p)) own.add(p);
        }
        for (int i = 0; i + 2 < manualOffsets.length; i += 3) {
            BlockPos from = baseMin.add(manualOffsets[i], manualOffsets[i + 1], manualOffsets[i + 2]);
            if (!own.contains(from)) continue;
            BlockPos dest = from.offset(moveDir, distance);
            if (own.contains(dest)) continue;
            BlockState at = world.getBlockState(dest);
            if (!at.isAir() && at.getFluidState().isEmpty()) {
                return new Obstacle(at, dest);
            }
        }
        return null;
    }

    private void reportObstacle(@Nullable PlayerEntity player, Obstacle o) {
        if (player == null) return;
        player.sendMessage(Text.translatable("message.slidingplatforms.blocked_movement",
                o.state().getBlock().getName(),
                o.pos().getX(), o.pos().getY(), o.pos().getZ()), false);
    }

    private void startSliding(World world, BlockPos baseMin, int distance,
                              Direction moveDir, boolean toOpen, @Nullable PlayerEntity player) {

        List<BlockPos> cells = new ArrayList<>();
        for (int i = 0; i + 2 < manualOffsets.length; i += 3) {
            BlockPos p = baseMin.add(manualOffsets[i], manualOffsets[i + 1], manualOffsets[i + 2]);
            if (isPlatformMaterial(world, p)) cells.add(p);
        }
        if (cells.isEmpty()) { say(player, "message.slidingplatforms.not_found"); return; }

        Scan scan = scanFromCells(cells);
        ServerWorld serverWorld = (ServerWorld) world;
        BlockPos target = scan.min.offset(moveDir, distance);

        int axisIdx = moveDir.getAxis() == Direction.Axis.X ? 0
                : moveDir.getAxis() == Direction.Axis.Y ? 1 : 2;
        int extent = axisIdx == 0 ? scan.size.getX() : axisIdx == 1 ? scan.size.getY() : scan.size.getZ();
        boolean sliced = cascadeOn && extent > 1;
        int rowsTot = sliced ? extent : 1;
        boolean dirPos = moveDir.getDirection() == Direction.AxisDirection.POSITIVE;

        List<List<BlockPos>> byRow = new ArrayList<>(rowsTot);
        for (int r = 0; r < rowsTot; r++) byRow.add(new ArrayList<>());
        int minAxis = axisIdx == 0 ? scan.min.getX() : axisIdx == 1 ? scan.min.getY() : scan.min.getZ();
        for (BlockPos p : cells) {
            int c = axisIdx == 0 ? p.getX() : axisIdx == 1 ? p.getY() : p.getZ();
            byRow.get(sliced ? c - minAxis : 0).add(p);
        }

        List<RowData> rows = new ArrayList<>();
        for (int r = 0; r < rowsTot; r++) {
            List<BlockPos> rc = byRow.get(r);
            if (rc.isEmpty()) continue;
            int rn = rc.size();
            int[] xs = new int[rn], ys = new int[rn], zs = new int[rn], ids = new int[rn];
            net.minecraft.nbt.NbtList bed = new net.minecraft.nbt.NbtList();
            for (int i = 0; i < rn; i++) {
                BlockPos p = rc.get(i);
                xs[i] = p.getX() - scan.min.getX();
                ys[i] = p.getY() - scan.min.getY();
                zs[i] = p.getZ() - scan.min.getZ();
                ids[i] = Block.getRawIdFromState(world.getBlockState(p));
                BlockEntity be = world.getBlockEntity(p);
                if (be != null) {
                    NbtCompound entry = new NbtCompound();
                    entry.putInt("rx", xs[i]);
                    entry.putInt("ry", ys[i]);
                    entry.putInt("rz", zs[i]);
                    entry.put("nbt", be.createNbt());
                    bed.add(entry);
                }
            }
            rows.add(new RowData(r, xs, ys, zs, ids, bed));
        }

        java.util.Set<BlockPos> cellSet = new java.util.HashSet<>(cells);
        for (BlockPos p : cells) {
            world.removeBlockEntity(p);
            world.setBlockState(p, Blocks.AIR.getDefaultState(),
                    Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS, 512);
        }
        for (BlockPos p : cells) {
            for (Direction d : Direction.values()) {
                if (!cellSet.contains(p.offset(d))) {
                    serverWorld.updateNeighborsAlways(p, Blocks.AIR);
                    break;
                }
            }
        }

        int minDelay = Integer.MAX_VALUE, maxDelay = Integer.MIN_VALUE;
        if (sliced) {
            for (RowData row : rows) {
                int fromLead = dirPos ? rowsTot - 1 - row.idx : row.idx;
                int d = (cascadeInvert ? rowsTot - 1 - fromLead : fromLead) * cascadeDelay;
                if (d < minDelay) minDelay = d;
                if (d > maxDelay) maxDelay = d;
            }
        }
        for (RowData row : rows) {
            SlidingPlatformEntity entity = new SlidingPlatformEntity(ModEntities.SLIDING_PLATFORM, world);
            entity.setup(this.pos, scan.min, target, moveDir, getSpeed(), row.be, row.xs, row.ys, row.zs, row.ids);
            entity.setSoundProfile(soundsEnabled, sndStart, sndStop, sndArrive, sndHum);
            if (sliced) {
                int fromLead = dirPos ? rowsTot - 1 - row.idx : row.idx;
                int delay = (cascadeInvert ? rowsTot - 1 - fromLead : fromLead) * cascadeDelay;

                int role = SlidingPlatformEntity.SND_MID;
                if (delay == minDelay) role = delay == maxDelay
                        ? SlidingPlatformEntity.SND_SOLO : SlidingPlatformEntity.SND_LEAD;
                else if (delay == maxDelay) role = SlidingPlatformEntity.SND_TAIL;
                entity.setCascade(delay, role);
            }
            entity.setPosition(scan.min.getX(), scan.min.getY(), scan.min.getZ());
            serverWorld.spawnEntity(entity);
        }

        this.open = toOpen;
        if (toOpen) openedAtTick = world.getTime();
        this.openDistance = distance;
        markDirty();
        SlidingPlatforms.dbg("controller {} slide: cells={} rows={} dist={} dir={} speed={} cascade={}/{}{}",
                pos.toShortString(), cells.size(), rows.size(), distance, moveDir, getSpeed(),
                cascadeOn, cascadeDelay, cascadeInvert ? " inv" : "");
    }

    private record RowData(int idx, int[] xs, int[] ys, int[] zs, int[] ids,
                           net.minecraft.nbt.NbtList be) {}

    public static final int RIDE_MAX_PATH = 128;

    public BlockPos cabinHome() {
        return open ? manualMin.offset(getSlideDir(), openDistance) : manualMin;
    }

    public boolean isCabinPresent() {
        World world = getWorld();
        if (!hasManual() || world == null) return false;
        int expect = manualOffsets.length / 3;
        int have = 0;
        BlockPos home = cabinHome();
        for (int i = 0; i + 2 < manualOffsets.length; i += 3) {
            BlockPos p = home.add(manualOffsets[i], manualOffsets[i + 1], manualOffsets[i + 2]);
            if (isPlatformMaterial(world, p)) have++;
        }
        return have * 2 >= expect;
    }

    public void onRideArrived(BlockPos at, int[] platformOffsets) {
        manualMin = at.toImmutable();
        if (platformOffsets.length >= 3) manualOffsets = platformOffsets.clone();
        open = false;
        openDistance = 0;
        markDirty();
    }

    public boolean startFloorRide(List<BlockPos> ctrlPath, @Nullable PlayerEntity player) {
        World world = getWorld();
        if (world == null || world.isClient || ctrlPath.size() < 2) return false;
        if (isBusy()) { say(player, "message.slidingplatforms.busy"); return false; }

        if (!hasManual()) {
            Direction facing = getCachedState().get(PlatformControllerBlock.FACING);
            Scan scan = scanPlatform(world, pos.offset(facing), getSlideDir(), facing);
            if (scan == null) { say(player, "message.slidingplatforms.not_found"); return false; }
            rebuildManual(scan.cells);
        }

        BlockPos cur = cabinHome();

        Direction facing0 = getCachedState().get(PlatformControllerBlock.FACING);
        Vec3i r0 = cur.subtract(ctrlPath.get(0).offset(facing0));

        java.util.List<Direction> dirs = new java.util.ArrayList<>();
        java.util.List<BlockPos> stops = new java.util.ArrayList<>();
        int totalPath = 0;
        BlockPos walk = cur;
        for (int i = 1; i < ctrlPath.size(); i++) {
            BlockState cs = world.getBlockState(ctrlPath.get(i));
            if (!(cs.getBlock() instanceof PlatformControllerBlock)) {
                say(player, "message.slidingplatforms.controller_missing");
                return false;
            }
            Direction fi = cs.get(PlatformControllerBlock.FACING);
            BlockPos park = ctrlPath.get(i).offset(fi).add(r0);

            int fx = fi.getOffsetX(), fy = fi.getOffsetY(), fz = fi.getOffsetZ();
            int r0dot = r0.getX() * fx + r0.getY() * fy + r0.getZ() * fz;
            int bad = 0;
            for (int j = 0; j + 2 < manualOffsets.length; j += 3) {
                int proj = r0dot + manualOffsets[j] * fx + manualOffsets[j + 1] * fy
                        + manualOffsets[j + 2] * fz;
                if (proj < bad) bad = proj;
            }
            if (bad < 0) park = park.offset(fi, -bad);
            if (park.equals(walk)) continue;
            Vec3i v = park.subtract(walk);
            int axes = (v.getX() != 0 ? 1 : 0) + (v.getY() != 0 ? 1 : 0) + (v.getZ() != 0 ? 1 : 0);
            if (axes != 1) {
                say(player, "message.slidingplatforms.leg_not_straight");
                return false;
            }
            Direction.Axis axis = v.getX() != 0 ? Direction.Axis.X
                    : v.getY() != 0 ? Direction.Axis.Y : Direction.Axis.Z;
            int comp = axis == Direction.Axis.X ? v.getX() : axis == Direction.Axis.Y ? v.getY() : v.getZ();
            Direction dir = Direction.from(axis, comp > 0
                    ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
            totalPath += Math.abs(comp);
            walk = park;
            dirs.add(dir);
            stops.add(park);
        }
        if (dirs.isEmpty()) { say(player, "message.slidingplatforms.ride_already"); return false; }

        if (totalPath > SlidingPlatformsConfig.VALUES.rideMaxPath) { say(player, "message.slidingplatforms.ride_too_far"); return false; }

        Vec3i shift = walk.subtract(cur);
        java.util.Set<BlockPos> own = new java.util.HashSet<>();
        for (int i = 0; i + 2 < manualOffsets.length; i += 3) {
            BlockPos p = cur.add(manualOffsets[i], manualOffsets[i + 1], manualOffsets[i + 2]);
            if (isPlatformMaterial(world, p)) own.add(p);
        }
        for (BlockPos from : own) {
            BlockPos dest = from.add(shift);
            if (own.contains(dest)) continue;
            BlockState at = world.getBlockState(dest);
            if (!at.isAir() && at.getFluidState().isEmpty()) {
                reportObstacle(player, new Obstacle(at, dest));
                return false;
            }
        }

        launchRide(world, cur, dirs, stops, ctrlPath.get(ctrlPath.size() - 1), player);
        return true;
    }

    private void launchRide(World world, BlockPos baseMin, java.util.List<Direction> dirs,
                            java.util.List<BlockPos> stops, BlockPos rideTarget,
                            @Nullable PlayerEntity player) {
        List<BlockPos> cells = new ArrayList<>();
        for (int i = 0; i + 2 < manualOffsets.length; i += 3) {
            BlockPos p = baseMin.add(manualOffsets[i], manualOffsets[i + 1], manualOffsets[i + 2]);
            if (isPlatformMaterial(world, p)) cells.add(p);
        }
        if (cells.isEmpty()) { say(player, "message.slidingplatforms.not_found"); return; }

        int[] platformCopy = manualOffsets.clone();

        Scan scan = scanFromCells(cells);
        ServerWorld serverWorld = (ServerWorld) world;

        int n = cells.size();
        int[] xs = new int[n], ys = new int[n], zs = new int[n], ids = new int[n];
        net.minecraft.nbt.NbtList beData = new net.minecraft.nbt.NbtList();
        for (int i = 0; i < n; i++) {
            BlockPos p = cells.get(i);
            xs[i] = p.getX() - scan.min.getX();
            ys[i] = p.getY() - scan.min.getY();
            zs[i] = p.getZ() - scan.min.getZ();
            ids[i] = Block.getRawIdFromState(world.getBlockState(p));
            BlockEntity be = world.getBlockEntity(p);
            if (be != null) {
                NbtCompound entry = new NbtCompound();
                entry.putInt("rx", xs[i]);
                entry.putInt("ry", ys[i]);
                entry.putInt("rz", zs[i]);
                entry.put("nbt", be.createNbt());
                beData.add(entry);
            }
        }

        java.util.Set<BlockPos> cellSet = new java.util.HashSet<>(cells);
        for (BlockPos p : cells) {
            world.removeBlockEntity(p);
            world.setBlockState(p, Blocks.AIR.getDefaultState(),
                    Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS, 512);
        }
        for (BlockPos p : cells) {
            for (Direction d : Direction.values()) {
                if (!cellSet.contains(p.offset(d))) {
                    serverWorld.updateNeighborsAlways(p, Blocks.AIR);
                    break;
                }
            }
        }

        SlidingPlatformEntity entity = new SlidingPlatformEntity(ModEntities.SLIDING_PLATFORM, world);
        entity.setup(this.pos, scan.min, stops.get(0), dirs.get(0), getSpeed(), beData, xs, ys, zs, ids);
        entity.setSoundProfile(soundsEnabled, sndStart, sndStop, sndArrive, sndHum);
        entity.startRide(stops.subList(1, stops.size()),
                dirs.subList(1, dirs.size()), rideTarget);
        entity.setPlatformOffsets(platformCopy);
        entity.setPosition(scan.min.getX(), scan.min.getY(), scan.min.getZ());
        serverWorld.spawnEntity(entity);
        SlidingPlatforms.dbg("controller {} ride: legs={} target={}", pos.toShortString(),
                stops.size(), rideTarget.toShortString());

        manualOffsets = new int[0];
        open = false;
        openDistance = 0;
        markDirty();
    }

    private @Nullable Scan scanPlatform(World world, BlockPos anchor, Direction dir, Direction facing) {
        int maxW = SlidingPlatformsConfig.VALUES.maxWidth;
        int maxH = SlidingPlatformsConfig.VALUES.maxHeight;
        int maxD = SlidingPlatformsConfig.VALUES.maxDepth;
        Direction dir3 = thirdAxis(dir, facing);

        List<BlockPos> cells = new ArrayList<>();
        for (int i = 0; i < maxW; i++) {
            BlockPos columnBase = anchor.offset(dir, i);
            boolean columnHasAnything = false;
            for (int j = 0; j < maxH; j++) {
                boolean rowHasFaceBlock = false;
                for (int k = 0; k < maxD; k++) {
                    BlockPos p = columnBase.offset(dir3, j).offset(facing, k);
                    if (isPlatformMaterial(world, p)) {
                        cells.add(p);
                        if (k == 0) rowHasFaceBlock = true;
                    } else {
                        break;
                    }
                }
                if (!rowHasFaceBlock) break;
                columnHasAnything = true;
            }
            if (!columnHasAnything) break;
        }

        if (cells.isEmpty()) return null;
        return scanFromCells(cells);
    }

    private Scan scanFromCells(List<BlockPos> cells) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : cells) {
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }
        Scan scan = new Scan();
        scan.min = new BlockPos(minX, minY, minZ);
        scan.size = new Vec3i(maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        scan.cells = cells;
        Direction dir = getSlideDir();
        scan.extentAlongSlide = switch (dir.getAxis()) {
            case X -> maxX - minX + 1;
            case Y -> maxY - minY + 1;
            case Z -> maxZ - minZ + 1;
        };
        return scan;
    }

    private int listExtentAlong(Direction dir) {
        if (manualOffsets.length < 3) return 1;
        int idx = switch (dir.getAxis()) { case X -> 0; case Y -> 1; case Z -> 2; default -> 0; };
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int i = idx; i < manualOffsets.length; i += 3) {
            min = Math.min(min, manualOffsets[i]);
            max = Math.max(max, manualOffsets[i]);
        }
        return max - min + 1;
    }

    private static Direction thirdAxis(Direction dir, Direction facing) {
        Direction.Axis a1 = dir.getAxis(), a2 = facing.getAxis();
        Direction.Axis a3;
        if (a1 != Direction.Axis.X && a2 != Direction.Axis.X) a3 = Direction.Axis.X;
        else if (a1 != Direction.Axis.Y && a2 != Direction.Axis.Y) a3 = Direction.Axis.Y;
        else a3 = Direction.Axis.Z;
        return Direction.from(a3, Direction.AxisDirection.POSITIVE);
    }

    private boolean isPlatformMaterial(World world, BlockPos p) {
        BlockState s = world.getBlockState(p);
        if (s.isAir() || !s.getFluidState().isEmpty()) return false;
        if (s.getBlock() instanceof PlatformControllerBlock) return false;
        return s.getHardness(world, p) >= 0;
    }

    public boolean hasManual() { return manualOffsets.length >= 3; }

    public int manualToggle(BlockPos absolute) {
        if (open) return -2;
        List<BlockPos> cells = new ArrayList<>();
        for (int i = 0; i + 2 < manualOffsets.length; i += 3) {
            cells.add(manualMin.add(manualOffsets[i], manualOffsets[i + 1], manualOffsets[i + 2]));
        }

        List<BlockPos> group = new ArrayList<>();
        group.add(absolute);
        if (getWorld() != null) group.addAll(MultiPart.relatedParts(getWorld(), absolute));

        if (cells.contains(absolute)) {
            cells.removeAll(group);
        } else {
            int need = 0;
            for (BlockPos p : group) if (!cells.contains(p)) need++;
            if (cells.size() + need > manualCap()) return -1;
            for (BlockPos p : group) if (!cells.contains(p)) cells.add(p);
        }
        rebuildManual(cells);
        return cells.size();
    }

    public int manualToggleRange(BlockPos a, BlockPos b) {
        if (open) return -2;
        World w = getWorld();
        if (w == null) return -2;

        java.util.Set<BlockPos> set = new java.util.LinkedHashSet<>();
        for (int i = 0; i + 2 < manualOffsets.length; i += 3) {
            set.add(manualMin.add(manualOffsets[i], manualOffsets[i + 1], manualOffsets[i + 2]));
        }
        java.util.Set<BlockPos> processed = new java.util.HashSet<>();

        BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()),
                Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()),
                Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));

        boolean changed = false;
        for (BlockPos p : BlockPos.iterate(min, max)) {
            BlockPos imm = p.toImmutable();
            if (processed.contains(imm)) continue;
            if (!isPlatformMaterial(w, imm)) continue;
            processed.add(imm);

            List<BlockPos> group = new ArrayList<>();
            group.add(imm);
            for (BlockPos rel : MultiPart.relatedParts(w, imm)) {
                group.add(rel);
                processed.add(rel);
            }

            if (set.contains(imm)) {
                set.removeAll(group);
            } else {
                for (BlockPos g : group) set.add(g);
            }
            changed = true;
        }
        if (set.size() > manualCap()) return -1;
        if (changed) rebuildManual(new ArrayList<>(set));
        return set.size();
    }

    private void rebuildManual(List<BlockPos> cells) {
        if (cells.isEmpty()) {
            manualOffsets = new int[0];
            markDirty();
            return;
        }
        BlockPos min = cells.get(0);
        for (BlockPos p : cells) min = new BlockPos(
                Math.min(min.getX(), p.getX()), Math.min(min.getY(), p.getY()), Math.min(min.getZ(), p.getZ()));
        manualMin = min;
        manualOffsets = new int[cells.size() * 3];
        for (int i = 0; i < cells.size(); i++) {
            BlockPos p = cells.get(i);
            manualOffsets[i * 3] = p.getX() - min.getX();
            manualOffsets[i * 3 + 1] = p.getY() - min.getY();
            manualOffsets[i * 3 + 2] = p.getZ() - min.getZ();
        }
        markDirty();
    }

    public int manualCount() { return manualOffsets.length / 3; }

    public List<BlockPos> currentManualPositions() {
        List<BlockPos> cells = new ArrayList<>();
        for (int i = 0; i + 2 < manualOffsets.length; i += 3) {
            cells.add(manualMin.add(manualOffsets[i], manualOffsets[i + 1], manualOffsets[i + 2]));
        }
        return cells;
    }

    public boolean isMoving() { return isBusy(); }

    private boolean isBusy() {
        if (!(getWorld() instanceof ServerWorld serverWorld)) return false;
        return !serverWorld.getEntitiesByType(ModEntities.SLIDING_PLATFORM,
                new Box(pos).expand(192),
                e -> e.isAlive() && pos.equals(e.getControllerPos())).isEmpty();
    }

    private void say(@Nullable PlayerEntity player, String messageKey) {
        if (player != null) player.sendMessage(Text.translatable(messageKey), true);
    }

    private void say(@Nullable PlayerEntity player, String messageKey, Object arg) {
        if (player != null) player.sendMessage(Text.translatable(messageKey, arg), true);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putBoolean("open", open);
        nbt.putInt("openDistance", openDistance);
        nbt.putBoolean("wasPowered", wasPowered);
        nbt.putInt("slideOffset", slideOffset);
        nbt.putFloat("speed", speed);
        nbt.putString("name", name);

        if (!soundsEnabled) nbt.putBoolean("soundsOff", true);
        if (!sndStart.isEmpty()) nbt.putString("sndStart", sndStart);
        if (!sndStop.isEmpty()) nbt.putString("sndStop", sndStop);
        if (!sndArrive.isEmpty()) nbt.putString("sndArrive", sndArrive);
        if (!sndHum.isEmpty()) nbt.putString("sndHum", sndHum);

        if (redstoneMode != RS_IMPULSE) nbt.putInt("rsMode", redstoneMode);

        if (sensOn) nbt.putBoolean("sensOn", true);
        if (sensRadius != 3) nbt.putInt("sensRadius", sensRadius);
        if (!sensPlayers) nbt.putBoolean("sensPlayersOff", true);
        if (!sensMobs) nbt.putBoolean("sensMobsOff", true);
        if (sensInvert) nbt.putBoolean("sensInvert", true);
        if (!sensNames.isEmpty()) nbt.putString("sensNames", sensNames);

        if (!lampGlow) nbt.putBoolean("lampGlowOff", true);

        if (cascadeOn) nbt.putBoolean("cascade", true);
        if (cascadeDelay != 2) nbt.putInt("cascadeDelay", cascadeDelay);
        if (cascadeInvert) nbt.putBoolean("cascadeInv", true);
        if (!litLamps.isEmpty()) {
            long[] lamps = new long[litLamps.size()];
            int li = 0;
            for (long v : litLamps) lamps[li++] = v;
            nbt.putLongArray("litLamps", lamps);
        }

        if (autoClose > 0) nbt.putInt("autoClose", autoClose);
        if (lockOn) {
            nbt.putBoolean("lockOn", true);
            if (!lockOwner.isEmpty()) nbt.putString("lockOwner", lockOwner);
            if (!lockTrusted.isEmpty()) nbt.putString("lockTrusted", lockTrusted);
        }
        if (zoneMin != null) {
            nbt.putLong("zoneMin", zoneMin.asLong());
            nbt.putLong("zoneMax", zoneMax.asLong());
        }
        if (!boundScreen.isEmpty()) nbt.putString("boundScreen", boundScreen);
        if (slideDir != null) nbt.putInt("slideDir", slideDir.ordinal());
        if (anchorDir != null) nbt.putInt("anchor", anchorDir.ordinal());
        if (manualOffsets.length > 0) {
            nbt.putIntArray("manual", manualOffsets);
            nbt.putLong("manualMin", manualMin.asLong());
        }
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        open = nbt.getBoolean("open");
        openDistance = nbt.getInt("openDistance");
        wasPowered = nbt.getBoolean("wasPowered");
        slideOffset = nbt.getInt("slideOffset");
        speed = nbt.getFloat("speed");
        name = nbt.getString("name");

        soundsEnabled = !nbt.getBoolean("soundsOff");
        sndStart = nbt.getString("sndStart");
        sndStop = nbt.getString("sndStop");
        sndArrive = nbt.getString("sndArrive");
        sndHum = nbt.getString("sndHum");

        if (nbt.contains("rsMode")) {
            int m = nbt.getInt("rsMode");
            redstoneMode = (m >= RS_IMPULSE && m <= RS_OFF) ? m : RS_IMPULSE;
        } else {
            redstoneMode = RS_IMPULSE;
        }

        sensOn = nbt.getBoolean("sensOn");
        sensRadius = nbt.contains("sensRadius") ? Math.max(1, Math.min(16, nbt.getInt("sensRadius"))) : 3;
        sensPlayers = !nbt.getBoolean("sensPlayersOff");
        sensMobs = !nbt.getBoolean("sensMobsOff");
        sensInvert = nbt.getBoolean("sensInvert");
        sensNames = sanitizeNames(nbt.getString("sensNames"));

        lampGlow = !nbt.getBoolean("lampGlowOff");

        cascadeOn = nbt.getBoolean("cascade");
        cascadeDelay = nbt.contains("cascadeDelay")
                ? Math.max(1, Math.min(4, nbt.getInt("cascadeDelay"))) : 2;
        cascadeInvert = nbt.getBoolean("cascadeInv");
        litLamps.clear();
        for (long v : nbt.getLongArray("litLamps")) litLamps.add(v);

        autoClose = nbt.contains("autoClose") ? Math.max(0, Math.min(60, nbt.getInt("autoClose"))) : 0;
        openedAtTick = 0;
        lockOn = nbt.getBoolean("lockOn");
        lockOwner = sanitizeNames(nbt.getString("lockOwner"));
        lockTrusted = sanitizeNames(nbt.getString("lockTrusted"));

        if (nbt.contains("zoneMin") && nbt.contains("zoneMax")) {
            zoneMin = BlockPos.fromLong(nbt.getLong("zoneMin"));
            zoneMax = BlockPos.fromLong(nbt.getLong("zoneMax"));
        } else {
            zoneMin = zoneMax = null;
        }

        if (nbt.contains("boundScreen", NbtElement.STRING_TYPE)) {
            boundScreen = nbt.getString("boundScreen");
        } else if (nbt.contains("boundScreen")) {
            boundScreen = Long.toString(nbt.getLong("boundScreen"));
        } else {
            boundScreen = "";
        }
        if (nbt.contains("slideDir")) {
            int idx = nbt.getInt("slideDir");
            Direction[] all = Direction.values();
            if (idx >= 0 && idx < all.length) slideDir = all[idx];
        }
        if (nbt.contains("anchor")) {
            int idx = nbt.getInt("anchor");
            Direction[] all = Direction.values();
            if (idx >= 0 && idx < all.length && all[idx].getAxis().isHorizontal()) anchorDir = all[idx];
        }
        if (nbt.contains("manual")) {
            manualOffsets = nbt.getIntArray("manual");
            manualMin = BlockPos.fromLong(nbt.getLong("manualMin"));
        }
    }
}

package mc.slidingplatforms;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SlidingPlatformEntity extends Entity {

    public static final Identifier DATA_PACKET = new Identifier(SlidingPlatforms.MOD_ID, "platform_render_data");

    public static final Identifier ARRIVE_PACKET = new Identifier(SlidingPlatforms.MOD_ID, "platform_arrive");

    public record RenderBlock(int x, int y, int z, BlockState state) {}

    private BlockPos controllerPos = BlockPos.ORIGIN;
    private BlockPos origin = BlockPos.ORIGIN;
    private BlockPos target = BlockPos.ORIGIN;
    private Direction moveDir = Direction.EAST;
    private double speed = 0.1;
    private double travelled = 0;
    private int revertCount = 0;
    private boolean placed = false;

    public static final int SND_SOLO = 0, SND_LEAD = 1, SND_MID = 2, SND_TAIL = 3;
    private int startDelay = 0;
    private int soundRole = SND_SOLO;

    public void setCascade(int delay, int role) {
        this.startDelay = Math.max(0, delay);
        this.soundRole = role;
    }

    public boolean hasHumSound() { return soundRole == SND_SOLO || soundRole == SND_LEAD; }

    public boolean hasTailSound() { return soundRole == SND_SOLO || soundRole == SND_TAIL; }

    private boolean sndEnabled = true;
    private String sndStart = "";
    private String sndStop = "";
    private String sndArrive = "";
    private String sndHum = "";
    private boolean soundKnown = false;

    private final List<BlockPos> chainTargets = new ArrayList<>();
    private final List<Direction> chainDirs = new ArrayList<>();

    private boolean rideMode = false;

    private @Nullable BlockPos rideTargetPos = null;

    private int[] platformOffsets = new int[0];

    public void startRide(List<BlockPos> remainingTargets, List<Direction> remainingDirs,
                          @Nullable BlockPos rideTarget) {
        chainTargets.clear();
        chainDirs.clear();
        chainTargets.addAll(remainingTargets);
        chainDirs.addAll(remainingDirs);
        this.rideMode = true;
        this.rideTargetPos = rideTarget;
    }

    public @Nullable BlockPos getRideTargetControllerPos() {
        return rideTargetPos;
    }

    public void setPlatformOffsets(int[] offsets) {
        this.platformOffsets = offsets.clone();
    }

    private net.minecraft.nbt.NbtList beData = new net.minecraft.nbt.NbtList();

    private NbtCompound renderData = new NbtCompound();

    private @Nullable List<RenderBlock> renderBlocks;

    private @Nullable Box relBox;

    private static final Box FALLBACK_BOX = new Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

    public SlidingPlatformEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
        this.ignoreCameraFrustum = true;
    }

    public void setup(BlockPos controllerPos, BlockPos origin, BlockPos target, Direction moveDir,
                      double speed, net.minecraft.nbt.NbtList beData,
                      int[] xs, int[] ys, int[] zs, int[] stateIds) {
        this.controllerPos = controllerPos;
        this.origin = origin.toImmutable();
        this.target = target.toImmutable();
        this.moveDir = moveDir;
        this.speed = speed;
        this.beData = beData;
        this.renderData = new NbtCompound();
        this.renderData.putIntArray("x", xs);
        this.renderData.putIntArray("y", ys);
        this.renderData.putIntArray("z", zs);
        this.renderData.putIntArray("s", stateIds);
        updateRelBox();
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    @Override
    public void tick() {
        super.tick();
        World world = getWorld();

        if (world.isClient) {
            double total = Math.abs(target.getX() - origin.getX())
                    + Math.abs(target.getY() - origin.getY())
                    + Math.abs(target.getZ() - origin.getZ());
            if (clientPos != null && !placed && total > 0) {
                if (startDelay > 0) {
                    startDelay--;
                    lastTickPos = clientPos;
                    return;
                }
                double t = Math.min(travelled + easedStep(total), total);
                travelled = t;
                Vec3d prev = clientPos;
                clientPos = new Vec3d(origin.getX() + moveDir.getOffsetX() * t,
                                      origin.getY() + moveDir.getOffsetY() * t,
                                      origin.getZ() + moveDir.getOffsetZ() * t);
                lastTickPos = prev;

                if (relBox != null) {
                    PlatformCollisionHandler.handle(this, world, prev, clientPos.subtract(prev));
                }
                setPosition(clientPos.x, clientPos.y, clientPos.z);
            } else if (clientPos == null) {
                lastTickPos = getPos();
            }
            return;
        }
        if (placed) return;

        resendIfNeeded();

        if (startDelay > 0) { startDelay--; return; }

        double total = Math.abs(target.getX() - origin.getX())
                + Math.abs(target.getY() - origin.getY())
                + Math.abs(target.getZ() - origin.getZ());
        if (total <= 0) { discard(); return; }

        travelled += easedStep(total);
        double t = Math.min(travelled, total);
        Vec3d oldPlatformPos = getPos();
        Vec3d newPlatformPos = new Vec3d(
                origin.getX() + moveDir.getOffsetX() * t,
                origin.getY() + moveDir.getOffsetY() * t,
                origin.getZ() + moveDir.getOffsetZ() * t);

        PlatformCollisionHandler.handle(this, world, oldPlatformPos, newPlatformPos.subtract(oldPlatformPos));
        setPosition(newPlatformPos.x, newPlatformPos.y, newPlatformPos.z);

        if (travelled >= total) {

            if (!chainTargets.isEmpty()) {
                origin = target;
                target = chainTargets.remove(0);
                moveDir = chainDirs.remove(0);
                travelled = 0;
                currentSpeed = 0.0;
                revertCount = 0;
                setPosition(origin.getX(), origin.getY(), origin.getZ());
                broadcastRenderData();
                return;
            }
            setPosition(target.getX(), target.getY(), target.getZ());
            ServerWorld serverWorld = (ServerWorld) world;
            if (tryPlace(serverWorld, target, false)) {
                placed = true;

                broadcastArrived(target, revertCount == 0);
                notifyRideArrived(target);
                discard();
            } else if (revertCount >= 1) {

                tryPlace(serverWorld, target, true);
                placed = true;
                broadcastArrived(target, false);
                notifyRideArrived(target);
                discard();
            } else {

                chainTargets.clear();
                chainDirs.clear();
                rideTargetPos = controllerPos;
                revertCount++;
                startDelay = 0;
                BlockPos swap = origin;
                origin = target;
                target = swap;
                moveDir = moveDir.getOpposite();
                travelled = 0;
                currentSpeed = 0.0;
                broadcastRenderData();

            }
        }
    }

    private void notifyRideArrived(BlockPos at) {
        if (!rideMode) return;
        BlockPos whom = rideTargetPos != null ? rideTargetPos : controllerPos;
        if (getWorld().getBlockEntity(whom) instanceof PlatformControllerBlockEntity be) {
            be.onRideArrived(at, platformOffsets);
        }
    }

    private boolean tryPlace(ServerWorld world, BlockPos at, boolean force) {
        int[] xs = renderData.getIntArray("x");
        int[] ys = renderData.getIntArray("y");
        int[] zs = renderData.getIntArray("z");
        int[] ids = renderData.getIntArray("s");

        if (!force) {
            for (int i = 0; i < xs.length; i++) {
                BlockState s = world.getBlockState(at.add(xs[i], ys[i], zs[i]));
                if (!s.isAir() && s.getFluidState().isEmpty()) return false;
            }
        }

        for (int i = 0; i < xs.length; i++) {
            BlockPos p = at.add(xs[i], ys[i], zs[i]);
            world.removeBlockEntity(p);
            world.setBlockState(p, Block.getStateFromRawId(ids[i]), Block.NOTIFY_ALL);
        }

        for (int i = 0; i < beData.size(); i++) {
            NbtCompound entry = beData.getCompound(i);
            BlockPos p = at.add(entry.getInt("rx"), entry.getInt("ry"), entry.getInt("rz"));
            net.minecraft.block.entity.BlockEntity be = world.getBlockEntity(p);
            if (be != null) {
                NbtCompound nbt = entry.getCompound("nbt").copy();
                nbt.remove("x"); nbt.remove("y"); nbt.remove("z");
                be.readNbt(nbt);
                be.markDirty();
            }
        }
        return true;
    }

    private static final double SPEED_GAIN = 0.05;
    private static final double STOP_FLOOR = 0.01;

    private double currentSpeed = 0.0;

    private double easedStep(double total) {
        double remaining = total - travelled;
        if (remaining <= 0.0) return 0.0;

        double ticksLeftToTarget = remaining / currentSpeed;
        double ticksNeededToStop = (currentSpeed - STOP_FLOOR) / SPEED_GAIN;

        if (ticksLeftToTarget < ticksNeededToStop) {

            currentSpeed = Math.max(STOP_FLOOR, currentSpeed - SPEED_GAIN);
        } else if (currentSpeed < speed) {

            currentSpeed = Math.min(speed, currentSpeed + SPEED_GAIN);
        }

        return Math.min(currentSpeed, remaining);
    }

    private Vec3d lastTickPos = null;

    private Vec3d clientPos = null;

    private List<Box> localBoxes = java.util.Collections.emptyList();

    public @Nullable Box getRelBox() { return relBox; }

    public List<Box> getLocalBoxes() { return localBoxes; }

    public boolean isTravelling() {
        return !placed && !localBoxes.isEmpty();
    }

    public Vec3d smoothRenderPos(float tickDelta) {
        Vec3d cur = clientPos != null ? clientPos : getPos();
        Vec3d from = lastTickPos != null ? lastTickPos : cur;
        return from.add(cur.subtract(from).multiply(tickDelta));
    }

    @Override
    public void remove(Entity.RemovalReason reason) {

        if (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED) {
            if (!getWorld().isClient && !placed && renderData.getIntArray("x").length > 0
                    && getWorld() instanceof ServerWorld serverWorld) {
                tryPlace(serverWorld, BlockPos.ofFloored(getPos()), true);
            }
        }
        super.remove(reason);
    }

    @Override
    protected Box calculateBoundingBox() {
        Box rel = relBox != null ? relBox : FALLBACK_BOX;
        return rel.offset(getPos());
    }

    private void updateRelBox() {
        int[] xs = renderData.getIntArray("x");
        int[] ys = renderData.getIntArray("y");
        int[] zs = renderData.getIntArray("z");
        if (xs.length == 0) return;
        int mx = 0, my = 0, mz = 0;
        for (int i = 0; i < xs.length; i++) {
            mx = Math.max(mx, xs[i]);
            my = Math.max(my, ys[i]);
            mz = Math.max(mz, zs[i]);
        }
        relBox = new Box(0.0, 0.0, 0.0, mx + 1.0, my + 1.0, mz + 1.0);

        List<Box> list = new java.util.ArrayList<>(xs.length);
        for (int i = 0; i < xs.length; i++) {
            list.add(new Box(xs[i], ys[i], zs[i], xs[i] + 1.0, ys[i] + 1.0, zs[i] + 1.0));
        }
        localBoxes = java.util.Collections.unmodifiableList(list);
        setBoundingBox(calculateBoundingBox());
    }

    @Override public boolean isPushable() { return false; }

    @Override public boolean isCollidable() { return false; }

    @Override public boolean damage(DamageSource source, float amount) { return false; }

    public void acceptSpawnData(NbtCompound data) {
        NbtCompound clean = (NbtCompound) data.copy();

        if (clean.contains("origin")) {
            applyMotionNbt(clean);
            clean.remove("origin"); clean.remove("target"); clean.remove("moveDir");
            clean.remove("speed"); clean.remove("travelled"); clean.remove("currentSpeed");
            clean.remove("startDelay");
        }

        if (clean.contains("sndRole")) {
            soundRole = clean.getInt("sndRole");
            clean.remove("sndRole");
        }

        if (clean.contains("snd")) {
            sndEnabled = clean.getBoolean("snd");
            sndStart = clean.getString("sndStart");
            sndStop = clean.getString("sndStop");
            sndArrive = clean.getString("sndArrive");
            sndHum = clean.getString("sndHum");
            soundKnown = true;
            clean.remove("snd"); clean.remove("sndStart"); clean.remove("sndStop");
            clean.remove("sndArrive"); clean.remove("sndHum");
        }
        this.renderData = clean;
        this.renderBlocks = null;
        updateRelBox();
    }

    private void applyMotionNbt(NbtCompound data) {
        origin = BlockPos.fromLong(data.getLong("origin"));
        target = BlockPos.fromLong(data.getLong("target"));
        moveDir = Direction.values()[data.getInt("moveDir")];
        speed = data.getDouble("speed");
        travelled = data.getDouble("travelled");
        currentSpeed = data.getDouble("currentSpeed");
        startDelay = data.getInt("startDelay");
        if (clientPos == null) {
            clientPos = getPos();
            lastTickPos = clientPos;
        }
    }

    public List<RenderBlock> getRenderBlocks() {
        if (renderBlocks == null) {
            renderBlocks = new ArrayList<>();
            int[] xs = renderData.getIntArray("x");
            int[] ys = renderData.getIntArray("y");
            int[] zs = renderData.getIntArray("z");
            int[] ids = renderData.getIntArray("s");
            for (int i = 0; i < xs.length; i++) {
                renderBlocks.add(new RenderBlock(xs[i], ys[i], zs[i], Block.getStateFromRawId(ids[i])));
            }
        }
        return renderBlocks;
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {

        return new EntitySpawnS2CPacket(this);
    }

    private void broadcastRenderData() {
        if (!(getWorld() instanceof ServerWorld serverWorld)) return;
        for (ServerPlayerEntity player : PlayerLookup.tracking(this)) {
            sendRenderDataTo(player);
        }
    }

    private void broadcastArrived(BlockPos at, boolean loud) {
        if (getWorld().isClient) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(getId());
        buf.writeLong(at.asLong());
        buf.writeBoolean(loud);
        for (ServerPlayerEntity player : PlayerLookup.tracking(this)) {
            ServerPlayNetworking.send(player, ARRIVE_PACKET, buf);
        }
    }

    public void sendRenderDataTo(ServerPlayerEntity player) {
        PacketByteBuf buf = PacketByteBufs.create();

        NbtCompound out = (NbtCompound) renderData.copy();
        out.putLong("origin", origin.asLong());
        out.putLong("target", target.asLong());
        out.putInt("moveDir", moveDir.ordinal());
        out.putDouble("speed", speed);
        out.putDouble("travelled", travelled);
        out.putDouble("currentSpeed", currentSpeed);
        out.putInt("startDelay", startDelay);
        out.putInt("sndRole", soundRole);

        out.putBoolean("snd", sndEnabled);
        out.putString("sndStart", sndStart);
        out.putString("sndStop", sndStop);
        out.putString("sndArrive", sndArrive);
        out.putString("sndHum", sndHum);
        buf.writeVarInt(getId());
        buf.writeNbt(out);
        ServerPlayNetworking.send(player, DATA_PACKET, buf);
    }

    private void resendIfNeeded() {
        if (!getWorld().isClient && age % 5 == 0) {
            broadcastRenderData();
        }
    }

    public static void onStartTracking(Entity entity, ServerPlayerEntity player) {
        if (entity instanceof SlidingPlatformEntity platform) platform.sendRenderDataTo(player);
    }

    public void setSoundProfile(boolean enabled, String start, String stop, String arrive, String hum) {
        this.sndEnabled = enabled;
        this.sndStart = start == null ? "" : start;
        this.sndStop = stop == null ? "" : stop;
        this.sndArrive = arrive == null ? "" : arrive;
        this.sndHum = hum == null ? "" : hum;
    }

    public boolean soundsEnabled() { return sndEnabled; }
    public boolean soundProfileKnown() { return soundKnown || !getWorld().isClient; }
    public String getSndStart() { return sndStart; }
    public String getSndStop() { return sndStop; }
    public String getSndArrive() { return sndArrive; }
    public String getSndHum() { return sndHum; }

    public boolean isTravellingNow() {
        if (origin == null || target == null || placed) return false;
        double total = Math.sqrt(origin.getSquaredDistance(target));
        return total > 0 && travelled < total - 1.0e-6;
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putLong("controller", controllerPos.asLong());
        nbt.putLong("origin", origin.asLong());
        nbt.putLong("target", target.asLong());
        nbt.putInt("dir", moveDir.ordinal());
        nbt.putDouble("speed", speed);
        nbt.putDouble("travelled", travelled);

        nbt.putBoolean("snd", sndEnabled);
        nbt.putString("sndStart", sndStart);
        nbt.putString("sndStop", sndStop);
        nbt.putString("sndArrive", sndArrive);
        nbt.putString("sndHum", sndHum);
        nbt.putInt("reverts", revertCount);
        nbt.putInt("startDelay", startDelay);
        if (soundRole != SND_SOLO) nbt.putInt("sndRole", soundRole);
        nbt.put("bes", beData);
        nbt.put("render", renderData);

        nbt.putBoolean("ride", rideMode);
        if (rideTargetPos != null) nbt.putLong("rideTarget", rideTargetPos.asLong());
        if (platformOffsets.length > 0) nbt.putIntArray("platformOffsets", platformOffsets);
        if (!chainTargets.isEmpty()) {
            long[] ct = new long[chainTargets.size()];
            for (int i = 0; i < ct.length; i++) ct[i] = chainTargets.get(i).asLong();
            nbt.putLongArray("chainT", ct);
            int[] cd = new int[chainDirs.size()];
            for (int i = 0; i < cd.length; i++) cd[i] = chainDirs.get(i).ordinal();
            nbt.putIntArray("chainD", cd);
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        controllerPos = BlockPos.fromLong(nbt.getLong("controller"));
        origin = BlockPos.fromLong(nbt.getLong("origin"));
        target = BlockPos.fromLong(nbt.getLong("target"));
        int dirIdx = nbt.getInt("dir");
        Direction[] all = Direction.values();
        if (dirIdx >= 0 && dirIdx < all.length) moveDir = all[dirIdx];
        speed = nbt.getDouble("speed");
        travelled = nbt.getDouble("travelled");
        revertCount = nbt.getInt("reverts");
        startDelay = nbt.getInt("startDelay");
        soundRole = nbt.getInt("sndRole");

        if (nbt.contains("snd")) {
            sndEnabled = nbt.getBoolean("snd");
            sndStart = nbt.getString("sndStart");
            sndStop = nbt.getString("sndStop");
            sndArrive = nbt.getString("sndArrive");
            sndHum = nbt.getString("sndHum");
        }
        beData = nbt.getList("bes", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
        renderData = nbt.getCompound("render");
        renderBlocks = null;

        rideMode = nbt.getBoolean("ride");
        rideTargetPos = nbt.contains("rideTarget") ? BlockPos.fromLong(nbt.getLong("rideTarget")) : null;
        platformOffsets = nbt.contains("platformOffsets") ? nbt.getIntArray("platformOffsets") : new int[0];
        chainTargets.clear();
        chainDirs.clear();
        long[] ct = nbt.getLongArray("chainT");
        int[] cd = nbt.getIntArray("chainD");
        for (int i = 0; i < ct.length && i < cd.length; i++) {
            chainTargets.add(BlockPos.fromLong(ct[i]));
            int idx = cd[i];
            if (idx >= 0 && idx < all.length) chainDirs.add(all[idx]);
        }
        updateRelBox();
    }

    @Override
    protected void initDataTracker() {

    }
}

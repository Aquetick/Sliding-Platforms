package mc.slidingplatforms;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ElevatorScreenBlockEntity extends BlockEntity {

    private String name = "";

    private String chain = "";

    private final Map<BlockPos, Integer> floorNumbers = new LinkedHashMap<>();

    private boolean dispEmpty = true;

    private int callFloor = 0;
    private boolean wasPowered = false;

    public int getCallFloor() { return callFloor; }
    public void setCallFloor(int n) {
        this.callFloor = Math.max(0, n);
        markDirty();
    }

    private static final int QUEUE_CAP = 8;
    private static final long QUEUE_PAUSE_TICKS = 100;
    private final java.util.ArrayDeque<BlockPos> rideQueue = new java.util.ArrayDeque<>();
    private boolean wasFlying = false;
    private long arrivedAtTick = 0;
    private boolean dispMoving = false;
    private int dispNo = -1;
    private String dispName = "";

    private int ticker;

    public ElevatorScreenBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.ELEVATOR_SCREEN_BE, pos, state);
    }

    public String getName() { return name; }
    public boolean isDispEmpty() { return dispEmpty; }
    public boolean isDispMoving() { return dispMoving; }
    public int getDispNo() { return dispNo; }
    public String getDispName() { return dispName; }

    private transient String placementWord;

    public void usePlacementWord(String word) { this.placementWord = word; }

    public String getScreenName() {
        if (getWorld() instanceof ServerWorld sw) {
            name = placementWord != null
                    ? ScreenRegistry.ensureName(sw, pos, name, placementWord)
                    : ScreenRegistry.ensureName(sw, pos, name);
            markDirty();
        }
        return name;
    }

    public void setScreenName(String newName) {
        if (newName == null || newName.isBlank()) return;
        this.name = newName.trim();
        if (this.name.length() > 24) this.name = this.name.substring(0, 24);
        if (getWorld() instanceof ServerWorld sw) {
            ScreenRegistry.rename(sw, pos, this.name);
            refresh(sw);
            sw.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
        }
        markDirty();
    }

    public void onBroken() {
        if (getWorld() != null) {
            ScreenRegistry.unregister(getWorld(), pos);
            if (!chain.isEmpty()) ChainRegistry.leave(getWorld(), chain, screenPosKey());
        }
    }

    public String getChain() { return chain; }

    public String screenKey() { return chain.isEmpty() ? screenPosKey() : "chain:" + chain; }

    public String screenPosKey() { return Long.toString(pos.asLong()); }

    public String getChainName() {
        if (chain.isEmpty() || getWorld() == null) return "";
        String n = ChainRegistry.nameOf(getWorld(), chain);
        return n != null ? n : "";
    }

    public String setChainName(String newName) {
        if (getWorld() == null) return "";
        if (chain.isEmpty()) {
            chain = Long.toString(pos.asLong());
            ChainRegistry.join(getWorld(), chain, screenPosKey());
            markDirty();
        }
        return ChainRegistry.setName(getWorld(), chain, newName);
    }

    public void linkTo(String targetKey) {
        World w = getWorld();
        if (w == null) return;
        String chainId;
        if (targetKey.startsWith("chain:")) {
            chainId = targetKey.substring(6);
            if (ChainRegistry.nameOf(w, chainId) == null) return;
        } else {
            chainId = targetKey;
            if (ChainRegistry.nameOf(w, chainId) == null) {
                ChainRegistry.setName(w, chainId, "");
                ChainRegistry.join(w, chainId, targetKey);
            }

            if (!targetKey.equals(screenPosKey())) {
                try {
                    if (w.getBlockEntity(BlockPos.fromLong(Long.parseLong(targetKey)))
                            instanceof ElevatorScreenBlockEntity other
                            && !chainId.equals(other.chain)) {
                        other.chain = chainId;
                        other.markDirty();
                        other.refreshNowWithListeners();
                    }
                } catch (NumberFormatException ignored) {  }
            }
        }
        if (chainId.equals(chain)) return;
        if (!chain.isEmpty()) ChainRegistry.leave(w, chain, screenPosKey());
        chain = chainId;
        ChainRegistry.join(w, chain, screenPosKey());
        markDirty();
        refreshNowWithListeners();
    }

    public void leaveChain() {
        World w = getWorld();
        if (w == null || chain.isEmpty()) return;
        ChainRegistry.leave(w, chain, screenPosKey());
        chain = "";
        markDirty();
        refreshNowWithListeners();
    }

    private void refreshNowWithListeners() {
        if (getWorld() instanceof ServerWorld sw) {
            refresh(sw);
            sw.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
        }
    }

    public record Floor(BlockPos ctrlPos, int number, String name) {}

    public List<Floor> floors() {
        List<Floor> out = new ArrayList<>();
        World w = getWorld();
        if (w == null) return out;
        String key = screenKey();
        for (PlatformRegistry.Entry e : PlatformRegistry.list(w)) {
            if (w.getBlockEntity(e.pos()) instanceof PlatformControllerBlockEntity c
                    && key.equals(c.getBoundScreen())) {
                out.add(new Floor(e.pos(), numberFor(e.pos()), c.getPlatformName()));
            }
        }
        out.sort(Comparator.comparingInt(Floor::number));
        return out;
    }

    private int numberFor(BlockPos ctrl) {
        if (!chain.isEmpty() && getWorld() != null) {
            return ChainRegistry.numberFor(getWorld(), chain, Long.toString(ctrl.asLong()));
        }
        Integer n = floorNumbers.get(ctrl);
        if (n != null) return n;
        int max = 0;
        for (int v : floorNumbers.values()) max = Math.max(max, v);
        floorNumbers.put(ctrl.toImmutable(), max + 1);
        markDirty();
        return max + 1;
    }

    public void trackController(BlockPos ctrl) { numberFor(ctrl); }

    public void setFloorNumber(BlockPos ctrl, int n) {
        if (!chain.isEmpty() && getWorld() != null) {
            ChainRegistry.setFloorNumber(getWorld(), chain, Long.toString(ctrl.asLong()), n);
            refreshNow();
            return;
        }
        n = Math.max(1, Math.min(n, 99));
        int old = numberFor(ctrl);
        if (old == n) return;
        for (Map.Entry<BlockPos, Integer> e : floorNumbers.entrySet()) {
            if (e.getValue() == n && !e.getKey().equals(ctrl)) {
                e.setValue(old);
                break;
            }
        }
        floorNumbers.put(ctrl, n);
        markDirty();
        refreshNow();
    }

    public void removeFloor(BlockPos ctrl) {
        if (!chain.isEmpty() && getWorld() != null) {
            ChainRegistry.forgetFloor(getWorld(), chain, Long.toString(ctrl.asLong()));
        } else {
            floorNumbers.remove(ctrl);
        }
        markDirty();
        World w = getWorld();
        if (w != null && w.getBlockEntity(ctrl) instanceof PlatformControllerBlockEntity c) {
            c.setBoundScreen("");
            c.detachFromChainIfAny();
        }
        refreshNow();
    }

    public void requestRideNoMessage(BlockPos targetCtrl) {
        requestRide(targetCtrl, null);
    }

    public void requestRide(BlockPos targetCtrl, @Nullable ServerPlayerEntity player) {
        requestRideInternal(targetCtrl, player, false);
    }

    private void startQueuedRide(BlockPos targetCtrl) {
        requestRideInternal(targetCtrl, null, true);
    }

    public void onRedstoneUpdate(boolean powered) {
        if (powered && !wasPowered && callFloor > 0) {
            for (Floor f : floors()) {
                if (f.number() == callFloor) { requestRideNoMessage(f.ctrlPos()); break; }
            }
        }
        if (powered != wasPowered) { wasPowered = powered; markDirty(); }
    }

    private void requestRideInternal(BlockPos targetCtrl, @Nullable ServerPlayerEntity player,
                                     boolean bypassQueue) {
        World w = getWorld();
        if (!(w instanceof ServerWorld world)) { return; }

        List<Floor> fs = floors();

        int toIdx = -1;
        for (int i = 0; i < fs.size(); i++) if (fs.get(i).ctrlPos().equals(targetCtrl)) toIdx = i;
        if (toIdx < 0) { say(player, "message.slidingplatforms.controller_missing"); return; }

        if (!bypassQueue && (isCabinFlying(world) || !rideQueue.isEmpty())) {
            if (!rideQueue.contains(targetCtrl) && rideQueue.size() < QUEUE_CAP) {
                rideQueue.add(targetCtrl);
                say(player, "message.slidingplatforms.ride_queued");
            } else {
                say(player, "message.slidingplatforms.cabin_flying");
            }
            return;
        }

        int fromIdx = -1;
        PlatformControllerBlockEntity origin = null;
        for (int i = 0; i < fs.size(); i++) {
            if (w.getBlockEntity(fs.get(i).ctrlPos()) instanceof PlatformControllerBlockEntity c
                    && c.isCabinPresent()) { fromIdx = i; origin = c; break; }
        }
        if (origin == null) { say(player, "message.slidingplatforms.cabin_not_found"); return; }
        if (fromIdx == toIdx) { say(player, "message.slidingplatforms.ride_already"); return; }

        int step = fromIdx < toIdx ? 1 : -1;
        List<BlockPos> ctrlPath = new ArrayList<>();
        for (int i = fromIdx; ; i += step) {
            ctrlPath.add(fs.get(i).ctrlPos());
            if (i == toIdx) break;
        }

        for (int i = 1; i < ctrlPath.size(); i++) {
            Vec3i d = ctrlPath.get(i).subtract(ctrlPath.get(i - 1));
            int axes = (d.getX() != 0 ? 1 : 0) + (d.getY() != 0 ? 1 : 0) + (d.getZ() != 0 ? 1 : 0);
            if (axes != 1) {
                say(player, "message.slidingplatforms.floors_not_aligned",
                        fs.get(fromIdx + (i - 1) * step).name() + " (этаж "
                                + fs.get(fromIdx + (i - 1) * step).number() + ")",
                        fs.get(fromIdx + i * step).name() + " (этаж "
                                + fs.get(fromIdx + i * step).number() + ")");
                return;
            }
        }

        String targetName = fs.get(toIdx).name();
        int targetNo = fs.get(toIdx).number();
        if (origin.startFloorRide(ctrlPath, player)) {
            if (player != null) {
                player.sendMessage(Text.translatable("message.slidingplatforms.ride_started",
                        targetNo, targetName), true);
            }
        }
    }

    private boolean isCabinFlying(ServerWorld world) {
        List<Floor> fs = floors();
        if (fs.isEmpty()) return false;
        Box routeBox = null;
        for (Floor f : fs) {
            Box b = new Box(f.ctrlPos());
            routeBox = routeBox == null ? b : routeBox.union(b);
        }
        return !world.getEntitiesByType(ModEntities.SLIDING_PLATFORM, routeBox.expand(16),
                net.minecraft.entity.Entity::isAlive).isEmpty();
    }

    private boolean isCabinAt(BlockPos ctrlPos) {
        World w = getWorld();
        return w != null && w.getBlockEntity(ctrlPos) instanceof PlatformControllerBlockEntity c
                && c.isCabinPresent();
    }

    private void tickRideQueue(World world) {
        if (!(world instanceof ServerWorld sw)) { rideQueue.clear(); wasFlying = false; return; }
        if (isCabinFlying(sw)) { wasFlying = true; return; }
        if (wasFlying) {
            wasFlying = false;
            arrivedAtTick = world.getTime();
        }
        if (rideQueue.isEmpty()) return;
        if (world.getTime() - arrivedAtTick < QUEUE_PAUSE_TICKS) return;
        BlockPos target = rideQueue.poll();
        if (target == null || isCabinAt(target)) return;
        startQueuedRide(target);
    }

    public static void serverTick(World world, BlockPos pos, ElevatorScreenBlockEntity be) {
        if (++be.ticker % 20 != 0) return;
        be.tickRideQueue(world);
        be.refresh(world);
    }

    private void refreshNow() {
        if (getWorld() != null) refresh(getWorld());
    }

    private void refresh(World world) {
        if (world.isClient) return;

        boolean empty = true;
        boolean moving = false;
        int no = -1;
        String nm = "";

        List<Floor> fs = floors();
        empty = fs.isEmpty();
        if (!empty && world instanceof ServerWorld sw) {

            Box routeBox = null;
            for (Floor f : fs) {
                Box b = new Box(f.ctrlPos());
                routeBox = routeBox == null ? b : routeBox.union(b);
            }
            List<SlidingPlatformEntity> flying = sw.getEntitiesByType(ModEntities.SLIDING_PLATFORM,
                    routeBox.expand(16),
                    e -> e.isAlive() && e.getRideTargetControllerPos() != null);
            if (!flying.isEmpty()) {
                BlockPos tgt = flying.get(0).getRideTargetControllerPos();
                moving = true;
                for (Floor f : fs) {
                    if (f.ctrlPos().equals(tgt)) { no = f.number(); nm = f.name(); break; }
                }
            } else {
                for (Floor f : fs) {
                    if (world.getBlockEntity(f.ctrlPos()) instanceof PlatformControllerBlockEntity c
                            && c.isCabinPresent()) { no = f.number(); nm = f.name(); break; }
                }
            }
        }

        if (empty != dispEmpty || moving != dispMoving || no != dispNo || !nm.equals(dispName)) {
            dispEmpty = empty;
            dispMoving = moving;
            dispNo = no;
            dispName = nm;
            markDirty();
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
        }
    }

    private void say(@Nullable PlayerEntity player, String key) {
        if (player != null) player.sendMessage(Text.translatable(key), true);
    }

    private void say(@Nullable PlayerEntity player, String key, Object a, Object b) {
        if (player != null) player.sendMessage(Text.translatable(key, a, b), true);
    }

    @Override
    public BlockEntityUpdateS2CPacket toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putString("name", name);

        NbtList nums = new NbtList();
        for (Map.Entry<BlockPos, Integer> e : floorNumbers.entrySet()) {
            NbtCompound entry = new NbtCompound();
            entry.putLong("ctrl", e.getKey().asLong());
            entry.putInt("num", e.getValue());
            nums.add(entry);
        }
        nbt.put("floors", nums);

        nbt.putBoolean("dispEmpty", dispEmpty);
        nbt.putBoolean("dispMoving", dispMoving);
        nbt.putInt("dispNo", dispNo);
        nbt.putString("dispName", dispName);
        if (!chain.isEmpty()) nbt.putString("chain", chain);

        if (callFloor > 0) nbt.putInt("callFloor", callFloor);
        if (wasPowered) nbt.putBoolean("rsWasP", true);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        name = nbt.getString("name");
        floorNumbers.clear();
        NbtList nums = nbt.getList("floors", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < nums.size(); i++) {
            NbtCompound entry = nums.getCompound(i);
            floorNumbers.put(BlockPos.fromLong(entry.getLong("ctrl")), entry.getInt("num"));
        }
        dispEmpty = nbt.getBoolean("dispEmpty");
        dispMoving = nbt.getBoolean("dispMoving");
        dispNo = nbt.getInt("dispNo");
        dispName = nbt.getString("dispName");
        chain = nbt.getString("chain");
        callFloor = nbt.getInt("callFloor");
        wasPowered = nbt.getBoolean("rsWasP");
    }
}

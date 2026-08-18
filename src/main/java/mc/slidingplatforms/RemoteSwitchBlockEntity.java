package mc.slidingplatforms;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

public class RemoteSwitchBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory {

    private final Set<BlockPos> targets = new LinkedHashSet<>();
    private boolean wasPowered = false;

    public RemoteSwitchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.REMOTE_SWITCH_BE, pos, state);
    }

    public void toggleTarget(BlockPos controllerPos) {
        if (!targets.remove(controllerPos)) targets.add(controllerPos);
        markDirty();
    }

    public Set<BlockPos> getTargets() {
        return targets;
    }

    public int targetCount() { return targets.size(); }

    @Override
    public Text getDisplayName() {
        return getCachedState().getBlock().getName();
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new RemoteSwitchScreenHandler(syncId, this);
    }

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        buf.writeBlockPos(pos);

        buf.writeVarInt(targets.size());
        for (BlockPos t : targets) buf.writeBlockPos(t);

        World world = getWorld();
        java.util.List<PlatformRegistry.Entry> entries = world != null
                ? PlatformRegistry.list(world) : java.util.List.of();
        buf.writeVarInt(entries.size());
        for (PlatformRegistry.Entry e : entries) {
            buf.writeBlockPos(e.pos());
            buf.writeString(e.name());
        }
    }

    public void onRedstoneUpdate(boolean powered) {
        if (powered && !wasPowered) trigger(null);
        if (powered != wasPowered) {
            wasPowered = powered;
            markDirty();
        }
    }

    public void trigger(@Nullable PlayerEntity player) {
        World world = getWorld();
        if (world == null || world.isClient) return;

        if (targets.isEmpty()) {
            say(player, "message.slidingplatforms.switch_no_targets");
            return;
        }

        int fired = 0;
        for (BlockPos controllerPos : targets) {
            if (!world.isChunkLoaded(controllerPos)) continue;
            if (world.getBlockEntity(controllerPos) instanceof PlatformControllerBlockEntity controllerBE) {
                controllerBE.trigger(player);
                fired++;
            }
        }
        if (fired == 0) {
            say(player, "message.slidingplatforms.controller_missing");
        }
    }

    private void say(@Nullable PlayerEntity player, String messageKey) {
        if (player != null) player.sendMessage(Text.translatable(messageKey), true);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        long[] arr = new long[targets.size()];
        int i = 0;
        for (BlockPos t : targets) arr[i++] = t.asLong();
        nbt.putLongArray("targets", arr);
        nbt.putBoolean("wasPowered", wasPowered);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        targets.clear();
        for (long packed : nbt.getLongArray("targets")) {
            targets.add(BlockPos.fromLong(packed));
        }
        wasPowered = nbt.getBoolean("wasPowered");
    }
}

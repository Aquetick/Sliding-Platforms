package mc.slidingplatforms;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;

public class PlatformLockScreenHandler extends ScreenHandler {

    private final BlockPos pos;
    private final boolean lockOn;
    private final String owner;
    private final String trusted;

    public PlatformLockScreenHandler(int syncId, BlockPos pos, boolean on, String owner, String trusted) {
        super(ModScreens.PLATFORM_LOCK, syncId);
        this.pos = pos;
        this.lockOn = on;
        this.owner = owner;
        this.trusted = trusted;
    }

    public PlatformLockScreenHandler(int syncId, PlayerInventory inv, PacketByteBuf buf) {
        super(ModScreens.PLATFORM_LOCK, syncId);
        this.pos = buf.readBlockPos();
        this.lockOn = buf.readBoolean();
        this.owner = buf.readString(24);
        this.trusted = buf.readString(96);
    }

    public BlockPos getPos() { return pos; }
    public boolean lockOn() { return lockOn; }
    public String lockOwner() { return owner; }
    public String lockTrusted() { return trusted; }

    @Override
    public boolean canUse(PlayerEntity player) {

        return player.getWorld().getBlockEntity(pos) instanceof PlatformControllerBlockEntity;
    }

    @Override
    public net.minecraft.item.ItemStack quickMove(PlayerEntity player, int slot) {
        return net.minecraft.item.ItemStack.EMPTY;
    }
}

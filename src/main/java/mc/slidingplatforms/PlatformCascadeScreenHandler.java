package mc.slidingplatforms;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;

public class PlatformCascadeScreenHandler extends ScreenHandler {

    private final BlockPos pos;
    private final boolean cascadeOn;
    private final int cascadeDelay;
    private final boolean cascadeInvert;

    public PlatformCascadeScreenHandler(int syncId, BlockPos pos, boolean on, int delay, boolean invert) {
        super(ModScreens.PLATFORM_CASCADE, syncId);
        this.pos = pos;
        this.cascadeOn = on;
        this.cascadeDelay = delay;
        this.cascadeInvert = invert;
    }

    public PlatformCascadeScreenHandler(int syncId, PlayerInventory inv, PacketByteBuf buf) {
        super(ModScreens.PLATFORM_CASCADE, syncId);
        this.pos = buf.readBlockPos();
        this.cascadeOn = buf.readBoolean();
        this.cascadeDelay = buf.readVarInt();
        this.cascadeInvert = buf.isReadable() && buf.readBoolean();
    }

    public BlockPos getPos() { return pos; }
    public boolean cascadeOn() { return cascadeOn; }
    public int cascadeDelay() { return cascadeDelay; }
    public boolean cascadeInvert() { return cascadeInvert; }

    @Override
    public boolean canUse(PlayerEntity player) {

        return player.getWorld().getBlockEntity(pos) instanceof PlatformControllerBlockEntity;
    }

    @Override
    public net.minecraft.item.ItemStack quickMove(PlayerEntity player, int slot) {
        return net.minecraft.item.ItemStack.EMPTY;
    }
}

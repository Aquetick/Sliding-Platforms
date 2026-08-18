package mc.slidingplatforms;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class PlatformControllerScreenHandler extends ScreenHandler {

    public final BlockPos pos;
    public final Direction slideDir;
    public final int slideOffset;
    public final float speed;
    public final String name;
    public final Direction anchorDir;
    public final String boundScreenName;
    public final int redstoneMode;
    public final boolean lampGlow;

    public PlatformControllerScreenHandler(int syncId, PlatformControllerBlockEntity be) {
        super(ModScreens.PLATFORM_CONTROLLER, syncId);
        this.pos = be.getPos();
        this.slideDir = be.getSlideDir();
        this.slideOffset = be.getSlideOffset();
        this.speed = be.getSpeed();
        this.name = be.getPlatformName();
        this.anchorDir = be.getAnchorDir();
        this.boundScreenName = be.boundScreenName();
        this.redstoneMode = be.getRedstoneMode();
        this.lampGlow = be.isLampGlow();
    }

    public PlatformControllerScreenHandler(int syncId, PlayerInventory inv, PacketByteBuf buf) {
        super(ModScreens.PLATFORM_CONTROLLER, syncId);
        this.pos = buf.readBlockPos();
        int dirId = buf.readByte();
        Direction[] all = Direction.values();
        this.slideDir = (dirId >= 0 && dirId < all.length) ? all[dirId] : Direction.EAST;
        this.slideOffset = buf.readInt();
        this.speed = buf.readFloat();
        this.name = buf.readString();
        int anchId = buf.readByte();
        this.anchorDir = (anchId >= 0 && anchId < all.length && all[anchId].getAxis().isHorizontal())
                ? all[anchId] : Direction.NORTH;
        this.boundScreenName = buf.readString();
        this.redstoneMode = buf.readByte() & 0xFF;

        this.lampGlow = buf.isReadable() ? buf.readBoolean() : true;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public net.minecraft.item.ItemStack quickMove(PlayerEntity player, int slot) {
        return net.minecraft.item.ItemStack.EMPTY;
    }
}

package mc.slidingplatforms;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;

public class PlatformSoundsScreenHandler extends ScreenHandler {

    private final BlockPos pos;
    private final boolean soundsEnabled;
    private final String sndStart;
    private final String sndStop;
    private final String sndArrive;
    private final String sndHum;

    public PlatformSoundsScreenHandler(int syncId, BlockPos pos, boolean enabled,
                                   String start, String stop, String arrive, String hum) {
        super(ModScreens.PLATFORM_SOUNDS, syncId);
        this.pos = pos;
        this.soundsEnabled = enabled;
        this.sndStart = start;
        this.sndStop = stop;
        this.sndArrive = arrive;
        this.sndHum = hum;
    }

    public PlatformSoundsScreenHandler(int syncId, PlayerInventory inv, PacketByteBuf buf) {
        super(ModScreens.PLATFORM_SOUNDS, syncId);
        this.pos = buf.readBlockPos();
        this.soundsEnabled = buf.readBoolean();
        this.sndStart = buf.readString(48);
        this.sndStop = buf.readString(48);
        this.sndArrive = buf.readString(48);
        this.sndHum = buf.readString(48);
    }

    public BlockPos getPos() { return pos; }
    public boolean soundsEnabled() { return soundsEnabled; }
    public String getSndStart() { return sndStart; }
    public String getSndStop() { return sndStop; }
    public String getSndArrive() { return sndArrive; }
    public String getSndHum() { return sndHum; }

    @Override
    public boolean canUse(PlayerEntity player) {

        return player.getWorld().getBlockEntity(pos) instanceof PlatformControllerBlockEntity;
    }

    @Override
    public net.minecraft.item.ItemStack quickMove(PlayerEntity player, int slot) {
        return net.minecraft.item.ItemStack.EMPTY;
    }
}

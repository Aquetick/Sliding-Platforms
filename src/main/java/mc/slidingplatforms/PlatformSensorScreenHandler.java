package mc.slidingplatforms;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;

public class PlatformSensorScreenHandler extends ScreenHandler {

    private final BlockPos pos;
    private final boolean sensorOn;
    private final int radius;
    private final boolean players;
    private final boolean mobs;
    private final boolean invert;
    private final String names;

    private final int zoneX, zoneY, zoneZ;

    private final int autoClose;

    public PlatformSensorScreenHandler(int syncId, BlockPos pos, boolean on, int radius,
                                   boolean players, boolean mobs, boolean invert, String names,
                                   int zoneX, int zoneY, int zoneZ, int autoClose) {
        super(ModScreens.PLATFORM_SENSOR, syncId);
        this.pos = pos;
        this.sensorOn = on;
        this.radius = radius;
        this.players = players;
        this.mobs = mobs;
        this.invert = invert;
        this.names = names;
        this.zoneX = zoneX;
        this.zoneY = zoneY;
        this.zoneZ = zoneZ;
        this.autoClose = autoClose;
    }

    public PlatformSensorScreenHandler(int syncId, PlayerInventory inv, PacketByteBuf buf) {
        super(ModScreens.PLATFORM_SENSOR, syncId);
        this.pos = buf.readBlockPos();
        this.sensorOn = buf.readBoolean();
        this.radius = buf.readVarInt();
        this.players = buf.readBoolean();
        this.mobs = buf.readBoolean();
        this.invert = buf.readBoolean();
        this.names = buf.readString(96);
        this.zoneX = buf.readVarInt();
        this.zoneY = buf.readVarInt();
        this.zoneZ = buf.readVarInt();
        this.autoClose = buf.readVarInt();
    }

    public BlockPos getPos() { return pos; }
    public boolean sensorOn() { return sensorOn; }
    public int sensorRadius() { return radius; }
    public boolean sensorPlayers() { return players; }
    public boolean sensorMobs() { return mobs; }
    public boolean sensorInvert() { return invert; }
    public String sensorNames() { return names; }
    public int zoneX() { return zoneX; }
    public int zoneY() { return zoneY; }
    public int zoneZ() { return zoneZ; }
    public int autoClose() { return autoClose; }

    @Override
    public boolean canUse(PlayerEntity player) {

        return player.getWorld().getBlockEntity(pos) instanceof PlatformControllerBlockEntity;
    }

    @Override
    public net.minecraft.item.ItemStack quickMove(PlayerEntity player, int slot) {
        return net.minecraft.item.ItemStack.EMPTY;
    }
}

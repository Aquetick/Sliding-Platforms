package mc.slidingplatforms;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class ScreenSettingsScreenHandler extends ScreenHandler {

    public record FloorRow(BlockPos pos, int number, String name) {}

    public record LinkRow(String key, String name) {}

    public final BlockPos screenPos;
    public final String screenName;
    public final String chainName;
    public final boolean chainTab;
    public final int callFloor;
    public final List<FloorRow> rows = new ArrayList<>();
    public final List<LinkRow> links = new ArrayList<>();

    public ScreenSettingsScreenHandler(int syncId, BlockPos screenPos, String screenName,
                                       String chainName, int callFloor) {
        super(ModScreens.SCREEN_SETTINGS, syncId);
        this.screenPos = screenPos;
        this.screenName = screenName;
        this.chainName = chainName;
        this.chainTab = false;
        this.callFloor = callFloor;
    }

    public ScreenSettingsScreenHandler(int syncId, PlayerInventory inv, PacketByteBuf buf) {
        super(ModScreens.SCREEN_SETTINGS, syncId);
        this.screenPos = buf.readBlockPos();
        this.screenName = buf.readString();
        this.chainName = buf.readString();
        int rowCount = buf.readVarInt();
        for (int i = 0; i < rowCount; i++) {
            rows.add(new FloorRow(buf.readBlockPos(), buf.readVarInt(), buf.readString()));
        }
        int linkCount = buf.readVarInt();
        for (int i = 0; i < linkCount; i++) {
            links.add(new LinkRow(buf.readString(), buf.readString()));
        }
        this.chainTab = buf.readBoolean();
        this.callFloor = buf.readVarInt();
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

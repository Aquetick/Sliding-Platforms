package mc.slidingplatforms;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class FloorSelectScreenHandler extends ScreenHandler {

    public record FloorRow(int number, BlockPos pos, String name, boolean cabinHere) {}

    public final BlockPos screenPos;
    public final List<FloorRow> rows = new ArrayList<>();

    public FloorSelectScreenHandler(int syncId, BlockPos screenPos) {
        super(ModScreens.FLOOR_SELECT, syncId);
        this.screenPos = screenPos;

    }

    public FloorSelectScreenHandler(int syncId, PlayerInventory inv, PacketByteBuf buf) {
        super(ModScreens.FLOOR_SELECT, syncId);
        this.screenPos = buf.readBlockPos();
        int rowCount = buf.readVarInt();
        for (int i = 0; i < rowCount; i++) {
            rows.add(new FloorRow(buf.readVarInt(), buf.readBlockPos(),
                    buf.readString(), buf.readBoolean()));
        }
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

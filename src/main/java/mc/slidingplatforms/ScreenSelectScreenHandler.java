package mc.slidingplatforms;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class ScreenSelectScreenHandler extends ScreenHandler {

    public record ScreenRow(String key, String name) {}

    public final BlockPos controllerPos;
    public final List<ScreenRow> rows = new ArrayList<>();

    public ScreenSelectScreenHandler(int syncId, BlockPos controllerPos) {
        super(ModScreens.SCREEN_SELECT, syncId);
        this.controllerPos = controllerPos;
    }

    public ScreenSelectScreenHandler(int syncId, PlayerInventory inv, PacketByteBuf buf) {
        super(ModScreens.SCREEN_SELECT, syncId);
        this.controllerPos = buf.readBlockPos();
        int rowCount = buf.readVarInt();
        for (int i = 0; i < rowCount; i++) {
            rows.add(new ScreenRow(buf.readString(), buf.readString()));
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

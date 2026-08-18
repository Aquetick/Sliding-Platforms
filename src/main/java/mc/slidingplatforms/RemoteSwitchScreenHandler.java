package mc.slidingplatforms;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class RemoteSwitchScreenHandler extends ScreenHandler {

    public record ControllerRow(BlockPos pos, String name, boolean selected) {}

    public final BlockPos pos;
    public final Set<BlockPos> selected = new LinkedHashSet<>();
    public final List<ControllerRow> rows = new ArrayList<>();

    public RemoteSwitchScreenHandler(int syncId, RemoteSwitchBlockEntity be) {
        super(ModScreens.REMOTE_SWITCH, syncId);
        this.pos = be.getPos();
        this.selected.addAll(be.getTargets());

    }

    public RemoteSwitchScreenHandler(int syncId, PlayerInventory inv, PacketByteBuf buf) {
        super(ModScreens.REMOTE_SWITCH, syncId);
        this.pos = buf.readBlockPos();
        int selCount = buf.readVarInt();
        for (int i = 0; i < selCount; i++) selected.add(buf.readBlockPos());
        int rowCount = buf.readVarInt();
        for (int i = 0; i < rowCount; i++) {
            BlockPos p = buf.readBlockPos();
            String name = buf.readString();
            rows.add(new ControllerRow(p, name, selected.contains(p)));
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

package mc.slidingplatforms;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;

public class ConfigScreenHandler extends ScreenHandler {

    private final String json;

    public ConfigScreenHandler(int syncId, String json) {
        super(ModScreens.CONFIG, syncId);
        this.json = json;
    }

    public ConfigScreenHandler(int syncId, PlayerInventory inv, PacketByteBuf buf) {
        super(ModScreens.CONFIG, syncId);
        this.json = buf.readString();
    }

    public String json() { return json; }

    @Override
    public net.minecraft.item.ItemStack quickMove(PlayerEntity player, int slot) {
        return net.minecraft.item.ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {

        return true;
    }
}

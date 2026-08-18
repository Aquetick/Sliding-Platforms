package mc.slidingplatforms.client;

import mc.slidingplatforms.SlidingPlatforms;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.function.Consumer;

public final class PlatformTabStrip {

    private static final int COUNT = 5;
    private static final String[] KEYS = {
            "gui.slidingplatforms.tab.main",
            "gui.slidingplatforms.tab.sounds",
            "gui.slidingplatforms.tab.sensor",
            "gui.slidingplatforms.tab.lock",
            "gui.slidingplatforms.tab.cascade",
    };
    private static final Identifier[] TARGETS = {
            SlidingPlatforms.PLATFORM_MAIN_GUI,
            SlidingPlatforms.PLATFORM_SOUNDS_GUI,
            SlidingPlatforms.PLATFORM_SENSOR_GUI,
            SlidingPlatforms.PLATFORM_LOCK_GUI,
            SlidingPlatforms.PLATFORM_CASCADE_GUI,
    };

    private final BlockPos pos;
    private final int active;
    private final ButtonWidget[] tabs = new ButtonWidget[2];
    private int offset;

    public PlatformTabStrip(BlockPos pos, int active) {
        this.pos = pos;
        this.active = active % COUNT;
        this.offset = this.active;
    }

    public void attach(Consumer<ButtonWidget> add, int left, int top) {
        ButtonWidget prev = ButtonWidget.builder(Text.literal("‹"), b -> scroll(-1))
                .dimensions(left + 8, top, 18, 14).build();
        ButtonWidget next = ButtonWidget.builder(Text.literal("›"), b -> scroll(1))
                .dimensions(left + 150, top, 18, 14).build();
        add.accept(prev);
        add.accept(next);
        for (int slot = 0; slot < 2; slot++) {
            final int s = slot;
            tabs[slot] = ButtonWidget.builder(Text.empty(), b -> click(s))
                    .dimensions(left + 28 + slot * 60, top, 58, 14).build();
            add.accept(tabs[slot]);
        }
        refresh();
    }

    private void scroll(int dir) {
        offset = ((offset + dir) % COUNT + COUNT) % COUNT;
        refresh();
    }

    private void click(int slot) {
        int tab = (offset + slot) % COUNT;
        if (tab == active) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        ClientPlayNetworking.send(TARGETS[tab], buf);
    }

    private void refresh() {
        for (int slot = 0; slot < 2; slot++) {
            int tab = (offset + slot) % COUNT;
            if (tab == active) {
                tabs[slot].setMessage(Text.literal("• ").append(Text.translatable(KEYS[tab])));
                tabs[slot].active = false;
            } else {
                tabs[slot].setMessage(Text.translatable(KEYS[tab]));
                tabs[slot].active = true;
            }
        }
    }
}

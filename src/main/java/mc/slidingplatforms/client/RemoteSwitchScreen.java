package mc.slidingplatforms.client;

import mc.slidingplatforms.RemoteSwitchScreenHandler;
import mc.slidingplatforms.SlidingPlatforms;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class RemoteSwitchScreen extends HandledScreen<RemoteSwitchScreenHandler> {

    private static final int ROWS_PER_PAGE = 5;

    private int page = 0;
    private final List<ButtonWidget> rowButtons = new ArrayList<>();
    private ButtonWidget prevBtn, nextBtn;

    public RemoteSwitchScreen(RemoteSwitchScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 190;
    }

    @Override
    protected void init() {
        super.init();
        rebuildRows();
    }

    private void rebuildRows() {
        for (ButtonWidget b : rowButtons) remove(b);
        rowButtons.clear();
        if (prevBtn != null) { remove(prevBtn); remove(nextBtn); }

        int left = this.x, top = this.y;
        List<RemoteSwitchScreenHandler.ControllerRow> rows = handler.rows;

        int pages = Math.max(1, (int) Math.ceil(rows.size() / (double) ROWS_PER_PAGE));
        page = MathHelper_clampPage(page, pages);

        int from = page * ROWS_PER_PAGE;
        int to = Math.min(rows.size(), from + ROWS_PER_PAGE);
        for (int i = from; i < to; i++) {
            RemoteSwitchScreenHandler.ControllerRow row = rows.get(i);
            ButtonWidget btn = ButtonWidget.builder(rowLabel(row), b -> toggle(row))
                    .dimensions(left + 8, top + 20 + (i - from) * 22, 160, 20).build();
            rowButtons.add(addDrawableChild(btn));
        }

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.slidingplatforms.done"), b -> this.close())
                .dimensions(left + 8, top + 140, 160, 18).build());

        if (pages > 1) {
            int y = top + 164;
            prevBtn = addDrawableChild(ButtonWidget.builder(Text.literal("‹"), b -> {
                page = MathHelper_clampPage(page - 1, pages);
                rebuildRows();
            }).dimensions(left + 8, y, 76, 18).build());
            nextBtn = addDrawableChild(ButtonWidget.builder(Text.literal("›"), b -> {
                page = MathHelper_clampPage(page + 1, pages);
                rebuildRows();
            }).dimensions(left + 92, y, 76, 18).build());
        }
    }

    private static int MathHelper_clampPage(int p, int pages) {
        return Math.max(0, Math.min(p, pages - 1));
    }

    private Text rowLabel(RemoteSwitchScreenHandler.ControllerRow row) {
        BlockPos p = row.pos();
        Text name = row.selected()
                ? Text.literal("✔ ").formatted(Formatting.GREEN).append(Text.literal(row.name()).formatted(Formatting.WHITE))
                : Text.literal("✘ ").formatted(Formatting.DARK_GRAY).append(Text.literal(row.name()).formatted(Formatting.GRAY));
        return Text.empty().append(name)
                .append(Text.literal("  (" + p.getX() + ", " + p.getY() + ", " + p.getZ() + ")")
                        .formatted(Formatting.DARK_GRAY));
    }

    private void toggle(RemoteSwitchScreenHandler.ControllerRow row) {
        boolean now = !row.selected();

        int idx = handler.rows.indexOf(row);
        handler.rows.set(idx, new RemoteSwitchScreenHandler.ControllerRow(row.pos(), row.name(), now));
        if (now) handler.selected.add(row.pos()); else handler.selected.remove(row.pos());

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(handler.pos);
        buf.writeBlockPos(row.pos());
        ClientPlayNetworking.send(SlidingPlatforms.SWITCH_TOGGLE, buf);

        rebuildRows();
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {

    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {        ctx.fillGradient(this.x, this.y, this.x + this.backgroundWidth, this.y + this.backgroundHeight,
                0xC0101015, 0xD0101015);
        int b = 0xFF6A6A75;
        ctx.fill(this.x, this.y, this.x + backgroundWidth, this.y + 1, b);
        ctx.fill(this.x, this.y + backgroundHeight - 1, this.x + backgroundWidth, this.y + backgroundHeight, b);
        ctx.fill(this.x, this.y, this.x + 1, this.y + backgroundHeight, b);
        ctx.fill(this.x + backgroundWidth - 1, this.y, this.x + backgroundWidth, this.y + backgroundHeight, b);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title,
                this.x + this.backgroundWidth / 2, this.y + 6, 0xFFFFFF);
        if (handler.rows.isEmpty()) {
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.translatable("gui.slidingplatforms.no_controllers"),
                    this.x + this.backgroundWidth / 2, this.y + 60, 0x808080);
        }
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }

    private void remove(ButtonWidget w) {
        this.remove((net.minecraft.client.gui.Element) w);
    }
}

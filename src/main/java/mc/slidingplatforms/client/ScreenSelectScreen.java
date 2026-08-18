package mc.slidingplatforms.client;

import mc.slidingplatforms.ScreenSelectScreenHandler;
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

public class ScreenSelectScreen extends HandledScreen<ScreenSelectScreenHandler> {

    private static final int ROWS_PER_PAGE = 5;

    private int page = 0;
    private final List<ButtonWidget> rowButtons = new ArrayList<>();
    private ButtonWidget prevBtn, nextBtn;

    public ScreenSelectScreen(ScreenSelectScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 190;
    }

    @Override
    protected void init() {
        super.init();
        rebuildRows();
    }

    private int totalRows() { return handler.rows.size() + 1; }

    private void rebuildRows() {
        for (ButtonWidget b : rowButtons) remove((net.minecraft.client.gui.Element) b);
        rowButtons.clear();
        if (prevBtn != null) {
            remove((net.minecraft.client.gui.Element) prevBtn);
            remove((net.minecraft.client.gui.Element) nextBtn);
        }

        int left = this.x, top = this.y;
        int total = totalRows();
        int pages = Math.max(1, (int) Math.ceil(total / (double) ROWS_PER_PAGE));
        page = Math.max(0, Math.min(page, pages - 1));

        int from = page * ROWS_PER_PAGE;
        int to = Math.min(total, from + ROWS_PER_PAGE);
        for (int i = from; i < to; i++) {
            if (i == 0) {
                rowButtons.add(addDrawableChild(ButtonWidget.builder(
                        Text.translatable("gui.slidingplatforms.unbind").formatted(Formatting.RED),
                        b -> bind(""))
                        .dimensions(left + 8, top + 20, 160, 20).build()));
                continue;
            }
            ScreenSelectScreenHandler.ScreenRow row = handler.rows.get(i - 1);
            Text label;
            if (row.key().startsWith("chain:")) {

                label = Text.literal("⟟ ").formatted(Formatting.GOLD)
                        .append(Text.literal(row.name()).formatted(Formatting.WHITE));
            } else {

                try {
                    BlockPos p = BlockPos.fromLong(Long.parseLong(row.key()));
                    label = Text.literal("▦ ").formatted(Formatting.AQUA)
                            .append(Text.literal(row.name()).formatted(Formatting.WHITE))
                            .append(Text.literal("  (" + p.getX() + ", " + p.getY() + ", "
                                    + p.getZ() + ")").formatted(Formatting.DARK_GRAY));
                } catch (NumberFormatException bad) {
                    label = Text.literal("▦ ").formatted(Formatting.AQUA)
                            .append(Text.literal(row.name()).formatted(Formatting.WHITE));
                }
            }
            rowButtons.add(addDrawableChild(ButtonWidget.builder(label, b -> bind(row.key()))
                    .dimensions(left + 8, top + 20 + (i - from) * 22, 160, 20).build()));
        }

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.slidingplatforms.done"), b -> this.close())
                .dimensions(left + 8, top + 140, 160, 18).build());

        if (pages > 1) {
            int y = top + 164;
            int p = pages;
            prevBtn = addDrawableChild(ButtonWidget.builder(Text.literal("‹"), b -> {
                page = Math.max(0, Math.min(page - 1, p - 1));
                rebuildRows();
            }).dimensions(left + 8, y, 76, 18).build());
            nextBtn = addDrawableChild(ButtonWidget.builder(Text.literal("›"), b -> {
                page = Math.max(0, Math.min(page + 1, p - 1));
                rebuildRows();
            }).dimensions(left + 92, y, 76, 18).build());
        }
    }

    private void bind(String key) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(handler.controllerPos);
        buf.writeString(key);
        ClientPlayNetworking.send(SlidingPlatforms.BIND_SCREEN, buf);
        this.close();
    }

    @Override
    protected void drawForeground(DrawContext ctx, int mouseX, int mouseY) {

    }

    @Override
    protected void drawBackground(DrawContext ctx, float delta, int mouseX, int mouseY) {
        ctx.fillGradient(this.x, this.y, this.x + this.backgroundWidth, this.y + this.backgroundHeight,
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
                    Text.translatable("gui.slidingplatforms.no_screens"),
                    this.x + this.backgroundWidth / 2, this.y + 60, 0x808080);
        }
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }
}

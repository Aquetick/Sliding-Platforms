package mc.slidingplatforms.client;

import mc.slidingplatforms.PlatformCascadeScreenHandler;
import mc.slidingplatforms.SlidingPlatforms;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

public class PlatformCascadeScreen extends HandledScreen<PlatformCascadeScreenHandler> {

    private boolean on;
    private int delay;
    private boolean invert;

    private ButtonWidget btnOn;
    private ButtonWidget btnDelay;
    private ButtonWidget btnInvert;

    public PlatformCascadeScreen(PlatformCascadeScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 150;
        this.on = handler.cascadeOn();
        this.delay = handler.cascadeDelay();
        this.invert = handler.cascadeInvert();
    }

    @Override
    protected void init() {
        super.init();
        int left = this.x, top = this.y;

        new PlatformTabStrip(handler.getPos(), 4).attach(this::addDrawableChild, left, top + 18);

        btnOn = addDrawableChild(ButtonWidget.builder(onLabel(), b -> {
            on = !on;
            btnOn.setMessage(onLabel());
            btnDelay.active = on;
            btnInvert.active = on;
            sendNow();
        }).dimensions(left + 8, top + 38, 160, 18).build());

        btnDelay = ButtonWidget.builder(delayLabel(), b -> {
            delay = delay % 4 + 1;
            b.setMessage(delayLabel());
            sendNow();
        }).dimensions(left + 8, top + 60, 160, 18).build();
        btnDelay.active = on;
        addDrawableChild(btnDelay);

        btnInvert = ButtonWidget.builder(invertLabel(), b -> {
            invert = !invert;
            b.setMessage(invertLabel());
            sendNow();
        }).dimensions(left + 8, top + 82, 160, 18).build();
        btnInvert.active = on;
        addDrawableChild(btnInvert);

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.slidingplatforms.done"), b -> this.close())
                .dimensions(left + 8, top + 126, 160, 18).build());
    }

    private Text onLabel() {
        return Text.translatable("gui.slidingplatforms.cascade.toggle",
                Text.translatable(on ? "gui.slidingplatforms.snd_on" : "gui.slidingplatforms.snd_off"));
    }

    private Text delayLabel() {
        return Text.translatable("gui.slidingplatforms.cascade.delay", delay);
    }

    private Text invertLabel() {
        return Text.translatable("gui.slidingplatforms.cascade.invert",
                Text.translatable(invert ? "gui.slidingplatforms.cascade.invert_root"
                                         : "gui.slidingplatforms.cascade.invert_edge"));
    }

    private void sendNow() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(handler.getPos());
        buf.writeBoolean(on);
        buf.writeVarInt(delay);
        buf.writeBoolean(invert);
        ClientPlayNetworking.send(SlidingPlatforms.PLATFORM_CASCADE_SET, buf);
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
        int left = this.x, top = this.y;
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title,
                left + this.backgroundWidth / 2, top + 6, 0xFFFFFF);

        MarqueeText.drawScaled(ctx, this.textRenderer,
                Text.translatable("gui.slidingplatforms.cascade.hint1"),
                left + 8, top + 102, 160, 0.78f, 0x707070);
        MarqueeText.drawScaled(ctx, this.textRenderer,
                Text.translatable("gui.slidingplatforms.cascade.hint2"),
                left + 8, top + 109, 160, 0.78f, 0x707070);
        MarqueeText.drawScaled(ctx, this.textRenderer,
                Text.translatable("gui.slidingplatforms.cascade.hint3"),
                left + 8, top + 116, 160, 0.78f, 0x707070);
        MarqueeText.drawScaled(ctx, this.textRenderer,
                Text.translatable("gui.slidingplatforms.cascade.hint4"),
                left + 8, top + 123, 160, 0.78f, 0x707070);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }
}

package mc.slidingplatforms.client;

import mc.slidingplatforms.PlatformControllerBlockEntity;
import mc.slidingplatforms.PlatformLockScreenHandler;
import mc.slidingplatforms.SlidingPlatforms;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

public class PlatformLockScreen extends HandledScreen<PlatformLockScreenHandler> {

    private boolean on;
    private String owner;
    private String trusted;

    private ButtonWidget btnOn;
    private TextFieldWidget ownerField;
    private TextFieldWidget trustedField;

    public PlatformLockScreen(PlatformLockScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 160;
        this.on = handler.lockOn();
        this.owner = handler.lockOwner();
        this.trusted = handler.lockTrusted();
    }

    @Override
    protected void init() {
        super.init();
        int left = this.x, top = this.y;

        new PlatformTabStrip(handler.getPos(), 3).attach(this::addDrawableChild, left, top + 18);

        btnOn = addDrawableChild(ButtonWidget.builder(onLabel(), b -> {
            on = !on;
            btnOn.setMessage(onLabel());
            sendNow();
        }).dimensions(left + 8, top + 38, 160, 18).build());

        ownerField = new TextFieldWidget(this.textRenderer, left + 8, top + 71, 160, 16,
                Text.translatable("gui.slidingplatforms.lock.owner"));
        ownerField.setMaxLength(16);
        ownerField.setDrawsBackground(true);
        ownerField.setText(owner);
        ownerField.setChangedListener(s -> {
            String clean = PlatformControllerBlockEntity.sanitizeNames(s).replace(",", "").trim();
            if (!clean.equals(owner)) {
                owner = clean;
                sendNow();
            }
        });
        addSelectableChild(ownerField);

        trustedField = new TextFieldWidget(this.textRenderer, left + 8, top + 109, 160, 16,
                Text.translatable("gui.slidingplatforms.lock.trusted"));
        trustedField.setMaxLength(96);
        trustedField.setDrawsBackground(true);
        trustedField.setText(trusted);
        trustedField.setChangedListener(s -> {
            String clean = PlatformControllerBlockEntity.sanitizeNames(s);
            if (!clean.equals(trusted)) {
                trusted = clean;
                sendNow();
            }
        });
        addSelectableChild(trustedField);

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.slidingplatforms.done"), b -> this.close())
                .dimensions(left + 8, top + 136, 160, 18).build());
    }

    private Text onLabel() {
        return Text.translatable("gui.slidingplatforms.lock.toggle",
                Text.translatable(on ? "gui.slidingplatforms.snd_on" : "gui.slidingplatforms.snd_off"));
    }

    private void sendNow() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(handler.getPos());
        buf.writeBoolean(on);
        buf.writeString(owner, 24);
        buf.writeString(trusted, 96);
        ClientPlayNetworking.send(SlidingPlatforms.PLATFORM_LOCK_SET, buf);
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

        ctx.drawTextWithShadow(this.textRenderer,
                Text.translatable("gui.slidingplatforms.lock.owner"), left + 8, top + 62, 0xA0A0A0);
        ctx.drawTextWithShadow(this.textRenderer,
                Text.translatable("gui.slidingplatforms.lock.trusted"), left + 8, top + 100, 0xA0A0A0);

        MarqueeText.drawScaled(ctx, this.textRenderer,
                Text.translatable("gui.slidingplatforms.lock.owner_hint"),
                left + 8, top + 89, 160, 0.78f, 0x707070);
        MarqueeText.drawScaled(ctx, this.textRenderer,
                Text.translatable("gui.slidingplatforms.lock.trusted_hint"),
                left + 8, top + 127, 160, 0.78f, 0x707070);
        ownerField.render(ctx, mouseX, mouseY, delta);
        trustedField.render(ctx, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ownerField.isFocused() && ownerField.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (trustedField.isFocused() && trustedField.keyPressed(keyCode, scanCode, modifiers)) return true;

        boolean anyFocused = ownerField.isFocused() || trustedField.isFocused();
        if (anyFocused && client != null && client.options.inventoryKey.matchesKey(keyCode, scanCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (ownerField.charTyped(chr, modifiers)) return true;
        if (trustedField.charTyped(chr, modifiers)) return true;
        return super.charTyped(chr, modifiers);
    }
}

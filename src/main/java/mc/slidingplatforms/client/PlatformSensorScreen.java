package mc.slidingplatforms.client;

import mc.slidingplatforms.PlatformControllerBlockEntity;
import mc.slidingplatforms.PlatformSensorScreenHandler;
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

public class PlatformSensorScreen extends HandledScreen<PlatformSensorScreenHandler> {

    private boolean on;
    private int radius;
    private boolean players;
    private boolean mobs;
    private boolean invert;
    private String names;

    private int zoneX, zoneY, zoneZ;

    private int autoClose;

    private ButtonWidget btnOn, btnPlayers, btnMobs, btnInvert, btnZone, btnZoneClear;
    private DragSlider radiusSlider, autoCloseSlider;
    private TextFieldWidget namesField;

    public PlatformSensorScreen(PlatformSensorScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 258;
        this.on = handler.sensorOn();
        this.radius = handler.sensorRadius();
        this.players = handler.sensorPlayers();
        this.mobs = handler.sensorMobs();
        this.invert = handler.sensorInvert();
        this.names = handler.sensorNames();
        this.zoneX = handler.zoneX();
        this.zoneY = handler.zoneY();
        this.zoneZ = handler.zoneZ();
        this.autoClose = handler.autoClose();
    }

    @Override
    protected void init() {
        super.init();
        int left = this.x, top = this.y;

        new PlatformTabStrip(handler.getPos(), 2).attach(this::addDrawableChild, left, top + 18);

        btnOn = addDrawableChild(ButtonWidget.builder(onLabel(), b -> {
            on = !on;
            btnOn.setMessage(onLabel());
            sendNow();
        }).dimensions(left + 8, top + 38, 160, 18).build());

        radiusSlider = new DragSlider(left + 8, top + 62, 160, 18,
                (radius - 1) / 15.0,
                v -> Text.translatable("gui.slidingplatforms.sensor.radius", String.valueOf(1 + (int) Math.round(v * 15))),
                v -> {
                    radius = 1 + (int) Math.round(v * 15);
                    sendNow();
                });
        addDrawableChild(radiusSlider);

        btnPlayers = addDrawableChild(ButtonWidget.builder(playersLabel(), b -> {
            players = !players;
            btnPlayers.setMessage(playersLabel());
            sendNow();
        }).dimensions(left + 8, top + 86, 78, 18).build());
        btnMobs = addDrawableChild(ButtonWidget.builder(mobsLabel(), b -> {
            mobs = !mobs;
            btnMobs.setMessage(mobsLabel());
            sendNow();
        }).dimensions(left + 90, top + 86, 78, 18).build());

        btnInvert = addDrawableChild(ButtonWidget.builder(invertLabel(), b -> {
            invert = !invert;
            btnInvert.setMessage(invertLabel());
            sendNow();
        }).dimensions(left + 8, top + 110, 160, 18).build());

        btnZone = addDrawableChild(ButtonWidget.builder(zoneLabel(), b -> {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(handler.getPos());
            ClientPlayNetworking.send(SlidingPlatforms.GUI_SENSOR_ZONE, buf);

        }).dimensions(left + 8, top + 134, 132, 18).build());
        btnZoneClear = addDrawableChild(ButtonWidget.builder(Text.literal("✕"), b -> {
            zoneX = zoneY = zoneZ = 0;
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(handler.getPos());
            ClientPlayNetworking.send(SlidingPlatforms.ZONE_CLEAR, buf);
            refreshZoneWidgets();
        }).dimensions(left + 144, top + 134, 24, 18).build());
        refreshZoneWidgets();

        autoCloseSlider = new DragSlider(left + 8, top + 166, 160, 18,
                autoClose / 60.0,
                v -> autoCloseLabel(0 + (int) Math.round(v * 60)),
                v -> {
                    autoClose = (int) Math.round(v * 60);
                    sendNow();
                });
        addDrawableChild(autoCloseSlider);

        namesField = new TextFieldWidget(this.textRenderer, left + 8, top + 203, 160, 16,
                Text.translatable("gui.slidingplatforms.sensor.names"));
        namesField.setMaxLength(96);
        namesField.setDrawsBackground(true);
        namesField.setText(names);
        namesField.setChangedListener(s -> {
            String clean = PlatformControllerBlockEntity.sanitizeNames(s);
            if (!clean.equals(names)) {
                names = clean;
                sendNow();
            }
        });
        addSelectableChild(namesField);

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.slidingplatforms.done"), b -> this.close())
                .dimensions(left + 8, top + 234, 160, 18).build());
    }

    private Text autoCloseLabel(int seconds) {
        return Text.translatable("gui.slidingplatforms.autoclose",
                seconds <= 0
                        ? Text.translatable("gui.slidingplatforms.snd_off")
                        : Text.translatable("gui.slidingplatforms.autoclose_secs", seconds));
    }

    private boolean hasZone() { return zoneX > 0; }

    private Text zoneLabel() {
        return hasZone()
                ? Text.translatable("gui.slidingplatforms.sensor.zone_set", zoneX, zoneY, zoneZ)
                : Text.translatable("gui.slidingplatforms.sensor.zone");
    }

    private void refreshZoneWidgets() {
        btnZone.setMessage(zoneLabel());
        btnZoneClear.active = hasZone();
        radiusSlider.active = !hasZone();
    }

    private Text onLabel() {
        return Text.translatable("gui.slidingplatforms.sensor.toggle",
                Text.translatable(on ? "gui.slidingplatforms.snd_on" : "gui.slidingplatforms.snd_off"));
    }

    private Text playersLabel() {
        return Text.translatable("gui.slidingplatforms.sensor.players",
                Text.translatable(players ? "gui.slidingplatforms.snd_on" : "gui.slidingplatforms.snd_off"));
    }

    private Text mobsLabel() {
        return Text.translatable("gui.slidingplatforms.sensor.mobs",
                Text.translatable(mobs ? "gui.slidingplatforms.snd_on" : "gui.slidingplatforms.snd_off"));
    }

    private Text invertLabel() {
        return Text.translatable("gui.slidingplatforms.sensor.invert",
                Text.translatable(invert ? "gui.slidingplatforms.snd_on" : "gui.slidingplatforms.snd_off"));
    }

    private void sendNow() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(handler.getPos());
        buf.writeBoolean(on);
        buf.writeVarInt(radius);
        buf.writeBoolean(players);
        buf.writeBoolean(mobs);
        buf.writeBoolean(invert);
        buf.writeString(names, 96);
        buf.writeVarInt(autoClose);
        ClientPlayNetworking.send(SlidingPlatforms.PLATFORM_SENSOR, buf);
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
                Text.translatable("gui.slidingplatforms.sensor.names"), left + 8, top + 194, 0xA0A0A0);

        if (hasZone()) {
            MarqueeText.drawScaled(ctx, this.textRenderer,
                    Text.translatable("gui.slidingplatforms.sensor.zone_off_hint"),
                    left + 8, top + 155, 160, 0.78f, 0x707070);
        }
        if (autoClose > 0) {
            MarqueeText.drawScaled(ctx, this.textRenderer,
                    Text.translatable("gui.slidingplatforms.autoclose_hint"),
                    left + 8, top + 186, 160, 0.78f, 0x707070);
        }
        MarqueeText.drawScaled(ctx, this.textRenderer,
                Text.translatable("gui.slidingplatforms.sensor.names_hint"),
                left + 8, top + 222, 160, 0.78f, 0x707070);
        namesField.render(ctx, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (namesField.isFocused() && namesField.keyPressed(keyCode, scanCode, modifiers)) return true;

        if (namesField.isFocused() && client != null && client.options.inventoryKey.matchesKey(keyCode, scanCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (namesField.charTyped(chr, modifiers)) return true;
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double deltaX, double deltaY) {
        for (net.minecraft.client.gui.Element child : this.children()) {
            if (child instanceof DragSlider slider && slider.isDraggingSlider()) {
                return slider.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
}

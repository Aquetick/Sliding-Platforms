package mc.slidingplatforms.client;

import mc.slidingplatforms.PlatformControllerScreenHandler;
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
import net.minecraft.util.math.Direction;

import java.util.Locale;

public class PlatformControllerScreen extends HandledScreen<PlatformControllerScreenHandler> {

    private Direction.Axis localAxis;
    private boolean localPositive;
    private double offsetValue;
    private double speedValue;
    private String name = "";
    private int redstoneMode;

    private boolean lampGlow;

    private TextFieldWidget nameField;
    private ButtonWidget btnX, btnY, btnZ;
    private ButtonWidget btnRedstone;
    private ButtonWidget btnLamp;

    public PlatformControllerScreen(PlatformControllerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 242;

        mc.slidingplatforms.PlatformControllerBlockEntity.LocalDir ld =
                mc.slidingplatforms.PlatformControllerBlockEntity.worldToLocal(handler.slideDir, handler.anchorDir);
        this.localAxis = ld.axis();
        this.localPositive = ld.positive();
        this.offsetValue = handler.slideOffset;
        this.speedValue = handler.speed;
        this.name = handler.name;
        this.redstoneMode = handler.redstoneMode;
        this.lampGlow = handler.lampGlow;
    }

    @Override
    protected void init() {
        super.init();
        int left = this.x, top = this.y;

        new PlatformTabStrip(handler.pos, 0).attach(this::addDrawableChild, left, top + 18);

        nameField = new TextFieldWidget(this.textRenderer, left + 8, top + 36, 160, 16,
                Text.translatable("gui.slidingplatforms.name"));
        nameField.setMaxLength(24);
        nameField.setDrawsBackground(true);
        nameField.setText(name);
        nameField.setChangedListener(s -> {
            String prev = name;
            name = s;
            if (!s.isBlank() && !s.trim().equals(prev.trim())) sendApply();
        });
        addSelectableChild(nameField);

        btnX = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> pressAxis(Direction.Axis.X))
                .dimensions(left + 8, top + 58, 52, 18).build());
        btnY = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> pressAxis(Direction.Axis.Y))
                .dimensions(left + 62, top + 58, 52, 18).build());
        btnZ = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> pressAxis(Direction.Axis.Z))
                .dimensions(left + 116, top + 58, 52, 18).build());
        refreshAxisButtons();

        addDrawableChild(new DragSlider(left + 8, top + 82, 160, 18,
                offsetValue / offsetCap(),
                v -> Text.translatable("gui.slidingplatforms.distance", offsetLabel(v)),
                v -> {
                    offsetValue = Math.round(v * offsetCap());
                    sendApply();
                }));

        addDrawableChild(new DragSlider(left + 8, top + 106, 160, 18,
                (speedValue - 0.05) / (speedCap() - 0.05),
                v -> Text.translatable("gui.slidingplatforms.speed", speedText(v)),
                v -> {
                    speedValue = 0.05 + v * (speedCap() - 0.05);
                    sendApply();
                }));

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.slidingplatforms.manual"), b -> {
                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeBlockPos(handler.pos);
                    ClientPlayNetworking.send(SlidingPlatforms.GUI_MANUAL, buf);
                    this.close();
                }).dimensions(left + 8, top + 130, 160, 18).build());

        addDrawableChild(ButtonWidget.builder(
                handler.boundScreenName.isEmpty()
                        ? Text.translatable("gui.slidingplatforms.screen_bind")
                        : Text.translatable("gui.slidingplatforms.screen_bind_named", handler.boundScreenName),
                b -> {
                    PacketByteBuf buf = PacketByteBufs.create();
                    buf.writeBlockPos(handler.pos);
                    ClientPlayNetworking.send(SlidingPlatforms.GUI_SCREENS, buf);

                }).dimensions(left + 8, top + 152, 160, 18).build());

        btnRedstone = ButtonWidget.builder(rsLabel(), b -> {
            redstoneMode = (redstoneMode + 1) % 4;
            b.setMessage(rsLabel());
            sendApply();
        }).dimensions(left + 8, top + 174, 160, 18).build();
        addDrawableChild(btnRedstone);

        btnLamp = ButtonWidget.builder(lampLabel(), b -> {
            lampGlow = !lampGlow;
            b.setMessage(lampLabel());
            sendApply();
        }).dimensions(left + 8, top + 196, 160, 18).build();
        addDrawableChild(btnLamp);

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.slidingplatforms.done"), b -> this.close())
                .dimensions(left + 8, top + 218, 160, 18).build());
    }

    private Text lampLabel() {
        return Text.translatable("gui.slidingplatforms.lamp.toggle",
                Text.translatable(lampGlow ? "gui.slidingplatforms.snd_on" : "gui.slidingplatforms.snd_off"));
    }

    private Text rsLabel() {
        String key = switch (redstoneMode) {
            case 1 -> "gui.slidingplatforms.rs.level";
            case 2 -> "gui.slidingplatforms.rs.lock";
            case 3 -> "gui.slidingplatforms.rs.off";
            default -> "gui.slidingplatforms.rs.impulse";
        };
        return Text.translatable("gui.slidingplatforms.rs.mode", Text.translatable(key));
    }

    private void pressAxis(Direction.Axis axis) {
        if (localAxis == axis) {
            localPositive = !localPositive;
        } else {
            localAxis = axis;
            localPositive = true;
        }
        refreshAxisButtons();
        sendApply();
    }

    private void refreshAxisButtons() {
        styleAxisButton(btnX, Direction.Axis.X, "X");
        styleAxisButton(btnY, Direction.Axis.Y, "Y");
        styleAxisButton(btnZ, Direction.Axis.Z, "Z");
    }

    private void styleAxisButton(ButtonWidget btn, Direction.Axis axis, String letter) {
        Text base = Text.translatable("gui.slidingplatforms.axis", letter);
        if (localAxis == axis) {
            String sign = localPositive ? "→+" : "→−";
            btn.setMessage(Text.literal("[").append(base).append(" " + sign + "]"));
        } else {
            btn.setMessage(base);
        }
    }

    private static int offsetCap() { return ClientConfig.get().maxOffset; }
    private static double speedCap() {
        return Math.max(0.10, ClientConfig.get().maxSpeed);
    }

    private String offsetLabel(double v) {
        int blocks = (int) Math.round(v * offsetCap());
        return blocks == 0 ? net.minecraft.client.resource.language.I18n
                .translate("gui.slidingplatforms.auto") : String.valueOf(blocks);
    }

    private String speedText(double v) {
        return String.format(Locale.ROOT, "%.2f", 0.05 + v * (speedCap() - 0.05));
    }

    private void sendApply() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(handler.pos);
        buf.writeByte(localAxis.ordinal());
        buf.writeBoolean(localPositive);
        buf.writeInt((int) Math.round(offsetValue));
        buf.writeFloat((float) speedValue);
        buf.writeString(name, 24);
        buf.writeByte(redstoneMode);
        buf.writeBoolean(lampGlow);
        ClientPlayNetworking.send(SlidingPlatforms.GUI_APPLY, buf);
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
        nameField.render(ctx, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameField.isFocused() && nameField.keyPressed(keyCode, scanCode, modifiers)) return true;

        if (nameField.isFocused() && client != null && client.options.inventoryKey.matchesKey(keyCode, scanCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (nameField.charTyped(chr, modifiers)) return true;
        return super.charTyped(chr, modifiers);
    }
}

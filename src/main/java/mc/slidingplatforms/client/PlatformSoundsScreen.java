package mc.slidingplatforms.client;

import mc.slidingplatforms.PlatformControllerBlockEntity;
import mc.slidingplatforms.PlatformSoundsScreenHandler;
import mc.slidingplatforms.SlidingPlatforms;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class PlatformSoundsScreen extends HandledScreen<PlatformSoundsScreenHandler> {

    private static final int PAGE_ROWS = 3;

    private boolean enabled;
    private String sndStart, sndStop, sndArrive, sndHum;

    private TextFieldWidget fieldStart, fieldHum, fieldStop, fieldArrive;

    private TextFieldWidget activeField;

    private final List<ButtonWidget> listButtons = new ArrayList<>();
    private List<String> userSounds = List.of();
    private int badCount = 0;
    private int page = 0;

    public PlatformSoundsScreen(PlatformSoundsScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 246;
        this.enabled = handler.soundsEnabled();
        this.sndStart = handler.getSndStart();
        this.sndStop = handler.getSndStop();
        this.sndArrive = handler.getSndArrive();
        this.sndHum = handler.getSndHum();
    }

    @Override
    protected void init() {
        super.init();
        int left = this.x, top = this.y;

        new PlatformTabStrip(handler.getPos(), 1).attach(this::addDrawableChild, left, top + 18);

        addDrawableChild(ButtonWidget.builder(toggleLabel(), b -> {
            enabled = !enabled;
            b.setMessage(toggleLabel());
            sendNow();
        }).dimensions(left + 8, top + 36, 160, 18).build());

        fieldStart = makeField(left, top + 64, sndStart, "start");
        fieldHum = makeField(left, top + 88, sndHum, "hum");
        fieldStop = makeField(left, top + 112, sndStop, "stop");
        fieldArrive = makeField(left, top + 136, sndArrive, "arrive");
        activeField = fieldStart;

        boolean changed = UserSoundLibrary.sync();
        userSounds = UserSoundLibrary.list();
        badCount = UserSoundLibrary.badFiles().size();
        if (changed) {
            MinecraftClient.getInstance().reloadResources();
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("‹"), b -> page(-1))
                .dimensions(left + 128, top + 169, 18, 12).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("›"), b -> page(1))
                .dimensions(left + 148, top + 169, 18, 12).build());
        for (int i = 0; i < PAGE_ROWS; i++) {
            final int row = i;
            ButtonWidget btn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> pickFromList(row))
                    .dimensions(left + 8, top + 183 + i * 13, 160, 12).build());
            listButtons.add(btn);
        }
        refreshListButtons();

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.slidingplatforms.done"), b -> this.close())
                .dimensions(left + 8, top + 224, 160, 16).build());
    }

    private Text toggleLabel() {
        return Text.translatable("gui.slidingplatforms.sounds.toggle",
                Text.translatable(enabled ? "gui.slidingplatforms.snd_on" : "gui.slidingplatforms.snd_off"));
    }

    private TextFieldWidget makeField(int left, int y, String initial, String kind) {
        TextFieldWidget f = new TextFieldWidget(this.textRenderer, left + 8, y, 136, 16,
                Text.translatable("gui.slidingplatforms.snd." + kind));
        f.setMaxLength(64);
        f.setText(initial);
        f.setChangedListener(s -> {
            activeField = f;
            onFieldChanged(kind, s);
        });
        addSelectableChild(f);
        addDrawableChild(ButtonWidget.builder(Text.literal("▶"), b -> preview(kind))
                .dimensions(left + 146, y, 20, 16).build());
        return f;
    }

    private void onFieldChanged(String kind, String raw) {
        String clean = PlatformControllerBlockEntity.sanitizeSoundId(raw);
        switch (kind) {
            case "start" -> sndStart = clean;
            case "hum" -> sndHum = clean;
            case "stop" -> sndStop = clean;
            case "arrive" -> sndArrive = clean;
        }
        sendNow();
    }

    private void sendNow() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(handler.getPos());
        buf.writeBoolean(enabled);
        buf.writeString(sndStart, 48);
        buf.writeString(sndStop, 48);
        buf.writeString(sndArrive, 48);
        buf.writeString(sndHum, 48);
        ClientPlayNetworking.send(SlidingPlatforms.PLATFORM_SOUNDS, buf);
    }

    private void preview(String kind) {
        String id = switch (kind) {
            case "start" -> sndStart.isEmpty() ? PlatformSoundManager.DEFAULT_START : sndStart;
            case "hum" -> sndHum.isEmpty() ? PlatformSoundManager.HUM.toString() : sndHum;
            case "stop" -> sndStop.isEmpty() ? PlatformSoundManager.DEFAULT_STOP : sndStop;
            default -> sndArrive.isEmpty() ? PlatformSoundManager.DEFAULT_ARRIVE : sndArrive;
        };
        playMaster(id);
    }

    private void playMaster(String id) {
        Identifier ident = Identifier.tryParse(id);
        if (ident == null) return;
        MinecraftClient.getInstance().getSoundManager().play(
                PositionedSoundInstance.master(SoundEvent.of(ident), 1.0f, 1.0f));
    }

    private void page(int dir) {
        int pages = Math.max(1, (userSounds.size() + PAGE_ROWS - 1) / PAGE_ROWS);
        page = ((page + dir) % pages + pages) % pages;
        refreshListButtons();
    }

    private void refreshListButtons() {
        for (int row = 0; row < listButtons.size(); row++) {
            int i = page * PAGE_ROWS + row;
            ButtonWidget btn = listButtons.get(row);
            if (i < userSounds.size()) {
                String base = userSounds.get(i);
                String id = UserSoundLibrary.idFor(base);
                if (ServerSounds.isUploading(base)) {
                    btn.setMessage(Text.literal(id + "  ↥…"));
                    btn.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
                            Text.translatable("gui.slidingplatforms.snd.uploading_hint")));
                } else if (ServerSounds.isLocalOnly(base)) {
                    btn.setMessage(Text.literal(id + "  ⇪"));
                    btn.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(
                            Text.translatable("gui.slidingplatforms.snd.local_only_hint")));
                } else {
                    btn.setMessage(Text.literal(id));
                    btn.setTooltip(null);
                }
                btn.active = true;
            } else {
                btn.setMessage(Text.literal("—"));
                btn.setTooltip(null);
                btn.active = false;
            }
        }
    }

    private void pickFromList(int row) {
        int i = page * PAGE_ROWS + row;
        if (i >= userSounds.size()) return;
        String base = userSounds.get(i);
        String id = UserSoundLibrary.idFor(base);
        activeField.setText(id);
        playMaster(id);
        ServerSounds.enqueueUpload(base);
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
                Text.translatable("gui.slidingplatforms.snd.start"), left + 8, top + 56, 0xA0A0A0);
        ctx.drawTextWithShadow(this.textRenderer,
                Text.translatable("gui.slidingplatforms.snd.hum"), left + 8, top + 80, 0xA0A0A0);
        ctx.drawTextWithShadow(this.textRenderer,
                Text.translatable("gui.slidingplatforms.snd.stop"), left + 8, top + 104, 0xA0A0A0);
        ctx.drawTextWithShadow(this.textRenderer,
                Text.translatable("gui.slidingplatforms.snd.arrive"), left + 8, top + 128, 0xA0A0A0);

        String hint2Key = badCount > 0 ? "gui.slidingplatforms.snd.hint2_bad" : "gui.slidingplatforms.snd.hint2";
        MarqueeText.drawScaled(ctx, this.textRenderer,
                Text.translatable("gui.slidingplatforms.snd.hint1"),
                left + 8, top + 153, 160, 0.78f, 0x707070);
        MarqueeText.drawScaled(ctx, this.textRenderer,
                Text.translatable(hint2Key, badCount),
                left + 8, top + 161, 160, 0.78f,
                badCount > 0 ? 0xD06020 : 0x707070);

        int pages = Math.max(1, (userSounds.size() + PAGE_ROWS - 1) / PAGE_ROWS);
        ctx.drawTextWithShadow(this.textRenderer,
                Text.translatable("gui.slidingplatforms.snd.custom", userSounds.size(),
                        (page + 1) + "/" + pages), left + 8, top + 171, 0xA0A0A0);

        fieldStart.render(ctx, mouseX, mouseY, delta);
        fieldHum.render(ctx, mouseX, mouseY, delta);
        fieldStop.render(ctx, mouseX, mouseY, delta);
        fieldArrive.render(ctx, mouseX, mouseY, delta);

        for (java.util.Map.Entry<String, String> rn : ServerSounds.drainRenames()) {
            String oldId = UserSoundLibrary.idFor(rn.getKey());
            for (TextFieldWidget f : new TextFieldWidget[]{fieldStart, fieldHum, fieldStop, fieldArrive}) {
                if (f.getText().equals(oldId)) {
                    f.setText(UserSoundLibrary.idFor(rn.getValue()));
                }
            }
        }
        if (ServerSounds.consumeDirty()) refreshListButtons();
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        TextFieldWidget[] fields = {fieldStart, fieldHum, fieldStop, fieldArrive};
        for (TextFieldWidget f : fields) {
            if (f.isFocused() && f.keyPressed(keyCode, scanCode, modifiers)) return true;
        }

        boolean anyFocused = false;
        for (TextFieldWidget f : fields) if (f.isFocused()) { anyFocused = true; break; }
        if (anyFocused && client != null && client.options.inventoryKey.matchesKey(keyCode, scanCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        for (TextFieldWidget f : new TextFieldWidget[]{fieldStart, fieldHum, fieldStop, fieldArrive}) {
            if (f.charTyped(chr, modifiers)) return true;
        }
        return super.charTyped(chr, modifiers);
    }
}

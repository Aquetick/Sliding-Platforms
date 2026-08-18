package mc.slidingplatforms.client;

import com.google.gson.Gson;
import mc.slidingplatforms.ConfigScreenHandler;
import mc.slidingplatforms.SlidingPlatforms;
import mc.slidingplatforms.SlidingPlatformsConfig;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class ModConfigScreen extends HandledScreen<ConfigScreenHandler> {

    private static final Gson GSON = new Gson();
    private static final DecimalFormat NUM = new DecimalFormat("0.##");
    private static final int WARN_COLOR = 0xFF5555;

    private static final int[][] SIZE_PRESETS = {
            {6, 6, 2}, {8, 8, 2}, {12, 12, 3}, {16, 16, 4}, {24, 24, 4}, {32, 32, 4}};
    private static final int[] OFFSETS = {16, 32, 64, 128, 256};
    private static final double[] MAX_SPEEDS = {0.5, 1.0, 2.0, 4.0, 8.0};
    private static final int[] RIDE_PATHS = {32, 64, 128, 256, 512, 1024};
    private static final double[] DEF_SPEEDS = {0.05, 0.1, 0.15, 0.25, 0.5, 1.0};

    private final SlidingPlatformsConfig.Values cfg;
    private int tab;
    private final List<TextFieldWidget> fields = new ArrayList<>();
    private TextFieldWidget portField;

    public ModConfigScreen(ConfigScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 230;
        SlidingPlatformsConfig.Values parsed;
        try {
            parsed = GSON.fromJson(handler.json(), SlidingPlatformsConfig.Values.class);
        } catch (Exception e) {
            parsed = null;
        }
        cfg = parsed != null ? SlidingPlatformsConfig.sanitize(parsed) : new SlidingPlatformsConfig.Values();
    }

    @Override
    protected void init() {
        super.init();
        fields.clear();
        portField = null;
        int l = this.x, t = this.y;
        buildTabs(l, t);
        if (tab == 0) buildLimits(l, t); else buildFree(l, t);

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.slidingplatforms.cfg.reset"), b -> {
            SlidingPlatformsConfig.Values fresh = new SlidingPlatformsConfig.Values();
            copyInto(cfg, fresh);
            refreshLabels();
            sendNow();
        }).dimensions(l + 8, t + 204, 78, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.slidingplatforms.done"),
                b -> this.close()).dimensions(l + 90, t + 204, 78, 18).build());
    }

    private void buildTabs(int l, int t) {
        ButtonWidget a = ButtonWidget.builder(tabLabel(0), b -> { tab = 0; refreshLabels(); })
                .dimensions(l + 8, t + 26, 78, 14).build();
        a.active = tab != 0;
        ButtonWidget f = ButtonWidget.builder(tabLabel(1), b -> { tab = 1; refreshLabels(); })
                .dimensions(l + 90, t + 26, 78, 14).build();
        f.active = tab != 1;
        addDrawableChild(a);
        addDrawableChild(f);
    }

    private Text tabLabel(int i) {
        Text base = Text.translatable(i == 0
                ? "gui.slidingplatforms.cfg.tab_limits" : "gui.slidingplatforms.cfg.tab_free");
        return tab == i ? Text.literal("• ").append(base) : base;
    }

    private void buildLimits(int l, int t) {
        addDrawableChild(cycler(l + 8, t + 44, 160, () -> Text.translatable(
                "gui.slidingplatforms.cfg.size",
                cfg.maxWidth + "×" + cfg.maxHeight + "×" + cfg.maxDepth), this::advanceSize));

        addDrawableChild(cycler(l + 8, t + 62, 160, () -> Text.translatable(
                "gui.slidingplatforms.cfg.offset", cfg.maxOffset), () ->
                cfg.maxOffset = OFFSETS[(indexOf(OFFSETS, cfg.maxOffset) + 1) % OFFSETS.length]));

        addDrawableChild(cycler(l + 8, t + 80, 160, () -> Text.translatable(
                "gui.slidingplatforms.cfg.max_speed", NUM.format(cfg.maxSpeed)), () ->
                cfg.maxSpeed = MAX_SPEEDS[(indexOf(MAX_SPEEDS, cfg.maxSpeed) + 1) % MAX_SPEEDS.length]));

        addDrawableChild(cycler(l + 8, t + 98, 160, () -> Text.translatable(
                "gui.slidingplatforms.cfg.ride_path", cfg.rideMaxPath), () ->
                cfg.rideMaxPath = RIDE_PATHS[(indexOf(RIDE_PATHS, cfg.rideMaxPath) + 1) % RIDE_PATHS.length]));

        addDrawableChild(cycler(l + 8, t + 116, 160, () -> Text.translatable(
                "gui.slidingplatforms.cfg.def_speed", NUM.format(cfg.speed)), () ->
                cfg.speed = DEF_SPEEDS[(indexOf(DEF_SPEEDS, cfg.speed) + 1) % DEF_SPEEDS.length]));

        addDrawableChild(cycler(l + 8, t + 134, 78, () -> Text.translatable(
                "gui.slidingplatforms.cfg.def_sounds", onOff(cfg.defaultSounds)), () ->
                cfg.defaultSounds = !cfg.defaultSounds));
        addDrawableChild(cycler(l + 90, t + 134, 78, () -> Text.translatable(
                "gui.slidingplatforms.cfg.def_lamp", onOff(cfg.defaultLampGlow)), () ->
                cfg.defaultLampGlow = !cfg.defaultLampGlow));

        addDrawableChild(cycler(l + 8, t + 152, 78, () -> Text.translatable(
                "gui.slidingplatforms.cfg.pack", onOff(cfg.soundPack)), () ->
                cfg.soundPack = !cfg.soundPack));
        addDrawableChild(cycler(l + 90, t + 152, 78, () -> Text.translatable(
                "gui.slidingplatforms.cfg.fallback", onOff(cfg.soundPackFallback)), () ->
                cfg.soundPackFallback = !cfg.soundPackFallback));

        addDrawableChild(cycler(l + 8, t + 170, 78, () -> Text.translatable(
                "gui.slidingplatforms.cfg.debug", onOff(cfg.debugLogs)), () ->
                cfg.debugLogs = !cfg.debugLogs));

        portField = numField(l + 124, t + 172, 44, Integer.toString(cfg.soundPackPort), 5,
                s -> s.matches("\\d{0,5}"), s -> {
                    int p = Integer.parseInt(s);
                    if (p >= 0 && p <= 65535 && p != cfg.soundPackPort) {
                        cfg.soundPackPort = p;
                        return true;
                    }
                    return false;
                });
        addDrawableChild(portField);
    }

    private void buildFree(int l, int t) {
        int fx = l + 118, fw = 50;
        addDrawableChild(numField(fx, t + 44, fw, Integer.toString(cfg.maxWidth), 3,
                s -> s.matches("\\d{0,3}"), s -> setInt(s, v -> cfg.maxWidth = v)));
        addDrawableChild(numField(fx, t + 62, fw, Integer.toString(cfg.maxHeight), 3,
                s -> s.matches("\\d{0,3}"), s -> setInt(s, v -> cfg.maxHeight = v)));
        addDrawableChild(numField(fx, t + 80, fw, Integer.toString(cfg.maxDepth), 3,
                s -> s.matches("\\d{0,3}"), s -> setInt(s, v -> cfg.maxDepth = v)));
        addDrawableChild(numField(fx, t + 98, fw, Integer.toString(cfg.maxOffset), 5,
                s -> s.matches("\\d{0,5}"), s -> setInt(s, v -> cfg.maxOffset = v)));
        addDrawableChild(numField(fx, t + 116, fw, NUM.format(cfg.maxSpeed), 8,
                s -> s.matches("\\d{0,5}(\\.\\d{0,3})?"), s -> setDouble(s, v -> cfg.maxSpeed = v)));
        addDrawableChild(numField(fx, t + 134, fw, Integer.toString(cfg.rideMaxPath), 6,
                s -> s.matches("\\d{0,6}"), s -> setInt(s, v -> cfg.rideMaxPath = v)));
        addDrawableChild(numField(fx, t + 152, fw, NUM.format(cfg.speed), 8,
                s -> s.matches("\\d{0,5}(\\.\\d{0,3})?"), s -> setDouble(s, v -> cfg.speed = v)));
    }

    private boolean setInt(String s, java.util.function.IntConsumer set) {
        try {
            int v = Integer.parseInt(s.trim());
            if (v <= 0) return false;
            set.accept(v);
            return true;
        } catch (NumberFormatException e) { return false; }
    }

    private boolean setDouble(String s, java.util.function.DoubleConsumer set) {
        try {
            double v = Double.parseDouble(s.trim());
            if (!(v > 0) || Double.isInfinite(v)) return false;
            set.accept(v);
            return true;
        } catch (NumberFormatException e) { return false; }
    }

    private interface Label { Text get(); }

    private ButtonWidget cycler(int x, int y, int w, Label label, Runnable mutate) {
        return ButtonWidget.builder(label.get(), b -> {
            mutate.run();
            b.setMessage(label.get());
            sendNow();
        }).dimensions(x, y, w, 18).build();
    }

    private TextFieldWidget numField(int x, int y, int w, String initial, int maxLen,
                                     java.util.function.Predicate<String> textFilter,
                                     java.util.function.Predicate<String> apply) {
        TextFieldWidget f = new TextFieldWidget(this.textRenderer, x, y, w, 14, Text.empty());
        f.setMaxLength(maxLen);
        f.setTextPredicate(textFilter);
        f.setText(initial);
        f.setChangedListener(s -> { if (apply.test(s)) sendNow(); });
        fields.add(f);
        return f;
    }

    private void refreshLabels() {
        this.clearChildren();
        this.init();
    }

    private void advanceSize() {
        int best = 0, bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < SIZE_PRESETS.length; i++) {
            int d = Math.abs(SIZE_PRESETS[i][0] - cfg.maxWidth)
                    + Math.abs(SIZE_PRESETS[i][1] - cfg.maxHeight)
                    + Math.abs(SIZE_PRESETS[i][2] - cfg.maxDepth);
            if (d < bestDist) { bestDist = d; best = i; }
        }
        int[] p = SIZE_PRESETS[bestDist == 0 ? (best + 1) % SIZE_PRESETS.length : best];
        cfg.maxWidth = p[0]; cfg.maxHeight = p[1]; cfg.maxDepth = p[2];
    }

    private static void copyInto(SlidingPlatformsConfig.Values to, SlidingPlatformsConfig.Values from) {
        to.maxWidth = from.maxWidth; to.maxHeight = from.maxHeight; to.maxDepth = from.maxDepth;
        to.maxSpeed = from.maxSpeed; to.maxOffset = from.maxOffset; to.rideMaxPath = from.rideMaxPath;
        to.speed = from.speed; to.defaultSounds = from.defaultSounds; to.defaultLampGlow = from.defaultLampGlow;
        to.soundPack = from.soundPack; to.soundPackPort = from.soundPackPort;
        to.soundPackHost = from.soundPackHost; to.soundPackFallback = from.soundPackFallback;
        to.debugLogs = from.debugLogs;
    }

    private Text onOff(boolean v) {
        return Text.translatable(v ? "gui.slidingplatforms.snd_on" : "gui.slidingplatforms.snd_off");
    }

    private static int indexOf(int[] arr, int v) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == v) return i;
        return 0;
    }

    private static int indexOf(double[] arr, double v) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == v) return i;
        return 0;
    }

    private void sendNow() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeString(GSON.toJson(cfg));
        ClientPlayNetworking.send(SlidingPlatforms.CFG_SET, buf);
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
        int l = this.x, t = this.y;
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title,
                l + this.backgroundWidth / 2, t + 5, 0xFFFFFF);
        MarqueeText.drawScaled(ctx, this.textRenderer,
                Text.translatable("gui.slidingplatforms.cfg.warn"), l + 8, t + 14, 160, 0.72f, WARN_COLOR);
        if (tab == 0) {
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.translatable("gui.slidingplatforms.cfg.port"), l + 90, t + 175, 0xA0A0A0);
        } else {
            fieldLabel(ctx, "gui.slidingplatforms.cfg.free_w",      l, t + 48);
            fieldLabel(ctx, "gui.slidingplatforms.cfg.free_h",      l, t + 66);
            fieldLabel(ctx, "gui.slidingplatforms.cfg.free_d",      l, t + 84);
            fieldLabel(ctx, "gui.slidingplatforms.cfg.free_offset", l, t + 102);
            fieldLabel(ctx, "gui.slidingplatforms.cfg.free_speed",  l, t + 120);
            fieldLabel(ctx, "gui.slidingplatforms.cfg.free_ride",   l, t + 138);
            fieldLabel(ctx, "gui.slidingplatforms.cfg.free_def",    l, t + 156);
        }
        MarqueeText.drawScaled(ctx, this.textRenderer,
                Text.translatable(tab == 0 ? "gui.slidingplatforms.cfg.hint1"
                                           : "gui.slidingplatforms.cfg.free_hint1"),
                l + 8, t + 190, 160, 0.78f, 0x707070);
        MarqueeText.drawScaled(ctx, this.textRenderer,
                Text.translatable(tab == 0 ? "gui.slidingplatforms.cfg.hint2"
                                           : "gui.slidingplatforms.cfg.free_hint2"),
                l + 8, t + 197, 160, 0.78f, 0x707070);
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }

    private void fieldLabel(DrawContext ctx, String key, int l, int y) {
        MarqueeText.drawScaled(ctx, this.textRenderer,
                Text.translatable(key), l + 8, y, 106, 0.9f, 0xA0A0A0);
    }

    private TextFieldWidget focusedField() {
        for (TextFieldWidget f : fields) if (f.isFocused()) return f;
        return null;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        TextFieldWidget focused = focusedField();
        if (focused != null) {
            if (keyCode == 256) { focused.setFocused(false); return true; }
            if (focused.keyPressed(keyCode, scanCode, modifiers)) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        TextFieldWidget focused = focusedField();
        if (focused != null && focused.charTyped(chr, modifiers)) return true;
        return super.charTyped(chr, modifiers);
    }
}

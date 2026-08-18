package mc.slidingplatforms.client;

import mc.slidingplatforms.ScreenSettingsScreenHandler;
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
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

public class ScreenSettingsScreen extends HandledScreen<ScreenSettingsScreenHandler> {

    private static final int ROWS_PER_PAGE_SETTINGS = 3;
    private static final int ROWS_PER_PAGE_CHAIN = 3;

    private boolean chainTab = false;
    private TextFieldWidget nameField;
    private TextFieldWidget chainNameField;

    private int pageSettings = 0;
    private int pageChain = 0;
    private final List<TextFieldWidget> numFields = new ArrayList<>();
    private final List<ButtonWidget> rowButtons = new ArrayList<>();
    private ButtonWidget prevBtn, nextBtn;
    private ButtonWidget createChainBtn;
    private int callFloor;

    public ScreenSettingsScreen(ScreenSettingsScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 214;
        this.callFloor = handler.callFloor;
    }

    @Override
    protected void init() {
        super.init();
        chainTab = handler.chainTab;

        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.slidingplatforms.tab_settings"), b -> switchTab(false))
                .dimensions(this.x + 8, this.y + 17, 80, 16).build());
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.slidingplatforms.tab_chain"), b -> switchTab(true))
                .dimensions(this.x + 88, this.y + 17, 80, 16).build());
        rebuildTab();
    }

    private void switchTab(boolean chain) {
        if (chain == chainTab) return;
        commitFocusedField();
        chainTab = chain;
        rebuildTab();
    }

    private void rebuildTab() {

        for (TextFieldWidget f : numFields) remove((net.minecraft.client.gui.Element) f);
        for (ButtonWidget b : rowButtons) remove((net.minecraft.client.gui.Element) b);
        numFields.clear();
        rowButtons.clear();
        if (nameField != null) remove((net.minecraft.client.gui.Element) nameField);
        if (chainNameField != null) remove((net.minecraft.client.gui.Element) chainNameField);
        nameField = null;
        chainNameField = null;
        if (prevBtn != null) {
            remove((net.minecraft.client.gui.Element) prevBtn);
            remove((net.minecraft.client.gui.Element) nextBtn);
            prevBtn = null;
            nextBtn = null;
        }
        if (createChainBtn != null) {
            remove((net.minecraft.client.gui.Element) createChainBtn);
            createChainBtn = null;
        }

        if (chainTab) buildChainTab(); else buildSettingsTab();

        rowButtons.add(addDrawableChild(ButtonWidget.builder(callLabel(), b -> {
            cycleCall();
            b.setMessage(callLabel());
        }).dimensions(this.x + 8, this.y + 140, 160, 18).build()));

        rowButtons.add(addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.slidingplatforms.done"), b -> this.close())
                .dimensions(this.x + 8, this.y + 164, 160, 18).build()));
    }

    private Text callLabel() {
        Text v = callFloor > 0
                ? Text.translatable("gui.slidingplatforms.screen_call.floor", callFloor)
                : Text.translatable("gui.slidingplatforms.snd_off");
        return Text.translatable("gui.slidingplatforms.screen_call", v);
    }

    private void cycleCall() {
        List<Integer> nums = new ArrayList<>();
        for (ScreenSettingsScreenHandler.FloorRow r : handler.rows) {
            if (!nums.contains(r.number())) nums.add(r.number());
        }
        java.util.Collections.sort(nums);
        int idx = nums.indexOf(callFloor);
        callFloor = idx < 0 ? (nums.isEmpty() ? 0 : nums.get(0))
                            : (idx + 1 < nums.size() ? nums.get(idx + 1) : 0);

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(handler.screenPos);
        buf.writeVarInt(callFloor);
        ClientPlayNetworking.send(SlidingPlatforms.SCREEN_CALL, buf);
    }

    private void buildSettingsTab() {
        int left = this.x, top = this.y;
        nameField = new TextFieldWidget(this.textRenderer, left + 8, top + 40, 160, 16,
                Text.translatable("gui.slidingplatforms.screen_name"));
        nameField.setMaxLength(24);
        nameField.setText(handler.screenName);
        nameField.setPlaceholder(
                Text.translatable("gui.slidingplatforms.name_placeholder").formatted(Formatting.DARK_GRAY));
        addDrawableChild(nameField);

        List<ScreenSettingsScreenHandler.FloorRow> rows = handler.rows;
        int pages = Math.max(1, (int) Math.ceil(rows.size() / (double) ROWS_PER_PAGE_SETTINGS));
        pageSettings = Math.max(0, Math.min(pageSettings, pages - 1));

        int from = pageSettings * ROWS_PER_PAGE_SETTINGS;
        int to = Math.min(rows.size(), from + ROWS_PER_PAGE_SETTINGS);
        for (int i = from; i < to; i++) {
            ScreenSettingsScreenHandler.FloorRow row = rows.get(i);
            int y = top + 62 + (i - from) * 24;
            TextFieldWidget num = new TextFieldWidget(this.textRenderer, left + 130, y, 20, 20,
                    Text.translatable("gui.slidingplatforms.floor_num_ph"));
            num.setMaxLength(2);
            num.setText(String.format("%02d", row.number()));
            num.setTextPredicate(s -> s.matches("\\d{0,2}"));
            numFields.add(addDrawableChild(num));

            rowButtons.add(addDrawableChild(ButtonWidget.builder(
                    Text.literal("✕").formatted(Formatting.RED), b -> sendRemove(row))
                    .dimensions(left + 152, y, 16, 20).build()));
        }
        buildPager(pages, false);
    }

    private void buildChainTab() {
        int left = this.x, top = this.y;
        boolean chained = !handler.chainName.isEmpty();

        chainNameField = new TextFieldWidget(this.textRenderer, left + 8, top + 40,
                chained ? 160 : 100, 16,
                Text.translatable("gui.slidingplatforms.chain_name"));
        chainNameField.setMaxLength(24);
        chainNameField.setText(handler.chainName);
        chainNameField.setPlaceholder(
                Text.translatable("gui.slidingplatforms.chain_name_ph").formatted(Formatting.DARK_GRAY));
        addDrawableChild(chainNameField);

        if (!chained) {
            createChainBtn = ButtonWidget.builder(
                    Text.translatable("gui.slidingplatforms.chain_create"), b -> sendChainName())
                    .dimensions(left + 112, top + 40, 56, 16).build();
            createChainBtn.active = !chainNameField.getText().trim().isEmpty();
            chainNameField.setChangedListener(
                    s -> createChainBtn.active = !s.trim().isEmpty());
            addDrawableChild(createChainBtn);
        }

        int total = handler.links.size() + (chained ? 1 : 0);
        int pages = Math.max(1, (int) Math.ceil(total / (double) ROWS_PER_PAGE_CHAIN));
        pageChain = Math.max(0, Math.min(pageChain, pages - 1));

        int from = pageChain * ROWS_PER_PAGE_CHAIN;
        int to = Math.min(total, from + ROWS_PER_PAGE_CHAIN);
        for (int i = from; i < to; i++) {
            int y = top + 78 + (i - from) * 20;
            if (chained && i == 0) {
                rowButtons.add(addDrawableChild(ButtonWidget.builder(
                        Text.translatable("gui.slidingplatforms.chain_unlink").formatted(Formatting.RED),
                        b -> sendLink(""))
                        .dimensions(left + 8, y, 160, 18).build()));
                continue;
            }
            ScreenSettingsScreenHandler.LinkRow link = handler.links.get(i - (chained ? 1 : 0));
            Text label;
            if (link.key().startsWith("chain:")) {
                label = Text.literal("⟟ ").formatted(Formatting.GOLD)
                        .append(Text.literal(link.name()).formatted(Formatting.WHITE));
            } else {
                label = Text.literal("▦ ").formatted(Formatting.AQUA)
                        .append(Text.translatable("gui.slidingplatforms.chain_new", link.name())
                                .formatted(Formatting.WHITE));
            }
            rowButtons.add(addDrawableChild(ButtonWidget.builder(label, b -> sendLink(link.key()))
                    .dimensions(left + 8, y, 160, 18).build()));
        }
        buildPager(pages, true);
    }

    private void buildPager(int pages, boolean chain) {
        if (pages <= 1) return;
        int left = this.x, y = this.y + 164;
        int p = pages;
        prevBtn = addDrawableChild(ButtonWidget.builder(Text.literal("‹"), b -> {
            if (chain) pageChain = Math.max(0, Math.min(pageChain - 1, p - 1));
            else pageSettings = Math.max(0, Math.min(pageSettings - 1, p - 1));
            rebuildTab();
        }).dimensions(left + 8, y, 76, 18).build());
        nextBtn = addDrawableChild(ButtonWidget.builder(Text.literal("›"), b -> {
            if (chain) pageChain = Math.max(0, Math.min(pageChain + 1, p - 1));
            else pageSettings = Math.max(0, Math.min(pageSettings + 1, p - 1));
            rebuildTab();
        }).dimensions(left + 92, y, 76, 18).build());
    }

    private void sendName() {
        String nm = nameField.getText().trim();
        if (nm.isEmpty()) {
            nameField.setText(handler.screenName);
            return;
        }
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(handler.screenPos);
        buf.writeString(nm);
        ClientPlayNetworking.send(SlidingPlatforms.SCREEN_NAME, buf);
    }

    private void sendChainName() {
        String nm = chainNameField.getText().trim();
        if (nm.isEmpty() && handler.chainName.isEmpty()) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(handler.screenPos);
        buf.writeString(nm);
        ClientPlayNetworking.send(SlidingPlatforms.CHAIN_RENAME, buf);

    }

    private void sendLink(String target) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(handler.screenPos);
        buf.writeString(target);
        ClientPlayNetworking.send(SlidingPlatforms.CHAIN_LINK, buf);

    }

    private void sendNum(ScreenSettingsScreenHandler.FloorRow row, int num) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(handler.screenPos);
        buf.writeBlockPos(row.pos());
        buf.writeVarInt(num);
        ClientPlayNetworking.send(SlidingPlatforms.SCREEN_FLOOR_NUM, buf);
    }

    private void sendRemove(ScreenSettingsScreenHandler.FloorRow row) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(handler.screenPos);
        buf.writeBlockPos(row.pos());
        ClientPlayNetworking.send(SlidingPlatforms.SCREEN_FLOOR_DEL, buf);

    }

    private List<TextFieldWidget> activeFields() {
        List<TextFieldWidget> out = new ArrayList<>();
        if (nameField != null) out.add(nameField);
        if (chainNameField != null) out.add(chainNameField);
        out.addAll(numFields);
        return out;
    }

    private void commitFocusedField() {
        if (nameField != null && nameField.isFocused()) {
            sendName();
            nameField.setFocused(false);
        }

        if (chainNameField != null && chainNameField.isFocused()) {
            if (!handler.chainName.isEmpty()) sendChainName();
            else if (createChainBtn != null && createChainBtn.active) sendChainName();
            chainNameField.setFocused(false);
        }
        for (TextFieldWidget f : numFields) {
            if (f.isFocused()) {
                applyNum(f);
                f.setFocused(false);
            }
        }
        setFocused(null);
    }

    private void applyNum(TextFieldWidget field) {
        int idx = numFields.indexOf(field);
        if (idx < 0) return;
        int rowsIdx = pageSettings * ROWS_PER_PAGE_SETTINGS + idx;
        if (rowsIdx >= handler.rows.size()) return;
        ScreenSettingsScreenHandler.FloorRow row = handler.rows.get(rowsIdx);
        int n;
        try {
            n = Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException e) {
            field.setText(String.format("%02d", row.number()));
            return;
        }
        n = Math.max(1, Math.min(99, n));
        field.setText(String.format("%02d", n));
        if (n != row.number()) sendNum(row, n);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        List<TextFieldWidget> fields = activeFields();
        boolean fieldFocused = fields.stream().anyMatch(TextFieldWidget::isFocused);
        if ((keyCode == 257 || keyCode == 335) && fieldFocused) {
            commitFocusedField();
            return true;
        }
        if (keyCode == 256 && fieldFocused) {
            fields.forEach(f -> f.setFocused(false));
            setFocused(null);
            return true;
        }

        if (fieldFocused && client != null && client.options.inventoryKey.matchesKey(keyCode, scanCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        boolean hit = activeFields().stream().anyMatch(f -> f.isMouseOver(mx, my));
        if (!hit) commitFocusedField();
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public void close() {

        if (nameField != null && !nameField.getText().equals(handler.screenName)) sendName();

        if (chainNameField != null && !handler.chainName.isEmpty()
                && !chainNameField.getText().trim().equals(handler.chainName)) {
            sendChainName();
        }
        super.close();
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

        int left = this.x, top = this.y;
        if (chainTab) {
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.translatable("gui.slidingplatforms.chain_links_hint"),
                    left + 8, top + 64, 0xAAAAAA);
            if (handler.links.isEmpty() && handler.chainName.isEmpty()) {
                ctx.drawCenteredTextWithShadow(this.textRenderer,
                        Text.translatable("gui.slidingplatforms.no_chains"),
                        left + this.backgroundWidth / 2, top + 100, 0x808080);
            }
        } else {

            List<ScreenSettingsScreenHandler.FloorRow> rows = handler.rows;
            int from = pageSettings * ROWS_PER_PAGE_SETTINGS;
            int to = Math.min(rows.size(), from + ROWS_PER_PAGE_SETTINGS);
            for (int i = from; i < to; i++) {
                int y = top + 62 + (i - from) * 24;
                ctx.drawTextWithShadow(this.textRenderer,
                        this.textRenderer.trimToWidth(rows.get(i).name(), 116),
                        left + 8, y + 6, 0xFFFFFF);
            }
            if (rows.isEmpty()) {
                ctx.drawCenteredTextWithShadow(this.textRenderer,
                        Text.translatable("gui.slidingplatforms.no_floors"),
                        left + this.backgroundWidth / 2, top + 90, 0x808080);
            }
        }
        this.drawMouseoverTooltip(ctx, mouseX, mouseY);
    }
}

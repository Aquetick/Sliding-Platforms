package mc.slidingplatforms.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.function.Consumer;
import java.util.function.Function;

public class DragSlider extends ClickableWidget {

    protected double value;
    private boolean dragging;

    private final Function<Double, Text> labeler;
    private final Consumer<Double> onChange;

    public DragSlider(int x, int y, int width, int height,
                      double initial, Function<Double, Text> labeler, Consumer<Double> onChange) {
        super(x, y, width, height, Text.empty());
        this.labeler = labeler;
        this.onChange = onChange;
        this.value = MathHelper.clamp(initial, 0.0, 1.0);
        refreshLabel();
    }

    public double getValue() { return value; }

    private void refreshLabel() {
        setMessage(labeler.apply(value));
    }

    private void setValueFromMouse(double mouseX) {
        double v = MathHelper.clamp((mouseX - (getX() + 4)) / (double) (getWidth() - 8), 0.0, 1.0);
        if (v != value) {
            value = v;
            refreshLabel();
            onChange.accept(value);
        }
    }

    @Override
    protected boolean isValidClickButton(int button) {
        return button == 0 || button == 1;
    }

    public boolean isDraggingSlider() {
        return dragging;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        dragging = true;
        setValueFromMouse(mouseX);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double deltaX, double deltaY) {
        if (dragging && isValidClickButton(button)) {
            setValueFromMouse(mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        if (dragging) {
            dragging = false;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 262) { setValueClamped(value + 0.05); return true; }
        if (keyCode == 263) { setValueClamped(value - 0.05); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void setValueClamped(double v) {
        value = MathHelper.clamp(v, 0.0, 1.0);
        refreshLabel();
        onChange.accept(value);
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
        super.playDownSound(soundManager);
    }

    @Override
    protected void appendClickableNarrations(
            net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }

    @Override
    protected void renderButton(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();

        int border = 0xFF6A6A75;
        ctx.fill(x, y, x + w, y + h, 0xFF141419);
        ctx.fill(x, y, x + w, y + 1, border);
        ctx.fill(x, y + h - 1, x + w, y + h, border);
        ctx.fill(x, y, x + 1, y + h, border);
        ctx.fill(x + w - 1, y, x + w, y + h, border);

        int knobX = x + 4 + (int) (value * (w - 8)) - 3;
        int fillTo = MathHelper.clamp(knobX + 3, x + 2, x + w - 2);
        if (fillTo > x + 2) ctx.fill(x + 2, y + 2, fillTo, y + h - 2, 0xFF4A3A12);

        int knobColor = (dragging || isHovered()) ? 0xFFFFD54F : 0xFFE0B93A;
        ctx.fill(knobX, y + 2, knobX + 6, y + h - 2, knobColor);

        int color = active ? 0xFFFFFF : 0xA0A0A0;
        MarqueeText.drawCentered(ctx, MinecraftClient.getInstance().textRenderer,
                getMessage(), x + 4, y + (h - 8) / 2, w - 8, color);
    }
}

package mc.slidingplatforms.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

public final class MarqueeText {

    private MarqueeText() {}

    public static void drawCentered(DrawContext ctx, TextRenderer tr, Text text,
                                    int x, int y, int width, int color) {
        if (tr.getWidth(text) <= width) {
            ctx.drawCenteredTextWithShadow(tr, text, x + width / 2, y, color);
        } else {
            drawScaled(ctx, tr, text, x, y, width, 1.0f, color);
        }
    }

    public static void drawScaled(DrawContext ctx, TextRenderer tr, Text text,
                                  int x, int y, int width, float scale, int color) {
        int textW = tr.getWidth(text);
        int fitW = Math.round(width / scale);
        int off = 0;
        boolean scroll = textW > fitW;
        if (scroll) {
            int over = textW - fitW;
            double t = (double) Util.getMeasuringTimeMs() / 1000.0;

            double period = Math.max(over * 0.5, 3.0);
            double f = Math.sin((Math.PI / 2.0) * Math.cos(2.0 * Math.PI * t / period)) / 2.0 + 0.5;
            off = (int) Math.round(MathHelper.lerp(f, 0.0, (double) over));

            ctx.enableScissor(x, y - 1, x + width, y + (int) Math.ceil(tr.fontHeight * scale) + 1);
        }
        boolean scaled = scale != 1.0f;
        if (scaled) {
            ctx.getMatrices().push();
            ctx.getMatrices().scale(scale, scale, 1.0f);
        }
        ctx.drawTextWithShadow(tr, text,
                Math.round(x / scale) - off, Math.round(y / scale), color);
        if (scaled) ctx.getMatrices().pop();
        if (scroll) ctx.disableScissor();
    }
}

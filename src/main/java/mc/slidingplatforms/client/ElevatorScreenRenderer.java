package mc.slidingplatforms.client;

import mc.slidingplatforms.ElevatorScreenBlockEntity;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

public class ElevatorScreenRenderer implements BlockEntityRenderer<ElevatorScreenBlockEntity> {

    private final BlockEntityRendererFactory.Context ctx;

    public ElevatorScreenRenderer(BlockEntityRendererFactory.Context ctx) {
        this.ctx = ctx;
    }

    @Override
    public void render(ElevatorScreenBlockEntity be, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vcp, int light, int overlay) {

        if (be.getPos().getSquaredDistance(ctx.getRenderDispatcher().camera.getPos()) > 45.0 * 45.0) return;

        String top; int topColor;
        String bottom; int bottomColor;

        if (be.isDispEmpty()) {

            top = be.getScreenName();
            topColor = 0xFF55FFFF;
            bottom = "--";
            bottomColor = 0xFF888888;
        } else {
            top = be.getDispName();
            topColor = 0xFF55FFFF;
            if (be.isDispMoving()) {
                bottom = "» " + String.format("%02d", be.getDispNo());
                bottomColor = 0xFFFFFF55;
            } else if (be.getDispNo() >= 0) {
                bottom = String.format("%02d", be.getDispNo());
                bottomColor = 0xFF55FF55;
            } else {
                bottom = "--";
                bottomColor = 0xFF888888;
            }
        }
        if (top.isEmpty()) top = "--";

        TextRenderer tr = ctx.getTextRenderer();
        int maxW = Math.max(1, Math.max(tr.getWidth(top), tr.getWidth(bottom)));
        float s = Math.min(1f / 60f, 0.92f / maxW);

        Direction facing = be.getCachedState().get(Properties.HORIZONTAL_FACING);
        matrices.push();
        matrices.translate(0.5, 0.5, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));
        matrices.translate(0, 0, 0.5005);
        matrices.scale(s, -s, s);

        Text topText = Text.literal(top);
        tr.draw(topText, -tr.getWidth(topText) / 2f, -10f, topColor, false,
                matrices.peek().getPositionMatrix(), vcp, TextRenderer.TextLayerType.NORMAL,
                0, 0xF000F0);

        Text bottomText = Text.literal(bottom);
        tr.draw(bottomText, -tr.getWidth(bottomText) / 2f, 1f, bottomColor, false,
                matrices.peek().getPositionMatrix(), vcp, TextRenderer.TextLayerType.NORMAL,
                0, 0xF000F0);

        matrices.pop();
    }
}

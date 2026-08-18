package mc.slidingplatforms.client;

import mc.slidingplatforms.SlidingPlatforms;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public class BoxSelection {

    private static KeyBinding toggleKey;
    private static boolean boxMode;
    private static BlockPos anchor;

    private BoxSelection() {}

    public static void init() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.slidingplatforms.selection_mode",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_N,
                "category.slidingplatforms"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                if (!SelectionHighlight.isActive() || client.player == null) continue;
                boxMode = !boxMode;
                anchor = null;
                client.player.sendMessage(Text.translatable(boxMode
                        ? "message.slidingplatforms.mode_box"
                        : "message.slidingplatforms.mode_single"), true);
            }
        });

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClient) return ActionResult.PASS;
            if (!SelectionHighlight.isActive() || !boxMode) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND || player.isSneaking()) return ActionResult.PASS;
            if (!player.getMainHandStack().isEmpty()) return ActionResult.PASS;

            BlockPos pos = hit.getBlockPos().toImmutable();
            if (anchor == null) {
                anchor = pos;
                player.sendMessage(Text.translatable("message.slidingplatforms.box_first"), true);
                player.swingHand(hand);
            } else {

                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeBlockPos(anchor);
                buf.writeBlockPos(pos);
                ClientPlayNetworking.send(SlidingPlatforms.BOX_SELECT, buf);
                anchor = null;
                player.swingHand(hand);
            }

            return ActionResult.FAIL;
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(BoxSelection::render);

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN
                .register((handler, sender, client) -> reset());
    }

    public static void reset() {
        boxMode = false;
        anchor = null;
    }

    private static final float R = 0.25f, G = 1.0f, B = 0.45f, A = 0.95f;

    private static void render(WorldRenderContext ctx) {
        if (!boxMode || !SelectionHighlight.isActive() || anchor == null) return;
        VertexConsumerProvider consumers = ctx.consumers();
        if (consumers == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.crosshairTarget == null || client.crosshairTarget.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos to = ((BlockHitResult) client.crosshairTarget).getBlockPos();

        double x0 = Math.min(anchor.getX(), to.getX()) - 0.002;
        double y0 = Math.min(anchor.getY(), to.getY()) - 0.002;
        double z0 = Math.min(anchor.getZ(), to.getZ()) - 0.002;
        double x1 = Math.max(anchor.getX(), to.getX()) + 1.002;
        double y1 = Math.max(anchor.getY(), to.getY()) + 1.002;
        double z1 = Math.max(anchor.getZ(), to.getZ()) + 1.002;

        Vec3d cam = ctx.camera().getPos();
        MatrixStack matrices = ctx.matrixStack();
        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);

        RenderLayer lines = RenderLayer.getLines();
        VertexConsumer vc = consumers.getBuffer(lines);
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f mat = entry.getPositionMatrix();

        SelectionHighlight.drawBoxOutline(vc, entry, mat, x0, y0, z0, x1, y1, z1, R, G, B, A);

        SelectionHighlight.drawBoxOutline(vc, entry, mat,
                anchor.getX() - 0.003, anchor.getY() - 0.003, anchor.getZ() - 0.003,
                anchor.getX() + 1.003, anchor.getY() + 1.003, anchor.getZ() + 1.003,
                1.0f, 0.6f, 0.1f, 1.0f);

        matrices.pop();

        if (consumers instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(lines);
        }
    }
}

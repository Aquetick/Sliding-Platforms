package mc.slidingplatforms.client;

import mc.slidingplatforms.SlidingPlatforms;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
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

public class ZoneSelection {

    private static boolean active;
    private static BlockPos ctrlPos;
    private static BlockPos zoneMin;
    private static BlockPos zoneMax;
    private static BlockPos anchor;

    private ZoneSelection() {}

    public static void apply(boolean isActive, BlockPos ctrl, BlockPos min, BlockPos max) {
        active = isActive;
        ctrlPos = isActive ? ctrl : null;
        zoneMin = min;
        zoneMax = max;
        anchor = null;
    }

    public static boolean isActive() { return active; }

    public static void reset() {
        active = false;
        ctrlPos = zoneMin = zoneMax = anchor = null;
    }

    public static void init() {

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClient) return ActionResult.PASS;
            if (!active || ctrlPos == null) return ActionResult.PASS;
            if (hand != Hand.MAIN_HAND || player.isSneaking()) return ActionResult.PASS;
            if (!player.getMainHandStack().isEmpty()) return ActionResult.PASS;

            BlockPos pos = hit.getBlockPos().toImmutable();
            if (pos.equals(ctrlPos)) return ActionResult.PASS;
            if (anchor == null) {
                anchor = pos;
                player.sendMessage(Text.translatable("message.slidingplatforms.zone_first"), true);
                player.swingHand(hand);
            } else {

                if (Math.abs(anchor.getX() - pos.getX()) + 1L
                        * (Math.abs(anchor.getY() - pos.getY()) + 1L)
                        * (Math.abs(anchor.getZ() - pos.getZ()) + 1L) > 512) {
                    player.sendMessage(Text.translatable("message.slidingplatforms.box_too_big"), true);
                    anchor = null;
                    player.swingHand(hand);
                    return ActionResult.FAIL;
                }
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeBlockPos(ctrlPos);
                buf.writeBlockPos(anchor);
                buf.writeBlockPos(pos);
                ClientPlayNetworking.send(SlidingPlatforms.ZONE_SELECT, buf);
                anchor = null;
                player.swingHand(hand);
            }

            return ActionResult.FAIL;
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(ZoneSelection::render);

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN
                .register((handler, sender, client) -> reset());
    }

    private static final float CR = 0.25f, CG = 0.85f, CB = 1.0f, CA = 0.95f;

    private static void render(WorldRenderContext ctx) {
        if (!active) return;
        VertexConsumerProvider consumers = ctx.consumers();
        if (consumers == null) return;

        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d cam = ctx.camera().getPos();
        MatrixStack matrices = ctx.matrixStack();
        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);

        RenderLayer lines = RenderLayer.getLines();
        VertexConsumer vc = consumers.getBuffer(lines);
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f mat = entry.getPositionMatrix();

        if (zoneMin != null) {
            SelectionHighlight.drawBoxOutline(vc, entry, mat,
                    zoneMin.getX() - 0.002, zoneMin.getY() - 0.002, zoneMin.getZ() - 0.002,
                    zoneMax.getX() + 1.002, zoneMax.getY() + 1.002, zoneMax.getZ() + 1.002,
                    CR, CG, CB, CA);
        }

        if (anchor != null
                && client.crosshairTarget != null
                && client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            BlockPos to = ((BlockHitResult) client.crosshairTarget).getBlockPos();
            SelectionHighlight.drawBoxOutline(vc, entry, mat,
                    Math.min(anchor.getX(), to.getX()) - 0.002,
                    Math.min(anchor.getY(), to.getY()) - 0.002,
                    Math.min(anchor.getZ(), to.getZ()) - 0.002,
                    Math.max(anchor.getX(), to.getX()) + 1.002,
                    Math.max(anchor.getY(), to.getY()) + 1.002,
                    Math.max(anchor.getZ(), to.getZ()) + 1.002,
                    CR, CG, CB, CA);

            SelectionHighlight.drawBoxOutline(vc, entry, mat,
                    anchor.getX() - 0.003, anchor.getY() - 0.003, anchor.getZ() - 0.003,
                    anchor.getX() + 1.003, anchor.getY() + 1.003, anchor.getZ() + 1.003,
                    1.0f, 0.6f, 0.1f, 1.0f);
        }

        matrices.pop();

        if (consumers instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(lines);
        }
    }
}

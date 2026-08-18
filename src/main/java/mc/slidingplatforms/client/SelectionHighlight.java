package mc.slidingplatforms.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SelectionHighlight {

    private static boolean active;
    private static final Set<BlockPos> blocks = new HashSet<>();

    private SelectionHighlight() {}

    public static void apply(boolean isActive, List<BlockPos> positions) {
        active = isActive;
        blocks.clear();
        if (isActive) blocks.addAll(positions);
    }

    public static boolean isActive() { return active; }
    public static boolean isEmpty() { return blocks.isEmpty(); }

    public static void clear() {
        active = false;
        blocks.clear();
    }

    private static final float R = 1.0f, G = 0.82f, B = 0.15f, A = 0.9f;

    public static void render(WorldRenderContext ctx) {
        if (!active || blocks.isEmpty()) return;
        VertexConsumerProvider consumers = ctx.consumers();
        if (consumers == null) return;

        Camera camera = ctx.camera();
        Vec3d cam = camera.getPos();

        MatrixStack matrices = ctx.matrixStack();
        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);

        RenderSystem.lineWidth(2.5f);

        VertexConsumer vc = consumers.getBuffer(RenderLayer.getLines());
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f mat = entry.getPositionMatrix();

        for (BlockPos pos : blocks) {
            drawMergedEdges(vc, entry, mat, pos);
        }
        matrices.pop();

        RenderSystem.lineWidth(1.0f);

        if (consumers instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(RenderLayer.getLines());
        }
    }

    private static void drawMergedEdges(VertexConsumer vc, MatrixStack.Entry entry, Matrix4f mat,
                                        BlockPos pos) {
        for (Direction face : Direction.values()) {

            if (blocks.contains(pos.offset(face))) continue;

            Direction[] tangents = tangents(face);

            for (Direction t : tangents) {

                BlockPos neighbor = pos.offset(t);
                if (blocks.contains(neighbor) && !blocks.contains(neighbor.offset(face))) continue;

                emitEdge(vc, entry, mat, pos, face, t);
            }
        }
    }

    private static Direction[] tangents(Direction face) {
        return switch (face.getAxis()) {
            case X -> new Direction[]{Direction.UP, Direction.DOWN, Direction.SOUTH, Direction.NORTH};
            case Y -> new Direction[]{Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST};
            case Z -> new Direction[]{Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST};
        };
    }

    private static void emitEdge(VertexConsumer vc, MatrixStack.Entry entry, Matrix4f mat,
                                 BlockPos pos, Direction face, Direction t) {

        double fx = pos.getX(), fy = pos.getY(), fz = pos.getZ();

        double min = 0.0, max = 1.0;
        double u0 = min, u1 = max;

        double ax = fx, ay = fy, az = fz;
        double bx = fx, by = fy, bz = fz;

        switch (face.getAxis()) {
            case X -> ax = bx = fx + faceOffset(face);
            case Y -> ay = by = fy + faceOffset(face);
            case Z -> az = bz = fz + faceOffset(face);
        }

        switch (t.getAxis()) {
            case X -> ax = bx = fx + (t == Direction.EAST ? 1 : 0);
            case Y -> ay = by = fy + (t == Direction.UP ? 1 : 0);
            case Z -> az = bz = fz + (t == Direction.SOUTH ? 1 : 0);
        }

        Direction.Axis run = runningAxis(face, t);
        switch (run) {
            case X -> { ax = fx + u0; bx = fx + u1; }
            case Y -> { ay = fy + u0; by = fy + u1; }
            case Z -> { az = fz + u0; bz = fz + u1; }
        }

        line(vc, entry, mat, ax, ay, az, bx, by, bz);
    }

    private static double faceOffset(Direction face) {
        return face.getDirection() == Direction.AxisDirection.POSITIVE ? 1.0 : 0.0;
    }

    private static Direction.Axis runningAxis(Direction face, Direction t) {
        for (Direction.Axis a : Direction.Axis.values()) {
            if (a != face.getAxis() && a != t.getAxis()) return a;
        }
        return Direction.Axis.X;
    }

    private static void line(VertexConsumer vc, MatrixStack.Entry entry, Matrix4f mat,
                             double x1, double y1, double z1, double x2, double y2, double z2) {
        float nx = (float) (x2 - x1), ny = (float) (y2 - y1), nz = (float) (z2 - z1);
        vc.vertex(mat, (float) x1, (float) y1, (float) z1)
                .color(R, G, B, A).normal(entry.getNormalMatrix(), nx, ny, nz).next();
        vc.vertex(mat, (float) x2, (float) y2, (float) z2)
                .color(R, G, B, A).normal(entry.getNormalMatrix(), nx, ny, nz).next();
    }

    public static void drawBoxOutline(VertexConsumer vc, MatrixStack.Entry entry, Matrix4f mat,
                                      double x0, double y0, double z0,
                                      double x1, double y1, double z1,
                                      float r, float g, float b, float a) {

        double[][] edges = {
                {x0, y0, z0, x1, y0, z0}, {x0, y0, z1, x1, y0, z1},
                {x0, y0, z0, x0, y0, z1}, {x1, y0, z0, x1, y0, z1},
                {x0, y1, z0, x1, y1, z0}, {x0, y1, z1, x1, y1, z1},
                {x0, y1, z0, x0, y1, z1}, {x1, y1, z0, x1, y1, z1},
                {x0, y0, z0, x0, y1, z0}, {x1, y0, z0, x1, y1, z0},
                {x0, y0, z1, x0, y1, z1}, {x1, y0, z1, x1, y1, z1},
        };
        for (double[] e : edges) {
            float nx = (float) (e[3] - e[0]), ny = (float) (e[4] - e[1]), nz = (float) (e[5] - e[2]);
            vc.vertex(mat, (float) e[0], (float) e[1], (float) e[2])
                    .color(r, g, b, a).normal(entry.getNormalMatrix(), nx, ny, nz).next();
            vc.vertex(mat, (float) e[3], (float) e[4], (float) e[5])
                    .color(r, g, b, a).normal(entry.getNormalMatrix(), nx, ny, nz).next();
        }
    }
}

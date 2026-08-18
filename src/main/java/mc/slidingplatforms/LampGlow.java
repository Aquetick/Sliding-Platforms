package mc.slidingplatforms;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RedstoneLampBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public final class LampGlow {

    public static final int RADIUS = 6;

    private LampGlow() {}

    public static void update(ServerWorld world, BlockPos ctrlPos, @Nullable Box box,
                              boolean enabled, Set<Long> lit) {

        Set<Long> want = (enabled && box != null) ? scan(world, box) : Set.of();

        Iterator<Long> it = lit.iterator();
        while (it.hasNext()) {
            long v = it.next();
            if (want.contains(v)) continue;
            unlight(world, BlockPos.fromLong(v));
            it.remove();
        }

        for (long v : want) {
            BlockPos p = BlockPos.fromLong(v);
            BlockState s = world.getBlockState(p);
            if (!(s.getBlock() instanceof RedstoneLampBlock)) {
                lit.remove(v);
                continue;
            }
            if (world.isReceivingRedstonePower(p)) {
                lit.remove(v);
                continue;
            }
            if (s.get(RedstoneLampBlock.LIT)) {
                lit.add(v);
                continue;
            }
            world.setBlockState(p, s.with(RedstoneLampBlock.LIT, true), Block.NOTIFY_LISTENERS);
            lit.add(v);
        }
    }

    public static void clear(ServerWorld world, Set<Long> lit) {
        for (long v : lit) unlight(world, BlockPos.fromLong(v));
        lit.clear();
    }

    private static void unlight(ServerWorld world, BlockPos p) {
        BlockState s = world.getBlockState(p);
        if (!(s.getBlock() instanceof RedstoneLampBlock)) return;
        if (!s.get(RedstoneLampBlock.LIT)) return;
        if (world.isReceivingRedstonePower(p)) return;
        world.setBlockState(p, s.with(RedstoneLampBlock.LIT, false), Block.NOTIFY_LISTENERS);
    }

    private static Set<Long> scan(ServerWorld world, Box box) {
        Set<Long> out = new HashSet<>();
        int minX = (int) Math.floor(box.minX) - RADIUS;
        int minY = (int) Math.floor(box.minY) - RADIUS;
        int minZ = (int) Math.floor(box.minZ) - RADIUS;
        int maxX = (int) Math.ceil(box.maxX) + RADIUS - 1;
        int maxY = (int) Math.ceil(box.maxY) + RADIUS - 1;
        int maxZ = (int) Math.ceil(box.maxZ) + RADIUS - 1;
        double r2 = (double) RADIUS * RADIUS;
        for (BlockPos p : BlockPos.iterate(minX, minY, minZ, maxX, maxY, maxZ)) {
            double cx = p.getX() + 0.5, cy = p.getY() + 0.5, cz = p.getZ() + 0.5;
            double dx = Math.max(Math.max(box.minX - cx, 0), cx - box.maxX);
            double dy = Math.max(Math.max(box.minY - cy, 0), cy - box.maxY);
            double dz = Math.max(Math.max(box.minZ - cz, 0), cz - box.maxZ);
            if (dx * dx + dy * dy + dz * dz > r2) continue;
            if (world.getBlockState(p).isOf(Blocks.REDSTONE_LAMP)) {
                out.add(p.asLong());
            }
        }
        return out;
    }
}

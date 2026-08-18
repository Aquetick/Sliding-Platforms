package mc.slidingplatforms;

import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Block;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.PistonHeadBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public final class MultiPart {

    private MultiPart() {}

    public static List<BlockPos> relatedParts(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        List<BlockPos> extra = new ArrayList<>(1);

        if (block instanceof DoorBlock) {

            BlockPos other = state.get(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.down() : pos.up();
            addIfSameBlock(world, block, extra, other);
        } else if (block instanceof BedBlock) {

            Direction f = state.get(BedBlock.FACING);
            BlockPos other = state.get(BedBlock.PART) == BedPart.HEAD ? pos.offset(f.getOpposite()) : pos.offset(f);
            addIfSameBlock(world, block, extra, other);
        } else if (block instanceof TallPlantBlock) {

            BlockPos other = state.get(TallPlantBlock.HALF) == DoubleBlockHalf.UPPER ? pos.down() : pos.up();
            addIfSameBlock(world, block, extra, other);
        } else if (block instanceof PistonHeadBlock) {

            BlockPos base = pos.offset(state.get(PistonHeadBlock.FACING).getOpposite());
            if (world.getBlockState(base).getBlock() instanceof PistonBlock) extra.add(base);
        } else if (block instanceof PistonBlock) {

            if (state.get(PistonBlock.EXTENDED)) {
                BlockPos head = pos.offset(state.get(PistonBlock.FACING));
                if (world.getBlockState(head).getBlock() instanceof PistonHeadBlock) extra.add(head);
            }
        }
        return extra;
    }

    private static void addIfSameBlock(World world, Block block, List<BlockPos> extra, BlockPos other) {
        if (!world.isOutOfHeightLimit(other) && world.getBlockState(other).getBlock() == block) {
            extra.add(other);
        }
    }
}

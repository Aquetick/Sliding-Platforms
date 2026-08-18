package mc.slidingplatforms;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class RemoteSwitchBlock extends Block implements BlockEntityProvider {

    public RemoteSwitchBlock(Settings settings) {
        super(settings);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new RemoteSwitchBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof RemoteSwitchBlockEntity be)) {
            return ActionResult.PASS;
        }
        if (world.isClient) return ActionResult.success(true);

        if (player.isSneaking() && player.getMainHandStack().isEmpty()) {
            player.openHandledScreen(be);
            return ActionResult.CONSUME;
        }
        if (player.isSneaking()) return ActionResult.PASS;
        be.trigger(player);
        return ActionResult.CONSUME;
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock,
                               BlockPos sourcePos, boolean notify) {
        if (world.isClient) return;
        if (world.getBlockEntity(pos) instanceof RemoteSwitchBlockEntity be) {
            be.onRedstoneUpdate(world.isReceivingRedstonePower(pos));
        }
    }
}

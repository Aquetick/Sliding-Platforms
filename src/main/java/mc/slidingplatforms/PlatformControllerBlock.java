package mc.slidingplatforms;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class PlatformControllerBlock extends Block implements BlockEntityProvider {

    public static final DirectionProperty FACING = Properties.FACING;

    public PlatformControllerBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {

        return getDefaultState().with(FACING, ctx.getPlayerLookDirection().getOpposite());
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         @Nullable net.minecraft.entity.LivingEntity placer,
                         net.minecraft.item.ItemStack itemStack) {
        if (world instanceof net.minecraft.server.world.ServerWorld
                && world.getBlockEntity(pos) instanceof PlatformControllerBlockEntity be) {

            if (placer instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                be.usePlacementWord(ClientLanguages.localizedWord(sp.getUuid(), "Контроллер", "Controller"));
            }
            be.getPlatformName();
            if (placer != null) be.setAnchorDir(placer.getHorizontalFacing());
        }
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PlatformControllerBlockEntity(pos, state);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos,
                                BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())
                && world.getBlockEntity(pos) instanceof PlatformControllerBlockEntity be) {
            be.onBroken();
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient || type != ModBlocks.PLATFORM_CONTROLLER_BE) return null;
        return (w, p, s, be) -> PlatformControllerBlockEntity.serverTick(w, p, (PlatformControllerBlockEntity) be);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof PlatformControllerBlockEntity be)) {
            return ActionResult.PASS;
        }
        if (world.isClient) {
            return ActionResult.success(true);
        }

        boolean emptyMain = player.getMainHandStack().isEmpty();

        if (player.isSneaking() && emptyMain) {

            if (SlidingPlatforms.finishSelection(player) != null) {
                int count = be.manualCount();
                player.sendMessage(count > 0
                        ? Text.translatable("message.slidingplatforms.select_done", count)
                        : Text.translatable("message.slidingplatforms.select_cleared"), true);
                SlidingPlatforms.sendSelectionSync(player, null, java.util.List.of());
            } else if (SlidingPlatforms.finishZoneSelection(player) != null) {

                SlidingPlatforms.sendZoneSync(player, null, false);
                player.sendMessage(be.hasSensorZone()
                        ? Text.translatable("message.slidingplatforms.zone_set",
                                be.zoneDims()[0], be.zoneDims()[1], be.zoneDims()[2])
                        : Text.translatable("message.slidingplatforms.zone_cleared"), true);
                SlidingPlatforms.openSensorMenu(player, pos);
            } else {

                if (!be.canConfigureLocked(player)) {
                    player.sendMessage(Text.translatable("message.slidingplatforms.lock_owner_only"), true);
                    return ActionResult.CONSUME;
                }
                player.openHandledScreen(be);
            }
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
        if (world.getBlockEntity(pos) instanceof PlatformControllerBlockEntity be) {
            be.onRedstoneUpdate(world.isReceivingRedstonePower(pos));
        }
    }

    @Override
    public boolean hasComparatorOutput(BlockState state) {
        return true;
    }

    @Override
    public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        if (!(world.getBlockEntity(pos) instanceof PlatformControllerBlockEntity be)) return 0;
        if (be.isMoving()) return 7;
        return be.isOpen() ? 15 : 0;
    }
}

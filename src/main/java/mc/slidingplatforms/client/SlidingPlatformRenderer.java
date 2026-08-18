package mc.slidingplatforms.client;

import mc.slidingplatforms.SlidingPlatformEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.Set;

public class SlidingPlatformRenderer extends EntityRenderer<SlidingPlatformEntity> {

    private final BlockRenderManager blockRenderManager;

    private static final Set<Integer> loggedEntities = new HashSet<>();

    public SlidingPlatformRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.blockRenderManager = ctx.getBlockRenderManager();
        this.shadowRadius = 0;
    }

    @Override
    public void render(SlidingPlatformEntity entity, float yaw, float tickDelta,
                       MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();

        net.minecraft.util.math.Vec3d ours = entity.smoothRenderPos(tickDelta);
        net.minecraft.util.math.Vec3d vanilla = entity.getLerpedPos(tickDelta);
        matrices.translate(ours.x - vanilla.x, ours.y - vanilla.y, ours.z - vanilla.z);
        for (SlidingPlatformEntity.RenderBlock block : entity.getRenderBlocks()) {
            matrices.push();

            matrices.translate(block.x(), block.y(), block.z());
            blockRenderManager.renderBlockAsEntity(block.state(), matrices, vertexConsumers,
                    light, OverlayTexture.DEFAULT_UV);
            matrices.pop();
        }
        matrices.pop();

        if (loggedEntities.add(entity.getId())) {
            mc.slidingplatforms.SlidingPlatforms.LOGGER.info(
                    "Рендер платформы #{}: блоков {}, позиция {}",
                    entity.getId(), entity.getRenderBlocks().size(), entity.getBlockPos());
        }
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(SlidingPlatformEntity entity) {

        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }
}

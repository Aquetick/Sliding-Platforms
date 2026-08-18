package mc.slidingplatforms;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModEntities {

    public static EntityType<SlidingPlatformEntity> SLIDING_PLATFORM;

    public static void register() {
        SLIDING_PLATFORM = Registry.register(Registries.ENTITY_TYPE,
                new Identifier(SlidingPlatforms.MOD_ID, "sliding_platform"),
                FabricEntityTypeBuilder.create(SpawnGroup.MISC, SlidingPlatformEntity::new)
                        .dimensions(EntityDimensions.fixed(0.6f, 0.6f))

                        .trackRangeBlocks(128)
                        .trackedUpdateRate(1)
                        .build());
    }
}

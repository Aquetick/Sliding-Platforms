package mc.slidingplatforms;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModEntities {

    public static EntityType<SlidingPlatformEntity> SLIDING_PLATFORM;

    public static void register() {
        SLIDING_PLATFORM = Registry.register(Registries.ENTITY_TYPE,
                Identifier.of(SlidingPlatforms.MOD_ID, "sliding_platform"),
                EntityType.Builder.<SlidingPlatformEntity>create(SlidingPlatformEntity::new, SpawnGroup.MISC)
                        .dimensions(0.6f, 0.6f)
                        .maxTrackingRange(128)
                        .trackingTickInterval(1)
                        .build());
    }
}

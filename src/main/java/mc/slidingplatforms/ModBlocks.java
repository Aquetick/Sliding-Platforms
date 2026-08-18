package mc.slidingplatforms;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ModBlocks {

    public static final PlatformControllerBlock PLATFORM_CONTROLLER = new PlatformControllerBlock(
            AbstractBlock.Settings.copy(Blocks.IRON_BLOCK));

    public static final RemoteSwitchBlock REMOTE_SWITCH = new RemoteSwitchBlock(
            AbstractBlock.Settings.copy(Blocks.IRON_BLOCK));

    public static final ElevatorScreenBlock ELEVATOR_SCREEN = new ElevatorScreenBlock(
            AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).luminance(s -> 4));

    public static BlockEntityType<PlatformControllerBlockEntity> PLATFORM_CONTROLLER_BE;
    public static BlockEntityType<RemoteSwitchBlockEntity> REMOTE_SWITCH_BE;
    public static BlockEntityType<ElevatorScreenBlockEntity> ELEVATOR_SCREEN_BE;

    public static void register() {
        Identifier controllerId = new Identifier(SlidingPlatforms.MOD_ID, "platform_controller");
        Identifier switchId = new Identifier(SlidingPlatforms.MOD_ID, "remote_switch");
        Identifier screenId = new Identifier(SlidingPlatforms.MOD_ID, "elevator_screen");

        Registry.register(Registries.BLOCK, controllerId, PLATFORM_CONTROLLER);
        Registry.register(Registries.BLOCK, switchId, REMOTE_SWITCH);
        Registry.register(Registries.BLOCK, screenId, ELEVATOR_SCREEN);

        Registry.register(Registries.ITEM, controllerId,
                new BlockItem(PLATFORM_CONTROLLER, new Item.Settings()));
        Registry.register(Registries.ITEM, switchId,
                new RemoteSwitchItem(REMOTE_SWITCH, new Item.Settings()));
        Registry.register(Registries.ITEM, screenId,
                new ElevatorScreenItem(ELEVATOR_SCREEN, new Item.Settings()));

        PLATFORM_CONTROLLER_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, controllerId,
                BlockEntityType.Builder.create(PlatformControllerBlockEntity::new, PLATFORM_CONTROLLER).build(null));
        REMOTE_SWITCH_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, switchId,
                BlockEntityType.Builder.create(RemoteSwitchBlockEntity::new, REMOTE_SWITCH).build(null));
        ELEVATOR_SCREEN_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE, screenId,
                BlockEntityType.Builder.create(ElevatorScreenBlockEntity::new, ELEVATOR_SCREEN).build(null));

        Registry.register(Registries.ITEM_GROUP, new Identifier(SlidingPlatforms.MOD_ID, "items"),
                FabricItemGroup.builder()
                        .displayName(Text.translatable("itemGroup.slidingplatforms.items"))
                        .icon(() -> new ItemStack(PLATFORM_CONTROLLER))
                        .entries((displayContext, entries) -> {
                            entries.add(PLATFORM_CONTROLLER);
                            entries.add(REMOTE_SWITCH);
                            entries.add(ELEVATOR_SCREEN);
                        })
                        .build());

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL)
                .register(entries -> {
                    entries.add(PLATFORM_CONTROLLER);
                    entries.add(ELEVATOR_SCREEN);
                });
    }
}

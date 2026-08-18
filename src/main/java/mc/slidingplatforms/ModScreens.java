package mc.slidingplatforms;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public class ModScreens {

    public static ScreenHandlerType<PlatformControllerScreenHandler> PLATFORM_CONTROLLER;
    public static ScreenHandlerType<RemoteSwitchScreenHandler> REMOTE_SWITCH;
    public static ScreenHandlerType<FloorSelectScreenHandler> FLOOR_SELECT;
    public static ScreenHandlerType<ScreenSelectScreenHandler> SCREEN_SELECT;
    public static ScreenHandlerType<ScreenSettingsScreenHandler> SCREEN_SETTINGS;
    public static ScreenHandlerType<PlatformSoundsScreenHandler> PLATFORM_SOUNDS;
    public static ScreenHandlerType<PlatformSensorScreenHandler> PLATFORM_SENSOR;
    public static ScreenHandlerType<PlatformLockScreenHandler> PLATFORM_LOCK;
    public static ScreenHandlerType<PlatformCascadeScreenHandler> PLATFORM_CASCADE;
    public static ScreenHandlerType<ConfigScreenHandler> CONFIG;

    public static void register() {
        PLATFORM_CONTROLLER = Registry.register(Registries.SCREEN_HANDLER,
                new Identifier(SlidingPlatforms.MOD_ID, "platform_controller"),
                new ExtendedScreenHandlerType<>(PlatformControllerScreenHandler::new));
        REMOTE_SWITCH = Registry.register(Registries.SCREEN_HANDLER,
                new Identifier(SlidingPlatforms.MOD_ID, "remote_switch"),
                new ExtendedScreenHandlerType<>(RemoteSwitchScreenHandler::new));
        FLOOR_SELECT = Registry.register(Registries.SCREEN_HANDLER,
                new Identifier(SlidingPlatforms.MOD_ID, "floor_select"),
                new ExtendedScreenHandlerType<>(FloorSelectScreenHandler::new));
        SCREEN_SELECT = Registry.register(Registries.SCREEN_HANDLER,
                new Identifier(SlidingPlatforms.MOD_ID, "screen_select"),
                new ExtendedScreenHandlerType<>(ScreenSelectScreenHandler::new));
        SCREEN_SETTINGS = Registry.register(Registries.SCREEN_HANDLER,
                new Identifier(SlidingPlatforms.MOD_ID, "screen_settings"),
                new ExtendedScreenHandlerType<>(ScreenSettingsScreenHandler::new));

        PLATFORM_SOUNDS = Registry.register(Registries.SCREEN_HANDLER,
                new Identifier(SlidingPlatforms.MOD_ID, "platform_sounds"),
                new ExtendedScreenHandlerType<>(PlatformSoundsScreenHandler::new));

        PLATFORM_SENSOR = Registry.register(Registries.SCREEN_HANDLER,
                new Identifier(SlidingPlatforms.MOD_ID, "platform_sensor"),
                new ExtendedScreenHandlerType<>(PlatformSensorScreenHandler::new));

        PLATFORM_LOCK = Registry.register(Registries.SCREEN_HANDLER,
                new Identifier(SlidingPlatforms.MOD_ID, "platform_lock"),
                new ExtendedScreenHandlerType<>(PlatformLockScreenHandler::new));

        PLATFORM_CASCADE = Registry.register(Registries.SCREEN_HANDLER,
                new Identifier(SlidingPlatforms.MOD_ID, "platform_cascade"),
                new ExtendedScreenHandlerType<>(PlatformCascadeScreenHandler::new));

        CONFIG = Registry.register(Registries.SCREEN_HANDLER,
                new Identifier(SlidingPlatforms.MOD_ID, "mod_config"),
                new ExtendedScreenHandlerType<>(ConfigScreenHandler::new));
    }
}

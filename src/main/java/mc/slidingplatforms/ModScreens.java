package mc.slidingplatforms;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public class ModScreens {

    /**
     * PacketCodec для PacketByteBuf-based экранов.
     *
     * Encoder: записывает VarInt(length), затем все байты буфера.
     * Decoder: читает VarInt(length), возвращает slice ровно на эти байты.
     *
     * Это гарантирует что после чтения данных экрана в общем пакете
     * не остаётся лишних байт, и Netty не ругается на "N bytes extra".
     */
    static final PacketCodec<PacketByteBuf, PacketByteBuf> BUF_CODEC = PacketCodec.of(
            // encoder: server → client
            (value, out) -> {
                byte[] bytes = new byte[value.readableBytes()];
                value.getBytes(value.readerIndex(), bytes);
                out.writeVarInt(bytes.length);
                out.writeBytes(bytes);
            },
            // decoder: client side
            in -> {
                int len = in.readVarInt();
                PacketByteBuf slice = new PacketByteBuf(in.readBytes(len));
                return slice;
            }
    );

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
                Identifier.of(SlidingPlatforms.MOD_ID, "platform_controller"),
                new ExtendedScreenHandlerType<>(PlatformControllerScreenHandler::new, BUF_CODEC));
        REMOTE_SWITCH = Registry.register(Registries.SCREEN_HANDLER,
                Identifier.of(SlidingPlatforms.MOD_ID, "remote_switch"),
                new ExtendedScreenHandlerType<>(RemoteSwitchScreenHandler::new, BUF_CODEC));
        FLOOR_SELECT = Registry.register(Registries.SCREEN_HANDLER,
                Identifier.of(SlidingPlatforms.MOD_ID, "floor_select"),
                new ExtendedScreenHandlerType<>(FloorSelectScreenHandler::new, BUF_CODEC));
        SCREEN_SELECT = Registry.register(Registries.SCREEN_HANDLER,
                Identifier.of(SlidingPlatforms.MOD_ID, "screen_select"),
                new ExtendedScreenHandlerType<>(ScreenSelectScreenHandler::new, BUF_CODEC));
        SCREEN_SETTINGS = Registry.register(Registries.SCREEN_HANDLER,
                Identifier.of(SlidingPlatforms.MOD_ID, "screen_settings"),
                new ExtendedScreenHandlerType<>(ScreenSettingsScreenHandler::new, BUF_CODEC));

        PLATFORM_SOUNDS = Registry.register(Registries.SCREEN_HANDLER,
                Identifier.of(SlidingPlatforms.MOD_ID, "platform_sounds"),
                new ExtendedScreenHandlerType<>(PlatformSoundsScreenHandler::new, BUF_CODEC));

        PLATFORM_SENSOR = Registry.register(Registries.SCREEN_HANDLER,
                Identifier.of(SlidingPlatforms.MOD_ID, "platform_sensor"),
                new ExtendedScreenHandlerType<>(PlatformSensorScreenHandler::new, BUF_CODEC));

        PLATFORM_LOCK = Registry.register(Registries.SCREEN_HANDLER,
                Identifier.of(SlidingPlatforms.MOD_ID, "platform_lock"),
                new ExtendedScreenHandlerType<>(PlatformLockScreenHandler::new, BUF_CODEC));

        PLATFORM_CASCADE = Registry.register(Registries.SCREEN_HANDLER,
                Identifier.of(SlidingPlatforms.MOD_ID, "platform_cascade"),
                new ExtendedScreenHandlerType<>(PlatformCascadeScreenHandler::new, BUF_CODEC));

        CONFIG = Registry.register(Registries.SCREEN_HANDLER,
                Identifier.of(SlidingPlatforms.MOD_ID, "mod_config"),
                new ExtendedScreenHandlerType<>(ConfigScreenHandler::new, BUF_CODEC));
    }
}

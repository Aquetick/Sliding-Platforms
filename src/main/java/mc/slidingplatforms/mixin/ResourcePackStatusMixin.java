package mc.slidingplatforms.mixin;

import mc.slidingplatforms.SoundPackService;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonNetworkHandler.class)
public abstract class ResourcePackStatusMixin {

    @Inject(method = "onResourcePackStatus", at = @At("TAIL"))
    private void slidingplatforms$onResourcePackStatus(ResourcePackStatusC2SPacket packet, CallbackInfo ci) {
        // In 1.21.1 the Status values changed; check for failure
        ResourcePackStatusC2SPacket.Status status = packet.status();
        if (status != ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED
                && status != ResourcePackStatusC2SPacket.Status.ACCEPTED
                && status != ResourcePackStatusC2SPacket.Status.DOWNLOADED) {
            // Try to notify, but guard against cast issues
            try {
                net.minecraft.server.network.ServerPlayerEntity player =
                    ((net.minecraft.server.network.ServerPlayNetworkHandler)(Object)this).player;
                SoundPackService.onPackDownloadFailed(player);
            } catch (ClassCastException ignored) {}
        }
    }
}

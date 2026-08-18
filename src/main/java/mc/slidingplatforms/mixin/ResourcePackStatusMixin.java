package mc.slidingplatforms.mixin;

import mc.slidingplatforms.SoundPackService;
import net.minecraft.network.packet.c2s.play.ResourcePackStatusC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ResourcePackStatusMixin {

    @Inject(method = "onResourcePackStatus", at = @At("TAIL"))
    private void slidingplatforms$onResourcePackStatus(ResourcePackStatusC2SPacket packet, CallbackInfo ci) {
        if (packet.getStatus() != ResourcePackStatusC2SPacket.Status.FAILED_DOWNLOAD) return;
        SoundPackService.onPackDownloadFailed(((ServerPlayNetworkHandler) (Object) this).player);
    }
}

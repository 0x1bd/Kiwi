package org.kvxd.kiwi.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import org.kvxd.kiwi.config.ConfigData;
import org.kvxd.kiwi.control.RotationManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public class MixinPlayerEntityRenderer {

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At("RETURN"))
    private void onUpdateRenderState(PlayerLikeEntity player, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (player == MinecraftClient.getInstance().player) {
            if (RotationManager.INSTANCE.getHasTarget() && ConfigData.INSTANCE.getFreelook()) {
                state.pitch = RotationManager.INSTANCE.getTargetPitch();
                state.bodyYaw = RotationManager.INSTANCE.getTargetYaw();

                state.relativeHeadYaw = 0f;
            }
        }
    }
}
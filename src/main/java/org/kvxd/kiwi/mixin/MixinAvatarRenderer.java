package org.kvxd.kiwi.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.LivingEntity;
import org.kvxd.kiwi.config.ConfigData;
import org.kvxd.kiwi.control.RotationManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class MixinAvatarRenderer<AvatarlikeEntity extends Avatar & ClientAvatarEntity> {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("RETURN"))
    private void kiwi$extractRenderState(AvatarlikeEntity avatar, AvatarRenderState avatarRenderState, float f, CallbackInfo ci) {
        if (avatar == Minecraft.getInstance().player) {
            if (RotationManager.INSTANCE.getHasTarget() && ConfigData.INSTANCE.getFreelook()) {
                avatarRenderState.xRot = RotationManager.INSTANCE.getTargetXRot();
                avatarRenderState.bodyRot = RotationManager.INSTANCE.getTargetYRot();

                avatarRenderState.yRot = 0f;
            }
        }
    }
}
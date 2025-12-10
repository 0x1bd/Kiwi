package org.kvxd.kiwi.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.kvxd.kiwi.control.RotationManager;
import org.kvxd.kiwi.util.RaycastHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "pick(F)V", at = @At("HEAD"), cancellable = true)
    private void onPick(float f, CallbackInfo ci) {
        if (RotationManager.INSTANCE.getHasTarget()) {
            Entity cameraEntity = this.minecraft.getCameraEntity();
            if (cameraEntity == null) return;

            HitResult customHit = RaycastHelper.INSTANCE.raycast(f);

            this.minecraft.hitResult = customHit;

            if (customHit instanceof EntityHitResult entityHit) {
                this.minecraft.crosshairPickEntity = entityHit.getEntity();
            } else {
                this.minecraft.crosshairPickEntity = null;
            }

            ci.cancel();
        }
    }

}

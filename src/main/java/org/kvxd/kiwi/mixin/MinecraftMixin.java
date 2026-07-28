package org.kvxd.kiwi.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;
import org.kvxd.kiwi.control.LookController;
import org.kvxd.kiwi.util.math.RaycastHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Shadow
    public abstract @Nullable Entity getCameraEntity();

    @Shadow
    @Nullable
    public HitResult hitResult;

    @Shadow
    @Nullable
    public Entity crosshairPickEntity;

    @Inject(method = "pick(F)V", at = @At("HEAD"), cancellable = true)
    private void kiwi$pick(float f, CallbackInfo ci) {
        if (LookController.getHasTarget()) {
            Entity cameraEntity = getCameraEntity();
            if (cameraEntity == null) return;

            HitResult customHit = RaycastHelper.INSTANCE.raycast(f);

            hitResult = customHit;

            if (customHit instanceof EntityHitResult entityHit) {
                crosshairPickEntity = entityHit.getEntity();
            } else {
                crosshairPickEntity = null;
            }

            ci.cancel();
        }
    }

}

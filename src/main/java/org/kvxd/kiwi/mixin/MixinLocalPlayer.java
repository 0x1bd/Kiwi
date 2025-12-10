package org.kvxd.kiwi.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.kvxd.kiwi.config.ConfigData;
import org.kvxd.kiwi.control.RotationManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer extends Player {

    @Shadow
    public float yBob;
    @Shadow
    public float yBobO;
    @Shadow
    public float xBob;
    @Shadow
    public float xBobO;

    @Unique
    private float storedYaw;
    @Unique
    private float storedPitch;

    public MixinLocalPlayer(Level level, GameProfile gameProfile) {
        super(level, gameProfile);
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void onAiStepHead(CallbackInfo ci) {
        if (RotationManager.INSTANCE.getHasTarget() && ConfigData.INSTANCE.getFreelook()) {
            this.storedYaw = this.getYRot();
            this.storedPitch = this.getXRot();

            this.setYRot(Mth.wrapDegrees(RotationManager.INSTANCE.getTargetYRot()));
            this.setXRot(RotationManager.INSTANCE.getTargetXRot());

            this.setYBodyRot(this.getYRot());
            this.setYHeadRot(this.getYRot());
        }
    }

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void onAiStepReturn(CallbackInfo ci) {
        if (RotationManager.INSTANCE.getHasTarget() && ConfigData.INSTANCE.getFreelook()) {
            this.setYRot(this.storedYaw);
            this.setXRot(this.storedPitch);

            this.yRotO = this.storedYaw;
            this.xRotO = this.storedPitch;

            this.yBob = this.storedYaw;
            this.yBobO = this.storedYaw;

            this.xBob = this.storedPitch;
            this.xBobO = this.storedPitch;

            this.setYBodyRot(this.storedYaw);
            this.setYHeadRot(this.storedYaw);
        }
    }

    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void onSendPositionHead(CallbackInfo ci) {
        if (RotationManager.INSTANCE.getHasTarget() && ConfigData.INSTANCE.getFreelook()) {
            this.storedYaw = this.getYRot();
            this.storedPitch = this.getXRot();

            this.setYRot(Mth.wrapDegrees(RotationManager.INSTANCE.getTargetYRot()));
            this.setXRot(RotationManager.INSTANCE.getTargetXRot());
        }
    }

    @Inject(method = "sendPosition", at = @At("RETURN"))
    private void onSendPositionReturn(CallbackInfo ci) {
        if (RotationManager.INSTANCE.getHasTarget() && ConfigData.INSTANCE.getFreelook()) {
            this.setYRot(this.storedYaw);
            this.setXRot(this.storedPitch);
        }
    }
}
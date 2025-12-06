package org.kvxd.kiwi.mixin;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.kvxd.kiwi.config.ConfigManager;
import org.kvxd.kiwi.control.RotationManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class MixinClientPlayerEntity extends PlayerEntity {

    @Shadow public float renderYaw;
    @Shadow public float lastRenderYaw;
    @Shadow public float renderPitch;
    @Shadow public float lastRenderPitch;

    @Unique private float storedYaw;
    @Unique private float storedPitch;

    public MixinClientPlayerEntity(World world, GameProfile profile) {
        super(world, profile);
    }

    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void onTickMovementHead(CallbackInfo ci) {
        if (RotationManager.INSTANCE.getHasTarget() && ConfigManager.INSTANCE.getData().getFreelook()) {
            this.storedYaw = this.getYaw();
            this.storedPitch = this.getPitch();

            this.setYaw(MathHelper.wrapDegrees(RotationManager.INSTANCE.getTargetYaw()));
            this.setPitch(RotationManager.INSTANCE.getTargetPitch());

            this.setHeadYaw(this.getYaw());
            this.setBodyYaw(this.getYaw());
        }
    }

    @Inject(method = "tickMovement", at = @At("RETURN"))
    private void onTickMovementReturn(CallbackInfo ci) {
        if (RotationManager.INSTANCE.getHasTarget() && ConfigManager.INSTANCE.getData().getFreelook()) {
            this.setYaw(this.storedYaw);
            this.setPitch(this.storedPitch);

            this.lastYaw = this.storedYaw;
            this.lastPitch = this.storedPitch;

            this.renderYaw = this.storedYaw;
            this.lastRenderYaw = this.storedYaw;

            this.renderPitch = this.storedPitch;
            this.lastRenderPitch = this.storedPitch;

            this.setBodyYaw(this.storedYaw);
            this.setHeadYaw(this.storedYaw);
        }
    }

    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void onSendMovementPacketsHead(CallbackInfo ci) {
        if (RotationManager.INSTANCE.getHasTarget() && ConfigManager.INSTANCE.getData().getFreelook()) {
            this.storedYaw = this.getYaw();
            this.storedPitch = this.getPitch();

            this.setYaw(MathHelper.wrapDegrees(RotationManager.INSTANCE.getTargetYaw()));
            this.setPitch(RotationManager.INSTANCE.getTargetPitch());
        }
    }

    @Inject(method = "sendMovementPackets", at = @At("RETURN"))
    private void onSendMovementPacketsReturn(CallbackInfo ci) {
        if (RotationManager.INSTANCE.getHasTarget() && ConfigManager.INSTANCE.getData().getFreelook()) {
            this.setYaw(this.storedYaw);
            this.setPitch(this.storedPitch);
        }
    }
}
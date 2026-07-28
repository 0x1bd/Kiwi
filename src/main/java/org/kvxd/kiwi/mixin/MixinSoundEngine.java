package org.kvxd.kiwi.mixin;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.kvxd.kiwi.HeadlessMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public class MixinSoundEngine {

    @Inject(
        method = "calculateVolume(FLnet/minecraft/sounds/SoundSource;)F",
        at = @At("HEAD"),
        cancellable = true
    )
    private void kiwi$muteWhenHeadless(float volume, SoundSource source, CallbackInfoReturnable<Float> cir) {
        if (HeadlessMode.isEnabled()) {
            cir.setReturnValue(0.0F);
        }
    }
}

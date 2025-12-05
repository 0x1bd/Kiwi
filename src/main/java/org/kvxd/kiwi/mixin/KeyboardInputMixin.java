package org.kvxd.kiwi.mixin;

import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec2f;
import org.kvxd.kiwi.control.InputController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin extends Input {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTick(CallbackInfo ci) {
        if (InputController.INSTANCE.getActive()) {
            InputController c = InputController.INSTANCE;

            this.playerInput = new PlayerInput(
                    c.getForward(),
                    c.getBack(),
                    c.getLeft(),
                    c.getRight(),
                    c.getJump(),
                    c.getSneak(),
                    c.getSprint()
            );

            // consume the jump
            if (c.getJump()) {
                c.setJump(false);
            }

            float forward = c.getForward() == c.getBack() ? 0.0F : (c.getForward() ? 1.0F : -1.0F);
            float strafe = c.getLeft() == c.getRight() ? 0.0F : (c.getLeft() ? 1.0F : -1.0F);

            this.movementVector = new Vec2f(strafe, forward);

            ci.cancel();
        }
    }

}

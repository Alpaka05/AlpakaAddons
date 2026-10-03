package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Always sprint: moving forward counts as holding the sprint key.
 *
 * Done at the input rather than by forcing the sprint flag on the player, so everything vanilla
 * decides about sprinting still applies - no sprinting while sneaking, when hungry or against a
 * wall. Only forward movement is affected; strafing and walking backwards stay as they are. With
 * vanilla's own toggle-sprint option on, the sprint key is left alone: that option already sprints
 * for the player, and its off state has to keep working.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {

    @Inject(method = "tick", at = @At("TAIL"))
    private void alpaka$alwaysSprint(CallbackInfo ci) {
        if (!AlpakaConfig.instance.alwaysSprintEnabled) return;
        if (net.minecraft.client.Minecraft.getInstance().options.toggleSprint().get()) return;
        Input keys = this.keyPresses;
        if (keys.forward() && !keys.sprint()) {
            this.keyPresses = new Input(keys.forward(), keys.backward(), keys.left(), keys.right(),
                    keys.jump(), keys.shift(), true);
        }
    }
}

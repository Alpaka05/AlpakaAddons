package net.alpaka.addons.mixin;

import net.alpaka.addons.features.skills.ExactSkillXp;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Exact Skill XP changes the action bar where it is handed to the HUD, after Fabric's message
 * events: the features that read the action bar there still see Hypixel's own percentage.
 */
@Mixin(Hud.class)
public abstract class HudOverlayMessageMixin {

    @ModifyVariable(method = "setOverlayMessage", at = @At("HEAD"), argsOnly = true)
    private Component alpaka$exactSkillXp(Component message) {
        return ExactSkillXp.rewrite(message);
    }
}

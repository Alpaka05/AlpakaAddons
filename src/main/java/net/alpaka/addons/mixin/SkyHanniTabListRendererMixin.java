package net.alpaka.addons.mixin;

import net.alpaka.addons.features.chat.ChatTabsFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps SkyHanni's Compact Tab List out of the way while Tab cycles the chat tabs.
 *
 * In its "toggle tab" mode SkyHanni does not go through the vanilla player list at all: every
 * frame it reads the physical state of the player list key straight from GLFW, flips its own
 * open/closed flag on each new press and draws its list while the flag is set. Neither the vanilla
 * list hook nor the key mapping can reach that, so the one place left is the handler itself - it is
 * skipped for as long as Tab means "next chat tab", which leaves the flag and the list as they were.
 *
 * A pseudo mixin: SkyHanni is not a dependency, so the target only exists when it is installed and
 * the mixin is simply not applied otherwise. The injection is not required either, so a SkyHanni
 * version that renames the handler costs only this compatibility, not the game.
 */
@Pseudo
@Mixin(targets = "at.hannibal2.skyhanni.features.misc.compacttablist.TabListRenderer", remap = false)
public class SkyHanniTabListRendererMixin {

    @Inject(method = "onGuiRenderOverlay()V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void alpaka$noToggleWhileTabCyclesChatTabs(CallbackInfo ci) {
        if (ChatTabsFeature.suppressesTabList()) {
            ci.cancel();
        }
    }
}

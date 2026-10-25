package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.dungeon.terminal.TerminalOverlay;
import net.alpaka.addons.features.itemvalue.ItemValuePopup;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The item the Item Value popup is open on keeps its tooltip to itself: the popup opens beside the
 * mouse, which is still on the item, and the tooltip would cover it.
 *
 * Also where the terminal solvers draw on the vanilla chest and may refuse the player's own click
 * (see TerminalOverlay). A refused click is dropped before vanilla does anything with it, so
 * nothing is sent and the menu is not touched; it is never turned into a different click. A click
 * let through is recorded once it went to the server. All of it is skipped while the Terminal
 * Solvers master is off.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Shadow protected Slot hoveredSlot;

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void alpaka$noTooltipUnderItemValue(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (hoveredSlot != null && ItemValuePopup.hidesTooltipOf((Screen) (Object) this, hoveredSlot.getItem())) ci.cancel();
    }

    /** Behind a slot's item: a solution fill. Cancelled to leave the item out (Hide Other Items). */
    @Inject(method = "extractSlot", at = @At("HEAD"), cancellable = true)
    private void alpaka$terminalUnderSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (!AlpakaConfig.instance.terminalSolversEnabled) return;
        if (TerminalOverlay.underSlot((AbstractContainerScreen<?>) (Object) this, graphics, slot)) ci.cancel();
    }

    /** Over a slot's item: labels and marks. */
    @Inject(method = "extractSlot", at = @At("RETURN"))
    private void alpaka$terminalOverSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (!AlpakaConfig.instance.terminalSolversEnabled) return;
        TerminalOverlay.overSlot((AbstractContainerScreen<?>) (Object) this, graphics, slot);
    }

    /** Every click path of the screen ends here; a refused terminal click stops before anything happens. */
    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void alpaka$terminalClickGuard(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
        if (!AlpakaConfig.instance.terminalSolversEnabled) return;
        if (!TerminalOverlay.allowSlotClick((AbstractContainerScreen<?>) (Object) this, slot, slotId, button, input)) {
            ci.cancel();
        }
    }

    /**
     * Right after the click went to the server and vanilla handled it on the client: the session
     * records it and notes what the slot holds now. Only reached when the click really went out -
     * a cancel at HEAD, or another mod's cancel before the call, skips it. Observes only.
     */
    @Inject(method = "slotClicked", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;handleContainerInput(IIILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V",
            shift = At.Shift.AFTER))
    private void alpaka$terminalClickSent(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
        if (!AlpakaConfig.instance.terminalSolversEnabled) return;
        TerminalOverlay.afterSlotClickSent((AbstractContainerScreen<?>) (Object) this);
    }

    /**
     * The release of a shift double click shift-clicks every slot holding the same item as the one
     * shift-clicked last (the first slotClicked call in mouseReleased). In a terminal none of those
     * is sent: they are clicks on cells the player never aimed at. Only ever sends less.
     */
    @WrapOperation(method = "mouseReleased", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V",
            ordinal = 0))
    private void alpaka$terminalNoShiftCollect(AbstractContainerScreen<?> screen, Slot slot, int slotId, int button,
                                               ContainerInput input, Operation<Void> original) {
        if (AlpakaConfig.instance.terminalSolversEnabled && input == ContainerInput.QUICK_MOVE
                && !TerminalOverlay.allowShiftCollect(screen)) {
            return;
        }
        original.call(screen, slot, slotId, button, input);
    }
}

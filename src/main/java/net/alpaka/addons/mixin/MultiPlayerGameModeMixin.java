package net.alpaka.addons.mixin;

import net.alpaka.addons.features.dungeon.DungeonSolvers;
import net.alpaka.addons.features.sound.LocalAttackTracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Notes the entity the local player attacks, so a death can later be credited to this client; and
 * the blocks and entities the player right-clicks, for the dungeon solvers (a Water Board lever
 * flip, an Arrow Align frame turned).
 *
 * These are read-only observations of actions the player has already made - the calls are not
 * altered, delayed or synthesised, no result is changed, and nothing extra is sent to the server.
 */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void onAttack(Player player, Entity target, CallbackInfo ci) {
        LocalAttackTracker.noteAttack(target.getId());
    }

    /** A right-click with an item, noted the same read-only way. */
    @Inject(method = "useItem", at = @At("HEAD"))
    private void alpaka$onUseItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        LocalAttackTracker.noteUse();
    }

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void alpaka$onUseItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                    CallbackInfoReturnable<InteractionResult> cir) {
        LocalAttackTracker.noteUse();
    }

    /**
     * After a right-click on a block. Only the main hand, and only when the client went through with
     * it: when the main hand consumes the click, vanilla never tries the off hand, so a lever flip is
     * seen exactly once.
     */
    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void alpaka$afterUseItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
                                       CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult result = cir.getReturnValue();
        if (hand == InteractionHand.MAIN_HAND && result != null && result.consumesAction()) {
            DungeonSolvers.onOwnBlockUse(hit.getBlockPos(), hand);
        }
    }

    /** After a right-click on an entity; vanilla has sent it to the server whatever the result. */
    @Inject(method = "interact", at = @At("RETURN"))
    private void alpaka$afterInteract(Player player, Entity target, EntityHitResult hit, InteractionHand hand,
                                      CallbackInfoReturnable<InteractionResult> cir) {
        if (hand == InteractionHand.MAIN_HAND) DungeonSolvers.onOwnEntityUse(target, hand);
    }
}

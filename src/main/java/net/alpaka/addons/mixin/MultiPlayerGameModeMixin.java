package net.alpaka.addons.mixin;

import net.alpaka.addons.features.sound.LocalAttackTracker;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Notes the entity the local player attacks, so a death can later be credited to this client.
 *
 * This is a read-only observation of an attack the player has already made - the call is not
 * altered, delayed or synthesised, and nothing extra is sent to the server.
 */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void onAttack(Player player, Entity target, CallbackInfo ci) {
        LocalAttackTracker.noteAttack(target.getId());
    }

    /** A right-click with an item, noted the same read-only way. */
    @Inject(method = "useItem", at = @At("HEAD"))
    private void alpaka$onUseItem(Player player, net.minecraft.world.InteractionHand hand,
                                  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir) {
        LocalAttackTracker.noteUse();
    }

    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void alpaka$onUseItemOn(net.minecraft.client.player.LocalPlayer player, net.minecraft.world.InteractionHand hand,
                                    net.minecraft.world.phys.BlockHitResult hit,
                                    org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir) {
        LocalAttackTracker.noteUse();
    }
}

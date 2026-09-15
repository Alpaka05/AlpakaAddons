package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.healthbar.WideHealthBarFeature;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The wide health bar: rows of twenty hearts where vanilla draws ten, no hunger bar, no armor bar,
 * and the air bubbles moved along so they still sit against the hearts.
 *
 * Every hook stands down while the player rides something with hearts of its own, because vanilla
 * draws those in the hunger bar's place - the very space the long row takes over - and the
 * vanilla layout is the one that fits then.
 */
@Mixin(Gui.class)
public abstract class WideHealthBarMixin {
    @Shadow @Final private RandomSource random;
    @Shadow private int displayHealth;
    @Shadow protected abstract LivingEntity getPlayerVehicleWithHealth();
    @Shadow protected abstract int getVehicleMaxHearts(LivingEntity vehicle);

    @Unique
    private boolean alpaka$wideBar() {
        return AlpakaConfig.instance.wideHealthBarEnabled
                && this.getVehicleMaxHearts(this.getPlayerVehicleWithHealth()) == 0;
    }

    @Inject(method = "extractHearts", at = @At("HEAD"), cancellable = true)
    private void alpaka$heartsInRowsOfTwenty(GuiGraphicsExtractor graphics, Player player, int xLeft, int yLineBase,
                                             int healthRowHeight, int heartOffsetIndex, float maxHealth,
                                             int currentHealth, int oldHealth, int absorption, boolean blink,
                                             CallbackInfo ci) {
        if (alpaka$wideBar()) {
            WideHealthBarFeature.renderHearts(graphics, player, xLeft, yLineBase, heartOffsetIndex, maxHealth,
                    currentHealth, oldHealth, absorption, blink, this.random);
            ci.cancel();
        }
    }

    /** No armor row either: SkyBlock shows its defence in the action bar, so the icons only took space. */
    @WrapOperation(
            method = "extractPlayerHealth",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;extractArmor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/entity/player/Player;IIII)V")
    )
    private void alpaka$noArmorBar(GuiGraphicsExtractor graphics, Player player, int yLineBase, int numHealthRows,
                                              int healthRowHeight, int xLeft, Operation<Void> original) {
        if (!alpaka$wideBar()) {
            original.call(graphics, player, yLineBase, numHealthRows, healthRowHeight, xLeft);
        }
    }

    @Inject(method = "extractFood", at = @At("HEAD"), cancellable = true)
    private void alpaka$noHungerBar(GuiGraphicsExtractor graphics, Player player, int yLineBase, int xRight, CallbackInfo ci) {
        if (alpaka$wideBar()) {
            ci.cancel();
        }
    }

    /**
     * Air bubbles go above the top heart row, on the right, in from the hotbar edge like the hearts.
     * Vanilla raises the line it is handed by one row for a player on foot, which is why the
     * target here is two rows above the top heart row.
     */
    @WrapOperation(
            method = "extractPlayerHealth",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;extractAirBubbles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/entity/player/Player;III)V")
    )
    private void alpaka$bubblesAboveTheWideRows(Gui gui, GuiGraphicsExtractor graphics, Player player, int vehicleHearts,
                                                int yLineAir, int xRight, Operation<Void> original) {
        if (vehicleHearts == 0 && AlpakaConfig.instance.wideHealthBarEnabled) {
            // Vanilla arrives here with yLineBase minus one row for the hearts and one for the hunger bar.
            int yLineBase = yLineAir + 20;
            int rows = WideHealthBarFeature.rows(player, this.displayHealth);
            int topRow = yLineBase - (rows - 1) * WideHealthBarFeature.rowHeight(rows);
            yLineAir = topRow - 20;
            xRight = WideHealthBarFeature.xEnd(xRight);
        }
        original.call(gui, graphics, player, vehicleHearts, yLineAir, xRight);
    }
}

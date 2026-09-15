package net.alpaka.addons.features.healthbar;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * One long row of hearts in place of two short ones and the hunger bar.
 *
 * SkyBlock has no hunger, yet vanilla keeps the hunger bar on the right and stacks the player's
 * forty health points into two rows of ten hearts on the left, with absorption from effects on a
 * third row above. Here a row holds twenty hearts, so the second row moves into the hunger bar's
 * space and the absorption hearts drop a row to sit directly on top of the health. The row is set
 * in from the hotbar's edges to stay centred over it, and the armor and air rows follow it.
 *
 * The hearts themselves are vanilla's: damage blink, regeneration bounce, low-health shake, the
 * poison, wither and frozen tints and the hardcore variants all behave exactly as before, and a
 * resource pack that recolours the vanilla hearts recolours these too.
 */
public final class WideHealthBarFeature {
    /** The ten hearts vanilla draws on the left plus the ten that take the hunger bar's place. */
    public static final int HEARTS_PER_ROW = 20;
    private static final int HEART_SIZE = 9;
    private static final int HEART_STEP = 8;
    private static final int ROW_WIDTH = (HEARTS_PER_ROW - 1) * HEART_STEP + HEART_SIZE;
    /** The hotbar is 182 wide and the row 161, so ten pixels on either side keep it centred. */
    private static final int INSET = (182 - ROW_WIDTH) / 2;

    private WideHealthBarFeature() {
    }

    /** Left edge of the row, given the left edge vanilla would have used for its hearts. */
    public static int xStart(int xLeft) {
        return xLeft + INSET;
    }

    /** Right edge of the row, given the right edge vanilla would have used for the hunger bar. */
    public static int xEnd(int xRight) {
        return xRight - INSET;
    }

    /**
     * How many rows of twenty the hearts take. Mirrors vanilla's own count, including the way the
     * max health is widened to whatever the current or displayed health happens to be.
     */
    public static int rows(Player player, int displayHealth) {
        int currentHealth = Mth.ceil(player.getHealth());
        float maxHealth = Math.max((float) player.getAttributeValue(Attributes.MAX_HEALTH), Math.max(displayHealth, currentHealth));
        return rows(maxHealth, Mth.ceil(player.getAbsorptionAmount()));
    }

    private static int rows(float maxHealth, int absorption) {
        return Mth.ceil((maxHealth + absorption) / 2.0F / HEARTS_PER_ROW);
    }

    /** Vanilla's row pitch: ten pixels, squeezed once a player somehow needs more than two rows. */
    public static int rowHeight(int rows) {
        return Math.max(10 - (rows - 2), 3);
    }

    /**
     * Draws the hearts in rows of twenty. Takes exactly what vanilla's own heart pass takes, so the
     * mixin swaps one for the other; the logic is vanilla's with the row length changed and the
     * row centred.
     */
    public static void renderHearts(GuiGraphicsExtractor graphics, Player player, int xLeft, int yLineBase,
                                    int heartOffsetIndex, float maxHealth, int currentHealth, int oldHealth,
                                    int absorption, boolean blink, RandomSource random) {
        HeartStyle style = HeartStyle.forPlayer(player);
        HeartStyle absorbing = style == HeartStyle.WITHERED ? style : HeartStyle.ABSORBING;
        boolean hardcore = player.level().getLevelData().isHardcore();
        int healthContainers = Mth.ceil(maxHealth / 2.0);
        int absorptionContainers = Mth.ceil(absorption / 2.0);
        int maxHealthHalves = healthContainers * 2;
        int rowHeight = rowHeight(rows(maxHealth, absorption));
        int xStart = xStart(xLeft);

        for (int index = healthContainers + absorptionContainers - 1; index >= 0; index--) {
            int x = xStart + (index % HEARTS_PER_ROW) * HEART_STEP;
            int y = yLineBase - (index / HEARTS_PER_ROW) * rowHeight;
            if (currentHealth + absorption <= 4) {
                y += random.nextInt(2);
            }
            if (index < healthContainers && index == heartOffsetIndex) {
                y -= 2;
            }

            blit(graphics, HeartStyle.CONTAINER.sprite(hardcore, false, blink), x, y);
            int halves = index * 2;
            if (index >= healthContainers) {
                int absorptionHalves = halves - maxHealthHalves;
                if (absorptionHalves < absorption) {
                    blit(graphics, absorbing.sprite(hardcore, absorptionHalves + 1 == absorption, false), x, y);
                }
            }
            if (blink && halves < oldHealth) {
                blit(graphics, style.sprite(hardcore, halves + 1 == oldHealth, true), x, y);
            }
            if (halves < currentHealth) {
                blit(graphics, style.sprite(hardcore, halves + 1 == currentHealth, false), x, y);
            }
        }
    }

    private static void blit(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, HEART_SIZE, HEART_SIZE);
    }

    /** Vanilla's heart sprites by name; its own enum for them is private, so this mirrors it. */
    private enum HeartStyle {
        CONTAINER("container", "container", "container_hardcore", "container_hardcore"),
        NORMAL("full", "half", "hardcore_full", "hardcore_half"),
        POISONED("poisoned_full", "poisoned_half", "poisoned_hardcore_full", "poisoned_hardcore_half"),
        WITHERED("withered_full", "withered_half", "withered_hardcore_full", "withered_hardcore_half"),
        ABSORBING("absorbing_full", "absorbing_half", "absorbing_hardcore_full", "absorbing_hardcore_half"),
        FROZEN("frozen_full", "frozen_half", "frozen_hardcore_full", "frozen_hardcore_half");

        /** Indexed by hardcore, half and blinking; see {@link #sprite}. */
        private final Identifier[] sprites = new Identifier[8];

        HeartStyle(String full, String half, String hardcoreFull, String hardcoreHalf) {
            String[] names = {full, half, hardcoreFull, hardcoreHalf};
            for (int i = 0; i < names.length; i++) {
                sprites[i * 2] = Identifier.withDefaultNamespace("hud/heart/" + names[i]);
                sprites[i * 2 + 1] = Identifier.withDefaultNamespace("hud/heart/" + names[i] + "_blinking");
            }
        }

        Identifier sprite(boolean hardcore, boolean half, boolean blinking) {
            return sprites[((hardcore ? 2 : 0) + (half ? 1 : 0)) * 2 + (blinking ? 1 : 0)];
        }

        static HeartStyle forPlayer(Player player) {
            if (player.hasEffect(MobEffects.POISON)) return POISONED;
            if (player.hasEffect(MobEffects.WITHER)) return WITHERED;
            if (player.isFullyFrozen()) return FROZEN;
            return NORMAL;
        }
    }
}

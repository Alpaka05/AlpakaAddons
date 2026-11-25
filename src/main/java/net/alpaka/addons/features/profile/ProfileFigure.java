package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientMannequin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * The player as a figure on the overview, as profile viewers show them: their skin, in the armor
 * they wear, turning to follow the mouse.
 *
 * It is a mannequin that never joins the world: made on the client alone, dressed in the armor of
 * the profile and drawn the way the inventory screen draws the player. Nothing of it reaches the
 * server or the world; it only lives as long as the dashboard that holds it.
 */
final class ProfileFigure {
    /** Vanilla's inventory preview: how far the model turns towards the mouse, and how soon. */
    private static final float LOOK_DIVISOR = 40.0f;
    private static final float LOOK_STRENGTH = 20.0f;
    private static final float Y_OFFSET = 0.0625f;
    /** How tall the figure counts as, in blocks: a standing player's box. */
    private static final float FIGURE_HEIGHT = 1.8f;
    /** Armor pieces by the armor inventory's slots, as Hypixel numbers them: boots first. */
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

    private Figure figure;
    private String profileFor;
    /** Whether drawing it went wrong once already, so the log says so once. */
    private boolean failed;

    /**
     * Draws the figure in the box, with its middle at the box's middle, at {@code scale} pixels to
     * a block. False when it can't be drawn: outside a world there is nothing to make it in, and
     * items have no looks yet there either.
     */
    boolean draw(GuiGraphicsExtractor graphics, String uuid, JsonElement armor, int x0, int y0, int x1, int y1,
                 int scale, float lookX, float lookY) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return false;
        if (figure == null || figure.level() != level) {
            figure = new Figure(level);
            profileFor = null;
        }
        if (uuid != null && !uuid.equals(profileFor)) {
            profileFor = uuid;
            UUID id = uuid(uuid);
            // By UUID, so the game asks Mojang for the whole skin: the hash alone doesn't say
            // whether the arms are slim.
            figure.profile = id == null ? null : ResolvableProfile.createUnresolved(id);
        }
        dress(armor);
        try {
            submit(graphics, x0, y0, x1, y1, scale, lookX, lookY);
        } catch (RuntimeException e) {
            // A render state the game can't draw must not take the dashboard down with it.
            if (!failed) net.alpaka.addons.AlpakaAddons.LOGGER.warn("Profile Dashboard: the player figure couldn't be drawn", e);
            failed = true;
            return false;
        }
        return true;
    }

    /** Puts on what the armor inventory holds; a piece whose look is still loading comes later. */
    private void dress(JsonElement armor) {
        JsonObject items = armor == null ? null : Json.obj(armor, "items");
        for (int i = 0; i < ARMOR.length; i++) {
            JsonElement item = items == null ? null : items.get(String.valueOf(i));
            ItemStack stack = InventoryItems.present(item) ? InventoryItems.stack(item) : ItemStack.EMPTY;
            // The built stacks stay the same objects, so this only changes when a look arrives.
            if (figure.getItemBySlot(ARMOR[i]) != stack) figure.setItemSlot(ARMOR[i], stack);
        }
    }

    /** As the inventory screen draws the player, without the name, shadow or fire. */
    private void submit(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int scale, float lookX, float lookY) {
        float yaw = (float) Math.atan(((x0 + x1) / 2.0f - lookX) / LOOK_DIVISOR);
        float pitch = (float) Math.atan(((y0 + y1) / 2.0f - lookY) / LOOK_DIVISOR);
        // New every time: the GUI keeps these by reference until the frame is drawn.
        Quaternionf pitchRot = new Quaternionf().rotateX(pitch * LOOK_STRENGTH * ((float) Math.PI / 180.0f));
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).mul(pitchRot);

        EntityRenderState state = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(figure).createRenderState(figure, 1.0f);
        state.shadowPieces.clear();
        state.outlineColor = EntityRenderState.NO_OUTLINE;
        state.displayFireAnimation = false;
        state.nameTag = null;
        state.scoreText = null;
        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = 180.0f + yaw * LOOK_STRENGTH;
            living.yRot = yaw * LOOK_STRENGTH;
            living.xRot = -pitch * LOOK_STRENGTH;
            living.boundingBoxWidth /= living.scale;
            living.boundingBoxHeight /= living.scale;
            living.scale = 1.0f;
        }
        graphics.entity(state, scale, new Vector3f(0.0f, state.boundingBoxHeight / 2.0f + Y_OFFSET, 0.0f), rotation, pitchRot,
                x0, y0, x1, y1);
    }

    /**
     * The armor piece drawn at this point, as its slot in the armor inventory ("3" the helmet down
     * to "0" the boots), or null for none; {@code dx}/{@code dy} from the middle of the figure's
     * box, at {@code scale} pixels to a block. The figure stands with its middle there, its feet
     * half its height and the offset below, and its parts where the player model has them - the
     * legs the lowest twelve of its 32 pixels, the body and arms the next twelve, the head the
     * top eight - at the 15/16 a player is drawn at. It turns towards the mouse, so the widths
     * are a little generous rather than exact.
     */
    static String pieceAt(float dx, float dy, int scale) {
        if (scale <= 0) return null;
        float feet = (FIGURE_HEIGHT / 2 + Y_OFFSET) * scale;
        float up = (feet - dy) / scale;
        float side = Math.abs(dx) / scale;
        if (up >= 1.38f && up <= 2.0f && side <= 0.34f) return "3";
        if (up >= 0.68f && up < 1.38f && side <= 0.5f) return "2";
        if (up >= 0.24f && up < 0.68f && side <= 0.28f) return "1";
        if (up >= -0.03f && up < 0.24f && side <= 0.28f) return "0";
        return null;
    }

    /** A UUID with or without its dashes; null for anything else. */
    static UUID uuid(String text) {
        String hex = text.replace("-", "");
        if (hex.length() != 32) return null;
        try {
            return new UUID(Long.parseUnsignedLong(hex.substring(0, 16), 16), Long.parseUnsignedLong(hex.substring(16), 16));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The mannequin, wearing the skin of the profile it was given. It is never ticked, so the
     * skin is read from the game's skin cache each time it is drawn, which hands out the default
     * skin until the real one has loaded.
     */
    private static final class Figure extends ClientMannequin {
        private static final int FIGURE_ID = -0x414C50;
        private ResolvableProfile profile;

        Figure(ClientLevel level) {
            super(level, Minecraft.getInstance().playerSkinRenderCache());
            // An id the world never hands out: the renderer reads one (as the seed of item looks),
            // but this entity is never added to the world.
            setId(FIGURE_ID);
            setSilent(true);
        }

        @Override
        public PlayerSkin getSkin() {
            return profile == null ? super.getSkin() : Minecraft.getInstance().playerSkinRenderCache().getOrDefault(profile).playerSkin();
        }
    }
}

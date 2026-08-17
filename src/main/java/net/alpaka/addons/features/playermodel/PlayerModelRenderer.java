package net.alpaka.addons.features.playermodel;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class PlayerModelRenderer {
    private static float modelAlpha = 0.0f;
    private static long lastTime = System.currentTimeMillis();

    public static boolean shouldShowModel(LocalPlayer player) {
        if (!AlpakaConfig.instance.playerModelEnabled) return false;

        Minecraft mc = Minecraft.getInstance();
        // Hide in normal menus unless showInGuis is enabled (always allow in chat screen)
        if (mc.screen != null && !(mc.screen instanceof ChatScreen) && !AlpakaConfig.instance.playerModelShowInGuis) {
            return false;
        }

        if (AlpakaConfig.instance.playerModelOnlyActions) {
            // Check movements/actions
            boolean isMoving = player.getDeltaMovement().x * player.getDeltaMovement().x
                    + player.getDeltaMovement().z * player.getDeltaMovement().z > 0.002;
            boolean isJumpingOrFalling = !player.onGround();
            boolean isSneaking = player.isCrouching();
            boolean isSprinting = player.isSprinting();
            boolean isSwimming = player.isSwimming();
            boolean isFallFlying = player.isFallFlying();
            boolean isRiding = player.isPassenger();
            boolean isSwinging = player.swingTime > 0;
            return isMoving || isJumpingOrFalling || isSneaking || isSprinting || isSwimming || isFallFlying || isRiding || isSwinging;
        }

        return true;
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;

        LocalPlayer player = mc.player;
        if (player == null) return;

        // Calculate delta time
        long now = System.currentTimeMillis();
        float dt = (now - lastTime) / 1000.0f;
        lastTime = now;

        boolean targetVisible = shouldShowModel(player);

        // Update animation alpha
        if (targetVisible) {
            modelAlpha = Math.min(1.0f, modelAlpha + dt * 5.0f);
        } else {
            modelAlpha = Math.max(0.0f, modelAlpha - dt * 5.0f);
        }

        if (modelAlpha <= 0.001f) {
            return;
        }

        // Retrieve screen dimensions
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // Retrieve configured coordinates and scale
        int targetX = AlpakaConfig.instance.playerModelX;
        int targetY = AlpakaConfig.instance.playerModelY;
        int scale = AlpakaConfig.instance.playerModelScale;

        // Determine starting X for slide animation:
        // If target X is on the left half of the screen, slide in from the left (-scale).
        // Otherwise, slide in from the right (screenWidth + scale).
        int startX = (targetX < screenWidth / 2) ? -scale : (screenWidth + scale);

        // Apply smooth ease-out quadratic interpolation
        float slideProgress = modelAlpha; // Linear
        float easeProgress = 1.0f - (1.0f - slideProgress) * (1.0f - slideProgress);

        int currentX = (int) (startX + (targetX - startX) * easeProgress);

        renderPlayerModel(graphics, currentX, targetY, scale, player);
    }

    public static void renderPlayerModel(GuiGraphicsExtractor graphics, int x, int y, int scale, LocalPlayer player) {
        // Bounding box for the player rendering scissor test
        int x0 = x - (int)(scale * 0.8f);
        int x1 = x + (int)(scale * 0.8f);
        int y0 = y - (int)(scale * 2.4f);
        int y1 = y + (int)(scale * 0.2f);

        // Calculate yaw and pitch to face slightly forward-right
        float mouseX = x - 20;
        float mouseY = y - (scale * 1.1f) - 15;

        // Save original equipment if hiding armor on model
        boolean hideArmor = AlpakaConfig.instance.playerModelHideArmor;
        ItemStack savedHead = hideArmor ? player.getItemBySlot(EquipmentSlot.HEAD) : ItemStack.EMPTY;
        ItemStack savedChest = hideArmor ? player.getItemBySlot(EquipmentSlot.CHEST) : ItemStack.EMPTY;
        ItemStack savedLegs = hideArmor ? player.getItemBySlot(EquipmentSlot.LEGS) : ItemStack.EMPTY;
        ItemStack savedFeet = hideArmor ? player.getItemBySlot(EquipmentSlot.FEET) : ItemStack.EMPTY;
        ItemStack savedBody = hideArmor ? player.getItemBySlot(EquipmentSlot.BODY) : ItemStack.EMPTY;

        if (hideArmor) {
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            player.setItemSlot(EquipmentSlot.BODY, ItemStack.EMPTY);
        }

        // Save fire state - player model HUD avatar never burns even if player is on fire
        int savedFireTicks = player.getRemainingFireTicks();
        player.setRemainingFireTicks(0);
        player.setSharedFlagOnFire(false);

        try {
            renderAvatar(graphics, x0, y0, x1, y1, scale, 0.0625f, mouseX, mouseY, player);
        } catch (Exception e) {
            // Safety catch to prevent game crash if rendering state is invalid
        } finally {
            player.setRemainingFireTicks(savedFireTicks);
            if (savedFireTicks > 0) {
                player.setSharedFlagOnFire(true);
            }
            if (hideArmor) {
                player.setItemSlot(EquipmentSlot.HEAD, savedHead);
                player.setItemSlot(EquipmentSlot.CHEST, savedChest);
                player.setItemSlot(EquipmentSlot.LEGS, savedLegs);
                player.setItemSlot(EquipmentSlot.FEET, savedFeet);
                player.setItemSlot(EquipmentSlot.BODY, savedBody);
            }
        }
    }

    /**
     * Reimplementation of vanilla's InventoryScreen.extractEntityInInventoryFollowsMouse
     * that additionally lets us zero out the extracted render state's pose fields when
     * "disable movement" is enabled. The render state is a fresh, throwaway object created
     * fresh for this call, so mutating it never touches the real entity's simulation state.
     */
    private static void renderAvatar(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int size, float yOffset, float mouseX, float mouseY, LivingEntity entity) {
        float centerX = (x1 + x2) / 2.0f;
        float centerY = (y1 + y2) / 2.0f;
        float f = (float) Math.atan((centerX - mouseX) / 40.0f);
        float g = (float) Math.atan((centerY - mouseY) / 40.0f);

        Quaternionf bodyFlip = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf pitchRot = new Quaternionf().rotateX(g * 20.0f * ((float) Math.PI / 180.0f));
        bodyFlip.mul(pitchRot);

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        EntityRenderer renderer = dispatcher.getRenderer(entity);
        EntityRenderState state = renderer.createRenderState(entity, 1.0f);
        state.shadowPieces.clear();
        state.outlineColor = EntityRenderState.NO_OUTLINE;

        if (state instanceof LivingEntityRenderState livingState) {
            if (AlpakaConfig.instance.playerModelDisableMovement) {
                freezeMovementPose(livingState, entity);
            }

            livingState.bodyRot = 180.0f + f * 20.0f;
            livingState.yRot = f * 20.0f;
            livingState.xRot = livingState.pose != Pose.FALL_FLYING ? -g * 20.0f : 0.0f;
            livingState.boundingBoxWidth /= livingState.scale;
            livingState.boundingBoxHeight /= livingState.scale;
            livingState.scale = 1.0f;
        }

        Vector3f translation = new Vector3f(0.0f, state.boundingBoxHeight / 2.0f + yOffset, 0.0f);
        graphics.entity(state, (float) size, translation, bodyFlip, pitchRot, x1, y1, x2, y2);
    }

    /**
     * Resets every pose/orientation field driven by the player's current movement so the
     * HUD avatar always shows a plain standing pose - not just while swimming, but also
     * while sneaking, riding a minecart/boat/horse, gliding, upside-down, etc.
     */
    private static void freezeMovementPose(LivingEntityRenderState state, LivingEntity entity) {
        state.pose = Pose.STANDING;
        state.walkAnimationPos = 0.0f;
        state.walkAnimationSpeed = 0.0f;
        state.isInWater = false;
        state.isUpsideDown = false;
        state.isAutoSpinAttack = false;

        // The avatar's vertical placement is derived from boundingBoxHeight (see renderAvatar),
        // and the live hitbox shrinks with the pose - 1.5 crouching, 0.6 swimming, versus 1.8
        // standing - which would slide the avatar up and down. Pin it to the standing hitbox.
        // getDimensions() already applies the entity's scale, matching how the renderer fills
        // these fields, so the caller's later divide-by-scale still yields the base size.
        EntityDimensions standing = entity.getDimensions(Pose.STANDING);
        state.boundingBoxWidth = standing.width();
        state.boundingBoxHeight = standing.height();
        state.eyeHeight = standing.eyeHeight();

        if (state instanceof HumanoidRenderState humanoidState) {
            humanoidState.swimAmount = 0.0f;
            humanoidState.isCrouching = false;
            humanoidState.isFallFlying = false;
            humanoidState.isVisuallySwimming = false;
            humanoidState.isPassenger = false;
            humanoidState.elytraRotX = 0.0f;
            humanoidState.elytraRotY = 0.0f;
            humanoidState.elytraRotZ = 0.0f;
        }

        if (state instanceof AvatarRenderState avatarState) {
            avatarState.fallFlyingTimeInTicks = 0.0f;
            avatarState.shouldApplyFlyingYRot = false;
            avatarState.flyingYRot = 0.0f;
        }
    }
}

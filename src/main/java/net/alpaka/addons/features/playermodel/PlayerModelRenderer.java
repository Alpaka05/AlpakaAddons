package net.alpaka.addons.features.playermodel;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class PlayerModelRenderer {
    private static float modelAlpha = 0.0f;
    private static long lastTime = System.currentTimeMillis();

    private static java.lang.reflect.Field speedOldField = null;
    private static java.lang.reflect.Field speedField = null;
    private static java.lang.reflect.Field positionField = null;

    static {
        try {
            int floatCount = 0;
            for (java.lang.reflect.Field f : net.minecraft.world.entity.WalkAnimationState.class.getDeclaredFields()) {
                if (f.getType() == float.class) {
                    f.setAccessible(true);
                    if (floatCount == 0) speedOldField = f;
                    else if (floatCount == 1) speedField = f;
                    else if (floatCount == 2) positionField = f;
                    floatCount++;
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }

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

        // Draw Player Model
        float savedSpeedOld = 0;
        float savedSpeed = 0;
        float savedPosition = 0;
        boolean reflectionSuccess = false;

        if (AlpakaConfig.instance.playerModelDisableMovement && speedOldField != null && speedField != null && positionField != null) {
            try {
                savedSpeedOld = speedOldField.getFloat(player.walkAnimation);
                savedSpeed = speedField.getFloat(player.walkAnimation);
                savedPosition = positionField.getFloat(player.walkAnimation);
                reflectionSuccess = true;
                player.walkAnimation.stop();
            } catch (Exception e) {
                // Ignore
            }
        }

        renderPlayerModel(graphics, currentX, targetY, scale, player);

        if (AlpakaConfig.instance.playerModelDisableMovement && reflectionSuccess) {
            try {
                speedOldField.setFloat(player.walkAnimation, savedSpeedOld);
                speedField.setFloat(player.walkAnimation, savedSpeed);
                positionField.setFloat(player.walkAnimation, savedPosition);
            } catch (Exception e) {
                // Ignore
            }
        }
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
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    graphics,
                    x0, y0,
                    x1, y1,
                    scale,
                    0.0625f, // offsetY
                    mouseX,
                    mouseY,
                    player
            );
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
}

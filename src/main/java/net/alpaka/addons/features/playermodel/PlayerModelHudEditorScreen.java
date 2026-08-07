package net.alpaka.addons.features.playermodel;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;

public class PlayerModelHudEditorScreen extends Screen {
    private final Screen parent;
    private boolean dragging = false;
    private double dragOffsetX = 0;
    private double dragOffsetY = 0;

    public PlayerModelHudEditorScreen(Screen parent) {
        super(Component.literal("HUD Editor"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerY = this.height - 40;

        // Reset Button
        this.addRenderableWidget(Button.builder(
                Component.literal("Reset"),
                button -> {
                    AlpakaConfig.instance.playerModelX = 40;
                    AlpakaConfig.instance.playerModelY = 85;
                    AlpakaConfig.instance.playerModelScale = 30;
                    AlpakaConfig.save();
                }
        )
        .bounds(this.width / 2 - 105, centerY, 100, 20)
        .build());

        // Save & Exit Button
        this.addRenderableWidget(Button.builder(
                Component.literal("Save"),
                button -> this.onClose()
        )
        .bounds(this.width / 2 + 5, centerY, 100, 20)
        .build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Draw the standard game background under the editor screen, but darker
        graphics.fill(0, 0, this.width, this.height, 0x88000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // 1. Draw Background
        this.extractBackground(graphics, mouseX, mouseY, partialTick);

        // 2. Render options/buttons
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        // 3. Draw instructions
        graphics.centeredText(this.font, Component.literal("HUD Editor - Drag model to position, Scroll to resize"), this.width / 2, 20, 0xFFFFFFFF);
        graphics.centeredText(this.font, Component.literal("X: " + AlpakaConfig.instance.playerModelX + " | Y: " + AlpakaConfig.instance.playerModelY + " | Scale: " + AlpakaConfig.instance.playerModelScale + "x"), this.width / 2, 35, 0xFFA0A0A0);

        // 4. Render player model
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            int scale = AlpakaConfig.instance.playerModelScale;
            int x = AlpakaConfig.instance.playerModelX;
            int y = AlpakaConfig.instance.playerModelY;

            // Render player model
            PlayerModelRenderer.renderPlayerModel(graphics, x, y, scale, mc.player);

            // Bounding box
            int x0 = x - (int)(scale * 0.8f);
            int x1 = x + (int)(scale * 0.8f);
            int y0 = y - (int)(scale * 2.4f);
            int y1 = y + (int)(scale * 0.2f);

            // Draw bounding box guides
            int guideColor = isMouseOverModel(mouseX, mouseY, x0, y0, x1, y1) ? 0xFF00FF00 : 0xFFFFFFFF;
            drawOutline(graphics, x0, y0, x1, y1, guideColor);
        }
    }

    private boolean isMouseOverModel(double mouseX, double mouseY, int x0, int y0, int x1, int y1) {
        return mouseX >= x0 && mouseX <= x1 && mouseY >= y0 && mouseY <= y1;
    }

    private void drawOutline(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        graphics.fill(x0, y0, x1, y0 + 1, color); // top
        graphics.fill(x0, y1 - 1, x1, y1, color); // bottom
        graphics.fill(x0, y0, x0 + 1, y1, color); // left
        graphics.fill(x1 - 1, y0, x1, y1, color); // right
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            int scale = AlpakaConfig.instance.playerModelScale;
            int x = AlpakaConfig.instance.playerModelX;
            int y = AlpakaConfig.instance.playerModelY;

            int x0 = x - (int)(scale * 0.8f);
            int x1 = x + (int)(scale * 0.8f);
            int y0 = y - (int)(scale * 2.4f);
            int y1 = y + (int)(scale * 0.2f);

            if (isMouseOverModel(event.x(), event.y(), x0, y0, x1, y1)) {
                this.dragging = true;
                this.dragOffsetX = event.x() - x;
                this.dragOffsetY = event.y() - y;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            this.dragging = false;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (this.dragging) {
            AlpakaConfig.instance.playerModelX = (int) (event.x() - this.dragOffsetX);
            AlpakaConfig.instance.playerModelY = (int) (event.y() - this.dragOffsetY);
            AlpakaConfig.save();
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            AlpakaConfig.instance.playerModelScale = Math.max(10, Math.min(200, AlpakaConfig.instance.playerModelScale + (int) (scrollY * 2)));
            AlpakaConfig.save();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) { // ESCAPE
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}

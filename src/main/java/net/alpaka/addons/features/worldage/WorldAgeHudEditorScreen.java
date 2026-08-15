package net.alpaka.addons.features.worldage;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class WorldAgeHudEditorScreen extends Screen {
    private final Screen parent;
    private boolean dragging = false;
    private double dragOffsetX = 0;
    private double dragOffsetY = 0;

    public WorldAgeHudEditorScreen(Screen parent) {
        super(Component.literal("World Age HUD Editor"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerY = this.height - 40;

        // Reset Button
        this.addRenderableWidget(Button.builder(
                Component.literal("Reset"),
                button -> {
                    AlpakaConfig.instance.worldAgeHudX = 10;
                    AlpakaConfig.instance.worldAgeHudY = 10;
                    AlpakaConfig.instance.worldAgeHudScale = 1.0f;
                    AlpakaConfig.save();
                }
        )
        .bounds(this.width / 2 - 105, centerY, 100, 20)
        .build());

        // Save Button
        this.addRenderableWidget(Button.builder(
                Component.literal("Save"),
                button -> this.onClose()
        )
        .bounds(this.width / 2 + 5, centerY, 100, 20)
        .build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x88000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.extractBackground(graphics, mouseX, mouseY, partialTick);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        // Draw instructions
        graphics.centeredText(this.font, Component.literal("World Age HUD Editor - Drag to position, Scroll to resize"), this.width / 2, 20, 0xFFFFFFFF);
        graphics.centeredText(this.font, Component.literal(String.format("X: %d | Y: %d | Scale: %.2fx", AlpakaConfig.instance.worldAgeHudX, AlpakaConfig.instance.worldAgeHudY, AlpakaConfig.instance.worldAgeHudScale)), this.width / 2, 35, 0xFFA0A0A0);

        int x = AlpakaConfig.instance.worldAgeHudX;
        int y = AlpakaConfig.instance.worldAgeHudY;
        float scale = AlpakaConfig.instance.worldAgeHudScale;
        long day = WorldAgeHudRenderer.getWorldDay();

        WorldAgeHudRenderer.renderHud(graphics, x, y, scale);

        int w = font != null ? WorldAgeHudRenderer.getWidth(font, day, scale) : 40;
        int h = font != null ? WorldAgeHudRenderer.getHeight(font, scale) : 10;

        int x0 = x - 2;
        int y0 = y - 2;
        int x1 = x + w + 2;
        int y1 = y + h + 2;

        int guideColor = isMouseOverBox(mouseX, mouseY, x0, y0, x1, y1) ? 0xFF00FF00 : 0xFFFFFFFF;
        drawOutline(graphics, x0, y0, x1, y1, guideColor);
    }

    private boolean isMouseOverBox(double mouseX, double mouseY, int x0, int y0, int x1, int y1) {
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
            Minecraft mc = Minecraft.getInstance();
            Font font = mc.font;

            int x = AlpakaConfig.instance.worldAgeHudX;
            int y = AlpakaConfig.instance.worldAgeHudY;
            float scale = AlpakaConfig.instance.worldAgeHudScale;
            long day = WorldAgeHudRenderer.getWorldDay();

            int w = font != null ? WorldAgeHudRenderer.getWidth(font, day, scale) : 40;
            int h = font != null ? WorldAgeHudRenderer.getHeight(font, scale) : 10;

            int x0 = x - 2;
            int y0 = y - 2;
            int x1 = x + w + 2;
            int y1 = y + h + 2;

            if (isMouseOverBox(event.x(), event.y(), x0, y0, x1, y1)) {
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
            AlpakaConfig.instance.worldAgeHudX = (int) (event.x() - this.dragOffsetX);
            AlpakaConfig.instance.worldAgeHudY = (int) (event.y() - this.dragOffsetY);
            AlpakaConfig.save();
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            float newScale = Math.max(0.5f, Math.min(3.0f, AlpakaConfig.instance.worldAgeHudScale + (float) (scrollY * 0.1f)));
            AlpakaConfig.instance.worldAgeHudScale = Math.round(newScale * 100.0f) / 100.0f;
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

package net.alpaka.addons.client;

import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.features.sound.CustomSoundFeature;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public class ColorPickerScreen extends Screen {
    private final Screen parent;
    private final Consumer<Integer> onSave;
    private int r, g, b, a;

    private int activeSlider = -1; // 0: R, 1: G, 2: B, 3: A

    // 10 Color Presets
    private static final int[] PRESETS = new int[] {
            0xFF00E5FF, // Cyan
            0xFF00E676, // Green
            0xFFFF5252, // Red
            0xFFFFEA00, // Yellow
            0xFF29B6F6, // Blue
            0xFFAB47BC, // Purple
            0xFFFF9800, // Orange
            0xFFFF4081, // Pink
            0xFFFFFFFF, // White
            0xFF181B24  // Dark
    };

    public ColorPickerScreen(Screen parent, String title, int initialColor, Consumer<Integer> onSave) {
        super(Component.literal(title));
        this.parent = parent;
        this.onSave = onSave;
        this.a = (initialColor >> 24) & 0xFF;
        this.r = (initialColor >> 16) & 0xFF;
        this.g = (initialColor >> 8) & 0xFF;
        this.b = initialColor & 0xFF;
    }

    private void playSound() {
        try {
            CustomSoundFeature.playButtonClickSound();
        } catch (Throwable ignored) {}
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Translucent dark backdrop (game visible behind color picker)
        graphics.fill(0, 0, this.width, this.height, 0x70000000);

        int winW = 460;
        int winH = 320;
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        // Panel frame
        ModernGuiUtils.drawRect(graphics, winX, winY, winW, winH, ModernGuiUtils.COLOR_PANEL_BG);
        ModernGuiUtils.drawOutline(graphics, winX, winY, winW, winH, ModernGuiUtils.COLOR_CARD_BORDER);

        // Header bar
        int headerH = 38;
        ModernGuiUtils.drawRect(graphics, winX, winY, winW, headerH, ModernGuiUtils.COLOR_SIDEBAR_BG);
        ModernGuiUtils.drawRect(graphics, winX, winY + headerH - 1, winW, 1, ModernGuiUtils.COLOR_ACCENT);

        graphics.text(this.font, this.title, winX + 16, winY + 13, ModernGuiUtils.COLOR_TEXT_PRIMARY);

        // Close '✕' button in header
        int closeX = winX + winW - 28;
        int closeY = winY + 9;
        boolean hoverClose = mouseX >= closeX && mouseX <= closeX + 18 && mouseY >= closeY && mouseY <= closeY + 18;
        graphics.text(this.font, Component.literal("✕"), closeX + 4, closeY + 3, hoverClose ? ModernGuiUtils.COLOR_ACCENT : ModernGuiUtils.COLOR_TEXT_MUTED);

        // LEFT COLUMN: Color Preview, HEX Code, Presets
        int prevX = winX + 20;
        int prevY = winY + 52;
        int prevW = 140;
        int prevH = 90;

        // Preview box alpha pattern + solid fill
        ModernGuiUtils.drawRect(graphics, prevX, prevY, prevW, prevH, 0xFF000000);
        int currentColor = (a << 24) | (r << 16) | (g << 8) | b;
        ModernGuiUtils.drawRect(graphics, prevX + 2, prevY + 2, prevW - 4, prevH - 4, currentColor);
        ModernGuiUtils.drawOutline(graphics, prevX, prevY, prevW, prevH, ModernGuiUtils.COLOR_CARD_BORDER);

        // HEX Display Box
        int hexY = prevY + prevH + 10;
        int hexH = 24;
        ModernGuiUtils.drawRect(graphics, prevX, hexY, prevW, hexH, ModernGuiUtils.COLOR_CARD_BG);
        ModernGuiUtils.drawOutline(graphics, prevX, hexY, prevW, hexH, ModernGuiUtils.COLOR_CARD_BORDER);

        String hexStr = String.format("#%02X%02X%02X%02X", a, r, g, b);
        int hexStrX = prevX + (prevW - this.font.width(hexStr)) / 2;
        graphics.text(this.font, Component.literal(hexStr), hexStrX, hexY + 7, ModernGuiUtils.COLOR_ACCENT);

        // Presets Header
        int presetY = hexY + hexH + 12;
        graphics.text(this.font, Component.literal("Presets:"), prevX, presetY, ModernGuiUtils.COLOR_TEXT_MUTED);

        // Presets Grid (5 cols x 2 rows)
        int swatchSize = 22;
        int swatchGap = 7;
        int gridStartX = prevX;
        int gridStartY = presetY + 14;

        for (int i = 0; i < PRESETS.length; i++) {
            int col = i % 5;
            int row = i / 5;
            int sx = gridStartX + col * (swatchSize + swatchGap);
            int sy = gridStartY + row * (swatchSize + swatchGap);

            boolean isHovered = mouseX >= sx && mouseX <= sx + swatchSize && mouseY >= sy && mouseY <= sy + swatchSize;
            int presetColor = PRESETS[i];

            ModernGuiUtils.drawRect(graphics, sx, sy, swatchSize, swatchSize, 0xFF000000);
            ModernGuiUtils.drawRect(graphics, sx + 1, sy + 1, swatchSize - 2, swatchSize - 2, presetColor);
            ModernGuiUtils.drawOutline(graphics, sx, sy, swatchSize, swatchSize, isHovered ? ModernGuiUtils.COLOR_ACCENT : ModernGuiUtils.COLOR_CARD_BORDER);
        }

        // RIGHT COLUMN: Sliders (R, G, B, A)
        int rightX = winX + 180;
        int rightW = winW - 200;
        int sliderY = winY + 52;
        int rowGap = 44;

        String[] sliderNames = new String[] {"Red", "Green", "Blue", "Alpha (Transparency)"};
        int[] sliderVals = new int[] {r, g, b, a};

        for (int i = 0; i < 4; i++) {
            int sy = sliderY + i * rowGap;
            String labelText = sliderNames[i] + ": " + sliderVals[i];
            graphics.text(this.font, Component.literal(labelText), rightX, sy, ModernGuiUtils.COLOR_TEXT_PRIMARY);

            int swY = sy + 14;
            int swH = 22;
            boolean isHovered = mouseX >= rightX && mouseX <= rightX + rightW && mouseY >= swY && mouseY <= swY + swH;
            double norm = sliderVals[i] / 255.0;

            ModernGuiUtils.drawModernSlider(graphics, this.font, rightX, swY, rightW, swH, norm, String.valueOf(sliderVals[i]), isHovered);
        }

        // BOTTOM ACTION BUTTONS
        int btnY = winY + winH - 38;
        int saveW = 110;
        int saveH = 26;
        int saveX = winX + winW - saveW - 16;

        int cancelW = 100;
        int cancelH = 26;
        int cancelX = saveX - cancelW - 10;

        boolean hoverSave = mouseX >= saveX && mouseX <= saveX + saveW && mouseY >= btnY && mouseY <= btnY + saveH;
        boolean hoverCancel = mouseX >= cancelX && mouseX <= cancelX + cancelW && mouseY >= btnY && mouseY <= btnY + cancelH;

        ModernGuiUtils.drawModernButton(graphics, this.font, cancelX, btnY, cancelW, cancelH, "Cancel", hoverCancel, false);
        ModernGuiUtils.drawModernButton(graphics, this.font, saveX, btnY, saveW, saveH, "Save ✓", hoverSave, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        int winW = 460;
        int winH = 320;
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        // Close '✕' button
        int closeX = winX + winW - 28;
        int closeY = winY + 9;
        if (mouseX >= closeX && mouseX <= closeX + 18 && mouseY >= closeY && mouseY <= closeY + 18) {
            playSound();
            this.onClose();
            return true;
        }

        // Presets grid click
        int prevX = winX + 20;
        int hexY = winY + 52 + 90 + 10;
        int presetY = hexY + 24 + 12;
        int swatchSize = 22;
        int swatchGap = 7;
        int gridStartX = prevX;
        int gridStartY = presetY + 14;

        for (int i = 0; i < PRESETS.length; i++) {
            int col = i % 5;
            int row = i / 5;
            int sx = gridStartX + col * (swatchSize + swatchGap);
            int sy = gridStartY + row * (swatchSize + swatchGap);

            if (mouseX >= sx && mouseX <= sx + swatchSize && mouseY >= sy && mouseY <= sy + swatchSize) {
                playSound();
                int color = PRESETS[i];
                this.a = (color >> 24) & 0xFF;
                this.r = (color >> 16) & 0xFF;
                this.g = (color >> 8) & 0xFF;
                this.b = color & 0xFF;
                return true;
            }
        }

        // Sliders click
        int rightX = winX + 180;
        int rightW = winW - 200;
        int sliderY = winY + 52;
        int rowGap = 44;

        for (int i = 0; i < 4; i++) {
            int swY = sliderY + i * rowGap + 14;
            int swH = 22;

            if (mouseX >= rightX && mouseX <= rightX + rightW && mouseY >= swY && mouseY <= swY + swH) {
                playSound();
                this.activeSlider = i;
                updateSliderVal(i, mouseX, rightX, rightW);
                return true;
            }
        }

        // Save & Cancel Buttons
        int btnY = winY + winH - 38;
        int saveW = 110;
        int saveH = 26;
        int saveX = winX + winW - saveW - 16;

        int cancelW = 100;
        int cancelH = 26;
        int cancelX = saveX - cancelW - 10;

        if (mouseX >= saveX && mouseX <= saveX + saveW && mouseY >= btnY && mouseY <= btnY + saveH) {
            playSound();
            int finalColor = (a << 24) | (r << 16) | (g << 8) | b;
            onSave.accept(finalColor);
            onClose();
            return true;
        }

        if (mouseX >= cancelX && mouseX <= cancelX + cancelW && mouseY >= btnY && mouseY <= btnY + cancelH) {
            playSound();
            onClose();
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (activeSlider >= 0) {
            int winW = 460;
            int winX = (this.width - winW) / 2;
            int rightX = winX + 180;
            int rightW = winW - 200;
            updateSliderVal(activeSlider, event.x(), rightX, rightW);
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            this.activeSlider = -1;
        }
        return super.mouseReleased(event);
    }

    private void updateSliderVal(int sliderIdx, double mouseX, int rightX, int rightW) {
        double norm = Math.max(0.0, Math.min(1.0, (mouseX - rightX) / (double) rightW));
        int val = (int) Math.round(norm * 255.0);
        switch (sliderIdx) {
            case 0 -> this.r = val;
            case 1 -> this.g = val;
            case 2 -> this.b = val;
            case 3 -> this.a = val;
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}


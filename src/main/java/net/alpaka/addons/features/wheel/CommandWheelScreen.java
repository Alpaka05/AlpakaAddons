package net.alpaka.addons.features.wheel;

import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

public class CommandWheelScreen extends Screen {
    private int selectedIndex = -1;

    public CommandWheelScreen() {
        super(Component.literal("Quick Command Menu"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (CommandWheelFeature.COMMAND_WHEEL_KEY != null && CommandWheelFeature.COMMAND_WHEEL_KEY.matches(event)) {
            executeSelectedCommandAndClose();
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() == 0 && selectedIndex >= 0) {
            executeSelectedCommandAndClose();
            return true;
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    private void executeSelectedCommandAndClose() {
        List<String> commands = AlpakaConfig.instance.commandWheelCommands;
        if (commands != null && selectedIndex >= 0 && selectedIndex < commands.size() && this.minecraft != null && this.minecraft.player != null) {
            String cmd = commands.get(selectedIndex);
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            this.minecraft.player.connection.sendCommand(cmd);
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        // Soft dark overlay
        graphics.fill(0, 0, this.width, this.height, 0x65000000);

        int cx = this.width / 2;
        int cy = this.height / 2;

        List<String> commands = AlpakaConfig.instance.commandWheelCommands;
        if (commands == null || commands.isEmpty()) {
            graphics.centeredText(this.font, Component.literal("No quick commands set! Add commands in Alpaka Config."), cx, cy, 0xFFFFFFFF);
            return;
        }

        int count = commands.size();

        // Small center dot / crosshair
        ModernGuiUtils.drawRect(graphics, cx - 2, cy - 2, 4, 4, ModernGuiUtils.COLOR_ACCENT);
        ModernGuiUtils.drawOutline(graphics, cx - 3, cy - 3, 6, 6, 0xFF0E1015);

        // Dynamic elliptical radius based on command count to prevent top & bottom horizontal card overlap
        double baseRadius = Math.max(85.0, 60.0 + count * 7.5);
        double rx = baseRadius * 1.45; // Wider horizontal radius prevents top/bottom card overlap
        double ry = baseRadius * 1.05;

        double sectorAngle = 2.0 * Math.PI / count;

        selectedIndex = -1;
        double bestDistSq = Double.MAX_VALUE;

        // Position & Hover Detection
        for (int i = 0; i < count; i++) {
            String cmd = commands.get(i);
            double angle = -Math.PI / 2.0 + i * sectorAngle;

            int boxW = Math.max(76, this.font.width(cmd) + 18);
            int boxH = 22;

            int boxCx = cx + (int) Math.round(Math.cos(angle) * rx);
            int boxCy = cy + (int) Math.round(Math.sin(angle) * ry);

            int bx = boxCx - boxW / 2;
            int by = boxCy - boxH / 2;

            boolean isDirectHover = mouseX >= bx && mouseX <= bx + boxW && mouseY >= by && mouseY <= by + boxH;

            if (isDirectHover) {
                selectedIndex = i;
                break;
            } else {
                double dSq = (mouseX - boxCx) * (mouseX - boxCx) + (mouseY - boxCy) * (mouseY - boxCy);
                if (dSq < bestDistSq && Math.sqrt(dSq) < 85.0) {
                    bestDistSq = dSq;
                    selectedIndex = i;
                }
            }
        }

        // Render Command Cards
        for (int i = 0; i < count; i++) {
            String cmd = commands.get(i);
            boolean isSelected = (i == selectedIndex);

            double angle = -Math.PI / 2.0 + i * sectorAngle;
            int boxW = Math.max(76, this.font.width(cmd) + 18);
            int boxH = 22;

            int boxCx = cx + (int) Math.round(Math.cos(angle) * rx);
            int boxCy = cy + (int) Math.round(Math.sin(angle) * ry);

            int bx = boxCx - boxW / 2;
            int by = boxCy - boxH / 2;

            int bg = isSelected ? ModernGuiUtils.COLOR_CARD_BG_HOVER : ModernGuiUtils.COLOR_CARD_BG;
            int border = isSelected ? ModernGuiUtils.COLOR_ACCENT : ModernGuiUtils.COLOR_CARD_BORDER;
            int textColor = isSelected ? ModernGuiUtils.COLOR_ACCENT : ModernGuiUtils.COLOR_TEXT_PRIMARY;

            // Hover pop scaling effect
            graphics.pose().pushMatrix();
            if (isSelected) {
                graphics.pose().scaleAround(1.08f, 1.08f, boxCx, boxCy);
            }

            ModernGuiUtils.drawRect(graphics, bx, by, boxW, boxH, bg);
            ModernGuiUtils.drawOutline(graphics, bx, by, boxW, boxH, border);

            int textX = bx + (boxW - this.font.width(cmd)) / 2;
            int textY = by + (boxH - 8) / 2;
            graphics.text(this.font, Component.literal(cmd), textX, textY, textColor);

            graphics.pose().popMatrix();
        }
    }
}

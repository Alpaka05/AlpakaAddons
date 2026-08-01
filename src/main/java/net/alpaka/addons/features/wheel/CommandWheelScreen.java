package net.alpaka.addons.features.wheel;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

public class CommandWheelScreen extends Screen {
    private int selectedSector = -1;

    public CommandWheelScreen() {
        super(Component.literal("Command Wheel"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (CommandWheelFeature.COMMAND_WHEEL_KEY.matches(event)) {
            executeSelectedCommandAndClose();
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() == 0 && selectedSector >= 0) {
            executeSelectedCommandAndClose();
            return true;
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    private void executeSelectedCommandAndClose() {
        List<CommandWheelFeature.WheelItem> items = CommandWheelFeature.ITEMS;
        if (selectedSector >= 0 && selectedSector < items.size() && this.minecraft != null && this.minecraft.player != null) {
            CommandWheelFeature.WheelItem selected = items.get(selectedSector);
            String cmd = selected.command();
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            this.minecraft.player.connection.sendCommand(cmd);
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    private void fillCircle(GuiGraphicsExtractor graphicsExtractor, int cx, int cy, int radius, int color) {
        int r2 = radius * radius;
        for (int y = -radius; y <= radius; y++) {
            int dx = (int) Math.round(Math.sqrt(r2 - y * y));
            graphicsExtractor.fill(cx - dx, cy + y, cx + dx, cy + y + 1, color);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphicsExtractor, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphicsExtractor, mouseX, mouseY, partialTick);

        int cx = this.width / 2;
        int cy = this.height / 2;

        List<CommandWheelFeature.WheelItem> items = CommandWheelFeature.ITEMS;
        int count = items.size();
        if (count == 0) return;

        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);

        double innerRadius = 42.0;
        double outerRadius = 145.0;

        selectedSector = -1;
        if (dist >= innerRadius && dist <= outerRadius + 20) {
            double angle = Math.atan2(dy, dx);
            if (angle < 0) {
                angle += 2 * Math.PI;
            }
            double sectorAngle = 2 * Math.PI / count;
            double shiftedAngle = (angle + Math.PI / 2 + sectorAngle / 2) % (2 * Math.PI);
            if (shiftedAngle < 0) shiftedAngle += 2 * Math.PI;
            selectedSector = (int) Math.floor(shiftedAngle / sectorAngle) % count;
        }

        // 1. Draw 100% Round Dark Circular Background Disk
        fillCircle(graphicsExtractor, cx, cy, (int) outerRadius + 15, 0x99000000);
        fillCircle(graphicsExtractor, cx, cy, (int) outerRadius + 5, 0xBB0B0D16);

        double sectorAngle = 2 * Math.PI / count;

        // 2. Draw Radial Separator Lines
        for (int i = 0; i < count; i++) {
            double sepAngle = -Math.PI / 2 + i * sectorAngle - sectorAngle / 2.0;
            for (double r = innerRadius; r <= outerRadius + 5; r += 2.0) {
                int sx = cx + (int) Math.round(Math.cos(sepAngle) * r);
                int sy = cy + (int) Math.round(Math.sin(sepAngle) * r);
                graphicsExtractor.fill(sx - 1, sy - 1, sx + 1, sy + 1, 0x44555577);
            }
        }

        // 3. Render Non-Overlapping Crisp Sector Tile Cards
        double midRadius = (innerRadius + outerRadius) / 2.0 + 5;

        for (int i = 0; i < count; i++) {
            boolean isSelected = (i == selectedSector);
            CommandWheelFeature.WheelItem item = items.get(i);

            double angle = -Math.PI / 2 + i * sectorAngle;
            int ix = cx + (int) Math.round(Math.cos(angle) * midRadius);
            int iy = cy + (int) Math.round(Math.sin(angle) * midRadius);

            int btnWidth = 94;
            int btnHeight = 24;
            int x1 = ix - btnWidth / 2;
            int y1 = iy - btnHeight / 2;
            int x2 = ix + btnWidth / 2;
            int y2 = iy + btnHeight / 2;

            if (isSelected) {
                // Gold outer highlight border
                graphicsExtractor.fill(x1 - 2, y1 - 2, x2 + 2, y2 + 2, 0xFFFFAA00);
                // Vibrant Purple tile fill
                graphicsExtractor.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xEE6C38FF);
            } else {
                // Sleek dark tile fill
                graphicsExtractor.fill(x1, y1, x2, y2, 0xDD1B1F30);
            }

            // Render Item Icon inside tile
            graphicsExtractor.fakeItem(item.iconStack(), ix - btnWidth / 2 + 5, iy - 8);

            // Render Item Title text inside tile
            int textColor = isSelected ? 0xFFFFFF55 : 0xFFFFFFFF;
            graphicsExtractor.centeredText(this.font, item.displayName(), ix + 8, iy - 4, textColor);
        }

        // 4. Render 100% Round Center Hub Disk
        fillCircle(graphicsExtractor, cx, cy, (int) innerRadius + 2, 0xFF5533AA);
        fillCircle(graphicsExtractor, cx, cy, (int) innerRadius, 0xFF0D0F18);

        if (selectedSector >= 0 && selectedSector < count) {
            CommandWheelFeature.WheelItem selected = items.get(selectedSector);
            graphicsExtractor.fakeItem(selected.iconStack(), cx - 8, cy - 22);
            graphicsExtractor.centeredText(this.font, selected.displayName(), cx, cy - 2, 0xFFFF55);
            graphicsExtractor.centeredText(this.font, selected.command(), cx, cy + 10, 0xAAAAAA);
        } else {
            graphicsExtractor.centeredText(this.font, "SELECT WARP", cx, cy - 4, 0x888888);
        }
    }
}

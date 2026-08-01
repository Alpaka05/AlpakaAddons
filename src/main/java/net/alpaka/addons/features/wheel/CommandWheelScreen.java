package net.alpaka.addons.features.wheel;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

public class CommandWheelScreen extends Screen {
    private int selectedSector = -1;
    private int openTicks = 0;

    public CommandWheelScreen() {
        super(Component.literal("Command Wheel"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        openTicks++;
        if (this.minecraft != null && openTicks >= 1) {
            InputConstants.Key key = CommandWheelFeature.COMMAND_WHEEL_KEY.getDefaultKey();
            if (key != null) {
                int keyCode = key.getValue();
                if (keyCode > 0) {
                    boolean isKeyDown = InputConstants.isKeyDown(this.minecraft.getWindow(), keyCode);
                    if (!isKeyDown) {
                        executeSelectedCommandAndClose();
                    }
                }
            }
        }
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

        double innerRadius = 40.0;
        double outerRadius = 135.0;

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

        graphicsExtractor.fill(cx - (int) outerRadius - 15, cy - (int) outerRadius - 15, cx + (int) outerRadius + 15, cy + (int) outerRadius + 15, 0x99000000);

        double sectorAngle = 2 * Math.PI / count;

        for (int i = 0; i < count; i++) {
            boolean isSelected = (i == selectedSector);
            CommandWheelFeature.WheelItem item = items.get(i);

            double angle = -Math.PI / 2 + i * sectorAngle;
            double currentOuter = isSelected ? outerRadius + 10 : outerRadius;

            double startAngle = angle - sectorAngle / 2.0;
            double endAngle = angle + sectorAngle / 2.0;
            int steps = 14;

            int sectorColor = isSelected ? 0xDD7C4DFF : 0xAA222533;

            for (int s = 0; s < steps; s++) {
                double a1 = startAngle + (endAngle - startAngle) * s / steps;
                double a2 = startAngle + (endAngle - startAngle) * (s + 1) / steps;

                int x1 = cx + (int) Math.round(Math.cos(a1) * innerRadius);
                int y1 = cy + (int) Math.round(Math.sin(a1) * innerRadius);
                int x2 = cx + (int) Math.round(Math.cos(a1) * currentOuter);
                int y2 = cy + (int) Math.round(Math.sin(a1) * currentOuter);
                int x3 = cx + (int) Math.round(Math.cos(a2) * currentOuter);
                int y3 = cy + (int) Math.round(Math.sin(a2) * currentOuter);

                int minX = Math.min(Math.min(x1, x2), x3);
                int maxX = Math.max(Math.max(x1, x2), x3);
                int minY = Math.min(Math.min(y1, y2), y3);
                int maxY = Math.max(Math.max(y1, y2), y3);

                graphicsExtractor.fill(minX, minY, maxX, maxY, sectorColor);
            }

            double midRadius = (innerRadius + currentOuter) / 2.0;
            int ix = cx + (int) Math.round(Math.cos(angle) * midRadius) - 8;
            int iy = cy + (int) Math.round(Math.sin(angle) * midRadius) - 14;

            graphicsExtractor.fakeItem(item.iconStack(), ix, iy);

            int textColor = isSelected ? 0xFFFFFF55 : 0xFFFFFFFF;
            graphicsExtractor.centeredText(this.font, item.displayName(), ix + 8, iy + 17, textColor);
        }

        graphicsExtractor.fill(cx - (int) innerRadius, cy - (int) innerRadius, cx + (int) innerRadius, cy + (int) innerRadius, 0xEE111625);
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

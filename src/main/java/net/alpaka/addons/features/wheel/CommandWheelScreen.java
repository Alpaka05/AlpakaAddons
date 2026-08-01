package net.alpaka.addons.features.wheel;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

public class CommandWheelScreen extends Screen {
    private int selectedSector = -1;
    private final long openTime;
    private boolean wasHeldPastThreshold = false;

    public CommandWheelScreen() {
        super(Component.literal("Command Wheel"));
        this.openTime = System.currentTimeMillis();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.minecraft != null) {
            long elapsed = System.currentTimeMillis() - openTime;
            InputConstants.Key key = CommandWheelFeature.COMMAND_WHEEL_KEY.getDefaultKey();
            if (key != null) {
                int keyCode = key.getValue();
                if (keyCode > 0) {
                    boolean isKeyDown = InputConstants.isKeyDown(this.minecraft.getWindow(), keyCode);
                    if (isKeyDown && elapsed > 150) {
                        wasHeldPastThreshold = true;
                    }
                    if (wasHeldPastThreshold && !isKeyDown) {
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

        graphicsExtractor.fill(cx - (int) outerRadius - 10, cy - (int) outerRadius - 10, cx + (int) outerRadius + 10, cy + (int) outerRadius + 10, 0x88000000);

        double sectorAngle = 2 * Math.PI / count;
        double midRadius = (innerRadius + outerRadius) / 2.0;

        for (int i = 0; i < count; i++) {
            boolean isSelected = (i == selectedSector);
            CommandWheelFeature.WheelItem item = items.get(i);

            double angle = -Math.PI / 2 + i * sectorAngle;
            int lx = cx + (int) Math.round(Math.cos(angle) * midRadius);
            int ly = cy + (int) Math.round(Math.sin(angle) * midRadius);

            int btnWidth = 100;
            int btnHeight = 20;
            int x1 = lx - btnWidth / 2;
            int y1 = ly - btnHeight / 2;
            int x2 = lx + btnWidth / 2;
            int y2 = ly + btnHeight / 2;

            int bgColor = isSelected ? 0xCC7C4DFF : 0xAA222533;
            int textColor = isSelected ? 0xFFFFFF55 : 0xFFFFFFFF;

            graphicsExtractor.fill(x1, y1, x2, y2, bgColor);
            graphicsExtractor.centeredText(this.font, item.displayName(), lx, ly - 4, textColor);
        }

        graphicsExtractor.fill(cx - (int) innerRadius, cy - (int) innerRadius, cx + (int) innerRadius, cy + (int) innerRadius, 0xDD111625);
        if (selectedSector >= 0 && selectedSector < count) {
            CommandWheelFeature.WheelItem selected = items.get(selectedSector);
            graphicsExtractor.centeredText(this.font, selected.displayName(), cx, cy - 10, 0xFFFF55);
            graphicsExtractor.centeredText(this.font, selected.command(), cx, cy + 3, 0xAAAAAA);
        } else {
            graphicsExtractor.centeredText(this.font, "SELECT WARP", cx, cy - 4, 0x888888);
        }
    }
}

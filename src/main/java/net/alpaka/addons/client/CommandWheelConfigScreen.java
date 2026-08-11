package net.alpaka.addons.client;

import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.sound.CustomSoundFeature;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class CommandWheelConfigScreen extends Screen {
    private final Screen parent;
    private String newCommandInput = "/";
    private boolean inputFocused = false;
    private long cursorBlinkTimer = 0L;

    private double scrollY = 0.0;
    private double targetScrollY = 0.0;
    private double maxScrollY = 0.0;

    public CommandWheelConfigScreen(Screen parent) {
        super(Component.literal("Configure Quick Commands"));
        this.parent = parent;
    }

    private void playPloppSound() {
        try {
            CustomSoundFeature.playButtonClickSound();
        } catch (Throwable ignored) {}
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        graphics.fill(0, 0, this.width, this.height, 0x80000000);

        int winW = 440;
        int winH = 340;
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        // Shadow & Frame
        for (int i = 1; i <= 6; i++) {
            int alpha = (int) (0x24 * (1.0f - (float) i / 6.0f));
            ModernGuiUtils.drawRect(graphics, winX - i, winY - i, winW + i * 2, winH + i * 2, (alpha << 24));
        }
        ModernGuiUtils.drawRect(graphics, winX, winY, winW, winH, ModernGuiUtils.COLOR_PANEL_BG);
        ModernGuiUtils.drawOutline(graphics, winX, winY, winW, winH, ModernGuiUtils.COLOR_CARD_BORDER);

        // Header Bar
        int headerH = 38;
        ModernGuiUtils.drawRect(graphics, winX, winY, winW, headerH, ModernGuiUtils.COLOR_SIDEBAR_BG);
        ModernGuiUtils.drawRect(graphics, winX, winY + headerH - 1, winW, 1, ModernGuiUtils.COLOR_ACCENT);

        graphics.text(this.font, Component.literal("Quick Command Settings"), winX + 16, winY + 12, ModernGuiUtils.COLOR_TEXT_PRIMARY);

        // Close Button
        int closeW = 60;
        int closeH = 22;
        int closeX = winX + winW - closeW - 10;
        int closeY = winY + 8;
        boolean hoverClose = mouseX >= closeX && mouseX <= closeX + closeW && mouseY >= closeY && mouseY <= closeY + closeH;
        ModernGuiUtils.drawModernButton(graphics, this.font, closeX, closeY, closeW, closeH, "Done ✕", hoverClose, false);

        // Commands List Viewport
        int listX = winX + 16;
        int listY = winY + headerH + 12;
        int listW = winW - 32;
        int listH = 190;

        ModernGuiUtils.drawRect(graphics, listX, listY, listW, listH, ModernGuiUtils.COLOR_SIDEBAR_BG);
        ModernGuiUtils.drawOutline(graphics, listX, listY, listW, listH, ModernGuiUtils.COLOR_CARD_BORDER);

        List<String> commands = AlpakaConfig.instance.commandWheelCommands;
        if (commands == null) {
            commands = new ArrayList<>();
            AlpakaConfig.instance.commandWheelCommands = commands;
        }

        int itemH = 30;
        int itemSpacing = 6;
        int totalH = commands.size() * (itemH + itemSpacing) + 10;
        maxScrollY = Math.max(0, totalH - listH);

        scrollY += (targetScrollY - scrollY) * 0.2;

        graphics.enableScissor(listX + 2, listY + 2, listX + listW - 2, listY + listH - 2);

        int curY = listY + 6 - (int) scrollY;
        int itemW = listW - 16;

        for (int i = 0; i < commands.size(); i++) {
            String cmd = commands.get(i);
            boolean hoverItem = mouseX >= listX + 8 && mouseX <= listX + 8 + itemW && mouseY >= curY && mouseY <= curY + itemH && mouseY >= listY && mouseY <= listY + listH;

            ModernGuiUtils.drawRect(graphics, listX + 8, curY, itemW, itemH, hoverItem ? ModernGuiUtils.COLOR_CARD_BG_HOVER : ModernGuiUtils.COLOR_CARD_BG);
            ModernGuiUtils.drawOutline(graphics, listX + 8, curY, itemW, itemH, hoverItem ? ModernGuiUtils.COLOR_ACCENT_DIM : ModernGuiUtils.COLOR_CARD_BORDER);

            // Command label
            graphics.text(this.font, Component.literal(cmd), listX + 18, curY + (itemH - 8) / 2, ModernGuiUtils.COLOR_TEXT_PRIMARY);

            // Delete button "✕"
            int delW = 20;
            int delH = 20;
            int delX = listX + 8 + itemW - delW - 5;
            int delY = curY + (itemH - delH) / 2;
            boolean hoverDel = mouseX >= delX && mouseX <= delX + delW && mouseY >= delY && mouseY <= delY + delH && mouseY >= listY && mouseY <= listY + listH;

            ModernGuiUtils.drawRect(graphics, delX, delY, delW, delH, hoverDel ? 0xFFEF4444 : 0x30EF4444);
            ModernGuiUtils.drawOutline(graphics, delX, delY, delW, delH, 0xFFEF4444);

            int xTextX = delX + (delW - this.font.width("✕")) / 2;
            int xTextY = delY + (delH - 8) / 2;
            graphics.text(this.font, Component.literal("✕"), xTextX, xTextY, 0xFFFFFFFF);

            curY += itemH + itemSpacing;
        }

        graphics.disableScissor();

        // Bottom Add Input Area
        int bottomY = winY + winH - 46;
        int inputX = winX + 16;
        int inputW = 240;
        int inputH = 24;

        boolean hoverInput = mouseX >= inputX && mouseX <= inputX + inputW && mouseY >= bottomY && mouseY <= bottomY + inputH;
        int inputBorder = inputFocused ? ModernGuiUtils.COLOR_ACCENT : (hoverInput ? ModernGuiUtils.COLOR_ACCENT_DIM : ModernGuiUtils.COLOR_CARD_BORDER);

        ModernGuiUtils.drawRect(graphics, inputX, bottomY, inputW, inputH, ModernGuiUtils.COLOR_CARD_BG);
        ModernGuiUtils.drawOutline(graphics, inputX, bottomY, inputW, inputH, inputBorder);

        String textToDraw = newCommandInput;
        if (inputFocused && (System.currentTimeMillis() - cursorBlinkTimer) % 1000 < 500) {
            textToDraw += "|";
        }
        graphics.text(this.font, Component.literal(textToDraw), inputX + 8, bottomY + (inputH - 8) / 2, ModernGuiUtils.COLOR_TEXT_PRIMARY);

        // "Add" Button
        int addBtnX = inputX + inputW + 8;
        int addBtnW = 75;
        int addBtnH = 24;
        boolean hoverAdd = mouseX >= addBtnX && mouseX <= addBtnX + addBtnW && mouseY >= bottomY && mouseY <= bottomY + addBtnH;
        ModernGuiUtils.drawModernButton(graphics, this.font, addBtnX, bottomY, addBtnW, addBtnH, "+ Add", hoverAdd, true);

        // "Reset" Button
        int resetBtnX = addBtnX + addBtnW + 8;
        int resetBtnW = 75;
        int resetBtnH = 24;
        boolean hoverReset = mouseX >= resetBtnX && mouseX <= resetBtnX + resetBtnW && mouseY >= bottomY && mouseY <= bottomY + resetBtnH;
        ModernGuiUtils.drawModernButton(graphics, this.font, resetBtnX, bottomY, resetBtnW, resetBtnH, "Reset", hoverReset, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        int winW = 440;
        int winH = 340;
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;
        int headerH = 38;

        // Close button
        int closeW = 60;
        int closeH = 22;
        int closeX = winX + winW - closeW - 10;
        int closeY = winY + 8;
        if (mouseX >= closeX && mouseX <= closeX + closeW && mouseY >= closeY && mouseY <= closeY + closeH) {
            playPloppSound();
            this.onClose();
            return true;
        }

        // List Viewport item clicks (Delete buttons)
        int listX = winX + 16;
        int listY = winY + headerH + 12;
        int listW = winW - 32;
        int listH = 190;
        int itemH = 30;
        int itemSpacing = 6;

        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            List<String> commands = AlpakaConfig.instance.commandWheelCommands;
            int curY = listY + 6 - (int) scrollY;
            int itemW = listW - 16;

            for (int i = 0; i < commands.size(); i++) {
                int delW = 20;
                int delH = 20;
                int delX = listX + 8 + itemW - delW - 5;
                int delY = curY + (itemH - delH) / 2;

                if (mouseX >= delX && mouseX <= delX + delW && mouseY >= delY && mouseY <= delY + delH) {
                    playPloppSound();
                    commands.remove(i);
                    AlpakaConfig.save();
                    return true;
                }
                curY += itemH + itemSpacing;
            }
        }

        // Bottom Add Input Area
        int bottomY = winY + winH - 46;
        int inputX = winX + 16;
        int inputW = 240;
        int inputH = 24;

        if (mouseX >= inputX && mouseX <= inputX + inputW && mouseY >= bottomY && mouseY <= bottomY + inputH) {
            this.inputFocused = true;
            this.cursorBlinkTimer = System.currentTimeMillis();
            return true;
        } else {
            this.inputFocused = false;
        }

        // "Add" Button click
        int addBtnX = inputX + inputW + 8;
        int addBtnW = 75;
        int addBtnH = 24;
        if (mouseX >= addBtnX && mouseX <= addBtnX + addBtnW && mouseY >= bottomY && mouseY <= bottomY + addBtnH) {
            addCurrentInput();
            return true;
        }

        // "Reset" Button click
        int resetBtnX = addBtnX + addBtnW + 8;
        int resetBtnW = 75;
        int resetBtnH = 24;
        if (mouseX >= resetBtnX && mouseX <= resetBtnX + resetBtnW && mouseY >= bottomY && mouseY <= bottomY + resetBtnH) {
            playPloppSound();
            AlpakaConfig.instance.commandWheelCommands = new ArrayList<>(List.of("/hub", "/island", "/warp dh", "/wardrobe", "/pets", "/pv"));
            AlpakaConfig.save();
            return true;
        }

        return super.mouseClicked(event, isDoubleClick);
    }

    private void addCurrentInput() {
        String trimmed = newCommandInput.trim();
        if (trimmed.isEmpty() || trimmed.equals("/")) return;

        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }

        if (AlpakaConfig.instance.commandWheelCommands == null) {
            AlpakaConfig.instance.commandWheelCommands = new ArrayList<>();
        }

        if (!AlpakaConfig.instance.commandWheelCommands.contains(trimmed)) {
            playPloppSound();
            AlpakaConfig.instance.commandWheelCommands.add(trimmed);
            AlpakaConfig.save();
        }

        newCommandInput = "/";
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            targetScrollY = Math.max(0, Math.min(maxScrollY, targetScrollY - scrollY * 24.0));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (inputFocused) {
            int codePoint = event.codepoint();
            if (codePoint >= 32 && codePoint != 127) {
                if (newCommandInput.length() < 35) {
                    newCommandInput += (char) codePoint;
                    return true;
                }
            }
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (inputFocused) {
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (newCommandInput.length() > 0) {
                    newCommandInput = newCommandInput.substring(0, newCommandInput.length() - 1);
                    return true;
                }
            } else if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                addCurrentInput();
                return true;
            }
        }
        return super.keyPressed(event);
    }
}

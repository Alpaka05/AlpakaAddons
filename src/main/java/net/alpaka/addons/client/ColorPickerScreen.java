package net.alpaka.addons.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;

public class ColorPickerScreen extends Screen {
    private final Screen parent;
    private final Consumer<Integer> onSave;
    private int r, g, b, a;

    public ColorPickerScreen(Screen parent, String title, int initialColor, Consumer<Integer> onSave) {
        super(Component.literal(title));
        this.parent = parent;
        this.onSave = onSave;
        this.a = (initialColor >> 24) & 0xFF;
        this.r = (initialColor >> 16) & 0xFF;
        this.g = (initialColor >> 8) & 0xFF;
        this.b = initialColor & 0xFF;
    }

    @Override
    protected void init() {
        int centerY = this.height / 2 - 80;

        this.addRenderableWidget(new StringWidget(0, 8, this.width, 20, this.title, this.font));

        // Red Slider
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY, 150, 20, Component.literal("Red: " + r), this.font));
        AbstractSliderButton redSlider = new AbstractSliderButton(this.width / 2 + 5, centerY, 150, 20, Component.literal(String.valueOf(r)), r / 255.0) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(String.valueOf(r)));
            }
            @Override
            protected void applyValue() {
                r = (int) (this.value * 255.0);
            }
        };
        this.addRenderableWidget(redSlider);

        // Green Slider
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 24, 150, 20, Component.literal("Green: " + g), this.font));
        AbstractSliderButton greenSlider = new AbstractSliderButton(this.width / 2 + 5, centerY + 24, 150, 20, Component.literal(String.valueOf(g)), g / 255.0) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(String.valueOf(g)));
            }
            @Override
            protected void applyValue() {
                g = (int) (this.value * 255.0);
            }
        };
        this.addRenderableWidget(greenSlider);

        // Blue Slider
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 48, 150, 20, Component.literal("Blue: " + b), this.font));
        AbstractSliderButton blueSlider = new AbstractSliderButton(this.width / 2 + 5, centerY + 48, 150, 20, Component.literal(String.valueOf(b)), b / 255.0) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(String.valueOf(b)));
            }
            @Override
            protected void applyValue() {
                b = (int) (this.value * 255.0);
            }
        };
        this.addRenderableWidget(blueSlider);

        // Alpha Slider
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 72, 150, 20, Component.literal("Alpha: " + a), this.font));
        AbstractSliderButton alphaSlider = new AbstractSliderButton(this.width / 2 + 5, centerY + 72, 150, 20, Component.literal(String.valueOf(a)), a / 255.0) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(String.valueOf(a)));
            }
            @Override
            protected void applyValue() {
                a = (int) (this.value * 255.0);
            }
        };
        this.addRenderableWidget(alphaSlider);

        // Done button
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> {
            int color = (a << 24) | (r << 16) | (g << 8) | b;
            onSave.accept(color);
            onClose();
        }).bounds(this.width / 2 - 100, centerY + 130, 200, 20).build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        
        int centerY = this.height / 2 - 80;
        int previewX = this.width / 2 - 50;
        int previewY = centerY + 100;
        
        graphics.fill(previewX - 2, previewY - 2, previewX + 102, previewY + 22, 0xFF000000);
        int color = (a << 24) | (r << 16) | (g << 8) | b;
        graphics.fill(previewX, previewY, previewX + 100, previewY + 20, color);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}

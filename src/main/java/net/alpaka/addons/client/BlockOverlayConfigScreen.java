package net.alpaka.addons.client;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class BlockOverlayConfigScreen extends Screen {
    private final Screen parent;

    public BlockOverlayConfigScreen(Screen parent) {
        super(Component.literal("Block Overlay Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerY = this.height / 2 - 130;

        this.addRenderableWidget(new StringWidget(0, 8, this.width, 20, this.title, this.font));

        // 1. Enable Block Overlay Toggle
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY, 150, 20,
                Component.literal("Enable Block Overlay"), this.font));
        Button toggleOverlay = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.blockOverlayEnabled),
                button -> {
                    AlpakaConfig.instance.blockOverlayEnabled = !AlpakaConfig.instance.blockOverlayEnabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.blockOverlayEnabled));
                }
        )
        .bounds(this.width / 2 + 5, centerY, 150, 20)
        .build();
        this.addRenderableWidget(toggleOverlay);

        // 2. Smooth Fade In Toggle
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 22, 150, 20,
                Component.literal("Smooth Fade In"), this.font));
        Button toggleFadeIn = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.blockFadeInEnabled),
                button -> {
                    AlpakaConfig.instance.blockFadeInEnabled = !AlpakaConfig.instance.blockFadeInEnabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.blockFadeInEnabled));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 22, 150, 20)
        .build();
        this.addRenderableWidget(toggleFadeIn);

        // 3. Fade In Duration Slider (50 ms to 1000 ms)
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 44, 150, 20,
                Component.literal("Fade In Duration"), this.font));
        AbstractSliderButton durationSlider = new AbstractSliderButton(
                this.width / 2 + 5, centerY + 44, 150, 20,
                Component.literal(AlpakaConfig.instance.blockFadeInDurationMs + " ms"),
                (AlpakaConfig.instance.blockFadeInDurationMs - 50.0f) / 950.0f
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(AlpakaConfig.instance.blockFadeInDurationMs + " ms"));
            }
            @Override
            protected void applyValue() {
                AlpakaConfig.instance.blockFadeInDurationMs = Math.round(50.0f + (float) this.value * 950.0f);
                AlpakaConfig.save();
            }
        };
        this.addRenderableWidget(durationSlider);

        // 4. Enable Outline Toggle
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 66, 150, 20,
                Component.literal("Render Outline"), this.font));
        Button toggleOutline = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.blockOutlineEnabled),
                button -> {
                    AlpakaConfig.instance.blockOutlineEnabled = !AlpakaConfig.instance.blockOutlineEnabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.blockOutlineEnabled));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 66, 150, 20)
        .build();
        this.addRenderableWidget(toggleOutline);

        // 5. Outline Thickness Slider (0.5 to 5.0)
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 88, 150, 20,
                Component.literal("Outline Thickness"), this.font));
        AbstractSliderButton thicknessSlider = new AbstractSliderButton(
                this.width / 2 + 5, centerY + 88, 150, 20,
                Component.literal(String.format("%.1f", AlpakaConfig.instance.blockOutlineThickness)),
                (AlpakaConfig.instance.blockOutlineThickness - 0.5f) / 4.5f
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(String.format("%.1f", AlpakaConfig.instance.blockOutlineThickness)));
            }
            @Override
            protected void applyValue() {
                AlpakaConfig.instance.blockOutlineThickness = (float) (0.5f + this.value * 4.5f);
                AlpakaConfig.save();
            }
        };
        this.addRenderableWidget(thicknessSlider);

        // 6. Outline Color Button (opens ColorPickerScreen)
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 110, 150, 20,
                Component.literal("Outline Color"), this.font));
        Button btnOutlineColor = Button.builder(
                Component.literal("Choose Color..."),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new ColorPickerScreen(this, "Block Outline Color", AlpakaConfig.instance.blockOutlineColor, color -> {
                            AlpakaConfig.instance.blockOutlineColor = color;
                            AlpakaConfig.save();
                        }));
                    }
                }
        )
        .bounds(this.width / 2 + 5, centerY + 110, 150, 20)
        .build();
        this.addRenderableWidget(btnOutlineColor);

        // 7. Chroma Toggle
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 132, 150, 20,
                Component.literal("Chroma (Rainbow)"), this.font));
        Button toggleChroma = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.blockChromaEnabled),
                button -> {
                    AlpakaConfig.instance.blockChromaEnabled = !AlpakaConfig.instance.blockChromaEnabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.blockChromaEnabled));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 132, 150, 20)
        .build();
        this.addRenderableWidget(toggleChroma);

        // 8. Chroma Speed Slider (0.1 to 2.0)
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 154, 150, 20,
                Component.literal("Chroma Speed"), this.font));
        AbstractSliderButton speedSlider = new AbstractSliderButton(
                this.width / 2 + 5, centerY + 154, 150, 20,
                Component.literal(String.format("%.2fx", AlpakaConfig.instance.blockChromaSpeed)),
                (AlpakaConfig.instance.blockChromaSpeed - 0.1f) / 1.9f
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(String.format("%.2fx", AlpakaConfig.instance.blockChromaSpeed)));
            }
            @Override
            protected void applyValue() {
                AlpakaConfig.instance.blockChromaSpeed = (float) (0.1f + this.value * 1.9f);
                AlpakaConfig.save();
            }
        };
        this.addRenderableWidget(speedSlider);

        // 9. Ignore Depth Toggle
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 176, 150, 20,
                Component.literal("Ignore Depth (X-Ray)"), this.font));
        Button toggleDepth = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.blockIgnoreDepth),
                button -> {
                    AlpakaConfig.instance.blockIgnoreDepth = !AlpakaConfig.instance.blockIgnoreDepth;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.blockIgnoreDepth));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 176, 150, 20)
        .build();
        this.addRenderableWidget(toggleDepth);

        // 10. Enable Fill Toggle
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 198, 150, 20,
                Component.literal("Fill Block Faces"), this.font));
        Button toggleFill = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.blockFillEnabled),
                button -> {
                    AlpakaConfig.instance.blockFillEnabled = !AlpakaConfig.instance.blockFillEnabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.blockFillEnabled));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 198, 150, 20)
        .build();
        this.addRenderableWidget(toggleFill);

        // 11. Fill Color Button (opens ColorPickerScreen)
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 220, 150, 20,
                Component.literal("Fill Color"), this.font));
        Button btnFillColor = Button.builder(
                Component.literal("Choose Color..."),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new ColorPickerScreen(this, "Block Fill Color", AlpakaConfig.instance.blockFillColor, color -> {
                            AlpakaConfig.instance.blockFillColor = color;
                            AlpakaConfig.save();
                        }));
                    }
                }
        )
        .bounds(this.width / 2 + 5, centerY + 220, 150, 20)
        .build();
        this.addRenderableWidget(btnFillColor);

        // Done button
        this.addRenderableWidget(Button.builder(
                CommonComponents.GUI_DONE,
                button -> this.onClose()
        )
        .bounds(this.width / 2 - 100, centerY + 246, 200, 20)
        .build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        int centerY = this.height / 2 - 130;

        // Outline Color Preview (at centerY + 110)
        int outlineColor = AlpakaConfig.instance.blockOutlineColor;
        graphics.fill(this.width / 2 + 160, centerY + 110, this.width / 2 + 180, centerY + 130, 0xFF000000);
        graphics.fill(this.width / 2 + 161, centerY + 111, this.width / 2 + 179, centerY + 129, outlineColor);

        // Fill Color Preview (at centerY + 220)
        int fillColor = AlpakaConfig.instance.blockFillColor;
        graphics.fill(this.width / 2 + 160, centerY + 220, this.width / 2 + 180, centerY + 240, 0xFF000000);
        graphics.fill(this.width / 2 + 161, centerY + 221, this.width / 2 + 179, centerY + 239, fillColor);
    }

    @Override
    public void onClose() {
        // A drag cut short by the screen closing never sees its mouse-up, so flush here as well.
        AlpakaConfig.endDeferredSaves();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    /**
     * Holds config writes back for the length of a slider drag.
     *
     * Each of this screen's sliders saves from {@code applyValue}, which the vanilla slider calls on
     * every mouse-move event while it is being dragged - a full serialise and file write per pixel.
     * Deferring here collects the whole drag into the single write done on release.
     */
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        AlpakaConfig.beginDeferredSaves();
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        // super first, so the slider's final applyValue still lands inside the deferred window.
        boolean handled = super.mouseReleased(event);
        AlpakaConfig.endDeferredSaves();
        return handled;
    }
}

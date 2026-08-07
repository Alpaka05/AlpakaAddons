package net.alpaka.addons.client;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
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
        int centerY = this.height / 2 - 110;

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

        // 2. Enable Outline Toggle
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 22, 150, 20,
                Component.literal("Render Outline"), this.font));
        Button toggleOutline = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.blockOutlineEnabled),
                button -> {
                    AlpakaConfig.instance.blockOutlineEnabled = !AlpakaConfig.instance.blockOutlineEnabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.blockOutlineEnabled));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 22, 150, 20)
        .build();
        this.addRenderableWidget(toggleOutline);

        // 3. Outline Thickness Slider (0.5 to 5.0)
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 44, 150, 20,
                Component.literal("Outline Thickness"), this.font));
        AbstractSliderButton thicknessSlider = new AbstractSliderButton(
                this.width / 2 + 5, centerY + 44, 150, 20,
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

        // 4. Outline Color Button (opens ColorPickerScreen)
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 66, 150, 20,
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
        .bounds(this.width / 2 + 5, centerY + 66, 150, 20)
        .build();
        this.addRenderableWidget(btnOutlineColor);

        // 5. Chroma Toggle
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 88, 150, 20,
                Component.literal("Chroma (Rainbow)"), this.font));
        Button toggleChroma = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.blockChromaEnabled),
                button -> {
                    AlpakaConfig.instance.blockChromaEnabled = !AlpakaConfig.instance.blockChromaEnabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.blockChromaEnabled));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 88, 150, 20)
        .build();
        this.addRenderableWidget(toggleChroma);

        // 6. Chroma Speed Slider (0.1 to 5.0)
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 110, 150, 20,
                Component.literal("Chroma Speed"), this.font));
        AbstractSliderButton speedSlider = new AbstractSliderButton(
                this.width / 2 + 5, centerY + 110, 150, 20,
                Component.literal(String.format("%.2fx", AlpakaConfig.instance.blockChromaSpeed)),
                (AlpakaConfig.instance.blockChromaSpeed - 0.05f) / 1.95f
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(String.format("%.2fx", AlpakaConfig.instance.blockChromaSpeed)));
            }
            @Override
            protected void applyValue() {
                AlpakaConfig.instance.blockChromaSpeed = (float) (0.05f + this.value * 1.95f);
                AlpakaConfig.save();
            }
        };
        this.addRenderableWidget(speedSlider);

        // 7. Ignore Depth Toggle
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 132, 150, 20,
                Component.literal("Ignore Depth (X-Ray)"), this.font));
        Button toggleDepth = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.blockIgnoreDepth),
                button -> {
                    AlpakaConfig.instance.blockIgnoreDepth = !AlpakaConfig.instance.blockIgnoreDepth;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.blockIgnoreDepth));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 132, 150, 20)
        .build();
        this.addRenderableWidget(toggleDepth);

        // 8. Enable Fill Toggle
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 154, 150, 20,
                Component.literal("Fill Block Faces"), this.font));
        Button toggleFill = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.blockFillEnabled),
                button -> {
                    AlpakaConfig.instance.blockFillEnabled = !AlpakaConfig.instance.blockFillEnabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.blockFillEnabled));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 154, 150, 20)
        .build();
        this.addRenderableWidget(toggleFill);

        // 9. Fill Color Button (opens ColorPickerScreen)
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 176, 150, 20,
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
        .bounds(this.width / 2 + 5, centerY + 176, 150, 20)
        .build();
        this.addRenderableWidget(btnFillColor);

        // Done button
        this.addRenderableWidget(Button.builder(
                CommonComponents.GUI_DONE,
                button -> this.onClose()
        )
        .bounds(this.width / 2 - 100, centerY + 206, 200, 20)
        .build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        int centerY = this.height / 2 - 110;

        // Outline Color Preview (at centerY + 66)
        int outlineColor = AlpakaConfig.instance.blockOutlineColor;
        graphics.fill(this.width / 2 + 160, centerY + 66, this.width / 2 + 180, centerY + 86, 0xFF000000);
        graphics.fill(this.width / 2 + 161, centerY + 67, this.width / 2 + 179, centerY + 85, outlineColor);

        // Fill Color Preview (at centerY + 176)
        int fillColor = AlpakaConfig.instance.blockFillColor;
        graphics.fill(this.width / 2 + 160, centerY + 176, this.width / 2 + 180, centerY + 196, 0xFF000000);
        graphics.fill(this.width / 2 + 161, centerY + 177, this.width / 2 + 179, centerY + 195, fillColor);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}

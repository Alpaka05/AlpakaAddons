package net.alpaka.addons.client;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class ItemSizeConfigScreen extends Screen {
    private final Screen parent;

    public ItemSizeConfigScreen(Screen parent) {
        super(Component.literal("Item Size & Viewmodel Settings"));
        this.parent = parent;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x66000000);
    }

    @Override
    protected void init() {
        int centerY = this.height / 2 - 140;

        // Title
        this.addRenderableWidget(new StringWidget(0, 8, this.width, 20, this.title, this.font));

        // 1. Enable Modifiers Toggle
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY, 150, 20,
                Component.literal("Enable Viewmodel Modifiers"), this.font));
        Button toggleFeature = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.itemSizeFeatureEnabled),
                button -> {
                    AlpakaConfig.instance.itemSizeFeatureEnabled = !AlpakaConfig.instance.itemSizeFeatureEnabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.itemSizeFeatureEnabled));
                }
        )
        .bounds(this.width / 2 + 5, centerY, 150, 20)
        .build();
        this.addRenderableWidget(toggleFeature);

        // 2. Scale Slider (0.2x to 2.5x)
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 24, 150, 20,
                Component.literal("Item Scale (Size)"), this.font));
        AbstractSliderButton scaleSlider = new AbstractSliderButton(
                this.width / 2 + 5, centerY + 24, 150, 20,
                Component.literal(String.format("%.2fx", AlpakaConfig.instance.itemScale)),
                (AlpakaConfig.instance.itemScale - 0.2f) / 2.3f
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(String.format("%.2fx", AlpakaConfig.instance.itemScale)));
            }
            @Override
            protected void applyValue() {
                AlpakaConfig.instance.itemScale = (float) (0.2f + this.value * 2.3f);
                AlpakaConfig.save();
            }
        };
        this.addRenderableWidget(scaleSlider);

        // 3. X Offset Slider (-1.5 to 1.5)
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 48, 150, 20,
                Component.literal("X Offset (Left/Right)"), this.font));
        AbstractSliderButton xSlider = new AbstractSliderButton(
                this.width / 2 + 5, centerY + 48, 150, 20,
                Component.literal(String.format("%.2f", AlpakaConfig.instance.itemXOffset)),
                (AlpakaConfig.instance.itemXOffset + 1.5f) / 3.0f
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(String.format("%.2f", AlpakaConfig.instance.itemXOffset)));
            }
            @Override
            protected void applyValue() {
                AlpakaConfig.instance.itemXOffset = (float) (-1.5f + this.value * 3.0f);
                AlpakaConfig.save();
            }
        };
        this.addRenderableWidget(xSlider);

        // 4. Y Offset Slider (-1.5 to 1.5)
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 72, 150, 20,
                Component.literal("Y Offset (Up/Down)"), this.font));
        AbstractSliderButton ySlider = new AbstractSliderButton(
                this.width / 2 + 5, centerY + 72, 150, 20,
                Component.literal(String.format("%.2f", AlpakaConfig.instance.itemYOffset)),
                (AlpakaConfig.instance.itemYOffset + 1.5f) / 3.0f
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(String.format("%.2f", AlpakaConfig.instance.itemYOffset)));
            }
            @Override
            protected void applyValue() {
                AlpakaConfig.instance.itemYOffset = (float) (-1.5f + this.value * 3.0f);
                AlpakaConfig.save();
            }
        };
        this.addRenderableWidget(ySlider);

        // 5. Z Offset Slider (-1.5 to 1.5)
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 96, 150, 20,
                Component.literal("Z Offset (Forward/Back)"), this.font));
        AbstractSliderButton zSlider = new AbstractSliderButton(
                this.width / 2 + 5, centerY + 96, 150, 20,
                Component.literal(String.format("%.2f", AlpakaConfig.instance.itemZOffset)),
                (AlpakaConfig.instance.itemZOffset + 1.5f) / 3.0f
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(String.format("%.2f", AlpakaConfig.instance.itemZOffset)));
            }
            @Override
            protected void applyValue() {
                AlpakaConfig.instance.itemZOffset = (float) (-1.5f + this.value * 3.0f);
                AlpakaConfig.save();
            }
        };
        this.addRenderableWidget(zSlider);

        // 6. Swing Speed Slider (0.1x to 1.5x)
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 120, 150, 20,
                Component.literal("Swing Speed"), this.font));
        AbstractSliderButton swingSlider = new AbstractSliderButton(
                this.width / 2 + 5, centerY + 120, 150, 20,
                Component.literal(String.format("%.2fx", AlpakaConfig.instance.itemSwingSpeed)),
                (AlpakaConfig.instance.itemSwingSpeed - 0.1f) / 1.4f
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(String.format("%.2fx", AlpakaConfig.instance.itemSwingSpeed)));
            }
            @Override
            protected void applyValue() {
                AlpakaConfig.instance.itemSwingSpeed = (float) (0.1f + this.value * 1.4f);
                AlpakaConfig.save();
            }
        };
        this.addRenderableWidget(swingSlider);

        // 7. Disable Hand Sway Toggle
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 144, 150, 20,
                Component.literal("Disable Hand Sway"), this.font));
        Button toggleSway = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.itemSwayDisabled),
                button -> {
                    AlpakaConfig.instance.itemSwayDisabled = !AlpakaConfig.instance.itemSwayDisabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.itemSwayDisabled));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 144, 150, 20)
        .build();
        this.addRenderableWidget(toggleSway);

        // 8. Disable Re-equip Toggle
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 168, 150, 20,
                Component.literal("Disable Re-equip"), this.font));
        Button toggleNoEquip = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.itemNoEquipEnabled),
                button -> {
                    AlpakaConfig.instance.itemNoEquipEnabled = !AlpakaConfig.instance.itemNoEquipEnabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.itemNoEquipEnabled));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 168, 150, 20)
        .build();
        this.addRenderableWidget(toggleNoEquip);

        // 9. Ignore Empty Hand Toggle
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 192, 150, 20,
                Component.literal("Ignore Empty Hand"), this.font));
        Button toggleIgnoreEmptyHand = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.itemIgnoreEmptyHandEnabled),
                button -> {
                    AlpakaConfig.instance.itemIgnoreEmptyHandEnabled = !AlpakaConfig.instance.itemIgnoreEmptyHandEnabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.itemIgnoreEmptyHandEnabled));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 192, 150, 20)
        .build();
        this.addRenderableWidget(toggleIgnoreEmptyHand);

        // 10. Swing Customizations Subscreen Button
        Button swingCustomsButton = Button.builder(
                Component.literal("Swing Customizations..."),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new ItemSwingConfigScreen(this));
                    }
                }
        )
        .bounds(this.width / 2 - 100, centerY + 220, 200, 20)
        .build();
        this.addRenderableWidget(swingCustomsButton);

        // 11. Load Preset row
        int loadY = centerY + 246;
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, loadY, 100, 20,
                Component.literal("Load Preset:"), this.font));

        Button loadP1 = Button.builder(Component.literal("P1"), button -> loadPreset(0))
                .bounds(this.width / 2 - 40, loadY, 50, 20).build();
        Button loadP2 = Button.builder(Component.literal("P2"), button -> loadPreset(1))
                .bounds(this.width / 2 + 15, loadY, 50, 20).build();
        Button loadP3 = Button.builder(Component.literal("P3"), button -> loadPreset(2))
                .bounds(this.width / 2 + 70, loadY, 50, 20).build();
        this.addRenderableWidget(loadP1);
        this.addRenderableWidget(loadP2);
        this.addRenderableWidget(loadP3);

        // 12. Save Preset row
        int saveY = centerY + 270;
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, saveY, 100, 20,
                Component.literal("Save Preset:"), this.font));

        Button saveP1 = Button.builder(Component.literal("to P1"), button -> savePreset(0))
                .bounds(this.width / 2 - 40, saveY, 50, 20).build();
        Button saveP2 = Button.builder(Component.literal("to P2"), button -> savePreset(1))
                .bounds(this.width / 2 + 15, saveY, 50, 20).build();
        Button saveP3 = Button.builder(Component.literal("to P3"), button -> savePreset(2))
                .bounds(this.width / 2 + 70, saveY, 50, 20).build();
        this.addRenderableWidget(saveP1);
        this.addRenderableWidget(saveP2);
        this.addRenderableWidget(saveP3);

        // Done button
        this.addRenderableWidget(Button.builder(
                CommonComponents.GUI_DONE,
                button -> this.onClose()
        )
        .bounds(this.width / 2 - 155, centerY + 300, 150, 20)
        .build());

        // Reset Defaults button
        this.addRenderableWidget(Button.builder(
                Component.literal("Reset Defaults"),
                button -> {
                    AlpakaConfig.instance.itemScale = 1.0f;
                    AlpakaConfig.instance.itemXOffset = 0.0f;
                    AlpakaConfig.instance.itemYOffset = 0.0f;
                    AlpakaConfig.instance.itemZOffset = 0.0f;
                    AlpakaConfig.instance.itemRotationX = 0.0f;
                    AlpakaConfig.instance.itemRotationY = 0.0f;
                    AlpakaConfig.instance.itemRotationZ = 0.0f;
                    AlpakaConfig.instance.itemSwingSpeed = 1.0f;
                    AlpakaConfig.instance.itemSwayDisabled = false;
                    AlpakaConfig.instance.itemSwingTranslationDisabled = false;
                    AlpakaConfig.instance.itemNoEquipEnabled = false;
                    AlpakaConfig.instance.itemSwingAlwaysFinishEnabled = false;
                    AlpakaConfig.instance.itemIgnoreEmptyHandEnabled = false;
                    AlpakaConfig.instance.swingDriftX = 0.0f;
                    AlpakaConfig.instance.swingDriftY = 0.0f;
                    AlpakaConfig.instance.swingDriftZ = 0.0f;
                    AlpakaConfig.instance.swingArcX = 0.0f;
                    AlpakaConfig.instance.swingArcY = 0.0f;
                    AlpakaConfig.instance.swingArcZ = 0.0f;
                    AlpakaConfig.save();
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(this);
                    }
                }
        )
        .bounds(this.width / 2 + 5, centerY + 300, 150, 20)
        .build());
    }

    private void loadPreset(int index) {
        if (AlpakaConfig.instance.itemPresets != null && index >= 0 && index < AlpakaConfig.instance.itemPresets.length) {
            AlpakaConfig.ItemPreset preset = AlpakaConfig.instance.itemPresets[index];
            AlpakaConfig.instance.itemScale = preset.scale;
            AlpakaConfig.instance.itemXOffset = preset.xOffset;
            AlpakaConfig.instance.itemYOffset = preset.yOffset;
            AlpakaConfig.instance.itemZOffset = preset.zOffset;
            AlpakaConfig.instance.itemRotationX = preset.rotationX;
            AlpakaConfig.instance.itemRotationY = preset.rotationY;
            AlpakaConfig.instance.itemRotationZ = preset.rotationZ;
            AlpakaConfig.instance.itemSwingSpeed = preset.swingSpeed;
            AlpakaConfig.instance.itemSwayDisabled = preset.swayDisabled;
            AlpakaConfig.instance.itemSwingTranslationDisabled = preset.swingTranslationDisabled;
            AlpakaConfig.instance.itemNoEquipEnabled = preset.noEquipEnabled;
            AlpakaConfig.instance.itemSwingAlwaysFinishEnabled = preset.swingAlwaysFinishEnabled;
            AlpakaConfig.instance.swingDriftX = preset.swingDriftX;
            AlpakaConfig.instance.swingDriftY = preset.swingDriftY;
            AlpakaConfig.instance.swingDriftZ = preset.swingDriftZ;
            AlpakaConfig.instance.swingArcX = preset.swingArcX;
            AlpakaConfig.instance.swingArcY = preset.swingArcY;
            AlpakaConfig.instance.swingArcZ = preset.swingArcZ;
            AlpakaConfig.save();
            if (this.minecraft != null) {
                this.minecraft.setScreen(this);
            }
        }
    }

    private void savePreset(int index) {
        if (AlpakaConfig.instance.itemPresets != null && index >= 0 && index < AlpakaConfig.instance.itemPresets.length) {
            AlpakaConfig.instance.itemPresets[index] = new AlpakaConfig.ItemPreset(
                    AlpakaConfig.instance.itemScale,
                    AlpakaConfig.instance.itemXOffset,
                    AlpakaConfig.instance.itemYOffset,
                    AlpakaConfig.instance.itemZOffset,
                    AlpakaConfig.instance.itemRotationX,
                    AlpakaConfig.instance.itemRotationY,
                    AlpakaConfig.instance.itemRotationZ,
                    AlpakaConfig.instance.itemSwingSpeed,
                    AlpakaConfig.instance.itemSwayDisabled,
                    AlpakaConfig.instance.itemSwingTranslationDisabled,
                    AlpakaConfig.instance.itemNoEquipEnabled,
                    AlpakaConfig.instance.itemSwingAlwaysFinishEnabled,
                    AlpakaConfig.instance.swingDriftX,
                    AlpakaConfig.instance.swingDriftY,
                    AlpakaConfig.instance.swingDriftZ,
                    AlpakaConfig.instance.swingArcX,
                    AlpakaConfig.instance.swingArcY,
                    AlpakaConfig.instance.swingArcZ
            );
            AlpakaConfig.save();
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}

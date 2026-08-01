package net.alpaka.addons.client;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class AlpakaConfigScreen extends Screen {
    private final Screen parent;

    public AlpakaConfigScreen(Screen parent) {
        super(Component.literal("Alpaka Addons Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerY = this.height / 2 - 130;

        // Add Title
        this.addRenderableWidget(new StringWidget(0, 8, this.width, 20, this.title, this.font));

        // 1. Show Hand in 3rd Person
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY, 150, 20, 
                Component.literal("Show Hand in 3rd Person"), this.font));

        Button toggleButton1 = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.renderHandInThirdPerson),
                button -> {
                    AlpakaConfig.instance.renderHandInThirdPerson = !AlpakaConfig.instance.renderHandInThirdPerson;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.renderHandInThirdPerson));
                }
        )
        .bounds(this.width / 2 + 5, centerY, 150, 20)
        .build();
        this.addRenderableWidget(toggleButton1);

        // 2. Slayer Drop Tracker
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 22, 150, 20, 
                Component.literal("Slayer Drop Tracker"), this.font));

        Button toggleButton2 = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.slayerDropTrackerEnabled),
                button -> {
                    AlpakaConfig.instance.slayerDropTrackerEnabled = !AlpakaConfig.instance.slayerDropTrackerEnabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.slayerDropTrackerEnabled));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 22, 150, 20)
        .build();
        this.addRenderableWidget(toggleButton2);

        // 3. Slayer Party Commands
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 44, 150, 20, 
                Component.literal("Slayer Party Commands"), this.font));

        Button toggleButton3 = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.partyCommandsEnabled),
                button -> {
                    AlpakaConfig.instance.partyCommandsEnabled = !AlpakaConfig.instance.partyCommandsEnabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.partyCommandsEnabled));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 44, 150, 20)
        .build();
        this.addRenderableWidget(toggleButton3);

        // 4. Custom Sounds
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 66, 150, 20, 
                Component.literal("Custom Sounds"), this.font));

        Button toggleButtonSound = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.customSoundsEnabled),
                button -> {
                    AlpakaConfig.instance.customSoundsEnabled = !AlpakaConfig.instance.customSoundsEnabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.customSoundsEnabled));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 66, 150, 20)
        .build();
        this.addRenderableWidget(toggleButtonSound);

        // 5. Name Highlighting
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 88, 150, 20, 
                Component.literal("Name Highlighting"), this.font));

        Button toggleButton4 = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.nameHighlightingEnabled),
                button -> {
                    AlpakaConfig.instance.nameHighlightingEnabled = !AlpakaConfig.instance.nameHighlightingEnabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.nameHighlightingEnabled));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 88, 150, 20)
        .build();
        this.addRenderableWidget(toggleButton4);

        // 6. Inventory Snowflakes
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 110, 150, 20, 
                Component.literal("Inventory Snowflakes"), this.font));

        Button toggleButton5 = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.inventorySnowEnabled),
                button -> {
                    AlpakaConfig.instance.inventorySnowEnabled = !AlpakaConfig.instance.inventorySnowEnabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.inventorySnowEnabled));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 110, 150, 20)
        .build();
        this.addRenderableWidget(toggleButton5);

        // 7. Snow Animation Speed Slider
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 132, 150, 20, 
                Component.literal("Snow Animation Speed"), this.font));

        AbstractSliderButton speedSlider = new AbstractSliderButton(
                this.width / 2 + 5, centerY + 132, 150, 20,
                Component.literal(String.format("%.1fx", AlpakaConfig.instance.inventorySnowSpeed)),
                (AlpakaConfig.instance.inventorySnowSpeed - 0.1) / 4.9
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(String.format("%.1fx", AlpakaConfig.instance.inventorySnowSpeed)));
            }

            @Override
            protected void applyValue() {
                AlpakaConfig.instance.inventorySnowSpeed = (float) (0.1 + this.value * 4.9);
                AlpakaConfig.save();
            }
        };
        this.addRenderableWidget(speedSlider);

        // 8. Clean Blaze Toggle
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 154, 150, 20, 
                Component.literal("Clean Blaze"), this.font));

        Button toggleButton6 = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.cleanBlazeEnabled),
                button -> {
                    AlpakaConfig.instance.cleanBlazeEnabled = !AlpakaConfig.instance.cleanBlazeEnabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.cleanBlazeEnabled));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 154, 150, 20)
        .build();
        this.addRenderableWidget(toggleButton6);

        // 9. Smooth Perspective Toggle
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 176, 150, 20, 
                Component.literal("Smooth Perspective"), this.font));

        Button toggleButton7 = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.smoothPerspectiveEnabled),
                button -> {
                    AlpakaConfig.instance.smoothPerspectiveEnabled = !AlpakaConfig.instance.smoothPerspectiveEnabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.smoothPerspectiveEnabled));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 176, 150, 20)
        .build();
        this.addRenderableWidget(toggleButton7);

        // 10. Smooth Perspective Duration Slider
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 198, 150, 20, 
                Component.literal("Transition Duration"), this.font));

        AbstractSliderButton durationSlider = new AbstractSliderButton(
                this.width / 2 + 5, centerY + 198, 150, 20,
                Component.literal(AlpakaConfig.instance.smoothPerspectiveDurationMs + " ms"),
                (AlpakaConfig.instance.smoothPerspectiveDurationMs - 100) / 900.0
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(AlpakaConfig.instance.smoothPerspectiveDurationMs + " ms"));
            }

            @Override
            protected void applyValue() {
                AlpakaConfig.instance.smoothPerspectiveDurationMs = 100 + (int) Math.round(this.value * 900.0);
                AlpakaConfig.save();
            }
        };
        this.addRenderableWidget(durationSlider);

        // 11. Dark Mode Skyblock Toggle
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 220, 150, 20, 
                Component.literal("Dark Mode Skyblock"), this.font));

        Button toggleButton8 = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.darkModeSkyblockEnabled),
                button -> {
                    AlpakaConfig.instance.darkModeSkyblockEnabled = !AlpakaConfig.instance.darkModeSkyblockEnabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.darkModeSkyblockEnabled));
                    net.alpaka.addons.features.darkmode.DarkModeSkyblockFeature.applyState(AlpakaConfig.instance.darkModeSkyblockEnabled);
                }
        )
        .bounds(this.width / 2 + 5, centerY + 220, 150, 20)
        .build();
        this.addRenderableWidget(toggleButton8);

        // 12. Custom Escape Menu Toggle
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 242, 150, 20, 
                Component.literal("Custom Escape Menu"), this.font));

        Button toggleButton9 = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.customEscapeMenuEnabled),
                button -> {
                    AlpakaConfig.instance.customEscapeMenuEnabled = !AlpakaConfig.instance.customEscapeMenuEnabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.customEscapeMenuEnabled));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 242, 150, 20)
        .build();
        this.addRenderableWidget(toggleButton9);

        // Add Done Button
        this.addRenderableWidget(Button.builder(
                CommonComponents.GUI_DONE,
                button -> this.onClose()
        )
        .bounds(this.width / 2 - 100, centerY + 270, 200, 20)
        .build());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}

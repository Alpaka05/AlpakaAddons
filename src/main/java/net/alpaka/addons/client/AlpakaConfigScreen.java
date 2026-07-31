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
        int centerY = this.height / 2 - 85;

        // Add Title
        this.addRenderableWidget(new StringWidget(0, 10, this.width, 20, this.title, this.font));

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
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 25, 150, 20, 
                Component.literal("Slayer Drop Tracker"), this.font));

        Button toggleButton2 = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.slayerDropTrackerEnabled),
                button -> {
                    AlpakaConfig.instance.slayerDropTrackerEnabled = !AlpakaConfig.instance.slayerDropTrackerEnabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.slayerDropTrackerEnabled));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 25, 150, 20)
        .build();
        this.addRenderableWidget(toggleButton2);

        // 3. Slayer Party Commands
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 50, 150, 20, 
                Component.literal("Slayer Party Commands"), this.font));

        Button toggleButton3 = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.partyCommandsEnabled),
                button -> {
                    AlpakaConfig.instance.partyCommandsEnabled = !AlpakaConfig.instance.partyCommandsEnabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.partyCommandsEnabled));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 50, 150, 20)
        .build();
        this.addRenderableWidget(toggleButton3);

        // 4. Name Highlighting
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 75, 150, 20, 
                Component.literal("Name Highlighting"), this.font));

        Button toggleButton4 = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.nameHighlightingEnabled),
                button -> {
                    AlpakaConfig.instance.nameHighlightingEnabled = !AlpakaConfig.instance.nameHighlightingEnabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.nameHighlightingEnabled));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 75, 150, 20)
        .build();
        this.addRenderableWidget(toggleButton4);

        // 5. Inventory Snowflakes
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 100, 150, 20, 
                Component.literal("Inventory Snowflakes"), this.font));

        Button toggleButton5 = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.inventorySnowEnabled),
                button -> {
                    AlpakaConfig.instance.inventorySnowEnabled = !AlpakaConfig.instance.inventorySnowEnabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.inventorySnowEnabled));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 100, 150, 20)
        .build();
        this.addRenderableWidget(toggleButton5);

        // 6. Snow Animation Speed Slider
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 125, 150, 20, 
                Component.literal("Snow Animation Speed"), this.font));

        AbstractSliderButton speedSlider = new AbstractSliderButton(
                this.width / 2 + 5, centerY + 125, 150, 20,
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

        // 7. Clean Blaze Toggle
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 150, 150, 20, 
                Component.literal("Clean Blaze"), this.font));

        Button toggleButton6 = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.cleanBlazeEnabled),
                button -> {
                    AlpakaConfig.instance.cleanBlazeEnabled = !AlpakaConfig.instance.cleanBlazeEnabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.cleanBlazeEnabled));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 150, 150, 20)
        .build();
        this.addRenderableWidget(toggleButton6);

        // Add Done Button
        this.addRenderableWidget(Button.builder(
                CommonComponents.GUI_DONE,
                button -> this.onClose()
        )
        .bounds(this.width / 2 - 100, centerY + 180, 200, 20)
        .build());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}

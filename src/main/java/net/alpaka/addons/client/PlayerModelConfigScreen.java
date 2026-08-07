package net.alpaka.addons.client;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.playermodel.PlayerModelHudEditorScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class PlayerModelConfigScreen extends Screen {
    private final Screen parent;

    public PlayerModelConfigScreen(Screen parent) {
        super(Component.literal("Player Model Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerY = this.height / 2 - 50;

        // Title
        this.addRenderableWidget(new StringWidget(0, 8, this.width, 20, this.title, this.font));

        // 1. Enable Player Model
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY, 150, 20,
                Component.literal("Enable Player Model"), this.font));

        Button toggleEnable = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.playerModelEnabled),
                button -> {
                    AlpakaConfig.instance.playerModelEnabled = !AlpakaConfig.instance.playerModelEnabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.playerModelEnabled));
                }
        )
        .bounds(this.width / 2 + 5, centerY, 150, 20)
        .build();
        this.addRenderableWidget(toggleEnable);

        // 2. Only Show on Actions
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 25, 150, 20,
                Component.literal("Only Show on Actions"), this.font));

        Button toggleOnlyActions = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.playerModelOnlyActions),
                button -> {
                    AlpakaConfig.instance.playerModelOnlyActions = !AlpakaConfig.instance.playerModelOnlyActions;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.playerModelOnlyActions));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 25, 150, 20)
        .build();
        this.addRenderableWidget(toggleOnlyActions);

        // 3. Disable Movement
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 50, 150, 20,
                Component.literal("Disable Movement Animations"), this.font));

        Button toggleDisableMovement = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.playerModelDisableMovement),
                button -> {
                    AlpakaConfig.instance.playerModelDisableMovement = !AlpakaConfig.instance.playerModelDisableMovement;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.playerModelDisableMovement));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 50, 150, 20)
        .build();
        this.addRenderableWidget(toggleDisableMovement);

        // 4. Edit HUD Layout Button
        Button editHudButton = Button.builder(
                Component.literal("Edit HUD Layout"),
                button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new PlayerModelHudEditorScreen(this));
                    }
                }
        )
        .bounds(this.width / 2 - 100, centerY + 80, 200, 20)
        .build();
        this.addRenderableWidget(editHudButton);

        // Done Button
        this.addRenderableWidget(Button.builder(
                CommonComponents.GUI_DONE,
                button -> this.onClose()
        )
        .bounds(this.width / 2 - 100, centerY + 110, 200, 20)
        .build());
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}

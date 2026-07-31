package net.alpaka.addons.client;

import net.alpaka.addons.config.AlpakaConfig;
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
        // Add Title
        this.addRenderableWidget(new StringWidget(0, 20, this.width, 20, this.title, this.font));

        // Add Label for the setting
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, this.height / 2 - 10, 150, 20, 
                Component.literal("Show Hand in 3rd Person"), this.font));

        // Add Toggle Button
        Button toggleButton = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.renderHandInThirdPerson),
                button -> {
                    AlpakaConfig.instance.renderHandInThirdPerson = !AlpakaConfig.instance.renderHandInThirdPerson;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.renderHandInThirdPerson));
                }
        )
        .bounds(this.width / 2 + 5, this.height / 2 - 10, 150, 20)
        .build();

        this.addRenderableWidget(toggleButton);

        // Add Done Button
        this.addRenderableWidget(Button.builder(
                CommonComponents.GUI_DONE,
                button -> this.onClose()
        )
        .bounds(this.width / 2 - 100, this.height / 2 + 30, 200, 20)
        .build());
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}

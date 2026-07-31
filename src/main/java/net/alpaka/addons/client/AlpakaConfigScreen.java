package net.alpaka.addons.client;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;
import net.minecraft.screen.ScreenTexts;

public class AlpakaConfigScreen extends Screen {
    private final Screen parent;

    public AlpakaConfigScreen(Screen parent) {
        super(Text.literal("Alpaka Addons Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Add Title
        this.addDrawableChild(new TextWidget(0, 20, this.width, 20, this.title, this.textRenderer));

        // Add Label for the setting
        this.addDrawableChild(new TextWidget(this.width / 2 - 155, this.height / 2 - 10, 150, 20, 
                Text.literal("Show Hand in 3rd Person"), this.textRenderer));

        // Add Toggle Button
        ButtonWidget toggleButton = ButtonWidget.builder(
                ScreenTexts.onOrOff(AlpakaConfig.instance.renderHandInThirdPerson),
                button -> {
                    AlpakaConfig.instance.renderHandInThirdPerson = !AlpakaConfig.instance.renderHandInThirdPerson;
                    AlpakaConfig.save();
                    button.setMessage(ScreenTexts.onOrOff(AlpakaConfig.instance.renderHandInThirdPerson));
                }
        )
        .dimensions(this.width / 2 + 5, this.height / 2 - 10, 150, 20)
        .build();

        this.addDrawableChild(toggleButton);

        // Add Done Button
        this.addDrawableChild(ButtonWidget.builder(
                ScreenTexts.DONE,
                button -> this.close()
        )
        .dimensions(this.width / 2 - 100, this.height / 2 + 30, 200, 20)
        .build());
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}

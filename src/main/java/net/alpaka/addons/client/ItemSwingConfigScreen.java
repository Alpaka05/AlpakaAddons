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

public class ItemSwingConfigScreen extends Screen {
    private final Screen parent;

    public ItemSwingConfigScreen(Screen parent) {
        super(Component.literal("Swing Animation Customizations"));
        this.parent = parent;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x66000000);
    }

    @Override
    protected void init() {
        int centerY = this.height / 2 - 100;

        // Title
        this.addRenderableWidget(new StringWidget(0, 8, this.width, 20, this.title, this.font));

        // 1. Swing Drift X (-100 to 100)
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY, 150, 20,
                Component.literal("Swing Drift X (Width)"), this.font));
        AbstractSliderButton driftXSlider = new AbstractSliderButton(
                this.width / 2 + 5, centerY, 150, 20,
                Component.literal(String.format("%.1f", AlpakaConfig.instance.swingDriftX)),
                (AlpakaConfig.instance.swingDriftX + 100.0f) / 200.0f
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(String.format("%.1f", AlpakaConfig.instance.swingDriftX)));
            }
            @Override
            protected void applyValue() {
                AlpakaConfig.instance.swingDriftX = (float) Math.round(-100.0f + this.value * 200.0f);
                AlpakaConfig.save();
            }
        };
        this.addRenderableWidget(driftXSlider);

        // 2. Swing Drift Y (-100 to 100)
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 24, 150, 20,
                Component.literal("Swing Drift Y (Height)"), this.font));
        AbstractSliderButton driftYSlider = new AbstractSliderButton(
                this.width / 2 + 5, centerY + 24, 150, 20,
                Component.literal(String.format("%.1f", AlpakaConfig.instance.swingDriftY)),
                (AlpakaConfig.instance.swingDriftY + 100.0f) / 200.0f
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(String.format("%.1f", AlpakaConfig.instance.swingDriftY)));
            }
            @Override
            protected void applyValue() {
                AlpakaConfig.instance.swingDriftY = (float) Math.round(-100.0f + this.value * 200.0f);
                AlpakaConfig.save();
            }
        };
        this.addRenderableWidget(driftYSlider);

        // 3. Swing Drift Z (-100 to 100)
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 48, 150, 20,
                Component.literal("Swing Drift Z (Depth)"), this.font));
        AbstractSliderButton driftZSlider = new AbstractSliderButton(
                this.width / 2 + 5, centerY + 48, 150, 20,
                Component.literal(String.format("%.1f", AlpakaConfig.instance.swingDriftZ)),
                (AlpakaConfig.instance.swingDriftZ + 100.0f) / 200.0f
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(String.format("%.1f", AlpakaConfig.instance.swingDriftZ)));
            }
            @Override
            protected void applyValue() {
                AlpakaConfig.instance.swingDriftZ = (float) Math.round(-100.0f + this.value * 200.0f);
                AlpakaConfig.save();
            }
        };
        this.addRenderableWidget(driftZSlider);

        // 4. Swing Arc X (-180 to 180)
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 76, 150, 20,
                Component.literal("Swing Rotation X"), this.font));
        AbstractSliderButton arcXSlider = new AbstractSliderButton(
                this.width / 2 + 5, centerY + 76, 150, 20,
                Component.literal(String.format("%.0f°", AlpakaConfig.instance.swingArcX)),
                (AlpakaConfig.instance.swingArcX + 180.0f) / 360.0f
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(String.format("%.0f°", AlpakaConfig.instance.swingArcX)));
            }
            @Override
            protected void applyValue() {
                AlpakaConfig.instance.swingArcX = (float) Math.round(-180.0f + this.value * 360.0f);
                AlpakaConfig.save();
            }
        };
        this.addRenderableWidget(arcXSlider);

        // 5. Swing Arc Y (-180 to 180)
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 100, 150, 20,
                Component.literal("Swing Rotation Y"), this.font));
        AbstractSliderButton arcYSlider = new AbstractSliderButton(
                this.width / 2 + 5, centerY + 100, 150, 20,
                Component.literal(String.format("%.0f°", AlpakaConfig.instance.swingArcY)),
                (AlpakaConfig.instance.swingArcY + 180.0f) / 360.0f
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(String.format("%.0f°", AlpakaConfig.instance.swingArcY)));
            }
            @Override
            protected void applyValue() {
                AlpakaConfig.instance.swingArcY = (float) Math.round(-180.0f + this.value * 360.0f);
                AlpakaConfig.save();
            }
        };
        this.addRenderableWidget(arcYSlider);

        // 6. Swing Arc Z (-180 to 180)
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 124, 150, 20,
                Component.literal("Swing Rotation Z"), this.font));
        AbstractSliderButton arcZSlider = new AbstractSliderButton(
                this.width / 2 + 5, centerY + 124, 150, 20,
                Component.literal(String.format("%.0f°", AlpakaConfig.instance.swingArcZ)),
                (AlpakaConfig.instance.swingArcZ + 180.0f) / 360.0f
        ) {
            @Override
            protected void updateMessage() {
                setMessage(Component.literal(String.format("%.0f°", AlpakaConfig.instance.swingArcZ)));
            }
            @Override
            protected void applyValue() {
                AlpakaConfig.instance.swingArcZ = (float) Math.round(-180.0f + this.value * 360.0f);
                AlpakaConfig.save();
            }
        };
        this.addRenderableWidget(arcZSlider);

        // 7. Disable Forward Swing Toggle
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 152, 150, 20,
                Component.literal("Disable Forward Swing"), this.font));
        Button toggleSwingTrans = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.itemSwingTranslationDisabled),
                button -> {
                    AlpakaConfig.instance.itemSwingTranslationDisabled = !AlpakaConfig.instance.itemSwingTranslationDisabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.itemSwingTranslationDisabled));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 152, 150, 20)
        .build();
        this.addRenderableWidget(toggleSwingTrans);

        // 8. Always Finish Swing Toggle
        this.addRenderableWidget(new StringWidget(this.width / 2 - 155, centerY + 176, 150, 20,
                Component.literal("Always Finish Swing"), this.font));
        Button toggleAlwaysFinish = Button.builder(
                CommonComponents.optionStatus(AlpakaConfig.instance.itemSwingAlwaysFinishEnabled),
                button -> {
                    AlpakaConfig.instance.itemSwingAlwaysFinishEnabled = !AlpakaConfig.instance.itemSwingAlwaysFinishEnabled;
                    AlpakaConfig.save();
                    button.setMessage(CommonComponents.optionStatus(AlpakaConfig.instance.itemSwingAlwaysFinishEnabled));
                }
        )
        .bounds(this.width / 2 + 5, centerY + 176, 150, 20)
        .build();
        this.addRenderableWidget(toggleAlwaysFinish);

        // Done button
        this.addRenderableWidget(Button.builder(
                CommonComponents.GUI_DONE,
                button -> this.onClose()
        )
        .bounds(this.width / 2 - 100, centerY + 208, 200, 20)
        .build());
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

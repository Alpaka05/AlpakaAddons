package net.alpaka.addons.mixin;

import net.alpaka.addons.features.chat.ChatTabsFeature;
import net.alpaka.addons.features.chat.ScreenshotMessageFeature;
import net.alpaka.addons.features.slayer.SlayerHudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The mod's additions to the chat screen: the slayer HUD click, the chat tabs, the screenshot
 * buttons and the send-to-channel redirect.
 *
 * The chat screen is the one place the HUD is drawn while a cursor exists, which is what makes the
 * HUD click possible at all - the rest of the time the mouse is captured for looking around. Every
 * click here is only consumed when it actually lands on something of ours, so chat itself behaves
 * exactly as before everywhere else on screen.
 */
@Mixin(ChatScreen.class)
public class ChatScreenMixin {

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (SlayerHudElement.INSTANCE.handleChatClick(event.x(), event.y())) {
            cir.setReturnValue(true);
        }
    }

    /**
     * The tab row's click. Placed after vanilla has offered the click to the command suggestions,
     * at the first read of the button, so a suggestion popup lying over the tabs keeps its click.
     */
    @Inject(
            method = "mouseClicked",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/MouseButtonEvent;button()I", ordinal = 0),
            cancellable = true
    )
    private void alpaka$clickChatTab(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        ChatScreen self = (ChatScreen) (Object) this;
        if (ChatTabsFeature.handleClick(event.x(), event.y(), event.button(), self.height)) {
            cir.setReturnValue(true);
        }
    }

    /** Draws the tab row before the command suggestions, so a popup covers the tabs and not the reverse. */
    @Inject(
            method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/CommandSuggestions;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V")
    )
    private void alpaka$drawChatTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ChatScreen self = (ChatScreen) (Object) this;
        ChatTabsFeature.render(graphics, self.height, mouseX, mouseY);
    }

    /**
     * The mod's own click events, before vanilla's dispatch: vanilla answers a custom click event by
     * sending it to the server, and these are meant for this client alone.
     */
    @Inject(method = "handleComponentClicked", at = @At("HEAD"), cancellable = true)
    private void alpaka$handleModClickEvents(Style clicked, boolean allowInsertions, CallbackInfoReturnable<Boolean> cir) {
        if (allowInsertions) return;
        if (clicked.getClickEvent() instanceof ClickEvent.Custom custom && ScreenshotMessageFeature.handleClick(custom)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Sends a plain message typed on a channel tab to that channel, when the toggle for it is on.
     * Mirrors the vanilla method - normalise, remember, send - only sending a command instead.
     */
    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void alpaka$sendToTabChannel(String message, boolean addToRecent, CallbackInfo ci) {
        ChatScreen self = (ChatScreen) (Object) this;
        String normalized = self.normalizeChatMessage(message);
        String command = ChatTabsFeature.channelCommand(normalized);
        if (command == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (addToRecent) {
            mc.gui.hud.getChat().addRecentChat(normalized);
        }
        if (mc.player != null) {
            mc.player.connection.sendCommand(command);
        }
        ci.cancel();
    }
}

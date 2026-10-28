package net.alpaka.addons.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.alpaka.addons.features.chat.ChatCopyFeature;
import net.alpaka.addons.features.chat.ChatOpenAnimation;
import net.alpaka.addons.features.chat.ChatSearchFeature;
import net.alpaka.addons.features.chat.ChatTabsFeature;
import net.alpaka.addons.features.chat.ScreenshotMessageFeature;
import net.alpaka.addons.features.slayer.SlayerHudElement;
import net.alpaka.addons.utils.UpdateInstaller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The mod's additions to the chat screen: the slayer HUD click, Copy Chat, the chat tabs, the
 * screenshot buttons and the send-to-channel redirect.
 *
 * The chat screen is the one place the HUD is drawn while a cursor exists, which is what makes the
 * HUD click possible at all - the rest of the time the mouse is captured for looking around. Every
 * click here is only consumed when it actually lands on something of ours, so chat itself behaves
 * exactly as before everywhere else on screen.
 */
@Mixin(ChatScreen.class)
public class ChatScreenMixin {
    @Shadow private CommandSuggestions commandSuggestions;
    @Shadow protected EditBox input;

    /** Whether this chat screen has been set up once; a resize runs init again, which is no new opening. */
    @Unique private boolean alpaka$opened;

    @Inject(method = "init", at = @At("HEAD"))
    private void alpaka$startOpenAnimation(CallbackInfo ci) {
        if (this.alpaka$opened) return;
        this.alpaka$opened = true;
        ChatOpenAnimation.onOpen();
    }

    /**
     * Opening Animation: the input bar and the text in it are drawn shifted down by the part of the
     * slide still to go, so the bar rises from below the screen's bottom edge as the chat unrolls.
     * The bar is the screen's first fill; the input box is drawn by the screen's own pass.
     */
    @Inject(method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 0))
    private void alpaka$lowerInputBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        this.alpaka$pushInputSlide(graphics);
    }

    @Inject(method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 0, shift = At.Shift.AFTER))
    private void alpaka$restoreAfterInputBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        graphics.pose().popMatrix();
    }

    @Inject(method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void alpaka$lowerInputBox(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        this.alpaka$pushInputSlide(graphics);
    }

    @Inject(method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V", shift = At.Shift.AFTER))
    private void alpaka$restoreAfterInputBox(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        graphics.pose().popMatrix();
    }

    /** Always pushes, so the matching pop after the draw is unconditional. */
    @Unique
    private void alpaka$pushInputSlide(GuiGraphicsExtractor graphics) {
        graphics.pose().pushMatrix();
        float rest = 1.0f - ChatOpenAnimation.progress();
        // The bar is 14 pixels from the bottom edge to its top: that far down, it is just out of sight.
        if (rest > 0.0f) graphics.pose().translate(0.0f, rest * 14.0f);
    }

    /**
     * Tab cycles the chat tabs, Shift+Tab the other way. Left to vanilla while a command is being
     * typed or a suggestion popup is up, where Tab completes; otherwise it would only bring up the
     * player list, which the HUD hook holds back meanwhile.
     */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void alpaka$tabCyclesChatTabs(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        // Chat search: Ctrl+F toggles it - Cmd+F on macOS, the platform's own find shortcut - and
        // Enter sends nothing while it is on.
        if (ChatSearchFeature.isEnabled() && event.key() == InputConstants.KEY_F && event.hasControlDownWithQuirk()) {
            ChatSearchFeature.toggle(this.input);
            cir.setReturnValue(true);
            return;
        }
        if (ChatSearchFeature.isSearching()
                && (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER)) {
            cir.setReturnValue(true);
            return;
        }
        if (ChatTabsFeature.onChatScreenKey(event.key(), event.hasShiftDown(), event.hasControlDown(),
                net.alpaka.addons.client.KeyRepeat.isRepeat(),
                this.commandSuggestions != null && this.commandSuggestions.isVisible(),
                this.input == null ? "" : this.input.getValue())) {
            cir.setReturnValue(true);
        }
    }

    /** While searching, the typed text is the search, not a message: no command suggestions for it. */
    @Inject(method = "onEdited", at = @At("HEAD"), cancellable = true)
    private void alpaka$editSearch(String text, CallbackInfo ci) {
        if (ChatSearchFeature.isSearching()) {
            ChatSearchFeature.onEdited(text, this.input);
            ci.cancel();
        }
    }

    /** Before vanilla saves the draft: the search text must not become the draft. */
    @Inject(method = "removed", at = @At("HEAD"))
    private void alpaka$leaveSearchOnClose(CallbackInfo ci) {
        ChatSearchFeature.exit(this.input);
        ChatTabsFeature.onChatClosed();
    }

    /** The input bar's background takes the accent while searching. */
    @ModifyArg(
            method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"),
            index = 4
    )
    private int alpaka$tintSearchBar(int color) {
        return ChatSearchFeature.isSearching() ? ChatSearchFeature.barColor() : color;
    }

    /**
     * The slayer HUD's click and Copy Chat's right-click, once vanilla has offered the click to the
     * chat's own links and found none: only then does it get handed to the screen, which is where
     * this sits. At the head of the method, as it used to be, the HUD took clicks meant for links
     * drawn under it.
     */
    @Inject(
            method = "mouseClicked",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z"),
            cancellable = true
    )
    private void onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (SlayerHudElement.INSTANCE.handleChatClick(event.x(), event.y(), event.button())
                || ChatCopyFeature.handleClick(event.button(), event.hasShiftDown())) {
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
        // The update button first: the screenshot handler claims every event in the mod's namespace.
        if (clicked.getClickEvent() instanceof ClickEvent.Custom custom
                && (UpdateInstaller.handleClick(custom) || ScreenshotMessageFeature.handleClick(custom))) {
            cir.setReturnValue(true);
        }
    }

    /**
     * A typed command whose first word is a Command Shortcuts alias goes out as what the alias
     * stands for, with whatever followed it. The chat history keeps it as typed.
     */
    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void alpaka$expandCommandAlias(String message, boolean addToRecent, CallbackInfo ci) {
        ChatScreen self = (ChatScreen) (Object) this;
        String normalized = self.normalizeChatMessage(message);
        if (!normalized.startsWith("/")) return;
        if (!net.alpaka.addons.features.shortcuts.CommandShortcuts.expandTyped(normalized.substring(1))) return;
        if (addToRecent) {
            Minecraft.getInstance().gui.hud.getChat().addRecentChat(normalized);
        }
        ci.cancel();
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
        net.alpaka.addons.compliance.Outbound.command(command, net.alpaka.addons.compliance.Outbound.Cause.CHAT_INPUT);
        ci.cancel();
    }
}

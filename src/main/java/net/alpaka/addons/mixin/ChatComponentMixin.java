package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.alpaka.addons.features.bridge.BridgeBotFormatter;
import net.alpaka.addons.features.chat.ChatBlurFeature;
import net.alpaka.addons.features.chat.ChatPeekFeature;
import net.alpaka.addons.features.chat.ChatTabsFeature;
import net.alpaka.addons.features.chat.CompactChatFeature;
import net.alpaka.addons.features.chat.ScreenshotMessageFeature;
import net.alpaka.addons.features.chat.SmoothChatFeature;
import net.alpaka.addons.features.guild.GuildPrefixFormatter;
import net.alpaka.addons.features.slayer.SlayerChatFilter;
import net.alpaka.addons.features.slayer.SlayerDropTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;

import java.util.List;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {
    private static final String EXTRACT_PUBLIC =
        "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V";
    private static final String EXTRACT_PRIVATE =
        "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V";
    private static final String FOR_EACH_LINE =
        "Lnet/minecraft/client/gui/components/ChatComponent;forEachLine(Lnet/minecraft/client/gui/components/ChatComponent$AlphaCalculator;Lnet/minecraft/client/gui/components/ChatComponent$LineConsumer;)I";

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private List<GuiMessage> allMessages;
    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow @Final private List<?> messageDeletionQueue;
    @Shadow private int chatScrollbarPos;

    @Shadow private int getLineHeight() { throw new AssertionError("replaced by mixin"); }
    @Shadow private double getScale() { throw new AssertionError("replaced by mixin"); }

    /** Lines a live message has added at the front of the display list so far; see smooth chat below. */
    @Unique private int alpaka$liveLines;

    @ModifyConstant(
        method = {"<init>", "addMessageToDisplayQueue", "addMessageToQueue", "addRecentChat"},
        constant = @Constant(intValue = 100)
    )
    private int modifyMaxChatHistory(int original) {
        return AlpakaConfig.instance.expandChatHistory ? 5000 : 100;
    }

    /**
     * Applies our guild-chat formatting to the copy of the message that gets drawn.
     *
     * ### Why the display queue and not addMessage
     *
     * The obvious place is the Component argument of addMessage, and that is where this used to sit.
     * It breaks chat-tab mods. Vanilla builds the GuiMessage there and only then runs it past
     * {@code visibleMessageFilter}, the hook a mod like Chatting or Hypixel Chat Tabs uses to decide
     * which tab a line belongs to - and those mods recognise guild chat by the line starting with
     * "Guild >". Rewriting the message first handed the filter a line that no longer looked like
     * guild chat, so the guild tab stayed empty.
     *
     * addMessageToDisplayQueue runs after every one of those decisions. Checked against the actual
     * bytecode of this version: the filter is consulted in exactly two places, addMessage and
     * refreshTrimmedMessages, and both test the message before calling this method. So the stored
     * history, the written chat log and every filter keep Hypixel's original wording, while only
     * the wrapped lines that reach the screen carry our formatting.
     *
     * Rebuilding the record rather than mutating it matters: the very same GuiMessage instance is
     * handed to addMessageToQueue straight afterwards, and that one has to stay untouched.
     *
     * The cost of living here is that the work repeats whenever the chat is re-wrapped, since
     * refreshTrimmedMessages replays the stored messages through this method. That is a window
     * resize, not a per-frame path, and each replay starts from the original text, so it cannot
     * compound.
     */
    @ModifyVariable(method = "addMessageToDisplayQueue", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private GuiMessage formatGuildMessage(GuiMessage message) {
        Component original = message.content();
        Component component = original;

        // Bridge formatting first, on purpose. It recognises a relay by the line starting with
        // Hypixel's "Guild > " marker, so swapping that marker out beforehand would leave it with
        // nothing to match. It puts the marker back on the line it rebuilds, which is what lets the
        // guild tag below reach a relay too - so a relay carries the same tag as everything else.
        Component reformatted = BridgeBotFormatter.reformat(component);
        if (reformatted != null) component = reformatted;

        Component retagged = GuildPrefixFormatter.rewrite(component);
        if (retagged != null) component = retagged;

        if (component == original) return message;
        return new GuiMessage(message.addedTime(), component, message.signature(), message.source(), message.tag());
    }

    @Inject(
        method = "addMessage",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onAddMessage(Component component, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        // Cancelling here rather than in a receive event is deliberate for both of these: this runs
        // downstream of every listener, so the message is kept out of the visible log while the
        // slayer tracking that reads it still sees it.
        if (SlayerDropTracker.shouldHideDropMessage(component)
                || SlayerChatFilter.shouldCancelChatMessage(component.getString())) {
            ci.cancel();
            return;
        }
        // The screenshot notice: replaced by the line with buttons, or held back until the automatic
        // copy is done. The replacement comes back in through addClientSystemMessage, as a plain
        // literal that this hook then lets through.
        ChatComponent self = (ChatComponent) (Object) this;
        if (ScreenshotMessageFeature.handleNotice(component,
                replacement -> this.minecraft.execute(() -> self.addClientSystemMessage(replacement)))) {
            ci.cancel();
            return;
        }
        // Only messages that actually reach the chat count towards a tab's unread number.
        ChatTabsFeature.onMessage(component);
    }

    /**
     * Compact chat, on the one place the record for a new message is built. Every filter that may
     * cancel the message has run by now, and nothing has stored or laid it out yet - so a repeat can
     * still take the previous copy out of both lists and go in with the counter in its place.
     */
    @WrapOperation(
        method = "addMessage",
        at = @At(value = "NEW", target = "net/minecraft/client/multiplayer/chat/GuiMessage")
    )
    private GuiMessage alpaka$compactRepeats(int addedTime, Component content, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, Operation<GuiMessage> original) {
        Component stored = CompactChatFeature.compact(content, this.allMessages, this.trimmedMessages);
        GuiMessage message = original.call(addedTime, stored, signature, source, tag);
        CompactChatFeature.onAdded(message);
        return message;
    }

    /**
     * Keeps the chat across a disconnect while Expand Chat History is on.
     *
     * Leaving a server runs onDisconnected, whose clearMessages(true) wipes both the messages and
     * the sent-message history; F3+D clears with false and is left alone, so the player can still
     * empty the chat on purpose. The two things the call did besides clearing - flushing the delayed
     * message queue and dropping pending deletions - still happen here.
     */
    @Inject(method = "clearMessages", at = @At("HEAD"), cancellable = true)
    private void alpaka$keepHistoryAcrossServers(boolean history, CallbackInfo ci) {
        if (history && AlpakaConfig.instance.expandChatHistory) {
            this.minecraft.gui.chatListener().flushQueue();
            this.messageDeletionQueue.clear();
            ci.cancel();
        }
    }

    @Inject(method = "clearMessages", at = @At("TAIL"))
    private void alpaka$forgetCompactedMessage(boolean history, CallbackInfo ci) {
        CompactChatFeature.forget();
        SmoothChatFeature.clear();
    }

    // ------------------------------------------------------------------ smooth chat

    /**
     * Smooth chat counts the lines a live message adds. A message is laid out into lines that go
     * in at the front of the display list one by one; the count is reported once the message is in,
     * so the slide covers the whole message at once.
     */
    @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"))
    private void alpaka$beginCountingLines(GuiMessage message, CallbackInfo ci) {
        this.alpaka$liveLines = 0;
    }

    @WrapOperation(
        method = "addMessageToDisplayQueue",
        at = @At(value = "INVOKE", target = "Ljava/util/List;addFirst(Ljava/lang/Object;)V")
    )
    private void alpaka$countAddedLine(List<GuiMessage.Line> lines, Object line, Operation<Void> original) {
        original.call(lines, line);
        this.alpaka$liveLines++;
    }

    @Inject(method = "addMessageToDisplayQueue", at = @At("RETURN"))
    private void alpaka$reportAddedLines(GuiMessage message, CallbackInfo ci) {
        SmoothChatFeature.onLinesAdded(this.alpaka$liveLines);
        this.alpaka$liveLines = 0;
    }

    /** A re-layout replays every stored message through the queue; none of that is a new arrival. */
    @Inject(method = "refreshTrimmedMessages", at = @At("HEAD"))
    private void alpaka$beginReplay(CallbackInfo ci) {
        SmoothChatFeature.clear();
        SmoothChatFeature.beginReplay();
    }

    @Inject(method = "refreshTrimmedMessages", at = @At("RETURN"))
    private void alpaka$endReplay(CallbackInfo ci) {
        SmoothChatFeature.endReplay();
    }

    /**
     * The slide: right after the chat applies its scale to the pose, the whole chat is moved down
     * by the height the arriving lines have not yet claimed, so the older lines glide up into place
     * as the new ones fade in. Not while scrolled up - the bottom of the list is off screen then.
     */
    @Inject(
        method = EXTRACT_PRIVATE,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;updatePose(Ljava/util/function/Consumer;)V",
            shift = At.Shift.AFTER
        )
    )
    private void alpaka$slideChat(ChatComponent.ChatGraphicsAccess access, int guiHeight, int ticks, ChatComponent.DisplayMode mode, CallbackInfo ci) {
        if (this.chatScrollbarPos != 0 || !SmoothChatFeature.isEnabled()) return;
        float offset = SmoothChatFeature.slideOffset(this.getLineHeight());
        if (offset > 0.01f) {
            access.updatePose(pose -> pose.translate(0.0f, offset));
        }
    }

    /**
     * The fade: an arriving line is drawn with the slide's progress as its alpha, on top of the
     * time fade vanilla already applies. The alpha stays just above zero rather than at it, so a
     * line at the very start of its fade is invisible instead of undefined.
     */
    @ModifyArgs(
        method = "forEachLine",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/ChatComponent$LineConsumer;accept(Lnet/minecraft/client/multiplayer/chat/GuiMessage$Line;IF)V"
        )
    )
    private void alpaka$fadeArrivingLines(Args args) {
        if (this.chatScrollbarPos != 0 || !SmoothChatFeature.isEnabled()) return;
        int index = args.get(1);
        float factor = SmoothChatFeature.lineAlpha(index);
        if (factor < 1.0f) {
            float alpha = args.get(2);
            args.set(2, alpha * Math.max(factor, 0.001f));
        }
    }

    // ------------------------------------------------------------------ blurred background

    @Inject(method = EXTRACT_PUBLIC, at = @At("HEAD"))
    private void alpaka$beginBlurExtraction(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY,
                                            ChatComponent.DisplayMode mode, boolean focused, CallbackInfo ci) {
        ChatBlurFeature.beginExtraction(graphics);
    }

    @Inject(method = EXTRACT_PUBLIC, at = @At("RETURN"))
    private void alpaka$endBlurExtraction(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY,
                                          ChatComponent.DisplayMode mode, boolean focused, CallbackInfo ci) {
        ChatBlurFeature.endExtraction();
    }

    @Inject(method = EXTRACT_PRIVATE, at = @At("HEAD"))
    private void alpaka$resetBlurPanel(ChatComponent.ChatGraphicsAccess access, int guiHeight, int ticks, ChatComponent.DisplayMode mode, CallbackInfo ci) {
        ChatBlurFeature.resetPanel();
    }

    /**
     * Vanilla's background pass draws one flat box per line through this lambda. With the blurred
     * panel on, each box is measured instead and the fill is skipped; the panel goes in for all of
     * them just before the text pass below. Clickable-text capture runs the same code without a
     * graphics to draw into, and is left alone.
     */
    @Inject(method = "lambda$extractRenderState$1", at = @At("HEAD"), cancellable = true)
    private static void alpaka$measureLineBox(int chatBottom, int lineHeight, ChatComponent.ChatGraphicsAccess access, int width,
                                              float backgroundOpacity, GuiMessage.Line line, int index, float alpha, CallbackInfo ci) {
        if (ChatBlurFeature.collectLine(chatBottom, lineHeight, width, backgroundOpacity, alpha)) {
            ci.cancel();
        }
    }

    @Inject(method = EXTRACT_PRIVATE, at = @At(value = "INVOKE", target = FOR_EACH_LINE, ordinal = 1))
    private void alpaka$submitBlurPanel(ChatComponent.ChatGraphicsAccess access, int guiHeight, int ticks, ChatComponent.DisplayMode mode, CallbackInfo ci) {
        ChatBlurFeature.submitPanel((float) this.getScale());
    }

    /**
     * The chat tabs' filter, wrapped around both places a message is laid out into visible lines:
     * on arrival and on the replay that rebuilds the lines after a resize or a tab switch.
     *
     * Wrapping the call rather than the method matters: this way the check sees the message as it
     * is stored, before the formatGuildMessage hook above rewrites the copy that gets drawn, which
     * is what lets a tab recognise guild chat that carries the custom guild tag on screen. The
     * stored history is untouched either way, so switching tabs never loses a message.
     */
    @WrapOperation(
        method = {"addMessage", "refreshTrimmedMessages"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessageToDisplayQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V")
    )
    private void alpaka$onlyActiveTab(ChatComponent chat, GuiMessage message, Operation<Void> original) {
        if (ChatTabsFeature.accepts(message)) {
            original.call(chat, message);
        }
    }

    /**
     * Chat peek: while the key is held the HUD chat takes the focused height, so as many lines show
     * as the chat screen would show.
     */
    @Inject(method = "getHeight()I", at = @At("HEAD"), cancellable = true)
    private void alpaka$peekHeight(CallbackInfoReturnable<Integer> cir) {
        if (ChatPeekFeature.isPeeking()) {
            cir.setReturnValue(ChatComponent.getHeight(this.minecraft.options.chatHeightFocused().get()));
        }
    }

    /**
     * Chat peek: the HUD's background draw becomes the foreground one, which is every line at full
     * opacity plus the scrollbar - the chat screen's look, without the chat screen.
     */
    @ModifyVariable(
        method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private ChatComponent.DisplayMode alpaka$peekDisplayMode(ChatComponent.DisplayMode mode) {
        if (mode == ChatComponent.DisplayMode.BACKGROUND && ChatPeekFeature.isPeeking()) {
            return ChatComponent.DisplayMode.FOREGROUND;
        }
        return mode;
    }
}

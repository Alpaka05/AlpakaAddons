package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.bridge.BridgeBotFormatter;
import net.alpaka.addons.features.guild.GuildPrefixFormatter;
import net.alpaka.addons.features.slayer.SlayerChatFilter;
import net.alpaka.addons.features.slayer.SlayerDropTracker;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {
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
        // nothing to match. A reformatted relay carries no marker any more and the guild tag below
        // then finds nothing to do, which is right: a relay is labelled [Discord], not by guild.
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
        }
    }
}

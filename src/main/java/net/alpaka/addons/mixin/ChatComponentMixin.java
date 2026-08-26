package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.bridge.BridgeBotFormatter;
import net.alpaka.addons.features.slayer.SlayerChatFilter;
import net.alpaka.addons.features.slayer.SlayerDropTracker;
import net.minecraft.client.gui.components.ChatComponent;
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
     * Reformats a bridge-bot relay in place.
     *
     * Rewriting the argument rather than cancelling and re-adding keeps the message's signature,
     * source and tag intact, and avoids re-entering this injector.
     */
    @ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Component formatBridgeMessage(Component component) {
        Component reformatted = BridgeBotFormatter.reformat(component);
        return reformatted != null ? reformatted : component;
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

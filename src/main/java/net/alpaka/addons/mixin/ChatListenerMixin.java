package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.alpaka.addons.features.chat.ChatOrigin;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Counts how deeply system messages nest, and tells the chat when the one being added is the
 * packet's own. See {@link ChatOrigin}.
 *
 * WrapMethod rather than an inject at the head, so the count also covers the receive events other
 * mods hook into the start of the method - a line a mod sends from one of those is nested.
 */
@Mixin(ChatListener.class)
public class ChatListenerMixin {

    @WrapMethod(method = "handleSystemMessage")
    private void alpaka$countNesting(Component message, boolean fromServer, Operation<Void> original) {
        ChatOrigin.enterListener();
        try {
            original.call(message, fromServer);
        } finally {
            ChatOrigin.leaveListener();
        }
    }

    @WrapOperation(
        method = "handleSystemMessage",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;addServerSystemMessage(Lnet/minecraft/network/chat/Component;)V")
    )
    private void alpaka$flagServerAdd(ChatComponent chat, Component message, Operation<Void> original) {
        ChatOrigin.beginAdd();
        try {
            original.call(chat, message);
        } finally {
            ChatOrigin.endAdd();
        }
    }
}

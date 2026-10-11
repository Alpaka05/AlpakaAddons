package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.alpaka.addons.features.chat.ChatOrigin;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Marks the system message a server packet carries, for the chat's Server and Mods tabs. */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerChatMixin {

    @WrapOperation(
        method = "handleSystemChat",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/chat/ChatListener;handleSystemMessage(Lnet/minecraft/network/chat/Component;Z)V")
    )
    private void alpaka$markServerMessage(ChatListener listener, Component message, boolean fromServer, Operation<Void> original) {
        int previous = ChatOrigin.beginPacket();
        try {
            original.call(listener, message, fromServer);
        } finally {
            ChatOrigin.endPacket(previous);
        }
    }
}

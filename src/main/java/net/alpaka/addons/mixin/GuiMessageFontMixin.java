package net.alpaka.addons.mixin;

import net.alpaka.addons.client.gui.GuiFont;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Writes chat in the menu font when that is switched on.
 *
 * The font goes onto the message before it is broken into lines, so the lines are measured in the
 * font they are drawn in; everything after - drawing, hover and click lookups - reads the styles
 * the lines carry. Switching the option re-wraps the chat ({@link GuiFont#refreshChat}).
 */
@Mixin(GuiMessage.class)
public abstract class GuiMessageFontMixin {

    @ModifyArg(method = "splitLines", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ComponentRenderUtils;wrapComponents(Lnet/minecraft/network/chat/FormattedText;ILnet/minecraft/client/gui/Font;)Ljava/util/List;"), index = 0)
    private FormattedText alpaka$chatInMenuFont(FormattedText content) {
        return GuiFont.inChat() && content instanceof Component component ? GuiFont.restyle(component) : content;
    }
}

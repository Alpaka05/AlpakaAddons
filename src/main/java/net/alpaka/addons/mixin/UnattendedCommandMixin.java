package net.alpaka.addons.mixin;

import net.alpaka.addons.client.CommandAttendance;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the commands a click event runs as unattended, for {@link CommandAttendance}.
 *
 * The mark goes around the caller's sendUnattendedCommand call rather than into that method itself,
 * where Fabric API runs client commands from its own HEAD injection and the order of the two
 * would not be ours to choose.
 */
@Mixin(Screen.class)
public class UnattendedCommandMixin {
    @Inject(method = "clickCommandAction", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendUnattendedCommand(Ljava/lang/String;Lnet/minecraft/client/gui/screens/Screen;)V"))
    private static void alpaka$beginUnattended(LocalPlayer player, String command, Screen screen, CallbackInfo ci) {
        CommandAttendance.beginUnattended();
    }

    @Inject(method = "clickCommandAction", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendUnattendedCommand(Ljava/lang/String;Lnet/minecraft/client/gui/screens/Screen;)V"))
    private static void alpaka$endUnattended(LocalPlayer player, String command, Screen screen, CallbackInfo ci) {
        CommandAttendance.endUnattended();
    }
}

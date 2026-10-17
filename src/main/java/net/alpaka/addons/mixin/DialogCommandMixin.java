package net.alpaka.addons.mixin;

import net.alpaka.addons.client.CommandAttendance;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks the commands a server dialog's action runs as unattended; see UnattendedCommandMixin. */
@Mixin(targets = "net.minecraft.client.multiplayer.ClientPacketListener$2")
public class DialogCommandMixin {
    @Inject(method = "runCommand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendUnattendedCommand(Ljava/lang/String;Lnet/minecraft/client/gui/screens/Screen;)V"))
    private void alpaka$beginUnattended(String command, Screen screen, CallbackInfo ci) {
        CommandAttendance.beginUnattended();
    }

    @Inject(method = "runCommand", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;sendUnattendedCommand(Ljava/lang/String;Lnet/minecraft/client/gui/screens/Screen;)V"))
    private void alpaka$endUnattended(String command, Screen screen, CallbackInfo ci) {
        CommandAttendance.endUnattended();
    }
}

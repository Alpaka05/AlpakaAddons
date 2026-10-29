package net.alpaka.addons.features.shortcuts;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.alpaka.addons.compliance.Outbound;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.config.AlpakaConfig.CommandAlias;
import net.alpaka.addons.config.AlpakaConfig.KeyShortcut;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

import java.util.List;
import java.util.Locale;

/**
 * Command Shortcuts: aliases for typed commands and keys that run a command, both set up in
 * {@link net.alpaka.addons.client.CommandShortcutsConfigScreen}.
 *
 * An alias stands for the first word of a command and keeps whatever follows, so "/v" for "/visit"
 * turns "/v Steve" into "/visit Steve", and "/pa" for "/p accept" turns "/pa Steve" into
 * "/p accept Steve". Skyblocker keeps two lists for that - whole commands and argument shortcuts -
 * which one list covers here. Only the typed command is rewritten, and only once, so an alias that
 * names another alias cannot loop.
 *
 * A key shortcut runs its command when its key is pressed with exactly its modifiers held and no
 * screen is open; the press is taken, so nothing else bound to the key fires as well. Both go out
 * through {@link Outbound}, as the direct result of Enter or a key press.
 */
public final class CommandShortcuts {

    /** The modifier bits a key shortcut can ask for; Caps Lock and Num Lock don't count. */
    public static final int MODIFIER_MASK =
            InputConstants.MOD_SHIFT | InputConstants.MOD_CONTROL | InputConstants.MOD_ALT | InputConstants.MOD_SUPER;

    private CommandShortcuts() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerAliases(dispatcher));
    }

    /**
     * Makes each alias known to the chat box, so typing one isn't marked as an unknown command.
     * Nothing executes them: a typed alias is rewritten before it gets this far (see
     * {@link #expandTyped}). An alias that is already a client command keeps that command's meaning
     * in the suggestions. Aliases added later show up from the next join on.
     */
    private static void registerAliases(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        if (!AlpakaConfig.instance.commandShortcutsEnabled) return;
        for (CommandAlias alias : aliases()) {
            String name = aliasName(alias.alias);
            if (name.isEmpty() || cleanCommand(alias.command).isEmpty()) continue;
            if (dispatcher.getRoot().getChild(name) != null) continue;
            dispatcher.register(ClientCommands.literal(name)
                    .then(ClientCommands.argument("args", StringArgumentType.greedyString())));
        }
    }

    // ------------------------------------------------------------------ aliases

    /**
     * A command typed into chat and sent with Enter, without its slash. Sends what its alias stands
     * for and returns true, or returns false to let it go out as typed.
     */
    public static boolean expandTyped(String command) {
        if (!AlpakaConfig.instance.commandShortcutsEnabled) return false;
        String expanded = expand(command, aliases());
        if (expanded == null) return false;
        Outbound.command(expanded, Outbound.Cause.CHAT_INPUT);
        return true;
    }

    /**
     * What a command stands for, without its slash, or null when its first word is no alias.
     * The first word is compared without regard to case; the rest is kept as typed.
     */
    public static String expand(String command, List<CommandAlias> aliases) {
        if (command == null || aliases == null) return null;
        String trimmed = command.strip();
        int space = trimmed.indexOf(' ');
        String first = (space < 0 ? trimmed : trimmed.substring(0, space)).toLowerCase(Locale.ROOT);
        if (first.isEmpty()) return null;
        String rest = space < 0 ? "" : trimmed.substring(space);
        for (CommandAlias alias : aliases) {
            if (alias == null || !first.equals(aliasName(alias.alias))) continue;
            String target = cleanCommand(alias.command);
            if (target.isEmpty()) continue;
            return target + rest;
        }
        return null;
    }

    /** An alias as it is matched: no slash, no spaces around it, lower case. */
    public static String aliasName(String alias) {
        return cleanCommand(alias).toLowerCase(Locale.ROOT);
    }

    /** A command as it is stored and sent: trimmed, without its leading slashes. */
    public static String cleanCommand(String command) {
        if (command == null) return "";
        String s = command.strip();
        while (s.startsWith("/")) s = s.substring(1);
        return s.strip();
    }

    private static List<CommandAlias> aliases() {
        List<CommandAlias> list = AlpakaConfig.instance.commandAliases;
        return list == null ? List.of() : list;
    }

    // ------------------------------------------------------------- key shortcuts

    /**
     * A key press, before the game sees it. Runs the command bound to the key and returns true when
     * the press is taken. A repeat of a held key is taken as well, without running it again.
     */
    public static boolean onKey(KeyEvent event, boolean repeat) {
        if (!AlpakaConfig.instance.commandShortcutsEnabled) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null || mc.player == null) return false;
        List<KeyShortcut> shortcuts = AlpakaConfig.instance.commandKeyShortcuts;
        if (shortcuts == null || shortcuts.isEmpty()) return false;
        String key = InputConstants.getKey(event).getName();
        KeyShortcut hit = find(shortcuts, key, event.modifiers());
        if (hit == null) return false;
        if (!repeat) Outbound.command(cleanCommand(hit.command), Outbound.Cause.KEY_PRESS);
        return true;
    }

    /** The shortcut for a key pressed with the given modifiers, or null. */
    public static KeyShortcut find(List<KeyShortcut> shortcuts, String key, int modifiers) {
        int held = modifiers & MODIFIER_MASK;
        for (KeyShortcut shortcut : shortcuts) {
            if (shortcut == null || shortcut.key == null || shortcut.key.isEmpty()) continue;
            if (!shortcut.key.equals(key) || (shortcut.modifiers & MODIFIER_MASK) != held) continue;
            if (cleanCommand(shortcut.command).isEmpty()) continue;
            return shortcut;
        }
        return null;
    }

    /** Whether a key is only a modifier, which a shortcut holds rather than presses. */
    public static boolean isModifierKey(int key) {
        return key == InputConstants.KEY_LSHIFT || key == InputConstants.KEY_RSHIFT
                || key == InputConstants.KEY_LCONTROL || key == InputConstants.KEY_RCONTROL
                || key == InputConstants.KEY_LALT || key == InputConstants.KEY_RALT
                || key == InputConstants.KEY_LSUPER || key == InputConstants.KEY_RSUPER;
    }

    /** How a shortcut's key reads: "Ctrl + Shift + Y", or "Not Bound". */
    public static String keyLabel(KeyShortcut shortcut) {
        if (shortcut == null || shortcut.key == null || shortcut.key.isEmpty()) return "Not Bound";
        StringBuilder sb = new StringBuilder();
        int mods = shortcut.modifiers;
        boolean mac = net.minecraft.util.Util.getPlatform() == net.minecraft.util.Util.OS.OSX;
        if ((mods & InputConstants.MOD_CONTROL) != 0) sb.append("Ctrl + ");
        if ((mods & InputConstants.MOD_ALT) != 0) sb.append(mac ? "Option + " : "Alt + ");
        if ((mods & InputConstants.MOD_SHIFT) != 0) sb.append("Shift + ");
        if ((mods & InputConstants.MOD_SUPER) != 0) sb.append(mac ? "Cmd + " : "Super + ");
        String name;
        try {
            name = InputConstants.getKey(shortcut.key).getDisplayName().getString();
        } catch (RuntimeException e) {
            name = shortcut.key;
        }
        if (name.length() == 1) name = name.toUpperCase(Locale.ROOT);
        return sb.append(name).toString();
    }
}

package net.alpaka.addons.features.shortcuts;

import net.alpaka.addons.config.AlpakaConfig.CommandAlias;
import net.alpaka.addons.config.AlpakaConfig.KeyShortcut;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class CommandShortcutsTest {
    private static final List<CommandAlias> ALIASES = List.of(
            new CommandAlias("/v", "/visit"),
            new CommandAlias("pa", "p accept"),
            new CommandAlias("/vp", "/visit portalhub"),
            new CommandAlias("/empty", "/"));

    @Test
    void aliasAloneBecomesItsCommand() {
        assertEquals("visit", CommandShortcuts.expand("v", ALIASES));
        assertEquals("visit portalhub", CommandShortcuts.expand("vp", ALIASES));
    }

    @Test
    void argumentsAreKept() {
        assertEquals("visit Steve", CommandShortcuts.expand("v Steve", ALIASES));
        assertEquals("p accept Steve", CommandShortcuts.expand("pa Steve", ALIASES));
        assertEquals("visit portalhub extra", CommandShortcuts.expand("vp extra", ALIASES));
    }

    @Test
    void firstWordIgnoresCaseRestKeepsIt() {
        assertEquals("visit SteveMC", CommandShortcuts.expand("V SteveMC", ALIASES));
    }

    @Test
    void otherCommandsAreLeftAlone() {
        assertNull(CommandShortcuts.expand("visit Steve", ALIASES));
        assertNull(CommandShortcuts.expand("vv", ALIASES));
        assertNull(CommandShortcuts.expand("", ALIASES));
        // An alias without a command does nothing.
        assertNull(CommandShortcuts.expand("empty", ALIASES));
    }

    @Test
    void keyNeedsExactlyItsModifiers() {
        KeyShortcut plain = new KeyShortcut("key.keyboard.y", 0, "/sb menu");
        KeyShortcut ctrl = new KeyShortcut("key.keyboard.y", 2, "/warp hub");
        List<KeyShortcut> list = List.of(plain, ctrl);
        assertSame(plain, CommandShortcuts.find(list, "key.keyboard.y", 0));
        // Caps Lock (16) and Num Lock (32) don't count as modifiers.
        assertSame(plain, CommandShortcuts.find(list, "key.keyboard.y", 16 | 32));
        assertSame(ctrl, CommandShortcuts.find(list, "key.keyboard.y", 2));
        assertNull(CommandShortcuts.find(list, "key.keyboard.y", 1));
        assertNull(CommandShortcuts.find(list, "key.keyboard.x", 0));
    }

    @Test
    void unboundOrEmptyShortcutsNeverMatch() {
        List<KeyShortcut> list = List.of(new KeyShortcut("", 0, "/sb"), new KeyShortcut("key.keyboard.u", 0, "/"));
        assertNull(CommandShortcuts.find(list, "", 0));
        assertNull(CommandShortcuts.find(list, "key.keyboard.u", 0));
    }
}

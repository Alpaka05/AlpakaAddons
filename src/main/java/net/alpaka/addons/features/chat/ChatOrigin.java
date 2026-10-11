package net.alpaka.addons.features.chat;

import com.google.common.collect.MapMaker;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;

import java.util.Collections;
import java.util.Set;

/**
 * Whether a chat message came from the server or was made on this client, for the Server and Mods
 * tabs.
 *
 * Minecraft 26.2 records a source on every message, but it cannot be used for this: a mod's
 * {@code LocalPlayer.sendSystemMessage} goes down the same path as a packet and comes out as
 * {@code SYSTEM_SERVER} too. So the one thing that really comes from the server - the call
 * {@code ClientPacketListener.handleSystemChat} makes - is marked on its way through.
 *
 * The listener a packet goes through runs the receive events of every mod, and a mod answering a
 * server line with a line of its own does so from inside that very call. Those nested calls are
 * told apart by depth: only the message added at the packet's own level is the server's.
 */
public final class ChatOrigin {

    private ChatOrigin() {
    }

    /** How many {@code ChatListener.handleSystemMessage} calls are running, nested. */
    private static int depth = 0;

    /** The depth the packet's own call runs at; -1 outside a packet. */
    private static int packetDepth = -1;

    /** True while the packet's own message is being added to the chat. */
    private static boolean addingServerMessage = false;

    /** The stored messages that came from the server, compared by identity and dropped with them. */
    private static final Set<GuiMessage> SERVER = Collections.newSetFromMap(new MapMaker().weakKeys().makeMap());

    /** The packet hands a message to the chat listener; returns the depth to restore afterwards. */
    public static int beginPacket() {
        int previous = packetDepth;
        packetDepth = depth + 1;
        return previous;
    }

    public static void endPacket(int previous) {
        packetDepth = previous;
    }

    public static void enterListener() {
        depth++;
    }

    public static void leaveListener() {
        depth--;
    }

    /** The listener adds a message as a server one; true when it is really the packet's own. */
    public static boolean beginAdd() {
        addingServerMessage = depth == packetDepth;
        return addingServerMessage;
    }

    public static void endAdd() {
        addingServerMessage = false;
    }

    /**
     * Whether the message being added right now came from the server. Player chat only ever
     * arrives from the server; system lines only when the packet's own call is adding them.
     */
    public static boolean isFromServer(GuiMessageSource source) {
        return source == GuiMessageSource.PLAYER || addingServerMessage;
    }

    public static void markServer(GuiMessage message) {
        SERVER.add(message);
    }

    public static boolean isServer(GuiMessage message) {
        return message.source() == GuiMessageSource.PLAYER || SERVER.contains(message);
    }
}

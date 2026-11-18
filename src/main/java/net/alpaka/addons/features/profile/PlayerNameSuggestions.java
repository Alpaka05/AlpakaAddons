package net.alpaka.addons.features.profile;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The names Tab completes after the dashboard's command ("/ad Alp" + Tab): the players in the
 * lobby, as Hypixel's own commands complete them, the friends, guild members and party members
 * Hypixel's chat named this game ("Friend > Alpakaa joined."), the people messaged, and the players
 * looked up in the dashboard before. All of it is what the game already knows; nothing is asked for.
 */
public final class PlayerNameSuggestions {
    private PlayerNameSuggestions() {}

    /** A player of the lobby: the name and the account, which tells a player from an NPC. */
    record Seen(String name, UUID id) {}

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    /** "[MVP+] " before a name, which Hypixel's chat puts there for ranked players. */
    private static final String RANK = "(?:\\[[^\\]]+\\] )?";
    private static final Pattern[] CHAT = {
            // A friend or guild member coming online or going.
            Pattern.compile("^(?:Friend|Guild) > " + RANK + "(\\w{3,16}) (?:joined|left)\\.$"),
            // A private message, either way.
            Pattern.compile("^(?:From|To) " + RANK + "(\\w{3,16}): "),
            // A party: who joined it, who invited, and the party list's lines.
            Pattern.compile("^" + RANK + "(\\w{3,16}) joined the party\\.$"),
            Pattern.compile("^" + RANK + "(\\w{3,16}) has invited you to join their party!"),
            Pattern.compile("^Party (?:Leader|Moderators|Members): (.+)$"),
    };
    /** Names from chat, the last named first; at most this many. */
    private static final int CHAT_KEPT = 200;
    private static final Map<String, String> FROM_CHAT = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > CHAT_KEPT;
        }
    };

    /** Starts listening to the chat for the names of friends, guild and party members. */
    public static void register() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) remember(message);
        });
    }

    private static void remember(Component message) {
        String text = net.alpaka.addons.utils.SkyblockUtils.cleanColor(message.getString()).trim();
        // Most lines are no business of this; one test before the patterns.
        if (text.length() > 300) return;
        for (String name : fromChat(text)) {
            synchronized (FROM_CHAT) {
                FROM_CHAT.put(name.toLowerCase(Locale.ROOT), name);
            }
        }
    }

    /** The players a chat line names, as {@link #CHAT} reads it. */
    static List<String> fromChat(String line) {
        List<String> out = new ArrayList<>();
        for (Pattern pattern : CHAT) {
            Matcher m = pattern.matcher(line);
            if (!m.find()) continue;
            String found = m.group(1);
            // The party list: "Alpakaa ● [MVP+] Someone ●", names between ranks and dots.
            for (String part : found.split("[●•,]")) {
                String name = part.trim().replaceFirst("^\\[[^\\]]+\\] ", "");
                if (NAME.matcher(name).matches()) out.add(name);
            }
        }
        return out;
    }

    /** For the command's argument: the names starting with what was typed so far. */
    public static CompletableFuture<Suggestions> suggest(CommandContext<?> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(names(), builder);
    }

    private static List<String> names() {
        Minecraft mc = Minecraft.getInstance();
        List<Seen> lobby = new ArrayList<>();
        ClientPacketListener connection = mc.getConnection();
        if (connection != null) {
            for (PlayerInfo info : connection.getOnlinePlayers()) {
                GameProfile profile = info.getProfile();
                lobby.add(new Seen(profile.name(), profile.id()));
            }
        }
        if (mc.level != null) {
            for (Player player : mc.level.players()) lobby.add(new Seen(player.getGameProfile().name(), player.getUUID()));
        }
        List<String> chat;
        synchronized (FROM_CHAT) {
            chat = new ArrayList<>(FROM_CHAT.values());
        }
        String own = mc.getUser() == null ? null : mc.getUser().getName();
        return candidates(lobby, chat, ProfileHistory.players(), own);
    }

    /**
     * Every name worth completing, once each whatever its case. The lobby's players only if they are
     * players: Hypixel's NPCs and the lines of SkyBlock's tab list come as accounts too, but not of
     * the kind Mojang hands out to players (version 4), and mostly under names no player can have.
     */
    static List<String> candidates(Collection<Seen> lobby, Collection<String> chat, Collection<String> lookedUp, String own) {
        Map<String, String> out = new LinkedHashMap<>();
        if (own != null && NAME.matcher(own).matches()) out.put(own.toLowerCase(Locale.ROOT), own);
        for (Seen seen : lobby) {
            if (seen.name() == null || seen.id() == null || seen.id().version() != 4) continue;
            if (NAME.matcher(seen.name()).matches()) out.putIfAbsent(seen.name().toLowerCase(Locale.ROOT), seen.name());
        }
        for (String name : chat) {
            if (name != null && NAME.matcher(name).matches()) out.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
        }
        for (String name : lookedUp) {
            if (name != null && NAME.matcher(name).matches()) out.putIfAbsent(name.toLowerCase(Locale.ROOT), name);
        }
        return new ArrayList<>(out.values());
    }
}

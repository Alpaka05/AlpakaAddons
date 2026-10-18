package net.alpaka.addons.features.ragaxe;

import net.alpaka.addons.config.AlpakaConfig;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Ragnarock axe's cast countdown under the crosshair instead of in the action bar.
 *
 * Right-clicking the axe starts a three second cast, and Hypixel counts it down in the action bar
 * ("CASTING IN 3s"), squeezed between health, defence and mana where it is easy to miss. Here that
 * part is cut out of the action bar - the rest of it stays - and the same count is drawn as plain
 * text just under the crosshair, where the eyes already are. Once the count runs out and the cast
 * goes through, "Rag Axe" shows there for a moment.
 *
 * The count is Hypixel's own: each action bar carries the current number, so it ticks exactly like
 * the original. A cast that ends early - damage, a weapon swap - must not read as a success: it
 * only counts as cast when the last number seen was 1 and the next action bar comes without one, and
 * Hypixel's "Ragnarock was cancelled" chat line takes everything down again.
 */
public final class RagAxeCountdownFeature {

    /**
     * The cast part of the action bar: "CASTING IN 3s", or a bare "CASTING" on the last beat, with any
     * formatting codes in front of or inside it.
     */
    private static final Pattern CASTING = Pattern.compile(
            "(?:§.)*CASTING(?:(?:§.)* IN (?:§.)*(\\d+)(?:\\.\\d+)?(?:§.)*s)?(?:§.)*");

    private static final String CANCELLED = "Ragnarock was cancelled";

    /** How long "Rag Axe" stays up after the cast went through. */
    private static final long CAST_SHOWN_MS = 2000L;

    /** A countdown with no action bar for this long is dropped; Hypixel sends one several times a second. */
    private static final long STALE_MS = 2500L;

    /** Gap below the screen's middle, clear of the crosshair's lower arm. */
    private static final int BELOW_CROSSHAIR = 10;

    private static final int COUNTDOWN_COLOR = 0xFFFFAA00;
    private static final int CAST_COLOR = 0xFF55FF55;

    /** Seconds left in the running cast, 0 for the bare "CASTING", or -1 when none is running. */
    private static int seconds = -1;
    private static long lastCountdownAt;
    /** When the cast went through, or 0; "Rag Axe" is shown for a while after it. */
    private static long castAt;

    private RagAxeCountdownFeature() {
    }

    public static void register() {
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) ->
                overlay ? onActionBar(message) : message);
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) onChat(message);
        });
        ClientReceiveMessageEvents.GAME_CANCELED.register((message, overlay) -> {
            if (!overlay) onChat(message);
        });
    }

    /** Where the cast part sits in an action bar text and the number it shows. */
    record Match(int start, int end, int seconds) {
    }

    /**
     * Finds the cast part in an action bar's text, widened by the spaces that separated it from its
     * neighbours so cutting it out leaves no gap. Null when the action bar has none.
     */
    static Match find(String text) {
        Matcher m = CASTING.matcher(text);
        if (!m.find()) return null;
        int start = m.start();
        int end = m.end();
        if (start > 0) {
            while (start > 0 && Character.isWhitespace(text.charAt(start - 1))) start--;
        } else {
            while (end < text.length() && Character.isWhitespace(text.charAt(end))) end++;
        }
        int secs = m.group(1) != null ? Integer.parseInt(m.group(1)) : 0;
        return new Match(start, end, secs);
    }

    private static Component onActionBar(Component message) {
        if (!AlpakaConfig.instance.ragAxeCountdownEnabled) {
            seconds = -1;
            return message;
        }
        List<Piece> pieces = new ArrayList<>();
        message.visit((style, text) -> {
            pieces.add(new Piece(style, text));
            return Optional.empty();
        }, Style.EMPTY);
        StringBuilder full = new StringBuilder();
        for (Piece piece : pieces) full.append(piece.text());

        long now = System.currentTimeMillis();
        Match match = find(full.toString());
        if (match == null) {
            // The countdown is over. Only a count that reached its last beat means the axe went off.
            if (seconds >= 0 && seconds <= 1 && now - lastCountdownAt <= STALE_MS) {
                castAt = now;
            }
            seconds = -1;
            return message;
        }

        if (match.seconds() == 0 && seconds >= 0 && seconds <= 1) {
            castAt = now;
            seconds = -1;
        } else if (match.seconds() > 0) {
            seconds = match.seconds();
            lastCountdownAt = now;
            castAt = 0;
        }
        return cut(pieces, match.start(), match.end());
    }

    private static void onChat(Component message) {
        if (message.getString().contains(CANCELLED)) {
            seconds = -1;
            castAt = 0;
        }
    }

    /** The action bar rebuilt piece by piece with its styles, minus the characters in [start, end). */
    static Component cut(List<Piece> pieces, int start, int end) {
        MutableComponent out = Component.empty();
        int pos = 0;
        for (Piece piece : pieces) {
            String text = piece.text();
            int from = pos;
            int to = pos + text.length();
            pos = to;
            StringBuilder kept = new StringBuilder();
            if (from < start) kept.append(text, 0, Math.min(text.length(), start - from));
            if (to > end) kept.append(text, Math.max(0, end - from), text.length());
            if (!kept.isEmpty()) out.append(Component.literal(kept.toString()).withStyle(piece.style()));
        }
        return out;
    }

    record Piece(Style style, String text) {
    }

    public static void render(GuiGraphicsExtractor graphics) {
        if (!AlpakaConfig.instance.ragAxeCountdownEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || mc.level == null || mc.player == null) return;
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen)) return;

        long now = System.currentTimeMillis();
        String text;
        int color;
        if (seconds > 0 && now - lastCountdownAt <= STALE_MS) {
            text = seconds + "s";
            color = COUNTDOWN_COLOR;
        } else if (castAt != 0 && now - castAt <= CAST_SHOWN_MS) {
            text = "Rag Axe";
            color = CAST_COLOR;
        } else {
            return;
        }
        int x = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2 + BELOW_CROSSHAIR;
        graphics.centeredText(mc.font, text, x, y, color);
    }
}

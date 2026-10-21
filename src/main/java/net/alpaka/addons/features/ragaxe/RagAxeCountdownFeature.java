package net.alpaka.addons.features.ragaxe;

import net.alpaka.addons.config.AlpakaConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Ragnarock's cast countdown under the crosshair instead of in the action bar.
 *
 * Right-clicking the Ragnarock starts a three second cast, and Hypixel counts it down in the action bar
 * ("CASTING IN 3s"), squeezed between health, defence and mana where it is easy to miss. Here that
 * part is cut out of the action bar - the rest of it stays - and the same count is drawn just under
 * the crosshair, where the eyes already are. Then "Ragnarock" shows there if the cast went through,
 * or "Cancelled" if it did not.
 *
 * The count is Hypixel's own: each action bar carries the current number, so it ticks exactly like
 * the original. Whether the cast went through is not read off the count running out, since a cast
 * broken on its last beat looks the same there. Like Odin, it is the sound Hypixel plays when the
 * cast goes off - a wolf's death sound at one particular pitch - while the Ragnarock is in hand. A cast
 * ends early on a hit, which Hypixel says in chat ("Ragnarock was cancelled due to ..."), or when
 * the Ragnarock leaves the hand; a countdown that just stops with no cast sound after it is cancelled too.
 */
public final class RagAxeCountdownFeature {

    /**
     * The cast part of the action bar: "CASTING IN 3s", or a bare "CASTING" on the last beat, with any
     * formatting codes in front of or inside it.
     */
    private static final Pattern CASTING = Pattern.compile(
            "(?:§.)*CASTING(?:(?:§.)* IN (?:§.)*(\\d+)(?:\\.\\d+)?(?:§.)*s)?(?:§.)*");

    private static final String CANCELLED = "Ragnarock was cancelled";

    /** Hypixel's id for the Ragnarock, in the item's custom data; it kept "axe" when the item became a sword. */
    private static final String AXE_ID = "RAGNAROCK_AXE";

    /** The pitch of the cast sound; Odin matches the same value. */
    private static final float CAST_PITCH = 1.4920635f;

    /** How long "Ragnarock" or "Cancelled" stays up. */
    private static final long RESULT_SHOWN_MS = 2000L;

    /** After the countdown stops, how long the cast sound may take before the cast counts as cancelled. */
    private static final long SOUND_GRACE_MS = 1500L;

    /**
     * Right after a cast or a cancel, countdown numbers that still arrive are the old cast's tail,
     * not a new cast: the sound or chat line and the last action bar can come in either order.
     */
    private static final long AFTER_RESULT_MS = 1000L;

    /** A countdown with no action bar for this long is dropped; Hypixel sends one several times a second. */
    private static final long STALE_MS = 2500L;

    /** Gap below the screen's middle, clear of the crosshair's lower arm. */
    private static final int BELOW_CROSSHAIR = 10;

    /** Twice the normal text size, so the count can be read at a glance. */
    private static final float SCALE = 2.0f;

    private static final int COUNTDOWN_COLOR = 0xFFFFAA00;
    private static final int CAST_COLOR = 0xFF55FF55;
    private static final int CANCELLED_COLOR = 0xFFFF5555;

    private enum Result { NONE, CAST, CANCELLED }

    /** Seconds left in the running cast, or -1 when none is running. */
    private static int seconds = -1;
    private static long lastCountdownAt;
    /** When the countdown stopped without the cast sound yet, or 0. */
    private static long countdownEndedAt;
    private static Result result = Result.NONE;
    private static long resultAt;

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
        ClientTickEvents.END_CLIENT_TICK.register(RagAxeCountdownFeature::tick);
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
            if (seconds > 0) countdownEndedAt = now;
            seconds = -1;
            return message;
        }
        boolean tail = result != Result.NONE && now - resultAt <= AFTER_RESULT_MS;
        if (match.seconds() > 0 && !tail) {
            if (seconds < 0) result = Result.NONE;
            seconds = match.seconds();
            lastCountdownAt = now;
            countdownEndedAt = 0;
        }
        return cut(pieces, match.start(), match.end());
    }

    private static void onChat(Component message) {
        if (message.getString().contains(CANCELLED)) finish(Result.CANCELLED);
    }

    /** Every sound the server plays, on the client thread; see ClientPacketListenerMixin. */
    public static void onSound(ClientboundSoundPacket packet) {
        if (!AlpakaConfig.instance.ragAxeCountdownEnabled) return;
        if (Math.abs(packet.getPitch() - CAST_PITCH) > 0.0001f) return;
        String path = packet.getSound().value().location().getPath();
        if (!path.startsWith("entity.wolf") || !path.endsWith(".death")) return;
        if (!holdingAxe()) return;
        finish(Result.CAST);
    }

    private static void tick(Minecraft mc) {
        if (seconds < 0 && countdownEndedAt == 0) return;
        long now = System.currentTimeMillis();
        if (mc.player == null || (seconds > 0 && now - lastCountdownAt > STALE_MS)) {
            // Left the world or the server stopped sending: nothing to report.
            seconds = -1;
            countdownEndedAt = 0;
        } else if (!holdingAxe()) {
            finish(Result.CANCELLED);
        } else if (countdownEndedAt != 0 && now - countdownEndedAt > SOUND_GRACE_MS) {
            finish(Result.CANCELLED);
        }
    }

    private static void finish(Result how) {
        seconds = -1;
        countdownEndedAt = 0;
        result = how;
        resultAt = System.currentTimeMillis();
    }

    private static boolean holdingAxe() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        CustomData data = mc.player.getMainHandItem().get(DataComponents.CUSTOM_DATA);
        return data != null && AXE_ID.equals(data.copyTag().getStringOr("id", ""));
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
        if (seconds > 0) {
            text = seconds + "s";
            color = COUNTDOWN_COLOR;
        } else if (result != Result.NONE && now - resultAt <= RESULT_SHOWN_MS) {
            text = result == Result.CAST ? "Ragnarock" : "Cancelled";
            color = result == Result.CAST ? CAST_COLOR : CANCELLED_COLOR;
        } else {
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(graphics.guiWidth() / 2.0f, graphics.guiHeight() / 2.0f + BELOW_CROSSHAIR);
        graphics.pose().scale(SCALE, SCALE);
        graphics.centeredText(mc.font, text, 0, 0, color);
        graphics.pose().popMatrix();
    }
}

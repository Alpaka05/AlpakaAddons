package net.alpaka.addons.features.tooltip;

import net.alpaka.addons.config.AlpakaConfig;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shows master stars the old way: Hypixel now writes a dungeon item's master stars as a circled
 * number behind five gold stars ("✪✪✪✪✪➎"); this turns that many of the stars red again and drops
 * the number ("✪✪✪✪✪" with the first five red).
 *
 * It is applied to the item name in tooltips and to the held item's name above the hotbar (see
 * HudMixin). Names without a master star number are returned untouched, so the cost on every other
 * item is one scan of the plain text.
 */
public final class OldMasterStarsFeature {

    private OldMasterStarsFeature() {
    }

    private static final int STAR = '✪';
    /** ➊ to ➎: one to five master stars. */
    private static final int FIRST_MASTER = 0x278A;
    private static final int LAST_MASTER = 0x278E;

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            if (isEnabled() && !lines.isEmpty()) {
                lines.set(0, apply(lines.get(0)));
            }
        });
    }

    public static boolean isEnabled() {
        return AlpakaConfig.instance.oldMasterStarsEnabled;
    }

    /** The name with its master star number turned into red stars, or the name itself if it has none. */
    public static Component apply(Component name) {
        return isEnabled() ? convert(name) : name;
    }

    static Component convert(Component name) {
        if (!hasMasterNumber(name.getString())) return name;

        // Flatten into one style per character, resolving any legacy § codes inside the text so the
        // stars can be recoloured one by one however Hypixel happened to split the name.
        StringBuilder text = new StringBuilder();
        List<Style> styles = new ArrayList<>();
        name.visit((style, part) -> {
            Style current = style;
            for (int i = 0; i < part.length(); ) {
                int cp = part.codePointAt(i);
                int len = Character.charCount(cp);
                if (cp == '§' && i + 1 < part.length()) {
                    ChatFormatting format = ChatFormatting.getByCode(part.charAt(i + 1));
                    if (format == ChatFormatting.RESET) current = style;
                    else if (format != null) current = current.applyLegacyFormat(format);
                    i += 2;
                    continue;
                }
                text.appendCodePoint(cp);
                for (int k = 0; k < len; k++) styles.add(current);
                i += len;
            }
            return Optional.empty();
        }, Style.EMPTY);

        Plan plan = plan(text.toString());
        if (plan == null) return name;
        for (int i = plan.firstRed(); i < plan.firstRed() + plan.redCount(); i++) {
            styles.set(i, styles.get(i).withColor(ChatFormatting.RED));
        }
        text.delete(plan.numberAt(), plan.numberAt() + plan.numberLength());
        styles.subList(plan.numberAt(), plan.numberAt() + plan.numberLength()).clear();

        MutableComponent out = Component.empty();
        int start = 0;
        for (int i = 1; i <= text.length(); i++) {
            if (i == text.length() || !styles.get(i).equals(styles.get(start))) {
                out.append(Component.literal(text.substring(start, i)).withStyle(styles.get(start)));
                start = i;
            }
        }
        return out;
    }

    private static boolean hasMasterNumber(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= FIRST_MASTER && c <= LAST_MASTER) return true;
        }
        return false;
    }

    /**
     * Where the change goes in a plain name: the stars to turn red and the number to remove.
     * {@code null} if the name has no master star number right behind its stars.
     */
    static Plan plan(String plain) {
        for (int i = 0; i < plain.length(); i++) {
            char c = plain.charAt(i);
            if (c < FIRST_MASTER || c > LAST_MASTER) continue;
            int count = c - FIRST_MASTER + 1;
            int runStart = i;
            while (runStart > 0 && plain.charAt(runStart - 1) == STAR) runStart--;
            if (runStart == i) continue;
            return new Plan(runStart, Math.min(count, i - runStart), i, 1);
        }
        return null;
    }

    record Plan(int firstRed, int redCount, int numberAt, int numberLength) {
    }
}

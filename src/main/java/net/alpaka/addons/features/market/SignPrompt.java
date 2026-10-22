package net.alpaka.addons.features.market;

import java.util.Locale;

/**
 * One of Hypixel's sign prompts: a sign whose first line is left for the answer, with a row of
 * carets under it pointing up at that line and the question below ("Enter query", "Your bid").
 *
 * Only such signs are given the input bar. A sign the player placed and writes on stays vanilla.
 */
public record SignPrompt(Type type, String question) {

    public enum Type {
        SEARCH, PRICE, AMOUNT, DURATION, TEXT;

        /** Whether the answer is a number, which the bar then also shows written out. */
        public boolean numeric() {
            return this == PRICE || this == AMOUNT;
        }
    }

    /** The prompt on a sign with these four lines, or null if it is not one of Hypixel's prompts. */
    public static SignPrompt of(String[] lines) {
        if (lines == null || lines.length < 4) return null;
        // A row of carets, or carets around a word as in "^^Flipping^^".
        String arrows = lines[1] == null ? "" : lines[1].trim();
        if (arrows.length() < 3 || !arrows.startsWith("^^") || !arrows.endsWith("^^")) return null;
        String question = ((lines[2] == null ? "" : lines[2].trim()) + " " + (lines[3] == null ? "" : lines[3].trim())).trim();
        return new SignPrompt(typeOf(question), question);
    }

    static Type typeOf(String question) {
        String q = question.toLowerCase(Locale.ROOT);
        if (q.contains("query") || q.contains("search")) return Type.SEARCH;
        if (q.contains("duration") || q.contains("hour") || q.contains("minute")) return Type.DURATION;
        if (q.contains("price") || q.contains("bid") || q.contains("coins")) return Type.PRICE;
        if (q.contains("amount") || q.contains("quantity") || q.contains("how many")) return Type.AMOUNT;
        return Type.TEXT;
    }

    /** The bar's heading: what is being asked, in the market it is asked for if known. */
    public String heading(MarketKind context) {
        String q = question.toLowerCase(Locale.ROOT);
        return switch (type) {
            case SEARCH -> context == MarketKind.AUCTION_HOUSE ? "Search the Auction House"
                    : context == MarketKind.BAZAAR ? "Search the Bazaar" : "Search";
            case PRICE -> q.contains("bid") ? "Place Your Bid" : "Set a Price";
            case AMOUNT -> "Choose an Amount";
            case DURATION -> "Auction Duration";
            case TEXT -> question.isEmpty() ? "Enter Text" : capitalize(question);
        };
    }

    /** The text in the empty field. */
    public String placeholder() {
        return switch (type) {
            case SEARCH -> "Item name…";
            case PRICE -> "e.g. 1.5m, 250k or 12,000";
            case AMOUNT -> "e.g. 64, 1k or 2,240";
            case DURATION -> "Hours";
            case TEXT -> "Type here…";
        };
    }

    /** The confirm button's label. */
    public String confirmLabel() {
        return switch (type) {
            case SEARCH -> "Search";
            case PRICE -> question.toLowerCase(Locale.ROOT).contains("bid") ? "Bid" : "Confirm";
            default -> "Confirm";
        };
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}

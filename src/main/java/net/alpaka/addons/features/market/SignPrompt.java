package net.alpaka.addons.features.market;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.ToIntFunction;

/**
 * One of Hypixel's sign prompts: the lines left for the answer, a row of carets under them pointing
 * up, and the question below.
 *
 * Hypixel uses two layouts. Searches leave two lines for the answer and ask on the last one
 * ({@code "", "", "^^^^^^^^^^^^^^^", "Enter query"}), and join the two lines with a space. Prices
 * and amounts leave one line and ask on the last two ({@code "", "^^^^^^^^^^^^^^^", "Enter amount",
 * "to order"}). Some carets frame a word, as in {@code "^^Flipping^^"}.
 *
 * The Auction House asks the same for a Buy It Now auction as for one taking bids: {@code "",
 * "^^^^^^^^^^^^^^^", "Your auction", "starting bid"}. Only the menu the sign came from tells them
 * apart.
 *
 * Only such signs are given the input bar. A sign the player placed and writes on stays vanilla.
 *
 * @param inputLines how many lines, from the top, take the answer: 1 or 2
 */
public record SignPrompt(Type type, String question, int inputLines) {

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
        int arrows = -1;
        for (int i = 1; i <= 2; i++) {
            if (isArrows(lines[i])) {
                arrows = i;
                break;
            }
        }
        if (arrows < 0) return null;
        StringBuilder question = new StringBuilder();
        for (int i = arrows + 1; i < 4; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isEmpty()) continue;
            if (!question.isEmpty()) question.append(' ');
            question.append(line);
        }
        return new SignPrompt(typeOf(question.toString()), question.toString(), arrows);
    }

    /** A row of carets, or carets around a word. */
    private static boolean isArrows(String line) {
        String l = line == null ? "" : line.trim();
        return l.length() >= 3 && l.startsWith("^^") && l.endsWith("^^");
    }

    static Type typeOf(String question) {
        String q = question.toLowerCase(Locale.ROOT);
        if (q.contains("query") || q.contains("search")) return Type.SEARCH;
        if (q.contains("duration") || q.contains("hour") || q.contains("minute")) return Type.DURATION;
        if (q.contains("price") || q.contains("bid") || q.contains("coins")) return Type.PRICE;
        if (q.contains("amount") || q.contains("quantity") || q.contains("how many")) return Type.AMOUNT;
        return Type.TEXT;
    }

    /**
     * The answer as the sign's input lines, each within the sign's width as vanilla's own editor
     * measures it, or null if it does not fit. An answer longer than one line is broken at a space,
     * which Hypixel puts back when it joins the lines, so the lines are exactly what a player could
     * have typed into the vanilla sign. A word too long for a line is not broken: Hypixel would read
     * it as two words.
     */
    public String[] split(String text, ToIntFunction<String> width, int maxWidth) {
        String[] out = new String[inputLines];
        Arrays.fill(out, "");
        return fill(text == null ? "" : text, 0, out, width, maxWidth) ? out : null;
    }

    private static boolean fill(String rest, int line, String[] out, ToIntFunction<String> width, int maxWidth) {
        if (width.applyAsInt(rest) <= maxWidth) {
            out[line] = rest;
            return true;
        }
        if (line == out.length - 1) return false;
        // The longest start of the text that fits, ending before a space.
        for (int space = rest.lastIndexOf(' '); space > 0; space = rest.lastIndexOf(' ', space - 1)) {
            String head = rest.substring(0, space);
            if (width.applyAsInt(head) <= maxWidth) {
                out[line] = head;
                return fill(rest.substring(space + 1), line + 1, out, width, maxWidth);
            }
        }
        return false;
    }

    /**
     * What goes on the sign for what was typed. A price or an amount goes as plain digits, "4000000"
     * for "4m", which every one of Hypixel's prompts reads; not every one takes the short forms.
     * Skyblocker's and NoammAddons' price inputs send it the same way. Anything that is not a number
     * goes as typed.
     */
    public String answer(String typed) {
        if (!type.numeric() || typed == null) return typed;
        Double value = Coins.parseInput(typed);
        return value == null ? typed : Coins.plain(value, type == Type.PRICE);
    }

    /** Whether this is the price of an auction being created, rather than a bid on one. */
    private boolean auctionPrice() {
        return question.toLowerCase(Locale.ROOT).contains("starting bid");
    }

    /**
     * The bar's heading: what is being asked, in the market it is asked for if known.
     *
     * @param menuTitle the market menu the sign was opened from, such as "Create BIN Auction"; null if not known
     */
    public String heading(MarketKind context, String menuTitle) {
        String q = question.toLowerCase(Locale.ROOT);
        return switch (type) {
            case SEARCH -> context == MarketKind.AUCTION_HOUSE ? "Search the Auction House"
                    : context == MarketKind.BAZAAR ? "Search the Bazaar" : "Search";
            case PRICE -> auctionPrice()
                    ? menuTitle == null ? "Set Your Price" : menuTitle.contains("BIN") ? "Buy It Now Price" : "Starting Bid"
                    : q.contains("bid") ? "Place Your Bid" : "Set a Price";
            case AMOUNT -> "Choose an Amount";
            case DURATION -> "Auction Duration";
            case TEXT -> question.isEmpty() ? "Enter Text" : capitalize(question);
        };
    }

    /**
     * The line under the field: Hypixel's question, except where it says the wrong thing - "starting
     * bid" for a Buy It Now auction, which takes no bids.
     */
    public String subtitle(String menuTitle) {
        if (auctionPrice() && menuTitle != null && menuTitle.contains("BIN")) return "Buyers pay this at once";
        return question;
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
            case PRICE -> question.toLowerCase(Locale.ROOT).contains("bid") && !auctionPrice() ? "Bid" : "Confirm";
            default -> "Confirm";
        };
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}

package net.alpaka.addons.features.itemsearch;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.utils.ModVersion;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A page of the official Hypixel SkyBlock wiki, read in the game: fetched through the wiki's own
 * API as HTML and turned into what a Minecraft screen can show - headings, paragraphs, lists,
 * tables and the infobox's facts, in the wiki's colors, with its links kept so they can be
 * followed. Pictures and the crafting grids drawn out of pictures are left out. Pages are kept
 * until the game restarts. The requests name nothing about the player.
 */
public final class WikiPage {
    private WikiPage() {}

    public static final String SITE = "https://hypixelskyblock.minecraft.wiki";
    private static final String API = SITE + "/api.php";
    private static final long RETRY_MS = 20_000L;
    private static final int MAX_HTML = 4 * 1024 * 1024;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    public enum Kind { HEADING, SUBHEADING, PARAGRAPH, BULLET, FACT, ROW, HEADER_ROW }

    /**
     * One piece of the page. A fact has its label in {@code text} and its value in {@code value};
     * a table row its cells, rows of one table sharing {@code table}; a bullet its depth.
     */
    public record Block(Kind kind, Component text, Component value, List<Component> cells, int depth, int table) {}

    /** A page as read: its title and what is on it. */
    public record Page(String title, List<Block> blocks) {}

    public enum State { LOADING, READY, MISSING, FAILED }

    private record Entry(State state, Page page, long at) {}

    private static final Map<String, Entry> PAGES = new ConcurrentHashMap<>();

    /** The page, or null while it loads, is missing or failed; {@link #state} tells which. Asking starts loading it. */
    public static Page get(String title) {
        String key = title.toLowerCase(Locale.ROOT);
        Entry e = PAGES.get(key);
        if (e != null && (e.state != State.FAILED || System.currentTimeMillis() - e.at < RETRY_MS)) return e.page;
        if (!AlpakaConfig.instance.allowApiCalls) return null;
        PAGES.put(key, new Entry(State.LOADING, null, System.currentTimeMillis()));
        fetch(title).whenComplete((page, error) -> {
            if (error != null) {
                AlpakaAddons.LOGGER.debug("Could not load the wiki page {}", title, error);
                PAGES.put(key, new Entry(State.FAILED, null, System.currentTimeMillis()));
            } else {
                PAGES.put(key, new Entry(page == null ? State.MISSING : State.READY, page, System.currentTimeMillis()));
            }
        });
        return null;
    }

    public static State state(String title) {
        Entry e = PAGES.get(title.toLowerCase(Locale.ROOT));
        return e == null ? State.LOADING : e.state;
    }

    /** The page's address, for opening it in the browser instead. */
    public static String url(String title) {
        return SITE + "/w/" + URLEncoder.encode(title.replace(' ', '_'), StandardCharsets.UTF_8).replace("%2F", "/");
    }

    /** The wiki page of an item: the official wiki's page the repository names, else the item's name. */
    public static String titleFor(String id) {
        ItemRecipes.Info info = ItemRecipes.get(id);
        if (info != null) {
            for (String url : info.wiki()) {
                String title = titleOf(url);
                if (title != null) return title;
            }
        }
        return ItemRecipes.displayName(id);
    }

    /** The title in a link to the official wiki, "https://…/w/Hyperion" as "Hyperion"; null for another site. */
    static String titleOf(String url) {
        int at = url.indexOf("hypixelskyblock.minecraft.wiki/w/");
        if (at < 0) return null;
        String path = url.substring(at + "hypixelskyblock.minecraft.wiki/w/".length());
        int hash = path.indexOf('#');
        if (hash >= 0) path = path.substring(0, hash);
        int query = path.indexOf('?');
        if (query >= 0) path = path.substring(0, query);
        if (path.isEmpty()) return null;
        return URLDecoder.decode(path, StandardCharsets.UTF_8).replace('_', ' ');
    }

    private static CompletableFuture<Page> fetch(String title) {
        return parsePage(title).thenCompose(page -> {
            if (page != null) return CompletableFuture.completedFuture(page);
            // No page by that name: the wiki's search finds the one meant.
            return search(title).thenCompose(found -> found == null || found.equalsIgnoreCase(title)
                    ? CompletableFuture.completedFuture(null) : parsePage(found));
        });
    }

    private static CompletableFuture<String> call(String query) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(API + "?" + query))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "AlpakaAddons/" + ModVersion.mod())
                .GET().build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() != 200) throw new IllegalStateException("wiki answered " + response.statusCode());
            return response.body();
        });
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static CompletableFuture<Page> parsePage(String title) {
        return call("action=parse&format=json&formatversion=2&redirects=1&prop=text&disableeditsection=1&disablelimitreport=1&page="
                + enc(title)).thenApply(body -> {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("error")) return null;
            JsonObject parse = root.getAsJsonObject("parse");
            String html = parse.get("text").getAsString();
            if (html.length() > MAX_HTML) html = html.substring(0, MAX_HTML);
            return new Page(parse.get("title").getAsString(), parse(html));
        });
    }

    private static CompletableFuture<String> search(String text) {
        return call("action=query&format=json&formatversion=2&list=search&srlimit=1&srsearch=" + enc(text)).thenApply(body -> {
            JsonElement hits = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("query").get("search");
            if (hits == null || !hits.isJsonArray() || hits.getAsJsonArray().isEmpty()) return null;
            return hits.getAsJsonArray().get(0).getAsJsonObject().get("title").getAsString();
        });
    }

    // ---- From HTML ------------------------------------------------------------------------------

    /** Links to another page of the wiki carry this before the title, as the text's insertion. */
    public static final String LINK = "wiki:";

    private static final Pattern TAG = Pattern.compile("<(/?)([a-zA-Z][a-zA-Z0-9]*)([^>]*?)(/?)>|<!--.*?-->", Pattern.DOTALL);
    private static final Pattern ATTR = Pattern.compile("([a-zA-Z-]+)\\s*=\\s*\"([^\"]*)\"");
    private static final Set<String> VOID = Set.of("br", "img", "input", "hr", "meta", "link", "wbr", "source", "col");
    private static final Set<String> SKIP_TAGS = Set.of("style", "script", "sup", "input", "label", "audio", "video", "noscript");
    private static final List<String> SKIP_CLASSES = List.of("navbox", "mw-editsection", "toc", "noexcerpt", "mcui",
            "reference", "infobox-title", "mw-empty-elt", "noprint", "metadata", "mw-references", "printfooter", "catlinks");
    private static final int LINK_COLOR = 0x8AB4F8;
    private static final int BOLD_COLOR = 0xFFFFFF;

    /** The blocks of a page's HTML, as the wiki's API gives it. */
    static List<Block> parse(String html) {
        HtmlReader reader = new HtmlReader();
        Matcher m = TAG.matcher(html);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) reader.text(html.substring(last, m.start()));
            last = m.end();
            if (m.group(2) == null) continue;
            String name = m.group(2).toLowerCase(Locale.ROOT);
            if (m.group(1).isEmpty()) {
                reader.open(name, m.group(3));
                if (VOID.contains(name) || !m.group(4).isEmpty()) reader.close(name);
            } else {
                reader.close(name);
            }
        }
        if (last < html.length()) reader.text(html.substring(last));
        reader.flush();
        return tidy(reader.blocks);
    }

    /**
     * Leaves out what the wiki only fills in in a browser (prices shown as "???" until its scripts
     * run), and headings with nothing under them, such as those of the navigation boxes left out.
     */
    private static boolean unfilled(Block b) {
        String text = (b.text() == null ? "" : b.text().getString()) + (b.value() == null ? "" : b.value().getString());
        return text.contains("???");
    }

    private static List<Block> tidy(List<Block> blocks) {
        boolean[] drop = new boolean[blocks.size()];
        for (int i = 0; i < blocks.size(); i++) drop[i] = unfilled(blocks.get(i));
        // An infobox part whose every fact went is left out with its heading.
        for (int i = 0; i + 1 < blocks.size(); i++) {
            if (blocks.get(i).kind() != Kind.SUBHEADING || blocks.get(i + 1).kind() != Kind.FACT) continue;
            boolean all = true;
            for (int j = i + 1; j < blocks.size() && blocks.get(j).kind() == Kind.FACT; j++) all &= drop[j];
            drop[i] = all;
        }
        List<Block> kept = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) if (!drop[i]) kept.add(blocks.get(i));
        for (int i = kept.size() - 1; i >= 0; i--) {
            Kind k = kept.get(i).kind();
            if (k != Kind.HEADING && k != Kind.SUBHEADING) continue;
            Kind next = i + 1 < kept.size() ? kept.get(i + 1).kind() : null;
            boolean empty = next == null || next == Kind.HEADING || (k == Kind.SUBHEADING && next == Kind.SUBHEADING);
            if (empty) kept.remove(i);
        }
        return kept;
    }

    /** Runs of text with their style, made into a component once complete, the spaces evened out. */
    private static final class Buffer {
        final List<String> texts = new ArrayList<>();
        final List<Style> styles = new ArrayList<>();

        void add(String text, Style style) {
            texts.add(text);
            styles.add(style);
        }

        boolean blank() {
            for (String t : texts) if (!t.isBlank()) return false;
            return true;
        }

        /** A list separator, put right after the text before it. */
        static final String SEPARATOR = "\u0001";

        Component build() {
            List<StringBuilder> runs = new ArrayList<>();
            List<Style> runStyles = new ArrayList<>();
            boolean space = true;
            for (int i = 0; i < texts.size(); i++) {
                if (texts.get(i).equals(SEPARATOR)) {
                    // No space before the comma that joins two list items.
                    if (!runs.isEmpty()) {
                        StringBuilder prev = runs.get(runs.size() - 1);
                        while (!prev.isEmpty() && prev.charAt(prev.length() - 1) == ' ') prev.setLength(prev.length() - 1);
                    }
                    runs.add(new StringBuilder(", "));
                    runStyles.add(Style.EMPTY);
                    space = true;
                    continue;
                }
                StringBuilder run = new StringBuilder();
                for (char c : texts.get(i).toCharArray()) {
                    if (Character.isWhitespace(c) || c == '\u00A0') {
                        if (!space) run.append(' ');
                        space = true;
                    } else if (c < 0xE000 || c > 0xF8FF) {
                        // The wiki's stat icons are private-use glyphs Minecraft's fonts do not have.
                        run.append(c);
                        space = false;
                    }
                }
                runs.add(run);
                runStyles.add(styles.get(i));
            }
            MutableComponent out = Component.empty();
            for (int i = 0; i < runs.size(); i++) {
                if (!runs.get(i).isEmpty()) out.append(Component.literal(runs.get(i).toString()).withStyle(runStyles.get(i)));
            }
            return out;
        }
    }

    private static final class Node {
        final String name;
        final boolean skip;
        final Style style;
        final String role;

        Node(String name, boolean skip, Style style, String role) {
            this.name = name;
            this.skip = skip;
            this.style = style;
            this.role = role;
        }
    }

    private static final class HtmlReader {
        final List<Block> blocks = new ArrayList<>();
        final Deque<Node> stack = new ArrayDeque<>();
        int skipping = 0;
        Buffer current = new Buffer();
        Kind kind = Kind.PARAGRAPH;
        int depth = 0;
        int lists = 0;
        int tables = 0;
        int tableId = 0;
        List<Component> cells = null;
        Buffer cell = null;
        boolean headerRow = false;
        Buffer label = null;
        Buffer value = null;
        String target = null;

        Style style() {
            return stack.isEmpty() ? Style.EMPTY : stack.peek().style;
        }

        void open(String name, String attrs) {
            Map<String, String> a = attributes(attrs);
            String cls = a.getOrDefault("class", "");
            boolean skip = skipping > 0 || SKIP_TAGS.contains(name) || "toc".equals(a.get("id"))
                    || a.getOrDefault("style", "").replace(" ", "").contains("display:none") || skipClass(cls);
            Style style = style();
            String role = null;
            if (!skip) {
                style = styled(style, name, cls, a);
                if (a.containsKey("data-minetip-title")) {
                    // An item picture with its name: the name stands for it.
                    text(minetip(a.get("data-minetip-title")));
                    skip = true;
                } else {
                    role = structure(name, cls);
                }
            }
            stack.push(new Node(name, skip, style, role));
            if (skip) skipping++;
            if (!skip && name.equals("br")) lineBreak();
        }

        void close(String name) {
            // Badly nested HTML: unwind to the matching element, closing what lies between.
            boolean found = false;
            for (Node n : stack) {
                if (n.name.equals(name)) {
                    found = true;
                    break;
                }
            }
            if (!found) return;
            while (!stack.isEmpty()) {
                Node n = stack.pop();
                if (n.skip) skipping--;
                else if (n.role != null) end(n.role);
                if (n.name.equals(name)) break;
            }
        }

        static boolean skipClass(String cls) {
            if (cls.isEmpty()) return false;
            for (String word : cls.split("\\s+")) {
                for (String skip : SKIP_CLASSES) if (word.equals(skip) || word.startsWith(skip + "-")) return true;
            }
            return false;
        }

        Style styled(Style style, String name, String cls, Map<String, String> a) {
            switch (name) {
                // Bold drawn as the brighter text color: a smooth font's faked bold blurs.
                case "b", "strong" -> {
                    if (style.getColor() == null) style = style.withColor(BOLD_COLOR);
                }
                case "i", "em" -> style = style.withItalic(true);
                case "a" -> {
                    String href = a.getOrDefault("href", "");
                    if (href.startsWith("/w/") && !cls.contains("new")) {
                        String title = titleOf(SITE + href);
                        if (title != null && !title.contains(":")) {
                            style = style.withColor(LINK_COLOR).withInsertion(LINK + title);
                        }
                    }
                }
                default -> {
                }
            }
            for (String word : cls.split("\\s+")) {
                if (word.startsWith("color-")) {
                    try {
                        ChatFormatting color = ChatFormatting.valueOf(word.substring(6).toUpperCase(Locale.ROOT));
                        if (net.minecraft.network.chat.TextColor.fromLegacyFormat(color) != null) style = style.withColor(color);
                    } catch (IllegalArgumentException ignored) {
                        // Not one of Minecraft's sixteen colors.
                    }
                }
            }
            return style;
        }

        /** What the element starts in the page's structure; the same name ends it. */
        String structure(String name, String cls) {
            if (cls.contains("infobox-row-container")) return "fact";
            if (cls.contains("infobox-header")) {
                start(Kind.SUBHEADING);
                return "block";
            }
            if (cls.contains("infobox-row-label")) {
                label = new Buffer();
                target = "label";
                return "label";
            }
            if (cls.contains("infobox-row-value")) {
                value = new Buffer();
                target = "value";
                return "value";
            }
            switch (name) {
                case "h1", "h2" -> {
                    start(Kind.HEADING);
                    return "block";
                }
                case "h3", "h4", "h5", "h6" -> {
                    start(Kind.SUBHEADING);
                    return "block";
                }
                case "p", "div", "dd", "dt", "blockquote", "pre", "center" -> {
                    if (tables == 0) flush();
                    return tables == 0 ? "block" : null;
                }
                case "ul", "ol", "dl" -> {
                    if (tables == 0) flush();
                    lists++;
                    return "list";
                }
                case "li" -> {
                    if (tables > 0) {
                        if (cell != null && !cell.blank()) cell.add(Buffer.SEPARATOR, Style.EMPTY);
                        return null;
                    }
                    start(Kind.BULLET);
                    depth = Math.max(0, lists - 1);
                    return "block";
                }
                case "table" -> {
                    if (tables == 0) {
                        flush();
                        tableId++;
                    }
                    tables++;
                    return "table";
                }
                case "tr" -> {
                    if (tables == 1) {
                        cells = new ArrayList<>();
                        headerRow = true;
                        return "row";
                    }
                    return null;
                }
                case "td", "th" -> {
                    if (tables == 1 && cells != null) {
                        cell = new Buffer();
                        if (name.equals("td")) headerRow = false;
                        return "cell";
                    }
                    if (cell != null) cell.add(" ", Style.EMPTY);
                    return null;
                }
                default -> {
                    return null;
                }
            }
        }

        void end(String role) {
            switch (role) {
                case "block" -> {
                    if (tables == 0) flush();
                }
                case "list" -> lists = Math.max(0, lists - 1);
                case "table" -> {
                    tables = Math.max(0, tables - 1);
                    if (tables == 0) {
                        cells = null;
                        cell = null;
                    }
                }
                case "cell" -> {
                    if (cells != null && cell != null) cells.add(cell.build());
                    cell = null;
                }
                case "row" -> {
                    if (cells != null && !cells.isEmpty()) {
                        boolean any = false;
                        for (Component c : cells) any |= !c.getString().isBlank();
                        if (any) blocks.add(new Block(headerRow ? Kind.HEADER_ROW : Kind.ROW, null, null, List.copyOf(cells), 0, tableId));
                    }
                    cells = null;
                }
                case "label", "value" -> target = null;
                case "fact" -> {
                    if (label != null && value != null && !value.blank()) {
                        blocks.add(new Block(Kind.FACT, label.build(), value.build(), List.of(), 0, 0));
                    } else if (value != null && !value.blank()) {
                        blocks.add(new Block(Kind.PARAGRAPH, value.build(), null, List.of(), 0, 0));
                    }
                    label = null;
                    value = null;
                    target = null;
                }
                default -> {
                }
            }
        }

        void start(Kind k) {
            flush();
            kind = k;
        }

        void lineBreak() {
            if (target != null || cell != null) {
                text(" ");
                return;
            }
            Kind k = kind;
            int d = depth;
            flush();
            kind = k == Kind.BULLET ? Kind.PARAGRAPH : k;
            depth = d;
        }

        void flush() {
            if (!current.blank()) blocks.add(new Block(kind, current.build(), null, List.of(), depth, 0));
            current = new Buffer();
            kind = Kind.PARAGRAPH;
            depth = 0;
        }

        void text(String raw) {
            if (skipping > 0 || raw.isEmpty()) return;
            String text = decode(raw);
            Style style = style();
            if ("label".equals(target) && label != null) label.add(text, style);
            else if ("value".equals(target) && value != null) value.add(text, style);
            else if (cell != null) cell.add(text, style);
            else if (tables > 0) return;
            else current.add(text, style);
        }
    }

    private static Map<String, String> attributes(String attrs) {
        Map<String, String> out = new java.util.HashMap<>();
        Matcher m = ATTR.matcher(attrs);
        while (m.find()) out.put(m.group(1).toLowerCase(Locale.ROOT), decode(m.group(2)));
        return out;
    }

    /** An item's name as the wiki's tooltips write it, "&6Hyperion": its color codes taken out. */
    private static String minetip(String title) {
        return title.replaceAll("&[0-9a-fk-or]", "").trim();
    }

    private static final Pattern ENTITY = Pattern.compile("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);");

    static String decode(String s) {
        if (s.indexOf('&') < 0) return s;
        Matcher m = ENTITY.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String e = m.group(1);
            String r;
            try {
                if (e.startsWith("#x")) r = new String(Character.toChars(Integer.parseInt(e.substring(2), 16)));
                else if (e.startsWith("#")) r = new String(Character.toChars(Integer.parseInt(e.substring(1))));
                else r = switch (e) {
                    case "amp" -> "&";
                    case "lt" -> "<";
                    case "gt" -> ">";
                    case "quot" -> "\"";
                    case "apos" -> "'";
                    case "nbsp" -> " ";
                    case "ndash" -> "–";
                    case "mdash" -> "—";
                    case "hellip" -> "…";
                    case "times" -> "×";
                    default -> m.group();
                };
            } catch (IllegalArgumentException ex) {
                r = m.group();
            }
            m.appendReplacement(out, Matcher.quoteReplacement(r));
        }
        m.appendTail(out);
        return out.toString();
    }
}

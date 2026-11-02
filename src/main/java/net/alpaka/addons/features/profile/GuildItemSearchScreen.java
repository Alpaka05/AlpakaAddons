package net.alpaka.addons.features.profile;

import net.alpaka.addons.client.AlpakaConfigScreen;
import net.alpaka.addons.client.AlpakaConfirmScreen;
import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;

/** Search controls and saved results; closing this window leaves an explicitly started run working. */
public final class GuildItemSearchScreen extends Screen {
    private final Screen parent;
    private final GuildItemSearch search;
    private final GuildItemSearch.View preview;
    private final List<Hit> hits = new ArrayList<>();
    private int tab;
    private double scroll;
    private double maxScroll;
    private long opened;
    private String notice;

    private record Hit(int x, int y, int w, int h, Runnable action) {}

    public GuildItemSearchScreen(Screen parent) {
        this(parent, GuildItemSearchService.instance(), null);
    }

    private GuildItemSearchScreen(Screen parent, GuildItemSearch search, GuildItemSearch.View preview) {
        super(GuiFont.text("Space Helmet Search"));
        this.parent = parent;
        this.search = search;
        this.preview = preview;
    }

    @Override
    protected void init() { if (opened == 0) opened = System.currentTimeMillis(); }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() { Minecraft.getInstance().gui.setScreen(parent); }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        AlpakaStyle.backdrop(graphics, this.width, this.height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        GuildItemSearch.View v = preview == null ? search.view() : preview;
        int w = Math.min(650, this.width - 16);
        int h = Math.min(390, this.height - 16);
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        int pad = AlpakaStyle.PAD;
        boolean pushed = AlpakaStyle.beginOpen(graphics, opened, this.width, this.height);
        AlpakaStyle.panel(graphics, x, y, w, h);
        AlpakaStyle.header(graphics, this.font, "Space Helmet Search", x, y, w);
        int by = AlpakaStyle.headerButtonY(y);
        button(graphics, x + w - pad - 42, by, 42, "Done", true, this::onClose, mouseX, mouseY);
        button(graphics, x + w - pad - 108, by, 60, "Settings", preview == null,
                () -> Minecraft.getInstance().gui.setScreen(AlpakaConfigScreen.openOnOption(this,
                        AlpakaConfig.instance.allowApiCalls ? "profile_dashboard_skykings_key" : "allow_api_calls")), mouseX, mouseY);

        int left = x + pad;
        int innerW = w - 2 * pad;
        int top = y + AlpakaStyle.HEADER_H + 9;
        text(graphics, "Space Helmet · From [ADMIN] Dctr · Any Recipient", left, top, innerW, AlpakaStyle.TEXT);
        text(graphics, "Top 10 Normal Guilds · All Profiles · Inventory, Storage, Wardrobe & Armor", left, top + 13, innerW, AlpakaStyle.TEXT_MUTED);
        String counts = v.guildsLoaded() < 10 ? "Guild Members: " + v.guildsLoaded() + "/10 Loaded"
                : "Players: " + v.playersDone() + "/" + v.playersTotal();
        counts += "   Profiles: " + v.profilesChecked() + "   Inventories: " + v.inventoriesChecked();
        text(graphics, counts, left, top + 28, innerW, AlpakaStyle.TEXT_SOFT);
        ModernGuiUtils.drawRoundedRect(graphics, left, top + 41, innerW, 3, 1, AlpakaStyle.ROW_SELECTED);
        double fraction = v.complete() ? 1 : v.guildsLoaded() < 10 ? v.guildsLoaded() / 10.0 * 0.04
                : 0.04 + (v.playersTotal() == 0 ? 0 : (double) v.playersDone() / v.playersTotal()) * 0.96;
        int filled = (int) Math.round(innerW * fraction);
        if (filled > 0) ModernGuiUtils.drawRoundedRect(graphics, left, top + 41, filled, 3, 1, AlpakaStyle.accent());

        String status = preview != null ? v.message() : !AlpakaConfig.instance.allowApiCalls ? "Network Features are off. Enable them in Settings."
                : preview == null && AlpakaConfig.instance.skykingsApiKey.isBlank() ? "Set your SkyKings API key in Settings to start."
                : notice == null ? v.message() : notice;
        text(graphics, status, left, top + 51, innerW, AlpakaStyle.TEXT_SOFT);
        if (AlpakaStyle.inside(mouseX, mouseY, left, top + 48, innerW, 14)) {
            // Draw the complete status later, outside the clipped list.
            noticeTip = status;
        } else noticeTip = null;

        String[] tabs = {"Matches (" + v.matches().size() + ")", "Not Checked (" + v.issues().size() + ")", "Guilds (" + v.guilds().size() + ")"};
        int tx = left;
        for (int i = 0; i < tabs.length; i++) {
            int index = i;
            int tw = GuiFont.width(this.font, tabs[i]) + 14;
            AlpakaStyle.button(graphics, this.font, tx, top + 68, tw, AlpakaStyle.BUTTON_H, tabs[i],
                    AlpakaStyle.inside(mouseX, mouseY, tx, top + 68, tw, AlpakaStyle.BUTTON_H),
                    tab == i ? AlpakaStyle.ButtonKind.PRIMARY : AlpakaStyle.ButtonKind.PLAIN);
            hits.add(new Hit(tx, top + 68, tw, AlpakaStyle.BUTTON_H, () -> { tab = index; scroll = 0; }));
            tx += tw + 5;
        }

        int listY = top + 92;
        int controlsY = y + h - pad - AlpakaStyle.BUTTON_H;
        int listH = Math.max(10, controlsY - 18 - listY);
        int rows = tab == 0 ? v.matches().size() : tab == 1 ? v.issues().size() : v.guilds().size();
        int rowH = tab == 0 ? 39 : tab == 1 ? 32 : 23;
        maxScroll = Math.max(0, rows * rowH - listH);
        scroll = Math.clamp(scroll, 0, maxScroll);
        GuildItemSearch.Match hovered = null;
        String issueTip = null;
        graphics.enableScissor(left, listY, left + innerW, listY + listH);
        for (int i = 0; i < rows; i++) {
            int ry = listY + i * rowH - (int) scroll;
            if (ry + rowH < listY || ry >= listY + listH) continue;
            boolean hover = mouseY >= listY && mouseY < listY + listH && AlpakaStyle.inside(mouseX, mouseY, left, ry, innerW, rowH - 3);
            ModernGuiUtils.drawRoundedRect(graphics, left, ry, innerW, rowH - 3, AlpakaStyle.ROW_RADIUS,
                    hover ? AlpakaStyle.ROW_HOVER : AlpakaStyle.ROW);
            if (tab == 0) {
                GuildItemSearch.Match match = v.matches().get(i);
                text(graphics, match.player() + "  ·  " + match.profile() + "  ·  " + match.guild(), left + 7, ry + 6, innerW - 55, AlpakaStyle.TEXT);
                String age = match.updated() > 0 ? "  ·  Updated " + Metrics.ago(match.updated() * 1000L) : "  ·  Update Time Unknown";
                text(graphics, match.location() + age, left + 7, ry + 20, innerW - 55, AlpakaStyle.TEXT_MUTED);
                int openX = left + innerW - 45;
                button(graphics, openX, ry + 9, 38, "Open", preview == null && AlpakaConfig.instance.allowApiCalls
                                && ry + 9 >= listY && ry + 9 + AlpakaStyle.BUTTON_H <= listY + listH,
                        () -> ProfileDashboardScreen.openMatch(match, this), mouseX, mouseY);
                if (hover && mouseX < openX) hovered = match;
            } else if (tab == 1) {
                GuildItemSearch.Issue issue = v.issues().get(i);
                text(graphics, issue.player() + "  ·  " + issue.profile() + "  ·  " + issue.guild(), left + 7, ry + 5, innerW - 14, AlpakaStyle.TEXT);
                text(graphics, issue.reason(), left + 7, ry + 17, innerW - 14, AlpakaStyle.TEXT_MUTED);
                if (hover) issueTip = issue.reason();
            } else {
                GuildItemSearch.Guild guild = v.guilds().get(i);
                text(graphics, "#" + guild.rank() + "  " + guild.name(), left + 7, ry + 7, innerW - 14, AlpakaStyle.TEXT);
            }
        }
        if (rows == 0) {
            String empty = tab == 0 ? "No matching helmets found yet." : tab == 1 ? "No coverage gaps recorded." : "The guild ranking is saved when you start.";
            text(graphics, empty, left + 7, listY + 9, innerW - 14, AlpakaStyle.TEXT_MUTED);
        }
        graphics.disableScissor();
        AlpakaStyle.scrollFades(graphics, left, listY, innerW, listH, scroll > 0, scroll < maxScroll);
        String footer = v.running() ? "Search continues when this window is closed." : "Progress and results are saved on this computer.";
        if (v.started() > 0 && !v.guilds().isEmpty()) footer += " Run: " + java.time.Instant.ofEpochMilli(v.started())
                .atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm", java.util.Locale.US));
        text(graphics, footer,
                left, controlsY - 13, innerW, AlpakaStyle.TEXT_MUTED);

        boolean real = preview == null;
        boolean started = v.guildsLoaded() > 0 || !v.guilds().isEmpty();
        boolean ended = v.complete() || v.cancelled();
        String primary = v.running() ? "Pause" : ended || !started ? "Start" : "Resume";
        button(graphics, left, controlsY, 60, primary, real && (v.running() || GuildItemSearchService.ready()), () -> {
            notice = null;
            if (search.view().running()) search.pause();
            else if (search.view().complete() || search.view().cancelled()) confirmNew();
            else search.start(false);
        }, mouseX, mouseY);
        button(graphics, left + 66, controlsY, 60, "New Run", real && !v.running() && started,
                this::confirmNew, mouseX, mouseY);
        button(graphics, left + 132, controlsY, 58, "Cancel", real && !ended && (started || v.running()),
                () -> Minecraft.getInstance().gui.setScreen(new AlpakaConfirmScreen(this, "Cancel Search",
                        "Stop this run? Its results stay saved, but continuing will require a new run.", "Cancel Run", true, () -> {
                    search.cancel();
                    Minecraft.getInstance().gui.setScreen(this);
                })), mouseX, mouseY);
        button(graphics, left + innerW - 72, controlsY, 72, "Copy CSV", real && !v.matches().isEmpty(), () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(search.csv());
            notice = "Results copied as CSV. Paste into a spreadsheet or save as a .csv file.";
        }, mouseX, mouseY);
        AlpakaStyle.endOpen(graphics, pushed);
        if (hovered != null) graphics.setComponentTooltipForNextFrame(this.font, InventoryItems.tooltip(hovered.item()), mouseX, mouseY);
        else if (issueTip != null || noticeTip != null) AlpakaStyle.tooltip(graphics, this.font, this.width, this.height, mouseX, mouseY,
                issueTip != null ? issueTip : noticeTip);
    }

    private String noticeTip;

    private void confirmNew() {
        Minecraft.getInstance().gui.setScreen(new AlpakaConfirmScreen(this, "Start New Search",
                "Replace the saved run and its results with a search of the current top 10 Normal guilds? Copy CSV first to keep the matches.",
                "New Run", true, () -> {
            notice = null;
            scroll = 0;
            search.start(true);
            Minecraft.getInstance().gui.setScreen(this);
        }));
    }

    private void text(GuiGraphicsExtractor graphics, String text, int x, int y, int w, int color) {
        graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, text, Math.max(0, w))), x, y, color, false);
    }

    private void button(GuiGraphicsExtractor graphics, int x, int y, int w, String label, boolean enabled,
                        Runnable action, int mx, int my) {
        AlpakaStyle.button(graphics, this.font, x, y, w, AlpakaStyle.BUTTON_H, label,
                AlpakaStyle.inside(mx, my, x, y, w, AlpakaStyle.BUTTON_H), AlpakaStyle.ButtonKind.PLAIN, enabled);
        if (enabled) hits.add(new Hit(x, y, w, AlpakaStyle.BUTTON_H, action));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && System.currentTimeMillis() - opened >= 220) {
            for (Hit hit : hits) {
                if (AlpakaStyle.inside(event.x(), event.y(), hit.x(), hit.y(), hit.w(), hit.h())) {
                    hit.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.clamp(scroll - scrollY * 32, 0, maxScroll);
        return true;
    }

    /** A tour can inspect results without contacting APIs or touching the player's checkpoint. */
    public static GuildItemSearchScreen devPreview(Screen parent, boolean results) {
        com.google.gson.JsonObject item = com.google.gson.JsonParser.parseString("""
                {"skyblockId":"DCTR_SPACE_HELM","name":"§cSpace Helmet","lore":["§7From: §c[ADMIN] Dctr"],
                 "attributes":{"sender_name":"§c[ADMIN] Dctr","recipient_name":"its_Eleminator"}}
                """).getAsJsonObject();
        List<GuildItemSearch.Match> matches = results ? List.of(new GuildItemSearch.Match("", "fkn9", "Example Guild", "", "Pineapple",
                "wardrobe", "12:HELMET", System.currentTimeMillis() / 1000L, item)) : List.of();
        GuildItemSearch.View view = new GuildItemSearch.View(false, results, false, results ? "Preview of a matching helmet and its location." : "Ready to search the top 10 Normal guilds on SkyKings.",
                0, results ? 10 : 0, List.of(), results ? 1250 : 0, results ? 1250 : 0, results ? 1840 : 0, results ? 23000 : 0,
                matches, List.of(new GuildItemSearch.Issue("ExamplePlayer", "Example Guild", "Apple", "Inventory API is off or unavailable.")));
        return new GuildItemSearchScreen(parent, null, view);
    }
}

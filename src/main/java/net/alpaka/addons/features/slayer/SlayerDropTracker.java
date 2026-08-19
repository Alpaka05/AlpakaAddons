package net.alpaka.addons.features.slayer;

import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.sound.CustomSoundFeature;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.ChatFormatting;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SlayerDropTracker {

    /**
     * A slayer boss was killed. This is the only message Hypixel sends on a kill, and it does not
     * name the slayer - which is why the type comes from {@link SlayerQuestDetector} instead.
     */
    private static final Pattern QUEST_COMPLETE_PATTERN = Pattern.compile("^\\s*SLAYER QUEST COMPLETE!\\s*$");

    /**
     * A rare drop. Hypixel's slayer drops put the item in parentheses and use <em>two</em> spaces
     * after "DROP!" - e.g. {@code "VERY RARE DROP!  (High Class Archfiend Dice) +305% ✯ Magic Find"}
     * once colour codes are stripped. The previous pattern required exactly one space and no leading
     * whitespace, so it never matched a single real drop.
     */
    private static final Pattern DROP_PATTERN = Pattern.compile(
            "^\\s*(?:UNCOMMON|RARE|VERY RARE|CRAZY RARE|INSANE|PRAY TO RNGESUS) DROP!\\s+\\((?<item>[^)]+)\\).*");

    /** Leading noise on a drop's item name, e.g. "3x " or the rune "◆ " marker. */
    private static final Pattern DROP_ITEM_PREFIX = Pattern.compile("^(?:\\d+x\\s+)?(?:◆\\s*)?");

    private static final Pattern SERVER_DROP_PATTERN = Pattern.compile("^\\s*(?:UNCOMMON|RARE|VERY RARE|CRAZY RARE|INSANE|PRAY TO RNGESUS|PET) DROP!.*");
    private static final Pattern PARTY_PATTERN = Pattern.compile("^Party > (?:\\[[A-Z+]+] )?\\w+: !since (?<item>.+)$");
    /**
     * Every legacy formatting code, not just the standard set - Hypixel uses codes outside it as
     * padding. See {@link net.alpaka.addons.utils.SkyblockUtils#cleanColor}.
     */
    private static final Pattern COLOR_PATTERN = Pattern.compile("§.");

    /** Ignore a second completion message this soon after one was counted. */
    private static final long KILL_DEBOUNCE_MS = 2000L;

    /**
     * Ticks to hold a drop before reporting it. The drop message arrives just before the quest
     * completion, so waiting lets the kill be counted first and keeps "took N bosses" off by none.
     */
    private static final int DROP_DELAY_TICKS = 30;

    public static SlayerType currentBoss = null;
    public static final ThreadLocal<Boolean> IS_PROCESSING = ThreadLocal.withInitial(() -> false);

    private static long lastKillCountedAtMs = 0L;
    private static int tickCounter = 0;
    private static final java.util.List<PendingDrop> PENDING_DROPS = new java.util.ArrayList<>();

    /** A drop waiting for its boss kill to be counted. */
    private record PendingDrop(String item, Component message, SlayerType type, int dueTick) {}

    public static void registerEvents() {
        // No isOnSkyblock() gate here on purpose. That check requires the sidebar *title* to contain
        // "skyblock", but Hypixel renames it during events (it reads "BLAZE SIMULATOR" during one),
        // which silently switched off all slayer tracking. The patterns below are specific enough to
        // stand on their own, and a drop is only ever recorded once a slayer quest has been read off
        // the sidebar.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            onChat(message);
        });

        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            onChat(message);
        });

        // Drives the deferred drop reporting, and keeps the sidebar read warm so a boss type is
        // already known by the time the kill message lands.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tickCounter++;
            SlayerQuestDetector.INSTANCE.refresh();

            // Primary kill signal: the sidebar leaving the boss fight. Chat is unreliable here.
            SlayerType killed = SlayerQuestDetector.INSTANCE.consumeKill();
            if (killed != null) countKill(killed);

            // Only signal for a boss spawning: Hypixel never sends a chat announcement for a
            // regular slayer boss (unlike the Ender Dragon, Arachne, etc.), confirmed against
            // SkyHanni's own pattern list, which has no such entry for slayer bosses.
            SlayerType spawned = SlayerQuestDetector.INSTANCE.consumeSpawn();
            if (spawned != null && AlpakaConfig.instance.customSoundsEnabled) {
                CustomSoundFeature.playBossSpawnSound();
            }

            flushPendingDrops();
        });

        // Messages that CleanBlazeFeature wants hidden are NOT cancelled here. ALLOW_GAME runs
        // before the GAME event above, so returning false would discard the message before onChat
        // ever sees it - which is exactly what silently broke slayer-quest and kill tracking whenever
        // clean-blaze mode was on. Hiding a message from the visible chat log and letting mods still
        // process it are different concerns; only ChatComponentMixin (which cancels the GUI's
        // addMessage call, downstream of all processing events) does the former.
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay) return true;

            if (!AlpakaConfig.instance.nameHighlightingEnabled) return true;
            if (IS_PROCESSING.get()) return true;

            if (message.getString().contains("Alpakaa")) {
                IS_PROCESSING.set(true);
                try {
                    Component highlighted = highlightName(message);
                    sendPlainMessage(highlighted);
                    return false; // Cancel original message
                } finally {
                    IS_PROCESSING.set(false);
                }
            }
            return true;
        });

        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            if (!AlpakaConfig.instance.nameHighlightingEnabled) return true;
            if (IS_PROCESSING.get()) return true;

            if (message.getString().contains("Alpakaa")) {
                IS_PROCESSING.set(true);
                try {
                    Component highlighted = highlightName(message);
                    sendPlainMessage(highlighted);
                    return false; // Cancel original message
                } finally {
                    IS_PROCESSING.set(false);
                }
            }
            return true;
        });

    }

    public static boolean isOnSkyblock() {
        return net.alpaka.addons.utils.SkyblockUtils.isOnSkyblock();
    }

    public static boolean isInRift() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective != null) {
            String displayName = cleanColor(objective.getDisplayName().getString()).toLowerCase();
            return displayName.contains("rift");
        }
        return false;
    }

    public static String cleanColor(String text) {
        if (text == null) return "";
        return COLOR_PATTERN.matcher(text).replaceAll("");
    }

    private static Component getDropComponent(Component component, String text) {
        Component matched = findComponent(component, text);
        return matched != null ? matched : Component.literal(text);
    }

    private static Component findComponent(Component component, String text) {
        if (!component.getString().contains(text)) {
            return null;
        }
        for (Component sibling : component.getSiblings()) {
            Component found = findComponent(sibling, text);
            if (found != null) {
                return found;
            }
        }
        return component;
    }

    public static Component highlightName(Component component) {
        if (component == null) return null;
        if (!component.getString().contains("Alpakaa")) {
            return component;
        }

        MutableComponent result;

        if (component.getContents() instanceof PlainTextContents plainTextContents) {
            String text = plainTextContents.text();
            if (text.contains("Alpakaa")) {
                result = highlightLiteralString(text, component.getStyle());
            } else {
                result = Component.literal(text).withStyle(component.getStyle());
            }
        } else {
            result = MutableComponent.create(component.getContents()).withStyle(component.getStyle());
        }

        for (Component sibling : component.getSiblings()) {
            result.append(highlightName(sibling));
        }

        return result;
    }

    private static MutableComponent highlightLiteralString(String text, Style style) {
        MutableComponent container = Component.empty();
        int lastIdx = 0;
        int idx;
        while ((idx = text.indexOf("Alpakaa", lastIdx)) != -1) {
            if (idx > lastIdx) {
                container.append(Component.literal(text.substring(lastIdx, idx)).withStyle(style));
            }
            container.append(Component.literal("Alpakaa")
                    .withStyle(style.withColor(ChatFormatting.LIGHT_PURPLE).withBold(true)));
            lastIdx = idx + "Alpakaa".length();
        }
        if (lastIdx < text.length()) {
            container.append(Component.literal(text.substring(lastIdx)).withStyle(style));
        }
        return container;
    }

    public static void sendModMessage(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            MutableComponent prefix = Component.literal("§6[AA] ").append(message);
            mc.player.sendSystemMessage(prefix);
        }
    }

    public static void sendModMessage(String message) {
        sendModMessage(Component.literal(message));
    }

    private static void sendPlainMessage(Component message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(message);
        }
    }

    public static void onChat(Component message) {
        if (!AlpakaConfig.instance.slayerDropTrackerEnabled) return;

        String string = cleanColor(message.getString());

        if (AlpakaConfig.instance.customSoundsEnabled && SERVER_DROP_PATTERN.matcher(string).matches()) {
            String lower = string.toLowerCase();
            if (lower.contains("insane drop!") || lower.contains("crazy rare drop!") || lower.contains("judgement core") || lower.contains("warden heart") || lower.contains("archfiend dice")) {
                CustomSoundFeature.playInsaneDropSound();
            } else if (lower.contains("rare drop!") || lower.contains("very rare drop!")) {
                CustomSoundFeature.playRareDropSound();
            }
        }

        // Secondary kill signal. Hypixel does not always send this, and other Skyblock mods often
        // swallow it, so the sidebar transition in the tick handler is the one that usually fires.
        if (QUEST_COMPLETE_PATTERN.matcher(string).matches()) {
            SlayerType type = SlayerQuestDetector.INSTANCE.currentOrRecent();
            if (type != null) countKill(type);
            return;
        }

        // Check drop message
        Matcher dropMatcher = DROP_PATTERN.matcher(string);
        if (dropMatcher.matches()) {
            SlayerType activeBoss = isInRift() ? SlayerType.VAMPIRE : SlayerQuestDetector.INSTANCE.currentOrRecent();
            if (activeBoss == null) {
                // Not on a slayer - this was a drop from farming, mining, a dungeon, etc.
                return;
            }

            String drop = DROP_ITEM_PREFIX.matcher(dropMatcher.group("item").trim()).replaceFirst("").trim();
            if (drop.isEmpty()) return;

            // Held briefly so the kill lands first; see DROP_DELAY_TICKS.
            PENDING_DROPS.add(new PendingDrop(drop, message, activeBoss, tickCounter + DROP_DELAY_TICKS));
            return;
        }

        // Check party command
        handlePartyCommand(string);
    }

    /**
     * Records one boss kill, debounced so the sidebar and chat signals cannot both count it.
     */
    private static void countKill(SlayerType type) {
        if (!AlpakaConfig.instance.slayerDropTrackerEnabled) return;

        long now = System.currentTimeMillis();
        if (now - lastKillCountedAtMs < KILL_DEBOUNCE_MS) return;
        lastKillCountedAtMs = now;

        currentBoss = type;

        AlpakaConfig.SlayerData data = AlpakaConfig.instance.slayerBossMap.get(type);
        if (data == null) {
            data = new AlpakaConfig.SlayerData();
            AlpakaConfig.instance.slayerBossMap.put(type, data);
        }
        data.kills++;
        AlpakaConfig.save();
    }

    /** Reports drops whose hold-off has elapsed. Called once per client tick. */
    private static void flushPendingDrops() {
        if (PENDING_DROPS.isEmpty()) return;

        java.util.Iterator<PendingDrop> iterator = PENDING_DROPS.iterator();
        boolean changed = false;

        while (iterator.hasNext()) {
            PendingDrop pending = iterator.next();
            if (tickCounter < pending.dueTick()) continue;
            iterator.remove();

            if (!AlpakaConfig.instance.slayerDropTrackerEnabled) continue;

            AlpakaConfig.SlayerData data = AlpakaConfig.instance.slayerBossMap.get(pending.type());
            if (data == null) {
                data = new AlpakaConfig.SlayerData();
                AlpakaConfig.instance.slayerBossMap.put(pending.type(), data);
            }

            int currentKills = data.kills;
            Integer lastDropped = data.drops.get(pending.item());
            data.drops.put(pending.item(), currentKills);
            changed = true;

            Component dropComponent = getDropComponent(pending.message(), pending.item());
            MutableComponent feedback;
            if (lastDropped == null) {
                feedback = Component.literal("First ").append(dropComponent)
                        .append(Component.literal(" - at " + currentKills + " " + pending.type().display + " kills"));
            } else {
                int sinceLast = currentKills - lastDropped;
                feedback = Component.literal("Took " + sinceLast + " boss" + (sinceLast != 1 ? "es" : "") + " to drop ")
                        .append(dropComponent);
            }
            sendModMessage(feedback);
        }

        if (changed) AlpakaConfig.save();
    }

    public static void handlePartyCommand(String message) {
        if (!AlpakaConfig.instance.slayerDropTrackerEnabled) return;

        Matcher matcher = PARTY_PATTERN.matcher(message);
        if (matcher.matches()) {
            String queryDrop = matcher.group("item").trim();

            for (Map.Entry<SlayerType, AlpakaConfig.SlayerData> entry : AlpakaConfig.instance.slayerBossMap.entrySet()) {
                AlpakaConfig.SlayerData data = entry.getValue();
                if (data == null || data.drops == null) continue;

                for (Map.Entry<String, Integer> dropEntry : data.drops.entrySet()) {
                    if (dropEntry.getKey().equalsIgnoreCase(queryDrop)) {
                        int sinceLast = data.kills - dropEntry.getValue();
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null && mc.player.connection != null) {
                            mc.player.connection.sendCommand("pc Bosses since last " + dropEntry.getKey() + ": " + sinceLast);
                        }
                    }
                }
            }
        }
    }

    public static void printKills(LocalPlayer player) {
        if (player == null) return;
        sendModMessage("§6--- Slayer Kills ---");
        for (SlayerType type : SlayerType.values()) {
            AlpakaConfig.SlayerData data = AlpakaConfig.instance.slayerBossMap.get(type);
            if (type == SlayerType.GUARDIAN && (data == null || data.kills == 0)) {
                continue;
            }
            int kills = data != null ? data.kills : 0;
            sendModMessage("§e" + type.display + ": §a" + kills + " §7kills");
        }

        // Every drop that has actually been recorded, rather than a fixed list of names. The old
        // hard-coded list only matched on exact spelling, so a tracked "Archfiend Dice" was reported
        // as "never" simply because the list asked for "High class archfiend dice".
        sendModMessage("§6--- Tracked Drops ---");
        boolean any = false;
        for (SlayerType type : SlayerType.values()) {
            AlpakaConfig.SlayerData data = AlpakaConfig.instance.slayerBossMap.get(type);
            if (data == null || data.drops == null || data.drops.isEmpty()) continue;
            any = true;

            sendModMessage("§e" + type.display + "§7:");
            data.drops.entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByValue(java.util.Comparator.reverseOrder()))
                    .forEach(entry -> {
                        int sinceLast = data.kills - entry.getValue();
                        sendModMessage("§7  " + entry.getKey() + " §8- §a" + sinceLast + " §7boss"
                                + (sinceLast != 1 ? "es" : "") + " ago");
                    });
        }
        if (!any) {
            sendModMessage("§7  §8nothing recorded yet");
        }
    }

}

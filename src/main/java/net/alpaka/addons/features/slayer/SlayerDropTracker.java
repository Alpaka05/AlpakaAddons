package net.alpaka.addons.features.slayer;

import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.config.AlpakaStats;
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
     * A slayer boss was killed. Neither message names the slayer, which is why the type comes from
     * {@link SlayerQuestDetector} instead.
     *
     * Hypixel sends both {@code "  SLAYER QUEST COMPLETE!"} and {@code "  NICE! SLAYER BOSS SLAIN!"};
     * either is accepted since the kill is debounced anyway. Both were confirmed against SkyHanni's
     * captured-message tests rather than guessed.
     */
    private static final Pattern QUEST_COMPLETE_PATTERN =
            Pattern.compile("^\\s*(?:SLAYER QUEST COMPLETE!|NICE! SLAYER BOSS SLAIN!)\\s*$");

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

    /**
     * The RNG meter reading Hypixel prints after every slayer boss kill, e.g.
     * {@code "RNG Meter - 69,300 Stored XP"} once colour codes are stripped.
     *
     * This is the only message that states a slayer XP figure, and the step between two consecutive
     * readings is the XP that kill awarded - two captured back to back read 69,300 then 69,850. It
     * only arrives when the player has an RNG meter set for the slayer, which is why
     * {@link SlayerSessionTracker} keeps a per-tier fallback.
     */
    private static final Pattern RNG_METER_PATTERN =
            Pattern.compile("^\\s*RNG Meter\\s*-\\s*(?<xp>[\\d,]+)\\s+Stored XP\\s*$");

    /**
     * Hypixel's own wording when a slayer quest is dropped, from a captured log.
     *
     * Anchored on the whole line rather than searched for: "cancelled" alone also appears in
     * unrelated messages such as "Ragnarock was cancelled due to taking damage!", which has nothing
     * to do with the slayer quest.
     */
    /**
     * Hypixel's line when a quest ends because the player died.
     *
     * Arrives <em>after</em> the sidebar has already dropped the quest, so this used to be too late
     * to stop the vanishing quest being counted as a kill - watching the client's own player die was
     * meant to catch it first, and did not. Since {@link SlayerQuestDetector} holds an inferred kill
     * back for a moment, this line is early enough to be the reliable signal rather than a spare one.
     */
    private static final Pattern QUEST_FAILED_PATTERN =
            Pattern.compile("^\\s*SLAYER QUEST FAILED!\\s*$");

    private static final Pattern QUEST_CANCELLED_PATTERN =
            Pattern.compile("^\\s*Your Slayer Quest has been cancelled!\\s*$");

    /**
     * Hypixel's death announcement, e.g. {@code " ☠ You were killed by Inferno Demonlord."} once
     * colour codes are stripped.
     *
     * A second net for the player dying, because the first one - watching the client's own player go
     * to zero health - was seen to miss it: a boss killed the player and the mod still counted a
     * kill, announced a time and wrote it as a personal best. Hypixel does not put the player
     * through a normal death in Skyblock, so the health it syncs is not a reliable signal.
     *
     * Shape and samples taken from SkyHanni's {@code PlayerDeathManager}, whose pattern is
     * {@code " ☠ (?<name>\w+) (?<reason>.+)"} with captured tests including {@code " ☠ You fell into
     * the void."} and {@code " ☠ ZeroHazel was killed by Ashfang."}. That second sample is why the
     * name has to be checked: the same line announces other players' deaths, and one of those must
     * not void our quest.
     */
    private static final Pattern PLAYER_DEATH_PATTERN =
            Pattern.compile("^\\s*☠\\s+(?<name>\\w+)\\s+.+$");

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

    private static long lastKillCountedAtMs = 0L;
    private static int tickCounter = 0;
    private static final java.util.List<PendingDrop> PENDING_DROPS = new java.util.ArrayList<>();

    /** A drop waiting for its boss kill to be counted. */
    private record PendingDrop(String item, Component message, SlayerType type, int dueTick) {}

    /**
     * How long a queued drop line stays eligible to be hidden from the chat log.
     *
     * The display call follows the receive event within the same tick, so this only has to cover
     * that hop. Expiring at all matters because a message another mod swallows never reaches the
     * chat log, and a stale entry would otherwise hide the next identical drop instead.
     */
    private static final long HIDE_WINDOW_MS = 2000L;

    /** One drop line the tracker has taken over announcing, keyed by its colour-stripped text. */
    private record PendingHide(String text, long expiresAtMs) {}

    private static final java.util.List<PendingHide> PENDING_HIDES = new java.util.ArrayList<>();

    /**
     * Whether this chat line is a drop the tracker is about to report itself.
     *
     * Deliberately not a pattern test. Matching "RARE DROP!" outright would also swallow drops from
     * fishing, mining and dungeons, which the tracker never echoes - the line would simply vanish.
     * Instead only the exact lines queued in {@link #onChat} are hidden, so what disappears is
     * always replaced by the tracker's own message.
     *
     * Consumes the entry it matches, so two identical drops in a row each hide exactly once.
     *
     * Must be called from the chat GUI rather than from a receive event: cancelling a message
     * before the events have run would take the tracker's own input away with it.
     */
    public static boolean shouldHideDropMessage(Component component) {
        if (PENDING_HIDES.isEmpty()) return false;
        if (!AlpakaConfig.instance.slayerDropTrackerEnabled) return false;
        if (!AlpakaConfig.instance.hideHypixelDropMessage) return false;

        String text = cleanColor(component.getString());
        long now = System.currentTimeMillis();

        boolean hide = false;
        java.util.Iterator<PendingHide> iterator = PENDING_HIDES.iterator();
        while (iterator.hasNext()) {
            PendingHide pending = iterator.next();
            if (now > pending.expiresAtMs()) {
                iterator.remove();
            } else if (!hide && pending.text().equals(text)) {
                iterator.remove();
                hide = true;
            }
        }
        return hide;
    }

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
            SlayerSessionTracker.INSTANCE.tick();
            SlayerTimer.INSTANCE.tick();
            net.alpaka.addons.utils.HypixelMayor.INSTANCE.tick();
            SlayerMenuXpReader.INSTANCE.tick();

            // Primary kill signal: the sidebar leaving the boss fight. Chat is unreliable here.
            SlayerType killed = SlayerQuestDetector.INSTANCE.consumeKill();
            if (killed != null) {
                long killedAtMs = SlayerQuestDetector.INSTANCE.getKillDetectedAtMs();
                countKill(killed);
                SlayerSessionTracker.INSTANCE.onBossKilled(killed, killedAtMs);
                SlayerTimer.INSTANCE.onBossKilled(killed, killedAtMs);
            }

            // Only signal for a boss spawning: Hypixel never sends a chat announcement for a
            // regular slayer boss (unlike the Ender Dragon, Arachne, etc.), confirmed against
            // SkyHanni's own pattern list, which has no such entry for slayer bosses.
            SlayerType spawned = SlayerQuestDetector.INSTANCE.consumeSpawn();
            if (spawned != null) {
                SlayerSessionTracker.INSTANCE.onBossSpawned(spawned);
                SlayerTimer.INSTANCE.onBossSpawned(spawned);
            }
            if (spawned != null && AlpakaConfig.instance.customSoundsEnabled) {
                CustomSoundFeature.playBossSpawnSound();
            }

            flushPendingDrops();
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

    public static void onChat(Component message) {
        String string = cleanColor(message.getString());

        // Ahead of the drop tracker's own switch, because these say a quest ended *without* a kill
        // and the boss timer is a separate feature that would otherwise count a phantom one. The
        // tracker being off used to disable these nets along with everything else in this method.
        if (handleQuestVoided(string)) return;

        if (!AlpakaConfig.instance.slayerDropTrackerEnabled) return;

        if (AlpakaConfig.instance.customSoundsEnabled && SERVER_DROP_PATTERN.matcher(string).matches()) {
            String lower = string.toLowerCase();
            if (lower.contains("insane drop!") || lower.contains("crazy rare drop!") || lower.contains("judgement core") || lower.contains("warden heart") || lower.contains("archfiend dice")) {
                CustomSoundFeature.playInsaneDropSound();
            } else if (lower.contains("rare drop!") || lower.contains("very rare drop!")) {
                CustomSoundFeature.playRareDropSound();
            }
        }

        // The RNG meter line is still swallowed here so it does not fall through to the drop and
        // party handling below, but nothing reads its figure any more. Session XP used to be the
        // step between two readings; that step is the meter's gain, which an unlockable 10% boost
        // inflates, so it only ever matched players who had the boost. See xpForTier.
        if (RNG_METER_PATTERN.matcher(string).matches()) return;

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

            // Marked here rather than in the chat GUI: this is the point at which the tracker
            // commits to announcing this drop, and so the point at which Hypixel's own line becomes
            // a duplicate rather than the only report of it.
            if (AlpakaConfig.instance.hideHypixelDropMessage) {
                PENDING_HIDES.add(new PendingHide(string, System.currentTimeMillis() + HIDE_WINDOW_MS));
            }
            return;
        }

        // Check party command
        handlePartyCommand(string);
    }

    /**
     * Tells the detector a quest ended without the boss dying. True when this line was one of those.
     *
     * A cancelled or failed quest leaves the sidebar looking exactly like a completed one, so the
     * detector has to be told which of the two happened - otherwise ending a quest any other way is
     * counted as a boss killed, complete with an announced time and a personal best.
     *
     * All three of these arrive <em>after</em> the sidebar has already dropped the quest, which is
     * why {@link SlayerQuestDetector} holds an inferred kill back for a moment instead of acting on
     * it at once. Without that hold, none of these could ever arrive in time to veto anything.
     */
    private static boolean handleQuestVoided(String message) {
        if (QUEST_CANCELLED_PATTERN.matcher(message).matches()
                || QUEST_FAILED_PATTERN.matcher(message).matches()) {
            SlayerQuestDetector.INSTANCE.onQuestVoided();
            return true;
        }

        Matcher death = PLAYER_DEATH_PATTERN.matcher(message);
        if (death.matches() && namesLocalPlayer(death.group("name"))) {
            SlayerQuestDetector.INSTANCE.onQuestVoided();
            return true;
        }
        return false;
    }

    /**
     * Whether a name out of a death line refers to this client's own player.
     *
     * Hypixel writes "You" for the local player, but the very same line announces other players'
     * deaths by name - voiding our quest for one of those would throw away a real kill.
     */
    private static boolean namesLocalPlayer(String name) {
        if ("You".equals(name)) return true;
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && name.equals(mc.player.getGameProfile().name());
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

        AlpakaConfig.SlayerData data = AlpakaStats.slayerBossMap().get(type);
        if (data == null) {
            data = new AlpakaConfig.SlayerData();
            AlpakaStats.slayerBossMap().put(type, data);
        }
        data.kills++;
        AlpakaStats.save();
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

            AlpakaConfig.SlayerData data = AlpakaStats.slayerBossMap().get(pending.type());
            if (data == null) {
                data = new AlpakaConfig.SlayerData();
                AlpakaStats.slayerBossMap().put(pending.type(), data);
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
                feedback = Component.literal("Dropped ").append(dropComponent)
                        .append(Component.literal(" after "))
                        .append(Component.literal(String.valueOf(sinceLast)).withStyle(ChatFormatting.GREEN))
                        .append(Component.literal(" Boss" + (sinceLast != 1 ? "es" : "")));
            }
            sendModMessage(feedback);
        }

        if (changed) AlpakaStats.save();
    }

    public static void handlePartyCommand(String message) {
        if (!AlpakaConfig.instance.slayerDropTrackerEnabled) return;

        Matcher matcher = PARTY_PATTERN.matcher(message);
        if (matcher.matches()) {
            String queryDrop = matcher.group("item").trim();

            for (Map.Entry<SlayerType, AlpakaConfig.SlayerData> entry : AlpakaStats.slayerBossMap().entrySet()) {
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
            AlpakaConfig.SlayerData data = AlpakaStats.slayerBossMap().get(type);
            if (type == SlayerType.GUARDIAN && (data == null || data.kills == 0)) {
                continue;
            }
            int kills = data != null ? data.kills : 0;
            sendModMessage("§e" + type.display + ": §a" + kills + " §7kills");
        }

        sendModMessage("§6--- Personal Bests ---");
        boolean anyBest = false;
        for (SlayerType type : SlayerType.values()) {
            Long best = SlayerTimer.INSTANCE.personalBest(type);
            if (best == null) continue;
            anyBest = true;
            sendModMessage("§e" + type.display + ": §a" + SlayerTimer.INSTANCE.format(best));
        }
        if (!anyBest) {
            sendModMessage("§7  §8no boss timed yet");
        }

        // Only each slayer's headline RNG drop, the one worth counting a dry streak against - the
        // same line the slayer HUD shows. The full history is a lot of lines for something that is
        // usually read to answer "how dry am I", so it moved behind /alpakaslayer <slayer>.
        sendModMessage("§6--- RNG Drops ---");
        boolean anyDrop = false;
        for (SlayerType type : SlayerType.values()) {
            String item = type.rngDropItem;
            if (item == null) continue;

            AlpakaConfig.SlayerData data = AlpakaStats.slayerBossMap().get(type);
            if (data == null || data.kills == 0) continue;
            anyDrop = true;

            Integer since = SlayerRngDropTracker.INSTANCE.bossesSince(type);
            if (since == null) {
                sendModMessage("§e" + type.display + "§7: " + item + " §8- §cnever §7in " + data.kills + " bosses");
            } else {
                sendModMessage("§e" + type.display + "§7: " + item + " §8- §a" + since + " §7boss"
                        + (since != 1 ? "es" : "") + " ago");
            }
        }
        if (!anyDrop) {
            sendModMessage("§7  §8nothing recorded yet");
        }
        sendModMessage("§8Use /alpakaslayer <slayer> for every drop of one slayer.");
    }

    /**
     * Every drop recorded for one slayer, most recent first.
     *
     * Listed from what has actually been recorded rather than from a fixed list of names: the old
     * hard-coded list only matched on exact spelling, so a tracked "Archfiend Dice" was reported as
     * "never" simply because the list asked for "High class archfiend dice".
     */
    public static void printDropsFor(LocalPlayer player, SlayerType type) {
        if (player == null) return;

        AlpakaConfig.SlayerData data = AlpakaStats.slayerBossMap().get(type);
        sendModMessage("§6--- " + type.display + " Drops ---");

        int kills = data != null ? data.kills : 0;
        sendModMessage("§7" + kills + " §7boss" + (kills != 1 ? "es" : "") + " killed");

        Long best = SlayerTimer.INSTANCE.personalBest(type);
        if (best != null) {
            sendModMessage("§7Personal best: §a" + SlayerTimer.INSTANCE.format(best));
        }

        if (data == null || data.drops == null || data.drops.isEmpty()) {
            sendModMessage("§7  §8nothing recorded yet");
            return;
        }

        data.drops.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByValue(java.util.Comparator.reverseOrder()))
                .forEach(entry -> {
                    int sinceLast = data.kills - entry.getValue();
                    sendModMessage("§7  " + entry.getKey() + " §8- §a" + sinceLast + " §7boss"
                            + (sinceLast != 1 ? "es" : "") + " ago");
                });
    }
}
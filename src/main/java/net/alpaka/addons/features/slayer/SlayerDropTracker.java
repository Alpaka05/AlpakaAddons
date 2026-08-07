package net.alpaka.addons.features.slayer;

import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.sound.CustomSoundFeature;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
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

    private static final Pattern BOSS_TYPE_PATTERN = Pattern.compile("^ +(Wolf|Zombie|Blaze|Vampire|Spider|Enderman|Guardian) Slayer LVL \\d.*");
    private static final Pattern DROP_PATTERN = Pattern.compile("^(?:VERY RARE|RARE|INSANE|CRAZY RARE) DROP! \\((?:(?<amount>\\d+x) )?(?<item>[^)]+)\\)(?: .+)?");
    private static final Pattern PARTY_PATTERN = Pattern.compile("^Party > (?:\\[[A-Z+]+] )?\\w+: !since (?<item>.+)$");
    private static final Pattern COLOR_PATTERN = Pattern.compile("(?i)§[0-9A-FK-OR]");

    public static SlayerType currentBoss = null;
    public static boolean hasWorldChanged = false;
    public static final ThreadLocal<Boolean> IS_PROCESSING = ThreadLocal.withInitial(() -> false);

    public static void registerEvents() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            if (!isOnSkyblock()) return;
            onChat(message);
        });

        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            if (!isOnSkyblock()) return;
            onChat(message);
        });

        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay) return true;

            if (AlpakaConfig.instance.cleanBlazeEnabled) {
                String clean = cleanColor(message.getString()).trim();
                if ("Your radio is weak. Find another enjoyer to boost it.".equals(clean) ||
                    "Your radio signal is strong!".equals(clean) ||
                    "Your radio lost signal. There's too many enjoyers on this channel.".equals(clean)) {
                    return false;
                }
            }

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

        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity == Minecraft.getInstance().player) {
                hasWorldChanged = true;
            }
        });
    }

    public static boolean isOnSkyblock() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;

        var serverData = mc.getCurrentServer();
        if (serverData != null && serverData.ip != null) {
            String ip = serverData.ip.toLowerCase();
            if (!ip.contains("hypixel.net") && !ip.contains("localhost")) {
                return false;
            }
        }

        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective != null) {
            String displayName = cleanColor(objective.getDisplayName().getString()).toLowerCase();
            return displayName.contains("skyblock") || displayName.contains("rift");
        }
        return false;
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

        if (AlpakaConfig.instance.customSoundsEnabled) {
            String lower = string.toLowerCase();
            if (lower.contains("insane drop!") || lower.contains("crazy rare drop!") || lower.contains("judgement core") || lower.contains("warden heart") || lower.contains("archfiend dice")) {
                CustomSoundFeature.playInsaneDropSound();
            } else if (lower.contains("rare drop!") || lower.contains("very rare drop!")) {
                CustomSoundFeature.playRareDropSound();
            }
        }

        // Check boss defeat message
        Matcher bossMatcher = BOSS_TYPE_PATTERN.matcher(string);
        if (bossMatcher.matches()) {
            String slayerDisplay = bossMatcher.group(1);
            SlayerType detectedType = null;
            for (SlayerType type : SlayerType.values()) {
                if (type.display.equals(slayerDisplay)) {
                    detectedType = type;
                    break;
                }
            }
            if (detectedType != null) {
                currentBoss = detectedType;
                AlpakaConfig.SlayerData data = AlpakaConfig.instance.slayerBossMap.get(currentBoss);
                if (data != null) {
                    data.kills++;
                    AlpakaConfig.save();
                }
                hasWorldChanged = false;
            }
            return;
        }

        // Check boss spawn message
        String lowerMsg = string.toLowerCase();
        if (lowerMsg.contains("slayer boss spawned") || lowerMsg.contains("boss spawned!") || lowerMsg.contains("slayer spawned!")) {
            CustomSoundFeature.playBossSpawnSound();
        }

        // Check drop message
        Matcher dropMatcher = DROP_PATTERN.matcher(string);
        if (dropMatcher.matches()) {
            final String matchedString = string;
            final Component matchedMessage = message;

            new Thread(() -> {
                try {
                    Thread.sleep(1500); // Wait 1.5 seconds (30 ticks)
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                Minecraft.getInstance().execute(() -> {
                    if (!AlpakaConfig.instance.slayerDropTrackerEnabled) return;

                    SlayerType activeBoss = currentBoss;
                    if (isInRift()) {
                        activeBoss = SlayerType.VAMPIRE;
                    }

                    if (activeBoss == null) {
                        AlpakaAddons.LOGGER.info("Dropped {} but no active boss tracked", matchedString);
                        return;
                    }

                    Matcher m = DROP_PATTERN.matcher(matchedString);
                    if (!m.matches()) return;

                    String drop = m.group("item");
                    AlpakaConfig.SlayerData data = AlpakaConfig.instance.slayerBossMap.get(activeBoss);
                    if (data == null) return;

                    if (hasWorldChanged && !data.drops.containsKey(drop)) {
                        AlpakaAddons.LOGGER.info("Dropped {} but world swap and boss hasn't dropped this before", matchedString);
                        return;
                    }

                    int currentKills = data.kills;
                    int lastDropped = data.drops.getOrDefault(drop, 0);
                    data.drops.put(drop, currentKills);

                    Component dropComponent = getDropComponent(matchedMessage, drop);
                    int sinceLast = currentKills - lastDropped;

                    MutableComponent feedback = Component.literal("Took " + sinceLast + " boss" + (sinceLast != 1 ? "es" : "") + " to drop ")
                            .append(dropComponent);

                    sendModMessage(feedback);

                    AlpakaConfig.save();
                });
            }).start();
            return;
        }

        // Check party command
        handlePartyCommand(string);
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
    }
}

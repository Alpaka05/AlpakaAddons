package net.alpaka.addons.features.cosmetics

import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import net.alpaka.addons.client.gui.AlpakaGuiElementSink
import net.alpaka.addons.client.gui.GlowRenderState
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.cosmetics.RareDrops.Kind
import net.alpaka.addons.features.itemvalue.ItemPrices
import net.alpaka.addons.features.market.ItemIcons
import net.alpaka.addons.features.market.ItemNames
import net.alpaka.addons.features.slayer.SlayerDropTracker
import net.alpaka.addons.features.slayer.SlayerQuestDetector
import net.alpaka.addons.features.slayer.SlayerType
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.LevelLoadingScreen
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.ARGB
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile
import org.joml.Matrix3x2f
import java.util.Locale
import java.util.Optional
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A rare drop flies onto the screen: the item alone, no text, big and in 3D. It shoots up from below
 * the screen's edge, tumbling end over end, lands in the middle with a flash and a shock wave,
 * whirls round and settles facing the player, then turns on show in its light until it spins up
 * and vanishes in a sparkle. The light is the drop's own ([Effect]): a vortex in the slayer's color
 * for a slayer drop, a rainbow across the screen for a dye, a beam for a dungeon reward, a flower
 * and fireflies for a pet, a constellation for the rest; Special Effects off leaves the item alone. Bound to the screen, not the
 * world, so it stays where it is while the player turns, and it is drawn over an open menu too.
 *
 * Which drops count is read off Hypixel's chat lines ([RareDrops]): a slayer's rarest drop, a dye
 * the player finds, a dungeon chest's rare reward, and any other drop that is crazy rare or better,
 * a pet, or worth at least the set minimum. Client-side only: nothing is sent and nobody else sees
 * it. The item is drawn by [RareDropItemRenderer]; the light from soft shapes ([GlowRenderState]).
 *
 * The item's look comes by its id in Hypixel's and SkyCofl's item lists ([ItemIcons], [ItemNames]),
 * the price for the Minimum Value from the Bazaar or the lowest BIN ([ItemPrices]); both only with
 * Network Features on. A slayer's headline drop looks right without them, from textures that ship
 * with the mod.
 */
object RareDropAnimation {

    /** How long the item flies, from below the screen to resting in its place, in seconds. */
    private const val FLY_SECONDS = 0.9f

    /**
     * How far the flight overshoots its place before it settles back: the back-easing's constant.
     * It first reaches its place at [FLY_SECONDS] / (1 + this), the moment it lands.
     */
    private const val OVERSHOOT = 1.6f

    /** When the item first reaches its place, with the flash, the shock wave and the chime, in seconds. */
    private const val LAND = FLY_SECONDS / (1f + OVERSHOOT)

    /** How long the end takes, the item spinning up and shrinking into a sparkle, in seconds. */
    private const val FADE_SECONDS = 0.9f

    /** How often the item makes a full turn on show, in seconds, and how long that turn takes. */
    private const val TURN_EVERY = 3.2f
    private const val TURN_SECONDS = 1.1f

    /**
     * Where SkyHanni's titles stand by default, in GUI pixels from the top: the big line at three
     * times the text size from 160 down, its subtitle under it, with a few pixels to spare.
     */
    private const val TITLE_TOP = 154f
    private const val TITLE_BOTTOM = 214f

    /** How much of the screen's bottom the hotbar, hearts and armor take, in GUI pixels. */
    private const val HOTBAR_PX = 52f

    /** How big the item is at Size 1, in GUI pixels. */
    private const val ITEM_PX = 56f

    /** Drops that come while one plays wait their turn; more than this many are let go. */
    private const val QUEUE_MAX = 3

    /** The same item named again this soon is the same drop told twice (a dye from a chest). */
    private const val REPEAT_MS = 10_000L

    /**
     * How long the item's look is looked for while the lists load before it flies in anyway, in
     * seconds: it only sets off once it has something to show.
     */
    private const val RESOLVE_SECONDS = 3f

    // Gold, from the white-hot center out to the deep amber at the edges of the light.
    private const val GOLD_PALE = 0xFFF4C0
    private const val GOLD = 0xFFD447
    private const val GOLD_DEEP = 0xFFA51F
    private const val AMBER = 0xFF7A12

    /**
     * The chime as the item lands: a rising major arpeggio on bells, the top note doubled by a
     * chime and the first by an amethyst shimmer. Each note is (seconds after the landing, pitch).
     */
    private val NOTES = floatArrayOf(
        0.00f, 1.0f,
        0.07f, 1.2599f,
        0.14f, 1.4983f,
        0.21f, 2.0f,
    )

    /** SkyBlock's rarities by the color of their names, in SkyCofl's order for pet ids. */
    private val RARITY_COLORS = intArrayOf(0xFFFFFF, 0x55FF55, 0x5555FF, 0xAA00AA, 0xFFAA00, 0xFF55FF)

    /** Which light a drop gets, by what it is: a dye does not look like a slayer drop. */
    private enum class Effect {
        /** A slayer's headline drop: a vortex of sparks in gold and the slayer's color, a beating heart. */
        GOLDEN,
        /** A dye: a rainbow drawing itself across the screen, glittering; confetti raining down. */
        PRISM,
        /** A dungeon reward: a beam of light, runes on two orbits, ripples. */
        ARCANE,
        /** A pet: a flower of light opening, fireflies wandering round. */
        BLOOM,
        /** Any other valuable drop: a constellation drawing itself, shooting stars. */
        STARBURST,
    }

    private class Drop(val kind: Kind, val name: String, val nameColor: Int, val accent: Int, val effect: Effect) {
        /** When the drop's turn came; its item is looked for from then on. */
        var queuedNanos = 0L

        /** When the item set off, once its look was found; 0 while it is still looked for. */
        var startNanos = 0L
        var notesPlayed = 0

        /** Which side the item comes in from, -1 for the left and 1 for the right, and which way it turns. */
        val side = if (Random.nextBoolean()) 1f else -1f

        var id: String? = null
        var stack: ItemStack = ItemStack.EMPTY
        val itemState = ItemStackRenderState()

        /** Whether [stack] only stands in for a look still on its way, to be swapped when it comes. */
        var standIn = false

        /** Whether the item and its id are found, so nothing more is looked up. */
        var resolved = false
    }

    private var current: Drop? = null
    private val queue = ArrayDeque<Drop>()
    private val recent = HashMap<String, Long>()
    private var lastLevel: ClientLevel? = null
    private var testIndex = 0

    private val stacks = HashMap<String, ItemStack>()

    @JvmStatic
    fun register() {
        PictureInPictureRendererRegistry.register { RareDropItemRenderer() }
        ClientReceiveMessageEvents.GAME.register { message, overlay -> if (!overlay) onChat(message) }
        // Lines another mod's chat filter hides still count: they only never reach the chat.
        ClientReceiveMessageEvents.GAME_CANCELED.register { message, overlay -> if (!overlay) onChat(message) }
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            ScreenEvents.afterExtract(screen).register { _, graphics, _, _, _ -> renderOverScreen(graphics) }
        }
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val level = client.level
            if (level == null) {
                current = null
                queue.clear()
            } else if (level !== lastLevel && AlpakaConfig.instance.rareDropAnimationEnabled) {
                // Each world change keeps the item lists and prices at hand, so a drop's value is
                // known the moment it comes; the prices only ask again once a few minutes old, the
                // lists not at all once in.
                ItemIcons.ready()
                ItemNames.refresh()
                ItemPrices.refresh()
            }
            lastLevel = level
        }
    }

    private fun onChat(message: Component) {
        val cfg = AlpakaConfig.instance
        if (!cfg.rareDropAnimationEnabled) return
        val mc = Minecraft.getInstance()
        if (mc.level == null) return
        val line = SlayerDropTracker.cleanColor(message.string)
        val found = RareDrops.parse(line, mc.user.name) ?: return
        ItemNames.refresh()
        ItemPrices.refresh()
        val color = colorAt(message, found.nameStart)

        var boss = SlayerQuestDetector.currentOrRecent()
        if (boss != null && SlayerDropTracker.isInRift()) boss = SlayerType.VAMPIRE
        val headline = found.kind == Kind.DROP && boss?.rngDropItem?.equals(found.name, ignoreCase = true) == true

        val wanted = when {
            headline -> cfg.rareDropAnimationSlayer
            found.kind == Kind.DYE -> cfg.rareDropAnimationDyes
            found.kind == Kind.DUNGEON -> cfg.rareDropAnimationDungeon
            !cfg.rareDropAnimationValuable -> false
            RareDrops.alwaysRare(found) -> true
            else -> {
                val price = ItemPrices.quote(idFor(found.kind, found.name, color))?.price() ?: return
                price >= cfg.rareDropAnimationMinValue * 1_000_000.0
            }
        }
        if (!wanted) return
        show(found.kind, found.name, color, if (headline) boss else null)
    }

    private fun show(kind: Kind, name: String, color: Int?, slayer: SlayerType?) {
        val now = System.currentTimeMillis()
        recent.entries.removeIf { now - it.value > REPEAT_MS }
        val key = name.lowercase(Locale.ROOT)
        if (key in recent) return
        recent[key] = now

        val accent = if (slayer != null) accentOf(slayer) else color ?: AMBER
        val effect = when {
            slayer != null -> Effect.GOLDEN
            kind == Kind.DYE -> Effect.PRISM
            kind == Kind.DUNGEON -> Effect.ARCANE
            kind == Kind.PET -> Effect.BLOOM
            else -> Effect.STARBURST
        }
        val drop = Drop(kind, name, color ?: GOLD, accent, effect)
        if (current == null) start(drop) else if (queue.size < QUEUE_MAX) queue.addLast(drop)
    }

    private fun start(drop: Drop) {
        drop.queuedNanos = System.nanoTime()
        current = drop
    }

    /**
     * The config's test button: plays the animation over the menu with a sample drop, a different
     * one each time. Works whether the feature is on or not; needs a world for the item.
     */
    @JvmStatic
    fun test() {
        val mc = Minecraft.getInstance()
        if (mc.level == null || mc.player == null) {
            net.alpaka.addons.features.notification.AlpakaNotifications.send("Rare Drop Animation",
                "Join a world to test the animation.")
            return
        }
        ItemIcons.ready()
        ItemNames.refresh()
        ItemPrices.refresh()
        val sample = SAMPLES[testIndex % SAMPLES.size]
        testIndex++
        recent.remove(sample.name.lowercase(Locale.ROOT))
        current = null
        queue.clear()
        show(sample.kind, sample.name, sample.color, sample.slayer)
    }

    private class Sample(val kind: Kind, val name: String, val color: Int, val slayer: SlayerType?)

    private val SAMPLES = listOf(
        Sample(Kind.DROP, "Judgement Core", 0xFFAA00, SlayerType.ENDERMAN),
        Sample(Kind.DYE, "Necron Dye", 0xFFAA00, null),
        Sample(Kind.DUNGEON, "Necron's Handle", 0xAA00AA, null),
        Sample(Kind.PET, "Ender Dragon", 0xFFAA00, null),
        Sample(Kind.DROP, "Smoldering VI", 0x5555FF, null),
        Sample(Kind.DROP, "Pestilence Rune II", 0x5555FF, null),
        Sample(Kind.DROP, "High Class Archfiend Dice", 0xFFAA00, SlayerType.BLAZE),
    )

    /**
     * Each slayer's own color, mixed into the gold of its headline drop: the outer halo, every other
     * ray, the shock wave and a third of the sparkles.
     */
    private fun accentOf(type: SlayerType): Int = when (type) {
        SlayerType.ZOMBIE -> 0x5CE65C
        SlayerType.SPIDER -> 0xE8423A
        SlayerType.WOLF -> 0xCFE3FF
        SlayerType.ENDERMAN -> 0xB25CFF
        SlayerType.BLAZE -> 0xFF5A1A
        SlayerType.VAMPIRE -> 0xE0244E
        else -> AMBER
    }

    /**
     * The color of the character at [index] of the line's plain text: the color Hypixel gave the
     * item's name, which is its rarity's. From the line's styles and any color codes inside its text.
     */
    private fun colorAt(message: Component, index: Int): Int? {
        var seen = 0
        val found = message.visit(FormattedText.StyledContentConsumer<Int> { style, text ->
            var color = style.color?.value ?: -1
            var i = 0
            while (i < text.length) {
                if (text[i] == '§' && i + 1 < text.length) {
                    val format = ChatFormatting.getByCode(text[i + 1])
                    if (format == ChatFormatting.RESET) color = style.color?.value ?: -1
                    else if (format != null) TextColor.fromLegacyFormat(format)?.let { color = it.value }
                    i += 2
                    continue
                }
                if (seen == index) return@StyledContentConsumer Optional.of(color)
                seen++
                i++
            }
            Optional.empty()
        }, Style.EMPTY)
        return found.orElse(-1).takeIf { it >= 0 }
    }

    /** The item's market id, for its price and look; null while Hypixel's list loads or for one it lacks. */
    private fun idFor(kind: Kind, name: String, color: Int?): String? {
        if (kind == Kind.PET) {
            val rarity = RARITY_COLORS.indexOf(color ?: return null)
            return if (rarity < 0) null else RareDrops.petId(name, rarity, nameIndex())
        }
        // The name's color is its rarity, which tells apart items of one name (the pet items).
        val rarity = if (color == null) -1 else RARITY_COLORS.indexOf(color)
        return RareDrops.bookId(name) { ItemPrices.quote(it) != null } ?: nameIndex()?.id(name, rarity)
            ?: RareDrops.runeId(name) ?: RareDrops.dyeId(name)
    }

    private var index: RareDrops.NameIndex? = null
    /** Which lists [index] was built from: 1 for Hypixel's, 2 for SkyCofl's. */
    private var indexSources = 0

    /** Item ids by name, built again as each of the two item lists comes in; null before either. */
    private fun nameIndex(): RareDrops.NameIndex? {
        val hypixel = ItemIcons.skyblockItems()
        val others = ItemNames.all()
        val sources = (if (hypixel != null) 1 else 0) or (if (others != null) 2 else 0)
        if (sources == 0) return null
        if (sources != indexSources) {
            index = RareDrops.NameIndex(hypixel ?: emptyMap(), others)
            indexSources = sources
        }
        return index
    }

    /**
     * Looks up what is still missing of the drop's id and look, for the first seconds while the lists
     * may still be on their way, then settles on what it has. [wait] is how long it has been looked for.
     */
    private fun resolve(drop: Drop, wait: Float) {
        if (drop.resolved) return
        // A slayer's headline drop ships with the mod: it needs no list and flies in at once.
        bundled(drop.name)?.let {
            drop.stack = it
            drop.resolved = true
            return
        }
        val listsIn = ItemIcons.skyblockItems() != null && (ItemNames.all() != null || ItemNames.failed()) &&
            !ItemPrices.loading()
        // Until the item lists and prices are in, the id is only a guess from the name ("Pestilence
        // Rune II" is RUNE_ZOMBIE_SLAYER, not RUNE_PESTILENCE): asked again each frame, and the look
        // found for an earlier guess dropped.
        if (drop.id == null || !listsIn) {
            val id = idFor(drop.kind, drop.name, drop.nameColor)
            if (id != drop.id) {
                drop.id = id
                drop.stack = ItemStack.EMPTY
                drop.standIn = false
            }
        }
        if (drop.stack.isEmpty || drop.standIn) {
            val found = ItemIcons.forItem(drop.id, drop.name)
            if (!found.isEmpty) {
                drop.stack = found
                drop.standIn = false
            }
        }
        val done = listsIn && drop.id != null && !drop.stack.isEmpty && !drop.standIn
        // A name no list knows will not turn up later: show the stand-in now rather than wait.
        val unknown = drop.id == null && drop.stack.isEmpty && listsIn
        // Without Network Features nothing more will come: settle on the first look.
        if (done || unknown || !AlpakaConfig.instance.allowApiCalls) {
            if (drop.stack.isEmpty) drop.stack = ItemStack(Items.NETHER_STAR)
            drop.resolved = true
        } else if (wait > RESOLVE_SECONDS && drop.stack.isEmpty) {
            // Slow lists: something flies in meanwhile, and the right look takes over once it comes.
            drop.stack = ItemStack(Items.NETHER_STAR)
            drop.standIn = true
        }
    }

    /**
     * A slayer's headline drop from the mod's own files, so it needs no network: the heads wear their
     * SkyBlock skins, copied from the NotEnoughUpdates repository into `textures/rare_drop/`, and the
     * Overflux Capacitor is an enchanted quartz as on SkyBlock. Null for any other item.
     */
    private fun bundled(name: String): ItemStack? {
        val id = name.lowercase(Locale.ROOT).replace(' ', '_')
        if (id != "overflux_capacitor" && id !in HEAD_SKINS) return null
        return stacks.getOrPut(id) {
            if (id == "overflux_capacitor") ItemStack(Items.QUARTZ).also {
                it.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
            } else head(id) ?: ItemStack(Items.NETHER_STAR)
        }
    }

    /** The headline drops that are player heads, by their skin's file name. */
    private val HEAD_SKINS = setOf(
        "warden_heart", "primordial_eye", "judgement_core", "high_class_archfiend_dice", "unfanged_vampire_part",
    )

    /**
     * A player head whose skin is a texture of the mod's own. The profile names no player, only the
     * texture, so nothing is looked up: vanilla's `profile={texture:...}`.
     */
    private fun head(id: String): ItemStack? {
        val json = JsonObject()
        json.addProperty("texture", "alpaka:rare_drop/$id")
        val profile = ResolvableProfile.CODEC.parse(JsonOps.INSTANCE, json).result().orElse(null) ?: return null
        return ItemStack(Items.PLAYER_HEAD).also { it.set(DataComponents.PROFILE, profile) }
    }

    /**
     * Plays the notes of the chime that are due. Driven by the frame rather than the tick, whose 50 ms
     * steps would smear the 70 ms arpeggio. Interface sounds, so only this player hears them.
     */
    private fun playDueNotes(drop: Drop, t: Float) {
        val cfg = AlpakaConfig.instance
        val sounds = Minecraft.getInstance().soundManager
        while (drop.notesPlayed * 2 < NOTES.size && LAND + NOTES[drop.notesPlayed * 2] <= t) {
            val index = drop.notesPlayed++
            if (!cfg.rareDropAnimationSound) continue
            val pitch = NOTES[index * 2 + 1]
            sounds.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), pitch, 0.7f))
            if (index == 0) sounds.play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.0f))
            if (index * 2 + 2 == NOTES.size) sounds.play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), pitch, 0.6f))
        }
    }

    /** Called at the end of the HUD. With a menu open it is drawn over the menu instead. */
    @JvmStatic
    fun renderHud(graphics: GuiGraphicsExtractor) {
        if (current == null) return
        val mc = Minecraft.getInstance()
        if (mc.gui.screen() != null || mc.gui.hud.isHidden()) return
        draw(graphics)
    }

    private fun renderOverScreen(graphics: GuiGraphicsExtractor) {
        if (current == null) return
        if (Minecraft.getInstance().gui.screen() is LevelLoadingScreen) return
        graphics.nextStratum()
        draw(graphics)
    }

    private fun draw(graphics: GuiGraphicsExtractor) {
        val drop = current ?: return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val cfg = AlpakaConfig.instance
        val now = System.nanoTime()
        if (drop.startNanos == 0L) {
            // The item only sets off once there is something to show: at once when the lists are in,
            // a few seconds at most while they load.
            val wait = ((now - drop.queuedNanos) / 1e9).toFloat()
            resolve(drop, wait)
            if (drop.stack.isEmpty || (!drop.resolved && wait <= RESOLVE_SECONDS)) return
            drop.startNanos = now
        }
        val duration = cfg.rareDropAnimationDuration.coerceIn(2f, 15f)
        val t = ((now - drop.startNanos) / 1e9).toFloat()
        if (t >= duration) {
            current = queue.removeFirstOrNull()?.also { start(it) }
            return
        }
        resolve(drop, RESOLVE_SECONDS + t)
        playDueNotes(drop, t)

        val size = cfg.rareDropAnimationSize.coerceIn(0.5f, 2.5f)
        val r = ITEM_PX * size
        val width = graphics.guiWidth().toFloat()
        val height = graphics.guiHeight().toFloat()
        // Where it rests, and where it sets off: below the screen's bottom edge, off to one side.
        val cx = width / 2f
        val cy = restY(height, r)
        val fromX = cx + drop.side * minOf(0.3f * width, 3.5f * r)
        val fromY = height + 1.3f * r

        val p = (t / FLY_SECONDS).coerceIn(0f, 1f)
        val sinceLand = t - LAND
        // How far the end has got: 0 until the last FADE_SECONDS, then up to 1.
        val end = ((t - (duration - FADE_SECONDS)) / FADE_SECONDS).coerceIn(0f, 1f)
        // How much it has calmed down after landing, for the slow sway and bob of the show.
        val calm = smooth((sinceLand / 1.2f).coerceIn(0f, 1f))

        val ix = flightX(t, fromX, cx)
        val iy = flightY(t, fromY, cy) - 0.6f * r * end * end
        val bob = 0.05f * r * sin((t - FLY_SECONDS) * 2.4f) * smooth(((t - FLY_SECONDS) / 0.8f).coerceIn(0f, 1f))

        // Small far off and growing as it comes near, a jolt when it lands, a last swell before it
        // shrinks away to nothing.
        val near = 0.45f + 0.55f * easeOutCubic(p)
        val jolt = if (sinceLand > 0f) 0.22f * (sinceLand * 10f) * exp(1f - sinceLand * 10f) else 0f
        val away = (1f - easeInBack(end)).coerceAtLeast(0f)
        val grow = near * (1f + jolt) * away

        // Two flips end over end on the way up, banking into the curve; two whirls round that
        // slow down to face the player; on show a full turn every few seconds over a gentle sway;
        // at the end it spins up faster and faster.
        val tumble = -4f * PI.toFloat() * (1f - easeOutCubic((t / (FLY_SECONDS * 1.15f)).coerceIn(0f, 1f)))
        val whirl = 4f * PI.toFloat() * (1f - exp(-t * 2.2f))
        val onShow = t - 2.4f
        val turns = if (onShow <= 0f) 0f else {
            val n = kotlin.math.floor(onShow / TURN_EVERY)
            n + easeInOutCubic(((onShow - n * TURN_EVERY) / TURN_SECONDS).coerceIn(0f, 1f))
        }
        val spin = drop.side * (whirl + 2f * PI.toFloat() * turns + 14f * end * end * end) + 0.3f * sin(t * 1.2f) * calm
        val tilt = -0.18f + tumble + 0.1f * sin(t * 1.4f) * calm
        val roll = -drop.side * 0.6f * (1f - easeOutCubic(p)) + 0.07f * sin(t * 0.9f + 1f) * calm

        val landed = smooth((sinceLand / 0.25f).coerceIn(0f, 1f))
        val fade = smooth(((duration - t) / FADE_SECONDS).coerceIn(0f, 1f))

        val sink = graphics as AlpakaGuiElementSink
        val pose = Matrix3x2f(graphics.pose())
        if (cfg.rareDropAnimationEffects) {
            drawFlight(sink, pose, drop, t, r, fromX, fromY, cx, cy, landed)
            if (drop.effect == Effect.PRISM) drawRainbow(sink, pose, width, height, r, sinceLand, landed, end)
            drawLight(sink, pose, drop, ix, iy, r, sinceLand, landed * fade)
            drawVanish(sink, pose, drop, ix, iy, r, end, sinceLand)
        }

        // The item over its light: a later stratum is drawn after the light's.
        graphics.nextStratum()
        // An item frame's view of the item (FIXED) is half a block across: twice r makes it r wide.
        val itemScale = 2f * r * grow
        if (itemScale > 1f && !drop.stack.isEmpty) {
            mc.itemModelResolver.updateForTopItem(drop.itemState, drop.stack, ItemDisplayContext.FIXED, level, null, 0)
            // A fixed box, a little bigger than the item at its biggest and turned any way, so the
            // picture's texture is made once rather than again every frame. The box sits on whole
            // GUI pixels; the rest of the way is moved inside the picture, so the flight glides.
            val half = (r * 1.25f).toInt()
            val x = ix.toInt()
            val y = iy.toInt()
            sink.`alpaka$submitPicture`(RareDropItemRenderer.State(drop.itemState, spin, tilt, roll,
                ix - x, iy - y + bob, x - half, y - half, x + half, y + half, itemScale))
        }
    }

    /**
     * How far down the item rests, in GUI pixels, for an item [r] across on a screen [height] high:
     * clear of the title SkyHanni shows for a rare drop, which stands at a fixed height from the
     * top. Under it where there is room above the hotbar, else over it.
     */
    private fun restY(height: Float, r: Float): Float {
        val reach = 0.62f * r
        val below = TITLE_BOTTOM + reach
        if (below <= height - HOTBAR_PX - reach) return below
        return minOf(TITLE_TOP - reach, height * 0.36f).coerceAtLeast(reach + 4f)
    }

    /** Where the item is across the screen [t] seconds after it set off: it swings in from its side early. */
    private fun flightX(t: Float, fromX: Float, cx: Float): Float =
        fromX + (cx - fromX) * easeOutQuint((t / FLY_SECONDS).coerceIn(0f, 1f))

    /** Where the item is up the screen [t] seconds after it set off: it shoots up, past its place and back. */
    private fun flightY(t: Float, fromY: Float, cy: Float): Float =
        fromY + (cy - fromY) * easeOutBack((t / FLY_SECONDS).coerceIn(0f, 1f), OVERSHOOT)

    /**
     * The color of the [i]th piece of light of a drop's effect, [t] seconds after it landed: gold and
     * the slayer's color for a slayer drop, the whole rainbow running round for a dye, and the drop's
     * rarity color with paler light for the rest.
     */
    private fun tint(drop: Drop, i: Int, t: Float): Int = when (drop.effect) {
        Effect.GOLDEN -> when (i % 3) { 0 -> GOLD_PALE; 1 -> GOLD; else -> drop.accent }
        Effect.PRISM -> hue(fract(i * 0.137f + t * 0.25f))
        Effect.ARCANE -> if (i % 2 == 0) AuraEffects.mix(drop.accent, 0xFFFFFF, 0.45f) else drop.accent
        Effect.BLOOM -> when (i % 3) { 0 -> AuraEffects.mix(drop.accent, 0xFFFFFF, 0.55f); 1 -> drop.accent; else -> 0xFFD6E8 }
        Effect.STARBURST -> if (i % 2 == 0) 0xFFFFFF else AuraEffects.mix(drop.accent, 0xFFFFFF, 0.2f)
    }

    /** A bright, not quite full color at [h] round the color wheel, from 0 to 1. */
    private fun hue(h: Float): Int {
        val k = h * 6f
        fun channel(n: Float): Int {
            val x = fract((n + k) / 6f) * 6f
            val v = 1f - 0.85f * maxOf(0f, minOf(x, 4f - x, 1f))
            return (v * 255f).toInt().coerceIn(0, 255)
        }
        return (channel(5f) shl 16) or (channel(3f) shl 8) or channel(1f)
    }

    /**
     * The flight's light: a glow around the item on its way up and a trail of light behind it along
     * the path it came, both going out as it lands ([landed] from 0 to 1).
     */
    private fun drawFlight(sink: AlpakaGuiElementSink, pose: Matrix3x2f, drop: Drop, t: Float, r: Float,
                           fromX: Float, fromY: Float, cx: Float, cy: Float, landed: Float) {
        val left = 1f - landed
        if (left <= 0f) return
        val x = flightX(t, fromX, cx)
        val y = flightY(t, fromY, cy)
        sink.`alpaka$submitElement`(GlowRenderState.disc(pose, x, y, 0.9f * r, GlowRenderState.GLOW, 0, argb(tint(drop, 1, t), 0.7f * left)))
        val trail = 14
        for (k in 1..trail) {
            val tk = t - k * 0.022f
            if (tk < 0f) break
            val fall = 1f - k / (trail + 1f)
            val px = flightX(tk, fromX, cx)
            val py = flightY(tk, fromY, cy)
            val near = 0.45f + 0.55f * easeOutCubic((tk / FLY_SECONDS).coerceIn(0f, 1f))
            sink.`alpaka$submitElement`(GlowRenderState.disc(pose, px, py, (0.5f * fall + 0.1f) * r * near,
                GlowRenderState.GLOW, 0, argb(tint(drop, k, t), 0.75f * fall * left)))
            // Sparks shed along the trail, drifting off it a little.
            val seed = AuraEffects.hash(k * 29 + 5)
            val drift = (seed - 0.5f) * 0.8f * r * (k / trail.toFloat())
            sink.`alpaka$submitElement`(GlowRenderState.disc(pose, px + drift, py + 0.2f * r * fall, (0.08f + 0.08f * seed) * r,
                GlowRenderState.SPARKLE, 0, argb(tint(drop, k * 2, t), fall * left)))
        }
    }

    /**
     * The end: as the item shrinks away ([end] from 0 to 1) a star of light flares up where it was,
     * with a ring running out from it, and goes out with it.
     */
    private fun drawVanish(sink: AlpakaGuiElementSink, pose: Matrix3x2f, drop: Drop, x: Float, y: Float, r: Float, end: Float, t: Float) {
        val u = ((end - 0.55f) / 0.45f).coerceIn(0f, 1f)
        if (u <= 0f || u >= 1f) return
        val flare = sin(u * PI.toFloat())
        sink.`alpaka$submitElement`(GlowRenderState.disc(pose, x, y, (0.6f + 0.9f * u) * r, GlowRenderState.GLOW, 0, argb(tint(drop, 0, t), 0.7f * flare)))
        sink.`alpaka$submitElement`(GlowRenderState.disc(pose, x, y, (0.3f + 0.8f * flare) * r, GlowRenderState.SPARKLE, 0, argb(0xFFFFFF, flare)))
        sink.`alpaka$submitElement`(GlowRenderState.disc(pose, x, y, (0.3f + 1.6f * easeOutCubic(u)) * r, GlowRenderState.RING, 70,
            argb(tint(drop, 1, t), 0.8f * (1f - u))))
    }

    /**
     * Everything that glows around the resting item at (cx, cy), for an item [r] across, [t] seconds
     * after it landed: the landing's flash, then the drop's own effect, all at [light].
     */
    private fun drawLight(sink: AlpakaGuiElementSink, pose: Matrix3x2f, drop: Drop, cx: Float, cy: Float, r: Float, t: Float, light: Float) {
        if (light <= 0f) return
        drawLanding(sink, pose, drop, cx, cy, r, t)
        when (drop.effect) {
            Effect.GOLDEN -> drawGolden(sink, pose, drop, cx, cy, r, t, light)
            Effect.PRISM -> drawPrism(sink, pose, drop, cx, cy, r, t, light)
            Effect.ARCANE -> drawArcane(sink, pose, drop, cx, cy, r, t, light)
            Effect.BLOOM -> drawBloom(sink, pose, drop, cx, cy, r, t, light)
            Effect.STARBURST -> drawStarburst(sink, pose, drop, cx, cy, r, t, light)
        }
    }

    /** The landing: a bright flash, two rings running out from the item and sparks flying off. */
    private fun drawLanding(sink: AlpakaGuiElementSink, pose: Matrix3x2f, drop: Drop, cx: Float, cy: Float, r: Float, t: Float) {
        val burst = (t / 0.9f).coerceIn(0f, 1f)
        if (burst >= 1f) return
        val k = easeOutCubic(burst)
        val gone = 1f - burst
        sink.`alpaka$submitElement`(GlowRenderState.disc(pose, cx, cy, (0.6f + 2.2f * k) * r, GlowRenderState.GLOW, 0, argb(tint(drop, 0, t), gone * 0.9f)))
        sink.`alpaka$submitElement`(GlowRenderState.disc(pose, cx, cy, (0.4f + 3.2f * k) * r, GlowRenderState.RING, 90, argb(tint(drop, 1, t), gone * 0.9f)))
        val late = easeOutCubic(((t - 0.12f) / 0.8f).coerceIn(0f, 1f))
        sink.`alpaka$submitElement`(GlowRenderState.disc(pose, cx, cy, (0.3f + 2.0f * late) * r, GlowRenderState.RING, 50, argb(tint(drop, 2, t), (1f - late) * 0.7f)))
        val sparks = 18
        for (i in 0 until sparks) {
            val seed = AuraEffects.hash(i * 41 + 11)
            val angle = i * (2f * PI.toFloat() / sparks) + seed
            val dist = (0.5f + (1.6f + 1.2f * seed) * k) * r
            sink.`alpaka$submitElement`(GlowRenderState.disc(pose, cx + cos(angle) * dist, cy + sin(angle) * dist + 0.5f * r * burst * burst,
                (0.09f + 0.07f * seed) * r * gone + 1f, GlowRenderState.SPARKLE, 0, argb(tint(drop, i, t), gone)))
        }
        // A dungeon reward's beam of light comes down on it from the top of the screen as it lands.
        if (drop.effect == Effect.ARCANE) {
            sink.`alpaka$submitElement`(GlowRenderState.ray(pose, cx, cy, 0f, -1f, 0f, cy * (1f - k * 0.3f), (1.6f - k) * r,
                argb(tint(drop, 0, t), gone)))
        }
    }

    /**
     * A slayer's headline drop: a vortex. Sparks in gold and the slayer's color stream round on
     * three spiral arms into the item, its heart beats out in double rings of the slayer's color, and
     * a few broad rays turn slowly behind it all.
     */
    private fun drawGolden(sink: AlpakaGuiElementSink, pose: Matrix3x2f, drop: Drop, cx: Float, cy: Float, r: Float, t: Float, light: Float) {
        val outer = AuraEffects.mix(GOLD_DEEP, drop.accent, 0.6f)
        val rayAccent = AuraEffects.mix(GOLD, drop.accent, 0.6f)

        // The heart: lub-dub every 1.4 s, the halo swelling with each beat.
        val beat = t % 1.4f
        val swell = exp(-beat * 9f) + 0.7f * exp(-maxOf(0f, beat - 0.22f) * 9f) * (if (beat > 0.22f) 1f else 0f)
        sink.`alpaka$submitElement`(GlowRenderState.disc(pose, cx, cy, (1.9f + 0.3f * swell) * r, GlowRenderState.GLOW, 0, argb(outer, light * (0.4f + 0.2f * swell))))
        sink.`alpaka$submitElement`(GlowRenderState.disc(pose, cx, cy, (1.0f + 0.2f * swell) * r, GlowRenderState.GLOW, 0, argb(GOLD, light * 0.5f)))
        for (offset in floatArrayOf(0f, 0.22f)) {
            val p = (beat - offset) / 0.8f
            if (p < 0f || p > 1f) continue
            sink.`alpaka$submitElement`(GlowRenderState.disc(pose, cx, cy, (0.9f + 1.6f * easeOutCubic(p)) * r, GlowRenderState.RING, 70,
                argb(rayAccent, light * 0.85f * (1f - p) * (1f - p))))
        }

        // Broad rays turning slowly behind the vortex.
        val grow = easeOutBack((t / 0.6f).coerceIn(0f, 1f), 1.8f)
        for (i in 0 until 6) {
            val angle = i * (2f * PI.toFloat() / 6f) + t * 0.25f
            val length = (1.8f + 0.3f * sin(t * 2f + i)) * r * grow
            if (length < 1f) continue
            sink.`alpaka$submitElement`(GlowRenderState.ray(pose, cx, cy, cos(angle), sin(angle),
                0.2f * r, 0.2f * r + length, 0.6f * r, argb(if (i % 2 == 0) GOLD else rayAccent, light * 0.3f)))
        }

        // The vortex: three arms of sparks, each spark swept round and in until it sinks into the item.
        val arms = 3
        val per = 12
        val gather = easeOutCubic((t / 0.8f).coerceIn(0f, 1f))
        for (a in 0 until arms) {
            for (j in 0 until per) {
                val life = fract(t * 0.4f + j / per.toFloat() + a * 0.11f)
                val radius = (2.4f - 2.0f * life) * r * gather
                val angle = a * (2f * PI.toFloat() / arms) + life * 3.4f + t * 0.9f
                val x = cx + cos(angle) * radius
                val y = cy + sin(angle) * radius * 0.85f
                val fade = sin(life * PI.toFloat())
                val color = if ((j + a) % 2 == 0) GOLD_PALE else drop.accent
                sink.`alpaka$submitElement`(GlowRenderState.disc(pose, x, y, (0.14f + 0.16f * (1f - life)) * r, GlowRenderState.GLOW, 0, argb(color, light * fade)))
                if (j % 2 == 0) sink.`alpaka$submitElement`(GlowRenderState.disc(pose, x, y, (0.16f + 0.14f * (1f - life)) * r,
                    GlowRenderState.SPARKLE, 0, argb(GOLD_PALE, light * fade)))
            }
        }
    }

    /**
     * A dye: a real rainbow. It draws itself across the background from left to right, a star at
     * its tip, glitters along its whole length once it stands, twinkles while it stays and at the end
     * dissolves into sparkles. A color-shifting glow behind the item and confetti raining down.
     */
    private fun drawPrism(sink: AlpakaGuiElementSink, pose: Matrix3x2f, drop: Drop, cx: Float, cy: Float, r: Float, t: Float, light: Float) {
        val breathe = 0.5f + 0.5f * sin(t * 2.2f)
        sink.`alpaka$submitElement`(GlowRenderState.disc(pose, cx, cy, (1.7f + 0.25f * breathe) * r, GlowRenderState.GLOW, 0, argb(hue(fract(t * 0.12f)), light * 0.4f)))
        sink.`alpaka$submitElement`(GlowRenderState.disc(pose, cx, cy, (0.95f + 0.15f * breathe) * r, GlowRenderState.GLOW, 0, argb(hue(fract(t * 0.12f + 0.5f)), light * 0.45f)))

        // Confetti raining down past the item, swaying as it falls.
        val confetti = 30
        for (i in 0 until confetti) {
            val seed = AuraEffects.hash(i * 31 + 7)
            val life = fract(t / (1.6f + 1.0f * seed) + AuraEffects.hash(i * 17 + 3))
            val x = cx + (AuraEffects.hash(i * 13 + 1) - 0.5f) * 3.6f * r + sin(life * 6f + i) * 0.25f * r
            val y = cy - 1.8f * r + 3.6f * r * life
            val fade = sin(life * PI.toFloat())
            // Each piece a little slip of paper turning over as it falls.
            val turn = abs(cos(life * 9f + i))
            sink.`alpaka$submitElement`(GlowRenderState.ellipse(pose, x, y, 0.09f * r, (0.02f + 0.06f * turn) * r, i + life * 4f,
                GlowRenderState.GLOW, 0, argb(hue(seed), light * fade)))
        }
    }

    /** The dye's rainbow across the screen behind everything; see [drawPrism]. */
    private fun drawRainbow(sink: AlpakaGuiElementSink, pose: Matrix3x2f, width: Float, height: Float, r: Float,
                            t: Float, landed: Float, end: Float) {
        if (landed <= 0f) return
        // Centered below the screen, so the arc stands on its bottom edge and spans it.
        val radius = maxOf(width * 0.62f, height * 0.9f)
        val ccx = width / 2f
        val ccy = height + 0.25f * radius
        // How far along the arc it leaves the screen at the bottom, on either side.
        val edge = (kotlin.math.asin(((ccy - height) / radius).coerceIn(0f, 1f)) / PI.toFloat())
        val span = 1f - 2f * edge
        val sweep = easeInOutCubic(((t - 0.1f) / 1.5f).coerceIn(0f, 1f))
        val reveal = edge + span * sweep
        val alpha = 0.6f * landed * (1f - smooth(end))
        sink.`alpaka$submitElement`(GlowRenderState.disc(pose, ccx, ccy, radius, GlowRenderState.RAINBOW,
            (reveal * 1000f).toInt().coerceIn(0, 1000), argb(0xFFFFFF, alpha)))

        val bandIn = radius * 0.88f
        val bandW = radius * 0.12f
        fun pointX(along: Float, across: Float) = ccx + (bandIn + bandW * across) * cos(PI.toFloat() * (1f - along))
        fun pointY(along: Float, across: Float) = ccy - (bandIn + bandW * across) * sin(PI.toFloat() * (1f - along))

        // The star at the tip as it draws itself.
        if (sweep in 0.001f..0.999f) {
            val x = pointX(reveal, 0.5f)
            val y = pointY(reveal, 0.5f)
            sink.`alpaka$submitElement`(GlowRenderState.disc(pose, x, y, 0.7f * r, GlowRenderState.GLOW, 0, argb(0xFFFFFF, 0.7f * landed)))
            sink.`alpaka$submitElement`(GlowRenderState.disc(pose, x, y, (0.4f + 0.08f * sin(t * 30f)) * r, GlowRenderState.SPARKLE, 0, argb(0xFFFFFF, landed)))
        }

        // Glitter running along it from left to right as it stands complete, then a slow twinkle,
        // and a shower of sparkles as it fades away at the end.
        val run = (t - 1.6f) / 1.1f
        val sparkles = 48
        for (i in 0 until sparkles) {
            val along = edge + span * AuraEffects.hash(i * 23 + 5)
            val across = AuraEffects.hash(i * 7 + 2)
            val place = (along - edge) / span
            val wave = if (run in -0.3f..1.3f) exp(-((run * 1.3f - 0.15f - place).let { it * it }) * 80f) else 0f
            val twinkle = if (run > 0f) maxOf(0f, sin(t * (2f + AuraEffects.hash(i * 3 + 1) * 2f) + i)).let { it * it * it * it } * 0.6f else 0f
            val shower = sin(end * PI.toFloat()) * maxOf(0f, sin(t * 9f + i * 1.3f))
            val flash = maxOf(wave, twinkle, shower) * landed
            if (flash < 0.03f || reveal < along) continue
            sink.`alpaka$submitElement`(GlowRenderState.disc(pose, pointX(along, across), pointY(along, across),
                (0.12f + 0.16f * flash) * r, GlowRenderState.SPARKLE, 0, argb(AuraEffects.mix(hue(0.78f * (1f - across)), 0xFFFFFF, 0.5f), flash)))
        }
    }

    /**
     * A dungeon reward: a beam of light standing on the item, runes circling it on two tilted
     * orbits and ripples running out.
     */
    private fun drawArcane(sink: AlpakaGuiElementSink, pose: Matrix3x2f, drop: Drop, cx: Float, cy: Float, r: Float, t: Float, light: Float) {
        val pale = AuraEffects.mix(drop.accent, 0xFFFFFF, 0.45f)
        val breathe = 0.5f + 0.5f * sin(t * 1.8f)
        sink.`alpaka$submitElement`(GlowRenderState.disc(pose, cx, cy, (2.0f + 0.2f * breathe) * r, GlowRenderState.GLOW, 0, argb(drop.accent, light * 0.4f)))
        sink.`alpaka$submitElement`(GlowRenderState.disc(pose, cx, cy, (1.0f + 0.1f * breathe) * r, GlowRenderState.GLOW, 0, argb(pale, light * 0.45f)))

        // The beam, up to the top of the screen and a little way down, breathing in width.
        val width = (0.8f + 0.2f * breathe) * r
        sink.`alpaka$submitElement`(GlowRenderState.ray(pose, cx, cy, 0f, -1f, 0.2f * r, cy, width, argb(pale, light * 0.5f)))
        sink.`alpaka$submitElement`(GlowRenderState.ray(pose, cx, cy, 0f, 1f, 0.2f * r, 1.6f * r, width, argb(pale, light * 0.25f)))

        // Ripples running out, one after another.
        for (j in 0 until 3) {
            val phase = fract(t / 1.6f + j / 3f)
            sink.`alpaka$submitElement`(GlowRenderState.disc(pose, cx, cy, (0.8f + 1.6f * phase) * r, GlowRenderState.RING, 40,
                argb(if (j % 2 == 0) drop.accent else pale, light * 0.6f * (1f - phase))))
        }

        // Runes on two orbits tilted against each other, turning opposite ways; bigger in front.
        for (orbit in 0 until 2) {
            val tiltAngle = if (orbit == 0) 0.45f else -0.45f
            val dir = if (orbit == 0) 1f else -1f
            val ct = cos(tiltAngle)
            val st = sin(tiltAngle)
            for (i in 0 until 6) {
                val a = i * (2f * PI.toFloat() / 6f) + dir * t * 1.2f + orbit * 0.5f
                val ox = cos(a) * 1.4f * r
                val oy = sin(a) * 0.4f * r
                val x = cx + ox * ct - oy * st
                val y = cy + ox * st + oy * ct
                val front = 0.5f + 0.5f * sin(a)
                sink.`alpaka$submitElement`(GlowRenderState.disc(pose, x, y, (0.18f + 0.1f * front) * r, GlowRenderState.GLOW, 0, argb(drop.accent, light * 0.5f)))
                sink.`alpaka$submitElement`(GlowRenderState.disc(pose, x, y, (0.1f + 0.08f * front) * r, GlowRenderState.SPARKLE, 0, argb(pale, light * (0.5f + 0.5f * front))))
            }
        }
    }

    /**
     * A pet: a flower of light opening behind it, two rings of petals turning against each other,
     * and fireflies wandering round it on their own looping paths, blinking, trailing light.
     */
    private fun drawBloom(sink: AlpakaGuiElementSink, pose: Matrix3x2f, drop: Drop, cx: Float, cy: Float, r: Float, t: Float, light: Float) {
        val pale = AuraEffects.mix(drop.accent, 0xFFFFFF, 0.55f)
        val breathe = 0.5f + 0.5f * sin(t * 1.5f)
        sink.`alpaka$submitElement`(GlowRenderState.disc(pose, cx, cy, (2.1f + 0.3f * breathe) * r, GlowRenderState.GLOW, 0, argb(drop.accent, light * 0.3f)))

        // The flower: it opens as the item lands, then breathes and turns.
        val open = easeOutBack((t / 1.0f).coerceIn(0f, 1f), 1.4f)
        if (open > 0.01f) {
            for (layer in 0 until 2) {
                val count = 8
                val turn = if (layer == 0) t * 0.18f else -t * 0.26f + PI.toFloat() / count
                val reach = (if (layer == 0) 2.0f else 1.4f) * r * open * (0.95f + 0.05f * breathe)
                val color = if (layer == 0) drop.accent else pale
                for (i in 0 until count) {
                    val angle = i * (2f * PI.toFloat() / count) + turn
                    val d = reach * 0.58f
                    sink.`alpaka$submitElement`(GlowRenderState.ellipse(pose, cx + cos(angle) * d, cy + sin(angle) * d,
                        reach * 0.48f, reach * 0.2f, angle, GlowRenderState.GLOW, 0, argb(color, light * 0.9f)))
                    // A bright vein down the middle of each petal.
                    sink.`alpaka$submitElement`(GlowRenderState.ellipse(pose, cx + cos(angle) * d, cy + sin(angle) * d,
                        reach * 0.4f, reach * 0.05f, angle, GlowRenderState.GLOW, 0, argb(0xFFFFFF, light * 0.5f)))
                }
            }
            sink.`alpaka$submitElement`(GlowRenderState.disc(pose, cx, cy, 0.6f * r * open, GlowRenderState.GLOW, 0, argb(0xFFFFFF, light * 0.4f)))
        }

        // Fireflies, each on a looping path of its own, with a short trail behind.
        val firefly = AuraEffects.mix(drop.accent, 0xF4FF9C, 0.65f)
        for (i in 0 until 7) {
            val fx = 0.55f + 0.13f * i
            val fy = 0.8f + 0.11f * i
            val blink = 0.45f + 0.55f * maxOf(0f, sin(t * 2.6f + i * 1.7f))
            for (k in 0 until 6) {
                val tk = t - k * 0.07f
                val x = cx + sin(tk * fx * 1.6f + i * 1.3f) * 2.3f * r
                val y = cy + sin(tk * fy * 1.6f + i * 2.1f) * 1.3f * r
                val fall = 1f - k / 6f
                sink.`alpaka$submitElement`(GlowRenderState.disc(pose, x, y, (if (k == 0) 0.35f else 0.16f * fall) * r,
                    GlowRenderState.GLOW, 0, argb(firefly, light * blink * fall)))
                if (k == 0) sink.`alpaka$submitElement`(GlowRenderState.disc(pose, x, y, 0.2f * r, GlowRenderState.SPARKLE, 0, argb(0xFFFFFF, light * blink)))
            }
        }
    }

    /**
     * Any other valuable drop: a constellation. Stars stand round the item and lines of light draw
     * themselves from one to the next, each star flaring as its line arrives; the figure turns slowly,
     * shooting stars streak past behind it and faint stars twinkle further out.
     */
    private fun drawStarburst(sink: AlpakaGuiElementSink, pose: Matrix3x2f, drop: Drop, cx: Float, cy: Float, r: Float, t: Float, light: Float) {
        val pale = AuraEffects.mix(drop.accent, 0xFFFFFF, 0.5f)
        sink.`alpaka$submitElement`(GlowRenderState.disc(pose, cx, cy, 1.5f * r, GlowRenderState.GLOW, 0, argb(drop.accent, light * 0.3f)))
        sink.`alpaka$submitElement`(GlowRenderState.disc(pose, cx, cy, 0.85f * r, GlowRenderState.GLOW, 0, argb(0xFFFFFF, light * 0.3f)))

        // Faint stars further out, twinkling.
        for (i in 0 until 18) {
            val angle = AuraEffects.hash(i * 19 + 4) * 2f * PI.toFloat()
            val radius = (1.6f + 1.4f * AuraEffects.hash(i * 11 + 9)) * r
            val tw = maxOf(0f, sin(t * (1.5f + AuraEffects.hash(i * 5 + 1) * 2f) + i * 2.4f))
            sink.`alpaka$submitElement`(GlowRenderState.disc(pose, cx + cos(angle) * radius, cy + sin(angle) * radius * 0.75f,
                (0.05f + 0.07f * tw) * r, GlowRenderState.SPARKLE, 0, argb(0xFFFFFF, light * (0.25f + 0.6f * tw))))
        }

        // Shooting stars, one every 1.8 s, from either side in turn, streaking down past the item.
        val beat = t / 1.8f
        val n = kotlin.math.floor(beat).toInt()
        val phase = beat - n
        if (phase < 0.4f) {
            val u = phase / 0.4f
            val side = if (n % 2 == 0) -1f else 1f
            val angle = if (side < 0f) 0.45f else PI.toFloat() - 0.45f
            val dx = cos(angle)
            val dy = sin(angle)
            val sx = cx - side * (2.6f + 0.6f * AuraEffects.hash(n)) * r
            val sy = cy - (1.9f - 0.5f * AuraEffects.hash(n + 7)) * r
            val x = sx + dx * u * 5f * r
            val y = sy + dy * u * 5f * r
            val fade = sin(u * PI.toFloat())
            sink.`alpaka$submitElement`(GlowRenderState.ellipse(pose, x - dx * 0.7f * r, y - dy * 0.7f * r, 0.8f * r, 0.06f * r, angle,
                GlowRenderState.GLOW, 0, argb(pale, light * fade * 0.8f)))
            sink.`alpaka$submitElement`(GlowRenderState.disc(pose, x, y, 0.2f * r, GlowRenderState.SPARKLE, 0, argb(0xFFFFFF, light * fade)))
        }

        // The constellation.
        val stars = 7
        val xs = FloatArray(stars)
        val ys = FloatArray(stars)
        for (i in 0 until stars) {
            val angle = i * (2f * PI.toFloat() / stars) + (AuraEffects.hash(i * 37 + 3) - 0.5f) * 0.6f + t * 0.12f
            val radius = (1.2f + 0.6f * AuraEffects.hash(i * 29 + 8)) * r
            xs[i] = cx + cos(angle) * radius
            ys[i] = cy + sin(angle) * radius * 0.8f
        }
        val drawn = ((t - 0.15f) / 1.6f).coerceAtLeast(0f) * stars
        for (i in 0 until stars) {
            val j = (i + 1) % stars
            val part = (drawn - i).coerceIn(0f, 1f)
            if (part <= 0f) continue
            val lx = xs[j] - xs[i]
            val ly = ys[j] - ys[i]
            val length = sqrt(lx * lx + ly * ly) * part
            val angle = kotlin.math.atan2(ly, lx)
            val ux = cos(angle)
            val uy = sin(angle)
            sink.`alpaka$submitElement`(GlowRenderState.ellipse(pose, xs[i] + ux * length / 2f, ys[i] + uy * length / 2f,
                length / 2f + 0.08f * r, 0.12f * r, angle, GlowRenderState.GLOW, 0, argb(drop.accent, light * 0.8f)))
            sink.`alpaka$submitElement`(GlowRenderState.ellipse(pose, xs[i] + ux * length / 2f, ys[i] + uy * length / 2f,
                length / 2f + 0.05f * r, 0.045f * r, angle, GlowRenderState.GLOW, 0, argb(0xFFFFFF, light)))
        }
        for (i in 0 until stars) {
            // Star i lights when the line before it arrives, the first one as the drawing starts.
            val since = (drawn - i) / stars * 1.6f
            if (since < 0f) continue
            val pop = easeOutBack((since / 0.35f).coerceIn(0f, 1f), 2.4f)
            val flare = exp(-since * 5f)
            val tw = 0.75f + 0.25f * sin(t * 3f + i * 1.9f)
            sink.`alpaka$submitElement`(GlowRenderState.disc(pose, xs[i], ys[i], (0.35f + 0.5f * flare) * r * pop, GlowRenderState.GLOW, 0,
                argb(drop.accent, light * (0.5f + 0.4f * flare))))
            sink.`alpaka$submitElement`(GlowRenderState.disc(pose, xs[i], ys[i], (0.2f + 0.35f * flare) * r * pop * tw, GlowRenderState.SPARKLE, 0,
                argb(0xFFFFFF, light)))
        }
    }

    private fun argb(rgb: Int, alpha: Float): Int = ARGB.color((alpha * 255f).toInt().coerceIn(0, 255), rgb)

    private fun easeOutCubic(x: Float): Float {
        val inv = 1f - x
        return 1f - inv * inv * inv
    }

    private fun easeOutQuint(x: Float): Float {
        val inv = 1f - x
        return 1f - inv * inv * inv * inv * inv
    }

    private fun easeInOutCubic(x: Float): Float =
        if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).let { it * it * it } / 2f

    /** Overshoots past 1 and settles back, by more the bigger [c1] is: what makes a move read as landing. */
    private fun easeOutBack(x: Float, c1: Float): Float {
        val c3 = c1 + 1f
        val m = x - 1f
        return 1f + c3 * m * m * m + c1 * m * m
    }

    /** Draws back a little below 0 first, then runs to 1: a last swell before something shrinks away. */
    private fun easeInBack(x: Float): Float {
        val c1 = 1.7f
        return (c1 + 1f) * x * x * x - c1 * x * x
    }

    private fun smooth(x: Float): Float = x * x * (3f - 2f * x)

    private fun fract(x: Float): Float = x - kotlin.math.floor(x)
}

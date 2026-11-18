package net.alpaka.addons.features.cosmetics

import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import net.alpaka.addons.client.gui.AlpakaGuiElementSink
import net.alpaka.addons.client.gui.GlowRenderState
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.cosmetics.RareDrops.Kind
import net.alpaka.addons.features.itemvalue.ItemPrices
import net.alpaka.addons.features.market.Coins
import net.alpaka.addons.features.market.ItemIcons
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
import net.minecraft.util.FormattedCharSequence
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
import kotlin.math.sqrt

/**
 * A rare drop pops up on the screen: the item, big and turning, in a golden glow, with its name in
 * its rarity's color and what it sells for under it. Bound to the screen, not the world, so it stays
 * where it is while the player turns, and it is drawn over an open menu too (a dungeon chest).
 *
 * Which drops count is read off Hypixel's chat lines ([RareDrops]): a slayer's rarest drop, a dye
 * the player finds, a dungeon chest's rare reward, and any other drop that is crazy rare or better,
 * a pet, or worth at least the set minimum. Client-side only: nothing is sent and nobody else sees
 * it. The item is drawn by [RareDropItemRenderer]; the light from soft shapes ([GlowRenderState]).
 *
 * The price is the Bazaar's or the lowest BIN ([ItemPrices]), looked up by the item's id in
 * Hypixel's item list ([ItemIcons]); both only with Network Features on. A slayer's headline drop
 * looks right without them, from textures that ship with the mod.
 */
object RareDropAnimation {

    /** How long the item takes to pop up to its full size, in seconds. */
    private const val POP_SECONDS = 0.6f

    /** How long the end takes, the item shrinking away and the light going out, in seconds. */
    private const val FADE_SECONDS = 0.8f

    /** How big the item is at Size 1, in GUI pixels. */
    private const val ITEM_PX = 56f

    /** Drops that come while one plays wait their turn; more than this many are let go. */
    private const val QUEUE_MAX = 3

    /** The same item named again this soon is the same drop told twice (a dye from a chest). */
    private const val REPEAT_MS = 10_000L

    /** How long the item, its id and its price are looked for while the lists load, in seconds. */
    private const val RESOLVE_SECONDS = 3f

    // Gold, from the white-hot center out to the deep amber at the edges of the light.
    private const val GOLD_PALE = 0xFFF4C0
    private const val GOLD = 0xFFD447
    private const val GOLD_DEEP = 0xFFA51F
    private const val AMBER = 0xFF7A12

    /** The coins' color on SkyBlock. */
    private const val COINS = 0xFFAA00

    /**
     * The chime as the item pops up: a rising major arpeggio on bells, the top note doubled by a
     * chime and the first by an amethyst shimmer. Each note is (seconds after the start, pitch).
     */
    private val NOTES = floatArrayOf(
        0.00f, 1.0f,
        0.07f, 1.2599f,
        0.14f, 1.4983f,
        0.21f, 2.0f,
    )

    /** SkyBlock's rarities by the color of their names, in SkyCofl's order for pet ids. */
    private val RARITY_COLORS = intArrayOf(0xFFFFFF, 0x55FF55, 0x5555FF, 0xAA00AA, 0xFFAA00, 0xFF55FF)

    private class Drop(
        val kind: Kind, heading: String, val name: String, val nameColor: Int, val accent: Int,
        val slayer: SlayerType?,
    ) {
        val heading: FormattedCharSequence =
            Component.literal(heading).withStyle(ChatFormatting.BOLD).visualOrderText
        val title: FormattedCharSequence =
            Component.literal(name).withStyle(ChatFormatting.BOLD).visualOrderText

        var startNanos = 0L
        var notesPlayed = 0

        var id: String? = null
        var stack: ItemStack = ItemStack.EMPTY
        var price: FormattedCharSequence? = null
        val itemState = ItemStackRenderState()

        /** Whether the item, id and price are all found, so nothing more is looked up. */
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
                // Each world change keeps the item list and prices at hand, so a drop's value is
                // known the moment it comes; both only ask again once theirs is a few minutes old.
                ItemIcons.ready()
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
        show(found.kind, found.heading, found.name, color, if (headline) boss else null)
    }

    private fun show(kind: Kind, heading: String, name: String, color: Int?, slayer: SlayerType?) {
        val now = System.currentTimeMillis()
        recent.entries.removeIf { now - it.value > REPEAT_MS }
        val key = name.lowercase(Locale.ROOT)
        if (key in recent) return
        recent[key] = now

        val nameColor = color ?: GOLD
        val accent = if (slayer != null && AlpakaConfig.instance.rareDropAnimationSlayerColors) accentOf(slayer)
        else color ?: AMBER
        val drop = Drop(kind, heading, name, nameColor, accent, slayer)
        if (current == null) start(drop) else if (queue.size < QUEUE_MAX) queue.addLast(drop)
    }

    private fun start(drop: Drop) {
        drop.startNanos = System.nanoTime()
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
        ItemPrices.refresh()
        val sample = SAMPLES[testIndex % SAMPLES.size]
        testIndex++
        recent.remove(sample.name.lowercase(Locale.ROOT))
        current = null
        queue.clear()
        show(sample.kind, sample.heading, sample.name, sample.color, sample.slayer)
    }

    private class Sample(val kind: Kind, val heading: String, val name: String, val color: Int, val slayer: SlayerType?)

    private val SAMPLES = listOf(
        Sample(Kind.DROP, "PRAY TO RNGESUS DROP!", "Judgement Core", 0xFFAA00, SlayerType.ENDERMAN),
        Sample(Kind.DYE, "RARE DYE!", "Necron Dye", 0xFFAA00, null),
        Sample(Kind.DUNGEON, "RARE REWARD!", "Necron's Handle", 0xAA00AA, null),
        Sample(Kind.PET, "PET DROP!", "Ender Dragon", 0xFFAA00, null),
        Sample(Kind.DROP, "CRAZY RARE DROP!", "Smoldering VI", 0x5555FF, null),
        Sample(Kind.DROP, "INSANE DROP!", "High Class Archfiend Dice", 0xFFAA00, SlayerType.BLAZE),
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
            return if (rarity < 0) null else RareDrops.petId(name, rarity)
        }
        return RareDrops.bookId(name) ?: idByName(name) ?: RareDrops.dyeId(name)
    }

    /** Every SkyBlock item's id by its name in lower case, built once Hypixel's item list is in. */
    private var idsByName: Map<String, String>? = null

    /**
     * The id of the item with this name in Hypixel's list, such as NECRON_HANDLE for "Necron's
     * Handle"; null while the list loads or for a name it lacks. Where several items share a name,
     * the shortest id wins: the plain item rather than a variant of it.
     */
    private fun idByName(name: String): String? {
        val map = idsByName ?: ItemIcons.skyblockItems()?.let { items ->
            val byName = HashMap<String, String>()
            for ((id, itemName) in items) {
                byName.merge(itemName.lowercase(Locale.ROOT), id) { a, b -> if (b.length < a.length || (b.length == a.length && b < a)) b else a }
            }
            byName.also { idsByName = it }
        } ?: return null
        return map[name.trim().lowercase(Locale.ROOT)]
    }

    /**
     * Looks up what is still missing of the drop's id, look and price, for the first seconds while
     * the lists may still be on their way, then settles on what it has.
     */
    private fun resolve(drop: Drop, t: Float) {
        if (drop.resolved) return
        val cfg = AlpakaConfig.instance
        if (drop.id == null) drop.id = idFor(drop.kind, drop.name, drop.nameColor)
        if (drop.stack.isEmpty) drop.stack = bundled(drop.name) ?: ItemIcons.forItem(drop.id, drop.name)
        if (drop.price == null && cfg.rareDropAnimationPrice) {
            ItemPrices.quote(drop.id)?.let {
                drop.price = Component.literal(Coins.compact(it.price()) + " coins").visualOrderText
            }
        }
        val done = drop.id != null && !drop.stack.isEmpty && (drop.price != null || !cfg.rareDropAnimationPrice)
        // A name no list knows will not turn up later: show the stand-in now rather than an empty
        // middle for the whole wait.
        val unknown = drop.id == null && drop.stack.isEmpty && ItemIcons.skyblockItems() != null
        // Without Network Features nothing more will come: settle on the first look.
        if (done || unknown || t > RESOLVE_SECONDS || !cfg.allowApiCalls) {
            if (drop.stack.isEmpty) drop.stack = ItemStack(Items.NETHER_STAR)
            drop.resolved = true
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
        while (drop.notesPlayed * 2 < NOTES.size && NOTES[drop.notesPlayed * 2] <= t) {
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
        val duration = cfg.rareDropAnimationDuration.coerceIn(2f, 15f)
        val t = ((System.nanoTime() - drop.startNanos) / 1e9).toFloat()
        if (t >= duration) {
            current = queue.removeFirstOrNull()?.also { start(it) }
            return
        }
        resolve(drop, t)
        playDueNotes(drop, t)

        val size = cfg.rareDropAnimationSize.coerceIn(0.5f, 2.5f)
        val r = ITEM_PX * size
        val cx = graphics.guiWidth() / 2f
        val cy = graphics.guiHeight() * 0.34f

        val pop = easeOutBack((t / POP_SECONDS).coerceIn(0f, 1f))
        val fade = smooth(((duration - t) / FADE_SECONDS).coerceIn(0f, 1f))
        val light = (t / 0.25f).coerceIn(0f, 1f) * fade
        val bob = 0.05f * r * sin(t * 2.4f)

        val sink = graphics as AlpakaGuiElementSink
        drawLight(sink, Matrix3x2f(graphics.pose()), cx, cy, r, t, light, drop.accent)

        // The item over its light: a later stratum is drawn after the light's.
        graphics.nextStratum()
        // An item frame's view of the item (FIXED) is half a block across: twice r makes it r wide.
        val itemScale = 2f * r * pop * fade
        if (itemScale > 1f && !drop.stack.isEmpty) {
            mc.itemModelResolver.updateForTopItem(drop.itemState, drop.stack, ItemDisplayContext.FIXED, level, null, 0)
            // A fixed box, a little bigger than the item at its biggest, so the picture's texture is
            // made once rather than again every frame of the pop.
            val half = (r * 1.25f).toInt()
            val x = cx.toInt()
            val y = cy.toInt()
            // Spins fast as it appears and settles into a slow turn.
            val spin = 1.3f * t + (5f * PI.toFloat()) * (1f - exp(-t * 1.8f)) / 1.8f
            sink.`alpaka$submitPicture`(RareDropItemRenderer.State(drop.itemState, spin, -0.18f,
                bob + (cy - y), x - half, y - half, x + half, y + half, itemScale))
        }

        // The heading above the item, the name and price under it, once it has popped up.
        val textAlpha = ((t - 0.35f) / 0.35f).coerceIn(0f, 1f) * fade
        if (textAlpha > 0.02f) {
            val big = 1.5f * sqrt(size)
            val small = 1f * sqrt(size)
            text(graphics, drop.heading, cx, cy - r * 0.95f - 9f * small, small, drop.accent.takeIf { drop.slayer != null } ?: GOLD, textAlpha)
            val nameY = cy + r * 0.8f
            text(graphics, drop.title, cx, nameY, big, drop.nameColor, textAlpha)
            drop.price?.let { text(graphics, it, cx, nameY + 9f * big + 3f, small, COINS, textAlpha) }
        }
    }

    private fun text(graphics: GuiGraphicsExtractor, text: FormattedCharSequence, x: Float, y: Float, scale: Float, rgb: Int, alpha: Float) {
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(x, y)
        pose.scale(scale, scale)
        graphics.centeredText(Minecraft.getInstance().font, text, 0, 0, ARGB.color((alpha * 255f).toInt().coerceIn(4, 255), rgb))
        pose.popMatrix()
    }

    /**
     * Everything that glows, in GUI pixels around (cx, cy), for an item [r] across: the flash and
     * shock wave of the pop, the halo and rays behind the item and sparkles drifting up around it.
     */
    private fun drawLight(sink: AlpakaGuiElementSink, pose: Matrix3x2f, cx: Float, cy: Float, r: Float, t: Float, light: Float, accent: Int) {
        if (light <= 0f) return
        val outer = AuraEffects.mix(GOLD_DEEP, accent, 0.6f)
        val rayAccent = AuraEffects.mix(GOLD, accent, 0.6f)

        // The pop: a bright flash and a ring running out from the item.
        val burst = (t / 0.9f).coerceIn(0f, 1f)
        if (burst < 1f) {
            val k = easeOutCubic(burst)
            sink.`alpaka$submitElement`(GlowRenderState.disc(pose, cx, cy, (0.6f + 2.2f * k) * r, GlowRenderState.GLOW, 0, argb(GOLD_PALE, (1f - burst) * 0.85f)))
            sink.`alpaka$submitElement`(GlowRenderState.disc(pose, cx, cy, (0.4f + 2.8f * k) * r, GlowRenderState.RING, 90, argb(rayAccent, (1f - burst) * 0.9f)))
        }

        // The halo behind the item, breathing slowly.
        val breathe = 0.5f + 0.5f * sin(t * 2.6f)
        sink.`alpaka$submitElement`(GlowRenderState.disc(pose, cx, cy, (1.9f + 0.25f * breathe) * r, GlowRenderState.GLOW, 0, argb(outer, light * 0.45f)))
        sink.`alpaka$submitElement`(GlowRenderState.disc(pose, cx, cy, (1.05f + 0.15f * breathe) * r, GlowRenderState.GLOW, 0, argb(GOLD, light * 0.5f)))

        // God rays, turning slowly and flickering.
        val rays = 14
        val grow = easeOutCubic((t / 0.7f).coerceIn(0f, 1f))
        for (i in 0 until rays) {
            val angle = i * (2f * PI.toFloat() / rays) + t * 0.35f
            val flicker = 0.6f + 0.4f * sin(t * 3.1f + i * 1.7f)
            val length = (1.3f + 0.9f * flicker) * r * grow
            if (length < 1f) continue
            val color = if (i % 2 == 0) GOLD else rayAccent
            sink.`alpaka$submitElement`(GlowRenderState.ray(pose, cx, cy, cos(angle), sin(angle),
                0.2f * r, 0.2f * r + length, 0.34f * r, argb(color, light * 0.5f * flicker)))
        }

        // A ring of light around the item, with a slow pulse.
        sink.`alpaka$submitElement`(GlowRenderState.disc(pose, cx, cy, (0.95f + 0.05f * breathe) * r, GlowRenderState.RING, 60, argb(rayAccent, light * 0.55f)))

        // Sparkles drifting up around the item, each born again at the bottom when it fades out.
        val sparkles = 22
        for (i in 0 until sparkles) {
            val seed = AuraEffects.hash(i * 31 + 7)
            val period = 1.8f + 1.2f * seed
            val life = fract(t / period + AuraEffects.hash(i * 17 + 3))
            val angle = i * 2.39996f + t * (0.5f + 0.4f * seed)
            val radius = (0.75f + 0.75f * AuraEffects.hash(i * 13 + 1)) * r
            val x = cx + cos(angle) * radius
            val y = cy + sin(angle) * radius * 0.6f + (0.6f - 1.3f * life) * r
            val twinkle = sin(life * PI.toFloat())
            val rgb = if (i % 3 == 0) GOLD_PALE else if (i % 3 == 1) GOLD else accent
            sink.`alpaka$submitElement`(GlowRenderState.disc(pose, x, y, (0.1f + 0.1f * seed) * r, GlowRenderState.SPARKLE, 0, argb(rgb, light * twinkle)))
        }
    }

    private fun argb(rgb: Int, alpha: Float): Int = ARGB.color((alpha * 255f).toInt().coerceIn(0, 255), rgb)

    private fun easeOutCubic(x: Float): Float {
        val inv = 1f - x
        return 1f - inv * inv * inv
    }

    /** Overshoots past 1 and settles back, which is what makes the item read as popping up. */
    private fun easeOutBack(x: Float): Float {
        val c1 = 2.2f
        val c3 = c1 + 1f
        val m = x - 1f
        return 1f + c3 * m * m * m + c1 * m * m
    }

    private fun smooth(x: Float): Float = x * x * (3f - 2f * x)

    private fun fract(x: Float): Float = x - kotlin.math.floor(x)
}

package net.alpaka.addons.features.damagetags

import com.mojang.blaze3d.vertex.PoseStack
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.slayer.SlayerBossNameTag
import net.alpaka.addons.features.slayer.SlayerType
import net.alpaka.addons.utils.AlpakaPerf
import net.alpaka.addons.utils.DungeonState
import net.alpaka.addons.utils.SkyblockUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.network.chat.Style
import net.minecraft.util.ARGB
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.Mth
import net.minecraft.world.entity.decoration.ArmorStand
import java.util.Locale
import kotlin.math.sin

/**
 * Draws Hypixel's damage splashes itself: each number pops up with a little overshoot, drifts
 * upwards, and fades out, colored with a gradient across its digits. Crits and normal hits each
 * get their own pair of colors.
 *
 * Purely a picture. Hypixel's splash is an invisible armor stand with the number as its name; that
 * name tag is hidden (see [DamageTagFeature.shouldHideNameTag]) and a copy of the number is drawn
 * where it stood. Nothing is sent and nothing about the fight changes.
 *
 * Splashes are found on the client tick, as young armor stands whose name reads as a damage number,
 * each taken once. From then on the copy lives on its own clock, so it animates the same however
 * long Hypixel keeps the stand around.
 */
object CustomDamageTagFeature {

    const val DUNGEON_SHOW = 0
    const val DUNGEON_HIDE_BOSS_ROOM = 1
    const val DUNGEON_HIDE_EVERYWHERE = 2

    /** Names for the config slider, indexed by the DUNGEON_* constants. */
    @JvmField
    val DUNGEON_MODE_NAMES = arrayOf("Show", "Hide In Boss Room", "Hide In Whole Dungeon")

    /** The slayer bosses' names, from every slayer. */
    private val SLAYER_BOSS_NAMES: Array<String> =
        SlayerType.values().flatMap { it.bossNames.asIterable() }.toTypedArray()

    /**
     * The Blaze boss's demons, which take damage as the boss does. Their names are circled letters
     * no other mob uses, so the name alone settles it.
     */
    private val DEMON_NAMES = arrayOf("ⓆⓊⒶⓏⒾⒾ", "ⓉⓎⓅⒽⓄⒺⓊⓈ")

    /** How far from a splash a slayer boss's name tag may be for the splash to count as the boss's. */
    private const val BOSS_RADIUS_XZ = 4.5
    private const val BOSS_RADIUS_Y = 5.0

    const val FORMAT_ORIGINAL = 0
    const val FORMAT_COMPACT = 1
    const val FORMAT_FULL = 2

    /** Names for the config slider, indexed by the FORMAT_* constants. */
    @JvmField
    val FORMAT_NAMES = arrayOf("As Hypixel", "Compact (1.2M)", "Full (1,234,567)")

    /** Vanilla's `EntityRenderer.NAMETAG_SCALE`: world units per text pixel. */
    private const val TEXT_SCALE = 0.025f

    /** Vanilla lifts a name tag half a block above the entity. */
    private const val NAME_TAG_LIFT = 0.5

    /** A stand older than this when first seen is not a fresh splash: the hit happened before we looked. */
    private const val MAX_DETECT_AGE_TICKS = 20

    /** How long the pop at the start takes. */
    private const val POP_MS = 220f

    /** The share of the lifetime at the end over which the tag fades out. */
    private const val FADE_SHARE = 0.4f

    /** Crits are drawn this much larger than normal hits. */
    private const val CRIT_SCALE = 1.25f

    /** Upper bound on tags in the air at once; the oldest goes first. */
    private const val MAX_TAGS = 64

    /** How long a stand's id is remembered after its tag was taken, so it is never taken twice. */
    private const val SEEN_MEMORY_MS = 30_000L

    private const val LOVE_HEART = "❤"

    private const val FULL_BRIGHT = 0xF000F0

    /** Below this alpha the font treats the color as opaque, so the tag is simply not drawn. */
    private const val MIN_ALPHA = 8

    private class Tag(
        val x: Double, val y: Double, val z: Double,
        val text: String,
        val crit: Boolean,
        val bornMs: Long,
    ) {
        /** Fixed per tag so a stack of hits on one spot fans out instead of piling up. */
        val driftX = (Math.random() - 0.5) * 0.35
        val driftZ = (Math.random() - 0.5) * 0.35

        /**
         * The still gradient last built for this tag, and the two colours it was built from. Only used while
         * Flowing Gradient is off, when the gradient cannot change unless the colours do.
         */
        var gradient: FormattedCharSequence? = null
        var gradientStart = 0
        var gradientEnd = 0
    }

    private val tags = ArrayDeque<Tag>()
    private val seen = HashMap<Int, Long>()

    @JvmStatic
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            val t = AlpakaPerf.begin(AlpakaPerf.Section.DAMAGE_TAGS_TICK)
            try {
                tick(mc)
            } finally {
                AlpakaPerf.end(AlpakaPerf.Section.DAMAGE_TAGS_TICK, t)
            }
        }
        LevelRenderEvents.COLLECT_SUBMITS.register { context ->
            val t = AlpakaPerf.begin(AlpakaPerf.Section.DAMAGE_TAGS)
            try {
                submit(context)
            } finally {
                AlpakaPerf.end(AlpakaPerf.Section.DAMAGE_TAGS, t)
            }
        }
    }

    @JvmStatic
    fun reset() {
        tags.clear()
        seen.clear()
    }

    /**
     * Puts a handful of sample splashes in front of the player, from "/alpakadebug damagetags":
     * the only way to see them without hitting something on SkyBlock.
     */
    @JvmStatic
    fun spawnPreview() {
        val player = Minecraft.getInstance().player ?: return
        val look = player.lookAngle
        val baseX = player.x + look.x * 3.0
        val baseY = player.eyeY - 0.6
        val baseZ = player.z + look.z * 3.0
        val now = System.currentTimeMillis()
        // Number, crit, Ring of Love.
        val samples = listOf(
            Triple("1,234", false, false), Triple("48,920", true, false), Triple("139k", false, false),
            Triple("2,417,880", true, true), Triple("873", false, true),
        )
        for ((i, sample) in samples.withIndex()) {
            val text = format(sample.first) + if (sample.third) LOVE_HEART else ""
            add(Tag(baseX + (i - 2) * 0.5, baseY, baseZ, text, sample.second, now + i * 150L))
        }
    }

    private fun tick(mc: Minecraft) {
        val cfg = AlpakaConfig.instance
        val now = System.currentTimeMillis()
        val lifetime = cfg.customDamageTagDurationMs.toLong()
        tags.removeAll { now - it.bornMs > lifetime }
        seen.entries.removeIf { now - it.value > SEEN_MEMORY_MS }

        val level = mc.level
        if (!cfg.customDamageTagsEnabled || level == null || !SkyblockUtils.isOnSkyblock()) return
        val hiddenInDungeon = when (cfg.customDamageTagDungeonMode) {
            DUNGEON_HIDE_EVERYWHERE -> DungeonState.inDungeon()
            DUNGEON_HIDE_BOSS_ROOM -> DungeonState.inBossRoom
            else -> false
        }

        for (entity in level.entitiesForRendering()) {
            if (entity !is ArmorStand || entity.tickCount > MAX_DETECT_AGE_TICKS) continue
            if (seen.containsKey(entity.id)) continue
            val splash = DamageTagFeature.parseSplash(entity) ?: continue
            seen[entity.id] = now
            // Only Show Crit Damage still applies: a hidden non-crit gets no replacement either.
            if (!splash.crit && cfg.onlyCritDamageEnabled) continue
            // Hypixel's own splash stays hidden either way; these only decide whether ours is drawn.
            if (hiddenInDungeon) continue
            if (cfg.customDamageTagHideNearSlayerBoss && nearSlayerBoss(level, entity)) continue
            // Ring of Love's heart stays on the copy: it is what tells a doubled hit apart.
            val text = format(splash.number) + if (splash.love) LOVE_HEART else ""
            add(Tag(entity.x, entity.y + entity.bbHeight + NAME_TAG_LIFT, entity.z, text, splash.crit, now))
        }
    }

    /**
     * Whether a slayer boss stands next to this splash, judged by the boss's name tag: what
     * SkyHanni's "Hide Damage Splashes Near Slayer Boss" checks, any player's boss included.
     * [SlayerBossNameTag] keeps out the summoning mobs that share a boss's name.
     */
    private fun nearSlayerBoss(level: ClientLevel, splash: ArmorStand): Boolean {
        val box = splash.boundingBox.inflate(BOSS_RADIUS_XZ, BOSS_RADIUS_Y, BOSS_RADIUS_XZ)
        return level.getEntitiesOfClass(ArmorStand::class.java, box) { stand ->
            val name = SkyblockUtils.cleanColor(stand.customName?.string ?: return@getEntitiesOfClass false)
            SlayerBossNameTag.isBoss(name, SLAYER_BOSS_NAMES) || DEMON_NAMES.any { name.contains(it) }
        }.isNotEmpty()
    }

    private fun add(tag: Tag) {
        if (tags.size >= MAX_TAGS) tags.removeFirst()
        tags.addLast(tag)
    }

    private fun submit(context: LevelRenderContext) {
        if (tags.isEmpty()) return
        // Not gated on the setting: with it off, only preview tags are ever added.
        val cfg = AlpakaConfig.instance

        val camera = context.levelState().cameraRenderState
        val poseStack = context.poseStack()
        val out = context.submitNodeCollector().order(1)
        val font = Minecraft.getInstance().font
        val now = System.currentTimeMillis()
        val lifetime = cfg.customDamageTagDurationMs.toFloat()
        val time = (now % 3_600_000L) / 1000.0

        for (tag in tags) {
            val ageMs = (now - tag.bornMs).toFloat()
            if (ageMs < 0f) continue // A staggered preview tag that is not due yet.
            val life = (ageMs / lifetime).coerceIn(0f, 1f)

            val alpha = (fadeAlpha(life) * 255f).toInt()
            if (alpha < MIN_ALPHA) continue

            val rise = cfg.customDamageTagRise * easeOutCubic(life)
            val drift = easeOutCubic(life)
            val scale = TEXT_SCALE * cfg.customDamageTagScale * (if (tag.crit) CRIT_SCALE else 1f) *
                popScale(ageMs, cfg.customDamageTagPopStrength) * (1f - 0.15f * fadeProgress(life))
            if (scale <= 0f) continue

            val text = tag.text
            val start = if (tag.crit) cfg.customDamageTagCritStart else cfg.customDamageTagNormalStart
            val end = if (tag.crit) cfg.customDamageTagCritEnd else cfg.customDamageTagNormalEnd
            // A still gradient is the same every frame, so it is built once per tag and again only when
            // a colour changes. The width is still measured every frame: a resource reload can change
            // the glyphs.
            val sequence = if (cfg.customDamageTagFlowingGradient) {
                gradient(text, start, end, time)
            } else {
                val built = tag.gradient
                if (built != null && tag.gradientStart == start && tag.gradientEnd == end) built
                else gradient(text, start, end, null).also { tag.gradient = it; tag.gradientStart = start; tag.gradientEnd = end }
            }
            val width = font.width(sequence)

            poseStack.pushPose()
            poseStack.translate(
                tag.x + tag.driftX * drift - camera.pos.x,
                tag.y + rise - camera.pos.y,
                tag.z + tag.driftZ * drift - camera.pos.z,
            )
            poseStack.mulPose(camera.orientation)
            poseStack.scale(scale, -scale, scale)

            val color = ARGB.color(alpha, 0xFFFFFF)
            out.submitText(poseStack, -width / 2f, -4f, sequence, cfg.customDamageTagShadow, Font.DisplayMode.NORMAL, FULL_BRIGHT, color, 0, 0)
            poseStack.popPose()
        }
    }

    /**
     * The number with each character in its own color, running from [start] to [end]. With a
     * [time], the gradient sways back and forth along the number.
     *
     * Built straight as the sequence the font draws rather than as a Component read through
     * visualOrderText. A tag only ever holds ASCII digits, ',', '.' and a k/M/B suffix (the damage
     * number pattern or [format]), which read left to right in every language, so a component tree
     * and an ICU bidi pass per tag per frame could never change the order. Should a tag ever hold
     * other characters, it has to go back through visualOrderText. The styles are worked out up
     * front, so the width pass and the later draw see the same ones.
     */
    private fun gradient(text: String, start: Int, end: Int, time: Double?): FormattedCharSequence {
        val count = text.length
        val styles = Array(count) { index ->
            var position = if (count > 1) index.toFloat() / (count - 1) else 0f
            if (time != null) position = (position + 0.35f * sin(time * 3.0 + index * 0.6).toFloat()).coerceIn(0f, 1f)
            Style.EMPTY.withColor(lerpRgb(start, end, position))
        }
        return FormattedCharSequence { sink ->
            for (i in 0 until count) {
                if (!sink.accept(i, styles[i], text[i].code)) return@FormattedCharSequence false
            }
            true
        }
    }

    /**
     * The tag's size over its first moments: it shoots past full size and settles back, by
     * [strength] (0 to 1). At zero it simply grows in.
     */
    private fun popScale(ageMs: Float, strength: Float): Float {
        val t = (ageMs / POP_MS).coerceIn(0f, 1f)
        val overshoot = strength * 3f
        val u = t - 1f
        return 1f + (overshoot + 1f) * u * u * u + overshoot * u * u
    }

    /** 0 until the fade starts, then up to 1 at the end of the lifetime. */
    private fun fadeProgress(life: Float): Float =
        ((life - (1f - FADE_SHARE)) / FADE_SHARE).coerceIn(0f, 1f)

    private fun fadeAlpha(life: Float): Float {
        val f = fadeProgress(life)
        return 1f - f * f
    }

    private fun easeOutCubic(t: Float): Float {
        val inv = 1f - t
        return 1f - inv * inv * inv
    }

    /** The number in the chosen format. Hypixel's own compact values ("139k") cannot be expanded and stay. */
    private fun format(number: String): String {
        val mode = AlpakaConfig.instance.customDamageTagFormat
        if (mode == FORMAT_ORIGINAL) return number
        val value = number.replace(",", "").toLongOrNull() ?: return number
        return when (mode) {
            FORMAT_COMPACT -> compact(value)
            else -> String.format(Locale.ROOT, "%,d", value)
        }
    }

    private fun compact(value: Long): String {
        val (divisor, suffix) = when {
            value >= 1_000_000_000L -> 1_000_000_000.0 to "B"
            value >= 1_000_000L -> 1_000_000.0 to "M"
            value >= 1_000L -> 1_000.0 to "k"
            else -> return value.toString()
        }
        val scaled = value / divisor
        val pattern = if (scaled >= 100) "%.0f" else if (scaled >= 10) "%.1f" else "%.2f"
        val digits = String.format(Locale.ROOT, pattern, scaled)
        return (if ('.' in digits) digits.trimEnd('0').trimEnd('.') else digits) + suffix
    }

    private fun lerpRgb(from: Int, to: Int, mix: Float): Int {
        val r = Mth.lerp(mix, ARGB.red(from).toFloat(), ARGB.red(to).toFloat()).toInt()
        val g = Mth.lerp(mix, ARGB.green(from).toFloat(), ARGB.green(to).toFloat()).toInt()
        val b = Mth.lerp(mix, ARGB.blue(from).toFloat(), ARGB.blue(to).toFloat()).toInt()
        return ARGB.color(r, g, b) and 0xFFFFFF
    }

}

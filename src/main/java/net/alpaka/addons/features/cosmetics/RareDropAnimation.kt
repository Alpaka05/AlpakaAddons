package net.alpaka.addons.features.cosmetics

import com.mojang.blaze3d.vertex.PoseStack
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.slayer.SlayerBossEntityTracker
import net.alpaka.addons.features.slayer.SlayerType
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.network.chat.Component
import net.minecraft.util.ARGB
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.component.ResolvableProfile
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * A slayer's rarest drop - High Class Archfiend Dice, Warden Heart and so on, see
 * [SlayerType.rngDropItem] - pops up as a big spinning item in a golden glow where the boss died.
 *
 * Client-side only, like the aura: the item and its light are drawn by this client into its own
 * world and nothing is sent, so nobody else sees it. The glow is made of the aura's quads and shader
 * ([AuraCanvas], `aura.fsh`); the item is drawn as an item frame would draw it, turned and scaled,
 * from textures that ship with the mod (see [stackFor]), so it works with Network Features off.
 *
 * Where the boss died is the last place the boss was seen alive, which [SlayerBossEntityTracker]
 * follows for the fight anyway. Hypixel's drop line comes as the boss dies, so that place is at most
 * a tick old. Without one (the boss was never found, or the drop came long after), the item appears
 * in front of the player instead.
 */
object RareDropAnimation {

    /** How long the item takes to pop up to its full size, in seconds. */
    private const val POP_SECONDS = 0.6f

    /** How long the end takes, the item shrinking away and the light going out, in seconds. */
    private const val FADE_SECONDS = 0.9f

    /** How far in front of the player the item appears without a boss position, in blocks. */
    private const val FRONT_DISTANCE = 6.0

    /** A boss seen this long before its drop is still where the drop belongs. */
    private const val BOSS_POSITION_MAX_AGE_MS = 10_000L

    /** A boss further away than this is not where the player is looking for the drop. */
    private const val BOSS_POSITION_MAX_DISTANCE_SQ = 48.0 * 48.0

    private const val FULL_BRIGHT = 0xF000F0

    // Gold, from the white-hot center out to the deep amber at the edges of the light.
    private const val GOLD_PALE = 0xFFF4C0
    private const val GOLD = 0xFFD447
    private const val GOLD_DEEP = 0xFFA51F
    private const val AMBER = 0xFF7A12

    private class Active(val x: Double, val y: Double, val z: Double, val type: SlayerType, val item: String, val startNanos: Long)

    private var active: Active? = null
    private var lastBossPos: Vec3? = null
    private var lastBossSeenAtMs = 0L
    private var testIndex = 0

    private val itemState = ItemStackRenderState()
    private val stacks = HashMap<String, ItemStack>()

    @JvmStatic
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val level = client.level
            if (level == null) {
                active = null
                lastBossPos = null
                return@register
            }
            SlayerBossEntityTracker.bossMob(level)?.let {
                lastBossPos = it.position()
                lastBossSeenAtMs = System.currentTimeMillis()
            }
        }
        LevelRenderEvents.COLLECT_SUBMITS.register { submit(it) }
    }

    /**
     * Hypixel announced a drop on a slayer. Starts the animation when it is that slayer's headline
     * drop and the feature is on.
     */
    @JvmStatic
    fun onSlayerDrop(type: SlayerType, item: String) {
        if (!AlpakaConfig.instance.rareDropAnimationEnabled) return
        val headline = type.rngDropItem ?: return
        if (!headline.equals(item, ignoreCase = true)) return
        val player = Minecraft.getInstance().player ?: return

        val boss = lastBossPos
        val fresh = boss != null && System.currentTimeMillis() - lastBossSeenAtMs <= BOSS_POSITION_MAX_AGE_MS &&
            boss.distanceToSqr(player.position()) <= BOSS_POSITION_MAX_DISTANCE_SQ
        val at = if (fresh) boss!! else inFrontOfPlayer() ?: return
        start(at, type, headline)
    }

    /**
     * The config's test button: closes the menu and plays the animation in front of the player, for
     * each slayer's drop in turn. Works whether the feature is on or not, and needs a world.
     */
    @JvmStatic
    fun test() {
        val mc = Minecraft.getInstance()
        if (mc.level == null || mc.player == null) return
        val types = SlayerType.entries.filter { it.rngDropItem != null }
        val type = types[testIndex % types.size]
        testIndex++
        mc.gui.setScreen(null)
        val at = inFrontOfPlayer() ?: return
        start(at, type, type.rngDropItem!!)
    }

    private fun start(at: Vec3, type: SlayerType, item: String) {
        active = Active(at.x, at.y, at.z, type, item, System.nanoTime())
    }

    /** On the ground a few blocks ahead of the player, the way they are facing. */
    private fun inFrontOfPlayer(): Vec3? {
        val player = Minecraft.getInstance().player ?: return null
        val look = player.lookAngle
        val flat = Vec3(look.x, 0.0, look.z)
        val dir = if (flat.lengthSqr() < 1e-4) Vec3(0.0, 0.0, 1.0) else flat.normalize()
        return player.position().add(dir.scale(FRONT_DISTANCE))
    }

    /**
     * What the drop looks like, from the mod's own files so it needs no network: the heads wear
     * their SkyBlock skins, copied from the NotEnoughUpdates repository into
     * `textures/rare_drop/`, and the Overflux Capacitor is an enchanted quartz as on SkyBlock. A
     * drop without a skin of its own (a new headline drop) shows as a nether star.
     */
    private fun stackFor(item: String): ItemStack = stacks.getOrPut(item.lowercase(Locale.ROOT)) {
        val id = item.lowercase(Locale.ROOT).replace(' ', '_')
        when {
            id == "overflux_capacitor" -> ItemStack(Items.QUARTZ).also {
                it.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
            }
            id in HEAD_SKINS -> head(id) ?: ItemStack(Items.NETHER_STAR)
            else -> ItemStack(Items.NETHER_STAR)
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

    private fun submit(context: LevelRenderContext) {
        val anim = active ?: return
        val cfg = AlpakaConfig.instance
        val duration = cfg.rareDropAnimationDuration.coerceIn(2f, 15f)
        val t = ((System.nanoTime() - anim.startNanos) / 1e9).toFloat()
        if (t >= duration) {
            active = null
            return
        }
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val size = cfg.rareDropAnimationSize.coerceIn(0.5f, 2.5f)

        val pop = easeOutBack((t / POP_SECONDS).coerceIn(0f, 1f))
        val fade = ((duration - t) / FADE_SECONDS).coerceIn(0f, 1f)
        val appear = (t / 0.25f).coerceIn(0f, 1f)
        val light = appear * smooth(fade)

        // The item shoots up out of the ground, then hovers and bobs.
        val rise = easeOutCubic((t / 0.8f).coerceIn(0f, 1f))
        val centerY = (0.3f + 1.3f * rise) * size + 0.08f * size * sin(t * 2.4f)
        // Spins fast as it appears and settles into a slow turn.
        val spin = 1.3f * t + (5f * PI.toFloat()) * (1f - exp(-t * 1.8f)) / 1.8f
        val itemScale = 2.2f * size * pop * smooth(fade)

        val camera = context.levelState().cameraRenderState
        val ox = (anim.x - camera.pos.x).toFloat()
        val oy = (anim.y - camera.pos.y).toFloat()
        val oz = (anim.z - camera.pos.z).toFloat()
        val right = camera.orientation.transform(Vector3f(1f, 0f, 0f))
        val up = camera.orientation.transform(Vector3f(0f, 1f, 0f))

        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), AuraFeature.renderType()) { pose, buffer ->
            val c = AuraCanvas(pose, buffer, ox, oy, oz, right.x, right.y, right.z, up.x, up.y, up.z, 1f)
            drawLight(c, t, centerY, size, light, right, up)
        }

        if (itemScale > 0.001f) {
            mc.itemModelResolver.updateForTopItem(itemState, stackFor(anim.item), ItemDisplayContext.FIXED, level, null, 0)
            val poseStack: PoseStack = context.poseStack()
            poseStack.pushPose()
            poseStack.translate(ox, oy + centerY, oz)
            poseStack.mulPose(Quaternionf().rotateY(spin))
            poseStack.scale(itemScale, itemScale, itemScale)
            itemState.submit(poseStack, context.submitNodeCollector(), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0)
            poseStack.popPose()
        }

        // The item's name above it, once it has popped up.
        val nameAlpha = ((t - 0.45f) / 0.35f).coerceIn(0f, 1f) * smooth(fade)
        if (nameAlpha > 0.02f) {
            val text = Component.literal(anim.item).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD).visualOrderText
            val font = mc.font
            val width = font.width(text)
            val textScale = 0.03f * size
            val poseStack = context.poseStack()
            poseStack.pushPose()
            poseStack.translate(ox, oy + centerY + 1.15f * size, oz)
            poseStack.mulPose(camera.orientation)
            poseStack.scale(textScale, -textScale, textScale)
            val color = ARGB.color((nameAlpha * 255f).toInt().coerceIn(4, 255), 0xFFFFFF)
            context.submitNodeCollector().submitText(poseStack, -width / 2f, -4f, text, true, Font.DisplayMode.NORMAL, FULL_BRIGHT, color, 0, 0)
            poseStack.popPose()
        }
    }

    /**
     * Everything that glows: the flash and shock wave of the pop, a beam of light into the sky, the
     * halo and rays behind the item, sparkles spiralling around it and a ring on the ground.
     */
    private fun drawLight(c: AuraCanvas, t: Float, cy: Float, size: Float, light: Float, right: Vector3f, up: Vector3f) {
        if (light <= 0f) return

        // The pop: a bright flash at the item and a wave running out across the ground.
        val burst = (t / 0.9f).coerceIn(0f, 1f)
        if (burst < 1f) {
            val k = easeOutCubic(burst)
            c.sprite(0f, cy, 0f, (1.5f + 6.5f * k) * size, GOLD_PALE, (1f - burst) * 0.95f)
            c.flatArc(0.06f, 0.4f + 5.5f * k * size, 0.5f * size * (1f - burst) + 0.05f, GOLD, (1f - burst) * 0.9f, segments = 64)
            c.ring(cy, 0.3f + 3.2f * k * size, 0.22f * size * (1f - burst) + 0.03f, GOLD_PALE, (1f - burst) * 0.8f, segments = 48)
        }

        // A beam of light up into the sky, like a loot beam, brightest at the item.
        c.beginRibbon()
        val beamTop = 14f * size
        for (i in 0..12) {
            val f = i / 12f
            val y = beamTop * f
            val near = 1f - f
            c.point(0f, y, 0f, (0.55f + 0.25f * sin(t * 3f + f * 6f)) * size * (0.4f + 0.6f * near), GOLD, light * 0.55f * near * near)
        }
        c.endRibbon()

        // The halo behind the item, breathing slowly.
        val breathe = 0.5f + 0.5f * sin(t * 2.6f)
        c.sprite(0f, cy, 0f, (3.4f + 0.5f * breathe) * size, GOLD_DEEP, light * 0.55f)
        c.sprite(0f, cy, 0f, (1.9f + 0.3f * breathe) * size, GOLD, light * 0.6f)

        // God rays: spokes of light in the plane facing the camera, turning slowly and flickering.
        val rays = 14
        for (i in 0 until rays) {
            val angle = i * (2f * PI.toFloat() / rays) + t * 0.35f
            val flicker = 0.6f + 0.4f * sin(t * 3.1f + i * 1.7f)
            val length = (1.6f + 1.1f * flicker) * size * easeOutCubic((t / 0.7f).coerceIn(0f, 1f))
            val dx = (right.x * cos(angle) + up.x * sin(angle))
            val dy = (right.y * cos(angle) + up.y * sin(angle))
            val dz = (right.z * cos(angle) + up.z * sin(angle))
            c.beginRibbon()
            for (s in 0..6) {
                val f = s / 6f
                val r = 0.25f * size + length * f
                c.point(dx * r, cy + dy * r, dz * r, 0.28f * size * (1f - f) + 0.01f, if (i % 2 == 0) GOLD else GOLD_PALE, light * 0.5f * flicker * (1f - f))
            }
            c.endRibbon()
        }

        // Sparkles spiralling up around the item, each born again at the bottom when it fades out.
        val sparkles = 26
        for (i in 0 until sparkles) {
            val seed = AuraEffects.hash(i * 31 + 7)
            val period = 1.8f + 1.2f * seed
            val life = fract(t / period + AuraEffects.hash(i * 17 + 3))
            val angle = i * 2.39996f + t * (0.9f + 0.6f * seed)
            val radius = (0.9f + 0.7f * AuraEffects.hash(i * 13 + 1)) * size * (1f - 0.3f * life)
            val y = cy - 1.1f * size + 2.8f * size * life
            val twinkle = sin(life * PI.toFloat())
            val rgb = if (i % 3 == 0) GOLD_PALE else if (i % 3 == 1) GOLD else AMBER
            c.sprite(cos(angle) * radius, y, sin(angle) * radius, (0.22f + 0.2f * seed) * size, rgb, light * twinkle, sparkle = true, spin = t * 2f + i)
        }

        // A ring of light on the ground under the item, and a comet running around it.
        c.flatArc(0.05f, 1.4f * size, 0.18f * size, GOLD, light * 0.55f, segments = 64)
        c.flatArc(0.06f, 1.4f * size, 0.3f * size, GOLD_PALE, light * 0.8f, start = t * 2.2f, sweep = 2.4f, segments = 32, fade = true)
        c.sprite(0f, 0.1f, 0f, 3.2f * size, GOLD_DEEP, light * 0.25f)
    }

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

package net.alpaka.addons.features.garden

import net.alpaka.addons.client.gui.TextWidths
import net.alpaka.addons.utils.ItemData
import com.mojang.blaze3d.platform.InputConstants
import net.alpaka.addons.client.AlpakaKeyBinds
import net.alpaka.addons.client.AlpakaKeyCategory
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.utils.SkyblockUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.tags.ItemTags
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemStack
import org.lwjgl.glfw.GLFW
import java.util.Locale

/**
 * Alignment Helper: the Garden's farms are built for one exact yaw and pitch, and this helps set
 * them before farming. It switches itself on when you stand still on the ground with a farming tool
 * (not a vacuum) and off as soon as you walk, break crops or put the tool away. While it is active
 * the mouse turns slower and the angles are shown under the crosshair; while farming (a farming tool in hand and the attack key held) the mouse can be locked
 * so a bump does not knock the angle off. Both stay off on the Barn plot. Only the camera is
 * touched; nothing is sent to the server.
 */
object AlignmentHelper {

    /** Switches the helper the other way until you walk again. Unbound by default. */
    @JvmField
    var KEY: KeyMapping? = null

    /** Below the crosshair, like the Ragnarock countdown, so the eye does not have to leave it. */
    private const val BELOW_CROSSHAIR = 10
    private const val LINE_HEIGHT = 10
    private const val TEXT_COLOR = 0xFFFFFFFF.toInt()
    private const val LABEL_COLOR = 0xFFAAAAAA.toInt()

    /** Movement smaller than this per tick counts as standing still; a crop's knockback is not walking. */
    private const val STILL_EPSILON_SQ = 1.0e-6

    /** Set by the key: forced on (true) or off (false) until the player walks; null follows the rules. */
    private var override: Boolean? = null
    private var lastX = Double.NaN
    private var lastY = Double.NaN
    private var lastZ = Double.NaN
    private var stillTicks = 0

    /** Worked out once per tick, read every frame by the mouse handler and the HUD. */
    private var active = false
    private var locked = false

    @JvmStatic
    fun register() {
        KEY = AlpakaKeyBinds.register(KeyMapping("key.alpaka.alignment_helper",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, AlpakaKeyCategory.CATEGORY))
        ClientTickEvents.END_CLIENT_TICK.register { tick(it) }
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> reset() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> reset() }
    }

    @JvmStatic
    fun reset() {
        override = null
        lastX = Double.NaN
        stillTicks = 0
        active = false
        locked = false
    }

    private fun tick(mc: Minecraft) {
        val cfg = AlpakaConfig.instance
        val key = KEY
        // Presses are drained even while switched off, so an old press does not toggle it later.
        var pressed = false
        while (key != null && key.consumeClick()) pressed = true

        val player = mc.player
        if (!cfg.alignmentHelperEnabled || player == null || !SkyblockUtils.isOnGarden()) {
            reset()
            return
        }
        val x = player.x
        val y = player.y
        val z = player.z
        val dx = x - lastX
        val dy = y - lastY
        val dz = z - lastZ
        // Hovering in flight keeps the position too, but only standing on the ground counts.
        val moved = lastX.isNaN() || dx * dx + dy * dy + dz * dz > STILL_EPSILON_SQ
        stillTicks = if (moved || !player.onGround() || player.abilities.flying) 0 else stillTicks + 1
        if (moved) override = null
        lastX = x
        lastY = y
        lastZ = z

        // Nobody farms on the Barn; standing at the visitors or the desk must not slow the mouse.
        if (GardenPlots.index(x, z) == GardenPlots.BARN) {
            active = false
            locked = false
            override = null
            return
        }
        val farmingTool = isFarmingTool(player.mainHandItem)
        val attacking = mc.options.keyAttack.isDown
        locked = cfg.alignmentHelperLockMouse && farmingTool && mc.gui.screen() == null && attacking
        // A vacuum or any other item is not a farming tool, so collecting pests never slows the mouse.
        val auto = farmingTool && !attacking && stillTicks >= Math.round(cfg.alignmentHelperStillSeconds * 20f)
        if (pressed) override = !(override ?: auto)
        active = !locked && (override ?: auto)
    }

    /**
     * What the mouse's movement since the last turn ([dx], [dy] pixels over [seconds]) is multiplied
     * by: 0 while locked, the reduced sensitivity while the helper is active, 1 otherwise. Called
     * from MouseHandler.turnPlayer.
     */
    @JvmStatic
    fun mouseFactor(dx: Double, dy: Double, seconds: Double): Double {
        if (locked) return 0.0
        if (!active) return 1.0
        // Clamped to the slider's range, for a value saved when the slider still went higher.
        val sensitivity = AlpakaConfig.instance.alignmentHelperSensitivity.coerceIn(0.01f, 0.5f).toDouble()
        if (!AlpakaConfig.instance.alignmentHelperFineOnly) return sensitivity
        return fineFactor(sensitivity, if (seconds > 0.0) Math.hypot(dx, dy) / seconds else 0.0)
    }

    /** Mouse speeds in pixels per second: up to SLOW fully slowed down, from FAST not at all. */
    private const val SLOW_SPEED = 400.0
    private const val FAST_SPEED = 2000.0

    /**
     * Fine Moves Only: a slow, small movement is the player lining up and gets the reduced
     * [sensitivity]; a quick flick is the player turning around and passes at full speed, with a
     * smooth blend in between.
     */
    @JvmStatic
    fun fineFactor(sensitivity: Double, speed: Double): Double {
        val t = ((speed - SLOW_SPEED) / (FAST_SPEED - SLOW_SPEED)).coerceIn(0.0, 1.0)
        return sensitivity + (1.0 - sensitivity) * t
    }

    @JvmStatic
    fun render(graphics: GuiGraphicsExtractor) {
        if (!active || !AlpakaConfig.instance.alignmentHelperShowAngles) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (mc.gui.hud.isHidden || mc.level == null) return
        if (mc.gui.screen() != null && mc.gui.screen() !is ChatScreen) return

        val cx = graphics.guiWidth() / 2
        val top = graphics.guiHeight() / 2 + BELOW_CROSSHAIR
        // Formatted again only when the angle moved: String.format every frame for an angle that
        // mostly stands still while farming.
        val yaw = player.yRot
        if (yaw.toRawBits() != shownYawBits || shownYaw == null) {
            shownYawBits = yaw.toRawBits()
            shownYaw = formatYaw(yaw)
        }
        val pitch = player.xRot
        if (pitch.toRawBits() != shownPitchBits || shownPitch == null) {
            shownPitchBits = pitch.toRawBits()
            shownPitch = formatPitch(pitch)
        }
        line(graphics, mc, "Yaw ", shownYaw!!, cx, top)
        line(graphics, mc, "Pitch ", shownPitch!!, cx, top + LINE_HEIGHT)
    }

    private var shownYawBits = 0
    private var shownYaw: String? = null
    private var shownPitchBits = 0
    private var shownPitch: String? = null

    private fun line(graphics: GuiGraphicsExtractor, mc: Minecraft, label: String, value: String, cx: Int, y: Int) {
        // Kept widths: the labels never change and the angles only when the view moves.
        val labelWidth = TextWidths.of(mc.font, label)
        val x = cx - (labelWidth + TextWidths.of(mc.font, value)) / 2
        graphics.text(mc.font, label, x, y, LABEL_COLOR)
        graphics.text(mc.font, value, x + labelWidth, y, TEXT_COLOR)
    }

    /** The yaw as F3 shows it, wrapped to -180..180, to five decimals. */
    @JvmStatic
    fun formatYaw(yaw: Float): String = String.format(Locale.ROOT, "%.5f", Mth.wrapDegrees(yaw.toDouble()))

    /** The pitch to two decimals. */
    @JvmStatic
    fun formatPitch(pitch: Float): String = String.format(Locale.ROOT, "%.2f", pitch.toDouble())

    @JvmStatic
    fun isFarmingTool(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        if (stack.`is`(ItemTags.HOES)) return true
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return false
        return isFarmingToolId(ItemData.view(data).getStringOr("id", ""))
    }

    /**
     * Whether a SkyBlock item id is one of the Garden's farming tools: the Theoretical Hoes, the
     * dicers, the crop-specific tools and the gardening hoe and axe.
     */
    @JvmStatic
    fun isFarmingToolId(id: String): Boolean {
        if (id.isEmpty()) return false
        return id.startsWith("THEORETICAL_HOE") || id.startsWith("MELON_DICER") || id.startsWith("PUMPKIN_DICER")
                || id.contains("GARDENING_HOE") || id.contains("GARDENING_AXE")
                || id in SINGLE_TOOLS
    }

    private val SINGLE_TOOLS = setOf("COCO_CHOPPER", "CACTUS_KNIFE", "FUNGI_CUTTER")
}

package net.alpaka.addons.features.garden

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
 * them before farming. While it is active the mouse turns slower and the angles are shown under the
 * crosshair; while farming (a farming tool in hand and the attack key held) the mouse can be locked
 * so a bump does not knock the angle off. Only the camera is touched; nothing is sent to the server.
 */
object AlignmentHelper {

    /** The Activation choices, in the order of [AlpakaConfig.alignmentHelperMode]. */
    const val MODE_STANDING_STILL = 0
    const val MODE_KEY = 1
    const val MODE_FARMING_TOOL = 2

    @JvmField
    val MODE_NAMES = arrayOf("Standing Still", "Key", "Holding Farming Tool")

    /** Toggles the helper while Activation is Key. Unbound by default. */
    @JvmField
    var KEY: KeyMapping? = null

    /** Below the crosshair, like the Ragnarock countdown, so the eye does not have to leave it. */
    private const val BELOW_CROSSHAIR = 10
    private const val LINE_HEIGHT = 10
    private const val TEXT_COLOR = 0xFFFFFFFF.toInt()
    private const val LABEL_COLOR = 0xFFAAAAAA.toInt()

    /** Movement smaller than this per tick counts as standing still; a crop's knockback is not walking. */
    private const val STILL_EPSILON_SQ = 1.0e-6

    private var toggledOn = false
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
        toggledOn = false
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
        if (pressed && cfg.alignmentHelperMode == MODE_KEY) toggledOn = !toggledOn

        val x = player.x
        val y = player.y
        val z = player.z
        val dx = x - lastX
        val dy = y - lastY
        val dz = z - lastZ
        stillTicks = if (lastX.isNaN() || dx * dx + dy * dy + dz * dz > STILL_EPSILON_SQ) 0 else stillTicks + 1
        lastX = x
        lastY = y
        lastZ = z

        val farmingTool = isFarmingTool(player.mainHandItem)
        val onBarn = GardenPlots.index(x, z) == GardenPlots.BARN
        locked = cfg.alignmentHelperLockMouse && farmingTool && !onBarn && mc.gui.screen() == null
                && mc.options.keyAttack.isDown
        active = !locked && when (cfg.alignmentHelperMode) {
            MODE_KEY -> toggledOn
            MODE_FARMING_TOOL -> farmingTool && !onBarn
            else -> stillTicks >= Math.round(cfg.alignmentHelperStillSeconds * 20f)
        }
    }

    /**
     * What the mouse's movement since the last turn is multiplied by: 0 while locked, the reduced
     * sensitivity while the helper is active, 1 otherwise. Called from MouseHandler.turnPlayer.
     */
    @JvmStatic
    fun mouseFactor(): Double {
        if (locked) return 0.0
        if (active) return AlpakaConfig.instance.alignmentHelperSensitivity.toDouble()
        return 1.0
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
        line(graphics, mc, "Yaw ", formatYaw(player.yRot), cx, top)
        line(graphics, mc, "Pitch ", formatPitch(player.xRot), cx, top + LINE_HEIGHT)
    }

    private fun line(graphics: GuiGraphicsExtractor, mc: Minecraft, label: String, value: String, cx: Int, y: Int) {
        val labelWidth = mc.font.width(label)
        val x = cx - (labelWidth + mc.font.width(value)) / 2
        graphics.text(mc.font, label, x, y, LABEL_COLOR)
        graphics.text(mc.font, value, x + labelWidth, y, TEXT_COLOR)
    }

    /** The yaw as F3 shows it, wrapped to -180..180, to five decimals. */
    @JvmStatic
    fun formatYaw(yaw: Float): String = String.format(Locale.ROOT, "%.5f", Mth.wrapDegrees(yaw.toDouble()))

    /** The pitch to two decimals. */
    @JvmStatic
    fun formatPitch(pitch: Float): String = String.format(Locale.ROOT, "%.2f", pitch.toDouble())

    private fun isFarmingTool(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        if (stack.`is`(ItemTags.HOES)) return true
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return false
        return isFarmingToolId(data.copyTag().getStringOr("id", ""))
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

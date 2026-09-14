package net.alpaka.addons.features.party

import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.client.hud.HudBounds
import net.alpaka.addons.client.hud.HudEditorScreen
import net.alpaka.addons.client.hud.HudElement
import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component

/**
 * The party invite prompt on the HUD, and its handle in the HUD editor.
 *
 * Drawn in the same glass style as the mod's notifications, with the inviter's name, the two key
 * hints and a thin bar that runs down as the invite's sixty seconds pass. It sits centred near the
 * top of the screen until moved in the editor; the editor shows a sample invite so there is
 * something to drag even while nobody is inviting.
 */
object PartyInvitePromptElement : HudElement {

    /** Stored X meaning "keep it horizontally centred", which is where it starts. */
    const val CENTERED: Int = Int.MIN_VALUE
    private const val DEFAULT_Y = 40

    private const val WIDTH = 176
    private const val PAD = 6
    private const val LINE = 10
    private const val STRIPE = 2
    private const val BAR = 2
    private const val HEIGHT = PAD * 2 + LINE * 3 + BAR

    private const val GLASS_TOP = 0xB2101418.toInt()
    private const val GLASS_BOTTOM = 0x59101418
    private const val EDGE = 0x3CFFFFFF
    private const val SHEEN = 0x26FFFFFF
    private const val BAR_TRACK = 0x40FFFFFF

    override val id: String = "party_invite_prompt"
    override val name: String = "Party Invite Prompt"

    override val isFeatureEnabled: Boolean
        get() = AlpakaConfig.instance.partyInvitePromptEnabled

    override var anchorX: Int
        get() = resolvedX()
        set(value) { AlpakaConfig.instance.partyInvitePromptX = value }

    override var anchorY: Int
        get() = AlpakaConfig.instance.partyInvitePromptY
        set(value) { AlpakaConfig.instance.partyInvitePromptY = value }

    private fun resolvedX(): Int {
        val stored = AlpakaConfig.instance.partyInvitePromptX
        if (stored != CENTERED) return stored
        return (Minecraft.getInstance().window.guiScaledWidth - WIDTH) / 2
    }

    override fun bounds(): HudBounds {
        val x = resolvedX()
        val y = AlpakaConfig.instance.partyInvitePromptY
        return HudBounds(x, y, x + WIDTH, y + HEIGHT)
    }

    /** Fixed size: the prompt is text at the GUI scale, like the chat. */
    override fun adjustScale(notches: Double) {}

    override fun reset() {
        AlpakaConfig.instance.partyInvitePromptX = CENTERED
        AlpakaConfig.instance.partyInvitePromptY = DEFAULT_Y
    }

    override fun render(graphics: GuiGraphicsExtractor) {
        val inEditor = Minecraft.getInstance().screen is HudEditorScreen
        val inviter = if (inEditor) "Steve" else PartyInviteFeature.inviterName() ?: return
        val remaining = if (inEditor) 0.7f else PartyInviteFeature.remainingFraction()
        draw(graphics, resolvedX(), AlpakaConfig.instance.partyInvitePromptY, inviter, remaining)
    }

    override fun scaleValue(): Float = 1.0f

    override fun scaleLabel(): String = "1x"

    /** Called every frame from the HUD hook. */
    @JvmStatic
    fun renderHud(graphics: GuiGraphicsExtractor) {
        if (!isFeatureEnabled || !PartyInviteFeature.isShowing()) return
        val mc = Minecraft.getInstance()
        if (mc.options.hideGui || mc.level == null) return
        val inviter = PartyInviteFeature.inviterName() ?: return
        draw(graphics, resolvedX(), AlpakaConfig.instance.partyInvitePromptY, inviter, PartyInviteFeature.remainingFraction())
    }

    private fun draw(graphics: GuiGraphicsExtractor, x: Int, y: Int, inviter: String, remaining: Float) {
        val font = Minecraft.getInstance().font
        val accent = ModernGuiUtils.getAccentColor()

        graphics.fillGradient(x, y, x + WIDTH, y + HEIGHT, GLASS_TOP, GLASS_BOTTOM)
        ModernGuiUtils.drawRect(graphics, x, y, WIDTH, 1, SHEEN)
        ModernGuiUtils.drawOutline(graphics, x, y, WIDTH, HEIGHT, EDGE)
        graphics.fillGradient(x, y, x + STRIPE, y + HEIGHT, accent, withAlpha(accent, 0x40))

        val textX = x + STRIPE + PAD
        graphics.text(font, Component.literal("Party Invite"), textX, y + PAD, accent)
        graphics.text(
            font, Component.literal("$inviter invited you to their party"),
            textX, y + PAD + LINE, ModernGuiUtils.COLOR_TEXT_PRIMARY
        )
        val hint = Component.literal("[Y]").withStyle(ChatFormatting.GREEN)
            .append(Component.literal(" Join    ").withStyle(ChatFormatting.WHITE))
            .append(Component.literal("[N]").withStyle(ChatFormatting.RED))
            .append(Component.literal(" Decline").withStyle(ChatFormatting.WHITE))
        graphics.text(font, hint, textX, y + PAD + LINE * 2, ModernGuiUtils.COLOR_TEXT_PRIMARY)

        // The time left, as a bar along the bottom edge that shrinks towards the stripe.
        val barY = y + HEIGHT - BAR
        val barWidth = WIDTH - STRIPE
        graphics.fill(x + STRIPE, barY, x + WIDTH, y + HEIGHT, BAR_TRACK)
        val filled = Math.round(barWidth * remaining.coerceIn(0.0f, 1.0f))
        if (filled > 0) {
            graphics.fill(x + STRIPE, barY, x + STRIPE + filled, y + HEIGHT, accent)
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (alpha shl 24) or (color and 0xFFFFFF)
}

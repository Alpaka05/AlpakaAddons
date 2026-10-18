package net.alpaka.addons.features.inventoryhud

/** The inventory HUD's visibility choice, stored as an index in `AlpakaConfig.inventoryHudVisibility`. */
object InventoryHudVisibility {
    const val ALWAYS = 0
    const val ITEM_CHANGE = 1
    const val KEY_PRESS = 2

    @JvmField
    val NAMES = arrayOf("Always Visible", "On Item Change", "On Key Press")

    /**
     * The choice the two old switches amount to. Always Visible won over Show on Item Change before
     * too, and with both off only the key showed the panel.
     */
    @JvmStatic
    fun fromLegacy(alwaysVisible: Boolean?, showOnItemChange: Boolean?): Int = when {
        alwaysVisible == true -> ALWAYS
        showOnItemChange == true -> ITEM_CHANGE
        else -> KEY_PRESS
    }
}

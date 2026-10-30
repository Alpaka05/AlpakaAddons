package net.alpaka.addons.features.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlignmentHelperTest {

    @Test
    fun `yaw is wrapped like F3 and shown to five decimals`() {
        assertEquals("90.00000", AlignmentHelper.formatYaw(90f))
        assertEquals("-90.00000", AlignmentHelper.formatYaw(270f))
        assertEquals("-179.50000", AlignmentHelper.formatYaw(540.5f))
    }

    @Test
    fun `pitch is shown to two decimals`() {
        assertEquals("2.50", AlignmentHelper.formatPitch(2.5f))
        assertEquals("-58.00", AlignmentHelper.formatPitch(-58f))
    }

    @Test
    fun `farming tools are recognized by their SkyBlock id`() {
        for (id in listOf("THEORETICAL_HOE_WHEAT_3", "THEORETICAL_HOE_WARTS_1", "MELON_DICER_3", "PUMPKIN_DICER",
                "COCO_CHOPPER", "CACTUS_KNIFE", "FUNGI_CUTTER", "BASIC_GARDENING_HOE", "ADVANCED_GARDENING_AXE")) {
            assertTrue(AlignmentHelper.isFarmingToolId(id), id)
        }
        for (id in listOf("", "ASPECT_OF_THE_END", "PEST_VACUUM", "MELON")) {
            assertFalse(AlignmentHelper.isFarmingToolId(id), id)
        }
    }
}

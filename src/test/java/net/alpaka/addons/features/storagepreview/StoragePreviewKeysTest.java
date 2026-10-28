package net.alpaka.addons.features.storagepreview;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StoragePreviewKeysTest {

    @Test
    void openPagesAreKeyedByTheirNumber() {
        assertEquals("ec2", StoragePreviewFeature.pageKey("Ender Chest (2/9)"));
        assertEquals("bp3", StoragePreviewFeature.pageKey("Jumbo Backpack (Slot #3)"));
        assertEquals("bp14", StoragePreviewFeature.pageKey("Greater Backpack ✦ (Slot #14)"));
        assertNull(StoragePreviewFeature.pageKey("Storage"));
        assertNull(StoragePreviewFeature.pageKey("Large Chest"));
    }

    @Test
    void storageIconsPointAtTheSamePages() {
        assertEquals("ec2", StoragePreviewFeature.iconKey("Ender Chest Page 2"));
        assertEquals("bp3", StoragePreviewFeature.iconKey("Backpack Slot 3"));
        assertNull(StoragePreviewFeature.iconKey("Empty Backpack Slot 3"));
        assertNull(StoragePreviewFeature.iconKey("Locked Page"));
    }
}

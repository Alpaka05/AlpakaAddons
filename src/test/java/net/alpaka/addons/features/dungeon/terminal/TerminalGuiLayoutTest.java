package net.alpaka.addons.features.dungeon.terminal;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The custom terminal window's grid: each cell is exactly one play slot, at a place that follows
 * from the slot index alone, and the mouse maps back to that slot and to nothing else
 * (compliance.md C3, C4).
 */
class TerminalGuiLayoutTest {

    private static final float[] SCALES = {1.0f, 1.5f, 2.0f, 2.5f, 3.0f};
    /** GUI sizes: the tour's 854x480 window at Auto, the most common real one, a big one, and very small ones. */
    private static final int[][] SCREENS = {{427, 240}, {480, 270}, {960, 540}, {1920, 1080}, {320, 180}, {200, 120}};

    private static List<TerminalGuiLayout> everyLayout() {
        List<TerminalGuiLayout> all = new ArrayList<>();
        for (TerminalKind kind : TerminalKind.values()) {
            for (float scale : SCALES) {
                for (int[] screen : SCREENS) {
                    all.add(TerminalGuiLayout.of(kind, scale, screen[0], screen[1], 0));
                    all.add(TerminalGuiLayout.of(kind, scale, screen[0], screen[1], 260));
                }
            }
        }
        return all;
    }

    @Test
    void everyPlaySlotHasACellThatMapsBackToIt() {
        for (TerminalGuiLayout l : everyLayout()) {
            for (int slot : l.kind().playSlots()) {
                TerminalGuiLayout.Rect r = l.cell(slot);
                assertNotNull(r, l.kind() + " slot " + slot);
                assertEquals(l.cell(), r.w());
                assertEquals(l.cell(), r.h());
                assertEquals(slot, l.slotAt(r.x() + r.w() / 2.0, r.y() + r.h() / 2.0), l.kind() + " centre of " + slot);
                // Every corner inside the cell, too.
                assertEquals(slot, l.slotAt(r.x(), r.y()), l.kind() + " top left of " + slot);
                assertEquals(slot, l.slotAt(r.x() + r.w() - 0.01, r.y() + r.h() - 0.01), l.kind() + " bottom right of " + slot);
                assertEquals(slot, l.slotAt(r.x(), r.y() + r.h() - 0.01));
                assertEquals(slot, l.slotAt(r.x() + r.w() - 0.01, r.y()));
            }
        }
    }

    @Test
    void cellsNeverOverlapAndStayInTheGridAndPanel() {
        for (TerminalGuiLayout l : everyLayout()) {
            int[] slots = l.kind().playSlots();
            TerminalGuiLayout.Rect grid = l.grid();
            TerminalGuiLayout.Rect panel = l.panel();
            for (int i = 0; i < slots.length; i++) {
                TerminalGuiLayout.Rect a = l.cell(slots[i]);
                assertTrue(a.x() >= grid.x() && a.y() >= grid.y()
                        && a.x() + a.w() <= grid.x() + grid.w() && a.y() + a.h() <= grid.y() + grid.h(), "in the grid");
                for (int j = i + 1; j < slots.length; j++) {
                    assertFalse(a.overlaps(l.cell(slots[j])), l.kind() + ": " + slots[i] + " and " + slots[j] + " overlap");
                }
            }
            assertTrue(grid.x() >= panel.x() + TerminalGuiLayout.PAD - 1 && grid.x() + grid.w() <= panel.x() + panel.w());
            assertTrue(grid.y() >= panel.y() + TerminalGuiLayout.HEADER_H, "below the header");
            assertTrue(grid.y() + grid.h() <= panel.y() + panel.h());
        }
    }

    @Test
    void theGridFollowsTheSlotIndex() {
        for (TerminalGuiLayout l : everyLayout()) {
            for (int slot : l.kind().playSlots()) {
                TerminalGuiLayout.Rect r = l.cell(slot);
                if (l.kind().isPlaySlot(slot + 1) && (slot + 1) % 9 != 0) {
                    assertEquals(r.x() + l.pitch(), l.cell(slot + 1).x(), "next column");
                    assertEquals(r.y(), l.cell(slot + 1).y());
                }
                if (l.kind().isPlaySlot(slot + 9)) {
                    assertEquals(r.y() + l.pitch(), l.cell(slot + 9).y(), "next row");
                    assertEquals(r.x(), l.cell(slot + 9).x());
                }
            }
        }
    }

    @Test
    void gapsAndTheRestHitNothing() {
        for (TerminalGuiLayout l : everyLayout()) {
            TerminalGuiLayout.Rect grid = l.grid();
            assertEquals(-1, l.slotAt(grid.x() - 1, grid.y() + 1), "left of the grid");
            assertEquals(-1, l.slotAt(grid.x() + 1, grid.y() - 1), "above the grid");
            assertEquals(-1, l.slotAt(grid.x() + grid.w() + 1, grid.y() + 1), "right of the grid");
            assertEquals(-1, l.slotAt(grid.x() + 1, grid.y() + grid.h() + 1), "below the grid");
            TerminalGuiLayout.Rect header = l.header();
            assertEquals(-1, l.slotAt(header.x() + header.w() / 2.0, header.y() + header.h() / 2.0), "the header");
            for (int slot : l.kind().playSlots()) {
                TerminalGuiLayout.Rect r = l.cell(slot);
                // Just right of and just below the cell lies the gap.
                assertEquals(-1, l.slotAt(r.x() + r.w() + l.gap() / 2.0, r.y() + r.h() / 2.0), l.kind() + " gap right of " + slot);
                assertEquals(-1, l.slotAt(r.x() + r.w() / 2.0, r.y() + r.h() + l.gap() / 2.0), l.kind() + " gap below " + slot);
            }
        }
    }

    @Test
    void everyPointMapsToAPlaySlotOrNothing() {
        for (TerminalKind kind : TerminalKind.values()) {
            TerminalGuiLayout l = TerminalGuiLayout.of(kind, 1.5f, 480, 270, 0);
            TerminalGuiLayout.Rect panel = l.panel();
            java.util.Set<Integer> reached = new java.util.HashSet<>();
            for (double y = panel.y() - 5; y < panel.y() + panel.h() + 5; y += 0.5) {
                for (double x = panel.x() - 5; x < panel.x() + panel.w() + 5; x += 0.5) {
                    int slot = l.slotAt(x, y);
                    if (slot < 0) continue;
                    assertTrue(kind.isPlaySlot(slot), kind + ": " + slot + " is no play slot");
                    assertTrue(l.cell(slot).contains(x, y), kind + ": " + slot + " hit outside its cell");
                    reached.add(slot);
                }
            }
            assertEquals(kind.playSlots().length, reached.size(), kind + ": every play slot can be clicked");
        }
    }

    @Test
    void clickTheButtonOnTimeLeavesItsFillerColumnEmpty() {
        TerminalGuiLayout l = TerminalGuiLayout.of(TerminalKind.MELODY, 1.5f, 960, 540, 0);
        // Column 6 is filler in every row, and slots 7 and 43 (column 7 of rows 0 and 4) too.
        for (int row = 0; row <= 4; row++) {
            int slot = row * 9 + 6;
            assertNull(l.cell(slot));
            TerminalGuiLayout.Rect left = l.cell(row * 9 + 5);
            assertEquals(-1, l.slotAt(left.x() + l.pitch() + l.cell() / 2.0, left.y() + l.cell() / 2.0), "column 6, row " + row);
        }
        assertNull(l.cell(7));
        assertNull(l.cell(43));
        TerminalGuiLayout.Rect button = l.cell(16);
        assertEquals(-1, l.slotAt(button.x() + l.cell() / 2.0, button.y() - l.pitch() + l.cell() / 2.0), "slot 7");
        // The buttons sit two columns right of column 5.
        assertEquals(l.cell(14).x() + 2 * l.pitch(), l.cell(16).x());
        assertEquals(l.cell(34).y(), l.cell(32).y());
    }

    @Test
    void notAPlaySlotHasNoCell() {
        assertNull(TerminalGuiLayout.of(TerminalKind.PANES, 1.5f, 480, 270, 0).cell(10));
        assertNull(TerminalGuiLayout.of(TerminalKind.RUBIX, 1.5f, 480, 270, 0).cell(11));
        assertNull(TerminalGuiLayout.of(TerminalKind.NUMBERS, 1.5f, 480, 270, 0).cell(29));
        assertNull(TerminalGuiLayout.of(TerminalKind.SELECT_ALL, 1.5f, 480, 270, 0).cell(0));
        assertNull(TerminalGuiLayout.of(TerminalKind.PANES, 1.5f, 480, 270, 0).cell(-1));
        assertNull(TerminalGuiLayout.of(TerminalKind.PANES, 1.5f, 480, 270, 0).cell(54));
    }

    @Test
    void theCellSizeFollowsTheSetting() {
        assertEquals(18, TerminalGuiLayout.pitchFor(1.0f));
        assertEquals(27, TerminalGuiLayout.pitchFor(1.5f));
        assertEquals(54, TerminalGuiLayout.pitchFor(3.0f));
        // Out of range or broken values are clamped like the setting.
        assertEquals(54, TerminalGuiLayout.pitchFor(7.0f));
        assertEquals(18, TerminalGuiLayout.pitchFor(0.2f));
        assertEquals(27, TerminalGuiLayout.pitchFor(Float.NaN));
        assertEquals(2, TerminalGuiLayout.gapFor(18));
        assertEquals(3, TerminalGuiLayout.gapFor(27));
        assertEquals(6, TerminalGuiLayout.gapFor(54));
        // Room to spare: the size asked for.
        for (TerminalKind kind : TerminalKind.values()) {
            for (float scale : SCALES) {
                assertEquals(TerminalGuiLayout.pitchFor(scale), TerminalGuiLayout.of(kind, scale, 1920, 1080, 0).pitch());
            }
        }
    }

    @Test
    void itShrinksToFitTheScreen() {
        for (TerminalKind kind : TerminalKind.values()) {
            for (float scale : SCALES) {
                for (int[] screen : new int[][]{{427, 240}, {480, 270}, {320, 180}}) {
                    TerminalGuiLayout l = TerminalGuiLayout.of(kind, scale, screen[0], screen[1], 0);
                    TerminalGuiLayout.Rect panel = l.panel();
                    String where = kind + " at " + scale + " on " + screen[0] + "x" + screen[1];
                    assertTrue(panel.x() >= TerminalGuiLayout.MARGIN && panel.y() >= TerminalGuiLayout.MARGIN, where);
                    assertTrue(panel.x() + panel.w() <= screen[0] - TerminalGuiLayout.MARGIN, where);
                    assertTrue(panel.y() + panel.h() <= screen[1] - TerminalGuiLayout.MARGIN, where);
                    assertTrue(l.pitch() <= TerminalGuiLayout.pitchFor(scale), where);
                }
            }
        }
        // The tour's window fits Select All's four rows at 3x only smaller than asked.
        assertTrue(TerminalGuiLayout.of(TerminalKind.SELECT_ALL, 3.0f, 427, 240, 0).pitch() < 54);
        // On a screen too small for anything it stops at the smallest pitch rather than vanish.
        assertEquals(TerminalGuiLayout.MIN_PITCH, TerminalGuiLayout.of(TerminalKind.MELODY, 3.0f, 60, 60, 0).pitch());
    }

    @Test
    void theHeaderWidensThePanelNotTheGrid() {
        TerminalGuiLayout narrow = TerminalGuiLayout.of(TerminalKind.RUBIX, 1.5f, 480, 270, 0);
        TerminalGuiLayout wide = TerminalGuiLayout.of(TerminalKind.RUBIX, 1.5f, 480, 270, 300);
        assertEquals(300, wide.panel().w());
        assertEquals(narrow.grid().w(), wide.grid().w());
        assertEquals(narrow.pitch(), wide.pitch());
        // Centred in the wider panel.
        TerminalGuiLayout.Rect p = wide.panel();
        TerminalGuiLayout.Rect g = wide.grid();
        assertTrue(Math.abs((g.x() - p.x()) - (p.x() + p.w() - g.x() - g.w())) <= 1);
        // Never wider than the screen allows.
        assertTrue(TerminalGuiLayout.of(TerminalKind.RUBIX, 1.5f, 200, 270, 1000).panel().w() <= 200 - 2 * TerminalGuiLayout.MARGIN);
    }

    @Test
    void theLayoutKnowsNothingOfTheSolution() {
        // Same inputs, same layout, whatever the terminal shows.
        assertEquals(TerminalGuiLayout.of(TerminalKind.NUMBERS, 1.5f, 480, 270, 200),
                TerminalGuiLayout.of(TerminalKind.NUMBERS, 1.5f, 480, 270, 200));
        assertNotEquals(TerminalGuiLayout.of(TerminalKind.NUMBERS, 1.5f, 480, 270, 200),
                TerminalGuiLayout.of(TerminalKind.NUMBERS, 2.0f, 480, 270, 200));
        // And it has no way to learn of it: nothing in it takes or holds a solution, session or item.
        for (Field field : TerminalGuiLayout.class.getDeclaredFields()) assertPure(field.getType());
        for (Method method : TerminalGuiLayout.class.getDeclaredMethods()) {
            for (Class<?> type : method.getParameterTypes()) assertPure(type);
            assertPure(method.getReturnType());
        }
    }

    private static void assertPure(Class<?> type) {
        assertFalse(type == TerminalSolution.class || type == TerminalSession.class || type == TerminalItem.class
                || type == TerminalSession.State.class || type.isArray() && type.getComponentType() == TerminalItem.class, type.getName());
    }
}

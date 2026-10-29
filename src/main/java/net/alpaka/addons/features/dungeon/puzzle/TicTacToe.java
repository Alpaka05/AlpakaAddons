package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.features.dungeon.SolverContext;
import net.alpaka.addons.features.dungeon.render.SolverColors;
import net.alpaka.addons.features.dungeon.render.SolverDraw;
import net.alpaka.addons.features.dungeon.room.RoomFrame;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Tic Tac Toe: the player ties a game against the computer on a 3 x 3 board on the room's wall by
 * pressing the button of a cell. Each mark is a map in an item frame; the color in the middle of
 * the map tells X (the computer, red) from O (the player, white).
 *
 * Every tick the board is read again from the maps, so it is right after a rejoin, after another
 * player's move and after an Architect's First Draft. When it is the player's move, the best reply
 * ({@link TicTacToeBoard}) gets its button boxed in the Solution color. Nothing is shown while the
 * computer is still to answer, once the game is over, while a map has not arrived yet (it would read
 * as an empty cell), or when no reply can save the game any more.
 *
 * The board hangs at room X 8, Y 70..72, Z 15..17 (puzzles-chat.md 4.2). Only reads entities, maps
 * and blocks; the player presses the button, nothing here does or stops it.
 *
 * Spec: puzzles-chat.md section 4.
 */
public final class TicTacToe extends PuzzleSolver {

    // This tick's result, drawn by render().
    private RoomFrame frame = null;
    private AABB button = null;
    /** The last board read and the advice for it; the search only runs again when the board changes. */
    private String lastBoard = null;
    private TicTacToeBoard.Advice lastAdvice = TicTacToeBoard.Advice.NONE;
    /** What the last tick saw, for the debug output. */
    private String seen = null;

    @Override
    public DungeonPuzzle puzzle() {
        return DungeonPuzzle.TIC_TAC_TOE;
    }

    @Override
    public void tick(SolverContext ctx) {
        button = null;
        seen = null;
        frame = ctx.frame();
        if (frame == null) return;
        ClientLevel level = ctx.level();

        AABB room = frame.bounds();
        AABB search = new AABB(room.minX, 68, room.minZ, room.maxX, 75, room.maxZ);
        TicTacToeBoard board = TicTacToeBoard.empty();
        int onBoard = 0;
        boolean unreadable = false;
        for (ItemFrame itemFrame : level.getEntitiesOfClass(ItemFrame.class, search, ItemFrame::hasFramedMap)) {
            BlockPos rel = frame.toRel(itemFrame.getPos());
            int cell = TicTacToeBoard.cellAt(rel.getX(), rel.getY(), rel.getZ());
            if (cell < 0) continue;
            onBoard++;
            char mark = markOf(level, itemFrame);
            if (mark == TicTacToeBoard.EMPTY || board.at(cell) != TicTacToeBoard.EMPTY) {
                // A map whose picture has not come yet, or two frames on one cell: wait.
                unreadable = true;
                continue;
            }
            board = board.with(cell, mark);
        }
        if (onBoard == 0) {
            // No marks on the wall: not this room, or a game the computer has not opened yet.
            frame = null;
            return;
        }
        seen = board + (unreadable ? " (a map is not readable yet)" : "");
        if (unreadable) return;

        String key = board.toString();
        if (!key.equals(lastBoard)) {
            lastBoard = key;
            lastAdvice = board.advise();
        }
        // A lost game has no reply worth marking: every button loses against a computer that does not slip.
        if (!lastAdvice.any() || lastAdvice.outcome() == TicTacToeBoard.Outcome.LOSS) return;
        button = buttonBox(level, frame.toWorld(TicTacToeBoard.buttonRel(lastAdvice.cell())));
    }

    @Override
    public void render(SolverDraw draw, float partialTick) {
        if (frame == null || button == null) return;
        draw.box(button, SolverColors.solution());
    }

    @Override
    public void reset() {
        frame = null;
        button = null;
        lastBoard = null;
        lastAdvice = TicTacToeBoard.Advice.NONE;
        seen = null;
    }

    @Override
    public boolean claimsRoom() {
        return frame != null;
    }

    @Override
    public String debugState() {
        if (seen == null) return null;
        if (lastBoard == null || !seen.equals(lastBoard)) return "board " + seen;
        if (!lastAdvice.any()) return "board " + seen + " -> not the player's move";
        return "board " + seen + " -> cell " + lastAdvice.cell() + " (" + lastAdvice.outcome() + ")"
                + (button == null ? ", no button there" : "");
    }

    /** X or O from the map in a frame, or EMPTY while the map's picture has not arrived. */
    private static char markOf(ClientLevel level, ItemFrame itemFrame) {
        MapId id = itemFrame.getFramedMapId(itemFrame.getItem());
        if (id == null) return TicTacToeBoard.EMPTY;
        MapItemSavedData data = level.getMapData(id);
        if (data == null || data.colors == null || data.colors.length <= TicTacToeBoard.CENTRE_PIXEL) {
            return TicTacToeBoard.EMPTY;
        }
        return TicTacToeBoard.markOfPixel(data.colors[TicTacToeBoard.CENTRE_PIXEL]);
    }

    /** The button's own outline at {@code pos}, a hair larger; null if there is no button. */
    private static AABB buttonBox(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ButtonBlock)) return null;
        VoxelShape shape = state.getShape(level, pos);
        if (shape.isEmpty()) return null;
        return shape.bounds().move(pos).inflate(SolverDraw.BLOCK_INFLATE);
    }
}

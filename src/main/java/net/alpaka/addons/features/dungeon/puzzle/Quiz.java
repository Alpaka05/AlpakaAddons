package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.features.dungeon.SolverContext;
import net.alpaka.addons.features.dungeon.render.SolverColors;
import net.alpaka.addons.features.dungeon.render.SolverDraw;
import net.alpaka.addons.features.dungeon.room.RoomFrame;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Quiz: Oruo the Omniscient asks three SkyBlock questions in chat, each with three options, and the
 * party answers by pressing the buttons on one of three stone blocks in front of him. One wrong
 * press fails the puzzle.
 *
 * {@link QuizReader} follows the questions and options in chat and looks them up in the answer table
 * ({@link QuizAnswers}). Once all three options of a question are in and exactly one of them is the
 * answer, the buttons of that option's block are boxed in the Solution color and the other two
 * blocks' buttons in the Wrong color. The mark goes as soon as anyone answers the question.
 *
 * The answer blocks are at room positions (20,70,6) for ⓐ, (15,70,9) for ⓑ and (10,70,6) for ⓒ
 * (puzzles-chat.md 3.6, the same in Odin and Skyblocker). Only reads chat and blocks; the player
 * presses the button, nothing here does or stops it.
 *
 * Spec: puzzles-chat.md section 3.
 */
public final class Quiz extends PuzzleSolver {

    /** The answer blocks for ⓐ, ⓑ, ⓒ, in the room frame (Y absolute). */
    private static final int[][] STATION_REL = {{20, 70, 6}, {15, 70, 9}, {10, 70, 6}};
    /** How many answer blocks must carry buttons for a room to count as the Quiz. */
    private static final int STATIONS_TO_MATCH = 2;
    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private QuizReader reader = null;
    /** The answer table could not be read; logged once, then the solver stays idle. */
    private boolean tableMissing = false;
    /** The question last written to the log as unanswerable, so each is logged once. */
    private String logged = null;

    // This tick's result, drawn by render().
    private RoomFrame frame = null;
    private final List<AABB> answerBoxes = new ArrayList<>();
    private final List<AABB> wrongBoxes = new ArrayList<>();

    @Override
    public DungeonPuzzle puzzle() {
        return DungeonPuzzle.QUIZ;
    }

    @Override
    public void onChat(String plain) {
        QuizReader quiz = reader();
        if (quiz == null) return;
        QuizReader.Status status = quiz.accept(plain, System.currentTimeMillis());
        if ((status == QuizReader.Status.UNKNOWN || status == QuizReader.Status.AMBIGUOUS)
                && quiz.question() != null && !quiz.question().equals(logged)) {
            logged = quiz.question();
            AlpakaAddons.LOGGER.info("Quiz: no answer marked for \"{}\" with options {} ({})",
                    quiz.question(), quiz.options(), status == QuizReader.Status.UNKNOWN ? "not in the table" : "more than one fits");
        }
    }

    @Override
    public void tick(SolverContext ctx) {
        answerBoxes.clear();
        wrongBoxes.clear();
        frame = ctx.frame();
        if (frame == null || !isThisRoom(ctx.level(), frame)) {
            frame = null;
            return;
        }
        int answer = reader == null ? -1 : reader.answer();
        if (answer < 0) return;
        for (int option = 0; option < 3; option++) {
            List<AABB> boxes = buttons(ctx.level(), station(frame, option));
            (option == answer ? answerBoxes : wrongBoxes).addAll(boxes);
        }
    }

    @Override
    public void render(SolverDraw draw, float partialTick) {
        if (frame == null) return;
        int right = SolverColors.solution();
        int wrong = SolverColors.wrong();
        for (AABB box : answerBoxes) draw.box(box, right);
        for (AABB box : wrongBoxes) draw.box(box, wrong);
    }

    @Override
    public void reset() {
        if (reader != null) reader.clear();
        logged = null;
        frame = null;
        answerBoxes.clear();
        wrongBoxes.clear();
    }

    @Override
    public boolean claimsRoom() {
        return frame != null;
    }

    @Override
    public String debugState() {
        if (tableMissing) return "answer table missing";
        if (reader == null || reader.question() == null) return frame == null ? null : "no question yet";
        String state = switch (reader.status()) {
            case ANSWER -> "answer " + QuizReader.LETTERS.charAt(reader.answer());
            case AMBIGUOUS -> "more than one option fits";
            case UNKNOWN -> "not in the table";
            case OPTIONS -> "waiting for the options";
            case IDLE -> "idle";
        };
        return "\"" + reader.question() + "\" " + reader.options() + " -> " + state;
    }

    /** The reader, made on first use so the table is only read in a run that needs it. */
    private QuizReader reader() {
        if (reader == null && !tableMissing) {
            try {
                reader = new QuizReader(QuizAnswers.bundled());
            } catch (RuntimeException e) {
                tableMissing = true;
                AlpakaAddons.LOGGER.error("Quiz: could not read the answer table; the Quiz solver stays off", e);
            }
        }
        return reader;
    }

    private static BlockPos station(RoomFrame frame, int option) {
        int[] p = STATION_REL[option];
        return frame.toWorld(p[0], p[1], p[2]);
    }

    /** The room is the Quiz room if its answer blocks are where they should be, with buttons on them. */
    private static boolean isThisRoom(ClientLevel level, RoomFrame frame) {
        int withButtons = 0;
        for (int option = 0; option < 3; option++) {
            BlockPos station = station(frame, option);
            for (Direction side : SIDES) {
                if (level.getBlockState(station.relative(side)).getBlock() instanceof ButtonBlock) {
                    withButtons++;
                    break;
                }
            }
        }
        return withButtons >= STATIONS_TO_MATCH;
    }

    /**
     * The outline of every button on the sides of an answer block, a hair larger; the block itself
     * if it carries none, so the mark still shows which one it is.
     */
    private static List<AABB> buttons(ClientLevel level, BlockPos station) {
        List<AABB> boxes = new ArrayList<>(2);
        for (Direction side : SIDES) {
            BlockPos at = station.relative(side);
            BlockState state = level.getBlockState(at);
            if (!(state.getBlock() instanceof ButtonBlock)) continue;
            VoxelShape shape = state.getShape(level, at);
            if (shape.isEmpty()) continue;
            boxes.add(shape.bounds().move(at).inflate(SolverDraw.BLOCK_INFLATE));
        }
        if (boxes.isEmpty()) boxes.add(new AABB(station).inflate(SolverDraw.BLOCK_INFLATE));
        return boxes;
    }
}

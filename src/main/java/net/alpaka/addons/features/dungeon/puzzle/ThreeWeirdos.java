package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.features.dungeon.SolverContext;
import net.alpaka.addons.features.dungeon.puzzle.ThreeWeirdosRiddle.Verdict;
import net.alpaka.addons.features.dungeon.render.SolverColors;
import net.alpaka.addons.features.dungeon.render.SolverDraw;
import net.alpaka.addons.features.dungeon.room.PuzzleChat;
import net.alpaka.addons.features.dungeon.room.RoomFrame;
import net.alpaka.addons.utils.SkyblockUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Three Weirdos: three NPCs, each with a chest, and only one chest holds the reward. Right-clicking
 * an NPC makes it say one line in chat; this reads those lines, works out from them which chest
 * holds the reward ({@link ThreeWeirdosRiddle}), and boxes that chest in the Solution color. The
 * chests known to be empty get the Wrong color.
 *
 * Everything comes from what the player was shown: a weirdo's line only arrives after the player
 * asked that weirdo, so nothing is known before the player has talked to them (compliance.md D2).
 * The player clicks the NPCs and opens the chest themselves; nothing here touches either.
 *
 * Which weirdo stands where is read from the name tags above them: a statement names its speaker,
 * and the armor stand carrying that name stands on one of the three NPC blocks of the room. That is
 * looked for every tick until found, because the name may arrive later than the line.
 *
 * Spec: puzzles-chat.md section 2.
 */
public final class ThreeWeirdos extends PuzzleSolver {

    /** How many of the three chests must be in place for a room to count as this puzzle. */
    private static final int CHESTS_TO_MATCH = 2;

    /** What each weirdo said, by name, in the order the lines came. */
    private final Map<String, Verdict> verdicts = new LinkedHashMap<>();
    /** The slot each weirdo stands in, once found. */
    private final Map<String, Integer> slots = new HashMap<>();
    /** Statements we could not read, already logged once each. */
    private final Set<String> unknownLogged = new HashSet<>();

    // This tick's result, drawn by render().
    private RoomFrame frame = null;
    private final List<AABB> rewardBoxes = new ArrayList<>();
    private final List<AABB> emptyBoxes = new ArrayList<>();
    private int answer = -1;

    @Override
    public DungeonPuzzle puzzle() {
        return DungeonPuzzle.THREE_WEIRDOS;
    }

    @Override
    public void onChat(String plain) {
        // The puzzle is over: solved, or failed and possibly brought back later with an Architect's
        // First Draft. Either way the old statements are worth nothing now.
        String solved = PuzzleChat.solvedBy(plain);
        String failed = PuzzleChat.failedBy(plain);
        if ((solved != null || failed != null) && plain.contains("fooled by")) {
            clearStatements();
            return;
        }

        ThreeWeirdosRiddle.Statement statement = ThreeWeirdosRiddle.parse(plain);
        if (statement != null) {
            verdicts.put(statement.npc(), statement.verdict());
            return;
        }
        // An NPC line that is none of the 18 statements. Mort and others talk too, so only a line
        // from a known weirdo name is worth a log line; Hypixel may have reworded it.
        String speaker = ThreeWeirdosRiddle.speaker(plain);
        if (ThreeWeirdosRiddle.knownName(speaker) && unknownLogged.add(plain)) {
            AlpakaAddons.LOGGER.info("Three Weirdos: did not recognise the statement \"{}\"", plain);
        }
    }

    @Override
    public void tick(SolverContext ctx) {
        rewardBoxes.clear();
        emptyBoxes.clear();
        answer = -1;
        frame = ctx.frame();
        if (frame == null || !isThisRoom(ctx.level(), frame)) {
            frame = null;
            return;
        }
        if (verdicts.isEmpty()) return;

        findSlots(ctx.level(), frame);
        Verdict[] bySlot = new Verdict[3];
        for (Map.Entry<String, Verdict> e : verdicts.entrySet()) {
            Integer slot = slots.get(e.getKey());
            if (slot != null) bySlot[slot] = e.getValue();
        }
        answer = ThreeWeirdosRiddle.answer(bySlot);
        boolean[] empty = ThreeWeirdosRiddle.empty(bySlot);
        for (int slot = 0; slot < 3; slot++) {
            if (slot != answer && !empty[slot]) continue;
            AABB box = chestBox(ctx.level(), frame.toWorld(ThreeWeirdosRiddle.chestRel(slot)));
            if (box == null) continue;
            (slot == answer ? rewardBoxes : emptyBoxes).add(box);
        }
    }

    @Override
    public void render(SolverDraw draw, float partialTick) {
        if (frame == null) return;
        int reward = SolverColors.solution();
        int wrong = SolverColors.wrong();
        for (AABB box : rewardBoxes) draw.box(box, reward);
        for (AABB box : emptyBoxes) draw.box(box, wrong);
    }

    @Override
    public void reset() {
        clearStatements();
        unknownLogged.clear();
        frame = null;
        rewardBoxes.clear();
        emptyBoxes.clear();
        answer = -1;
    }

    @Override
    public boolean claimsRoom() {
        return frame != null;
    }

    @Override
    public String debugState() {
        if (verdicts.isEmpty()) return frame == null ? null : "no statements yet";
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, Verdict> e : verdicts.entrySet()) {
            if (!out.isEmpty()) out.append(", ");
            Integer slot = slots.get(e.getKey());
            out.append(e.getKey()).append('=').append(e.getValue() == Verdict.REWARD ? "reward" : "empty")
                    .append(" @").append(slot == null ? "?" : slot.toString());
        }
        out.append(answer >= 0 ? " -> chest " + answer : " -> not known yet");
        return out.toString();
    }

    private void clearStatements() {
        verdicts.clear();
        slots.clear();
    }

    /** The room is the Three Weirdos room if the weirdos' chests stand where they should. */
    private static boolean isThisRoom(ClientLevel level, RoomFrame frame) {
        int chests = 0;
        for (int slot = 0; slot < 3; slot++) {
            BlockState state = level.getBlockState(frame.toWorld(ThreeWeirdosRiddle.chestRel(slot)));
            if (state.getBlock() instanceof ChestBlock) chests++;
        }
        return chests >= CHESTS_TO_MATCH;
    }

    /**
     * Finds the slot of every weirdo that spoke and has none yet: the armor stand with their exact
     * name standing on one of the three NPC blocks of this room.
     */
    private void findSlots(ClientLevel level, RoomFrame frame) {
        boolean missing = false;
        for (String name : verdicts.keySet()) {
            if (!slots.containsKey(name)) {
                missing = true;
                break;
            }
        }
        if (!missing) return;
        AABB room = frame.bounds();
        AABB search = new AABB(room.minX, 66, room.minZ, room.maxX, 74, room.maxZ);
        for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, search, ArmorStand::hasCustomName)) {
            String name = SkyblockUtils.cleanColor(stand.getCustomName().getString());
            if (!verdicts.containsKey(name) || slots.containsKey(name)) continue;
            BlockPos rel = frame.toRel(BlockPos.containing(stand.getX(), 69, stand.getZ()));
            int slot = ThreeWeirdosRiddle.slotAt(rel.getX(), rel.getZ());
            if (slot >= 0) slots.put(name, slot);
        }
    }

    /** The chest's own outline at {@code pos}, a hair larger; null if no chest is there. */
    private static AABB chestBox(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) return null;
        VoxelShape shape = state.getShape(level, pos);
        AABB box = shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
        return box.inflate(SolverDraw.BLOCK_INFLATE);
    }
}

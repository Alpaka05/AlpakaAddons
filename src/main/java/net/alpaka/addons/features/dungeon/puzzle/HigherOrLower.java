package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.features.dungeon.SolverContext;
import net.alpaka.addons.features.dungeon.puzzle.HigherOrLowerOrder.Entry;
import net.alpaka.addons.features.dungeon.puzzle.HigherOrLowerOrder.Order;
import net.alpaka.addons.features.dungeon.puzzle.HigherOrLowerOrder.Plan;
import net.alpaka.addons.features.dungeon.render.SolverColors;
import net.alpaka.addons.features.dungeon.render.SolverDraw;
import net.alpaka.addons.features.dungeon.room.DungeonRooms;
import net.alpaka.addons.utils.SkyblockUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Higher Or Lower (the blaze room): reads the health in the blazes' name tags and marks the one
 * to kill next in the Solution color and the one after it in the Next color, with a line from the
 * first to the second. Lowest health goes first in the room whose blazes float above the doors,
 * highest first in the one where they float below (see {@link HigherOrLowerOrder}).
 *
 * Only what the player can see is read: the name tags Hypixel shows over the blazes (its armor
 * stands are invisible, their names are not) and the blazes themselves, for where to draw the box.
 * Hypixel gives a mob's name tag the entity id right after the mob's own, which pairs a tag with its
 * blaze; failing that, the nearest blaze just below the tag. A blaze without a pair is boxed where
 * its tag says it is.
 *
 * A dying blaze drops out at once: its health is zero from the death event on, while its body and
 * tag linger for the death animation. Its tag is remembered as dead, so it stays out even if the
 * tag outlives the blaze. When blazes read the same health (abbreviated numbers), it cannot be told
 * which goes first: they are all marked in the Next color with a question mark, and nothing else.
 *
 * Spec: puzzles-entities.md section 2 and its Critic additions. Needs no room frame.
 */
public final class HigherOrLower extends PuzzleSolver {

    /** How far a blaze may be from its tag, sideways and downwards, to count as the tag's blaze. */
    private static final double PAIR_SIDEWAYS = 1.0;
    private static final double PAIR_BELOW = 3.5;
    /** A blaze's box grows by this much, so its outline does not cut through the blaze. */
    private static final double BLAZE_INFLATE = 0.15;

    /** A living puzzle blaze: its tag, and the blaze itself if it was found. */
    private record Found(ArmorStand tag, Blaze blaze) {}

    /** One blaze to mark this tick. */
    private record Mark(ArmorStand tag, Blaze blaze, Role role) {
        Mark(Found found, Role role) {
            this(found.tag(), found.blaze(), role);
        }
    }

    private enum Role { NEXT, AFTER, MAYBE_NEXT, MAYBE_AFTER }

    private final List<Mark> marks = new ArrayList<>();
    /** Tags whose blaze was seen dying; they stay out until the world changes. */
    private final Set<Integer> deadTags = new HashSet<>();
    /**
     * The order of each room it was decided for, from all its blazes, kept for the rest of the run:
     * the player may leave and come back with one blaze left, which cannot tell it any more.
     */
    private final Map<Long, Order> orders = new HashMap<>();
    private Order order = null;
    private int alive = 0;
    private boolean undecidedLogged = false;

    @Override
    public DungeonPuzzle puzzle() {
        return DungeonPuzzle.HIGHER_OR_LOWER;
    }

    @Override
    public void tick(SolverContext ctx) {
        marks.clear();
        alive = 0;
        order = null;
        AABB cell = DungeonRooms.cellBounds();
        if (cell == null) return;
        long cellKey = ((long) DungeonRooms.cellX() << 32) | (DungeonRooms.cellZ() & 0xFFFFFFFFL);

        ClientLevel level = ctx.level();
        List<Entry<Found>> living = new ArrayList<>();
        List<Double> heights = new ArrayList<>();
        for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, cell, HigherOrLower::showsName)) {
            Component name = stand.getCustomName();
            HigherOrLowerTag tag = name == null ? null : HigherOrLowerTag.parse(SkyblockUtils.cleanColor(name.getString()));
            if (tag == null) continue;
            heights.add(stand.getY());
            Blaze blaze = blazeOf(level, stand);
            if (blaze != null && blaze.isDeadOrDying()) deadTags.add(stand.getId());
            if (tag.dead() || deadTags.contains(stand.getId())) continue;
            living.add(new Entry<>(new Found(stand, blaze), tag.max()));
        }
        alive = living.size();
        if (heights.isEmpty()) return;
        order = orders.get(cellKey);
        if (order == null) {
            // Every tag counts here, dead or alive: they all float on the same side of the doors.
            order = HigherOrLowerOrder.decide(heights);
            if (order != null) {
                orders.put(cellKey, order);
            } else if (heights.size() >= 2 && !undecidedLogged) {
                undecidedLogged = true;
                AlpakaAddons.LOGGER.info("Higher Or Lower: blaze tags on both sides of Y 69 ({}), showing nothing", heights);
            }
        }
        if (living.isEmpty()) return;
        // The last blaze is next whichever way the room goes.
        Order use = order != null ? order : living.size() == 1 ? Order.LOWEST_FIRST : null;
        if (use == null) return;

        Plan<Found> plan = HigherOrLowerOrder.plan(living, use);
        Role next = plan.nextCertain() ? Role.NEXT : Role.MAYBE_NEXT;
        for (Entry<Found> e : plan.next()) marks.add(new Mark(e.key(), next));
        Role after = plan.afterCertain() ? Role.AFTER : Role.MAYBE_AFTER;
        for (Entry<Found> e : plan.after()) marks.add(new Mark(e.key(), after));
    }

    @Override
    public void render(SolverDraw draw, float partialTick) {
        AABB next = null;
        AABB after = null;
        for (Mark mark : marks) {
            AABB box = boxOf(mark, partialTick);
            if (box == null) continue;
            int color = mark.role() == Role.NEXT ? SolverColors.solution() : SolverColors.next();
            draw.box(box, color);
            String label = switch (mark.role()) {
                case NEXT -> "1";
                case AFTER -> "2";
                case MAYBE_NEXT, MAYBE_AFTER -> "?";
            };
            // Under the blaze: above it is Hypixel's own name tag.
            draw.label(new Vec3((box.minX + box.maxX) / 2, box.minY - 0.35, (box.minZ + box.maxZ) / 2), label, color, 1.0f);
            if (mark.role() == Role.NEXT) next = box;
            if (mark.role() == Role.AFTER) after = box;
        }
        if (next != null && after != null) draw.line(next.getCenter(), after.getCenter(), SolverColors.next());
    }

    @Override
    public void reset() {
        marks.clear();
        deadTags.clear();
        orders.clear();
        order = null;
        alive = 0;
        undecidedLogged = false;
    }

    @Override
    public boolean claimsRoom() {
        return alive > 0;
    }

    @Override
    public String debugState() {
        if (alive == 0 && order == null) return null;
        String side = order == null ? "undecided" : order == Order.LOWEST_FIRST ? "lowest first" : "highest first";
        return String.format(Locale.ROOT, "%s, %d alive, %d marked", side, alive, marks.size());
    }

    // --- entities ---

    /** A stand whose name the player can see; Hypixel's tag stands are invisible, their names are not. */
    private static boolean showsName(ArmorStand stand) {
        return !stand.isRemoved() && stand.hasCustomName() && stand.isCustomNameVisible();
    }

    /** The visible blaze the tag belongs to, or null. */
    private static Blaze blazeOf(ClientLevel level, ArmorStand tag) {
        if (level.getEntity(tag.getId() - 1) instanceof Blaze blaze && under(tag, blaze)) return blaze;
        AABB below = new AABB(tag.getX() - PAIR_SIDEWAYS, tag.getY() - PAIR_BELOW, tag.getZ() - PAIR_SIDEWAYS,
                tag.getX() + PAIR_SIDEWAYS, tag.getY() + 0.5, tag.getZ() + PAIR_SIDEWAYS);
        Blaze nearest = null;
        double best = Double.MAX_VALUE;
        for (Blaze blaze : level.getEntitiesOfClass(Blaze.class, below, b -> !b.isRemoved() && !b.isInvisible())) {
            double d = blaze.distanceToSqr(tag);
            if (d < best) {
                best = d;
                nearest = blaze;
            }
        }
        return nearest;
    }

    private static boolean under(ArmorStand tag, Blaze blaze) {
        if (blaze.isRemoved() || blaze.isInvisible()) return false;
        double dy = tag.getY() - blaze.getY();
        return Math.abs(tag.getX() - blaze.getX()) <= PAIR_SIDEWAYS && Math.abs(tag.getZ() - blaze.getZ()) <= PAIR_SIDEWAYS
                && dy >= -0.5 && dy <= PAIR_BELOW;
    }

    /**
     * Where to draw the blaze this frame, moved along with it between ticks; null once it is gone.
     * Without a paired blaze, a blaze-sized box just under the tag.
     */
    private static AABB boxOf(Mark mark, float partialTick) {
        Blaze blaze = mark.blaze();
        if (blaze != null && !blaze.isRemoved() && !blaze.isDeadOrDying()) {
            return between(blaze, partialTick).inflate(BLAZE_INFLATE);
        }
        ArmorStand tag = mark.tag();
        if (tag.isRemoved()) return null;
        Vec3 at = tag.getPosition(partialTick);
        return new AABB(at.x - 0.45, at.y - 2.0, at.z - 0.45, at.x + 0.45, at.y - 0.1, at.z + 0.45);
    }

    /** The entity's box at its position between the last tick and this one. */
    private static AABB between(Entity entity, float partialTick) {
        Vec3 now = entity.position();
        Vec3 then = entity.getPosition(partialTick);
        return entity.getBoundingBox().move(then.x - now.x, then.y - now.y, then.z - now.z);
    }
}

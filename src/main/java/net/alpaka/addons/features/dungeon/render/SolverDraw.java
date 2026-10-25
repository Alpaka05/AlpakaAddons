package net.alpaka.addons.features.dungeon.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything the dungeon solvers draw in the world this frame, and the only place that draws it.
 *
 * Solvers add boxes, lines and labels in world coordinates from their {@code render} method; then
 * the whole frame is handed to the renderer at once. Every primitive goes through a vanilla render
 * type that tests depth: {@code RenderTypes.debugFilledBox()} for fills, {@code RenderTypes.lines()}
 * (or {@code linesTranslucent()} below full alpha) for edges and lines, and text in
 * {@code Font.DisplayMode.NORMAL}. So a wall in front of a mark hides it like any block - the
 * solvers show nothing through walls, and NoSeeThroughRenderingTest keeps it that way.
 *
 * While a puzzle solver draws, the frame is limited to the player's own room, and while a device
 * solver draws, to the blocks around the player: anything outside is dropped, a line only stays
 * if both its ends are inside. A solver cannot show something about a room or a device the player
 * is not at, even by mistake.
 *
 * The geometry is built later in the frame than it is submitted, so it is taken from a copy of the
 * lists made at submit time; the next frame's drawing cannot change what this frame shows.
 */
public final class SolverDraw {

    /** A filled box, in the color as given. */
    public record Fill(AABB box, int argb) {}

    /** A line between two world points. */
    public record Line(Vec3 from, Vec3 to, int argb, float width) {}

    /** A billboard text label. */
    public record Label(Vec3 at, String text, int argb, float scale) {}

    /** Full-block boxes grow by this much, so they sit just outside the block's faces. */
    public static final double BLOCK_INFLATE = 0.002;
    /** Vanilla's name tag text scale; a label of scale 1 is that size. */
    private static final float TEXT_SCALE = 0.025f;
    /** Text below this alpha is drawn opaque by the font, so it is left out instead. */
    private static final int MIN_TEXT_ALPHA = 8;

    private final List<Fill> fills = new ArrayList<>();
    private final List<Line> lines = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private AABB clip = null;

    // --- what solvers call ---

    /** A box in the Box Style setting: edges in {@code argb}, a fill in it at reduced alpha, or both. */
    public void box(AABB box, int argb) {
        if (!inside(box)) return;
        int style = SolverColors.boxStyle();
        if (style != SolverColors.OUTLINE) fills.add(new Fill(box, SolverColors.fill(argb)));
        if (style != SolverColors.FILLED) edges(box, argb, SolverColors.lineWidth());
    }

    /** A whole block in the Box Style setting, a hair larger than the block. */
    public void block(BlockPos pos, int argb) {
        box(new AABB(pos).inflate(BLOCK_INFLATE), argb);
    }

    /** Only the edges of a box. */
    public void outline(AABB box, int argb, float width) {
        if (!inside(box)) return;
        edges(box, argb, width);
    }

    public void outline(AABB box, int argb) {
        outline(box, argb, SolverColors.lineWidth());
    }

    /** Only a fill, in {@code argb} as given; see {@link SolverColors#fill} for the usual fill alpha. */
    public void filled(AABB box, int argb) {
        if (!inside(box)) return;
        fills.add(new Fill(box, argb));
    }

    /** A straight line. Lift lines that lie on a floor a little (about 0.05) or the floor hides them. */
    public void line(Vec3 from, Vec3 to, int argb, float width) {
        if (!inside(from) || !inside(to)) return;
        lines.add(new Line(from, to, argb, SolverColors.clampLineWidth(width)));
    }

    public void line(Vec3 from, Vec3 to, int argb) {
        line(from, to, argb, SolverColors.lineWidth());
    }

    /** Lines through the points in order. */
    public void path(List<Vec3> points, int argb, float width) {
        for (int i = 1; i < points.size(); i++) line(points.get(i - 1), points.get(i), argb, width);
    }

    public void path(List<Vec3> points, int argb) {
        path(points, argb, SolverColors.lineWidth());
    }

    /**
     * A text label facing the camera, centred on {@code at}; scale 1 is the size of a name tag.
     * Left out while Show Labels is off.
     */
    public void label(Vec3 at, String text, int argb, float scale) {
        if (!SolverColors.labels() || text == null || text.isEmpty()) return;
        if ((argb >>> 24) < MIN_TEXT_ALPHA || scale <= 0.0f || !inside(at)) return;
        labels.add(new Label(at, text, argb, scale));
    }

    // --- for DungeonSolvers ---

    /** Empties the frame. */
    public void clear() {
        fills.clear();
        lines.clear();
        labels.clear();
        clip = null;
    }

    /** Limits what is added next to {@code bounds}; null lifts the limit. */
    public void clip(AABB bounds) {
        clip = bounds;
    }

    public boolean isEmpty() {
        return fills.isEmpty() && lines.isEmpty() && labels.isEmpty();
    }

    /** How many primitives the frame holds, for the debug command. */
    public int size() {
        return fills.size() + lines.size() + labels.size();
    }

    /** Hands this frame's primitives to the renderer. */
    public void submit(LevelRenderContext context) {
        if (isEmpty()) return;
        CameraRenderState camera = context.levelState().cameraRenderState;
        Vec3 cam = camera.pos;
        double camX = cam.x;
        double camY = cam.y;
        double camZ = cam.z;
        PoseStack poseStack = context.poseStack();
        SubmitNodeCollector out = context.submitNodeCollector();

        // Copies: the callbacks run after this returns, when the lists may already hold the next frame.
        List<Fill> fillSnapshot = List.copyOf(fills);
        List<Line> opaque = new ArrayList<>();
        List<Line> translucent = new ArrayList<>();
        for (Line line : lines) ((line.argb() >>> 24) == 0xFF ? opaque : translucent).add(line);

        if (!fillSnapshot.isEmpty()) {
            out.submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, buffer) -> {
                for (Fill fill : fillSnapshot) filledBox(buffer, pose, fill.box(), fill.argb(), camX, camY, camZ);
            });
        }
        // Opaque lines write depth like terrain; translucent ones must not, or they would hide
        // each other where they cross.
        if (!opaque.isEmpty()) {
            List<Line> snapshot = List.copyOf(opaque);
            out.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
                for (Line line : snapshot) line(buffer, pose, line, camX, camY, camZ);
            });
        }
        if (!translucent.isEmpty()) {
            List<Line> snapshot = List.copyOf(translucent);
            out.submitCustomGeometry(poseStack, RenderTypes.linesTranslucent(), (pose, buffer) -> {
                for (Line line : snapshot) line(buffer, pose, line, camX, camY, camZ);
            });
        }
        if (!labels.isEmpty()) submitLabels(out.order(1), poseStack, camera);
    }

    // --- building ---

    private boolean inside(AABB box) {
        return clip == null || clip.intersects(box);
    }

    private boolean inside(Vec3 point) {
        return clip == null || clip.contains(point);
    }

    /** The twelve edges of a box. */
    private void edges(AABB b, int argb, float width) {
        float w = SolverColors.clampLineWidth(width);
        Vec3 p000 = new Vec3(b.minX, b.minY, b.minZ);
        Vec3 p100 = new Vec3(b.maxX, b.minY, b.minZ);
        Vec3 p010 = new Vec3(b.minX, b.maxY, b.minZ);
        Vec3 p001 = new Vec3(b.minX, b.minY, b.maxZ);
        Vec3 p110 = new Vec3(b.maxX, b.maxY, b.minZ);
        Vec3 p101 = new Vec3(b.maxX, b.minY, b.maxZ);
        Vec3 p011 = new Vec3(b.minX, b.maxY, b.maxZ);
        Vec3 p111 = new Vec3(b.maxX, b.maxY, b.maxZ);
        lines.add(new Line(p000, p100, argb, w));
        lines.add(new Line(p001, p101, argb, w));
        lines.add(new Line(p010, p110, argb, w));
        lines.add(new Line(p011, p111, argb, w));
        lines.add(new Line(p000, p001, argb, w));
        lines.add(new Line(p100, p101, argb, w));
        lines.add(new Line(p010, p011, argb, w));
        lines.add(new Line(p110, p111, argb, w));
        lines.add(new Line(p000, p010, argb, w));
        lines.add(new Line(p100, p110, argb, w));
        lines.add(new Line(p001, p011, argb, w));
        lines.add(new Line(p101, p111, argb, w));
    }

    // --- vertices, relative to the camera ---
    // Positions are taken relative to the camera in double precision first: a float cannot hold a
    // world position finely enough far from the origin, and the marks would shake (FirePitFeature).

    private static void filledBox(VertexConsumer buffer, PoseStack.Pose pose, AABB a, int argb,
                                  double camX, double camY, double camZ) {
        if ((argb >>> 24) == 0) return;
        float x0 = (float) (a.minX - camX), y0 = (float) (a.minY - camY), z0 = (float) (a.minZ - camZ);
        float x1 = (float) (a.maxX - camX), y1 = (float) (a.maxY - camY), z1 = (float) (a.maxZ - camZ);
        // The debug box type does not cull faces, so one winding per side is enough.
        quad(buffer, pose, argb, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(buffer, pose, argb, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(buffer, pose, argb, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(buffer, pose, argb, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(buffer, pose, argb, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(buffer, pose, argb, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose, int argb,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        buffer.addVertex(pose, ax, ay, az).setColor(argb);
        buffer.addVertex(pose, bx, by, bz).setColor(argb);
        buffer.addVertex(pose, cx, cy, cz).setColor(argb);
        buffer.addVertex(pose, dx, dy, dz).setColor(argb);
    }

    /** One line; the line shader needs its direction as the normal and a width on every vertex. */
    private static void line(VertexConsumer buffer, PoseStack.Pose pose, Line line,
                             double camX, double camY, double camZ) {
        float x0 = (float) (line.from().x - camX), y0 = (float) (line.from().y - camY), z0 = (float) (line.from().z - camZ);
        float x1 = (float) (line.to().x - camX), y1 = (float) (line.to().y - camY), z1 = (float) (line.to().z - camZ);
        float nx = x1 - x0, ny = y1 - y0, nz = z1 - z0;
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 1.0e-5f) return;
        nx /= length;
        ny /= length;
        nz /= length;
        buffer.addVertex(pose, x0, y0, z0).setColor(line.argb()).setNormal(pose, nx, ny, nz).setLineWidth(line.width());
        buffer.addVertex(pose, x1, y1, z1).setColor(line.argb()).setNormal(pose, nx, ny, nz).setLineWidth(line.width());
    }

    private void submitLabels(net.minecraft.client.renderer.OrderedSubmitNodeCollector out, PoseStack poseStack,
                              CameraRenderState camera) {
        Font font = Minecraft.getInstance().font;
        for (Label label : List.copyOf(labels)) {
            FormattedCharSequence text = Component.literal(label.text()).getVisualOrderText();
            float width = font.width(text);
            float scale = TEXT_SCALE * label.scale();
            poseStack.pushPose();
            poseStack.translate(label.at().x - camera.pos.x, label.at().y - camera.pos.y, label.at().z - camera.pos.z);
            poseStack.mulPose(camera.orientation);
            poseStack.scale(scale, -scale, scale);
            // NORMAL: depth-tested like the world. Never SEE_THROUGH.
            out.submitText(poseStack, -width / 2.0f, -4.0f, text, true, Font.DisplayMode.NORMAL,
                    LightCoordsUtil.FULL_BRIGHT, label.argb(), 0, 0);
            poseStack.popPose();
        }
    }
}

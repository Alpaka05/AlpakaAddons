package net.alpaka.addons.features.etherwarp;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.sound.CustomSoundFeature;
import net.alpaka.addons.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.BigDripleafStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.DryVegetationBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SeagrassBlock;
import net.minecraft.world.level.block.ShortDryGrassBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SmallDripleafBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.TallSeagrassBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Shows where an Etherwarp teleport will land while the player is aiming one.
 *
 * Hypixel does not tell the client the destination ahead of time, so it has to be worked out the
 * way the server does: a ray from the eyes along the view direction, 57 blocks plus whatever the
 * item's Tuned Transmission adds, stopped by the first block Etherwarp cannot pass through. That
 * block is the target, and the warp succeeds only if the two blocks above it leave room to stand.
 * The passable-block list and the eye heights follow Odin's Etherwarp helper, which is the
 * reference other Skyblock mods use; the eye heights are the server's, not this client's, since it
 * is the server that decides where the player ends up.
 *
 * Everything here is display: a box on the target (gold when the warp will work, red when it will
 * not), an optional line from the player's feet to it, and an optional stand-in for the warp sound.
 * Nothing is sent, and the teleport itself is left entirely to the server - no client-side
 * position prediction of any kind.
 *
 * Drawing goes through vanilla's gizmo system, the world-space debug geometry the game itself uses
 * for chunk borders and the like: {@link #collectGizmos()} adds the box and the line while the level
 * renderer gathers its per-frame gizmos, and the game draws them with the rest of the frame. That
 * takes world coordinates directly and offers "always on top" for the through-walls option.
 *
 * The warp sound is recognised the same way Odin does it: Hypixel plays the ender dragon's hurt
 * sound at a pitch no vanilla event uses, 0.5397, at the moment of the teleport.
 */
public final class EtherwarpOverlayFeature {

    private EtherwarpOverlayFeature() {
    }

    /** Range of an untuned Etherwarp, in blocks. */
    private static final double BASE_RANGE = 57.0;

    /** Custom-data key Hypixel stores the Tuned Transmission level under. */
    private static final String TUNED_TRANSMISSION_KEY = "tuned_transmission";

    /** Server-side eye heights, which is what the warp is computed from. */
    private static final double EYE_HEIGHT_STANDING = 1.62;
    private static final double EYE_HEIGHT_CROUCHING = 1.27;
    private static final double EYE_HEIGHT_SWIMMING = 0.4;

    /** Hypixel's warp sound: the ender dragon's hurt sound at this exact pitch. */
    private static final String WARP_SOUND_PATH = "entity.ender_dragon.hurt";
    private static final float WARP_SOUND_PITCH = 0.53968257f;
    private static final float WARP_SOUND_PITCH_TOLERANCE = 0.005f;

    /** Squared distance within which the warp sound counts as the player's own. */
    private static final double OWN_WARP_RADIUS_SQR = 36.0;

    /** Upper bound on traversal steps; the ray is at most ~60 blocks long. */
    private static final int MAX_STEPS = 400;

    /** The fill, when on, is this fraction of the outline's alpha. */
    private static final float FILL_ALPHA = 0.35f;

    /** Names for the config slider, indexed like {@code CustomSoundFeature.ETHERWARP_SOUNDS}. */
    public static final String[] SOUND_NAMES = {"Whoosh", "Pling", "Thud", "Chime", "Pop"};

    /** How far off the block faces the box is drawn, so it does not z-fight with them. */
    private static final double BOX_INFLATE = 0.003;

    private static final int PASSABLE = 1;
    private static final int BLOCKS_FEET = 2;

    /** Passability per block, filled on first sight; the block set is fixed for the session. */
    private static final Map<Block, Integer> FLAGS = new IdentityHashMap<>();

    /** The block the ray stopped on, its outline, and whether there is room to stand on it. */
    public record Target(BlockPos pos, VoxelShape shape, boolean valid) {
    }

    public static boolean isEnabled() {
        return AlpakaConfig.instance.etherwarpOverlayEnabled;
    }

    // ---------------------------------------------------------------- rendering

    /**
     * Adds this frame's box and line to the level renderer's gizmo collection. Called while that
     * collection is open, so world coordinates go straight in.
     */
    public static void collectGizmos() {
        AlpakaConfig cfg = AlpakaConfig.instance;
        if (!cfg.etherwarpOverlayEnabled) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return;
        if (!EtherwarpDetector.isAimingEtherwarp()) return;

        Target target = computeTarget(player, level);
        if (target == null) return;
        if (!target.valid && !cfg.etherwarpOverlayShowFail) return;

        int color = target.valid ? cfg.etherwarpOverlayColor : cfg.etherwarpOverlayFailColor;
        boolean onTop = cfg.etherwarpOverlayThroughWalls;

        GizmoStyle style = cfg.etherwarpOverlayFill
                ? GizmoStyle.strokeAndFill(color, cfg.etherwarpOverlayThickness, ARGB.color(Math.round(ARGB.alpha(color) * FILL_ALPHA), color))
                : GizmoStyle.stroke(color, cfg.etherwarpOverlayThickness);

        // The outline shape rather than a plain cube, so a slab or a stair is marked as itself.
        for (AABB box : target.shape.toAabbs()) {
            onTop(Gizmos.cuboid(box.move(target.pos).inflate(BOX_INFLATE), style), onTop);
        }

        if (cfg.etherwarpLineEnabled) {
            float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            Vec3 feet = player.getPosition(partialTick);
            double top = target.shape.max(Direction.Axis.Y);
            Vec3 to = new Vec3(target.pos.getX() + 0.5, target.pos.getY() + top, target.pos.getZ() + 0.5);
            // Always on top, whatever the box does: a line from the feet skims the ground for most of
            // its length, and with the depth test on it kept vanishing into every rise in the terrain.
            Gizmos.line(feet, to, color, cfg.etherwarpLineWidth).setAlwaysOnTop();
        }
    }

    private static void onTop(GizmoProperties gizmo, boolean onTop) {
        if (onTop) gizmo.setAlwaysOnTop();
    }

    // ---------------------------------------------------------------- target

    /** Where the warp would land right now, or null when no block is within range. */
    public static Target computeTarget(LocalPlayer player, ClientLevel level) {
        double range = BASE_RANGE + tunedTransmission(player.getMainHandItem());
        Vec3 eye = player.position().add(0.0, eyeHeight(player), 0.0);
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        return traverse(level, eye, end);
    }

    private static double eyeHeight(LocalPlayer player) {
        if (player.getPose() == Pose.SWIMMING) return EYE_HEIGHT_SWIMMING;
        if (player.isCrouching()) return EYE_HEIGHT_CROUCHING;
        return EYE_HEIGHT_STANDING;
    }

    /** The item's Tuned Transmission level, from Hypixel's custom data, or 0. */
    private static int tunedTransmission(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return 0;
        CompoundTag tag = data.copyTag();
        return tag.getInt(TUNED_TRANSMISSION_KEY).orElse(0);
    }

    /**
     * Walks the ray voxel by voxel and stops on the first block Etherwarp cannot pass.
     *
     * A standard grid traversal: at each step the axis whose next grid boundary is nearest along the
     * ray is advanced, so every voxel the ray touches is visited in order and none is skipped.
     */
    private static Target traverse(ClientLevel level, Vec3 start, Vec3 end) {
        int x = (int) Math.floor(start.x);
        int y = (int) Math.floor(start.y);
        int z = (int) Math.floor(start.z);
        int endX = (int) Math.floor(end.x);
        int endY = (int) Math.floor(end.y);
        int endZ = (int) Math.floor(end.z);

        double dirX = end.x - start.x;
        double dirY = end.y - start.y;
        double dirZ = end.z - start.z;

        int stepX = (int) Math.signum(dirX);
        int stepY = (int) Math.signum(dirY);
        int stepZ = (int) Math.signum(dirZ);

        double invX = dirX != 0.0 ? 1.0 / dirX : Double.MAX_VALUE;
        double invY = dirY != 0.0 ? 1.0 / dirY : Double.MAX_VALUE;
        double invZ = dirZ != 0.0 ? 1.0 / dirZ : Double.MAX_VALUE;

        double tDeltaX = Math.abs(invX * stepX);
        double tDeltaY = Math.abs(invY * stepY);
        double tDeltaZ = Math.abs(invZ * stepZ);

        double tMaxX = Math.abs((x + Math.max(stepX, 0) - start.x) * invX);
        double tMaxY = Math.abs((y + Math.max(stepY, 0) - start.y) * invY);
        double tMaxZ = Math.abs((z + Math.max(stepZ, 0) - start.z) * invZ);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < MAX_STEPS; i++) {
            pos.set(x, y, z);
            BlockState state = level.getBlockState(pos);
            if ((flagsOf(state.getBlock()) & PASSABLE) == 0) {
                return hit(level, pos.immutable(), state);
            }
            if (x == endX && y == endY && z == endZ) return null;

            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                tMaxX += tDeltaX;
                x += stepX;
            } else if (tMaxY <= tMaxZ) {
                tMaxY += tDeltaY;
                y += stepY;
            } else {
                tMaxZ += tDeltaZ;
                z += stepZ;
            }
        }
        return null;
    }

    /** The target the ray stopped on, with the standing-room check that decides success. */
    private static Target hit(ClientLevel level, BlockPos pos, BlockState state) {
        VoxelShape outline = state.getShape(level, pos);
        if (outline.isEmpty()) outline = Shapes.block();

        VoxelShape collision = state.getCollisionShape(level, pos);
        double collisionTop = collision.isEmpty() ? 1.0 : collision.max(Direction.Axis.Y);
        int baseY = pos.getY() + Math.max(1, (int) Math.ceil(collisionTop));

        boolean valid = standable(level, new BlockPos(pos.getX(), baseY, pos.getZ()))
                && standable(level, new BlockPos(pos.getX(), baseY + 1, pos.getZ()));
        return new Target(pos, outline, valid);
    }

    /** Whether the player fits into this block: passable, and not one that blocks the feet. */
    private static boolean standable(ClientLevel level, BlockPos pos) {
        int flags = flagsOf(level.getBlockState(pos).getBlock());
        return (flags & PASSABLE) != 0 && (flags & BLOCKS_FEET) == 0;
    }

    private static int flagsOf(Block block) {
        Integer cached = FLAGS.get(block);
        if (cached != null) return cached;
        int flags = 0;
        if (passable(block)) flags |= PASSABLE;
        if (blocksFeet(block)) flags |= BLOCKS_FEET;
        FLAGS.put(block, flags);
        return flags;
    }

    /** Blocks the Etherwarp ray passes through, i.e. that can never be the target. */
    private static boolean passable(Block block) {
        return block instanceof AirBlock
                || block instanceof FlowerBlock || block instanceof TallGrassBlock || block instanceof BushBlock
                || block instanceof TallFlowerBlock || block instanceof ShortDryGrassBlock
                || block instanceof TorchBlock || block instanceof RedstoneTorchBlock
                || block instanceof TripWireBlock || block instanceof TripWireHookBlock
                || block instanceof RailBlock
                || block instanceof FireBlock
                || block instanceof VineBlock
                || block instanceof LiquidBlock
                || block instanceof SaplingBlock
                || block instanceof CropBlock || block instanceof StemBlock
                || block instanceof SeagrassBlock || block instanceof TallSeagrassBlock
                || block instanceof SugarCaneBlock
                || block instanceof MushroomBlock
                || block instanceof NetherWartBlock
                || block instanceof RedStoneWireBlock || block instanceof ComparatorBlock || block instanceof RepeaterBlock
                || block instanceof SmallDripleafBlock || block instanceof BigDripleafStemBlock
                || block instanceof DoublePlantBlock
                || block instanceof LeverBlock
                || block instanceof SnowLayerBlock
                || block instanceof BubbleColumnBlock
                || block instanceof GrowingPlantBlock
                || block instanceof PistonHeadBlock
                || block instanceof DryVegetationBlock
                || block instanceof ButtonBlock
                || block instanceof LanternBlock
                || block instanceof SkullBlock || block instanceof WallSkullBlock
                || block instanceof LadderBlock
                || block instanceof FlowerPotBlock
                || block instanceof WebBlock
                || block instanceof NetherPortalBlock;
    }

    /** Passable to the ray, yet the server refuses to put the player's feet inside them. */
    private static boolean blocksFeet(Block block) {
        return block instanceof SkullBlock || block instanceof WallSkullBlock
                || block instanceof FlowerPotBlock
                || block instanceof LadderBlock
                || block instanceof VineBlock;
    }

    // ---------------------------------------------------------------- sound

    /**
     * Whether a sound about to play is Hypixel's warp cue for this player, with the custom sound on.
     *
     * The position check keeps another player's warp a few blocks away from triggering it: the cue
     * is emitted where the warping player lands, and after our own warp that is where we stand.
     */
    public static boolean isOwnWarpSound(Identifier id, float pitch, double x, double y, double z) {
        if (!AlpakaConfig.instance.etherwarpSoundEnabled) return false;
        if (!WARP_SOUND_PATH.equals(id.getPath())) return false;
        if (Math.abs(pitch - WARP_SOUND_PITCH) > WARP_SOUND_PITCH_TOLERANCE) return false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        double dx = player.getX() - x, dy = player.getY() - y, dz = player.getZ() - z;
        if (dx * dx + dy * dy + dz * dz > OWN_WARP_RADIUS_SQR) return false;
        return SkyblockUtils.isOnSkyblock();
    }

    /** Plays the selected warp sound. Independent of the Custom Sounds master toggle on purpose. */
    public static void playWarpSound() {
        var sounds = CustomSoundFeature.ETHERWARP_SOUNDS;
        if (sounds.length == 0) return;
        int index = Mth.clamp(AlpakaConfig.instance.etherwarpSoundIndex, 0, sounds.length - 1);
        if (sounds[index] == null) return;
        float volume = AlpakaConfig.instance.etherwarpSoundVolume;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sounds[index], 1.0f, volume));
    }

    /** Previews the selected sound, for the config slider. */
    public static void previewWarpSound() {
        playWarpSound();
    }
}

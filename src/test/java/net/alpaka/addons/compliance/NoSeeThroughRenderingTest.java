package net.alpaka.addons.compliance;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dungeon solvers draw nothing through walls and touch no interaction, read from the compiled
 * classes of {@code features/dungeon}.
 *
 * The solvers promise visual help only: every box, line and label is depth-tested, so a wall hides
 * it like any block, and they never cancel, change or make a click, a use or an attack
 * (design.md section 1). Each of these ways around that promise is a reference to one of a few
 * methods, fields or types, whatever the source looked like - a method reference included - so a
 * scan of the bytecode catches all of them:
 *
 * - see-through drawing: {@code Font.DisplayMode.SEE_THROUGH} and every other "see through" render
 *   type or pipeline, {@code GizmoProperties.setAlwaysOnTop}, depth tests that always or "not equal"
 *   pass ({@code CompareOp.ALWAYS_PASS}, {@code NOT_EQUAL}), the mod's own
 *   {@code BlockOverlayRenderTypes.noDepthQuads}, {@code submitNameTag} (it can add a see-through
 *   copy), and the glow outline ({@code setGlowingTag}, {@code RenderTypes.outline},
 *   {@code RenderPipelines.OUTLINE_NO_CULL}, {@code shouldShowEntityOutlines}), which is drawn over
 *   everything. A pipeline of its own could set any depth test, so the package may not build one
 *   either: no {@code DepthStencilState}, no {@code RenderPipeline.Builder}, and not the mod's
 *   accessors that register pipelines and render types. Vanilla's depth-tested types are all it needs;
 * - acting in the world or sending: anything of {@code MultiPlayerGameMode} and {@code Connection},
 *   {@code ClientPacketListener.send}, and Fabric's interaction callbacks, which can cancel a click
 *   or make Fabric send one.
 *
 * The custom terminal window still clicks the slot the player clicked, through
 * {@code compliance/Outbound}; OutboundBytecodeScanTest guards that path.
 */
class NoSeeThroughRenderingTest {
    private static final String PACKAGE = "net/alpaka/addons/features/dungeon";

    private static final String PACKET = "Lnet/minecraft/network/protocol/Packet;";

    /** Members that draw through walls, by owner and name. */
    private static final Map<String, Set<String>> FORBIDDEN_MEMBERS = Map.of(
            "net/minecraft/client/gui/Font$DisplayMode", Set.of("SEE_THROUGH"),
            "net/minecraft/gizmos/GizmoProperties", Set.of("setAlwaysOnTop"),
            "com/mojang/blaze3d/platform/CompareOp", Set.of("ALWAYS_PASS", "NOT_EQUAL"),
            "net/alpaka/addons/features/blockoverlay/BlockOverlayRenderTypes", Set.of("noDepthQuads"),
            "net/minecraft/client/renderer/rendertype/RenderTypes", Set.of("outline"),
            "net/minecraft/client/renderer/RenderPipelines", Set.of("OUTLINE_NO_CULL"));

    /** Names that are forbidden on any owner: a subclass or another collector has them too. */
    private static final Set<String> FORBIDDEN_NAMES = Set.of("setGlowingTag", "submitNameTag", "shouldShowEntityOutlines");

    /** Types the package may not mention at all. */
    private static final Set<String> FORBIDDEN_TYPES = Set.of(
            "net/minecraft/client/multiplayer/MultiPlayerGameMode",
            "net/minecraft/network/Connection",
            "net/fabricmc/fabric/api/event/client/player/ClientPreAttackCallback",
            // A depth test of its own choosing: only through a pipeline built here.
            "com/mojang/blaze3d/pipeline/DepthStencilState",
            "com/mojang/blaze3d/pipeline/RenderPipeline$Builder",
            "net/alpaka/addons/mixin/RenderPipelinesAccessor",
            "net/alpaka/addons/mixin/RenderTypeInvoker");

    /** Every Fabric player interaction event: use and attack on blocks, entities and items. */
    private static final String FABRIC_PLAYER_EVENTS = "net/fabricmc/fabric/api/event/player/";

    @Test
    void theDungeonSolversDrawNothingThroughWallsAndTouchNoInteraction() throws IOException {
        Map<String, String> found = new TreeMap<>();
        int scanned = 0;
        for (Path dir : classDirectories()) {
            Path root = dir.resolve(PACKAGE);
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    if (!file.toString().endsWith(".class")) continue;
                    scanned++;
                    String why = forbidden(Files.readAllBytes(file));
                    if (why != null) found.put(dir.relativize(file).toString(), why);
                }
            }
        }
        assertTrue(scanned > 30, "Found only " + scanned + " compiled dungeon classes; the scan looked in the wrong place");
        assertEquals(Map.of(), found, "The dungeon solvers must only draw depth-tested and never act: " + found);
    }

    /** The checks themselves, on references written out by hand. */
    @Test
    void recognisesEveryForbiddenReference() {
        String displayMode = "net/minecraft/client/gui/Font$DisplayMode";
        String renderTypes = "net/minecraft/client/renderer/rendertype/RenderTypes";
        String renderType = "()Lnet/minecraft/client/renderer/rendertype/RenderType;";
        String compareOp = "com/mojang/blaze3d/platform/CompareOp";

        assertNotNull(check(field(displayMode, "SEE_THROUGH", "L" + displayMode + ";")));
        assertNull(check(field(displayMode, "NORMAL", "L" + displayMode + ";")));
        assertNull(check(field(displayMode, "POLYGON_OFFSET", "L" + displayMode + ";")));

        assertNotNull(check(call("net/minecraft/gizmos/GizmoProperties", "setAlwaysOnTop", "()Lnet/minecraft/gizmos/GizmoProperties;")));
        assertNull(check(call("net/minecraft/gizmos/GizmoProperties", "persistForMillis", "(I)Lnet/minecraft/gizmos/GizmoProperties;")));

        assertNotNull(check(call(renderTypes, "textSeeThrough", "(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;")));
        assertNotNull(check(call(renderTypes, "textBackgroundSeeThrough", renderType)));
        assertNotNull(check(call(renderTypes, "outline", "(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;")));
        assertNotNull(check(field("net/minecraft/client/renderer/RenderPipelines", "TEXT_SEE_THROUGH", "Lcom/mojang/blaze3d/pipeline/RenderPipeline;")));
        assertNull(check(call(renderTypes, "lines", renderType)));
        assertNull(check(call(renderTypes, "linesTranslucent", renderType)));
        assertNull(check(call(renderTypes, "debugFilledBox", renderType)));

        assertNotNull(check(field(compareOp, "ALWAYS_PASS", "L" + compareOp + ";")));
        assertNotNull(check(field(compareOp, "NOT_EQUAL", "L" + compareOp + ";")));
        assertNull(check(field(compareOp, "GREATER_THAN_OR_EQUAL", "L" + compareOp + ";")));

        assertNotNull(check(call("net/alpaka/addons/features/blockoverlay/BlockOverlayRenderTypes", "noDepthQuads", renderType)));
        assertNotNull(check(field("net/minecraft/client/renderer/RenderPipelines", "OUTLINE_NO_CULL", "Lcom/mojang/blaze3d/pipeline/RenderPipeline;")));
        assertNull(check(field("net/minecraft/client/renderer/RenderPipelines", "LINES", "Lcom/mojang/blaze3d/pipeline/RenderPipeline;")));
        assertNotNull(check(new FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/client/renderer/state/level/LevelRenderState",
                "shouldShowEntityOutlines", "Z")));
        // A pipeline of its own, with whatever depth test it likes.
        assertNotNull(check(new TypeInsnNode(Opcodes.NEW, "com/mojang/blaze3d/pipeline/DepthStencilState")));
        assertNotNull(check(call("com/mojang/blaze3d/pipeline/RenderPipeline$Builder", "withDepthStencilState",
                "(Lcom/mojang/blaze3d/pipeline/DepthStencilState;)Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;")));
        assertNotNull(check(call("com/mojang/blaze3d/pipeline/RenderPipeline", "builder",
                "([Lcom/mojang/blaze3d/pipeline/RenderPipeline$Snippet;)Lcom/mojang/blaze3d/pipeline/RenderPipeline$Builder;")));
        assertNotNull(check(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/alpaka/addons/mixin/RenderTypeInvoker", "alpaka$create",
                "()Lnet/minecraft/client/renderer/rendertype/RenderType;")));
        assertNotNull(check(call("net/minecraft/world/entity/Entity", "setGlowingTag", "(Z)V")));
        assertNotNull(check(call("net/minecraft/client/player/LocalPlayer", "setGlowingTag", "(Z)V")));
        assertNotNull(check(call("net/minecraft/client/renderer/OrderedSubmitNodeCollector", "submitNameTag",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/phys/Vec3;ILnet/minecraft/network/chat/Component;ZILnet/minecraft/client/renderer/state/level/CameraRenderState;)V")));
        assertNull(check(call("net/minecraft/client/renderer/OrderedSubmitNodeCollector", "submitText",
                "(Lcom/mojang/blaze3d/vertex/PoseStack;FFLnet/minecraft/util/FormattedCharSequence;ZLnet/minecraft/client/gui/Font$DisplayMode;IIII)V")));

        // Acting: any use of the game mode, the connection, sending, Fabric's interaction events.
        assertNotNull(check(field("net/minecraft/client/Minecraft", "gameMode", "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;")));
        assertNotNull(check(call("net/minecraft/client/multiplayer/MultiPlayerGameMode", "getPlayerMode", "()Lnet/minecraft/world/level/GameType;")));
        assertNotNull(check(call("net/minecraft/client/multiplayer/ClientPacketListener", "send", "(" + PACKET + ")V")));
        assertNotNull(check(call("net/minecraft/network/Connection", "isConnected", "()Z")));
        assertNotNull(check(field("net/fabricmc/fabric/api/event/player/UseBlockCallback", "EVENT", "Lnet/fabricmc/fabric/api/event/Event;")));
        assertNotNull(check(field("net/fabricmc/fabric/api/event/player/AttackEntityCallback", "EVENT", "Lnet/fabricmc/fabric/api/event/Event;")));
        assertNotNull(check(field("net/fabricmc/fabric/api/event/client/player/ClientPreAttackCallback", "EVENT", "Lnet/fabricmc/fabric/api/event/Event;")));
        assertNotNull(check(new TypeInsnNode(Opcodes.CHECKCAST, "net/minecraft/client/multiplayer/MultiPlayerGameMode")));
        assertNotNull(check(new LdcInsnNode(Type.getObjectType("net/minecraft/network/Connection"))));
        assertNull(check(call("net/minecraft/client/multiplayer/ClientPacketListener", "getListedOnlinePlayers", "()Ljava/util/Collection;")));
        // The one way a terminal click may leave.
        assertNull(check(call("net/alpaka/addons/compliance/Outbound", "menuClick",
                "(Lnet/minecraft/world/inventory/AbstractContainerMenu;IILnet/minecraft/world/inventory/ContainerInput;Lnet/alpaka/addons/compliance/Outbound$Cause;)Z")));

        // A method reference counts like a call.
        Handle seeThrough = new Handle(Opcodes.H_INVOKESTATIC, renderTypes, "textSeeThrough",
                "(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;", false);
        assertNotNull(check(new InvokeDynamicInsnNode("apply", "()Ljava/util/function/Function;",
                new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "()V", false),
                Type.getType("(Ljava/lang/Object;)Ljava/lang/Object;"), seeThrough, Type.getType("(Ljava/lang/Object;)Ljava/lang/Object;"))));

        // Mentioned in a signature only, the type still counts.
        ClassNode node = new ClassNode();
        node.name = "net/alpaka/addons/features/dungeon/Example";
        node.superName = "java/lang/Object";
        node.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "use", "(Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;)V", null, null));
        assertNotNull(forbidden(node));
        node.methods.clear();
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, "connection", "Lnet/minecraft/network/Connection;", null, null));
        assertNotNull(forbidden(node));
    }

    private static MethodInsnNode call(String owner, String name, String desc) {
        return new MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner, name, desc);
    }

    private static FieldInsnNode field(String owner, String name, String desc) {
        return new FieldInsnNode(Opcodes.GETSTATIC, owner, name, desc);
    }

    // --- the scan ---

    /** What in the class is forbidden, or null. */
    private static String forbidden(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES);
        return forbidden(node);
    }

    private static String forbidden(ClassNode node) {
        if (node.superName != null && forbiddenType(node.superName)) return node.name + " extends " + node.superName;
        for (String iface : node.interfaces) {
            if (forbiddenType(iface)) return node.name + " implements " + iface;
        }
        for (FieldNode field : node.fields) {
            String why = inDescriptor(field.desc);
            if (why != null) return node.name + "." + field.name + ": " + why;
        }
        for (MethodNode method : node.methods) {
            String why = inDescriptor(method.desc);
            if (why != null) return node.name + "." + method.name + ": " + why;
            if (method.instructions == null) continue;
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                why = check(insn);
                if (why != null) return node.name + "." + method.name + ": " + why;
            }
        }
        return null;
    }

    /** Why this instruction is forbidden, or null. */
    private static String check(AbstractInsnNode insn) {
        if (insn instanceof MethodInsnNode call) return member(call.owner, call.name, call.desc);
        if (insn instanceof FieldInsnNode field) return member(field.owner, field.name, field.desc);
        if (insn instanceof TypeInsnNode type) return inTypeName(type.desc);
        if (insn instanceof MultiANewArrayInsnNode array) return inDescriptor(array.desc);
        if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof Type type) return inType(type);
        if (insn instanceof InvokeDynamicInsnNode indy) {
            String why = inDescriptor(indy.desc);
            if (why != null) return why;
            for (Object arg : indy.bsmArgs) {
                if (arg instanceof Handle handle) why = member(handle.getOwner(), handle.getName(), handle.getDesc());
                else if (arg instanceof Type type) why = inType(type);
                if (why != null) return why + " (method reference)";
            }
        }
        return null;
    }

    private static String member(String owner, String name, String desc) {
        Set<String> names = FORBIDDEN_MEMBERS.get(owner);
        if (names != null && names.contains(name)) return owner + "." + name;
        if (FORBIDDEN_NAMES.contains(name)) return owner + "." + name;
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("seethrough") || lower.contains("see_through")) return owner + "." + name;
        if (name.equals("send") && desc.startsWith("(" + PACKET)) return owner + ".send";
        String why = inTypeName(owner);
        return why != null ? why : inDescriptor(desc);
    }

    private static String inDescriptor(String desc) {
        if (desc == null || desc.isEmpty()) return null;
        if (desc.startsWith("(")) {
            String why = inType(Type.getReturnType(desc));
            if (why != null) return why;
            for (Type arg : Type.getArgumentTypes(desc)) {
                why = inType(arg);
                if (why != null) return why;
            }
            return null;
        }
        return inType(Type.getType(desc));
    }

    private static String inType(Type type) {
        if (type.getSort() == Type.ARRAY) return inType(type.getElementType());
        if (type.getSort() == Type.METHOD) return inDescriptor(type.getDescriptor());
        if (type.getSort() != Type.OBJECT) return null;
        return inTypeName(type.getInternalName());
    }

    private static String inTypeName(String internalName) {
        if (internalName.startsWith("[")) return inDescriptor(internalName);
        return forbiddenType(internalName) ? internalName : null;
    }

    private static boolean forbiddenType(String internalName) {
        return FORBIDDEN_TYPES.contains(internalName) || internalName.startsWith(FABRIC_PLAYER_EVENTS);
    }

    /** Gradle's output folders for the main classes, Java and Kotlin, as the test sees them. */
    private static List<Path> classDirectories() {
        List<Path> dirs = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            String normalized = entry.replace(File.separatorChar, '/');
            if (normalized.endsWith("classes/java/main") || normalized.endsWith("classes/kotlin/main")) {
                dirs.add(Path.of(entry));
            }
        }
        if (dirs.isEmpty()) {
            dirs.add(Path.of("build/classes/java/main"));
            dirs.add(Path.of("build/classes/kotlin/main"));
        }
        return dirs;
    }
}

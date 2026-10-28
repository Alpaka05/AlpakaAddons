package net.alpaka.addons.compliance;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every place in the mod that can send something to the server, read from the compiled classes.
 *
 * The mod may only send a chat message or command as the direct result of the player pressing a
 * key, clicking or pressing Enter. Two features once broke that - a party-chat auto-reply and a
 * guild announcement fired from a timer - so a new call site has to be added to this list on
 * purpose, after checking that it only runs on such an action. Menu clicks count too: a mod that
 * clicks slots on its own is exactly what Hypixel bans for.
 *
 * The bytecode is scanned rather than the source: a pattern over the source missed Kotlin's
 * {@code ?.send(}, {@code getConnection().send(}, method references, gameplay packets and
 * anything outside the Java folder. In the bytecode each of those is a call or a handle to one of
 * a few methods, whatever the language and spelling.
 */
class OutboundBytecodeScanTest {
    /**
     * The classes allowed to send, each with why. Everything else that sends fails the build.
     */
    private static final Map<String, String> ALLOWED = Map.of(
            "compliance/Outbound",
            "the gate: commands for a key press, click or Enter, Quick Join's /skyblock and the Performance HUD's latency probe",
            "mixin/DownloadedPackSourceMixin",
            "answers a server resource pack that is already loaded the way vanilla answers one it just loaded",
            "mixin/ClientCommonPacketListenerPackCacheMixin",
            "hands such a pack on to be answered instead of prompting for it again");

    private static final String PACKET = "Lnet/minecraft/network/protocol/Packet;";

    /** Chat and commands, by name: a shadow or a duck interface calls them on an owner of its own. */
    private static final Set<String> CHAT = Set.of(
            "sendChat", "sendCommand", "sendUnattendedCommand", "sendSignedCommand", "handleChatInput");

    private static final String GAME_MODE = "net/minecraft/client/multiplayer/MultiPlayerGameMode";
    /** The methods of MultiPlayerGameMode that only read; every other one acts on the server. */
    private static final Set<String> GAME_MODE_READS = Set.of(
            "canHurtPlayer", "hasExperience", "hasMissTime", "isServerControlledInventory", "isSpectator",
            "getPreviousPlayerMode", "getPlayerMode", "isDestroying", "getDestroyStage");

    @Test
    void onlyReviewedClassesSendToTheServer() throws IOException {
        Map<String, String> found = new TreeMap<>();
        int scanned = 0;
        for (Path dir : classDirectories()) {
            Path root = dir.resolve("net/alpaka/addons");
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    if (!file.toString().endsWith(".class")) continue;
                    scanned++;
                    String sends = sends(Files.readAllBytes(file));
                    if (sends != null) found.putIfAbsent(topLevel(root.relativize(file)), sends);
                }
            }
        }
        assertTrue(scanned > 100, "Found only " + scanned + " compiled classes; the scan looked in the wrong place");
        assertEquals(new TreeSet<>(ALLOWED.keySet()), found.keySet(),
                "Something sends to the server without going through Outbound: " + found
                        + ". Route it through Outbound.command with the player's action as its cause.");
    }

    /** The checks themselves, on a few calls written out by hand. */
    @Test
    void recognisesEveryWayOfSending() {
        assertTrue(isSend(call("net/minecraft/client/multiplayer/ClientPacketListener", "send", "(" + PACKET + ")V")));
        assertTrue(isSend(call("net/minecraft/network/Connection", "send", "(" + PACKET + "Lio/netty/channel/ChannelFutureListener;)V")));
        assertTrue(isSend(call("net/alpaka/addons/mixin/SomeMixin", "sendCommand", "(Ljava/lang/String;)V")));
        assertTrue(isSend(call(GAME_MODE, "handleContainerInput", "(IIILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V")));
        assertTrue(isSend(call("net/minecraft/client/player/LocalPlayer", "swing", "(Lnet/minecraft/world/InteractionHand;)V")));
        assertTrue(isSend(new TypeInsnNode(Opcodes.NEW, "net/minecraft/network/protocol/game/ServerboundSwingPacket")));

        assertFalse(isSend(call(GAME_MODE, "canHurtPlayer", "()Z")));
        // The two-argument swing only animates; it is the one-argument one that tells the server.
        assertFalse(isSend(call("net/minecraft/client/player/LocalPlayer", "swing", "(Lnet/minecraft/world/InteractionHand;Z)V")));
        assertFalse(isSend(call("java/net/http/HttpClient", "send", "(Ljava/net/http/HttpRequest;Ljava/net/http/HttpResponse$BodyHandler;)Ljava/net/http/HttpResponse;")));
        assertFalse(isSend(call("net/alpaka/addons/features/notification/AlpakaNotifications", "send", "(Ljava/lang/String;Ljava/lang/String;)V")));
    }

    private static MethodInsnNode call(String owner, String name, String desc) {
        return new MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner, name, desc);
    }

    /** What in the class sends, or null if nothing does. Method references count like calls. */
    private static String sends(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES);
        for (MethodNode method : node.methods) {
            if (method.instructions == null) continue;
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (isSend(insn)) return node.name + "." + method.name;
                if (insn instanceof InvokeDynamicInsnNode indy) {
                    for (Object arg : indy.bsmArgs) {
                        if (arg instanceof Handle handle
                                && isSendCall(handle.getOwner(), handle.getName(), handle.getDesc())) {
                            return node.name + "." + method.name + " (method reference)";
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isSend(AbstractInsnNode insn) {
        if (insn instanceof MethodInsnNode call) return isSendCall(call.owner, call.name, call.desc);
        // A packet for the server built here is a packet about to be sent from here.
        if (insn instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW) {
            String name = type.desc;
            return name.startsWith("net/minecraft/network/protocol/")
                    && name.substring(name.lastIndexOf('/') + 1).startsWith("Serverbound");
        }
        return false;
    }

    private static boolean isSendCall(String owner, String name, String desc) {
        // A handle can point at a field too; only methods send.
        if (!desc.startsWith("(")) return false;
        Type[] args = Type.getArgumentTypes(desc);
        if (name.equals("send") && args.length > 0 && args[0].getDescriptor().equals(PACKET)) return true;
        if (CHAT.contains(name)) return true;
        if (owner.equals(GAME_MODE)) return !GAME_MODE_READS.contains(name) && !name.equals("<init>");
        if (owner.equals("net/minecraft/client/player/LocalPlayer")) {
            return (name.equals("swing") && desc.equals("(Lnet/minecraft/world/InteractionHand;)V"))
                    || name.equals("drop") || name.equals("closeContainer");
        }
        if (name.equals("slotClicked") && owner.startsWith("net/minecraft/client/gui/screens/inventory/")) return true;
        // Server resource pack answers.
        if (owner.equals("net/minecraft/client/resources/server/PackLoadFeedback")) return true;
        return owner.equals("net/minecraft/client/resources/server/DownloadedPackSource") && name.equals("pushPack");
    }

    /** The class a compiled file belongs to: a nested or synthetic class counts as its outer one. */
    private static String topLevel(Path relative) {
        String name = relative.toString().replace(File.separatorChar, '/');
        name = name.substring(0, name.length() - ".class".length());
        int nested = name.indexOf('$');
        return nested < 0 ? name : name.substring(0, nested);
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

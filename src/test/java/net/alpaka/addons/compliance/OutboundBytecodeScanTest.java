package net.alpaka.addons.compliance;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
 * purpose, after checking that it only runs on such an action. (The one opt-in exception, party
 * chat answers to "!tps"-style requests, still goes through Outbound with a cause of its own.)
 * Menu clicks count too: a mod that clicks slots, closes menus or presses keys on its own is
 * exactly what Hypixel bans for.
 *
 * The bytecode is scanned rather than the source: a pattern over the source missed Kotlin's
 * {@code ?.send(}, {@code getConnection().send(}, method references, gameplay packets and
 * anything outside the Java folder. In the bytecode each of those is a call or a handle to one of
 * a few methods, whatever the language and spelling. A call on one of the mod's own classes counts
 * as a call on the vanilla class it extends, a call on a mixin or its invoker as one on the class
 * the mixin targets, so a subclass or an accessor does not hide a {@code slotClicked}.
 *
 * Reflection counts as sending: it can reach any of these without naming them.
 */
class OutboundBytecodeScanTest {
    /**
     * The methods allowed to send, each with why: {@code class.method}, with a lambda or local class
     * counted as the method it is written in. Everything else that sends fails the build, and so
     * does an entry here that no longer sends. {@code class.*} is a whole dev-only class (see
     * {@link #DEV_ONLY}).
     */
    private static final Map<String, String> ALLOWED = Map.ofEntries(
            Map.entry("compliance/Outbound.command",
                    "the gate for commands: a key press, click or Enter, or an opt-in party chat answer"),
            Map.entry("compliance/Outbound.dialogueResponse",
                    "an NPC dialog choice for a key pressed on its prompt"),
            Map.entry("compliance/Outbound.menuClick",
                    "a slot click for a click on the mod's drawing of that slot"),
            Map.entry("compliance/Outbound.latencyProbe",
                    "vanilla's ping request for the Performance HUD, at most every 5 s"),
            Map.entry("compliance/Outbound.quickJoinSkyblock",
                    "Quick Join's opt-in /skyblock after joining Hypixel, at most every 30 s"),
            Map.entry("mixin/DownloadedPackSourceMixin.alpaka$skipLoadedPack",
                    "answers a server resource pack that is already loaded the way vanilla answers one it just loaded"),
            Map.entry("mixin/ClientCommonPacketListenerPackCacheMixin.alpaka$skipPromptForLoadedPack",
                    "hands such a pack on to be answered instead of prompting for it again"),
            Map.entry("features/market/MarketInputScreen.close",
                    "closes the sign bar for Enter or a click; vanilla sends the lines as it closes"),
            Map.entry("features/market/MarketInputScreen.removed",
                    "vanilla's sign update as the bar closes, which only Enter, a click, Escape or the sign going away do"),
            Map.entry("features/market/MarketScreen.drawHeader",
                    "the close button's click closes the menu"),
            Map.entry("features/market/MarketScreen.keyPressed",
                    "Escape or the inventory key closes the menu"),
            Map.entry("features/leapmenu/LeapMenuScreen.keyPressed",
                    "Escape or the inventory key closes the menu"),
            Map.entry("features/dungeon/terminal/TerminalScreen.mouseClicked",
                    "the close button's click closes the terminal"),
            Map.entry("features/dungeon/terminal/TerminalScreen.keyPressed",
                    "Escape or the inventory key closes the terminal"),
            Map.entry("features/dungeon/terminal/TerminalScreen.onClose",
                    "practice mode closes only its own window, nothing is open on the server"),
            Map.entry("features/wheel/CommandWheelScreen.close",
                    "closes the wheel, a screen of its own, when its key is released or a segment clicked"),
            Map.entry("compat/GnetumCompat.resolve",
                    "looks up Gnetum's HUD cache switches; reads and toggles that cache, nothing on the server"),
            Map.entry("compat/MacOSInputFixesCompat.resolve",
                    "looks up MacOS Input Fixes' duplicate-key filter; only drops a key press, never makes one"),
            Map.entry("config/ConfigSanitizer.sanitizeField",
                    "puts a default back into a broken field of the mod's own config"),
            Map.entry("config/ConfigSanitizer.fresh",
                    "makes a fresh copy of one of the mod's own config classes to take defaults from"),
            Map.entry("client/DevScreenshotTour.*",
                    "the dev client's screenshot tour; register() does nothing outside the development environment"));

    /**
     * Classes for the dev client only, started by an environment variable. Their {@code register}
     * must also ask Fabric whether this is the development environment, so a released jar never
     * runs them, whatever the variables on the player's machine say.
     */
    private static final Set<String> DEV_ONLY = Set.of("client/DevScreenshotTour", "client/DevClutterBenchmark");

    private static final String PACKAGE = "net/alpaka/addons/";

    private static final String PACKET = "Lnet/minecraft/network/protocol/Packet;";
    private static final String PAYLOAD = "Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;";

    /** Chat and commands, by name: a shadow or a duck interface calls them on an owner of its own. */
    private static final Set<String> CHAT = Set.of(
            "sendChat", "sendCommand", "sendUnattendedCommand", "sendSignedCommand", "handleChatInput");

    private static final String GAME_MODE = "net/minecraft/client/multiplayer/MultiPlayerGameMode";
    /** The methods of MultiPlayerGameMode that only read; every other one acts on the server. */
    private static final Set<String> GAME_MODE_READS = Set.of(
            "canHurtPlayer", "hasExperience", "hasMissTime", "isServerControlledInventory", "isSpectator",
            "getPreviousPlayerMode", "getPlayerMode", "isDestroying", "getDestroyStage");

    private static final String SCREENS = "net/minecraft/client/gui/screens/";
    /** Container, sign and book screens: closing one tells the server, or sends what was typed. */
    private static final String MENU_SCREENS = SCREENS + "inventory/";
    private static final String SCREEN = SCREENS + "Screen";
    /** What a click on a chat component does: run its command, or hand its custom action back. */
    private static final Set<String> SCREEN_CLICK_ACTIONS = Set.of(
            "clickCommandAction", "defaultHandleGameClickEvent", "defaultHandleClickEvent", "handleComponentClicked");

    private static final String KEY_MAPPING = "net/minecraft/client/KeyMapping";
    private static final String INVENTORY = "net/minecraft/world/entity/player/Inventory";

    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
    private static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";

    @Test
    void onlyReviewedMethodsSendToTheServer() throws IOException {
        Mod mod = Mod.load();
        assertTrue(mod.classes.size() > 100,
                "Found only " + mod.classes.size() + " compiled classes; the scan looked in the wrong place");

        Map<String, String> found = new TreeMap<>();
        for (ClassNode node : mod.classes.values()) mod.sends(node, found);

        Map<String, String> unreviewed = new TreeMap<>();
        Set<String> used = new TreeSet<>();
        for (Map.Entry<String, String> e : found.entrySet()) {
            String entry = allowedBy(e.getKey());
            if (entry == null) unreviewed.put(e.getKey(), e.getValue());
            else used.add(entry);
        }
        assertEquals(Map.of(), unreviewed,
                "Something sends to the server without going through Outbound. Route it through Outbound with "
                        + "the player's action as its cause, or, after checking it only runs on such an action, "
                        + "add the method to ALLOWED with why.");
        Set<String> stale = new TreeSet<>(ALLOWED.keySet());
        stale.removeAll(used);
        assertEquals(Set.of(), stale, "These ALLOWED entries no longer send; remove them");
    }

    /** A dev-only class may send because a released jar never starts it. */
    @Test
    void devOnlyClassesStartOnlyInTheDevelopmentEnvironment() throws IOException {
        Mod mod = Mod.load();
        for (String dev : DEV_ONLY) {
            ClassNode node = mod.classes.get(PACKAGE + dev);
            assertTrue(node != null, dev + " not found");
            MethodNode register = node.methods.stream().filter(m -> m.name.equals("register")).findFirst().orElse(null);
            assertTrue(register != null, dev + " has no register()");
            boolean asks = false;
            for (AbstractInsnNode insn = register.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode call && call.owner.equals("net/fabricmc/loader/api/FabricLoader")
                        && call.name.equals("isDevelopmentEnvironment")) {
                    asks = true;
                    break;
                }
            }
            assertTrue(asks, dev + ".register() must return unless FabricLoader.isDevelopmentEnvironment()");
        }
        for (String entry : ALLOWED.keySet()) {
            if (entry.endsWith(".*")) {
                assertTrue(DEV_ONLY.contains(entry.substring(0, entry.length() - 2)),
                        entry + ": only a DEV_ONLY class may be allowed as a whole");
            }
        }
    }

    /** The checks themselves, on a few calls written out by hand. */
    @Test
    void recognisesEveryWayOfSending() {
        Mod mod = new Mod();
        assertTrue(mod.isSend(call("net/minecraft/client/multiplayer/ClientPacketListener", "send", "(" + PACKET + ")V")));
        assertTrue(mod.isSend(call("net/minecraft/network/Connection", "send", "(" + PACKET + "Lio/netty/channel/ChannelFutureListener;)V")));
        assertTrue(mod.isSend(call("net/alpaka/addons/mixin/SomeMixin", "sendCommand", "(Ljava/lang/String;)V")));
        assertTrue(mod.isSend(call(GAME_MODE, "handleContainerInput", "(IIILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V")));
        assertTrue(mod.isSend(call("net/minecraft/client/player/LocalPlayer", "swing", "(Lnet/minecraft/world/InteractionHand;)V")));
        assertTrue(mod.isSend(new TypeInsnNode(Opcodes.NEW, "net/minecraft/network/protocol/game/ServerboundSwingPacket")));
        // Fabric's own networking.
        assertTrue(mod.isSend(new MethodInsnNode(Opcodes.INVOKESTATIC, "net/fabricmc/fabric/api/client/networking/v1/ClientPlayNetworking",
                "send", "(" + PAYLOAD + ")V")));
        assertTrue(mod.isSend(call("net/fabricmc/fabric/api/networking/v1/PacketSender", "sendPacket", "(" + PAYLOAD + ")V")));
        // A chat component's click: its command, or its custom action.
        assertTrue(mod.isSend(new MethodInsnNode(Opcodes.INVOKESTATIC, SCREEN, "clickCommandAction",
                "(Lnet/minecraft/client/player/LocalPlayer;Ljava/lang/String;Lnet/minecraft/client/gui/screens/Screen;)V")));
        assertTrue(mod.isSend(call(SCREENS + "ChatScreen", "handleComponentClicked", "(Lnet/minecraft/network/chat/Style;Z)Z")));
        // Closing a menu or sign tells the server.
        assertTrue(mod.isSend(call(MENU_SCREENS + "AbstractContainerScreen", "onClose", "()V")));
        assertTrue(mod.isSend(call(MENU_SCREENS + "SignEditScreen", "removed", "()V")));
        assertFalse(mod.isSend(call(MENU_SCREENS + "ContainerScreen", "removed", "()V")));
        assertTrue(mod.isSend(after(new InsnNode(Opcodes.ACONST_NULL),
                call("net/minecraft/client/gui/Gui", "setScreen", "(Lnet/minecraft/client/gui/screens/Screen;)V"))));
        // Pressing keys and changing the held slot.
        assertTrue(mod.isSend(new MethodInsnNode(Opcodes.INVOKESTATIC, KEY_MAPPING, "click", "(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V")));
        assertTrue(mod.isSend(new MethodInsnNode(Opcodes.INVOKESTATIC, KEY_MAPPING, "set", "(Lcom/mojang/blaze3d/platform/InputConstants$Key;Z)V")));
        assertTrue(mod.isSend(after(new InsnNode(Opcodes.ICONST_1), call(KEY_MAPPING, "setDown", "(Z)V"))));
        assertTrue(mod.isSend(new FieldInsnNode(Opcodes.PUTFIELD, KEY_MAPPING, "clickCount", "I")));
        assertTrue(mod.isSend(call(INVENTORY, "setSelectedSlot", "(I)V")));
        assertTrue(mod.isSend(new FieldInsnNode(Opcodes.PUTFIELD, INVENTORY, "selected", "I")));
        // Reflection reaches all of the above without naming it.
        assertTrue(mod.isSend(call("java/lang/reflect/Method", "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;")));
        assertTrue(mod.isSend(call("java/lang/invoke/MethodHandles$Lookup", "findVirtual",
                "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;")));
        assertTrue(mod.isSend(call("java/lang/Class", "getDeclaredMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;")));
        assertTrue(mod.isSend(call("java/lang/reflect/Field", "setBoolean", "(Ljava/lang/Object;Z)V")));

        assertFalse(mod.isSend(call(GAME_MODE, "canHurtPlayer", "()Z")));
        // The two-argument swing only animates; it is the one-argument one that tells the server.
        assertFalse(mod.isSend(call("net/minecraft/client/player/LocalPlayer", "swing", "(Lnet/minecraft/world/InteractionHand;Z)V")));
        assertFalse(mod.isSend(call("java/net/http/HttpClient", "send", "(Ljava/net/http/HttpRequest;Ljava/net/http/HttpResponse$BodyHandler;)Ljava/net/http/HttpResponse;")));
        assertFalse(mod.isSend(call("net/alpaka/addons/features/notification/AlpakaNotifications", "send", "(Ljava/lang/String;Ljava/lang/String;)V")));
        // Letting go of a key presses nothing; opening another screen closes nothing on purpose.
        assertFalse(mod.isSend(after(new InsnNode(Opcodes.ICONST_0), call(KEY_MAPPING, "setDown", "(Z)V"))));
        assertFalse(mod.isSend(call(KEY_MAPPING, "consumeClick", "()Z")));
        assertFalse(mod.isSend(after(new InsnNode(Opcodes.ALOAD), call("net/minecraft/client/gui/Gui", "setScreen", "(Lnet/minecraft/client/gui/screens/Screen;)V"))));
        assertFalse(mod.isSend(call(INVENTORY, "getSelectedSlot", "()I")));
        assertFalse(mod.isSend(call(SCREEN, "onClose", "()V")));
        assertFalse(mod.isSend(call("java/lang/Class", "getName", "()Ljava/lang/String;")));
        assertFalse(mod.isSend(call("java/lang/Class", "getDeclaredFields", "()[Ljava/lang/reflect/Field;")));
    }

    /** A subclass, a mixin and an invoker do not hide a call from the scan. */
    @Test
    void seesThroughSubclassesMixinsAndInvokers() {
        Mod mod = new Mod();
        ClassNode screen = new ClassNode();
        screen.name = PACKAGE + "features/Example";
        screen.superName = MENU_SCREENS + "AbstractContainerScreen";
        mod.add(screen);
        assertTrue(mod.isSend(call(screen.name, "slotClicked",
                "(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V")));
        assertTrue(mod.isSend(call(screen.name, "onClose", "()V")));

        ClassNode invoker = new ClassNode();
        invoker.name = PACKAGE + "mixin/ExampleInvoker";
        invoker.superName = "java/lang/Object";
        invoker.invisibleAnnotations = new ArrayList<>(List.of(annotation(MIXIN, "value", List.of(Type.getObjectType("net/minecraft/client/Minecraft")))));
        MethodNode attack = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "alpaka$attack", "()Z", null, null);
        attack.invisibleAnnotations = new ArrayList<>(List.of(annotation(INVOKER, "value", "startAttack")));
        MethodNode selected = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "alpaka$setSelected", "(I)V", null, null);
        selected.invisibleAnnotations = new ArrayList<>(List.of(annotation(ACCESSOR, "value", "selected")));
        invoker.methods.add(attack);
        invoker.methods.add(selected);
        mod.add(invoker);
        assertTrue(mod.isSend(new MethodInsnNode(Opcodes.INVOKEINTERFACE, invoker.name, "alpaka$attack", "()Z")));

        ClassNode inventory = new ClassNode();
        inventory.name = PACKAGE + "mixin/InventoryAccessor";
        inventory.superName = "java/lang/Object";
        inventory.invisibleAnnotations = new ArrayList<>(List.of(annotation(MIXIN, "value", List.of(Type.getObjectType(INVENTORY)))));
        inventory.methods.add(selected);
        mod.add(inventory);
        assertTrue(mod.isSend(new MethodInsnNode(Opcodes.INVOKEINTERFACE, inventory.name, "alpaka$setSelected", "(I)V")));
    }

    private static AnnotationNode annotation(String desc, String key, Object value) {
        AnnotationNode node = new AnnotationNode(desc);
        node.values = new ArrayList<>(List.of(key, value));
        return node;
    }

    private static MethodInsnNode call(String owner, String name, String desc) {
        return new MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner, name, desc);
    }

    /** {@code insn} with {@code before} right ahead of it, as the compiler would leave them. */
    private static AbstractInsnNode after(AbstractInsnNode before, AbstractInsnNode insn) {
        InsnList list = new InsnList();
        list.add(before);
        list.add(insn);
        return insn;
    }

    /** The ALLOWED entry that covers a found {@code class.method}, or null. */
    private static String allowedBy(String found) {
        if (ALLOWED.containsKey(found)) return found;
        for (String entry : ALLOWED.keySet()) {
            if (!entry.endsWith(".*")) continue;
            String cls = entry.substring(0, entry.length() - 2);
            if (found.startsWith(cls + ".") || found.startsWith(cls + "$")) return entry;
        }
        return null;
    }

    /** The mod's compiled classes, with what is needed to see through subclasses and mixins. */
    private static final class Mod {
        final Map<String, ClassNode> classes = new HashMap<>();
        /** Mixin class to the classes it targets. */
        final Map<String, List<String>> mixinTargets = new HashMap<>();

        static Mod load() throws IOException {
            Mod mod = new Mod();
            for (Path dir : classDirectories()) {
                Path root = dir.resolve(PACKAGE);
                if (!Files.isDirectory(root)) continue;
                try (Stream<Path> files = Files.walk(root)) {
                    for (Path file : (Iterable<Path>) files::iterator) {
                        if (!file.toString().endsWith(".class")) continue;
                        ClassNode node = new ClassNode();
                        new ClassReader(Files.readAllBytes(file)).accept(node, ClassReader.SKIP_FRAMES);
                        mod.add(node);
                    }
                }
            }
            return mod;
        }

        void add(ClassNode node) {
            classes.put(node.name, node);
            AnnotationNode mixin = annotation(node.invisibleAnnotations, MIXIN);
            if (mixin == null) return;
            List<String> targets = new ArrayList<>();
            Object value = value(mixin, "value");
            if (value instanceof List<?> types) for (Object t : types) targets.add(((Type) t).getInternalName());
            Object names = value(mixin, "targets");
            if (names instanceof List<?> list) for (Object n : list) targets.add(((String) n).replace('.', '/'));
            mixinTargets.put(node.name, targets);
        }

        /** Adds every sending method of {@code node} to {@code found}, as {@code class.method} to what it does. */
        void sends(ClassNode node, Map<String, String> found) {
            for (MethodNode method : node.methods) {
                if (method.instructions == null) continue;
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    String what = null;
                    if (isSend(insn)) what = describe(insn);
                    else if (insn instanceof InvokeDynamicInsnNode indy) {
                        for (Object arg : indy.bsmArgs) {
                            if (arg instanceof Handle h && isSendCall(h.getOwner(), h.getName(), h.getDesc(), null)) {
                                what = h.getOwner() + "." + h.getName() + " (method reference)";
                            }
                        }
                    }
                    if (what != null) found.putIfAbsent(key(node, method), what);
                }
            }
        }

        boolean isSend(AbstractInsnNode insn) {
            if (insn instanceof MethodInsnNode call) return isSendCall(call.owner, call.name, call.desc, insn);
            if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD) {
                for (String owner : resolve(field.owner)) if (isStateWrite(owner, field.name)) return true;
                return false;
            }
            // A packet for the server built here is a packet about to be sent from here.
            if (insn instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW) {
                String name = type.desc;
                return name.startsWith("net/minecraft/network/protocol/")
                        && name.substring(name.lastIndexOf('/') + 1).startsWith("Serverbound");
            }
            return false;
        }

        /**
         * Whether calling {@code owner.name} sends, where {@code insn} is the call (null for a method
         * reference). A call on one of the mod's classes is checked as a call on what it extends or
         * targets, and an invoker or accessor as the method or field it stands for.
         */
        boolean isSendCall(String owner, String name, String desc, AbstractInsnNode insn) {
            // A handle can point at a field too; only methods send.
            if (!desc.startsWith("(")) return false;
            if (isReflection(owner, name)) return true;
            // Names that send on any owner, a duck interface or shadow included.
            Type[] args = Type.getArgumentTypes(desc);
            if (name.equals("send") && args.length > 0 && args[0].getDescriptor().equals(PACKET)) return true;
            if (name.startsWith("send") && desc.contains(PAYLOAD)) return true;
            if (CHAT.contains(name)) return true;
            if ((name.equals("setScreen") || name.equals("setScreenAndShow")) && isNullArgument(insn)) return true;

            MethodNode member = member(owner, name, desc);
            String target = targetName(member, INVOKER);
            String field = targetName(member, ACCESSOR);
            for (String resolved : resolve(owner)) {
                if (field != null) {
                    if (Type.getReturnType(desc) == Type.VOID_TYPE && args.length == 1 && isStateWrite(resolved, field)) return true;
                    continue;
                }
                if (isVanillaSend(resolved, target != null ? target : name, desc, insn)) return true;
            }
            return false;
        }

        private boolean isVanillaSend(String owner, String name, String desc, AbstractInsnNode insn) {
            if (owner.startsWith("net/fabricmc/fabric/api/") && owner.contains("/networking/")
                    && name.startsWith("send")) return true;
            if (owner.equals(GAME_MODE)) return !GAME_MODE_READS.contains(name) && !name.equals("<init>");
            if (owner.equals("net/minecraft/client/player/LocalPlayer")) {
                return (name.equals("swing") && desc.equals("(Lnet/minecraft/world/InteractionHand;)V"))
                        || name.equals("drop") || name.equals("closeContainer");
            }
            if (owner.equals("net/minecraft/client/Minecraft")) {
                return name.equals("startAttack") || name.equals("continueAttack") || name.equals("startUseItem")
                        || name.equals("pickBlockOrEntity") || name.equals("handleKeybinds");
            }
            if (owner.startsWith(SCREENS) && SCREEN_CLICK_ACTIONS.contains(name)) return true;
            if (owner.startsWith(MENU_SCREENS)) {
                // A container tells the server it closed in onClose; a sign sends its lines in
                // removed, which closing it by any way leads to.
                if (name.equals("slotClicked") || name.equals("onClose")) return true;
                if (name.equals("removed") && owner.contains("Sign")) return true;
            }
            if (owner.startsWith("net/minecraft/client/") && owner.endsWith("KeyMapping")) {
                if (name.equals("click") || name.equals("set")) return true;
                // Letting go of a key presses nothing.
                if (name.equals("setDown")) return insn == null || previous(insn) == null
                        || previous(insn).getOpcode() != Opcodes.ICONST_0;
            }
            if (owner.equals(INVENTORY) && name.equals("setSelectedSlot")) return true;
            // Server resource pack answers.
            if (owner.equals("net/minecraft/client/resources/server/PackLoadFeedback")) return true;
            return owner.equals("net/minecraft/client/resources/server/DownloadedPackSource") && name.equals("pushPack");
        }

        /** Fields whose change the game turns into a key press or a packet. */
        private static boolean isStateWrite(String owner, String field) {
            if (owner.startsWith("net/minecraft/client/") && owner.endsWith("KeyMapping")) {
                return field.equals("isDown") || field.equals("clickCount");
            }
            return owner.equals(INVENTORY) && field.equals("selected");
        }

        private static boolean isReflection(String owner, String name) {
            return switch (owner) {
                case "java/lang/reflect/Method", "java/lang/reflect/Constructor" ->
                        name.equals("invoke") || name.equals("newInstance");
                case "java/lang/reflect/Field" -> name.startsWith("set") && !name.equals("setAccessible");
                case "java/lang/invoke/MethodHandles$Lookup" -> name.startsWith("find") || name.startsWith("unreflect");
                // Looking a method up by name; listing fields or reading one only reads.
                case "java/lang/Class" -> name.startsWith("getDeclaredMethod") || name.startsWith("getMethod");
                default -> owner.startsWith("kotlin/reflect/") && (name.equals("call") || name.equals("callBy"));
            };
        }

        /** The vanilla classes a call on {@code owner} reaches: itself, or what a mod class extends or targets. */
        Set<String> resolve(String owner) {
            Set<String> out = new LinkedHashSet<>();
            resolveInto(owner, out, 0);
            return out;
        }

        private void resolveInto(String owner, Set<String> out, int depth) {
            if (depth > 20) return;
            List<String> targets = mixinTargets.get(owner);
            if (targets != null) {
                for (String target : targets) resolveInto(target, out, depth + 1);
                return;
            }
            ClassNode node = classes.get(owner);
            if (node != null && node.superName != null) {
                resolveInto(node.superName, out, depth + 1);
                return;
            }
            out.add(owner);
        }

        private MethodNode member(String owner, String name, String desc) {
            ClassNode node = classes.get(owner);
            if (node == null) return null;
            for (MethodNode m : node.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
            return null;
        }

        /** What an @Invoker or @Accessor method stands for, from its annotation or, as Mixin does, its name. */
        private static String targetName(MethodNode method, String annotation) {
            if (method == null) return null;
            AnnotationNode node = annotation(method.invisibleAnnotations, annotation);
            if (node == null) return null;
            Object value = value(node, "value");
            if (value instanceof String s && !s.isEmpty()) return s;
            String name = method.name.substring(method.name.indexOf('$') + 1);
            for (String prefix : new String[] {"invoke", "call", "get", "set", "is"}) {
                if (name.startsWith(prefix) && name.length() > prefix.length()) {
                    return Character.toLowerCase(name.charAt(prefix.length())) + name.substring(prefix.length() + 1);
                }
            }
            return name;
        }

        private static AnnotationNode annotation(List<AnnotationNode> annotations, String desc) {
            if (annotations == null) return null;
            for (AnnotationNode a : annotations) if (a.desc.equals(desc)) return a;
            return null;
        }

        private static Object value(AnnotationNode node, String key) {
            if (node.values == null) return null;
            for (int i = 0; i + 1 < node.values.size(); i += 2) {
                if (node.values.get(i).equals(key)) return node.values.get(i + 1);
            }
            return null;
        }

        private static boolean isNullArgument(AbstractInsnNode insn) {
            AbstractInsnNode before = insn == null ? null : previous(insn);
            return before != null && before.getOpcode() == Opcodes.ACONST_NULL;
        }

        /** The instruction before {@code insn}, skipping labels, line numbers and frames. */
        private static AbstractInsnNode previous(AbstractInsnNode insn) {
            AbstractInsnNode p = insn.getPrevious();
            while (p != null && p.getOpcode() < 0) p = p.getPrevious();
            return p;
        }

        private static String describe(AbstractInsnNode insn) {
            if (insn instanceof MethodInsnNode call) return call.owner + "." + call.name;
            if (insn instanceof FieldInsnNode field) return field.owner + "." + field.name + " (write)";
            if (insn instanceof TypeInsnNode type) return "new " + type.desc;
            return insn.toString();
        }

        /**
         * {@code class.method} for a method, relative to the mod's package. A lambda counts as the
         * method it is written in, and so does a local or anonymous class with all its methods.
         */
        private static String key(ClassNode node, MethodNode method) {
            String cls = node.name;
            String name = method.name;
            if (node.outerMethod != null && node.outerClass != null) {
                cls = node.outerClass;
                name = node.outerMethod;
            }
            return cls.substring(PACKAGE.length()) + "." + enclosing(name);
        }

        /** The method a compiled lambda was written in: Java's {@code lambda$foo$0}, Kotlin's {@code foo$lambda$0}. */
        private static String enclosing(String name) {
            if (name.startsWith("lambda$")) {
                int end = name.indexOf('$', "lambda$".length());
                return end < 0 ? name : name.substring("lambda$".length(), end);
            }
            int kotlin = name.indexOf("$lambda");
            return kotlin > 0 ? name.substring(0, kotlin) : name;
        }
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

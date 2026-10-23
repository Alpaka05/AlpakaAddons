package net.alpaka.addons.compat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HookCheckTest {
    private static final String MIXIN = "net.alpaka.addons.mixin.GameRendererMixin";
    private static final String HANDLER = "alpaka$markFrameGuiPass";

    /** A target class with the handler merged in as Mixin names it, and a method that may call it. */
    private static ClassNode target(String name) {
        ClassNode node = new ClassNode();
        node.name = name.replace('.', '/');
        node.methods.add(new MethodNode(Opcodes.ACC_PRIVATE, "wrapOperation$zza000$" + HANDLER, "()V", null, null));
        MethodNode render = new MethodNode(Opcodes.ACC_PUBLIC, "render", "()V", null, null);
        render.instructions.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(render);
        return node;
    }

    /** What MixinExtras does after the plugin has seen the class: the call to the handler goes in. */
    private static void wrap(ClassNode node) {
        MethodNode render = node.methods.get(1);
        render.instructions.insert(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.name,
                "wrapOperation$zza000$" + HANDLER, "()V", false));
    }

    @Test
    void aWrapperAddedAfterThePluginSawTheClassCountsAsApplied() {
        List<String> reported = new ArrayList<>();
        HookCheck check = new HookCheck(reported::add);
        ClassNode gameRenderer = target("a.GameRenderer");

        check.applied(MIXIN, Set.of("a.GameRenderer"), "a.GameRenderer", gameRenderer, List.of(HANDLER));
        wrap(gameRenderer);
        check.checkFinished("b.NextClass");

        assertEquals(List.of(), reported);
    }

    @Test
    void aHandlerNothingCallsIsReportedOnceItsClassIsDone() {
        List<String> reported = new ArrayList<>();
        HookCheck check = new HookCheck(reported::add);

        check.applied(MIXIN, Set.of("a.GameRenderer"), "a.GameRenderer", target("a.GameRenderer"), List.of(HANDLER));
        // Still being worked on: another mixin of the same class must not have it looked at yet.
        check.checkFinished("a.GameRenderer");
        assertEquals(List.of(), reported);

        check.checkFinished("b.NextClass");
        assertEquals(List.of("GameRendererMixin." + HANDLER), reported);

        // Told once.
        check.checkFinished("c.Later");
        assertEquals(1, reported.size());
    }

    @Test
    void aMixinWithTwoTargetsCountsWhenEitherCallsIt() {
        List<String> reported = new ArrayList<>();
        HookCheck check = new HookCheck(reported::add);
        ClassNode first = target("a.First");
        ClassNode second = target("a.Second");
        wrap(second);

        check.applied(MIXIN, Set.of("a.First", "a.Second"), "a.First", first, List.of(HANDLER));
        check.checkFinished("a.Second");
        assertEquals(List.of(), reported, "judged before its second target was seen");

        check.applied(MIXIN, Set.of("a.First", "a.Second"), "a.Second", second, List.of(HANDLER));
        check.checkFinished("b.NextClass");
        assertEquals(List.of(), reported);
    }

    @Test
    void theLastClassesAreLookedAtOnceTheyAreDefined() {
        List<String> reported = new ArrayList<>();
        HookCheck check = new HookCheck(reported::add);
        ClassNode gameRenderer = target("a.GameRenderer");
        ClassNode broken = target("a.Broken");

        check.applied(MIXIN, Set.of("a.GameRenderer"), "a.GameRenderer", gameRenderer, List.of(HANDLER));
        check.applied("net.alpaka.addons.mixin.BrokenMixin", Set.of("a.Broken"), "a.Broken", broken, List.of(HANDLER));
        List<String> awaited = new ArrayList<>();
        check.checkAll(className -> {
            awaited.add(className);
            // Another thread finishing the class while this one waits for it.
            if (className.equals("a.GameRenderer")) wrap(gameRenderer);
            return !className.equals("a.Broken");
        });

        assertEquals(List.of("a.GameRenderer", "a.Broken"), awaited);
        assertEquals(List.of(), reported, "a class that failed to load proves nothing");
    }

    @Test
    void callsThroughAMethodHandleCount() {
        ClassNode node = target("a.Hud");
        MethodNode render = node.methods.get(1);
        render.instructions.insert(new org.objectweb.asm.tree.InvokeDynamicInsnNode("call", "()Ljava/lang/Runnable;",
                new org.objectweb.asm.Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
                        "()V", false),
                new org.objectweb.asm.Handle(Opcodes.H_INVOKESPECIAL, node.name, "wrapOperation$zza000$" + HANDLER,
                        "()V", false)));

        assertTrue(HookCheck.called(node, List.of(HANDLER)).contains(HANDLER));
    }
}

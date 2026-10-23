package net.alpaka.addons.compat;

import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Which optional hooks found nothing to hook into, for {@link OptionalHooksPlugin}.
 *
 * A target class is noted as a mixin goes into it and looked at only once Mixin is done with that
 * class. MixinExtras puts its wrapping injectors (WrapOperation, WrapWithCondition,
 * ModifyExpressionValue, WrapMethod) into the class in a pass of its own, after the config plugins
 * have seen it; looked at right away, every one of them read as missing. Mixin works on one class
 * at a time, so once the next class comes along, the ones before it are finished
 * ({@link #checkFinished}). The last ones are looked at from outside, once the game is up
 * ({@link #checkAll}).
 */
final class HookCheck {
    /** A target a mixin went into, with that mixin's handlers, waiting to be looked at. */
    private record Waiting(String mixin, String target, ClassNode targetClass, List<String> handlers) {}

    private final Consumer<String> report;
    private final List<Waiting> waiting = new ArrayList<>();
    /** Per mixin: the handlers found called in the targets looked at so far. */
    private final Map<String, Set<String>> found = new HashMap<>();
    /** Per mixin: the targets not looked at yet. */
    private final Map<String, Set<String>> targetsLeft = new HashMap<>();

    /** {@code report} is handed "Mixin.handler" for every handler that found nothing. */
    HookCheck(Consumer<String> report) {
        this.report = report;
    }

    /**
     * Notes that a mixin went into {@code target}, one of its {@code targets}. A handler counts as
     * missing only once every one of those has been looked at without calling it.
     */
    synchronized void applied(String mixin, Collection<String> targets, String target, ClassNode targetClass,
                              List<String> handlers) {
        if (handlers.isEmpty()) return;
        targetsLeft.computeIfAbsent(mixin, key -> new HashSet<>(targets));
        waiting.add(new Waiting(mixin, target, targetClass, handlers));
    }

    /** Looks at every waiting target but {@code inProgress}, the class Mixin is working on now. */
    void checkFinished(String inProgress) {
        for (Waiting w : take(w -> !w.target().equals(inProgress))) check(w);
    }

    /**
     * Looks at every waiting target, from outside any class transformation. {@code awaitDone} waits
     * until a class is finished and says whether it loaded at all; one that did not is dropped
     * unchecked, a half-transformed class proving nothing.
     */
    void checkAll(Predicate<String> awaitDone) {
        for (Waiting w : take(w -> true)) {
            if (awaitDone.test(w.target())) check(w);
        }
    }

    private synchronized List<Waiting> take(Predicate<Waiting> which) {
        if (waiting.isEmpty()) return List.of();
        List<Waiting> taken = new ArrayList<>();
        waiting.removeIf(w -> which.test(w) && taken.add(w));
        return taken;
    }

    private void check(Waiting w) {
        Set<String> calledHere = called(w.targetClass(), w.handlers());
        List<String> missing = new ArrayList<>();
        synchronized (this) {
            Set<String> seen = found.computeIfAbsent(w.mixin(), key -> new HashSet<>());
            seen.addAll(calledHere);
            Set<String> left = targetsLeft.get(w.mixin());
            if (left != null) {
                left.remove(w.target());
                if (!left.isEmpty()) return;
            }
            for (String handler : w.handlers()) {
                if (!seen.contains(handler)) missing.add(handler);
            }
            found.remove(w.mixin());
            targetsLeft.remove(w.mixin());
        }
        String mixin = w.mixin().substring(w.mixin().lastIndexOf('.') + 1);
        for (String handler : missing) report.accept(mixin + "." + handler);
    }

    /**
     * Which of the handlers the target calls. Mixin merges each handler into its target as
     * {@code prefix$id$name}, and an injection that applied is a call to it, direct or through a
     * method handle (MixinExtras' Operation).
     */
    static Set<String> called(ClassNode target, List<String> handlers) {
        Map<String, String> merged = new HashMap<>();
        for (MethodNode method : target.methods) {
            for (String handler : handlers) {
                if (method.name.endsWith("$" + handler)) merged.put(method.name, handler);
            }
        }
        Set<String> called = new HashSet<>();
        if (merged.isEmpty()) return called;
        for (MethodNode method : target.methods) {
            if (method.instructions == null) continue;
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode call && call.owner.equals(target.name)) {
                    String handler = merged.get(call.name);
                    if (handler != null) called.add(handler);
                } else if (insn instanceof InvokeDynamicInsnNode indy) {
                    for (Object arg : indy.bsmArgs) {
                        if (arg instanceof Handle handle && handle.getOwner().equals(target.name)) {
                            String handler = merged.get(handle.getName());
                            if (handler != null) called.add(handler);
                        }
                    }
                }
            }
        }
        return called;
    }
}

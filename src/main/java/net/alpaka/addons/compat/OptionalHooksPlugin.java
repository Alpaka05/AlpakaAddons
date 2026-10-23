package net.alpaka.addons.compat;

import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The plugin of {@code alpaka.cosmetic.mixins.json}: the hooks that only change how something looks
 * or sounds.
 *
 * That config requires none of its injections ({@code defaultRequire} 0). When another mod takes
 * one of those spots first, or a new Minecraft moves the code, the game starts without that effect
 * instead of crashing on launch. Hooks that correctness or compliance depend on stay in
 * {@code alpaka.mixins.json}, where every injection is required.
 *
 * Mixin says nothing about an optional injection that found no target, so this does, once per hook.
 * Mixin merges each handler into its target as {@code prefix$id$name}, and an injection that applied
 * is a call to it; a merged handler that nothing in the target calls did not apply. A handler only
 * counts as missing once every target class of its mixin has been through here.
 */
public final class OptionalHooksPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("AlpakaAddons/Mixin");

    /** The injector annotations a handler can carry, Mixin's own and MixinExtras'. */
    private static final Set<String> INJECTORS = Set.of(
            "Lorg/spongepowered/asm/mixin/injection/Inject;",
            "Lorg/spongepowered/asm/mixin/injection/Redirect;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;",
            "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;",
            "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;",
            "Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;",
            "Lcom/llamalad7/mixinextras/injector/ModifyReceiver;",
            "Lcom/llamalad7/mixinextras/injector/WrapWithCondition;",
            "Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;",
            "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;");

    /** Per mixin: the handlers seen applied in any of its targets so far. */
    private final Map<String, Set<String>> applied = new HashMap<>();
    /** Per mixin: the target classes it has not been applied to yet. */
    private final Map<String, Set<String>> targetsLeft = new HashMap<>();

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        try {
            List<String> handlers = handlers(mixinInfo.getClassNode(0));
            if (handlers.isEmpty()) return;
            Set<String> seen = applied.computeIfAbsent(mixinClassName, key -> new HashSet<>());
            seen.addAll(called(targetClass, handlers));

            Set<String> left = targetsLeft.computeIfAbsent(mixinClassName, key -> dotted(mixinInfo.getTargetClasses()));
            left.remove(targetClassName.replace('/', '.'));
            if (!left.isEmpty()) return;
            for (String handler : handlers) {
                if (!seen.contains(handler)) {
                    LOGGER.warn("Optional hook {}.{} found nothing to hook into, so that effect is off. "
                            + "Another mod got there first, or this Minecraft version moved the code.",
                            mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1), handler);
                }
            }
            applied.remove(mixinClassName);
            targetsLeft.remove(mixinClassName);
        } catch (RuntimeException e) {
            // Only a diagnosis; it must never stand in the way of the game starting.
            LOGGER.debug("Could not check the optional hooks of {}", mixinClassName, e);
        }
    }

    /** The names of the mixin's injector handlers. */
    private static List<String> handlers(ClassNode mixin) {
        List<String> names = new ArrayList<>();
        for (MethodNode method : mixin.methods) {
            if (isInjector(method.visibleAnnotations) || isInjector(method.invisibleAnnotations)) names.add(method.name);
        }
        return names;
    }

    private static boolean isInjector(List<AnnotationNode> annotations) {
        if (annotations == null) return false;
        for (AnnotationNode annotation : annotations) {
            if (INJECTORS.contains(annotation.desc)) return true;
        }
        return false;
    }

    /** Which of the handlers the target calls, by their merged names ({@code prefix$id$name}). */
    private static Set<String> called(ClassNode target, List<String> handlers) {
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

    private static Set<String> dotted(List<String> names) {
        Set<String> set = new HashSet<>();
        for (String name : names) set.add(name.replace('/', '.'));
        return set;
    }
}

package net.alpaka.addons.compat;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
 * counts as missing once every target class of its mixin has been looked at, and a class only once
 * Mixin is done with it ({@link HookCheck}): MixinExtras adds its wrapping injectors after
 * {@link #postApply}, so looking right there called all of them missing.
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

    private static final HookCheck CHECK = new HookCheck(hook -> LOGGER.warn(
            "Optional hook {} found nothing to hook into, so that effect is off. "
                    + "Another mod got there first, or this Minecraft version moved the code.", hook));

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
        try {
            // Mixin works on one class at a time: every class before this one is finished.
            CHECK.checkFinished(dotted(targetClassName));
        } catch (RuntimeException e) {
            LOGGER.debug("Could not check the optional hooks", e);
        }
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        try {
            String target = dotted(targetClassName);
            CHECK.checkFinished(target);
            CHECK.applied(mixinClassName, dotted(mixinInfo.getTargetClasses()), target, targetClass,
                    handlers(mixinInfo.getClassNode(0)));
        } catch (RuntimeException e) {
            // Only a diagnosis; it must never stand in the way of the game starting.
            LOGGER.debug("Could not check the optional hooks of {}", mixinClassName, e);
        }
    }

    /**
     * Looks at the classes still waiting: the ones Mixin finished last, with no class after them to
     * show they are done. Called from outside any class transformation ({@link OptionalHooksReport}).
     */
    public static void checkRemaining() {
        try {
            CHECK.checkAll(OptionalHooksPlugin::awaitDefined);
        } catch (RuntimeException e) {
            LOGGER.debug("Could not check the optional hooks", e);
        }
    }

    /**
     * Waits until the class is defined, which is once Mixin is done with it: a class another thread
     * is still loading holds the loader's lock for that name until then. False if it failed to load.
     */
    private static boolean awaitDefined(String className) {
        try {
            Class.forName(className, false, OptionalHooksPlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
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

    private static String dotted(String name) {
        return name.replace('/', '.');
    }

    private static Set<String> dotted(List<String> names) {
        Set<String> set = new HashSet<>();
        for (String name : names) set.add(dotted(name));
        return set;
    }
}

package net.alpaka.addons.client.gui;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every class of the mod that handles typed characters is marked {@link TakesTypedText}, read from
 * the compiled classes: an unmarked one gets no characters on 26.3, which nothing else would tell.
 */
class TypedTextInputTest {
    private static final String MARKER = "net/alpaka/addons/client/gui/TakesTypedText";
    private static final String CHAR_TYPED = "(Lnet/minecraft/client/input/CharacterEvent;)Z";

    @Test
    void everyScreenThatHandlesTypedCharactersIsMarked() throws IOException {
        Map<String, ClassNode> classes = modClasses();
        List<String> handlers = new ArrayList<>();
        List<String> unmarked = new ArrayList<>();
        for (ClassNode node : classes.values()) {
            if (!handlesTypedCharacters(node)) continue;
            handlers.add(node.name);
            if (!isMarked(node, classes)) unmarked.add(node.name);
        }
        // The scan found the classes at all: the config menu is one of them.
        assertTrue(handlers.contains("net/alpaka/addons/client/AlpakaConfigScreen"), "no classes scanned: " + handlers);
        assertEquals(List.of(), unmarked, "screens with a charTyped that are not marked TakesTypedText");
    }

    @Test
    void aMarkOnASuperclassCounts() {
        ClassNode base = type("a/Base", "net/minecraft/client/gui/screens/Screen", MARKER);
        ClassNode sub = type("a/Sub", "a/Base");
        sub.methods.add(new MethodNode(1, "charTyped", CHAR_TYPED, null, null));
        ClassNode plain = type("a/Plain", "net/minecraft/client/gui/screens/Screen");
        plain.methods.add(new MethodNode(1, "charTyped", CHAR_TYPED, null, null));
        Map<String, ClassNode> classes = Map.of(base.name, base, sub.name, sub, plain.name, plain);

        assertTrue(handlesTypedCharacters(sub));
        assertTrue(isMarked(sub, classes));
        assertFalse(isMarked(plain, classes));
        assertFalse(handlesTypedCharacters(base));
    }

    private static ClassNode type(String name, String superName, String... interfaces) {
        ClassNode node = new ClassNode();
        node.name = name;
        node.superName = superName;
        node.interfaces = List.of(interfaces);
        return node;
    }

    private static boolean handlesTypedCharacters(ClassNode node) {
        for (MethodNode method : node.methods) {
            if (method.name.equals("charTyped") && method.desc.equals(CHAR_TYPED)) return true;
        }
        return false;
    }

    /** Marked itself, or through a class of the mod it extends. */
    private static boolean isMarked(ClassNode node, Map<String, ClassNode> classes) {
        for (ClassNode at = node; at != null; at = classes.get(at.superName)) {
            if (at.interfaces.contains(MARKER)) return true;
        }
        return false;
    }

    private static Map<String, ClassNode> modClasses() throws IOException {
        Map<String, ClassNode> out = new HashMap<>();
        for (Path root : classDirectories()) {
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                    ClassNode node = new ClassNode();
                    new ClassReader(Files.readAllBytes(file)).accept(node, ClassReader.SKIP_CODE);
                    out.put(node.name, node);
                }
            }
        }
        return out;
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

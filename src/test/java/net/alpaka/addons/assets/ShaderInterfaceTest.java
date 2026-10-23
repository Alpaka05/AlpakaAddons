package net.alpaka.addons.assets;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The values our shaders hand from one stage to the next, as the Vulkan backend can place them.
 *
 * On Vulkan (26.2's Graphics API option; OneClient always uses it) Minecraft gives every vertex
 * output one location, numbered by its place in the list, and each fragment input the number of
 * the output with its name. A matrix or an array takes several locations, so the next value lands
 * on one of them and the pipeline does not compile. The starlight shader's {@code flat out mat3}
 * did exactly that and left the screen black on launch; OpenGL never minded, so development on it
 * did not show it.
 */
class ShaderInterfaceTest {
    private static final Path SHADERS = Path.of("src/main/resources/assets/alpaka/shaders");

    /** An in or out at the top level of a shader: qualifiers, the type, the name, maybe an array size. */
    private static final Pattern DECLARATION = Pattern.compile(
            "^\\s*(?:(?:flat|smooth|noperspective|centroid|sample|invariant|layout\\s*\\([^)]*\\))\\s+)*"
                    + "(?:in|out)\\s+(?:(?:lowp|mediump|highp)\\s+)?(\\w+)\\s+\\w+\\s*(\\[[^\\]]*\\])?\\s*;",
            Pattern.MULTILINE);

    /** mat2 to mat4, the non-square ones and their double versions. */
    private static final Pattern MATRIX = Pattern.compile("d?mat[234](?:x[234])?");

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");

    @Test
    void shadersHandOverSingleVectorsOnly() throws IOException {
        List<String> rejected = new ArrayList<>();
        int declarations = 0;
        try (Stream<Path> files = Files.walk(SHADERS)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".vsh") && !name.endsWith(".fsh") && !name.endsWith(".glsl")) continue;
                String source = Files.readString(file);
                declarations += declarations(source);
                for (String declaration : rejected(source)) {
                    rejected.add(SHADERS.relativize(file).toString().replace('\\', '/') + ": " + declaration);
                }
            }
        }
        // A scan that sees nothing would pass for the wrong reason.
        assertTrue(declarations > 0, "No in or out declarations found under " + SHADERS);
        assertEquals(List.of(), rejected,
                "A shader hands a matrix or an array from one stage to the next. On Vulkan that takes "
                        + "several locations and the pipeline fails; pass it as single vectors (a mat3 as "
                        + "its three columns) and put the matrix back together in the next stage.");
    }

    @Test
    void matricesAndArraysAreRejectedAndVectorsAreNot() {
        assertEquals(List.of("flat out mat3 viewToWorld;"),
                rejected("flat out mat3 viewToWorld;\nout vec4 vertexColor;"));
        assertEquals(List.of("in float weights[4];"), rejected("in float weights[4];\nflat in vec2 halfSize;"));
        assertEquals(List.of("layout(location = 2) smooth out highp mat2x3 m;"),
                rejected("layout(location = 2) smooth out highp mat2x3 m;"));
        assertEquals(List.of(), rejected("// flat out mat3 viewToWorld;\n/* in mat4 old; */\nout vec3 viewPos;"));
        assertEquals(List.of(), rejected("void rotate(in mat3 rotation, out vec3 result) {}"));
        assertEquals(2, declarations("in vec3 Position;\nflat out vec2 shape;\nuniform sampler2D Sampler0;"));
    }

    private static int declarations(String source) {
        return (int) DECLARATION.matcher(withoutComments(source)).results().count();
    }

    /** The declarations in this source that take more than one location. */
    private static List<String> rejected(String source) {
        List<String> rejected = new ArrayList<>();
        Matcher declaration = DECLARATION.matcher(withoutComments(source));
        while (declaration.find()) {
            if (MATRIX.matcher(declaration.group(1)).matches() || declaration.group(2) != null) {
                rejected.add(declaration.group().strip());
            }
        }
        return rejected;
    }

    private static String withoutComments(String source) {
        return LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(source).replaceAll(" ")).replaceAll("");
    }
}

#version 330

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

// Every piece of the aura is a quad of soft light; the shape of that light is worked out here, so no
// texture is needed and the glow stays smooth however close the camera comes.
//
//   u in 0..1 - a round glow: a hot core inside a wide halo. Ribbons and rings hold u at 0.5, which
//               leaves only the falloff across their width.
//   u in 2..3 - a four-pointed sparkle, the same glow with two thin rays crossing it.
//
// Blended premultiplied: the color is light added to the scene, and a little alpha darkens what is
// behind the glow, so it keeps its contrast against a bright sky instead of washing out.
void main() {
    bool sparkle = texCoord.x > 1.5;
    vec2 p = vec2(sparkle ? texCoord.x - 2.0 : texCoord.x, texCoord.y) * 2.0 - 1.0;
    float d2 = dot(p, p);
    if (d2 >= 1.0) {
        discard;
    }
    float edge = 1.0 - d2;
    float core = exp(-d2 * 22.0);
    float halo = exp(-d2 * 4.0) * edge * edge;
    float glow = core * 0.8 + halo * 0.7;
    if (sparkle) {
        float rays = exp(-abs(p.x) * 26.0) + exp(-abs(p.y) * 26.0);
        glow += rays * edge * edge * 0.8;
    }
    float strength = vertexColor.a;
    // The very center runs a little towards white, the way a bright light overexposes; kept slight,
    // or thin lights all read as white and lose their color.
    vec3 color = vertexColor.rgb * glow * 1.25 + vec3(core * 0.25);
    fragColor = vec4(color * strength, min(glow * strength * 0.4, 1.0));
}

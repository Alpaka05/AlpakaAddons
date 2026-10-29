#version 330

// A copy of globals.glsl, as in starlight.fsh; only GameTime is read, to move the clouds.
layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    vec3 CameraOffset;
    vec2 ScreenSize;
    float GlintAlpha;
    float GameTime;
    int MenuBlurRadius;
    int UseRgss;
};

in vec2 texCoord;
in vec4 vertexColor;

out vec4 fragColor;

// Every piece of the aura is a quad of soft light; the shape of that light is worked out here, so no
// texture is needed and the glow stays smooth however close the camera comes.
//
//   u in 0..1 - a round glow: a hot core inside a wide halo. Ribbons and rings hold u at 0.5, which
//               leaves only the falloff across their width.
//   u in 2..3 - a four-pointed sparkle, the same glow with two thin rays crossing it.
//   u from 4  - a puff of cloud: not light but matter. The whole part of u above 4 picks the puff's
//               own pattern (u = 4 + 2 * seed + 0..1), so no two puffs look alike.
//
// Blended premultiplied: the color is light added to the scene, and a little alpha darkens what is
// behind the glow, so it keeps its contrast against a bright sky instead of washing out.
float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), u.x),
               mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm2(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 4; i++) {
        value += amplitude * noise2(p);
        p = p * 2.03 + vec2(5.3, 1.7);
        amplitude *= 0.5;
    }
    return value;
}

// A puff of storm cloud. Its outline is a circle pushed in and out by drifting noise, so the edge
// billows instead of being a clean disc, and its inside is shaded as if it were a ball lit from
// above: bright, lumpy tops, a dark belly, and thin edges glowing a little where light gets through.
void cloud() {
    float seed = floor(texCoord.x);
    vec2 q = vec2(fract(texCoord.x), texCoord.y) * 2.0 - 1.0;
    float r2 = dot(q, q);
    if (r2 >= 1.0) {
        discard;
    }
    // GameTime runs from 0 to 1 over a Minecraft day of 20 minutes; this makes it seconds.
    float t = GameTime * 1200.0;
    vec2 offset = vec2(seed * 7.13, seed * 3.71);
    float edge = fbm2(q * 1.7 + offset + vec2(t * 0.05, t * 0.03));
    float shape = r2 + (edge - 0.5) * 0.95;
    float density = 1.0 - smoothstep(0.42, 0.98, shape);
    if (density <= 0.002) {
        discard;
    }
    float bulge = sqrt(max(0.0, 1.0 - r2));
    vec3 normal = normalize(vec3(q.x, q.y, bulge));
    float light = dot(normal, normalize(vec3(0.25, 0.85, 0.45))) * 0.5 + 0.5;
    float detail = fbm2(q * 4.3 + offset * 1.3 + vec2(-t * 0.07, t * 0.04));
    float shade = 0.5 + 0.6 * light + 0.35 * (detail - 0.5);
    vec3 color = vertexColor.rgb * shade + vec3(0.1) * (1.0 - density) * light;
    float alpha = density * vertexColor.a;
    fragColor = vec4(color * alpha, alpha);
}

void main() {
    if (texCoord.x > 3.5) {
        cloud();
        return;
    }
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

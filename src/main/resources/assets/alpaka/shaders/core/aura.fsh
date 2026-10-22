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
//               own pattern (u = 4 + 2 * seed + 0..1), so no two puffs look alike, and the whole part
//               of v how strongly lightning inside lights it (v = 2 * level + 0..1, level 0..31).
//
// Negative u holds the storm's own shapes:
//   -2..-1    - a lightning channel (ribbon): u runs along it, v = 2 * seed + 0..1 across it.
//   -3.5      - a streak of rain or wind (ribbon): matter, grey against a bright sky.
//   -6..-5    - a scorch mark lying on the ground (v = 2 * seed + 0..1): darkens it, glows in cracks.
//   -8..-7    - a chip of debris: opaque, two-tone.
//   -10..-9   - a plasma globe: writhing filaments round a white core.
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

// Fewer octaves for the cloud, which covers a lot of the screen: three for its outline, two for the
// detail inside, where the finer ones were not to be seen anyway.
float fbmEdge(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 3; i++) {
        value += amplitude * noise2(p);
        p = p * 2.03 + vec2(5.3, 1.7);
        amplitude *= 0.5;
    }
    return value / 0.875;
}

float fbmDetail(vec2 p) {
    float value = 0.5 * noise2(p);
    value += 0.25 * noise2(p * 2.03 + vec2(5.3, 1.7));
    return value / 0.75;
}

// A puff of storm cloud. Its outline is a circle pushed in and out by drifting noise, so the edge
// billows instead of being a clean disc, and its inside is shaded as if it were a ball lit from
// above: bright, lumpy tops, a dark belly, and thin edges glowing a little where light gets through.
void cloud() {
    float seed = floor(texCoord.x);
    float lit = floor(texCoord.y * 0.5);
    float inner = lit / 31.0;
    vec2 q = vec2(fract(texCoord.x), texCoord.y - 2.0 * lit) * 2.0 - 1.0;
    float r2 = dot(q, q);
    if (r2 >= 1.0) {
        discard;
    }
    // GameTime runs from 0 to 1 over a Minecraft day of 20 minutes; this makes it seconds.
    float t = GameTime * 1200.0;
    vec2 offset = vec2(seed * 7.13, seed * 3.71);
    float edge = fbmEdge(q * 1.7 + offset + vec2(t * 0.05, t * 0.03));
    float shape = r2 + (edge - 0.5) * 0.95;
    float density = 1.0 - smoothstep(0.42, 0.98, shape);
    if (density <= 0.002) {
        discard;
    }
    float bulge = sqrt(max(0.0, 1.0 - r2));
    vec3 normal = normalize(vec3(q.x, q.y, bulge));
    // While lightning runs through it the light comes from inside and below instead of the sky.
    vec3 lightDir = normalize(mix(vec3(0.25, 0.85, 0.45), vec3(0.1, -0.6, 0.8), inner));
    float light = dot(normal, lightDir) * 0.5 + 0.5;
    float detail = fbmDetail(q * 4.3 + offset * 1.3 + vec2(-t * 0.07, t * 0.04));
    float shade = 0.5 + 0.6 * light + 0.35 * (detail - 0.5);
    vec3 color = vertexColor.rgb * shade + mix(vec3(0.1), vertexColor.rgb * 0.6, inner) * (1.0 - density) * light;
    // Lit from within, a puff glows brightest where it is thickest.
    color = mix(color, vertexColor.rgb * (0.4 + 1.0 * bulge) * (0.4 + 1.2 * detail), inner * 0.55);
    float alpha = density * vertexColor.a;
    // Lightning inside also glows out of the puff's thick middle, on top of lighting it.
    fragColor = vec4(color * alpha + vertexColor.rgb * inner * inner * bulge * detail * detail * 1.2 * alpha, alpha);
}

// A lightning channel: a white-hot core that stays white in any palette and never gets thinner than
// about a pixel and a half, a tinted sheath round it, and a faint wide halo whose alpha darkens the
// sky a little behind the bolt, so it keeps its bite by day. The core wavers along the channel.
void bolt(float fwV) {
    float along = texCoord.x + 2.0;
    float seed = floor(texCoord.y * 0.5);
    float y = abs((texCoord.y - 2.0 * seed) * 2.0 - 1.0);
    float t = GameTime * 1200.0;
    float waver = 0.8 + 0.4 * noise2(vec2(along * 36.0 + seed * 13.0, t * 20.0));
    float coreW = max(0.30 * waver, 3.0 * fwV);
    float k = y / coreW;
    float core = exp(-k * k);
    float inner = exp(-y * y * 12.0);
    float e = 1.0 - y * y;
    float halo = exp(-y * y * 5.0) * e * e;
    float a = vertexColor.a;
    vec3 rgb = vec3(1.1 * core) + vertexColor.rgb * (inner * 1.3 + halo * 0.30);
    fragColor = vec4(rgb * a, min(core * 0.55 + inner * 0.25 + halo * 0.35, 1.0) * a);
}

// Rain and wind: matter, not light, so it reads grey against a noon sky and pale at night.
void streak() {
    float y = texCoord.y * 2.0 - 1.0;
    float f = 1.0 - y * y;
    f *= f;
    float a = vertexColor.a;
    fragColor = vec4(vertexColor.rgb * f * a, f * a * 0.8);
}

// A charred patch on the ground: darkens it with alpha, and glows in its cracks with the color, which
// the effect cools from white-hot to black.
void scorch() {
    float seed = floor(texCoord.y * 0.5);
    vec2 q = vec2(texCoord.x + 6.0, texCoord.y - 2.0 * seed) * 2.0 - 1.0;
    float r = length(q);
    if (r >= 1.0) {
        discard;
    }
    float n = fbm2(q * 2.2 + seed * 3.17);
    // Rays of burn reaching out unevenly, so the mark is a splash and not a dark disc.
    float rays = noise2(normalize(q + vec2(1e-4)) * 3.0 + seed * 5.3);
    float burn = 1.0 - smoothstep(0.25, 0.95, r + (n - 0.5) * 0.6 + (rays - 0.5) * 0.7 * r);
    float vein = abs(fbm2(q * 3.1 + seed * 1.91 + 7.0) - 0.5);
    float crack = (1.0 - smoothstep(0.0, 0.06, vein)) * (1.0 - smoothstep(0.2, 0.8, r));
    float a = vertexColor.a;
    fragColor = vec4(vertexColor.rgb * crack * 1.6 * a, burn * a * 0.8);
}

// A chip of debris flung up by the wind or a strike: opaque, a lit and a shaded facet.
void shard() {
    vec2 p = vec2(texCoord.x + 8.0, texCoord.y) * 2.0 - 1.0;
    float d = abs(p.x) + 1.6 * abs(p.y) + 0.25 * (noise2(p * 3.0 + vec2(11.0, 7.0)) - 0.5);
    if (d > 0.9) {
        discard;
    }
    float facet = (0.6 * p.x + 0.8 * p.y > 0.0) ? 1.0 : 0.55;
    float a = vertexColor.a;
    fragColor = vec4(vertexColor.rgb * facet * a, a);
}

// A plasma globe: five filaments writhing round a white core. |sin((angle - ai) / 2)| measures the
// angle to a filament without the seam atan has at the back.
void plasma() {
    vec2 q = vec2(texCoord.x + 10.0, texCoord.y) * 2.0 - 1.0;
    float r = length(q);
    if (r >= 1.0) {
        discard;
    }
    float t = GameTime * 1200.0;
    // atan(0, 0) is undefined, and Metal does return garbage for it.
    float ang = r < 1e-4 ? 0.0 : atan(q.y, q.x);
    float fil = 0.0;
    for (int i = 0; i < 5; i++) {
        float fi = float(i);
        float ai = fi * 1.2566 + 0.9 * sin(t * 1.7 + fi * 2.3) + 0.6 * noise2(vec2(r * 3.0, t * 2.0 + fi * 7.0));
        fil += exp(-abs(sin((ang - ai) * 0.5)) * 28.0 * r);
    }
    fil *= 1.0 - smoothstep(0.55, 1.0, r);
    float core = exp(-r * r * 30.0);
    float e = 1.0 - r * r;
    float halo = exp(-r * r * 5.0) * e * e;
    float a = vertexColor.a;
    vec3 rgb = vertexColor.rgb * (fil * 1.3 + halo * 0.6) + vec3(core * 0.9 + fil * 0.35);
    fragColor = vec4(rgb * a, min(fil * 0.8 + halo * 0.4 + core * 0.8, 1.0) * a);
}

void main() {
    // Taken before any branch or discard: derivatives are only defined in uniform control flow.
    float fwV = fwidth(texCoord.y);
    float u = texCoord.x;
    if (u > 3.5) {
        cloud();
        return;
    }
    if (u < 0.0) {
        if (u > -2.5) {
            bolt(fwV);
        } else if (u > -4.5) {
            streak();
        } else if (u > -6.5) {
            scorch();
        } else if (u > -8.5) {
            shard();
        } else {
            plasma();
        }
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

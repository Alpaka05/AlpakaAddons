#version 330

layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    vec3 CameraOffset;
    vec2 ScreenSize;
    float GlintAlpha;
    float GameTime;
    int MenuBlurRadius;
    int UseRgss;
};

in vec3 viewPos;
flat in vec3 viewToWorld0;
flat in vec3 viewToWorld1;
flat in vec3 viewToWorld2;
in vec4 vertexColor;

out vec4 fragColor;

// A starry nebula seen through the held item, as if the item were a window into space. The galaxy
// hangs in the sky around the player: each pixel shows the part of it in the direction, in the
// world, that the pixel is seen in. Turning sweeps it past the item, moving the item moves the
// window over it, and on its own it only drifts and twinkles slowly.
// Everything is built from 3D noise over that direction, so the sky has no seams or poles.
// Blended premultiplied: alpha darkens the item towards the void, the color is light added on top.

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

vec3 hash33(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}

float noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash13(i), hash13(i + vec3(1.0, 0.0, 0.0)), u.x),
                   mix(hash13(i + vec3(0.0, 1.0, 0.0)), hash13(i + vec3(1.0, 1.0, 0.0)), u.x), u.y),
               mix(mix(hash13(i + vec3(0.0, 0.0, 1.0)), hash13(i + vec3(1.0, 0.0, 1.0)), u.x),
                   mix(hash13(i + vec3(0.0, 1.0, 1.0)), hash13(i + vec3(1.0, 1.0, 1.0)), u.x), u.y), u.z);
}

float fbm(vec3 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 4; i++) {
        value += amplitude * noise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 4.1);
        amplitude *= 0.5;
    }
    return value;
}

// One layer of stars on the sky: q is the view direction scaled up, so a unit cell spans a small
// patch of sky. A third of the cells hold a star somewhere inside; where the sky sphere passes
// close to it, it shows as a soft dot - larger or smaller depending on how close, which makes the
// stars vary in size. The 2x2x2 cells around q hold every star near enough to be seen.
//
// Anti-aliased: pixel is how much of a cell one screen pixel covers. A star is never drawn
// smaller than about a pixel and a half - one that would be gets that size and is dimmed to keep
// its brightness - and a layer whose cells are only a few pixels wide fades out. A star that is
// smaller than a pixel lights the pixel or misses it from frame to frame, which is the sparkle.
float stars(vec3 q, float pixel, float t) {
    float visible = 1.0 - smoothstep(0.12, 0.30, pixel);
    if (visible <= 0.0) {
        return 0.0;
    }
    float light = 0.0;
    vec3 base = floor(q - 0.5);
    for (int i = 0; i < 8; i++) {
        vec3 cell = base + vec3(float(i & 1), float((i >> 1) & 1), float((i >> 2) & 1));
        float h = hash13(cell);
        if (h < 0.66) {
            continue;
        }
        vec3 center = cell + 0.2 + 0.6 * hash33(cell);
        float size = 0.14 + 0.12 * hash13(cell + 7.1);
        float radius = max(size, pixel * 1.5);
        float d = length(q - center);
        float core = smoothstep(radius, 0.0, d) * (size * size) / (radius * radius);
        float halo = smoothstep(radius * 3.0, 0.0, d) * 0.15 * step(0.9, h);
        float twinkle = 0.7 + 0.3 * sin(t * (0.8 + 1.6 * hash13(cell + 3.3)) + 6.2831 * h);
        light += (core + halo) * twinkle;
    }
    return light * visible;
}

void main() {
    // GameTime is the day's progress, 0..1 over 20 minutes: this makes t count seconds.
    float t = GameTime * 1200.0;
    vec3 tint = vertexColor.rgb;
    float strength = vertexColor.a;

    vec3 dir = normalize(mat3(viewToWorld0, viewToWorld1, viewToWorld2) * viewPos);
    // How far the view direction turns from one pixel to the next.
    float pixel = max(length(dFdx(dir)), length(dFdy(dir)));

    // Slowly drifting gas in the starlight's color, shading into blue where it is dense. Never
    // fully dark, so every part of the item keeps some of the galaxy.
    vec3 g = dir * 2.5 + vec3(t * 0.006, t * 0.004, -t * 0.005);
    float gas = fbm(g);
    float detail = fbm(g * 1.9 + gas * 1.4);
    vec3 nebula = mix(tint, vec3(0.30, 0.55, 1.0), smoothstep(0.45, 0.80, detail));
    nebula *= 0.35 + 0.65 * smoothstep(0.25, 0.75, gas) * (0.6 + 0.4 * detail);

    // Three layers of stars, from sparse and large to fine and dense.
    float s = stars(dir * 18.0, pixel * 18.0, t) * 0.7
            + stars(dir * 30.0 + 17.0, pixel * 30.0, t * 1.3) * 0.85
            + stars(dir * 48.0 + 41.0, pixel * 48.0, t * 1.7);
    vec3 starLight = mix(vec3(1.0), tint, 0.2) * s * 1.4;

    vec3 light = (nebula * 0.85 + starLight) * strength;
    fragColor = vec4(min(light, vec3(1.0)), strength * 0.55);
}

#version 330

// Sky Effects: the animated skies. One shader, built three times with a different define -
// SKY_NETHER, SKY_OVERWORLD or SKY_END - by SkyEffectsFeature.
//
// It is drawn on a box around the camera, and skyPos is the box's point under the pixel, so its
// direction is where in the world the pixel looks. Everything below is a function of that
// direction and of the time: the sky needs no texture and has no seams.
//
// The parameters ride in vanilla's DynamicTransforms block, which every sky draw fills anyway:
//   ColorModulator.a  - Intensity, 0..1
//   ModelOffset.x     - the time in seconds, already scaled by Animation Speed
//   ModelOffset.y     - Overworld: how far into the night it is, 0 by day .. 1 at night
//   ModelOffset.z     - Overworld: how clear the sky is, 1 .. 0 in heavy rain
//   TextureMat[0].xyz - Overworld: towards the sun
//   TextureMat[1].xyz - Overworld: towards the moon
//   TextureMat[2].x   - Overworld: the stars' angle, which turns the Milky Way with them
// The Nether's colors come from the fog, so the storm takes on each biome's own hue.

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

// A copy of fog.glsl's block.
layout(std140) uniform Fog {
    vec4 FogColor;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    float FogSkyEnd;
    float FogCloudsEnd;
};

in vec3 skyPos;

out vec4 fragColor;

const float TAU = 6.28318530718;

// --- Noise ---------------------------------------------------------------------------------------

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

float hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 hash33(vec3 p3) {
    p3 = fract(p3 * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yxz + 33.33);
    return fract((p3.xxy + p3.yxx) * p3.zyx);
}

// 1D value noise that repeats every `period` (a whole number), so it closes up around a circle.
float noise1(float x, float period) {
    float i = floor(x);
    float u = fract(x);
    u = u * u * (3.0 - 2.0 * u);
    return mix(hash11(mod(i, period)), hash11(mod(i + 1.0, period)), u);
}

float noise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), u.x),
               mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), u.x), u.y);
}

// Two independent noises for the price of little more than one, to push a 2D field around.
vec2 noise22(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash22(i), hash22(i + vec2(1.0, 0.0)), u.x),
               mix(hash22(i + vec2(0.0, 1.0)), hash22(i + vec2(1.0, 1.0)), u.x), u.y);
}

float noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash13(i), hash13(i + vec3(1.0, 0.0, 0.0)), u.x),
                   mix(hash13(i + vec3(0.0, 1.0, 0.0)), hash13(i + vec3(1.0, 1.0, 0.0)), u.x), u.y),
               mix(mix(hash13(i + vec3(0.0, 0.0, 1.0)), hash13(i + vec3(1.0, 0.0, 1.0)), u.x),
                   mix(hash13(i + vec3(0.0, 1.0, 1.0)), hash13(i + vec3(1.0, 1.0, 1.0)), u.x), u.y), u.z);
}

// Each octave is turned against the one before, so the grids the noise is built on never line up.
const mat2 TURN2 = mat2(0.80, 0.60, -0.60, 0.80);
const mat3 TURN3 = mat3(0.00, 0.80, 0.60, -0.80, 0.36, -0.48, -0.60, -0.48, 0.64);

float fbm2(vec2 p, int octaves) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < octaves; i++) {
        value += amplitude * noise2(p);
        p = TURN2 * p * 2.03 + 17.17;
        amplitude *= 0.5;
    }
    return value;
}

vec2 fbm22(vec2 p, int octaves) {
    vec2 value = vec2(0.0);
    float amplitude = 0.5;
    for (int i = 0; i < octaves; i++) {
        value += amplitude * noise22(p);
        p = TURN2 * p * 2.03 + 17.17;
        amplitude *= 0.5;
    }
    return value;
}

float fbm3(vec3 p, int octaves) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < octaves; i++) {
        value += amplitude * noise3(p);
        p = TURN3 * p * 2.02 + vec3(1.7, 9.2, 4.1);
        amplitude *= 0.5;
    }
    return value;
}

// --- Shared --------------------------------------------------------------------------------------

// Now and then a streak across the sky: a shooting star, a comet, or in the Nether a falling
// fireball. Time is cut into slots of `period` seconds; in a share `chance` of them one streak
// flies for `duration` seconds along a great circle from a random point at least `lowest` (an
// elevation in radians) up, mostly downwards. Its trail is the arc behind the head, brightest at
// the head, never thinner than a pixel and a bit.
vec3 streak(vec3 dir, float t, float period, float duration, float chance, float seed,
            float lowest, float pixel, vec3 headColor, vec3 tailColor) {
    float slot = floor(t / period);
    float local = t - slot * period;
    if (local > duration || hash12(vec2(slot, seed)) > chance) return vec3(0.0);
    float progress = local / duration;

    float azimuth = hash12(vec2(slot, seed + 1.0)) * TAU;
    float elevation = mix(lowest, 1.25, hash12(vec2(slot, seed + 2.0)));
    vec3 start = vec3(cos(azimuth) * cos(elevation), sin(elevation), sin(azimuth) * cos(elevation));
    vec3 side = normalize(cross(vec3(0.0, 1.0, 0.0), start));
    vec3 lift = cross(start, side);
    // Downwards, slanting to one side or the other.
    float heading = mix(-2.6, -0.55, hash12(vec2(slot, seed + 3.0)));
    vec3 travel = side * cos(heading) + lift * sin(heading);

    float reach = mix(0.35, 0.7, hash12(vec2(slot, seed + 4.0)));
    float headAt = progress * reach;
    float tailAt = max(headAt - 0.2, 0.0);
    vec3 head = start * cos(headAt) + travel * sin(headAt);
    vec3 tail = start * cos(tailAt) + travel * sin(tailAt);

    // The nearest point of the trail, put back on the sphere: the straight chord would sag
    // below the arc in the middle by more than the trail is wide.
    vec3 along = head - tail;
    float h = clamp(dot(dir - tail, along) / max(dot(along, along), 1e-7), 0.0, 1.0);
    float d = length(dir - normalize(tail + along * h));
    float width = max(0.0022, pixel * 1.3);
    float trail = smoothstep(width, 0.0, d) * h * h;
    float glow = smoothstep(width * 6.0, 0.0, length(dir - head)) * 0.5;
    float fade = smoothstep(0.0, 0.08, progress) * (1.0 - smoothstep(0.7, 1.0, progress));
    return (mix(tailColor, headColor, h) * trail + headColor * glow) * fade;
}

// --- The Nether: a firestorm ---------------------------------------------------------------------

#ifdef SKY_NETHER

// Sparks rising from the burning land, in cells laid out by compass direction and height:
// `around` cells to a full turn (a whole number, so the grid closes up), the grid drifting
// upwards by `rise` cells a second. About a fifth of the cells hold a spark near their middle, so
// only the pixel's own cell needs looking at.
float embers(vec3 dir, float t, float around, float rise, float pixel, float seed) {
    float px = pixel * around / TAU;
    float visible = 1.0 - smoothstep(0.08, 0.2, px);
    if (visible <= 0.0) return 0.0;
    vec2 uv = vec2(atan(dir.z, dir.x) / TAU + 0.5, asin(clamp(dir.y, -1.0, 1.0)) / TAU) * around;
    uv.y -= t * rise;
    vec2 cell = floor(uv);
    cell.x = mod(cell.x, around);
    float h = hash12(cell + seed);
    if (h < 0.78) return 0.0;
    vec2 center = 0.3 + 0.4 * hash22(cell + seed + 3.7);
    // Swaying a little from side to side on the way up.
    center.x += 0.12 * sin(t * (0.8 + 1.5 * h) + h * 40.0);
    vec2 d = fract(uv) - center;
    // A short streak, drawn out by the rise.
    d.y *= 0.45;
    float size = 0.05 + 0.05 * hash12(cell + seed + 9.1);
    float radius = max(size, px * 1.2);
    float spark = smoothstep(radius, 0.0, length(d)) * (size * size) / (radius * radius);
    float flicker = 0.55 + 0.45 * sin(t * (5.0 + 9.0 * h) + h * 120.0);
    float brightness = 0.35 + 0.65 * hash12(cell + seed + 5.3);
    // Born near the horizon, burnt out on the way up.
    float life = smoothstep(-0.04, 0.04, dir.y) * (1.0 - smoothstep(0.35, 0.8, dir.y));
    return spark * flicker * brightness * life * visible;
}

// Now and then a burst of fire lights up a patch of the storm from inside and flickers out.
float stormFlash(vec3 dir, float t) {
    const float PERIOD = 2.6;
    float slot = floor(t / PERIOD);
    float local = t - slot * PERIOD;
    if (hash12(vec2(slot, 11.0)) > 0.55) return 0.0;
    float azimuth = hash12(vec2(slot, 12.0)) * TAU;
    float elevation = mix(0.1, 0.8, hash12(vec2(slot, 13.0)));
    vec3 at = vec3(cos(azimuth) * cos(elevation), sin(elevation), sin(azimuth) * cos(elevation));
    float d = length(dir - at) / mix(0.15, 0.35, hash12(vec2(slot, 14.0)));
    float envelope = smoothstep(0.0, 0.04, local) * exp(-local * 2.2) * (0.65 + 0.35 * sin(local * 53.0 + slot));
    return exp(-d * d) * envelope;
}

vec3 netherSky(vec3 dir, float t, float pixel, vec3 fog) {
    // The fog's own hue at full brightness. The storm is built from it, so it burns red over the
    // Crimson Isle and in the crimson forest, and its flames turn to soul fire where the fog is
    // clearly blue-green, as in the soul sand valley; gray and purple fogs keep ordinary fire.
    vec3 hue = fog / max(max(fog.r, max(fog.g, fog.b)), 0.001);
    float soul = clamp(((hue.g + hue.b) * 0.5 - hue.r) * 2.0, 0.0, 1.0);
    vec3 flame = mix(vec3(1.0, 0.45, 0.08), vec3(0.35, 0.85, 1.0), soul);
    vec3 whiteHot = mix(vec3(1.0, 0.85, 0.5), vec3(0.8, 1.0, 1.0), soul);

    float up = max(dir.y, 0.0);

    // The clouds lie on a plane overhead, wound into a vortex that turns slowly about the zenith...
    vec2 p = dir.xz / (up + 0.1);
    float twist = t * 0.035 + 0.8 * log(length(p) + 0.3);
    p = mat2(cos(twist), sin(twist), -sin(twist), cos(twist)) * p;
    // ... and boil: their field is pushed around by a second, slower one.
    vec2 warp = fbm22(p * 0.55 + vec2(t * 0.045, -t * 0.03), 3);
    float f = fbm2(p * 0.9 + warp * 2.4 + vec2(-t * 0.07, t * 0.045), 5);
    // How fast the field changes from pixel to pixel; taken before any branch.
    float df = fwidth(f);

    // Behind the clouds the sky glows in the fog's color low down and darkens overhead, so the
    // horizon still meets the land in the color the distant land fades into.
    vec3 base = mix(fog, hue * 0.12 + fog * 0.22, smoothstep(0.0, 0.7, up));
    // Lit from below: the lower the clouds, the more of the fires' light reaches them.
    float glow = exp(-up * 2.5);

    // Thick smoke darkens the sky. Where the clouds are hot they glow, and the hottest burn
    // through white; which parts of the storm burn wanders in large patches. Thin glowing seams
    // run along the clouds' edges there, never thinner than the pixels allow.
    float body = smoothstep(0.3, 0.75, f);
    float fire = smoothstep(0.3, 0.7, noise2(p * 0.22 + vec2(t * 0.02, -t * 0.015) + 3.0));
    // Flame tongues: fine, fast detail licking along the hot clouds.
    float tongues = noise2(p * 4.0 + warp * 3.0 + vec2(0.0, -t * 0.4)) - 0.5;
    float heat = smoothstep(0.48, 0.86, f + 0.25 * (warp.y - 0.5) + 0.12 * tongues) * (0.35 + 0.65 * fire);
    float width = 0.02 + df;
    float seam = exp(-pow((f - 0.5) / width, 2.0)) * 0.02 / width * fire;
    vec3 storm = base * mix(1.0, 0.25, body);
    storm = mix(storm, flame * (0.55 + 0.45 * glow), clamp(heat * 1.15 + seam * 0.35, 0.0, 1.0));
    storm += whiteHot * pow(heat, 4.0) * 0.7;
    storm += whiteHot * stormFlash(dir, t) * (0.25 + 0.75 * body);

    // The storm thins out towards the horizon, into the haze the distant land fades into.
    vec3 col = mix(base, storm, smoothstep(0.02, 0.3, dir.y));

    float sparks = embers(dir, t, 96.0, 0.7, pixel, 0.0) + 0.7 * embers(dir, t, 160.0, 1.1, pixel, 7.0);
    col += (flame + whiteHot * 0.3) * sparks;
    col += streak(dir, t, 5.0, 1.3, 0.6, 3.0, 0.35, pixel, whiteHot, flame);
    return col;
}

#endif

// --- The Overworld: aurora, Milky Way and shooting stars by night, a glowing sun by day ----------

#ifdef SKY_OVERWORLD

// Two curtains of light over the northern sky. Each hangs from a lower edge that waves and folds
// along the horizon and drifts, is brightest along that edge and fades upwards, and is combed into
// fine rays that slide along it, with brighter and dimmer stretches wandering through. Green low
// down and violet at the top, as the real one.
vec3 aurora(vec3 dir, float t) {
    // North is -Z in Minecraft: 0 there, +-pi in the south, where the curtains have faded out.
    float azimuth = atan(dir.x, -dir.z);
    float spread = smoothstep(2.4, 1.0, abs(azimuth));
    if (spread <= 0.0) return vec3(0.0);
    float elevation = asin(clamp(dir.y, -1.0, 1.0));
    vec3 light = vec3(0.0);
    for (int i = 0; i < 2; i++) {
        float n = float(i);
        float x = azimuth * (1.0 + 0.35 * n) + n * 2.7;
        float edge = 0.19 + 0.1 * n
                + 0.06 * sin(x * 1.6 + t * 0.04)
                + 0.035 * sin(x * 4.3 - t * 0.07 + 1.3)
                + 0.03 * (noise1(x * 7.0 + t * 0.1, 1000.0) - 0.5);
        float h = elevation - edge;
        if (h < -0.03) continue;
        float rays = noise1(x * 70.0 + t * 0.35, 1000.0) * 0.6 + noise1(x * 23.0 - t * 0.2, 1000.0) * 0.4;
        rays = 0.35 + 0.65 * rays * rays;
        float pulse = smoothstep(0.2, 0.8, noise1(x * 2.5 - t * 0.12 + n * 5.0, 1000.0));
        // The bright stretches reach higher, as where a real curtain folds towards the viewer.
        float reach = 0.12 + 0.1 * noise1(x * 3.0 + t * 0.05 + n * 7.0, 1000.0) + 0.12 * pulse;
        float curtain = smoothstep(-0.03, 0.0, h) * exp(-max(h, 0.0) / reach * 2.0);
        vec3 tint = mix(vec3(0.15, 1.0, 0.5), vec3(0.6, 0.3, 1.0), smoothstep(0.02, 0.2, h));
        light += tint * curtain * rays * (0.2 + 0.8 * pulse) * (0.85 - 0.3 * n);
    }
    return light * spread;
}

// A faint band of light along a great circle of the stars' sky, with darker lanes of dust in it.
// Turned with the stars the way vanilla turns them: -90 degrees about Y, then the stars' angle
// about X; here both are undone to find the direction among the stars.
vec3 milkyWay(vec3 dir, float starAngle) {
    vec3 s = vec3(dir.z, dir.y, -dir.x);
    float c = cos(starAngle);
    float sn = sin(starAngle);
    s = vec3(s.x, s.y * c + s.z * sn, -s.y * sn + s.z * c);
    float across = dot(s, normalize(vec3(0.35, 0.25, 0.9)));
    float band = exp(-across * across * 14.0);
    if (band < 0.03) return vec3(0.0);
    float cloud = fbm3(s * 3.5, 4);
    float dust = smoothstep(0.42, 0.62, noise3(s * 9.0 + 4.0) * 0.6 + cloud * 0.5);
    return vec3(0.6, 0.65, 0.9) * band * smoothstep(0.3, 0.75, cloud) * (1.0 - 0.6 * dust) * 0.45;
}

vec3 moonHalo(vec3 dir, vec3 moon) {
    float c = max(dot(dir, moon), 0.0);
    return vec3(0.6, 0.7, 1.0) * (pow(c, 300.0) * 0.25 + pow(c, 30.0) * 0.06);
}

// A soft glow around the sun, with long rays turning slowly through it; warmer the lower it stands.
vec3 sunGlow(vec3 dir, vec3 sun, float t) {
    float c = dot(dir, sun);
    if (c <= 0.0) return vec3(0.0);
    vec3 side = normalize(abs(sun.y) < 0.99 ? cross(vec3(0.0, 1.0, 0.0), sun) : vec3(1.0, 0.0, 0.0));
    vec3 lift = cross(sun, side);
    float around = atan(dot(dir, lift), dot(dir, side)) / TAU + 0.5;
    float rays = noise1(around * 24.0 + t * 0.05, 24.0) * noise1(around * 9.0 - t * 0.03 + 3.0, 9.0);
    float halo = pow(c, 400.0) * 0.4 + pow(c, 40.0) * 0.14 + pow(c, 6.0) * 0.05;
    float beams = rays * pow(c, 18.0) * 0.3;
    vec3 tint = mix(vec3(1.0, 0.55, 0.25), vec3(1.0, 0.9, 0.7), smoothstep(0.0, 0.5, sun.y));
    return tint * (halo + beams);
}

vec3 overworldLight(vec3 dir, float t, float pixel) {
    if (dir.y < -0.02) return vec3(0.0);
    float night = ModelOffset.y;
    float clear = ModelOffset.z;
    vec3 sun = TextureMat[0].xyz;
    vec3 moon = TextureMat[1].xyz;

    // Faded towards the horizon as vanilla fades its sky disc into the fog (the disc's point in
    // this direction lies 16 / dir.y away), and so also under water or in thick fog.
    float fog = clamp(16.0 / max(dir.y, 0.001) / max(FogSkyEnd, 1.0), 0.0, 1.0);
    float visible = (1.0 - fog) * smoothstep(-0.02, 0.05, dir.y) * clear;
    if (visible <= 0.0) return vec3(0.0);

    vec3 light = vec3(0.0);
    if (night > 0.0) {
        vec3 shooting = streak(dir, t, 8.0, 0.8, 0.45, 21.0, 0.3, pixel, vec3(1.0), vec3(0.55, 0.7, 1.0))
                + streak(dir, t + 2.3, 13.0, 1.1, 0.4, 37.0, 0.3, pixel, vec3(1.0), vec3(0.55, 0.7, 1.0));
        light += (milkyWay(dir, TextureMat[2].x) + aurora(dir, t) + moonHalo(dir, moon) + shooting) * night;
    }
    if (night < 1.0 && sun.y > -0.15) {
        light += sunGlow(dir, sun, t) * (1.0 - night) * smoothstep(-0.15, 0.05, sun.y);
    }
    return light * visible;
}

#endif

// --- The End: a nebula -----------------------------------------------------------------------------

#ifdef SKY_END

// One layer of stars: in a grid over the sky half of the cells hold a star near
// their middle, a soft dot where the sky passes close to it, and the brightest a faint halo. Only
// the pixel's own cell is looked at, so a star stays inside its cell, and a layer whose cells
// shrink to a few pixels fades out rather than flicker. A star is never drawn smaller than about
// a pixel; one that would be is dimmed to keep its brightness.
float stars(vec3 dir, float scale, float pixel, float t, float seed) {
    float px = pixel * scale;
    float visible = 1.0 - smoothstep(0.12, 0.3, px);
    if (visible <= 0.0) return 0.0;
    vec3 q = dir * scale + seed;
    vec3 cell = floor(q);
    float h = hash13(cell);
    if (h < 0.5) return 0.0;
    vec3 center = cell + 0.3 + 0.4 * hash33(cell + 4.7);
    float size = 0.08 + 0.08 * hash13(cell + 1.9);
    float radius = max(size, px * 1.2);
    float d = length(q - center);
    float core = smoothstep(radius, 0.0, d) * (size * size) / (radius * radius);
    float halo = h > 0.94 ? smoothstep(0.28, 0.0, d) * 0.15 : 0.0;
    float twinkle = 0.6 + 0.4 * sin(t * (0.6 + 2.2 * hash13(cell + 3.3)) + TAU * h);
    return (core + halo) * twinkle * visible;
}

// Deep space: blue, violet and magenta gas with cyan edges and glowing wisps drifting through it,
// three layers of twinkling stars, now and then a comet, and the whole sky turning slowly about
// the vertical.
vec3 endSky(vec3 dir, float t, float pixel, float strength) {
    float turn = t * 0.006;
    vec3 d = vec3(dir.x * cos(turn) - dir.z * sin(turn), dir.y, dir.x * sin(turn) + dir.z * cos(turn));
    vec3 g = d * 1.7;
    float warp = fbm3(g * 1.1 + vec3(0.0, t * 0.012, 0.0), 2);
    float gas = fbm3(g + warp * 1.6 + vec3(t * 0.005, 0.0, -t * 0.004), 5);
    // How fast the gas changes from pixel to pixel; taken before any branch.
    float dgas = fwidth(gas);
    float detail = noise3(g * 7.0 + warp * 3.0);

    vec3 deep = vec3(0.012, 0.005, 0.024);
    vec3 blue = vec3(0.1, 0.16, 0.5);
    vec3 violet = vec3(0.38, 0.1, 0.62);
    vec3 magenta = vec3(1.0, 0.35, 0.85);
    vec3 cyan = vec3(0.2, 0.8, 0.95);

    float density = smoothstep(0.3, 0.72, gas);
    vec3 nebula = mix(blue, violet, smoothstep(0.35, 0.6, gas));
    nebula = mix(nebula, magenta, smoothstep(0.55, 0.85, gas + 0.12 * (detail - 0.5)));
    nebula = mix(nebula, cyan, smoothstep(0.5, 0.72, warp) * 0.6 * density);
    vec3 col = nebula * density * (0.55 + 0.45 * detail) * 0.85;
    // Glowing wisps along the edges of the gas, never thinner than the pixels allow.
    float width = 0.02 + dgas;
    float wisp = (exp(-pow((gas - 0.47) / width, 2.0)) + 0.5 * exp(-pow((gas - 0.58) / width, 2.0))) * 0.02 / width;
    col += mix(magenta, cyan, smoothstep(0.4, 0.7, warp)) * wisp * 0.22;
    col += magenta * pow(smoothstep(0.62, 0.9, gas), 3.0) * 0.6;

    float s = stars(d, 24.0, pixel, t, 0.0)
            + 0.8 * stars(d, 42.0, pixel, t * 1.3, 17.0)
            + 0.6 * stars(d, 75.0, pixel, t * 1.7, 41.0);
    col += vec3(0.95, 0.9, 1.0) * s * (1.0 - 0.5 * density);
    col += streak(dir, t, 5.0, 1.2, 0.6, 5.0, -0.5, pixel, vec3(1.0, 0.92, 1.0), vec3(0.75, 0.35, 1.0));
    return deep + col * strength;
}

#endif

void main() {
    vec3 dir = normalize(skyPos);
    // How far one pixel reaches across the sky, as an angle. Taken here, before any branch, where
    // the derivatives are defined.
    float pixel = max(length(dFdx(dir)), length(dFdy(dir)));
    float t = ModelOffset.x;
    float strength = ColorModulator.a;

#if defined(SKY_NETHER)
    vec3 fog = FogColor.rgb;
    fragColor = vec4(mix(fog, netherSky(dir, t, pixel, fog), strength), 1.0);
#elif defined(SKY_END)
    fragColor = vec4(endSky(dir, t, pixel, strength), 1.0);
#else
    // Light added over vanilla's sky: blended as premultiplied alpha with nothing taken away.
    fragColor = vec4(overworldLight(dir, t, pixel) * strength, 0.0);
#endif
}

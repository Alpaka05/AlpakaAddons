#version 330

// Sky Effects: the animated skies. One shader, built four times with a different define -
// SKY_NETHER, SKY_OVERWORLD, SKY_END, and SKY_OVERWORLD with SKY_CLOUDS - by SkyEffectsFeature.
//
// It is drawn on a box around the camera, and skyPos is the box's point under the pixel, so its
// direction is where in the world the pixel looks. Everything below is a function of that
// direction and of the time, built from a grid of random values (Sampler0), so it has no seams.
//
// The Overworld's clouds, the costly part, are worked out at half the resolution in each
// direction (SKY_CLOUDS, into a texture of their own) and laid over the sky from there
// (Sampler1): they are soft enough that this does not show, and cost a quarter. That pass skips
// the texels the world covers, from the frame's depth (its Sampler1).
//
// The parameters ride in vanilla's DynamicTransforms block, which every sky draw fills anyway:
//   ColorModulator.a  - Intensity, 0..1
//   ColorModulator.x  - the far plane's depth in clip space, for the vertex shader
//   ColorModulator.yz - Overworld: the size of the frame in pixels, to find the clouds' texel
//   ModelOffset.x     - the time in seconds, already scaled by Animation Speed
//   ModelOffset.y     - Overworld: how far into the night it is, 0 by day .. 1 at night
//   ModelOffset.z     - Overworld: how clear the sky is, 1 .. 0 in heavy rain
//   TextureMat[0].xyz - Overworld: towards the sun
//   TextureMat[1].xyz - Overworld: towards the moon
//   TextureMat[2].x   - Overworld: the stars' angle, which turns the Milky Way with them
//   TextureMat[3].xy  - Overworld: 1 where the lights (aurora, sun glow, ...) and the clouds are on
// The clouds' own pass (SKY_CLOUDS) gets a block of its own: ColorModulator.yz the sun's x and y
// (its z is always 0), ColorModulator.w Intensity plus twice which half of the texels is worked
// out anew (1 or 2, 0 for all of them), and TextureMat where the last frame saw the sky: its
// projection times its view.
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

// Random values on a 256x256 grid that wraps around, filtered linearly: r and b two independent
// grids, g the r grid moved by (37, 17), so two slices of a 3D grid come in one lookup.
uniform sampler2D Sampler0;

#if defined(SKY_CLOUDS)
// The frame's depth so far: 0 where nothing has been drawn (the depth buffer is reversed).
uniform sampler2D Sampler1;
// The clouds' last frame, as this pass wrote them.
uniform sampler2D Sampler2;

#elif defined(SKY_OVERWORLD)
// The clouds, worked out at half resolution, premultiplied and with Intensity applied.
uniform sampler2D Sampler1;
#endif

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
float valueNoise1(float x, float period) {
    float i = floor(x);
    float u = fract(x);
    u = u * u * (3.0 - 2.0 * u);
    return mix(hash11(mod(i, period)), hash11(mod(i + 1.0, period)), u);
}

// Value noise from the grid texture: the filter blends the four corners of the cell, and moving
// the lookup inside the cell along the smoothstep curve makes that blend the smooth one, so one
// lookup does the work of four hashes and three mixes.
vec4 noiseGrid(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    // The grid has one level, so no level needs choosing; textureLod also keeps lookups in
    // branches, where most of them are, well defined.
    return textureLod(Sampler0, (i + u + 0.5) / 256.0, 0.0);
}

float valueNoise2(vec2 p) {
    return noiseGrid(p).r;
}

// Two independent noises in the same lookup, to push a 2D field around.
vec2 noise22(vec2 p) {
    return noiseGrid(p).rb;
}

// 3D: each slice of the grid is the one below moved by (37, 17), so a cell's lower and upper
// slices are the texture's r and g at one place, blended along z.
float valueNoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    vec2 slices = textureLod(Sampler0, (i.xy + vec2(37.0, 17.0) * i.z + u.xy + 0.5) / 256.0, 0.0).rg;
    return mix(slices.x, slices.y, u.z);
}

// Each octave is turned against the one before, so the grids the noise is built on never line up.
const mat2 TURN2 = mat2(0.80, 0.60, -0.60, 0.80);
const mat3 TURN3 = mat3(0.00, 0.80, 0.60, -0.80, 0.36, -0.48, -0.60, -0.48, 0.64);

float fbm2(vec2 p, int octaves) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < octaves; i++) {
        value += amplitude * valueNoise2(p);
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
        value += amplitude * valueNoise3(p);
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
    float trail = (1.0 - smoothstep(0.0, width, d)) * h * h;
    float glow = (1.0 - smoothstep(0.0, width * 6.0, length(dir - head))) * 0.5;
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
    // Born near the horizon, burnt out on the way up: nothing outside that band.
    if (dir.y <= -0.04 || dir.y >= 0.8) return 0.0;
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
    float spark = (1.0 - smoothstep(0.0, radius, length(d))) * (size * size) / (radius * radius);
    float flicker = 0.55 + 0.45 * sin(t * (5.0 + 9.0 * h) + h * 120.0);
    float brightness = 0.35 + 0.65 * hash12(cell + seed + 5.3);
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
    // Behind the clouds the sky glows in the fog's color low down and darkens overhead, so the
    // horizon still meets the land in the color the distant land fades into.
    vec3 base = mix(fog, hue * 0.12 + fog * 0.22, smoothstep(0.0, 0.7, up));

    // The storm thins out towards the horizon, into the haze the distant land fades into, and is
    // gone below 0.02: only worked out above. The derivative below is then taken in a branch, so
    // in a quad across -0.01 it is undefined, even NaN; all of such a quad lies below 0.02 (a
    // pixel would have to span 0.03 rad otherwise), where the storm is not mixed in at all.
    vec3 col = base;
    if (dir.y > -0.01) {
        // The clouds lie on a plane overhead, wound into a vortex that turns slowly about the zenith...
        vec2 p = dir.xz / (up + 0.1);
        float twist = t * 0.035 + 0.8 * log(length(p) + 0.3);
        p = mat2(cos(twist), sin(twist), -sin(twist), cos(twist)) * p;
        // ... and boil: their field is pushed around by a second, slower one.
        vec2 warp = fbm22(p * 0.55 + vec2(t * 0.045, -t * 0.03), 3);
        float f = fbm2(p * 0.9 + warp * 2.4 + vec2(-t * 0.07, t * 0.045), 5);
        // How fast the field changes from pixel to pixel.
        float df = fwidth(f);

        // Lit from below: the lower the clouds, the more of the fires' light reaches them.
        float glow = exp(-up * 2.5);

        // Thick smoke darkens the sky. Where the clouds are hot they glow, and the hottest burn
        // through white; which parts of the storm burn wanders in large patches. Thin glowing
        // seams run along the clouds' edges there, never thinner than the pixels allow.
        float body = smoothstep(0.3, 0.75, f);
        float fire = smoothstep(0.3, 0.7, valueNoise2(p * 0.22 + vec2(t * 0.02, -t * 0.015) + 3.0));
        // Flame tongues: fine, fast detail licking along the hot clouds.
        float tongues = valueNoise2(p * 4.0 + warp * 3.0 + vec2(0.0, -t * 0.4)) - 0.5;
        float heat = smoothstep(0.48, 0.86, f + 0.25 * (warp.y - 0.5) + 0.12 * tongues) * (0.35 + 0.65 * fire);
        float width = 0.02 + df;
        // Squared by hand: pow() is undefined for a negative base, and Apple's GPUs return NaN there.
        float seamD = (f - 0.5) / width;
        float seam = exp(-seamD * seamD) * 0.02 / width * fire;
        vec3 storm = base * mix(1.0, 0.25, body);
        storm = mix(storm, flame * (0.55 + 0.45 * glow), clamp(heat * 1.15 + seam * 0.35, 0.0, 1.0));
        storm += whiteHot * pow(heat, 4.0) * 0.7;
        storm += whiteHot * stormFlash(dir, t) * (0.25 + 0.75 * body);
        float weight = smoothstep(0.02, 0.3, dir.y);
        if (weight > 0.0) col = mix(base, storm, weight);
    }

    float sparks = embers(dir, t, 96.0, 0.7, pixel, 0.0) + 0.7 * embers(dir, t, 160.0, 1.1, pixel, 7.0);
    col += (flame + whiteHot * 0.3) * sparks;
    col += streak(dir, t, 5.0, 1.3, 0.6, 3.0, 0.35, pixel, whiteHot, flame);
    return col;
}

#endif

// --- The Overworld: aurora, Milky Way and shooting stars by night, clouds and a glowing sun by day -

#ifdef SKY_OVERWORLD

// Two curtains of light over the northern sky. Each hangs from a lower edge that waves and folds
// along the horizon and drifts, is brightest along that edge and fades upwards, and is combed into
// fine rays that slide along it, with brighter and dimmer stretches wandering through. Green low
// down and violet at the top, as the real one.
vec3 aurora(vec3 dir, float t) {
    // North is -Z in Minecraft: 0 there, +-pi in the south, where the curtains have faded out.
    // Straight up the direction has none (atan(0, 0) is undefined); the curtains hang far below.
    if (dir.x * dir.x + dir.z * dir.z < 1e-10) return vec3(0.0);
    float azimuth = atan(dir.x, -dir.z);
    float spread = 1.0 - smoothstep(1.0, 2.4, abs(azimuth));
    if (spread <= 0.0) return vec3(0.0);
    float elevation = asin(clamp(dir.y, -1.0, 1.0));
    vec3 light = vec3(0.0);
    for (int i = 0; i < 2; i++) {
        float n = float(i);
        float x = azimuth * (1.0 + 0.35 * n) + n * 2.7;
        float edge = 0.19 + 0.1 * n
                + 0.06 * sin(x * 1.6 + t * 0.04)
                + 0.035 * sin(x * 4.3 - t * 0.07 + 1.3)
                + 0.03 * (valueNoise1(x * 7.0 + t * 0.1, 1000.0) - 0.5);
        float h = elevation - edge;
        if (h < -0.03) continue;
        float rays = valueNoise1(x * 70.0 + t * 0.35, 1000.0) * 0.6 + valueNoise1(x * 23.0 - t * 0.2, 1000.0) * 0.4;
        rays = 0.35 + 0.65 * rays * rays;
        float pulse = smoothstep(0.2, 0.8, valueNoise1(x * 2.5 - t * 0.12 + n * 5.0, 1000.0));
        // The bright stretches reach higher, as where a real curtain folds towards the viewer.
        float reach = 0.12 + 0.1 * valueNoise1(x * 3.0 + t * 0.05 + n * 7.0, 1000.0) + 0.12 * pulse;
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
    float dust = smoothstep(0.42, 0.62, valueNoise3(s * 9.0 + 4.0) * 0.6 + cloud * 0.5);
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
    // The sun moves in the XY plane, so Z is never parallel to it: a frame that turns smoothly
    // with it all day, without a jump at noon.
    vec3 side = normalize(cross(vec3(0.0, 0.0, 1.0), sun));
    vec3 lift = cross(sun, side);
    // atan(0, 0) is undefined; the very middle of the sun gets a fixed angle.
    vec2 across = vec2(dot(dir, side), dot(dir, lift));
    float around = dot(across, across) > 1e-12 ? atan(across.y, across.x) / TAU + 0.5 : 0.0;
    float rays = valueNoise1(around * 24.0 + t * 0.05, 24.0) * valueNoise1(around * 9.0 - t * 0.03 + 3.0, 9.0);
    float halo = pow(c, 400.0) * 0.4 + pow(c, 40.0) * 0.14 + pow(c, 6.0) * 0.05;
    float beams = rays * pow(c, 18.0) * 0.3;
    vec3 tint = mix(vec3(1.0, 0.55, 0.25), vec3(1.0, 0.9, 0.7), smoothstep(0.0, 0.5, sun.y));
    return tint * (halo + beams);
}

// Clouds drifting with the wind in a layer of some thickness high above. A 2D noise says how much
// cloud stands over each spot of the ground, and the cloud there reaches the higher up the more
// there is: flat bottoms and domed tops, like heaped cumulus. Each pixel walks its ray up through
// the layer, so towards the horizon it sees past the bottoms onto the sides and tops, and nearer
// clouds hide farther ones. A height met on the ray is lit by how much cloud lies above it towards
// the sun, so the tops facing the sun are bright and the bottoms and the far sides grey. The layer
// is reached at dir.xz / dir.y, so the clouds shrink and crowd together towards the horizon.
// Thin edges light up in front of the sun, thin streaks of cirrus lie higher up. Golden and pink
// at sunrise and sunset, dimming into the night; in rain they close up into grey overcast.
// Returned premultiplied: color times cover, and how much of the sky they cover.

const int CLOUD_STEPS_MAX = 18;
const float CLOUD_DEPTH = 0.45;

// How much cloud stands over a spot (scaled layer coordinates), minus where clouds begin.
float cloudExcess(vec2 at, float edge, int octaves) {
    return fbm2(at, octaves) - edge;
}

// fbm2(at, 4), or -1 as soon as the octaves still to come can no longer lift it above `needed`:
// most of a ray's samples lie in open sky, and those are known after an octave or two. Each
// octave adds at most its amplitude, so what is left after octave n is at most 0.5^n... summed.
float cloudField(vec2 at, float needed) {
    float value = 0.5 * valueNoise2(at);
    if (value + 0.4375 <= needed) return -1.0;
    at = TURN2 * at * 2.03 + 17.17;
    value += 0.25 * valueNoise2(at);
    if (value + 0.1875 <= needed) return -1.0;
    at = TURN2 * at * 2.03 + 17.17;
    value += 0.125 * valueNoise2(at);
    if (value + 0.0625 <= needed) return -1.0;
    at = TURN2 * at * 2.03 + 17.17;
    return value + 0.0625 * valueNoise2(at);
}

// How dense the cloud is at a height h through the layer (0 bottom, 1 top), given the excess
// there: the more excess, the higher the cloud reaches, rising like a dome from the edges.
float cloudDensity(float excess, float h) {
    float bottom = smoothstep(0.0, 0.12, h);
    return smoothstep(0.0, 0.14, excess - 0.26 * h * h - 0.05 * (1.0 - bottom));
}

// How far above h the cloud with this excess still reaches, through the layer (0 to 1).
float cloudAbove(float excess, float h) {
    return clamp(sqrt(max(excess, 0.0) / 0.26), 0.0, 1.0) - h;
}

vec4 clouds(vec3 dir, vec3 sun, float t, float night, float clear) {
    // Where the layer is too far away to show more than flicker, it has faded out.
    float horizon = smoothstep(0.03, 0.2, dir.y);
    float dim = 1.0 - night;
    if (horizon <= 0.0 || dim <= 0.0) return vec4(0.0);

    // The 0.08 keeps the far side of the layer from running off to infinity at the horizon.
    vec2 onLayer = dir.xz / (dir.y + 0.08);
    vec2 drift = vec2(0.010, 0.0035) * t;
    // Their tops lean a little downwind of their bottoms.
    const vec2 LEAN = vec2(0.3, 0.105);
    float cover = mix(0.95, 0.5, clear);
    float edge = 0.72 - 0.4 * cover;

    // A slower noise pushes the clouds around and makes them change shape as they go. It changes
    // little over one ray, so it is taken once, halfway up.
    vec2 mid = (onLayer * (1.0 + 0.5 * CLOUD_DEPTH) + drift) * 2.2;
    vec2 warp = (fbm22(mid * 0.3 + vec2(t * 0.003, 0.0), 3) - 0.5) * 0.9;

    float low = 1.0 - smoothstep(0.0, 0.45, sun.y);
    vec3 sunlit = mix(vec3(1.0, 0.99, 0.96), vec3(1.0, 0.62, 0.42), low);
    vec3 shade = mix(vec3(0.52, 0.58, 0.70), vec3(0.42, 0.31, 0.47), low);
    // Overcast is grey, whatever the sun does.
    sunlit = mix(vec3(0.66, 0.68, 0.72), sunlit, clear);
    shade = mix(vec3(0.36, 0.38, 0.43), shade, clear);
    float fade = horizon * dim;

    float forward = pow(max(dot(dir, sun), 0.0), 8.0) * clear;

    // Towards the sun across the layer, per unit of height: the lower the sun, the farther.
    vec2 towardsSun = normalize(sun.xz + vec2(1e-4, 0.0)) * 2.2 * CLOUD_DEPTH
            / max(sun.y, 0.25) * 0.35;

    // A flat ray crosses much more of the layer per step, so it takes more of them.
    int steps = int(mix(float(CLOUD_STEPS_MAX), 12.0, smoothstep(0.1, 0.6, dir.y)));
    // A fixed random offset per pixel on where the steps fall hides the slices between them.
    float jitter = hash12(gl_FragCoord.xy);
    float stepH = 1.0 / float(steps);
    // A ray climbs the layer over a longer stretch the lower it looks; how thick one step is.
    float stepLength = CLOUD_DEPTH / max(dir.y + 0.08, 0.1) * stepH;

    vec3 color = vec3(0.0);
    float through = 1.0;
    for (int i = 0; i < steps; i++) {
        float h = (float(i) + jitter) * stepH;
        // Higher up the wind is a little faster.
        vec2 at = (onLayer * (1.0 + h * CLOUD_DEPTH) + drift + LEAN * h) * 2.2 + warp;
        // Below this the field holds no cloud at this height (see cloudDensity); the billows
        // only take away, so they need not be looked at there either.
        float needed = edge + 0.26 * h * h + 0.05 * (1.0 - smoothstep(0.0, 0.12, h));
        float field = cloudField(at, needed);
        if (field <= needed) continue;
        // Small billows that change with height, so the sides are not smooth walls.
        float excess = field - edge - 0.07 * valueNoise2(at * 5.0 + vec2(h * 3.0, -h * 2.0));
        float density = cloudDensity(excess, h);
        if (density <= 0.0) continue;

        // Light reaching this point: dimmed by the cloud still above it, and by cloud lying
        // towards the sun. Thin edges and tops come out bright, thick middles grey underneath.
        float overhead = max(cloudAbove(excess, h), 0.0);
        // Two octaves are enough for a soft shadow; the third's average (0.0625) stands in for it.
        float sunward = max(cloudAbove(cloudExcess(at + towardsSun * (1.0 - h), edge, 2) + 0.0625, h), 0.0);
        float lit = exp(-1.5 * overhead - 1.8 * sunward);
        vec3 c = mix(shade, sunlit, 0.3 + 0.7 * lit) * mix(0.9, 1.0, h);
        // The thin edges light up in front of the sun.
        c += sunlit * forward * (1.0 - density) * 0.9;

        float alpha = 1.0 - exp(-density * 14.0 * stepLength);
        color += c * alpha * through;
        through *= 1.0 - alpha;
        if (through < 0.03) break;
    }

    // Cirrus: fine streaks combed out along the wind, higher and faster, seen only through the
    // gaps of the cumulus.
    float cirrus = 0.0;
    if (through > 0.03 && clear > 0.0) {
        vec2 r = TURN2 * (onLayer * 0.8 + drift * 1.8);
        float wisps = fbm2(vec2(r.x * 1.2, r.y * 4.0) + 3.1, 4);
        cirrus = smoothstep(0.55, 0.8, wisps) * 0.3 * clear;
    }
    color += sunlit * cirrus * through;
    float alpha = (1.0 - through * (1.0 - cirrus)) * fade;
    color *= fade * mix(0.3, 1.0, dim);
    return vec4(color, alpha);
}

vec3 overworldLight(vec3 dir, float t, float pixel, float night, vec3 sun, vec3 moon) {
    vec3 light = vec3(0.0);
    if (night > 0.0) {
        vec3 shooting = streak(dir, t, 8.0, 0.8, 0.45, 21.0, 0.3, pixel, vec3(1.0), vec3(0.55, 0.7, 1.0))
                + streak(dir, t + 2.3, 13.0, 1.1, 0.4, 37.0, 0.3, pixel, vec3(1.0), vec3(0.55, 0.7, 1.0));
        light += (milkyWay(dir, TextureMat[2].x) + aurora(dir, t) + moonHalo(dir, moon) + shooting) * night;
    }
    if (night < 1.0 && sun.y > -0.15) {
        light += sunGlow(dir, sun, t) * (1.0 - night) * smoothstep(-0.15, 0.05, sun.y);
    }
    return light;
}

// How much of the Overworld's sky shows in this direction: faded towards the horizon as vanilla
// fades its sky disc into the fog (the disc's point in this direction lies 16 / dir.y away), and
// so also under water or in thick fog.
float overworldOpen(vec3 dir) {
    float fog = clamp(16.0 / max(dir.y, 0.001) / max(FogSkyEnd, 1.0), 0.0, 1.0);
    return (1.0 - fog) * smoothstep(-0.02, 0.05, dir.y);
}

#ifdef SKY_CLOUDS

// Whether the world covers every pixel that looks this texel up when the clouds are laid over the
// sky. The filter blends each pixel's four nearest texels, so a texel reaches the pixels around
// its own 2x2; with an odd frame size the two grids drift apart by up to a pixel, so 5x5 of them
// are looked at. Only then is the texel never seen; a texel next to the sky must keep its
// clouds, or they would thin out along the world's edges. Most texels in open sky stop at the
// first pixel.
bool coveredByWorld() {
    ivec2 last = textureSize(Sampler1, 0) - 1;
    ivec2 corner = ivec2(gl_FragCoord.xy) * 2 - 2;
    // The corners first: a texel along the world's edge mostly has sky at one of them.
    if (texelFetch(Sampler1, clamp(corner, ivec2(0), last), 0).r <= 0.0
            || texelFetch(Sampler1, clamp(corner + ivec2(4, 0), ivec2(0), last), 0).r <= 0.0
            || texelFetch(Sampler1, clamp(corner + ivec2(0, 4), ivec2(0), last), 0).r <= 0.0
            || texelFetch(Sampler1, clamp(corner + ivec2(4, 4), ivec2(0), last), 0).r <= 0.0) {
        return false;
    }
    for (int y = 0; y < 5; y++) {
        for (int x = 0; x < 5; x++) {
            ivec2 at = clamp(corner + ivec2(x, y), ivec2(0), last);
            if (texelFetch(Sampler1, at, 0).r <= 0.0) return false;
        }
    }
    return true;
}

// What a texel the world covers holds: no cloud (alpha 0) never has any color, so this cannot be
// a real texel, and the next frame knows not to reuse it. No pixel ever blends it in.
const vec4 COVERED = vec4(1.0 / 255.0, 0.0, 0.0, 0.0);

bool isCovered(vec4 texel) {
    return texel.a == 0.0 && texel.r > 0.5 / 255.0;
}

// The clouds of the last frame in this direction, when they are there to reuse: the last frame's
// projection and view (TextureMat) find where the box's point in this direction was on the
// screen, and the four texels around it are blended as the filter would, unless one of them was
// off the screen or covered. The clouds depend on the direction alone, and drift by a hair a
// frame, so this is as good as working them out.
bool lastClouds(int reused, out vec4 result) {
    // The tiles the last frame reused are the ones this frame works out anew.
    int lastReused = 1 - reused;
    // The box's own point, as it was drawn, so view bobbing moves it as it moved the sky.
    vec4 clip = TextureMat * vec4(skyPos, 1.0);
    if (clip.w <= 0.0) return false;
    ivec2 size = textureSize(Sampler2, 0);
    vec2 texel = (clip.xy / clip.w * 0.5 + 0.5) * vec2(size) - 0.5;
    vec2 base = floor(texel);
    ivec2 at = ivec2(base);
    if (at.x < 0 || at.y < 0 || at.x + 1 >= size.x || at.y + 1 >= size.y) return false;
    vec4 a = texelFetch(Sampler2, at, 0);
    vec4 b = texelFetch(Sampler2, at + ivec2(1, 0), 0);
    vec4 c = texelFetch(Sampler2, at + ivec2(0, 1), 0);
    vec4 d = texelFetch(Sampler2, at + ivec2(1, 1), 0);
    if (isCovered(a) || isCovered(b) || isCovered(c) || isCovered(d)) return false;
    // Only texels worked out in the last frame are taken, never ones it took from the frame
    // before: the tiles it worked out had the other parity. Reused clouds are a frame old at
    // most, and filtered once.
    // A square's neighbours across a tile edge have the other parity, so the four texels must
    // lie in one tile.
    ivec2 tile = at >> 3;
    if (((tile.x + tile.y) & 1) == lastReused || (at.x & 7) == 7 || (at.y & 7) == 7) return false;
    vec2 f = texel - base;
    result = mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
    return true;
}

// The clouds alone, premultiplied and with Intensity applied, for the half-resolution texture.
// Half the texels reuse the last frame's clouds where they can, in a checkerboard of 8x8 tiles
// that swaps each frame: tiles, so the GPU's neighbouring threads, which run together, take the
// same way.
vec4 cloudLayer(vec3 dir, float t) {
    if (dir.y < -0.02) return vec4(0.0);
    float open = overworldOpen(dir);
    if (open <= 0.0) return vec4(0.0);
    if (coveredByWorld()) return COVERED;
    float fresh = floor(ColorModulator.w * 0.5);
    float strength = ColorModulator.w - 2.0 * fresh;
    if (fresh > 0.5) {
        ivec2 tile = ivec2(gl_FragCoord.xy) >> 3;
        vec4 last;
        int reused = 1 - (int(fresh + 0.5) - 1);
        if (((tile.x + tile.y) & 1) == reused && lastClouds(reused, last)) return last;
    }
    vec3 sun = vec3(ColorModulator.yz, 0.0);
    return clouds(dir, sun, t, ModelOffset.y, ModelOffset.z) * open * strength;
}

#else

// The Overworld's sky over vanilla's, Intensity (strength) applied.
vec4 overworldSky(vec3 dir, float t, float pixel, float strength) {
    if (dir.y < -0.02) return vec4(0.0);
    float night = ModelOffset.y;
    float clear = ModelOffset.z;
    vec3 sun = TextureMat[0].xyz;
    vec3 moon = TextureMat[1].xyz;
    bool lightsOn = TextureMat[3].x > 0.5;
    bool cloudsOn = TextureMat[3].y > 0.5;

    float open = overworldOpen(dir);
    if (open <= 0.0) return vec4(0.0);
    float visible = open * clear;

    // The clouds are laid over the lights, hiding some of the sun's glow as they pass it; where
    // they hide all of it, the lights need not be worked out. Their texture holds a quarter of
    // the pixels, with Intensity already in; the filter blends the four nearest.
    vec4 cloud = cloudsOn ? textureLod(Sampler1, gl_FragCoord.xy / ColorModulator.yz, 0.0) : vec4(0.0);
    float cover = cloud.a / strength;
    vec3 light = vec3(0.0);
    // The march stops at 3% light through, so about 0.97 is as thick as clouds get; the lights
    // fade out before that rather than stop at a line.
    float behind = 1.0 - smoothstep(0.93, 0.96, cover);
    if (lightsOn && visible > 0.0 && behind > 0.0) {
        light = overworldLight(dir, t, pixel, night, sun, moon) * visible * behind;
    }
    return vec4(cloud.rgb + light * strength * (1.0 - cover), cloud.a);
}

#endif

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
    float core = (1.0 - smoothstep(0.0, radius, d)) * (size * size) / (radius * radius);
    float halo = h > 0.94 ? (1.0 - smoothstep(0.0, 0.28, d)) * 0.15 : 0.0;
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

    vec3 deep = vec3(0.012, 0.005, 0.024);
    vec3 blue = vec3(0.1, 0.16, 0.5);
    vec3 violet = vec3(0.38, 0.1, 0.62);
    vec3 magenta = vec3(1.0, 0.35, 0.85);
    vec3 cyan = vec3(0.2, 0.8, 0.95);

    float density = smoothstep(0.3, 0.72, gas);
    // Fine detail, which only shows where there is gas.
    float detail = density > 0.0 ? valueNoise3(g * 7.0 + warp * 3.0) : 0.0;
    vec3 nebula = mix(blue, violet, smoothstep(0.35, 0.6, gas));
    nebula = mix(nebula, magenta, smoothstep(0.55, 0.85, gas + 0.12 * (detail - 0.5)));
    nebula = mix(nebula, cyan, smoothstep(0.5, 0.72, warp) * 0.6 * density);
    vec3 col = nebula * density * (0.55 + 0.45 * detail) * 0.85;
    // Glowing wisps along the edges of the gas, never thinner than the pixels allow.
    float width = 0.02 + dgas;
    // Squared by hand: pow() is undefined for a negative base, and Apple's GPUs return NaN there.
    float wispA = (gas - 0.47) / width;
    float wispB = (gas - 0.58) / width;
    float wisp = (exp(-wispA * wispA) + 0.5 * exp(-wispB * wispB)) * 0.02 / width;
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
#elif defined(SKY_CLOUDS)
    // Intensity applied before the texture's 8 bits clamp them, as it was when they were worked
    // out in the same pass as the rest.
    fragColor = cloudLayer(dir, t);
#else
    // Light added over vanilla's sky and clouds laid over it, blended as premultiplied alpha.
    fragColor = overworldSky(dir, t, pixel, strength);
#endif
}

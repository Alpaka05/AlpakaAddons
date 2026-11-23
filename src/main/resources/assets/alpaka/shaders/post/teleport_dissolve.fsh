#version 330

// The Dissolve style of Teleport Warp: the picture of the old place breaks up into small blocks
// that give way to the new place, starting in the middle and sweeping outward, with a sparkling
// light-blue edge where they break. OldSampler holds the last frame before the teleport,
// InSampler the frame now. By the end of Progress every block has gone.

uniform sampler2D InSampler;
uniform sampler2D OldSampler;

layout(std140) uniform WarpConfig {
    // How brightly the edge sparkles: the player's strength setting.
    float Amount;
    // How far through the effect, 0 to 1; moves the edge outward.
    float Progress;
    // Width over height, so the sweep is round on a wide screen.
    float Aspect;
};

in vec2 texCoord;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
               mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

void main() {
    vec2 size = vec2(textureSize(InSampler, 0));
    // Blocks of about an 80th of the screen's height, whatever the resolution.
    float block = max(2.0, floor(size.y / 80.0));
    vec2 cell = floor(texCoord * size / block);
    vec2 d = texCoord - 0.5;
    float r = length(vec2(d.x * Aspect, d.y));

    // When each block goes: a little random, a little patchy, mostly by its distance from the
    // middle. The highest values, in the corners, stay below the threshold's last value of 1.3.
    float n = hash(cell) * 0.45 + noise(cell / 9.0) * 0.25 + r * 0.55;
    float t = Progress * 1.45 - 0.15;

    vec3 newColor = texture(InSampler, texCoord).rgb;
    // The old picture shimmers a little while it breaks up.
    vec2 shimmer = vec2(noise(cell * 1.3 + Progress * 40.0) - 0.5, 0.0) * 0.004 * (1.0 - Progress);
    vec3 oldColor = texture(OldSampler, texCoord + shimmer).rgb;
    vec3 color = mix(oldColor, newColor, step(n, t));

    float edge = 1.0 - smoothstep(0.0, 0.06, abs(n - t));
    float spark = step(0.6, hash(cell + floor(Progress * 30.0)));
    color += vec3(0.55, 0.85, 1.0) * edge * (0.6 + 0.8 * spark) * clamp(Amount, 0.25, 2.0) * 0.8;

    fragColor = vec4(color, texture(InSampler, texCoord).a);
}

#version 330

// The Hyperspace style of Teleport Effects: a jump to light speed. The frame zooms in and blurs
// outward from the middle, and thin streaks of light shoot past towards the edges, each a dash that
// grows longer the further out it gets. Everything scales with Amount, so 0 gives back the frame
// unchanged.

uniform sampler2D InSampler;

layout(std140) uniform WarpConfig {
    // How strong the effect is right now: the envelope times the player's strength setting.
    float Amount;
    // How far through the effect, 0 to 1; moves the streaks outward.
    float Progress;
    // Width over height, so the streaks run straight out on a wide screen.
    float Aspect;
};

in vec2 texCoord;

out vec4 fragColor;

const int SAMPLES = 10;
const float TAU = 6.28318530718;

float hash(float n) {
    return fract(sin(n * 127.1) * 43758.5453);
}

// One set of streaks: `count` slots around the middle, about half of them holding a dash that flies
// outward at its own speed. Returns how much of a streak covers this pixel.
float streaks(float angle, float r, float count, float layer) {
    float a = angle / TAU * count;
    float cell = floor(a);
    float seed = cell + layer * 97.0;
    if (hash(seed) > 0.5) return 0.0;

    // Distance across the streak, in screen units, so it stays thin however far out it is.
    float across = abs(fract(a) - 0.5) * (TAU / count) * r;
    float width = 0.0010 + 0.0018 * r;
    float line = 1.0 - smoothstep(width * 0.4, width, across);

    float speed = 0.8 + 1.2 * hash(seed + 7.3);
    float head = fract(hash(seed + 5.7) + Progress * speed) * 1.6;
    float len = (0.25 + 0.45 * hash(seed + 9.1)) * head;
    float along = clamp((r - (head - len)) / max(len, 1e-3), 0.0, 1.0) * step(r, head);
    return line * along * along;
}

void main() {
    vec2 d = texCoord - 0.5;
    vec2 v = vec2(d.x * Aspect, d.y);
    float r = length(v);
    float angle = atan(v.y, v.x);

    // Below 1 reads closer to the middle, which enlarges it; the blur smears the picture outward.
    float scale = 1.0 - Amount * 0.12;
    float blur = Amount * 0.11 * smoothstep(0.0, 0.9, r);
    vec3 sum = vec3(0.0);
    for (int i = 0; i < SAMPLES; i++) {
        sum += texture(InSampler, 0.5 + d * (scale - blur * (float(i) / float(SAMPLES - 1)))).rgb;
    }
    vec3 color = sum / float(SAMPLES);

    // A cool, slightly darker rim, so the streaks read against any sky.
    float rim = smoothstep(0.2, 1.0, r) * min(Amount, 1.5);
    color = mix(color, color * vec3(0.7, 0.8, 1.05), rim * 0.5);

    float s = streaks(angle, r, 150.0, 0.0) + 0.7 * streaks(angle + 0.011, r, 95.0, 1.0);
    s *= smoothstep(0.1, 0.45, r) * Amount;
    color += vec3(0.8, 0.9, 1.0) * clamp(s, 0.0, 1.5);

    fragColor = vec4(color, texture(InSampler, texCoord).a);
}

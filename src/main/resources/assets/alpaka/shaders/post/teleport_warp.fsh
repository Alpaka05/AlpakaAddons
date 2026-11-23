#version 330

// The screen warp after a teleport. Reads a copy of the frame and draws it back pulled towards the
// middle, as if the view lunged forward: the centre zooms in, a ring of distortion runs outward,
// the edges smear into radial streaks with red and blue fringes, and a faint violet tint sits in
// the corners. Everything scales with Amount, so 0 gives back the frame unchanged.

uniform sampler2D InSampler;

layout(std140) uniform WarpConfig {
    // How strong the warp is right now: the envelope times the player's strength setting.
    float Amount;
    // How far through the effect, 0 to 1; drives the outward ring.
    float Progress;
    // Width over height, so the warp is round on a wide screen.
    float Aspect;
};

in vec2 texCoord;

out vec4 fragColor;

const int SAMPLES = 8;

void main() {
    vec2 d = texCoord - 0.5;
    float r = length(vec2(d.x * Aspect, d.y));

    float ringRadius = mix(0.05, 1.15, Progress);
    float ring = exp(-pow((r - ringRadius) * 7.0, 2.0));

    // Below 1 reads closer to the middle, which enlarges it.
    float scale = 1.0 - Amount * (0.07 * (1.0 - smoothstep(0.0, 1.0, r)) + 0.035 * ring);
    float streak = Amount * 0.05 * smoothstep(0.1, 1.0, r);
    float fringe = Amount * 0.012 * r;

    vec3 sum = vec3(0.0);
    for (int i = 0; i < SAMPLES; i++) {
        float s = scale - streak * (float(i) / float(SAMPLES - 1));
        sum.r += texture(InSampler, 0.5 + d * (s + fringe)).r;
        sum.g += texture(InSampler, 0.5 + d * s).g;
        sum.b += texture(InSampler, 0.5 + d * (s - fringe)).b;
    }
    vec3 color = sum / float(SAMPLES);

    float tint = clamp(smoothstep(0.35, 1.05, r) * Amount * 0.5, 0.0, 0.6);
    color = mix(color, color * vec3(0.75, 0.6, 1.0) + vec3(0.08, 0.02, 0.14), tint);

    fragColor = vec4(color, texture(InSampler, texCoord).a);
}

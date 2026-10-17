#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

// The picture, stretched over the whole rectangle.
uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 localPos;
flat in vec2 halfSize;
flat in vec2 shape;
flat in float opacity;
// Reach in screen pixels and strength of the drop shadow; a reach of 0 means none.
flat in vec2 shadow;

out vec4 fragColor;

float roundedBoxDistance(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + vec2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, vec2(0.0))) - r;
}

void main() {
    float radius = clamp(shape.x, 0.0, min(halfSize.x, halfSize.y));
    float d = roundedBoxDistance(localPos, halfSize, radius);
    float coverage = 1.0 - smoothstep(-0.5, 0.5, d);

    float shadowAlpha = 0.0;
    if (shadow.x > 0.5) {
        // The same soft shadow as the blur panels: the shape moved down a little, fading out
        // before the quad's edge.
        float offset = shadow.x * 0.3;
        float ds = roundedBoxDistance(localPos - vec2(0.0, offset), halfSize, radius);
        float t = 1.0 - clamp(ds / (shadow.x - offset), 0.0, 1.0);
        t = t * t * (3.0 - 2.0 * t);
        shadowAlpha = shadow.y * t * t;
    }
    coverage *= opacity;
    shadowAlpha *= opacity * (1.0 - coverage);
    if (coverage + shadowAlpha <= 0.002) {
        discard;
    }

    // The rectangle's own corners are (-halfSize) and (+halfSize); the shadow band outside them
    // clamps to the picture's edge, and is hidden under the coverage anyway.
    vec2 uv = clamp((localPos + halfSize) / max(halfSize * 2.0, vec2(1.0)), 0.0, 1.0);
    vec3 color = texture(Sampler0, uv).rgb * (vertexColor * ColorModulator).rgb;

    float alpha = coverage + shadowAlpha;
    fragColor = vec4(color * (coverage / alpha), alpha);
}

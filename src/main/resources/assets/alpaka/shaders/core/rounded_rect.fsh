#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 localPos;
flat in vec2 halfSize;
flat in vec2 shape;

out vec4 fragColor;

// Signed distance from p to the edge of a box of half size b with corners of radius r:
// negative inside, positive outside, in the same units as p (screen pixels here).
float roundedBoxDistance(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + vec2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, vec2(0.0))) - r;
}

void main() {
    float radius = clamp(shape.x, 0.0, min(halfSize.x, halfSize.y));
    float d = roundedBoxDistance(localPos, halfSize, radius);

    // One pixel of falloff across the edge is what makes it anti-aliased rather than stepped.
    float coverage = 1.0 - smoothstep(-0.5, 0.5, d);

    // A border is the band between the outer edge and the same shape `thickness` pixels inside.
    float thickness = shape.y;
    if (thickness > 0.0) {
        coverage *= smoothstep(-0.5, 0.5, d + thickness);
    }

    vec4 color = vertexColor * ColorModulator;
    color.a *= coverage;
    if (color.a <= 0.002) {
        discard;
    }
    fragColor = color;
}

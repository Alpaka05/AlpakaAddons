#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

// The blurred copy of the frame, the same size as the frame itself.
uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 localPos;
flat in vec2 halfSize;
flat in vec2 shape;

out vec4 fragColor;

float roundedBoxDistance(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + vec2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, vec2(0.0))) - r;
}

void main() {
    float coverage;
    vec4 tint = vertexColor * ColorModulator;
    if (halfSize.x <= 0.0) {
        // Free geometry rather than a rectangle: the vertex alpha carries the shape's coverage
        // (including feathered edges), and the tint strength rides in UV2.y as 0..255.
        coverage = tint.a;
        tint.a = shape.y / 255.0;
    } else {
        float radius = clamp(shape.x, 0.0, min(halfSize.x, halfSize.y));
        float d = roundedBoxDistance(localPos, halfSize, radius);
        coverage = 1.0 - smoothstep(-0.5, 0.5, d);
    }
    if (coverage <= 0.002) {
        discard;
    }

    // The copy is frame-sized and drawn in the frame's own coordinates, so this fragment's screen
    // position, in texture space, is exactly where the blurred pixel behind it lives.
    vec2 uv = gl_FragCoord.xy / vec2(textureSize(Sampler0, 0));
    vec3 blurred = texture(Sampler0, uv).rgb;

    // The tint's alpha is how much of it covers the blur; the shape's edge is the only place the
    // panel itself becomes translucent.
    vec3 color = mix(blurred, tint.rgb, clamp(tint.a, 0.0, 1.0));
    fragColor = vec4(color, coverage);
}

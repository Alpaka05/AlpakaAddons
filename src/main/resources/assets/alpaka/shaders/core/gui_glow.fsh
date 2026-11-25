#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec2 localPos;
layout(location = 2) flat in ivec2 shape;

layout(location = 0) out vec4 fragColor;

// Soft shapes of light for the GUI, each from one quad (see GlowRenderState):
//   0 glow    - a disc fading from the middle out, localPos in -1..1
//   1 ring    - a soft ring at the edge of that disc, the parameter its width
//   2 ray     - a spoke, localPos.x 0 at its root to 1 at its tip, localPos.y -1..1 across
//   3 sparkle - a four-pointed star with a bright core, localPos in -1..1
//   4 rainbow - an arc of rainbow bands, the outer 30 % of that disc, red outside and violet inside,
//               drawn from the left over the top as far as the parameter (0 left, 1 right); the
//               vertex color only tints it
vec3 hue(float h) {
    vec3 k = clamp(abs(fract(h + vec3(1.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
    return mix(vec3(1.0), k, 0.85);
}

void main() {
    vec2 p = localPos;
    float param = float(shape.y) / 1000.0;
    float a;
    if (shape.x == 0) {
        float k = max(0.0, 1.0 - length(p));
        a = k * k * (0.55 + 0.45 * k);
    } else if (shape.x == 1) {
        float width = max(param, 0.01);
        float d = abs(length(p) - (1.0 - width)) / width;
        float k = max(0.0, 1.0 - d);
        a = k * k;
    } else if (shape.x == 2) {
        float along = clamp(p.x, 0.0, 1.0);
        float across = 1.0 - smoothstep(0.0, 1.0, abs(p.y));
        float root = smoothstep(0.0, 0.15, along);
        a = across * root * (1.0 - along) * (1.0 - along);
    } else if (shape.x == 4) {
        float width = 0.3;
        float across = (length(p) - (1.0 - width)) / width;
        float band = smoothstep(0.0, 0.15, across) * (1.0 - smoothstep(0.85, 1.0, across));
        float along = atan(-p.y, -p.x) / 3.14159265;
        // Faded out at both feet rather than cut off where the arc meets its own middle line.
        float shown = (1.0 - smoothstep(param - 0.025, param, along)) * smoothstep(0.0, 0.1, along)
                * (1.0 - smoothstep(0.9, 1.0, along));
        vec4 color = vertexColor * ColorModulator;
        color.rgb *= hue(0.78 * (1.0 - clamp(across, 0.0, 1.0)));
        color.a *= band * shown;
        if (color.a <= 0.003) {
            discard;
        }
        fragColor = color;
        return;
    } else {
        vec2 q = abs(p);
        float star = max(0.0, 1.0 - (q.x * 7.0 + q.y)) + max(0.0, 1.0 - (q.y * 7.0 + q.x));
        float core = max(0.0, 1.0 - length(p) * 2.6);
        a = min(1.0, star * 0.85 + core * core);
    }

    vec4 color = vertexColor * ColorModulator;
    color.a *= a;
    if (color.a <= 0.003) {
        discard;
    }
    fragColor = color;
}

#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 localPos;
flat in ivec2 shape;

out vec4 fragColor;

// Soft shapes of light for the GUI, each from one quad (see GlowRenderState):
//   0 glow    - a disc fading from the middle out, localPos in -1..1
//   1 ring    - a soft ring at the edge of that disc, the parameter its width
//   2 ray     - a spoke, localPos.x 0 at its root to 1 at its tip, localPos.y -1..1 across
//   3 sparkle - a four-pointed star with a bright core, localPos in -1..1
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

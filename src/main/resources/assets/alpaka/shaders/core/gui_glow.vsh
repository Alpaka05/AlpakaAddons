#version 330
#extension GL_ARB_separate_shader_objects : require

// Copies of dynamictransforms.glsl and projection.glsl, as in vanilla's core/gui.vsh: GUI
// pipelines are compiled at startup, before resource packs (and so #include) are available.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

// The ENTITY vertex format, its texture slots carrying the shape (see GlowRenderState):
//   UV0 - where this corner lies in the shape's own coordinates
//   UV2 - x: which shape, y: its parameter in thousandths
layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

layout(location = 0) out vec4 vertexColor;
layout(location = 1) out vec2 localPos;
layout(location = 2) flat out ivec2 shape;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    localPos = UV0;
    shape = UV2;
}

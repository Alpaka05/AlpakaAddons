#version 330

// Copies of dynamictransforms.glsl and projection.glsl, as in vanilla's core/gui.vsh: GUI
// pipelines are compiled at startup, before resource packs (and so moj_import) are available.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

// The ENTITY vertex format, its texture slots carrying the shape (see GlowRenderState):
//   UV0 - where this corner lies in the shape's own coordinates
//   UV2 - x: which shape, y: its parameter in thousandths
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec4 vertexColor;
out vec2 localPos;
flat out ivec2 shape;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    localPos = UV0;
    shape = UV2;
}

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

// The ENTITY vertex format, with its texture slots carrying the shape instead of texture data:
//   UV0    - this vertex's position relative to the rectangle's centre, in screen pixels
//   UV1    - the rectangle's full width and height, in screen pixels
//   UV2    - corner radius and border thickness (0 = filled), in screen pixels
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec4 vertexColor;
out vec2 localPos;
flat out vec2 halfSize;
flat out vec2 shape;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexColor = Color;
    localPos = UV0;
    halfSize = vec2(UV1) * 0.5;
    shape = vec2(UV2);
}

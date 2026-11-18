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

// The ENTITY vertex format, with its texture slots carrying the shape instead of texture data:
//   UV0    - this vertex's position relative to the rectangle's centre, in screen pixels
//   UV1    - the rectangle's full width and height, in screen pixels
//   UV2    - corner radius and border thickness (0 = filled), in screen pixels
//   Normal - z is the shape's overall opacity, 0..1; everything that does not fade sends 1.
//            x and y are a blur panel's drop shadow: reach in 64ths of 64 px, and strength
layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

layout(location = 0) out vec4 vertexColor;
layout(location = 1) out vec2 localPos;
layout(location = 2) flat out vec2 halfSize;
layout(location = 3) flat out vec2 shape;
layout(location = 4) flat out float opacity;
layout(location = 5) flat out vec2 shadow;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexColor = Color;
    localPos = UV0;
    halfSize = vec2(UV1) * 0.5;
    shape = vec2(UV2);
    opacity = Normal.z;
    shadow = vec2(Normal.x * 64.0, Normal.y);
}

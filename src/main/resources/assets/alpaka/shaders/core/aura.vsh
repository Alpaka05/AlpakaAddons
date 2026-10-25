#version 330

// Copies of dynamictransforms.glsl and projection.glsl, as in rounded_rect.vsh.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

//   Position - the aura vertex, relative to the camera
//   UV0      - where in the glow the vertex sits; see aura.fsh
//   Color    - the glow's color, and in alpha its strength
in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 texCoord;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord = UV0;
    vertexColor = Color;
}

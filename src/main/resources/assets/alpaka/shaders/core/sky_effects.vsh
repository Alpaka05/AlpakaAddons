#version 330

// Copies of dynamictransforms.glsl and projection.glsl, as in starlight.vsh.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

//   Position - a corner of the box around the camera the sky is drawn on, in world directions
in vec3 Position;

// The box's point under each pixel. The box is flat, so this is exact after interpolation, and
// its direction is the direction in the world the pixel looks in.
out vec3 skyPos;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    // On the far plane (depth 0: the depth buffer is reversed), so the depth test lets the sky
    // through only where nothing has been drawn yet, never over a block. ColorModulator.x says
    // where that plane lies in clip space: 0 where depth runs 0..1 (Vulkan, OpenGL with clip
    // control), -1 where it runs -1..1.
    gl_Position.z = ColorModulator.x * gl_Position.w;
    skyPos = Position;
}

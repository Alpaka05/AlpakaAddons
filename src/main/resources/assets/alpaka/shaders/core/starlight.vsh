#version 330

// Copies of dynamictransforms.glsl, projection.glsl and globals.glsl, as in rounded_rect.vsh.
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

//   Position - the item's vertex, as the item itself was drawn
//   UV0      - the camera's yaw and pitch, as Camera.setRotation hands them to rotationYXZ
//   Color    - the starlight's color, and in alpha its strength
in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec3 viewPos;
// The view-to-world rotation by its columns, not as one mat3: on the Vulkan backend every output
// gets one location, and a mat3 takes three, so the next output would land on one of them.
flat out vec3 viewToWorld0;
flat out vec3 viewToWorld1;
flat out vec3 viewToWorld2;
out vec4 vertexColor;

void main() {
    // Exactly vanilla's expression, as in entity.vsh and glint.vsh: the pass only draws where its
    // depth equals the item's, and ProjMat * (ModelViewMat * v) rounds differently from
    // (ProjMat * ModelViewMat) * v - enough to make the test fail in patches as the item moves.
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    viewPos = (ModelViewMat * vec4(Position, 1.0)).xyz;
    float cy = cos(UV0.x), sy = sin(UV0.x);
    float cx = cos(UV0.y), sx = sin(UV0.y);
    // rotationYXZ(yaw, pitch, 0) = rotY(yaw) * rotX(pitch); GLSL matrices are given by columns.
    mat3 rotY = mat3(cy, 0.0, -sy, 0.0, 1.0, 0.0, sy, 0.0, cy);
    mat3 rotX = mat3(1.0, 0.0, 0.0, 0.0, cx, sx, 0.0, -sx, cx);
    mat3 viewToWorld = rotY * rotX;
    viewToWorld0 = viewToWorld[0];
    viewToWorld1 = viewToWorld[1];
    viewToWorld2 = viewToWorld[2];
    vertexColor = Color;
}

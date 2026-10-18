#version 330

// One step up the dual Kawase blur: renders a target twice the input's size from eight samples
// in a ring around the pixel, the four diagonal ones weighted double.

uniform sampler2D InSampler;

layout(std140) uniform BlurConfig {
    float Offset;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    // Half a texel of the target, which is twice the input's size.
    vec2 h = Offset * 0.25 / vec2(textureSize(InSampler, 0));
    vec3 sum = texture(InSampler, texCoord + vec2(-2.0 * h.x, 0.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(-h.x, h.y)).rgb * 2.0;
    sum += texture(InSampler, texCoord + vec2(0.0, 2.0 * h.y)).rgb;
    sum += texture(InSampler, texCoord + vec2(h.x, h.y)).rgb * 2.0;
    sum += texture(InSampler, texCoord + vec2(2.0 * h.x, 0.0)).rgb;
    sum += texture(InSampler, texCoord + vec2(h.x, -h.y)).rgb * 2.0;
    sum += texture(InSampler, texCoord + vec2(0.0, -2.0 * h.y)).rgb;
    sum += texture(InSampler, texCoord + vec2(-h.x, -h.y)).rgb * 2.0;
    fragColor = vec4(sum / 12.0, 1.0);
}

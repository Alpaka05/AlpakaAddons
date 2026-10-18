#version 330

// One d down the dual Kawase blur: renders a target half the input's size, each pixel the
// input's centre sample weighted 4 against four diagonal samples Offset input texels away. The
// bilinear filter makes each of the five reads an average of four texels.

uniform sampler2D InSampler;

layout(std140) uniform BlurConfig {
    float Offset;
};

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 d = Offset / vec2(textureSize(InSampler, 0));
    vec3 sum = texture(InSampler, texCoord).rgb * 4.0;
    sum += texture(InSampler, texCoord - d).rgb;
    sum += texture(InSampler, texCoord + d).rgb;
    sum += texture(InSampler, texCoord + vec2(d.x, -d.y)).rgb;
    sum += texture(InSampler, texCoord - vec2(d.x, -d.y)).rgb;
    fragColor = vec4(sum / 8.0, 1.0);
}

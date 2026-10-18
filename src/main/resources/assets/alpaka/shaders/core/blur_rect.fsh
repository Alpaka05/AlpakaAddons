#version 330

#moj_import <minecraft:globals.glsl>

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

// The blurred frame, at half the frame's size (or the plain copy at full size, at strength 0).
uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 localPos;
flat in vec2 halfSize;
flat in vec2 shape;
flat in float opacity;
// Reach in screen pixels and strength of the drop shadow; a reach of 0 means none.
flat in vec2 shadow;

out vec4 fragColor;

float roundedBoxDistance(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + vec2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, vec2(0.0))) - r;
}

// Cheap per-pixel noise, used to dither the smooth gradients so they do not band in 8 bits.
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    float coverage;
    float shadowAlpha = 0.0;
    float d = 0.0;
    vec4 tint = vertexColor * ColorModulator;
    bool rect = halfSize.x > 0.0;
    if (!rect) {
        // Free geometry rather than a rectangle: the vertex alpha carries the shape's coverage
        // (including feathered edges), and the tint strength rides in UV2.y as 0..255.
        coverage = tint.a;
        tint.a = shape.y / 255.0;
    } else {
        float radius = clamp(shape.x, 0.0, min(halfSize.x, halfSize.y));
        d = roundedBoxDistance(localPos, halfSize, radius);
        coverage = 1.0 - smoothstep(-0.5, 0.5, d);

        if (shadow.x > 0.5) {
            // A soft shadow under the panel: the same shape moved down a little, fading out over
            // what is left of the quad below it, so it reaches zero before the quad's edge.
            float offset = shadow.x * 0.3;
            float ds = roundedBoxDistance(localPos - vec2(0.0, offset), halfSize, radius);
            float t = 1.0 - clamp(ds / (shadow.x - offset), 0.0, 1.0);
            t = t * t * (3.0 - 2.0 * t);
            shadowAlpha = shadow.y * t * t;
        }
    }
    // A panel fading in or out lets the unblurred world through as a whole, shadow included.
    coverage *= opacity;
    shadowAlpha *= opacity * (1.0 - coverage);
    if (coverage + shadowAlpha <= 0.002) {
        discard;
    }

    // The blurred frame covers the whole screen, so this fragment's screen position, as a share of
    // the screen, is where the blurred pixel behind it lives - whatever size the texture is.
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    vec3 blurred = texture(Sampler0, uv).rgb;

    // Frosted glass rather than a dark sheet: what shows through keeps a little more of its color
    // and light than the world around it, so the panel reads as glass the world glows through.
    float luma = dot(blurred, vec3(0.2126, 0.7152, 0.0722));
    blurred = clamp(mix(vec3(luma), blurred, 1.25), 0.0, 1.0);
    blurred = blurred * 0.94 + 0.04;

    // The tint's alpha is how much of it covers the blur; the shape's edge is the only place the
    // panel itself becomes translucent.
    vec3 color = mix(blurred, tint.rgb, clamp(tint.a, 0.0, 1.0));

    if (rect) {
        // A faint light rim along the inside of the edge, brightest along the top, as on a pane
        // of glass catching the light.
        float rim = 1.0 - smoothstep(0.0, 1.5, -d);
        float top = clamp(0.5 - localPos.y / max(halfSize.y, 1.0) * 0.5, 0.0, 1.0);
        color += vec3(rim * mix(0.04, 0.12, top));
    }

    // Half a step of noise either way hides the 8-bit steps of the blurred gradients.
    color += (hash(gl_FragCoord.xy) - 0.5) / 255.0;

    // Panel over shadow: the shadow is black, so only the alpha has to be composed.
    float alpha = coverage + shadowAlpha;
    fragColor = vec4(color * (coverage / alpha), alpha);
}

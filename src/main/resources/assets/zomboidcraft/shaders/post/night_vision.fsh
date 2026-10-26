#version 330
#extension GL_ARB_separate_shader_objects : require

// The view through night vision goggles: brightness to one channel, halos round lights, a soft response curve,
// grain, the phosphor's colour and the tube's round edge. The same steps and numbers as the preview page.

uniform sampler2D InSampler;
uniform sampler2D ParamsSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    float GlintAlpha;
    vec3 CameraOffset;
    float GameTime;
    vec2 ScreenSize;
    int MenuBlurRadius;
    int UseRgss;
};

// One generation's look, from post_effect/nvg_*.json.
layout(std140) uniform NightVisionConfig {
    vec4 Phosphor;  // the tube's bright colour; w: how hard the response curve bends
    vec4 Dark;      // its black; w: grain
    vec4 Look;      // x: halos, y: gain, z: the infrared lamp's boost in the middle, w: warp toward the edge
    vec4 Tube;      // x: radius against the set tube size, y: 1 for a monocular, z: the naked eye's brightness beside it
};

layout(location = 0) out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float luma(vec3 c) {
    return dot(c, vec3(0.30, 0.59, 0.11));
}

void main() {
    // written by the game (NightVisionClient): r the tube's size, g low while the battery is dying, b brightness;
    // the missing-texture pink means it isn't there, so use the defaults
    vec4 p = texelFetch(ParamsSampler, ivec2(0, 0), 0);
    bool missing = p.r > 0.9 && p.g < 0.1 && p.b > 0.9;
    float size = missing ? 0.92 : p.r * 1.5;
    bool dying = !missing && p.g < 0.5;
    float brightness = missing ? 0.85 : p.b * 2.0;
    float tube = size * 0.5 * Tube.x;

    float aspect = OutSize.x / OutSize.y;
    vec2 uv = texCoord;
    vec2 d = vec2((uv.x - 0.5) * aspect, uv.y - 0.5);
    float r = length(d);
    vec2 wuv = uv - d / vec2(aspect, 1.0) * Look.w * pow(r / tube, 2.0);
    float l = luma(texture(InSampler, wuv).rgb) * Look.y * brightness * (1.0 + Look.z * (1.0 - smoothstep(0.0, tube, r)));

    vec2 px = 1.0 / OutSize;
    float h = 0.0;
    for (int i = 0; i < 8; i++) {
        float a = float(i) * 0.785;
        h += max(luma(texture(InSampler, wuv + vec2(cos(a), sin(a)) * px * 10.0).rgb) - 0.75, 0.0);
    }
    l += h * Look.x * 0.3;
    l = (1.0 - exp(-Phosphor.w * l)) / (1.0 - exp(-Phosphor.w));

    // seconds from the day's ticks, for the grain's shimmer and a dying battery's flicker
    float t = GameTime * 1200.0;
    l += (hash(floor(gl_FragCoord.xy / 2.0) + mod(floor(t * 24.0), 997.0) * 17.0) - 0.5) * Dark.w;
    float flicker = 1.0;
    if (dying) {
        float k = sin(t * 17.0) + sin(t * 5.3) * 0.8 + sin(t * 41.0) * 0.4;
        flicker = k < -1.0 ? 0.15 : 0.62 + 0.12 * sin(t * 23.0);
    }
    l = clamp(l * flicker, 0.0, 1.0);

    vec3 c = mix(Dark.rgb, Phosphor.rgb, l) * (1.0 - smoothstep(tube * 0.55, tube, r) * 0.45);
    float edge = 1.0 - smoothstep(tube - 0.03, tube, r);
    vec3 outside = Tube.y > 0.5 ? texture(InSampler, uv).rgb * Tube.z : vec3(0.0);
    fragColor = vec4(mix(outside, c, edge), 1.0);
}

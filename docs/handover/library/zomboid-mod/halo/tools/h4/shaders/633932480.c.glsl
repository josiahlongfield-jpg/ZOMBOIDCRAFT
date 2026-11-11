#version 450

layout(binding = 0, std140) uniform cb5_struct
{
    vec4 _m0[5];
} cb13_0;

layout(binding = 1, std140) uniform cb6_struct
{
    vec4 _m0[6];
} cb0_0;

layout(binding = 2, std140) uniform cb8_struct
{
    vec4 _m0[8];
} cb11_0;

layout(binding = 3, std140) uniform cb9_struct
{
    vec4 _m0[9];
} cb1_0;

layout(binding = 4, std140) uniform cb1_struct
{
    vec4 _m0[1];
} cb2_0;

uniform sampler2D SPIRV_Cross_Combinedt7s7;
uniform sampler2D SPIRV_Cross_Combinedt6s6;
uniform sampler2D SPIRV_Cross_Combinedt2s2;
uniform sampler2D SPIRV_Cross_Combinedt0s0;
uniform sampler2D SPIRV_Cross_Combinedt3s3;
uniform sampler2D SPIRV_Cross_Combinedt5s5;
uniform sampler2D SPIRV_Cross_Combinedt4s4;

layout(location = 1) in vec2 v1;
layout(location = 0) out vec4 o0;

void main()
{
    vec4 r0 = cb13_0._m0[3u].yyzz * cb2_0._m0[0u].zwzw;
    r0 *= cb0_0._m0[4u].zwzw;
    vec2 _84 = cb0_0._m0[4u].zw * cb2_0._m0[0u].zw;
    vec4 r1;
    r1 = vec4(_84.x, _84.y, r1.z, r1.w);
    vec2 _96 = fma(-cb2_0._m0[0u].zw, cb0_0._m0[4u].xy, v1);
    r1 = vec4(r1.x, r1.y, _96.x, _96.y);
    vec2 _103 = r1.zw / r1.xy;
    r1 = vec4(_103.x, _103.y, r1.z, r1.w);
    vec2 _115 = fma(r1.xy, cb11_0._m0[7u].xy, cb11_0._m0[7u].zw);
    r1 = vec4(r1.x, r1.y, _115.x, _115.y);
    vec2 _125 = texture(SPIRV_Cross_Combinedt7s7, r1.zwzz.xy).xy;
    r1 = vec4(r1.x, r1.y, _125.x, _125.y);
    vec2 _132 = r0.zw * r1.zw;
    r0 = vec4(r0.x, r0.y, _132.x, _132.y);
    vec2 _143 = fma(r1.xy, cb11_0._m0[6u].xy, cb11_0._m0[6u].zw);
    r1 = vec4(r1.x, r1.y, _143.x, _143.y);
    vec2 _152 = texture(SPIRV_Cross_Combinedt6s6, r1.zwzz.xy).xy;
    r1 = vec4(r1.x, r1.y, _152.x, _152.y);
    vec2 _161 = fma(r1.zw, r0.xy, r0.zw);
    r0 = vec4(_161.x, _161.y, r0.z, r0.w);
    r0.z = cb13_0._m0[4u].x * 0.0039215688593685626983642578125;
    vec4 r2 = texture(SPIRV_Cross_Combinedt2s2, v1.xyxx.xy);
    r0.w = r2.w + (-cb13_0._m0[3u].w);
    vec4 _191 = (-r2) + vec4(1.0);
    vec4 _500 = mix(mix(max(_191, vec4(0.0)), vec4(0.0), isnan(_191)), _191, isnan(vec4(0.0)));
    r2 = mix(mix(min(_500, vec4(1.0)), vec4(1.0), isnan(_500)), _500, isnan(vec4(1.0)));
    float _199 = r0.w / r0.z;
    float _511 = isnan(0.0) ? _199 : (isnan(_199) ? 0.0 : max(_199, 0.0));
    r0.z = isnan(1.0) ? _511 : (isnan(_511) ? 1.0 : min(_511, 1.0));
    vec2 _207 = fma(r0.xy, r0.zz, v1);
    r0 = vec4(_207.x, _207.y, r0.z, r0.w);
    vec2 _215 = mix(mix(max(r0.xy, cb0_0._m0[5u].xz), cb0_0._m0[5u].xz, isnan(r0.xy)), r0.xy, isnan(cb0_0._m0[5u].xz));
    r0 = vec4(_215.x, _215.y, r0.z, r0.w);
    vec2 _223 = mix(mix(min(r0.xy, cb0_0._m0[5u].yw), cb0_0._m0[5u].yw, isnan(r0.xy)), r0.xy, isnan(cb0_0._m0[5u].yw));
    r0 = vec4(_223.x, _223.y, r0.z, r0.w);
    r0 = texture(SPIRV_Cross_Combinedt0s0, r0.xyxx.xy);
    vec2 _240 = fma(r1.xy, cb11_0._m0[3u].xy, cb11_0._m0[3u].zw);
    r1 = vec4(r1.x, r1.y, _240.x, _240.y);
    r1.z = texture(SPIRV_Cross_Combinedt3s3, r1.zwzz.xy).x;
    r1.z = fma(cb13_0._m0[1u].x, r1.z, -0.5);
    r1.z = fma(cb13_0._m0[1u].w, r1.z, 0.5);
    r1.z = dot(r1.zz, r0.ww);
    vec2 _281 = fma(r1.xy, cb11_0._m0[4u].xy, cb11_0._m0[4u].zw);
    vec4 r3;
    r3 = vec4(_281.x, _281.y, r3.z, r3.w);
    vec2 _292 = fma(r1.xy, cb11_0._m0[5u].xy, cb11_0._m0[5u].zw);
    r1 = vec4(_292.x, _292.y, r1.z, r1.w);
    r1.x = texture(SPIRV_Cross_Combinedt5s5, r1.xyxx.xy).x;
    r1.x += (-1.0);
    r1.x = fma(cb13_0._m0[3u].x, r1.x, 1.0);
    r1.y = texture(SPIRV_Cross_Combinedt4s4, r3.xyxx.xy).x;
    r1.w = r1.y + (-0.5);
    r1.y = fma(r1.y, r1.w, 0.5);
    r3 = fma(cb13_0._m0[2u].xxyz, r1.yyyy, vec4(-0.5, -1.0, -1.0, -1.0));
    r3 = fma(cb13_0._m0[2u].wwww, r3, vec4(0.5, 1.0, 1.0, 1.0));
    r0.w = dot(r3.xx, r1.zz);
    vec4 _354 = (-r0) + vec4(1.0);
    vec4 _532 = mix(mix(max(_354, vec4(0.0)), vec4(0.0), isnan(_354)), _354, isnan(vec4(0.0)));
    vec4 r4 = mix(mix(min(_532, vec4(1.0)), vec4(1.0), isnan(_532)), _532, isnan(vec4(1.0)));
    r2 = fma(-r4, r2, vec4(1.0));
    r2 = mix(mix(max(r0, r2), r2, isnan(r0)), r0, isnan(r2));
    o0.w = r0.w;
    r0.x = dot(vec3(0.2125000059604644775390625, 0.7153999805450439453125, 0.07209999859333038330078125), r2.xyz);
    r0.x = inversesqrt(r0.x);
    r0.x = 1.0 / r0.x;
    vec3 _389 = r0.xxx + cb13_0._m0[0u].xyz;
    r0 = vec4(r0.x, _389.x, _389.y, _389.z);
    vec3 _396 = r0.yzw * r0.yzw;
    r0 = vec4(r0.x, _396.x, _396.y, _396.z);
    vec3 _406 = fma(r0.xxx, r0.yzw, -r2.xyz);
    r0 = vec4(_406.x, _406.y, _406.z, r0.w);
    vec3 _416 = fma(cb13_0._m0[0u].www, r0.xyz, r2.xyz);
    r0 = vec4(_416.x, _416.y, _416.z, r0.w);
    vec3 _423 = r1.xxx * r0.xyz;
    r0 = vec4(_423.x, _423.y, _423.z, r0.w);
    vec3 _430 = r3.yzw * r0.xyz;
    r0 = vec4(_430.x, _430.y, _430.z, r0.w);
    vec3 _441 = fma(r0.xyz, cb1_0._m0[4u].xyz, cb1_0._m0[5u].xyz);
    r1 = vec4(_441.x, _441.y, _441.z, r1.w);
    vec3 _448 = r0.xyz * r1.xyz;
    r1 = vec4(_448.x, _448.y, _448.z, r1.w);
    vec3 _459 = fma(r0.xyz, cb1_0._m0[6u].xyz, cb1_0._m0[7u].xyz);
    r2 = vec4(_459.x, _459.y, _459.z, r2.w);
    vec3 _469 = fma(r0.xyz, r2.xyz, cb1_0._m0[8u].xyz);
    r0 = vec4(_469.x, _469.y, _469.z, r0.w);
    vec3 _476 = r1.xyz / r0.xyz;
    r0 = vec4(_476.x, _476.y, _476.z, r0.w);
    vec3 _483 = r2.www * r0.xyz;
    o0 = vec4(_483.x, _483.y, _483.z, o0.w);
}


#version 450

layout(binding = 0, std140) uniform cb13_struct
{
    vec4 _m0[13];
} cb13_0;

layout(binding = 1, std140) uniform cb8_struct
{
    vec4 _m0[8];
} cb0_0;

layout(binding = 2, std140) uniform cb9_struct
{
    vec4 _m0[9];
} cb1_0;

layout(binding = 3, std140) uniform cb2_0
{
    vec4 _m0[8];
} cb2_0_1;

layout(binding = 4, std140) uniform cb5_struct
{
    vec4 _m0[5];
} cb11_0;

uniform sampler2D SPIRV_Cross_Combinedt2s2;
uniform sampler2D SPIRV_Cross_Combinedt5s5;
uniform sampler2D SPIRV_Cross_Combinedt0s0;
uniform sampler2D SPIRV_Cross_Combinedt3s3;
uniform sampler2D SPIRV_Cross_Combinedt4s4;
uniform sampler2D SPIRV_Cross_Combinedt1s1;

layout(location = 1) in vec4 v1;
layout(location = 2) in vec3 v2;
layout(location = 3) in vec3 v3;
layout(location = 4) in vec3 v4;
layout(location = 5) in vec3 v5;
layout(location = 0) out vec4 o0;

void main()
{
    vec2 _74 = texture(SPIRV_Cross_Combinedt2s2, v1.xyxx.xy).xy;
    vec4 r0;
    r0 = vec4(_74.x, _74.y, r0.z, r0.w);
    vec3 _80 = r0.yyy * v3;
    vec4 r1;
    r1 = vec4(_80.x, _80.y, _80.z, r1.w);
    vec3 _89 = fma(r0.xxx, v4, r1.xyz);
    r1 = vec4(_89.x, _89.y, _89.z, r1.w);
    r0.x = dot(r0.xy, -r0.xy);
    float _104 = r0.x + 1.0;
    r0.x = saturate(_104);
    r0.x = sqrt(r0.x);
    vec3 _117 = fma(r0.xxx, v2, r1.xyz);
    r0 = vec4(_117.x, _117.y, _117.z, r0.w);
    r0.w = dot(r0.xyz, r0.xyz);
    r0.w = inversesqrt(r0.w);
    vec3 _135 = r0.www * r0.xyz;
    r0 = vec4(_135.x, _135.y, _135.z, r0.w);
    float _145 = dot(cb2_0_1._m0[4u].xyz, r0.xyz);
    r0.w = saturate(_145);
    r1.x = r0.w * cb13_0._m0[9u].x;
    float _162 = fma(-r0.w, cb13_0._m0[9u].x, 1.0);
    r0.w = saturate(_162);
    r1.y = texture(SPIRV_Cross_Combinedt5s5, v1.xyxx.xy).x;
    r1.y *= cb13_0._m0[12u].w;
    r0.w = fma(r0.w, r1.y, r1.x);
    vec4 r2;
    r2.x = r0.w * cb11_0._m0[0u].x;
    r2 = vec4(r2.x, vec2(0.0).x, r2.z, vec2(0.0).y);
    vec2 _205 = r2.xy + cb11_0._m0[0u].zw;
    r1 = vec4(_205.x, r1.y, _205.y, r1.w);
    vec3 _214 = textureLod(SPIRV_Cross_Combinedt0s0, r1.xzxx.xy, 0.0).xyz;
    r1 = vec4(_214.x, r1.y, _214.y, _214.z);
    vec3 _223 = fma(-r1.xzw, r1.xzw, vec3(1.0));
    r1 = vec4(_223.x, r1.y, _223.y, _223.z);
    vec3 _229 = (-r1.xzw) + vec3(1.0);
    r1 = vec4(_229.x, r1.y, _229.y, _229.z);
    vec3 _235 = (-r1.xzw) + vec3(1.0);
    r1 = vec4(_235.x, r1.y, _235.y, _235.z);
    float _243 = dot(cb2_0_1._m0[5u].xyz, r0.xyz);
    r0.w = saturate(_243);
    r2.x = r0.w * cb13_0._m0[9u].y;
    float _259 = fma(-r0.w, cb13_0._m0[9u].y, 1.0);
    r0.w = saturate(_259);
    r0.w = fma(r0.w, r1.y, r2.x);
    r2.z = r0.w * cb11_0._m0[0u].x;
    vec2 _283 = r2.zw + cb11_0._m0[0u].zw;
    r2 = vec4(_283.x, _283.y, r2.z, r2.w);
    vec3 _292 = textureLod(SPIRV_Cross_Combinedt0s0, r2.xyxx.xy, 0.0).xyz;
    r2 = vec4(_292.x, _292.y, _292.z, r2.w);
    vec3 _300 = fma(-r2.xyz, r2.xyz, vec3(1.0));
    r2 = vec4(_300.x, _300.y, _300.z, r2.w);
    vec3 _308 = fma(-r1.xzw, r2.xyz, vec3(1.0));
    r1 = vec4(_308.x, r1.y, _308.y, _308.z);
    vec3 _314 = (-r1.xzw) + vec3(1.0);
    r1 = vec4(_314.x, r1.y, _314.y, _314.z);
    float _323 = dot(cb2_0_1._m0[6u].xyz, r0.xyz);
    r0.w = saturate(_323);
    r2.x = r0.w * cb13_0._m0[9u].z;
    float _339 = fma(-r0.w, cb13_0._m0[9u].z, 1.0);
    r0.w = saturate(_339);
    r0.w = fma(r0.w, r1.y, r2.x);
    r2.x = r0.w * cb11_0._m0[0u].x;
    r2 = vec4(r2.x, vec2(0.0, 0.5).x, r2.z, vec2(0.0, 0.5).y);
    vec2 _366 = r2.xy + cb11_0._m0[0u].zw;
    r2 = vec4(_366.x, _366.y, r2.z, r2.w);
    vec3 _375 = textureLod(SPIRV_Cross_Combinedt0s0, r2.xyxx.xy, 0.0).xyz;
    vec4 r3;
    r3 = vec4(_375.x, _375.y, _375.z, r3.w);
    vec3 _383 = fma(-r3.xyz, r3.xyz, vec3(1.0));
    r3 = vec4(_383.x, _383.y, _383.z, r3.w);
    vec3 _391 = fma(-r1.xzw, r3.xyz, vec3(1.0));
    r1 = vec4(_391.x, _391.y, _391.z, r1.w);
    vec3 _397 = (-r1.xyz) + vec3(1.0);
    r3 = vec4(_397.x, _397.y, _397.z, r3.w);
    r0.w = dot(v5, v5);
    r0.w = sqrt(r0.w);
    vec3 _411 = v5 / r0.www;
    vec4 r4;
    r4 = vec4(_411.x, _411.y, _411.z, r4.w);
    vec3 _420 = (-r4.xyz) + cb2_0_1._m0[5u].xyz;
    vec4 r5;
    r5 = vec4(_420.x, _420.y, _420.z, r5.w);
    r0.w = dot(r5.xyz, r5.xyz);
    r0.w = inversesqrt(r0.w);
    vec3 _437 = r0.www * r5.xyz;
    r5 = vec4(_437.x, _437.y, _437.z, r5.w);
    float _444 = dot(r5.xyz, r0.xyz);
    r0.w = saturate(_444);
    r0.w = log2(r0.w);
    r0.w = fma(r0.w, cb13_0._m0[5u].w, -1.6514961719512939453125);
    r0.w = exp2(r0.w);
    r5 = texture(SPIRV_Cross_Combinedt3s3, v1.xyxx.xy);
    r0.w *= r5.w;
    r0.w *= cb13_0._m0[9u].y;
    vec3 _488 = (-r4.xyz) + cb2_0_1._m0[4u].xyz;
    vec4 r6;
    r6 = vec4(_488.x, _488.y, _488.z, r6.w);
    r1.w = dot(r6.xyz, r6.xyz);
    r1.w = inversesqrt(r1.w);
    vec3 _505 = r1.www * r6.xyz;
    r6 = vec4(_505.x, _505.y, _505.z, r6.w);
    float _512 = dot(r6.xyz, r0.xyz);
    r1.w = saturate(_512);
    r1.w = log2(r1.w);
    r1.w = fma(r1.w, cb13_0._m0[5u].w, -1.6514961719512939453125);
    r1.w = exp2(r1.w);
    r1.w *= r5.w;
    r0.w = fma(r1.w, cb13_0._m0[9u].x, r0.w);
    vec3 _551 = (-r4.xyz) + cb2_0_1._m0[6u].xyz;
    r6 = vec4(_551.x, _551.y, _551.z, r6.w);
    float _559 = dot(-r4.xyz, r0.xyz);
    r1.w = saturate(_559);
    r1.w = log2(r1.w);
    r1.w *= cb13_0._m0[6u].x;
    r1.w = exp2(r1.w);
    r1.w = (-r1.w) + 1.0;
    r1.w += (-cb13_0._m0[6u].y);
    r2.x = dot(r6.xyz, r6.xyz);
    r2.x = inversesqrt(r2.x);
    vec3 _604 = r2.xxx * r6.xyz;
    r4 = vec4(_604.x, _604.y, _604.z, r4.w);
    float _611 = dot(r4.xyz, r0.xyz);
    r0.x = saturate(_611);
    r0.x = log2(r0.x);
    r0.x = fma(r0.x, cb13_0._m0[5u].w, -1.6514961719512939453125);
    r0.x = exp2(r0.x);
    r0.x *= r5.w;
    r2.z = fma(r0.x, cb13_0._m0[9u].z, r0.w);
    vec3 _650 = texture(SPIRV_Cross_Combinedt0s0, r2.zwzz.xy).xyz;
    r0 = vec4(_650.x, _650.y, _650.z, r0.w);
    vec3 _656 = (-r0.xyz) + vec3(1.0);
    r0 = vec4(_656.x, _656.y, _656.z, r0.w);
    vec3 _664 = fma(-r3.xyz, r0.xyz, vec3(1.0));
    r0 = vec4(_664.x, _664.y, _664.z, r0.w);
    vec3 _671 = r0.xyz * r2.zzz;
    r0 = vec4(_671.x, _671.y, _671.z, r0.w);
    vec3 _679 = r0.xyz * cb13_0._m0[5u].yyy;
    r0 = vec4(_679.x, _679.y, _679.z, r0.w);
    r0.w = (-cb13_0._m0[6u].y) + cb13_0._m0[6u].z;
    float _695 = r1.w / r0.w;
    r0.w = saturate(_695);
    r0.w *= cb13_0._m0[6u].w;
    float _709 = r5.y * r0.w;
    r0.w = saturate(_709);
    float _716 = dot(r5.zywx, cb13_0._m0[1u]);
    r1.w = saturate(_716);
    r0.w += r1.w;
    r1.w = (-r5.y) + 1.0;
    r0.w = fma(r0.x, r1.w, r0.w);
    r2.x = r0.w + (-cb2_0_1._m0[7u].x);
    r0.w = saturate(r0.w);
    r3.w = r0.w * cb13_0._m0[0u].w;
    r0.w = uintBitsToFloat((r2.x < 0.0) ? 4294967295u : 0u);
    if (floatBitsToUint(r0.w) != 0u)
    {
        discard;
    }
    vec3 _778 = (-r1.xyz) + cb13_0._m0[3u].xyz;
    r2 = vec4(_778.x, _778.y, _778.z, r2.w);
    vec3 _785 = r1.xyz * r5.www;
    r4 = vec4(_785.x, _785.y, _785.z, r4.w);
    vec3 _794 = fma(r4.xyz, r2.xyz, r1.xyz);
    r1 = vec4(_794.x, _794.y, _794.z, r1.w);
    vec3 _802 = cb13_0._m0[3u].xyz + vec3(-1.0);
    r2 = vec4(_802.x, _802.y, _802.z, r2.w);
    vec3 _809 = fma(r5.www, r2.xyz, vec3(1.0));
    r2 = vec4(_809.x, _809.y, _809.z, r2.w);
    vec3 _816 = r1.xyz * r2.xyz;
    r1 = vec4(_816.x, _816.y, _816.z, r1.w);
    vec3 _822 = cb13_0._m0[2u].xyz + vec3(-1.0);
    r2 = vec4(_822.x, _822.y, _822.z, r2.w);
    vec3 _829 = fma(r5.yyy, r2.xyz, vec3(1.0));
    r2 = vec4(_829.x, _829.y, _829.z, r2.w);
    r0.w = r5.y * cb13_0._m0[7u].w;
    r0.w = fma(r0.w, -0.5, cb13_0._m0[7u].w);
    vec3 _851 = r1.xyz * r2.xyz;
    r1 = vec4(_851.x, _851.y, _851.z, r1.w);
    vec2 _862 = fma(v1.zw, cb11_0._m0[4u].xy, cb11_0._m0[4u].zw);
    r2 = vec4(_862.x, _862.y, r2.z, r2.w);
    vec3 _871 = texture(SPIRV_Cross_Combinedt4s4, r2.xyxx.xy).xyw;
    r2 = vec4(_871.x, _871.y, _871.z, r2.w);
    r2.x += (-0.980000019073486328125);
    r2.y += r2.z;
    r2.y += (-cb13_0._m0[8u].x);
    r2.y = fract(r2.y);
    r2.y *= r2.y;
    r2.y *= (-cb13_0._m0[8u].y);
    r2.y = exp2(r2.y);
    r2.y = isnan(1.0) ? r2.y : (isnan(r2.y) ? 1.0 : min(r2.y, 1.0));
    float _922 = r2.x * 255.0000152587890625;
    r2.x = saturate(_922);
    r0.w *= r2.x;
    r0.w = r2.y * r0.w;
    vec3 _942 = r0.www * cb13_0._m0[7u].xyz;
    r2 = vec4(_942.x, _942.y, _942.z, r2.w);
    vec3 _951 = texture(SPIRV_Cross_Combinedt1s1, v1.xyxx.xy).xyz;
    r4 = vec4(_951.x, _951.y, _951.z, r4.w);
    vec3 _960 = fma(r1.xyz, r4.xyz, r2.xyz);
    r1 = vec4(_960.x, _960.y, _960.z, r1.w);
    vec3 _969 = fma(r0.xyz, r1.www, r1.xyz);
    r0 = vec4(_969.x, _969.y, _969.z, r0.w);
    vec3 _976 = r3.www * r0.xyz;
    r3 = vec4(_976.x, _976.y, _976.z, r3.w);
    r0 = r3 * vec4(0.3333333432674407958984375);
    r1.x = uintBitsToFloat((0.996078431606292724609375 >= r3.w) ? 4294967295u : 0u);
    r0 = mix(r3, r0, notEqual(floatBitsToUint(r1.xxxx), uvec4(0u)));
    r1 = fma(r0, cb0_0._m0[7u].wwww, -r0);
    r0 = fma(cb1_0._m0[8u].zzzz, r1, r0);
    o0 = mix(mix(max(r0, vec4(0.0)), vec4(0.0), isnan(r0)), r0, isnan(vec4(0.0)));
}


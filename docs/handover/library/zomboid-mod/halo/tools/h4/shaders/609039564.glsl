#version 450

layout(binding = 0, std140) uniform cb9_struct
{
    vec4 _m0[9];
} cb13_0;

layout(binding = 1, std140) uniform cb8_struct
{
    vec4 _m0[8];
} cb0_0;

layout(binding = 2, std140) uniform cb1_0
{
    vec4 _m0[9];
} cb1_0_1;

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
layout(location = 6) in vec4 v6;
layout(location = 0) out vec4 o0;

void main()
{
    vec4 r0;
    r0.x = (-v6.x) + 1.0;
    vec2 _83 = texture(SPIRV_Cross_Combinedt2s2, v1.xyxx.xy).xy;
    r0 = vec4(r0.x, _83.x, _83.y, r0.w);
    vec2 _92 = texture(SPIRV_Cross_Combinedt5s5, v1.xyxx.xy).xy;
    vec4 r1;
    r1 = vec4(_92.x, _92.y, r1.z, r1.w);
    vec2 _102 = fma(r0.xx, r1.xy, r0.yz);
    r0 = vec4(_102.x, _102.y, r0.z, r0.w);
    vec3 _108 = r0.yyy * v3;
    r1 = vec4(_108.x, _108.y, _108.z, r1.w);
    vec3 _116 = fma(r0.xxx, v4, r1.xyz);
    r1 = vec4(_116.x, _116.y, _116.z, r1.w);
    r0.x = dot(r0.xy, -r0.xy);
    float _128 = r0.x + 1.0;
    float _885 = isnan(0.0) ? _128 : (isnan(_128) ? 0.0 : max(_128, 0.0));
    r0.x = isnan(1.0) ? _885 : (isnan(_885) ? 1.0 : min(_885, 1.0));
    r0.x = sqrt(r0.x);
    vec3 _141 = fma(r0.xxx, v2, r1.xyz);
    r0 = vec4(_141.x, _141.y, _141.z, r0.w);
    r0.w = dot(r0.xyz, r0.xyz);
    r0.w = inversesqrt(r0.w);
    vec3 _159 = r0.www * r0.xyz;
    r0 = vec4(_159.x, _159.y, _159.z, r0.w);
    float _169 = dot(cb2_0_1._m0[4u].xyz, r0.xyz);
    float _896 = isnan(0.0) ? _169 : (isnan(_169) ? 0.0 : max(_169, 0.0));
    r0.w = isnan(1.0) ? _896 : (isnan(_896) ? 1.0 : min(_896, 1.0));
    r0.w *= cb13_0._m0[8u].x;
    r1.x = r0.w * cb11_0._m0[0u].x;
    r1 = vec4(r1.x, vec2(0.0).x, r1.z, vec2(0.0).y);
    vec2 _195 = r1.xy + cb11_0._m0[0u].zw;
    r1 = vec4(_195.x, _195.y, r1.z, r1.w);
    vec3 _204 = textureLod(SPIRV_Cross_Combinedt0s0, r1.xyxx.xy, 0.0).xyz;
    vec4 r2;
    r2 = vec4(_204.x, _204.y, _204.z, r2.w);
    vec3 _213 = fma(-r2.xyz, r2.xyz, vec3(1.0));
    r2 = vec4(_213.x, _213.y, _213.z, r2.w);
    vec3 _219 = (-r2.xyz) + vec3(1.0);
    r2 = vec4(_219.x, _219.y, _219.z, r2.w);
    vec3 _225 = (-r2.xyz) + vec3(1.0);
    r2 = vec4(_225.x, _225.y, _225.z, r2.w);
    float _233 = dot(cb2_0_1._m0[5u].xyz, r0.xyz);
    float _907 = isnan(0.0) ? _233 : (isnan(_233) ? 0.0 : max(_233, 0.0));
    r0.w = isnan(1.0) ? _907 : (isnan(_907) ? 1.0 : min(_907, 1.0));
    r0.w *= cb13_0._m0[8u].y;
    r1.z = r0.w * cb11_0._m0[0u].x;
    vec2 _257 = r1.zw + cb11_0._m0[0u].zw;
    r1 = vec4(_257.x, _257.y, r1.z, r1.w);
    vec3 _266 = textureLod(SPIRV_Cross_Combinedt0s0, r1.xyxx.xy, 0.0).xyz;
    r1 = vec4(_266.x, _266.y, _266.z, r1.w);
    vec3 _274 = fma(-r1.xyz, r1.xyz, vec3(1.0));
    r1 = vec4(_274.x, _274.y, _274.z, r1.w);
    vec3 _282 = fma(-r2.xyz, r1.xyz, vec3(1.0));
    r1 = vec4(_282.x, _282.y, _282.z, r1.w);
    vec3 _288 = (-r1.xyz) + vec3(1.0);
    r1 = vec4(_288.x, _288.y, _288.z, r1.w);
    float _297 = dot(cb2_0_1._m0[6u].xyz, r0.xyz);
    float _918 = isnan(0.0) ? _297 : (isnan(_297) ? 0.0 : max(_297, 0.0));
    r0.w = isnan(1.0) ? _918 : (isnan(_918) ? 1.0 : min(_918, 1.0));
    r0.w *= cb13_0._m0[8u].z;
    r2.x = r0.w * cb11_0._m0[0u].x;
    r2 = vec4(r2.x, vec2(0.0, 0.5).x, r2.z, vec2(0.0, 0.5).y);
    vec2 _323 = r2.xy + cb11_0._m0[0u].zw;
    r2 = vec4(_323.x, _323.y, r2.z, r2.w);
    vec3 _332 = textureLod(SPIRV_Cross_Combinedt0s0, r2.xyxx.xy, 0.0).xyz;
    vec4 r3;
    r3 = vec4(_332.x, _332.y, _332.z, r3.w);
    vec3 _340 = fma(-r3.xyz, r3.xyz, vec3(1.0));
    r3 = vec4(_340.x, _340.y, _340.z, r3.w);
    vec3 _348 = fma(-r1.xyz, r3.xyz, vec3(1.0));
    r1 = vec4(_348.x, _348.y, _348.z, r1.w);
    vec3 _354 = (-r1.xyz) + vec3(1.0);
    r3 = vec4(_354.x, _354.y, _354.z, r3.w);
    r0.w = dot(v5, v5);
    r0.w = sqrt(r0.w);
    vec3 _368 = v5 / r0.www;
    vec4 r4;
    r4 = vec4(_368.x, _368.y, _368.z, r4.w);
    vec3 _377 = (-r4.xyz) + cb2_0_1._m0[5u].xyz;
    vec4 r5;
    r5 = vec4(_377.x, _377.y, _377.z, r5.w);
    r0.w = dot(r5.xyz, r5.xyz);
    r0.w = inversesqrt(r0.w);
    vec3 _394 = r0.www * r5.xyz;
    r5 = vec4(_394.x, _394.y, _394.z, r5.w);
    float _401 = dot(r5.xyz, r0.xyz);
    float _929 = isnan(0.0) ? _401 : (isnan(_401) ? 0.0 : max(_401, 0.0));
    r0.w = isnan(1.0) ? _929 : (isnan(_929) ? 1.0 : min(_929, 1.0));
    r0.w = log2(r0.w);
    r0.w = fma(r0.w, cb13_0._m0[5u].w, -1.6514961719512939453125);
    r0.w = exp2(r0.w);
    r5 = texture(SPIRV_Cross_Combinedt3s3, v1.xyxx.xy);
    r0.w *= r5.w;
    r0.w *= cb13_0._m0[8u].y;
    vec3 _445 = (-r4.xyz) + cb2_0_1._m0[4u].xyz;
    vec4 r6;
    r6 = vec4(_445.x, _445.y, _445.z, r6.w);
    vec3 _454 = (-r4.xyz) + cb2_0_1._m0[6u].xyz;
    r4 = vec4(_454.x, _454.y, _454.z, r4.w);
    r1.w = dot(r6.xyz, r6.xyz);
    r1.w = inversesqrt(r1.w);
    vec3 _471 = r1.www * r6.xyz;
    r6 = vec4(_471.x, _471.y, _471.z, r6.w);
    float _478 = dot(r6.xyz, r0.xyz);
    float _940 = isnan(0.0) ? _478 : (isnan(_478) ? 0.0 : max(_478, 0.0));
    r1.w = isnan(1.0) ? _940 : (isnan(_940) ? 1.0 : min(_940, 1.0));
    r1.w = log2(r1.w);
    r1.w = fma(r1.w, cb13_0._m0[5u].w, -1.6514961719512939453125);
    r1.w = exp2(r1.w);
    r1.w *= r5.w;
    r0.w = fma(r1.w, cb13_0._m0[8u].x, r0.w);
    r1.w = dot(r4.xyz, r4.xyz);
    r1.w = inversesqrt(r1.w);
    vec3 _525 = r1.www * r4.xyz;
    r4 = vec4(_525.x, _525.y, _525.z, r4.w);
    float _532 = dot(r4.xyz, r0.xyz);
    float _951 = isnan(0.0) ? _532 : (isnan(_532) ? 0.0 : max(_532, 0.0));
    r0.x = isnan(1.0) ? _951 : (isnan(_951) ? 1.0 : min(_951, 1.0));
    r0.x = log2(r0.x);
    r0.x = fma(r0.x, cb13_0._m0[5u].w, -1.6514961719512939453125);
    r0.x = exp2(r0.x);
    r0.x *= r5.w;
    r2.z = fma(r0.x, cb13_0._m0[8u].z, r0.w);
    vec3 _571 = texture(SPIRV_Cross_Combinedt0s0, r2.zwzz.xy).xyz;
    r0 = vec4(_571.x, _571.y, _571.z, r0.w);
    vec3 _577 = (-r0.xyz) + vec3(1.0);
    r0 = vec4(_577.x, _577.y, _577.z, r0.w);
    vec3 _585 = fma(-r3.xyz, r0.xyz, vec3(1.0));
    r0 = vec4(_585.x, _585.y, _585.z, r0.w);
    vec3 _592 = r0.xyz * r2.zzz;
    r0 = vec4(_592.x, _592.y, _592.z, r0.w);
    vec3 _600 = r0.xyz * cb13_0._m0[5u].yyy;
    r0 = vec4(_600.x, _600.y, _600.z, r0.w);
    float _607 = dot(r5.zywx, cb13_0._m0[1u]);
    float _962 = isnan(0.0) ? _607 : (isnan(_607) ? 0.0 : max(_607, 0.0));
    r0.w = isnan(1.0) ? _962 : (isnan(_962) ? 1.0 : min(_962, 1.0));
    r1.w = (-r5.y) + 1.0;
    r0.w = fma(r0.x, r1.w, r0.w);
    r2.x = r0.w + (-cb2_0_1._m0[7u].x);
    float _973 = isnan(0.0) ? r0.w : (isnan(r0.w) ? 0.0 : max(r0.w, 0.0));
    r0.w = isnan(1.0) ? _973 : (isnan(_973) ? 1.0 : min(_973, 1.0));
    r3.w = r0.w * cb13_0._m0[0u].w;
    r0.w = uintBitsToFloat((r2.x < 0.0) ? 4294967295u : 0u);
    if (floatBitsToUint(r0.w) != 0u)
    {
        discard;
    }
    vec3 _663 = (-r1.xyz) + cb13_0._m0[3u].xyz;
    r2 = vec4(_663.x, _663.y, _663.z, r2.w);
    vec3 _670 = r1.xyz * r5.www;
    r4 = vec4(_670.x, _670.y, _670.z, r4.w);
    vec3 _679 = fma(r4.xyz, r2.xyz, r1.xyz);
    r1 = vec4(_679.x, _679.y, _679.z, r1.w);
    vec3 _687 = cb13_0._m0[3u].xyz + vec3(-1.0);
    r2 = vec4(_687.x, _687.y, _687.z, r2.w);
    vec3 _694 = fma(r5.www, r2.xyz, vec3(1.0));
    r2 = vec4(_694.x, _694.y, _694.z, r2.w);
    vec3 _701 = r1.xyz * r2.xyz;
    r1 = vec4(_701.x, _701.y, _701.z, r1.w);
    vec3 _707 = cb13_0._m0[2u].xyz + vec3(-1.0);
    r2 = vec4(_707.x, _707.y, _707.z, r2.w);
    vec3 _714 = fma(r5.yyy, r2.xyz, vec3(1.0));
    r2 = vec4(_714.x, _714.y, _714.z, r2.w);
    r0.w = r5.y * cb13_0._m0[6u].w;
    r0.w = fma(r0.w, -0.5, cb13_0._m0[6u].w);
    vec3 _736 = r1.xyz * r2.xyz;
    r1 = vec4(_736.x, _736.y, _736.z, r1.w);
    vec2 _747 = fma(v1.zw, cb11_0._m0[4u].xy, cb11_0._m0[4u].zw);
    r2 = vec4(_747.x, _747.y, r2.z, r2.w);
    vec3 _756 = texture(SPIRV_Cross_Combinedt4s4, r2.xyxx.xy).xyw;
    r2 = vec4(_756.x, _756.y, _756.z, r2.w);
    r2.x += (-0.980000019073486328125);
    r2.y += r2.z;
    r2.y += (-cb13_0._m0[7u].x);
    r2.y = fract(r2.y);
    r2.y *= r2.y;
    r2.y *= (-cb13_0._m0[7u].y);
    r2.y = exp2(r2.y);
    r2.y = isnan(1.0) ? r2.y : (isnan(r2.y) ? 1.0 : min(r2.y, 1.0));
    float _807 = r2.x * 255.0000152587890625;
    float _989 = isnan(0.0) ? _807 : (isnan(_807) ? 0.0 : max(_807, 0.0));
    r2.x = isnan(1.0) ? _989 : (isnan(_989) ? 1.0 : min(_989, 1.0));
    r0.w *= r2.x;
    r0.w = r2.y * r0.w;
    vec3 _827 = r0.www * cb13_0._m0[6u].xyz;
    r2 = vec4(_827.x, _827.y, _827.z, r2.w);
    vec3 _836 = texture(SPIRV_Cross_Combinedt1s1, v1.xyxx.xy).xyz;
    r4 = vec4(_836.x, _836.y, _836.z, r4.w);
    vec3 _845 = fma(r1.xyz, r4.xyz, r2.xyz);
    r1 = vec4(_845.x, _845.y, _845.z, r1.w);
    vec3 _854 = fma(r0.xyz, r1.www, r1.xyz);
    r3 = vec4(_854.x, _854.y, _854.z, r3.w);
    r0 = fma(r3, cb0_0._m0[7u].wwww, -r3);
    r0 = fma(cb1_0_1._m0[8u].zzzz, r0, r3);
    o0 = mix(mix(max(r0, vec4(0.0)), vec4(0.0), isnan(r0)), r0, isnan(vec4(0.0)));
}


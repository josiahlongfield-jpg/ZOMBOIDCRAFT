#version 450

layout(binding = 0, std140) uniform cb7_struct
{
    vec4 _m0[7];
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

layout(binding = 4, std140) uniform cb1_struct
{
    vec4 _m0[1];
} cb11_0;

uniform sampler2D SPIRV_Cross_Combinedt2s2;
uniform sampler2D SPIRV_Cross_Combinedt3s3;
uniform sampler2D SPIRV_Cross_Combinedt5s5;
uniform sampler2D SPIRV_Cross_Combinedt1s1;
uniform samplerCube SPIRV_Cross_Combinedt6s6;
uniform sampler2D SPIRV_Cross_Combinedt0s0;

layout(location = 1) in vec4 v1;
layout(location = 2) in vec3 v2;
layout(location = 3) in vec3 v3;
layout(location = 4) in vec3 v4;
layout(location = 5) in vec3 v5;
layout(location = 0) out vec4 o0;

void main()
{
    vec2 _76 = texture(SPIRV_Cross_Combinedt2s2, v1.xyxx.xy).xy;
    vec4 r0;
    r0 = vec4(_76.x, _76.y, r0.z, r0.w);
    vec3 _82 = r0.yyy * v3;
    vec4 r1;
    r1 = vec4(_82.x, _82.y, _82.z, r1.w);
    vec3 _91 = fma(r0.xxx, v4, r1.xyz);
    r1 = vec4(_91.x, _91.y, _91.z, r1.w);
    r0.x = dot(r0.xy, -r0.xy);
    float _106 = r0.x + 1.0;
    r0.x = saturate(_106);
    r0.x = sqrt(r0.x);
    vec3 _119 = fma(r0.xxx, v2, r1.xyz);
    r0 = vec4(_119.x, _119.y, _119.z, r0.w);
    r0.w = dot(r0.xyz, r0.xyz);
    r0.w = inversesqrt(r0.w);
    vec3 _137 = r0.www * r0.xyz;
    r0 = vec4(_137.x, _137.y, _137.z, r0.w);
    r0.w = dot(v5, v5);
    r0.w = sqrt(r0.w);
    vec3 _151 = v5 / r0.www;
    r1 = vec4(_151.x, _151.y, _151.z, r1.w);
    r0.w += (-cb13_0._m0[5u].z);
    vec3 _172 = (-r1.xyz) + cb2_0_1._m0[5u].xyz;
    vec4 r2;
    r2 = vec4(_172.x, _172.y, _172.z, r2.w);
    r1.w = dot(r2.xyz, r2.xyz);
    r1.w = inversesqrt(r1.w);
    vec3 _189 = r1.www * r2.xyz;
    r2 = vec4(_189.x, _189.y, _189.z, r2.w);
    float _196 = dot(r2.xyz, r0.xyz);
    r1.w = saturate(_196);
    r1.w = log2(r1.w);
    r1.w = fma(r1.w, cb13_0._m0[2u].y, -1.6514961719512939453125);
    r1.w = exp2(r1.w);
    r1.w *= cb13_0._m0[6u].z;
    vec3 _230 = (-r1.xyz) + cb2_0_1._m0[4u].xyz;
    vec4 r3;
    r3 = vec4(_230.x, _230.y, _230.z, r3.w);
    r2.w = dot(r3.xyz, r3.xyz);
    r2.w = inversesqrt(r2.w);
    vec3 _247 = r2.www * r3.xyz;
    r3 = vec4(_247.x, _247.y, _247.z, r3.w);
    float _254 = dot(r3.xyz, r0.xyz);
    r2.w = saturate(_254);
    r2.w = log2(r2.w);
    r2.w = fma(r2.w, cb13_0._m0[2u].y, -1.6514961719512939453125);
    r2.w = exp2(r2.w);
    r1.w = fma(r2.w, cb13_0._m0[6u].y, r1.w);
    vec3 _287 = (-r1.xyz) + cb2_0_1._m0[6u].xyz;
    vec4 r4;
    r4 = vec4(_287.x, _287.y, _287.z, r4.w);
    r2.w = dot(r4.xyz, r4.xyz);
    r2.w = inversesqrt(r2.w);
    vec3 _304 = r2.www * r4.xyz;
    r4 = vec4(_304.x, _304.y, _304.z, r4.w);
    float _311 = dot(r4.xyz, r0.xyz);
    r2.w = saturate(_311);
    float _319 = dot(r0.xyz, -r1.xyz);
    r0.x = saturate(_319);
    r0.x = (-r0.x) + 1.0;
    r0.y = log2(r2.w);
    r0.y = fma(r0.y, cb13_0._m0[2u].y, -1.6514961719512939453125);
    r0.y = exp2(r0.y);
    r0.y = fma(r0.y, cb13_0._m0[6u].w, r1.w);
    vec2 _357 = texture(SPIRV_Cross_Combinedt3s3, v1.xyxx.xy).xy;
    vec4 r5;
    r5 = vec4(_357.x, _357.y, r5.z, r5.w);
    vec3 _363 = r5.yyy * v3;
    vec4 r6;
    r6 = vec4(_363.x, _363.y, _363.z, r6.w);
    vec3 _371 = fma(r5.xxx, v4, r6.xyz);
    r6 = vec4(_371.x, _371.y, _371.z, r6.w);
    r0.z = dot(r5.xy, -r5.xy);
    float _383 = r0.z + 1.0;
    r0.z = saturate(_383);
    r0.z = sqrt(r0.z);
    vec3 _395 = fma(r0.zzz, v2, r6.xyz);
    r5 = vec4(_395.x, _395.y, _395.z, r5.w);
    r0.z = dot(r5.xyz, r5.xyz);
    r0.z = inversesqrt(r0.z);
    vec3 _412 = r0.zzz * r5.xyz;
    r5 = vec4(_412.x, _412.y, _412.z, r5.w);
    float _419 = dot(r2.xyz, r5.xyz);
    r0.z = saturate(_419);
    r0.z = log2(r0.z);
    r1.w = (-cb13_0._m0[4u].y) + cb13_0._m0[4u].z;
    vec3 _441 = texture(SPIRV_Cross_Combinedt5s5, v1.xyxx.xy).xyz;
    r2 = vec4(_441.x, _441.y, _441.z, r2.w);
    float _447 = (-r2.z) + 1.0;
    r2.z = saturate(_447);
    r1.w = fma(r2.z, r1.w, cb13_0._m0[4u].y);
    r1.w = isnan(9.9999997473787516355514526367188e-06) ? r1.w : (isnan(r1.w) ? 9.9999997473787516355514526367188e-06 : max(r1.w, 9.9999997473787516355514526367188e-06));
    r1.w = 1.0 / r1.w;
    r0.z = fma(r0.z, r1.w, -1.6514961719512939453125);
    r0.z = exp2(r0.z);
    r0.z *= cb13_0._m0[6u].z;
    float _489 = dot(r3.xyz, r5.xyz);
    r2.z = saturate(_489);
    r2.z = log2(r2.z);
    r2.z = fma(r2.z, r1.w, -1.6514961719512939453125);
    r2.z = exp2(r2.z);
    r0.z = fma(r2.z, cb13_0._m0[6u].y, r0.z);
    float _519 = dot(r4.xyz, r5.xyz);
    r2.z = saturate(_519);
    r2.z = log2(r2.z);
    r1.w = fma(r2.z, r1.w, -1.6514961719512939453125);
    r1.w = exp2(r1.w);
    r0.z = fma(r1.w, cb13_0._m0[6u].w, r0.z);
    r3 = texture(SPIRV_Cross_Combinedt1s1, v1.xyxx.xy);
    vec3 _557 = r3.xyz + (-cb13_0._m0[3u].xyz);
    r4 = vec4(_557.x, _557.y, _557.z, r4.w);
    vec3 _568 = fma(cb13_0._m0[3u].www, r4.xyz, cb13_0._m0[3u].xyz);
    r4 = vec4(_568.x, _568.y, _568.z, r4.w);
    vec3 _576 = r4.xyz * cb13_0._m0[4u].xxx;
    r4 = vec4(_576.x, _576.y, _576.z, r4.w);
    vec3 _583 = r2.yyy * r4.xyz;
    r4 = vec4(_583.x, _583.y, _583.z, r4.w);
    vec3 _590 = r0.zzz * r4.xyz;
    r4 = vec4(_590.x, _590.y, _590.z, r4.w);
    vec3 _599 = r3.xyz + (-cb13_0._m0[1u].xyz);
    r6 = vec4(_599.x, _599.y, _599.z, r6.w);
    vec3 _610 = fma(cb13_0._m0[1u].www, r6.xyz, cb13_0._m0[1u].xyz);
    r6 = vec4(_610.x, _610.y, _610.z, r6.w);
    vec3 _618 = r6.xyz * cb13_0._m0[2u].xxx;
    r6 = vec4(_618.x, _618.y, _618.z, r6.w);
    vec3 _625 = r2.xxx * r6.xyz;
    r2 = vec4(_625.x, r2.y, _625.y, _625.z);
    r0.x *= r2.y;
    vec3 _640 = fma(r0.yyy, r2.xzw, r4.xyz);
    r2 = vec4(_640.x, _640.y, _640.z, r2.w);
    r0.y = (-cb13_0._m0[5u].z) + cb13_0._m0[5u].y;
    float _656 = r0.w / r0.y;
    r0.y = saturate(_656);
    r0.z = (-r0.y) + 1.0;
    float _671 = fma(cb13_0._m0[4u].w, r0.z, r0.y);
    r0.y = saturate(_671);
    r0.z = (-r0.y) + 1.0;
    r0.y = fma(cb13_0._m0[5u].x, r0.z, r0.y);
    r0.z = fma(r2.x, r0.y, r3.w);
    vec3 _700 = r0.yyy * r2.xyz;
    r2 = vec4(_700.x, _700.y, _700.z, r2.w);
    r0.y = r0.z + (-cb2_0_1._m0[7u].x);
    r0.z = saturate(r0.z);
    r4.w = r0.z * cb13_0._m0[0u].w;
    r0.y = uintBitsToFloat((r0.y < 0.0) ? 4294967295u : 0u);
    if (floatBitsToUint(r0.y) != 0u)
    {
        discard;
    }
    r0.y = dot(r1.xyz, r5.xyz);
    r0.y += r0.y;
    vec3 _755 = fma(r5.xyz, -r0.yyy, r1.xyz);
    r0 = vec4(r0.x, _755.x, _755.y, _755.z);
    vec3 _765 = texture(SPIRV_Cross_Combinedt6s6, r0.yzwy.xyz).xyz;
    r0 = vec4(r0.x, _765.x, _765.y, _765.z);
    r1.x = dot(v2, v2);
    r1.x = inversesqrt(r1.x);
    vec3 _779 = r1.xxx * v2;
    r1 = vec4(_779.x, _779.y, _779.z, r1.w);
    float _787 = dot(cb2_0_1._m0[4u].xyz, r1.xyz);
    r1.w = saturate(_787);
    r1.w *= cb13_0._m0[6u].y;
    r5.x = r1.w * cb11_0._m0[0u].x;
    r5 = vec4(r5.x, vec2(0.0).x, r5.z, vec2(0.0).y);
    vec2 _812 = r5.xy + cb11_0._m0[0u].zw;
    r5 = vec4(_812.x, _812.y, r5.z, r5.w);
    vec3 _821 = textureLod(SPIRV_Cross_Combinedt0s0, r5.xyxx.xy, 0.0).xyz;
    r6 = vec4(_821.x, _821.y, _821.z, r6.w);
    vec3 _830 = fma(-r6.xyz, r6.xyz, vec3(1.0));
    r6 = vec4(_830.x, _830.y, _830.z, r6.w);
    vec3 _836 = (-r6.xyz) + vec3(1.0);
    r6 = vec4(_836.x, _836.y, _836.z, r6.w);
    vec3 _842 = (-r6.xyz) + vec3(1.0);
    r6 = vec4(_842.x, _842.y, _842.z, r6.w);
    float _850 = dot(cb2_0_1._m0[5u].xyz, r1.xyz);
    r1.w = saturate(_850);
    float _858 = dot(cb2_0_1._m0[6u].xyz, r1.xyz);
    r1.x = saturate(_858);
    vec2 _866 = r1.xw * cb13_0._m0[6u].wz;
    r1 = vec4(_866.x, r1.y, _866.y, r1.w);
    r1.x *= cb11_0._m0[0u].x;
    r5.z = r1.z * cb11_0._m0[0u].x;
    vec2 _888 = r5.zw + cb11_0._m0[0u].zw;
    r1 = vec4(r1.x, r1.y, _888.x, _888.y);
    vec3 _897 = textureLod(SPIRV_Cross_Combinedt0s0, r1.zwzz.xy, 0.0).xyz;
    r5 = vec4(_897.x, _897.y, _897.z, r5.w);
    vec3 _905 = fma(-r5.xyz, r5.xyz, vec3(1.0));
    r5 = vec4(_905.x, _905.y, _905.z, r5.w);
    vec3 _913 = fma(-r6.xyz, r5.xyz, vec3(1.0));
    r5 = vec4(_913.x, _913.y, _913.z, r5.w);
    vec3 _919 = (-r5.xyz) + vec3(1.0);
    r5 = vec4(_919.x, _919.y, _919.z, r5.w);
    r1.y = 0.0;
    vec2 _928 = r1.xy + cb11_0._m0[0u].zw;
    r1 = vec4(_928.x, _928.y, r1.z, r1.w);
    vec3 _937 = textureLod(SPIRV_Cross_Combinedt0s0, r1.xyxx.xy, 0.0).xyz;
    r1 = vec4(_937.x, _937.y, _937.z, r1.w);
    vec3 _945 = fma(-r1.xyz, r1.xyz, vec3(1.0));
    r1 = vec4(_945.x, _945.y, _945.z, r1.w);
    vec3 _953 = fma(-r5.xyz, r1.xyz, vec3(1.0));
    r1 = vec4(_953.x, _953.y, _953.z, r1.w);
    vec3 _962 = fma(r1.xyz, r3.xyz, r2.xyz);
    r2 = vec4(_962.x, _962.y, _962.z, r2.w);
    vec3 _969 = r0.xxx * r1.xyz;
    r1 = vec4(_969.x, _969.y, _969.z, r1.w);
    vec3 _978 = fma(r0.yzw, r1.xyz, r2.xyz);
    r4 = vec4(_978.x, _978.y, _978.z, r4.w);
    r0 = fma(r4, cb0_0._m0[7u].wwww, -r4);
    r0 = fma(cb1_0._m0[8u].zzzz, r0, r4);
    o0 = mix(mix(max(r0, vec4(0.0)), vec4(0.0), isnan(r0)), r0, isnan(vec4(0.0)));
}


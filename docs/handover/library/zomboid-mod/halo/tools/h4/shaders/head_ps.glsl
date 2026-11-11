#version 450

layout(binding = 0, std140) uniform cb32_struct
{
    vec4 _m0[32];
} cb13_0;

layout(binding = 1, std140) uniform cb17_struct
{
    vec4 _m0[17];
} cb0_0;

layout(binding = 2, std140) uniform cb14_struct
{
    vec4 _m0[14];
} cb1_0;

layout(binding = 3, std140) uniform cb9_struct
{
    vec4 _m0[9];
} cb2_0;

layout(binding = 4, std140) uniform cb12_struct
{
    vec4 _m0[12];
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
    vec2 _85 = texture(SPIRV_Cross_Combinedt2s2, v1.xyxx.xy).xy;
    r0 = vec4(r0.x, _85.x, _85.y, r0.w);
    vec2 _94 = texture(SPIRV_Cross_Combinedt5s5, v1.xyxx.xy).xy;
    vec4 r1;
    r1 = vec4(_94.x, _94.y, r1.z, r1.w);
    vec2 _104 = fma(r0.xx, r1.xy, r0.yz);
    r0 = vec4(_104.x, _104.y, r0.z, r0.w);
    vec3 _110 = r0.yyy * v3;
    r1 = vec4(_110.x, _110.y, _110.z, r1.w);
    vec3 _118 = fma(r0.xxx, v4, r1.xyz);
    r1 = vec4(_118.x, _118.y, _118.z, r1.w);
    r0.x = dot(r0.xy, -r0.xy);
    float _130 = r0.x + 1.0;
    float _917 = isnan(0.0) ? _130 : (isnan(_130) ? 0.0 : max(_130, 0.0));
    r0.x = isnan(1.0) ? _917 : (isnan(_917) ? 1.0 : min(_917, 1.0));
    r0.x = sqrt(r0.x);
    vec3 _143 = fma(r0.xxx, v2, r1.xyz);
    r0 = vec4(_143.x, _143.y, _143.z, r0.w);
    r0.w = dot(r0.xyz, r0.xyz);
    r0.w = inversesqrt(r0.w);
    vec3 _161 = r0.www * r0.xyz;
    r0 = vec4(_161.x, _161.y, _161.z, r0.w);
    float _171 = dot(cb2_0._m0[4u].xyz, r0.xyz);
    float _928 = isnan(0.0) ? _171 : (isnan(_171) ? 0.0 : max(_171, 0.0));
    r0.w = isnan(1.0) ? _928 : (isnan(_928) ? 1.0 : min(_928, 1.0));
    r0.w *= cb13_0._m0[8u].x;
    r1.x = r0.w * cb11_0._m0[0u].x;
    r1 = vec4(r1.x, vec2(0.0).x, r1.z, vec2(0.0).y);
    vec2 _198 = r1.xy + cb11_0._m0[0u].zw;
    r1 = vec4(_198.x, _198.y, r1.z, r1.w);
    vec3 _207 = textureLod(SPIRV_Cross_Combinedt0s0, r1.xyxx.xy, 0.0).xyz;
    vec4 r2;
    r2 = vec4(_207.x, _207.y, _207.z, r2.w);
    vec3 _216 = fma(-r2.xyz, r2.xyz, vec3(1.0));
    r2 = vec4(_216.x, _216.y, _216.z, r2.w);
    vec3 _222 = (-r2.xyz) + vec3(1.0);
    r2 = vec4(_222.x, _222.y, _222.z, r2.w);
    vec3 _228 = (-r2.xyz) + vec3(1.0);
    r2 = vec4(_228.x, _228.y, _228.z, r2.w);
    float _237 = dot(cb2_0._m0[5u].xyz, r0.xyz);
    float _939 = isnan(0.0) ? _237 : (isnan(_237) ? 0.0 : max(_237, 0.0));
    r0.w = isnan(1.0) ? _939 : (isnan(_939) ? 1.0 : min(_939, 1.0));
    r0.w *= cb13_0._m0[8u].y;
    r1.z = r0.w * cb11_0._m0[0u].x;
    vec2 _261 = r1.zw + cb11_0._m0[0u].zw;
    r1 = vec4(_261.x, _261.y, r1.z, r1.w);
    vec3 _270 = textureLod(SPIRV_Cross_Combinedt0s0, r1.xyxx.xy, 0.0).xyz;
    r1 = vec4(_270.x, _270.y, _270.z, r1.w);
    vec3 _278 = fma(-r1.xyz, r1.xyz, vec3(1.0));
    r1 = vec4(_278.x, _278.y, _278.z, r1.w);
    vec3 _286 = fma(-r2.xyz, r1.xyz, vec3(1.0));
    r1 = vec4(_286.x, _286.y, _286.z, r1.w);
    vec3 _292 = (-r1.xyz) + vec3(1.0);
    r1 = vec4(_292.x, _292.y, _292.z, r1.w);
    float _301 = dot(cb2_0._m0[6u].xyz, r0.xyz);
    float _950 = isnan(0.0) ? _301 : (isnan(_301) ? 0.0 : max(_301, 0.0));
    r0.w = isnan(1.0) ? _950 : (isnan(_950) ? 1.0 : min(_950, 1.0));
    r0.w *= cb13_0._m0[8u].z;
    r2.x = r0.w * cb11_0._m0[0u].x;
    r2 = vec4(r2.x, vec2(0.0, 0.5).x, r2.z, vec2(0.0, 0.5).y);
    vec2 _327 = r2.xy + cb11_0._m0[0u].zw;
    r2 = vec4(_327.x, _327.y, r2.z, r2.w);
    vec3 _336 = textureLod(SPIRV_Cross_Combinedt0s0, r2.xyxx.xy, 0.0).xyz;
    vec4 r3;
    r3 = vec4(_336.x, _336.y, _336.z, r3.w);
    vec3 _344 = fma(-r3.xyz, r3.xyz, vec3(1.0));
    r3 = vec4(_344.x, _344.y, _344.z, r3.w);
    vec3 _352 = fma(-r1.xyz, r3.xyz, vec3(1.0));
    r1 = vec4(_352.x, _352.y, _352.z, r1.w);
    vec3 _358 = (-r1.xyz) + vec3(1.0);
    r3 = vec4(_358.x, _358.y, _358.z, r3.w);
    r0.w = dot(v5, v5);
    r0.w = sqrt(r0.w);
    vec3 _372 = v5 / r0.www;
    vec4 r4;
    r4 = vec4(_372.x, _372.y, _372.z, r4.w);
    vec3 _381 = (-r4.xyz) + cb2_0._m0[5u].xyz;
    vec4 r5;
    r5 = vec4(_381.x, _381.y, _381.z, r5.w);
    r0.w = dot(r5.xyz, r5.xyz);
    r0.w = inversesqrt(r0.w);
    vec3 _398 = r0.www * r5.xyz;
    r5 = vec4(_398.x, _398.y, _398.z, r5.w);
    float _405 = dot(r5.xyz, r0.xyz);
    float _961 = isnan(0.0) ? _405 : (isnan(_405) ? 0.0 : max(_405, 0.0));
    r0.w = isnan(1.0) ? _961 : (isnan(_961) ? 1.0 : min(_961, 1.0));
    r0.w = log2(r0.w);
    r0.w = fma(r0.w, cb13_0._m0[5u].w, -1.6514961719512939453125);
    r0.w = exp2(r0.w);
    r5 = texture(SPIRV_Cross_Combinedt3s3, v1.xyxx.xy);
    r0.w *= r5.w;
    r0.w *= cb13_0._m0[8u].y;
    vec3 _449 = (-r4.xyz) + cb2_0._m0[4u].xyz;
    vec4 r6;
    r6 = vec4(_449.x, _449.y, _449.z, r6.w);
    vec3 _458 = (-r4.xyz) + cb2_0._m0[6u].xyz;
    r4 = vec4(_458.x, _458.y, _458.z, r4.w);
    r1.w = dot(r6.xyz, r6.xyz);
    r1.w = inversesqrt(r1.w);
    vec3 _475 = r1.www * r6.xyz;
    r6 = vec4(_475.x, _475.y, _475.z, r6.w);
    float _482 = dot(r6.xyz, r0.xyz);
    float _972 = isnan(0.0) ? _482 : (isnan(_482) ? 0.0 : max(_482, 0.0));
    r1.w = isnan(1.0) ? _972 : (isnan(_972) ? 1.0 : min(_972, 1.0));
    r1.w = log2(r1.w);
    r1.w = fma(r1.w, cb13_0._m0[5u].w, -1.6514961719512939453125);
    r1.w = exp2(r1.w);
    r1.w *= r5.w;
    r0.w = fma(r1.w, cb13_0._m0[8u].x, r0.w);
    r1.w = dot(r4.xyz, r4.xyz);
    r1.w = inversesqrt(r1.w);
    vec3 _529 = r1.www * r4.xyz;
    r4 = vec4(_529.x, _529.y, _529.z, r4.w);
    float _536 = dot(r4.xyz, r0.xyz);
    float _983 = isnan(0.0) ? _536 : (isnan(_536) ? 0.0 : max(_536, 0.0));
    r0.x = isnan(1.0) ? _983 : (isnan(_983) ? 1.0 : min(_983, 1.0));
    r0.x = log2(r0.x);
    r0.x = fma(r0.x, cb13_0._m0[5u].w, -1.6514961719512939453125);
    r0.x = exp2(r0.x);
    r0.x *= r5.w;
    r2.z = fma(r0.x, cb13_0._m0[8u].z, r0.w);
    vec3 _575 = texture(SPIRV_Cross_Combinedt0s0, r2.zwzz.xy).xyz;
    r0 = vec4(_575.x, _575.y, _575.z, r0.w);
    vec3 _581 = (-r0.xyz) + vec3(1.0);
    r0 = vec4(_581.x, _581.y, _581.z, r0.w);
    vec3 _589 = fma(-r3.xyz, r0.xyz, vec3(1.0));
    r0 = vec4(_589.x, _589.y, _589.z, r0.w);
    vec3 _596 = r0.xyz * r2.zzz;
    r0 = vec4(_596.x, _596.y, _596.z, r0.w);
    vec3 _604 = r0.xyz * cb13_0._m0[5u].yyy;
    r0 = vec4(_604.x, _604.y, _604.z, r0.w);
    float _611 = dot(r5.zywx, cb13_0._m0[1u]);
    float _994 = isnan(0.0) ? _611 : (isnan(_611) ? 0.0 : max(_611, 0.0));
    r0.w = isnan(1.0) ? _994 : (isnan(_994) ? 1.0 : min(_994, 1.0));
    r1.w = (-r5.y) + 1.0;
    r0.w = fma(r0.x, r1.w, r0.w);
    r2.x = r0.w + (-cb2_0._m0[7u].x);
    float _1005 = isnan(0.0) ? r0.w : (isnan(r0.w) ? 0.0 : max(r0.w, 0.0));
    r0.w = isnan(1.0) ? _1005 : (isnan(_1005) ? 1.0 : min(_1005, 1.0));
    r3.w = r0.w * cb13_0._m0[0u].w;
    r0.w = uintBitsToFloat((r2.x < 0.0) ? 4294967295u : 0u);
    if (floatBitsToUint(r0.w) != 0u)
    {
        discard;
    }
    vec3 _667 = (-r1.xyz) + cb13_0._m0[3u].xyz;
    r2 = vec4(_667.x, _667.y, _667.z, r2.w);
    vec3 _674 = r1.xyz * r5.www;
    r4 = vec4(_674.x, _674.y, _674.z, r4.w);
    vec3 _683 = fma(r4.xyz, r2.xyz, r1.xyz);
    r1 = vec4(_683.x, _683.y, _683.z, r1.w);
    vec3 _691 = cb13_0._m0[3u].xyz + vec3(-1.0);
    r2 = vec4(_691.x, _691.y, _691.z, r2.w);
    vec3 _698 = fma(r5.www, r2.xyz, vec3(1.0));
    r2 = vec4(_698.x, _698.y, _698.z, r2.w);
    vec3 _705 = r1.xyz * r2.xyz;
    r1 = vec4(_705.x, _705.y, _705.z, r1.w);
    vec3 _711 = cb13_0._m0[2u].xyz + vec3(-1.0);
    r2 = vec4(_711.x, _711.y, _711.z, r2.w);
    vec3 _718 = fma(r5.yyy, r2.xyz, vec3(1.0));
    r2 = vec4(_718.x, _718.y, _718.z, r2.w);
    r0.w = r5.y * cb13_0._m0[6u].w;
    r0.w = fma(r0.w, -0.5, cb13_0._m0[6u].w);
    vec3 _740 = r1.xyz * r2.xyz;
    r1 = vec4(_740.x, _740.y, _740.z, r1.w);
    vec2 _751 = fma(v1.zw, cb11_0._m0[4u].xy, cb11_0._m0[4u].zw);
    r2 = vec4(_751.x, _751.y, r2.z, r2.w);
    vec3 _760 = texture(SPIRV_Cross_Combinedt4s4, r2.xyxx.xy).xyw;
    r2 = vec4(_760.x, _760.y, _760.z, r2.w);
    r2.x += (-0.980000019073486328125);
    r2.y += r2.z;
    r2.y += (-cb13_0._m0[7u].x);
    r2.y = fract(r2.y);
    r2.y *= r2.y;
    r2.y *= (-cb13_0._m0[7u].y);
    r2.y = exp2(r2.y);
    r2.y = isnan(1.0) ? r2.y : (isnan(r2.y) ? 1.0 : min(r2.y, 1.0));
    float _811 = r2.x * 255.0000152587890625;
    float _1021 = isnan(0.0) ? _811 : (isnan(_811) ? 0.0 : max(_811, 0.0));
    r2.x = isnan(1.0) ? _1021 : (isnan(_1021) ? 1.0 : min(_1021, 1.0));
    r0.w *= r2.x;
    r0.w = r2.y * r0.w;
    vec3 _831 = r0.www * cb13_0._m0[6u].xyz;
    r2 = vec4(_831.x, _831.y, _831.z, r2.w);
    vec3 _840 = texture(SPIRV_Cross_Combinedt1s1, v1.xyxx.xy).xyz;
    r4 = vec4(_840.x, _840.y, _840.z, r4.w);
    vec3 _849 = fma(r1.xyz, r4.xyz, r2.xyz);
    r1 = vec4(_849.x, _849.y, _849.z, r1.w);
    vec3 _858 = fma(r0.xyz, r1.www, r1.xyz);
    r0 = vec4(_858.x, _858.y, _858.z, r0.w);
    vec3 _865 = r3.www * r0.xyz;
    r3 = vec4(_865.x, _865.y, _865.z, r3.w);
    r0 = r3 * vec4(0.3333333432674407958984375);
    r1.x = uintBitsToFloat((0.996078431606292724609375 >= r3.w) ? 4294967295u : 0u);
    r0 = mix(r3, r0, notEqual(floatBitsToUint(r1.xxxx), uvec4(0u)));
    r1 = fma(r0, cb0_0._m0[7u].wwww, -r0);
    r0 = fma(cb1_0._m0[8u].zzzz, r1, r0);
    o0 = mix(mix(max(r0, vec4(0.0)), vec4(0.0), isnan(r0)), r0, isnan(vec4(0.0)));
}


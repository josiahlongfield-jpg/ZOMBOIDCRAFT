#version 450

layout(binding = 0, std140) uniform cb3_struct
{
    vec4 _m0[3];
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

uniform sampler2D SPIRV_Cross_Combinedt1s1;
uniform sampler2D SPIRV_Cross_Combinedt2s2;
uniform sampler2D SPIRV_Cross_Combinedt4s4;
uniform sampler2D SPIRV_Cross_Combinedt0s0;

layout(location = 1) in vec4 v1;
layout(location = 2) in vec3 v2;
layout(location = 3) in vec3 v3;
layout(location = 4) in vec3 v4;
layout(location = 5) in vec3 v5;
layout(location = 0) out vec4 o0;

void main()
{
    vec4 r0 = texture(SPIRV_Cross_Combinedt1s1, v1.xyxx.xy).wxyz;
    vec4 r1;
    r1.x = r0.x + (-cb2_0_1._m0[7u].x);
    r1.x = uintBitsToFloat((r1.x < 0.0) ? 4294967295u : 0u);
    if (floatBitsToUint(r1.x) != 0u)
    {
        discard;
    }
    vec2 _104 = texture(SPIRV_Cross_Combinedt2s2, v1.xyxx.xy).xy;
    r1 = vec4(_104.x, _104.y, r1.z, r1.w);
    vec3 _110 = r1.yyy * v3;
    vec4 r2;
    r2 = vec4(_110.x, _110.y, _110.z, r2.w);
    vec3 _119 = fma(r1.xxx, v4, r2.xyz);
    r2 = vec4(_119.x, _119.y, _119.z, r2.w);
    r1.x = dot(r1.xy, -r1.xy);
    float _132 = r1.x + 1.0;
    float _767 = isnan(0.0) ? _132 : (isnan(_132) ? 0.0 : max(_132, 0.0));
    r1.x = isnan(1.0) ? _767 : (isnan(_767) ? 1.0 : min(_767, 1.0));
    r1.x = sqrt(r1.x);
    vec3 _144 = fma(r1.xxx, v2, r2.xyz);
    r1 = vec4(_144.x, _144.y, _144.z, r1.w);
    r1.w = dot(r1.xyz, r1.xyz);
    r1.w = inversesqrt(r1.w);
    vec3 _161 = r1.www * r1.xyz;
    r1 = vec4(_161.x, _161.y, _161.z, r1.w);
    vec3 _167 = r1.zxy * v3;
    r2 = vec4(_167.x, _167.y, _167.z, r2.w);
    vec3 _177 = fma(v3.zxy, r1.xyz, -r2.xyz);
    r2 = vec4(_177.x, _177.y, _177.z, r2.w);
    vec3 _184 = r1.zxy * r2.xyz;
    vec4 r3;
    r3 = vec4(_184.x, _184.y, _184.z, r3.w);
    vec3 _194 = fma(r1.yzx, r2.yzx, -r3.xyz);
    r2 = vec4(_194.x, _194.y, _194.z, r2.w);
    r1.w = dot(r2.xyz, v3);
    r2.w = uintBitsToFloat((0.0 < r1.w) ? 4294967295u : 0u);
    r1.w = uintBitsToFloat((r1.w < 0.0) ? 4294967295u : 0u);
    r1.w = intBitsToFloat((-floatBitsToInt(r2.w)) + floatBitsToInt(r1.w));
    r1.w = float(floatBitsToInt(r1.w));
    vec2 _239 = fma(v1.xy, cb11_0._m0[4u].xy, cb11_0._m0[4u].zw);
    r3 = vec4(_239.x, _239.y, r3.z, r3.w);
    vec2 _248 = texture(SPIRV_Cross_Combinedt4s4, r3.xyxx.xy).xy;
    r3 = vec4(_248.x, _248.y, r3.z, r3.w);
    r2.w = r3.y + cb13_0._m0[1u].z;
    r3.x *= cb13_0._m0[1u].x;
    vec3 _272 = r3.xxx * cb13_0._m0[2u].xyz;
    r3 = vec4(_272.x, _272.y, _272.z, r3.w);
    vec3 _279 = r1.xyz * r2.www;
    vec4 r4;
    r4 = vec4(_279.x, _279.y, _279.z, r4.w);
    vec3 _288 = fma(r2.xyz, r1.www, r4.xyz);
    r2 = vec4(_288.x, _288.y, _288.z, r2.w);
    r1.w = dot(r2.xyz, r2.xyz);
    r1.w = inversesqrt(r1.w);
    vec3 _305 = r1.www * r2.xyz;
    r2 = vec4(_305.x, _305.y, _305.z, r2.w);
    r1.w = dot(v5, v5);
    r1.w = sqrt(r1.w);
    vec3 _319 = v5 / r1.www;
    r4 = vec4(_319.x, _319.y, _319.z, r4.w);
    vec3 _328 = (-r4.xyz) + cb2_0_1._m0[4u].xyz;
    vec4 r5;
    r5 = vec4(_328.x, _328.y, _328.z, r5.w);
    r1.w = dot(r5.xyz, r5.xyz);
    r1.w = inversesqrt(r1.w);
    vec3 _345 = r1.www * r5.xyz;
    r5 = vec4(_345.x, _345.y, _345.z, r5.w);
    r1.w = dot(r5.xyz, r2.xyz);
    r1.w = fma(-r1.w, r1.w, 1.0);
    r1.w = sqrt(r1.w);
    r1.w = log2(r1.w);
    r1.w *= cb13_0._m0[1u].y;
    r1.w = exp2(r1.w);
    vec3 _386 = (-r4.xyz) + cb2_0_1._m0[5u].xyz;
    r5 = vec4(_386.x, _386.y, _386.z, r5.w);
    vec3 _396 = (-r4.xyz) + cb2_0_1._m0[6u].xyz;
    r4 = vec4(_396.x, _396.y, _396.z, r4.w);
    r2.w = dot(r5.xyz, r5.xyz);
    r2.w = inversesqrt(r2.w);
    vec3 _413 = r2.www * r5.xyz;
    r5 = vec4(_413.x, _413.y, _413.z, r5.w);
    r2.w = dot(r5.xyz, r2.xyz);
    r2.w = fma(-r2.w, r2.w, 1.0);
    r2.w = sqrt(r2.w);
    r2.w = log2(r2.w);
    r2.w *= cb13_0._m0[1u].y;
    r2.w = exp2(r2.w);
    r2.w *= r3.y;
    r1.w = fma(r3.x, r1.w, r2.w);
    r2.w = dot(r4.xyz, r4.xyz);
    r2.w = inversesqrt(r2.w);
    vec3 _476 = r2.www * r4.xyz;
    r3 = vec4(_476.x, _476.y, r3.z, _476.z);
    r2.x = dot(r3.xyw, r2.xyz);
    r2.x = fma(-r2.x, r2.x, 1.0);
    r2.x = sqrt(r2.x);
    r2.x = log2(r2.x);
    r2.x *= cb13_0._m0[1u].y;
    r2.x = exp2(r2.x);
    r1.w = fma(r3.z, r2.x, r1.w);
    float _524 = dot(cb2_0_1._m0[4u].xyz, r1.xyz);
    float _778 = isnan(0.0) ? _524 : (isnan(_524) ? 0.0 : max(_524, 0.0));
    r2.x = isnan(1.0) ? _778 : (isnan(_778) ? 1.0 : min(_778, 1.0));
    r2.x *= cb13_0._m0[2u].x;
    r2.x *= cb11_0._m0[0u].x;
    r2 = vec4(r2.x, vec2(0.0).x, r2.z, vec2(0.0).y);
    vec2 _549 = r2.xy + cb11_0._m0[0u].zw;
    r2 = vec4(_549.x, _549.y, r2.z, r2.w);
    vec3 _558 = textureLod(SPIRV_Cross_Combinedt0s0, r2.xyxx.xy, 0.0).xyz;
    r3 = vec4(_558.x, _558.y, _558.z, r3.w);
    vec3 _567 = fma(-r3.xyz, r3.xyz, vec3(1.0));
    r3 = vec4(_567.x, _567.y, _567.z, r3.w);
    vec3 _573 = (-r3.xyz) + vec3(1.0);
    r3 = vec4(_573.x, _573.y, _573.z, r3.w);
    vec3 _579 = (-r3.xyz) + vec3(1.0);
    r3 = vec4(_579.x, _579.y, _579.z, r3.w);
    float _587 = dot(cb2_0_1._m0[5u].xyz, r1.xyz);
    float _789 = isnan(0.0) ? _587 : (isnan(_587) ? 0.0 : max(_587, 0.0));
    r2.x = isnan(1.0) ? _789 : (isnan(_789) ? 1.0 : min(_789, 1.0));
    float _595 = dot(cb2_0_1._m0[6u].xyz, r1.xyz);
    float _800 = isnan(0.0) ? _595 : (isnan(_595) ? 0.0 : max(_595, 0.0));
    r1.x = isnan(1.0) ? _800 : (isnan(_800) ? 1.0 : min(_800, 1.0));
    r1.x *= cb13_0._m0[2u].z;
    r1.x *= cb11_0._m0[0u].x;
    r1.z = r2.x * cb13_0._m0[2u].y;
    r2.z = r1.z * cb11_0._m0[0u].x;
    vec2 _631 = r2.zw + cb11_0._m0[0u].zw;
    r2 = vec4(_631.x, _631.y, r2.z, r2.w);
    vec3 _640 = textureLod(SPIRV_Cross_Combinedt0s0, r2.xyxx.xy, 0.0).xyz;
    r2 = vec4(_640.x, _640.y, _640.z, r2.w);
    vec3 _648 = fma(-r2.xyz, r2.xyz, vec3(1.0));
    r2 = vec4(_648.x, _648.y, _648.z, r2.w);
    vec3 _656 = fma(-r3.xyz, r2.xyz, vec3(1.0));
    r2 = vec4(_656.x, _656.y, _656.z, r2.w);
    vec3 _662 = (-r2.xyz) + vec3(1.0);
    r2 = vec4(_662.x, _662.y, _662.z, r2.w);
    r1.y = 0.0;
    vec2 _671 = r1.xy + cb11_0._m0[0u].zw;
    r1 = vec4(_671.x, _671.y, r1.z, r1.w);
    vec3 _680 = textureLod(SPIRV_Cross_Combinedt0s0, r1.xyxx.xy, 0.0).xyz;
    r1 = vec4(_680.x, _680.y, _680.z, r1.w);
    vec3 _688 = fma(-r1.xyz, r1.xyz, vec3(1.0));
    r1 = vec4(_688.x, _688.y, _688.z, r1.w);
    vec3 _696 = fma(-r2.xyz, r1.xyz, vec3(1.0));
    r1 = vec4(_696.x, _696.y, _696.z, r1.w);
    vec3 _705 = fma(r0.yzw, r1.xyz, vec3(-1.0));
    r2 = vec4(_705.x, _705.y, _705.z, r2.w);
    vec3 _712 = r0.yzw * r1.xyz;
    r0 = vec4(r0.x, _712.x, _712.y, _712.z);
    float _811 = isnan(0.0) ? r0.x : (isnan(r0.x) ? 0.0 : max(r0.x, 0.0));
    r0.x = isnan(1.0) ? _811 : (isnan(_811) ? 1.0 : min(_811, 1.0));
    r3.w = r0.x * cb13_0._m0[0u].w;
    vec3 _731 = fma(cb13_0._m0[1u].www, r2.xyz, vec3(1.0));
    r1 = vec4(_731.x, _731.y, _731.z, r1.w);
    vec3 _740 = fma(r1.www, r1.xyz, r0.yzw);
    r3 = vec4(_740.x, _740.y, _740.z, r3.w);
    r0 = fma(r3, cb0_0._m0[7u].wwww, -r3);
    r0 = fma(cb1_0._m0[8u].zzzz, r0, r3);
    o0 = mix(mix(max(r0, vec4(0.0)), vec4(0.0), isnan(r0)), r0, isnan(vec4(0.0)));
}


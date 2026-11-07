#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 ScreenSize;
uniform float Time;
uniform float Intensity;

in vec2 localUv;
in vec4 vertexColor;

out vec4 fragColor;

const float CORE = 0.43;
const float TAU = 6.28318530718;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 4; i++) {
        value += amplitude * noise(p);
        p = p * 2.03 + vec2(17.1, 9.2);
        amplitude *= 0.5;
    }
    return value;
}

// 与锻炉星云层同一套 GTO palette 常数
vec3 nebulaPalette(float t) {
    vec3 a = vec3(0.50, 0.50, 0.50);
    vec3 b = vec3(0.55, 0.48, 0.44);
    vec3 c = vec3(1.00, 1.00, 0.72);
    vec3 d = vec3(0.02, 0.28, 0.55);
    return clamp(a + b * cos(TAU * (c * t + d)), 0.0, 1.0);
}

vec3 saturateColor(vec3 color, float amount) {
    float mean = (color.r + color.g + color.b) / 3.0;
    return clamp(vec3(mean) + (color - vec3(mean)) * amount, 0.0, 1.0);
}

void main() {
    float r = length(localUv);
    if (r > 1.0) {
        discard;
    }
    float rr = max(r, 0.0001);
    float angle = atan(localUv.y, localUv.x);

    // ★ 用笛卡尔坐标取样噪声，不要用 (rr, angle)：atan 在 -X 轴（左半边）从 +π 跳到 -π，
    //   会沿着那条水平线留下一条明显的接缝。localUv 本身没有这个跳变。
    float n = fbm(localUv * 6.0 + vec2(Time * -0.35, Time * 0.12));
    float n2 = fbm(localUv * 13.0 + vec2(Time * 0.20, Time * -0.22));
    float coreR = CORE + (n - 0.5) * 0.025;

    float hue = Time * 0.09;
    float phase = angle / TAU + 0.62 * rr + 0.35 * n;
    float phase2 = angle / TAU - 0.35 * rr + 0.28 * n2;
    vec3 col1 = nebulaPalette(phase + hue);
    vec3 col2 = nebulaPalette(phase2 + hue * 1.25 + 0.22);
    vec3 nebula = mix(col1, col2, 0.35 + 0.5 * n2);
    // phase 在 -X 轴会跳 1；必须用周期为 1 的整数倍频率，否则又是一条接缝。
    float vein = clamp(sin(phase * TAU * 6.0 + n * 9.0) * 0.5 + 0.5, 0.0, 1.0);
    nebula = saturateColor(nebula, 1.45);
    nebula *= 0.55 + 0.65 * vein;
    nebula = clamp(nebula * 0.95, 0.0, 1.0);

    vec2 screenUv = gl_FragCoord.xy / ScreenSize;
    vec2 radial = localUv / rr;
    vec2 tangent = vec2(-radial.y, radial.x);
    float lensFade = 1.0 - smoothstep(0.55, 0.95, r);
    float bend = (0.060 * exp(-max(r - coreR, 0.0) * 3.0) + 0.010 * (1.0 - r)) * lensFade;
    vec2 offset = radial * bend + tangent * bend * 0.55 * sin(Time * 0.6 + rr * 9.0);
    vec3 scene = texture(DiffuseSampler, clamp(screenUv + offset, vec2(0.002), vec2(0.998))).rgb;

    vec2 texel = 1.0 / ScreenSize;
    vec3 blur = scene
            + texture(DiffuseSampler, clamp(screenUv + vec2( 4.0, 0.0) * texel, vec2(0.002), vec2(0.998))).rgb
            + texture(DiffuseSampler, clamp(screenUv + vec2(-4.0, 0.0) * texel, vec2(0.002), vec2(0.998))).rgb
            + texture(DiffuseSampler, clamp(screenUv + vec2(0.0,  4.0) * texel, vec2(0.002), vec2(0.998))).rgb
            + texture(DiffuseSampler, clamp(screenUv + vec2(0.0, -4.0) * texel, vec2(0.002), vec2(0.998))).rgb;
    blur *= 0.2;
    vec3 warpedScene = mix(scene, blur, smoothstep(0.32, 0.85, r));

    float spiral = 0.5 + 0.5 * sin(angle * 6.0 + log(rr) * 11.0 - Time * 2.2 + n * 4.5);
    float inner = smoothstep(coreR - 0.12, coreR + 0.25, r);
    float outerEdge = 0.72 + (n2 - 0.5) * 0.10;
    float outer = 1.0 - smoothstep(0.28, outerEdge, r);
    float disk = inner * outer * (0.22 + 0.78 * spiral)
            * (0.35 + 0.65 * exp(-max(r - coreR, 0.0) * 2.0))
            * (0.55 + 0.45 * n2);

    float photonR = coreR + 0.060 + 0.020 * sin(angle * 5.0 + Time * 0.8) + 0.015 * (n - 0.5);
    float photonDelta = (r - photonR) / 0.050;
    float photon = exp(-photonDelta * photonDelta) * (0.50 + 0.50 * n2);

    float halo = smoothstep(coreR * 0.85, coreR + 0.30, r) * (1.0 - smoothstep(0.12, 0.98, r));
    halo = pow(halo, 1.2) * (0.08 + 0.07 * n);

    float coreMask = 1.0 - smoothstep(coreR - 0.080, coreR + 0.060, r);

    vec3 color = nebula;
    color = mix(color, vec3(1.0, 0.95, 0.85), photon * 0.55);
    color *= 0.65 + 0.75 * disk + 0.45 * halo;
    color += warpedScene * 0.10 * halo;
    color = clamp(color, 0.0, 1.2);
    color = mix(color, vec3(0.0), coreMask);

    float edgeFade = 1.0 - smoothstep(0.12, 1.0, r);
    edgeFade = pow(edgeFade, 1.30);
    edgeFade *= 0.88 + 0.12 * n2;

    float glowAlpha = max(disk * 0.88, max(photon * 0.95, halo * 0.72));
    glowAlpha *= edgeFade * Intensity * vertexColor.a;
    float alpha = max(coreMask, glowAlpha);
    if (alpha < 0.003) {
        discard;
    }
    fragColor = vec4(color, alpha);
}

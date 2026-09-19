#ifdef GL_ES
precision mediump float;
#endif

#extension GL_OES_standard_derivatives : enable

uniform float time;
uniform vec2 mouse;
uniform vec2 resolution;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash(i + vec2(0, 0)), hash(i + vec2(1, 0)), f.x),
        mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x),
        f.y
    );
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    mat2 rot = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.5));
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = rot * p * 2.0 + mouse * 0.08;
        a *= 0.5;
    }
    return v;
}

void main(void) {
    vec2 uv = (gl_FragCoord.xy - 0.5 * resolution.xy) / min(resolution.x, resolution.y);
    float t = time * 0.12;

    float n = fbm(uv * 3.0 + t * 0.4);
    float r = fbm(uv * 2.0 + vec2(1.2, 3.4) + t * 0.25);
    float g = fbm(uv * 2.0 + vec2(5.6, 7.8) + t * 0.35);
    float b = fbm(uv * 2.0 + vec2(9.0, 1.1) + t * 0.45);

    vec3 purple = vec3(0.20, 0.02, 0.35);
    vec3 red    = vec3(0.25, 0.02, 0.05);
    vec3 dark   = vec3(0.01, 0.00, 0.02);
    vec3 accent = vec3(0.15, 0.01, 0.18);

    vec3 col = mix(dark, purple, n * 1.2);
    col = mix(col, red, r * 0.5);
    col = mix(col, accent, g * 0.4);
    col += vec3(b * 0.06, 0.0, b * 0.10);

    float vig = 1.0 - 0.5 * length(uv / 1.3);
    col *= vig;

    col = clamp(col, 0.0, 1.0);
    gl_FragColor = vec4(col, 1.0);
}

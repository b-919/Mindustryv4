// Synthwave test background for Background Customization > Shaders > Import Shader.
// Uniforms supplied by the game: u_time (seconds), u_resolution, u_scl, u_uv, u_uv2, u_texture.

#ifdef GL_ES
precision mediump float;
#endif

uniform vec2 u_resolution;
uniform float u_time;

const float PI = 3.14159265359;

// Grid tuning
const vec2 vanishing_point = vec2(0.5, 0.28);
const float depth_gradient = 5.0;
const float v_line_density = 50.0;
const float v_line_sin_cutoff = 0.9;
const float h_line_density = 50.0;
const float h_line_sin_cutoff = 0.9;
const float h_line_scroll_rate = -0.02;
const float horizon_y_uv = 0.32;
const float fade_horizon_y_uv = 0.78;

// Neon gradient
const vec3 neon_purple = vec3(0.35, 0.02, 0.95);
const vec3 neon_pink = vec3(1.0, 0.08, 0.62);

vec3 neonGradient(float y){
    return mix(neon_purple, neon_pink, smoothstep(horizon_y_uv, 1.0, y));
}

void main(){
    // UVs start at the top-left, so flip the screen-space Y.
    vec2 uv = gl_FragCoord.xy / max(u_resolution, vec2(1.0));
    uv.y = 1.0 - uv.y;

    vec2 vanishing_y_direction = normalize(vanishing_point - vec2(0.5, 0.0));
    vec2 vanishing_x_direction = vec2(vanishing_y_direction.y, -vanishing_y_direction.x);
    mat2 basis_change = mat2(vanishing_x_direction, vanishing_y_direction);

    vec2 displacement = basis_change * (uv - vanishing_point);
    float angle = atan(displacement.x / (displacement.y + 0.0001));
    float yangle = atan(depth_gradient * displacement.y);
    yangle = mod((yangle + h_line_scroll_rate * u_time), PI) + PI * 0.5;

    float v_wave = sin(v_line_density * angle);
    float h_wave = sin(2.0 * h_line_density * yangle);

    float v_presence = smoothstep(v_line_sin_cutoff, v_line_sin_cutoff + 0.02, v_wave);
    float h_presence = smoothstep(h_line_sin_cutoff, h_line_sin_cutoff + 0.02, h_wave);

    float v_glow = smoothstep(v_line_sin_cutoff - 0.15, v_line_sin_cutoff, v_wave) * 0.18;
    float h_glow = smoothstep(h_line_sin_cutoff - 0.15, h_line_sin_cutoff, h_wave) * 0.18;

    float lines = clamp(v_presence + h_presence, 0.0, 1.0);
    float glow = v_glow + h_glow;
    float plane = smoothstep(horizon_y_uv, fade_horizon_y_uv, uv.y);

    vec3 neon = neonGradient(uv.y);

    // Purple-to-pink sky above the grid.
    vec3 sky = mix(vec3(0.015, 0.0, 0.06), neon_pink * 0.32,
        smoothstep(0.0, horizon_y_uv, uv.y));

    // Bright neon band where the grid meets the horizon.
    float horizon = exp(-abs(uv.y - horizon_y_uv) * 42.0);

    vec3 color = sky;
    color += neon * horizon * 0.85;
    color += neon * (lines + glow) * plane * 1.25;

    // Extra purple-to-pink glow where the grid ends toward the foreground.
    float bottom = smoothstep(0.72, 1.0, uv.y);
    color = mix(color, color + neon * 0.35, bottom * 0.5);

    color *= 1.0 - length(uv - 0.5) * 0.18;

    gl_FragColor = vec4(color, 1.0);
}

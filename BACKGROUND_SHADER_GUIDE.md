# Background Shaders

This guide explains how to write a custom GLSL shader for the game's main menu background.

Custom shaders can be loaded in:

`Settings → Background Customization → Shaders → Import Shader`

The shader is used by `io.anuke.mindustry.graphics.MenuBackground` through `Shaders.CustomShader`, and only affects the main menu. It does not replace in-game rendering.

Example shader in this repo:

- `core/assets/synthwave_test.glsl`

---

## 1. File requirements

Supported extensions:

- `.fragment`
- `.frag`
- `.glsl`

Imported shaders are copied into the game data folder under `background/`. The currently selected shader is stored in settings.

The shader must be a **fragment shader**. There is no custom vertex-shader support _yet_. The game pairs your fragment source with `core/assets/shaders/default.vertex` for now.

You don't have to write a `#version` directive. The game compiles the shader as GLSL ES compatible code, so keep it compatible with GLSL ES 1.00 / OpenGL ES 2.0.

Start every shader with:

```glsl
#ifdef GL_ES
precision mediump float;
#endif
```

Avoid modern GLSL features such as `in`/`out`, `texture()`, uniform blocks, dynamic-length loops and geometry shaders. Use `gl_FragColor`, `texture2D`, `attribute`-era syntax and constant loop bounds.

---

## 2. Entry point and output

The fragment shader must define `main()` and write to `gl_FragColor`:

```glsl
void main(){
    gl_FragColor = vec4(1.0, 0.0, 0.5, 1.0);
}
```

The shader is drawn as a full-screen quad. If the output alpha is `1.0`, it fully replaces the background. If alpha is lower, the normal dark menu backdrop can show through.

---

## 3. Available uniforms

The custom shader receives these uniforms:

| Uniform | Type | Meaning |
|---|---|---|
| `u_time` | `float` | Seconds since the shader was loaded. Use this for animation. |
| `u_resolution` | `vec2` | Screen width and height in pixels. |
| `u_scl` | `float` | UI scale factor. Useful for pixel-size calculations. |
| `u_uv`, `u_uv2` | `vec2` | UV corners of the blank sprite region used by the full-screen quad. Usually not useful for background effects. |
| `u_texture` | `sampler2D` | The blank white UI texture. Not a screen capture. |

The built-in `default.vertex` also provides:

```glsl
varying vec4 v_color;
varying vec2 v_texCoord;
```

`v_texCoord` is the atlas UV of the blank sprite, **not** a normalized 0..1 screen UV. For background effects, calculate screen UV from `gl_FragCoord`.

---

## 4. Screen coordinates

`gl_FragCoord` is in pixels and its origin is the bottom-left corner.

Normalized bottom-up UV:

```glsl
vec2 uv = gl_FragCoord.xy / u_resolution;
```

Normalized top-down UV (matches common shader examples and other UVs styles like Godot ones):

```glsl
vec2 uv = gl_FragCoord.xy / u_resolution;
uv.y = 1.0 - uv.y;
```

Aspect-correct coordinates:

```glsl
vec2 uv = gl_FragCoord.xy / u_resolution;
uv.x *= u_resolution.x / u_resolution.y;
```

---

## 5. Animated Example

```glsl
#ifdef GL_ES
precision mediump float;
#endif

uniform vec2 u_resolution;
uniform float u_time;

void main(){
    vec2 uv = gl_FragCoord.xy / max(u_resolution, vec2(1.0));
    uv.y = 1.0 - uv.y;

    float wave = sin(uv.x * 10.0 + u_time) * 0.5 + 0.5;

    vec3 color = mix(vec3(0.05, 0.02, 0.2), vec3(1.0, 0.1, 0.6), wave);
    gl_FragColor = vec4(color, 1.0);
}
```

---

## 6. Structure

A typical background shader has:

1. Precision setup.
2. Uniform declarations.
3. Constants and tuning values.
4. Small helper functions.
5. `main()`:
   - build UVs,
   - animate using `u_time`,
   - build the color,
   - write `gl_FragColor`.

Keep constants at the top so the shader is easy to modify.<br>
(still you can leave them whatever you want)

Example:

```glsl
#ifdef GL_ES
precision mediump float;
#endif

uniform vec2 u_resolution;
uniform float u_time;

const vec3 colorA = vec3(0.03, 0.0, 0.12);
const vec3 colorB = vec3(1.0, 0.08, 0.62);

float hash(vec2 p){
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

void main(){
    vec2 uv = gl_FragCoord.xy / max(u_resolution, vec2(1.0));
    uv.y = 1.0 - uv.y;

    vec3 color = mix(colorA, colorB, uv.y * 0.7);

    vec2 cell = floor(uv * 120.0);
    float star = step(0.985, hash(cell));
    color += star * 0.6;

    gl_FragColor = vec4(color, 1.0);
}
```

---

## 7. Miscellanea

- Imported shaders live in the game data directory.
- The selected custom shader is saved in settings.
- Shader files are not compiled until selected.
- If compilation fails, the dialog reports an error and the default menu shader is used.
- Importing a file with the same name overwrites the previous copy.

---

## 8. Performance

This shader runs every frame across the whole screen.

- Keep per-pixel work cheap.
- Avoid large nested loops.
- Prefer `mediump` unless you need more precision.
- Cache values in local variables where possible.
- Avoid sampling `u_texture` unless you actually need it.
- Remember that mobile GPUs are much slower than desktop GPUs.

---

<br><br><br><br><br><br><br><br><br><br><br><br><br><br><br><br><br><br><br><br><br><br><br> 
wanted something in specific?
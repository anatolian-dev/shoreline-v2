#version 330

#define TAU 6.28318530718

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform ShorelineOutlineConfig {
    vec4 u_GradientColor;
    vec2 u_Resolution;
    float u_ShaderTime;
    float u_Width;
    float u_FillAlpha;
    float u_GradientFactor;
    float u_FlowSpeed;
    float u_FlowFactor;
    float u_LiquidIntensity;
    float u_LiquidFactor;
    float u_OutlineAlpha;
    float u_FillMode;
};

in vec2 texCoord;

out vec4 fragColor;

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

vec4 getFill(vec3 centerColor)
{
    if (int(u_FillMode) == 1)
    {
        float time = u_ShaderTime / 5.0;
        float distance = sqrt(gl_FragCoord.x * gl_FragCoord.x + gl_FragCoord.y * gl_FragCoord.y) + time;
        distance = distance / u_GradientFactor;
        distance = ((sin(distance) + 1.0) / 2.0);
        float j = 1.0 - distance;
        float r = centerColor.r * distance + u_GradientColor.r * j;
        float g = centerColor.g * distance + u_GradientColor.g * j;
        float b = centerColor.b * distance + u_GradientColor.b * j;
        float a = u_FillAlpha * distance + u_GradientColor.a * j;
        return vec4(r, g, b, a);
    }

    if (int(u_FillMode) == 2)
    {
        float time = u_ShaderTime / 500.0;
        vec2 uv = (2.0 * gl_FragCoord.xy - u_Resolution.xy) / min(u_Resolution.x, u_Resolution.y);
        for (float i = 1.0; i < u_FlowSpeed; i++)
        {
            uv.x += u_FlowFactor / i * cos(i * 2.5 * uv.y + time);
            uv.y += u_FlowFactor / i * cos(i * 1.5 * uv.x + time);
        }

        return vec4(centerColor.r / abs(sin(time - uv.y - uv.x)), centerColor.g / abs(sin(time - uv.y - uv.x)), centerColor.b / abs(sin(time - uv.y - uv.x)), u_FillAlpha);
    }

    if (int(u_FillMode) == 3)
    {
        float time = u_ShaderTime / 1000.0;
        vec2 uv = gl_FragCoord.xy / u_Resolution.xy;
        vec2 p = mod(uv * TAU, TAU) - 250.0;

        vec2 i = vec2(p);
        float c = 1.0;
        float inten = u_LiquidIntensity / 1000.0;

        for (int n = 0; n < int(u_LiquidFactor); n++)
        {
            float t = time * (1.0 - (3.5 / float(n + 1)));
            i = p + vec2(cos(t - i.x) + sin(t + i.y), sin(t - i.y) + cos(t + i.x));
            c += 1.0 / length(vec2(p.x / (sin(i.x + t) / inten), p.y / (cos(i.y + t) / inten)));
        }

        c /= u_LiquidFactor;
        c = 1.17 - pow(c, 1.4);
        vec3 color = vec3(pow(abs(c), 8.0));
        color = clamp(color + centerColor, 0.0, 1.0);

        return vec4(color, u_FillAlpha);
    }

    if (int(u_FillMode) == 4)
    {
        float zoom = 1.0;
        vec2 uv = (gl_FragCoord.xy / u_Resolution.xy - 0.5) * zoom + 0.5;
        float theta = uv.x * 3.14159;
        float phi = uv.y * 3.14159 * 0.5;

        vec3 dir = vec3(
            cos(phi) * cos(theta),
            sin(phi),
            cos(phi) * sin(theta)
        );

        float time = u_ShaderTime / 750.0;
        float rot = time * 0.2;
        mat2 rotMat = mat2(cos(rot), -sin(rot), sin(rot), cos(rot));
        dir.xz = rotMat * dir.xz;

        float dist = length(dir.xy);
        float angle = atan(dir.y, dir.x);
        float spiral = sin(dist * 10.0 - angle * 3.0 - time * 2.0);

        float hue = fract(dist * 2.0 - time * 0.3 + angle / 6.28318);
        vec3 rainbowColor = hsv2rgb(vec3(hue, 0.8, 1.0));

        float rings = sin(dist * 20.0 - time * 3.0);
        rings = pow(max(0.0, rings), 3.0);

        vec3 finalColor = rainbowColor * (spiral * 0.3 + 0.7);
        finalColor += vec3(1.0) * rings * 0.5;

        float glow = exp(-dist * 3.0);
        finalColor += vec3(1.0, 0.9, 1.0) * glow * 0.5;
        return vec4(finalColor, u_FillAlpha);
    }

    return vec4(centerColor, u_FillAlpha);
}

void main()
{
    vec4 center = texture(InSampler, texCoord);

    if (center.a > 0.0)
    {
        fragColor = getFill(center.rgb);
        return;
    }

    vec2 oneTexel = 1.0 / InSize;
    vec4 neighbor = vec4(0.0);
    float maxDistSq = u_Width * u_Width;
    int r = int(ceil(u_Width));

    for (int x = -r; x <= r; x++)
    {
        for (int y = -r; y <= r; y++)
        {
            if (float(x * x + y * y) > maxDistSq) continue;

            vec4 offset = texture(InSampler, texCoord + vec2(float(x), float(y)) * oneTexel);
            if (offset.a > 0.0)
            {
                neighbor = offset;
                break;
            }
        }
        if (neighbor.a > 0.0) break;
    }

    if (neighbor.a > 0.0)
    {
        vec3 outlineRGB;
        if (int(u_FillMode) == 4)
        {
            outlineRGB = vec3(getFill(neighbor.rgb));
        }
        else
        {
            outlineRGB = neighbor.rgb;
        }

        fragColor = vec4(outlineRGB, u_OutlineAlpha);
    }
    else
    {
        fragColor = vec4(0.0);
    }
}

#version 330

#define TAU 6.28318530718

uniform sampler2D InSampler;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform ShorelineBloomConfig {
    vec4 u_GradientColor;
    vec2 u_Resolution;
    float u_ShaderTime;
    float u_Width;
    float u_GlowMultiplier;
    float u_FillAlpha;
    float u_GradientFactor;
    float u_FlowSpeed;
    float u_FlowFactor;
    float u_LiquidIntensity;
    float u_LiquidFactor;
    float u_OutlineAlpha;
    float u_GlowInside;
    float u_GlowQuality;
    float u_FillMode;
};

in vec2 texCoord;

out vec4 fragColor;

vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

vec4 getFill(vec3 centerColor, float alpha)
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
        float a = alpha * distance + u_GradientColor.a * j;
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

        return vec4(centerColor.r / abs(sin(time - uv.y - uv.x)), centerColor.g / abs(sin(time - uv.y - uv.x)), centerColor.b / abs(sin(time - uv.y - uv.x)), alpha);
    }

    if (int(u_FillMode) == 3)
    {
        float time = u_ShaderTime / 1000.0;
        vec2 uv = gl_FragCoord.xy / u_Resolution.xy;
        vec2 p = mod(uv * TAU, TAU) - 250.0;

        vec2 i = vec2(p);
        float c = 1.0;
        float inten = u_LiquidIntensity / 1000.0;

        for (int n = 0; n < floor(u_LiquidFactor); n++)
        {
        	float t = time * (1.0 - (3.5 / float(n + 1)));
        	i = p + vec2(cos(t - i.x) + sin(t + i.y), sin(t - i.y) + cos(t + i.x));
        	c += 1.0 / length(vec2(p.x / (sin(i.x + t) / inten), p.y / (cos(i.y + t) / inten)));
        }

        c /= floor(u_LiquidFactor);
        c = 1.17 - pow(c, 1.4);
        vec3 color = vec3(pow(abs(c), 8.0));
        color = clamp(color + centerColor, 0.0, 1.0);

        return vec4(color, alpha);
    }

    if (int(u_FillMode) == 4)
    {
        float zoom = 1;
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
        return vec4(finalColor, alpha);
    }

    return vec4(centerColor, alpha);
}

vec3 getSobelColor(vec2 uv, vec2 oneTexel)
{
    for (int r = 1; r <= int(u_GlowQuality) * int(u_Width); r += int(u_GlowQuality))
    {
        float rf = float(r);
        vec2 dx = vec2(oneTexel.x * rf, 0.0);
        vec2 dy = vec2(0.0, oneTexel.y * rf);

        vec4 s = texture(InSampler, uv - dx);
        if (s.a > 0.0) return s.rgb;
        s = texture(InSampler, uv + dx);
        if (s.a > 0.0) return s.rgb;
        s = texture(InSampler, uv - dy);
         if (s.a > 0.0) return s.rgb;
        s = texture(InSampler, uv + dy);
        if (s.a > 0.0) return s.rgb;

        vec2 od = vec2(dx.x, dy.y);
        s = texture(InSampler, uv + od);
        if (s.a > 0.0) return s.rgb;
        s = texture(InSampler, uv + vec2(od.x, -od.y));
        if (s.a > 0.0) return s.rgb;
        s = texture(InSampler, uv + vec2(-od.x, od.y));
        if (s.a > 0.0) return s.rgb;
        s = texture(InSampler, uv - od);
        if (s.a > 0.0) return s.rgb;
    }

    return vec3(0.0);
}

float blur(vec4 center, bool outline, vec2 oneTexel)
{
    if (u_Width == 0)
    {
        return 0.0;
    }

    int w = int(u_GlowQuality) * int(u_Width);
    float blurred = 0.0;

    blurred += sign(texture(InSampler, texCoord + oneTexel * vec2( float(w), 0)).a);
    blurred += sign(texture(InSampler, texCoord + oneTexel * vec2(-float(w), 0)).a);
    blurred += sign(texture(InSampler, texCoord + oneTexel * vec2( 0, float(w))).a);
    blurred += sign(texture(InSampler, texCoord + oneTexel * vec2( 0, -float(w))).a);

    blurred += sign(texture(InSampler, texCoord + oneTexel * vec2(float(w), float(w))).a);
    blurred += sign(texture(InSampler, texCoord + oneTexel * vec2(float(w), -float(w))).a);
    blurred += sign(texture(InSampler, texCoord + oneTexel * vec2(-float(w), float(w))).a);
    blurred += sign(texture(InSampler, texCoord + oneTexel * vec2(-float(w), -float(w))).a);

    if (int(u_Width) > 2 && blurred == 0.0)
    {
        return 0.0;
    }

    for (int x = -w; x <= w; x += int(u_GlowQuality))
    {
        for (int y = -w; y <= w; y += int(u_GlowQuality))
        {
            if (x == 0 && y == 0) continue;

            if ((abs(x) == w && abs(y) == w) ||
                (abs(x) == w && y == 0) ||
                (abs(y) == w && x == 0))
            {
                continue;
            }

            blurred += sign(texture(InSampler, texCoord + oneTexel * vec2(float(x), float(y))).a);
        }
    }

    return clamp(blurred / max(float(((int(u_Width) * int(u_Width)) + int(u_Width)) * 4), 1.0), 0.0, 1.0) * u_GlowMultiplier;
}

void main()
{
    vec2 oneTexel = 1.0 / InSize;
    vec4 center = texture(InSampler, texCoord);

    if (center.a > 0.0)
    {
        vec4 fill = getFill(center.rgb, u_FillAlpha);
        if (int(u_GlowInside) == 1)
        {
            vec4 outlineCol = vec4(center.rgb, u_OutlineAlpha);
            fragColor = mix(fill, outlineCol, u_GlowMultiplier - blur(fill, false, oneTexel));
        } else
        {
            fragColor = fill;
        }

        return;
    }

    float glow = blur(center, true, oneTexel);
    if (glow <= 0.0)
    {
        fragColor = vec4(0.0);
        return;
    }

    bool edge = false;
    int r = max(1, int(ceil(u_Width)));
    for (int x = -r; x <= r && !edge; ++x)
    {
        for (int y = -r; y <= r && !edge; ++y)
        {
            if (x == 0 && y == 0) continue;

            if (texture(InSampler, texCoord + vec2(float(x), float(y)) * oneTexel).a > 0.0)
            {
                edge = true;
            }
        }
    }

    vec3 outlineRGB;
    if (int(u_FillMode) == 4)
    {
        outlineRGB = vec3(getFill(center.rgb, u_OutlineAlpha));
    }
    else
    {
        outlineRGB = getSobelColor(texCoord, oneTexel);
    }

    if (outlineRGB.r + outlineRGB.g + outlineRGB.b == 0.0)
    {
        int w = max(1, int(u_GlowQuality) * int(u_Width));
        vec2 dx = vec2(oneTexel.x * float(w), 0.0);
        vec2 dy = vec2(0.0, oneTexel.y * float(w));

        vec4 s = texture(InSampler, texCoord + dx);
        if (s.a > 0.0) outlineRGB = s.rgb;
        s = texture(InSampler, texCoord - dx);
        if (s.a > 0.0) outlineRGB = s.rgb;
        s = texture(InSampler, texCoord + dy);
        if (s.a > 0.0) outlineRGB = s.rgb;
        s = texture(InSampler, texCoord - dy);
        if (s.a > 0.0) outlineRGB = s.rgb;
    }

    if (edge)
    {
        fragColor = vec4(outlineRGB, u_OutlineAlpha);
    }
    else
    {
        fragColor = vec4(outlineRGB, glow * u_OutlineAlpha);
    }
}

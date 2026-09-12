package net.shoreline.client.api.font;

import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.chars.Char2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import lombok.Getter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.shoreline.client.ShorelineMod;
import net.shoreline.client.impl.module.client.FontModule;
import net.shoreline.client.impl.module.client.SocialsModule;
import net.shoreline.client.impl.module.client.ThemeModule;
import net.shoreline.client.impl.module.misc.NameProtectModule;
import net.shoreline.client.impl.render.ColorUtil;
import org.apache.commons.lang3.mutable.Mutable;
import org.apache.commons.lang3.mutable.MutableObject;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix4f;

import java.awt.*;
import java.io.Closeable;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

public final class FontRenderer implements Closeable
{
    private static final Function<Identifier, RenderLayer> FONT_LAYERS = Util.memoize(
            id -> RenderLayer.of(
                    "shoreline_font/" + id.getNamespace() + "/" + id.getPath().replace('/', '_'),
                    RenderSetup.builder(RenderPipelines.GUI_TEXTURED).texture("Sampler0", id).translucent().build()));

    private static final String HEX_CHARS = "0123456789abcdef";

    @Getter
    private String name;
    private Font font;
    private final float size;

    private int scale;
    private int lastScale;

    private final ObjectList<GlyphCache> caches = new ObjectArrayList<>();
    private final Char2ObjectArrayMap<Glyph> glyphs = new Char2ObjectArrayMap<>();
    private final Map<Identifier, ObjectList<CharLocation>> cache = new Object2ObjectOpenHashMap<>();

    public FontRenderer(InputStream inputStream, float size)
    {
        try
        {
            this.font = Font.createFont(Font.TRUETYPE_FONT, inputStream);
        }
        catch (Throwable t)
        {
            t.printStackTrace();
            this.font = new Font("Verdana", Font.PLAIN, Math.round(size));
        }

        this.size = size;
        createFont(font, size);
    }

    public FontRenderer(String name, float size)
    {
        this.name = name;
        this.font = new Font(name, Font.PLAIN, Math.round(size));
        this.size = size;
        createFont(font, size);
    }

    public static String stripControlCodes(String text)
    {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length() && text.charAt(i + 1) == 'j')
            {
                i += 1;
                i += Math.min(8, text.length() - (i + 1));
                continue;
            }

            if (c == '§' && i + 1 < text.length())
            {
                i++;
                continue;
            }

            out.append(c);
        }

        return out.toString();
    }

    private void createFont(Font font, float size)
    {
        this.lastScale = (int) MinecraftClient.getInstance().getWindow().getScaleFactor();
        this.scale = this.lastScale;
        this.font = font.deriveFont(size * scale);
    }

    public void drawStringWithShadow(MatrixStack stack, String text, double x, double y, int color)
    {
        drawString(stack, text, x + 0.5, y + 0.5, color, true);
        drawString(stack, text, x, y, color, false);
    }

    public void drawString(MatrixStack stack, String text, double x, double y, int color)
    {
        drawString(stack, text, x, y, color, false);
    }

    public void drawStringWithShadow(DrawContext context, String text, double x, double y, int color)
    {
        drawString(context, text, x + 0.5, y + 0.5, color, true);
        drawString(context, text, x, y, color, false);
    }

    public void drawString(DrawContext context, String text, double x, double y, int color)
    {
        drawString(context, text, x, y, color, false);
    }

    private void drawString(MatrixStack stack, String text, double x, double y, int color, boolean shadow)
    {
        float brightness = shadow ? 0.25f : 1.0f;
        float alpha = alphaOf(color);
        text = validate(text);

        stack.push();
        stack.translate((float) x, (float) y - 3.0f, 0.0f);
        stack.scale(1.0f / scale, 1.0f / scale, 1.0f);

        synchronized (cache)
        {
            layout(text, color, brightness);
            Matrix4f pose = stack.peek().getPositionMatrix();
            for (Map.Entry<Identifier, ObjectList<CharLocation>> entry : cache.entrySet())
            {
                drawGlyphs(pose, entry.getKey(), entry.getValue(), alpha);
            }

            cache.clear();
        }

        stack.pop();
    }

    private void drawString(DrawContext context, String text, double x, double y, int color, boolean shadow)
    {
        float brightness = shadow ? 0.25f : 1.0f;
        float alpha = alphaOf(color);
        text = validate(text);

        Matrix3x2fStack stack = context.getMatrices();
        stack.pushMatrix();
        stack.translate((float) x, (float) y - 3.0f);
        stack.scale(1.0f / scale, 1.0f / scale);

        synchronized (cache)
        {
            layout(text, color, brightness);
            for (Map.Entry<Identifier, ObjectList<CharLocation>> entry : cache.entrySet())
            {
                drawGlyphs(context, entry.getKey(), entry.getValue(), alpha);
            }

            cache.clear();
        }

        stack.popMatrix();
    }

    private String validate(String text)
    {
        if (NameProtectModule.INSTANCE.isEnabled())
        {
            String username = MinecraftClient.getInstance().getSession().getUsername();
            if (text.contains(username))
            {
                text = text.replace(username, NameProtectModule.INSTANCE.getAliasConfig().getValue());
            }
        }

        if ((int) MinecraftClient.getInstance().getWindow().getScaleFactor() != lastScale)
        {
            close();
            createFont(font, size);
        }

        return text;
    }

    private static float alphaOf(int color)
    {
        int alpha = (color >>> 24) & 0xff;
        return alpha == 0 ? 1.0f : alpha / 255.0f;
    }

    private void layout(String text, int color, float brightness)
    {
        float r = ((color >> 16) & 0xff) / 255.0f * brightness;
        float g = ((color >> 8) & 0xff) / 255.0f * brightness;
        float b = (color & 0xff) / 255.0f * brightness;

        float r2 = r;
        float g2 = g;
        float b2 = b;
        float xOffset = 0;
        boolean formatting = false;

        char[] chars = text.toCharArray();
        glyphs.clear();

        Mutable<Integer> index = new MutableObject<>(0);
        for (; index.getValue() < chars.length; index.setValue(index.getValue() + 1))
        {
            char c = chars[index.getValue()];
            if (formatting)
            {
                formatting = false;
                if (c == 'r')
                {
                    r2 = r;
                    g2 = g;
                    b2 = b;
                }
                else
                {
                    int[] col = ColorUtil.getRGBColorValues(getColorFromCode(text, index, c));
                    r2 = col[0] / 255.0f * brightness;
                    g2 = col[1] / 255.0f * brightness;
                    b2 = col[2] / 255.0f * brightness;
                }

                continue;
            }

            if (c == '§')
            {
                formatting = true;
                continue;
            }

            Glyph glyph = glyphs.computeIfAbsent(c, this::getGlyphFromChar);
            if (glyph == null)
            {
                continue;
            }

            if (glyph.value() != ' ')
            {
                cache.computeIfAbsent(glyph.owner().getId(), id -> new ObjectArrayList<>())
                        .add(new CharLocation(xOffset, 0, r2, g2, b2, glyph));
            }

            xOffset += glyph.width();
        }
    }

    private void drawGlyphs(DrawContext context, Identifier identifier, List<CharLocation> locations, float alpha)
    {
        for (CharLocation location : locations)
        {
            Glyph glyph = location.glyph();
            GlyphCache owner = glyph.owner();
            int width = glyph.width();
            int height = glyph.height();
            if (width <= 0 || height <= 0)
            {
                continue;
            }

            context.drawTexture(
                    RenderPipelines.GUI_TEXTURED,
                    identifier,
                    Math.round(location.x()),
                    Math.round(location.y()),
                    glyph.textureWidth(),
                    glyph.textureHeight(),
                    width,
                    height,
                    width,
                    height,
                    owner.getWidth(),
                    owner.getHeight(),
                    packArgb(location.r(), location.g(), location.b(), alpha));
        }
    }

    private void drawGlyphs(Matrix4f pose, Identifier identifier, List<CharLocation> locations, float alpha)
    {
        RenderLayer layer = FONT_LAYERS.apply(identifier);
        BufferBuilder buffer = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        for (CharLocation location : locations)
        {
            Glyph glyph = location.glyph();
            GlyphCache owner = glyph.owner();
            float x = location.x();
            float y = location.y();
            int w = glyph.width();
            int h = glyph.height();
            float u1 = (float) glyph.textureWidth() / owner.getWidth();
            float v1 = (float) glyph.textureHeight() / owner.getHeight();
            float u2 = (float) (glyph.textureWidth() + w) / owner.getWidth();
            float v2 = (float) (glyph.textureHeight() + h) / owner.getHeight();

            buffer.vertex(pose, x, y + h, 0).texture(u1, v2).color(location.r(), location.g(), location.b(), alpha);
            buffer.vertex(pose, x + w, y + h, 0).texture(u2, v2).color(location.r(), location.g(), location.b(), alpha);
            buffer.vertex(pose, x + w, y, 0).texture(u2, v1).color(location.r(), location.g(), location.b(), alpha);
            buffer.vertex(pose, x, y, 0).texture(u1, v1).color(location.r(), location.g(), location.b(), alpha);
        }

        BuiltBuffer built = buffer.endNullable();
        if (built != null)
        {
            layer.draw(built);
        }
    }

    private static int packArgb(float r, float g, float b, float a)
    {
        return (clampByte(a) << 24) | (clampByte(r) << 16) | (clampByte(g) << 8) | clampByte(b);
    }

    private static int clampByte(float value)
    {
        return Math.clamp(Math.round(value * 255.0f), 0, 255);
    }

    public int getStringWidth(String text)
    {
        if (NameProtectModule.INSTANCE.isEnabled())
        {
            String username = MinecraftClient.getInstance().getSession().getUsername();
            if (text.contains(username))
            {
                text = text.replace(username, NameProtectModule.INSTANCE.getAliasConfig().getValue());
            }
        }

        float currentLine = 0;
        float maxPreviousLines = 0;
        boolean formatting = false;
        for (char c : stripControlCodes(text).toCharArray())
        {
            if (c == '\n')
            {
                maxPreviousLines = Math.max(currentLine, maxPreviousLines);
                currentLine = 0;
                continue;
            }

            if (formatting)
            {
                formatting = false;
                continue;
            }

            if (c == '§')
            {
                formatting = true;
                continue;
            }

            Glyph glyph = glyphs.computeIfAbsent(c, this::getGlyphFromChar);
            currentLine += glyph == null ? 0 : glyph.width() / (float) scale;
        }

        return Math.round(Math.max(currentLine, maxPreviousLines));
    }

    public float getFontHeight()
    {
        return size;
    }

    public Glyph getGlyphFromChar(char c)
    {
        for (GlyphCache map : caches)
        {
            if (map.contains(c))
            {
                return map.getGlyph(c);
            }
        }

        int base = 256 * (int) Math.floor(c / 256.0);
        GlyphCache glyphCache = new GlyphCache((char) base, (char) (base + 256), font, getGlyphIdentifier(), 5,
                FontModule.INSTANCE.getAntiAlias().getValue(), FontModule.INSTANCE.getFractionalMetrics().getValue());

        caches.add(glyphCache);
        return glyphCache.getGlyph(c);
    }

    @Override
    public void close()
    {
        try
        {
            for (GlyphCache cache1 : caches)
            {
                cache1.clear();
            }

            caches.clear();
            glyphs.clear();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public Identifier getGlyphIdentifier()
    {
        return Identifier.of(ShorelineMod.MOD_ID, "font/storage/" + generateRandomHex(32));
    }

    public String generateRandomHex(int length)
    {
        StringBuilder hexString = new StringBuilder(length);
        for (int i = 0; i < length; i++)
        {
            hexString.append(HEX_CHARS.charAt(ThreadLocalRandom.current().nextInt(HEX_CHARS.length())));
        }

        return hexString.toString();
    }

    private int getColorFromCode(String str, Mutable<Integer> index, char code)
    {
        return switch (code)
        {
            case '0' -> Color.BLACK.getRGB();
            case '1' -> 0xff0000AA;
            case '2' -> 0xff00AA00;
            case '3' -> 0xff00AAAA;
            case '4' -> 0xffAA0000;
            case '5' -> 0xffAA00AA;
            case '6' -> 0xffFFAA00;
            case '7' -> 0xffAAAAAA;
            case '8' -> 0xff555555;
            case '9' -> 0xff5555FF;
            case 'a' -> 0xff55FF55;
            case 'b' -> 0xff55FFFF;
            case 'c' -> 0xffFF5555;
            case 'd' -> 0xffFF55FF;
            case 'e' -> 0xffFFFF55;
            case 'f' -> 0xffffffff;
            case 'g' -> ThemeModule.INSTANCE.getPrimaryColor().getRGB();
            case 'h' -> SocialsModule.INSTANCE.getFriendsColor().getRGB();
            case 'j' ->
            {
                int start = index.getValue() + 2;
                int end = Math.min(start + 8, str.length());
                index.setValue(end - 2);
                try
                {
                    yield (int) Long.parseLong(str.substring(start, end), 16);
                }
                catch (NumberFormatException ignored)
                {
                    yield 0xFFFFFFFF;
                }
            }
            default -> -1;
        };
    }

    public record CharLocation(float x, float y, float r, float g, float b, Glyph glyph) {}
}

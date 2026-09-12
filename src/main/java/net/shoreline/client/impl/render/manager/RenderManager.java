package net.shoreline.client.impl.render.manager;

import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.shoreline.client.api.GenericFeature;
import net.shoreline.client.api.font.FontManager;
import net.shoreline.client.impl.event.render.RenderWorldEvent;
import net.shoreline.client.impl.module.client.FontModule;
import net.shoreline.client.impl.render.Layers;
import net.shoreline.eventbus.EventBus;
import net.shoreline.eventbus.annotation.EventListener;
import org.joml.Matrix4f;

import java.util.List;

@Getter
@Setter
public class RenderManager extends GenericFeature
{
    private static final float LINE_WIDTH = 1.5f;

    private final List<BoxRender> quadQueue = new ObjectArrayList<>(512);
    private final List<BoxRender> lineQuadQueue = new ObjectArrayList<>(512);
    private final List<LineRender> lineQueue = new ObjectArrayList<>(1024);
    private final List<TextRender> textQueue = new ObjectArrayList<>(512);

    private final Matrix4f positionMatrix = new Matrix4f();
    private final Matrix4f projectionMatrix = new Matrix4f();

    private Frustum frustum;
    private Vec3d cameraPos = Vec3d.ZERO;

    private double deltaTime;

    public RenderManager()
    {
        super("Custom Rendering");
        EventBus.INSTANCE.subscribe(this);
    }

    public void beginFrame(Matrix4f position, Matrix4f projection, Matrix4f cullProjection, Vec3d camera)
    {
        positionMatrix.set(position);
        projectionMatrix.set(projection);
        cameraPos = camera;
        frustum = new Frustum(position, cullProjection);
        frustum.setPosition(camera.x, camera.y, camera.z);
    }

    @EventListener(priority = Integer.MIN_VALUE)
    public void onRenderWorld(RenderWorldEvent.Post event)
    {
        flushQuads();
        flushBoxOutlines();
        flushLines();
        flushText();

        quadQueue.clear();
        lineQuadQueue.clear();
        lineQueue.clear();
        textQueue.clear();
    }

    public void renderBox(MatrixStack matrixStack, BlockPos blockPos, int color)
    {
        renderBox(matrixStack, new Box(blockPos), color);
    }

    public void renderBox(MatrixStack matrixStack, Box box, int color)
    {
        if (isVisible(box))
        {
            quadQueue.add(new BoxRender(color, box));
        }
    }

    public void renderBoundingBox(MatrixStack matrixStack, BlockPos pos, int color)
    {
        renderBoundingBox(matrixStack, new Box(pos), color);
    }

    public void renderBoundingBox(MatrixStack matrixStack, Box box, int color)
    {
        if (isVisible(box))
        {
            lineQuadQueue.add(new BoxRender(color, box));
        }
    }

    public void renderLine(MatrixStack matrices, Vec3d start, Vec3d end, int color)
    {
        if (isVisible(new Box(start, end)))
        {
            lineQueue.add(new LineRender(color, start, end));
        }
    }

    public void renderNametag(MatrixStack matrixStack, Vec3d pos, float scale, String text, int color)
    {
        if (isVisible(Box.from(pos)))
        {
            textQueue.add(new TextRender(matrixStack, color, pos, scale, text));
        }
    }

    private void flushQuads()
    {
        if (quadQueue.isEmpty())
        {
            return;
        }

        BufferBuilder buffer = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        for (BoxRender render : quadQueue)
        {
            float minX = (float) (render.box.minX - cameraPos.x);
            float minY = (float) (render.box.minY - cameraPos.y);
            float minZ = (float) (render.box.minZ - cameraPos.z);
            float maxX = (float) (render.box.maxX - cameraPos.x);
            float maxY = (float) (render.box.maxY - cameraPos.y);
            float maxZ = (float) (render.box.maxZ - cameraPos.z);
            int color = render.color;

            vertex(buffer, minX, minY, minZ, color);
            vertex(buffer, maxX, minY, minZ, color);
            vertex(buffer, maxX, minY, maxZ, color);
            vertex(buffer, minX, minY, maxZ, color);

            vertex(buffer, minX, maxY, minZ, color);
            vertex(buffer, minX, maxY, maxZ, color);
            vertex(buffer, maxX, maxY, maxZ, color);
            vertex(buffer, maxX, maxY, minZ, color);

            vertex(buffer, minX, minY, minZ, color);
            vertex(buffer, minX, maxY, minZ, color);
            vertex(buffer, maxX, maxY, minZ, color);
            vertex(buffer, maxX, minY, minZ, color);

            vertex(buffer, maxX, minY, minZ, color);
            vertex(buffer, maxX, maxY, minZ, color);
            vertex(buffer, maxX, maxY, maxZ, color);
            vertex(buffer, maxX, minY, maxZ, color);

            vertex(buffer, minX, minY, maxZ, color);
            vertex(buffer, maxX, minY, maxZ, color);
            vertex(buffer, maxX, maxY, maxZ, color);
            vertex(buffer, minX, maxY, maxZ, color);

            vertex(buffer, minX, minY, minZ, color);
            vertex(buffer, minX, minY, maxZ, color);
            vertex(buffer, minX, maxY, maxZ, color);
            vertex(buffer, minX, maxY, minZ, color);
        }

        draw(Layers.QUADS, buffer);
    }

    private void flushBoxOutlines()
    {
        if (lineQuadQueue.isEmpty())
        {
            return;
        }

        BufferBuilder buffer = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH);

        for (BoxRender render : lineQuadQueue)
        {
            float minX = (float) (render.box.minX - cameraPos.x);
            float minY = (float) (render.box.minY - cameraPos.y);
            float minZ = (float) (render.box.minZ - cameraPos.z);
            float maxX = (float) (render.box.maxX - cameraPos.x);
            float maxY = (float) (render.box.maxY - cameraPos.y);
            float maxZ = (float) (render.box.maxZ - cameraPos.z);
            int color = render.color;

            line(buffer, minX, minY, minZ, maxX, minY, minZ, color);
            line(buffer, maxX, minY, minZ, maxX, minY, maxZ, color);
            line(buffer, maxX, minY, maxZ, minX, minY, maxZ, color);
            line(buffer, minX, minY, maxZ, minX, minY, minZ, color);

            line(buffer, minX, maxY, minZ, maxX, maxY, minZ, color);
            line(buffer, maxX, maxY, minZ, maxX, maxY, maxZ, color);
            line(buffer, maxX, maxY, maxZ, minX, maxY, maxZ, color);
            line(buffer, minX, maxY, maxZ, minX, maxY, minZ, color);

            line(buffer, minX, minY, minZ, minX, maxY, minZ, color);
            line(buffer, maxX, minY, minZ, maxX, maxY, minZ, color);
            line(buffer, maxX, minY, maxZ, maxX, maxY, maxZ, color);
            line(buffer, minX, minY, maxZ, minX, maxY, maxZ, color);
        }

        draw(Layers.LINES, buffer);
    }

    private void flushLines()
    {
        if (lineQueue.isEmpty())
        {
            return;
        }

        BufferBuilder buffer = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH);

        for (LineRender render : lineQueue)
        {
            line(buffer,
                    (float) (render.start.x - cameraPos.x),
                    (float) (render.start.y - cameraPos.y),
                    (float) (render.start.z - cameraPos.z),
                    (float) (render.end.x - cameraPos.x),
                    (float) (render.end.y - cameraPos.y),
                    (float) (render.end.z - cameraPos.z),
                    render.color);
        }

        draw(Layers.LINES, buffer);
    }

    private void flushText()
    {
        if (textQueue.isEmpty())
        {
            return;
        }

        Camera camera = mc.gameRenderer.getCamera();
        for (TextRender render : textQueue)
        {
            float distance = (float) camera.getCameraPos().distanceTo(render.pos);
            float scaling = distance <= 8.0f ? 0.0245f : 0.0018f + render.scale * distance;

            MatrixStack matrixStack = render.matrixStack;
            matrixStack.push();
            matrixStack.translate(
                    render.pos.x - cameraPos.x,
                    render.pos.y - cameraPos.y,
                    render.pos.z - cameraPos.z);
            matrixStack.multiply(camera.getRotation());
            matrixStack.scale(scaling, -scaling, scaling);

            drawText(matrixStack, render.text, -getTextWidth(render.text) / 2.0f, 0.0f, render.color);
            matrixStack.pop();
        }
    }

    private static void vertex(BufferBuilder buffer, float x, float y, float z, int color)
    {
        buffer.vertex(x, y, z).color(color);
    }

    private static void line(BufferBuilder buffer,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             int color)
    {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length == 0.0f)
        {
            return;
        }

        dx /= length;
        dy /= length;
        dz /= length;

        buffer.vertex(x1, y1, z1).color(color).normal(dx, dy, dz).lineWidth(LINE_WIDTH);
        buffer.vertex(x2, y2, z2).color(color).normal(dx, dy, dz).lineWidth(LINE_WIDTH);
    }

    private static void draw(RenderLayer layer, BufferBuilder buffer)
    {
        BuiltBuffer built = buffer.endNullable();
        if (built != null)
        {
            layer.draw(built);
        }
    }

    public void drawRect(DrawContext context, float x, float y, float width, float height, int color)
    {
        int x1 = Math.round(x);
        int y1 = Math.round(y);
        context.fill(x1, y1, x1 + Math.round(width), y1 + Math.round(height), color);
    }

    public void drawGuiText(DrawContext context, String text, float x, float y, int color)
    {
        if (text.isEmpty())
        {
            return;
        }

        if (FontModule.INSTANCE.isEnabled())
        {
            FontManager.FONT_RENDERER.drawStringWithShadow(context, text, x, y, color);
        }
        else
        {
            context.drawTextWithShadow(mc.textRenderer, text, Math.round(x), Math.round(y), color);
        }
    }

    public void drawOutline(DrawContext context, float x, float y, float width, float height, float thickness, int color)
    {
        float t2 = thickness * 2;
        drawRect(context, x - thickness, y - thickness, width + t2, thickness, color);
        drawRect(context, x - thickness, y, thickness, height, color);
        drawRect(context, x + width, y, thickness, height, color);
        drawRect(context, x - thickness, y + height, width + t2, thickness, color);
    }

    public void drawText(MatrixStack matrices, String text, float x, float y, int color)
    {
        if (text.isEmpty())
        {
            return;
        }

        if (FontModule.INSTANCE.isEnabled())
        {
            FontManager.FONT_RENDERER.drawStringWithShadow(matrices, text, x, y, color);
            return;
        }

        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();
        mc.textRenderer.draw(
                text,
                x,
                y,
                color,
                true,
                matrices.peek().getPositionMatrix(),
                consumers,
                TextRenderer.TextLayerType.SEE_THROUGH,
                0,
                LightmapTextureManager.MAX_LIGHT_COORDINATE);

        consumers.draw();
    }

    public float getTextWidth(String text)
    {
        if (text.isEmpty())
        {
            return 0;
        }

        if (FontModule.INSTANCE.isEnabled())
        {
            return FontManager.FONT_RENDERER.getStringWidth(text);
        }

        return mc.textRenderer.getWidth(text);
    }

    public boolean isVisible(Box box)
    {
        return frustum == null || frustum.isVisible(box);
    }

    @RequiredArgsConstructor
    private static class BoxRender
    {
        private final int color;
        private final Box box;
    }

    @RequiredArgsConstructor
    private static class LineRender
    {
        private final int color;
        private final Vec3d start;
        private final Vec3d end;
    }

    @RequiredArgsConstructor
    private static class TextRender
    {
        private final MatrixStack matrixStack;
        private final int color;
        private final Vec3d pos;
        private final float scale;
        private final String text;
    }
}

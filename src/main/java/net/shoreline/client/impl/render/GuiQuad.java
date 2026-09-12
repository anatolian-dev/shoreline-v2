package net.shoreline.client.impl.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;
import net.shoreline.client.impl.imixin.IDrawContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

public record GuiQuad(RenderPipeline pipeline,
                      TextureSetup textureSetup,
                      Matrix3x2f pose,
                      float x1,
                      float y1,
                      float x2,
                      float y2,
                      float u1,
                      float v1,
                      float u2,
                      float v2,
                      int color,
                      @Nullable ScreenRect scissorArea,
                      @Nullable ScreenRect bounds) implements SimpleGuiElementRenderState
{
    public static void submit(DrawContext context,
                              Identifier texture,
                              float x1,
                              float y1,
                              float x2,
                              float y2,
                              float u1,
                              float v1,
                              float u2,
                              float v2,
                              int color)
    {
        AbstractTexture abstractTexture = MinecraftClient.getInstance().getTextureManager().getTexture(texture);
        Matrix3x2f pose = new Matrix3x2f(context.getMatrices());
        ScreenRect scissor = ((IDrawContext) context).getScissor();
        ScreenRect bounds = new ScreenRect((int) Math.floor(x1), (int) Math.floor(y1),
                (int) Math.ceil(x2 - x1) + 1, (int) Math.ceil(y2 - y1) + 1).transformEachVertex(pose);

        ((IDrawContext) context).getGuiState().addSimpleElement(new GuiQuad(
                RenderPipelines.GUI_TEXTURED,
                TextureSetup.of(abstractTexture.getGlTextureView(), abstractTexture.getSampler()),
                pose, x1, y1, x2, y2, u1, v1, u2, v2, color,
                scissor,
                scissor != null ? scissor.intersection(bounds) : bounds));
    }

    @Override
    public void setupVertices(VertexConsumer vertices)
    {
        vertices.vertex(pose, x1, y1).texture(u1, v1).color(color);
        vertices.vertex(pose, x1, y2).texture(u1, v2).color(color);
        vertices.vertex(pose, x2, y2).texture(u2, v2).color(color);
        vertices.vertex(pose, x2, y1).texture(u2, v1).color(color);
    }
}

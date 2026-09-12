package net.shoreline.client.gui.titlescreen.snow;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureSetup;
import net.shoreline.client.impl.imixin.IDrawContext;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

record SnowElement(SnowField field,
                   TextureSetup textureSetup,
                   Matrix3x2f pose,
                   @Nullable ScreenRect scissorArea,
                   @Nullable ScreenRect bounds) implements SimpleGuiElementRenderState
{
    static void submit(DrawContext context, SnowField field)
    {
        AbstractTexture texture = MinecraftClient.getInstance().getTextureManager().getTexture(SnowField.FLAKE);
        ScreenRect screen = new ScreenRect(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight());

        ((IDrawContext) context).getGuiState().addSimpleElement(new SnowElement(
                field,
                TextureSetup.of(texture.getGlTextureView(), RenderSystem.getSamplerCache().get(FilterMode.LINEAR)),
                new Matrix3x2f(context.getMatrices()),
                ((IDrawContext) context).getScissor(),
                screen));
    }

    @Override
    public RenderPipeline pipeline()
    {
        return RenderPipelines.GUI_TEXTURED;
    }

    @Override
    public void setupVertices(VertexConsumer vertices)
    {
        field.writeQuads((x, y, size) ->
        {
            vertices.vertex(pose, x, y).texture(0.0f, 0.0f).color(-1);
            vertices.vertex(pose, x, y + size).texture(0.0f, 1.0f).color(-1);
            vertices.vertex(pose, x + size, y + size).texture(1.0f, 1.0f).color(-1);
            vertices.vertex(pose, x + size, y).texture(1.0f, 0.0f).color(-1);
        });
    }
}

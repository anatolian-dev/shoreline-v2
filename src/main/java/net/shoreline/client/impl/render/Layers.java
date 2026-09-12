package net.shoreline.client.impl.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.PolygonMode;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.TextureTransform;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.shoreline.client.ShorelineMod;
import net.shoreline.client.impl.module.render.ChamsModule;
import org.joml.Matrix4f;

import java.util.function.BiFunction;
import java.util.function.Function;

public final class Layers
{
    private static final int BUFFER_SIZE = 786432;

    public static final RenderLayer QUADS = quads("shoreline_quads", DepthTestFunction.NO_DEPTH_TEST);

    public static final RenderLayer QUADS_DEPTH = quads("shoreline_quads_depth", DepthTestFunction.LEQUAL_DEPTH_TEST);

    public static final RenderLayer LINES = lines("shoreline_lines", DepthTestFunction.NO_DEPTH_TEST);

    public static final RenderLayer LINES_DEPTH = lines("shoreline_lines_depth", DepthTestFunction.LEQUAL_DEPTH_TEST);

    public static final BiFunction<Identifier, Boolean, RenderLayer> ENTITY = Util.memoize(
            (BiFunction<Identifier, Boolean, RenderLayer>) RenderLayers::entityCutoutNoCull);

    private static final Identifier BLANK = Identifier.of(ShorelineMod.MOD_ID, "textures/blank.png");

    public static final RenderLayer CHAMS = chams("shoreline_chams", DepthTestFunction.NO_DEPTH_TEST, PolygonMode.FILL);

    public static final RenderLayer CHAMS_DEPTH = chams("shoreline_chams_depth", DepthTestFunction.LEQUAL_DEPTH_TEST, PolygonMode.FILL);

    public static final RenderLayer CHAMS_WIRE = chams("shoreline_chams_wire", DepthTestFunction.NO_DEPTH_TEST, PolygonMode.WIREFRAME);

    public static final RenderLayer CHAMS_WIRE_DEPTH = chams("shoreline_chams_wire_depth", DepthTestFunction.LEQUAL_DEPTH_TEST, PolygonMode.WIREFRAME);

    public static final Function<Identifier, RenderLayer> ENTITY_NO_DEPTH = Util.memoize(
            id -> RenderLayer.of("shoreline_entity_no_depth/" + id.getPath(),
                    RenderSetup.builder(RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                                    .withLocation(Identifier.of(ShorelineMod.MOD_ID, "pipeline/entity_no_depth"))
                                    .withSampler("Sampler1")
                                    .withBlend(BlendFunction.TRANSLUCENT)
                                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                                    .withDepthWrite(false)
                                    .withCull(true)
                                    .build())
                            .texture("Sampler0", id)
                            .useLightmap()
                            .useOverlay()
                            .expectedBufferSize(BUFFER_SIZE)
                            .translucent()
                            .build()));

    private static final Identifier SHINE = Identifier.of(ShorelineMod.MOD_ID, "textures/shine.png");

    public static final RenderLayer SHINE_LAYER = RenderLayer.of("shoreline_shine",
            RenderSetup.builder(RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                            .withLocation(Identifier.of(ShorelineMod.MOD_ID, "pipeline/shine"))
                            .withVertexShader(Identifier.of(ShorelineMod.MOD_ID, "core/entity_shine"))
                            .withFragmentShader(Identifier.of(ShorelineMod.MOD_ID, "core/entity_shine"))
                            .withSampler("Sampler0")
                            .withVertexFormat(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.QUADS)
                            .withBlend(BlendFunction.GLINT)
                            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                            .withDepthWrite(false)
                            .withCull(true)
                            .build())
                    .texture("Sampler0", SHINE)
                    .textureTransform(new TextureTransform("shoreline_shine", Layers::shineTransform))
                    .expectedBufferSize(BUFFER_SIZE)
                    .translucent()
                    .build());

    private static Matrix4f shineTransform()
    {
        ChamsModule chams = ChamsModule.getInstance();
        float scale = chams == null ? 1.0f : chams.getScale();
        float speed = chams == null ? 0.5f : chams.getSpeed();
        long time = (long) (Util.getMeasuringTimeMs() * speed * 8.0);
        float u = (time % 110000L) / 110000.0f;
        float v = (time % 30000L) / 30000.0f;
        return new Matrix4f().translation(-u, v, 0.0f)
                .rotateZ((float) (Math.PI / 18.0))
                .scale(0.5f * scale);
    }

    public static RenderLayer noDepth(RenderLayer layer)
    {
        RenderSetup.TextureSpec texture = layer.renderSetup.textures.get("Sampler0");
        return texture == null ? layer : ENTITY_NO_DEPTH.apply(texture.location());
    }

    public static RenderLayer chams(boolean throughWalls, boolean wireframe)
    {
        if (wireframe)
        {
            return throughWalls ? CHAMS_WIRE : CHAMS_WIRE_DEPTH;
        }

        return throughWalls ? CHAMS : CHAMS_DEPTH;
    }

    private static RenderLayer quads(String name, DepthTestFunction depthTest)
    {
        return of(name, RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
                .withLocation(Identifier.of(ShorelineMod.MOD_ID, "pipeline/" + name))
                .withDepthTestFunction(depthTest)
                .withDepthWrite(false)
                .withCull(false)
                .build());
    }

    private static RenderLayer lines(String name, DepthTestFunction depthTest)
    {
        return of(name, RenderPipeline.builder(RenderPipelines.RENDERTYPE_LINES_SNIPPET)
                .withLocation(Identifier.of(ShorelineMod.MOD_ID, "pipeline/" + name))
                .withDepthTestFunction(depthTest)
                .withDepthWrite(false)
                .build());
    }

    private static RenderLayer chams(String name, DepthTestFunction depthTest, PolygonMode polygonMode)
    {
        RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(Identifier.of(ShorelineMod.MOD_ID, "pipeline/" + name))
                .withSampler("Sampler1")
                .withBlend(BlendFunction.TRANSLUCENT)
                .withPolygonMode(polygonMode)
                .withDepthTestFunction(depthTest)
                .withDepthWrite(false)
                .withCull(true)
                .build();

        return RenderLayer.of(name, RenderSetup.builder(pipeline)
                .texture("Sampler0", BLANK)
                .useLightmap()
                .useOverlay()
                .expectedBufferSize(BUFFER_SIZE)
                .translucent()
                .build());
    }

    private static RenderLayer of(String name, RenderPipeline pipeline)
    {
        return RenderLayer.of(name, RenderSetup.builder(pipeline)
                .expectedBufferSize(BUFFER_SIZE)
                .translucent()
                .build());
    }

    private Layers()
    {
    }
}

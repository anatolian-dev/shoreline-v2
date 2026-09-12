package net.shoreline.client.impl.render.manager;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Getter;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.shoreline.client.ShorelineMod;
import net.shoreline.client.api.GenericFeature;
import net.shoreline.client.impl.imixin.IGameRenderer;
import net.shoreline.client.impl.imixin.IPostEffectPass;
import net.shoreline.client.impl.imixin.IPostEffectProcessor;
import net.shoreline.client.impl.module.render.ShadersModule;
import net.shoreline.client.impl.render.shader.ShaderEffect;
import org.lwjgl.system.MemoryStack;

import java.util.function.Function;

@Getter
public class ShaderManager extends GenericFeature
{
    private static final Identifier BLANK = Identifier.of(ShorelineMod.MOD_ID, "textures/blank.png");

    private final ShadersModule shadersModule;
    private final Framebuffer framebuffer;
    private final OutputTarget target;
    private final Function<Boolean, RenderLayer> layers;

    public ShaderManager(ShadersModule shadersModule)
    {
        super("Shaders");
        this.shadersModule = shadersModule;

        int width = mc.getWindow().getFramebufferWidth();
        int height = mc.getWindow().getFramebufferHeight();
        this.framebuffer = new SimpleFramebuffer("shoreline_shader", width, height, true);
        this.target = new OutputTarget("shoreline_shader_target", () -> framebuffer);
        this.layers = Util.memoize(this::createLayer);
    }

    public RenderLayer getLayer()
    {
        return layers.apply(shadersModule.getDepth());
    }

    private RenderLayer createLayer(boolean throughWalls)
    {
        RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(Identifier.of(ShorelineMod.MOD_ID, "pipeline/shader" + (throughWalls ? "" : "_depth")))
                .withSampler("Sampler1")
                .withDepthTestFunction(throughWalls ? DepthTestFunction.NO_DEPTH_TEST : DepthTestFunction.LEQUAL_DEPTH_TEST)
                .withDepthWrite(false)
                .withCull(true)
                .build();

        return RenderLayer.of("shoreline_shader" + (throughWalls ? "" : "_depth"),
                RenderSetup.builder(pipeline)
                        .texture("Sampler0", BLANK)
                        .useLightmap()
                        .useOverlay()
                        .outputTarget(target)
                        .expectedBufferSize(786432)
                        .build());
    }

    public void begin()
    {
        RenderSystem.getDevice().createCommandEncoder()
                .clearColorAndDepthTextures(framebuffer.getColorAttachment(), 0, framebuffer.getDepthAttachment(), 1.0);

        if (!shadersModule.getDepth())
        {
            framebuffer.copyDepthFrom(mc.getFramebuffer());
        }
    }

    public void render(ShaderEffect effect)
    {
        PostEffectProcessor processor = mc.getShaderLoader()
                .loadPostEffect(Identifier.of(ShorelineMod.MOD_ID, effect.getName()), DefaultFramebufferSet.MAIN_ONLY);
        if (processor == null)
        {
            return;
        }

        writeUniforms(processor, effect);
        processor.render(framebuffer, ((IGameRenderer) mc.gameRenderer).getPool());
        framebuffer.drawBlit(mc.getFramebuffer().getColorAttachmentView());
    }

    private static void writeUniforms(PostEffectProcessor processor, ShaderEffect effect)
    {
        for (PostEffectPass pass : ((IPostEffectProcessor) processor).getPasses())
        {
            GpuBuffer previous = ((IPostEffectPass) pass).getUniformBuffers().get(effect.getBlock());
            if (previous == null)
            {
                continue;
            }

            try (MemoryStack stack = MemoryStack.stackPush())
            {
                Std140Builder builder = Std140Builder.onStack(stack, effect.size());
                effect.write(builder);
                GpuBuffer buffer = RenderSystem.getDevice()
                        .createBuffer(effect::getBlock, GpuBuffer.USAGE_UNIFORM, builder.get());
                ((IPostEffectPass) pass).getUniformBuffers().put(effect.getBlock(), buffer);
            }

            previous.close();
        }
    }

    public void resize(int width, int height)
    {
        framebuffer.resize(width, height);
    }
}

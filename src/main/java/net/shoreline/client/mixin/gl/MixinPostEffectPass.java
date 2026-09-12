package net.shoreline.client.mixin.gl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import net.minecraft.client.gl.PostEffectPass;
import net.shoreline.client.impl.imixin.IPostEffectPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(PostEffectPass.class)
public abstract class MixinPostEffectPass implements IPostEffectPass
{
    @Override
    @Accessor("uniformBuffers")
    public abstract Map<String, GpuBuffer> getUniformBuffers();
}

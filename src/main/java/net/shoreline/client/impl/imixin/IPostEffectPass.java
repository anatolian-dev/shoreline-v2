package net.shoreline.client.impl.imixin;

import com.mojang.blaze3d.buffers.GpuBuffer;

import java.util.Map;

@IMixin
public interface IPostEffectPass
{
    Map<String, GpuBuffer> getUniformBuffers();
}

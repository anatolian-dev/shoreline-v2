package net.shoreline.client.mixin.render;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import net.shoreline.client.impl.Managers;
import net.shoreline.client.impl.event.render.RenderShaderEvent;
import net.shoreline.client.impl.event.render.RenderWorldEvent;
import net.shoreline.client.impl.imixin.IWorldRenderer;
import net.shoreline.client.impl.render.WorldProjection;
import net.shoreline.eventbus.EventBus;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class MixinWorldRenderer implements IWorldRenderer
{
    @Override
    @Accessor("capturedFrustum")
    public abstract Frustum getFrustum();

    @Inject(method = "render", at = @At("HEAD"))
    private void hookRenderHead(ObjectAllocator allocator,
                                RenderTickCounter tickCounter,
                                boolean renderBlockOutline,
                                Camera camera,
                                Matrix4f positionMatrix,
                                Matrix4f projectionMatrix,
                                Matrix4f cullProjectionMatrix,
                                GpuBufferSlice fogBuffer,
                                Vector4f fogColor,
                                boolean shouldRenderSky,
                                CallbackInfo ci)
    {
        WorldProjection.capture(positionMatrix, projectionMatrix);
        Managers.RENDER.beginFrame(positionMatrix, projectionMatrix, cullProjectionMatrix, camera.getCameraPos());
        EventBus.INSTANCE.dispatch(new RenderShaderEvent());
    }

    @Inject(method = "onResized", at = @At("TAIL"))
    private void hookResized(int width, int height, CallbackInfo info)
    {
        EventBus.INSTANCE.dispatch(new RenderWorldEvent.Resized(width, height));
    }
}

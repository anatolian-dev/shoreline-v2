package net.shoreline.client.mixin.accessor;

import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(WorldRenderer.class)
public interface WorldRendererAccessor
{
    @Accessor("entityRenderCommandQueue")
    OrderedRenderCommandQueueImpl getEntityRenderCommandQueue();
}

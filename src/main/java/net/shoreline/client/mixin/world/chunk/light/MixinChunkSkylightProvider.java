package net.shoreline.client.mixin.world.chunk.light;

import net.minecraft.world.chunk.light.ChunkLightProvider;
import net.minecraft.world.chunk.light.ChunkSkyLightProvider;
import net.shoreline.client.impl.event.world.WorldSkylightEvent;
import net.shoreline.eventbus.EventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkLightProvider.class)
public class MixinChunkSkylightProvider
{

    @Inject(method = "queueLightIncrease", at = @At(value = "HEAD"), cancellable = true)
    private void hookQueueLightIncrease(long blockPos, long flags, CallbackInfo ci)
    {
        if (!((Object) this instanceof ChunkSkyLightProvider))
        {
            return;
        }
        WorldSkylightEvent renderSkylightEvent = new WorldSkylightEvent();
        EventBus.INSTANCE.dispatch(renderSkylightEvent);
        if (renderSkylightEvent.isCanceled())
        {
            ci.cancel();
        }
    }
}

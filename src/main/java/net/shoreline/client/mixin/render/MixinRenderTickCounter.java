package net.shoreline.client.mixin.render;

import it.unimi.dsi.fastutil.floats.FloatUnaryOperator;
import net.minecraft.client.render.RenderTickCounter;
import net.shoreline.client.impl.event.render.RenderTickCounterEvent;
import net.shoreline.eventbus.EventBus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderTickCounter.Dynamic.class)
public class MixinRenderTickCounter
{
    @Shadow
    @Final
    private FloatUnaryOperator targetMillisPerTick;

    @Shadow
    @Final
    private float tickTime;

    @Shadow
    private float dynamicDeltaTicks;

    @Shadow
    private long lastTimeMillis;

    @Shadow
    private float tickProgress;

    @Inject(method = "beginRenderTick(J)I", at = @At(value = "HEAD"), cancellable = true)
    private void hookBeginRenderTick(long timeMillis, CallbackInfoReturnable<Integer> cir)
    {
        RenderTickCounterEvent event = new RenderTickCounterEvent();
        EventBus.INSTANCE.dispatch(event);
        if (event.isCanceled())
        {
            dynamicDeltaTicks = ((timeMillis - lastTimeMillis) / tickTime) * event.getTicks();
            lastTimeMillis = timeMillis;
            tickProgress += dynamicDeltaTicks;
            int i = (int) tickProgress;
            tickProgress -= i;
            cir.setReturnValue(i);
        }
    }
}

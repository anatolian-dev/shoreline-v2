package net.shoreline.client.mixin.accessor;

import net.minecraft.entity.LimbAnimator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LimbAnimator.class)
public interface LimbAnimatorAccessor
{
    @Accessor("lastSpeed")
    float shoreline$getLastSpeed();

    @Accessor("lastSpeed")
    void shoreline$setLastSpeed(float value);

    @Accessor("animationProgress")
    float shoreline$getAnimationProgress();

    @Accessor("animationProgress")
    void shoreline$setAnimationProgress(float value);

    @Accessor("timeScale")
    float shoreline$getTimeScale();

    @Accessor("timeScale")
    void shoreline$setTimeScale(float value);
}

package net.shoreline.client.mixin.world;

import net.minecraft.block.BlockState;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.shoreline.client.impl.event.particle.BlockBreakParticleEvent;
import net.shoreline.client.impl.imixin.IClientWorld;
import net.shoreline.eventbus.EventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public abstract class MixinClientWorld implements IClientWorld
{
    @Override
    @Accessor("pendingUpdateManager")
    public abstract PendingUpdateManager getUpdateManager();

    @Inject(method = "addBlockBreakParticles", at = @At("HEAD"), cancellable = true)
    private void hookAddBlockBreakParticles(BlockPos pos, BlockState state, CallbackInfo ci)
    {
        BlockBreakParticleEvent event = new BlockBreakParticleEvent();
        EventBus.INSTANCE.dispatch(event);
        if (event.isCanceled())
        {
            ci.cancel();
        }
    }

    @Inject(method = "spawnBlockBreakingParticle", at = @At("HEAD"), cancellable = true)
    private void hookSpawnBlockBreakingParticle(BlockPos pos, Direction direction, CallbackInfo ci)
    {
        BlockBreakParticleEvent event = new BlockBreakParticleEvent();
        EventBus.INSTANCE.dispatch(event);
        if (event.isCanceled())
        {
            ci.cancel();
        }
    }
}

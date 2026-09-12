package net.shoreline.client.mixin.render.entity;

import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.shoreline.client.impl.event.render.EntityLightEvent;
import net.shoreline.client.impl.imixin.IEntityRenderState;
import net.shoreline.eventbus.EventBus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer<T extends Entity, S extends EntityRenderState>
{
    @Shadow
    @Final
    protected EntityRenderManager dispatcher;

    @Shadow
    public void updateRenderState(T entity, S state, float tickDelta)
    {
    }

    @Inject(method = "updateRenderState", at = @At("HEAD"))
    private void updateRenderStateHook(T entity, S state, float tickDelta, CallbackInfo ci)
    {
        ((IEntityRenderState) state).setEntity(entity);
    }

    @Inject(
            method = "getSkyLight",
            at = @At(value = "RETURN"),
            cancellable = true)
    private void getSkylightHook(T entity, BlockPos pos, CallbackInfoReturnable<Integer> cir)
    {
        EntityLightEvent.Skylight event = new EntityLightEvent.Skylight();
        EventBus.INSTANCE.dispatch(event);
        if (event.isCanceled())
        {
            cir.cancel();
            cir.setReturnValue(event.getLight());
        }
    }

    @Inject(
            method = "getBlockLight",
            at = @At(value = "RETURN"),
            cancellable = true)
    private void getBlocklightHook(T entity, BlockPos pos, CallbackInfoReturnable<Integer> cir)
    {
        EntityLightEvent.Block event = new EntityLightEvent.Block();
        EventBus.INSTANCE.dispatch(event);
        if (event.isCanceled())
        {
            cir.cancel();
            cir.setReturnValue(event.getLight());
        }
    }
}

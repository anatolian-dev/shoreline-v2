package net.shoreline.client.mixin.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.Pool;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import net.shoreline.client.api.module.Module;
import net.shoreline.client.impl.Managers;
import net.shoreline.client.impl.module.render.NoBobModule;
import net.shoreline.client.impl.event.render.*;
import net.shoreline.client.impl.imixin.IGameRenderer;
import net.shoreline.eventbus.EventBus;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer implements IGameRenderer
{
    @Override
    @Accessor("pool")
    public abstract Pool getPool();

    @Override
    @Invoker("getFov")
    public abstract float invokeGetFov(Camera camera, float tickDelta, boolean changingFov);

    @Inject(method = "bobView", at = @At(value = "HEAD"), cancellable = true)
    private void hookBobView(MatrixStack matrices, float tickProgress, CallbackInfo ci)
    {
        Module mod = Managers.MODULES.getModule("nobob_module");
        if (mod instanceof NoBobModule noBob && noBob.isEnabled())
        {
            ci.cancel();
        }
    }

    @Inject(method = "tiltViewWhenHurt", at = @At(value = "HEAD"), cancellable = true)
    private void hookTiltViewWhenHurt(MatrixStack matrices,
                                      float tickDelta,
                                      CallbackInfo ci)
    {
        TiltViewEvent hurtCamEvent = new TiltViewEvent();
        EventBus.INSTANCE.dispatch(hurtCamEvent);
        if (hurtCamEvent.isCanceled())
        {
            ci.cancel();
        }
    }

    @Inject(
            method = "renderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;ZLnet/minecraft/client/render/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
                    shift = At.Shift.AFTER))
    private void renderWorldHook(RenderTickCounter tickCounter, CallbackInfo info)
    {
        RenderEntityWorldEvent.Post renderEntityEvent =
                new RenderEntityWorldEvent.Post(tickCounter.getTickProgress(true));
        EventBus.INSTANCE.dispatch(renderEntityEvent);

        Matrix4f position = Managers.RENDER.getPositionMatrix();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(position);

        RenderWorldEvent.Post renderWorldEvent = new RenderWorldEvent.Post(
                new MatrixStack(),
                tickCounter.getTickProgress(true),
                position,
                Managers.RENDER.getProjectionMatrix());
        EventBus.INSTANCE.dispatch(renderWorldEvent);

        modelView.popMatrix();
    }

    @Inject(method = "renderWorld", at = @At("TAIL"))
    private void renderWorld$TAIL(RenderTickCounter renderTickCounter, CallbackInfo info)
    {
        RenderShaderEvent.Post shaderEvent = new RenderShaderEvent.Post();
        EventBus.INSTANCE.dispatch(shaderEvent);
    }

    @Redirect(method = "renderWorld", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/math/MathHelper;lerp(FFF)F"))
    private float hookLerpNausea(float delta,
                                 float start,
                                 float end)
    {
        RenderNauseaEvent renderNauseaEvent = new RenderNauseaEvent();
        EventBus.INSTANCE.dispatch(renderNauseaEvent);
        return renderNauseaEvent.isCanceled() ? 0.0f : MathHelper.lerp(delta, start, end);
    }

    @Inject(method = "showFloatingItem", at = @At(value = "HEAD"), cancellable = true)
    private void hookShowFloatingItem(ItemStack floatingItem, CallbackInfo ci)
    {
        RenderFloatingItemEvent renderFloatingItemEvent =
                new RenderFloatingItemEvent(floatingItem);
        EventBus.INSTANCE.dispatch(renderFloatingItemEvent);
        if (renderFloatingItemEvent.isCanceled())
        {
            ci.cancel();
        }
    }

    @Inject(method = "shouldRenderBlockOutline", at = @At(value = "HEAD"), cancellable = true)
    private void hookShouldRenderBlockOutline(CallbackInfoReturnable<Boolean> cir)
    {
        RenderBlockOutlineEvent renderBlockOutlineEvent = new RenderBlockOutlineEvent();
        EventBus.INSTANCE.dispatch(renderBlockOutlineEvent);
        if (renderBlockOutlineEvent.isCanceled())
        {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }

    @Redirect(method = "updateCrosshairTarget", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/hit/EntityHitResult;getEntity()Lnet/minecraft/entity/Entity;"))
    private Entity hookCrosshairTarget(EntityHitResult instance)
    {
        Entity entity = instance.getEntity();
        if (entity != null)
        {
            CrosshairTargetEvent targetEvent = new CrosshairTargetEvent(entity);
            EventBus.INSTANCE.dispatch(targetEvent);
            return targetEvent.isCanceled() ? null : entity;
        }

        return null;
    }

}

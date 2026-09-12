package net.shoreline.client.mixin.render.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.shoreline.client.impl.event.entity.EntityHurtEvent;
import net.shoreline.client.impl.imixin.IEntityRenderState;
import net.shoreline.client.impl.module.render.NametagsModule;
import net.shoreline.client.impl.module.render.ChamsModule;
import net.shoreline.eventbus.EventBus;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer<T extends LivingEntity,
        S extends LivingEntityRenderState,
        M extends EntityModel<? super S>>
    extends MixinEntityRenderer<T, S>
{
    @Shadow
    public abstract Identifier getTexture(S state);

    @Shadow
    protected M model;

    @Shadow
    protected abstract void scale(S state, MatrixStack matrices);

    @Shadow
    protected abstract boolean isVisible(S state);

    @Shadow
    private static float clampBodyYaw(LivingEntity entity, float degrees, float tickDelta) {return 0;}

    @Shadow @Final protected ItemModelManager itemModelResolver;
    @Unique
    private static final String RENDER = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;"
            + "Lnet/minecraft/client/util/math/MatrixStack;"
            + "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;"
            + "Lnet/minecraft/client/render/state/CameraRenderState;)V";

    @ModifyExpressionValue(
            method = RENDER,
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/render/entity/LivingEntityRenderer;features:Ljava/util/List;"))
    private List<FeatureRenderer<S, M>> featuresHook(List<FeatureRenderer<S, M>> original, @Local(argsOnly = true) S state)
    {
        return shoreline$chamsTarget(state) == null ? original : Collections.emptyList();
    }

    @Unique
    private static Entity shoreline$chamsTarget(LivingEntityRenderState state)
    {
        ChamsModule chams = ChamsModule.getInstance();
        if (chams == null || !chams.isEnabled() || chams.mode.getValue() == ChamsModule.ChamsMode.NONE)
        {
            return null;
        }

        Entity entity = ((IEntityRenderState) state).getEntity();
        return entity != null && chams.isValid(entity) ? entity : null;
    }

    @Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void shoreline$hideVanillaNametagForPlayers(T entity, double squaredDistanceToCamera, CallbackInfoReturnable<Boolean> cir)
    {
        if (NametagsModule.INSTANCE.isEnabled() && entity instanceof PlayerEntity)
        {
            cir.setReturnValue(false);
        }
    }

    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;" +
                    "Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V",
            at = @At(value = "RETURN"))
    private void updateRenderStateHook_Post(T livingEntity, S livingEntityRenderState, float f, CallbackInfo ci)
    {
        if (livingEntity instanceof ChamsModule.PopEntity)
        {
            livingEntityRenderState.limbSwingAnimationProgress = livingEntity.limbAnimator.getAnimationProgress(1.0f);
            livingEntityRenderState.limbSwingAmplitude = livingEntity.limbAnimator.getAmplitude(1.0f);
        }
    }

    @Redirect(
            method = "getOverlay",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;hurt:Z"))
    private static boolean hurtHook(LivingEntityRenderState instance)
    {
        EntityHurtEvent event = new EntityHurtEvent();
        EventBus.INSTANCE.dispatch(event);
        if (event.isCanceled())
        {
            return false;
        }

        return instance.hurt;
    }
}

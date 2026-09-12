package net.shoreline.client.mixin.render.entity;

import net.minecraft.client.render.entity.EndCrystalEntityRenderer;
import net.minecraft.client.render.entity.state.EndCrystalEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.shoreline.client.impl.module.render.ModelsModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(EndCrystalEntityRenderer.class)
public class MixinEndCrystalEntityRenderer
{
    @Unique
    @SuppressWarnings("unused")
    private EndCrystalEntity last;

    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/decoration/EndCrystalEntity;Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;F)V",
            at = @At("HEAD"))
    private void updateRenderStateHook(EndCrystalEntity entity,
                                       EndCrystalEntityRenderState state,
                                       float tickDelta,
                                       CallbackInfo ci)
    {
        this.last = entity;
    }

    @Redirect(
            method = "render(Lnet/minecraft/client/render/entity/state/EndCrystalEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;scale(FFF)V"))
    private void hookScale(MatrixStack instance, float x, float y, float z)
    {
        float scale = ModelsModule.INSTANCE.isEnabled() ? ModelsModule.INSTANCE.getCrystalScale().getValue() : 1.0f;
        instance.scale(2.0f * scale, 2.0f * scale, 2.0f * scale);
    }
}

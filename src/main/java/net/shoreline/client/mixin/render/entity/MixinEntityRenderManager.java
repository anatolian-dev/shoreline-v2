package net.shoreline.client.mixin.render.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.shoreline.client.impl.imixin.IEntityRenderState;
import net.shoreline.client.impl.render.EntityRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderManager.class)
public abstract class MixinEntityRenderManager
{
    @WrapOperation(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/render/entity/EntityRenderer;render("
                            + "Lnet/minecraft/client/render/entity/state/EntityRenderState;"
                            + "Lnet/minecraft/client/util/math/MatrixStack;"
                            + "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;"
                            + "Lnet/minecraft/client/render/state/CameraRenderState;)V"))
    private void hookRenderEntity(EntityRenderer<?, ?> renderer,
                                  EntityRenderState state,
                                  MatrixStack matrices,
                                  OrderedRenderCommandQueue queue,
                                  CameraRenderState cameraState,
                                  Operation<Void> original)
    {
        EntityRenderContext.push(((IEntityRenderState) state).getEntity());
        original.call(renderer, state, matrices, queue, cameraState);
        EntityRenderContext.pop();
    }
}

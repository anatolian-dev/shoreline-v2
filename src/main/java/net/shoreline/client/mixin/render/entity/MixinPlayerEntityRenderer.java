package net.shoreline.client.mixin.render.entity;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;
import net.shoreline.client.impl.event.render.entity.PlayerTransformsEvent;
import net.shoreline.eventbus.EventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public class MixinPlayerEntityRenderer
{
    @Inject(method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V", at = @At(value = "TAIL"))
    private void hookUpdateRenderState(PlayerLikeEntity playerLikeEntity,
                                       PlayerEntityRenderState playerEntityRenderState,
                                       float f,
                                       CallbackInfo ci)
    {
        if (MinecraftClient.getInstance().player != null && playerLikeEntity.getId() == MinecraftClient.getInstance().player.getId())
        {
            PlayerTransformsEvent event = new PlayerTransformsEvent(f);
            EventBus.INSTANCE.dispatch(event);
            if (event.isCanceled())
            {
                playerEntityRenderState.bodyYaw = event.getYaw();
                playerEntityRenderState.pitch = event.getPitch();
                playerEntityRenderState.relativeHeadYaw = 0.0f;
            }
        }
    }
}

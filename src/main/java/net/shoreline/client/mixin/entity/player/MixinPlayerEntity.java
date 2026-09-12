package net.shoreline.client.mixin.entity.player;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.shoreline.client.impl.event.entity.player.ReachEvent;
import net.shoreline.client.impl.event.entity.player.SprintResetEvent;
import net.shoreline.client.impl.event.entity.player.TravelEvent;
import net.shoreline.eventbus.EventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public class MixinPlayerEntity
{
    @Inject(method = "travel", at = @At(value = "HEAD"), cancellable = true)
    private void hookTravelPre(Vec3d movementInput, CallbackInfo ci)
    {
        if ((Object) this == MinecraftClient.getInstance().player)
        {
            TravelEvent.Pre travelEvent = new TravelEvent.Pre(movementInput);
            EventBus.INSTANCE.dispatch(travelEvent);
            if (travelEvent.isCanceled())
            {
                ci.cancel();
            }
        }
    }

    @Inject(method = "travel", at = @At(value = "TAIL"))
    private void hookTravelPost(Vec3d movementInput, CallbackInfo ci)
    {
        if ((Object) this == MinecraftClient.getInstance().player)
        {
            TravelEvent.Post travelEvent = new TravelEvent.Post(movementInput);
            EventBus.INSTANCE.dispatch(travelEvent);
        }
    }

    @Inject(method = "attack", at = @At("HEAD"))
    private void hookAttack$ResetSprint(Entity target, CallbackInfo ci)
    {
        PlayerEntity player = (PlayerEntity) (Object) this;
        if (!(player instanceof ClientPlayerEntity clientPlayer))
        {
            return;
        }
        if (clientPlayer != MinecraftClient.getInstance().player)
        {
            return;
        }

        SprintResetEvent sprintResetEvent = new SprintResetEvent();
        EventBus.INSTANCE.dispatch(sprintResetEvent);
        if (!sprintResetEvent.isCanceled())
        {
            Vec3d v = player.getVelocity();
            player.setVelocity(v.x * 0.6, v.y, v.z * 0.6);
            player.setSprinting(false);
        }
    }

    @Inject(method = "getBlockInteractionRange", at = @At(value = "RETURN"), cancellable = true)
    private void hookGetBlockInteractionRange(CallbackInfoReturnable<Double> cir)
    {
        if ((Object) this == MinecraftClient.getInstance().player)
        {
            final ReachEvent reachEvent = new ReachEvent();
            EventBus.INSTANCE.dispatch(reachEvent);
            if (reachEvent.isCanceled())
            {
                cir.cancel();
                cir.setReturnValue(cir.getReturnValueD() + reachEvent.getReach());
            }
        }

    }

    @Inject(method = "getEntityInteractionRange", at = @At(value = "RETURN"), cancellable = true)
    private void hookGetEntityInteractionRange(CallbackInfoReturnable<Double> cir)
    {
        if ((Object) this == MinecraftClient.getInstance().player)
        {
            final ReachEvent reachEvent = new ReachEvent();
            EventBus.INSTANCE.dispatch(reachEvent);
            if (reachEvent.isCanceled())
            {
                cir.cancel();
                cir.setReturnValue(cir.getReturnValueD() + reachEvent.getReach());
            }
        }
    }
}

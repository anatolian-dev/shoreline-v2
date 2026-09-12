package net.shoreline.client.mixin.render;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.entry.RegistryEntry;
import net.shoreline.client.impl.event.render.BlockLightEvent;
import net.shoreline.client.impl.event.render.NightVisionEvent;
import net.shoreline.eventbus.EventBus;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LightmapTextureManager.class)
public class MixinLightmapTextureManager
{
    @WrapMethod(method = "getBlockLightCoordinates")
    private static int getBlockLightCoordinatesHook(int light, Operation<Integer> original)
    {
        BlockLightEvent event = new BlockLightEvent();
        EventBus.INSTANCE.dispatch(event);
        if (event.isCanceled())
        {
            return event.getBlockLight();
        }

        return original.call(light);
    }


    @org.spongepowered.asm.mixin.injection.Redirect(method = "update", at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z", ordinal = 0))

    private boolean hookUpdate(ClientPlayerEntity instance, RegistryEntry<StatusEffect> registryEntry)
    {
        NightVisionEvent nightVisionEvent = new NightVisionEvent();
        EventBus.INSTANCE.dispatch(nightVisionEvent);
        return nightVisionEvent.isCanceled() || instance.hasStatusEffect(registryEntry);
    }


    @org.spongepowered.asm.mixin.injection.Redirect(method = "update", at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/GameRenderer;getNightVisionStrength(Lnet/minecraft/entity/LivingEntity;F)F"))

    private float hookUpdate$2(LivingEntity entity, float tickDelta)
    {
        NightVisionEvent nightVisionEvent = new NightVisionEvent();
        EventBus.INSTANCE.dispatch(nightVisionEvent);
        return nightVisionEvent.isCanceled() ? 1.0f : GameRenderer.getNightVisionStrength(entity, tickDelta);
    }
}

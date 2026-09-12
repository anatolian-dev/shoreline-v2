package net.shoreline.client.impl.module.movement;

import net.minecraft.entity.effect.StatusEffects;
import net.shoreline.client.api.config.BooleanConfig;
import net.shoreline.client.api.config.Config;
import net.shoreline.client.api.module.GuiCategory;
import net.shoreline.client.api.module.Toggleable;
import net.shoreline.client.impl.event.TickEvent;
import net.shoreline.eventbus.annotation.EventListener;

public class AntiLevitationModule extends Toggleable
{
    Config<Boolean> levitation = new BooleanConfig.Builder("Levitation")
            .setDefaultValue(true)
            .setDescription("Removes levitation effect")
            .build();

    Config<Boolean> slowFalling = new BooleanConfig.Builder("SlowFalling")
            .setDefaultValue(false)
            .setDescription("Removes slow falling effect")
            .build();

    public AntiLevitationModule()
    {
        super("AntiLevitation", "Cancels levitation and slow falling effects", GuiCategory.MOVEMENT);
    }

    @EventListener
    public void onTick(TickEvent.Pre event)
    {
        if (checkNull()) return;

        if (levitation.getValue() && mc.player.hasStatusEffect(StatusEffects.LEVITATION))
        {
            mc.player.removeStatusEffect(StatusEffects.LEVITATION);
        }

        if (slowFalling.getValue() && mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING))
        {
            mc.player.removeStatusEffect(StatusEffects.SLOW_FALLING);
        }
    }
}

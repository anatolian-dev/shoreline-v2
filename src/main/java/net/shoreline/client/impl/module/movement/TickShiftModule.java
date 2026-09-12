package net.shoreline.client.impl.module.movement;

import net.shoreline.client.api.config.Config;
import net.shoreline.client.api.config.NumberConfig;
import net.shoreline.client.api.module.GuiCategory;
import net.shoreline.client.api.module.Toggleable;
import net.shoreline.client.impl.event.TickEvent;
import net.shoreline.client.impl.event.render.RenderTickCounterEvent;
import net.shoreline.eventbus.annotation.EventListener;

public class TickShiftModule extends Toggleable
{
    Config<Float> timerConfig = new NumberConfig.Builder<Float>("Timer")
            .setMin(1.1f).setMax(5.0f).setDefaultValue(2.0f)
            .setDescription("Timer multiplier when discharging")
            .build();

    Config<Integer> maxTicksConfig = new NumberConfig.Builder<Integer>("MaxTicks")
            .setMin(5).setMax(60).setDefaultValue(25).setFormat(" ticks")
            .setDescription("Maximum accumulated ticks")
            .build();

    private int accumulatedTicks = 0;
    private boolean shifting = false;

    public TickShiftModule()
    {
        super("TickShift", "Stores ticks when idle and boosts speed when moving", GuiCategory.MOVEMENT);
    }

    @Override
    public String getModuleData()
    {
        return accumulatedTicks + " / " + maxTicksConfig.getValue();
    }

    @Override
    public void onDisable()
    {
        accumulatedTicks = 0;
        shifting = false;
    }

    @EventListener
    public void onTick(TickEvent.Pre event)
    {
        if (checkNull()) return;

        boolean moving = mc.player.getVelocity().horizontalLengthSquared() > 1.0e-5;

        if (moving)
        {
            if (accumulatedTicks > 0)
            {
                shifting = true;
                accumulatedTicks--;
            }
            else
            {
                shifting = false;
            }
        }
        else
        {
            shifting = false;
            if (accumulatedTicks < maxTicksConfig.getValue())
            {
                accumulatedTicks++;
            }
        }
    }

    @EventListener
    public void onTickCounter(RenderTickCounterEvent event)
    {
        if (shifting)
        {
            event.cancel();
            event.setTicks(timerConfig.getValue());
        }
    }
}

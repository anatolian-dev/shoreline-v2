package net.shoreline.client.impl.module.combat;

import net.shoreline.client.api.module.GuiCategory;
import net.shoreline.client.api.module.Toggleable;
import net.shoreline.client.impl.event.TickEvent;
import net.shoreline.client.impl.event.render.item.SwingAnimFactorEvent;
import net.shoreline.client.impl.imixin.IMinecraftClient;
import net.shoreline.eventbus.annotation.EventListener;

public class NoHitDelayModule extends Toggleable
{
    public NoHitDelayModule()
    {
        super("NoHitDelay", "Removes vanilla attack delay", GuiCategory.COMBAT);
    }

    @EventListener
    public void onTick(TickEvent.Pre event)
    {
        if (checkNull())
        {
            return;
        }
        ((IMinecraftClient) mc).setItemUseCooldown(0);
    }

    @EventListener
    public void onSwingAnimFactor(SwingAnimFactorEvent event)
    {
        event.cancel();
    }
}

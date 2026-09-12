package net.shoreline.client.impl.module.render;

import net.shoreline.client.api.config.BooleanConfig;
import net.shoreline.client.api.config.Config;
import net.shoreline.client.api.module.GuiCategory;
import net.shoreline.client.api.module.Toggleable;
import net.shoreline.client.impl.event.network.SwingHandEvent;
import net.shoreline.client.impl.event.particle.BlockBreakParticleEvent;
import net.shoreline.client.impl.module.world.SpeedMineModule;
import net.shoreline.eventbus.annotation.EventListener;

public class NoMineAnimationModule extends Toggleable
{
    Config<Boolean> noSwing = new BooleanConfig.Builder("NoSwing")
            .setDefaultValue(true)
            .setDescription("Prevents hand swing animation while mining")
            .build();

    Config<Boolean> noParticles = new BooleanConfig.Builder("NoParticles")
            .setDefaultValue(true)
            .setDescription("Prevents block break particles while mining")
            .build();

    public NoMineAnimationModule()
    {
        super("NoMineAnimation", "Disables client-side animations while mining blocks", GuiCategory.RENDER);
    }

    @EventListener
    public void onSwingHand(SwingHandEvent event)
    {
        if (noSwing.getValue() && SpeedMineModule.INSTANCE != null && SpeedMineModule.INSTANCE.getMainMiningBlock() != null)
        {
            event.cancel();
        }
    }

    @EventListener
    public void onBlockBreakParticle(BlockBreakParticleEvent event)
    {
        if (noParticles.getValue())
        {
            event.cancel();
        }
    }
}

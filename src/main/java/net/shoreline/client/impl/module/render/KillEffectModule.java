package net.shoreline.client.impl.module.render;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.shoreline.client.api.config.BooleanConfig;
import net.shoreline.client.api.config.Config;
import net.shoreline.client.api.config.EnumConfig;
import net.shoreline.client.api.module.GuiCategory;
import net.shoreline.client.impl.event.entity.EntityDeathEvent;
import net.shoreline.client.impl.module.impl.RenderModule;
import net.shoreline.eventbus.annotation.EventListener;

public class KillEffectModule extends RenderModule
{
    Config<EffectMode> modeConfig = new EnumConfig.Builder<EffectMode>("Mode")
            .setValues(EffectMode.values())
            .setDefaultValue(EffectMode.LIGHTNING)
            .setDescription("Effect to display on kill")
            .build();

    Config<Boolean> soundConfig = new BooleanConfig.Builder("Sound")
            .setDefaultValue(true)
            .setDescription("Plays sound with the kill effect")
            .build();

    public KillEffectModule()
    {
        super("KillEffect", "Renders visual effects when an enemy is killed", GuiCategory.RENDER);
    }

    @EventListener
    public void onEntityDeath(EntityDeathEvent event)
    {
        if (checkNull() || mc.world == null) return;

        if (event.getEntity() instanceof PlayerEntity player && player != mc.player)
        {
            double x = player.getX();
            double y = player.getY();
            double z = player.getZ();

            switch (modeConfig.getValue())
            {
                case LIGHTNING -> {
                    LightningEntity lightning = new LightningEntity(EntityType.LIGHTNING_BOLT, mc.world);
                    lightning.refreshPositionAfterTeleport(x, y, z);
                    lightning.setCosmetic(true);
                    mc.world.addEntity(lightning);

                    if (soundConfig.getValue())
                    {
                        mc.world.playSound(mc.player, x, y, z, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 1.0f, 1.0f);
                    }
                }
                case TOTEM -> {
                    for (int i = 0; i < 40; i++)
                    {
                        double vx = (Math.random() - 0.5) * 2.0;
                        double vy = Math.random() * 2.0;
                        double vz = (Math.random() - 0.5) * 2.0;
                        mc.particleManager.addParticle(ParticleTypes.TOTEM_OF_UNDYING, x, y + 1.0, z, vx, vy, vz);
                    }
                    if (soundConfig.getValue())
                    {
                        mc.world.playSound(mc.player, x, y, z, SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 1.0f, 1.0f);
                    }
                }
                case EXPLOSION -> {
                    mc.particleManager.addParticle(ParticleTypes.EXPLOSION_EMITTER, x, y + 1.0, z, 0.0, 0.0, 0.0);
                    if (soundConfig.getValue())
                    {
                        mc.world.playSound(mc.player, x, y, z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.0f, 1.0f);
                    }
                }
            }
        }
    }

    public enum EffectMode
    {
        LIGHTNING,
        TOTEM,
        EXPLOSION
    }
}

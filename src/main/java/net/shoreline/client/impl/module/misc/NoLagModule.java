package net.shoreline.client.impl.module.misc;

import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundFromEntityS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.shoreline.client.api.config.BooleanConfig;
import net.shoreline.client.api.config.Config;
import net.shoreline.client.api.config.NumberConfig;
import net.shoreline.client.api.module.GuiCategory;
import net.shoreline.client.api.module.Toggleable;
import net.shoreline.client.impl.event.network.PacketEvent;
import net.shoreline.client.impl.event.render.entity.RenderItemFrameEvent;
import net.shoreline.client.impl.event.world.WorldSkylightEvent;
import net.shoreline.eventbus.annotation.EventListener;

import java.util.HashSet;
import java.util.Set;

public class NoLagModule extends Toggleable
{
    Config<Boolean> noSkyLightLag = new BooleanConfig.Builder("Skylight")
            .setDescription("Prevents lag from skylight updates")
            .setDefaultValue(false).build();
    Config<Boolean> noItemFrameLag = new BooleanConfig.Builder("ItemFrame")
            .setDescription("Prevents lag from item frames")
            .setDefaultValue(false).build();
    Config<Boolean> noParticleLag = new BooleanConfig.Builder("Particle")
            .setDescription("Prevents lag from particle entities")
            .setDefaultValue(false).build();
    Config<Boolean> noSoundLag = new BooleanConfig.Builder("Sound")
            .setDescription("Prevents lag from sounds in the world")
            .setDefaultValue(true).build();
    Config<Boolean> soundThrottle = new BooleanConfig.Builder("SoundThrottle")
            .setDescription("Throttles excessive sound packets from lag machines")
            .setDefaultValue(true).build();
    Config<Integer> maxSoundsPerSec = new NumberConfig.Builder<Integer>("MaxSounds")
            .setMin(5).setMax(100).setDefaultValue(30)
            .setDescription("Maximum sounds allowed per second")
            .build();
    Config<Boolean> noAnvilLag = new BooleanConfig.Builder("Anvils")
            .setDescription("Prevents lag from anvil sounds")
            .setDefaultValue(true).build();

    private final Set<SoundEvent> equipSounds = new HashSet<>(Set.of(
            SoundEvents.ITEM_ARMOR_EQUIP_GENERIC.value(),
            SoundEvents.ITEM_ARMOR_EQUIP_ELYTRA.value(),
            SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(),
            SoundEvents.ITEM_ARMOR_EQUIP_DIAMOND.value(),
            SoundEvents.ITEM_ARMOR_EQUIP_IRON.value(),
            SoundEvents.ITEM_ARMOR_EQUIP_GOLD.value(),
            SoundEvents.ITEM_ARMOR_EQUIP_CHAIN.value(),
            SoundEvents.ITEM_ARMOR_EQUIP_LEATHER.value()
    ));

    private final Set<SoundEvent> anvilSounds = new HashSet<>(Set.of(
            SoundEvents.BLOCK_ANVIL_DESTROY,
            SoundEvents.BLOCK_ANVIL_USE,
            SoundEvents.BLOCK_ANVIL_LAND,
            SoundEvents.BLOCK_ANVIL_HIT,
            SoundEvents.BLOCK_ANVIL_FALL,
            SoundEvents.BLOCK_ANVIL_PLACE,
            SoundEvents.BLOCK_ANVIL_BREAK
    ));

    private long lastSoundResetTime = System.currentTimeMillis();
    private int soundCount = 0;

    public NoLagModule()
    {
        super("NoLag", "Prevents attempts to intentionally lag the game", GuiCategory.MISCELLANEOUS);
    }

    @EventListener
    public void onRenderSkylight(WorldSkylightEvent event)
    {
        if (noSkyLightLag.getValue())
        {
            event.cancel();
        }
    }

    @EventListener
    public void onRenderItemFrame(RenderItemFrameEvent event)
    {
        if (noItemFrameLag.getValue())
        {
            event.cancel();
        }
    }

    @EventListener
    public void onPacketInbound(PacketEvent.Inbound event)
    {
        if (event.getPacket() instanceof ParticleS2CPacket packet && noParticleLag.getValue())
        {
            if (packet.getCount() > 512)
            {
                event.cancel();
                return;
            }
        }

        if (noSoundLag.getValue())
        {
            long now = System.currentTimeMillis();
            if (now - lastSoundResetTime >= 1000)
            {
                lastSoundResetTime = now;
                soundCount = 0;
            }

            if (event.getPacket() instanceof PlaySoundFromEntityS2CPacket packet)
            {
                if (equipSounds.contains(packet.getSound().value()))
                {
                    event.cancel();
                    return;
                }

                if (soundThrottle.getValue())
                {
                    soundCount++;
                    if (soundCount > maxSoundsPerSec.getValue())
                    {
                        event.cancel();
                        return;
                    }
                }
            }
            else if (event.getPacket() instanceof PlaySoundS2CPacket packet)
            {
                if (noAnvilLag.getValue() && anvilSounds.contains(packet.getSound().value()))
                {
                    event.cancel();
                    return;
                }

                if (soundThrottle.getValue())
                {
                    soundCount++;
                    if (soundCount > maxSoundsPerSec.getValue())
                    {
                        event.cancel();
                        return;
                    }
                }
            }
        }
    }
}

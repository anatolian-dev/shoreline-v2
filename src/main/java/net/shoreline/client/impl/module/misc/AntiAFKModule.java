package net.shoreline.client.impl.module.misc;

import net.minecraft.util.Hand;
import net.shoreline.client.api.config.BooleanConfig;
import net.shoreline.client.api.config.Config;
import net.shoreline.client.api.config.NumberConfig;
import net.shoreline.client.api.math.NanoTimer;
import net.shoreline.client.api.math.Timer;
import net.shoreline.client.api.module.GuiCategory;
import net.shoreline.client.api.module.Toggleable;
import net.shoreline.client.impl.event.TickEvent;
import net.shoreline.eventbus.annotation.EventListener;

import java.util.Random;

public class AntiAFKModule extends Toggleable
{
    Config<Integer> delayConfig = new NumberConfig.Builder<Integer>("Delay")
            .setMin(5).setMax(120).setDefaultValue(15).setFormat("s")
            .setDescription("Delay between anti-AFK actions")
            .build();

    Config<Boolean> swing = new BooleanConfig.Builder("Swing")
            .setDefaultValue(true)
            .setDescription("Swings hand periodically")
            .build();

    Config<Boolean> rotate = new BooleanConfig.Builder("Rotate")
            .setDefaultValue(true)
            .setDescription("Rotates slightly periodically")
            .build();

    Config<Boolean> jump = new BooleanConfig.Builder("Jump")
            .setDefaultValue(false)
            .setDescription("Jumps periodically")
            .build();

    private final Timer actionTimer = new NanoTimer();
    private final Random random = new Random();

    public AntiAFKModule()
    {
        super("AntiAFK", "Prevents getting kicked for idling", GuiCategory.MISCELLANEOUS);
    }

    @EventListener
    public void onTick(TickEvent.Pre event)
    {
        if (checkNull()) return;

        if (actionTimer.hasPassed(delayConfig.getValue() * 1000L))
        {
            if (swing.getValue())
            {
                mc.player.swingHand(Hand.MAIN_HAND);
            }

            if (rotate.getValue())
            {
                float yawOffset = (random.nextFloat() - 0.5f) * 20.0f;
                float pitchOffset = (random.nextFloat() - 0.5f) * 10.0f;
                mc.player.setYaw(mc.player.getYaw() + yawOffset);
                mc.player.setPitch(Math.max(-90.0f, Math.min(90.0f, mc.player.getPitch() + pitchOffset)));
            }

            if (jump.getValue() && mc.player.isOnGround())
            {
                mc.player.jump();
            }

            actionTimer.reset();
        }
    }
}

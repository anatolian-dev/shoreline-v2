package net.shoreline.client.impl.module.combat;

import net.minecraft.entity.Entity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.shoreline.client.api.config.Config;
import net.shoreline.client.api.config.EnumConfig;
import net.shoreline.client.api.config.NumberConfig;
import net.shoreline.client.api.math.NanoTimer;
import net.shoreline.client.api.math.Timer;
import net.shoreline.client.api.module.GuiCategory;
import net.shoreline.client.api.module.Toggleable;
import net.shoreline.client.impl.Managers;
import net.shoreline.client.impl.event.TickEvent;
import net.shoreline.client.impl.imixin.IMinecraftClient;
import net.shoreline.eventbus.annotation.EventListener;

public class TriggerModule extends Toggleable
{
    Config<TriggerMode> modeConfig = new EnumConfig.Builder<TriggerMode>("Mode")
            .setValues(TriggerMode.values())
            .setDefaultValue(TriggerMode.MOUSE_BUTTON)
            .build();
    Config<Float> attackSpeedConfig = new NumberConfig.Builder<Float>("AttackSpeed")
            .setMin(0.1f).setMax(20.0f).setDefaultValue(8.0f)
            .build();
    Config<Float> randomSpeedConfig = new NumberConfig.Builder<Float>("RandomSpeed")
            .setMin(0.1f).setMax(10.0f).setDefaultValue(2.0f)
            .build();

    private final Timer triggerTimer = new NanoTimer();

    public TriggerModule()
    {
        super("Trigger", "Automatically attacks entities in the crosshair", GuiCategory.COMBAT);
        registerConfigs(modeConfig, attackSpeedConfig, randomSpeedConfig);
    }

    @EventListener
    public void onTick(TickEvent.Pre event)
    {
        if (checkNull())
        {
            return;
        }

        boolean buttonDown = switch (modeConfig.getValue())
        {
            case MOUSE_BUTTON -> mc.options.attackKey.isPressed();
            case MOUSE_OVER ->
            {
                if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.ENTITY)
                {
                    yield false;
                }
                EntityHitResult entityHit = (EntityHitResult) mc.crosshairTarget;
                Entity crosshairEntity = entityHit.getEntity();
                yield !Managers.SOCIAL.isFriend(crosshairEntity);
            }
            case MOUSE_CLICK -> true;
        };

        double d = Math.random() * randomSpeedConfig.getValue() * 2.0 - randomSpeedConfig.getValue();
        long delay = (long) (1000.0 - Math.max(attackSpeedConfig.getValue() + d, 0.5) * 50.0);

        if (buttonDown && triggerTimer.hasPassed(delay))
        {
            ((IMinecraftClient) mc).hookDoAttack();
            ((IMinecraftClient) mc).setItemUseCooldown(0);
            triggerTimer.reset();
        }
    }

    public enum TriggerMode
    {
        MOUSE_BUTTON,
        MOUSE_OVER,
        MOUSE_CLICK
    }
}

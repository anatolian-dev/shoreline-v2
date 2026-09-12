package net.shoreline.client.impl.module.misc;

import net.minecraft.entity.player.PlayerEntity;
import net.shoreline.client.api.config.BooleanConfig;
import net.shoreline.client.api.config.Config;
import net.shoreline.client.api.config.StringConfig;
import net.shoreline.client.api.module.GuiCategory;
import net.shoreline.client.api.module.Toggleable;
import net.shoreline.client.impl.Managers;
import net.shoreline.client.impl.event.entity.EntityDeathEvent;
import net.shoreline.eventbus.annotation.EventListener;

public class AutoGGModule extends Toggleable
{
    Config<String> messageConfig = new StringConfig.Builder("Message")
            .setDefaultValue("gg {name}!")
            .setDescription("The message to send on player death")
            .build();

    Config<Boolean> onlyTarget = new BooleanConfig.Builder("OnlyTarget")
            .setDefaultValue(true)
            .setDescription("Only send GG if the dead player was targeted")
            .build();

    public AutoGGModule()
    {
        super("AutoGG", "Sends a GG message in chat when a player dies", GuiCategory.MISCELLANEOUS);
    }

    @EventListener
    public void onEntityDeath(EntityDeathEvent event)
    {
        if (checkNull() || mc.getNetworkHandler() == null) return;

        if (event.getEntity() instanceof PlayerEntity player && player != mc.player)
        {
            if (onlyTarget.getValue())
            {
                PlayerEntity currentTarget = Managers.TARGETING.getTarget();
                if (currentTarget == null || !currentTarget.equals(player))
                {
                    return;
                }
            }
            else if (player.squaredDistanceTo(mc.player) > 625.0) // 25 blocks
            {
                return;
            }

            String msg = messageConfig.getValue().replace("{name}", player.getName().getString());
            mc.getNetworkHandler().sendChatMessage(msg);
        }
    }
}

package net.shoreline.client.impl.module.combat;

import net.minecraft.item.BowItem;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.shoreline.client.api.config.Config;
import net.shoreline.client.api.config.NumberConfig;
import net.shoreline.client.api.module.GuiCategory;
import net.shoreline.client.api.module.Toggleable;
import net.shoreline.client.impl.Managers;
import net.shoreline.client.impl.event.TickEvent;
import net.shoreline.client.impl.rotation.Rotation;
import net.shoreline.eventbus.annotation.EventListener;

public class SelfBowModule extends Toggleable
{
    Config<Integer> pullTicksConfig = new NumberConfig.Builder<Integer>("PullTicks")
            .setMin(3).setMax(15).setDefaultValue(4).setFormat(" ticks")
            .setDescription("How many ticks to charge the bow before releasing")
            .build();

    private int bowTicks = 0;

    public SelfBowModule()
    {
        super("SelfBow", "Shoots positive tipped arrows upward to hit yourself", GuiCategory.COMBAT);
    }

    @Override
    public void onEnable()
    {
        bowTicks = 0;
    }

    @EventListener
    public void onTick(TickEvent.Pre event)
    {
        if (checkNull())
        {
            disable();
            return;
        }

        boolean holdingBow = mc.player.getMainHandStack().isOf(Items.BOW)
                || mc.player.getOffHandStack().isOf(Items.BOW);

        if (!holdingBow)
        {
            int bowSlot = -1;
            for (int i = 0; i < 9; i++)
            {
                if (mc.player.getInventory().getStack(i).isOf(Items.BOW))
                {
                    bowSlot = i;
                    break;
                }
            }

            if (bowSlot == -1)
            {
                disable();
                return;
            }

            Managers.INVENTORY.setSelectedSlot(bowSlot);
        }

        Hand hand = mc.player.getMainHandStack().isOf(Items.BOW) ? Hand.MAIN_HAND : Hand.OFF_HAND;

        Managers.ROTATION.setSilentRotation(new Rotation(mc.player.getYaw(), -90.0f));

        if (!mc.player.isUsingItem())
        {
            mc.interactionManager.interactItem(mc.player, hand);
            bowTicks = 0;
        }
        else
        {
            bowTicks++;
            if (bowTicks >= pullTicksConfig.getValue())
            {
                mc.interactionManager.stopUsingItem(mc.player);
                disable();
            }
        }
    }
}

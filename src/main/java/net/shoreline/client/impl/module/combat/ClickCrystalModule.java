package net.shoreline.client.impl.module.combat;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.shoreline.client.api.config.BooleanConfig;
import net.shoreline.client.api.config.Config;
import net.shoreline.client.api.module.GuiCategory;
import net.shoreline.client.api.module.Toggleable;
import net.shoreline.client.impl.event.TickEvent;
import net.shoreline.eventbus.annotation.EventListener;

public class ClickCrystalModule extends Toggleable
{
    Config<Boolean> autoAttack = new BooleanConfig.Builder("AutoAttack")
            .setDefaultValue(true)
            .setDescription("Automatically attacks crystal when clicking it")
            .build();

    public ClickCrystalModule()
    {
        super("ClickCrystal", "Automatically places crystals when clicking obsidian or bedrock", GuiCategory.COMBAT);
    }

    @EventListener
    public void onTick(TickEvent.Pre event)
    {
        if (checkNull()) return;

        boolean holdingCrystal = mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)
                || mc.player.getOffHandStack().isOf(Items.END_CRYSTAL);

        if (!holdingCrystal) return;

        Hand hand = mc.player.getMainHandStack().isOf(Items.END_CRYSTAL) ? Hand.MAIN_HAND : Hand.OFF_HAND;

        if (mc.options.useKey.isPressed() && mc.crosshairTarget instanceof BlockHitResult hitResult)
        {
            BlockPos pos = hitResult.getBlockPos();
            BlockState state = mc.world.getBlockState(pos);

            if (state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK))
            {
                BlockPos up = pos.up();
                if (mc.world.getBlockState(up).isAir())
                {
                    mc.interactionManager.interactBlock(mc.player, hand, hitResult);
                }
            }
        }

        if (autoAttack.getValue() && mc.options.attackKey.isPressed() && mc.crosshairTarget instanceof EntityHitResult hitResult)
        {
            if (hitResult.getEntity() instanceof EndCrystalEntity crystal)
            {
                mc.interactionManager.attackEntity(mc.player, crystal);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }
}

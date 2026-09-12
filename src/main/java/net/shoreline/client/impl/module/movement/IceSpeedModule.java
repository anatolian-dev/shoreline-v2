package net.shoreline.client.impl.module.movement;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.shoreline.client.api.config.Config;
import net.shoreline.client.api.config.NumberConfig;
import net.shoreline.client.api.module.GuiCategory;
import net.shoreline.client.api.module.Toggleable;
import net.shoreline.client.impl.event.network.PlayerMoveEvent;
import net.shoreline.eventbus.annotation.EventListener;

public class IceSpeedModule extends Toggleable
{
    Config<Float> speedConfig = new NumberConfig.Builder<Float>("Speed")
            .setMin(1.0f).setMax(5.0f).setDefaultValue(1.5f)
            .setDescription("Speed multiplier when walking on ice")
            .build();

    public IceSpeedModule()
    {
        super("IceSpeed", "Increases movement speed on ice blocks", GuiCategory.MOVEMENT);
    }

    @EventListener
    public void onPlayerMove(PlayerMoveEvent event)
    {
        if (checkNull() || !mc.player.isOnGround()) return;

        BlockPos pos = mc.player.getVelocityAffectingPos();
        BlockState state = mc.world.getBlockState(pos);

        if (state.isOf(Blocks.ICE)
                || state.isOf(Blocks.PACKED_ICE)
                || state.isOf(Blocks.BLUE_ICE)
                || state.isOf(Blocks.FROSTED_ICE))
        {
            Vec3d movement = event.getMovement();
            float factor = speedConfig.getValue();
            event.setMovement(new Vec3d(movement.x * factor, movement.y, movement.z * factor));
        }
    }
}

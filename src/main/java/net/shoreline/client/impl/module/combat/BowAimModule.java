package net.shoreline.client.impl.module.combat;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;
import net.shoreline.client.api.config.BooleanConfig;
import net.shoreline.client.api.config.Config;
import net.shoreline.client.api.config.NumberConfig;
import net.shoreline.client.api.module.GuiCategory;
import net.shoreline.client.api.module.Toggleable;
import net.shoreline.client.impl.Managers;
import net.shoreline.client.impl.event.TickEvent;
import net.shoreline.client.impl.module.combat.util.MovementExtrapolation;
import net.shoreline.client.impl.rotation.Rotation;
import net.shoreline.eventbus.annotation.EventListener;

public class BowAimModule extends Toggleable
{
    Config<Float> rangeConfig = new NumberConfig.Builder<Float>("Range")
            .setMin(5.0f).setMax(60.0f).setDefaultValue(35.0f).setFormat("m")
            .setDescription("Maximum range to aim at targets")
            .build();
    Config<Boolean> predictConfig = new BooleanConfig.Builder("Predict")
            .setDescription("Predicts moving target positions using block collision physics")
            .setDefaultValue(true).build();

    public BowAimModule()
    {
        super("BowAim", "Automatically aims bow trajectory at targets", GuiCategory.COMBAT);
    }

    @EventListener
    public void onTick(TickEvent.Pre event)
    {
        if (checkNull()) return;

        if (!mc.player.isUsingItem() || !mc.player.getActiveItem().isOf(Items.BOW))
        {
            return;
        }

        PlayerEntity target = Managers.TARGETING.getClosestTarget(rangeConfig.getValue());
        if (target == null)
        {
            return;
        }

        int useDuration = mc.player.getItemUseTime();
        float pullProgress = BowItem.getPullProgress(useDuration);
        if (pullProgress < 0.15f)
        {
            return;
        }

        float speed = pullProgress * 3.0f;
        double directDx = target.getX() - mc.player.getX();
        double directDz = target.getZ() - mc.player.getZ();
        double directDist = Math.sqrt(directDx * directDx + directDz * directDz);

        Vec3d targetPos = target.getEntityPos();
        if (predictConfig.getValue())
        {
            int ticks = (int) Math.round(directDist / Math.max(0.1, speed));
            if (ticks > 0)
            {
                targetPos = MovementExtrapolation.extrapolatePosition(mc.world,
                        box -> mc.world.getBlockCollisions(null, box),
                        target.getVelocity(),
                        target.getBoundingBox(),
                        ticks);
            }
        }

        double dx = targetPos.x - mc.player.getX();
        double dz = targetPos.z - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        double dy = (targetPos.y + target.getEyeHeight(target.getPose()) * 0.5) - mc.player.getEyeY();

        double gravity = 0.05;
        double v2 = speed * speed;
        double v4 = v2 * v2;
        double root = v4 - gravity * (gravity * dist * dist + 2.0 * dy * v2);

        float pitch;
        if (root >= 0)
        {
            double pitchRad = Math.atan((v2 - Math.sqrt(root)) / (gravity * dist));
            pitch = (float) -Math.toDegrees(pitchRad);
        }
        else
        {
            pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
        }

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);

        Managers.ROTATION.setSilentRotation(new Rotation(yaw, pitch));
    }
}

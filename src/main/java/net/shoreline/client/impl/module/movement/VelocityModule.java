package net.shoreline.client.impl.module.movement;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;
import net.shoreline.client.api.config.*;
import net.shoreline.client.api.module.GuiCategory;
import net.shoreline.client.api.module.Toggleable;
import net.shoreline.client.impl.event.entity.PushEvent;
import net.shoreline.client.impl.event.network.ExplosionEvent;
import net.shoreline.client.impl.event.network.PacketEvent;
import net.shoreline.client.impl.event.network.PushOutOfBlocksEvent;
import net.shoreline.client.impl.imixin.IEntityVelocityUpdateS2CPacket;
import net.shoreline.client.impl.module.combat.util.PhaseUtil;
import net.shoreline.client.util.text.Formatter;
import net.shoreline.eventbus.annotation.EventListener;

public class VelocityModule extends Toggleable
{
    Config<Boolean> cancelKnockback = new BooleanConfig.Builder("Knockback")
            .setDefaultValue(true)
            .setDescription ("if u take knocback")
            .build();

    Config<Boolean> cancelExplosion = new BooleanConfig.Builder("Explosion")
            .setDefaultValue(true)
            .setDescription ("if u take kb from explostion or not")
            .build();

    Config<VelocityMode> modeConfig = new EnumConfig.Builder<VelocityMode>("Mode")
            .setValues(VelocityMode.values())
            .setDefaultValue(VelocityMode.NORMAL)
            .setDescription ("mode for knowback config")
            .build();

    Config<Boolean> noPushEntitiesConfig = new BooleanConfig.Builder("Entities")
            .setDefaultValue(false)
            .setDescription ("if u take kb from entites")
            .build();

    Config<Boolean> noPushBlocksConfig = new BooleanConfig.Builder("Blocks")
            .setDefaultValue(false)
            .setDescription ("take kb only in block (use this on 2b2t)")
            .build();

    Config<Boolean> noPushLiquidsConfig = new BooleanConfig.Builder("Liquid")
            .setDefaultValue(false)
            .setDescription (" if u take kb from liquid like wter")
            .build();

    Config<Void> noPushGroup = new ConfigGroup.Builder("NoPush")
            .addAll(noPushEntitiesConfig, noPushBlocksConfig, noPushLiquidsConfig)
            .setDescription (" prevent getting pushed by entities")
            .build();

    Config<Boolean> fishhookConfig = new BooleanConfig.Builder("NoFishhook")
            .setDefaultValue(false)
            .setDescription ("no fishhook kb")
            .build();

    Config<Integer> horizontalConfig = new NumberConfig.Builder<Integer>("Horizontal")
            .setDefaultValue(0).setMin(0).setMax(100).setFormat("%")
            .setVisible(() -> modeConfig.getValue() == VelocityMode.NORMAL)
            .setDescription ("your horizontal kb")
            .build();

    Config<Integer> verticalConfig = new NumberConfig.Builder<Integer>("Vertical")
            .setDefaultValue(0).setMin(0).setMax(100).setFormat("%")
            .setVisible(() -> modeConfig.getValue() == VelocityMode.NORMAL)
            .setDescription ("your vertical kb")
            .build();

    Config<Boolean> groundOnlyConfig = new BooleanConfig.Builder("GroundOnly")
            .setVisible(() -> modeConfig.getValue().equals(VelocityMode.WALLS))
            .setDefaultValue(false)
            .setDescription ("kb only apply to ground")
            .build();

    Config<Boolean> concealConfig = new BooleanConfig.Builder("Conceal")
            .setDefaultValue(false)
            .setDescription ("conceal")
            .build();

    private boolean concealVelocity;

    public VelocityModule()
    {
        super("Velocity", new String[]{"AntiKB"}, "Prevents knockback", GuiCategory.MOVEMENT);
    }

    @Override
    public String getModuleData()
    {
        if (modeConfig.getValue() == VelocityMode.NORMAL)
        {
            return String.format("H:%s%%, V:%s%%",
                    DECIMAL.format(horizontalConfig.getValue()),
                    DECIMAL.format(verticalConfig.getValue()));
        }
        return Formatter.formatEnum(modeConfig.getValue());
    }

    @Override
    public void onDisable()
    {
        concealVelocity = false;
    }

    @EventListener
    public void onPacketInbound(PacketEvent.Inbound event)
    {
        if (checkNull()) return;


        if (event.getPacket() instanceof EntityVelocityUpdateS2CPacket packet
                && packet.getEntityId() == mc.player.getId())
        {
            Vec3d pv = packet.getVelocity();
            if (concealVelocity && pv.x == 0 && pv.y == 0 && pv.z == 0)
            {
                concealVelocity = false;
                return;
            }

            if (!cancelKnockback.getValue()) return;

            if (shouldCancelKnockback())
            {
                event.cancel();
                mc.player.setVelocity(0, mc.player.getVelocity().y, 0);
            }
            else if (modeConfig.getValue() == VelocityMode.NORMAL)
            {
                Vec3d v = packet.getVelocity();
                double x = v.x * (horizontalConfig.getValue() / 100.0f);
                double y = v.y * (verticalConfig.getValue() / 100.0f);
                double z = v.z * (horizontalConfig.getValue() / 100.0f);

                ((IEntityVelocityUpdateS2CPacket) packet).setVelocity(new Vec3d(x, y, z));
            }
        }


        if (event.getPacket() instanceof ExplosionS2CPacket && cancelExplosion.getValue())
        {
            if (shouldCancelExplosions())
            {
                event.cancel();
            }
        }

        if (event.getPacket() instanceof PlayerPositionLookS2CPacket && concealConfig.getValue())
        {
            concealVelocity = true;
        }


        if (event.getPacket() instanceof EntityStatusS2CPacket packet
                && packet.getStatus() == EntityStatuses.PULL_HOOKED_ENTITY && fishhookConfig.getValue())
        {
            Entity entity = packet.getEntity(mc.world);
            if (entity instanceof FishingBobberEntity hook && hook.getHookedEntity() == mc.player)
            {
                event.cancel();
            }
        }
    }

    @EventListener
    public void onExplosion(ExplosionEvent event)
    {
        if (!cancelExplosion.getValue()) return;

        if (shouldCancelExplosions())
        {
            event.cancel();
            event.setPlayerVelocity(Vec3d.ZERO);
        }
        else if (modeConfig.getValue() == VelocityMode.NORMAL)
        {
            Vec3d kb = event.getPlayerVelocity();

            double x = kb.x * (horizontalConfig.getValue() / 100.0f);
            double y = kb.y * (verticalConfig.getValue() / 100.0f);
            double z = kb.z * (horizontalConfig.getValue() / 100.0f);

            event.cancel();
            event.setPlayerVelocity(new Vec3d(x, y, z));
        }
    }

    @EventListener
    public void onPushOutOfBlocks(PushOutOfBlocksEvent event)
    {
        if (noPushBlocksConfig.getValue()) event.cancel();
    }

    @EventListener
    public void onPushEntity(PushEvent.Entity event)
    {
        if (noPushEntitiesConfig.getValue()) event.cancel();
    }

    @EventListener
    public void onPushLiquid(PushEvent.Liquid event)
    {
        if (noPushLiquidsConfig.getValue()) event.cancel();
    }


    Config<Boolean> phaseOnlyConfig = new BooleanConfig.Builder("PhaseOnly")
            .setDefaultValue(false)
            .setDescription("Only cancels velocity when intersecting block collisions")
            .build();

    private boolean shouldCancelKnockback()
    {
        if (phaseOnlyConfig.getValue() && !isInBlockCollision())
        {
            return false;
        }

        return switch (modeConfig.getValue())
        {
            case WALLS -> {
                boolean inside = PhaseUtil.isInsideBlock(mc.player);
                boolean colliding = mc.player.horizontalCollision || mc.player.verticalCollision;

                yield (inside || colliding) && (!groundOnlyConfig.getValue() || mc.player.isOnGround());
            }

            case GRIM_V2 -> isInBlockCollision() || PhaseUtil.isInsideBlock(mc.player) || mc.player.horizontalCollision;

            case JUMP -> {
                if (mc.player.isOnGround())
                {
                    mc.player.jump();
                }
                yield false;
            }

            case NORMAL -> horizontalConfig.getValue() == 0 && verticalConfig.getValue() == 0;
        };
    }

    private boolean shouldCancelExplosions()
    {
        if (phaseOnlyConfig.getValue() && !isInBlockCollision())
        {
            return false;
        }

        return switch (modeConfig.getValue())
        {
            case WALLS, GRIM_V2 -> isInBlockCollision() || PhaseUtil.isInsideBlock(mc.player) || mc.player.horizontalCollision;

            case JUMP -> false;

            case NORMAL -> horizontalConfig.getValue() == 0 && verticalConfig.getValue() == 0;
        };
    }

    private boolean isInBlockCollision()
    {
        if (mc.player == null || mc.world == null) return false;
        return mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox()).iterator().hasNext();
    }

    private enum VelocityMode
    {
        NORMAL,
        WALLS,
        GRIM_V2,
        JUMP
    }
}

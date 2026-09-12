package net.shoreline.client.impl.module.movement;

import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.Vec3d;
import net.shoreline.client.api.config.BooleanConfig;
import net.shoreline.client.api.config.Config;
import net.shoreline.client.api.config.EnumConfig;
import net.shoreline.client.api.config.NumberConfig;
import net.shoreline.client.api.module.GuiCategory;
import net.shoreline.client.api.module.Toggleable;
import net.shoreline.client.impl.event.network.PlayerMoveEvent;
import net.shoreline.client.util.item.EnchantUtil;
import net.shoreline.eventbus.annotation.EventListener;

public class FastSwimModule extends Toggleable {
    Config<SwimMode> modeConfig = new EnumConfig.Builder<SwimMode>("Mode")
            .setValues(SwimMode.values())
            .setDefaultValue(SwimMode.VANILLA)
            .setDescription("The mode for swimming acceleration")
            .build();

    Config<Float> waterSpeedConfig = new NumberConfig.Builder<Float>("WaterSpeed")
            .setMin(1.0f).setMax(10.0f).setDefaultValue(1.0f)
            .setDescription("Speed multiplier for moving through water")
            .build();

    Config<Float> lavaSpeedConfig = new NumberConfig.Builder<Float>("LavaSpeed")
            .setMin(1.0f).setMax(10.0f).setDefaultValue(1.0f)
            .setDescription("Speed multiplier for moving through lava")
            .build();

    Config<Boolean> verticalConfig = new BooleanConfig.Builder("Vertical")
            .setDefaultValue(true)
            .setDescription("Allows fast vertical movement in liquids")
            .build();

    Config<Boolean> elytraConfig = new BooleanConfig.Builder("Elytra")
            .setDefaultValue(false)
            .setDescription("Applies elytra speed when moving through liquids")
            .build();

    Config<Float> elytraSpeedConfig = new NumberConfig.Builder<Float>("ElytraSpeed")
            .setMin(1.0f).setMax(10.0f).setDefaultValue(1.0f)
            .setVisible(elytraConfig::getValue)
            .setDescription("Speed multiplier when using elytra in liquids")
            .build();

    Config<Boolean> depthStriderConfig = new BooleanConfig.Builder("DepthStrider")
            .setDefaultValue(false)
            .setDescription("If disabled, module won't work if you have Depth Strider enchantment")
            .build();

    public FastSwimModule() {
        super("FastSwim", "Move faster in liquids", GuiCategory.MOVEMENT);
    }

    @EventListener
    public void onPlayerMove(PlayerMoveEvent event) {
        if (checkNull()) return;

        if (!depthStriderConfig.getValue() && EnchantUtil.getLevel(Enchantments.DEPTH_STRIDER, mc.player.getEquippedStack(EquipmentSlot.FEET)) > 0) {
            return;
        }

        if (!mc.player.isSubmergedIn(FluidTags.WATER) && !mc.player.isSubmergedIn(FluidTags.LAVA)) {
            return;
        }

        Vec3d vel = event.getMovement();
        double x = vel.x;
        double y = vel.y;
        double z = vel.z;

        if (mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA) && elytraConfig.getValue()) {
            event.cancel();
            float eSpeed = elytraSpeedConfig.getValue();

            double nextY = y;
            if (verticalConfig.getValue()) {
                if (mc.options.jumpKey.isPressed()) nextY = 0.16;
                else if (mc.options.sneakKey.isPressed()) nextY = -0.12;
            }

            event.setMovement(new Vec3d(x * eSpeed, nextY, z * eSpeed));
            return;
        }

        float speed = mc.player.isSubmergedIn(FluidTags.WATER) ? waterSpeedConfig.getValue() : lavaSpeedConfig.getValue();
        double nextX = x;
        double nextZ = z;

        switch (modeConfig.getValue()) {
            case VANILLA -> {
                nextX *= speed;
                nextZ *= speed;
            }
            case NORMAL -> {
                Vec3d dir = getStrafeDir(speed / 10.0f);
                nextX = dir.x;
                nextZ = dir.z;
            }
        }

        double nextY = y;
        if (verticalConfig.getValue()) {
            if (mc.options.jumpKey.isPressed()) {
                nextY = 0.16;
                mc.player.setVelocity(mc.player.getVelocity().x, 0.16, mc.player.getVelocity().z);
            } else if (mc.options.sneakKey.isPressed()) {
                nextY = -0.12;
                mc.player.setVelocity(mc.player.getVelocity().x, -0.12, mc.player.getVelocity().z);
            }
        }

        event.cancel();
        event.setMovement(new Vec3d(nextX, nextY, nextZ));
    }

    private Vec3d getStrafeDir(float speed) {
        float forward = mc.player.input.getMovementInput().y;
        float strafe = mc.player.input.getMovementInput().x;
        float yaw = mc.player.getYaw();

        if (forward == 0.0f && strafe == 0.0f) {
            return Vec3d.ZERO;
        } else if (forward != 0.0f) {
            if (strafe > 0.0f) {
                yaw += (forward > 0.0f ? -45 : 45);
            } else if (strafe < 0.0f) {
                yaw += (forward > 0.0f ? 45 : -45);
            }
            strafe = 0.0f;
            if (forward > 0.0f) {
                forward = 1.0f;
            } else if (forward < 0.0f) {
                forward = -1.0f;
            }
        }

        double cos = Math.cos(Math.toRadians(yaw + 90.0f));
        double sin = Math.sin(Math.toRadians(yaw + 90.0f));
        return new Vec3d(forward * speed * cos + strafe * speed * sin, 0, forward * speed * sin - strafe * speed * cos);
    }

    public enum SwimMode {
        VANILLA,
        NORMAL
    }
}

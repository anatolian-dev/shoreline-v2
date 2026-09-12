package net.shoreline.client.impl.module.movement;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.Vec3d;
import net.shoreline.client.api.config.BooleanConfig;
import net.shoreline.client.api.config.Config;
import net.shoreline.client.api.config.NumberConfig;
import net.shoreline.client.api.module.GuiCategory;
import net.shoreline.client.api.module.Toggleable;
import net.shoreline.client.impl.event.TickEvent;
import net.shoreline.client.impl.event.network.PacketEvent;
import net.shoreline.client.impl.event.network.PlayerMoveEvent;
import net.shoreline.eventbus.annotation.EventListener;

import java.util.HashSet;
import java.util.Set;

public class PacketFlyModule extends Toggleable {
    private final Config<Double> horizontalSpeed = new NumberConfig.Builder<Double>("Horizontal Speed")
            .setMin(0.0).setMax(20.0).setDefaultValue(5.2).setDescription("Horizontal speed in blocks per second.").build();
    private final Config<Double> verticalSpeed = new NumberConfig.Builder<Double>("Vertical Speed")
            .setMin(0.0).setMax(20.0).setDefaultValue(1.24).setDescription("Vertical speed in blocks per second.").build();
    private final Config<Boolean> sendTeleport = new BooleanConfig.Builder("Teleport")
            .setDefaultValue(true).setDescription("Sends teleport packets.").build();
    private final Config<Boolean> setYaw = new BooleanConfig.Builder("Set Yaw")
            .setDefaultValue(true).setDescription("Sets yaw client side.").build();
    private final Config<Boolean> setMove = new BooleanConfig.Builder("Set Move")
            .setDefaultValue(false).setDescription("Sets movement client side.").build();
    private final Config<Boolean> setPos = new BooleanConfig.Builder("Set Pos")
            .setDefaultValue(false).setDescription("Sets position client side.").build();
    private final Config<Boolean> setID = new BooleanConfig.Builder("Set ID")
            .setDefaultValue(false).setDescription("Updates teleport id when a position packet is received.").build();
    private final Config<Boolean> antiKick = new BooleanConfig.Builder("Anti Kick")
            .setDefaultValue(true).setDescription("Moves down occasionally to prevent kicks.").build();
    private final Config<Integer> downDelay = new NumberConfig.Builder<Integer>("Down Delay")
            .setMin(1).setMax(30).setDefaultValue(4).setDescription("How often you move down when not flying upwards.").build();
    private final Config<Integer> downDelayFlying = new NumberConfig.Builder<Integer>("Flying Down Delay")
            .setMin(1).setMax(30).setDefaultValue(10).setDescription("How often you move down when flying upwards.").build();
    private final Config<Boolean> invalidPacket = new BooleanConfig.Builder("Invalid Packet")
            .setDefaultValue(false).setDescription("Sends invalid movement packets.").build();

    private final Set<Packet<?>> packets = new HashSet<>();
    private int flightCounter = 0;
    private int teleportID = 0;

    public PacketFlyModule() {
        super("PacketFly", "Fly using packets.", GuiCategory.MOVEMENT);
    }

    @EventListener
    public void onUpdate(TickEvent.Pre event) {
        if (checkNull()) return;

        mc.player.setVelocity(0.0, 0.0, 0.0);

        boolean checkCollisionBoxes = !mc.world.getBlockCollisions(mc.player, mc.player.getBoundingBox().expand(-0.0625)).iterator().hasNext();
        double speed;

        if (mc.options.jumpKey.isPressed() && (checkCollisionBoxes || !(mc.player.input.getMovementInput().y != 0.0f || mc.player.input.getMovementInput().x != 0.0f))) {
            if (antiKick.getValue() && !checkCollisionBoxes) {
                speed = resetCounter(downDelayFlying.getValue()) ? -0.032 : verticalSpeed.getValue() / 20.0;
            } else {
                speed = verticalSpeed.getValue() / 20.0;
            }
        } else if (mc.options.sneakKey.isPressed()) {
            speed = verticalSpeed.getValue() / -20.0;
        } else {
            if (!checkCollisionBoxes) {
                speed = resetCounter(downDelay.getValue()) ? (antiKick.getValue() ? -0.04 : 0.0) : 0.0;
            } else {
                speed = 0.0;
            }
        }

        Vec3d hor = getHorizontalVelocity(horizontalSpeed.getValue());
        mc.player.setVelocity(hor.x, speed, hor.z);

        sendPackets(mc.player.getVelocity().x, mc.player.getVelocity().y, mc.player.getVelocity().z, sendTeleport.getValue());
    }

    @EventListener
    public void onMove(PlayerMoveEvent event) {
        if (setMove.getValue() && flightCounter != 0) {
            event.cancel();
            event.setMovement(mc.player.getVelocity());
        }
    }

    @EventListener
    public void onPacketSend(PacketEvent.Outbound event) {
        if (event.getPacket() instanceof PlayerMoveC2SPacket packet) {
            if (!packets.remove(packet)) {
                event.cancel();
            }
        }
    }

    @EventListener
    public void onPacketReceive(PacketEvent.Inbound event) {
        if (checkNull()) return;
        if (event.getPacket() instanceof PlayerPositionLookS2CPacket packet) {
            if (setYaw.getValue()) {
                mc.player.setYaw(mc.player.getYaw());
                mc.player.setPitch(mc.player.getPitch());
            }

            if (setID.getValue()) {
                teleportID = packet.teleportId();
            }
        }
    }

    private void sendPackets(double x, double y, double z, boolean teleport) {
        Vec3d playerPos = mc.player.getEntityPos();
        Vec3d nextPos = playerPos.add(x, y, z);
        Vec3d outOfBoundsVec = nextPos.add(0.0, 1500.0, 0.0);

        PlayerMoveC2SPacket.PositionAndOnGround posPacket = new PlayerMoveC2SPacket.PositionAndOnGround(
                nextPos.x, nextPos.y, nextPos.z, mc.player.isOnGround(), mc.player.horizontalCollision);
        sendAndRegister(posPacket);

        if (invalidPacket.getValue()) {
            PlayerMoveC2SPacket.PositionAndOnGround boundsPacket = new PlayerMoveC2SPacket.PositionAndOnGround(
                    outOfBoundsVec.x, outOfBoundsVec.y, outOfBoundsVec.z, mc.player.isOnGround(), mc.player.horizontalCollision);
            sendAndRegister(boundsPacket);
        }

        if (setPos.getValue()) {
            mc.player.setPosition(nextPos.x, nextPos.y, nextPos.z);
        }

        if (teleport) {
            sendQuietPacket(new TeleportConfirmC2SPacket(++teleportID));
        }
    }

    private void sendAndRegister(PlayerMoveC2SPacket packet) {
        packets.add(packet);
        sendQuietPacket(packet);
    }

    private boolean resetCounter(int limit) {
        if (++flightCounter >= limit) {
            flightCounter = 0;
            return true;
        }
        return false;
    }

    private Vec3d getHorizontalVelocity(double bps) {
        float yaw = mc.player.getYaw();
        float forward = mc.player.input.getMovementInput().y;
        float strafe = mc.player.input.getMovementInput().x;

        if (forward == 0 && strafe == 0) return Vec3d.ZERO;
        if (forward != 0) {
            if (strafe > 0) yaw += (forward > 0 ? -45 : 45);
            else if (strafe < 0) yaw += (forward > 0 ? 45 : -45);
            strafe = 0;
            if (forward > 0) forward = 1;
            else if (forward < 0) forward = -1;
        }
        double cos = Math.cos(Math.toRadians(yaw + 90.0f));
        double sin = Math.sin(Math.toRadians(yaw + 90.0f));
        double velocity = bps / 20.0;

        return new Vec3d(forward * velocity * cos + strafe * velocity * sin, 0, forward * velocity * sin - strafe * velocity * cos);
    }
}

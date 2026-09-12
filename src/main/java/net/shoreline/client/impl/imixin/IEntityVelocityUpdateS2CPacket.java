package net.shoreline.client.impl.imixin;

import net.minecraft.util.math.Vec3d;

@IMixin
public interface IEntityVelocityUpdateS2CPacket
{
    void setVelocity(Vec3d velocity);
}

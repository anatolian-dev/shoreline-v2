package net.shoreline.client.impl.render;

import lombok.experimental.UtilityClass;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

@UtilityClass
public class Interpolation
{

    public Vec3d getRenderPosition(Entity entity, float tickDelta)
    {
        return new Vec3d(MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()),
                MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()),
                MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()));
    }

    public Vec3d getRenderPosition(Vec3d pos, Vec3d lastPos, float tickDelta)
    {
        return pos.subtract(MathHelper.lerp(tickDelta, lastPos.x, pos.x),
                MathHelper.lerp(tickDelta, lastPos.y, pos.y),
                MathHelper.lerp(tickDelta, lastPos.z, pos.z));
    }

    public Box getEntityRenderBox(Entity entity, float tickDelta)
    {
        Box box = entity.getBoundingBox();
        Box lastBox = box.offset(entity.lastX - entity.getX(),
                entity.lastY - entity.getY(),
                entity.lastZ - entity.getZ());

        return getRenderBox(box, lastBox, tickDelta);
    }

    public Box getRenderBox(Box box, Box lastBox, float tickDelta)
    {
        return new Box(MathHelper.lerp(tickDelta, lastBox.minX, box.minX),
                MathHelper.lerp(tickDelta, lastBox.minY, box.minY),
                MathHelper.lerp(tickDelta, lastBox.minZ, box.minZ),
                MathHelper.lerp(tickDelta, lastBox.maxX, box.maxX),
                MathHelper.lerp(tickDelta, lastBox.maxY, box.maxY),
                MathHelper.lerp(tickDelta, lastBox.maxZ, box.maxZ));
    }
}

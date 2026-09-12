package net.shoreline.client.impl.imixin;

import net.minecraft.entity.Entity;

public interface IEntityRenderState
{
    Entity getEntity();

    void setEntity(Entity entity);
}

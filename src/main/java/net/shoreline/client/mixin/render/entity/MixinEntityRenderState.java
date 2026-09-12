package net.shoreline.client.mixin.render.entity;

import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.shoreline.client.impl.imixin.IEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class MixinEntityRenderState implements IEntityRenderState
{
    @Unique
    private Entity shoreline$entity;

    @Override
    public Entity getEntity()
    {
        return shoreline$entity;
    }

    @Override
    public void setEntity(Entity entity)
    {
        this.shoreline$entity = entity;
    }
}

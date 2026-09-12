package net.shoreline.client.impl.render;

import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

public final class EntityRenderContext
{
    private static Entity entity;

    public static void push(@Nullable Entity current)
    {
        entity = current;
    }

    public static void pop()
    {
        entity = null;
    }

    @Nullable
    public static Entity get()
    {
        return entity;
    }

    private EntityRenderContext()
    {
    }
}

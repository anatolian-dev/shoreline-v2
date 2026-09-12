package net.shoreline.client.impl.render;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.shoreline.client.impl.Managers;

public enum ChamsRenderer
{
    NONE,
    CHAMS,
    WIREFRAME,
    BOTH;

    public static boolean rendering = false;

    public static void render(ChamsRenderer chams, Entity entity, float tickDelta, int color)
    {
        render(chams, entity, tickDelta, color, 1.0f);
    }

    public static void render(ChamsRenderer chams, Entity entity, float tickDelta, int color, float factor)
    {
        if (chams == NONE || entity == null)
        {
            return;
        }

        int argb = ColorUtil.withTransparency(color, Math.clamp(factor, 0.0f, 1.0f));
        Box box = Interpolation.getEntityRenderBox(entity, tickDelta).expand(0.02);
        MatrixStack matrices = new MatrixStack();

        if (chams == CHAMS || chams == BOTH)
        {
            Managers.RENDER.renderBox(matrices, box, argb);
        }

        if (chams == WIREFRAME || chams == BOTH)
        {
            Managers.RENDER.renderBoundingBox(matrices, box, argb);
        }
    }
}

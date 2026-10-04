package net.shoreline.client.impl.module.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.shoreline.client.api.config.BooleanConfig;
import net.shoreline.client.api.config.Config;
import net.shoreline.client.api.config.NumberConfig;
import net.shoreline.client.impl.module.impl.hud.HudModule;
import net.shoreline.client.impl.render.ColorUtil;
import net.shoreline.client.util.item.ItemUtil;

public class ArmorHudModule extends HudModule
{
    Config<Float> scale = new NumberConfig.Builder<Float>("Scale")
            .setMin(0.5f).setMax(2.5f).setDefaultValue(1.0f)
            .setDescription("Size scaling of armor HUD").build();
    Config<Boolean> percent = new BooleanConfig.Builder("Percent")
            .setDescription("Displays the percent of the armor piece")
            .setDefaultValue(true).build();

    public ArmorHudModule()
    {
        super("Armor", "Displays your armor slots", 200, 200);
    }

    @Override
    public void drawHudComponent(DrawContext context, float tickDelta)
    {
        float s = scale.getValue();
        if (s != 1.0f)
        {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(getX(), getY());
            context.getMatrices().scale(s, s);
            context.getMatrices().translate(-getX(), -getY());
        }

        int offset = 19;
        float rawWidth = getRawWidth();
        for (EquipmentSlot slot : EquipmentSlot.values())
        {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR)
            {
                continue;
            }

            ItemStack stack = mc.player.getEquippedStack(slot);
            if (stack.isEmpty())
            {
                continue;
            }

            int extra = percent.getValue() ? 8 : 3;
            context.drawItem(stack, (int) (getX() + rawWidth - offset), (int) (getY() + extra));
            context.drawStackOverlay(mc.textRenderer, stack, (int) (getX() + rawWidth - offset), (int) (getY() + extra));
            if (percent.getValue())
            {
                context.getMatrices().pushMatrix();
                context.getMatrices().translate(getX() + rawWidth - offset + 17, getY() + 1);
                context.getMatrices().scale(0.66f, 0.66f);

                float percent = ItemUtil.getStackPercent(stack);
                String text = String.valueOf((int) (percent * 100));
                float width = getTextWidth(text) / 2f;
                int color = ColorUtil.hslToColor(percent * 120, 100f, 50f, 1f).getRGB();
                drawText(context, text, -width - 13, 0, color);
                context.getMatrices().popMatrix();
            }

            offset += 18;
        }

        if (s != 1.0f)
        {
            context.getMatrices().popMatrix();
        }
    }

    public float getRawWidth()
    {
        return 75;
    }

    public float getRawHeight()
    {
        return percent.getValue() ? 25 : 20;
    }

    @Override
    public float getWidth()
    {
        return getRawWidth() * scale.getValue();
    }

    @Override
    public float getHeight()
    {
        return getRawHeight() * scale.getValue();
    }
}

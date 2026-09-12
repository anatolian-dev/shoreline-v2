package net.shoreline.client.impl.module.render;

import net.minecraft.item.Item;
import net.minecraft.item.Items;

import net.shoreline.client.api.config.Config;
import net.shoreline.client.api.config.EnumConfig;
import net.shoreline.client.api.module.GuiCategory;

import net.shoreline.client.impl.module.impl.RenderModule;

public class crystalhand extends RenderModule
{
    public final Config<Mode> mode =
            new EnumConfig.Builder<Mode>("Mode")
                    .setValues(Mode.values())
                    .setDefaultValue(Mode.TOTEM)
                    .setDescription("Visual hand spoof")
                    .build();

    public crystalhand()
    {
        super(
                "CrystalHand",
                "Client-side hand visual override",
                GuiCategory.RENDER
        );
    }

    public Item getVisualItem()
    {
        return switch (mode.getValue())
        {
            case SWORD -> Items.NETHERITE_SWORD;
            case GAP -> Items.ENCHANTED_GOLDEN_APPLE;
            case TOTEM -> Items.TOTEM_OF_UNDYING;
        };
    }

    public enum Mode
    {
        SWORD,
        GAP,
        TOTEM
    }
}

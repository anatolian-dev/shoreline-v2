package net.shoreline.client.impl.module.client;

import lombok.Getter;
import net.shoreline.client.api.config.BooleanConfig;
import net.shoreline.client.api.config.Config;
import net.shoreline.client.api.config.EnumConfig;
import net.shoreline.client.api.module.Concurrent;
import net.shoreline.client.api.module.GuiCategory;
import net.shoreline.client.impl.event.gui.screen.MouseDraggedEvent;
import net.shoreline.client.impl.inventory.SilentSwapType;
import net.shoreline.eventbus.annotation.EventListener;

@Getter
public class InventoryModule extends Concurrent
{
    public static InventoryModule INSTANCE;

    Config<SilentSwapType> silentSwap = new EnumConfig.Builder<SilentSwapType>("SilentSwap")
            .setValues(SilentSwapType.values())
            .setDescription("The mode for silent swapping to items")
            .setDefaultValue(SilentSwapType.HOTBAR).build();
    Config<Boolean> assumeEnchanted = new BooleanConfig.Builder("AssumeBestArmor")
            .setDescription("Assumes that all enemy armor is max enchanted")
            .setDefaultValue(false).build();
    Config<Boolean> dragQuickMove = new BooleanConfig.Builder("DragQuickMove")
            .setDescription("Allows you to drag quick move items in the inventory")
            .setDefaultValue(false).build();

    public InventoryModule()
    {
        super("Inventory", "Manages inventory interactions", GuiCategory.CLIENT);
        INSTANCE = this;
    }

    @EventListener
    public void onMouseDragged(MouseDraggedEvent event)
    {
        if (dragQuickMove.getValue())
        {
            event.cancel();
        }
    }

    public SilentSwapType getSilentSwapType()
    {
        return silentSwap.getValue();
    }
}

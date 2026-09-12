package net.shoreline.client.impl.module.render;

import net.shoreline.client.api.module.GuiCategory;
import net.shoreline.client.api.module.Toggleable;

public class NoBobModule extends Toggleable
{
    public NoBobModule()
    {
        super("NoBob", "Prevents the bobbing animation", GuiCategory.RENDER);
    }
}

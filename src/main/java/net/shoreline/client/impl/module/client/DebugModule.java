package net.shoreline.client.impl.module.client;

import net.shoreline.client.api.module.GuiCategory;
import net.shoreline.client.api.module.Toggleable;

public class DebugModule extends Toggleable
{
    public static DebugModule INSTANCE;

    public DebugModule()
    {
        super("Debug", "Enables client debug messages and module notifications in chat", GuiCategory.CLIENT);
        INSTANCE = this;
    }
}

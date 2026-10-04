package net.shoreline.client.impl.module.client;

import net.shoreline.client.api.module.GuiCategory;
import net.shoreline.client.api.module.ListeningToggleable;
import net.shoreline.client.gui.hud.HudGuiScreen;

public class HudEditorModule extends ListeningToggleable
{
    public static HudEditorModule INSTANCE;

    public HudEditorModule()
    {
        super("HUDEditor", new String[] {"HudEdit", "EditHUD"}, "Opens the HUD editor to move and configure elements", GuiCategory.CLIENT);
        INSTANCE = this;
    }

    @Override
    public void onEnable()
    {
        if (checkNull())
        {
            disable();
            return;
        }

        ClickGuiModule.INSTANCE.disable();
        ClickGuiModule.INSTANCE.setFadeState(true);
        mc.setScreen(HudGuiScreen.INSTANCE);
    }

    @Override
    public void onDisable()
    {
        if (checkNull())
        {
            return;
        }

        ClickGuiModule.INSTANCE.setFadeState(false);
        if (mc.currentScreen == HudGuiScreen.INSTANCE)
        {
            mc.player.closeScreen();
        }
    }
}

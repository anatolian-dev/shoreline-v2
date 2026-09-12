package net.shoreline.client.impl.gui;

import net.minecraft.client.gui.hud.ChatHudLine;


public final class ChatHudRenderCapture
{
    public static final ThreadLocal<ChatHudLine.Visible> CURRENT_VISIBLE = new ThreadLocal<>();

    private ChatHudRenderCapture()
    {
    }
}

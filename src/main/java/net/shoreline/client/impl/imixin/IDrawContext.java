package net.shoreline.client.impl.imixin;

import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.jetbrains.annotations.Nullable;

@IMixin
public interface IDrawContext
{
    GuiRenderState getGuiState();

    @Nullable
    ScreenRect getScissor();
}

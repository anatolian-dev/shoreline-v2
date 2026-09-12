package net.shoreline.client.mixin.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.shoreline.client.impl.imixin.IDrawContext;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(DrawContext.class)
public abstract class MixinDrawContext implements IDrawContext
{
    @Shadow
    @Final
    private GuiRenderState state;

    @Shadow
    @Final
    public DrawContext.ScissorStack scissorStack;

    @Override
    public GuiRenderState getGuiState()
    {
        return state;
    }

    @Override
    public ScreenRect getScissor()
    {
        return scissorStack.peekLast();
    }
}

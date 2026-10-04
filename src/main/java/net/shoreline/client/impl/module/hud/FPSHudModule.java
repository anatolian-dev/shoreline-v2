package net.shoreline.client.impl.module.hud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Formatting;
import net.shoreline.client.api.config.Config;
import net.shoreline.client.api.config.NumberConfig;
import net.shoreline.client.api.math.NanoTimer;
import net.shoreline.client.api.math.Timer;
import net.shoreline.client.impl.module.impl.hud.DynamicEntry;
import net.shoreline.client.impl.module.impl.hud.DynamicHudModule;
import net.shoreline.client.util.math.PerSecond;

public class FPSHudModule extends DynamicHudModule
{
    Config<Float> scale = new NumberConfig.Builder<Float>("Scale")
            .setMin(0.5f).setMax(2.5f).setDefaultValue(1.0f)
            .setDescription("Size scaling of FPS text").build();
    Config<Integer> delayConfig = new NumberConfig.Builder<Integer>("Delay")
            .setMin(100).setMax(2000).setDefaultValue(500).setFormat("ms")
            .setDescription("The delay between FPS updates").build();

    private final PerSecond fps = new PerSecond();
    private final Timer updateTimer = new NanoTimer();
    private int displayFps = 0;

    public FPSHudModule()
    {
        super("FPS", "Displays current game FPS", 200, 200);
    }

    @Override
    public float getScale()
    {
        return scale.getValue();
    }

    @Override
    public void loadEntries()
    {
        getHudEntries().add(new DynamicEntry(this, this::getFPSText, () -> true));
    }

    @Override
    public void drawHudComponent(DrawContext context, float tickDelta)
    {
        fps.count();
        if (displayFps == 0 || updateTimer.hasPassed(delayConfig.getValue()))
        {
            displayFps = fps.getPerSecond();
            updateTimer.reset();
        }
        super.drawHudComponent(context, tickDelta);
    }

    public String getFPSText()
    {
        return "FPS " + Formatting.WHITE + (displayFps > 0 ? displayFps : fps.getPerSecond());
    }
}

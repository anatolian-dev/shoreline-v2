package net.shoreline.client.impl.module.misc;

import lombok.Getter;
import net.minecraft.client.network.PlayerListEntry;
import net.shoreline.client.api.config.BooleanConfig;
import net.shoreline.client.api.config.Config;
import net.shoreline.client.api.config.NumberConfig;
import net.shoreline.client.api.module.GuiCategory;
import net.shoreline.client.api.module.Toggleable;
import net.shoreline.client.impl.Managers;
import net.shoreline.client.impl.event.gui.hud.RenderPlayerListEvent;
import net.shoreline.client.impl.event.gui.hud.RenderTabEvent;
import net.shoreline.client.impl.module.client.SocialsModule;
import net.shoreline.client.impl.module.client.ThemeModule;
import net.shoreline.client.impl.render.animation.Animation;
import net.shoreline.client.impl.render.Easing;
import net.shoreline.eventbus.annotation.EventListener;

import java.awt.*;
import java.util.Collection;

@Getter
public class BetterTabModule extends Toggleable
{
    public static BetterTabModule INSTANCE;

    Config<Boolean> animateConfig = new BooleanConfig.Builder("Animate")
            .setDescription("Animates the tab list")
            .setDefaultValue(false).build();
    Config<Integer> playerLimit = new NumberConfig.Builder<Integer>("Limit")
            .setMin(80).setMax(1000).setDefaultValue(80)
            .setDescription("The max number of players shown in tab list").build();
    Config<Integer> playerColumns = new NumberConfig.Builder<Integer>("Columns")
            .setMin(20).setMax(100).setDefaultValue(20)
            .setDescription("The number of columns to show in tab list").build();
    Config<Float> scale = new NumberConfig.Builder<Float>("Scale")
            .setMin(0.1f).setMax(2.0f).setDefaultValue(1.0f)
            .setDescription("The scale of the tab").build();
    Config<Boolean> onlySocials = new BooleanConfig.Builder("OnlySocials")
            .setDescription("Only shows people you have added as a friend/enemy in tab")
            .setDefaultValue(false).build();

    @Getter
    private final Animation tabListAnim = new Animation(200L);

    public BetterTabModule()
    {
        super("BetterTab", "Improves server tab list", GuiCategory.MISCELLANEOUS);
        INSTANCE = this;
    }

    @EventListener
    public void onRenderPlayerList_Collect(RenderPlayerListEvent.Collect event)
    {
        event.cancel();
        Collection<PlayerListEntry> playerList = mc.player.networkHandler.getListedPlayerListEntries();
        if (onlySocials.getValue())
        {
            event.setPlayers(playerList.stream()
                     .filter(entry -> Managers.SOCIAL.getType(entry.getProfile().name()) != null
                             || entry.getProfile().name().equalsIgnoreCase(mc.player.getGameProfile().name()))
                     .limit(playerLimit.getValue()).toList());
            return;
        }

        event.setPlayers(playerList.stream().limit(playerLimit.getValue()).toList());
    }

    @EventListener
    public void onRenderPlayerListPre(RenderPlayerListEvent.Pre event)
    {
        if (animateConfig.getValue())
        {
            float animFactor = (float) Easing.CIRC_OUT.ease(tabListAnim.getFactor());
            float width      = (event.getX1() + event.getX2()) / 2f;
            var matrices     = event.getContext().getMatrices();

            matrices.translate(width, -event.getY2() + (event.getY2() * animFactor));
            matrices.scale(scale.getValue(), scale.getValue());
            matrices.translate(-width, 0.0f);
        }
    }

    @EventListener
    public void onRenderPlayerListText(RenderPlayerListEvent.DrawText event)
    {
        String[] tabEntry = event.getText().getString().split(" ");
        for (String s : tabEntry)
        {
            if (s.equals(mc.getGameProfile().name()))
            {
                event.cancel();
                event.setColor(ThemeModule.INSTANCE.getPrimaryColor().getRGB());
                return;
            }
            else if (Managers.SOCIAL.getType(s) != null)
            {
                event.cancel();
                event.setColor(SocialsModule.INSTANCE.getEntityColor(s, Color.WHITE).getRGB());
                return;
            }
        }
    }

    @EventListener
    public void renderTabEvent(RenderTabEvent event)
    {
        tabListAnim.setState(event.isPressed());
        if (animateConfig.getValue() && tabListAnim.getFactor() > 0.01f)
        {
            event.setCanceled(true);
            if (!event.isPressed())
            {
                event.setPressed(true);
            }
        }
    }
}

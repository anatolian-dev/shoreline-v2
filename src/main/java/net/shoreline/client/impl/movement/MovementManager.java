package net.shoreline.client.impl.movement;

import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.util.PlayerInput;
import net.shoreline.client.impl.event.network.InteractSneakEvent;
import net.shoreline.client.impl.network.NetworkHandler;
import net.shoreline.client.util.input.InputUtil;
import net.shoreline.eventbus.EventBus;
import net.shoreline.eventbus.annotation.EventListener;

public class MovementManager extends NetworkHandler
{
    private boolean sneaking;

    public MovementManager()
    {
        super("Movement");
        EventBus.INSTANCE.subscribe(this);
    }

    @EventListener
    public void onInteractSneak(InteractSneakEvent event)
    {
        if (sneaking)
        {
            event.cancel();
        }
    }

    public void setSilentSneaking(boolean sneaking)
    {
        PlayerInput playerInput = InputUtil.inputSneaking(mc.player.input.playerInput, sneaking);
        sendPacket(new PlayerInputC2SPacket(playerInput));
    }
}

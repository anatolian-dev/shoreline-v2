package net.shoreline.client.impl.module.misc;

import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.shoreline.client.api.config.Config;
import net.shoreline.client.api.config.NumberConfig;
import net.shoreline.client.api.module.GuiCategory;
import net.shoreline.client.api.module.Toggleable;
import net.shoreline.client.impl.event.TickEvent;
import net.shoreline.client.impl.event.network.PacketEvent;
import net.shoreline.eventbus.annotation.EventListener;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PingStablerModule extends Toggleable
{
    Config<Integer> targetPingConfig = new NumberConfig.Builder<Integer>("TargetPing")
            .setMin(10).setMax(300).setDefaultValue(50).setFormat("ms")
            .setDescription("The target smoothed ping delay")
            .build();

    private final ConcurrentMap<Packet<?>, Long> queue = new ConcurrentHashMap<>();

    public PingStablerModule()
    {
        super("PingStabler", "Stabilizes connection latency and reduces ping jitter", GuiCategory.MISCELLANEOUS);
    }

    @Override
    public void onDisable()
    {
        flushQueue();
    }

    @EventListener
    public void onPacketOutbound(PacketEvent.Outbound event)
    {
        if (checkNull() || mc.isInSingleplayer()) return;

        Packet<?> packet = event.getPacket();
        if (packet instanceof KeepAliveC2SPacket || packet instanceof CommonPongC2SPacket)
        {
            if (!queue.containsKey(packet))
            {
                event.cancel();
                queue.put(packet, System.currentTimeMillis() + targetPingConfig.getValue());
            }
        }
    }

    @EventListener
    public void onTick(TickEvent.Pre event)
    {
        if (checkNull() || mc.isInSingleplayer())
        {
            flushQueue();
            return;
        }

        long now = System.currentTimeMillis();
        queue.forEach((packet, sendTime) -> {
            if (now >= sendTime)
            {
                queue.remove(packet);
                sendPacket(packet);
            }
        });
    }

    private void flushQueue()
    {
        if (!queue.isEmpty())
        {
            if (!checkNull() && !mc.isInSingleplayer() && mc.getNetworkHandler() != null)
            {
                queue.forEach((packet, time) -> sendPacket(packet));
            }
            queue.clear();
        }
    }
}

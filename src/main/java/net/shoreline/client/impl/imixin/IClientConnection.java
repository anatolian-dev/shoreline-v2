package net.shoreline.client.impl.imixin;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.packet.Packet;
import org.jetbrains.annotations.Nullable;

@IMixin
public interface IClientConnection
{
    void hookSendInternal(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush);
}

package net.shoreline.client.irc.packet.client;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Formatting;
import net.shoreline.client.irc.IRCManager;
import net.shoreline.client.irc.packet.IRCPacket;

public final class CPacketChatMessage extends IRCPacket
{
    private final String message;

    public CPacketChatMessage(String message)
    {
        super("CPacketChatMessage");

        this.message = message;
    }

    @Override
    public void addData(JsonObject data)
    {
        data.addProperty("Message", this.message);
    }

    @Override
    public void onSend(IRCManager ircManager)
    {
        MinecraftClient client = MinecraftClient.getInstance();
        String username = client != null && client.getSession() != null
                ? client.getSession().getUsername()
                : "Player";

        Formatting colorCode = Formatting.GRAY;

        String message = colorCode
                + "<" + username + "> " + Formatting.GRAY + this.message;

        if (ircManager.MUTED)
        {
            return;
        }

        ircManager.addToChat(message);
    }
}

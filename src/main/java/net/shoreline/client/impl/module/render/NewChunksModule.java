package net.shoreline.client.impl.module.render;

import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.shoreline.client.api.config.ColorConfig;
import net.shoreline.client.api.config.Config;
import net.shoreline.client.api.config.EnumConfig;
import net.shoreline.client.api.config.NumberConfig;
import net.shoreline.client.api.module.GuiCategory;
import net.shoreline.client.impl.event.network.PacketEvent;
import net.shoreline.client.impl.event.render.RenderWorldEvent;
import net.shoreline.client.impl.module.impl.RenderModule;
import net.shoreline.client.impl.render.BoxRender;
import net.shoreline.eventbus.annotation.EventListener;

import java.awt.Color;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NewChunksModule extends RenderModule
{
    Config<BoxRender> modeConfig = new EnumConfig.Builder<BoxRender>("Mode")
            .setValues(BoxRender.values())
            .setDefaultValue(BoxRender.OUTLINE)
            .setDescription("Render mode for new chunks")
            .build();

    Config<Color> colorConfig = new ColorConfig.Builder("Color")
            .setDefaultValue(new Color(255, 60, 60, 160))
            .setDescription("Color for new chunk boundaries")
            .build();

    Config<Float> renderHeight = new NumberConfig.Builder<Float>("Height")
            .setMin(0.1f).setMax(10.0f).setDefaultValue(1.0f)
            .setDescription("The render height of the chunk box")
            .build();

    private final Set<ChunkPos> newChunks = ConcurrentHashMap.newKeySet();

    public NewChunksModule()
    {
        super("NewChunks", "Highlights newly loaded/generated chunks", GuiCategory.RENDER);
    }

    @Override
    public void onDisable()
    {
        newChunks.clear();
    }

    @EventListener
    public void onPacketInbound(PacketEvent.Inbound event)
    {
        if (event.getPacket() instanceof ChunkDataS2CPacket packet)
        {
            ChunkPos pos = new ChunkPos(packet.getChunkX(), packet.getChunkZ());
            newChunks.add(pos);
        }
    }

    @EventListener
    public void onRenderWorld(RenderWorldEvent.Post event)
    {
        if (checkNull() || newChunks.isEmpty()) return;

        double playerX = mc.player.getX();
        double playerZ = mc.player.getZ();
        double playerY = mc.player.getY();

        for (ChunkPos chunk : newChunks)
        {
            int startX = chunk.getStartX();
            int startZ = chunk.getStartZ();

            double distSq = (startX - playerX) * (startX - playerX) + (startZ - playerZ) * (startZ - playerZ);
            if (distSq > 16384.0) // 128 blocks
            {
                continue;
            }

            Box box = new Box(startX, playerY, startZ, startX + 16, playerY + renderHeight.getValue(), startZ + 16);
            modeConfig.getValue().render(event.getMatrixStack(), box, colorConfig.getValue().getRGB());
        }
    }
}

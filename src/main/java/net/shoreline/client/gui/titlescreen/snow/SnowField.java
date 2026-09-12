package net.shoreline.client.gui.titlescreen.snow;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;
import net.shoreline.client.ShorelineMod;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ThreadLocalRandom;

public final class SnowField
{
    static final Identifier FLAKE = Identifier.of(ShorelineMod.MOD_ID, "textures/snowflake.png");

    private static final Identifier LOGO = Identifier.of(ShorelineMod.MOD_ID, "textures/shoreline.png");
    private static final float LOGO_SCALE = 0.67f;
    private static final int CELL = 2;
    private static final float MAX_DELTA = 0.1f;
    private static final float SPAWN_DELAY = 5.0f;

    private final int count;

    private final float[] x;
    private final float[] y;
    private final float[] size;
    private final float[] fall;
    private final float[] sway;
    private final float[] frequency;
    private final float[] phase;
    private final float[] delay;
    private final boolean[] frozen;
    private final boolean[] sticky;

    private int width;
    private int height;
    private int columns;
    private int rows;
    private long[] mask;

    private long lastUpdate = System.currentTimeMillis();

    public SnowField(int count)
    {
        this.count = Math.max(count, 0);
        this.x = new float[this.count];
        this.y = new float[this.count];
        this.size = new float[this.count];
        this.fall = new float[this.count];
        this.sway = new float[this.count];
        this.frequency = new float[this.count];
        this.phase = new float[this.count];
        this.delay = new float[this.count];
        this.frozen = new boolean[this.count];
        this.sticky = new boolean[this.count];
    }

    public int getCount()
    {
        return count;
    }

    public void update(int screenWidth, int screenHeight)
    {
        if (screenWidth != width || screenHeight != height)
        {
            width = screenWidth;
            height = screenHeight;
            buildMask();
            reset();
        }

        long now = System.currentTimeMillis();
        float delta = Math.min((now - lastUpdate) / 1000.0f, MAX_DELTA);
        lastUpdate = now;

        for (int i = 0; i < count; i++)
        {
            if (frozen[i])
            {
                continue;
            }

            if (delay[i] > 0.0f)
            {
                delay[i] -= delta;
                continue;
            }

            phase[i] += delta * frequency[i];
            y[i] += fall[i] * delta;
            x[i] += (fall[i] * 0.33f + (float) Math.sin(phase[i]) * sway[i] * 60.0f) * delta;

            if (sticky[i] && freeze(i))
            {
                continue;
            }

            if (y[i] > height || x[i] < -size[i] || x[i] > width + size[i])
            {
                spawn(i, false);
            }
        }
    }

    public void render(DrawContext context)
    {
        if (count > 0 && width > 0)
        {
            SnowElement.submit(context, this);
        }
    }

    void writeQuads(QuadWriter writer)
    {
        for (int i = 0; i < count; i++)
        {
            if (delay[i] <= 0.0f)
            {
                writer.quad(x[i], y[i], size[i]);
            }
        }
    }

    public void reset()
    {
        for (int i = 0; i < count; i++)
        {
            frozen[i] = false;
            spawn(i, true);
        }
    }

    private void spawn(int i, boolean anywhere)
    {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        float roll = Math.min(random.nextFloat() * random.nextFloat(), 0.85f);
        size[i] = 1.5f + roll * 2.5f;

        float spread = Math.max(0.3f, 1.0f - (size[i] - 1.5f) / 3.0f);
        float base = (350.0f + random.nextFloat() * 70.0f) / (size[i] / 2.0f);
        fall[i] = base * (1.0f + (random.nextFloat() * 2.0f - 1.0f) * 0.3f * spread);
        sway[i] = 0.3f + random.nextFloat() * 0.7f;
        frequency[i] = 1.0f + random.nextFloat() * 2.0f;
        phase[i] = random.nextFloat() * (float) Math.PI * 2.0f;
        delay[i] = anywhere ? 0.0f : random.nextFloat() * SPAWN_DELAY;

        if (anywhere)
        {
            x[i] = random.nextFloat() * width;
            y[i] = random.nextFloat() * height;
            sticky[i] = true;
            return;
        }

        float side = random.nextFloat();
        if (side < 0.5f)
        {
            x[i] = random.nextFloat() * width;
            y[i] = -size[i];
            sticky[i] = true;
        }
        else
        {
            x[i] = side < 0.75f ? -size[i] : width + size[i];
            y[i] = random.nextFloat() * height;
            sticky[i] = false;
        }
    }

    private boolean freeze(int i)
    {
        int column = (int) (x[i] / CELL);
        int row = (int) (y[i] / CELL);
        if (column < 0 || row < 0 || column >= columns || row >= rows)
        {
            return false;
        }

        int index = row * columns + column;
        long bit = 1L << (index & 63);
        int word = index >> 6;
        if ((mask[word] & bit) == 0L)
        {
            return false;
        }

        mask[word] &= ~bit;
        frozen[i] = true;
        return true;
    }

    private void buildMask()
    {
        columns = Math.max(width / CELL, 1);
        rows = Math.max(height / CELL, 1);
        mask = new long[(columns * rows + 63) >> 6];

        MinecraftClient mc = MinecraftClient.getInstance();
        try (InputStream stream = mc.getResourceManager().getResourceOrThrow(LOGO).getInputStream();
             NativeImage image = NativeImage.read(stream))
        {
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();
            float scale = Math.min((float) width / imageWidth, (float) height / imageHeight) * LOGO_SCALE;
            float originX = (width - imageWidth * scale) / 2.0f;
            float originY = (height - imageHeight * scale) / 2.0f;

            for (int row = 0; row < rows; row++)
            {
                float sampleY = ((row * CELL) - originY) / scale;
                if (sampleY < 0 || sampleY >= imageHeight)
                {
                    continue;
                }

                for (int column = 0; column < columns; column++)
                {
                    float sampleX = ((column * CELL) - originX) / scale;
                    if (sampleX < 0 || sampleX >= imageWidth)
                    {
                        continue;
                    }

                    if ((image.getColorArgb((int) sampleX, (int) sampleY) >>> 24) >= 50)
                    {
                        int index = row * columns + column;
                        mask[index >> 6] |= 1L << (index & 63);
                    }
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    interface QuadWriter
    {
        void quad(float x, float y, float size);
    }
}

package net.shoreline.client.impl.render.shader;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import lombok.Getter;
import net.shoreline.client.api.GenericFeature;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class ShaderEffect extends GenericFeature
{
    private final String block;
    private final List<Slot> layout;
    private final Map<String, float[]> uniforms = new HashMap<>();

    public ShaderEffect(String name, String block, List<Slot> layout)
    {
        super(name);
        this.block = block;
        this.layout = layout;
    }

    public void addIntUniform(String name, int value)
    {
        uniforms.put(name, new float[] { value });
    }

    public void addFltUniform(String name, float value)
    {
        uniforms.put(name, new float[] { value });
    }

    public void addVec2Uniform(String name, float x, float y)
    {
        uniforms.put(name, new float[] { x, y });
    }

    public void addVec4Uniform(String name, float r, float g, float b, float a)
    {
        uniforms.put(name, new float[] { r, g, b, a });
    }

    public int size()
    {
        Std140SizeCalculator calculator = new Std140SizeCalculator();
        for (Slot slot : layout)
        {
            switch (slot.size())
            {
                case 4 -> calculator.putVec4();
                case 2 -> calculator.putVec2();
                default -> calculator.putFloat();
            }
        }

        return calculator.get();
    }

    public void write(Std140Builder builder)
    {
        for (Slot slot : layout)
        {
            float[] value = uniforms.getOrDefault(slot.name(), new float[slot.size()]);
            switch (slot.size())
            {
                case 4 -> builder.putVec4(value[0], value[1], value[2], value[3]);
                case 2 -> builder.putVec2(value[0], value[1]);
                default -> builder.putFloat(value[0]);
            }
        }
    }

    public record Slot(String name, int size)
    {
        public static Slot flt(String name)
        {
            return new Slot(name, 1);
        }

        public static Slot vec2(String name)
        {
            return new Slot(name, 2);
        }

        public static Slot vec4(String name)
        {
            return new Slot(name, 4);
        }
    }
}

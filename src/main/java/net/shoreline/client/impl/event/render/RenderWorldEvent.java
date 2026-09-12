package net.shoreline.client.impl.event.render;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.client.util.math.MatrixStack;
import net.shoreline.eventbus.Event;
import net.shoreline.eventbus.annotation.Cancelable;
import org.joml.Matrix4f;

@RequiredArgsConstructor
@Getter
public class RenderWorldEvent extends Event
{
    private final MatrixStack matrixStack;
    private final float tickDelta;

    @Cancelable
    @Getter
    public static class Post extends RenderWorldEvent
    {

        private final Matrix4f positionMatrix;

        private final Matrix4f projectionMatrix;

        public Post(MatrixStack matrixStack,
                    float tickDelta,
                    Matrix4f positionMatrix,
                    Matrix4f projectionMatrix)
        {
            super(matrixStack, tickDelta);
            this.positionMatrix = positionMatrix;
            this.projectionMatrix = projectionMatrix;
        }
    }

    @RequiredArgsConstructor
    @Getter
    public static class Resized extends Event
    {
        private final int width, height;
    }
}

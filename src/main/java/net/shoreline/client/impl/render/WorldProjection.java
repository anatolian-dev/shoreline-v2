package net.shoreline.client.impl.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;


public final class WorldProjection
{
    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f MODEL_VIEW = new Matrix4f();
    private static final Matrix4f POSITION = new Matrix4f();

    private WorldProjection()
    {
    }

    public static void capture(Matrix4f positionMatrix, Matrix4f projectionMatrix)
    {
        PROJECTION.set(projectionMatrix);
        MODEL_VIEW.set(positionMatrix);
        POSITION.identity();
    }


    public static Vec3d project(Vec3d world)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.gameRenderer == null)
        {
            return null;
        }

        Camera camera = mc.gameRenderer.getCamera();
        Vec3d rel = world.subtract(camera.getCameraPos());

        int fbW = mc.getWindow().getFramebufferWidth();
        int fbH = mc.getWindow().getFramebufferHeight();
        int[] viewport = new int[] { 0, 0, fbW, fbH };

        Vector4f clip = new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1.0f);
        clip.mul(POSITION);

        Matrix4f mvp = new Matrix4f(PROJECTION).mul(MODEL_VIEW);
        Vector3f win = new Vector3f();
        mvp.project(clip.x(), clip.y(), clip.z(), viewport, win);

        double guiScale = mc.getWindow().getScaleFactor();
        double sx = win.x / guiScale;
        double sy = (fbH - win.y) / guiScale;
        return new Vec3d(sx, sy, win.z);
    }

    public static boolean isVisible(Vec3d projected)
    {
        return projected.z > 0.0 && projected.z < 1.0;
    }
}

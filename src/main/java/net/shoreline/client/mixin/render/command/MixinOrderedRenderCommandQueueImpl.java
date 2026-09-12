package net.shoreline.client.mixin.render.command;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.model.Model;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.shoreline.client.impl.module.render.ChamsModule;
import net.shoreline.client.impl.module.render.ShadersModule;
import net.shoreline.client.impl.render.ColorUtil;
import net.shoreline.client.impl.render.EntityRenderContext;
import net.shoreline.client.impl.render.Layers;
import net.shoreline.client.impl.render.manager.ShaderManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(OrderedRenderCommandQueueImpl.class)
public abstract class MixinOrderedRenderCommandQueueImpl
{
    @WrapMethod(method = "submitModel")
    private void hookSubmitModel(Model model,
                                 Object state,
                                 MatrixStack matrices,
                                 RenderLayer renderLayer,
                                 int light,
                                 int overlay,
                                 int tintedColor,
                                 @Nullable Sprite sprite,
                                 int outlineColor,
                                 @Nullable ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay,
                                 Operation<Void> original)
    {
        Entity entity = EntityRenderContext.get();
        if (entity == null)
        {
            original.call(model, state, matrices, renderLayer, light, overlay, tintedColor, sprite, outlineColor, crumblingOverlay);
            return;
        }

        ChamsModule chams = ChamsModule.getInstance();
        boolean chamsTarget = chams != null && chams.isEnabled() && chams.isValid(entity);
        ChamsModule.ChamsMode mode = chamsTarget ? chams.mode.getValue() : ChamsModule.ChamsMode.NONE;

        RenderLayer layer = renderLayer;
        int color = tintedColor;
        int lightmap = light;
        int overlayUv = overlay;

        if (mode == ChamsModule.ChamsMode.CHAMS || mode == ChamsModule.ChamsMode.WIRECHAMS)
        {
            layer = Layers.chams(chams.throughWalls.getValue(), false);
            color = chams.getColor(entity).getRGB();
            lightmap = LightmapTextureManager.MAX_LIGHT_COORDINATE;
            overlayUv = OverlayTexture.DEFAULT_UV;
        }
        else if (mode == ChamsModule.ChamsMode.X_Q_Z)
        {
            if (chams.throughWalls.getValue())
            {
                layer = Layers.noDepth(renderLayer);
            }

            color = ColorUtil.withTransparency(0xFFFFFF, chams.getOpacity());
            overlayUv = OverlayTexture.DEFAULT_UV;
        }
        else if (mode == ChamsModule.ChamsMode.SHINE && chams.getModel().getValue())
        {
            if (chams.throughWalls.getValue())
            {
                layer = Layers.noDepth(renderLayer);
            }

            color = ColorUtil.withTransparency(0xFFFFFF, chams.getOpacity());
        }

        original.call(model, state, matrices, layer, lightmap, overlayUv, color, sprite, outlineColor, crumblingOverlay);

        if (mode == ChamsModule.ChamsMode.SHINE)
        {
            original.call(model, state, matrices, Layers.SHINE_LAYER,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV,
                    chams.getColor(entity).getRGB(), null, 0, null);
        }

        if (mode == ChamsModule.ChamsMode.WIRECHAMS)
        {
            original.call(model, state, matrices, Layers.chams(chams.throughWalls.getValue(), true),
                    LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV,
                    chams.getColor(entity).getRGB() | 0xFF000000, null, 0, null);
        }

        shoreline$submitShaderPass(model, state, matrices, entity, original);
    }

    @Unique
    private void shoreline$submitShaderPass(Model model,
                                            Object state,
                                            MatrixStack matrices,
                                            Entity entity,
                                            Operation<Void> original)
    {
        ShadersModule shaders = ShadersModule.INSTANCE;
        if (shaders == null || !shaders.isEnabled() || !shaders.shouldRenderShader(entity))
        {
            return;
        }

        ShaderManager manager = shaders.getShaderManager();
        if (manager == null)
        {
            return;
        }

        original.call(model, state, matrices, manager.getLayer(),
                LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV,
                shaders.getShaderColor(entity).getRGB() | 0xFF000000, null, 0, null);
    }
}

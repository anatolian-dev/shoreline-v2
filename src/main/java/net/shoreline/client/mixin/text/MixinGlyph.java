package net.shoreline.client.mixin.text;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.font.GlyphMetrics;
import net.shoreline.client.impl.event.render.GlyphShadowEvent;
import net.shoreline.eventbus.EventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GlyphMetrics.class)
public interface MixinGlyph
{
    @ModifyReturnValue(method = "getShadowOffset", at = @At("RETURN"))
    private float shoreline$hookGetShadowOffset(float original)
    {
        GlyphShadowEvent event = new GlyphShadowEvent();
        EventBus.INSTANCE.dispatch(event);
        return event.isCanceled() ? event.getShadowOffset() : original;
    }
}

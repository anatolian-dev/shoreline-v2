package net.shoreline.client.mixin.gui.hud;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.font.Alignment;
import net.minecraft.client.font.DrawnTextConsumer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.shoreline.client.impl.event.gui.hud.RenderChatEvent;
import net.shoreline.client.impl.gui.ChatHudRenderCapture;
import net.shoreline.eventbus.EventBus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.client.gui.hud.ChatHud$Hud")
public abstract class MixinChatHudHud
{
    @Shadow
    @Final
    private DrawContext context;

    @WrapOperation(
            method = "text",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/font/DrawnTextConsumer;text(Lnet/minecraft/client/font/Alignment;IILnet/minecraft/client/font/DrawnTextConsumer$Transformation;Lnet/minecraft/text/OrderedText;)V"))
    private void shoreline$wrapChatText(DrawnTextConsumer consumer,
                                        Alignment alignment,
                                        int i,
                                        int j,
                                        DrawnTextConsumer.Transformation transformation,
                                        OrderedText text,
                                        Operation<Void> original)
    {
        int alpha = Math.round(Math.clamp(transformation.opacity(), 0.0f, 1.0f) * 255.0f);
        int color = alpha << 24 | 0xFFFFFF;
        RenderChatEvent.Text renderChatTextEvent = new RenderChatEvent.Text(
                ChatHudRenderCapture.CURRENT_VISIBLE.get(),
                context,
                text,
                j,
                i,
                color,
                alpha);
        EventBus.INSTANCE.dispatch(renderChatTextEvent);
        if (!renderChatTextEvent.isCanceled())
        {
            original.call(consumer, alignment, i, j, transformation, text);
        }
    }
}

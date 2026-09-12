package net.shoreline.client.mixin.gui.hud;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.shoreline.client.impl.event.gui.hud.MessageIndicatorEvent;
import net.shoreline.eventbus.EventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.client.gui.hud.ChatHud$1")
public abstract class MixinChatHud1
{
    @ModifyExpressionValue(
            method = "accept",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/ChatHudLine$Visible;indicator()Lnet/minecraft/client/gui/hud/MessageIndicator;"))
    private MessageIndicator shoreline$hookIndicator(MessageIndicator original)
    {
        MessageIndicatorEvent signatureIndicatorEvent = new MessageIndicatorEvent();
        EventBus.INSTANCE.dispatch(signatureIndicatorEvent);
        return signatureIndicatorEvent.isCanceled() ? null : original;
    }
}

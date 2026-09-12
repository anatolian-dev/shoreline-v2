package net.shoreline.client.mixin;

import net.minecraft.client.Keyboard;
import net.minecraft.client.input.KeyInput;
import net.shoreline.client.impl.event.InputEvent;
import net.shoreline.eventbus.EventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class MixinKeyboard
{
    @Inject(method = "onKey", at = @At(value = "HEAD"))
    private void hookOnKey(long window, int action, KeyInput input, CallbackInfo ci)
    {
        InputEvent.Keyboard event = new InputEvent.Keyboard(
                input.key(), input.scancode(), action, input.modifiers());
        EventBus.INSTANCE.dispatch(event);
    }
}

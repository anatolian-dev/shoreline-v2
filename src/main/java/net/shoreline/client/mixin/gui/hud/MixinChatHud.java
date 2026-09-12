package net.shoreline.client.mixin.gui.hud;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import net.shoreline.client.impl.event.gui.hud.ChatMessageEvent;
import net.shoreline.client.impl.gui.ChatHudRenderCapture;
import net.shoreline.client.impl.imixin.IChatHud;
import net.shoreline.client.impl.imixin.IChatHudLine;
import net.shoreline.client.impl.imixin.IChatHudLineVisible;
import net.shoreline.eventbus.EventBus;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ChatHud.class)
public abstract class MixinChatHud implements IChatHud
{
    @Shadow
    @Final
    private List<ChatHudLine.Visible> visibleMessages;

    @Shadow
    @Final
    private List<ChatHudLine> messages;

    @Shadow
    public abstract void addMessage(Text message, @Nullable MessageSignatureData signatureData, @Nullable MessageIndicator indicator);

    @Unique
    private int messageId;

    @Override
    public void addMessage(Text message, MessageIndicator messageIndicator, int id)
    {
        messageId = id;
        addMessage(message, null, messageIndicator);
        messageId = 0;
    }

    @WrapOperation(
            method = "forEachVisibleLine",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/hud/ChatHud$LineConsumer;accept(Lnet/minecraft/client/gui/hud/ChatHudLine$Visible;IF)V"))
    private void shoreline$trackVisibleChatLine(ChatHud.LineConsumer lineConsumer,
                                                ChatHudLine.Visible visible,
                                                int lineIndex,
                                                float opacity,
                                                Operation<Void> original)
    {
        ChatHudRenderCapture.CURRENT_VISIBLE.set(visible);
        try
        {
            original.call(lineConsumer, visible, lineIndex, opacity);
        }
        finally
        {
            ChatHudRenderCapture.CURRENT_VISIBLE.remove();
        }
    }

    @Redirect(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At(value = "NEW",
                    target = "(ILnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)Lnet/minecraft/client/gui/hud/ChatHudLine;")
    )
    private ChatHudLine hookInitChatLine(int creationTick,
                                         Text text,
                                         MessageSignatureData messageSignatureData,
                                         MessageIndicator messageIndicator)
    {
        ChatMessageEvent chatMessageEvent = new ChatMessageEvent(text);
        EventBus.INSTANCE.dispatch(chatMessageEvent);
        if (chatMessageEvent.isCanceled())
        {
            text = chatMessageEvent.getText();
        }

        return new ChatHudLine(creationTick, text, messageSignatureData, messageIndicator);
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At(value = "HEAD")
    )
    private void hookAddMessage(Text message,
                                MessageSignatureData signatureData,
                                MessageIndicator indicator,
                                CallbackInfo ci)
    {
        if (messageId == 0)
        {
            return;
        }

        visibleMessages.removeIf(msg -> ((IChatHudLineVisible) (Object) msg).getId() == messageId);
        for (int i = messages.size() - 1; i > -1; i--)
        {
            if (((IChatHudLine) (Object) messages.get(i)).getId() == messageId)
            {
                messages.remove(i);
            }
        }
    }

    @Redirect(
            method = "addVisibleMessage",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/List;addFirst(Ljava/lang/Object;)V"))
    private void hookAddVisibleMessage(List instance, Object e)
    {
        ChatHudLine.Visible chatLine = (ChatHudLine.Visible) e;
        ChatMessageEvent.Visible event = new ChatMessageEvent.Visible(chatLine);
        EventBus.INSTANCE.dispatch(event);

        ((IChatHudLineVisible) e).setId(messageId);
        instance.addFirst(e);
    }

}

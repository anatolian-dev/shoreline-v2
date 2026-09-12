package net.shoreline.client.impl.event.render.item;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.shoreline.eventbus.Event;
import net.shoreline.eventbus.annotation.Cancelable;

@AllArgsConstructor
@Cancelable
@Getter
@Setter
public class RenderHandEvent extends Event
{
    private OrderedRenderCommandQueue orderedRenderCommandQueue;

    public static class Post extends Event {}
}

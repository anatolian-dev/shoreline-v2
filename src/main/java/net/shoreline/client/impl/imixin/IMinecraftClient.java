package net.shoreline.client.impl.imixin;

@IMixin
public interface IMinecraftClient
{
    void hookDoItemUse();

    boolean hookDoAttack();

    int getItemUseCooldown();

    void setItemUseCooldown(int itemUseCooldown);
}

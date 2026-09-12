package net.shoreline.client.impl.module.render;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import lombok.Getter;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Colors;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.shoreline.client.api.config.BooleanConfig;
import net.shoreline.client.api.config.Config;
import net.shoreline.client.api.config.NumberConfig;
import net.shoreline.client.api.module.GuiCategory;
import net.shoreline.client.impl.Managers;
import net.shoreline.client.impl.event.TickEvent;
import net.shoreline.client.impl.event.gui.hud.HudOverlayEvent;
import net.shoreline.client.impl.module.client.SocialsModule;
import net.shoreline.client.impl.module.client.ThemeModule;
import net.shoreline.client.impl.module.impl.RenderModule;
import net.shoreline.client.impl.render.ColorUtil;
import net.shoreline.client.impl.render.Interpolation;
import net.shoreline.client.impl.render.WorldProjection;
import net.shoreline.client.util.entity.FakePlayerEntity;
import net.shoreline.eventbus.annotation.EventListener;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class NametagsModule extends RenderModule
{
    public static NametagsModule INSTANCE;

    Config<Boolean> entityIdConfig = new BooleanConfig.Builder("EntityId")
            .setDescription("Displays the players entity id")
            .setDefaultValue(false).build();
    Config<Boolean> gamemodeConfig = new BooleanConfig.Builder("Gamemode")
            .setDescription("Displays the players gamemode")
            .setDefaultValue(false).build();
    Config<Boolean> pingConfig = new BooleanConfig.Builder("Ping")
            .setDescription("Displays the players ping")
            .setDefaultValue(true).build();
    Config<Boolean> healthConfig = new BooleanConfig.Builder("Health")
            .setDescription("Displays the players current health")
            .setDefaultValue(true).build();
    Config<Boolean> totemsConfig = new BooleanConfig.Builder("Totems")
            .setDescription("Displays the totem count")
            .setDefaultValue(true).build();
    Config<Boolean> itemConfig = new BooleanConfig.Builder("Item")
            .setDescription("Displays the name of the players equipped stack")
            .setDefaultValue(true).build();
    Config<Boolean> armorConfig = new BooleanConfig.Builder("Armor")
            .setDescription("Displays the players equipped armor")
            .setDefaultValue(true).build();
    Config<Boolean> enchantmentsConfig = new BooleanConfig.Builder("Enchantments")
            .setDescription("Displays the enchantments of equipped armor")
            .setVisible(armorConfig::getValue)
            .setDefaultValue(true).build();
    Config<Boolean> dynamicScaleConfig = new BooleanConfig.Builder("DynamicScale")
            .setDescription("Slightly enlarges nametags when the player is nearby (vanilla-style readability)")
            .setDefaultValue(true).build();

    Config<Float> scalingConfig = new NumberConfig.Builder<Float>("Scaling")
            .setMin(0.5f).setMax(1.5f).setDefaultValue(1.0f).build();

    private final List<PlayerEntry> players = new ArrayList<>();

    public NametagsModule()
    {
        super("Nametags", "Adds info to player nametags", GuiCategory.RENDER);
        INSTANCE = this;
    }


    @EventListener
    public void onHudPost(HudOverlayEvent.Post event)
    {
        if (!isEnabled() || mc.world == null || mc.gameRenderer == null || mc.getCameraEntity() == null)
        {
            return;
        }

        DrawContext context = event.getContext();
        float tickDelta = event.getTickDelta();

        for (PlayerEntry playerEntry : players)
        {
            PlayerEntity player = playerEntry.getPlayer();
            if (!Managers.RENDER.isVisible(player.getBoundingBox()))
            {
                continue;
            }

            float yOff = 2.2f;
            if (player.isSneaking())
            {
                yOff = 2.0f;
            } else if (player.isCrawling())
            {
                yOff = 1.2f;
            }

            Vec3d interp = Interpolation.getRenderPosition(player, tickDelta);
            Vec3d anchor = interp.add(0.0, yOff, 0.0);
            Vec3d projected = WorldProjection.project(anchor);
            if (projected == null || !WorldProjection.isVisible(projected))
            {
                continue;
            }

            var camera = mc.getEntityRenderDispatcher().camera;
            float distance = (float) Math.sqrt(camera.getCameraPos().squaredDistanceTo(interp));
            float scale = 1.0f;
            if (dynamicScaleConfig.getValue())
            {
                float start = 10.0f;
                float end = 0.5f;
                float maxScale = 4.0f;
                if (distance < start)
                {
                    float t = (start - distance) / (start - end);
                    t = Math.clamp(t, 0.0f, 1.0f);
                    t = (float) Math.pow(t, 4.0);
                    scale = 1.0f + t * (maxScale - 1.0f);
                }
            }

            scale *= scalingConfig.getValue();

            String info = playerEntry.getInfo();
            int color = getNametagColor(player);

            context.getMatrices().pushMatrix();
            context.getMatrices().translate((float) projected.x, (float) projected.y);
            context.getMatrices().scale(scale, scale);

            renderItemsHud(context, player, armorConfig.getValue());

            float hwidth = getTextWidth(info) / 2.0f;
            drawText(context, info, -hwidth, 0.0f, color);

            context.getMatrices().popMatrix();
        }
    }

    @EventListener
    public void onTickPost(TickEvent.Post event)
    {
        players.clear();
        if (checkNull())
        {
            return;
        }

        for (Entity entity : mc.world.getEntities())
        {
            if (!(entity instanceof PlayerEntity playerEntity))
            {
                continue;
            }

            if (entity == mc.player && !FreecamModule.INSTANCE.isEnabled())
            {
                continue;
            }

            players.add(new PlayerEntry(playerEntity));
        }
    }

    private void renderItemsHud(DrawContext context, PlayerEntity player, boolean icons)
    {
        List<ItemStack> displayItems = new CopyOnWriteArrayList<>();
        if (!player.getOffHandStack().isEmpty())
        {
            displayItems.add(player.getOffHandStack());
        }

        for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD })
        {
            ItemStack armorStack = player.getEquippedStack(slot);
            if (!armorStack.isEmpty())
            {
                displayItems.add(armorStack);
            }
        }

        if (!player.getMainHandStack().isEmpty())
        {
            displayItems.add(player.getMainHandStack());
        }

        Collections.reverse(displayItems);
        float xOffset = 0;
        int yOffset = 0;
        for (ItemStack stack : displayItems)
        {
            xOffset -= 8;
            if (stack.getEnchantments().getEnchantments().size() > yOffset)
            {
                yOffset = stack.getEnchantments().getEnchantments().size();
            }
        }

        float enchY = icons ? enchantOffset(yOffset) : -5.0f;
        for (ItemStack stack : displayItems)
        {
            if (icons)
            {
                context.drawItem(stack, (int) xOffset, (int) enchY);
            }

            renderItemOverlayHud(context, stack, (int) xOffset, (int) enchY);
            renderDurabilityHud(context, stack, xOffset + 2.0f, enchY - 4.5f);
            if (enchantmentsConfig.getValue() && icons)
            {
                renderEnchantsHud(context, stack, xOffset + 2.0f, enchY);
            }

            xOffset += 16;
        }

        ItemStack heldItem = player.getMainHandStack();
        if (!heldItem.isEmpty() && itemConfig.getValue())
        {
            renderItemNameHud(context, heldItem, 0.0f, enchY - 10.0f);
        }
    }

    private void renderEnchantsHud(DrawContext context, ItemStack itemStack, float x, float y)
    {
        if (!itemStack.hasEnchantments())
        {
            return;
        }

        Set<Object2IntMap.Entry<RegistryEntry<Enchantment>>> enchants =
                EnchantmentHelper.getEnchantments(itemStack).getEnchantmentEntries();
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(0.5f, 0.5f);
        float n2 = 0;
        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> e : enchants)
        {
            int lvl = e.getIntValue();
            StringBuilder enchantString = new StringBuilder();
            String translatedName = Enchantment.getName(e.getKey(), lvl).getString();
            if (translatedName.contains("Vanish"))
            {
                enchantString.append("Van");
            } else if (translatedName.contains("Bind"))
            {
                enchantString.append("Bind");
            } else
            {
                int maxLen = lvl > 1 ? 2 : 3;
                if (translatedName.length() > maxLen)
                {
                    translatedName = translatedName.substring(0, maxLen);
                }
                enchantString.append(translatedName);
                enchantString.append(lvl);
            }

            drawText(context, enchantString.toString(), 0.0f, n2, -1);
            n2 += 9.0f;
        }

        context.getMatrices().popMatrix();
    }

    private void renderItemOverlayHud(DrawContext context, ItemStack stack, int x, int y)
    {
        if (stack.getCount() != 1)
        {
            String count = String.valueOf(stack.getCount());
            drawText(context, count, x + 17 - getTextWidth(count), y + 9.0f, -1);
        }

        if (stack.isItemBarVisible())
        {
            int i = (int) Math.clamp(stack.getItemBarStep() * 0.923076923, 0, 12);
            int j = stack.getItemBarColor();
            Managers.RENDER.drawRect(context, x + 3, y + 13, 12, 1, Colors.BLACK);
            Managers.RENDER.drawRect(context, x + 3, y + 13, i, 1, j | Colors.BLACK);
        }
    }

    private void renderDurabilityHud(DrawContext context, ItemStack itemStack, float x, float y)
    {
        if (!itemStack.isDamageable())
        {
            return;
        }

        int n = itemStack.getMaxDamage();
        int n2 = itemStack.getDamage();
        int color = ColorUtil.hslToColor((float) (n - n2) / (float) n * 120.0f, 100.0f, 50.0f, 1.0f).getRGB();
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(0.5f, 0.5f);
        int durability = (int) ((n - n2) / ((float) n) * 100.0f);
        drawText(context, durability + "%", 0.0f, 0.0f, color);
        context.getMatrices().popMatrix();
    }

    private void renderItemNameHud(DrawContext context, ItemStack itemStack, float x, float y)
    {
        String itemName = itemStack.getName().getString();
        float width = getTextWidth(itemName) / 4.0f;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(0.5f, 0.5f);
        drawText(context, itemName, -width * 2.0f, 0.0f, -1);
        context.getMatrices().popMatrix();
    }

    private float enchantOffset(final int n)
    {
        if (!enchantmentsConfig.getValue() || n <= 3)
        {
            return -18.0f;
        }

        float n2 = -14.0f;
        n2 -= (n - 3) * 4.5f;
        return n2;
    }

    private int getNametagColor(PlayerEntity player)
    {
        if (player == mc.player)
        {
            return ThemeModule.INSTANCE.getPrimaryColor().getRGB();
        }

        if (Managers.SOCIAL.isFriend(player))
        {
            return SocialsModule.INSTANCE.getFriendsColor().getRGB();
        }

        if (player.isInvisible())
        {
            return 0xffff2500;
        }

        if (player instanceof FakePlayerEntity)
        {
            return 0xffef0147;
        }

        if (player.isSneaking())
        {
            return 0xffff9900;
        }

        return 0xffffffff;
    }

    @Getter
    public class PlayerEntry
    {
        private final PlayerEntity player;
        private final String info;

        public PlayerEntry(PlayerEntity player)
        {
            this.player = player;
            StringBuilder builder = new StringBuilder(player.getName().getString());
            builder.append(" ");
            if (entityIdConfig.getValue())
            {
                builder.append("ID: ").append(player.getId()).append(" ");
            }

            if (gamemodeConfig.getValue())
            {
                if (player.isCreative())
                {
                    builder.append("[C] ");
                } else if (player.isSpectator())
                {
                    builder.append("[I] ");
                } else
                {
                    builder.append("[S] ");
                }
            }

            if (pingConfig.getValue() && mc.getNetworkHandler() != null)
            {
                PlayerListEntry playerEntry = mc.getNetworkHandler().getPlayerListEntry(player.getGameProfile().id());
                if (playerEntry != null)
                {
                    builder.append(playerEntry.getLatency());
                    builder.append("ms ");
                }
            }

            if (healthConfig.getValue())
            {
                double health = player.getHealth() + player.getAbsorptionAmount();

                Formatting hcolor;
                if (health > 18)
                {
                    hcolor = Formatting.GREEN;
                } else if (health > 16)
                {
                    hcolor = Formatting.DARK_GREEN;
                } else if (health > 12)
                {
                    hcolor = Formatting.YELLOW;
                } else if (health > 8)
                {
                    hcolor = Formatting.GOLD;
                } else if (health > 4)
                {
                    hcolor = Formatting.RED;
                } else
                {
                    hcolor = Formatting.DARK_RED;
                }

                BigDecimal bigDecimal = new BigDecimal(health);
                bigDecimal = bigDecimal.setScale(1, RoundingMode.HALF_UP);
                builder.append(hcolor);
                builder.append(bigDecimal.doubleValue());
                builder.append(" ");
            }

            if (totemsConfig.getValue() && player != mc.player)
            {
                int totems = Managers.TOTEM.getTotems(player);
                if (totems > 0)
                {
                    builder.append(Formatting.WHITE);
                    builder.append(-totems);
                    builder.append(" ");
                }
            }

            info = builder.toString().trim();
        }
    }
}

package net.shoreline.client.impl.module.combat;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.shoreline.client.api.config.*;
import net.shoreline.client.api.module.GuiCategory;
import net.shoreline.client.impl.event.network.PlayerUpdateEvent;
import net.shoreline.client.impl.inventory.InventoryUtil;
import net.shoreline.client.impl.module.impl.ObsidianPlacerModule;
import net.shoreline.eventbus.annotation.EventListener;

import java.util.ArrayList;
import java.util.List;

public class AntiPhase extends ObsidianPlacerModule {

    public enum PlaceMode {
        LEGS, HEAD, BOTH
    }

    Config<Float> range = new NumberConfig.Builder<Float>("Range")
            .setDefaultValue(4.5f)
            .setMin(1.0f)
            .setMax(6.0f)
            .build();

    Config<PlaceMode> placeMode = new EnumConfig.Builder<PlaceMode>("PlaceMode")
            .setDefaultValue(PlaceMode.LEGS)
            .build();

    Config<Boolean> autoDisable = new BooleanConfig.Builder("AutoDisable")
            .setDefaultValue(false)
            .build();

    Config<Integer> delay = new NumberConfig.Builder<Integer>("Delay")
            .setDefaultValue(1)
            .setMin(1)
            .setMax(5)
            .build();

    Config<Integer> shiftTicks = new NumberConfig.Builder<Integer>("ShiftTicks")
            .setDefaultValue(1)
            .setMin(1)
            .setMax(8)
            .build();

    private int cooldown = 0;

    public AntiPhase() {
        super("AntiPhase", "Places scaffold inside players", GuiCategory.COMBAT);
    }

    @EventListener
    public void onPlayerUpdate(PlayerUpdateEvent.Pre event) {
        if (checkNull()) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        Entity target = getTarget();
        if (target == null) return;

        int slot = getScaffoldSlot();
        if (slot == -1) {
            if (autoDisable.getValue()) disable();
            return;
        }

        List<BlockPos> positions = getPositions(target);

        int placed = 0;

        for (BlockPos pos : positions) {
            if (!mc.world.getBlockState(pos).isReplaceable()) continue;

            runSingleBlockPlacement(pos, Block.getBlockFromItem(Items.SCAFFOLDING), slot);

            placed++;

            if (placed >= shiftTicks.getValue()) break;
        }

        if (placed > 0) {
            cooldown = delay.getValue();
        } else if (autoDisable.getValue()) {
            disable();
        }
    }

    private Entity getTarget() {
        Entity best = null;
        double bestDist = range.getValue();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity)) continue;
            if (entity == mc.player) continue;
            if (!entity.isAlive()) continue;

            double dist = mc.player.distanceTo(entity);

            if (dist < bestDist) {
                bestDist = dist;
                best = entity;
            }
        }

        return best;
    }

    private int getScaffoldSlot() {
        ItemStack offhand = mc.player.getOffHandStack();

        if (!offhand.isEmpty() && offhand.getItem() == Items.SCAFFOLDING) {
            return mc.player.getInventory().getSelectedSlot();
        }

        return InventoryUtil.getHotbarItem(Items.SCAFFOLDING).getSlot();
    }

    private List<BlockPos> getPositions(Entity target) {
        List<BlockPos> positions = new ArrayList<>();

        BlockPos feet = BlockPos.ofFloored(
                target.getX(),
                target.getBoundingBox().minY,
                target.getZ()
        );

        BlockPos head = feet.up();

        if (placeMode.getValue() == PlaceMode.LEGS || placeMode.getValue() == PlaceMode.BOTH) {
            positions.add(feet);
        }

        if (placeMode.getValue() == PlaceMode.HEAD || placeMode.getValue() == PlaceMode.BOTH) {
            positions.add(head);
        }

        return positions;
    }
}

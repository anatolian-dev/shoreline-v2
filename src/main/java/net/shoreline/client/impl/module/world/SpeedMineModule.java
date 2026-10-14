package net.shoreline.client.impl.module.world;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.shoreline.client.api.config.*;
import net.shoreline.client.api.module.GuiCategory;
import net.shoreline.client.api.module.ListeningToggleable;
import net.shoreline.client.impl.Managers;
import net.shoreline.client.impl.event.TickEvent;
import net.shoreline.client.impl.module.combat.AutoMineModule;
import net.shoreline.client.impl.module.impl.Priorities;
import net.shoreline.client.impl.event.WorldEvent;
import net.shoreline.client.impl.event.entity.EntityDeathEvent;
import net.shoreline.client.impl.event.network.AttackBlockEvent;
import net.shoreline.client.impl.event.render.RenderWorldEvent;
import net.shoreline.client.impl.inventory.ItemSlot;
import net.shoreline.client.impl.inventory.SilentSwapType;
import net.shoreline.client.impl.mining.MiningData;
import net.shoreline.client.impl.mining.MiningPackets;
import net.shoreline.client.impl.mining.MiningUtil;
import net.shoreline.client.impl.render.animation.Animation;
import net.shoreline.client.impl.render.BoxRender;
import net.shoreline.client.impl.render.Easing;
import net.shoreline.client.util.entity.PlayerUtil;
import net.shoreline.eventbus.annotation.EventListener;

import java.awt.*;

@Getter
public class SpeedMineModule extends ListeningToggleable
{
    public static SpeedMineModule INSTANCE;

    Config<MiningPackets> miningPackets = new EnumConfig.Builder<MiningPackets>("Mode")
            .setValues(MiningPackets.values()).setDefaultValue(MiningPackets.NORMAL)
            .setDescription("The mode for block click packets").build();
    Config<Float> speedConfig = new NumberConfig.Builder<Float>("Speed")
            .setMin(0.5f).setMax(1.0f).setDefaultValue(1.0f)
            .setRoundingScale(2)
            .setDescription("The mining progress before breaking").build();
    Config<Boolean> doubleMine = new BooleanConfig.Builder("DoubleMine")
            .setDescription("Mines two blocks concurrently")
            .setDefaultValue(true).build();
    Config<Boolean> swingConfig = new BooleanConfig.Builder("Swing")
            .setDescription("Swings hand when sending mining packets")
            .setDefaultValue(true).build();
    Config<RemineMode> remineMode = new EnumConfig.Builder<RemineMode>("Remine")
            .setValues(RemineMode.values())
            .setDescription("The mode for remining mined blocks")
            .setDefaultValue(RemineMode.OFF).build();
    Config<Integer> instantLimit = new NumberConfig.Builder<Integer>("Limit")
            .setMin(1).setMax(20).setDefaultValue(10)
            .setDescription("Maximum mines per interval")
            .setVisible(() -> remineMode.getValue() == RemineMode.INSTANT).build();
    Config<Integer> limitInterval = new NumberConfig.Builder<Integer>("Interval")
            .setMin(25).setMax(1000).setDefaultValue(100).setFormat("ms")
            .setDescription("The interval for the limit")
            .setVisible(() -> remineMode.getValue() == RemineMode.INSTANT).build();
    Config<Float> rangeConfig = new NumberConfig.Builder<Float>("Range")
            .setMin(1.0f).setMax(6.0f).setDefaultValue(5.0f).setFormat("m")
            .setDescription("The max range to mine").build();
    Config<Boolean> multitaskConfig = new BooleanConfig.Builder("Multitask")
            .setDescription("Allows using items while mining")
            .setDefaultValue(true).build();
    Config<Boolean> rotateConfig = new BooleanConfig.Builder("Rotate")
            .setDescription("Rotates before mining block")
            .setDefaultValue(false).build();
    Config<SilentSwapType> swapType = new EnumConfig.Builder<SilentSwapType>("Swap")
            .setValues(SilentSwapType.values())
            .setDescription("The silent swap type")
            .setDefaultValue(SilentSwapType.HOTBAR).build();
    Config<Boolean> autoDisable = new BooleanConfig.Builder("AutoDisable")
            .setDescription("Disables on death to prevent double mine failing")
            .setDefaultValue(true).build();

    Config<BoxRender> boxMode = new EnumConfig.Builder<BoxRender>("BoxMode")
            .setValues(BoxRender.values())
            .setDescription("Box rendering mode")
            .setDefaultValue(BoxRender.FILL).build();
    Config<Color> miningColor = new ColorConfig.Builder("Mining")
            .setDescription("The color when mining a block")
            .setDefaultValue(Color.RED).build();
    Config<Color> breakingColor = new ColorConfig.Builder("Breaking")
            .setDescription("The color when breaking a block")
            .setDefaultValue(Color.GREEN).build();
    Config<Void> renderConfig = new ConfigGroup.Builder("Render")
            .addAll(boxMode, miningColor, breakingColor).build();

    @Setter
    private boolean isManualMining;

    private MiningData mainMiningBlock, packetMiningBlock;
    private MiningRenderState mainState, packetState;
    private boolean secondaryHolding;
    private int mainStopCooldown;
    private int packetStopCooldown;

    private MiningData pendingClear;

    private long instantStartMs;
    private int instantCount;

    private long lastStopMs;
    private double delayBalance;
    private int stopCooldown;

    public SpeedMineModule()
    {
        super("SpeedMine", new String[] {"SpeedyGonzales"}, "Mine faster", GuiCategory.WORLD);
        INSTANCE = this;
    }

    @Override
    public String getModuleData()
    {
        MiningData block = mainMiningBlock;
        return block != null ? (block.isDoneMining() ? Formatting.GREEN : Formatting.WHITE) +
                String.format("%.1f", Math.min(block.getBlockDamage(), 1.0f)) : super.getModuleData();
    }

    @Override
    public void onDisable()
    {
        if (checkNull())
        {
            return;
        }

        clearPacket();
        clearMain();
        pendingClear = null;
        instantStartMs = 0L;
        instantCount = 0;
        secondaryHolding = false;
        mainStopCooldown = 0;
        packetStopCooldown = 0;
        stopCooldown = 0;
        lastStopMs = 0L;
        delayBalance = 0;
    }

    @EventListener
    public void onWorldDisconnect(WorldEvent.Disconnect event)
    {
        if (autoDisable.getValue())
        {
            disable();
        }

        clearPacket();
        clearMain();
        pendingClear = null;
        instantStartMs = 0L;
        instantCount = 0;
        secondaryHolding = false;
        mainStopCooldown = 0;
        packetStopCooldown = 0;
        stopCooldown = 0;
        lastStopMs = 0L;
        delayBalance = 0;
    }

    @EventListener
    public void onEntityDeath(EntityDeathEvent event)
    {
        if (event.getEntity() == mc.player)
        {
            if (autoDisable.getValue())
            {
                disable();
            }

            clearPacket();
            clearMain();
            pendingClear = null;
            instantStartMs = 0L;
            instantCount = 0;
            secondaryHolding = false;
            mainStopCooldown = 0;
            packetStopCooldown = 0;
            stopCooldown = 0;
            lastStopMs = 0L;
            delayBalance = 0;
        }
    }

    @EventListener(priority = Priorities.SPEED_MINE)
    public void onTickEvent(TickEvent.Pre event)
    {
        if (mc.player == null || mc.world == null)
        {
            return;
        }

        if (stopCooldown > 0)
        {
            stopCooldown--;
        }

        tickPacket();

        if (isEnabled())
        {
            tickMain();
        }

        if (isManualMining && mainMiningBlock == null)
        {
            isManualMining = false;
        }
    }

    @EventListener
    public void onAttackBlock(AttackBlockEvent event)
    {
        if (checkNull() || !PlayerUtil.isInSurvival(mc.player))
        {
            return;
        }

        event.cancel();
        if (isMining(event.getPos()) || !MiningUtil.canMineBlock(event.getState()))
        {
            return;
        }

        isManualMining = true;
        startMining(event.getPos(), event.getDirection());
    }

    @EventListener
    public void onRenderWorld(RenderWorldEvent.Post event)
    {
        if (mainState != null)
        {
            if (mainState.getAnimation().getFactor() < 0.01f)
            {
                mainState = null;
            }
            else
            {
                mainState.data.render(event.getMatrixStack(),
                        event.getTickDelta(),
                        boxMode.getValue(),
                        miningColor.getValue().getRGB(),
                        breakingColor.getValue().getRGB(),
                        (float) Easing.SMOOTH_STEP.ease(mainState.getAnimation().getFactor()),
                        speedConfig.getValue());
            }
        }

        if (packetState != null)
        {
            if (packetState.getAnimation().getFactor() < 0.01f)
            {
                packetState = null;
            }
            else
            {
                packetState.data.render(event.getMatrixStack(),
                        event.getTickDelta(),
                        boxMode.getValue(),
                        miningColor.getValue().getRGB(),
                        breakingColor.getValue().getRGB(),
                        (float) Easing.SMOOTH_STEP.ease(packetState.getAnimation().getFactor()), 1.0f);
            }
        }
    }

    private boolean isGrim()
    {
        return miningPackets.getValue() == MiningPackets.GRIM
                || miningPackets.getValue() == MiningPackets.GRIM_V3;
    }

    private boolean canBegin()
    {
        if (!isGrim())
        {
            return true;
        }

        long delay = System.currentTimeMillis() - lastStopMs;
        if (delay >= 275)
        {
            return true;
        }

        double cost = (300 - delay) * 2;
        return delayBalance + cost <= 900;
    }

    private void trackStarts(int starts)
    {
        if (!isGrim())
        {
            return;
        }

        long delay = System.currentTimeMillis() - lastStopMs;
        for (int i = 0; i < starts; i++)
        {
            if (delay >= 275)
            {
                delayBalance *= 0.9;
            }
            else
            {
                delayBalance += (300 - delay);
            }
        }

        delayBalance = MathHelper.clamp((float) delayBalance, -1000.0f, 1000.0f);
    }

    private boolean demote()
    {
        if (mainMiningBlock == null || checkNull())
        {
            return false;
        }

        BlockState state = mc.world.getBlockState(mainMiningBlock.getBlockPos());
        if (state.isAir() || !MiningUtil.canMineBlock(state))
        {
            return false;
        }

        if (isGrim() && !mainMiningBlock.isDoneMining())
        {
            ItemSlot bestTool = AutoToolModule.INSTANCE.getBestTool(mainMiningBlock.getBlockPos());
            boolean swapped = bestTool != null && Managers.INVENTORY.startSwap(bestTool.getSlot(), swapType.getValue());

            miningPackets.getValue().sendStopPackets(this,
                    mainMiningBlock.getBlockPos(),
                    mainMiningBlock.getDirection());

            lastStopMs = System.currentTimeMillis();

            if (swingConfig.getValue())
            {
                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            }

            if (swapped)
            {
                Managers.INVENTORY.endSwap(swapType.getValue());
            }
        }

        packetMiningBlock = mainMiningBlock.copy(1.0f);
        packetState = new MiningRenderState(packetMiningBlock, new Animation(true, 300L));
        secondaryHolding = false;
        return true;
    }

    private void beginMain()
    {
        if (mainMiningBlock == null || checkNull())
        {
            return;
        }

        trackStarts(isGrim() ? 2 : 1);

        miningPackets.getValue().sendStartPackets(this,
                mainMiningBlock.getBlockPos(),
                mainMiningBlock.getDirection());

        if (swingConfig.getValue())
        {
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }

        mainMiningBlock.setStarted(true);
    }

    public void startMining(BlockPos blockPos, Direction direction)
    {
        if (checkNull())
        {
            return;
        }

        if (direction == null)
        {
            direction = Direction.UP;
        }

        float oldDamage = -1.0f;
        if (doubleMine.getValue())
        {
            if (pendingClear != null)
            {
                if (pendingClear.equals(mainMiningBlock) && mainMiningBlock.getBlockDamage() < speedConfig.getValue())
                {
                    clearMain();
                    if (pendingClear.getBlockPos().equals(blockPos))
                    {
                        oldDamage = pendingClear.getBlockDamage();
                    }
                }

                pendingClear = null;
            }

            if (mainMiningBlock != null && !mainMiningBlock.isBlockMined())
            {
                if (packetMiningBlock == null || packetMiningBlock.isBlockMined())
                {
                    demote();
                }
                else
                {
                    clearMain();
                }
            }
        }
        else
        {
            clearMain();
        }

        ItemSlot slot = AutoToolModule.INSTANCE.getBestTool(blockPos);

        mainMiningBlock = MiningData.builder()
                .blockPos(blockPos)
                .direction(direction)
                .maxProgress(speedConfig.getValue())
                .player(mc.player)
                .miningStack(slot == null ? mc.player.getMainHandStack() : slot.getItemStack())
                .build();

        if (oldDamage > 0.0f)
        {
            mainMiningBlock.setBlockDamage(oldDamage);
        }

        mainState = new MiningRenderState(mainMiningBlock, new Animation(true, 300L));
        mainStopCooldown = 0;

        if (stopCooldown == 0 && canBegin())
        {
            beginMain();
        }
        else
        {
            mainMiningBlock.setStarted(false);
        }
    }

    private boolean tryConsumeInstantBudget()
    {
        if (remineMode.getValue() != RemineMode.INSTANT)
        {
            return true;
        }

        long now = System.currentTimeMillis();
        int windowMs = limitInterval.getValue();
        if (windowMs <= 0)
        {
            windowMs = 100;
        }

        if (instantStartMs == 0L || now - instantStartMs >= (long) windowMs)
        {
            instantStartMs = now;
            instantCount = 0;
        }

        int limit = instantLimit.getValue();
        if (limit < 1)
        {
            limit = 1;
        }

        return instantCount < limit;
    }

    private void tickMain()
    {
        if (mainMiningBlock == null || checkNull())
        {
            return;
        }

        if (mainStopCooldown > 0)
        {
            mainStopCooldown--;
        }

        BlockState state = mc.world.getBlockState(mainMiningBlock.getBlockPos());

        if (!mainMiningBlock.isStarted())
        {
            if (state.isAir() || !MiningUtil.canMineBlock(state))
            {
                clearMain();
                return;
            }

            if (stopCooldown > 0 || !canBegin())
            {
                return;
            }

            beginMain();
            return;
        }

        if (state.isAir() || !MiningUtil.canMineBlock(state))
        {
            if (remineMode.getValue() == RemineMode.OFF)
            {
                clearMain();
                return;
            }

            mainMiningBlock.resetTicksMining();

            if (isManualMining)
            {
                isManualMining = false;
            }

            if (remineMode.getValue() != RemineMode.INSTANT)
            {
                mainMiningBlock.setBlockDamage(0.0f);
                mainMiningBlock.setLastDamage(0.0f);
                mainMiningBlock.setStarted(false);
                return;
            }

            if (!tryConsumeInstantBudget())
            {
                return;
            }
        }

        if (mainMiningBlock.getSquaredDistanceTo() > MathHelper.square(rangeConfig.getValue()))
        {
            clearMain();
            return;
        }

        if (!mainMiningBlock.isStarted() && !mainMiningBlock.isAir() && remineMode.getValue() == RemineMode.FAST)
        {
            beginMain();
        }

        boolean multiTasking = mc.player.isUsingItem() && !multitaskConfig.getValue();
        float blockDamage = mainMiningBlock.tickDelta(multiTasking);

        if (mainMiningBlock.hasMinedFor(30))
        {
            clearMain();
            return;
        }

        if (blockDamage < speedConfig.getValue())
        {
            return;
        }

        if (multiTasking)
        {
            return;
        }

        if (secondaryHolding && packetMiningBlock != null && !packetMiningBlock.isAir())
        {
            return;
        }

        if (mainStopCooldown > 0)
        {
            return;
        }

        ItemSlot bestTool = AutoToolModule.INSTANCE.getBestTool(mainMiningBlock.getBlockPos());
        if (bestTool != null && !Managers.INVENTORY.startSwap(bestTool.getSlot(), swapType.getValue()))
        {
            return;
        }

        instantCount++;
        miningPackets.getValue().sendStopPackets(this,
                mainMiningBlock.getBlockPos(),
                mainMiningBlock.getDirection());

        lastStopMs = System.currentTimeMillis();
        stopCooldown = 5;

        if (swingConfig.getValue())
        {
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }

        Managers.INVENTORY.endSwap(swapType.getValue());

        mainStopCooldown = 5;
    }

    private void tickPacket()
    {
        if (packetMiningBlock == null || checkNull())
        {
            if (secondaryHolding)
            {
                Managers.INVENTORY.endMultitickSwap();
                secondaryHolding = false;
            }
            return;
        }

        if (packetStopCooldown > 0)
        {
            packetStopCooldown--;
        }

        BlockState state = mc.world.getBlockState(packetMiningBlock.getBlockPos());
        if (state.isAir() || !MiningUtil.canMineBlock(state))
        {
            clearPacket();
            return;
        }

        if (packetMiningBlock.getSquaredDistanceTo() > MathHelper.square(rangeConfig.getValue()))
        {
            clearPacket();
            return;
        }

        boolean multiTasking = mc.player.isUsingItem() && !multitaskConfig.getValue();
        float blockDamage = packetMiningBlock.tickDelta(multiTasking);

        float delta = packetMiningBlock.getBlockBreakingDelta();
        if (delta <= 0.0f)
        {
            clearPacket();
            return;
        }

        int expected = (int) Math.ceil(1.0f / delta) - 1;

        if (packetMiningBlock.getTotalTicks() > expected + 10)
        {
            clearPacket();
            return;
        }

        if ((packetMiningBlock.getTotalTicks() >= expected - 1 || blockDamage >= 0.95f) && !secondaryHolding)
        {
            ItemSlot bestTool = AutoToolModule.INSTANCE.getBestTool(packetMiningBlock.getBlockPos());
            if (bestTool != null && Managers.INVENTORY.startMultitickSwap(bestTool.getSlot()))
            {
                secondaryHolding = true;
            }
        }

        if (!isGrim())
        {
            if ((mainMiningBlock == null || mainMiningBlock.isBlockMined()) && (packetMiningBlock.getTotalTicks() >= expected || blockDamage >= 1.0f) && !multiTasking)
            {
                if (packetStopCooldown == 0)
                {
                    ItemSlot bestTool = AutoToolModule.INSTANCE.getBestTool(packetMiningBlock.getBlockPos());
                    if (bestTool != null)
                    {
                        Managers.INVENTORY.startSwap(bestTool.getSlot(), swapType.getValue());
                    }

                    miningPackets.getValue().sendStopPackets(this,
                            packetMiningBlock.getBlockPos(),
                            packetMiningBlock.getDirection());

                    if (swingConfig.getValue())
                    {
                        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                    }

                    Managers.INVENTORY.endSwap(swapType.getValue());
                    packetStopCooldown = 5;
                }
            }
        }
    }

    private void clearMain()
    {
        if (mainMiningBlock != null)
        {
            if (!mainMiningBlock.isAir() && !mainMiningBlock.isDoneMining() && mainMiningBlock.isStarted())
            {
                mainMiningBlock.abort(this);
            }

            mainMiningBlock = null;
        }

        mainStopCooldown = 0;

        if (mainState != null)
        {
            mainState.getAnimation().setState(false);
        }
    }

    private void clearPacket()
    {
        if (secondaryHolding)
        {
            Managers.INVENTORY.endMultitickSwap();
            secondaryHolding = false;
        }

        packetMiningBlock = null;
        packetStopCooldown = 0;

        if (packetState != null)
        {
            packetState.getAnimation().setState(false);
        }
    }

    public boolean hasFreeMine()
    {
        return mainMiningBlock == null || packetMiningBlock == null;
    }

    public boolean isMining(BlockPos blockPos)
    {
        return mainMiningBlock != null && mainMiningBlock.getBlockPos().equals(blockPos)
                || packetMiningBlock != null && packetMiningBlock.getBlockPos().equals(blockPos);
    }

    public boolean isUsedByAutoMine()
    {
        return AutoMineModule.INSTANCE.isEnabled() && isEnabled();
    }

    @Getter
    @RequiredArgsConstructor
    private static class MiningRenderState
    {
        private final MiningData data;
        private final Animation animation;
    }

    private enum RemineMode
    {
        INSTANT, FAST, OFF
    }
}

package net.shoreline.client.impl.module.combat;

import it.unimi.dsi.fastutil.ints.Int2LongArrayMap;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import lombok.Getter;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.EndCrystalItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Colors;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.BlockView;
import net.shoreline.client.api.config.*;
import net.shoreline.client.api.math.NanoTimer;
import net.shoreline.client.api.math.Timer;
import net.shoreline.client.api.module.GuiCategory;
import net.shoreline.client.impl.Managers;
import net.shoreline.client.impl.event.TickEvent;
import net.shoreline.client.impl.event.network.EntitySpawnEvent;
import net.shoreline.client.impl.event.network.PacketEvent;
import net.shoreline.client.impl.event.render.RenderWorldEvent;
import net.shoreline.client.impl.inventory.InventoryUtil;
import net.shoreline.client.impl.inventory.SilentSwapType;
import net.shoreline.client.impl.module.client.ThemeModule;
import net.shoreline.client.impl.module.combat.crystal.CrystalCalcManager;
import net.shoreline.client.impl.module.combat.crystal.CrystalData;
import net.shoreline.client.impl.module.combat.crystal.CrystalOptimizer;
import net.shoreline.client.impl.module.combat.util.DamageUtil;
import net.shoreline.client.impl.module.combat.util.MovementExtrapolation;
import net.shoreline.client.impl.module.impl.ObsidianPlacerModule;
import net.shoreline.client.impl.network.NetworkUtil;
import net.shoreline.client.impl.render.BoxRender;
import net.shoreline.client.impl.render.ColorUtil;
import net.shoreline.client.impl.render.Easing;
import net.shoreline.client.impl.render.animation.Animation;
import net.shoreline.client.impl.rotation.ClientRotationEvent;
import net.shoreline.client.impl.rotation.RotateMode;
import net.shoreline.client.impl.rotation.Rotation;
import net.shoreline.client.impl.rotation.RotationUtil;
import net.shoreline.client.impl.world.EntityState;
import net.shoreline.client.impl.world.LivingEntityState;
import net.shoreline.client.impl.world.explosion.ExplosionUtil;
import net.shoreline.client.util.text.Formatter;
import net.shoreline.eventbus.annotation.EventListener;

import java.text.DecimalFormat;
import java.util.*;

@Getter
public class AutoCrystalRecodeModule extends ObsidianPlacerModule
{
    public static AutoCrystalRecodeModule INSTANCE;

    // Target Settings
    Config<Float> targetRange = new NumberConfig.Builder<Float>("TargetRange")
            .setMin(1.0f).setMax(15.0f).setDefaultValue(10.0f).setFormat("m")
            .setDescription("Range to scan for players").build();
    Config<Integer> extrapolateTicks = new NumberConfig.Builder<Integer>("Extrapolate")
            .setMin(0).setMax(20).setDefaultValue(2).setFormat("t")
            .setDescription("Ticks to extrapolate target movement with physics").build();
    Config<Boolean> targetNakeds = new BooleanConfig.Builder("Nakeds")
            .setDescription("Target naked players").setDefaultValue(false).build();
    Config<Void> targetGroup = new ConfigGroup.Builder("Target")
            .addAll(targetRange, extrapolateTicks, targetNakeds).build();

    // Break Settings
    Config<Float> breakRange = new NumberConfig.Builder<Float>("BreakRange")
            .setMin(1.0f).setMax(6.0f).setDefaultValue(4.5f).setFormat("m")
            .setDescription("Range to attack crystals").build();
    Config<Float> breakTrace = new NumberConfig.Builder<Float>("BreakTrace")
            .setMin(1.0f).setMax(6.0f).setDefaultValue(3.0f).setFormat("m")
            .setDescription("Wall trace range for breaking crystals").build();
    Config<Integer> breakDelay = new NumberConfig.Builder<Integer>("BreakDelay")
            .setMin(0).setMax(500).setDefaultValue(0).setFormat("ms")
            .setDescription("Delay between crystal attacks").build();
    Config<Integer> ticksExisted = new NumberConfig.Builder<Integer>("MinExisted")
            .setMin(0).setMax(10).setDefaultValue(0).setFormat("t")
            .setDescription("Ticks crystal must exist before attack").build();
    Config<Boolean> antiWeakness = new BooleanConfig.Builder("AntiWeakness")
            .setDescription("Swaps to sword/axe when affected by weakness").setDefaultValue(true).build();
    Config<Void> breakGroup = new ConfigGroup.Builder("Break")
            .addAll(breakRange, breakTrace, breakDelay, ticksExisted, antiWeakness).build();

    // Place Settings
    Config<Float> placeRange = new NumberConfig.Builder<Float>("PlaceRange")
            .setMin(1.0f).setMax(6.0f).setDefaultValue(4.5f).setFormat("m")
            .setDescription("Range to place crystals").build();
    Config<Float> placeTrace = new NumberConfig.Builder<Float>("PlaceTrace")
            .setMin(1.0f).setMax(6.0f).setDefaultValue(3.0f).setFormat("m")
            .setDescription("Wall trace range for placing crystals").build();
    Config<Integer> placeDelay = new NumberConfig.Builder<Integer>("PlaceDelay")
            .setMin(0).setMax(500).setDefaultValue(0).setFormat("ms")
            .setDescription("Delay between crystal placements").build();
    Config<Boolean> protocolPlace = new BooleanConfig.Builder("1.12-Protocol")
            .setDescription("Requires 2 air blocks above base").setDefaultValue(false).build();
    Config<Boolean> basePlace = new BooleanConfig.Builder("Support")
            .setDescription("Places obsidian under crystal if needed").setDefaultValue(false).build();
    Config<Void> placeGroup = new ConfigGroup.Builder("Place")
            .addAll(placeRange, placeTrace, placeDelay, protocolPlace, basePlace).build();

    // Damage Settings
    Config<Float> minDamage = new NumberConfig.Builder<Float>("MinDamage")
            .setMin(1.0f).setMax(36.0f).setDefaultValue(4.0f).setFormat("hp")
            .setDescription("Minimum damage to target").build();
    Config<Float> maxSelfDamage = new NumberConfig.Builder<Float>("MaxSelfDamage")
            .setMin(1.0f).setMax(36.0f).setDefaultValue(8.0f).setFormat("hp")
            .setDescription("Maximum self damage allowed").build();
    Config<Void> damageGroup = new ConfigGroup.Builder("Damage")
            .addAll(minDamage, maxSelfDamage).build();

    // Sequential & Timing
    Config<Boolean> instantBreak = new BooleanConfig.Builder("InstantBreak")
            .setDescription("Attacks spawned crystals instantly").setDefaultValue(true).build();
    Config<Boolean> instantPlace = new BooleanConfig.Builder("InstantPlace")
            .setDescription("Places crystals sequentially").setDefaultValue(true).build();
    Config<Void> seqGroup = new ConfigGroup.Builder("Sequential")
            .addAll(instantBreak, instantPlace).build();

    // Rotation & Misc
    Config<RotateMode> rotateConfig = new EnumConfig.Builder<RotateMode>("Rotate")
            .setValues(RotateMode.values()).setDefaultValue(RotateMode.OFF)
            .setDescription("Rotation mode").build();
    Config<SilentSwapType> silentType = new EnumConfig.Builder<SilentSwapType>("Swap")
            .setValues(SilentSwapType.values()).setDefaultValue(SilentSwapType.HOTBAR)
            .setDescription("Inventory swap method").build();
    Config<Boolean> swingConfig = new BooleanConfig.Builder("Swing")
            .setDescription("Client-side arm swing").setDefaultValue(true).build();

    // State Tracking
    private final Timer attackTimer = new NanoTimer();
    private final Timer placeTimer = new NanoTimer();
    private final CrystalOptimizer optimizer = new CrystalOptimizer();
    private final Int2LongMap attackPackets = new Int2LongArrayMap();
    private final Map<BlockPos, Animation> fadeAnimations = new HashMap<>();
    private static final DecimalFormat DECIMAL = new DecimalFormat("0.0");

    private CrystalData<BlockPos> currentPlace;
    private CrystalData<EntityState> currentAttack;
    private float[] currentRotations;
    private boolean silentRotated;

    private static final Box FULL_CRYSTAL_BB = new Box(-1.0, 0.0, -1.0, 1.0, 2.0, 1.0);
    private static final Box HALF_CRYSTAL_BB = new Box(-0.5, 0.0, -0.5, 0.5, 1.0, 0.5);

    public AutoCrystalRecodeModule()
    {
        super("AutoCrystalRecode", "Recoded modular crystal combat engine with physics prediction", GuiCategory.COMBAT);
        INSTANCE = this;
    }

    @Override
    public String getModuleData()
    {
        if (currentPlace != null)
        {
            return DECIMAL.format(currentPlace.getDamageToTarget());
        }
        return "Idle";
    }

    @Override
    public void onDisable()
    {
        currentPlace = null;
        currentAttack = null;
        currentRotations = null;
        silentRotated = false;
        attackPackets.clear();
        fadeAnimations.clear();
    }

    @EventListener
    public void onTick(TickEvent.Pre event)
    {
        if (checkNull()) return;

        silentRotated = false;
        long time = System.currentTimeMillis();
        attackPackets.values().removeIf(t -> time - t > 1000);

        Hand crystalHand = getCrystalHand();
        int crystalSlot = InventoryUtil.getItemSlot(Items.END_CRYSTAL, silentType.getValue());

        // Find nearby crystals to break
        List<CrystalData<EntityState>> crystalsToBreak = scanCrystalsToBreak();
        CrystalData<EntityState> bestAttack = getBestCrystal(crystalsToBreak);

        if (bestAttack != null)
        {
            currentAttack = bestAttack;
            currentRotations = runAttack(bestAttack, Hand.MAIN_HAND);
        }
        else
        {
            currentAttack = null;
        }

        // Find best placement
        if (crystalHand != null || (crystalSlot != -1 && !Managers.INVENTORY.isSilentSwapping()))
        {
            List<CrystalData<BlockPos>> placements = scanPlacements();
            CrystalData<BlockPos> bestPlace = getBestPlacement(placements);

            if (bestPlace != null)
            {
                currentPlace = bestPlace;
                if (placeTimer.hasPassed(placeDelay.getValue()))
                {
                    runPlace(bestPlace, crystalHand != null ? crystalHand : Hand.MAIN_HAND, crystalSlot);
                    placeTimer.reset();
                }
            }
            else
            {
                currentPlace = null;
            }
        }
    }

    @EventListener
    public void onClientRotation(ClientRotationEvent event)
    {
        if (checkNull() || currentRotations == null) return;

        if (rotateConfig.getValue() != RotateMode.OFF)
        {
            event.setRotation(new Rotation(currentRotations[0], currentRotations[1]));
        }
    }

    @EventListener
    public void onEntitySpawn(EntitySpawnEvent event)
    {
        if (checkNull() || !instantBreak.getValue() || event.getType() != EntityType.END_CRYSTAL) return;

        if (mc.player.getEntityPos().squaredDistanceTo(event.getPos()) <= MathHelper.square(breakRange.getValue()))
        {
            attackCrystal(event.getEntityId(), Hand.MAIN_HAND);
        }
    }

    @EventListener
    public void onPacketInbound(PacketEvent.Inbound event)
    {
        if (checkNull()) return;

        if (event.getPacket() instanceof ExplosionS2CPacket explosion)
        {
            Vec3d center = explosion.center();
            for (Entity entity : mc.world.getEntities())
            {
                if (entity instanceof EndCrystalEntity && entity.getEntityPos().squaredDistanceTo(center) <= 144.0)
                {
                    optimizer.setDead(entity.getId());
                }
            }
        }
        else if (event.getPacket() instanceof PlaySoundS2CPacket sound)
        {
            if (sound.getCategory() == SoundCategory.BLOCKS && sound.getSound().value() == SoundEvents.ENTITY_GENERIC_EXPLODE.value())
            {
                Vec3d center = new Vec3d(sound.getX(), sound.getY(), sound.getZ());
                for (Entity entity : mc.world.getEntities())
                {
                    if (entity instanceof EndCrystalEntity && entity.getEntityPos().squaredDistanceTo(center) <= 144.0)
                    {
                        optimizer.setDead(entity.getId());
                    }
                }
            }
        }
    }

    @EventListener
    public void onRenderWorld(RenderWorldEvent.Post event)
    {
        if (checkNull()) return;

        if (currentPlace != null)
        {
            BlockPos pos = currentPlace.getValue();
            Animation anim = fadeAnimations.computeIfAbsent(pos, p -> new Animation(true, 300, Easing.CUBIC_OUT));
            anim.setState(true);

            double factor = anim.getFactor();
            BoxRender.FILL.render(event.getMatrixStack(), pos, ThemeModule.INSTANCE.getPrimaryColor().getRGB(), (float) (factor * 0.4));
            BoxRender.OUTLINE.render(event.getMatrixStack(), pos, ThemeModule.INSTANCE.getPrimaryColor().getRGB(), (float) factor);

            String tag = DECIMAL.format(currentPlace.getDamageToTarget()) + " hp";
            Managers.RENDER.renderNametag(event.getMatrixStack(),
                    pos.toCenterPos().add(0, 0.5, 0),
                    0.003f,
                    tag,
                    ColorUtil.withTransparency(Colors.WHITE, (float) factor));
        }

        fadeAnimations.entrySet().removeIf(entry -> {
            if (currentPlace == null || !currentPlace.getValue().equals(entry.getKey()))
            {
                entry.getValue().setState(false);
            }
            return entry.getValue().getFactor() <= 0.0;
        });
    }

    private float[] runAttack(CrystalData<EntityState> attack, Hand hand)
    {
        EntityState crystalState = attack.getValue();
        Vec3d crystalVec = crystalState.getPos().add(0.0, 0.5, 0.0);
        float[] rotations = RotationUtil.getRotationsTo(mc.player.getEyePos(), crystalVec);

        if (rotateConfig.getValue() == RotateMode.SILENT && !silentRotated)
        {
            Managers.ROTATION.setSilentRotation(new Rotation(rotations[0], rotations[1]));
            silentRotated = true;
        }

        if (breakDelay.getValue() == 0 || attackTimer.hasPassed(breakDelay.getValue()))
        {
            attackCrystal(crystalState.getId(), hand);
            attackTimer.reset();
        }

        return rotations;
    }

    private void attackCrystal(int crystalId, Hand hand)
    {
        StatusEffectInstance weakness = mc.player.getStatusEffect(StatusEffects.WEAKNESS);
        StatusEffectInstance strength = mc.player.getStatusEffect(StatusEffects.STRENGTH);

        boolean canBreak = weakness == null || (strength != null && strength.getAmplifier() >= weakness.getAmplifier());
        int antiWeakSlot = -1;

        if (!canBreak && antiWeakness.getValue())
        {
            antiWeakSlot = getAntiWeaknessSlot();
            if (antiWeakSlot == -1 || !Managers.INVENTORY.startSwap(antiWeakSlot, silentType.getValue()))
            {
                return;
            }
        }

        sendAttackPacketsInternal(crystalId, swingConfig.getValue(), hand != null ? hand : Hand.MAIN_HAND);
        optimizer.setDead(crystalId);

        if (!canBreak && antiWeakSlot != -1)
        {
            Managers.INVENTORY.endSwap(silentType.getValue());
        }

        attackPackets.put(crystalId, System.currentTimeMillis());
    }

    private void runPlace(CrystalData<BlockPos> placement, Hand hand, int swapSlot)
    {
        BlockPos placePos = placement.getValue();
        boolean needsSwap = getCrystalHand() == null;

        if (needsSwap)
        {
            if (swapSlot == -1 || !Managers.INVENTORY.startSwap(swapSlot, silentType.getValue()))
            {
                return;
            }
        }

        placeCrystal(placePos, placement.getCrystalVec(), needsSwap ? Hand.MAIN_HAND : hand);

        if (needsSwap)
        {
            Managers.INVENTORY.endSwap(silentType.getValue());
        }
    }

    private void placeCrystal(BlockPos blockPos, Vec3d crystalVec, Hand hand)
    {
        Hand placeHand = hand != null ? hand : Hand.MAIN_HAND;
        Direction side = Direction.UP;
        BlockHitResult hit = new BlockHitResult(crystalVec != null ? crystalVec : blockPos.toCenterPos(), side, blockPos, false);
        sendSequencedPacket(id -> new PlayerInteractBlockC2SPacket(placeHand, hit, id));

        if (swingConfig.getValue())
        {
            mc.player.swingHand(placeHand);
        }
        else
        {
            sendPacket(new HandSwingC2SPacket(placeHand));
        }
    }

    private List<CrystalData<EntityState>> scanCrystalsToBreak()
    {
        List<CrystalData<EntityState>> results = new ArrayList<>();
        PlayerEntity target = getTarget();
        if (target == null) return results;

        Vec3d targetPos = getExtrapolatedPos(target);

        for (Entity entity : mc.world.getEntities())
        {
            if (!(entity instanceof EndCrystalEntity crystal) || !crystal.isAlive())
            {
                continue;
            }

            if (mc.player.squaredDistanceTo(crystal) > MathHelper.square(breakRange.getValue()))
            {
                continue;
            }

            float selfDamage = (float) ExplosionUtil.crystalDamageToEntity(mc.world, mc.player, crystal.getEntityPos(), true, Collections.emptySet());
            if (selfDamage > maxSelfDamage.getValue() || mc.player.getHealth() + mc.player.getAbsorptionAmount() - selfDamage < 1.0f)
            {
                continue;
            }

            float damage = (float) ExplosionUtil.crystalDamageToEntity(mc.world, target, crystal.getEntityPos(), true, Collections.emptySet());
            if (damage < minDamage.getValue())
            {
                continue;
            }

            EntityState state = new EntityState(crystal);
            results.add(new CrystalData.Immediate<>("Break", state, crystal.getEntityPos(), new LivingEntityState(target), damage, selfDamage));
        }

        return results;
    }

    private List<CrystalData<BlockPos>> scanPlacements()
    {
        List<CrystalData<BlockPos>> results = new ArrayList<>();
        PlayerEntity target = getTarget();
        if (target == null) return results;

        Vec3d targetPos = getExtrapolatedPos(target);
        BlockPos center = BlockPos.ofFloored(targetPos);
        int radius = (int) Math.ceil(placeRange.getValue());

        for (int x = -radius; x <= radius; x++)
        {
            for (int y = -3; y <= 3; y++)
            {
                for (int z = -radius; z <= radius; z++)
                {
                    BlockPos pos = center.add(x, y, z);
                    if (!canUseOnBlock(pos)) continue;

                    double dist = mc.player.squaredDistanceTo(pos.toCenterPos());
                    if (dist > MathHelper.square(placeRange.getValue())) continue;

                    Vec3d crystalVec = pos.toCenterPos().add(0, 0.5, 0);
                    float selfDmg = (float) ExplosionUtil.crystalDamageToEntity(mc.world, mc.player, crystalVec, true, Collections.emptySet());
                    if (selfDmg > maxSelfDamage.getValue() || mc.player.getHealth() + mc.player.getAbsorptionAmount() - selfDmg < 1.0f)
                    {
                        continue;
                    }

                    float targetDmg = (float) ExplosionUtil.crystalDamageToEntity(mc.world, target, crystalVec, true, Collections.emptySet());
                    if (targetDmg < minDamage.getValue()) continue;

                    results.add(new CrystalData.Immediate<>("Place", pos, crystalVec, new LivingEntityState(target), targetDmg, selfDmg));
                }
            }
        }

        return results;
    }

    private Vec3d getExtrapolatedPos(PlayerEntity player)
    {
        int ticks = extrapolateTicks.getValue();
        if (ticks <= 0) return player.getEntityPos();

        return MovementExtrapolation.extrapolatePosition(mc.world,
                box -> mc.world.getBlockCollisions(null, box),
                player.getVelocity(),
                player.getBoundingBox(),
                ticks);
    }

    public boolean canUseOnBlock(BlockPos pos)
    {
        BlockState state = mc.world.getBlockState(pos);
        if (!state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK))
        {
            return false;
        }

        BlockPos up = pos.up();
        BlockState upState = mc.world.getBlockState(up);
        if (!upState.isAir() && !upState.isOf(Blocks.FIRE))
        {
            return false;
        }

        if (protocolPlace.getValue() && !mc.world.getBlockState(up.up()).isAir())
        {
            return false;
        }

        Box box = getCrystalBox(pos);
        return mc.world.getOtherEntities(null, box, this::isBlockingEntity).isEmpty();
    }

    private boolean isBlockingEntity(Entity entity)
    {
        return entity.isAlive() && !(entity instanceof ItemEntity) && !(entity instanceof EndCrystalEntity);
    }

    public Box getCrystalBox(BlockPos pos)
    {
        Box bb = NetworkUtil.getServerIp().contains("crystalpvp.cc") ? HALF_CRYSTAL_BB : FULL_CRYSTAL_BB;
        return bb.offset(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
    }

    private Hand getCrystalHand()
    {
        if (mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)) return Hand.MAIN_HAND;
        if (mc.player.getOffHandStack().isOf(Items.END_CRYSTAL)) return Hand.OFF_HAND;
        return null;
    }

    private int getAntiWeaknessSlot()
    {
        for (int i = 0; i < 9; i++)
        {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isIn(ItemTags.SWORDS) || stack.isIn(ItemTags.AXES))
            {
                return i;
            }
        }
        return -1;
    }

    private PlayerEntity getTarget()
    {
        return Managers.TARGETING.getClosestTarget(targetRange.getValue());
    }

    private <T> CrystalData<T> getBestCrystal(List<CrystalData<T>> list)
    {
        if (list.isEmpty()) return null;
        CrystalData<T> best = null;
        double maxDmg = -1.0;
        for (CrystalData<T> data : list)
        {
            if (data.getDamageToTarget() > maxDmg)
            {
                maxDmg = data.getDamageToTarget();
                best = data;
            }
        }
        return best;
    }

    private CrystalData<BlockPos> getBestPlacement(List<CrystalData<BlockPos>> list)
    {
        return getBestCrystal(list);
    }
}

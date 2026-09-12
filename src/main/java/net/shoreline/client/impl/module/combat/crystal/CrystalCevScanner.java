package net.shoreline.client.impl.module.combat.crystal;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.shoreline.client.impl.mining.MiningData;
import net.shoreline.client.impl.module.combat.AutoCrystalModule;

public abstract class CrystalCevScanner extends CrystalTrapScanner
{
    protected CrystalCevScanner(AutoCrystalModule autoCrystal)
    {
        super(autoCrystal);
    }

    protected boolean isCevBreakerPos(BlockPos blockPos,
                                    PlayerEntity target,
                                    MiningData currentMine)
    {
        return false;
    }
}

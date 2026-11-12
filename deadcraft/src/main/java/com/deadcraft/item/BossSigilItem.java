package com.deadcraft.item;

import com.deadcraft.entity.DeadMob;
import com.deadcraft.registry.ModEntities;
import com.deadcraft.registry.ModParticles;
import com.deadcraft.registry.ModSounds;
import com.deadcraft.util.Fx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

/** Use on the ground to summon a boss. */
public class BossSigilItem extends Item {
    private final DeadMob boss;

    public BossSigilItem(DeadMob boss, Properties props) {
        super(props);
        this.boss = boss;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            if (ctx.getPlayer() != null) ctx.getPlayer().displayClientMessage(Component.translatable("message.deadcraft.peaceful"), true);
            return InteractionResult.FAIL;
        }
        BlockPos pos = ctx.getClickedPos().above();
        Mob mob = ModEntities.type(boss).create(level);
        if (mob == null) return InteractionResult.FAIL;
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, ctx.getRotation() + 180f, 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.TRIGGERED, null);
        if (ctx.getPlayer() != null) mob.setTarget(ctx.getPlayer());
        level.addFreshEntity(mob);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        level.sendParticles(ModParticles.SHOCKWAVE.get(), pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
        level.sendParticles(ModParticles.RUNE.get(), pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 120, 2, 2, 2, 0.1);
        level.playSound(null, pos, ModSounds.BOSS_ROAR.get(), SoundSource.HOSTILE, 4f, 0.9f);
        Fx.shake(level, pos.getCenter(), 48, 1.2f);
        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isFoil(ItemStack stack) { return true; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("tooltip.deadcraft.sigil", Component.translatable("entity.deadcraft." + boss.id))
                .withStyle(ChatFormatting.DARK_RED));
    }
}

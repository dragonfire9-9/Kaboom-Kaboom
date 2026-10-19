package com.kaboom.jjk.worldgen;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.entity.ModEntities;
import com.kaboom.jjk.npc.CurseUserEntity;
import com.kaboom.jjk.npc.NpcType;
import com.kaboom.jjk.npc.SorcererNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Jujutsu High sends students to villages on patrol; at night, Geto's followers sometimes show up too.
 * Gojo and Yaga stay at Jujutsu High (placed by the structures).
 */
@EventBusSubscriber(modid = JJKMod.MODID)
public final class NpcSpawner {
    private static final NpcType[] PATROLS = {NpcType.YUJI, NpcType.MEGUMI, NpcType.NOBARA, NpcType.MAKI, NpcType.PANDA, NpcType.INUMAKI, NpcType.NANAMI};

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % 1200 != 600) return;
        if (!level.dimensionType().natural()) return;
        for (ServerPlayer player : level.players()) {
            BlockPos pos = player.blockPosition();
            if (!level.isVillage(pos)) continue;
            var random = level.getRandom();
            AABB area = new AABB(pos).inflate(80);
            if (random.nextFloat() < 0.35f && level.getEntitiesOfClass(SorcererNpc.class, area).isEmpty()) {
                spawn(level, pos, ModEntities.npc(PATROLS[random.nextInt(PATROLS.length)]).create(level));
            }
            if (level.isNight() && random.nextFloat() < 0.15f && level.getEntitiesOfClass(CurseUserEntity.class, area).isEmpty()) {
                spawn(level, pos, ModEntities.CURSE_USER.get().create(level));
            }
        }
    }

    private static void spawn(ServerLevel level, BlockPos around, Mob mob) {
        if (mob == null) return;
        var random = level.getRandom();
        for (int i = 0; i < 10; i++) {
            int x = around.getX() + random.nextInt(41) - 20;
            int z = around.getZ() + random.nextInt(41) - 20;
            BlockPos p = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!level.getBlockState(p.below()).isSolid() || !level.getBlockState(p).isAir()) continue;
            mob.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, random.nextFloat() * 360, 0);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null);
            level.addFreshEntity(mob);
            return;
        }
    }

    private NpcSpawner() {}
}

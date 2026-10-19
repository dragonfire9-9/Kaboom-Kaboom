package com.kaboom.jjk.npc;

import com.kaboom.jjk.data.ModAttachments;
import com.kaboom.jjk.effect.ModEffects;
import com.kaboom.jjk.entity.CurseEntity;
import com.kaboom.jjk.entity.ModEntities;
import com.kaboom.jjk.item.StudentIdItem;
import com.kaboom.jjk.network.OpenNpcPayload;
import com.kaboom.jjk.particle.ModParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * A jujutsu sorcerer (Gojo, Yuji, Megumi, ...). Talk to open the dialogue screen: missions, trades
 * (registered Student ID required) and, for teachers, training. Fights curses and Exiled players.
 */
public class SorcererNpc extends PathfinderMob implements Merchant {
    private NpcType npcType;
    @Nullable private Player tradingPlayer;
    @Nullable private MerchantOffers offers;

    public SorcererNpc(EntityType<? extends SorcererNpc> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public NpcType npcType() {
        if (npcType == null) npcType = ModEntities.npcTypeOf(getType());
        return npcType;
    }

    public static AttributeSupplier.Builder attributes(NpcType t) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, t.health)
                .add(Attributes.ATTACK_DAMAGE, t.damage)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 24)
                .add(Attributes.ARMOR, 6);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(2, new OpenDoorGoal(this, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, CurseEntity.class, 10, true, false,
                c -> ((CurseEntity) c).getOwnerUUID() == null));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, CurseUserEntity.class, true));
        // Sukuna's vessels are executed on sight.
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false,
                p -> p.hasEffect(ModEffects.EXILED) && !((Player) p).isCreative()));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide() && tickCount % 40 == 0) {
            if (getHealth() < getMaxHealth()) heal(2f);
            // The strongest sorcerer keeps Infinity up at all times.
            if (npcType() == NpcType.GOJO && !hasEffect(ModEffects.INFINITY)) {
                addEffect(new MobEffectInstance(ModEffects.INFINITY, MobEffectInstance.INFINITE_DURATION, 0, false, false));
            }
        }
        if (level().isClientSide() && getTarget() != null && tickCount % 3 == 0) {
            level().addParticle(ModParticles.glow(npcType().color, 1.1f, 12), getRandomX(0.5), getRandomY(), getRandomZ(0.5), 0, 0.02, 0);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!isAlive() || getTarget() == player) return super.mobInteract(player, hand);
        if (player instanceof ServerPlayer sp) {
            if (player.hasEffect(ModEffects.EXILED)) {
                player.displayClientMessage(Component.translatable("jjk.npc.refuse_exiled").withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            openDialogue(sp);
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    public void openDialogue(ServerPlayer player) {
        var data = ModAttachments.get(player);
        CompoundTag tag = new CompoundTag();
        tag.putString("npc", npcType().id);
        tag.putBoolean("hasId", StudentIdItem.carriesValidId(player));
        tag.putBoolean("teacher", npcType().teacher);
        tag.putBoolean("rct", data.isRctUnlocked());
        tag.putBoolean("blackFlash", data.isBlackFlashUnlocked());
        Mission active = data.getMission();
        if (active != null) tag.put("active", active.save());
        tag.put("offer", MissionService.offerFor(npcType(), data).save());
        if (player.connection.hasChannel(OpenNpcPayload.TYPE)) PacketDistributor.sendToPlayer(player, new OpenNpcPayload(getId(), tag));
    }

    public void openTrades(ServerPlayer player) {
        if (!StudentIdItem.carriesValidId(player)) {
            player.displayClientMessage(Component.translatable("jjk.npc.need_id").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (tradingPlayer != null && tradingPlayer != player) return;
        setTradingPlayer(player);
        openTradingScreen(player, getDisplayName(), 1);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    // ------------------------------------------------------------------ Merchant

    @Override
    public void setTradingPlayer(@Nullable Player player) {
        tradingPlayer = player;
    }

    @Override
    public @Nullable Player getTradingPlayer() {
        return tradingPlayer;
    }

    @Override
    public MerchantOffers getOffers() {
        if (offers == null) offers = NpcTrades.forType(npcType());
        return offers;
    }

    @Override
    public void overrideOffers(MerchantOffers offers) {
        this.offers = offers;
    }

    @Override
    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        playSound(SoundEvents.VILLAGER_YES, 0.6f, 1.1f);
    }

    @Override
    public void notifyTradeUpdated(ItemStack stack) {
    }

    @Override
    public int getVillagerXp() {
        return 0;
    }

    @Override
    public void overrideXp(int xp) {
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return SoundEvents.VILLAGER_YES;
    }

    @Override
    public boolean isClientSide() {
        return level().isClientSide();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (offers != null) tag.put("Offers", MerchantOffers.CODEC.encodeStart(registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), offers).getOrThrow());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Offers")) {
            MerchantOffers.CODEC.parse(registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), tag.get("Offers"))
                    .resultOrPartial(e -> {}).ifPresent(o -> offers = o);
        }
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !(target instanceof SorcererNpc) && super.canAttack(target);
    }
}

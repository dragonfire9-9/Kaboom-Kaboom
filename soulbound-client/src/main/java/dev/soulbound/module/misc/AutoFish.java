package dev.soulbound.module.misc;

import dev.soulbound.mixin.FishingBobberAccessor;
import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

import java.util.concurrent.ThreadLocalRandom;

/** Reels in the moment a fish bites and casts again after a short, random pause. */
public final class AutoFish extends Module {
    private final NumberSetting reaction = add(new NumberSetting("Reaction", "Delay before reeling in", 150, 0, 600, 10, "ms"));
    private final NumberSetting recast = add(new NumberSetting("Recast Delay", "Delay before casting again", 600, 100, 3000, 50, "ms"));
    private final BoolSetting autoRecast = add(new BoolSetting("Recast", "Cast again after a catch", true));
    private final BoolSetting saveRod = add(new BoolSetting("Save Rod", "Stop before your rod breaks", true));
    private final BoolSetting swapRod = add(new BoolSetting("Swap Rods", "Switch to another rod in the hotbar when this one is nearly broken", true));
    private final NumberSetting timeoutSec = add(new NumberSetting("Recast Timeout", "Reel in and recast if nothing bites for this long", 45, 10, 120, 5, "s"));
    private long castTime = -1;
    private long reelAt = -1, castAt = -1;

    public AutoFish() {
        super("Auto Fish", "Catches fish for you.", Category.MISC);
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null || mc.interactionManager == null || mc.currentScreen != null) return;
        Hand hand = p.getMainHandStack().isOf(Items.FISHING_ROD) ? Hand.MAIN_HAND : p.getOffHandStack().isOf(Items.FISHING_ROD) ? Hand.OFF_HAND : null;
        if (hand != null) {
            var rod = p.getStackInHand(hand);
            if (rod.getMaxDamage() - rod.getDamage() <= 3) {
                int other = -1;
                for (int i = 0; i < 9 && swapRod.isOn(); i++) {
                    var s = p.getInventory().getStack(i);
                    if (s.isOf(Items.FISHING_ROD) && s != rod && s.getMaxDamage() - s.getDamage() > 3) other = i;
                }
                if (other >= 0 && hand == Hand.MAIN_HAND) p.getInventory().setSelectedSlot(other);
                else if (saveRod.isOn()) { reelAt = castAt = -1; return; }
            }
        }
        if (hand == null) { reelAt = castAt = -1; return; }
        long now = System.currentTimeMillis();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        var bobber = p.fishHook;
        if (bobber != null && reelAt < 0 && ((FishingBobberAccessor) bobber).soulbound$caughtFish())
            reelAt = now + (long) (reaction.get() * r.nextDouble(0.7, 1.3));
        if (reelAt >= 0 && now >= reelAt) {
            reelAt = -1;
            if (p.fishHook != null) {
                use(p, hand);
                if (autoRecast.isOn()) castAt = now + (long) (recast.get() * r.nextDouble(0.8, 1.4));
            }
        }
        if (p.fishHook != null && castTime > 0 && now - castTime > timeoutSec.get() * 1000) {
            use(p, hand);
            castTime = -1;
            castAt = now + 400;
        }
        if (p.fishHook != null && castTime < 0) castTime = now;
        if (p.fishHook == null) castTime = -1;
        if (castAt >= 0 && now >= castAt) {
            castAt = -1;
            if (p.fishHook == null) use(p, hand);
        }
    }

    private void use(net.minecraft.client.network.ClientPlayerEntity p, Hand hand) {
        mc.interactionManager.interactItem(p, hand);
        p.swingHand(hand);
    }

    @Override
    protected void onDisable() { reelAt = castAt = -1; }
}

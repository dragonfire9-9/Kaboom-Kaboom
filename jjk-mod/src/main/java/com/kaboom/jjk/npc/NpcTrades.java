package com.kaboom.jjk.npc;

import com.kaboom.jjk.item.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.Optional;

/** What each sorcerer sells. Currency: cursed fragments, CE shards and special grade objects. */
public final class NpcTrades {

    public static MerchantOffers forType(NpcType type) {
        MerchantOffers o = new MerchantOffers();
        Item frag = ModItems.CURSED_FRAGMENT.get();
        Item shard = ModItems.CE_SHARD.get();
        Item sgo = ModItems.SPECIAL_GRADE_OBJECT.get();
        switch (type) {
            case GOJO -> {
                o.add(offer(sgo, 3, shard, 32, new ItemStack(ModItems.GOJO_BLINDFOLD.get())));
                o.add(offer(sgo, 4, shard, 48, new ItemStack(ModItems.GOJO_JACKET.get())));
                o.add(offer(sgo, 3, shard, 40, new ItemStack(ModItems.GOJO_PANTS.get())));
                o.add(offer(sgo, 2, shard, 24, new ItemStack(ModItems.GOJO_SHOES.get())));
                o.add(offer(sgo, 2, null, 0, new ItemStack(ModItems.CURSED_ENERGY_SCROLL.get())));
            }
            case YAGA -> {
                o.add(offer(frag, 12, null, 0, new ItemStack(ModItems.JUJUTSU_UNIFORM_JACKET.get())));
                o.add(offer(frag, 10, null, 0, new ItemStack(ModItems.JUJUTSU_UNIFORM_PANTS.get())));
                o.add(offer(frag, 6, null, 0, new ItemStack(ModItems.JUJUTSU_UNIFORM_SHOES.get())));
                o.add(offer(shard, 24, frag, 16, new ItemStack(ModItems.CLAN_SCROLL.get())));
            }
            case NANAMI -> {
                o.add(offer(frag, 24, shard, 12, new ItemStack(ModItems.NANAMI_JACKET.get())));
                o.add(offer(frag, 20, shard, 10, new ItemStack(ModItems.NANAMI_SLACKS.get())));
                o.add(offer(frag, 12, shard, 6, new ItemStack(ModItems.NANAMI_SHOES.get())));
            }
            case MAKI -> {
                o.add(offer(frag, 8, null, 0, new ItemStack(ModItems.CURSE_GLASSES.get())));
                o.add(offer(frag, 16, Items.IRON_INGOT, 4, new ItemStack(ModItems.SLAUGHTER_DEMON.get())));
                o.add(offer(sgo, 2, frag, 24, new ItemStack(ModItems.PLAYFUL_CLOUD.get())));
                o.add(offer(sgo, 1, frag, 20, new ItemStack(ModItems.DRAGON_BONE.get())));
            }
            case PANDA, INUMAKI -> {
                o.add(offer(frag, 3, null, 0, new ItemStack(ModItems.TALISMAN.get(), 2)));
                o.add(offer(shard, 6, null, 0, new ItemStack(ModItems.CE_BOTTLE.get())));
            }
            case YUJI, MEGUMI, NOBARA -> {
                o.add(offer(frag, 4, null, 0, new ItemStack(ModItems.CE_SHARD.get(), 2)));
                o.add(offer(shard, 20, frag, 12, new ItemStack(ModItems.CURSED_ENERGY_SCROLL.get())));
                o.add(offer(Items.EMERALD, 6, null, 0, new ItemStack(ModItems.CURSED_FRAGMENT.get(), 3)));
            }
        }
        return o;
    }

    private static MerchantOffer offer(Item a, int countA, Item b, int countB, ItemStack result) {
        Optional<ItemCost> second = b == null ? Optional.empty() : Optional.of(new ItemCost(b, countB));
        return new MerchantOffer(new ItemCost(a, countA), second, result, 8, 0, 0.05f);
    }

    private NpcTrades() {}
}

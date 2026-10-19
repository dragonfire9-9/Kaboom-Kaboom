package com.kaboom.jjk.item;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.roll.RollType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(JJKMod.MODID);

    /** Creative tab icon only (Jujutsu High emblem). */
    public static final DeferredItem<Item> JJK_EMBLEM = ITEMS.registerSimpleItem("jjk_emblem", new Item.Properties().stacksTo(1));

    // ---- Roll scrolls
    public static final DeferredItem<ScrollItem> CURSED_ENERGY_SCROLL = ITEMS.register("cursed_energy_scroll",
            () -> new ScrollItem(RollType.CURSED_ENERGY, new Item.Properties().rarity(Rarity.RARE).stacksTo(16)));
    public static final DeferredItem<ScrollItem> CLAN_SCROLL = ITEMS.register("clan_scroll",
            () -> new ScrollItem(RollType.CLAN, new Item.Properties().rarity(Rarity.RARE).stacksTo(16)));
    public static final DeferredItem<ScrollItem> HEAVENLY_RESTRICTION_SCROLL = ITEMS.register("heavenly_restriction_scroll",
            () -> new ScrollItem(RollType.HEAVENLY_RESTRICTION, new Item.Properties().rarity(Rarity.EPIC).stacksTo(16)));

    private ModItems() {}
}

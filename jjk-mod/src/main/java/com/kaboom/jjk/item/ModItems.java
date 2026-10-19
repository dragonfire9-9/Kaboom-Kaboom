package com.kaboom.jjk.item;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.roll.RollType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.ItemAttributeModifiers;
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

    // ---- Materials and drops
    public static final DeferredItem<Item> CURSED_FRAGMENT = ITEMS.register("cursed_fragment",
            () -> new TooltipItem("cursed_fragment", new Item.Properties()));
    public static final DeferredItem<ConsumableCursedObjectItem> CE_SHARD = ITEMS.register("ce_shard",
            () -> new ConsumableCursedObjectItem(ConsumableCursedObjectItem.Kind.CE_SHARD, new Item.Properties()
                    .food(snack(true))));

    // ---- Sukuna's fingers and cursed objects
    public static final DeferredItem<SukunaFingerItem> SUKUNA_FINGER = ITEMS.register("sukuna_finger",
            () -> new SukunaFingerItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(20).fireResistant()
                    .food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.1f).alwaysEdible().build())));
    public static final DeferredItem<ConsumableCursedObjectItem> DEATH_PAINTING = ITEMS.register("death_painting",
            () -> new ConsumableCursedObjectItem(ConsumableCursedObjectItem.Kind.DEATH_PAINTING, new Item.Properties()
                    .rarity(Rarity.EPIC).stacksTo(9).food(snack(false))));
    public static final DeferredItem<ConsumableCursedObjectItem> SPECIAL_GRADE_OBJECT = ITEMS.register("special_grade_object",
            () -> new ConsumableCursedObjectItem(ConsumableCursedObjectItem.Kind.SPECIAL_GRADE_OBJECT, new Item.Properties()
                    .rarity(Rarity.EPIC).stacksTo(16).food(snack(false))));
    public static final DeferredItem<CEBottleItem> CE_BOTTLE = ITEMS.register("ce_bottle",
            () -> new CEBottleItem(0.5, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<Item> TALISMAN = ITEMS.register("talisman",
            () -> new TooltipItem("talisman", new Item.Properties().stacksTo(16)));
    public static final DeferredItem<PrisonRealmItem> PRISON_REALM = ITEMS.register("prison_realm",
            () -> new PrisonRealmItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<StudentIdItem> STUDENT_ID = ITEMS.register("student_id",
            () -> new StudentIdItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ArmorItem> CURSE_GLASSES = ITEMS.register("curse_glasses",
            () -> new ArmorItem(ModArmorMaterials.CURSE_GLASSES, ArmorItem.Type.HELMET,
                    new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(12))));

    // ---- Cursed tools
    public static final DeferredItem<CursedToolItem> INVERTED_SPEAR_OF_HEAVEN = tool("inverted_spear_of_heaven",
            CursedToolItem.Kind.INVERTED_SPEAR_OF_HEAVEN, Tiers.NETHERITE, 4, -2.6f, 0, Rarity.EPIC);
    public static final DeferredItem<CursedToolItem> PLAYFUL_CLOUD = tool("playful_cloud",
            CursedToolItem.Kind.PLAYFUL_CLOUD, Tiers.NETHERITE, 9, -3.2f, 0.5, Rarity.EPIC);
    public static final DeferredItem<CursedToolItem> SPLIT_SOUL_KATANA = tool("split_soul_katana",
            CursedToolItem.Kind.SPLIT_SOUL_KATANA, Tiers.NETHERITE, 5, -2.4f, 0, Rarity.EPIC);
    public static final DeferredItem<CursedToolItem> DRAGON_BONE = tool("dragon_bone",
            CursedToolItem.Kind.DRAGON_BONE, Tiers.DIAMOND, 6, -2.8f, 0, Rarity.RARE);
    public static final DeferredItem<CursedToolItem> SLAUGHTER_DEMON = tool("slaughter_demon",
            CursedToolItem.Kind.SLAUGHTER_DEMON, Tiers.IRON, 4, -2.2f, 0, Rarity.RARE);
    public static final DeferredItem<CursedToolItem> CHAIN_OF_A_THOUSAND_MILES = tool("chain_of_a_thousand_miles",
            CursedToolItem.Kind.CHAIN_OF_A_THOUSAND_MILES, Tiers.IRON, 3, -2.6f, 5.0, Rarity.RARE);
    public static final DeferredItem<CursedToolItem> BLACK_ROPE = tool("black_rope",
            CursedToolItem.Kind.BLACK_ROPE, Tiers.IRON, 2, -2.0f, 2.0, Rarity.RARE);

    /** Zero-nutrition "food" so absorbable objects use the eating animation. */
    private static FoodProperties snack(boolean fast) {
        FoodProperties.Builder b = new FoodProperties.Builder().nutrition(0).saturationModifier(0).alwaysEdible();
        return fast ? b.fast().build() : b.build();
    }

    /**
     * @param extraReach added entity interaction range (chains and ropes)
     */
    private static DeferredItem<CursedToolItem> tool(String name, CursedToolItem.Kind kind, Tier tier, int damage, float speed,
                                                     double extraReach, Rarity rarity) {
        return ITEMS.register(name, () -> {
            ItemAttributeModifiers attributes = SwordItem.createAttributes(tier, damage, speed);
            if (extraReach > 0) {
                attributes = attributes.withModifierAdded(Attributes.ENTITY_INTERACTION_RANGE,
                        new AttributeModifier(JJKMod.id(name + "_reach"), extraReach, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND);
            }
            return new CursedToolItem(kind, tier, new Item.Properties().rarity(rarity).fireResistant()
                    .component(DataComponents.ATTRIBUTE_MODIFIERS, attributes));
        });
    }

    private ModItems() {}
}

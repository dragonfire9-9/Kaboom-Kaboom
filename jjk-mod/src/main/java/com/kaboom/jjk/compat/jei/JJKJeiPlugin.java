package com.kaboom.jjk.compat.jei;

import com.kaboom.jjk.JJKMod;
import com.kaboom.jjk.item.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** JEI integration: "info" pages explaining how the mod's special items are obtained and used. Loaded only by JEI. */
@JeiPlugin
public class JJKJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return JJKMod.id("jei");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<Item> documented = List.of(
                ModItems.CURSED_ENERGY_SCROLL.get(), ModItems.CLAN_SCROLL.get(), ModItems.HEAVENLY_RESTRICTION_SCROLL.get(),
                ModItems.SUKUNA_FINGER.get(), ModItems.PRISON_REALM.get(), ModItems.INVERTED_SPEAR_OF_HEAVEN.get(),
                ModItems.DEATH_PAINTING.get(), ModItems.SPECIAL_GRADE_OBJECT.get(), ModItems.CURSE_GLASSES.get(),
                ModItems.STUDENT_ID.get(), ModItems.TALISMAN.get(), ModItems.CE_BOTTLE.get(), ModItems.SUKUNA_KIMONO.get(),
                ModItems.GOJO_BLINDFOLD.get(), ModItems.TOJI_SHIRT.get());
        for (Item item : documented) {
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
            registration.addIngredientInfo(new ItemStack(item), VanillaTypes.ITEM_STACK, Component.translatable("jjk.jei." + id));
        }
    }
}

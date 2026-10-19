package com.kaboom.jjk.item;

import com.kaboom.jjk.JJKMod;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

/**
 * Outfit piece rendered with GeckoLib. {@code set} names the shared model:
 * {@code geo/item/armor/<set>.geo.json} and {@code textures/item/armor/<set>.png}.
 * Set bonuses live in {@link com.kaboom.jjk.event.ArmorBonuses}.
 */
public class JJKArmorItem extends ArmorItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final String set;

    public JJKArmorItem(String set, Holder<ArmorMaterial> material, Type type, Properties properties) {
        super(material, type, properties);
        this.set = set;
    }

    public String set() {
        return set;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoArmorRenderer<?> renderer;

            @Override
            public <T extends LivingEntity> HumanoidModel<?> getGeoArmorRenderer(@Nullable T livingEntity, ItemStack itemStack,
                                                                                 @Nullable EquipmentSlot equipmentSlot,
                                                                                 @Nullable HumanoidModel<T> original) {
                if (renderer == null) renderer = new GeoArmorRenderer<>(new DefaultedItemGeoModel<JJKArmorItem>(JJKMod.id("armor/" + set)));
                return renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.jjk.armor_set." + set + ".bonus").withStyle(ChatFormatting.DARK_AQUA));
    }
}

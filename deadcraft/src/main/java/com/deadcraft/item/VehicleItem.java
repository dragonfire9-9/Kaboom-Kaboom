package com.deadcraft.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;
import java.util.function.Supplier;

public class VehicleItem extends Item {
    private final Supplier<? extends EntityType<? extends Mob>> type;

    public VehicleItem(Properties props, Supplier<? extends EntityType<? extends Mob>> type) {
        super(props);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        BlockPos pos = ctx.getClickedPos().relative(ctx.getClickedFace());
        Mob vehicle = type.get().create(level);
        if (vehicle == null) return InteractionResult.FAIL;
        float yaw = ctx.getPlayer() != null ? ctx.getPlayer().getYRot() : 0f;
        vehicle.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
        vehicle.setYHeadRot(yaw);
        vehicle.yBodyRot = yaw;
        if (!level.noCollision(vehicle)) return InteractionResult.FAIL;
        level.addFreshEntity(vehicle);
        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.deadcraft.vehicle_pickup").withStyle(ChatFormatting.DARK_GRAY));
    }
}

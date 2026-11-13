package dev.soulbound.hud.elements;

import dev.soulbound.gui.render.Fonts;
import dev.soulbound.gui.render.Render2D;
import dev.soulbound.gui.render.Theme;
import dev.soulbound.hud.HudElement;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Worn armour with durability bars. */
public final class ArmorElement extends HudElement {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final ItemStack[] PREVIEW = {new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
            new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS)};
    private final List<ItemStack> items = new ArrayList<>();

    public ArmorElement() { super("Armor", true, 0.72, 1); }

    @Override
    public boolean layout(boolean editing) {
        items.clear();
        if (mc.player != null) for (EquipmentSlot s : SLOTS) {
            ItemStack st = mc.player.getEquippedStack(s);
            if (!st.isEmpty()) items.add(st);
        }
        if (items.isEmpty() && editing) items.addAll(List.of(PREVIEW));
        w = 6 + items.size() * 20;
        h = 30;
        return !items.isEmpty();
    }

    @Override
    public void draw(DrawContext ctx, float x, float y, boolean right, boolean editing) {
        card(ctx, x, y, w, h);
        float cx = x + 5;
        for (ItemStack st : items) {
            int ix = Math.round(cx + 1), iy = Math.round(y + 3);
            if (alpha > 0.5f) ctx.drawItem(st, ix, iy);
            if (st.isDamageable()) {
                float f = 1f - (float) st.getDamage() / st.getMaxDamage();
                int col = f > 0.5f ? Theme.lerp(0xFFFFD36B, 0xFF4ADE80, (f - 0.5f) * 2) : Theme.lerp(0xFFFF4D6A, 0xFFFFD36B, f * 2);
                Render2D.roundRect(ctx, cx + 1, y + 21, 16, 2, 1, a(Theme.TRACK_OFF));
                Render2D.roundRect(ctx, cx + 1, y + 21, 16 * f, 2, 1, a(col));
                String pct = Math.round(f * 100) + "";
                Fonts.LABEL.draw(ctx, pct, cx + 9 - Fonts.LABEL.width(pct) / 2f, y + 24, a(Theme.TEXT_MUTED));
            }
            cx += 20;
        }
    }
}

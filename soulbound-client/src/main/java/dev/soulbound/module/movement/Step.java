package dev.soulbound.module.movement;

import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.NumberSetting;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;

/** Walk straight up full blocks without jumping. */
public final class Step extends Module {
    private final NumberSetting height;
    private final BoolSetting notSneaking;
    private Double saved;

    public Step() {
        super("Step", "Step up blocks without jumping.", Category.MOVEMENT);
        height = add(new NumberSetting("Height", "Tallest step (vanilla is 0.6)", 1, 0.6, 2.5, 0.1, " blocks"));
        notSneaking = add(new BoolSetting("Not Sneaking", "Normal steps while sneaking", true));
    }

    @Override
    public void onTick() {
        var p = mc.player;
        if (p == null) return;
        EntityAttributeInstance a = p.getAttributeInstance(EntityAttributes.STEP_HEIGHT);
        if (a == null) return;
        if (saved == null) saved = a.getBaseValue();
        a.setBaseValue(notSneaking.isOn() && p.isSneaking() ? saved : height.get());
    }

    @Override
    protected void onDisable() {
        if (mc.player != null && saved != null) {
            EntityAttributeInstance a = mc.player.getAttributeInstance(EntityAttributes.STEP_HEIGHT);
            if (a != null) a.setBaseValue(saved);
        }
        saved = null;
    }
}

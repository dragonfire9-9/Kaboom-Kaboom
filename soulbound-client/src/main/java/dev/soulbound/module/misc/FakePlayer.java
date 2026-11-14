package dev.soulbound.module.misc;

import com.mojang.authlib.GameProfile;
import dev.soulbound.module.Category;
import dev.soulbound.module.Module;
import dev.soulbound.setting.BoolSetting;
import dev.soulbound.setting.TextSetting;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;

import java.util.UUID;

/** Spawns a client-side copy of you to practise aim on, test ESP, or line up builds. Only you can see it. */
public final class FakePlayer extends Module {
    private final TextSetting name;
    private final BoolSetting copyInventory, copyRotation;
    private OtherClientPlayerEntity fake;

    public FakePlayer() {
        super("Fake Player", "Spawn a dummy copy of yourself (client-side).", Category.MISC);
        name = add(new TextSetting("Name", "Name above its head", "SoulDummy", 16));
        copyInventory = add(new BoolSetting("Copy Gear", "Give it your armour and held items", true));
        copyRotation = add(new BoolSetting("Copy Rotation", "Face the way you're facing", true));
    }

    @Override
    protected void onEnable() {
        if (mc.world == null || mc.player == null) {
            setEnabled(false);
            return;
        }
        var p = mc.player;
        fake = new OtherClientPlayerEntity(mc.world, new GameProfile(UUID.randomUUID(), name.get().isBlank() ? "SoulDummy" : name.get()));
        fake.copyPositionAndRotation(p);
        if (!copyRotation.isOn()) fake.setYaw(p.getYaw() + 180);
        fake.setHeadYaw(fake.getYaw());
        fake.setBodyYaw(fake.getYaw());
        if (copyInventory.isOn()) {
            for (EquipmentSlot s : EquipmentSlot.values()) fake.equipStack(s, p.getEquippedStack(s).copy());
        }
        fake.setId(-424242);
        mc.world.addEntity(fake);
    }

    @Override
    protected void onDisable() {
        if (fake != null && mc.world != null) mc.world.removeEntity(fake.getId(), Entity.RemovalReason.DISCARDED);
        fake = null;
    }

    @Override
    public void onTick() {
        if (fake != null && (mc.world == null || fake.getEntityWorld() != mc.world)) fake = null;
    }
}

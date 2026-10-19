package com.kaboom.jjk.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * A sorcerer mission.
 * @param minCurseGrade minimum {@link com.kaboom.jjk.data.Grade} ordinal of curses that count (EXORCISE)
 */
public record Mission(Type type, int target, int progress, int minCurseGrade, String giver) {
    public enum Type { EXORCISE, RETRIEVE_FINGER }

    public boolean complete() {
        return type == Type.EXORCISE && progress >= target;
    }

    public Mission withProgress(int p) {
        return new Mission(type, target, Math.min(p, target), minCurseGrade, giver);
    }

    public int xpReward() {
        return switch (type) {
            case EXORCISE -> 30 * target * (1 + minCurseGrade);
            case RETRIEVE_FINGER -> 450;
        };
    }

    public Component describe() {
        return switch (type) {
            case EXORCISE -> Component.translatable("jjk.mission.exorcise", target,
                    Component.translatable("jjk.grade." + com.kaboom.jjk.data.Grade.values()[minCurseGrade].id()), progress);
            case RETRIEVE_FINGER -> Component.translatable("jjk.mission.retrieve_finger");
        };
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("type", type.name());
        t.putInt("target", target);
        t.putInt("progress", progress);
        t.putInt("minGrade", minCurseGrade);
        t.putString("giver", giver);
        return t;
    }

    public static Mission load(CompoundTag t) {
        if (!t.contains("type")) return null;
        Type type;
        try {
            type = Type.valueOf(t.getString("type"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        return new Mission(type, t.getInt("target"), t.getInt("progress"), t.getInt("minGrade"), t.getString("giver"));
    }
}

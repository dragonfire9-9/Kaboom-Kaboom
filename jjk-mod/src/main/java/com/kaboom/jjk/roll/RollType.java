package com.kaboom.jjk.roll;

public enum RollType {
    CURSED_ENERGY,
    CLAN,
    HEAVENLY_RESTRICTION;

    public String id() { return name().toLowerCase(); }

    public static RollType byOrdinal(int i) {
        RollType[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}

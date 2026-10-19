package com.kaboom.jjk.npc;

/** Jujutsu sorcerer NPCs. {@code teacher}s can train Reverse Cursed Technique and Black Flash. */
public enum NpcType {
    GOJO("gojo", 400, 20, 0xFF6FD8FF, true, true),
    YUJI("yuji", 80, 9, 0xFFF096AA, false, true),
    MEGUMI("megumi", 60, 7, 0xFF2A2440, false, false),
    NOBARA("nobara", 55, 7, 0xFFD2783C, false, false),
    NANAMI("nanami", 90, 10, 0xFFE6D28C, false, false),
    MAKI("maki", 75, 11, 0xFF284632, false, false),
    PANDA("panda", 110, 9, 0xFF202020, false, false),
    INUMAKI("inumaki", 55, 6, 0xFFE1E1E6, false, false),
    YAGA("yaga", 120, 10, 0xFF282828, true, false);

    public final String id;
    public final double health;
    public final double damage;
    public final int color;
    public final boolean teacher;
    /** Offers "retrieve a finger" missions once the player is strong enough. */
    public final boolean fingerMissions;

    NpcType(String id, double health, double damage, int color, boolean teacher, boolean fingerMissions) {
        this.id = id;
        this.health = health;
        this.damage = damage;
        this.color = color;
        this.teacher = teacher;
        this.fingerMissions = fingerMissions;
    }

    public static NpcType byId(String id) {
        for (NpcType t : values()) if (t.id.equals(id)) return t;
        return YUJI;
    }
}

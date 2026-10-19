package com.kaboom.jjk.data;

import com.kaboom.jjk.ability.Abilities;
import com.kaboom.jjk.ability.Ability;
import com.kaboom.jjk.config.JJKConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-player jujutsu data. Stored as a NeoForge data attachment on the player, persisted to NBT,
 * copied on death and synced server -> client with {@link com.kaboom.jjk.network.SyncJJKDataPayload}.
 */
public class CursedEnergyData implements INBTSerializable<CompoundTag> {
    private Technique technique = Technique.NONE;
    private Clan clan = Clan.CLANLESS;
    private CETier ceTier = CETier.AVERAGE;
    private CENature ceNature = CENature.STANDARD;
    private HeavenlyRestriction restriction = HeavenlyRestriction.NONE;
    private Grade grade = Grade.GRADE_4;

    private double currentCE = 0;
    private double bonusMaxCE = 0;
    private int experience = 0;
    private int curseKills = 0;

    private boolean rolledTechnique = false;
    private boolean rolledClan = false;
    private boolean rctUnlocked = false;
    private boolean blackFlashUnlocked = false;
    private int blackFlashCount = 0;
    private int domainsCast = 0;
    private int fingersEaten = 0;
    private boolean starterKitGiven = false;
    /** While positive, Sukuna has taken over: the player's technique is temporarily Shrine. */
    private int takeoverTicks = 0;

    /** Abilities unlocked from outside the grade ladder (teachers, items, events). */
    private final Set<String> bonusAbilities = new LinkedHashSet<>();
    /** Derived list, kept in a stable order for the HUD and cycling. */
    private final List<String> unlockedAbilities = new ArrayList<>();
    private int selectedAbility = 0;
    private final Map<String, Integer> cooldowns = new HashMap<>();

    private transient boolean dirty = true;

    public CursedEnergyData() {
        refreshUnlocks();
    }

    // ------------------------------------------------------------------ derived stats

    public double getMaxCE() {
        if (restriction == HeavenlyRestriction.PHYSICAL) return 0;
        double max = JJKConfig.BASE_MAX_CE.get() * ceTier.maxMultiplier * grade.ceMultiplier * clan.maxCEMultiplier();
        if (restriction == HeavenlyRestriction.CURSED_ENERGY) max *= 3.0;
        return max + bonusMaxCE;
    }

    /** CE regenerated per second. */
    public double getRegenPerSecond() {
        if (restriction == HeavenlyRestriction.PHYSICAL) return 0;
        double regen = JJKConfig.BASE_CE_REGEN.get() * ceTier.regenMultiplier * clan.regenMultiplier()
                * (1.0 + 0.15 * grade.ordinal());
        if (ceNature == CENature.FLOWING) regen *= 1.2;
        if (restriction == HeavenlyRestriction.CURSED_ENERGY) regen *= 2.5;
        return regen;
    }

    public double getCostMultiplier() {
        double m = JJKConfig.CE_COST_MULTIPLIER.get() * clan.costMultiplier();
        if (ceNature == CENature.PRISTINE) m *= 0.9;
        return m;
    }

    /** Range multiplier for projectiles and area techniques (Mechamaru-style restriction extends range). */
    public double getRangeMultiplier() {
        return restriction == HeavenlyRestriction.CURSED_ENERGY ? 1.6 : 1.0;
    }

    public double getTechniqueDamageMultiplier() {
        double m = clan.techniqueDamageMultiplier() * (1.0 + 0.12 * grade.ordinal());
        if (ceNature == CENature.SHARP) m *= 1.1;
        return m;
    }

    public boolean canUseCursedEnergy() {
        return restriction != HeavenlyRestriction.PHYSICAL;
    }

    /** Whether the player perceives curses without glasses (CE users, and Heavenly Restriction's heightened senses). */
    public boolean canSeeCurses() {
        return restriction == HeavenlyRestriction.PHYSICAL || getMaxCE() > 0;
    }

    public boolean isDomainUnlocked() {
        return canUseCursedEnergy() && grade.atLeast(Grade.GRADE_1) || domainsCast > 0 && canUseCursedEnergy();
    }

    // ------------------------------------------------------------------ CE

    public double getCurrentCE() { return currentCE; }

    public void setCurrentCE(double value) {
        double clamped = Mth.clamp(value, 0, getMaxCE());
        if (clamped != currentCE) {
            currentCE = clamped;
            dirty = true;
        }
    }

    public boolean trySpend(double amount) {
        if (amount <= 0) return true;
        if (currentCE < amount) return false;
        setCurrentCE(currentCE - amount);
        return true;
    }

    public void addBonusMaxCE(double amount) {
        bonusMaxCE += amount;
        dirty = true;
    }

    public double getBonusMaxCE() { return bonusMaxCE; }

    /** Called every server tick. */
    public void tick() {
        double max = getMaxCE();
        if (currentCE < max) setCurrentCE(currentCE + getRegenPerSecond() / 20.0);
        else if (currentCE > max) setCurrentCE(max);

        if (!cooldowns.isEmpty()) {
            cooldowns.replaceAll((k, v) -> v - 1);
            if (cooldowns.values().removeIf(v -> v <= 0)) dirty = true;
        }
    }

    // ------------------------------------------------------------------ progression

    /** Adds experience. Returns true if the grade went up. */
    public boolean addExperience(int amount) {
        if (amount <= 0) return false;
        experience += amount;
        dirty = true;
        Grade newGrade = Grade.forXp(experience);
        if (newGrade.ordinal() > grade.ordinal()) {
            grade = newGrade;
            refreshUnlocks();
            return true;
        }
        return false;
    }

    public void setGrade(Grade grade) {
        this.grade = grade;
        if (experience < grade.xpRequired) experience = grade.xpRequired;
        refreshUnlocks();
        dirty = true;
    }

    public void addCurseKill() {
        curseKills++;
        dirty = true;
    }

    // ------------------------------------------------------------------ abilities

    /** Rebuilds the unlocked ability list from technique + grade + bonus unlocks. */
    public void refreshUnlocks() {
        String previous = getSelectedAbilityId();
        unlockedAbilities.clear();
        for (Ability a : Abilities.availableFor(this)) unlockedAbilities.add(a.id());
        for (String id : bonusAbilities) {
            Ability a = Abilities.get(id);
            if (a != null && !unlockedAbilities.contains(id) && a.usableBy(this)) unlockedAbilities.add(id);
        }
        int idx = previous == null ? -1 : unlockedAbilities.indexOf(previous);
        selectedAbility = Math.max(idx, 0);
        dirty = true;
    }

    public void unlockBonusAbility(String id) {
        if (bonusAbilities.add(id)) refreshUnlocks();
    }

    public List<String> getUnlockedAbilities() {
        return Collections.unmodifiableList(unlockedAbilities);
    }

    public int getSelectedIndex() { return selectedAbility; }

    public String getSelectedAbilityId() {
        if (unlockedAbilities.isEmpty()) return null;
        return unlockedAbilities.get(Mth.clamp(selectedAbility, 0, unlockedAbilities.size() - 1));
    }

    public void cycleAbility(int direction) {
        if (unlockedAbilities.isEmpty()) return;
        selectedAbility = Math.floorMod(selectedAbility + direction, unlockedAbilities.size());
        dirty = true;
    }

    public int getCooldown(String abilityId) { return cooldowns.getOrDefault(abilityId, 0); }

    public void setCooldown(String abilityId, int ticks) {
        if (ticks <= 0) cooldowns.remove(abilityId);
        else cooldowns.put(abilityId, ticks);
        dirty = true;
    }

    /** Client-side smoothing: tick cooldowns down locally between syncs. */
    public void clientTickCooldowns() {
        if (!cooldowns.isEmpty()) {
            cooldowns.replaceAll((k, v) -> v - 1);
            cooldowns.values().removeIf(v -> v <= 0);
        }
    }

    // ------------------------------------------------------------------ simple accessors

    /** Effective technique (Shrine during a Sukuna takeover). */
    public Technique getTechnique() { return takeoverTicks > 0 ? Technique.SHRINE : technique; }
    /** The player's own innate technique, ignoring takeovers. */
    public Technique getInnateTechnique() { return technique; }
    public int getTakeoverTicks() { return takeoverTicks; }
    public boolean isTakenOver() { return takeoverTicks > 0; }

    public void setTakeoverTicks(int ticks) {
        boolean changed = (takeoverTicks > 0) != (ticks > 0);
        takeoverTicks = Math.max(0, ticks);
        if (changed) refreshUnlocks();
        dirty = true;
    }
    public Clan getClan() { return clan; }
    public CETier getCeTier() { return ceTier; }
    public CENature getCeNature() { return ceNature; }
    public HeavenlyRestriction getRestriction() { return restriction; }
    public Grade getGrade() { return grade; }
    public int getExperience() { return experience; }
    public int getCurseKills() { return curseKills; }
    public boolean hasRolledTechnique() { return rolledTechnique; }
    public boolean hasRolledClan() { return rolledClan; }
    public boolean isRctUnlocked() { return rctUnlocked; }
    public boolean isBlackFlashUnlocked() { return blackFlashUnlocked; }
    public int getBlackFlashCount() { return blackFlashCount; }
    public int getDomainsCast() { return domainsCast; }
    public int getFingersEaten() { return fingersEaten; }
    public boolean isStarterKitGiven() { return starterKitGiven; }
    public void setStarterKitGiven(boolean v) { starterKitGiven = v; dirty = true; }

    public void setTechnique(Technique technique, CETier tier, CENature nature) {
        this.technique = technique;
        this.ceTier = tier;
        this.ceNature = nature;
        this.rolledTechnique = true;
        refreshUnlocks();
        currentCE = Math.min(currentCE, getMaxCE());
        dirty = true;
    }

    public void setClan(Clan clan) {
        this.clan = clan;
        this.rolledClan = true;
        refreshUnlocks();
        dirty = true;
    }

    public void setRestriction(HeavenlyRestriction restriction) {
        this.restriction = restriction;
        if (restriction == HeavenlyRestriction.PHYSICAL) {
            technique = Technique.NONE;
            currentCE = 0;
        }
        refreshUnlocks();
        dirty = true;
    }

    public void setRctUnlocked(boolean v) { rctUnlocked = v; dirty = true; }
    public void setBlackFlashUnlocked(boolean v) { blackFlashUnlocked = v; dirty = true; }
    public void incrementBlackFlash() { blackFlashCount++; blackFlashUnlocked = true; dirty = true; }
    public void incrementDomainsCast() { domainsCast++; dirty = true; }
    public void setFingersEaten(int v) { fingersEaten = v; dirty = true; }

    public boolean isDirty() { return dirty; }
    public void markDirty() { dirty = true; }
    public void clearDirty() { dirty = false; }

    // ------------------------------------------------------------------ NBT

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putString("technique", technique.id());
        tag.putString("clan", clan.id());
        tag.putString("ceTier", ceTier.id());
        tag.putString("ceNature", ceNature.id());
        tag.putString("restriction", restriction.id());
        tag.putString("grade", grade.id());
        tag.putDouble("currentCE", currentCE);
        tag.putDouble("bonusMaxCE", bonusMaxCE);
        tag.putInt("experience", experience);
        tag.putInt("curseKills", curseKills);
        tag.putBoolean("rolledTechnique", rolledTechnique);
        tag.putBoolean("rolledClan", rolledClan);
        tag.putBoolean("rct", rctUnlocked);
        tag.putBoolean("blackFlash", blackFlashUnlocked);
        tag.putInt("blackFlashCount", blackFlashCount);
        tag.putInt("domainsCast", domainsCast);
        tag.putInt("fingersEaten", fingersEaten);
        tag.putBoolean("starterKit", starterKitGiven);
        tag.putInt("takeover", takeoverTicks);
        tag.putInt("selected", selectedAbility);

        ListTag bonus = new ListTag();
        bonusAbilities.forEach(id -> bonus.add(StringTag.valueOf(id)));
        tag.put("bonusAbilities", bonus);

        ListTag unlocked = new ListTag();
        unlockedAbilities.forEach(id -> unlocked.add(StringTag.valueOf(id)));
        tag.put("unlocked", unlocked);

        CompoundTag cds = new CompoundTag();
        cooldowns.forEach(cds::putInt);
        tag.put("cooldowns", cds);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        technique = Technique.byId(tag.getString("technique"));
        clan = Clan.byId(tag.getString("clan"));
        ceTier = CETier.byId(tag.getString("ceTier"));
        ceNature = CENature.byId(tag.getString("ceNature"));
        restriction = HeavenlyRestriction.byId(tag.getString("restriction"));
        grade = Grade.byId(tag.getString("grade"));
        currentCE = tag.getDouble("currentCE");
        bonusMaxCE = tag.getDouble("bonusMaxCE");
        experience = tag.getInt("experience");
        curseKills = tag.getInt("curseKills");
        rolledTechnique = tag.getBoolean("rolledTechnique");
        rolledClan = tag.getBoolean("rolledClan");
        rctUnlocked = tag.getBoolean("rct");
        blackFlashUnlocked = tag.getBoolean("blackFlash");
        blackFlashCount = tag.getInt("blackFlashCount");
        domainsCast = tag.getInt("domainsCast");
        fingersEaten = tag.getInt("fingersEaten");
        starterKitGiven = tag.getBoolean("starterKit");
        takeoverTicks = tag.getInt("takeover");

        bonusAbilities.clear();
        for (Tag t : tag.getList("bonusAbilities", Tag.TAG_STRING)) bonusAbilities.add(t.getAsString());

        cooldowns.clear();
        CompoundTag cds = tag.getCompound("cooldowns");
        for (String key : cds.getAllKeys()) cooldowns.put(key, cds.getInt(key));

        // The unlocked list is derived, but the client receives it verbatim so the HUD matches the server exactly.
        unlockedAbilities.clear();
        for (Tag t : tag.getList("unlocked", Tag.TAG_STRING)) unlockedAbilities.add(t.getAsString());
        if (unlockedAbilities.isEmpty()) refreshUnlocks();
        selectedAbility = Mth.clamp(tag.getInt("selected"), 0, Math.max(0, unlockedAbilities.size() - 1));
        dirty = true;
    }
}

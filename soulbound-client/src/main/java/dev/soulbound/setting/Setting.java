package dev.soulbound.setting;

import com.google.gson.JsonElement;

import java.util.function.BooleanSupplier;

/** A single configurable value shown in the GUI's parameter list. */
public abstract class Setting<T> {
    private final String name;
    private final String description;
    protected T value;
    private final T defaultValue;
    private BooleanSupplier visibility = () -> true;

    protected Setting(String name, String description, T defaultValue) {
        this.name = name;
        this.description = description;
        this.value = defaultValue;
        this.defaultValue = defaultValue;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public T get() { return value; }
    public void set(T value) { this.value = value; }
    public T getDefault() { return defaultValue; }
    public void reset() { set(defaultValue); }

    /** Hide this setting unless the supplier returns true (e.g. depends on another setting). */
    @SuppressWarnings("unchecked")
    public <S extends Setting<T>> S visibleWhen(BooleanSupplier supplier) {
        this.visibility = supplier;
        return (S) this;
    }

    public boolean isVisible() { return visibility.getAsBoolean(); }

    public abstract JsonElement toJson();
    public abstract void fromJson(JsonElement element);
}

package dev.soulbound.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

public class NumberSetting extends Setting<Double> {
    private final double min, max, step;
    private final String suffix;

    public NumberSetting(String name, String description, double defaultValue, double min, double max, double step) {
        this(name, description, defaultValue, min, max, step, "");
    }

    public NumberSetting(String name, String description, double defaultValue, double min, double max, double step, String suffix) {
        super(name, description, defaultValue);
        this.min = min;
        this.max = max;
        this.step = step;
        this.suffix = suffix;
    }

    public double getMin() { return min; }
    public double getMax() { return max; }
    public double getStep() { return step; }
    public float getFloat() { return value.floatValue(); }
    public int getInt() { return (int) Math.round(value); }

    @Override
    public void set(Double v) {
        double clamped = Math.max(min, Math.min(max, v));
        if (step > 0) clamped = Math.round((clamped - min) / step) * step + min;
        clamped = Math.round(clamped * 1e6) / 1e6;
        super.set(Math.max(min, Math.min(max, clamped)));
    }

    /** Fraction 0..1 of the current value inside [min, max]. */
    public double progress() { return max == min ? 0 : (value - min) / (max - min); }

    public void setProgress(double p) { set(min + (max - min) * Math.max(0, Math.min(1, p))); }

    public String display() {
        int decimals = 0;
        double s = step;
        while (decimals < 4 && Math.abs(s - Math.rint(s)) > 1e-9) { s *= 10; decimals++; }
        return String.format(Locale.ROOT, "%." + Math.max(decimals, step >= 1 ? 0 : 2) + "f", value) + suffix;
    }

    @Override public JsonElement toJson() { return new JsonPrimitive(value); }
    @Override public void fromJson(JsonElement e) { if (e.isJsonPrimitive()) set(e.getAsDouble()); }
}

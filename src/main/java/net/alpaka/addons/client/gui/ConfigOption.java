package net.alpaka.addons.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ConfigOption {
    public enum Type {
        BOOLEAN,
        SLIDER,
        ACTION,
        HEADER
    }

    private final String id;
    private final String title;
    private final String description;
    private final ConfigCategory category;
    private final Type type;
    private final String keywords;

    // Boolean
    private Supplier<Boolean> getterBool;
    private Consumer<Boolean> setterBool;

    // Slider
    private Supplier<Float> getterFloat;
    private Consumer<Float> setterFloat;
    private float minVal;
    private float maxVal;
    private Function<Float, String> formatter;

    // Action
    private Consumer<Screen> actionHandler;
    private String actionLabel;

    // Animation & Hover state tracking
    private float hoverProgress = 0.0f;
    private float clickProgress = 0.0f;
    private boolean isDragging = false;

    // Header constructor
    public ConfigOption(String title, ConfigCategory category) {
        this.id = title.toLowerCase().replace(" ", "_");
        this.title = title;
        this.description = "";
        this.category = category;
        this.type = Type.HEADER;
        this.keywords = title;
    }

    // Boolean option constructor
    public ConfigOption(String id, String title, String description, ConfigCategory category,
                        Supplier<Boolean> getterBool, Consumer<Boolean> setterBool, String keywords) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.category = category;
        this.type = Type.BOOLEAN;
        this.getterBool = getterBool;
        this.setterBool = setterBool;
        this.keywords = (title + " " + description + " " + keywords).toLowerCase();
    }

    // Float Slider option constructor
    public ConfigOption(String id, String title, String description, ConfigCategory category,
                        Supplier<Float> getterFloat, Consumer<Float> setterFloat,
                        float minVal, float maxVal, Function<Float, String> formatter, String keywords) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.category = category;
        this.type = Type.SLIDER;
        this.getterFloat = getterFloat;
        this.setterFloat = setterFloat;
        this.minVal = minVal;
        this.maxVal = maxVal;
        this.formatter = formatter;
        this.keywords = (title + " " + description + " " + keywords).toLowerCase();
    }

    // Action button constructor
    public ConfigOption(String id, String title, String description, ConfigCategory category,
                        String actionLabel, Consumer<Screen> actionHandler, String keywords) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.category = category;
        this.type = Type.ACTION;
        this.actionLabel = actionLabel;
        this.actionHandler = actionHandler;
        this.keywords = (title + " " + description + " " + keywords).toLowerCase();
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public ConfigCategory getCategory() { return category; }
    public Type getType() { return type; }

    public boolean matches(String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.trim().toLowerCase();
        return title.toLowerCase().contains(q) || description.toLowerCase().contains(q) || keywords.contains(q);
    }

    // Boolean getters/setters
    public boolean getBool() { return getterBool != null && getterBool.get(); }
    public void toggleBool() {
        if (setterBool != null) {
            setterBool.accept(!getBool());
        }
    }

    // Slider getters/setters
    public float getFloat() { return getterFloat != null ? getterFloat.get() : minVal; }
    public void setFloat(float val) {
        if (setterFloat != null) {
            setterFloat.accept(Math.max(minVal, Math.min(maxVal, val)));
        }
    }
    public float getMinVal() { return minVal; }
    public float getMaxVal() { return maxVal; }

    public double getSliderNormalizedValue() {
        if (maxVal <= minVal) return 0.0;
        return (getFloat() - minVal) / (maxVal - minVal);
    }

    public void setSliderNormalizedValue(double normalized) {
        float val = (float) (minVal + normalized * (maxVal - minVal));
        setFloat(val);
    }

    public String getFormattedValue() {
        if (formatter != null) return formatter.apply(getFloat());
        return String.format("%.1f", getFloat());
    }

    // Action
    public String getActionLabel() { return actionLabel != null ? actionLabel : "Öffnen..."; }
    public void triggerAction(Screen parent) {
        if (actionHandler != null) {
            actionHandler.accept(parent);
        }
    }

    // Animation & Hover states
    public float getHoverProgress() { return hoverProgress; }
    public void updateHoverProgress(boolean isHovered, float deltaSec) {
        this.hoverProgress = PloppAnimation.interpolate(hoverProgress, isHovered ? 1.0f : 0.0f, deltaSec, 12.0f);
    }

    public float getClickProgress() { return clickProgress; }
    public void triggerClickAnimation() { this.clickProgress = 1.0f; }
    public void updateClickProgress(float deltaSec) {
        if (clickProgress > 0.0f) {
            clickProgress = Math.max(0.0f, clickProgress - deltaSec * 8.0f);
        }
    }

    public boolean isDragging() { return isDragging; }
    public void setDragging(boolean dragging) { isDragging = dragging; }
}

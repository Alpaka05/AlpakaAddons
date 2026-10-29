package net.alpaka.addons.client.gui;

import net.alpaka.addons.config.AlpakaConfig;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class ConfigOption {
    public enum Type {
        BOOLEAN,
        SLIDER,
        ACTION,
        HEADER,
        DROPDOWN,
        TEXT,
        /** A sample of what the feature puts on screen, drawn in the settings; see {@link #preview}. */
        PREVIEW,
        /** The viewmodel presets, one card each, which the config screen draws itself; see {@link #presets}. */
        PRESETS,
        /** One of the mod's keys, bound right in the settings; see {@link #keybind}. */
        KEYBIND
    }

    /**
     * One tickable line inside a {@link Type#DROPDOWN}.
     *
     * Deliberately not a nested {@link ConfigOption}: an entry has no card, no description and no
     * animation state of its own, and letting the search index or the layout treat it as a normal
     * option would put it back in the flat list this exists to get it out of.
     */
    public static class ToggleEntry {
        private final String label;
        private final Supplier<Boolean> getter;
        private final Consumer<Boolean> setter;

        public ToggleEntry(String label, Supplier<Boolean> getter, Consumer<Boolean> setter) {
            this.label = label;
            this.getter = getter;
            this.setter = setter;
        }

        public String getLabel() { return label; }
        public boolean get() { return getter != null && getter.get(); }
        public void toggle() {
            if (setter != null) setter.accept(!get());
        }
    }

    private Supplier<String> textGetter;
    private Consumer<String> textSetter;
    private String placeholder = "";

    private final String id;
    private final String title;
    private final String description;
    private final ConfigCategory category;
    private final Type type;
    private String keywords;

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
    /** Set for color options only: the current color, which the button shows as its swatch. */
    private java.util.function.IntSupplier colorGetter;

    // Dropdown
    private List<ToggleEntry> entries;
    private boolean expanded = false;

    // Animation & Hover state tracking
    private float hoverProgress = 0.0f;
    /** 0..1 knob position of a BOOLEAN option, easing toward its state; -1 until first drawn. */
    private float toggleProgress = -1.0f;

    // Header constructor
    public ConfigOption(String title, ConfigCategory category) {
        this.id = title.toLowerCase(Locale.ROOT).replace(" ", "_");
        this.title = title;
        this.description = "";
        this.category = category;
        this.type = Type.HEADER;
        this.keywords = title.toLowerCase(Locale.ROOT);
    }

    // Preview constructor, see preview()
    private ConfigOption(String title, ConfigCategory category, Type type, String id) {
        this.id = id;
        this.title = title;
        this.description = "";
        this.category = category;
        this.type = type;
        this.keywords = title.toLowerCase(Locale.ROOT);
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
        this.keywords = (title + " " + description + " " + keywords).toLowerCase(Locale.ROOT);
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
        this.keywords = (title + " " + description + " " + keywords).toLowerCase(Locale.ROOT);
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
        this.keywords = (title + " " + description + " " + keywords).toLowerCase(Locale.ROOT);
    }

    /**
     * A color option: a button showing the current color as a swatch, which opens the color
     * picker. Every color setting is made this way, so the config screen needs no list of them.
     *
     * The option is given the config field rather than a getter, so its default can be read from a
     * fresh config: the picker's reset button goes back to that without a second copy of it here.
     */
    public static ConfigOption color(String id, String title, String description, ConfigCategory category,
                                     java.util.function.ToIntFunction<AlpakaConfig> field,
                                     java.util.function.IntConsumer setter, String keywords) {
        java.util.function.IntSupplier getter = () -> field.applyAsInt(AlpakaConfig.instance);
        int defaultColor = field.applyAsInt(defaults());
        ConfigOption option = new ConfigOption(id, title, description, category, "Choose Color",
                parent -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(
                        new net.alpaka.addons.client.ColorPickerScreen(parent, title, getter.getAsInt(),
                                defaultColor, setter::accept)),
                keywords);
        option.colorGetter = getter;
        return option;
    }

    /** A config as freshly created, for reading defaults from; made once, never saved. */
    private static AlpakaConfig defaults;

    private static AlpakaConfig defaults() {
        if (defaults == null) {
            defaults = new AlpakaConfig();
        }
        return defaults;
    }

    // Dropdown constructor
    public ConfigOption(String id, String title, String description, ConfigCategory category,
                        List<ToggleEntry> entries, String keywords) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.category = category;
        this.type = Type.DROPDOWN;
        this.entries = entries;
        // Entry labels join the search index, so searching for a line by name still finds the
        // dropdown that holds it rather than turning up nothing.
        StringBuilder entryText = new StringBuilder();
        if (entries != null) {
            for (ToggleEntry entry : entries) entryText.append(' ').append(entry.getLabel());
        }
        this.keywords = (title + " " + description + " " + keywords + entryText).toLowerCase(Locale.ROOT);
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public ConfigCategory getCategory() { return category; }
    public Type getType() { return type; }

    /**
     * A search box's contents in the form {@link #matchesNormalized} expects.
     *
     * Kept separate so a caller filtering the whole option list normalises once instead of once per
     * option - the config screen does this several times a frame while a search is active.
     */
    public static String normalizeQuery(String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }

    /** Whether this option matches an already-normalised query. Allocation-free. */
    public boolean matchesNormalized(String normalizedQuery) {
        if (normalizedQuery.isEmpty()) return true;
        // keywords already holds the title and description, lowercased, so one test covers all three.
        return keywords.contains(normalizedQuery);
    }

    public boolean matches(String query) {
        return matchesNormalized(normalizeQuery(query));
    }

    // Dropdown getters/setters
    public List<ToggleEntry> getEntries() { return entries; }
    public int getEntryCount() { return entries == null ? 0 : entries.size(); }
    public boolean isExpanded() { return expanded; }
    public void setExpanded(boolean expanded) { this.expanded = expanded; }
    public void toggleExpanded() { this.expanded = !this.expanded; }

    /** How many of this dropdown's lines are ticked, for the collapsed summary. */
    public int getEnabledEntryCount() {
        if (entries == null) return 0;
        int count = 0;
        for (ToggleEntry entry : entries) {
            if (entry.get()) count++;
        }
        return count;
    }

    // Boolean getters/setters
    public boolean getBool() { return getterBool != null && getterBool.get(); }
    public void toggleBool() {
        if (setterBool != null) {
            setterBool.accept(!getBool());
        }
    }

    // Slider getters/setters
    /**
     * The value, clamped to the slider's range. A hand-edited or old config can hold an index past
     * the end of a choice list, and the formatters index their name arrays with it every frame.
     */
    public float getFloat() {
        if (getterFloat == null) return minVal;
        float value = getterFloat.get();
        if (maxVal <= minVal || Float.isNaN(value)) return Float.isNaN(value) ? minVal : value;
        return Math.max(minVal, Math.min(maxVal, value));
    }
    public void setFloat(float val) {
        if (setterFloat != null) {
            setterFloat.accept(Math.max(minVal, Math.min(maxVal, val)));
        }
    }
    /**
     * Puts a slider's stored value back inside the range when it lies outside it. A value inside is
     * never written, so a setter's rounding cannot touch it. Returns whether the value changed.
     */
    public boolean clampStoredValue() {
        if (type != Type.SLIDER || getterFloat == null || setterFloat == null || maxVal <= minVal) return false;
        float stored = getterFloat.get();
        if (stored >= minVal && stored <= maxVal) return false;
        setFloat(getFloat());
        return true;
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
        return String.format(Locale.ROOT, "%.1f", getFloat());
    }

    // Action
    public String getActionLabel() { return actionLabel != null ? actionLabel : "Open…"; }

    private String[] choices;

    /**
     * Makes this slider a pick from a list: the value is an index into {@code names}, and the
     * settings show it as a select field that opens the list, not as a track to drag along.
     */
    public ConfigOption choices(String[] names) {
        this.choices = names;
        return this;
    }

    /** The names to pick from, or null for an ordinary slider. */
    public String[] getChoices() { return choices; }

    /** The index of the current choice, clamped to the list. */
    public int getChoiceIndex() {
        if (choices == null || choices.length == 0) return 0;
        return Math.max(0, Math.min(choices.length - 1, Math.round(getFloat())));
    }

    private Supplier<net.minecraft.network.chat.Component> previewText;

    /**
     * A live sample of what a feature puts on screen - a chat line, say - drawn in its settings and
     * rebuilt every frame, so ticking one of the feature's toggles shows its effect at once.
     */
    public static ConfigOption preview(String id, String title, ConfigCategory category,
                                       Supplier<net.minecraft.network.chat.Component> text) {
        ConfigOption option = new ConfigOption(title, category, Type.PREVIEW, id);
        option.previewText = text;
        return option;
    }

    /**
     * The list of saved viewmodel presets: a card per preset with its name, Load, Save and Delete,
     * and a button that adds one. A single option, because how many presets there are changes while
     * the menu is open and the option list is built once.
     */
    public static ConfigOption presets(String id, String title, ConfigCategory category, String keywords) {
        ConfigOption option = new ConfigOption(title, category, Type.PRESETS, id);
        option.keywords = (title + " " + keywords).toLowerCase(Locale.ROOT);
        return option;
    }

    private Supplier<net.minecraft.client.KeyMapping> keyMapping;

    /**
     * One of the mod's keys, shown as a field with the bound key: a click on it waits for the next
     * key or mouse button. Placed in the settings of the feature it belongs to, which is why the keys
     * are not in vanilla's Controls; see AlpakaKeyBinds.
     *
     * The mapping is given as a supplier because the features make theirs when they register, which
     * may be after this list is built.
     */
    public static ConfigOption keybind(String id, String title, String description, ConfigCategory category,
                                       Supplier<net.minecraft.client.KeyMapping> mapping, String keywords) {
        ConfigOption option = new ConfigOption(id, title, description, category, Type.KEYBIND,
                (title + " " + description + " key keybind hotkey bind button " + keywords).toLowerCase(Locale.ROOT));
        option.keyMapping = mapping;
        return option;
    }

    private ConfigOption(String id, String title, String description, ConfigCategory category, Type type, String keywords) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.category = category;
        this.type = type;
        this.keywords = keywords;
    }

    /** The key mapping a keybind option sets, or null while its feature has not registered it. */
    public net.minecraft.client.KeyMapping getKeyMapping() {
        return keyMapping == null ? null : keyMapping.get();
    }

    /** The sample a preview shows. */
    public net.minecraft.network.chat.Component getPreviewText() {
        return previewText == null ? net.minecraft.network.chat.Component.empty() : previewText.get();
    }

    /** Whether this is a color option, made by {@link #color}. */
    public boolean isColor() { return colorGetter != null; }

    /** The color a color option currently holds. */
    public int getColor() { return colorGetter.getAsInt(); }

    private boolean dangerous = false;

    /**
     * Makes this action ask first, in a dialog, for actions that overwrite or throw away something the
     * player set: resetting settings, saving over a preset. The action runs only once the dialog's
     * confirm button is clicked; Cancel and Escape go back to the menu without doing anything.
     *
     * @param dangerous whether the action destroys settings: its row and the dialog's confirm button
     *                  are then red, otherwise the confirm button carries the accent
     */
    public ConfigOption confirm(String title, String message, String confirmLabel, boolean dangerous) {
        Consumer<Screen> action = this.actionHandler;
        this.actionHandler = parent -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(
                new net.alpaka.addons.client.AlpakaConfirmScreen(parent, title, message, confirmLabel, dangerous,
                        () -> action.accept(parent)));
        this.dangerous = dangerous;
        return this;
    }

    /** Whether this action destroys settings, which its row shows in red. */
    public boolean isDangerous() { return dangerous; }

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

    /** Where the toggle knob is drawn, 0 (off) to 1 (on); the real state until first updated. */
    public float getToggleProgress() {
        return toggleProgress < 0.0f ? (getBool() ? 1.0f : 0.0f) : toggleProgress;
    }

    public void updateToggleProgress(float deltaSec) {
        float target = getBool() ? 1.0f : 0.0f;
        toggleProgress = toggleProgress < 0.0f ? target : PloppAnimation.interpolate(toggleProgress, target, deltaSec, 12.0f);
    }


    /**
     * A free-text option, edited inline in the config list.
     *
     * Exists because some settings are a name rather than a choice - the bridge bot's account, say -
     * and routing those through a button that opens its own screen makes typing one word a
     * three-click detour.
     */
    public ConfigOption(String id, String title, String description, ConfigCategory category,
                        Supplier<String> getter, Consumer<String> setter,
                        String placeholder, String keywords) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.category = category;
        this.type = Type.TEXT;
        this.textGetter = getter;
        this.textSetter = setter;
        this.placeholder = placeholder;
        this.keywords = (title + " " + description + " " + keywords).toLowerCase(Locale.ROOT);
    }

    public String getText() {
        return textGetter != null ? textGetter.get() : "";
    }

    public void setText(String value) {
        if (textSetter != null) textSetter.accept(value);
    }

    public String getPlaceholder() {
        return placeholder;
    }

    private boolean secret = false;
    private int maxLength = 32;

    /**
     * Marks a text option as a secret, such as an API key: shown masked but for its last four
     * characters, and long enough to paste one in.
     */
    public ConfigOption secret(int maxLength) {
        this.secret = true;
        this.maxLength = maxLength;
        return this;
    }

    public boolean isSecret() {
        return secret;
    }

    /** How many characters the text may have. */
    public int getMaxLength() {
        return maxLength;
    }

    /** The text as the field shows it: a secret masked but for its end, so the player can tell which key it is. */
    public String getShownText() {
        String text = getText();
        if (!secret || text.isEmpty()) return text;
        String end = text.length() > 8 ? text.substring(text.length() - 4) : "";
        return "•".repeat(Math.min(10, text.length() - end.length())) + end;
    }
}

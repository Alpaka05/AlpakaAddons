package net.alpaka.addons.compat.oneconfig;

import net.alpaka.addons.client.gui.ConfigOption;
import net.alpaka.addons.client.gui.ConfigTree;
import net.alpaka.addons.config.Chroma;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.MetaKeys;
import org.polyfrost.oneconfig.api.config.v1.MetaKeysKt;
import org.polyfrost.oneconfig.api.config.v1.Properties;
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.config.v1.ValueFormatter;
import org.polyfrost.oneconfig.api.config.v1.Visualizer;
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The mod's settings as a OneConfig tree, so they can be changed in OneConfig's own menu.
 *
 * Nothing is stored twice. Every node reads and writes through the option it stands for, the same
 * getter and setter the mod's own menu uses, so the values stay in the mod's config file and both
 * menus always show the same thing. OneConfig is told that the tree saves itself
 * ({@code custom_save}), which keeps it from writing a file of its own for it.
 *
 * The layout follows {@link ConfigTree}: a sidebar section becomes a OneConfig category and a
 * feature with settings a subcategory, its switch first. Features that are only a switch share the
 * subcategory {@link #LONE_FEATURES}, since a heading over a single switch of the same name says
 * everything twice.
 *
 * As much as OneConfig has a control for is set right there, a key and a color's gradient or
 * rainbow included. Only what it has none for is a button into the mod's own menu: the list of
 * presets, a masked text, and the actions that open a screen of the mod.
 */
final class OneConfigTree {

    /** The mod id: OneConfig matches a tree to its mod by it, and drops its Mod Menu card for one that has a tree. */
    static final String ID = "alpaka";

    /** The subcategory of the features that have no settings of their own. */
    static final String LONE_FEATURES = "General";

    /** What OneConfig is given to start a save with: the mod writes its own file. */
    interface Host {
        /** Runs an option's setter; the host decides on which thread and when the file is written. */
        void change(Runnable setter);

        /** Runs an option's action, which may open one of the mod's screens. */
        void act(ConfigOption option);

        /** Opens the mod's own menu on the feature that holds the option. */
        void openInOwnMenu(ConfigOption option);

        /** Writes the config file now. */
        void save();

        /** The key the option's key mapping is bound to; null while it is bound to none. */
        Bound key(ConfigOption option);

        /** The key the mapping starts out with; null for none. */
        Bound defaultKey(ConfigOption option);

        /** Binds the option's key mapping to the key; null unbinds it. */
        void bind(ConfigOption option, Bound key);

        /**
         * Runs the task now and then for as long as the game runs. What a row shows can depend on
         * a value that also changes in the mod's own menu, where OneConfig hears nothing of it.
         */
        void keepFresh(Runnable task);
    }

    /** A key or a mouse button, by the number the game knows it under. */
    record Bound(int code, boolean mouse) {
    }

    private final Host host;
    private final Set<String> usedIds = new HashSet<>();
    /** The feature whose options are being added. */
    private String featureTitle = "";
    /** The rows that are shown only in one color mode; see {@link Host#keepFresh}. */
    private final List<Property<?>> conditional = new ArrayList<>();

    private OneConfigTree(Host host) {
        this.host = host;
    }

    static Tree build(List<ConfigTree.Group> groups, Host host) {
        return new OneConfigTree(host).build(groups);
    }

    private Tree build(List<ConfigTree.Group> groups) {
        Tree root = Tree.tree(ID);
        root.setTitle("Alpaka Addons");
        root.addMetadata("custom_save", (Runnable) host::save);
        // The values also change in the mod's own menu, behind OneConfig's back.
        root.addMetadata("no_cache", true);
        root.addMetadata("category", Config.Category.HYPIXEL);
        root.addMetadata("icon", "assets/alpaka/icon.png");
        root.addMetadata("icon_path", "assets/alpaka/icon.png");
        // "Disable All Features" is an action, not a switch the mod card could show.
        root.addMetadata(MetaKeysKt.NO_MASTER_SWITCH_KEY, true);

        for (ConfigTree.Group group : groups) {
            for (ConfigTree.Section section : group.sections) {
                // Two passes, so the shared subcategory is listed above the features' own.
                for (ConfigTree.Feature feature : section.features) {
                    List<ConfigOption> options = optionsOf(feature);
                    featureTitle = feature.title;
                    if (options.size() == 1) add(root, options.get(0), section.title, LONE_FEATURES);
                }
                for (ConfigTree.Feature feature : section.features) {
                    List<ConfigOption> options = optionsOf(feature);
                    if (options.size() == 1) continue;
                    featureTitle = feature.title;
                    for (ConfigOption option : options) add(root, option, section.title, feature.title);
                }
            }
        }
        if (!conditional.isEmpty()) {
            host.keepFresh(() -> {
                for (Property<?> property : conditional) property.revaluateDisplay();
            });
        }
        return root;
    }

    /** A feature's switch and settings, in the order the mod's own menu lists them. */
    private static List<ConfigOption> optionsOf(ConfigTree.Feature feature) {
        List<ConfigOption> options = new ArrayList<>();
        if (feature.hasToggle()) options.add(feature.master);
        for (ConfigOption option : feature.panelOptions()) {
            // A preview is drawn by the mod's own menu; there is nothing to set on it.
            if (option.getType() != ConfigOption.Type.PREVIEW) options.add(option);
        }
        return options;
    }

    private void add(Tree root, ConfigOption option, String category, String subcategory) {
        switch (option.getType()) {
            case BOOLEAN -> put(root, category, subcategory, Visualizer.SwitchVisualizer.class,
                    value(option, Boolean.class, option::getBool, on -> {
                        if (option.getBool() != on) option.toggleBool();
                    }));
            case SLIDER -> addSlider(root, option, category, subcategory);
            case ACTION -> addAction(root, option, category, subcategory);
            case DROPDOWN -> addToggleList(root, option, category, subcategory);
            case TEXT -> addText(root, option, category, subcategory);
            case KEYBIND -> addKey(root, option, category, subcategory);
            // The presets are cards with buttons of their own, and their number changes: they stay
            // in the mod's menu, one click away.
            case PRESETS -> put(root, category, subcategory, Visualizer.ButtonVisualizer.class,
                    button(option, option.getId(), option.getTitle(), "Open", () -> host.openInOwnMenu(option)));
            case HEADER, PREVIEW -> { }
        }
    }

    private void addSlider(Tree root, ConfigOption option, String category, String subcategory) {
        String[] choices = option.getChoices();
        if (choices != null && choices.length > 0) {
            Property<Integer> property = value(option, Integer.class, option::getChoiceIndex,
                    index -> option.setFloat(index));
            MetaKeysKt.set(property, MetaKeys.OPTIONS, List.of(choices));
            put(root, category, subcategory, Visualizer.DropdownVisualizer.class, property);
            return;
        }
        Property<Float> property = value(option, Float.class, option::getFloat, option::setFloat);
        MetaKeysKt.set(property, MetaKeys.MIN, option.getMinVal());
        MetaKeysKt.set(property, MetaKeys.MAX, option.getMaxVal());
        property.addMetadata("formatter", (ValueFormatter) number -> option.format(number.floatValue()));
        put(root, category, subcategory, Visualizer.SliderVisualizer.class, property);
    }

    private void addAction(Tree root, ConfigOption option, String category, String subcategory) {
        if (!option.isColor()) {
            put(root, category, subcategory, Visualizer.ButtonVisualizer.class,
                    button(option, option.getId(), option.getTitle(), option.getActionLabel(), () -> host.act(option)));
            return;
        }
        Property<Color> property = value(option, Color.class,
                () -> new Color(option.getStoredColor(), true),
                color -> option.setColor(color.getRGB()));
        property.addMetadata("default", new Color(option.getDefaultColor(), true));
        put(root, category, subcategory, Visualizer.ColorVisualizer.class, property);
        if (option.hasColorEffects()) addColorModes(root, option, property, category, subcategory);
    }

    /**
     * What the mod's own picker has on top for a color that can be more than one color: the mode,
     * and under it what the mode needs, the gradient's second color or the rainbow's sliders. Only
     * the rows of the mode that is on are shown, so the list reads like the picker does.
     */
    private void addColorModes(Tree root, ConfigOption option, Property<Color> color, String category,
                               String subcategory) {
        List<ConfigOption.ColorMode> modes = option.getColorModes();
        String id = option.getId();
        String title = option.getTitle();

        Property<Integer> mode = value(id + "_mode", title + " Mode",
                "Solid is the one color. Gradient runs from it to a second color, Chroma is a moving rainbow.",
                Integer.class, () -> modes.indexOf(option.getColorMode()),
                index -> option.setColorMode(modes.get(Math.max(0, Math.min(modes.size() - 1, index)))));
        List<String> labels = new ArrayList<>();
        for (ConfigOption.ColorMode m : modes) labels.add(m.label);
        MetaKeysKt.set(mode, MetaKeys.OPTIONS, labels);
        put(root, category, subcategory, Visualizer.DropdownVisualizer.class, mode);

        // A rainbow has no color to pick: its picker in the mod's menu hides the color then as well.
        showIn(color, mode, option, ConfigOption.ColorMode.SOLID, ConfigOption.ColorMode.GRADIENT);

        if (modes.contains(ConfigOption.ColorMode.GRADIENT)) {
            Property<Color> end = value(id + "_gradient_end", title + " Gradient End",
                    "The color the gradient ends in; it starts in the color above.", Color.class,
                    () -> new Color(option.getStoredGradientEnd(), true),
                    c -> option.setGradientEnd(c.getRGB()));
            end.addMetadata("default", new Color(option.getDefaultGradientEnd(), true));
            put(root, category, subcategory, Visualizer.ColorVisualizer.class, end);
            showIn(end, mode, option, ConfigOption.ColorMode.GRADIENT);
        }

        if (modes.contains(ConfigOption.ColorMode.CHROMA)) {
            chromaSlider(root, option, mode, category, subcategory,
                    "_chroma_speed", " Chroma Speed", "How fast the rainbow moves.",
                    Chroma.MIN_SPEED, Chroma.MAX_SPEED, 0.05f,
                    Chroma::speed, (chroma, value) -> chroma.speed = value,
                    value -> String.format(Locale.ROOT, "%.2fx", value));
            chromaSlider(root, option, mode, category, subcategory,
                    "_chroma_saturation", " Chroma Saturation",
                    "How strong the rainbow's colors are. Low values fade toward white.",
                    0.0f, 100.0f, 1.0f,
                    chroma -> chroma.saturation01() * 100.0f, (chroma, value) -> chroma.saturation = value,
                    value -> Math.round(value) + "%");
            if (option.isChromaSpread()) {
                chromaSlider(root, option, mode, category, subcategory,
                        "_chroma_size", " Chroma Size",
                        "How many letters one full rainbow spans. Smaller packs more colors into a word.",
                        Chroma.MIN_SIZE, Chroma.MAX_SIZE, 1.0f,
                        Chroma::size, (chroma, value) -> chroma.size = (float) Math.round(value),
                        value -> Math.round(value) + " letters");
            }
        }
    }

    private void chromaSlider(Tree root, ConfigOption option, Property<Integer> mode, String category,
                              String subcategory, String idSuffix, String titleSuffix,
                              String description, float min, float max, float step,
                              Function<Chroma, Float> getter, BiConsumer<Chroma, Float> setter,
                              Function<Float, String> wording) {
        Property<Float> property = value(option.getId() + idSuffix, option.getTitle() + titleSuffix, description,
                Float.class, () -> getter.apply(option.getChroma()),
                value -> setter.accept(option.getChroma(), Math.max(min, Math.min(max, value))));
        MetaKeysKt.set(property, MetaKeys.MIN, min);
        MetaKeysKt.set(property, MetaKeys.MAX, max);
        MetaKeysKt.set(property, MetaKeys.STEP, step);
        property.addMetadata("formatter", (ValueFormatter) number -> wording.apply(number.floatValue()));
        put(root, category, subcategory, Visualizer.SliderVisualizer.class, property);
        showIn(property, mode, option, ConfigOption.ColorMode.CHROMA);
    }

    /** Shows the row only while the color is in one of the modes. */
    private void showIn(Property<?> row, Property<Integer> mode, ConfigOption option, ConfigOption.ColorMode... in) {
        Set<ConfigOption.ColorMode> shown = Set.of(in);
        row.addDisplayCondition(() -> shown.contains(option.getColorMode())
                ? Property.Display.SHOWN : Property.Display.HIDDEN);
        // OneConfig asks again when the mode is set in its menu; a change in the mod's own is caught by keepFresh.
        mode.addDependent(row);
        conditional.add(row);
    }

    /**
     * A key, set in OneConfig's own key field. The key stays one of the mod's key mappings, which
     * is what acts on a press: OneConfig's bind is only how the field reads and writes it, so it is
     * kept out of OneConfig's own key handling and out of vanilla's Controls.
     */
    private void addKey(Tree root, ConfigOption option, String category, String subcategory) {
        // Most keys are just "Key" under their feature. OneConfig also lists every key on its
        // Keybinds page and names it when two clash, where the feature has to be in the name.
        String title = "Key".equals(option.getTitle()) ? featureTitle + " Key" : option.getTitle();
        Property<OneConfigKeybind> property = value(option.getId(), title, option.getDescription(),
                OneConfigKeybind.class, () -> keybind(host.key(option)), bind -> host.bind(option, bound(bind)));
        property.addMetadata("oc_no_mc_mirror", true);
        // A key mapping is one key or mouse button, without Ctrl or Shift held to it.
        property.addMetadata("singleKey", true);
        property.addMetadata("default", keybind(host.defaultKey(option)));
        put(root, category, subcategory, Visualizer.KeybindVisualizer.class, property);
    }

    private static OneConfigKeybind keybind(Bound key) {
        int[] keys = key != null && !key.mouse() ? new int[]{key.code()} : null;
        int[] buttons = key != null && key.mouse() ? new int[]{key.code()} : null;
        // Never registered with OneConfig, so the action is never called.
        return new OneConfigKeybind(keys, buttons, (byte) 0, 0L, down -> false);
    }

    private static Bound bound(OneConfigKeybind bind) {
        int[] keys = bind.getKeyCodes();
        if (keys != null && keys.length > 0) return new Bound(keys[0], false);
        int[] buttons = bind.getMouseBtns();
        if (buttons != null && buttons.length > 0) return new Bound(buttons[0], true);
        return null;
    }

    /** A list of switches under one title, which OneConfig shows as a dropdown with a tick per line. */
    private void addToggleList(Tree root, ConfigOption option, String category, String subcategory) {
        List<ConfigOption.ToggleEntry> entries = option.getEntries();
        if (entries == null || entries.isEmpty()) return;
        String[] labels = new String[entries.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = entries.get(i).getLabel();
        Property<boolean[]> property = value(option, boolean[].class, () -> {
            boolean[] ticked = new boolean[entries.size()];
            for (int i = 0; i < ticked.length; i++) ticked[i] = entries.get(i).get();
            return ticked;
        }, ticked -> {
            for (int i = 0; i < entries.size() && i < ticked.length; i++) {
                if (entries.get(i).get() != ticked[i]) entries.get(i).toggle();
            }
        });
        property.addMetadata("options", labels);
        put(root, category, subcategory, Visualizer.MultiSelectDropdownVisualizer.class, property);
    }

    private void addText(Tree root, ConfigOption option, String category, String subcategory) {
        if (option.isSecret()) {
            // OneConfig would show an API key in the clear; the mod's own field masks it.
            put(root, category, subcategory, Visualizer.ButtonVisualizer.class,
                    button(option, option.getId(), option.getTitle(), "Edit", () -> host.openInOwnMenu(option)));
            return;
        }
        Property<String> property = value(option, String.class, option::getText, text -> {
            int max = option.getMaxLength();
            option.setText(text.length() > max ? text.substring(0, max) : text);
        });
        if (!option.getPlaceholder().isEmpty()) property.addMetadata("placeholder", option.getPlaceholder());
        put(root, category, subcategory, Visualizer.TextVisualizer.class, property);
    }

    /** A node that holds no value of its own: it reads the option and, through the host, writes it. */
    private <T> Property<T> value(ConfigOption option, Class<T> type, Supplier<T> getter, Consumer<T> setter) {
        return value(option.getId(), option.getTitle(), option.getDescription(), type, getter, setter);
    }

    private <T> Property<T> value(String id, String title, String description, Class<T> type,
                                  Supplier<T> getter, Consumer<T> setter) {
        return Properties.functional(getter, value -> {
            if (value != null) host.change(() -> setter.accept(value));
        }, uniqueId(id), title, description, type);
    }

    private Property<Void> button(ConfigOption option, String id, String title, String text, Runnable action) {
        Property<Void> property = Properties.dummy(uniqueId(id), title, option.getDescription());
        property.addMetadata("text", text);
        property.addMetadata("runnable", action);
        return property;
    }

    private static void put(Tree root, String category, String subcategory,
                            Class<? extends Visualizer> visualizer, Property<?> property) {
        MetaKeysKt.set(property, MetaKeys.VISUALIZER, visualizer);
        MetaKeysKt.set(property, MetaKeys.CATEGORY, category);
        MetaKeysKt.set(property, MetaKeys.SUBCATEGORY, subcategory);
        root.put(property);
    }

    /** Two nodes with one id would overwrite each other in the tree, so a repeated id gets a number. */
    private String uniqueId(String id) {
        String unique = id;
        for (int n = 2; !usedIds.add(unique); n++) unique = id + "_" + n;
        return unique;
    }
}

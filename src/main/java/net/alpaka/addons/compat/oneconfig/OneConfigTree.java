package net.alpaka.addons.compat.oneconfig;

import net.alpaka.addons.client.gui.ConfigOption;
import net.alpaka.addons.client.gui.ConfigTree;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.MetaKeys;
import org.polyfrost.oneconfig.api.config.v1.MetaKeysKt;
import org.polyfrost.oneconfig.api.config.v1.Properties;
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.config.v1.ValueFormatter;
import org.polyfrost.oneconfig.api.config.v1.Visualizer;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
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
    }

    private final Host host;
    private final Set<String> usedIds = new HashSet<>();

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
                    if (options.size() == 1) add(root, options.get(0), section.title, LONE_FEATURES);
                }
                for (ConfigTree.Feature feature : section.features) {
                    List<ConfigOption> options = optionsOf(feature);
                    if (options.size() == 1) continue;
                    for (ConfigOption option : options) add(root, option, section.title, feature.title);
                }
            }
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
            // A key is bound by pressing it, and the presets are cards with buttons of their own:
            // both stay in the mod's menu, one click away.
            case KEYBIND -> put(root, category, subcategory, Visualizer.ButtonVisualizer.class,
                    button(option, option.getId(), option.getTitle(), "Set Key", () -> host.openInOwnMenu(option)));
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
        // The rainbow and the gradient are the mod's own; its picker sets them.
        if (option.hasColorEffects()) {
            put(root, category, subcategory, Visualizer.ButtonVisualizer.class,
                    button(option, option.getId() + "_effects", option.getTitle() + " Effects",
                            "Chroma & Gradient", () -> host.act(option)));
        }
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
        return Properties.functional(getter, value -> {
            if (value != null) host.change(() -> setter.accept(value));
        }, uniqueId(option.getId()), option.getTitle(), option.getDescription(), type);
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

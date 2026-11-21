package net.alpaka.addons.compat.oneconfig;

import net.alpaka.addons.client.gui.ConfigCategory;
import net.alpaka.addons.client.gui.ConfigOption;
import net.alpaka.addons.client.gui.ConfigTree;
import net.alpaka.addons.config.AlpakaConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.polyfrost.oneconfig.api.config.v1.MetaKeys;
import org.polyfrost.oneconfig.api.config.v1.MetaKeysKt;
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.config.v1.ValueFormatter;
import org.polyfrost.oneconfig.api.config.v1.Visualizer;
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The settings as OneConfig gets them: each node has to read and write the option it stands for,
 * and nothing may be stored on OneConfig's side.
 */
class OneConfigTreeTest {
    private static final ConfigCategory CATEGORY = ConfigCategory.GENERAL;

    private boolean chatOn = true;
    private boolean fullbright = false;
    private float scale = 1.0f;
    private float mode = 2.0f;
    private String name = "Bot";
    private String apiKey = "secret-key";
    private final boolean[] lines = {true, false, true};

    private final List<String> acted = new ArrayList<>();
    private final List<String> openedInOwnMenu = new ArrayList<>();
    private final Map<String, OneConfigTree.Bound> keys = new HashMap<>();
    private Runnable keptFresh;
    private int changes = 0;
    private int saves = 0;

    private final OneConfigTree.Host host = new OneConfigTree.Host() {
        @Override public void change(Runnable setter) { changes++; setter.run(); }
        @Override public void act(ConfigOption option) { acted.add(option.getId()); }
        @Override public void openInOwnMenu(ConfigOption option) { openedInOwnMenu.add(option.getId()); }
        @Override public void save() { saves++; }
        @Override public OneConfigTree.Bound key(ConfigOption option) { return keys.get(option.getId()); }
        @Override public OneConfigTree.Bound defaultKey(ConfigOption option) { return new OneConfigTree.Bound(75, false); }
        @Override public void bind(ConfigOption option, OneConfigTree.Bound key) { keys.put(option.getId(), key); }
        @Override public void keepFresh(Runnable task) { keptFresh = task; }
    };

    private Tree tree;

    @BeforeEach
    void build() {
        AlpakaConfig.instance = new AlpakaConfig();
        AlpakaConfig config = AlpakaConfig.instance;
        config.nameTagGradient = false;
        config.nameTagChroma.enabled = false;
        // The name tag's fields stand in for any color that can also be a gradient or a rainbow.
        ConfigOption tagColor = ConfigOption.color("tag_color", "Tag Color", "", CATEGORY,
                        c -> c.nameTagGradientStart, value -> AlpakaConfig.instance.nameTagGradientStart = value, "")
                .withGradient(c -> c.nameTagGradientEnd, value -> AlpakaConfig.instance.nameTagGradientEnd = value,
                        c -> c.nameTagGradient, on -> AlpakaConfig.instance.nameTagGradient = on)
                .withChroma(c -> c.nameTagChroma, true);
        ConfigOption plainColor = ConfigOption.color("plain_color", "Plain Color", "", CATEGORY,
                c -> c.nameTagGradientStart, value -> AlpakaConfig.instance.nameTagGradientStart = value, "");
        ConfigOption chat = new ConfigOption("better_chat", "Better Chat", "A nicer chat.", CATEGORY,
                () -> chatOn, on -> chatOn = on, "");
        ConfigOption chatScale = new ConfigOption("chat_scale", "Scale", "How big the chat is.", CATEGORY,
                () -> scale, value -> scale = value, 0.5f, 2.0f, value -> Math.round(value * 100) + "%", "");
        ConfigOption chatMode = new ConfigOption("chat_mode", "Mode", "", CATEGORY,
                () -> mode, value -> mode = value, 0.0f, 2.0f, null, "").choices(new String[]{"Off", "Some", "All"});
        ConfigOption chatLines = new ConfigOption("chat_lines", "Lines", "", CATEGORY, List.of(
                new ConfigOption.ToggleEntry("Party", () -> lines[0], on -> lines[0] = on),
                new ConfigOption.ToggleEntry("Guild", () -> lines[1], on -> lines[1] = on),
                new ConfigOption.ToggleEntry("Private", () -> lines[2], on -> lines[2] = on)), "");
        ConfigOption chatName = new ConfigOption("chat_name", "Name", "", CATEGORY,
                () -> name, value -> name = value, "Account", "");
        ConfigOption chatKey = new ConfigOption("chat_api_key", "API Key", "", CATEGORY,
                () -> apiKey, value -> apiKey = value, "", "").secret(64);
        ConfigOption chatReset = new ConfigOption("chat_reset", "Reset Chat", "", CATEGORY,
                "Reset", parent -> { }, "");
        ConfigOption chatBind = ConfigOption.keybind("chat_key", "Key", "", CATEGORY, () -> null, "");
        ConfigOption chatPreview = ConfigOption.preview("chat_preview", "Preview", CATEGORY, () -> null);
        ConfigOption bright = new ConfigOption("fullbright", "Fullbright", "No darkness.", CATEGORY,
                () -> fullbright, on -> fullbright = on, "");
        // Registered under an id another option already has.
        ConfigOption twin = new ConfigOption("fullbright", "Fullbright Twin", "", CATEGORY,
                () -> fullbright, on -> fullbright = on, "");

        ConfigTree.Feature chatFeature = new ConfigTree.Feature("Better Chat", "A nicer chat.", chat);
        chatFeature.settings.addAll(List.of(chatBind, chatPreview, chatScale, chatMode, chatLines, chatName,
                chatKey, chatReset, tagColor, plainColor));
        ConfigTree.Section section = new ConfigTree.Section("Chat");
        section.features.add(chatFeature);
        section.features.add(new ConfigTree.Feature("Fullbright", "No darkness.", bright));
        section.features.add(new ConfigTree.Feature("Fullbright Twin", "", twin));
        ConfigTree.Group group = new ConfigTree.Group("Visuals");
        group.sections.add(section);

        tree = OneConfigTree.build(List.of(group), host);
    }

    @Test
    void theTreeSavesThroughTheModAndIsFoundByItsModId() {
        assertEquals("alpaka", tree.getID());
        Runnable save = tree.getMetadata("custom_save");
        assertNotNull(save, "without custom_save OneConfig writes a file of its own for the tree");
        save.run();
        assertEquals(1, saves);
    }

    @Test
    void aFeatureWithSettingsIsASubcategoryAndLoneSwitchesShareOne() {
        assertPlaced("better_chat", "Chat", "Better Chat");
        assertPlaced("chat_scale", "Chat", "Better Chat");
        assertPlaced("fullbright", "Chat", OneConfigTree.LONE_FEATURES);
    }

    @Test
    void aSwitchReadsAndWritesItsOption() {
        Property<Boolean> property = property("better_chat");
        assertEquals(Visualizer.SwitchVisualizer.class, MetaKeysKt.get(property, MetaKeys.VISUALIZER));
        assertTrue(property.get());
        property.set(false);
        assertFalse(chatOn);
        assertEquals(1, changes, "a change has to go through the host, which decides when the file is written");
        chatOn = true;
        assertTrue(property.get(), "a change made in the mod's own menu has to show in OneConfig");
    }

    @Test
    void aSliderKeepsItsRangeAndItsWording() {
        Property<Float> property = property("chat_scale");
        assertEquals(Visualizer.SliderVisualizer.class, MetaKeysKt.get(property, MetaKeys.VISUALIZER));
        assertEquals(0.5f, MetaKeysKt.get(property, MetaKeys.MIN).floatValue());
        assertEquals(2.0f, MetaKeysKt.get(property, MetaKeys.MAX).floatValue());
        ValueFormatter formatter = property.getMetadata("formatter");
        assertEquals("150%", formatter.format(1.5f));
        property.set(5.0f);
        assertEquals(2.0f, scale, "the option clamps what OneConfig hands it");
    }

    @Test
    void aChoiceIsADropdownOfItsNames() {
        Property<Integer> property = property("chat_mode");
        assertEquals(Visualizer.DropdownVisualizer.class, MetaKeysKt.get(property, MetaKeys.VISUALIZER));
        assertEquals(List.of("Off", "Some", "All"), MetaKeysKt.get(property, MetaKeys.OPTIONS));
        assertEquals(2, property.get());
        property.set(1);
        assertEquals(1.0f, mode);
    }

    @Test
    void aListOfSwitchesIsOneMultiSelect() {
        Property<boolean[]> property = property("chat_lines");
        assertEquals(Visualizer.MultiSelectDropdownVisualizer.class, MetaKeysKt.get(property, MetaKeys.VISUALIZER));
        assertArrayEquals(new String[]{"Party", "Guild", "Private"}, (String[]) property.getMetadata("options"));
        assertArrayEquals(new boolean[]{true, false, true}, property.get());
        property.set(new boolean[]{true, true, false});
        assertArrayEquals(new boolean[]{true, true, false}, lines);
    }

    @Test
    void aTextIsEditedInPlaceButASecretStaysInTheModsMenu() {
        Property<String> property = property("chat_name");
        assertEquals(Visualizer.TextVisualizer.class, MetaKeysKt.get(property, MetaKeys.VISUALIZER));
        assertEquals("Account", property.getMetadata("placeholder"));
        property.set("x".repeat(40));
        assertEquals(32, name.length(), "the option's length limit holds in OneConfig too");

        Property<Void> secret = property("chat_api_key");
        assertEquals(Visualizer.ButtonVisualizer.class, MetaKeysKt.get(secret, MetaKeys.VISUALIZER));
        assertNull(secret.get(), "the key itself must not reach OneConfig");
        ((Runnable) secret.getMetadata("runnable")).run();
        assertEquals(List.of("chat_api_key"), openedInOwnMenu);
    }

    @Test
    void anActionIsAButton() {
        Property<Void> reset = property("chat_reset");
        assertEquals(Visualizer.ButtonVisualizer.class, MetaKeysKt.get(reset, MetaKeys.VISUALIZER));
        assertEquals("Reset", reset.getMetadata("text"));
        ((Runnable) reset.getMetadata("runnable")).run();
        assertEquals(List.of("chat_reset"), acted);
    }

    @Test
    void aKeyIsSetInOneConfigsKeyFieldAndStaysTheModsOwn() {
        Property<OneConfigKeybind> key = property("chat_key");
        assertEquals(Visualizer.KeybindVisualizer.class, MetaKeysKt.get(key, MetaKeys.VISUALIZER));
        assertEquals("Better Chat Key", key.getTitle(), "on OneConfig's Keybinds page a bare \"Key\" says nothing");
        assertEquals(true, key.getMetadata("oc_no_mc_mirror"),
                "OneConfig must not act on the key itself, nor list it in vanilla's Controls");
        assertEquals(true, key.getMetadata("singleKey"));
        assertArrayEquals(new int[]{75}, ((OneConfigKeybind) key.getMetadata("default")).getKeyCodes());
        assertFalse(key.get().isBound());

        key.set(new OneConfigKeybind(new int[]{82}, null, (byte) 0, 0L, down -> true));
        assertEquals(new OneConfigTree.Bound(82, false), keys.get("chat_key"));
        assertArrayEquals(new int[]{82}, key.get().getKeyCodes());

        key.set(new OneConfigKeybind(null, new int[]{4}, (byte) 0, 0L, down -> true));
        assertEquals(new OneConfigTree.Bound(4, true), keys.get("chat_key"));
        assertArrayEquals(new int[]{4}, key.get().getMouseBtns());
        assertNull(key.get().getKeyCodes());

        key.set(new OneConfigKeybind(null, null, (byte) 0, 0L, down -> true));
        assertNull(keys.get("chat_key"), "an emptied field unbinds the key");
        assertTrue(openedInOwnMenu.isEmpty());
    }

    @Test
    void aColorsModeIsADropdownThatShowsOnlyWhatTheModeNeeds() {
        AlpakaConfig config = AlpakaConfig.instance;
        Property<Integer> mode = property("tag_color_mode");
        assertEquals(Visualizer.DropdownVisualizer.class, MetaKeysKt.get(mode, MetaKeys.VISUALIZER));
        assertEquals(List.of("Solid", "Gradient", "Chroma"), MetaKeysKt.get(mode, MetaKeys.OPTIONS));
        assertEquals(0, mode.get());
        assertShown(true, "tag_color");
        assertShown(false, "tag_color_gradient_end", "tag_color_chroma_speed", "tag_color_chroma_saturation",
                "tag_color_chroma_size");

        mode.set(1);
        assertTrue(config.nameTagGradient);
        assertFalse(config.nameTagChroma.enabled);
        assertShown(true, "tag_color", "tag_color_gradient_end");
        assertShown(false, "tag_color_chroma_speed");
        Property<Color> end = property("tag_color_gradient_end");
        end.set(new Color(0x80112233, true));
        assertEquals(0x80112233, config.nameTagGradientEnd);

        mode.set(2);
        assertTrue(config.nameTagChroma.enabled);
        assertTrue(config.nameTagGradient, "the gradient is kept for when the rainbow is switched off again");
        assertShown(true, "tag_color_chroma_speed", "tag_color_chroma_saturation", "tag_color_chroma_size");
        assertShown(false, "tag_color", "tag_color_gradient_end");

        // Switched back in the mod's own menu, where OneConfig hears nothing of it.
        config.nameTagChroma.enabled = false;
        assertNotNull(keptFresh);
        keptFresh.run();
        assertEquals(1, mode.get());
        assertShown(true, "tag_color", "tag_color_gradient_end");
        assertShown(false, "tag_color_chroma_speed");

        assertNull(tree.get("tag_color_effects"), "no button into the mod's picker is left");
        assertNull(tree.get("plain_color_mode"), "a color that is only ever one color has no mode");
    }

    @Test
    void aRainbowsSlidersKeepThePickersRangeAndWording() {
        AlpakaConfig config = AlpakaConfig.instance;
        Property<Float> speed = property("tag_color_chroma_speed");
        assertEquals(0.1f, MetaKeysKt.get(speed, MetaKeys.MIN).floatValue());
        assertEquals(3.0f, MetaKeysKt.get(speed, MetaKeys.MAX).floatValue());
        assertEquals("1.25x", ((ValueFormatter) speed.getMetadata("formatter")).format(1.25f));
        speed.set(9.0f);
        assertEquals(3.0f, config.nameTagChroma.speed);

        Property<Float> saturation = property("tag_color_chroma_saturation");
        assertEquals("40%", ((ValueFormatter) saturation.getMetadata("formatter")).format(40.0f));
        saturation.set(40.0f);
        assertEquals(40.0f, config.nameTagChroma.saturation);

        Property<Float> size = property("tag_color_chroma_size");
        assertEquals("20 letters", ((ValueFormatter) size.getMetadata("formatter")).format(20.4f));
        size.set(20.4f);
        assertEquals(20.0f, config.nameTagChroma.size);
    }

    @Test
    void aPreviewIsLeftOutAndARepeatedIdKeepsBothOptions() {
        assertNull(tree.get("chat_preview"));
        assertNotNull(tree.get("fullbright"));
        assertEquals("Fullbright Twin", tree.get("fullbright_2").getTitle());
    }

    @SuppressWarnings("unchecked")
    private <T> Property<T> property(String id) {
        Property<T> property = (Property<T>) tree.getProp(id);
        assertNotNull(property, id);
        return property;
    }

    private void assertShown(boolean shown, String... ids) {
        for (String id : ids) assertEquals(shown, tree.getProp(id).canDisplay(), id);
    }

    private void assertPlaced(String id, String category, String subcategory) {
        assertEquals(category, MetaKeysKt.get(tree.getProp(id), MetaKeys.CATEGORY), id);
        assertEquals(subcategory, MetaKeysKt.get(tree.getProp(id), MetaKeys.SUBCATEGORY), id);
    }
}

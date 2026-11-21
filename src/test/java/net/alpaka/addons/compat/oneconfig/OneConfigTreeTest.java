package net.alpaka.addons.compat.oneconfig;

import net.alpaka.addons.client.gui.ConfigCategory;
import net.alpaka.addons.client.gui.ConfigOption;
import net.alpaka.addons.client.gui.ConfigTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.polyfrost.oneconfig.api.config.v1.MetaKeys;
import org.polyfrost.oneconfig.api.config.v1.MetaKeysKt;
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.config.v1.ValueFormatter;
import org.polyfrost.oneconfig.api.config.v1.Visualizer;

import java.util.ArrayList;
import java.util.List;

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
    private int changes = 0;
    private int saves = 0;

    private final OneConfigTree.Host host = new OneConfigTree.Host() {
        @Override public void change(Runnable setter) { changes++; setter.run(); }
        @Override public void act(ConfigOption option) { acted.add(option.getId()); }
        @Override public void openInOwnMenu(ConfigOption option) { openedInOwnMenu.add(option.getId()); }
        @Override public void save() { saves++; }
    };

    private Tree tree;

    @BeforeEach
    void build() {
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
                chatKey, chatReset));
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
    void anActionIsAButtonAndAKeyOpensTheModsMenu() {
        Property<Void> reset = property("chat_reset");
        assertEquals(Visualizer.ButtonVisualizer.class, MetaKeysKt.get(reset, MetaKeys.VISUALIZER));
        assertEquals("Reset", reset.getMetadata("text"));
        ((Runnable) reset.getMetadata("runnable")).run();
        assertEquals(List.of("chat_reset"), acted);

        Property<Void> key = property("chat_key");
        ((Runnable) key.getMetadata("runnable")).run();
        assertEquals(List.of("chat_key"), openedInOwnMenu);
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

    private void assertPlaced(String id, String category, String subcategory) {
        assertEquals(category, MetaKeysKt.get(tree.getProp(id), MetaKeys.CATEGORY), id);
        assertEquals(subcategory, MetaKeysKt.get(tree.getProp(id), MetaKeys.SUBCATEGORY), id);
    }
}

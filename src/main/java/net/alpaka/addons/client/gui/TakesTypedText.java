package net.alpaka.addons.client.gui;

/**
 * A screen of the mod's that handles typed characters itself ({@code charTyped}), in a text field
 * of its own or as a hotkey. {@link TypedTextInput} keeps text input switched on while one is
 * open, without which 26.3 sends it no characters. Every screen of the mod with a
 * {@code charTyped} has to be marked; TypedTextInputTest fails the build for one that is not.
 */
public interface TakesTypedText {
}

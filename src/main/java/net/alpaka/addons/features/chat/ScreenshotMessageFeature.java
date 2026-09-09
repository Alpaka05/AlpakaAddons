package net.alpaka.addons.features.chat;

import com.mojang.logging.LogUtils;
import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * What happens to the "Saved screenshot as ..." notice: the line with Open, Copy and Delete buttons,
 * and the automatic copy to the clipboard.
 *
 * ### How the buttons work
 *
 * Open is vanilla's own file click event, handled by the game. Copy and Delete are custom click
 * events in the mod's namespace; the chat screen hook hands those to {@link #handleClick} before
 * vanilla sees them, because vanilla's answer to a custom click event is to send it to the server.
 *
 * ### Why the payload is a file name and not a path
 *
 * A server can put any component it likes into chat, including a click event with this mod's
 * identifier. So the payload names a file, never a path, and is resolved inside the screenshots
 * folder only - nothing outside it can be read or deleted by a crafted message.
 *
 * ### Copying
 *
 * Minecraft starts with AWT in headless mode, which has no clipboard. The flag is only read when
 * the toolkit is first created, and nothing in the game creates one, so it is switched off right
 * before the first use; on a client where something else already brought AWT up headless the copy
 * fails and says so. macOS is left out entirely, since AWT and GLFW do not share its main thread.
 *
 * ### Auto copy
 *
 * With the auto copy toggle on, the copy starts the moment the notice arrives. When the button line
 * is on as well, the notice is held back until the copy is done and then posted with "copied" - or
 * the failure - at its end, so one line says everything and nothing is announced twice. With the
 * vanilla notice kept, the copy is silent unless it fails.
 */
public final class ScreenshotMessageFeature {

    private ScreenshotMessageFeature() {
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NAMESPACE = "alpaka";
    public static final Identifier COPY = Identifier.fromNamespaceAndPath(NAMESPACE, "screenshot/copy");
    public static final Identifier DELETE = Identifier.fromNamespaceAndPath(NAMESPACE, "screenshot/delete");

    /**
     * Takes a message that is about to enter the chat and, if it is vanilla's screenshot notice,
     * does what the toggles ask for. Returns true when the vanilla line must not be shown, because
     * a replacement is posted through {@code repost} - now, or once the automatic copy has finished.
     */
    public static boolean handleNotice(Component message, Consumer<Component> repost) {
        if (!(message.getContents() instanceof TranslatableContents contents)) return false;
        if (!"screenshot.success".equals(contents.getKey())) return false;
        File file = fileOf(contents);
        if (file == null) return false;

        boolean buttons = AlpakaConfig.instance.betterScreenshotMessageEnabled;
        if (AlpakaConfig.instance.autoCopyScreenshots) {
            copy(file, error -> {
                if (buttons) {
                    Component note = error == null
                            ? Component.literal("copied").withStyle(ChatFormatting.DARK_GRAY)
                            : Component.literal("copy failed").withStyle(ChatFormatting.RED);
                    repost.accept(buttonLine(file, note));
                } else if (error != null) {
                    feedback("§cCouldn't copy the screenshot (" + error + ").");
                }
            });
            return buttons;
        }
        if (buttons) {
            repost.accept(buttonLine(file, null));
            return true;
        }
        return false;
    }

    /** "Saved screenshot [Open] [Copy] [Delete]", with an optional note between the text and the buttons. */
    private static Component buttonLine(File file, Component note) {
        String name = file.getName();
        MutableComponent line = Component.literal("Saved screenshot").withStyle(style -> style
                .withColor(ChatFormatting.GRAY)
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(name).withStyle(ChatFormatting.WHITE))));
        if (note != null) {
            line.append(" ").append(note);
        }
        line.append(" ").append(button("[Open]", ChatFormatting.GREEN, new ClickEvent.OpenFile(file), "Open " + name));
        line.append(" ").append(button("[Copy]", ChatFormatting.AQUA, custom(COPY, name), "Copy the image to the clipboard"));
        line.append(" ").append(button("[Delete]", ChatFormatting.RED, custom(DELETE, name), "Delete " + name));
        return line;
    }

    /** The file the vanilla notice links to: its one argument is the name, clickable to open the file. */
    private static File fileOf(TranslatableContents contents) {
        for (Object arg : contents.getArgs()) {
            if (arg instanceof Component component
                    && component.getStyle().getClickEvent() instanceof ClickEvent.OpenFile openFile) {
                return openFile.file();
            }
        }
        return null;
    }

    private static ClickEvent custom(Identifier id, String fileName) {
        return new ClickEvent.Custom(id, Optional.of(StringTag.valueOf(fileName)));
    }

    private static Component button(String text, ChatFormatting color, ClickEvent click, String hover) {
        return Component.literal(text).withStyle(style -> style
                .withColor(color)
                .withClickEvent(click)
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    /**
     * A custom click event from the chat. True when it was this mod's, whether or not anything
     * could be done with it - an event in this namespace must never reach the server.
     */
    public static boolean handleClick(ClickEvent.Custom event) {
        if (!NAMESPACE.equals(event.id().getNamespace())) return false;
        File file = resolve(event.payload().flatMap(Tag::asString).orElse(null));
        if (file == null) {
            feedback("§cThat screenshot link is not valid.");
        } else if (COPY.equals(event.id())) {
            copy(file, error -> feedback(error == null
                    ? "§aScreenshot copied to the clipboard."
                    : "§cCouldn't copy the screenshot (" + error + ")."));
        } else if (DELETE.equals(event.id())) {
            delete(file);
        }
        return true;
    }

    /** The named file inside the screenshots folder, or null for anything that is not just a .png name. */
    private static File resolve(String name) {
        if (name == null || name.isEmpty()) return null;
        if (name.contains("/") || name.contains("\\") || name.contains("..")) return null;
        if (!name.toLowerCase(Locale.ROOT).endsWith(".png")) return null;
        File folder = new File(Minecraft.getInstance().gameDirectory, Screenshot.SCREENSHOT_DIR);
        return new File(folder, name);
    }

    /**
     * Puts the image on the system clipboard, off the render thread, and reports back with null or a
     * short reason for the failure. The callback may run on any thread.
     */
    private static void copy(File file, Consumer<String> done) {
        if (Util.getPlatform() == Util.OS.OSX) {
            done.accept("not supported on macOS");
            return;
        }
        // Off the render thread: decoding a full-size screenshot takes a noticeable moment.
        Util.ioPool().execute(() -> {
            try {
                if (!file.isFile()) {
                    done.accept("file not found");
                    return;
                }
                System.setProperty("java.awt.headless", "false");
                BufferedImage decoded = ImageIO.read(file);
                if (decoded == null) throw new IOException("not a readable image");
                // Opaque RGB: the clipboard on Windows carries a bitmap, which has no alpha to speak of.
                BufferedImage image = new BufferedImage(decoded.getWidth(), decoded.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image.createGraphics();
                g.drawImage(decoded, 0, 0, null);
                g.dispose();
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageTransferable(image), null);
                done.accept(null);
            } catch (Throwable t) {
                LOGGER.warn("Couldn't copy screenshot {} to the clipboard", file, t);
                done.accept(t.getClass().getSimpleName());
            }
        });
    }

    private static void delete(File file) {
        try {
            if (Files.deleteIfExists(file.toPath())) {
                feedback("§7Deleted §f" + file.getName() + "§7.");
            } else {
                feedback("§cThat screenshot no longer exists.");
            }
        } catch (IOException e) {
            LOGGER.warn("Couldn't delete screenshot {}", file, e);
            feedback("§cCouldn't delete the screenshot (" + e.getClass().getSimpleName() + ").");
        }
    }

    /** A line in chat with the mod's prefix, from any thread. */
    private static void feedback(String message) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) {
                mc.player.sendSystemMessage(Component.literal("§6[AA] " + message));
            }
        });
    }

    private record ImageTransferable(BufferedImage image) implements Transferable {
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
            return image;
        }
    }
}

package net.alpaka.addons.features.chat;

import com.mojang.logging.LogUtils;
import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
 * ### Why the payload is a token and not a file
 *
 * A server can put any component it likes into chat, including a click event with this mod's
 * identifier. So the payload is a random token the mod made for one of its own button lines this
 * session, and only that maps to a file: a crafted message can name no file at all. Delete also
 * takes a second click, since it cannot be undone.
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
        if (AlpakaConfig.instance.autoCopyScreenshots && canCopy()) {
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

    /** Whether copying an image to the clipboard is possible here. Not on macOS; see copy(). */
    public static boolean canCopy() {
        return Util.getPlatform() != Util.OS.OSX;
    }

    /**
     * The screenshots this session has posted buttons for, by a random token. A button carries only
     * its token, so a click resolves to a file only if the mod itself put that button in chat; a
     * server message carrying the mod's click event cannot point it at anything. The last few only.
     */
    private static final Map<String, File> TOKENS = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, File> eldest) {
            return size() > 32;
        }
    };

    /** A Delete that asked for a second click, and until when that click counts. */
    private static String armedDelete = null;
    private static long armedUntilMs = 0L;
    private static final long CONFIRM_WINDOW_MS = 5_000L;

    private static String tokenFor(File file) {
        String token = UUID.randomUUID().toString();
        synchronized (TOKENS) {
            TOKENS.put(token, file);
        }
        return token;
    }

    /** "Saved screenshot [Open] [Copy] [Delete]", with an optional note between the text and the buttons. */
    private static Component buttonLine(File file, Component note) {
        String name = file.getName();
        String token = tokenFor(file);
        MutableComponent line = Component.literal("Saved screenshot").withStyle(style -> style
                .withColor(ChatFormatting.GRAY)
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(name).withStyle(ChatFormatting.WHITE))));
        if (note != null) {
            line.append(" ").append(note);
        }
        line.append(" ").append(button("[Open]", ChatFormatting.GREEN, new ClickEvent.OpenFile(file), "Open " + name));
        // Offered only where it can work: on macOS the copy always fails, see copy().
        if (canCopy()) {
            line.append(" ").append(button("[Copy]", ChatFormatting.AQUA, custom(COPY, token), "Copy the image to the clipboard"));
        }
        line.append(" ").append(button("[Delete]", ChatFormatting.RED, custom(DELETE, token), "Delete " + name));
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

    private static ClickEvent custom(Identifier id, String token) {
        return new ClickEvent.Custom(id, Optional.of(StringTag.valueOf(token)));
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
        String token = event.payload().flatMap(Tag::asString).orElse(null);
        File file;
        synchronized (TOKENS) {
            file = token == null ? null : TOKENS.get(token);
        }
        if (file == null) {
            feedback("§cThat screenshot link is not from this session.");
        } else if (COPY.equals(event.id())) {
            copy(file, error -> feedback(error == null
                    ? "§aScreenshot copied to the clipboard."
                    : "§cCouldn't copy the screenshot (" + error + ")."));
        } else if (DELETE.equals(event.id())) {
            // Deleting is permanent, so the first click only asks.
            long now = System.currentTimeMillis();
            if (token.equals(armedDelete) && now < armedUntilMs) {
                armedDelete = null;
                delete(file);
            } else {
                armedDelete = token;
                armedUntilMs = now + CONFIRM_WINDOW_MS;
                feedback("§7Click §c[Delete]§7 again within 5 seconds to delete §f" + file.getName() + "§7.");
            }
        }
        return true;
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

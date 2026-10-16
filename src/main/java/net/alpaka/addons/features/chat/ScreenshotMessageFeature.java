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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Better Screenshots - what happens to the "Saved screenshot as ..." notice: the line with Open,
 * Copy and Delete buttons, the automatic copy to the clipboard, and the {@link ScreenshotPreview}.
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
 * Not through AWT: Minecraft starts it headless, and the flag is fixed the first time anything
 * touches AWT, so there is no clipboard to be had from it. GLFW's clipboard carries text only. The
 * PNG goes to the system's own tool instead - PowerShell on Windows, osascript on macOS, wl-copy or
 * xclip on Linux - with the path in an environment variable, so it is never part of a command line.
 *
 * ### Where it starts
 *
 * At the moment the game writes the file ({@link #onSaved}), not at the chat notice: other mods may
 * keep that notice out of the chat. The notice, when it does come, only makes way for the button
 * line. A notice for a file the hook did not see is still acted on, as before.
 *
 * ### Auto copy
 *
 * With the auto copy toggle on, the copy starts the moment the screenshot is saved. When the button line
 * is on as well, the line is held back until the copy is done and then posted with "copied" - or
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
     * The screenshots {@link #onSaved} has already acted on, so the vanilla notice that follows is
     * not acted on a second time. The notice may never come - another mod can swallow it - so only
     * the last few are kept.
     */
    private static final Map<String, Boolean> SAVED = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > 16;
        }
    };

    /**
     * When the screenshot key was pressed, oldest first: one entry per screenshot still to be
     * written. Screenshots that other code takes (a mod, the dev tour) find none and are left alone.
     */
    private static final ArrayDeque<Long> KEY_PRESSES = new ArrayDeque<>();
    private static final long KEY_PRESS_WINDOW_MS = 10_000L;

    /** The screenshot key was pressed; the file it leads to is the player's own screenshot. */
    public static void onKeyPressed() {
        synchronized (KEY_PRESSES) {
            KEY_PRESSES.addLast(System.currentTimeMillis());
        }
    }

    private static boolean takeKeyPress() {
        long now = System.currentTimeMillis();
        synchronized (KEY_PRESSES) {
            // A press whose capture never wrote a file must not claim a later, unrelated one.
            while (!KEY_PRESSES.isEmpty() && now - KEY_PRESSES.peekFirst() > KEY_PRESS_WINDOW_MS) {
                KEY_PRESSES.pollFirst();
            }
            return KEY_PRESSES.pollFirst() != null;
        }
    }

    private static String keyOf(File file) {
        return file.getAbsoluteFile().toPath().normalize().toString();
    }

    /**
     * The game has just written a screenshot to disk; called on the thread that wrote it. This is
     * where the feature starts, not at the chat notice, so it still works when another mod (Essential,
     * for one) keeps "Saved screenshot as ..." out of the chat. Only files from the screenshot key.
     */
    public static void onSaved(File file) {
        if (!takeKeyPress()) return;
        if (!AlpakaConfig.instance.betterScreenshotMessageEnabled) return;
        synchronized (SAVED) {
            SAVED.put(keyOf(file), Boolean.TRUE);
        }
        // Queued before the game queues its own notice, so the notice finds the file marked.
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> act(file, line -> mc.execute(() -> mc.gui.hud.getChat().addClientSystemMessage(line))));
    }

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
        if (!AlpakaConfig.instance.betterScreenshotMessageEnabled) return false;

        boolean handled;
        synchronized (SAVED) {
            handled = SAVED.remove(keyOf(file)) != null;
        }
        // Already posted (or about to be) by onSaved: the notice only has to make way for it.
        if (handled) return AlpakaConfig.instance.screenshotChatButtons;
        return act(file, repost);
    }

    /** Preview, button line and automatic copy for one screenshot. Returns whether a line is posted. */
    private static boolean act(File file, Consumer<Component> post) {
        ScreenshotPreview.show(file);
        boolean buttons = AlpakaConfig.instance.screenshotChatButtons;
        if (AlpakaConfig.instance.autoCopyScreenshots) {
            copy(file, error -> {
                if (buttons) {
                    Component note = error == null
                            ? Component.literal("copied").withStyle(ChatFormatting.DARK_GRAY)
                            : Component.literal("copy failed").withStyle(ChatFormatting.RED);
                    post.accept(buttonLine(file, note));
                } else if (error != null) {
                    feedback("§cCouldn't copy the screenshot (" + error + ").");
                }
            });
            return buttons;
        }
        if (buttons) {
            post.accept(buttonLine(file, null));
            return true;
        }
        return false;
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

    /**
     * "Saved screenshot [Open] [Copy] [Delete]", with an optional note between the text and the
     * buttons. Which parts appear is up to the config: the file name, and each button on its own.
     */
    private static Component buttonLine(File file, Component note) {
        AlpakaConfig cfg = AlpakaConfig.instance;
        String name = file.getName();
        String token = tokenFor(file);
        MutableComponent line = Component.literal("Saved screenshot").withStyle(style -> style
                .withColor(ChatFormatting.GRAY)
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(name).withStyle(ChatFormatting.WHITE))));
        if (cfg.screenshotShowFileName) {
            line.append(" ").append(Component.literal(name).withStyle(ChatFormatting.WHITE));
        }
        if (note != null) {
            line.append(" ").append(note);
        }
        if (cfg.screenshotShowOpen) {
            line.append(" ").append(button("[Open]", ChatFormatting.GREEN, new ClickEvent.OpenFile(file), "Open " + name));
        }
        if (cfg.screenshotShowCopy) {
            line.append(" ").append(button("[Copy]", ChatFormatting.AQUA, custom(COPY, token), "Copy the image to the clipboard"));
        }
        if (cfg.screenshotShowDelete) {
            line.append(" ").append(button("[Delete]", ChatFormatting.RED, custom(DELETE, token), "Delete " + name));
        }
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
        // Off the render thread: the clipboard tool takes a moment to start.
        Util.ioPool().execute(() -> {
            try {
                if (!file.isFile()) {
                    done.accept("file not found");
                    return;
                }
                done.accept(copyWithSystemTool(file));
            } catch (Throwable t) {
                LOGGER.warn("Couldn't copy screenshot {} to the clipboard", file, t);
                done.accept(t.getClass().getSimpleName());
            }
        });
    }

    private static final String PATH_VAR = "ALPAKA_SCREENSHOT";

    /** Runs the platform's clipboard tool on the PNG; null on success, else a short reason. */
    private static String copyWithSystemTool(File file) throws IOException, InterruptedException {
        String path = file.getAbsolutePath();
        switch (Util.getPlatform()) {
            case WINDOWS -> {
                return run(List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-STA", "-Command",
                        "Add-Type -AssemblyName System.Windows.Forms, System.Drawing; "
                                + "$img = [System.Drawing.Image]::FromFile($env:" + PATH_VAR + "); "
                                + "try { [System.Windows.Forms.Clipboard]::SetImage($img) } finally { $img.Dispose() }"),
                        path, null);
            }
            case OSX -> {
                return run(List.of("osascript", "-e",
                        "set the clipboard to (read (POSIX file (system attribute \"" + PATH_VAR + "\")) as \u00abclass PNGf\u00bb)"),
                        path, null);
            }
            default -> {
                // wl-copy and xclip stay behind to serve the clipboard; only the launcher is waited for.
                if (System.getenv("WAYLAND_DISPLAY") != null && run(List.of("wl-copy", "--type", "image/png"), path, file) == null) {
                    return null;
                }
                String error = run(List.of("xclip", "-selection", "clipboard", "-t", "image/png", "-i"), path, file);
                return error == null || !error.startsWith("xclip not found") ? error : "needs wl-copy or xclip";
            }
        }
    }

    private static String run(List<String> command, String path, File stdin) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put(PATH_VAR, path);
        if (stdin != null) builder.redirectInput(stdin);
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            return command.get(0) + " not found";
        }
        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return command.get(0) + " timed out";
        }
        int exit = process.exitValue();
        if (exit != 0) LOGGER.warn("Clipboard tool {} exited with {}", command.get(0), exit);
        return exit == 0 ? null : command.get(0) + " failed";
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
}

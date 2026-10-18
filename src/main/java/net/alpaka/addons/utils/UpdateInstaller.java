package net.alpaka.addons.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.slayer.SlayerDropTracker;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Installs the release {@link UpdateChecker} found, once the player has confirmed it.
 *
 * Nothing happens without that confirmation. The [Install] button on the chat notice (or
 * {@code /alpakaupdate install}) only opens a confirmation screen naming the version, file, size
 * and checksum; its "Install on restart" button is the one thing that starts a download. The click
 * event carries no data, so a server that sends a message with the same button can at most open
 * that screen for the release this client found itself.
 *
 * The jar is downloaded into {@code .alpaka_update/staged/} in the game folder, which Fabric never
 * scans, and kept only if its size and SHA-256 match what GitHub lists for it and its
 * fabric.mod.json is this mod at that version. Nothing is written into the mods folder while the
 * game runs: the running jar is locked on Windows, so a shutdown hook starts {@link UpdateHelper}
 * in its own JVM, which swaps the jars once the game has exited.
 *
 * The confirmed update lives only in memory. A game that is killed rather than closed installs
 * nothing, and the next start clears what it left in the staging folder and offers the update again.
 *
 * Installing is left to the player wherever replacing the jar would go wrong: a development run, a
 * test build, a mod not loaded from one jar, a read-only mods folder, or an instance a launcher
 * manages - a launcher would undo or fight a jar replaced behind its back.
 */
public final class UpdateInstaller {
    /** The [Install] button's click event, caught in ChatScreenMixin before it can reach the server. */
    public static final Identifier INSTALL = Identifier.fromNamespaceAndPath(AlpakaAddons.MOD_ID, "update_install");

    /** No jar of this mod comes near it; a larger size is refused before any request. */
    private static final long MAX_JAR_BYTES = 32L * 1024 * 1024;
    /** The whole download, every redirect and the body included. */
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(120);
    private static final int MAX_REDIRECTS = 5;
    /** A release asset name that stays a plain file name in the mods folder. */
    private static final Pattern JAR_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._+-]*\\.jar");
    private static final Pattern FILENAME_LINE = Pattern.compile("filename\\s*=\\s*([\"'])(.*)\\1");
    private static final String HELPER_CLASS = "net/alpaka/addons/utils/UpdateHelper.class";
    private static final String HELPER_FOLDER = "helper";

    /** A checked jar waiting in the staging folder to replace {@code old} with {@code dest} at exit. */
    private record Staged(String version, Path file, Path dest, Path old, String sha256) {}

    private static final AtomicBoolean downloading = new AtomicBoolean();
    private static final AtomicBoolean hooked = new AtomicBoolean();
    private static volatile Staged staged;
    private static HttpClient client;

    private UpdateInstaller() {}

    /**
     * Clears what an earlier session staged but never installed - a game that was killed, or an
     * update the helper could not apply - and any half-copied jar the helper left in the mods folder.
     */
    public static void cleanUp() {
        Util.ioPool().execute(() -> {
            deleteStaged();
            Path jar = runningJar();
            if (jar == null) return;
            try (DirectoryStream<Path> parts = Files.newDirectoryStream(jar.getParent(), "*.alpaka-part")) {
                for (Path part : parts) {
                    deleteQuietly(part);
                }
            } catch (IOException ignored) {
                // Tried again on the next start.
            }
        });
    }

    /** Why {@code release} cannot be installed from the game, or null when it can. */
    public static String blocker(UpdateChecker.Release release) {
        // A release found while network features were on must not be downloaded once they are off:
        // with the setting off the mod makes no requests at all. Checked again right before the download.
        if (!AlpakaConfig.instance.allowApiCalls) {
            return "Network features are off in /aa, so nothing is downloaded - turn them on or use the release page";
        }
        if (release.getSha256() == null) {
            return "GitHub lists no checksum for this jar, so download it from the release page";
        }
        if (release.getJarSize() <= 0 || release.getJarSize() > MAX_JAR_BYTES
                || !JAR_NAME.matcher(release.getJarName()).matches()) {
            return "The jar on GitHub doesn't look as expected, so download it from the release page";
        }
        return eligibilityProblem();
    }

    /** Whether {@code version} is downloaded and installs when the game closes. */
    public static boolean isStaged(String version) {
        Staged current = staged;
        return current != null && current.version().equals(version);
    }

    /** True when the click event was the [Install] button, whatever came of it. */
    public static boolean handleClick(ClickEvent.Custom event) {
        if (!INSTALL.equals(event.id())) return false;
        confirm();
        return true;
    }

    /** Opens the confirmation for the newest release, or says why there is nothing to confirm. */
    public static void confirm() {
        UpdateChecker.Release release = UpdateChecker.latestRelease();
        if (release == null) {
            SlayerDropTracker.sendModMessage("§7No newer release is known. §f/alpakaupdate check§7 looks again.");
            return;
        }
        String version = release.getVersion();
        if (isStaged(version)) {
            SlayerDropTracker.sendModMessage("§7Version §f" + version + "§7 is downloaded and installs when you close the game.");
            return;
        }
        if (downloading.get()) {
            SlayerDropTracker.sendModMessage("§7An update is already downloading.");
            return;
        }
        String blocker = blocker(release);
        if (blocker != null) {
            SlayerDropTracker.sendModMessage("§7" + blocker + ".");
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Screen previous = mc.gui.screen();
        String details = release.getJarName() + ", "
                + String.format(Locale.ROOT, "%.1f MB", release.getJarSize() / (1024.0 * 1024.0))
                + ", SHA-256 " + release.getSha256().substring(0, 12) + "...";
        mc.gui.setScreen(new ConfirmScreen(yes -> {
            mc.gui.setScreen(previous);
            if (yes) download(release);
        },
                Component.literal("Install Alpaka Addons " + version + "?"),
                Component.literal("It is downloaded from GitHub now and checked before anything changes. It replaces "
                        + ModVersion.mod() + " when you close the game.\n\n" + details),
                Component.literal("Install on restart"),
                CommonComponents.GUI_CANCEL));
    }

    /** Drops a confirmed update before the game closes. */
    public static void cancel() {
        if (downloading.get()) {
            SlayerDropTracker.sendModMessage("§7The update is still downloading; cancel it once it's done.");
            return;
        }
        Staged current = staged;
        if (current == null) {
            SlayerDropTracker.sendModMessage("§7No update is waiting to be installed.");
            return;
        }
        staged = null;
        Util.ioPool().execute(UpdateInstaller::deleteStaged);
        SlayerDropTracker.sendModMessage("§7Version §f" + current.version() + "§7 won't be installed.");
    }

    private static void download(UpdateChecker.Release release) {
        if (!downloading.compareAndSet(false, true)) return;
        String version = release.getVersion();
        SlayerDropTracker.sendModMessage("§7Downloading version §f" + version + "§7...");
        Util.ioPool().execute(() -> {
            String problem;
            try {
                problem = stage(release);
            } catch (IOException | RuntimeException e) {
                problem = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            } finally {
                downloading.set(false);
            }
            if (problem != null) {
                AlpakaAddons.LOGGER.warn("Could not install version {}: {}", version, problem);
            }
            String result = problem;
            Minecraft.getInstance().execute(() -> SlayerDropTracker.sendModMessage(result == null
                    ? "§aVersion §f" + version + "§a is ready and installs when you close the game."
                    : "§cCouldn't install version " + version + " (" + result + "). §7The release page still has it."));
        });
    }

    /** Downloads, checks and arms {@code release}; null when done, else why not. */
    private static String stage(UpdateChecker.Release release) throws IOException {
        String problem = blocker(release);
        if (problem != null) return problem;
        Path old = runningJar();
        String name = release.getJarName();
        if (old == null) return "the running jar is not known";
        // NTFS compares names without case.
        if (name.equalsIgnoreCase(old.getFileName().toString())) return "the new jar has the running jar's name";
        Path dest = old.getParent().resolve(name);
        if (Files.exists(dest)) return name + " is already in the mods folder";

        Path folder = root().resolve("staged");
        Files.createDirectories(folder);
        Path part = folder.resolve(name + ".part");
        Path done = folder.resolve(name);
        String sha256;
        try {
            try (OutputStream out = Files.newOutputStream(part)) {
                sha256 = fetch(release.getJarUrl(), release.getJarSize(), out);
            }
            HexFormat hex = HexFormat.of();
            if (!MessageDigest.isEqual(hex.parseHex(sha256), hex.parseHex(release.getSha256()))) {
                return "the download does not match its checksum";
            }
            String jarProblem = jarProblem(part, release.getVersion());
            if (jarProblem != null) return jarProblem;
            // On disk before the rename: the helper may move it into the mods folder seconds later.
            try (FileChannel channel = FileChannel.open(part, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(part, done, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            deleteQuietly(part);
        }

        String helperProblem = copyHelper();
        if (helperProblem != null) return helperProblem;
        Staged previous = staged;
        staged = new Staged(release.getVersion(), done, dest, old, sha256);
        if (previous != null && !previous.file().equals(done)) deleteQuietly(previous.file());
        if (hooked.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(UpdateInstaller::onExit, "Alpaka-UpdateApply"));
        }
        return null;
    }

    /**
     * Streams {@code url} into {@code sink} and returns the bytes' SHA-256. Only HTTPS to github.com
     * or its download host on every hop, redirects followed by hand, and exactly {@code size} bytes
     * by header and by count. Sends the same two headers as the update check and nothing else.
     */
    private static String fetch(String url, long size, OutputStream sink) throws IOException {
        URI target;
        try {
            target = new URI(url);
        } catch (URISyntaxException e) {
            throw new IOException("the download address is not valid");
        }
        long deadline = System.nanoTime() + DOWNLOAD_TIMEOUT.toNanos();
        for (int hop = 0; ; hop++) {
            if (!target.isAbsolute() || !isGitHub(target)) {
                throw new IOException("a download from " + target.getHost() + " is not allowed");
            }
            Duration left = Duration.ofNanos(deadline - System.nanoTime());
            if (left.isNegative() || left.isZero()) throw new IOException("timed out");
            HttpResponse<InputStream> response;
            try {
                response = client().send(HttpRequest.newBuilder(target)
                        .timeout(left)
                        .header("User-Agent", "AlpakaAddons")
                        .header("Accept", "application/octet-stream")
                        .GET()
                        .build(), HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted");
            }
            int status = response.statusCode();
            if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                response.body().close();
                if (hop == MAX_REDIRECTS) throw new IOException("too many redirects");
                String location = response.headers().firstValue("location")
                        .orElseThrow(() -> new IOException("a redirect without a target"));
                target = target.resolve(location);
                continue;
            }
            if (status != 200) {
                response.body().close();
                throw new IOException("HTTP " + status);
            }
            long length = response.headers().firstValueAsLong("content-length").orElse(size);
            if (length != size) {
                response.body().close();
                throw new IOException("GitHub sent " + length + " bytes instead of " + size);
            }
            return read(response.body(), size, deadline, sink);
        }
    }

    private static String read(InputStream input, long size, long deadline, OutputStream sink) throws IOException {
        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            input.close();
            throw new IOException("this Java has no SHA-256");
        }
        // The request timeout ends once the headers arrive; a body that stalls after that is cut off
        // at the deadline by closing it under the reader.
        AtomicBoolean timedOut = new AtomicBoolean();
        Thread watchdog = Thread.ofVirtual().name("Alpaka-UpdateTimeout").start(() -> {
            try {
                Thread.sleep(Duration.ofNanos(Math.max(0, deadline - System.nanoTime())));
                timedOut.set(true);
                input.close();
            } catch (InterruptedException | IOException finished) {
                // Read in time, or already closed.
            }
        });
        long count = 0;
        byte[] buffer = new byte[16 * 1024];
        try (input) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                count += read;
                if (count > size) throw new IOException("the download is longer than " + size + " bytes");
                sha256.update(buffer, 0, read);
                sink.write(buffer, 0, read);
            }
        } catch (IOException e) {
            throw timedOut.get() ? new IOException("timed out") : e;
        } finally {
            watchdog.interrupt();
        }
        if (count != size) throw new IOException("the download broke off after " + count + " bytes");
        return HexFormat.of().formatHex(sha256.digest());
    }

    /** HTTPS on the default port to github.com or a githubusercontent.com host, with no user part. */
    private static boolean isGitHub(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null || uri.getHost() == null) {
            return false;
        }
        if (uri.getPort() != -1 && uri.getPort() != 443) return false;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        return host.equals("github.com")
                || host.endsWith(".githubusercontent.com") && host.length() > ".githubusercontent.com".length();
    }

    /**
     * Why the downloaded jar is not this mod at {@code version}, or null. A build between releases
     * names itself after its release ({@code 1.4.0+2.abc1234}), as the update check does.
     */
    private static String jarProblem(Path jar, String version) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry("fabric.mod.json");
            if (entry == null) return "the download is not a Fabric mod";
            JsonObject json;
            try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }
            if (!AlpakaAddons.MOD_ID.equals(text(json.get("id")))) return "the download is not Alpaka Addons";
            String declared = text(json.get("version"));
            if (declared == null || !version.equals(declared.split("\\+", 2)[0])) {
                return "the download says it is version " + declared + ", not " + version;
            }
            return null;
        } catch (IOException | RuntimeException e) {
            return "the download is not a valid jar";
        }
    }

    private static String text(JsonElement element) {
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    /** Why the game may not replace its own jar, or null when it may. */
    private static String eligibilityProblem() {
        FabricLoader loader = FabricLoader.getInstance();
        if (loader.isDevelopmentEnvironment()) {
            return "This is a development run, so nothing is installed here";
        }
        if (ModVersion.mod().contains("+")) {
            return "This is a test build, not a release, so install the release by hand";
        }
        Path jar = runningJar();
        if (jar == null) {
            return "The mod isn't loaded from a single jar, so install it by hand from the release page";
        }
        // The jar too: a read-only jar can be replaced but never deleted.
        if (!Files.isWritable(jar.getParent()) || !Files.isWritable(jar)) {
            return "The mods folder can't be written to, so install it by hand from the release page";
        }
        if (launcherManaged(jar, loader.getGameDir())) {
            return "Your launcher manages this instance's mods, so update it there";
        }
        return null;
    }

    /** The one regular .jar the mod runs from, or null. */
    private static Path runningJar() {
        ModOrigin origin = FabricLoader.getInstance().getModContainer(AlpakaAddons.MOD_ID)
                .map(ModContainer::getOrigin).orElse(null);
        if (origin == null || origin.getKind() != ModOrigin.Kind.PATH || origin.getPaths().size() != 1) return null;
        Path jar = origin.getPaths().get(0).toAbsolutePath();
        if (jar.getParent() == null || !Files.isRegularFile(jar) || !jar.getFileName().toString().endsWith(".jar")) {
            return null;
        }
        return jar;
    }

    /**
     * Whether a launcher keeps track of this instance's mods. These are guesses from the files each
     * one leaves; a wrong guess only means the player installs by hand.
     */
    private static boolean launcherManaged(Path jar, Path gameDir) {
        // CurseForge writes minecraftinstance.json into the instance.
        if (Files.exists(gameDir.resolve("minecraftinstance.json"))) return true;
        // The Modrinth App keeps instances in <app data>/ModrinthApp/profiles/<name> (older builds: its id).
        for (Path dir = gameDir.toAbsolutePath(); dir != null && dir.getParent() != null; dir = dir.getParent()) {
            Path parent = dir.getParent();
            Path app = parent.getParent() == null ? null : parent.getParent().getFileName();
            if ("profiles".equals(String.valueOf(parent.getFileName()))
                    && app != null && List.of("ModrinthApp", "com.modrinth.theseus").contains(app.toString())) {
                return true;
            }
        }
        // Prism and packwiz list each mod in mods/.index/<mod>.pw.toml, naming its jar.
        Path index = jar.getParent().resolve(".index");
        if (!Files.isDirectory(index)) return false;
        String name = jar.getFileName().toString();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(index, "*.pw.toml")) {
            for (Path file : files) {
                if (Files.size(file) > 64 * 1024) continue;
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    Matcher match = FILENAME_LINE.matcher(line.trim());
                    if (match.matches() && match.group(2).equalsIgnoreCase(name)) return true;
                }
            }
        } catch (IOException | RuntimeException e) {
            return false;
        }
        return false;
    }

    /**
     * Copies the helper's class file out of the mod, so the helper does not run from a jar in the
     * mods folder and always matches the code that starts it. Null when done, else why not.
     */
    private static String copyHelper() {
        try (InputStream helper = UpdateInstaller.class.getClassLoader().getResourceAsStream(HELPER_CLASS)) {
            if (helper == null) return "the installer is missing from the mod";
            Path copy = root().resolve(HELPER_FOLDER).resolve(HELPER_CLASS);
            Files.createDirectories(copy.getParent());
            Files.copy(helper, copy, StandardCopyOption.REPLACE_EXISTING);
            return null;
        } catch (IOException e) {
            return "the installer could not be prepared (" + e.getClass().getSimpleName() + ")";
        }
    }

    /** The shutdown hook: checks the staged jar once more and starts the helper. Never waits for it. */
    private static void onExit() {
        Staged plan = staged;
        if (plan == null) return;
        try {
            if (!plan.sha256().equals(UpdateHelper.sha256(plan.file()))) {
                AlpakaAddons.LOGGER.warn("The staged {} changed after it was checked; it is not installed", plan.file().getFileName());
                return;
            }
            Path root = root();
            String parentStart = Long.toString(ProcessHandle.current().info().startInstant()
                    .map(Instant::toEpochMilli).orElse(-1L));
            // The class path is relative to the folder the helper starts in, so a ';' or ':' in the
            // game folder's path cannot split it.
            new ProcessBuilder(List.of(javaLauncher(), "-Xmx32m", "-cp", HELPER_FOLDER, UpdateHelper.class.getName(),
                    "--parent-pid", Long.toString(ProcessHandle.current().pid()),
                    "--parent-start", parentStart,
                    "--staged", plan.file().toString(), "--dest", plan.dest().toString(), "--old", plan.old().toString(),
                    "--sha256", plan.sha256(), "--log", root.resolve("logs").resolve("helper.log").toString()))
                    .directory(root.toFile())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException | RuntimeException e) {
            AlpakaAddons.LOGGER.warn("Could not start the update installer", e);
        }
    }

    /** The running JVM's java: javaw.exe on Windows, so no console window opens. */
    private static String javaLauncher() {
        Path bin = Path.of(System.getProperty("java.home"), "bin");
        for (String name : new String[]{"javaw.exe", "java.exe", "java"}) {
            if (Files.isRegularFile(bin.resolve(name))) return bin.resolve(name).toString();
        }
        return ProcessHandle.current().info().command().orElse("java");
    }

    /** {@code .alpaka_update} in the game folder, beside the mods folder rather than in it. */
    private static Path root() {
        return FabricLoader.getInstance().getGameDir().resolve(".alpaka_update");
    }

    private static void deleteStaged() {
        Path folder = root().resolve("staged");
        // Never through a link put in the folder's place.
        if (!Files.isDirectory(folder, LinkOption.NOFOLLOW_LINKS)) return;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder)) {
            for (Path file : files) {
                deleteQuietly(file);
            }
        } catch (IOException ignored) {
            // Tried again on the next start.
        }
    }

    private static synchronized HttpClient client() {
        if (client == null) {
            // Redirects are followed by hand, so each hop's host can be checked.
            client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
        }
        return client;
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Cleared on the next start.
        }
    }
}

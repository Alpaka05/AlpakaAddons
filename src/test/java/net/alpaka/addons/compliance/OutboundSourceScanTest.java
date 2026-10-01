package net.alpaka.addons.compliance;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every place in the mod that can send a chat message or command to the server.
 *
 * The mod may only send something as the direct result of the player pressing a key, clicking or
 * pressing Enter. Two features once broke that - a party-chat auto-reply and a guild announcement
 * fired from a timer - so a new call site has to be added to this list on purpose, after checking
 * that it only runs on such an action.
 */
class OutboundSourceScanTest {
    private static final Pattern SEND = Pattern.compile(
            "\\.(?:sendCommand|sendChat|sendUnattendedCommand|sendSignedCommand)\\s*\\(|connection\\.send\\s*\\(");

    /**
     * Only the outbound gate itself. Everything that sends goes through it with the player's action
     * as its cause: Enter on a chat tab, the Y key on a party invite, a command wheel release.
     */
    private static final Set<String> ALLOWED = Set.of("compliance/Outbound.java");

    @Test
    void onlyReviewedFilesSendToTheServer() throws IOException {
        Path root = Path.of("src/main/java/net/alpaka/addons");
        Set<String> found = new TreeSet<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String name = file.toString();
                if (!name.endsWith(".java") && !name.endsWith(".kt")) continue;
                if (SEND.matcher(Files.readString(file)).find()) {
                    found.add(root.relativize(file).toString().replace('\\', '/'));
                }
            }
        }
        assertEquals(new TreeSet<>(ALLOWED), found,
                "Something sends to the server without going through Outbound. Route it through "
                        + "Outbound.command with the player's action as its cause.");
    }
}

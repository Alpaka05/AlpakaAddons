package net.alpaka.addons.features.profile;

import com.google.gson.JsonParser;
import net.alpaka.addons.config.AlpakaConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.net.http.HttpHeaders;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutionException;

/** The game's one scanner, independent of which screen happens to be open. */
final class GuildItemSearchService {
    private GuildItemSearchService() {}

    private static final class Holder {
        static final GuildItemSearch INSTANCE = new GuildItemSearch(
                FabricLoader.getInstance().getConfigDir().resolve("alpaka-guild-item-search.json"),
                (path, skyKings) -> {
                    var response = request(path, skyKings);
                    int status = response.statusCode();
                    if (status / 100 != 2) {
                        String provider = skyKings ? "SkyKings" : "Elite";
                        String message = switch (status) {
                            case 401, 403 -> provider + " refused access. Check your key and resume.";
                            case 404 -> provider + " couldn't find this player or inventory.";
                            case 429 -> provider + " request limit reached.";
                            default -> provider + " answered " + status + ". Resume to retry.";
                        };
                        throw new GuildItemSearch.Failure(status, message, retryAt(response.headers(), System.currentTimeMillis()));
                    }
                    try { return JsonParser.parseString(response.body()); }
                    catch (RuntimeException error) {
                        throw new GuildItemSearch.Failure(0, "Data source returned unreadable JSON. Resume to retry.", 0);
                    }
                }, GuildItemSearchService::ready);
    }

    static GuildItemSearch instance() { return Holder.INSTANCE; }

    static boolean ready() {
        return AlpakaConfig.instance.allowApiCalls && !AlpakaConfig.instance.skykingsApiKey.isBlank();
    }

    private static java.net.http.HttpResponse<String> request(String path, boolean skyKings) throws Exception {
        try { return ProfileApi.searchRequest(path, skyKings, AlpakaConfig.instance.skykingsApiKey.trim()).get(); }
        catch (ExecutionException error) {
            throw new GuildItemSearch.Failure(0, "Couldn't reach " + (skyKings ? "SkyKings" : "Elite") + ". Resume to retry.", 0);
        }
    }

    /** Both seconds and HTTP dates are valid Retry-After values; SkyKings also publishes a reset. */
    static long retryAt(HttpHeaders headers, long now) {
        long retry = now;
        String after = headers.firstValue("Retry-After").orElse("");
        try { retry = Math.max(retry, now + Long.parseLong(after) * 1000L); }
        catch (NumberFormatException ignored) {
            try { retry = Math.max(retry, ZonedDateTime.parse(after, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()); }
            catch (RuntimeException notDate) { /* An absent or malformed header uses the scanner's backoff. */ }
        }
        try {
            retry = Math.max(retry, Instant.ofEpochSecond(Long.parseLong(headers.firstValue("X-RateLimit-Reset").orElse("0"))).toEpochMilli());
        } catch (RuntimeException ignored) { /* Older providers may omit their reset time. */ }
        return retry;
    }
}

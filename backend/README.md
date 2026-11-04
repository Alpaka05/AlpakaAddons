# AlpakaAddons API

The server behind `https://api.alpaka.yt`: the Profile Dashboard's way to Hypixel's API. Hypixel's
API needs a key, and a key may never ship in a mod, so it stays here and the mod asks this server.
Not affiliated with Hypixel.

## What it does

- `GET /v1/auth` logs a player in. The mod first tells Mojang's session server that it joins a
  made-up server id (`joinServer`, as joining any server does) and sends that id with the player's
  name in `X-Minecraft-Username` and `X-Minecraft-Server`. The server asks Mojang (`hasJoined`)
  and, if Mojang confirms, answers with a token for 12 hours. The player's access token never
  reaches this server.
- `GET /v1/skyblock/profiles/<uuid>`, `/v1/skyblock/museum/<profile id>` and
  `/v1/skyblock/garden/<profile id>` with `Authorization: Bearer <token>` answer with Hypixel's
  `/v2/skyblock/profiles`, `/museum` and `/garden`, unchanged.
- `GET /` and `GET /health` say what it is and that it runs.

Within Hypixel's [API policy](https://developer.hypixel.net/policies): nothing is fetched unless a
player opens a profile, answers are cached (profiles 5 minutes, museum and garden 10), each player
may cause at most 20 new requests to Hypixel in 5 minutes (60 requests in all, cached ones
included), logins are limited per address, and the last 15 requests of the key's budget are never
spent. It keeps nothing about who asked for what, and isn't offered to other developers.

## Build and run

```bash
./gradlew -p backend build                      # from the repository root
java -jar backend/build/libs/alpaka-api-1.0.0.jar
```

Configured from the environment: `ALPAKA_API_BIND` and `ALPAKA_API_PORT` (default
`127.0.0.1:8787`) and `ALPAKA_HYPIXEL_KEY_FILE` (default `~/.config/alpaka-api/hypixel.key`). The
key file is `chmod 600` and never goes into the repository. It listens only on localhost: the way
in is a Cloudflare tunnel from `api.alpaka.yt` to `localhost:8787`, so no port is open on the
router. `deploy/alpaka-api.service` runs it as a systemd user service.

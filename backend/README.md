# AlpakaAddons API

The server behind `https://api.alpaka.yt`: the Profile Dashboard's way to Hypixel's API. Hypixel's
API needs a key, and a key may never ship in a mod, so it stays here and the mod asks this server.
Not affiliated with Hypixel.

## What it does

- `GET /v1/auth/challenge` hands out a random server id, good for one login within a minute
  (ten per address in ten minutes).
- `GET /v1/auth` logs a player in. The mod tells Mojang's session server that it joins a server
  with that id (`joinServer`, as joining any server does) and sends the id with the player's name in
  `X-Minecraft-Username` and `X-Minecraft-Server`. The server checks that it handed the id out and
  that it wasn't used yet, asks Mojang (`hasJoined`) and, if Mojang confirms, answers with a token
  for 12 hours. The player's access token never reaches this server. The one-time id is what keeps
  the owner of some other server from logging in here as the players who join theirs: their server
  id is that server's hash, which this server never handed out.
- `GET /v1/dashboard/<uuid>` with `Authorization: Bearer <token>` answers with what the Profile
  Dashboard shows of that player from Hypixel's `/v2/skyblock/profiles`: each profile's id, name,
  mode and whether it is selected, and of the player's own member data only the fields in
  `Dashboard.MEMBER_FIELDS` (today the Rift's inventories). Co-op members' data and everything else
  is left out, and there is no way to get Hypixel's answer as it is: this is the dashboard's
  backend, not a copy of Hypixel's API. A new dashboard page adds its fields there.
- `GET /` and `GET /health` say what it is and that it runs.

Within Hypixel's [API policy](https://developer.hypixel.net/policies): nothing is fetched unless a
player opens a profile; answers are cached for an hour, as Hypixel suggests (a player's own profile
for 10 minutes), in at most about 48 MB; each player may cause at most 20 new requests to Hypixel
in 5 minutes (60 requests in all, cached ones included); answers over 16 MB are refused; and the
last 15 requests of the key's budget are never spent. It keeps nothing about who asked for what,
and isn't offered to other developers.

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

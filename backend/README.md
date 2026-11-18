# AlpakaAddons API

The server behind `https://api.alpaka.yt`: where the Profile Dashboard gets its profiles from.
Hypixel's API needs a key, and a key may never ship in a mod, so it stays here and the mod asks
this server. Not affiliated with Hypixel.

## What it does

- `GET /v1/auth/challenge` hands out a random server id, good for one login within a minute
  (ten per address in ten minutes).
- `GET /v1/auth` logs a player in. The mod tells Mojang's session server that it joins a server
  with that id (`joinServer`, as joining any server does) and sends the id with the player's name in
  `X-Minecraft-Username` and `X-Minecraft-Server`. The server checks that it handed the id out and
  that it wasn't used yet, asks Mojang (`hasJoined`) and, if Mojang confirms, answers with a token
  for 12 hours. The player's access token never reaches this server. The one-time id is what keeps
  the owner of some other server from logging in here as the players who join theirs: their server
  id is that server's hash, which this server never handed out. The token names the player and
  when it ends, signed with the server's secret (`Sessions`), so nothing is stored about it and it
  outlives a restart; the mod keeps it for the next game.
- `GET /v1/dashboard/<name or uuid>` with `Authorization: Bearer <token>` answers with everything
  the Profile Dashboard shows of that player, in one request: who they are (name and skin, from
  Mojang), the SkyBlock profiles they are on, and of one profile the parts of the player's own
  member data listed in `Dashboard.MEMBER_FIELDS`, the two things its co-op shares (collection tiers
  unlocked, minions crafted) and its garden (`Dashboard.GARDEN_FIELDS`). Jacob's contests come
  counted by crop and medal (`contest_summary`), not one by one. The profile is the one named with
  `?profile=<id>`, else the one the player has selected. `?fresh=1` is the dashboard's Refresh
  button: the cached answer is then only used if it is very young (see below). `?hint=<id>` names
  the profile last found selected, which only makes the answer faster.
- `GET /v1/museum/<uuid>/<profile id>`, logged in, answers with what the player donated to that
  profile's museum (`Dashboard.MUSEUM_FIELDS`), `null` when Hypixel doesn't hand it out. The mod
  asks for it right after the dashboard, which has started on it already: it is as big as the rest of
  the profile together, so the profile is on screen first. `?fresh=1` as above.
- `GET /v1/bazaar`, without a login, is Hypixel's public Bazaar list cut down to each product's
  instant buy and sell price, in Hypixel's shape: a few dozen KB instead of 3.5 MB. Asked of
  Hypixel, without the key, at most once a minute however many ask; 30 requests per address in
  5 minutes.
- `GET /` and `GET /health` say what it is and that it runs.

Behind this are three of Hypixel's endpoints, `/v2/skyblock/profiles`, `/garden` and `/museum`.
The garden and the museum belong to a profile, and which profile is shown only follows from the
profiles; so that they need not wait for those, they are asked for alongside on a guess: the profile
named, the one chosen the last time this player was looked up, or the mod's hint. Whose a UUID is
is asked of Mojang alongside too. A profile not cached then takes as long as Hypixel's profiles
answer, about a second, instead of the sum of them; a wrong guess only adds the garden's tenth of a
second, since the museum is a request of its own.

Co-op members' data and everything no page draws is left out, and there is no way to get Hypixel's
answer as it is: this is the dashboard's backend, not a copy of Hypixel's API. A new dashboard page
adds its fields to the lists in `Dashboard`. The mod works levels, totals and the networth out
itself (`HypixelProfile` and `Networth` in the mod), so the server never has to know the game's
tables. Answers are sent gzipped to a client that asks for it; a big profile is about a megabyte of
text and half that packed, its museum as much again. The server's upload is what most of the time
to open a profile goes into, so the less that is sent, the sooner it is there.

Within Hypixel's [API policy](https://developer.hypixel.net/policies): nothing is fetched unless a
player opens a profile; answers are cached for an hour, as Hypixel suggests (a player's own profile
for 10 minutes; with Refresh at least 5 minutes, the own 1 minute), in at most about 48 MB; a name's
account is remembered for 6 hours; each player may cause at most 45 new requests to Hypixel in
5 minutes, where a profile not cached takes three (60 requests in all, cached ones included, and
opening a profile is two: the dashboard and its museum);
answers over 16 MB are refused; and the last 15 requests of the key's budget are never spent. It
keeps nothing about who asked for what, and isn't offered to other developers.

## Build and run

```bash
./gradlew -p backend build                      # from the repository root
java -jar backend/build/libs/alpaka-api-1.0.0.jar
```

Configured from the environment: `ALPAKA_API_BIND` and `ALPAKA_API_PORT` (default
`127.0.0.1:8787`), `ALPAKA_HYPIXEL_KEY_FILE` (default `~/.config/alpaka-api/hypixel.key`) and
`ALPAKA_TOKEN_SECRET_FILE` (default `~/.config/alpaka-api/token.secret`, made on the first start).
Both files are `chmod 600` and never go into the repository. It listens only on localhost: the way
in is a Cloudflare tunnel from `api.alpaka.yt` to `localhost:8787`, so no port is open on the
router. `deploy/alpaka-api.service` runs it as a systemd user service.

<div align="center">

<img src="src/main/resources/assets/alpaka/icon.png" alt="Alpaka Addons" width="128">

# Alpaka Addons

**A client-side Fabric mod for Hypixel SkyBlock: slayer tracking, a better chat and a cleaner look.**

[![Latest release](https://img.shields.io/github/v/release/Alpaka05/AlpakaAddons?label=release&color=29B6B2)](https://github.com/Alpaka05/AlpakaAddons/releases/latest)
[![Build](https://github.com/Alpaka05/AlpakaAddons/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/Alpaka05/AlpakaAddons/actions/workflows/build.yml)
![Minecraft 26.2](https://img.shields.io/badge/Minecraft-26.2-8A6FDF)
![Fabric](https://img.shields.io/badge/loader-Fabric-DBB47E)
![Client-side](https://img.shields.io/badge/client--side-only-4C9A2A)

</div>

## About

Alpaka Addons adds quality-of-life features, HUDs and visual tweaks for Hypixel SkyBlock, and makes the game a little nicer to look at along the way. It includes:

- **Slayer Tracking:** boss timers, drops and XP for every slayer, with a live session HUD.
- **A Better Chat:** tabs for Party, Guild and PMs, search, compact repeats, smooth scrolling and a peek key.
- **Useful HUDs:** your inventory, a small player model, a performance line with the server's day, and a wide health bar, all movable.
- **Visual Tweaks:** item viewmodel, block overlay, Etherwarp target, fullbright and less clutter on mobs.
- **Fresh Menus:** a new main menu, pause menu and config, all in one consistent glass style.

Alpaka Addons is most useful for **Slayers**, but a lot of it helps anywhere in SkyBlock, and some of it everywhere in Minecraft.

## Getting Started

1. **Install:** you need [Fabric Loader](https://fabricmc.net/use/installer/), [Fabric API](https://modrinth.com/mod/fabric-api), [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) and Java 25. Download the latest `alpaka-<version>-mc26.2.jar` from [Releases](https://github.com/Alpaka05/AlpakaAddons/releases/latest) and put it in your `mods` folder. [Mod Menu](https://modrinth.com/mod/modmenu) is optional.
2. **Set up:** type `/aa` in-game to open the config. Every feature has its own toggle and is easy to find with the search bar, and `/aa <term>` opens the config with that search already filled in.
3. **Arrange your HUD:** `/alpakahud` opens the HUD editor, where you can move and resize every overlay.

## Features

<details>
<summary><b>Slayer</b></summary>

- **Slayer Drop Tracker:** counts kills and rare drops per slayer, with a sound on rare drops and optional share buttons for the rarest one. `/alpakaslayer since <item>` tells you how many bosses ago a drop last came.
- **Boss Timer:** times every fight from spawn to death, calls out personal bests per slayer tier and shows the running time on screen.
- **Session HUD:** live stats while a quest is active, with a choice of lines. It can be limited to slayer areas and pauses when you are idle.
- **Boss Spawn Alert** and an option to hide Hypixel's slayer chat spam.
- `/alpakastats` stores your stats in a folder you choose, for example a cloud-synced one, so they follow you across PCs.

</details>

<details>
<summary><b>Chat</b></summary>

- **Chat Tabs:** All, Party, Guild and PMs, with unread counts. Tab cycles through them.
- **Chat Search:** Ctrl+F filters the chat to messages containing your text.
- **Chat Peek:** hold a key to read and scroll the full chat while playing, even with a menu open.
- **Compact Chat:** repeated messages stack into one line with (x2), (x3)…
- **Expand Chat History:** keeps 1000 messages (up to 5000), even after leaving a server.
- **Smooth Chat:** messages slide in, scrolling glides, and an optional blurred, rounded background.
- **Chat Mentions**, **Custom Guild Tag** and a **Bridge Bot Formatter** that shows relayed Discord messages as `[Discord]`.
- **Better Screenshots**: a preview that shrinks into the corner, and Open, Copy and Delete buttons.

</details>

<details>
<summary><b>HUD & Interface</b></summary>

- **Inventory HUD:** your 27 inventory slots on screen, as a frosted panel or a chest.
- **Player Model HUD:** a small 3D version of yourself on screen.
- **Wide Health Bar:** both heart rows in one line, since SkyBlock has no hunger.
- **Performance HUD:** the server's day, FPS, ping and TPS on one line, each part optional.
- **World Age on Join:** a chat message with the server's age and when you last visited.
- **Notifications** for mentions, boss spawns and party invites, in any corner or at the top centre.
- **Party Invite Prompt:** accept with Y or dismiss with N.
- **Scrollable Tooltips**, **Keep Item Name Visible** and adjustable container background opacity.
- **Custom Main Menu** with a live Hypixel player count, and a **Custom Pause Menu**.

</details>

<details>
<summary><b>Visuals</b></summary>

- **Item Viewmodel:** position, size, rotation and swing of the held item, with swing motion blur and three presets.
- **Block Overlay:** custom outline and fill for the targeted block, with chroma and fade-in.
- **Etherwarp Overlay:** shows where you will land, with a line to the target and optional custom warp sounds.
- **Hide Mob Deaths**, **Hide Damage Flash**, **Clean Blaze** and **Only Show Crit Damage**.
- **Fullbright**, **Zoom**, **Smooth Perspective** and an option to skip the front view.

</details>

<details>
<summary><b>Cosmetics</b> (only you can see them)</summary>

- **Custom Name Tag** in third person with rainbow, gradient and wave effects.
- **Sensei Hat**, a straw hat with optional chroma colors.
- **Player Scale** per axis, for yourself and optionally other players.

</details>

<details>
<summary><b>Sound & Utility</b></summary>

- **Quick Command Wheel:** a radial menu for your favourite commands, sorted into pages.
- **Always Sprint**.
- **Custom Sounds** for menus, the hotbar, hits, slayer drops, boss spawns and a low-HP heartbeat.

</details>

### Commands

| Command | What it does |
| --- | --- |
| `/aa` (also `/alpaka`) | Opens the config. `/aa <term>` searches it. |
| `/alpakahud` | Opens the HUD editor. |
| `/alpakaslayer [slayer]` | Shows your slayer kills and drops, or the full drop history of one slayer. |
| `/alpakaslayer since <item>` | Shows how many bosses ago a drop last came, with buttons to share it. |
| `/alpakastats folder <path>` | Moves your stats to another folder. `folder default` moves them back. |
| `/alpakapreset <1-3>` | Loads a viewmodel preset. `save <1-3>` saves the current settings. |

Keybinds for **Zoom**, **Peek Chat**, **Command Wheel** and **Inventory HUD** are under Options > Controls.

## Fair Play

Alpaka Addons runs entirely on your client and follows the [Hypixel rules](https://hypixel.net/rules). It only shows or rearranges what the game already tells you, or what you look up yourself. There are no macros, no automation and nothing that plays the game for you.

**Not affiliated with Hypixel.** Alpaka Addons is a fan-made mod. It is not affiliated with, endorsed by or associated with Hypixel Inc. or Mojang.

**Chat and commands.** The mod sends a chat message or command only when you act: pressing Enter in chat, pressing the party-accept key, clicking a button in one of its windows or picking a command on the command wheel. Each of those sends exactly one command. Share buttons only fill your chat box, so you read the message and send it yourself. Every outgoing command goes through one place in the code, and a test fails the build if anything else tries to send one. Three things send without a click, each only while you have it switched on:

- **Quick Join** sends `/skyblock` once after you join Hypixel (off by default).
- **Auto Answer** replies in party chat when a party member asks for something like `!tps` (off by default, at most once every two seconds).
- The **Performance HUD's ping** uses Minecraft's own ping request to the server, which nobody sees and which changes nothing, at most once every five seconds.

When a server offers a resource pack, the mod's pack cache answers it the way Minecraft itself does.

**What the mod reads.** In the game, only what your client already has:

- chat lines, the sidebar, the tab list and boss name tags, for the slayer tracker and timers
- the item data of your own items, for overlays such as Etherwarp and Item Value
- the contents of menus, and only once you have opened them yourself

**What it contacts.** With "Allow Network Features" on (it can be turned off in General), the mod asks these services. Each request is made because you opened something or because the feature needs it right then. None of them gets your Minecraft login.

- **Hypixel's public API** (api.hypixel.net): the current mayor, for slayer XP; Bazaar prices and the list of SkyBlock items, collections, skills and Bingo dates, for prices, icons and the Profile Dashboard. These need no key and send nothing about you.
- **The Hypixel server's status**, for the player count on the main menu.
- **SkyCofl** (sky.coflnet.com): auction prices and price history, for Item Value, the Auction House price suggestions and the market graphs. Requests name the item, never you.
- **The NotEnoughUpdates repository** on GitHub: item looks, pet heads and the lists behind the Bestiary, Museum, skill trees and rabbits.
- **For the Profile Dashboard**, only when you open it on a player: **Elite** (api.elitebot.dev) for the profile itself, **Soopy** (api.soopy.dev) for its history, and **SkyKings** (api.skykings.net) only if you entered your own SkyKings key. They are asked for the player you look up, by name or UUID.
- **The AlpakaAddons API** (api.alpaka.yt), only when you open a player's Rift page in the Profile Dashboard: the mod's own server, which asks Hypixel's API with a key that stays on that server (its code is in `backend/`), and answers only with what the dashboard shows of the player you look up. To log in, the API hands out a one-time id, the game tells Mojang's session server that you join a server with that id, the way joining any server works, and the API asks Mojang whether you did. Your access token only goes to Mojang, never to the API. The API keeps its answers for an hour (your own profile for 10 minutes) and nothing about who asked for what; it is reached through Cloudflare, which sees your address and the request like any website's host.
- **GitHub**, with "Update Check" on: once per start, whether a newer release is out. Only after you click Install and confirm does it download that release's jar from GitHub, check it against GitHub's checksum and swap it in when you close the game.

Player heads load their skins from Mojang, as everywhere in Minecraft. With "Allow Network Features" off, the mod makes none of these requests.

**Guardrails.**

- Smooth perspective only eases between the vanilla camera views. There is no free look, and every transition ends exactly where vanilla's camera is.
- Commands that delete or move your data (`/alpakaslayer reset`, `/alpakastats folder`, `/alpakapreset save`) work only when you type them. A clickable chat message cannot run them.
- Screenshot buttons only act on screenshots this session posted, and Delete needs a second click.
- An update installs only after you confirm it on a screen. The chat button and `/alpakaupdate install` just open that screen, and the command works only when you type it.

## Feedback & Bugs

Found a bug or have an idea? Open an [issue](https://github.com/Alpaka05/AlpakaAddons/issues). Please include your mod version and, for crashes, the log.

## Building

```bash
./gradlew build -Pdeploy_target=none
```

The jar ends up in `build/libs`. The AlpakaAddons API behind the Profile Dashboard's Rift page is a build of its own in `backend/`: `./gradlew -p backend build`. `deploy_target` in `gradle.properties` can also copy it straight into a Modrinth App or OneClient profile.

### Versions

The version comes from the git tags, not from a file. A tagged commit builds as that version (`1.3.0`); any other commit builds as how far it is past the last release (`1.3.0+4.a1b2c3d`, plus `.dirty` with uncommitted changes). Releasing means tagging:

```bash
git tag 1.4.0 && git push origin 1.4.0
./gradlew build
```

Raise the middle number for a release with new features (1.3.0 → 1.4.0), the last one for a release that only fixes bugs (1.3.0 → 1.3.1).

A branch working towards the next major version sets `version_line` in `gradle.properties` (`version_line=2.0.0` for the 2.0 line). Its builds are then named for that version, `2.0.0-alpha+134.0de331d`, until a tag of it exists. Test builds handed out before the release are tagged as betas on that branch, `2.0.0-beta.1`, and published as GitHub pre-releases; only players already on a pre-release are told about them. `2.0.0` itself is tagged on `main` once it is ready, and `version_line` can then be removed.

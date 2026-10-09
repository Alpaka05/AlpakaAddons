<div align="center">

<img src="src/main/resources/assets/alpaka/icon.png" alt="Alpaka Addons" width="128">

# Alpaka Addons

**A client-side Fabric mod for Hypixel SkyBlock: slayer tracking, a better chat and a cleaner look.**

[![Latest release](https://img.shields.io/github/v/release/Alpaka05/AlpakaAddons?label=release&color=29B6B2)](https://github.com/Alpaka05/AlpakaAddons/releases/latest)
![Minecraft 26.2](https://img.shields.io/badge/Minecraft-26.2-8A6FDF)
![Fabric](https://img.shields.io/badge/loader-Fabric-DBB47E)
![Client-side](https://img.shields.io/badge/client--side-only-4C9A2A)

</div>

## About

Alpaka Addons adds quality-of-life features, HUDs and visual tweaks for Hypixel SkyBlock, and makes the game a little nicer to look at along the way. It includes:

- **Slayer Tracking:** boss timers, drops and XP for every slayer, with a live session HUD.
- **A Better Chat:** tabs for Party, Guild and PMs, search, compact repeats, smooth scrolling and a peek key.
- **Useful HUDs:** your inventory, a small player model, world age and a wide health bar, all movable.
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
- **Boss Timer:** times every fight from spawn to death, calls out personal bests and shows the running time on screen.
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
- **Expand Chat History:** keeps 5000 messages, even after leaving a server.
- **Smooth Chat:** messages slide in, scrolling glides, and an optional blurred, rounded background.
- **Chat Mentions**, **Custom Guild Tag** and a **Bridge Bot Formatter** that shows relayed Discord messages as `[Discord]`.
- **Better Screenshot Message** with Open, Copy and Delete buttons.

</details>

<details>
<summary><b>HUD & Interface</b></summary>

- **Inventory HUD:** your 27 inventory slots on screen, as a frosted panel or a chest.
- **Player Model HUD:** a small 3D version of yourself on screen.
- **Wide Health Bar:** both heart rows in one line, since SkyBlock has no hunger.
- **World Age HUD** and a join message with the server's age.
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
- **Pangolin Highlight** in Torrhus Canyon.
- **Fullbright**, **Zoom**, **Smooth Perspective** and an option to skip the front view.

</details>

<details>
<summary><b>Cosmetics</b> (only you can see them)</summary>

- **Custom Name Tag** in third person with rainbow, gradient and wave effects.
- **Sensei Hat**, a straw hat with optional chroma colours.
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

Alpaka Addons runs entirely on your client and follows the [Hypixel rules](https://hypixel.net/rules). It only shows or rearranges what the game already tells you. There are no macros, no automation and nothing that plays the game for you.

**Chat and commands.** The mod never sends anything unless you act: pressing Enter in chat, pressing the party-accept key, or picking a command on the command wheel. Each of those sends exactly one command. Share buttons only fill your chat box, so you read the message and send it yourself. Every outgoing command goes through one place in the code, and a test fails the build if anything else tries to send one.

**What the mod reads.** Only what your client already has:

- chat lines, the sidebar, the tab list and boss name tags, for the slayer tracker and timers
- the item data of your own items, for the Etherwarp overlay
- the contents of menus, and only once you have opened them yourself

**What it contacts.** With "Allow Network Features" on (it can be turned off in General), the mod fetches the current SkyBlock mayor from Hypixel's public API to get slayer XP right, and pings the Hypixel server from the main menu to show its player count. Neither one sends anything about you. With the setting off, the mod makes no network requests at all.

**Guardrails.**

- Smooth perspective only eases between the vanilla camera views. There is no free look, and every transition ends exactly where vanilla's camera is.
- Highlights such as the pangolin outline appear only when you have a clear line of sight. Nothing is drawn through walls.
- Commands that delete or move your data (`/alpakaslayer reset`, `/alpakastats folder`, `/alpakapreset save`) work only when you type them. A clickable chat message cannot run them.
- Screenshot buttons only act on screenshots this session posted, and Delete needs a second click.

## Feedback & Bugs

Found a bug or have an idea? Open an [issue](https://github.com/Alpaka05/AlpakaAddons/issues). Please include your mod version and, for crashes, the log.

## Building

```bash
./gradlew build -Pdeploy_target=none
```

The jar ends up in `build/libs`. `deploy_target` in `gradle.properties` can also copy it straight into a Modrinth App or OneClient profile.

### Versions

The version comes from the git tags, not from a file. A tagged commit builds as that version (`1.3.0`); any other commit builds as how far it is past the last release (`1.3.0+4.a1b2c3d`, plus `.dirty` with uncommitted changes). Releasing means tagging:

```bash
git tag 1.4.0 && git push origin 1.4.0
./gradlew build
```

Raise the middle number for a release with new features (1.3.0 → 1.4.0), the last one for a release that only fixes bugs (1.3.0 → 1.3.1).

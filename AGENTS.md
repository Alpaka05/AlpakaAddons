# AlpakaAddons

Client-side Fabric mod for Hypixel SkyBlock (Java + some Kotlin), mod id `alpaka`. Players install it
from the GitHub releases. The maintainer plays and tests on a Mac; the agents work on a Linux server
without a display.

This file is for every coding agent (Claude Code, Codex, or any agent run through T3 Code);
`CLAUDE.md` only imports it. More rules live in `.agents/rules/`. The maintainer's words in a
session always win over this file.

## Build and test

- JDK 25: `export JAVA_HOME=$(ls -d ~/.jdks/*25* | head -1)`.
- `./gradlew build -Pdeploy_target=none` compiles, runs the JUnit tests and leaves the jar in
  `build/libs`. Without `-Pdeploy_target=none` the build tries to copy the jar into a local
  Modrinth/OneClient folder, which doesn't exist on the server.
- Only one Gradle build or dev client at a time (the server has 4 CPUs, 10 GB).
- The version comes from git tags (`gitVersion()` in `build.gradle`). Never change a version or
  create, move or push a release tag unless asked (see `.agents/rules/versions.md`).
- To look at the UI, there is a screenshot tour of the dev client on Xvfb (`alpaka-tour`, see the
  maintainer's memory). Show every screenshot under a new, unique filename.
- Check a Minecraft or Fabric API before using it instead of guessing from memory: 26.2 is not
  obfuscated, so `javap -cp ~/.gradle/caches/fabric-loom/26.2/minecraft-merged.jar <class>` shows
  the real signatures.

## Layout

- `src/main/java/net/alpaka/addons/`
  - `client/AlpakaClient.java`: entry point. `client/gui/AlpakaConfigRegistry.java` registers every
    config option (`ConfigOption`), `config/AlpakaConfig.java` holds the values.
  - `features/<feature>/`: one package per feature (chat, slayer, blockoverlay, wheel, ...).
  - `mixin/`: mixins. One that only changes how something looks or sounds goes into
    `alpaka.cosmetic.mixins.json`, where no injection is required: a hook that finds no target
    is logged once (`OptionalHooksPlugin`) and the effect stays off. Anything correctness or
    compliance depends on goes into `alpaka.mixins.json`, where every injection is required.
    Prefer `@WrapOperation`, `@WrapWithCondition` and `@ModifyExpressionValue` over `@Redirect` and
    `@ModifyConstant`, and never target a generated `lambda$...` method.
  - `compliance/Outbound.java`: the only place that may send chat messages or commands to the
    server, and only as the direct result of a key press, click or Enter.
    `OutboundBytecodeScanTest` scans the compiled classes and fails the build if anything else
    sends to the server (packets, chat, commands, gameplay actions, method references included).
    The documented exceptions: `Outbound.latencyProbe()`, the Performance HUD's ping (at most every
    5 s), `Outbound.quickJoinSkyblock()`, Quick Join's opt-in /skyblock after joining Hypixel
    (at most every 30 s), and the resource pack answers of the pack cache in
    `mixin/DownloadedPackSourceMixin` and
    `mixin/ClientCommonPacketListenerPackCacheMixin`.
- `src/test/`: JUnit 5 tests (Java and Kotlin).
- `tools/`: helper scripts (Modrinth cleanup, menu icons, ...).

## Branches

- `main`: Minecraft 26.2, version line 1.x. Releases come from here.
- `design-rework`: the redesigned config menu and design language (AlpakaStyle), version line
  2.0.0-alpha. It stays off main until the maintainer says the redesign is ready, so never merge
  design-rework into main. A feature meant for both gets two PRs: one against main (ported onto the
  old config menu) and one against design-rework (cherry-pick, or the same change redone there).
- `mc-26.1.2`: backport line for Minecraft 26.1.2, discontinued since 2026-10-18 (releases are
  26.2 only). Not synced any more; only touched when the maintainer asks.
- `test/*`: throwaway integration branches for test builds. Never base PRs on them.

## How changes are shipped

The full procedure is in `.agents/rules/shipping.md` (Claude also has it as the `alpaka-ship`
skill). In short:

1. Every change gets its own branch (`fix/...`, `feat/...`, `perf/...`, `docs/...`) off the right
   base, Conventional Commit messages (`fix(block-overlay): ...`), pushed, and a PR against that base.
   The maintainer merges PRs, agents don't, unless told to.
2. If the change belongs on both lines, open the second PR right away.
3. **Every change is deployed to the maintainer's Mac on its own, without asking:**
   - changes for `main` go into the Modrinth App profile **Fabric 26.2**,
   - changes for `design-rework` go into **OneClient** (cluster "26.2 Fabric").

   `alpaka-test-build <main|design-rework>` (on the server) builds the base branch plus every open
   PR against it and deploys the result to the right place, so the maintainer always tests the
   whole pending state, not just the latest PR.
4. Report in German, short: what changed, PR links, what was deployed where.

## Working rules

- Never delete, weaken or skip a failing test to get the build green. Fix the code, or say plainly
  which test fails and why.
- Stage only the files of the change (`git add <files>`), never a blind `git add -A`; worktrees
  often hold build output or scratch files.
- Never print or commit tokens, passwords or other credentials; write `<token>` instead.
- Ask before anything hard to undo that this file doesn't already allow: force-pushing, deleting
  branches or releases, adding or upgrading a dependency, changing CI.

## Where a new config option goes

main and design-rework have two different config menus, and an option placed right in one can
land in the wrong spot in the other. Work out where it goes in each menu separately, from that
branch's own code, every time:

- **main (old menu):** one flat list per tab (`ConfigCategory`), split by header options. The
  option appears exactly where it is registered in `AlpakaConfigRegistry`, under the header before
  it. Register it in the section it belongs to, not just at the end of the tab.
- **design-rework (new menu):** `ConfigTree` builds sidebar page → feature → settings from the
  registry. An option is a feature of its own (its own row in the middle column) only if its id is
  in `ConfigTree.MASTERS`; otherwise it silently becomes a setting of whatever feature was
  registered just before it. Each feature then has to be listed on its sidebar page in
  `ConfigTree.LAYOUT`, or it ends up under "Other". Settings registered away from their feature
  go into `PARENTS`.
- **design-rework: Blur and Smooth Font come last.** In every feature that has them, the Blur and
  Smooth Font switches are the last settings; nothing goes below them. A new setting for such a
  feature is registered before them (the per-HUD ones are registered at the end in
  `registerHudStyleToggles` for this reason). This rule is for design-rework only.
- **design-rework: the key comes first.** A feature's key (`ConfigOption.keybind`, title "Key") is
  its first setting, right under the description. `ConfigTree` moves it there wherever it is
  registered, but register it straight after the feature anyway. Never register a key through
  Fabric's `KeyMappingHelper` (that puts it in vanilla Controls); use `AlpakaKeyBinds.register`.
- If it isn't clear which page or section an option belongs on, ask before you open the PR. Check
  in the menu (screenshot tour) that it shows up where intended.

## Conventions

- User-visible text: English, American spelling ("Color"), Title Case for titles.
- Colour options always via `ConfigOption.color(...)` (shows a swatch and a reset button), never a
  plain "Choose Color" button.
- On design-rework, every window opened from the config uses `AlpakaStyle` (header height
  `AlpakaStyle.HEADER_H`), and every dangerous action asks through the one `AlpakaConfirmScreen`
  (`ConfigOption.confirm(...)`). The pause and main menu keep their glass style (`MenuStyle`), HUD
  text keeps its Hypixel colours.
- Comments explain why, in plain sentences; match the surrounding code.
- Release notes: English, sections **New** / **Changed** / **Fixed**, one short bullet per item, no
  explanations.

## Known mod interactions (OneClient)

The maintainer's OneClient setup has mods that the Modrinth profile doesn't. If a bug only shows up
there, check these first:

- **Gnetum** renders the HUD into its own cached framebuffer; HUD code that reads the frame gets an
  empty buffer, or Gnetum's cached copy (PRs #66 and #67 deal with this).
- **MacOS Input Fixes** sends Tab and Escape into `KeyboardHandler.keyPress` twice
  (`MacOSInputFixesCompat`).
- **Screenshot Message Enhancer** replaces vanilla's screenshot code; hook
  `NativeImage.writeToFile`, not `Screenshot`.
- Rendering runs on Vulkan (MoltenVK) there.

# AlpakaAddons

Client-side Fabric mod for Hypixel SkyBlock (Java + some Kotlin), mod id `alpaka`. Players install it
from the GitHub releases. The maintainer plays and tests on a Mac; Claude works on a Linux server
without a display.

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

## Layout

- `src/main/java/net/alpaka/addons/`
  - `client/AlpakaClient.java`: entry point. `client/gui/AlpakaConfigRegistry.java` registers every
    config option (`ConfigOption`), `config/AlpakaConfig.java` holds the values.
  - `features/<feature>/`: one package per feature (chat, slayer, blockoverlay, wheel, ...).
  - `mixin/`: mixins, listed in `src/main/resources/alpaka.mixins.json`.
  - `compliance/Outbound.java`: the only place that may send chat messages or commands to the
    server, and only as the direct result of a key press, click or Enter.
    `OutboundSourceScanTest` fails the build if anything else calls the network.
- `src/test/`: JUnit 5 tests (Java and Kotlin).
- `tools/`: helper scripts (Modrinth cleanup, menu icons, ...).

## Branches

- `main`: Minecraft 26.2, version line 1.x. Releases come from here.
- `design-rework`: the redesigned config menu and design language (AlpakaStyle), version line
  2.0.0-alpha. It stays off main until the maintainer says the redesign is ready, so never merge
  design-rework into main. A feature meant for both gets two PRs: one against main (ported onto the
  old config menu) and one against design-rework (cherry-pick, or the same change redone there).
- `mc-26.1.2`: backport line for Minecraft 26.1.2, synced by merging main and translating 26.2 APIs
  back. Only touched when the maintainer asks.
- `test/*`: throwaway integration branches for test builds. Never base PRs on them.

## How changes are shipped

Follow the `alpaka-ship` skill. In short:

1. Every change gets its own branch (`fix/...`, `feat/...`, `perf/...`, `docs/...`) off the right
   base, Conventional Commit messages (`fix(block-overlay): ...`), pushed, and a PR against that base.
   The maintainer merges PRs, Claude doesn't, unless told to.
2. If the change belongs on both lines, open the second PR right away.
3. **Every change is deployed to the maintainer's Mac on its own, without asking:**
   - changes for `main` go into the Modrinth App profile **Fabric 26.2**,
   - changes for `design-rework` go into **OneClient** (cluster "26.2 Fabric").

   `alpaka-test-build <main|design-rework>` (on the server) builds the base branch plus every open
   PR against it and deploys the result to the right place, so the maintainer always tests the
   whole pending state, not just the latest PR.
4. Report in German, short: what changed, PR links, what was deployed where.

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

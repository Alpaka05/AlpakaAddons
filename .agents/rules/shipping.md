# Shipping a change

How any AlpakaAddons change goes from request to the maintainer's Mac. The maintainer doesn't want
to explain branches, merges or deploys each time: do all of this yourself, without asking, for every
change. Ask only when the base branch is genuinely unclear.

## 1. Pick the base

| The change is about... | Base | PRs |
|---|---|---|
| the redesigned config menu, AlpakaStyle, anything that only exists on design-rework | `design-rework` | 1 |
| a feature or fix that exists on both lines (default for gameplay/HUD/chat fixes) | `main` **and** `design-rework` | 2 |
| something the maintainer explicitly scopes to one branch | that branch | 1 |
| Minecraft 26.1.2 | `mc-26.1.2`, only when asked | 1 |

If the maintainer is clearly testing in OneClient, the change must at least reach design-rework.
`git grep` the code on both `origin/main` and `origin/design-rework` before deciding; the config
menus differ a lot.

## 2. Branch, change, verify

```bash
git fetch -q origin
git worktree add -q /tmp/<topic> -b fix/<topic> origin/<base>   # or work in the current worktree
export JAVA_HOME=$(ls -d ~/.jdks/*25* | head -1)
./gradlew build -Pdeploy_target=none -q   # compiles + tests; must pass
```

- Branch names: `fix/`, `feat/`, `perf/`, `docs/` + a short topic; the design-rework twin gets a
  `-design-rework` suffix (e.g. `fix/slower-block-chroma` and `fix/slower-block-chroma-design-rework`).
- For the second line, `git cherry-pick` the commit onto a branch from the other base; if it doesn't
  apply, redo the change there (main has the old config menu).
- Never merge design-rework into main. Never base a PR on a `test/*` branch.

## 3. Commit, push, PR

- Conventional Commit, English: `fix(block-overlay): halve chroma base speed so 1x matches the old 0.5x`,
  ending with your agent's co-author line.
- `git push -u origin <branch>`, then `gh pr create --base <base>` with a short English body (what
  changes for the player; for the twin PR: "Same change as #NN, for design-rework.") ending with
  your agent's generator line.
- In T3 Code, link every PR to the thread with the `link_pull_request` tool.
- Don't merge PRs; the maintainer (Alpaka05) merges. Only merge when told, then with a merge commit.
- Push follow-up fixes to the same branch; the PR updates itself.

## 4. Deploy to the Mac (always, unasked)

For each base a PR was opened against, run on the server:

```bash
alpaka-test-build main            # -> OneClient, cluster "26.2 Fabric" (since 2026-10-30; was Modrinth)
alpaka-test-build design-rework   # -> OneClient, cluster "26.2 Fabric"
```

It builds `origin/<base>` plus every open, non-draft PR against it (so the maintainer tests the whole
pending state), puts the jar on the Mac and disables the old one. It prints which PRs went in and
which were skipped for conflicts. `ALPAKA_SKIP_PRS="57 66"` leaves PRs out; extra origin branches
can be passed as arguments. Also run it after the maintainer merges something, so the profile
catches up.

- Only one build at a time; run the two calls one after the other.
- If the Mac isn't reachable (lid closed, Remote Login off), say so instead of retrying in a loop.
- `alpaka-deploy-mac <repo>` deploys one checkout as it is (target chosen by branch, or
  `ALPAKA_TARGET=oneclient|modrinth`).
- Changes that only touch docs or agent files (nothing in the jar) need no deploy.

## 5. Report (German, short)

- What changed, in one or two sentences.
- PR links per branch.
- What now runs where: jar name and OneClient, and that the game must be
  restarted.
- Anything skipped or still open (e.g. a PR left out for a conflict).

## Before claiming something is "already there"

Check what's actually on the Mac instead of assuming:

```bash
ssh mac 'ls "$HOME/Library/Application Support/Polyfrost/OneClient/clusters/26.2 Fabric/mods" | grep alpaka | grep -v disabled'
ssh mac 'ls "$HOME/Library/Application Support/ModrinthApp/profiles/Fabric 26.2/mods" | grep alpaka | grep -v disabled'
```

The jar name ends in the commit hash; `git branch -a --contains <hash>` and
`git show <hash>:<file>` tell whether a change is in it.

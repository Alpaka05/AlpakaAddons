# Shipping a change

How any AlpakaAddons change goes from request to the maintainer's Mac. The maintainer doesn't want
to explain branches, merges or deploys each time: do all of this yourself, without asking, for every
change. Ask only when something is genuinely unclear.

## 1. Base

Every change goes against `main`, as one PR. `design-rework` was merged into main on 2026-10-29
and is frozen: no branches, twin PRs or test builds for it. `mc-26.1.2` is only touched when the
maintainer asks.

## 2. Branch, change, verify

```bash
git fetch -q origin
git switch -c fix/<topic> origin/main   # in the current worktree
export JAVA_HOME=$(ls -d ~/.jdks/*25* | head -1)
./gradlew build -Pdeploy_target=none -q   # compiles + tests; must pass
```

- Branch names: `fix/`, `feat/`, `perf/`, `docs/` + a short topic (e.g. `fix/slower-block-chroma`).
- Never base a PR on a `test/*` branch.

## 3. Commit, push, PR

- Conventional Commit, English: `fix(block-overlay): halve chroma base speed so 1x matches the old 0.5x`,
  ending with your agent's co-author line.
- `git push -u origin <branch>`, then `gh pr create --base main` with a short English body (what
  changes for the player) ending with your agent's generator line.
- In T3 Code, link every PR to the thread with the `link_pull_request` tool.
- Don't merge PRs; the maintainer (Alpaka05) merges. Only merge when told, then with a merge commit.
- Push follow-up fixes to the same branch; the PR updates itself.

## 4. Deploy to the Mac (always, unasked)

Run on the server:

```bash
alpaka-test-build main   # -> OneClient, cluster "26.2 Fabric"
```

The Modrinth App profile "Fabric 26.2" is no longer a deploy target (since 2026-10-30).

It builds `origin/main` plus every open, non-draft PR against it (so the maintainer tests the whole
pending state), puts the jar on the Mac and disables the old one. It prints which PRs went in and
which were skipped for conflicts. `ALPAKA_SKIP_PRS="57 66"` leaves PRs out; extra origin branches
can be passed as arguments. Also run it after the maintainer merges something, so the profile
catches up.

- Only one build at a time.
- If the Mac isn't reachable (lid closed, Remote Login off), say so instead of retrying in a loop.
- `alpaka-deploy-mac <repo>` deploys one checkout as it is (target chosen by branch, or
  `ALPAKA_TARGET=oneclient|modrinth`).
- Changes that only touch docs or agent files (nothing in the jar) need no deploy.

## 5. Report (German, short)

- What changed, in one or two sentences.
- PR links.
- What now runs on the Mac: jar name in OneClient, and that the game must be restarted.
- Anything skipped or still open (e.g. a PR left out for a conflict).

## Before claiming something is "already there"

Check what's actually on the Mac instead of assuming:

```bash
ssh mac 'ls "$HOME/Library/Application Support/Polyfrost/OneClient/clusters/26.2 Fabric/mods" | grep alpaka | grep -v disabled'
```

The jar name ends in the commit hash; `git branch -a --contains <hash>` and
`git show <hash>:<file>` tell whether a change is in it.

# Credits

Everything in the mod that was not made for it, and where it came from. Keep this list in step with
`src/main/resources/assets/alpaka/sounds.json` and `src/main/resources/assets/alpaka/font/`: a file
that is added there gets a row here in the same change.

## Fonts

All four are under the SIL Open Font License 1.1. The licence texts ship with the mod in
`assets/alpaka/font/licenses/`.

| Font | Files | Licence |
| --- | --- | --- |
| Inter | `inter.ttf` | OFL 1.1, `inter-ofl.txt` |
| Nunito SemiBold (weight-600 instance of the variable font) | `nunito_semibold.ttf` | OFL 1.1, `nunito-ofl.txt` |
| Outfit Bold | `outfit_bold.ttf` | OFL 1.1, `outfit-ofl.txt` |
| Poppins Bold | `poppins_bold.ttf` | OFL 1.1, `poppins-ofl.txt` |
| Varela Round | `varela_round.ttf` | OFL 1.1, `varela_round-ofl.txt` |

## Dungeon map data

The Dungeon Map recognizes rooms the way [Odin](https://github.com/odtheking/Odin) by odtheking does,
and uses its room table, under the BSD 3-Clause License. The licence text ships inside the mod in
`src/main/resources/assets/alpaka/dungeonmap/licenses/odin-bsd-3-clause.txt`.

| File | What | Source | Licence |
| --- | --- | --- | --- |
| `src/main/resources/assets/alpaka/dungeonmap/rooms.json` (read by `features/dungeonmap/DungeonRooms.kt`) | Room names, types, secret counts and center-column cores of all 140 rooms | Odin, `src/main/resources/assets/odin/rooms.json` (commit b412ad1c) | BSD-3-Clause |
| `src/main/java/net/alpaka/addons/features/dungeonmap/DungeonRooms.kt` | The center-column hash a room is recognized by, and how action bar secret counts are assigned to rooms | Odin, `features/impl/dungeon/map/WorldScan.kt` and `utils/skyblock/ActionBarListener.kt` | BSD-3-Clause |

## Sounds

**To fill in.** No source or licence has been recorded for any of these yet. For each file, note
where it came from (a sound library, a resource pack, your own recording), who made it, and the
licence or permission that lets the mod ship it. A file whose origin cannot be shown should be
replaced, for example with CC0 audio from Kenney or Freesound, or with your own recording. Do not
ship audio taken or derived from Minecraft's own sounds.

The three files marked ⚠ were added in commits that describe them as copied from a texture pack,
so they need a source and permission first.

All paths are under `src/main/resources/assets/alpaka/sounds/`.

| File | Used for | Added in | Source | Licence |
| --- | --- | --- | --- | --- |
| `button_click.ogg` | UI button click | b46b8b9 (v1.0.19) | | |
| `inventory_click.ogg` | Inventory click | b46b8b9 (v1.0.19) | | |
| `inventory_open.ogg` | Inventory open | 94d218a | | |
| `inventory_close.ogg` | Inventory close | 94d218a | | |
| `player_hurt.ogg` | Player hurt | 94d218a | | |
| `heartbeat.ogg` | Low HP heartbeat | d668cc0 (v1.0.20) | | |
| `boss_spawn.ogg` | Slayer boss spawn | d668cc0 (v1.0.20) | | |
| `rare_drop.ogg` | Slayer rare drop | fcd163d (v1.0.17) | | |
| `insane_drop.ogg` | Slayer insane drop | fcd163d (v1.0.17) | | |
| `hotbar_equip.ogg` | Hotbar equip | 7fcf788 (v1.0.21) | | |
| `etherwarp.ogg` | Etherwarp sound, default | 4e4b708 | | |
| `etherwarp_pling.ogg` | Etherwarp sound choice | 7779d0d | | |
| `etherwarp_thud.ogg` | Etherwarp sound choice | 7779d0d | | |
| `etherwarp_chime.ogg` | Etherwarp sound choice | 7779d0d | | |
| `etherwarp_pop.ogg` | Etherwarp sound choice | 7779d0d | | |
| ⚠ `zombie/remedy.ogg` | Hyperion Wither Shield | 4e5e49c (v1.0.23), "custom zombie/remedy.ogg" | | |
| ⚠ `random/successful_hit.ogg` | Successful hit | 57a7212 (v1.0.22), "custom texturepack sound assets 1:1" | | |
| ⚠ `mob/blaze/death.ogg` | Blaze death | 4e5e49c (v1.0.23) | | |

`mob/blaze/death.ogg` and `random/successful_hit.ogg` are the same file, byte for byte, so the
blaze death currently plays the hit sound.

## Dungeon solver data

Some of the data the dungeon solvers and their tests use (under
`src/main/java/net/alpaka/addons/features/dungeon/`, `src/main/resources/assets/alpaka/dungeon/` and
`src/test/java/net/alpaka/addons/features/dungeon/`) is adapted from
[Odin](https://github.com/odtheking/Odin) by odtheking, under the BSD 3-Clause License. Each such
file says so in a header comment and has a row below. Game facts (coordinates, chat lines, quiz
answers) are not anyone's work and are not listed. No code is taken from Skyblocker, NoFrills or
Skytils. Their lists were only used, together with both Hypixel SkyBlock wikis, to check and
complete game facts: a few quiz questions and answers (the single Wither Lord status questions,
the Hub's stained glass vendor) and the Arrow Align layouts the tests compare against.

| File | What | Source | Licence |
| --- | --- | --- | --- |
| `src/main/resources/assets/alpaka/dungeon/quiz_answers.json` (read by `features/dungeon/puzzle/QuizAnswers.java`) | Quiz answer table (questions, answers, fairy souls per zone). The answers themselves are game facts, checked and completed against Skyblocker's and Skytils' lists and both wikis; Backwater Bayou only from Odin | Odin, `src/main/resources/assets/odin/puzzles/quiz-answers.json` | BSD-3-Clause |
| `src/main/java/net/alpaka/addons/features/dungeon/puzzle/CreeperBeamsPairs.java` | Creeper Beams lantern pairs (11 pairs; Odin's 13 rows minus one duplicate and one reversed duplicate) | Odin, `src/main/resources/assets/odin/puzzles/creeper-beams-solutions.json` (commit b412ad1c) | BSD-3-Clause |
| `src/main/resources/assets/alpaka/dungeon/water_board.json` (read by `features/dungeon/puzzle/WaterBoardSolutions.java`) | Water Board solution timings, Standard and Fast sets | Odin, `src/main/resources/assets/odin/puzzles/water-solutions.json` (sets "false" and "true"). The Standard set is the one-flow solutions by derek1984, Jade and bonsai collected in [Desco1/WaterSolver](https://github.com/Desco1/WaterSolver), as shipped in Odin | BSD-3-Clause |
| `src/main/java/net/alpaka/addons/features/dungeon/terminal/TerminalSolver.java` | Select All item name prefixes per color (white: white, bone, wool; black: black, ink; blue: blue, lapis; brown: brown, cocoa; green: green, cactus; red: red, rose; yellow: yellow, dandelion; light gray: light gray, silver) | Odin, `SelectAllHandler.kt` | BSD-3-Clause |
| `src/main/java/net/alpaka/addons/features/dungeon/device/ArrowAlignPatterns.java` | Arrow Align: the nine solution layouts (target rotation per item frame) | Odin, `features/impl/boss/ArrowAlign.kt` (`possibleSolutions`) | BSD-3-Clause |
| `src/test/java/net/alpaka/addons/features/dungeon/puzzle/BoulderSearchTest.java` | The eight Boulder board layouts and three of their push lists, as test data only | Odin, `src/main/resources/assets/odin/puzzles/boulder-solutions.json` | BSD-3-Clause |
| `src/test/java/net/alpaka/addons/features/dungeon/puzzle/IceFillSearchTest.java` | The fifteen Ice Fill floor layouts, decoded, as test data only | Odin, `src/main/resources/assets/odin/puzzles/ice-fill-floors.json` | BSD-3-Clause |

Odin's licence, as BSD-3 asks for it to be reproduced with anything derived from it. The same text ships
inside the mod, in `src/main/resources/assets/alpaka/dungeon/licenses/odin-bsd-3-clause.txt`, so every
release jar carries it too:

```
BSD 3-Clause License

Copyright (c) 2025, odtheking

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

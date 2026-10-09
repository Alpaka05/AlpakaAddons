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
| Outfit Bold | `outfit_bold.ttf` | OFL 1.1, `outfit-ofl.txt` |
| Poppins Bold | `poppins_bold.ttf` | OFL 1.1, `poppins-ofl.txt` |
| Varela Round | `varela_round.ttf` | OFL 1.1, `varela_round-ofl.txt` |

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

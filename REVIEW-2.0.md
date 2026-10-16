# AlpakaAddons `design-rework` — Code-Review für 2.0

Stand `3f58992` (design-rework) gegenüber `origin/main` (`c0105b1`) plus die übrige Codebasis. 14 Review-Slices (5 auf den Änderungen gegenüber main, 7 auf dem Rest, 1 Querschnitt, 1 Release-Readiness), jedes Finding von einem zweiten Agenten gegen den Code geprüft, Duplikate zusammengeführt, für 2.0 priorisiert.

- **307** Findings gemeldet, **296** bestätigt, 7 widerlegt, 4 unsicher
- **237** eindeutige Probleme: **19 Blocker**, 105 „sollte“, 113 Politur
- **64** Performance-Vorschläge, 37 vom Prüfer genehmigt, **30 umgesetzt** (Branch `t3/design-rework-code-review-perf`)

Prioritäten: **Blocker** = so nicht ausliefern (Datenverlust, sichtbar kaputtes Feature, gebrochene Konvention in den neuen Menüs, Lizenz). **Sollte** = echte Bugs in Randfällen und Wartbarkeit. **Politur** = Kleinigkeiten, Wortlaut, Aufräumen. Aufwand S < 30 min, M einige Stunden, L mehr.

## Blocker für 2.0

- **[high · M]** Für keinen der 18 Sounds unter assets/alpaka/sounds sind Quelle und Lizenz in CREDITS.md eingetragen (die Tabelle steht noch auf 'To fill in'), und zombie/remedy.ogg, random/successful_hit.ogg und mob/blaze/death.ogg wurden laut Commit-Historie 1:1 aus einem Texturepack übernommen, was einen öffentlichen 2.0-Release auf GitHub/Modrinth für eine Takedown-Meldung angreifbar macht.  
  → Vor dem Tag für jeden Sound Quelle und Lizenz nachtragen oder ihn durch CC0-Audio bzw. eigene Aufnahmen ersetzen, mindestens die drei Texturepack-Dateien.  
  `CREDITS.md:22` · X2-1
  <details><summary>Details</summary>

  **No sound has a recorded source or licence; three were copied from a texture pack**

  The Sounds table in CREDITS.md is still marked 'To fill in'. All 18 .ogg files under assets/alpaka/sounds have empty Source and Licence columns. The CREDITS file itself flags zombie/remedy.ogg, random/successful_hit.ogg and mob/blaze/death.ogg as added in commits that describe them as copied from a texture pack ('custom texturepack sound assets 1:1'). Shipping a public 2.0 on GitHub/Modrinth with third-party audio of unknown licence risks a takedown of the release. Unlike the fonts, these files ship with no licence texts in the jar.

  *Vorschlag:* Before tagging 2.0, either record the source and licence of each file or replace it, for example with CC0 audio (Kenney, Freesound) or your own recordings. At minimum, drop or replace the three marked texture-pack files.

  </details>
- **[high · M]** Disable All Features verspricht im Dialog 'Turns off every feature', lässt aber Chat Mentions, Boss Spawn Alert, Party Invite Prompt (Y/N), Scrollable Tooltips und Keep Item Name Visible an, schaltet dafür Unteroptionen wie Block-Outline/-Fill, Boss Time In Chat, Boss Timer HUD und Only Show On Action ab, sodass wieder eingeschaltete Features nichts oder etwas anderes tun, und stellt obendrein Menu Font auf die Pixelschrift.  
  → Die Liste aus ConfigTree ableiten und nur die booleschen Feature-Master innerhalb von begin/endDeferredSaves ausschalten, Unteroptionen und menuFont unangetastet lassen und per Test absichern, dass danach jeder Master false ist.  
  `config/AlpakaConfig.java:934` · D2-1, X1-3, D2-2
  <details><summary>Details</summary>

  **Disable All leaves five feature switches on, despite the new 'every feature' dialog**

  disableAllFeatures() is a hand-kept list that has drifted from the registry. I checked it against ConfigTree.MASTERS. These boolean feature switches are never set to false: mentionNotificationEnabled (on by default), bossSpawnAlertEnabled (on by default), partyInvitePromptEnabled (on by default, and still takes the Y/N keys while an invite notice is up), scrollableTooltipsEnabled (on by default) and persistentItemNameEnabled. updateNotificationsEnabled and itemMotionBlurEnabled are only covered indirectly, through allowApiCalls and itemSizeFeatureEnabled. Scenario: the player confirms the dialog, which on this branch reads 'Turns off every feature of Alpaka Addons' (registry line 161). The next slayer spawn still slides in 'Boss Spawned', a party invite still raises the Y/N prompt, a name mention still raises a notice, and tooltips still scroll with the wheel. The method even switches off notificationBlur and notificationSmoothFont while leaving on the features that raise notifications.

  *Vorschlag:* Build the list from the registry instead of keeping it by hand. Walk ConfigTree.groups(), and for every feature whose hasToggle() is true, switch its master off inside begin/endDeferredSaves so there is one write. Then set hudBlurAll=false, hudSmoothFontAll=false and menuFont=0 explicitly. At minimum, add the five missing fields, plus a unit test that every boolean master id is covered.

  </details>
- **[high · S]** Im neuen Config-Menü zählt die Scroll-Grenze der Einstellungsspalte den aktuellen Offset doppelt, sodass man bei jedem Feature mit mehr Einstellungen als Platz (z. B. Custom Name Tag, auf kleineren Fenstern häufig) die Einstellungen mit jedem Mausrad-Schritt weiter ins Leere scrollen kann, ohne Ende.  
  → detailScroll.max nur aus der Inhaltshöhe berechnen, also Math.max(0, rowY - startY + 6 - clipH), wie bei Sidebar und Feature-Liste.  
  `client/AlpakaConfigScreen.java:704` · D1-1
  <details><summary>Details</summary>

  **Details column scroll max double-counts the scroll offset, allowing unbounded overscroll**

  startY is captured after the scroll shift (rowY = clipY + 2 - offset; startY = rowY), and the max is computed as (rowY + offset) - startY + 6 - clipH, i.e. contentH + offset + 6 - clipH. The offset is counted twice, so the further the column is scrolled the larger its limit becomes. Example: Custom Name Tag (13 settings incl. dropdowns) with contentH 500 and clipH 339 gives a correct max of 167. After scrolling to 167 the next frame's max is 334, then 501, and so on. The player can keep wheeling the settings off the top into blank space indefinitely. This affects every feature whose settings overflow the column, which is common on smaller windows where panelH = height - 16.

  *Vorschlag:* Compute the max from the content height alone, e.g. detailScroll.max = Math.max(0, rowY - startY + 6 - clipH) (or (rowY + offset) - (clipY + 2) + 6 - clipH). The sidebar and feature list already do it this way. A small pure helper with a unit test for this would also have caught it.

  </details>
- **[high · S]** Der Slayer Boss Timer liest SlayerBossEntityTracker.diedAtMs erst, nachdem onBossKilled den Tracker zurückgesetzt hat. Deshalb endet jede Bosszeit am Sidebar-Kill statt am Tod des Bosses: angezeigte Zeiten und Personal Bests sind 1–2 s zu lang und passen nie zu SkyHanni, und /alpakadebug meldet bei jedem Kampf 'boss death never seen'.  
  → diedAtMs vor SlayerBossEntityTracker.reset() in eine lokale Variable sichern, diese verwenden und einen Unit-Test dafür ergänzen.  
  `features/slayer/SlayerTimer.kt:173` · C2-1
  <details><summary>Details</summary>

  **Entity death is wiped before it is read, so every boss time ends on the sidebar**

  onBossKilled calls SlayerBossEntityTracker.reset() on its first line (moved there in 7a8b1b6). reset() sets diedAtMs = null. Line 190 then reads SlayerBossEntityTracker.diedAtMs, which is now always null. So `death` is never set and `elapsed` always runs to the sidebar kill moment (killedAtMs). That moment comes a second or two after the boss's health reached 1, which is the whole reason the entity tracker exists. Scenario: an Inferno Demonlord dies at T and the sidebar flips to 'Boss slain!' at T+1.2s. The time announced and stored as a personal best is 1.2s too long and never matches SkyHanni. FightBreakdown.entityDeathMs is always null, so /alpakadebug prints 'boss death never seen - end fell back to the sidebar' for every fight. The comment at lines 275-279 describes exactly this symptom as fixed.

  *Vorschlag:* Capture the tracker state before resetting, e.g. `val diedAt = SlayerBossEntityTracker.diedAtMs; SlayerBossEntityTracker.reset()`, and use `diedAt` at line 190. Alternatively, keep the reset unconditional by putting it in a `finally` after the read. A unit test that sets a died time and calls onBossKilled would have caught this.

  </details>
- **[high · M]** Nach dem ersten gezählten Boss bleibt killCountedThisFight gesetzt. Springt die Sidebar bei einem späteren Boss direkt von GRINDING auf SLAIN (schneller Kill), wird er weder über die Sidebar noch über die Chat-Zeile gezählt, und Kills, Session-Bosse, XP und RNG-Dry-Streak fehlen ohne jeden Hinweis.  
  → Das Flag beim Übergang SLAIN/NONE→GRINDING bzw. im GRINDING→SLAIN-Zweig zurücksetzen, späte Chat-Kills des vorigen Bosses über ein kurzes Zeitfenster ignorieren und Tests für zwei aufeinanderfolgende Bosse ergänzen.  
  `features/slayer/SlayerQuestDetector.kt:428` · C2-2
  <details><summary>Details</summary>

  **killCountedThisFight stays set after a kill, so later bosses that skip the fight sample are lost**

  emitKill returns early while killCountedThisFight is true. Only three things clear it: the sidebar entering FIGHT (line 398), a void while pendingKillInferred is set, or resetForTest. Grinding, SLAIN->GRINDING, NONE and world changes do not. Scenario: boss 1 goes FIGHT->SLAIN and is counted (flag = true). The quest auto-restarts (SLAIN->GRINDING, flag still true). Boss 2 dies inside one sidebar update, so the sidebar goes GRINDING->SLAIN. Line 382 calls emitKill(... timed=false), which returns at once. The chat fallback ('SLAYER QUEST COMPLETE!' -> onChatKill -> emitKill) hits the same guard. Lifetime kills, session bosses, XP and the RNG dry streak all miss boss 2 and every later boss like it. This is exactly the 'one-tick boss' case the KDoc at 355-357 says is now counted. The test bossBetweenTwoSamplesCountsButIsNotTimed only passes because it starts from resetForTest().

  *Vorschlag:* Clear the guard once a new quest is visibly under way. For example, in the GRINDING->SLAIN branch set killCountedThisFight = false before emitKill, since GRINDING was seen after the previous kill. Also clear it when the phase enters GRINDING from SLAIN/NONE. To stop a late chat line for the previous boss from double counting, ignore onChatKill within a couple of seconds of the last handed-out kill. Add a test that runs two consecutive grind->slain bosses, and one with a chat-only second kill.

  </details>
- **[high · S]** Drückt man F2 erneut, solange die Screen Preview des vorigen Screenshots noch in der Ecke steht, enthält der neue Screenshot trotzdem Vorschaubild bzw. Blitz, weil die Aufnahme sofort im selben Extract-Durchgang läuft und den vorherigen Frame kopiert.  
  → Die Aufnahme mit Minecraft.getInstance().schedule(capture) einreihen statt execute() aufzurufen, damit sie erst nach dem vorschaufreien Frame läuft.  
  `features/chat/ScreenshotPreview.java:130` · C1-1
  <details><summary>Details</summary>

  **Capture-without-preview runs synchronously and grabs the frame that still shows the preview**

  render() is reached from GameRenderer.extract -> Gui.extractRenderState inside Minecraft.renderFrame, which runs outside any task. Minecraft is a ReentrantBlockableEventLoop: scheduleExecutables() is false on the render thread when reentrantCount == 0, so BlockableEventLoop.execute() calls doRunTask() right away (checked in the 26.2 bytecode). Screenshot.grab therefore runs during extraction of the 'clean' frame, before GameRenderer.render clears and redraws the main render target. copyTextureToBuffer copies the target as it is at that moment: the previous frame, with the thumbnail or flash in it. Scenario: press F2, then press F2 again within ~4 s while the thumbnail is in the corner. The second PNG contains the first screenshot's preview, which is exactly what this mechanism (and MinecraftScreenshotMixin) exists to prevent. The comment 'the capture runs before the next one' describes the intended behaviour, not the actual one.

  *Vorschlag:* Queue the capture instead of executing it: Minecraft.getInstance().schedule(capture). BlockableEventLoop.schedule always adds to pendingRunnables, and runAllTasks drains that queue at the start of the next runTick, after this preview-free frame has been rendered. Alternatively, count down two extract calls before running it.

  </details>
- **[high · M]** '/alpakaslayer reset pb <slayer>' meldet 'Cleared the personal best', doch schon beim nächsten Speichern kopiert StatsMerge die alte Bestzeit von der Platte zurück, sodass Slayer Boss Timer und Statistikdatei die Phantom-Bestzeit dauerhaft behalten.  
  → Ein bewusstes Löschen den Merge überstehen lassen, z. B. mit einem Tombstone-Zeitstempel (bestBossClearedAtMs) oder einem Dreiwege-Merge mit dem zuletzt gelesenen Stand als Basis, und dafür einen StatsMergeTest ergänzen.  
  `config/StatsMerge.java:86` · C6-1, X1-1
  <details><summary>Details</summary>

  **Clearing a personal best is undone by the very next save**

  SlayerTimer.clearPersonalBest sets bestBossMs = -1 and calls markDirty(). At the end of the same tick, AlpakaStats.save() re-reads the file and runs StatsMerge.mergeSlayer. Because `theirs.bestBossMs > 0 && ours.bestBossMs <= 0` is true, it copies the old best time from disk back into memory and writes it out again. Scenario: the player types `/alpakaslayer reset pb blaze` and chat says "Cleared the personal best for Blaze". The PB is back in memory one tick later, the timer HUD keeps comparing against it, and the file keeps it. The merge treats every field as only ever growing, which is wrong for an action that deliberately lowers a value. No StatsMergeTest covers a cleared value.

  *Vorschlag:* Let a deliberate clear win over the disk copy. Options: (a) a three-way merge that keeps the snapshot last read from or written to disk as the base and takes the disk value only when it differs from that base; or (b) a tombstone timestamp (e.g. bestBossClearedAtMs) that mergeSlayer respects, dropping a disk best older than the clear. Add a StatsMergeTest case: memory cleared, disk holds 31 000 ms, result stays cleared.

  </details>
- **[high · S]** Die Hypixel-Spielerzahl im Custom Main Menu wird nur beim ersten Ping aktualisiert, danach läuft jeder Refresh wegen des unveränderten Favicons in den 8-s-Timeout, und nach rund 3 Minuten steht dauerhaft „can't reach Hypixel“, obwohl Hypixel erreichbar ist.  
  → Den Pong-Callback als Erfolg werten und dort onStatus aufrufen (oder pro Versuch ein frisches ServerData anlegen) und den Kommentar korrigieren.  
  `features/mainmenu/HypixelStatus.kt:78` · C5-1
  <details><summary>Details</summary>

  **Hypixel player count only updates once per game, then degrades to "can't reach Hypixel"**

  pingServer's first Runnable is vanilla's onPersistentDataChange. In 26.2 bytecode (ServerStatusPinger$1.handleStatusResponse, lambda$4) it only runs when the favicon bytes differ from data.getIconBytes(). HypixelStatus keeps one ServerData (`data`, line 37) for the whole game. The first ping stores Hypixel's favicon, and every later ping receives the same favicon, so onStatus() is never called again. Only the pong callback fires, and that just updates pingMs. Each refresh therefore stays inFlight until the 8 s timeout and counts as a failure. Timeline on the menu: success at T0. Refresh at T0+60 s times out at T0+68 s, then retries at +15/+30/+60 s back-off. At about T0+197 s the last success is older than FRESH_MS*3, so online = null and the line reads "can't reach Hypixel" for the rest of the session while Hypixel is fine. Until then a count up to 3 minutes old is shown as current. Returning to the menu after a session longer than 3 minutes shows the old count for 8 s, then the offline text. The comment on lines 74-75 ("The first callback fires when the status with the player count has arrived") is wrong for the same reason.

  *Vorschlag:* Treat the pong callback as success: `{ mc.execute { pingMs = attempt.ping; onStatus(attempt) } }`. The pong only arrives after the status was handled, and data.players is set by then. Alternatively, build a fresh ServerData per attempt and capture it in the callbacks, so neither the favicon check nor stale players from an earlier attempt can interfere. Fix the comment either way.

  </details>
- **[medium · S]** Reset All Settings übernimmt Slayer-Daten und Viewmodel-Presets, aber nicht commandWheelPages, sodass alle selbst eingetippten Befehle im Quick Command Menu ohne Warnung durch die sechs Standardbefehle ersetzt werden, obwohl der Dialog nur Slayer-Statistiken und Presets als erhalten nennt.  
  → In resetToDefaults() old.commandWheelPages wie die Presets übernehmen, commandWheelCommands auf null setzen und den Dialogtext um das Quick Command Menu ergänzen.  
  `config/AlpakaConfig.java:831` · X1-5, D2-7
  <details><summary>Details</summary>

  **'Reset All Settings' silently wipes the player's quick commands**

  resetToDefaults() carries over slayerBossMap, itemPresets, statsDirectory and lastSeenVersion, but not commandWheelPages. The fresh config has commandWheelPages = null and the legacy default list, so the next CommandWheelPages.pages() folds the six defaults back in and saves. Every command the player typed is gone. The dialog only says 'Slayer stats and saved viewmodel presets are kept', so this is unannounced loss of user-written content. The typed Guild Tag and Bridge Bot Name are also lost, which is more defensible since they are settings.

  *Vorschlag:* Carry commandWheelPages over like itemPresets (and null out commandWheelCommands), or name it in the dialog text.

  </details>
- **[medium · S]** Ein Klick auf Titel oder Wert einer Slider-Zeile im neuen Config-Menü setzt den Slider auf diese x-Position, sodass z. B. ein Klick auf 'Item Scale (Size)' das gehaltene Item ohne Rückgängig auf das Minimum schrumpft; der alte Screen hatte genau das schon behoben, und da andere Zeilen auf Klick umschalten, klicken Spieler gewohnheitsmäßig auf die Zeile.  
  → Einen Drag nur bei Klicks nahe der Slider-Spur starten (etwa my >= trackY - 5) und Klicks auf die Beschriftung ignorieren.  
  `client/AlpakaConfigScreen.java:753` · D1-8
  <details><summary>Details</summary>

  **Clicking a slider's label or value snaps the slider to that x position**

  The slider's hit is the whole 24 px row, and the track spans the full row width. A click on the label text (left) sets the value near its minimum, and a click on the value text (right) sets it near its maximum. Example: clicking 'Item Scale' shrinks the held item to the minimum. The old screen had fixed exactly this ('A click on the card's title ... used to read as the slider's far left and snap the value to its minimum').

  *Vorschlag:* Start a drag only for clicks within a few pixels of the track (e.g. my >= trackY - 5). Let clicks on the label row do nothing, or show the tooltip.

  </details>
- **[medium · S]** Mit dem standardmäßig aktiven 'Hide Slayer Chat Spam' verschwindet jede Chatnachricht, die 'Slayer LVL <Zahl>' enthält, auch Gilden- und Partynachrichten anderer Spieler wie 'Guild > Bob: finally blaze slayer lvl 9!'. Das Regex wirkt praktisch wie ein contains ohne Groß-/Kleinschreibung.  
  → Das Muster auf Hypixels eigene Zeilenform verankern ('»' optional, Slayer-Name, 'Slayer LVL <n> - …') und Zeilen mit Kanal- oder Namenspräfix ('Guild >', 'Party >', ': ') ausschließen.  
  `features/slayer/SlayerChatFilter.kt:51` · C2-4
  <details><summary>Details</summary>

  **"Slayer LVL" pattern hides other players' chat**

  `^.*Slayer LVL\s+\d+\b.*$` with IGNORE_CASE is matched against the whole cleaned line, so it is really a case-insensitive 'contains'. With 'Hide Slayer Chat Spam' on (the default), any message mentioning a slayer level vanishes from the visible chat. Examples: 'Guild > [MVP+] Bob: finally blaze slayer lvl 9!' or 'Party > Foo: need slayer lvl 4 for this'. This hook runs at ChatComponent.addMessage for every line, including player chat.

  *Vorschlag:* Anchor the pattern to Hypixel's own line shape, e.g. `^(?:»\s*)?(?:Zombie|Spider|Wolf|Enderman|Blaze|Vampire) Slayer LVL \d+ - .*$`. At minimum, reject lines carrying a channel or name prefix (contain ': ' or start with 'Guild >'/'Party >').

  </details>
- **[medium · S]** Wer im Farbwähler ins Hex-Feld klickt und 'FF0000' tippt, hängt die Eingabe an den vorbefüllten Code '#29B6B2' an. Daraus wird '#29B6B2FF', das als AARRGGBB gelesen ein fast durchsichtiges Hellblau statt Rot ergibt. '#FF0000' bewirkt gar nichts; nur nach siebenmal Backspace funktioniert es (Regression gegenüber main).  
  → Den ersten Tastendruck nach dem Fokussieren den Inhalt ersetzen lassen (z. B. Feld auf '#' setzen und den aktuellen Code als Platzhalter zeigen), optional zusätzlich Strg+A unterstützen.  
  `client/ColorPickerScreen.java:271` · D3-1
  <details><summary>Details</summary>

  **Typing a hex code into the colour picker appends to the old one and can give a wrong, mostly transparent colour**

  When the field gets focus it is filled with hexOf(). For an opaque colour that is now the 7-character "#RRGGBB"; main always showed 9 characters, so extra typing was simply refused. charTyped (line 341) appends and re-parses after every character, with a 9-character cap. Example: Menu Accent Color is 0xFF29B6B2. The player clicks the field and types FF0000. "#29B6B2F" has 7 digits and is ignored. "#29B6B2FF" has 8 digits and is parsed as AARRGGBB = 0x29B6B2FF, a light blue at about 16% opacity. The remaining "0000" is dropped by the cap. Typing "#FF0000" instead gives "#29B6B2#F" and then nothing happens. The only working path is to backspace seven times first, which nothing tells the player.

  *Vorschlag:* Make the first keystroke after focusing replace the content. For example, set hexInput = "#" on focus and show the current code as the placeholder, or keep a select-all flag that the first charTyped/Backspace clears. Optionally also accept Ctrl+A.

  </details>
- **[medium · S]** 'Reset Viewmodel Settings' unter Visuals > Viewmodel öffnet noch Vanillas ConfirmScreen mit §c§l-Text und Vanilla-Buttons statt AlpakaConfirmScreen. Das bricht die AlpakaStyle-Regel für Untermenüs, und der Klicksound wird doppelt abgespielt.  
  → Durch new AlpakaConfirmScreen(parent, "Reset Viewmodel Settings", …, "Reset", true, () -> AlpakaConfig.instance.resetItemViewmodel()) ersetzen und den zusätzlichen playButtonClickSound() sowie den Component-Import entfernen.  
  `client/gui/AlpakaConfigRegistry.java:965` · D1-M1, D2-4, X1-7, D3-M1
  <details><summary>Details</summary>

  **Reset Viewmodel Settings opens a vanilla ConfirmScreen instead of AlpakaConfirmScreen**

  item_viewmodel_reset, listed in the Viewmodel section of the new config tree (ConfigTree LAYOUT line 182), opens net.minecraft.client.gui.screens.ConfirmScreen with §c/§l formatted literals and vanilla buttons. Every other confirmation opened from the config (disable_all_features, reset_all_settings) uses AlpakaConfirmScreen with dangerous=true, which draws AlpakaStyle and ButtonKind.DANGER. That breaks the rule that sub menus use AlpakaStyle with no vanilla ConfirmScreen. The file is outside this slice's list, but it is reached directly from the slice's screen.

  *Vorschlag:* Replace it with new AlpakaConfirmScreen(parent, "Reset Viewmodel Settings", "Resets every viewmodel and swing value to its default. Saved presets are kept.", "Reset", true, () -> { AlpakaConfig.instance.resetItemViewmodel(); AlpakaConfig.save(); }), and draw its row in the danger style along with disable_all_features and reset_all_settings (see D1-3).

  </details>
- **[medium · S]** Im Config-Menü ist nur 'Disable All Features' rot (DANGER), weil die Gefahrenfarbe fest an diese ID gebunden ist. Das noch zerstörerischere 'Reset All Settings', in General > Main direkt darunter gelistet, sowie 'Reset Viewmodel Settings' und 'Reset Scale' sehen wie harmlose Aktionen aus.  
  → Ein ConfigOption.danger()-Flag einführen, es an allen destruktiven Aktionen setzen und im Screen statt des ID-Vergleichs auswerten; 0xFFC9726C als benannte Konstante in AlpakaStyle ablegen.  
  `client/gui/AlpakaConfigRegistry.java:166` · D2-3, D1-3, X1-6, D3-M2
  <details><summary>Details</summary>

  **Reset All is not drawn as a DANGER action; the danger colour is hard-coded to one id**

  ConfigOption has no way to declare an action as dangerous, so AlpakaConfigScreen.java:773 decides it with option.getId().equals("disable_all_features"). The new reset_all_settings action is the most destructive one in the mod ('This cannot be undone'), yet its row in General > Main is drawn in the normal accent colour right next to a red Disable All. item_viewmodel_reset and player_scale_reset are not red either. This breaks the convention that destructive actions use ButtonKind.DANGER, and each new destructive action would need another hard-coded id in the screen.

  *Vorschlag:* Add a ConfigOption.danger() flag (like confirmFirst()/buttonOnly()). Set it in the registry on disable_all_features, reset_all_settings, item_viewmodel_reset and player_scale_reset, and have the screen read option.isDanger() instead of comparing ids.

  </details>
- **[medium · M]** Die gewollte Korrektur einer zu hohen Gesamt-XP über das Slayer-Menü (etwa nach den zehnfach zu großzügigen Vampire-Werten) wird beim nächsten Speichern wieder überschrieben, weil StatsMerge immer den größeren Wert behält, und die Zeile 'Total XP' im Slayer Session HUD bleibt dauerhaft falsch.  
  → Menü-Ablesungen mit Zeitstempel speichern und im Merge die neuere Ablesung statt der größeren Zahl gewinnen lassen (dieselbe Regel in AlpakaStats.mergeInto), Doku-Kommentare und Tests anpassen.  
  `config/StatsMerge.java:89` · X1-2, C6-2
  <details><summary>Details</summary>

  **SlayerXpTracker's downward XP correction is reverted by the merge**

  SlayerXpTracker.observeTotal deliberately lowers totalXp when a Slayer-menu reading is far below a total that drifted high (its comment cites the Vampire table being ten times too generous) and calls markDirty(). The next flush merges the file, where the old high total still sits, and mergeSlayer keeps the larger value (`if (theirs.totalXp > ours.totalXp) ours.totalXp = theirs.totalXp`). Example: 1,000,000 is stored, then the menu shows 100,000 more than 30 minutes after the last credit. observeTotal sets 100,000, and the save restores 1,000,000 in memory and on disk. The correction can never stick, and the HUD shows the wrong 'Total XP' for good.

  *Vorschlag:* Timestamp observations (e.g. totalXpObservedAtMs) and let the merge prefer the newer observation over the larger figure. Keep 'larger wins' only for credits made after the newest observation.

  </details>
- **[medium · M]** Ist der gewählte Statistik-Ordner (z. B. ein nicht eingebundenes Cloud-Laufwerk) die ganze Sitzung über nicht da, gehen beim Beenden alle Kills, Drops und Bestzeiten dieser Sitzung kommentarlos verloren, obwohl die Warnung verspricht, sie würden im Speicher gehalten und später geschrieben.  
  → Beim Beenden und Trennen mit awaitingStore && dirty den Sitzungsstand in eine Nebendatei im Config-Ordner (z. B. alpaka-stats.pending.json) schreiben und beim nächsten erfolgreichen load() per foldInto() einlesen und löschen.  
  `config/AlpakaStats.java:515` · C6-5
  <details><summary>Details</summary>

  **Kills recorded while the stats folder is missing are discarded on exit**

  While awaitingStore is set, flushNow() returns immediately, and so does save(). The CLIENT_STOPPING hook (SessionLifecycle.onStopping) therefore writes nothing. The warning shown every five minutes promises "Kills are kept in memory and written as soon as the folder appears". If the cloud drive never mounts during the session, every kill, drop and PB from it is lost when the game closes, without a word.

  *Vorschlag:* On stop (and on disconnect) while awaitingStore && dirty, write the session record to a side file in the instance config dir (e.g. alpaka-stats.pending.json) with AtomicJsonFile. On the next successful load(), fold it in with the existing foldInto() and then delete it. Alternatively warn before exit. The pending file is the safe option.

  </details>
- **[medium · S]** Bei frischer Installation und nach Reset All im HUD Editor liegt der Slayer Boss Timer auf der Bear-Zeile des Slayer Session HUD, weil HudDefaults nur neun statt zehn Zeilen einplant, sodass sich in jedem Bosskampf Texte und geblurte Panels überlappen.  
  → SLAYER_SESSION_MAX_HEIGHT auf 99 setzen, den Abstand um Panel-Padding und Schatten vergrößern und per Test absichern, dass sich die Standard-HUDs nicht überschneiden.  
  `client/hud/HudDefaults.java:21` · D5-1
  <details><summary>Details</summary>

  **Default boss timer sits inside the session HUD: max height assumes 9 rows, the HUD has 10**

  SLAYER_SESSION_MAX_HEIGHT = 89 is documented as 'nine rows of ten pixels', and SLAYER_TIMER_Y = 60 + 89 + 6 = 155 is derived from it. But SlayerHudRenderer.buildRows can show ten rows: Title, Total XP, Session XP, XP/hr, Bosses, Avg Boss, Bosses/hr, Session, Since <drop>, Bear. All ten are on by default (slayerHudShowBear = true, slayerHudBearBlazeOnly = false), and every real SlayerType has a non-null rngDropItem. So the default session HUD is (10-1)*10+9 = 99 px tall and covers y 60..159. During a boss fight on a fresh install, or after the editor's Reset All, the timer line 'Boss 12.3s' at y 155..164 is drawn over the 'Bear ...' row. The blurred panels on this branch make it worse: each panel adds PAD_Y = 3 plus a 4 px shadow, so the two panels overlap by about 10 px. Even the intended nine-row layout leaves the two panels touching (6 px gap against 3 + 3 of padding). This contradicts the class comment that no two of the mod's HUDs overlap.

  *Vorschlag:* Make SLAYER_SESSION_MAX_HEIGHT 99 ('ten rows') and widen the gap to cover both panels' padding and shadow (e.g. + 2*HudPanel.PAD_Y + 4, about 10-12 px). Better still, derive the row count from the number of row toggles so the next added row cannot silently reintroduce the overlap, and add a unit test that the default boxes do not intersect.

  </details>
- **[medium · S]** Liegt die Taste für das Quick Command Menu auf einer Maustaste (z. B. einer Seitentaste), öffnet sich das Rad zwar, aber das Loslassen führt den gewählten Befehl nicht aus und das Rad bleibt offen, bis man links klickt oder Escape drückt.  
  → mouseReleased überschreiben und bei COMMAND_WHEEL_KEY.matchesMouse(event) runSelectedAndClose() aufrufen.  
  `features/wheel/CommandWheelScreen.kt:92` · C5-4
  <details><summary>Details</summary>

  **Hold-and-release does nothing when the wheel key is a mouse button**

  The wheel opens on `key.isDown` (CommandWheelFeature), which also works for mouse bindings. Mouse side buttons are a common choice for a wheel. Only keyReleased(KeyEvent) runs the selection, though, and mouseReleased is not overridden. Releasing a mouse-bound wheel key reaches Screen.mouseReleased and is ignored, so the wheel stays open until the player left-clicks or presses Escape. The class doc promises "Releasing the wheel key or clicking runs the command". Pressing the bound button again inside the wheel also does nothing (mouseClicked only handles buttons 0 and 1).

  *Vorschlag:* Override mouseReleased: `val key = CommandWheelFeature.COMMAND_WHEEL_KEY; if (key != null && key.matchesMouse(event)) { runSelectedAndClose(); return true }`. KeyMapping.matchesMouse(MouseButtonEvent) exists in 26.2.

  </details>
- **[medium · S]** Löscht man im Editor des Quick Command Menu den letzten Befehl, tauchen sofort wieder die sechs Standardbefehle (/hub, /island, /warp dh, /wardrobe, /pets, /pv) auf, sodass sich das Rad nicht leeren lässt und der Hinweis „No quick commands yet“ nie erscheint.  
  → Altliste bzw. Standardbefehle nur einfügen, wenn commandWheelPages null ist, ein geleertes Rad stehen lassen und die Standardliste nur an einer Stelle definieren.  
  `features/wheel/CommandWheelPages.kt:27` · C5-2, D3-4, X1-20
  <details><summary>Details</summary>

  **Removing the last quick command restores the six defaults; the wheel's empty state is unreachable**

  remove() → pruneEmpty() leaves `[[]]` and saves. The next pages() call sees `pages.all { it.isEmpty() }` and refills from the legacy list or DEFAULTS, then saves again. The editor (CommandWheelConfigScreen.extractBackground) calls pages() every frame, so the moment a player deletes their last command, /hub, /island, /warp dh, /wardrobe, /pets and /pv reappear. A player who removes the defaults one by one gets all six back on the last click. As a result CommandWheelScreen.drawEmptyState ("No quick commands yet") is dead code. The "migration done" marker `commandWheelCommands = null` also does not survive a restart: Gson omits nulls, so the field initialiser's six-command list is back on the next load.

  *Vorschlag:* Treat 'pages == null' (never migrated) and 'all pages empty' differently: only fold the legacy or default list in when commandWheelPages is null. Let an empty list of pages stand, and drop the `if (pages.isEmpty()) pages.add(ArrayList())` re-seeding, or keep it and treat `[[]]` as a valid empty wheel. Then the empty state can show.

  </details>

## Sollte für 2.0 behoben werden


### Release, Build, Doku & Tests (12)

- **[medium · S]** Der Fair-Play-Abschnitt der README ('What it contacts') nennt nur die Hypixel-Mayor-API und den Server-Ping und verschweigt den standardmäßig aktiven GitHub-Abruf von Update Notifications, obwohl Spieler genau diesen Absatz vor der Installation lesen.  
  → Den Update-Check (einmal pro Start an api.github.com, abschaltbar über Update Notifications) im Absatz ergänzen und 'Neither one' in 'None of these' ändern.  
  `README.md:130` · X2-4
  <details><summary>Details</summary>

  **'What it contacts' omits the GitHub update check, which is on by default**

  The Fair Play section lists exactly two network uses: the Hypixel mayor API and the main menu server ping. UpdateChecker.kt also sends a GET to https://api.github.com/repos/Alpaka05/AlpakaAddons/releases once per session whenever allowApiCalls and updateNotificationsEnabled are both on, and both default to true. The config's own description of Allow Network Features mentions 'the update check', so the README is the one stale disclosure, and it is the one players read before installing.

  *Vorschlag:* Add the update check to the paragraph, for example: '...and checks GitHub once per start for a newer release (Update Notifications in General).' Keep 'Neither one sends anything about you' accurate by saying 'None of these'.

  </details>
- **[medium · S]** Die Version kommt allein aus dem nächsten numerischen Git-Tag, der auf mc-26.1.2 nicht erreichbar ist, sodass der 26.1.2-Jar zu 2.0 als 1.2.22+76.0440626 o. Ä. gebaut würde und so in Mod Menu steht; ein Tag wie 2.0.0-mc26.1.2 wäre eine Semver-Vorabversion und schaltet über UpdateChecker.parse den Update-Check dieses Builds stillschweigend ab.  
  → Für Release-Builds ein -Pmod_version=2.0.0-Override von gitVersion() oder Tags der Form *-mc${minecraft_version} (Suffix abgeschnitten) unterstützen und das im Release-Ablauf dokumentieren.  
  `build.gradle:22` · X2-9
  <details><summary>Details</summary>

  **Tag-based version cannot produce a clean release version for the 26.1.2 line**

  A release is 'a tag, and nothing else', and a tag names one commit. Releases are cut for main (26.2) and for the mc-26.1.2 backport branch. Tag 1.3.0 is not reachable from mc-26.1.2: `git describe mc-26.1.2` gives 1.2.22-76-g0440626, so that branch builds as 1.2.22+76.0440626. The 1.3.0 release accordingly only has alpaka-1.3.0-mc26.2.jar, while 1.1.75 still shipped both. For 2.0, the 26.1.2 jar would be versioned 2.0.0+N.hash, or older, and show that in Mod Menu. The obvious workaround, a tag like 2.0.0-mc26.1.2, makes the version a semver pre-release ranked below 2.0.0. UpdateChecker.parse also rejects it ("0-mc26" is not an Int), which silently turns update checks off for that build.

  *Vorschlag:* Support per-line tags explicitly. For example, first try `git describe --match "*-mc${minecraft_version}"` and strip that suffix from the version, then fall back to plain numeric tags. Alternatively, allow `-Pmod_version=2.0.0` to override gitVersion() for release builds of the backport. Document whichever you choose next to the release recipe.

  </details>
- **[low · S]** Die Dev-Screenshot-Tour (DevScreenshotTour) steckt im Release-Jar und springt bei jedem Spieler allein über die Umgebungsvariable ALPAKA_SCREENSHOT_TOUR an: Schon ALPAKA_SCREENSHOT_TOUR=0 beendet das Spiel nach etwa 6 s, ein passender Wert löscht saves/alpaka-tour, legt eine Welt an und verstellt Einstellungen.  
  → register() zusätzlich an FabricLoader.getInstance().isDevelopmentEnvironment() koppeln und "", "0" und "false" als aus werten.  
  `client/DevScreenshotTour.java:167` · D3-3, X2-21
  <details><summary>Details</summary>

  **Screenshot tour is switched on by an environment variable alone, not limited to dev, and "=0" quits the game**

  register() runs in every player's onInitializeClient and only checks System.getenv("ALPAKA_SCREENSHOT_TOUR") != null. build.gradle does not exclude the class from the release jar. Any value turns the tour on. Because begin() now treats every value other than "1" as a filter (new on this branch), ALPAKA_SCREENSHOT_TOUR=0 or =false (someone trying to switch it off) matches no stops and calls mc.stop() about 6 s after the title screen. A value that does match deletes saves/alpaka-tour, creates and enters a world, sets options.pauseOnLostFocus=false, changes config fields in memory and then quits the game.

  *Vorschlag:* Return early unless FabricLoader.getInstance().isDevelopmentEnvironment() (runClient sets fabric.development) and the variable is set. Treat "", "0" and "false" as off. If a tour on a production jar is ever needed, require an explicit -Dalpaka.screenshotTour system property instead.

  </details>
- **[low · S]** gradle.properties committet deploy_target=modrinth und modrinth_profile=Fabric 26.2, sodass ein normales ./gradlew build bei jedem, der das Repo klont und ein gleichnamiges Modrinth-Profil hat, dort alpaka-Jars auf .disabled umbenennt, den neuen Jar hineinkopiert und ModrinthCleanup.py auf seine app.db loslässt.  
  → deploy_target=none committen, die eigenen Werte nach ~/.gradle/gradle.properties verschieben und den Building-Abschnitt der README auf ./gradlew build vereinfachen.  
  `gradle.properties:16` · X2-5
  <details><summary>Details</summary>

  **Committed default makes `./gradlew build` deploy into the builder's Modrinth App and edit its database**

  deploy_target=modrinth and modrinth_profile=Fabric 26.2 are committed, and `build` is finalizedBy deployJar. Anyone who clones the public repo and runs the usual `./gradlew build` on a machine with the Modrinth App and a profile of that common name gets several changes: every alpaka*.jar in that profile renamed to .disabled, the new jar copied in, and tools/ModrinthCleanup.py run against their app.db (see X2-6). The README has to tell people to add -Pdeploy_target=none to avoid this, which shows the default is the wrong way round for a public 2.0.

  *Vorschlag:* Commit deploy_target=none and no modrinth_profile. Move the maintainer's values to ~/.gradle/gradle.properties, which overrides the project file, and simplify the README's Building section to plain `./gradlew build`.

  </details>
- **[low · M]** Feature-Liste und Befehlstabelle der README stammen noch von vor 2.0: Menu Font, die Blur- und Smooth-Font-Schalter pro HUD, HUD Style, Starlight Effect, Custom Damage Tags, Fire Pit Highlight, Disable All Features, Reset All Settings u. a. fehlen, der Chatverlauf steht noch auf '5000 messages' statt des Sliders 500–20.000, die Tabs Co-op/Server/Mods fehlen und /alpakaslayer reset sowie /alpakastats stehen nicht in der Tabelle.  
  → About-Liste und Features-Abschnitte anhand von ConfigTree.LAYOUT aktualisieren, Chatverlauf- und Tab-Zeile korrigieren und die Befehlstabelle vervollständigen.  
  `README.md:18` · X2-13
  <details><summary>Details</summary>

  **README feature list and command table predate the 2.0 config**

  Missing from the README: Menu Font, with five typefaces and its use in chat, scoreboard and HUDs (the 'smooth font'); the per-HUD Blur and Smooth Font switches and HUD Style; Starlight Effect; Custom Damage Tags, which sits in the config's Cosmetics section while the README's Cosmetics lists only name tag, hat and scale; Fire Pit Highlight; Stop Blaze Spinning; Inventory Snowflakes; Update Notifications; Disable All Features; and Reset All Settings. Stale entries: line 54 says chat history 'keeps 5000 messages', but it is now a 500–20,000 slider. Line 50 lists tabs 'All, Party, Guild and PMs', but Co-op, Server and Mods tabs exist and are selectable. The Commands table omits `/alpakaslayer reset [pb [slayer]]`, which the Fair Play section names, and plain `/alpakastats`.

  *Vorschlag:* Before 2.0, refresh the About bullets and the Features sections against ConfigTree's LAYOUT: add a 'Design' bullet for the font and blur, and Custom Damage Tags under Cosmetics. Fix the chat history and tabs lines and complete the commands table.

  </details>
- **[low · S]** In de_de.json fehlt key.alpaka.chat_peek, sodass deutsche Spieler unter Optionen > Steuerung neben 'Befehlsrad' und 'Inventar-HUD' das englische 'Peek Chat' sehen; dazu steht in beiden Sprachdateien der nie gelesene Key category.alpaka.addons.  
  → key.alpaka.chat_peek in de_de.json übersetzen (z. B. 'Chat-Vorschau') und category.alpaka.addons aus beiden Dateien löschen.  
  `res/assets/alpaka/lang/de_de.json:4` · X2-15
  <details><summary>Details</summary>

  **de_de lacks key.alpaka.chat_peek; both files carry an unused category key**

  en_us has four key names; de_de has three. In Options > Controls, German players see 'Befehlsrad' and 'Inventar-HUD' next to the English fallback 'Peek Chat'. Both files also define 'category.alpaka.addons', which nothing uses. KeyMapping.Category builds its label from Identifier.toLanguageKey("key.category") (checked with javap), so only 'key.category.alpaka.addons' is ever looked up.

  *Vorschlag:* Add "key.alpaka.chat_peek": "Chat-Vorschau" (or similar) to de_de.json, remove "category.alpaka.addons" from both files, and cover key parity in the test proposed in X2-11.

  </details>
- **[low · S]** alpaka.mixins.json verweist auf eine alpaka.refmap.json, die beim unobfuskierten 26.2 nie erzeugt wird, sodass jedes Spieler-Log beim Start ein WARN 'Reference map ... could not be read' enthält, das in Bug-Reports wie ein kaputter Jar aussieht.  
  → Den refmap-Eintrag aus alpaka.mixins.json entfernen.  
  `res/alpaka.mixins.json:5` · X2-16
  <details><summary>Details</summary>

  **Mixin config names a refmap that is never generated, so every player's log gets a WARN**

  Minecraft 26.2 is unobfuscated, so Loom writes no alpaka.refmap.json; the built jar contains none. Mixin therefore logs at startup, in production too: "[main/WARN] Reference map 'alpaka.refmap.json' for alpaka.mixins.json could not be read. If this is a development environment you can ignore this message". This is visible in run/logs/latest.log. Players attaching logs to bug reports see a warning that looks like a broken jar.

  *Vorschlag:* Remove the "refmap" entry from alpaka.mixins.json.

  </details>
- **[low · S]** SkyHanniTabListRendererMixin ist ohne Bedingung eingetragen, sodass jeder Spieler ohne SkyHanni bei jedem Start ein WARN mit ClassNotFoundException im Log hat, das in Support-Threads wie ein Fehler aussieht.  
  → Ein IMixinConfigPlugin einhängen, dessen shouldApplyMixin für diesen Mixin FabricLoader.getInstance().isModLoaded("skyhanni") prüft, oder den Mixin in eine eigene optionale Mixin-Config auslagern.  
  `res/alpaka.mixins.json:46` · X2-17
  <details><summary>Details</summary>

  **SkyHanni pseudo-mixin logs a ClassNotFoundException WARN for everyone without SkyHanni**

  SkyHanniTabListRendererMixin is listed unconditionally. When SkyHanni is absent, Mixin logs "[main/WARN] Error loading class: at/hannibal2/skyhanni/features/misc/compacttablist/TabListRenderer (java.lang.ClassNotFoundException ...)" on every start, as seen in latest.log. That looks like an error in user logs and support threads.

  *Vorschlag:* Add an IMixinConfigPlugin whose shouldApplyMixin returns FabricLoader.getInstance().isModLoaded("skyhanni") for this mixin, set via "plugin" in alpaka.mixins.json. Alternatively, move it to a separate optional mixin config.

  </details>
- **[low · S]** Der Release-Jar enthält weder LICENSE noch CREDITS.md, obwohl die MIT-Lizenz den Hinweis in jeder Kopie verlangt und in CREDITS künftig auch die Sound-Quellen stehen sollen.  
  → In build.gradle beides per jar { from("LICENSE") { rename { "${it}_alpaka" } }; from("CREDITS.md") } mit ins Jar packen.  
  `build.gradle:48` · X2-20
  <details><summary>Details</summary>

  **Release jar ships neither LICENSE nor CREDITS**

  The MIT licence requires the copyright and permission notice 'in all copies or substantial portions of the Software', and the distributed jar is such a copy. The jar contains the five OFL font licences but not the mod's own LICENSE, or CREDITS.md once the sound sources are filled in. The Fabric example mod adds the licence with `jar { from("LICENSE") }`.

  *Vorschlag:* Add the licence to the jar, e.g. `tasks.named("jar") { from("LICENSE") { rename { "${it}_alpaka" } }; from("CREDITS.md") }`.

  </details>
- **[low · M]** Das neue Config-Menü hängt an rund 100 Options-IDs und Header-Titeln in MASTERS, PARENTS, HEADER_FEATURES und LAYOUT, ohne jeden Test, sodass ein Tippfehler oder eine umbenannte ID ein Feature stillschweigend unter 'Other' einsortiert, einen Blur-Schalter ins falsche Panel hängt oder ein Feature bei doppeltem Header-Titel ganz verschwinden lässt (heute löst alles korrekt auf).  
  → Einen Konsistenztest ergänzen, der prüft, dass alle IDs existieren, PARENTS-Ziele vor ihren Kindern registriert sind, HEADER_FEATURES-Keys echte Header sind und groups() keine 'Other'-Sektion liefert, notfalls per Quelltext-Parsing wie in OutboundSourceScanTest.  
  `client/gui/ConfigTree.java:91` · X2-10, D1-10, X1-33
  <details><summary>Details</summary>

  **Redesigned config tree is keyed by ~100 option-id strings with no test**

  MASTERS, PARENTS, HEADER_FEATURES and LAYOUT refer to options by string id or header title. A typo or a renamed id never fails. A LAYOUT id that resolves to nothing is skipped (`if (feature == null ...) continue`), and the feature ends up in an 'Other' section. A PARENTS entry whose parent is unknown falls through and attaches the setting to whatever feature was registered before it, so a per-HUD Blur switch lands in the wrong panel. I simulated build() against the current registry and all ids resolve and nothing lands in 'Other' today. With 278 options and per-HUD style switches moving around in this branch, the 2.0 centrepiece has no regression guard.

  *Vorschlag:* Add a test, in the style of ChatBlurFeatureTest's file check, that asserts several things: every MASTERS/PARENTS/LAYOUT id exists in the registry, every PARENTS target is built before its child, every HEADER_FEATURES key is a registered header, and groups() produces no 'Other' section. If the registry cannot be loaded off-game (AlpakaConfig's static FILE calls FabricLoader), parse the ids from the sources, as OutboundSourceScanTest does.

  </details>
- **[low · M]** Für die 2.0-Migrationen (Inter zu Nunito genau einmal, Gold zu Teal), Reset All Settings und Disable All Features gibt es keinen Test, obwohl schon ein Abdeckungstest über die ConfigTree-Master die Lücken aus D2-1 sofort gezeigt hätte.  
  → Die Migrationen in ein statisches migrate(AlpakaConfig) ziehen, FILE/BACKUP lazy machen und Migration, die erhaltenen Felder von resetToDefaults und 'alle Master aus' nach disableAllFeatures testen.  
  `config/AlpakaConfig.java:844` · D2-17
  <details><summary>Details</summary>

  **No tests for the 2.0 migrations, Reset All or Disable All**

  load()'s migrations (Inter to Nunito exactly once, so that choosing Inter again sticks; gold to teal), resetToDefaults' list of what it keeps, and disableAllFeatures' coverage are pure logic, but none has a test. The drift in D2-1 and D2-2 would have been caught by one. They are hard to test today because the class's static initializer calls FabricLoader. src/test has no AlpakaConfig test, only nested SlayerData use.

  *Vorschlag:* Move the migrations into a static migrate(AlpakaConfig) and make FILE and BACKUP lazy, so new AlpakaConfig() works in JUnit. Test: menuFont 1 becomes 5 with the flag set, and a second migrate keeps 1; resetToDefaults keeps presets, statsDirectory and lastSeenVersion; every boolean ConfigTree master is false after disableAllFeatures.

  </details>
- **[low · S]** OutboundSourceScanTest, auf den sich die README mit 'a test fails the build if anything else tries to send one' beruft, erkennt getConnection().send(, connection?.send(, new Serverbound...Packet( und handleChatInput( nicht und lässt jeden neuen Aufrufer von Outbound.command mit selbst erklärter Cause durch, also genau den timergesteuerten Fall, den er verhindern soll.  
  → Die Aufrufer von Outbound.command( auf eine geprüfte Liste festnageln und das SEND-Muster um die genannten Aufrufformen erweitern.  
  `src/test/java/net/alpaka/addons/compliance/OutboundSourceScanTest.java:24` · X2-3
  <details><summary>Details</summary>

  **Compliance scan misses common send call shapes and does not pin Outbound's callers**

  The README promises that 'a test fails the build if anything else tries to send one'. The SEND regex only matches '.sendCommand(' / '.sendChat(' / ... and the literal 'connection.send('. Checked with the same pattern, none of these match: `Minecraft.getInstance().getConnection().send(p)`, Kotlin `mc.connection?.send(p)` and `connection!!.send(p)`, `new ServerboundChatCommandPacket(...)`, and calling `chatScreen.handleChatInput("/p accept", false)` directly. Calls like `gameMode.handleContainerInput(...)` (GUI auto-clicking) or `useItem` / `attack` are not guarded either, although the README promises 'no macros, no automation'. Most importantly, ALLOWED only lists Outbound.java itself. Any new file that calls `Outbound.command(..., Cause.KEY_PRESS)` from a tick handler or timer passes the test silently. That is exactly the class of bug (timer-driven guild announcement) the test's comment says it exists to stop.

  *Vorschlag:* Add a second scan that pins the callers of `Outbound.command(` (today ChatScreenMixin.java, PartyInviteFeature.java, CommandWheelScreen.kt), so a new caller needs a reviewed list entry. Widen SEND to `(?:get)?[cC]onnection\(?\)?\s*[?!]*\.send\s*\(`, `Serverbound\w*Packet\s*\(`, `\.handleChatInput\s*\(`, and `gameMode\W+\.(?:handleContainerInput|useItem|useItemOn|attack|startDestroyBlock|handleInventoryButtonClick)\s*\(`. Allow only the mixin that injects into handleChatInput.

  </details>

### Toter Code, Duplikate & Konsistenz (4)

- **[low · S]** Die Beschreibung von 'Blur Strength' verspricht, dass auch das Command Wheel unscharf hinterlegt wird. Kein Aufrufer baut WheelMesh aber noch mit blur = true, der Text führt Spieler also in die Irre, und der Blur-Pfad in WheelMesh ist toter Code.  
  → Das Command Wheel aus der Beschreibung und aus dem Javadoc von chatBlurStrength streichen und den Blur-Modus samt Parametern aus WheelMesh entfernen.  
  `client/gui/AlpakaConfigRegistry.java:120` · X1-21, C5-11
  <details><summary>Details</summary>

  **Blur Strength claims to affect the command wheel, which no longer blurs**

  The description says '... every blurred panel, the command wheel's included', and AlpakaConfig.chatBlurStrength's javadoc (line 453) says the wheel shares the chat's blurred copy. No caller constructs WheelMesh with blur = true, so WheelMesh's blur/blurTint parameters and Element's blur pipeline branch are dead.

  *Vorschlag:* Drop the command wheel from both texts and remove WheelMesh's blur mode, or wire it back up.

  </details>
- **[low · M]** Vom alten Karten-Config-Modell sind viele wirkungslose Reste übrig: buttonOnly() wird gesetzt, aber nie gelesen (der Javadoc verspricht einen Klickschutz, den es nicht mehr gibt), und Action-Labels wie 'Choose Color' oder 'Open HUD Editor' werden nie gezeichnet. Ungenutzt sind außerdem expanded/hoverProgress, Such-Cache und countOptions in der Registry sowie der 4-Argument-Konstruktor von ColorPickerScreen.  
  → Die toten Member und die .buttonOnly()-Aufrufe entfernen, den actionLabel-Parameter streichen und die veralteten Javadocs (Karten, Buttons, 'several times a frame') ans neue Menü anpassen.  
  `client/gui/ConfigOption.java:354` · X1-10, D1-14, D2-9, D2-21, D3-19
  <details><summary>Details</summary>

  **Leftovers of the old config model: settings that are never read, and misleading docs**

  buttonOnly()/isButtonOnly() are called 6 times in the registry and never read; the new screen makes the whole row the hit, so the javadoc's promised protection for 'Load Preset' no longer exists. Every actionLabel ('Open HUD Editor', 'Choose Color', 'Load P1', ...) and getActionLabel() are never drawn. Also unused: expanded/isExpanded/toggleExpanded, hoverProgress/updateHoverProgress, getMinVal/getMaxVal and matches(String). ConfigCategory description, heading and getHeading() are unused. In AlpakaConfigRegistry, the search-cache branch of getOptions and countOptions() are unused, since ConfigTree only calls getOptions(cat, ""). ConfigTree.Feature.isOn() and the 4-argument ColorPickerScreen constructor have no callers either.

  *Vorschlag:* Remove them, and drop the actionLabel constructor parameter (or render it).

  </details>
- **[low · S]** toLowerCase() ohne Locale bricht auf türkischen oder aserbaidschanischen Systemen, weil 'I' dort zu 'ı' wird. Betroffen sind die SkyBlock-Erkennung (bei groß geschriebener Server-IP sind alle SkyBlock-Features aus), die Rift-Erkennung, der INSANE-Drop-Sound, '/alpakaslayer inferno' und die Config-Suche, die z. B. 'Inventory HUD' nicht findet.  
  → Überall toLowerCase(Locale.ROOT) verwenden (SkyblockUtils, SlayerDropTracker, SlayerType, ConfigOption, ConfigTree).  
  `utils/SkyblockUtils.java:78` · C6-13, C2-8, D1-13
  <details><summary>Details</summary>

  **Locale-sensitive toLowerCase() in Skyblock detection**

  `serverData.ip.toLowerCase()` (line 78) and the sidebar title `.toLowerCase()` (line 88) use the default locale. On a Turkish or Azerbaijani system, "MC.HYPIXEL.NET" becomes "mc.hypıxel.net" (dotless ı), so the hypixel.net check fails and every Skyblock-gated feature switches off. A title "RIFT" becomes "rıft", so the Rift title check fails too; the Rift's area line uses a different marker from ⏣, so the fallback does not catch it. Elsewhere the code base already uses Locale.ROOT (e.g. AlpakaClient.setStatsDirectory).

  *Vorschlag:* Use toLowerCase(Locale.ROOT) in both places, and in SlayerDropTracker's duplicate rift check.

  </details>
- **[low · S]** Mehrere String.format-Aufrufe ohne Locale.ROOT (Session-Uhr im Slayer HUD, World-Age-Joinnachricht, Slider-Formatter wie '%d ms', Spielerzahl im Hauptmenü) zeigen auf ar- oder fa-Systemen arabisch-indische Ziffern neben ASCII-Ziffern. Auf fr_FR wird die Spielerzahl mit schmalem geschützten Leerzeichen statt mit Punkt gruppiert.  
  → Überall Locale.ROOT übergeben oder die Ganzzahl-Strings per Konkatenation bauen, und die Spielerzahl nur bei Änderung neu formatieren.  
  `features/slayer/SlayerHudRenderer.kt:410` · X1-32, D5-11, C5-8, D2-11
  <details><summary>Details</summary>

  **Some formatters use the default locale while others insist on Locale.ROOT**

  SlayerTimer.format explains why it uses Locale.ROOT: on a German machine the default renders the decimal separator wrong. formatDuration here, WorldAgeHudRenderer.formatTimeAgo, CustomSoundFeature volume and the registry formatters ('%d ms', '%dm %02ds') use String.format without a locale. That gives localized digits in locales such as ar-EG or fa.

  *Vorschlag:* Pass Locale.ROOT consistently, or use string templates for integer-only formats.

  </details>

### Veraltete Kommentare & Javadoc (3)

- **[low · S]** Die Compliance-Doku von Highlight Pangolins behauptet, die Sichtbarkeit werde mit LocalPlayer.hasLineOfSight geprüft und nur ein paar Mal pro Sekunde neu berechnet, tatsächlich laufen drei eigene OUTLINE-Strahlen (2 von 3 müssen frei sein) bis zu 20-mal pro Sekunde, und ausgerechnet die Doku, die die Hypixel-Regelkonformität begründet, stimmt damit nicht.  
  → Die Klassendoku auf die Drei-Strahlen-Prüfung umschreiben und den Kommentar zu VISIBILITY_TTL_MS auf 'einmal pro Tick (50 ms)' korrigieren.  
  `features/critters/PangolinHighlightFeature.kt:27` · C4-5
  <details><summary>Details</summary>

  **Compliance doc and TTL comment no longer match the visibility code**

  The class doc (lines 27-29) says every candidate is checked with LocalPlayer.hasLineOfSight. The code actually traces its own three ClipContext rays with Block.OUTLINE and needs 2 of 3 clear (clearSightlines, and isVisibleToPlayer's own doc says vanilla's check was dropped). The comment on VISIBILITY_TTL_MS (line 56) says the raycast reruns 'a couple of times a second per mob', but the value was lowered from 100 to 50 ms in 7a8b1b6, i.e. 20 times a second. This is the documentation that argues the feature stays within Hypixel's rules, so it should be exact.

  *Vorschlag:* Rewrite the bullet to describe the three-ray OUTLINE check, and the TTL comment to say once per tick (50 ms).

  </details>
- **[low · S]** Die Klassendokus von ConfigTree und AlpakaConfigScreen beschreiben noch die alte Struktur (Abschnitte als alte Header), obwohl Sidebar und Gruppen im neuen Menü aus LAYOUT kommen und Features pro Registry-ConfigCategory entstehen, und drawChevron verspricht glatte Striche, obwohl die WheelMesh-Feather nie angewendet wird.  
  → Die Dokus auf 'Registry-Kategorie' und LAYOUT-gesteuerte Abschnitte umschreiben und die Feather-Aussage streichen oder die Kanten wirklich weichzeichnen.  
  `client/gui/ConfigTree.java:15` · D1-16
  <details><summary>Details</summary>

  **Class docs describe the old structure**

  ConfigTree's doc says an option belongs to the feature before it 'in the same section' and that a section opening with a setting 'becomes one feature named after the section'. The code works per registry ConfigCategory and names that feature after category.getDisplayName(), while 'Section' in this class means a sidebar entry. AlpakaConfigScreen's doc (line 30) says the sidebar shows 'the sections - the old headers - grouped under their categories', but sections and groups now come from LAYOUT. drawChevron's doc promises 'smooth strokes', but the WheelMesh feather it computes is never applied by quad().

  *Vorschlag:* Reword the docs to say 'registry category' and to describe LAYOUT-driven sections. Drop the feather or anti-aliasing claim, or actually feather the strokes.

  </details>
- **[low · S]** Mehrere Kommentare in AlpakaConfigRegistry erklären Kategorie und Reihenfolge einer Option über den Tab, auf dem sie erscheint, im neuen Menü bestimmt aber ConfigTree.LAYOUT die Platzierung, sodass ein Maintainer, der eine Option zum Verschieben umkategorisiert, keine Wirkung sieht.  
  → Die Kommentare so umschreiben, dass Kategorie und Reihenfolge nur noch die Feature-Zuordnung (PARENTS/HEADER_FEATURES) bestimmen, und für die Anzeige auf ConfigTree.LAYOUT verweisen.  
  `client/gui/AlpakaConfigRegistry.java:42` · D2-10
  <details><summary>Details</summary>

  **Registry comments still describe tab placement that ConfigTree.LAYOUT now decides**

  Several comments explain an option's ConfigCategory or position by which tab it shows on. Line 42: 'The General tab is what opens first, so it holds only what a new player needs'. Line 89: menu font 'belongs on the tab that screen opens on'. Line 1847: the command list 'heads the tab rather than sitting below every sound option'. Line 604: about the Custom HUD tab. In the redesign, the sidebar comes from ConfigTree.LAYOUT. For example, command_wheel_custom_commands is listed under General > Main, custom sounds under General > Sounds, and clean_blaze under SkyBlock > Blaze, even though their categories here say otherwise. A maintainer who moves an option between categories to change where it appears will see no effect. Registry order now only matters for which feature a setting attaches to.

  *Vorschlag:* Rewrite these comments to say what category and order decide now: which feature a setting joins, and the PARENTS/HEADER_FEATURES lookups. Point to ConfigTree.LAYOUT for where things are shown.

  </details>

### Rendering, Schriften & Mixin-Kompatibilität (18)

- **[medium · S]** GameRendererMixin (isFirstPerson), LightningBoltMixin (setSkyFlashTime) und ItemInHandRendererMixin (mulPose) nutzen @Redirect; leitet ein weiterer Kamera-, Viewmodel- oder Anti-Flash-Mod denselben Aufruf um, startet das Spiel wegen defaultRequire 1 nicht.  
  → Auf die verkettbaren MixinExtras-Varianten umstellen: @ModifyExpressionValue für isFirstPerson, @WrapWithCondition bzw. @WrapOperation für setSkyFlashTime und mulPose.  
  `mixin/GameRendererMixin.java:14` · X1-14, C3-3, C4-9
  <details><summary>Details</summary>

  **@Redirect in three mixins invites hard conflicts with other mods**

  GameRendererMixin redirects CameraType.isFirstPerson in renderItemInHand. LightningBoltMixin (line 17) redirects Level.setSkyFlashTime. ItemInHandRendererMixin (line 58) redirects every PoseStack.mulPose in submitHandsWithItems. Only one @Redirect per call site can apply. A first-person, viewmodel or no-flash mod targeting the same call fails Mixin application, and with defaultRequire 1 the game fails to start. Everywhere else the code base already uses the composable @WrapOperation/@ModifyExpressionValue.

  *Vorschlag:* Use @ModifyExpressionValue for isFirstPerson and @WrapWithCondition for setSkyFlashTime and mulPose.

  </details>
- **[medium · S]** Swing Arc Pitch/Yaw/Roll und Swing Drift X/Y/Z ersetzen die Vanilla-Schwungbewegung auf allen Achsen, sobald ein Wert ungleich 0 ist, statt wie beschrieben etwas hinzuzufügen: Schon 5° bei Swing Arc Pitch machen aus dem 80°-Hieb ein kleines Nicken, und bei 0 springt alles zurück.  
  → Jede Achse auf den Vanilla-Wert aufaddieren (z. B. Pitch −80+arcX, Drift −0.4+X/100), sodass 0 pro Achse Vanilla bedeutet.  
  `mixin/ItemInHandRendererMixin.java:96` · C3-4
  <details><summary>Details</summary>

  **Swing Arc and Swing Drift sliders replace vanilla's motion on all axes instead of adding to it**

  overrideSwingArc cancels vanilla as soon as any arc value is non-zero, then rotates only by the slider values. Vanilla's yaw -20°, roll -20° and pitch -80° arc is dropped on all three axes. The descriptions say "Additional swing rotation angle around X axis". Example: set Swing Arc Pitch to 5° and the 80° chop collapses into a 5° nod; set it back to 0 and the full swing snaps back. Swing Drift (line 73) has the same all-or-nothing switch: setting only Drift X = 10 also zeroes vanilla's Y (0.2) and Z (-0.2) drift. Each slider is discontinuous at 0 and the axes are not independent.

  *Vorschlag:* Make each axis add to vanilla's constant: pitch -80+arcX, yaw -20+arcY, roll -20+arcZ, and drift -0.4+X/100, 0.2+Y/100, -0.2+Z/100. Then 0 means vanilla per axis and the inject can always run while the feature is on. Alternatively relabel the options as absolute and seed the sliders with vanilla's values.

  </details>
- **[medium · M]** Ab GUI-Skalierung 5 (Auto ergibt auf 1440p-Monitoren und Retina-MacBooks im Vollbild 6–8) wird die gewählte Menu Font bzw. Smooth Font aus dem 4x-Raster per NEAREST vergrößert, sodass Menüs, HUDs, Chat und Scoreboard ungleichmäßige, ausgefranste Striche zeigen.  
  → Zusätzliche _s5/_s6/_s8-Fontdefinitionen ausliefern (dabei nur die gewählte Schriftfamilie laden) oder die Grenze an der Menu-Font-Option dokumentieren.  
  `client/gui/GuiFont.java:82` · D4-12
  <details><summary>Details</summary>

  **GUI scales above 4 reuse the 4x raster, so the smooth font is magnified and soft on high-DPI screens**

  scaleSuffix() returns _s4 for every scale >= 4. The class doc says a glyph is only crisp when the oversample equals the GUI scale. Retina Macs on Auto pick high scales: a MacBook Air framebuffer of 2560x1600 gives scale 6, a 14" MBP gives 8. Every menu, HUD and the smooth chat or scoreboard text is then drawn from a 4x raster magnified 1.5-2x with linear filtering, which is the very softness the per-scale definitions were added to avoid.

  *Vorschlag:* Ship _s5/_s6 (and possibly _s8) definitions, or document the limit near the Menu Font option. If more definitions are added, consider loading only the selected family, because each definition opens its own FreeType face and its own copy of the TTF in native memory.

  </details>
- **[medium · S]** Beim Zoom lässt sich bis 50x scrollen, aber Camera.tickFov klemmt den FOV-Faktor bei 0.1, sodass sich das Bild ab etwa 10x nicht mehr ändert und man bis zu 9 wirkungslose Mausrad-Rasten beim Herauszoomen erst wieder zurückdrehen muss.  
  → MAX_ZOOM auf 10 senken (mit Kommentar zum Clamp in Camera.tickFov) oder den Zoom nach dem Clamp auf das endgültige FOV anwenden.  
  `mixin/AbstractClientPlayerMixin.java:18` · C4-2, C3-8
  <details><summary>Details</summary>

  **Zoom beyond ~10x is clamped away by Camera.tickFov, creating a dead scroll range**

  The zoom divides the value returned by getFieldOfViewModifier. In 26.2 the only caller is Camera.tickFov, which smooths the result and then clamps fovModifier to [0.1, 1.5] (Mth.clamp(fovModifier, 0.1f, 1.5f)). ZoomFeature lets zoomFactor climb to MAX_ZOOM = 50 (x1.2 per scroll notch). Above roughly 10x (11.5x while sprinting), further notches change nothing on screen but still raise zoomFactor. Scenario: zoom in with C and scroll up about 14 notches to 50x. The picture stops changing after about 5 notches. Scrolling back out then needs about 9 notches before the view moves at all.

  *Vorschlag:* Cap the effective factor at what vanilla will accept, e.g. `info.setReturnValue((float) Math.max(0.1, originalFov / ZoomFeature.getZoomFactor()))` and also lower ZoomFeature.MAX_ZOOM to 10, so scrolling stops where the image stops. Alternatively apply the zoom after the clamp, for example on the camera render state's FOV, if a stronger zoom is wanted.

  </details>
- **[medium · M]** FontTextureMixin wirkt nie auf GUI-Text, weil 26.2 jedes GUI-Glyph mit festem NEAREST-Sampler zeichnet, sodass die in drei Doku-Stellen beschriebene lineare Glättung nie stattfindet und Schrift überall pixelig wird, wo sie nicht 1:1 abgebildet ist (skalierte HUDs, Chat-Skalierung, HUD-Editor-Vorschau, GUI-Scale 1 oder ab 5, die Linien-Icons im Hauptmenü).  
  → Entweder den Mixin löschen und die Docs korrigieren oder in GlyphRenderState.textureSetup() für Alpaka-Fontseiten den LINEAR-Sampler einsetzen und dann den Atlas mit 1-Texel-Rand versehen.  
  `mixin/FontTextureMixin.java:34` · D4-M1, D4-10
  <details><summary>Details</summary>

  **FontTextureMixin never affects GUI text: 26.2 samples every GUI glyph with a hard-coded NEAREST sampler**

  The mixin sets AbstractTexture.sampler on alpaka font pages. In 26.2, GUI text never reads that field. GuiRenderer.prepareText -> GuiTextRenderState.ensurePrepared().visit(new GuiRenderer$1) -> acceptRenderable creates a GlyphRenderState for every glyph and effect. GlyphRenderState.textureSetup() returns TextureSetup.singleTextureWithLightmap(renderable.textureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)), and GuiRenderer's draw binds textureSetup.sampler0(). AbstractTexture.getSampler() is only consulted by RenderSetup (world render types), GuiGraphicsExtractor blits-by-id, and the sky, weather and world-border renderers. The mod draws no alpaka-font text in the world: no file that uses GuiFont submits world text. So the mixin is effectively dead. The GuiFont class doc (lines 49-50), the usesSmoothSampling Javadoc (158-166) and the mixin's own doc all describe linear sampling that never happens. Wherever glyphs are not mapped 1:1 they are nearest-resampled, which gives the uneven, 'pixelated in places' strokes the mixin was written to remove. That covers GUI scale 1 and >= 5 (see D4-12), HUDs with their own scale (for example 1.25), the chat-scale slider, the HUD editor's scaled previews, and the menu_icons font (40-texel glyphs drawn 10 GUI px tall, minified 2x at GUI scale 2 and 1.33x at scale 3, so the main-menu line icons are aliased).

  *Vorschlag:* Decide deliberately. Either delete FontTextureMixin and correct the three docs (relying on the per-scale definitions and accepting nearest elsewhere), or make it effective. To make it effective, mix into GlyphRenderState.textureSetup() (or wrap the getClampToEdge(NEAREST) call there) and return getClampToEdge(FilterMode.LINEAR) when renderable.textureView() belongs to an alpaka page. FontTextureMixin can record those views in a Set<GpuTextureView>, limited to non-coloured TrueType pages plus the menu_icons pages, so the vanilla pixel fallbacks stay crisp (the concern D4-1 raised). If linear is enabled, the gutter-less atlas concern from D4-11 becomes real and needs a 1-texel border.

  </details>
- **[low · S]** Etherwarp Overlay und „Ignore Plants“ beim Block Overlay prüfen auf BushBlock, das seit 26.x nur noch minecraft:bush ist, daher stoppt der Etherwarp-Strahl an Seerosen und angehängten Melonen- und Kürbisstielen (falsche goldene ‚gültig‘-Box), und Ignore Plants umrandet weiter tote Büsche, trockenes Gras, Seegras oder Beerensträucher.  
  → In beiden Prüfungen `instanceof VegetationBlock` statt `BushBlock` verwenden und die dadurch überflüssigen Einzelklassen streichen.  
  `features/etherwarp/EtherwarpOverlayFeature.java:311` · C3-1, C3-2
  <details><summary>Details</summary>

  **Etherwarp passable list relies on BushBlock, which on 26.2 is only minecraft:bush**

  Since 1.21.5 the old plant base class is VegetationBlock, and BushBlock is the new single `minecraft:bush` block (javap: `BushBlock extends VegetationBlock`). The list follows Odin's helper, where BushBlock was the base of every plant. Several VegetationBlock subclasses are not listed anywhere else, so the ray now stops on them: AttachedStemBlock (not a StemBlock), LilyPadBlock, SweetBerryBushBlock, NetherFungus/Roots/SproutsBlock, FlowerBedBlock (pink petals, wildflowers), LeafLitterBlock, AzaleaBlock, FireflyBushBlock and CactusFlowerBlock. Example: in a Garden melon or pumpkin plot, a ray across the plot stops on an attached stem. Air is above it, so the overlay draws a gold 'valid' box on the stem, while the server passes through and lands somewhere else. Lily pads on a pond do the same.

  *Vorschlag:* Replace `block instanceof BushBlock` with `block instanceof VegetationBlock`. That also covers FlowerBlock, TallGrassBlock, SaplingBlock, CropBlock, StemBlock, AttachedStemBlock, SeagrassBlock, MushroomBlock, NetherWartBlock, DoublePlantBlock and DryVegetationBlock, so those entries can go. Leave out any VegetationBlock the server really treats as solid (for example SeaPickleBlock), with a comment saying why.

  </details>
- **[low · S]** Mit Render Hand In Third Person und Smooth Perspective wird beim Gleiten aus der Frontansicht zurück in die Ego-Perspektive rund 350 ms lang die First-Person-Hand vor das eigene Gesicht gezeichnet, während die Kamera noch auf den Spieler schaut.  
  → Die Hand während des Übergangs ausblenden, solange die Kamera noch zum Spieler zeigt (z. B. |shownYaw| > 90° oder Start in THIRD_PERSON_FRONT).  
  `mixin/GameRendererMixin.java:24` · C3-7
  <details><summary>Details</summary>

  **With Render Hand In Third Person on, the hand floats in front of your own face during the front-view-to-first-person glide**

  hidesHand() returns false whenever renderHandInThirdPerson is on, and during the glide the camera type is already FIRST_PERSON, so the redirect returns true. The usual F5 cycle (back, front, first person) therefore ends each time with about 350 ms in which the first-person hand is drawn over a camera that is still in front of the player, looking back at their face. That is exactly the case line 27's comment says must not happen.

  *Vorschlag:* Hide the hand during a detached transition while the camera still faces the player, for example `isDetachedTransition() && (!renderHandInThirdPerson || Math.abs(shownYaw) > 90f)`, or remember the camera type the glide started from and hide it when that was THIRD_PERSON_FRONT.

  </details>
- **[low · S]** Der Etherwarp Overlay zeigt keine Box, wenn man genau entlang einer Achse schaut (Yaw 0 oder 90) und die Augenposition auf einer ganzzahligen Koordinate liegt, weil die Rasterverfolgung mit tDelta = 0 400-mal denselben Block prüft.  
  → Für Achsen ohne Richtungsanteil tMax und tDelta auf Double.POSITIVE_INFINITY setzen.  
  `features/etherwarp/EtherwarpOverlayFeature.java:251` · C3-9
  <details><summary>Details</summary>

  **Grid traversal gets stuck on an axis-aligned ray that starts on an integer coordinate**

  When dirX == 0, invX is Double.MAX_VALUE. If start.x is also exactly an integer, then tMaxX = |(floor(x) - x) * MAX_VALUE| = 0 and tDeltaX = |MAX_VALUE * 0| = 0. Every iteration picks the X axis (tMaxX <= the others), adds 0 to x and 0 to tMaxX, and re-tests the same voxel 400 times, then returns null, so no box is shown. dirX is exactly 0 at yaw 0 (Mth's sine table returns exactly 0), and dirZ at yaw 90, so it only takes an integer eye x (or z). This is rare, and the cause is easy to fix.

  *Vorschlag:* For an axis with zero direction, set tMax and tDelta to Double.POSITIVE_INFINITY, or skip that axis whenever step == 0, so it can never be chosen.

  </details>
- **[low · S]** Sobald Enable Viewmodel Modifiers an ist, dauert jeder Schwung fest 250 ms / Swing Speed, sodass 1.00x rund 17 % schneller als Vanilla (300 ms) ist und Haste und Mining Fatigue ignoriert werden.  
  → Die Basisdauer aus getCurrentSwingDuration() des Spielers (×50 ms) ableiten und erst dann durch Swing Speed teilen.  
  `mixin/ItemInHandRendererMixin.java:329` · C3-10
  <details><summary>Details</summary>

  **Swing Speed 1.00x is not vanilla speed and ignores Haste/Mining Fatigue**

  The custom swing clock always lasts 250 ms / itemSwingSpeed. Vanilla's swing is SwingAnimation.DEFAULT = 6 ticks (300 ms) and is shortened by Haste and lengthened by Mining Fatigue in LivingEntity.getCurrentSwingDuration. With the viewmodel feature on only to scale or offset the item, every swing is about 17% faster than vanilla and no longer follows Haste or Mining Fatigue (relevant on SkyBlock, where both are common). getCustomSwingProgress replaces the progress whenever the feature is on, even with all swing settings at their defaults.

  *Vorschlag:* Derive the base duration from the player's current swing duration (an @Invoker for getCurrentSwingDuration, times 50 ms), then divide by itemSwingSpeed, so 1.00x matches vanilla.

  </details>
- **[low · S]** Unter etwa 8 FPS (starker Lag in Dungeons) wird der Block Overlay mit Smooth Fade In komplett unsichtbar, weil jede Lücke über 120 ms die Einblendung neu startet.  
  → Ausgelassene Frames über einen Frame-Zähler statt über die Wanduhrzeit erkennen oder die Schwelle auf etwa 1 s anheben.  
  `features/blockoverlay/BlockOverlayFeature.java:64` · C3-14
  <details><summary>Details</summary>

  **Block overlay goes invisible below about 8 FPS when Smooth Fade In is on**

  The fade restarts whenever more than 120 ms of wall time pass between two submits. Below about 8.3 FPS (heavy lag in dungeons or on weak machines) every frame counts as a gap, so targetStartTime = now, elapsed = 0 and fadeInFactor = 0. The overlay and fill stay fully transparent for as long as the lag lasts, exactly when a clear target outline helps most.

  *Vorschlag:* Detect a missed submit with a frame counter (last submitted frame != this frame - 1) instead of wall time, or raise the threshold well above any realistic frame time (for example 1 s).

  </details>
- **[low · S]** Der shouldShowName-Hook in EntityRendererMixin greift in 26.2 nur für nicht-lebende Entities, daher ist das Ausblenden von Blaze-Namensschildern bei Clean Blaze toter Code, die Doku in CustomDamageTagFeature beschreibt den falschen Mechanismus, und jede Item- oder Display-Entity kostet pro Frame unnötige Prüfungen.  
  → Den Hook nach LivingEntityRenderer.shouldShowName verlegen oder ihn samt CleanBlazeFeature.shouldHideNameTag löschen und die Doku korrigieren.  
  `mixin/EntityRendererMixin.java:44` · C4-1
  <details><summary>Details</summary>

  **shouldShowName hook never runs for Blazes or armor stands, so the Blaze name-tag hiding does nothing**

  The hook injects into EntityRenderer.shouldShowName, the base implementation. In 26.2, LivingEntityRenderer.shouldShowName, MobRenderer.shouldShowName and ArmorStandRenderer.shouldShowName all override it (with erased bridges) and never call the base body; javap shows no invokespecial to EntityRenderer.shouldShowName. The hook therefore only runs for non-living entities (items, displays, minecarts), where neither CleanBlazeFeature.shouldHideNameTag (needs a Blaze or a 'Smoldering Blaze' name) nor DamageTagFeature.shouldHideNameTag (needs an ArmorStand) can ever return true. Concrete effects: (a) CleanBlazeFeature.shouldHideNameTag's `if (entity is Blaze) return true` (CleanBlazeFeature.kt:127) is dead, so a custom-named Blaze that the crosshair is on still shows its vanilla name even with 'Name tags (health included)' on. (b) CustomDamageTagFeature's class doc (line 28) says the splash's name tag is hidden by shouldHideNameTag, but it is actually the shouldRender hook that hides the whole stand. (c) Every non-living entity still pays for two config checks, an isOnSkyblock call and two AlpakaPerf timings per frame for nothing. AvatarRendererMixin's own comment (lines 84-87) already documents this exact pitfall for players.

  *Vorschlag:* Either move the injection to LivingEntityRenderer.shouldShowName(LivingEntity, double) at HEAD, which is where the Blaze and armor-stand decisions are actually made (then retest the Clean Blaze name option), or delete the hook and CleanBlazeFeature.shouldHideNameTag, and rename DamageTagFeature.shouldHideNameTag to something like shouldHideSplash, since it hides the whole stand through shouldRender. Fix the CustomDamageTagFeature doc reference either way.

  </details>
- **[low · S]** parseDamageTag entfernt Sternchen und Leerzeichen, isNonCritDamageTag nicht, obwohl das Javadoc gleiche Regeln verspricht, sodass ein Nicht-Krit-Treffer wie ‚1,234*‘ bei Only Show Crit Damage sichtbar bleibt, aber verschwindet, sobald Custom Damage Tags an ist.  
  → isNonCritDamageTag aus parseDamageTag ableiten, das Javadoc korrigieren und Tests mit Sternchen- und Krit-Fällen ergänzen.  
  `features/damagetags/DamageTagFeature.java:48` · C4-3
  <details><summary>Details</summary>

  **parseDamageTag and isNonCritDamageTag use different rules despite the doc saying they are the same**

  parseDamageTag strips `[✧✦*\s]` before matching DAMAGE_NUMBER (line 56). isNonCritDamageTag matches the cleaned name directly (line 84), so any asterisk or inner whitespace makes it fail. The Javadoc on line 48 says parseDamageTag follows 'the same rules as isNonCritDamageTag'. Scenario: a non-crit splash carrying an asterisk, e.g. '§71,234*'. With Only Show Crit Damage on and Custom Damage Tags off it stays visible. Switching Custom Damage Tags on makes the same stand count as a splash and hides it. Two copies of the parsing rules will keep drifting apart.

  *Vorschlag:* Derive one from the other: `isNonCritDamageTag(name) == (parseDamageTag(name) is a Splash with !crit)`, after deciding whether asterisks and spaces count as decoration on the Only-Crit path too. Then fix the Javadoc and extend DamageTagFeatureTest with asterisk and crit cases.

  </details>
- **[low · S]** Bei Custom Damage Tags mit dem kompakten Number Format erscheinen Werte knapp unter einer Million bzw. Milliarde als ‚1000k‘ bzw. ‚1000M‘ statt ‚1M‘ bzw. ‚1B‘, weil die Einheit vor dem Runden gewählt wird.  
  → Nach dem Runden eine Stufe höher gehen, wenn der Wert 1000 erreicht, und compact() per Unit-Test absichern.  
  `features/damagetags/CustomDamageTagFeature.kt:294` · C4-4
  <details><summary>Details</summary>

  **Compact format shows '1000k' / '1000M' at bucket boundaries**

  compact() picks the suffix before rounding. For 999,999 it picks k: 999.999 >= 100 gives "%.0f", which yields "1000", so the output is "1000k" instead of "1M". The same happens for 999,500-999,999 and for 999.5M-999.99M ("1000M"). The lower boundaries are fine, because 9.999 rounds to 10.00 and is trimmed to "10k". On SkyBlock, hits just under a million or a billion are common enough to see.

  *Vorschlag:* After formatting, if the rounded value reaches 1000 and a larger suffix exists, step up one bucket (or choose the bucket from the rounded value). Add a unit test for compact()/format(), which are pure.

  </details>
- **[low · M]** Der Sensei Hat wird bei SkyBlock-Helmen aus Spielerköpfen (die meisten Rüstungssets) wie auf einem nackten Kopf platziert, sodass die 1,1875-fach skalierten Kopfecken in F5 etwa 1–1,6 px durch die Krone stechen.  
  → Getragene Köpfe über state.wornHeadType erkennen und ihnen einen eigenen, höheren und breiteren Sitz geben.  
  `features/cosmetics/ChromaHatLayer.kt:36` · C4-7
  <details><summary>Details</summary>

  **Sensei hat seat ignores skull helmets, which poke through the crown on SkyBlock**

  Any head item counts as 'wearingHelmet', and the hat is then seated for an armour helmet cube (HELMET_TOP -9/16, half width 5/16). Most SkyBlock helmets are player-head skulls, which CustomHeadLayer draws at 1.1875x: the skull reaches about 9.5 px above the neck, and its 0.25-inflated hat layer about 9.8 px, with a half width of about 5.05 px. The crown is lowered until it touches the 9 px helmet corners, so a skull's top corners stick out about 0.5-0.8 px through the translucent hat. Visible in F5 with most SkyBlock armour sets.

  *Vorschlag:* Distinguish skull/head items (state.wornHeadType or the item's SkullBlock) from armour, and use a third seat (top about -9.8/16, half width about 5.05/16) for them.

  </details>
- **[low · S]** ChatComponentMixin injiziert über den Namen in das synthetische Lambda `lambda$extractRenderState$1`; ändert ein 26.2.x-Hotfix die Lambda-Nummerierung, wird der rein kosmetische Chat-Blur-Hook wegen defaultRequire 1 zum Absturz beim Start.  
  → Den Zeilenhintergrund über einen benannten Aufruf (@WrapOperation mit @Expression) treffen oder kosmetische Hooks mit require = 0 und einmaligem Log absichern.  
  `mixin/ChatComponentMixin.java:499` · X1-15
  <details><summary>Details</summary>

  **Fragile injection into a synthetic lambda while every injection is required**

  @Inject(method = "lambda$extractRenderState$1") depends on javac's lambda numbering, which shifts whenever Mojang adds or reorders a lambda in ChatComponent. With required:true and defaultRequire 1, that turns a cosmetic blur hook into a startup crash on the next minor update. The neighbouring @ModifyArgs (line 432) is a similar maintenance and perf hazard (see perf).

  *Vorschlag:* Reach the line-box draw through a named call, e.g. @WrapOperation on the fill inside it via MixinExtras @Expression. Alternatively give purely cosmetic hooks require = 0 and log once when they did not apply.

  </details>
- **[low · M]** Der Starlight-Effekt springt alle 20 Minuten Spielzeit und bei jedem Lobby-Wechsel sichtbar, weil GameTime pro Ingame-Tag von 1 auf 0 zurückfällt und Nebel-Drift und Funkel-Phasen mitspringen.  
  → Die Animation exakt periodisch über 1200 s machen (Drift auf geschlossener Bahn, Funkelfrequenzen als Vielfache von 2π/1200) oder eine eigene, nicht umlaufende Zeit an den Shader geben.  
  `res/assets/alpaka/shaders/core/starlight.fsh:94` · X2-25, C3-11
  <details><summary>Details</summary>

  **Starlight nebula and twinkle jump every 20 minutes because GameTime wraps**

  The comment treats GameTime as a clock ('this makes t count seconds'). Vanilla fills it with ((gameTime % 24000) + partialTick) / 24000, checked in GlobalSettingsUniform bytecode (lrem 24000, fdiv 24000f). So t falls from 1200 back to 0 once per in-game day. At that moment the gas drift offset vec3(t*0.006, t*0.004, -t*0.005) snaps by (7.2, 4.8, -6.0) noise units, and every star's twinkle phase jumps. A held item with Starlight visibly pops once every 20 minutes of play, and on every lobby switch, since each server has its own game time.

  *Vorschlag:* Make the motion periodic over exactly 1200 s, e.g. drive the drift with sin/cos of 2π·GameTime and use twinkle frequencies that are whole multiples of 2π/1200. Alternatively, pass a continuous time from the CPU through the vertex data. At minimum, fix the comment.

  </details>
- **[low · S]** GuiGraphicsExtractorMixin führt einen eigenen Scissor-Stack, der anders als Vanillas (und anders als der Kommentar behauptet) nicht auf den Bildschirm beschnitten wird, weshalb abgerundete und geblurte Elemente Off-Screen-Scissors tragen, GuiRendererMixin nur den linken Rand abfängt und das Konfigurationsmenü bei einer GUI-Höhe unter 160 am Header abstürzt.  
  → alpaka$currentScissor() direkt über das per Fabric-Access-Widener erreichbare scissorStack.peek() lösen, die Spiegel-Injects samt unerreichbarer Fallbacks löschen und in GuiRendererMixin auch die fernen Kanten klemmen.  
  `mixin/GuiGraphicsExtractorMixin.java:48` · C6-8, C6-7, X1-16
  <details><summary>Details</summary>

  **Scissor mirror is not intersected with the screen, so it differs from vanilla's stack despite the comment**

  Vanilla's ScissorStack.push intersects each new rectangle with `peekLast()`, falling back to screenSize (0, 0, guiWidth, guiHeight) when the stack is empty (javap of GuiGraphicsExtractor$ScissorStack). The mirror only intersects with a parent when one exists, so the outermost rectangle keeps negative or off-screen coordinates. The comment at line 40 says "exactly as vanilla's stack does". Every RoundedRect, GradientRoundedRect or BlurRect state therefore carries a scissor vanilla would never produce. That is the real source of the crash GuiRendererMixin works around (and only half fixes, C6-7). It also widens those elements' bounds past the screen.

  *Vorschlag:* Use `ScreenRectangle parent = Objects.requireNonNullElse(alpaka$scissors.peekLast(), new ScreenRectangle(0, 0, guiWidth(), guiHeight()))`, with @Shadow guiWidth()/guiHeight(), and always intersect. Alternatively, open GuiGraphicsExtractor$ScissorStack with an access widener and read vanilla's peek() instead of keeping a mirror.

  </details>
- **[low · S]** GuiSkinStyle verwendet eine WeakHashMap mit dem Record GuiSkinRenderState als Schlüssel (Wert- statt Identitätsvergleich, entgegen dem Kommentar), sodass ein GC den Avatar im Custom Main Menu im Stillstand gelegentlich einen Frame lang ungetönt und auf ganze Pixel gerundet aufblitzen lässt.  
  → Nach Identität schlüsseln (pro Frame geleerte IdentityHashMap oder Entfernen beim Lesen wie in GuiItemFade) und den Kommentar korrigieren.  
  `client/gui/GuiSkinStyle.java:31` · C6-10, X1-17, C5-3
  <details><summary>Details</summary>

  **Skin style map is keyed by record equality, not identity as documented, and can drop the style for a frame**

  GuiSkinRenderState is a record (javap: extends java.lang.Record, with equals and hashCode), so the WeakHashMap compares keys by value, not by identity as the comment says. While the main-menu avatar is idle, consecutive frames produce equal states. WeakHashMap.put then keeps the old key (an earlier frame's state, now only weakly reachable) and replaces just the value. If a GC lands between this frame's extraction and its blit, that old key is cleared, color() and pose() miss, and the avatar is blitted untinted at the whole-pixel position for one frame. Every PIP blit also pays two record hashCode calls. GuiItemFade is fine, because GuiItemRenderState has no equals.

  *Vorschlag:* Key by identity, e.g. a `java.util.IdentityHashMap` cleared at the start of each frame. Since only one styled preview is drawn per frame, a simpler fix is to remember the last styled state and its Style and compare with `==`. Fix the comment.

  </details>

### HUDs & Benachrichtigungen (10)

- **[medium · S]** Mit den Standardeinstellungen (Blur an) zeigt die über Send Test Notification ausgelöste Benachrichtigung im Konfigurationsmenü die unverdunkelte Spielwelt hinter dem Menü wie ein Loch im Panel, weil die Blur-Kopie vor dem GUI aufgenommen wird.  
  → In renderOverScreen das flache Glas (drawGlass) statt des geblurten Panels verwenden oder im Konfigurationsmenü captureAfterBackground() anfordern.  
  `features/notification/AlpakaNotifications.kt:358` · D4-7
  <details><summary>Details</summary>

  **A blurred notice drawn over the config menu shows the world behind the menu**

  renderOverScreen draws the notices in a new stratum on top of AlpakaConfigScreen. With Blur on, HudPanel.submit uses a BlurRectRenderState that samples ChatBlurFeature's copy. The config screen never defers the capture, so that copy is the frame taken in captureFrameBeforeGui: the world (or panorama) before any GUI. 'Send Test Notification' therefore shows a pane that looks through the opaque config panel at the undimmed world, unlike how the notice looks in game over the HUD.

  *Vorschlag:* In renderOverScreen use the flat glass (drawGlass) instead of the blurred panel. Alternatively have the config screen request captureAfterBackground() while a notice is showing, so the copy includes the menu.

  </details>
- **[low · S]** Im HUD Editor verdeckt die neue Steuerleiste oben in der Mitte dort liegende HUDs (etwa den Boss Timer) und schluckt alle Klicks in ihrem Rechteck, sodass ein dorthin gezogenes HUD verschwindet und nur per Mausrad plus Pfeiltasten oder Reset zurückkommt.  
  → Klicks in der Leiste, die keinen Button treffen, an HudRegistry.topmostAt durchreichen und die Leiste beim Ziehen ausblenden oder ausweichen lassen.  
  `client/hud/HudEditorScreen.kt:151` · D3-2
  <details><summary>Details</summary>

  **HUD editor bar hides HUDs at the top centre and takes their clicks**

  The new control bar (about 226x26 at y 6..32, centred, plus the status line down to y≈46) is drawn in a stratum above the HUD previews (nextStratum, line 97). clickBar returns true for any click inside the bar rectangle, not just on its buttons. A HUD kept at the top centre (a natural spot for the boss timer, just under the boss bar) is covered and cannot be grabbed. Dragging any HUD there and letting go makes it vanish under the bar. The only way to select it is to scroll over it (topmostAt ignores the bar), and then only the arrow keys or Reset can bring it back out. The old layout had plain text at the top, which did not take clicks.

  *Vorschlag:* Let clicks inside the bar but off its buttons fall through to HudRegistry.topmostAt. Also consider moving the bar to the bottom (or to whichever half has no HUD under it) when a HUD overlaps it, or fading it while a drag is in progress.

  </details>
- **[low · S]** Beim Wide Health Bar im Modus Gold Over Health wird die Absorption über die ersten roten Herzen gemalt, sodass bei viel Absorption die echte Gesundheit gar nicht mehr zu sehen ist und Gold sogar leere Herzcontainer füllt.  
  → Gold hinter die roten Herzen zeichnen (ab currentHealth) oder über die letzten roten Herzen, damit Rot immer lesbar bleibt, und die Beschreibung anpassen.  
  `features/healthbar/WideHealthBarFeature.java:156` · D4-3
  <details><summary>Details</summary>

  **'Gold Over Health' paints absorption over the red hearts, so actual health can become invisible**

  goldHalves = min(absorption, maxHealthHalves) is drawn over halves 0..goldHalves-1, which are the same halves that show current health. Example with SkyBlock's 40 max health: 7 red hearts (14 halves) and 10 absorption hearts. The bar shows hearts 1-10 gold (three of them over empty containers), hearts 11-20 empty, and no red at all. The player cannot see that real health is 7 hearts until absorption drops below it. Vanilla's layout and this feature's own 'Own Row' mode both show the two values separately.

  *Vorschlag:* Keep the 'used up first' meaning but draw the gold after the red: gold for halves in [currentHealth, currentHealth + absorption), capped at maxHealthHalves. Alternatively draw the gold over the last red hearts (halves in [max(0, currentHealth - absorption), currentHealth)), so red health always stays readable. Adjust the option description to match.

  </details>
- **[low · S]** Mit Notification Position oben links oder oben rechts verdecken Benachrichtigungen auf 1080p mit GUI-Scale 4 Ende und Namen der Bossleisten, weil nur die Mitte unter die Bossleisten ausweicht.  
  → Den Abstand unter den Bossleisten auch für die oberen Ecken anwenden, sobald sich ihr x-Bereich mit der Bossleisten-Spalte überschneidet.  
  `features/notification/AlpakaNotifications.kt:286` · D4-6
  <details><summary>Details</summary>

  **Top-corner notices still overlap boss bars at GUI scale 4 on 1080p**

  Only TOP_CENTER moves below the boss bars; the top corners start at MARGIN. At 1920x1080 with Auto GUI scale (4) the screen is 480 GUI px wide. Boss bars span x 149..331, a top-right notice spans 276..472 and a top-left one 8..204, and all of them sit at y 3..17 and below. Whenever SkyBlock shows a boss bar (dungeon or Kuudra bosses, events), the notice covers the bar's end and its name.

  *Vorschlag:* Apply the same below-bars offset to the top corners when the notice's x-range intersects the bar column (screenWidth/2 ± 91), or always when bars are shown.

  </details>
- **[low · S]** Das an die Hotbar angehängte Inventory HUD fragt Fabrics Registry jeden Frame nach der Höhe der Action Bar, die dort nicht existiert, und baut dabei jedes Mal eine Exception samt Stacktrace (im HUD Editor sechsmal pro Frame), während der Kommentar das Gegenteil beschreibt.  
  → ACTION_BAR_LINE direkt verwenden, registeredHeight nur für echte Statusleisten aufrufen und den Kommentar richtigstellen.  
  `features/inventoryhud/InventoryHudRenderer.kt:223` · D5-2
  <details><summary>Details</summary>

  **Action-bar height lookup throws every frame; comment says the opposite of what the code does**

  statusStackHeight calls registeredHeight(VanillaHudElements.OVERLAY_MESSAGE, ACTION_BAR_LINE) whenever overlayMessageTime > 0. On Hypixel that is effectively always, because the action bar is resent continuously. In fabric-rendering-v1 25.3.3 (shipped with fabric-api 0.159.0+26.2), HudStatusBarHeightRegistryImpl.getHeight throws IllegalArgumentException for any id missing from resolvedHeightProviders. That map holds only HEALTH_BAR, ARMOR_BAR, MOUNT_HEALTH, FOOD_BAR and AIR_BAR; OVERLAY_MESSAGE is only wrapped, never added (checked with javap). So every frame with the attached inventory HUD visible builds and catches an exception: a string concat plus a stack-trace fill on the render thread. The HUD editor does this about 6 times per frame (anchorX getter, visibleAnchorX/Y, visibleBounds, topmostAt). The KDoc at lines 208-210 says asking the registry for the action bar 'always failed', yet the code still asks and relies on the catch. Anyone reading it will believe the registry value is being used.

  *Vorschlag:* Use ACTION_BAR_LINE directly (top = maxOf(top, ACTION_BAR_LINE + ACTION_BAR_ABOVE_LINE)) and reword the comment to say the action bar line is vanilla's constant because Fabric's registry does not cover it. Keep registeredHeight for the real bars only.

  </details>
- **[low · S]** Im HUD Editor lässt sich das Player Model per Trackpad (etwa auf dem Mac) bei langsamem Scrollen nicht skalieren, weil Bruchteile einer Raste auf 0 abgeschnitten werden, während alle anderen HUDs flüssig skalieren.  
  → Die Scroll-Bruchteile in einem Akkumulator sammeln und nur ganze Schritte anwenden.  
  `features/playermodel/PlayerModelHudElement.kt:40` · D5-3
  <details><summary>Details</summary>

  **Player model cannot be resized with a trackpad: fractional notches truncate to zero**

  adjustScale computes (notches * 2.0).toInt(). With Discrete Scrolling off (the default), MouseHandler.onScroll passes GLFW's raw fractional offset (times sensitivity) to Screen.mouseScrolled. macOS precise trackpad deltas come in steps of about 0.1 per pixel, so slow and medium two-finger scrolls report 0.1-0.4 per event, and every event truncates to 0. The player model then only resizes on fast flicks. Every other HudElement adds notches * 0.1 as a float and resizes smoothly, and the HudElement contract explicitly mentions trackpad fractions. The maintainer deploys to a Mac.

  *Vorschlag:* Keep a private fractional accumulator: acc += notches * SCALE_PER_NOTCH; val delta = acc.toInt(); acc -= delta; then apply delta. Reset acc when the scale hits a limit or the selection changes.

  </details>
- **[low · S]** World Age stempelt weiter den zuletzt besuchten SkyBlock-Server als aktiv, auch nachdem man ihn verlassen hat (etwa in die Hypixel-Hauptlobby), sodass der Hinweis ‚visited Xs ago‘ nach einem kurzen Abstecher fehlt und die letzte Aktivität jedes Servers um etwa 2,5 s verschoben ist.  
  → Beim Scharfschalten des Join-Countdowns currentServerKey leeren und die doppelte Prüfung in Zeile 170 entfernen.  
  `features/worldage/WorldAgeHudRenderer.java:225` · D5-4
  <details><summary>Details</summary>

  **Presence stamp keeps refreshing a server the player already left, giving a wrong 'visited Xs ago'**

  render() stamps SERVER_LEAVE_MAP[currentServerKey] = now once a second. currentServerKey is only reassigned in processServerJoin at line 167, which comes after early returns for the join message being off (line 147), not being on SkyBlock (line 148) and an empty server key. Scenario: the join message is on, the player is on SkyBlock server A, then goes to the Hypixel main lobby (not SkyBlock) for 10 minutes. currentServerKey stays 'hypixel_A' and gets stamped every second the whole time. If they come back to A, the diff is about 2.5-3.5 s (the join delay), so the message says 'visited 3s ago' instead of 10 minutes, or reports a visit when the threshold should have suppressed it. In the normal path the old key also keeps being stamped through the 50-tick join countdown, shifting every 'last active' by about 2.5 s. Separately, the check at line 170 repeats line 147 and can never fail.

  *Vorschlag:* In onServerJoin(), when arming the countdown, set currentServerKey = "" so stamping stops the moment the old world is left. Remove the redundant check at line 170.

  </details>
- **[low · S]** Mit den Menu Fonts Inter, Poppins oder Outfit (proportionale Ziffern) wackelt das Panel des Slayer Boss Timer zehnmal pro Sekunde in der Breite, und am rechten Bildschirmrand springt die ganze Zeile hin und her.  
  → Panelbreite und bounds() aus einer festen Vorlage mit der breitesten Ziffer berechnen oder die maximale Breite des laufenden Kampfes halten.  
  `features/slayer/SlayerTimerHudRenderer.kt:80` · D5-5
  <details><summary>Details</summary>

  **Boss-timer panel and clamp box follow the running text width, so they wobble with proportional-digit fonts**

  The blurred panel width is timeX + font.width(time), and the editor/clamp bounds use the same per-frame width. Of the bundled smooth fonts, Inter, Poppins and Outfit have proportional digits: measured from the TTF hmtx tables, Inter '1' is 833/2048 em against '0' at 1292/2048, roughly 4.1 px vs 6.3 px at size 10. Nunito and Varela Round are tabular. With one of those three selected, the panel's right edge shifts by a few pixels ten times a second as '11.1s' becomes '11.2s' and so on. If the timer is parked at the right screen edge, clampShift moves the whole line left and right in step. Vanilla's font and the default Nunito don't show this.

  *Vorschlag:* Size the panel and bounds() from a fixed template, e.g. SlayerTimer.format(elapsed) with every digit replaced by '0' (the widest digit in all bundled faces), while still drawing the real time. Alternatively keep the maximum width seen during the current fight.

  </details>
- **[low · S]** Schaltet man beim Inventory HUD Attach Above Hotbar aus, landet das Panel standardmäßig bei (10, 10) genau auf World Age und der ersten Zeile des Slayer Session HUD, entgegen dem Versprechen, dass sich Standard-HUDs nicht überlappen.  
  → Eine freie Standardposition für das losgelöste Inventory HUD wählen und in den Überlappungstest der Standard-HUDs aufnehmen.  
  `client/hud/HudDefaults.java:32` · D5-6
  <details><summary>Details</summary>

  **Detached inventory HUD default lands on top of World Age and the session HUD**

  INVENTORY_X/Y = (10, 10) is the same point as WORLD_AGE_X/Y. The flat panel is 168x60 at scale 1, so it covers y 10..70, which takes in the World Age line and the first row of the slayer session HUD at y = 60. On a fresh install, unticking 'Attach to hotbar' in the config (AlpakaConfigRegistry line 663) drops the panel exactly there. This contradicts the class comment that the defaults are laid out so no two of the mod's HUDs overlap.

  *Vorschlag:* Pick a free default for the detached inventory HUD, e.g. to the right of the text column below the player model, or bottom-centre above the hotbar area. Cover it with the same non-overlap test proposed for D5-1.

  </details>
- **[low · S]** Mit Wide Health Bar schwebt das angehängte Inventory HUD bis zu etwa 20 px über den Herzen, weil die Höhenberechnung von Vanillas Zehn-Herz-Reihen plus Rüstungsreihe ausgeht, die Wide Health Bar gar nicht zeichnet.  
  → Bei aktivem Wide Health Bar die Spaltenhöhe aus WideHealthBarFeature.rows()/rowHeight() berechnen und die Rüstungsreihe ignorieren.  
  `features/inventoryhud/InventoryHudRenderer.kt:219` · X1-19
  <details><summary>Details</summary>

  **Attached inventory HUD does not account for Wide Health Bar**

  statusStackHeight takes the left column's top from Fabric's HudStatusBarHeightRegistry, which models vanilla's ten-heart rows plus the armor row. With Wide Health Bar on, hearts are drawn in rows of twenty and the armor row is hidden. A SkyBlock player with 40 HP and armor gets two heart rows plus armor counted while only one row is drawn, so the attached panel floats about 20 px above the hearts.

  *Vorschlag:* When WideHealthBar is active, compute the column from WideHealthBarFeature.rows()/rowHeight() and ignore the armor row.

  </details>

### Konfigurationsmenü (9)

- **[low · S]** /alpakapreset <n> ist nicht mit .requires(FabricClientCommandSource::attended) abgesichert, sodass ein Klick-Event vom Server ein Viewmodel-Preset laden und ungespeicherte Viewmodel-Werte (Größe, Offsets, Rotation, Swing) überschreiben kann, entgegen der eigenen Regel für Befehle, die im Namen des Spielers laufen.  
  → Auch das number-Argument mit .requires(FabricClientCommandSource::attended) absichern (auf mc-26.1.2 über CommandAttendance).  
  `client/AlpakaClient.java:227` · C6-20
  <details><summary>Details</summary>

  **`/alpakapreset <n>` can overwrite live viewmodel settings from a server click event**

  The comment at lines 179-181 states the policy: commands run on the player's behalf (a server's click event, a macro) "cannot wipe anything", and destructive subcommands use `.requires(FabricClientCommandSource::attended)`. `/alpakapreset <n>` calls loadPreset, which overwrites the live item scale, offsets, rotation, swing and motion-blur values with the preset's. Any unsaved tweaks are lost, and the command is not gated, so a chat click event from the server can trigger it.

  *Vorschlag:* Add `.requires(FabricClientCommandSource::attended)` to the number argument as well, or document why switching presets is exempt.

  </details>
- **[low · S]** Drückt man im Config-Menü Strg+F, während ein Textfeld wie Bridge Bot Name fokussiert ist, zeigt die Suche Fokus und Cursor, die Eingabe landet aber weiter im Textfeld (aus 'VultureGround' wird 'VultureGroundblaze').  
  → Im Strg+F-Zweig vorher focusedText auf null setzen und AlpakaConfig.save() aufrufen.  
  `client/AlpakaConfigScreen.java:1101` · D1-2
  <details><summary>Details</summary>

  **Ctrl+F while a text setting is focused leaves it focused; typing goes into the setting, not the search**

  The Ctrl+F branch sets searchFocused = true and selects all search text but never clears focusedText. charTyped checks focusedText first. Scenario: click Bridge Bot Name, type 'VultureGround', press Ctrl+F (the search pill now shows the accent outline and a blinking caret), type 'blaze'. The bridge bot name becomes 'VultureGroundblaze' while the search box looks focused and stays empty. Both carets blink at once.

  *Vorschlag:* In the Ctrl+F branch, commit and drop the text focus first: if (focusedText != null) { focusedText = null; AlpakaConfig.save(); }.

  </details>
- **[low · S]** Klickflächen gescrollter Zeilen im Config-Menü werden nur oben auf den sichtbaren Bereich gekürzt, sodass ein unten angeschnittener Slider bis etwa 18 px unter dem Panel auf Klicks in den abgedunkelten Hintergrund reagiert und seinen Wert ohne Hover-Hervorhebung verstellt.  
  → Nur den sichtbaren Ausschnitt von max(rowY, clipY) bis min(rowY + rowH, clipY + clipH) registrieren, über einen gemeinsamen Helfer wie addClippedHit für alle gescrollten Spalten.  
  `client/AlpakaConfigScreen.java:725` · D1-4
  <details><summary>Details</summary>

  **Click areas are not clipped to the scroll viewport; half-hidden rows react to clicks below the panel**

  Every scrolled row registers Hit(x, Math.max(rowY, clipY), w, rowH). Only the top is clamped, and the height is never reduced. A row straddling the bottom of the viewport keeps its full height below clipY + clipH. A 24 px slider with 2 px visible reacts up to 22 px below the clip, which is about 18 px below the panel's bottom edge on the dimmed backdrop. Clicking there sets that hidden slider to the click's x. The same applies to feature rows and their switches (lines 635/640), sidebar sections (577), the description row (683), dropdown entries (816) and choice rows (885/900). A section clipped at the top also keeps its full 13 px, so it reacts over the next group's label and divider below it. Hover is clipped correctly, so the row reacts without being highlighted. That contradicts the class doc's 'can never disagree' claim.

  *Vorschlag:* Register the visible slice only: int top = Math.max(rowY, clipY), bottom = Math.min(rowY + rowH, clipY + clipH); hits.add(new Hit(x, top, w, bottom - top, ...)). Put it in one helper (e.g. addClippedHit) used by all scrolled columns.

  </details>
- **[low · S]** Löscht man im Config-Menü eine Suche ohne Treffer (z. B. 'zzz'), bleibt kein Feature ausgewählt, die Detailspalte ist leer und der Kopf ohne Namen; ein gewählter Suchtreffer wird außerdem nicht in den sichtbaren Bereich gescrollt.  
  → Im Nicht-Such-Zweig von onSearchChanged bei selectedFeature == null das erste Feature der Sektion wählen und sonst revealSelected = true setzen.  
  `client/AlpakaConfigScreen.java:284` · D1-5
  <details><summary>Details</summary>

  **Clearing a search after a no-result query leaves no feature selected; chosen result is not scrolled into view**

  Typing a query with no matches makes selectFirstResult() set selectedFeature = null. Clearing the query (Backspace or the clear button) takes the else-branch only if selectedFeature != null, so nothing happens. The middle column shows the old section with nothing highlighted, the details panel is empty and the header has no name until the player clicks a feature. Example: type 'zzz', then erase it. When a result was selected, onSearchChanged switches to its section and resets featureScroll to 0 but does not set revealSelected. On short windows the selected feature can sit below the visible list.

  *Vorschlag:* In the not-searching branch: if selectedFeature == null and selectedSection != null, select the section's first feature. Otherwise set selectedSection = selectedFeature.section and revealSelected = true.

  </details>
- **[low · S]** Der Suchtext im Config-Menü wird nicht auf das Suchfeld beschnitten, sodass längere Suchen (ab etwa 20 Zeichen bei 1080p und GUI-Scale 4) über das ✕ und über das Feldende hinauslaufen und der Cursor mitwandert.  
  → Wie AlpakaStyle.textField von links abschneiden (Ende sichtbar, Cursor am Ende) oder den Text auf [tx, iconX - 2] scissorn.  
  `client/AlpakaConfigScreen.java:520` · D1-7
  <details><summary>Details</summary>

  **Search text is not clipped to the search pill**

  The search text is drawn in full at tx with no clipping. Only the header scissor (up to detailX) limits it. With MAX_SEARCH_LENGTH 35 and a feature column of about 150 px (GUI-scaled width around 480, e.g. 1080p at GUI scale 4), a query such as '/aa custom sound inventory open close' runs over the clear '✕' button, past the pill's right end, and the caret follows it outside the field.

  *Vorschlag:* Clip from the left as AlpakaStyle.textField does (show the tail, caret at the clipped end), or scissor the text to [tx, iconX - 2].

  </details>
- **[low · S]** MASTERS in ConfigTree ist genau die Menge der Nicht-'#'-IDs aus LAYOUT (je 75) und muss von Hand synchron gehalten werden; fehlt ein neues Feature in einer der Listen, wird sein Schalter still zur Einstellung des vorherigen Features oder landet unter 'Other'.  
  → MASTERS aus LAYOUT ableiten (alle Einträge ohne '#') und das separate Set.of löschen.  
  `client/gui/ConfigTree.java:91` · D1-9
  <details><summary>Details</summary>

  **MASTERS duplicates LAYOUT exactly and must be kept in sync by hand**

  MASTERS is exactly the set of non-'#' ids in LAYOUT (checked: 75 ids each, no difference). Adding a feature means editing both lists, and either omission fails silently. Leave it out of MASTERS and the new switch becomes a setting of whatever feature was registered before it. Leave it out of LAYOUT and it lands under 'Other' inside the SkyBlock group.

  *Vorschlag:* Derive MASTERS from LAYOUT (collect every entry not starting with '#') and delete the separate Set.of.

  </details>
- **[low · S]** Wird das Config-Menü geschlossen, während ein Textfeld wie Guild Tag oder Bridge Bot Name fokussiert ist (Kick, Disconnect, Spiel beenden), speichert removed() die Eingabe nicht, und sie geht verloren, wenn danach keine andere Einstellung mehr gespeichert wird.  
  → In removed() bei gesetztem focusedText diesen auf null setzen und AlpakaConfig.save() aufrufen.  
  `client/AlpakaConfigScreen.java:1143` · D1-11
  <details><summary>Details</summary>

  **removed() does not commit a focused text setting**

  Text setters write only to memory (registry lambdas do not save). Saving happens on blur through a click, Esc or Enter. If the screen is replaced while a text option is focused (server kick or disconnect while the menu is open), removed() ends deferred saves but leaves focusedText unsaved. The edit then reaches disk only if some other setting is saved before the game exits.

  *Vorschlag:* In removed(): if (focusedText != null) { focusedText = null; AlpakaConfig.save(); }.

  </details>
- **[low · S]** Steht in einer (handbearbeiteten) Config "bridgeBotName": null oder "guildPrefixText": null, reicht ConfigOption.getText() das null durch und das Config-Menü wirft beim Anzeigen von Bridge Bot Formatter oder Custom Guild Tag eine NullPointerException im Render-Thread.  
  → In getText() null zu "" machen oder beide Felder in AlpakaConfig.load() normalisieren.  
  `client/gui/ConfigOption.java:404` · D1-12
  <details><summary>Details</summary>

  **getText() can return null and crash the config screen every frame**

  AlpakaConfig does not null-normalise bridgeBotName or guildPrefixText. A config containing "bridgeBotName": null (hand-edited or written by a tool) deserialises to null. getText() passes it through, and AlpakaConfigScreen line 845 calls value.isEmpty(), throwing an NPE on the render thread as soon as the Social section's Bridge Bot Formatter is shown.

  *Vorschlag:* Return an empty string for null: String v = textGetter != null ? textGetter.get() : null; return v == null ? "" : v;

  </details>
- **[low · S]** Die Blur- und Smooth-Font-Schalter pro HUD haben alle dieselbe Beschreibung und dieselben Keywords ohne HUD-Namen, sodass die Suche im Config-Menü für 'chat blur', 'scoreboard blur' oder 'chat font' nichts findet, obwohl 'chat blur' vorher funktionierte.  
  → styleToggle ein Subjekt-Argument geben ("chat", "scoreboard sidebar", "notification" usw.) und die Keywords daraus bilden.  
  `client/gui/AlpakaConfigRegistry.java:2038` · D2-8
  <details><summary>Details</summary>

  **Per-HUD style switches can't be found by searching 'chat blur', 'scoreboard blur' or 'chat font'**

  styleToggle() gives every Blur / Smooth Font switch the same generic description and keywords, which never mention the HUD they belong to. Search does a substring match on the feature title and on each option's 'title + description + keywords'. So 'chat blur', 'scoreboard blur', 'chat font' and 'scoreboard font' now return nothing. On main, 'chat blur' found 'Blurred Chat Background' (its keywords started with 'chat blur background') and 'scoreboard blur' found the scoreboard option. 'chat font' is the obvious way to look for the new Nunito-in-chat switch.

  *Vorschlag:* Give styleToggle a subject argument, for example "chat", "scoreboard sidebar", "slayer session hud", "boss timer", "world age" or "notification". Build keywords as subject + " blur ..." or subject + " font smooth font ..." so phrases like 'chat blur' and 'chat font' appear in the search string.

  </details>

### Untermenüs, AlpakaStyle & Konventionen (6)

- **[medium · M]** Die selbstgebauten Textfelder (Inline-Optionen wie Bridge Bot Name und Guild Tag, Hex-Feld im Farbwähler, Quick-Command-Eingabe im Wheel-Editor) können nur anhängen und löschen: kein Cursor, keine Auswahl, kein Strg+V. Im Config-Menü verschwindet ein normaler Name mit 13–16 Zeichen schon beim Tippen hinter einem '…', und der Cursor steht dahinter.  
  → Alle drei Felder mit EditableText hinterlegen, mit AlpakaStyle.textField (clipLeft) zeichnen und Code Points per Character.toChars einfügen.  
  `client/CommandWheelConfigScreen.kt:495` · X1-11, D1-6, D3-10
  <details><summary>Details</summary>

  **Three hand-rolled text fields have no caret, selection or paste although EditableText exists**

  EditableText's own doc says it exists 'so the same behaviour can be given to the other hand-rolled fields'. It is still used only by the config search box. The quick-command input (CommandWheelConfigScreen 495), the colour picker's hex field (ColorPickerScreen 342) and the config's TEXT options such as Guild Tag and Bridge Bot Name (AlpakaConfigScreen 1085) are append/backspace-only Strings. They have no caret, no Ctrl+A/C/V and no selection, and `(char) codePoint` mangles code points outside the BMP. A player cannot paste a long /warp command, a hex colour or a bot name.

  *Vorschlag:* Back all three with EditableText and route keyPressed/charTyped through it, as AlpakaConfigScreen does for the search box.

  </details>
- **[low · S]** Das Config-Menü und seine Untermenüs setzen openTimeMs in init(), deshalb läuft die Öffnungsanimation bei jedem Resize, F11 oder Ressourcen-Reload erneut. Das eigene Pausenmenü spielt sogar nach jeder Rückkehr aus Options, Mods oder Config den ganzen Einstieg neu ab, samt Abdunklung ab 0 (die Welt blitzt kurz unverdunkelt auf).  
  → Den Zeitstempel nur einmal setzen, wie im Hauptmenü mit if (openTime == 0L), oder in added() bzw. im Konstruktor.  
  `client/ColorPickerScreen.java:71` · D3-8, C5-5
  <details><summary>Details</summary>

  **Opening pop replays on every resize and resource reload**

  Screen.resize → repositionElements → rebuildWidgets → init(). LoadingOverlay also calls screen.init(w,h) after a resource reload. All four sub menus (ColorPickerScreen:71, AlpakaConfirmScreen:45, CommandWheelConfigScreen:178, HudEditorScreen:55) reset openTimeMs in init(). Dragging the window edge, pressing F11 or reloading resource packs shrinks the window to 0.9 and pops it again; during a drag-resize it jitters the whole time.

  *Vorschlag:* Set openTimeMs in added(), which only Gui.setScreen calls, or in the constructor.

  </details>
- **[low · S]** Im Wheel-Editor ('Reset' aller Quick Commands) und im HUD-Editor ('Reset All' aller HUD-Positionen) löst ein normaler Doppelklick die 'Sure?'-Bestätigung sofort aus, und es gibt kein Undo. Der scharfe Zustand ändert nur das Label, und insgesamt bestätigt der Mod destruktive Aktionen auf vier bis fünf verschiedene Arten.  
  → Den bestätigenden Klick etwa 400 ms nach dem Scharfschalten ignorieren, den scharfen Zustand sichtbar mit DANGER füllen und dafür einen gemeinsamen Inline-Arm-Helfer verwenden.  
  `client/CommandWheelConfigScreen.kt:377` · D3-5, X1-12
  <details><summary>Details</summary>

  **"Sure?" second-click confirmation goes through on an ordinary double-click**

  The wheel editor's Reset (wipes every quick command) and the HUD editor's Reset All (HudEditorScreen.kt:158-168, every HUD position and scale) confirm with a second click within 3 s. A fast or habitual double-click arms and fires within about 200 ms, and there is no undo. The armed state only changes the label to "Sure?", in the same colours. The config screen's own destructive actions (Disable All, Reset All Settings) use AlpakaConfirmScreen, so sub menus confirm differently from the menu they belong to.

  *Vorschlag:* Open AlpakaConfirmScreen(this, ..., dangerous = true, ...) for both, as Reset All Settings does. If the inline arm is kept, ignore the confirming click for about 400 ms after arming and give the armed state a distinct DANGER fill.

  </details>
- **[low · S]** Ein Klick auf eine Farbvorlage im Farbwähler setzt die Deckkraft auf FF. Block Overlays 'Fill Color' (Standard 0x440000FF) wird dadurch z. B. plötzlich deckend und verdeckt den Block.  
  → Die aktuelle Deckkraft behalten mit setColor((a << 24) | (PRESETS[i] & 0xFFFFFF)) und den Auswahlring per RGB-Vergleich setzen.  
  `client/ColorPickerScreen.java:282` · D3-9
  <details><summary>Details</summary>

  **Presets throw away the option's opacity**

  setColor(PRESETS[i]) replaces the alpha with FF. Block Overlay's Fill Color defaults to 0x440000FF. Clicking the red preset makes it 0xFFFF5252, an opaque fill that hides the block, and the player then has to find the Opacity slider to undo it.

  *Vorschlag:* Keep the current alpha: setColor((a << 24) | (PRESETS[i] & 0xFFFFFF)). Compare by RGB for the ring around the current preset.

  </details>
- **[low · S]** ModernGuiUtils enthält noch rund 170 Zeilen des alten Karten- und Button-Looks (drawModernCard, drawModernButton, drawModernTextField, alte Farbkonstanten), die auf design-rework niemand mehr aufruft. Genau dieser Stil ist in Untermenüs verboten, bleibt aber öffentlich verfügbar, und der Header spricht noch von 'Gold accent'.  
  → Die ungenutzten Controls und Konstanten löschen, nur die Form-Primitive behalten und im Javadoc für Controls auf AlpakaStyle verweisen.  
  `client/gui/ModernGuiUtils.java:63` · C6-9, X1-9
  <details><summary>Details</summary>

  **The old card/toggle/slider/button/text-field look is dead on this branch**

  On design-rework nothing calls drawModernCard, drawModernToggle, drawModernCheckbox, drawModernSlider, drawModernButton, drawModernColorButton, drawModernDestructiveButton, drawModernTextField, drawRoundedPanel (used only by those), getAccentBgColor, CARD_RADIUS, WIDGET_RADIUS or COLOR_PANEL_BG/SIDEBAR_BG/CARD_BG/CARD_BG_HOVER/TOGGLE_*/TEXT_DARK. Only origin/main's old AlpakaConfigScreen used them. That is about 170 lines, and it is exactly the "old ModernGuiUtils card/button look" the conventions ban from sub menus. Leaving it invites new code to reach for it. Two smaller leftovers: drawModernToggle takes an unused Font, and the header comment "Gold accent" is stale (the accent is the configurable teal).

  *Vorschlag:* Delete the unused controls and constants on design-rework. Keep only the shape primitives (drawRect, drawOutline, drawRoundedRect/Outline, drawPanelShadow, centeredText, lerpColor, getAccentColor/getAccentDimColor) and give the class a Javadoc saying controls live in AlpakaStyle. Consider renaming it to something like GuiShapes.

  </details>
- **[low · M]** AlpakaConfigScreen hält eigene Kopien von AlpakaStyles Farben, PAD, inside() sowie der Slider- und Panel-Zeichnung. HEADER_H weicht ab (33 statt 30), AlpakaStyle.sliderTrack/TRACK/ROW_H sind ungenutzt, und der HUD-Editor hat ein eigenes ellipsize mit '...' statt '…'. Eine Farbänderung im Config-Menü erreicht die Untermenüs dann nicht mehr.  
  → AlpakaConfigScreen auf AlpakaStyles Konstanten und Helfer umstellen (sliderTrack, panel, inside, beginOpen/endOpen), einen einheitlichen HEADER_H festlegen und ein gemeinsames ellipsize in AlpakaStyle anbieten.  
  `client/AlpakaConfigScreen.java:46` · D1-15, X1-8, D3-6, D3-7
  <details><summary>Details</summary>

  **Colours and controls duplicate AlpakaStyle**

  COLOR_BACKDROP through COLOR_DANGER repeat AlpakaStyle's constants value for value (AlpakaStyle even labels them 'Colours, the config screen's'). inside() duplicates AlpakaStyle.inside. The slider track (lines 743-751) is the same drawing as AlpakaStyle.sliderTrack, and the panel and shadow repeat AlpakaStyle.panel. ellipsize (line 1006) also exists in HudEditorScreen.kt:131 with '...' instead of '…'. Changing a colour or control in one place leaves the config menu and its sub menus out of step.

  *Vorschlag:* Reference AlpakaStyle.* constants and helpers (sliderTrack, panel, inside). Move ellipsize into AlpakaStyle so both menus use the same ellipsis.

  </details>

### Chat (12)

- **[medium · M]** Mit Custom Guild Tag oder Bridge Bot Formatter kann Compact Chat die alte Kopie einer wiederholten Gildennachricht nicht entfernen, sodass etwa 'LEMAN > Steve joined.' und 'LEMAN > Steve joined. (x2)' beide stehen bleiben und Smooth Chat die Wiederholung als neue Zeile hereinschiebt.  
  → Die in formatGuildMessage neu gebaute GuiMessage auf das Original abbilden (schwache Identity-Map) und in remove() über diese Zuordnung vergleichen.  
  `features/chat/CompactChatFeature.java:201` · C1-2
  <details><summary>Details</summary>

  **Compact Chat cannot remove the old copy of a guild-formatted message, so both lines stay visible**

  remove() finds the earlier copy's display lines via trimmedMessages.get(i).parent() == message. ChatComponentMixin.formatGuildMessage (ChatComponentMixin.java:125) is a @ModifyVariable at the head of addMessageToDisplayQueue and returns a new GuiMessage, so each GuiMessage.Line's parent is that rebuilt record, not the instance stored in allMessages. When Guild Prefix or the Bridge Bot Formatter rewrites a line, the stored message is removed from allMessages (it is found), but none of its lines are. count still goes up and the new line is added. Scenario: Guild Prefix on, Compact Chat on with the default 30 s window, and 'Guild > Steve joined.' arrives twice. The chat shows 'LEMAN > Steve joined.' and 'LEMAN > Steve joined. (x2)'; a third repeat adds a third line. They only collapse after the next re-layout. takeRemovedLines also reports nothing, so smooth chat slides the repeat in as if it were a new line.

  *Vorschlag:* Map display copies back to their stored message: record rebuilt -> original in a weak identity map inside formatGuildMessage, and compare resolve(line.parent()) == message. Or carry the display formatting in a way that keeps the original record as the line parent.

  </details>
- **[medium · S]** Chat Search durchsucht den Rohtext mitsamt §-Farbcodes: Einzelbuchstaben wie 'a', 'f' oder '6' treffen fast jede Zeile, Suchbegriffe über einen Farbwechsel hinweg ('Name: hi') finden nichts, und der sichtbare Gilden-Tag oder '[Discord]' ist gar nicht auffindbar.  
  → Vor dem Vergleich die Formatierungscodes mit einem gemeinsamen Stripper entfernen und dokumentieren, dass der Originalwortlaut durchsucht wird.  
  `features/chat/ChatSearchFeature.java:43` · C1-3
  <details><summary>Details</summary>

  **Chat search matches against raw text that still contains § colour codes**

  accepts() lower-cases message.content().getString(). For Hypixel lines that string still holds legacy codes such as '§2Guild > §6[MVP§c++§6] Name§f: hi' (BridgeBotFormatter and ChatTab.strip exist for exactly this reason). Searching '6', 'a' or 'f' matches nearly every line through §6, §a and §f. Any multi-word query that crosses a colour change ('[MVP++] Name', 'Name: hi') finds nothing. The search also runs on the stored, pre-formatting text, so typing the custom guild tag or '[Discord]' that is visible on screen finds no lines.

  *Vorschlag:* Strip formatting codes before matching (one shared stripper, see C1-18), e.g. strip(getString()).toLowerCase(Locale.ROOT). Consider documenting that the search covers Hypixel's original wording rather than the reformatted display.

  </details>
- **[medium · M]** Jeder Tastendruck in der Chat Search (bei Strg+F sogar doppelt) und jeder Wechsel des Chat-Tabs baut den kompletten Verlauf neu auf, und bei 5000 bis 20000 Nachrichten aus Expand Chat History wächst der Aufwand durch ArrayList.addFirst quadratisch, was spürbare Ruckler verursacht.  
  → Die Suche entprellen (~100 ms), den doppelten Refresh bei Strg+F streichen, den Neuaufbau linear machen (Zeilen gesammelt einfügen) und den veralteten Kommentar im ChatComponentMixin anpassen.  
  `features/chat/ChatSearchFeature.java:89` · C1-4
  <details><summary>Details</summary>

  **Full chat re-layout on every search keystroke and tab switch scales with the square of the 5000-20000 message history**

  refresh() calls rescaleChat(), which runs refreshTrimmedMessages. That replays every stored message (expandChatHistory is on by default, chatHistoryLimit defaults to 5000 and the slider allows 20000) through the tab and search filters, formatGuildMessage and GuiMessage.splitLines. Each line goes in with ArrayList.addFirst (both lists are ArrayLists), which shifts up to 5000-20000 references per line. The replay runs on every keystroke while searching, twice on Ctrl+F (see perf), on every tab click or Tab cycle, on closing the chat with a tab active, and on smooth-font toggles (GuiFont:145). Typing the first letter of a query, which matches most of the history, re-wraps and inserts thousands of lines in one frame: a visible hitch. The formatGuildMessage comment (ChatComponentMixin.java:104-107) says a re-wrap only happens on a window resize, which understates this.

  *Vorschlag:* Debounce the search refresh, e.g. re-layout once typing has paused for ~100 ms. Make the replay linear: collect the lines into a buffer in reverse and add them all at once, or lay out only the newest page(s) and extend on scroll. Reconsider the 5000 default. Update the stale comment in ChatComponentMixin.

  </details>
- **[medium · S]** Chat Mentions vergleicht den Spielernamen als reinen Teilstring, sodass ein Spieler namens 'Ice' bei 'nice drop' eine Benachrichtigung bekommt und 'Max' nie benachrichtigt wird, wenn 'Maxwell' 'Max come here' schreibt.  
  → Den Namen in Absender und Text nur als ganzes Minecraft-Namens-Token per Regex mit Wortgrenzen matchen (pro Name gecacht) und Tests für beide Richtungen ergänzen.  
  `features/notification/MentionNotifier.kt:68` · C1-5
  <details><summary>Details</summary>

  **Mention detection is a plain substring match, so short names get false notices and similar names are ignored**

  body.contains(name, ignoreCase = true) fires for the name inside any longer word, and sender.contains(name, ignoreCase = true) on line 67 treats a different player whose name contains the player's name as 'our own line'. Scenarios: a player named 'Ice' gets 'X mentioned you' for 'nice drop'; 'Max' for 'max dmg'. A guildmate 'Maxwell' writing 'Max come here' never notifies 'Max', because the sender block contains 'Max'.

  *Vorschlag:* Match the name as a whole Minecraft-name token in both places, e.g. Regex("(?<![A-Za-z0-9_])" + Regex.escape(name) + "(?![A-Za-z0-9_])", IGNORE_CASE), cached per name. Add test cases for both directions.

  </details>
- **[low · S]** Schaltet man Chat Tabs (oder per Disable All Features alles) aus, während mit Keep Tab After Closing Chat etwa der Party-Tab aktiv ist, bleibt der bisherige Verlauf ohne Filter-Chip auf Party gefiltert, während neue Nachrichten ungefiltert dazukommen, bis zum nächsten Neuaufbau.  
  → Bei jeder Änderung von chatTabsEnabled (auch über Disable All und Reset All) active auf ALL setzen, die Ungelesen-Zähler leeren und rescaleChat() aufrufen.  
  `features/chat/ChatTabsFeature.java:87` · C1-6
  <details><summary>Details</summary>

  **Turning Chat Tabs off while a kept tab is active leaves the HUD chat silently filtered**

  With 'Keep Tab After Closing Chat' on and e.g. Party active, switching Chat Tabs off (or using Disable All, AlpakaConfig.disableAllFeatures) only flips the config flag. 'active' stays PARTY and nothing re-lays out the chat. The old history stays filtered to party lines, renderFilterChip stops drawing because isEnabled() is false, and the chip was added precisely so a filtered chat never looks like an empty one. accepts() now lets new messages through, so the chat becomes a mix until the next resize. Switching the feature back on later restores the Party filter for new lines without a refresh.

  *Vorschlag:* When chatTabsEnabled changes, including disableAllFeatures and Reset All, set active = ALL, clear unread and call rescaleChat(). Alternatively, have accepts() and the chip treat a disabled feature with a non-ALL tab as 'reset now'.

  </details>
- **[low · S]** Party-Einladungen, die ein Mitglied im Namen des Leaders verschickt ('... has invited you to join [MVP++] Leader's party!'), gelten bei Chat Tabs weder als dringend noch als Party und sind deshalb auf Party, Guild und allen anderen Kanal-Tabs unsichtbar.  
  → PARTY_INVITE und PARTY_NOTICE um die Form "NAME's party" erweitern, idealerweise mit dem Muster aus PartyInviteFeature teilen, und ChatTabTest ergänzen.  
  `features/chat/ChatTab.java:84` · C1-7
  <details><summary>Details</summary>

  **Party invites sent on the leader's behalf are not urgent and not sorted onto the Party tab**

  PARTY_INVITE and PARTY_NOTICE only know 'X has invited you to join their party'. Hypixel's variant when a member invites for the leader, '[MVP+] Member has invited you to join [MVP++] Leader's party!', matches neither. It sorts to All and is hidden on Party, Guild and every other channel tab, contradicting the field comment 'never filtered away, whatever tab is up'. PartyInviteFeature.INVITE (PartyInviteFeature.java:49) already handles this form with its own, different regex.

  *Vorschlag:* Extend PARTY_INVITE (and PARTY_NOTICE) with the "(?:their|NAME's) party" form, ideally by sharing one invite pattern with PartyInviteFeature. Add the line to ChatTabTest.

  </details>
- **[low · S]** Der Server-Tab der Chat Tabs lässt NPC-, Boss- und Abiphone-Dialoge ('[NPC] Elle: ...', '[BOSS] Maxor: ...', '✆ Kat: ...') weg, weil PLAYER_LINE sie für Spielerchat hält, und sie erscheinen nur unter All.  
  → Server-Tags wie [NPC], [BOSS], [STATUE] und ✆ in isPlayerWritten ausschließen, die Liste mit MentionNotifier.SERVER_TAGS teilen und Tests für den Server-Tab ergänzen.  
  `features/chat/ChatTab.java:53` · C1-8
  <details><summary>Details</summary>

  **Server tab drops NPC, boss and Abiphone dialogue because PLAYER_LINE treats them as player chat**

  PLAYER_LINE allows an optional bracket tag and an optional single-glyph emblem before the name, so '[NPC] Elle: ...', '[BOSS] Maxor: ...' and '✆ Kat: ...' all match. ChatTabsFeature.belongsTo then excludes them from Server via isPlayerWritten(). They appear only under All, although the Server tab is documented as 'everything the server sent that no player wrote'. MentionNotifier already keeps a SERVER_TAGS list for exactly these speakers.

  *Vorschlag:* Exclude server-voiced tags ([NPC], [BOSS], [STATUE], ✆ ...) in isPlayerWritten, ideally sharing the list with MentionNotifier.fromPlayer. Add these lines to ChatTabTest for the Server tab.

  </details>
- **[low · S]** ChatOrigin merkt sich die Herkunft (Server oder Client) in einem einzigen Boolean, das verschachtelte Chat-Aufrufe anderer Mods überschreiben oder zurücksetzen, sodass Hypixel-Zeilen im Mods-Tab oder Client-Zeilen als Server-Zeilen landen können.  
  → beginAdd() den vorherigen Wert zurückgeben und endAdd(previous) ihn wiederherstellen lassen, isFromServer zusätzlich auf SYSTEM_SERVER prüfen und ChatOriginTest um den verschachtelten Fall erweitern.  
  `features/chat/ChatOrigin.java:74` · C1-9
  <details><summary>Details</summary>

  **Server/client origin flag is not nest-safe and ignores the message source**

  beginAdd() overwrites addingServerMessage and endAdd() resets it to false. Two cases go wrong. (1) Inside the packet's addServerSystemMessage -> addMessage, any synchronous addClientSystemMessage (SYSTEM_CLIENT), e.g. from another mod's addMessage hook, sees addingServerMessage == true and is marked as server. (2) A nested handleSystemMessage inside that addMessage runs beginAdd/endAdd and leaves the flag false. alpaka$compactRepeats, which runs after the HEAD injects, then reads false, so the packet's own Hypixel line lands on Mods instead of Server.

  *Vorschlag:* Have beginAdd() return the previous value and endAdd(previous) restore it. Make isFromServer require source == GuiMessageSource.SYSTEM_SERVER for the flag path. Extend ChatOriginTest with the nested-add case.

  </details>
- **[low · S]** Erwähnungen, die über einen Discord-Bridge-Bot in den Gildenchat kommen, meldet Chat Mentions als 'BridgeBot mentioned you', weil die Erkennung auf Hypixels Rohzeile läuft und der Bridge Bot Formatter erst danach greift.  
  → Ist der Absender der konfigurierte Bridge-Bot, den Autor aus dem Textanfang (bis zum Pfeil bzw. ': ') lesen und die Tests auf die rohe Hypixel-Form umstellen.  
  `features/notification/MentionNotifier.kt:96` · C1-10
  <details><summary>Details</summary>

  **Mentions relayed by a Discord bridge are credited to the bot account**

  inspect() runs on ClientReceiveMessageEvents.GAME, which carries the line as Hypixel sent it ('Guild > [VIP] BridgeBot [Member]: VultureAir⇾Jiles777: hi'). BridgeBotFormatter and GuildPrefixFormatter only change the display copy later, in addMessageToDisplayQueue. The sender block is therefore the bot's, speakerOf() returns 'BridgeBot', and the notice reads 'BridgeBot mentioned you'. The REPLY_ARROW branch and the test case 'LEMAN > [Discord] VultureAir⇾Jiles777' model the display-formatted line, which this event never delivers.

  *Vorschlag:* When the sender block matches the configured bridge bot, take the author from the start of the body (the text before the next ': ', cut at the arrow). Base the tests on the raw Hypixel form.

  </details>
- **[low · S]** Der Bridge Bot Formatter erkennt den Bot per Teilstring, sodass bei Bot-Name 'Bridge' ein Gildenmitglied wie 'BridgeFan' als '[Discord]'-Nachricht ohne eigenen Namen umgeschrieben wird, und Officer-Relays werden dunkelgrün statt in Hypixels Dunkeltürkis eingefärbt.  
  → Den Namen im Absenderblock nach Entfernen der Ränge exakt (equalsIgnoreCase) vergleichen und die Präfixfarbe je Kanal wählen (DARK_AQUA für Officer).  
  `features/bridge/BridgeBotFormatter.kt:74` · C1-11
  <details><summary>Details</summary>

  **Bot detection by substring, and Officer relays recoloured dark green**

  sender.contains(botName, ignoreCase = true) also matches any guild member whose name contains the bot's name (bot 'Bridge', member 'BridgeFan'). That member's lines are rewritten to 'Guild > [Discord] <their message>', which drops their identity and lets them pose as a Discord user. Separately, line 87 styles every prefix DARK_GREEN, while Hypixel colours 'Officer > ' dark aqua (§3). The comment says 'the marker keeps the colour Hypixel gives it'.

  *Vorschlag:* Compare the sender block's name token exactly: strip ranks and brackets, then equalsIgnoreCase. Pick the prefix colour per channel (DARK_AQUA for Officer/O), or slice the coloured prefix out of the coded string.

  </details>
- **[low · S]** Custom Guild Tag ersetzt 'Guild >' an beliebiger Stelle einer Zeile, sodass etwa eine öffentliche Nachricht 'type Guild > to see it' als 'type LEMAN to see it' angezeigt wird.  
  → Nur umschreiben, wenn die von Farbcodes bereinigte Zeile mit 'Guild >' beginnt.  
  `features/guild/GuildPrefixFormatter.kt:108` · C1-12
  <details><summary>Details</summary>

  **Custom guild tag replaces 'Guild >' anywhere in a line, not just the channel marker**

  containsTarget/replaceFirst look for the first text run that contains 'Guild >' anywhere. A public or party line such as '[MVP+] Someone: type Guild > to see it' is displayed as '[MVP+] Someone: type LEMAN to see it'. A non-guild player can also make their line carry the guild tag. The KDoc says only the channel marker is touched.

  *Vorschlag:* Only rewrite when the colour-stripped line starts with 'Guild >': check the first non-empty plain run after skipping leading § codes, or test strip(getString()).startsWith("Guild >") before walking the tree.

  </details>
- **[low · S]** Chatzeilen behalten die Smooth-Font-Variante der GUI-Skalierung, mit der sie umgebrochen wurden, und nach einem Skalierungswechsel oder Disable All Features (ohne GuiFont.refreshChat()) stehen alte Zeilen in Nunito bzw. unsauber skaliert neben neuen Zeilen in der richtigen Schrift.  
  → Nach disableAllFeatures() wie bei Reset All GuiFont.refreshChat() aufrufen und den zuletzt verwendeten Chat-Stil merken, um bei einer Abweichung (z. B. nach GUI-Scale-Wechsel) einmal neu umzubrechen.  
  `mixin/GuiMessageFontMixin.java:23` · D4-2, D2-6, X1-4
  <details><summary>Details</summary>

  **Chat lines keep the font id of the GUI scale they were wrapped at; scale change and Disable All never re-wrap**

  restyle() stores GuiFont.style() in each chat line at wrap time. That font id includes the GUI-scale suffix (_s2/_s3/_s4). In 26.2 only the chat option sliders call ChatComponent.rescaleChat (Options lambda$new$32..44); Minecraft.resizeGui does not (only Options.class and ChatComponent.class reference it). After a GUI scale change (the options menu, or Auto scale when the window is resized, F11 is pressed or the window moves to another monitor), every line already in chat keeps the old oversample. For example, _s2 lines drawn at scale 3 are magnified 1.5x with linear filtering and look soft, while new messages arrive in _s3: one chat with two sharpnesses. This is exactly what the class doc says the per-scale definitions are there to prevent. Separately, Disable All Features (AlpakaConfig.disableAllFeatures sets menuFont=0 and menuFontInChat=false) is not followed by GuiFont.refreshChat(), unlike Reset All. The chat history then stays in Nunito while new lines use the pixel font.

  *Vorschlag:* Remember the style the chat was last wrapped with, for example a `static Style chatWrapStyle` written by the mixin. Once per frame at the chat's extract HEAD (ChatComponentMixin already hooks it), call refreshChat() when `inChat() ? style() : Style.EMPTY` differs from it. That covers GUI-scale changes, Disable All and any future path that changes the setting. Alternatively, inject at the TAIL of Minecraft.resizeGui and call refreshChat() after disableAllFeatures().

  </details>

### Haupt-/Pausenmenü & Befehlsrad (10)

- **[low · S]** Die Menüuhr in MenuAmbience ist ein Float seit Klassenladung, sodass nach einem Tag Client-Laufzeit (typisch für AFK-Spieler) Avatar-Winken, Logo, Hero-Kante und Partikel im Hauptmenü und auf der Resume-Kachel sichtbar ruckeln.  
  → Die Uhr als Double führen und Phasen in Double reduzieren, bevor sie zu Float werden, oder sie beim Öffnen des Menüs neu starten.  
  `features/mainmenu/MenuAmbience.kt:39` · C5-7
  <details><summary>Details</summary>

  **Menu clock as Float loses precision after long client uptime; animations stutter**

  seconds() converts milliseconds since class load to Float. After 24 h of uptime the value moves in 8 ms steps, after 7 days in 62 ms steps. Phases built from it lose more: at t = 86 400 s, `t * WAVE_SPEED` (13) is about 1.1e6 with an ulp of 0.125 rad. The avatar's wave, the logo wiggle (`t * 11f`), the hero edge, the pulse, the drift and the motes then advance in visible jumps; after a week everything ambient updates at about 16 Hz. SkyBlock AFK players keep clients open for days and land on this menu after a disconnect. The comment on line 36 already argues against Float wall-clock time for this exact reason, only at a larger scale.

  *Vorschlag:* Keep the clock as Double (`(now - START_MS) / 1000.0`) and reduce each phase in Double before converting to Float (for example `sin(((t * 13.0) % (2 * PI)).toFloat())`). Alternatively, restart the clock when the menu opens (openTime-based), as the pause menu does.

  </details>
- **[low · S]** Schlägt ein Serverbeitritt aus der Serverliste des Custom Escape Menu fehl, landet man über Zurück im Pausenmenü ohne Welt, das über einem leeren oder alten Bild hängt, und Resume Game führt stillschweigend zum Titelbildschirm.  
  → Die Serverliste mit einem Eltern-Screen öffnen, der nur bei vorhandener Welt zum Pausenmenü und sonst zum Titelbildschirm zurückkehrt.  
  `features/escapemenu/CustomPauseScreen.java:187` · C5-10
  <details><summary>Details</summary>

  **Failed join from the pause menu's Server List leaves a pause menu with no world**

  Joining a server from the JoinMultiplayerScreen opened here calls ConnectScreen.startConnecting. That first runs minecraft.disconnectWithProgressScreen(false), so the current world or server is already gone. If the connection then fails, DisconnectedScreen → Back → JoinMultiplayerScreen → Back returns to this CustomPauseScreen with level == null. The pause menu is then drawn over nothing: no panorama and no world, only the stale or black frame plus blur. Its Resume Game silently goes to the title screen. Vanilla's pause menu has no server list, so this state cannot happen there.

  *Vorschlag:* Open the list with a parent that falls back sensibly, for example a lambda screen that returns to this menu only while `minecraft.level != null` and otherwise to the title screen. Or close the pause screen in init() / added() when level is null.

  </details>
- **[low · S]** Im Quick Command Menu begrenzt das Layout den Radius nur nach der Höhe, sodass lange Befehle links und rechts unter die Seitenpfeile laufen und das Zielen darauf ungewollt die Seite umblättert.  
  → Den Label-Radius zusätzlich durch die Breite minus Pfeilspalte begrenzen oder zu lange Labels mit Auslassungspunkten kürzen.  
  `features/wheel/CommandWheelScreen.kt:171` · C5-14
  <details><summary>Details</summary>

  **Wheel layout only fits vertically; long labels can run under the page arrows**

  layout() limits ringInner by height only. Labels at the left and right grow outward with no width check. Example: GUI 427x240 (scale 3 on 1280x720), 12 commands, the longest about 95 px. labelRadius ≈ 98, so the label at angle 0 spans x ≈ 311-406. That overlaps the right arrow (centre 397, hit radius 20). Aiming far out at that label enters the arrow's hover zone and turns the page with no click.

  *Vorschlag:* Also limit the label radius by `width/2 - ARROW_MARGIN - ARROW_HIT - maxLabelWidth`. Or truncate labels with an ellipsis when they would reach the arrow column.

  </details>
- **[low · S]** Bei Scrollable Tooltips übernimmt das nächste Item den Scrollstand, wenn sein Tooltip gleich groß ist (z. B. zwei gleiche Items oder Bazaar-Zeilen), sodass es schon gescrollt mit abgeschnittenem Namen erscheint.  
  → Das Tooltip zusätzlich über die Identität der ersten Komponente oder den gehoverten Slot erkennen und die Klassendoku korrigieren.  
  `features/tooltip/ScrollableTooltipsFeature.java:75` · C5-15
  <details><summary>Details</summary>

  **Scroll offset carries over to the next item when its tooltip has the same size**

  The tooltip is identified only by `width * 31 + height`, and is reset only after a gap of more than GONE_MS (150 ms). Moving between adjacent slots draws a tooltip every frame. Two items with equal-size tooltips, such as two copies of an item or bazaar or auction rows with lore of the same length, share a fingerprint, so the second opens already scrolled with its name and rarity cut off. The class doc says the offset lasts "for as long as the pointer stays on the same item" and that "a frame without a tooltip resets it", but a single frame without a tooltip does not reset it.

  *Vorschlag:* Include the tooltip's first line or component identity in the fingerprint. Since the wrapped call does not receive the components, wrap the List<ClientTooltipComponent> argument too and use System.identityHashCode of the list's first element or the hovered slot. Also fix the doc.

  </details>
- **[low · S]** Container Background Opacity ändert auch den Hintergrund von Schildern (Bazaar- und Auktionssuche), Büchern und Befehlsblöcken, obwohl die Option nur von Container-GUIs spricht.  
  → Den Opacity-Override wie das Einblenden auf isContainer beschränken und dabei auf ein einziges ci.cancel() und fill() vereinfachen.  
  `mixin/ScreenMixin.java:34` · C5-16
  <details><summary>Details</summary>

  **Container Background Opacity also replaces the backdrop of signs, books and command blocks**

  onExtractTransparentBackground replaces vanilla's 0xC0101010→0xD0101010 gradient for every screen whose isInGameUi() is true. That includes AbstractSignEditScreen (SkyBlock's bazaar and auction search sign), BookView/BookEdit/BookSign and the command-block screens, not only containers. The option reads "Darkening behind container GUIs", and the code gates the fade-in to containers (isContainer) but not the opacity. Two smaller points: `ci.cancel()` appears in both branches (lines 50 and 54), and `fillGradient(color, color)` is just fill.

  *Vorschlag:* Gate the opacity override on isContainer as well, so other screens keep vanilla's backdrop. Or reword the option to say it applies to all in-game screens. Then simplify to a single cancel and `fill(...)`.

  </details>
- **[low · S]** Die Buttons im Custom Main Menu und im Custom Escape Menu haben keine Sprachausgabe für den Narrator und keinen sichtbaren Tastaturfokus, sodass Tab einen unsichtbaren Fokus verschiebt und Enter einen Button auslöst, den man nicht ausgewählt sieht.  
  → defaultButtonNarrationText mit dem reinen Label aufrufen und isFocused() beim Hover-Ziel wie Hover behandeln.  
  `features/mainmenu/CustomMainMenuScreen.kt:530` · C5-18
  <details><summary>Details</summary>

  **Menu buttons have no narration and no visible keyboard focus**

  MenuButton (and CustomPauseScreen.PauseButton, line 460) override updateWidgetNarration with an empty body, so the Narrator (Ctrl+B) announces nothing on the main or pause menu. draw() ignores isFocused(), so Tab moves an invisible focus and Enter activates a button the player cannot see is selected. Vanilla's title and pause screens support both.

  *Vorschlag:* Call defaultButtonNarrationText(narration) using the plain label (without the icon glyph). Treat `isFocused()` like hover when computing the hover target.

  </details>
- **[low · S]** Eine von Hand bearbeitete Konfiguration mit null-Seiten oder null-Befehlen in commandWheelPages lässt das Spiel beim Öffnen des Quick Command Menu oder seines Editors abstürzen.  
  → In pages() null-Seiten sowie null- oder leere Befehle verwerfen und bei Änderungen speichern.  
  `features/wheel/CommandWheelPages.kt:27` · C5-20
  <details><summary>Details</summary>

  **A hand-edited config with null pages or commands crashes the wheel**

  commandWheelPages comes straight from Gson. `[["/hub"], null]` throws an NPE in `pages.all { it.isEmpty() }`, and `[[null]]` throws an NPE every frame in GuiFont.width inside CommandWheelScreen.layout(). Either one crashes the game when the wheel or its editor opens. GuiFont.selectedIndex shows the code base intends to survive hand-edited configs ("so a hand-edited config cannot take the menu down").

  *Vorschlag:* Sanitise in pages(): drop null pages and null or blank commands, and save if anything was removed.

  </details>
- **[low · S]** Container, die per Chatbefehl geöffnet werden (/storage, /pets, /bz), bekommen kein Container Background Fade In, weil das Schließen des Chats als dieselbe GUI-Sitzung zählt und die Startzeit vom Öffnen des Chats übernommen wird.  
  → Sitzungen nur für Container-Screens verfolgen bzw. die Sitzung zurücksetzen, wenn der vorige Screen kein AbstractContainerScreen war.  
  `features/guifade/GuiFadeTracker.java:29` · C6-12
  <details><summary>Details</summary>

  **Containers opened from a chat command never fade in**

  The grace window treats any screen opening within 400 ms of any screen closing as "the same session". Typing `/storage`, `/pets`, `/bz` and so on closes the ChatScreen (onGuiClosed), and Hypixel opens the container one round trip later (well under 400 ms). onGuiOpened therefore keeps guiOpenTime from when the chat was opened, seconds earlier, and ScreenMixin sees elapsed > 250 ms: the container background appears at full opacity with no fade. The same container opened by key or command wheel does fade. This is the most common way containers are opened on Hypixel.

  *Vorschlag:* Track sessions for container screens only: pass the screen into onGuiOpened/onGuiClosed and ignore ChatScreen and other non-container screens, or reset the session when the previous screen was not an AbstractContainerScreen.

  </details>
- **[low · S]** Disconnect im Custom Escape Menu (standardmäßig an) führt immer zum Titelbildschirm statt wie Vanilla nach dem Verlassen von Hypixel zur Serverliste bzw. bei Realms zurück zu Realms.  
  → this.minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE) aufrufen.  
  `features/escapemenu/CustomPauseScreen.java:250` · X1-18
  <details><summary>Details</summary>

  **Custom pause menu's Disconnect skips vanilla's leave flow**

  Confirming calls level.disconnect(...) and then minecraft.disconnect(new TitleScreen(), false) by hand, instead of Minecraft.disconnectFromWorld, which vanilla's PauseScreen uses. As a result: singleplayer shows the title screen while the world saves, with no 'Saving world' progress screen; leaving Hypixel lands on the title screen instead of the server list; and Realms players are not returned to RealmsMainScreen. Custom Escape Menu is on by default, so every player sees this behaviour.

  *Vorschlag:* Call this.minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE).

  </details>
- **[low · S]** Mit Custom Escape Menu öffnet F3+Esc im Einzelspieler das komplette Menü statt der menülosen Pause, die Vanilla für Screenshots und Aufnahmen bietet.  
  → In GuiMixin nur ersetzen, wenn ((PauseScreen) screen).showsPauseMenu() true ist.  
  `mixin/GuiMixin.java:37` · C7-2, C5-9
  <details><summary>Details</summary>

  **F3+Esc ('pause without menu') now opens the full custom escape menu**

  In singleplayer, Gui.setPauseScreen(pauseOnly=true, local=true) creates new PauseScreen(false), whose showsPauseMenu() is false. Vanilla uses that for a menu-less pause, e.g. for screenshots or recording. replacePauseScreen swaps any exact PauseScreen for CustomPauseScreen without looking at showsPauseMenu(). With Custom Escape Menu on (the default), F3+Esc therefore shows the whole custom menu and the vanilla feature is lost.

  *Vorschlag:* Only replace when the screen asks for a menu: `screen.getClass() == PauseScreen.class && ((PauseScreen) screen).showsPauseMenu() && cfg.customEscapeMenuEnabled`.

  </details>

### Statistik & Speicherung (9)

- **[medium · S]** AtomicJsonFile benennt die Temp-Datei ohne vorheriges fsync um und schreibt auch die .bak im selben Moment ungesichert, sodass ein Stromausfall oder Bluescreen kurz nach einem Boss-Kill alpaka-stats.json und Backup beide leer hinterlassen kann und die komplette Slayer-Historie verloren ist (bei alpaka.json die Einstellungen).  
  → Die Temp-Datei über einen FileChannel schreiben und vor Files.move force(true) aufrufen, dasselbe für die .bak, und einen eindeutigen Temp-Namen verwenden.  
  `config/AtomicJsonFile.java:45` · C6-3
  <details><summary>Details</summary>

  **Atomic write never fsyncs: after a power loss or OS crash the record and its .bak can both be empty**

  write() calls Files.writeString(temp) and then renames temp over the target, with no FileChannel.force() before the rename. On NTFS and APFS (most players) the rename can reach disk before the file's data does. A BSOD or power cut within the OS write-back window can then leave alpaka-stats.json zero-filled or truncated. AlpakaStats.save() writes the .bak in the same moment, so both files may be damaged. On the next start load() finds neither readable, begins from an empty record, and the first boss kill overwrites the damaged file (see C6-4): the lifetime history is gone. alpaka.json uses the same path and would be quarantined and reset to defaults. Separately, the temp name is fixed (`<name>.tmp`). Two instances saving at once after tryLock gave up, or on a drive that refuses locks, write the same temp file concurrently and can move interleaved JSON into place.

  *Vorschlag:* Write the temp through a FileChannel and call channel.force(true) before Files.move. Ideally also fsync the directory on POSIX. Make the temp name unique, e.g. Files.createTempFile(dir, name, ".tmp"). Apply the same force to the .bak copy in backup().

  </details>
- **[medium · M]** Ist alpaka-stats.json vorhanden, aber nicht lesbar (halb synchronisiert, Sync-Konflikt, kurzer IO-Fehler), behandelt AlpakaStats sie wie eine fehlende Datei, sodass der Merge entfällt und der nächste Kill die einzige reparierbare Kopie überschreibt, ohne sie wie bei alpaka.json in Quarantäne zu verschieben oder den Spieler zu informieren.  
  → Zwischen 'fehlt' und 'unlesbar' unterscheiden und eine unlesbare Datei vor jedem Schreiben per AtomicJsonFile.quarantine sichern, mit Chat-Hinweis auf die .corrupt-Datei.  
  `config/AlpakaStats.java:557` · C6-4
  <details><summary>Details</summary>

  **A stats file that exists but cannot be parsed is silently overwritten (no quarantine, unlike AlpakaConfig)**

  read() returns null both for "no file" and for "file present but unreadable". In save(), a null onDisk means no merge and no backup, and AtomicJsonFile.write then replaces the damaged file with memory. In load() (lines 317-325), an unreadable file with no usable .bak leaves the fresh empty instance in place. Scenario: a sync conflict, a half-synced cloud copy, or a C6-3 power loss leaves a truncated alpaka-stats.json. The session starts empty or from an older .bak, and the next kill's save destroys the only copy that could have been repaired by hand. AlpakaConfig already handles this case with AtomicJsonFile.quarantine.

  *Vorschlag:* Tell "missing" apart from "unreadable" (e.g. a read result type, or check file.exists() when read returns null). When the file exists but does not parse, call AtomicJsonFile.quarantine(file) before anything is written, both in load() and in save(), and post a chat line naming the .corrupt-<time> file.

  </details>
- **[low · M]** Das Speichern der Slayer-Statistik liest, mergt und schreibt die ganze Datei synchron im Client-Tick, oft auf einem Cloud-Laufwerk, und wartet bei einem Sperrkonflikt bis zu 10×10 ms per Thread.sleep, sodass das Spiel genau beim Boss-Kill hängen kann.  
  → Den Datensatz im Client-Thread serialisieren und Lock, Lesen, Mergen und Schreiben auf einem Single-Thread-Executor erledigen, mindestens aber die Sleep-Wiederholungen streichen.  
  `config/AlpakaStats.java:598` · C6-16, X1-27
  <details><summary>Details</summary>

  **Stats save can stall the client thread for 100 ms or more**

  save() runs on the client tick, at the moment a boss dies. It re-reads and parses the file, copies the .bak, pretty-prints the record and moves it into place, and tryLock() can Thread.sleep(10) up to ten times while another instance holds the lock. On a cloud drive (FUSE / Files On-Demand) each of these is a network-backed operation. The comment on markDirty already notes the hitch this caused when saves happened three times per kill. One save is better, but it is still blocking IO on the render/client thread.

  *Vorschlag:* Serialise the merged record on the client thread and do the lock, read and write on a single-thread executor. The merge would then fold disk data back on the next tick, which needs care. At minimum, drop the sleep-based lock retry to a single tryLock and go ahead unlocked.

  </details>
- **[low · S]** Wählt man mit '/alpakastats folder ...' einen neuen Ordner, während noch auf einen fehlenden gewartet wird, meldet die Mod den gerade gewählten Ordner als 'not available - drive not mounted?' und zwei Sekunden später als 'available again', also zwei widersprüchliche Meldungen.  
  → Eine Methode wie AlpakaStats.onDirectoryChanged() einführen, die nextStoreCheckNanos zurücksetzt und awaitingStore sofort neu prüft, und sie in setStatsDirectory vor save() aufrufen.  
  `client/AlpakaClient.java:364` · C6-14
  <details><summary>Details</summary>

  **`/alpakastats folder ...` while waiting for a missing folder reports the new folder as unavailable**

  While awaitingStore is set, the tick calls retryLoadIfAwaiting() every tick, so nextStoreCheckNanos is almost always less than 2 s in the future. setStatsDirectory then calls AlpakaStats.save(), whose retry returns early (rate-limited); awaitingStore stays true and the save fails. printStatsLocation() then prints "folder not available - drive not mounted?" for the folder just chosen, e.g. the default one. Within 2 s the tick loads it and prints "Slayer stats folder is available again", so the player gets two contradictory messages.

  *Vorschlag:* Add something like AlpakaStats.onDirectoryChanged() that resets nextStoreCheckNanos (and re-evaluates awaitingStore immediately), and call it from setStatsDirectory before save().

  </details>
- **[low · S]** Die Warnungen zu fehlendem Statistik-Ordner und fehlgeschlagenem Speichern verbrauchen ihre 5-Minuten-Sperre auch im Titelbildschirm, wo sendModMessage sie verwirft, sodass man nach dem Wiederbeitritt bis zu fünf Minuten mit scheiternden Speichervorgängen ohne Hinweis spielt.  
  → Vor dem Setzen des Zeitstempels abbrechen, wenn Minecraft.getInstance().player null ist.  
  `config/AlpakaStats.java:431` · C6-15
  <details><summary>Details</summary>

  **Save and missing-folder warnings are rate-limited even when nobody could see them**

  warnStoreMissing (line 431) and warnSaveFailed (line 622) set their last-notice timestamps before sendModMessage. sendModMessage silently drops the line when mc.player is null. flushIfDirty runs in the title screen too, for example after disconnecting with unsaved kills. A warning sent there is never seen, yet it still blocks the next one for five minutes. The player rejoins and can grind for up to five minutes with failing saves and no chat warning, which is exactly what warnSaveFailed's comment says must not happen.

  *Vorschlag:* Return early when Minecraft.getInstance().player == null before updating the timestamp, so the notice goes out on the first attempt after joining.

  </details>
- **[low · S]** '/alpakadebug' meldet bei noch nicht angekündigtem Profil 'kills are filed under the placeholder', obwohl AlpakaStats die Kills dann dem zuletzt bekannten Profil zuordnet, sodass ein Spieler, der falsche Kill-Zahlen untersucht, das Gegenteil der Wahrheit liest.  
  → Das tatsächlich aufgelöste Profil samt Herkunft (angekündigt, gemerkt oder Platzhalter) über einen Getter wie currentProfileKey() aus AlpakaStats ausgeben.  
  `utils/AlpakaDiagnostics.kt:51` · C6-17
  <details><summary>Details</summary>

  **/alpakadebug says kills go to the placeholder when they actually go to the last known profile**

  Diagnostics reads SkyblockProfileTracker.current and prints "not announced yet - kills are filed under the placeholder" whenever it is null. AlpakaStats.current() actually uses currentFor(account) and then Account.lastProfile, so after any rejoin the kills go to the remembered profile. The check also ignores whether the announced profile belongs to the current account. A player debugging a wrong kill count is told the opposite of what is happening.

  *Vorschlag:* Expose AlpakaStats' resolved profile (e.g. a public `currentProfileKey()` returning the profile and whether it was announced, remembered or the placeholder) and print that.

  </details>
- **[low · M]** Ein kurzzeitiger Lesefehler bei alpaka.json (Virenscanner oder Sync-Client hält die Datei beim Start offen) wird wie eine beschädigte Datei behandelt: Die Mod meldet 'damaged', lädt das Backup vom vorigen Start und überschreibt später die intakte Datei, womit alle Einstellungsänderungen der letzten Sitzung verloren gehen.  
  → IO-Fehler von Parse-Fehlern trennen, bei IO-Fehlern kurz erneut versuchen und sonst ohne Quarantäne und ohne Speichern in dieser Sitzung weiterlaufen, und nur unparsbaren Text in Quarantäne schieben.  
  `config/AlpakaConfig.java:921` · D2-5
  <details><summary>Details</summary>

  **A short-lived read error is treated as a damaged file, which can overwrite good settings**

  read() catches every Exception and returns null, so an IOException from Files.readString is handled like broken JSON. Examples are a Windows sharing violation while an antivirus or sync client has alpaka.json open, or an AccessDeniedException. load() then calls recoverUnreadable(), which moves the file aside (or fails to, if it is still locked), takes the backup from the previous start, tells the player the file was 'damaged', and calls save(). Once the lock is released, the atomic move replaces the intact alpaka.json with the older backup, so every change from the last session is lost. With no backup present, defaults are written instead.

  *Vorschlag:* Treat IO failures differently from parse failures. On an IOException other than CharacterCodingException, retry a few times with a short sleep. If it still fails, run on defaults or the backup in memory, but do not quarantine the file and do not save this session (for example, a static flag checked in save()), and say so in the notice. Quarantine and overwrite only when the text was read but could not be parsed.

  </details>
- **[low · S]** Die Umstellung der Menu Accent Color von Gold auf Türkis läuft ohne Erledigt-Flag bei jedem Start, sodass ein Spieler, der das alte Gold E5B849 bewusst wieder wählt, nach jedem Neustart wieder Türkis hat.  
  → Die Migration wie menuFontNunitoApplied mit einem einmaligen Flag (z. B. accentTealApplied) absichern oder beide in eine configVersion mit geordneten Schritten zusammenführen.  
  `config/AlpakaConfig.java:862` · D2-13
  <details><summary>Details</summary>

  **Accent migration runs on every launch, so the old gold can't be picked again**

  The gold-to-teal accent migration has no done-flag. Unlike the new font migration, it runs on every load(). A player who wants the 1.x gold back and enters E5B849 in the colour picker sees it switch to teal on every restart.

  *Vorschlag:* Guard it with a one-shot flag the way menuFontNunitoApplied does (for example accentTealApplied), or fold both into one configVersion int with ordered steps.

  </details>
- **[low · S]** Ein NaN- oder Infinity-Wert in alpaka.json (von Hand eingetragen oder durch Sync beschädigt) wird beim Laden akzeptiert, lässt aber jedes spätere Speichern mit einer nur geloggten Exception scheitern, sodass keine Einstellungsänderung mehr auf der Platte landet.  
  → In load() nicht-endliche Float-Felder auf die Standardwerte zurücksetzen, NaN in ConfigOption.setFloat abweisen und optional serializeSpecialFloatingPointValues() aktivieren.  
  `config/AlpakaConfig.java:18` · D2-14
  <details><summary>Details</summary>

  **A NaN or Infinity float makes every later save fail silently; load() checks no values**

  GSON is built without serializeSpecialFloatingPointValues(). fromJson reads leniently and accepts NaN, but toJson throws IllegalArgumentException for a non-finite float. save() catches that and only logs it. One non-finite value, from a hand-edited or sync-mangled alpaka.json, or a slider normalised over a zero-width track (setFloat's Math.max/min clamp passes NaN through), stops every later setting change from reaching disk with no notice to the player. NaN also slips through the Mth.clamp and Math.max guards at the call sites. load() does no validation pass.

  *Vorschlag:* Add a sanitise step in load() that resets non-finite float fields to the defaults of a fresh AlpakaConfig (reflection over float fields is enough), and reject NaN in ConfigOption.setFloat. Optionally use serializeSpecialFloatingPointValues() so a bad value cannot block saving.

  </details>

### Slayer-Tracking (6)

- **[low · S]** Ist der 'Slayer Drop Tracker' aus, kehrt onChat schon vor der 'SLAYER QUEST COMPLETE!'-Erkennung und vor offerSinceReply zurück. Session HUD, Boss Timer und XP bekommen dann keine Chat-Kills mehr: abgeleitete Kills warten immer die volle Sekunde, und ein Tod in dieser Zeit verwirft sie. Auch 'Offer Replies To !since' tut dann ohne Hinweis nichts.  
  → Den QUEST_COMPLETE-Zweig und offerSinceReply vor das slayerDropTrackerEnabled-Gate ziehen (countKill prüft den Schalter selbst) oder die Optionen in ConfigTree als Einstellungen des Drop Trackers zeigen.  
  `features/slayer/SlayerDropTracker.java:324` · C2-3, C2-10
  <details><summary>Details</summary>

  **Chat kill signal for session, timer and XP only works while the Drop Tracker is on**

  onChat returns at line 324 when slayerDropTrackerEnabled is off. The QUEST_COMPLETE_PATTERN branch at 345, which feeds SlayerQuestDetector.onChatKill, comes after that return. The comment at 341-344 says this path exists so 'session, timer and XP see it too', and those are separate features (Slayer HUD, Boss Timer). The comment at 319-321 already fixed the same coupling for the void lines. Scenario: the player switches the Drop Tracker off but keeps the Slayer HUD and timer. A kill the sidebar misses is no longer reported to them. An inferred kill is also no longer confirmed by chat, so it always waits the full KILL_CONFIRM_MS, and a death in that second wrongly vetoes it.

  *Vorschlag:* Move the QUEST_COMPLETE_PATTERN check up next to handleQuestVoided, ahead of the drop-tracker gate. countKill already applies its own slayerDropTrackerEnabled check.

  </details>
- **[low · S]** 'Avg Boss' im Slayer Session HUD rechnet killedAtMs − bossStartMs auch für ungetimte Kills ein und behält nach Tod, Abbruch oder Weltwechsel den alten Kampfstart. Dann trägt ein einziger Kill mehrere Minuten in den Durchschnitt ein und verfälscht ihn für den Rest der Session.  
  → Das timed-Flag aus SlayerDropTracker.tick durchreichen und nur getimte Kills aufsummieren; bossStartMs bei onQuestVoided und beim Weltwechsel zurücksetzen.  
  `features/slayer/SlayerSessionTracker.kt:298` · C2-5
  <details><summary>Details</summary>

  **Session boss time ignores the timed flag and keeps a stale start after a void**

  onBossKilled adds `killedAtMs - bossStartMs` whenever bossStartMs != 0. Two cases go wrong. (1) bossStartMs is never cleared when a quest is voided (death/cancel only calls SlayerTimer.clear) or when the world changes. (2) The detector's `timed` flag, which SlayerTimer honours, is not passed in. Scenario: a fight starts (bossStartMs = T0) and the player dies, so the quest fails. They grind a new quest and the next boss dies between two sidebar samples (GRINDING->SLAIN, timed=false). The session adds now - T0, several minutes, as one boss time, and 'Avg Boss' on the HUD is wrong for the rest of the session. Unverified spawns (fight first seen from NONE after joining) also feed partial fights into the average.

  *Vorschlag:* Pass `killTimed` through (SlayerDropTracker.tick already has it) and only accumulate when timed. Reset bossStartMs from SlayerQuestDetector.onQuestVoided and on level change, the same way SlayerTimer is cleared.

  </details>
- **[low · S]** checkDeath verwirft die Quest bei jedem eigenen Tod, anders als der Chat-Pfad ohne Prüfung auf deathCanVoidQuest. Stirbt man z. B. zwei Sekunden nach dem Boss-Kill an einem Mob, verschwindet die gerade angezeigte Bosszeit aus dem Boss-HUD. Außerdem nennt die KDoc die Gesundheits-Erkennung noch 'early enough', obwohl sie laut SlayerDropTracker unzuverlässig ist.  
  → Mit if (dead && !wasDead && deathCanVoidQuest) absichern und die KDoc so umschreiben, dass sie die Erkennung als zweites Netz beschreibt.  
  `features/slayer/SlayerQuestDetector.kt:337` · C2-12
  <details><summary>Details</summary>

  **checkDeath voids on every death, unlike the chat death path; its KDoc is stale**

  The chat route only voids when deathCanVoidQuest is true, because 'a death while grinding mobs fails nothing'. checkDeath calls onQuestVoided() on any local death. That runs SlayerTimer.clear(), which wipes the lingering finished time from the Boss HUD and resets the entity tracker, and it opens the 10s VOID_GRACE window. Scenario: the boss dies (stated kill), the time shows, and the player dies to a mob 2s later; the result disappears. The KDoc (326-333) calls health-based detection 'early enough', while SlayerDropTracker.PLAYER_DEATH_PATTERN's doc says it 'was seen to miss it' and that the chat lines are now the reliable signal.

  *Vorschlag:* Guard with `if (dead && !wasDead && deathCanVoidQuest)` and rewrite the KDoc to describe it as the secondary net, matching SlayerDropTracker.

  </details>
- **[low · S]** Im SlayerQuestDetector sind mehrere Kommentare zur Abtastrate veraltet (250 ms, Begründung von REFRESH_MS). Steht die Quest nur in der Tab-Liste, wird diese auch im Kampf nur einmal pro Sekunde gelesen, sodass Spawn und Kill bis zu 1 s zu spät erkannt werden – genau die Fehlergrenze des Timers.  
  → Die drei Kommentare korrigieren und die Tab-Liste mit ACTIVE_QUEST_REFRESH_MS lesen, solange die Quest zuletzt von dort kam.  
  `features/slayer/SlayerQuestDetector.kt:41` · C2-13
  <details><summary>Details</summary>

  **Stale cadence comments; a quest found only on the tab list is sampled once a second**

  TAB_FALLBACK_MS says 'during a fight the quest is on the sidebar, which stays at 250ms'. But linesToScan's own doc says the quest is 'frequently missing' from the sidebar while the tab list carries it, and the active interval is now 50ms (ACTIVE_QUEST_REFRESH_MS). REFRESH_MS (16-20) still says it is short 'because a kill is detected from the progress line changing', but it is now used only when no quest is up. refresh()'s KDoc (190) says 'at most once per [REFRESH_MS]'. In practice, when the quest lives on the tab list, the cached tab lines are refreshed only every 1s even while activeType != null. So spawn and kill transitions are seen up to 1s late, which is the timer's sidebar fallback error bar.

  *Vorschlag:* Update the three comments. Consider using ACTIVE_QUEST_REFRESH_MS for the tab read while the quest was last found on the tab list.

  </details>
- **[low · S]** Der 'Slayer Rare Drop Sound' bleibt bei 'PRAY TO RNGESUS DROP!', der seltensten Stufe, meist stumm, spielt dafür aber bei jedem RARE/VERY RARE DROP aus Fishing oder Dungeons. Bei Slayer-Drops fällt er ganz weg, sobald der Drop Tracker aus ist.  
  → 'pray to rngesus drop!' in den Insane-Zweig aufnehmen, auf eine aktive Slayer-Quest prüfen und den Sound vor das Drop-Tracker-Gate ziehen.  
  `features/slayer/SlayerDropTracker.java:326` · C2-7
  <details><summary>Details</summary>

  **Rare drop sound skips the rarest tier and fires for non-slayer drops**

  SERVER_DROP_PATTERN accepts 'PRAY TO RNGESUS DROP!'. The branch then only plays a sound if the line contains 'insane drop!', 'crazy rare drop!', one of three item names, or 'rare drop!'. 'pray to rngesus drop!' contains none of these, so the rarest tier plays nothing unless the item happens to be one of the three listed. There is no slayer check either: the option is named and described as 'Slayer Rare Drop Sound' ('when getting a rare Slayer loot drop'), yet every RARE/VERY RARE drop from fishing, dungeons or pests plays it. Meanwhile, slayer drops lose the sound entirely when the Drop Tracker is switched off (gate at line 324). `|| lower.contains("very rare drop!")` is redundant with the 'rare drop!' test.

  *Vorschlag:* Add 'pray to rngesus drop!' to the insane branch. Decide whether the sound is slayer-only; if so, require SlayerQuestDetector.currentOrRecent() != null, as the DROP_PATTERN branch does. Move the sound ahead of the drop-tracker gate so it follows only its own switch.

  </details>
- **[low · S]** Schaltet man den 'Slayer Boss Timer' mitten im Kampf aus und nach dem Kill wieder ein, bleibt der alte Kampfstart stehen. Das Boss-HUD zeigt dann eine minutenlang weiterlaufende Uhr, oder der nächste Kill wird ab diesem veralteten Start gemessen.  
  → Im deaktivierten Pfad von onBossKilled clear() aufrufen oder den laufenden Kampf in tick() löschen, solange der Schalter aus ist.  
  `features/slayer/SlayerTimer.kt:174` · C2-11
  <details><summary>Details</summary>

  **Turning the timer off mid-fight leaves a fight running that reappears when it is turned back on**

  onBossKilled returns when slayerTimerEnabled is false without clearing sidebarStartMs/startMs, and tick() returns before doing anything either. If the timer is switched off during a fight and the boss dies, the fight state stays. When the timer is switched back on, tick() falls back to startMs = sidebarStartMs after 5s, and the Boss HUD shows a running clock of many minutes for a fight that ended. A kill reported before a new spawn is then timed from that stale start.

  *Vorschlag:* Call clear() (not only SlayerBossEntityTracker.reset()) on the disabled path of onBossKilled, or clear the running fight in tick() whenever the switch is off.

  </details>

### Sounds & Eingabe (6)

- **[low · S]** Der Scroll-Hook sitzt am Anfang von MouseHandler.onScroll und bekommt das rohe GLFW-Delta, sodass Chat Peek Discrete Scrolling und Mausrad-Empfindlichkeit ignoriert; auf einem Mac-Trackpad mit Discrete Scrolling bewegt sich der gepeekte Chat bei kleinen Wischern gar nicht, während der offene Chat 7 Zeilen springt.  
  → Das Delta wie Vanilla berechnen ((discreteMouseScroll ? signum(y) : y) * mouseWheelSensitivity) oder den Hook hinter die Vanilla-Berechnung verlegen.  
  `mixin/MouseHandlerMixin.java:25` · C7-1
  <details><summary>Details</summary>

  **Scroll hooks get the raw GLFW delta, so Peek Chat ignores the Discrete Scrolling and sensitivity options**

  The hook runs at HEAD of MouseHandler.onScroll. Vanilla only scales the delta after that point: (discreteMouseScroll ? signum(y) : y) * mouseWheelSensitivity. ChatScreen.mouseScrolled gets the scaled value, but ChatPeekFeature.onScroll, ScrollableTooltipsFeature.onScroll and ZoomFeature get the raw yoffset. ChatPeekFeature says it has the 'same feel as the chat screen', but it does not. On a macOS trackpad with Discrete Scrolling on, a swipe reports about 0.1-0.2 per event. The chat screen scrolls 7 lines per event; peek scrolls (int)(0.2*7)=1 line, and (int)(0.1*7)=0 lines, i.e. nothing. A mouse with sensitivity set to 0.5 scrolls the chat screen 3 lines per notch and the peeked chat 7. The HEAD position also runs before vanilla's window-handle check, its 'no overlay' check and FramerateLimitTracker.onInputReceived(), so a scroll used for zoom or peek does not count as player input.

  *Vorschlag:* Before handing the delta on, compute it the way vanilla does: double d = (opts.discreteMouseScroll().get() ? Math.signum(yoffset) : yoffset) * opts.mouseWheelSensitivity().get(). Or move the hook after vanilla computes it (e.g. @ModifyVariable/@WrapOperation at the screen.mouseScrolled / scrollWheelHandler calls) so the window and overlay checks still apply. Add a short class javadoc while there; this is the only input mixin without one.

  </details>
- **[low · S]** Inventory Open/Close Sound verspricht Töne für Container-GUIs, zählt aber nur das eigene Inventar, sodass Truhen und alle Hypixel-Menüs stumm bleiben und beim Öffnen des SkyBlock-Menüs über den Stern im Inventar sogar der Schließen-Ton spielt, beim späteren Schließen dafür nichts.  
  → Jeden AbstractContainerScreen über einen gemeinsamen Helfer für alten und neuen Screen als Inventar zählen und beim Wechsel zwischen zwei Containern nichts abspielen.  
  `mixin/GuiMixin.java:66` · C7-3
  <details><summary>Details</summary>

  **Inventory open/close sound only covers the player's own inventory and plays 'close' when a container opens from it**

  The option 'Inventory Open/Close Sound' says it plays 'when opening or closing container GUIs'. The code only counts InventoryScreen and CreativeModeInventoryScreen, so chests, backpacks and every Hypixel menu (all ContainerScreen) make no sound. Also, clicking the SkyBlock Menu star in the inventory makes Hypixel open a ContainerScreen while the old screen is the InventoryScreen. That takes the isOldInventory && !isNewInventory branch and plays the close sound just as a menu opens. Closing that menu afterwards plays nothing.

  *Vorschlag:* Decide what counts as an inventory, probably `instanceof AbstractContainerScreen`, and use one helper for both the old and new screen. Moving between two containers should play nothing. Alternatively, reword the option description to match what the code does.

  </details>
- **[low · S]** Alle AlpakaStyle-Fenster (Config-Menü, Farbwähler, Bestätigungsdialog, HUD-Editor, Quick-Command-Editor und -Rad) rufen nur CustomSoundFeature.playButtonClickSound() auf, das ohne Master Custom Sounds und UI Button Click Sound (beide standardmäßig aus) nichts tut, sodass das neue Menü für neue Spieler stumm ist, während Pausen- und Hauptmenü normal klicken.  
  → Einen gemeinsamen UI-Klick-Helfer mit AbstractWidget.playButtonClickSound(mc.getSoundManager()) nutzen, den SoundEngineMixin bei aktivem Custom Sound ohnehin ersetzt, und die try/catch-Hüllen entfernen.  
  `features/sound/CustomSoundFeature.java:92` · C7-4
  <details><summary>Details</summary>

  **Every AlpakaStyle window is silent by default because its click is the optional custom chime**

  AlpakaConfigScreen.playClick, ColorPickerScreen, AlpakaConfirmScreen, HudEditorScreen, CommandWheelConfigScreen, CommandWheelScreen and the config registry's preset buttons all call CustomSoundFeature.playButtonClickSound(). That method does nothing unless customSoundsEnabled (default false, labelled work in progress) and customSoundButtonClick are both on. So for a new player the whole 2.0 config menu and its sub-windows make no click sound. CustomPauseScreen and CustomMainMenuScreen use AbstractWidget.playButtonClickSound, which plays the vanilla click, and SoundEngineMixin already swaps that for the chime when the option is on. The two sets of menus therefore sound different. Callers also wrap the call in try/catch(Throwable), which is dead code since playSound already null-checks.

  *Vorschlag:* Add one UI-click helper that plays the vanilla click (`AbstractWidget.playButtonClickSound(mc.getSoundManager())`). SoundEngineMixin's ui.button.click rule turns it into the custom chime when that is enabled. Point every AlpakaStyle window at the helper and drop the try/catch wrappers.

  </details>
- **[low · S]** Custom Sounds ersetzt Player Hurt, Successful Hit und Blaze Death auch dann, wenn der Originalton stumm wäre, sodass Spieler, die die Lautstärke-Kategorie Players oder Hostile Creatures auf 0 % stellen, die Ersatztöne trotzdem in voller Custom Sounds Volume hören.  
  → Vor den Ersatzprüfungen bei volume <= 0f den Ton unverändert lassen oder die Ersatzlautstärke mit der Kategorie-Lautstärke des Originals skalieren.  
  `mixin/SoundEngineMixin.java:95` · C7-6
  <details><summary>Details</summary>

  **Replacements fire even when the original sound would have been silent**

  muteOrReplace receives the volume vanilla has already computed: per-sound volume × the category slider × gain. It replaces the sound without checking that value. The replacement plays via SimpleSoundInstance.forUI in the UI category. A player who sets 'Players' or 'Hostile Creatures' to 0% to silence hurt, crit or blaze sounds still hears the custom hurt, hit and blaze-death sounds at full Custom Sounds volume. Likewise a server sound sent at volume 0 is turned into an audible custom one.

  *Vorschlag:* Leave the sound alone when it was already silent: `if (volume <= 0f) return volume;` before the Etherwarp and replacement checks. Or scale the replacement's volume by the original category volume.

  </details>
- **[low · S]** Zombie Remedy Sound zählt eine eigene Item-Benutzung nur 300 ms lang, der Heil-Sound kommt aber erst nach einem Server-Roundtrip, sodass Spieler ab etwa 250 ms Ping (z. B. aus Asien oder Ozeanien) immer den Vanilla-Ton hören, ohne Hinweis warum.  
  → Das Fenster an die gemessene Latenz koppeln (etwa 300 + 2 * Ping aus PlayerInfo.getLatency()) oder auf rund 1 s erweitern und die Use-Verfolgung im Javadoc von LocalAttackTracker erwähnen.  
  `features/sound/LocalAttackTracker.java:37` · C7-8
  <details><summary>Details</summary>

  **300 ms use window is shorter than a high-ping round trip, so the Zombie Remedy replacement never fires for those players**

  The cure sound comes from the server after it has processed the right-click, so it arrives one round trip after noteUse(). usedItemRecently() only accepts 300 ms. A player at 250-400 ms ping (e.g. Oceania or Asia to Hypixel) never gets the custom remedy sound and keeps the vanilla one, with nothing in the UI saying why. The class name and javadoc also describe only attack tracking; the use-tracking fields sit between the attack fields and the constructor.

  *Vorschlag:* Base the window on the measured latency, e.g. `300 + 2 * ping` from the player's PlayerInfo.getLatency(), or widen it to about 1 s; the 3-block radius check still keeps other players' remedies out. Mention item-use tracking in the class javadoc and group the fields.

  </details>
- **[low · S]** Hotbar Scroll Sound merkt sich lastSelectedSlot über Weltwechsel und Disconnects hinweg und aktualisiert ihn nicht, solange Custom Sounds aus ist, sodass beim Betreten einer Welt oder eines Hypixel-Servers mit anderem Slot oder beim Einschalten der Option ein Wechselton ohne echten Slotwechsel spielt.  
  → lastSelectedSlot in jedem Tick mit Spieler vor der Config-Prüfung aktualisieren und bei fehlendem Spieler bzw. Disconnect auf -1 zurücksetzen.  
  `features/sound/CustomSoundFeature.java:59` · C7-10, X1-28
  <details><summary>Details</summary>

  **Hotbar slot tracking goes stale while the feature is off or no player exists, so a stray equip sound plays**

  The tick handler returns before updating lastSelectedSlot when the master switch is off or there is no player, and nothing resets it on disconnect. Example: the player switches slots with Custom Sounds off, then turns them on, and the first tick plays the equip sound without any slot change. Same when joining a world or server whose held slot differs from the last one seen before disconnecting.

  *Vorschlag:* Update lastSelectedSlot on every tick that has a player, before the config check. Reset it to -1 when client.player is null, or on ClientPlayConnectionEvents.DISCONNECT.

  </details>

## Politur


### Release, Build, Doku & Tests (25)

- **[low · S]** Findet tools/ModrinthCleanup.py das Profil nicht in der Tabelle instances, sammelt der Fallback die alpaka-Einträge aller Instanzen und prüft sie gegen den falschen Profilordner, sodass DB-Einträge anderer Profile (z. B. 'Fabric 26.1.2') gelöscht werden, obwohl deren Jars noch existieren.  
  → Im Fallback den eigenen Pfad jeder Instanz aus instances verwenden oder den Fallback streichen und nur 'Profil nicht gefunden' ausgeben.  
  `tools/ModrinthCleanup.py:33` · X2-6
- **[low · S]** ModrinthCleanup.py sucht app.db nur unter %APPDATA%, obwohl build.gradle auch nach macOS und Linux deployt, sodass ./gradlew build auf dem Mac 'no app.db found' meldet und den veralteten Jar-Eintrag stehen lässt; das Mac-Deploy-Skript umgeht das nur per APPDATA-Override.  
  → Den tatsächlich benutzten Modrinth-Root aus build.gradle als Argument an das Skript übergeben oder dort dieselben drei Pfade durchprobieren.  
  `tools/ModrinthCleanup.py:16` · X2-7
- **[low · S]** Der OneClient-Deploy (-Pdeploy_target=oneclient) überschreibt jedes alpaka*.jar unter dem OneClient-Ordner samt Caches und kopiert den 26.2-Jar in alle Cluster, sodass ein 26.1.2-Cluster danach nicht mehr startet.  
  → Nur Jars und Cluster mit passendem -mc${minecraft_version}-Suffix anfassen oder auf einen konfigurierten Cluster beschränken und das rekursive Überschreiben streichen.  
  `build.gradle:274` · X2-8
- **[low · S]** CREDITS.md spricht von 'All four' Schriften, obwohl fünf ausgeliefert werden, und führt Outfit und Poppins als Bold, obwohl beide Dateien SemiBold (Gewicht 600) sind; die Name-Table von nunito_semibold.ttf meldet außerdem 'Nunito ExtraLight'.  
  → 'All five' schreiben, Outfit SemiBold und Poppins SemiBold eintragen und Nunito optional mit --update-name-table neu schneiden.  
  `CREDITS.md:9` · X2-14
- **[low · S]** menu_background.png macht mit 2,5 MB rund 64 % des Jars aus und ist ungefiltert gespeichert; ein verlustfreies Neu-Encodieren spart etwa 1,1 MB (rund 28 % des Release-Jars), und die Herkunft des Hypixel-Hub-Screenshots fehlt in CREDITS.md.  
  → Die Datei mit oxipng -o max --strip safe (oder zopflipng) neu packen und eine Herkunftszeile in CREDITS.md ergänzen.  
  `res/assets/alpaka/textures/gui/menu_background.png:1` · X2-18
- **[low · S]** Der Kommentar in gitVersion verspricht ohne git einen Fallback auf 0.0.0+unknown, doch fehlt das git-Programm, wirft providers.exec eine ExecException und die Gradle-Konfiguration bricht ab.  
  → Den Zugriff auf das exec-Ergebnis in try/catch packen und dann "0.0.0+unknown" liefern oder den Kommentar einschränken.  
  `build.gradle:23` · X2-24
- **[low · M]** Reine Chat-Logik ist ungetestet: die Überlappungsrechnung von Smooth Chat, CompactChatFeature.isFoldable/SEPARATOR (Hypixel-Trennzeilen dürfen nie zusammengefaltet werden), GuildPrefixFormatter.translateColorCodes und die Patterns des Party Invite Prompt.  
  → Parametrisierte String-Tests im Stil von ChatTabTest ergänzen und SmoothChatFeature eine paketprivate Überladung mit now und Dauer geben.  
  `features/chat/SmoothChatFeature.java:81` · C1-22
- **[low · M]** Das Parsen der Hypixel-Nachrichten im Slayer-Bereich (SlayerChatFilter, SlayerDropTracker, SkyblockProfileTracker, SlayerBossNameTag, SlayerType, LEADERBOARD_ROW, SlayerTimer.format) hat keine Tests, und SlayerQuestDetectorTest prüft nur einzelne Bosse aus frischem Zustand, weshalb C2-2 durchgerutscht ist.  
  → Die Patterns paketprivat machen, je Parser einen tabellengetriebenen Test mit den Beispielen aus den KDocs schreiben und Mehrboss-Sequenzen zu SlayerQuestDetectorTest hinzufügen.  
  `features/slayer/SlayerChatFilter.kt:47` · C2-22
- **[low · M]** Die Voxel-Traversierung des Etherwarp Overlay, die entscheidet, ob ein Warp als gültig oder blockiert angezeigt wird, hat keine Tests, obwohl sie schon den Randfall aus C3-9 (achsparalleler Strahl ab ganzzahligem Start) enthält.  
  → traverse als reine Funktion mit Solid-Prädikat herausziehen und achsparallele, negative, Start-Voxel- und Fehlstrahlen testen.  
  `features/etherwarp/EtherwarpOverlayFeature.java:227` · C3-18
- **[low · M]** Nur isNonCritDamageTag ist getestet; parseDamageTag, die Formatierung der Custom Damage Tags (samt 1000k-Grenzfehler aus C4-4) und das Markieren des eigenen Namens im Custom Name Tag haben keine Tests.  
  → Parametrisierte Tests ergänzen und format/compact den Modus als Parameter übergeben statt ihn aus AlpakaConfig.instance zu lesen.  
  `features/damagetags/DamageTagFeature.java:49` · C4-10
- **[low · M]** Die Seitenlogik des Quick Command Menu (move, add, shiftToPage, Pruning) und das Clamping von Scrollable Tooltips sind ungetestet, obwohl ein einziger Test den Fehler aus C5-2 (leeres Rad wird mit Standardbefehlen gefüllt) gefangen hätte.  
  → Die Listenoperationen als Funktionen über MutableList<MutableList<String>> ohne AlpakaConfig herausziehen und move/add/remove/prune sowie position() testen.  
  `features/wheel/CommandWheelPages.kt:78` · C5-19
- **[low · M]** Reine Logik wie EditableText (Ersetzen der Auswahl, maxLength beim Einfügen, Strg-Wortsprünge), SkyblockUtils.containsIgnoringFormatting/cleanColor, UpdateChecker.parse/compare/newest und das Floor-/Boss-Mapping von DungeonState hat keine Tests.  
  → Einfache JUnit-Tests ergänzen, die UpdateChecker-Helfer internal machen und die Zwischenablage von EditableText injizierbar machen.  
  `client/gui/EditableText.java:94` · C6-21
- **[low · M]** Die heikelsten Regeln der Custom Sounds (Zuordnung ID zu Ersatz, Container- gegen Button-Klick, 100-ms-Debounce für Player Hurt Sound, Blaze-Stummschaltung) stecken als @Unique-Statics im SoundEngineMixin und lassen sich nicht testen.  
  → Die Entscheidung in eine eigene Klasse wie features/sound/SoundRules mit Pfad, Quelle, Kontext und Uhr als Eingabe verschieben, den Mixin nur verdrahten lassen und Debounce sowie Fallbacks testen.  
  `mixin/SoundEngineMixin.java:120` · C7-18
- **[low · M]** Hex-Parsing und -Formatierung im Farbwähler (tryParseHexInput/hexOf) sowie die Drop-Index-Korrektur beim Verschieben im Quick-Command-Editor sind ungetestet, obwohl ein Test mit vorbefülltem Hex-Feld D3-1 gefangen hätte.  
  → parseHex/formatHex und die Abbildung Drop-Slot zu Move-Index als reine Helfer herausziehen und 6, 8 und andere Stellenzahlen sowie Verschiebungen vor und hinter den Ursprungsslot testen.  
  `client/ColorPickerScreen.java:108` · D3-13
- **[low · M]** Die neue Layout-Rechnung der Wide Health Bar (Zeilenzahl mit Absorption Hearts, zentrierte Position bei Center Health Bar, Platz unter Bossleisten mit dem Off-by-one aus D4-4) ist reine Ganzzahlarithmetik ohne Tests und wird nur über die Screenshot-Tour geprüft.  
  → rows, heartPosition und shownBossBars als statische reine Funktionen herausziehen und die genannten Fälle (40 HP mit und ohne Absorption, Bossleisten bei h/3) testen.  
  `features/healthbar/WideHealthBarFeature.java:80` · D4-14
- **[low · M]** clampShift, von dem jede HUD-Position abhängt, und die 'keine Überlappung'-Regel der HUD-Standardpositionen sind ungetestet; die Regel ist bereits verletzt (D5-1, D5-6), was ein Test beim Einbau der Bear-Zeile sofort gezeigt hätte.  
  → Einen kleinen Kotlin-Test für die vier Zweige von clampShift und einen HudDefaults-Test ergänzen, der alle Standardboxen auf Überschneidung prüft.  
  `client/hud/HudElement.kt:115` · D5-14
- **[low · M]** Ressourcen-Invarianten (Mixin-Klassen in alpaka.mixins.json, Font-JSONs zu GuiFont-IDs und TTFs, sounds.json zu registerSound und .ogg, Key-Parität en_us/de_de) werden nur durch einen Spielstart geprüft, obwohl ChatBlurFeatureTest das Muster schon zeigt; aktuell ist nur die Sprach-Parität (X2-15) gebrochen.  
  → Einen ResourceConsistencyTest mit Files.walk/Files.readString für diese vier Prüfungen anlegen.  
  `src/test/java/net/alpaka/addons/features/chat/ChatBlurFeatureTest.java:39` · X2-11
- **[low · S]** newest(), parse() und compare() im UpdateChecker entscheiden, ob jedem Spieler 2.0 angekündigt wird, sind aber private und ungetestet, sodass ein Fehler entweder bei jedem Start nerven oder 2.0 nie melden würde (heute ist die Logik korrekt).  
  → Die Funktionen internal machen und einen Tabellentest für 1.3.0 zu 2.0.0, Vorabversion/Draft, fehlenden -mc26.2-Jar, 2.0 gleich 2.0.0, +-Metadaten und kaputte Tags schreiben.  
  `utils/UpdateChecker.kt:106` · X2-12
- **[nit · S]** Das Mod-Icon liegt dreimal byte-gleich im Jar, wobei src/main/resources/icon.png nirgends benutzt wird; außerdem sind mob/blaze/death.ogg und random/successful_hit.ogg byte-gleich, sodass Blaze Death Sound und Successful Hit Sound identisch klingen.  
  → Das Root-icon.png löschen, MenuStyle.MOD_ICON_ID auf alpaka:icon.png zeigen lassen, alpaka_icon.png entfernen und einem der beiden Sounds eigenes Audio geben.  
  `res/icon.png:1` · X2-19, X1-30
- **[nit · S]** build.gradle legt zusätzlich org.spongepowered:mixin 0.8.5 samt eigenem Repository auf den Compile-Classpath, obwohl Loom schon sponge-mixin 0.17.4 mitbringt, und wendet maven-publish ohne publishing-Block an.  
  → Die compileOnly-Mixin-Zeile, das spongepowered-Repository und id "maven-publish" entfernen.  
  `build.gradle:68` · X2-22
- **[nit · S]** Kotlin 2.1.10 kompiliert für JVM 23, während Java auf 25 steht, und kotlin.jvm.target.validation.mode=ignore blendet diese und jede künftige Abweichung global aus.  
  → Das Kotlin-Plugin auf eine Version mit JVM_25 (2.3+) heben, jvmTarget auf JVM_25 setzen und das Validation-Override löschen.  
  `build.gradle:111` · X2-23
- **[nit · S]** tools/FixModrinthMissingMods.py ist durch ModrinthCleanup.py überholt, stürzt auf macOS/Linux mit KeyError ab, lässt verwaiste store_instance_files-Zeilen zurück und löscht 'missing'-Einträge aller Instanzen ohne Profilfilter.  
  → Das Skript löschen.  
  `tools/FixModrinthMissingMods.py:13` · X2-26
- **[nit · S]** Mit einem Filter wie ALPAKA_SCREENSHOT_TOUR=chat-rest überspringt die Screenshot-Tour die Vorbereitung früherer Stopps mit Bild, sodass etwa der Chat leer bleibt oder Starlight ohne abgeschaltetes Item-Sway aufgenommen wird und gefilterte Bilder von der vollen Tour abweichen.  
  → Abhängige Vorbereitung in eigene Stopps ohne Bild legen oder per setup-Flag markieren, oder dokumentieren, dass Filter Gruppenpräfixe wie 'chat' oder 'starlight' nennen sollen.  
  `client/DevScreenshotTour.java:217` · D3-15
- **[nit · S]** Die Icon-Tests in CustomPauseScreenTest prüfen nur 7 der 11 Icons und lassen ICON_PERSON, ICON_PEOPLE, ICON_FLASK und ICON_POWER aus, die im Hauptmenü bei Singleplayer, Multiplayer, Alpha und Quit stehen.  
  → Die vier Konstanten in beide Assertions aufnehmen oder über alle elf Icons iterieren.  
  `src/test/java/net/alpaka/addons/features/escapemenu/CustomPauseScreenTest.java:105` · X2-27
- **[nit · S]** Zwei Tests in SlayerQuestDetectorTest warten per Thread.sleep(1_100) auf die echte Uhr, was 2,2 s der 2,7 s Testlaufzeit ausmacht.  
  → SlayerQuestDetector eine injizierbare Uhr geben und sie im Test vorstellen statt zu schlafen.  
  `src/test/kotlin/net/alpaka/addons/features/slayer/SlayerQuestDetectorTest.kt:72` · X2-28

### Toter Code, Duplikate & Konsistenz (31)

- **[low · S]** AlpakaConfig.slayerBossMap dient nur noch als Quelle für den einmaligen Legacy-Import in AlpakaStats. Ohne Hinweis darauf wird es weiter in Konstruktor und load() befüllt, was sieben leere Blöcke in jede frische alpaka.json schreibt, und der Javadoc von resetToDefaults liest sich, als lägen die Slayer-Stats noch dort.  
  → Das Feld als Legacy-Import-Quelle dokumentieren, es nicht mehr vorbefüllen und den Javadoc von resetToDefaults entsprechend korrigieren.  
  `config/AlpakaConfig.java:764` · D2-15
- **[low · S]** PlayerModelRenderer (Spielermodell-HUD) schleppt die alte Fade- und Slide-Mechanik mit: dt wird berechnet und verworfen, FADE_SPEED ist ungenutzt und der Slide-Zweig unerreichbar. Die KDoc behauptet noch, das Modell gleite vom Bildschirmrand herein.  
  → alpha, FADE_SPEED, ALPHA_EPSILON, lastTimeMs, dt und den Slide-Zweig löschen und die KDoc auf 'erscheint und verschwindet' ändern.  
  `features/playermodel/PlayerModelRenderer.kt:136` · D5-7
- **[low · S]** Der @ModifyArg alpaka$pickFoilBuffer in ItemFeatureRendererMixin ändert in 26.2 nie etwas, weil die Submits schon im ersten Durchgang verbraucht sind, und sein Kommentar beschreibt den Render-Pfad von vor 26.2. Würde der Tausch je greifen, wählte er unter Fabulous den falschen Glint.  
  → alpaka$pickFoilBuffer löschen und den Kommentar auf den 26.2-Pfad umschreiben (Basis-Quads laufen immer über getVertexBuilder).  
  `mixin/ItemFeatureRendererMixin.java:50` · C3-15
- **[low · M]** SlayerDropTracker ist zugleich Tick-Zentrale für Quest-Detector, Session, Timer, HypixelMayor und XP-Leser und die Heimat von sendModMessage, das über 40 Aufrufer im ganzen Mod nutzen (Config, UpdateChecker, Befehle). ScreenshotMessageFeature baut den Präfix '§6[AA] ' sogar ein zweites Mal nach.  
  → sendModMessage samt thread-sicherer Variante in eine kleine ModChat-Utility verschieben und die Slayer-Tick-Steuerung in einen eigenen SlayerTicker auslagern.  
  `features/slayer/SlayerDropTracker.java:229` · C2-20, X1-24
- **[low · S]** WorldAgeHudRenderer.getSidebarServerId läuft selbst übers Scoreboard und setzt prefix+name+suffix neu zusammen, und SlayerDropTracker.isInRift liest den Sidebar-Titel bei jeder Drop-Zeile neu, obwohl SkyblockUtils beides pro Tick cacht.  
  → Beide über SkyblockUtils.getSidebarLines() laufen lassen (der Titel ist get(0)).  
  `features/worldage/WorldAgeHudRenderer.java:111` · X1-25
- **[low · S]** Die Blaze-Dämonennamen 'ⓆⓊⒶⓏⒾⒾ' und 'ⓉⓎⓅⒽⓄⒺⓊⓈ' stehen doppelt in CleanBlazeFeature und CustomDamageTagFeature, und lerpRgb, fract sowie die Animationsuhr sind über mehrere Features kopiert. Benennt Hypixel einen Dämon um, wird das leicht nur an einer Stelle nachgezogen.  
  → Die Namen als eine Konstante neben SlayerType.BLAZE ablegen und die Farbhelfer in eine kleine gemeinsame Util verschieben.  
  `features/blaze/CleanBlazeFeature.kt:56` · C4-11
- **[nit · S]** Es gibt vier Farbcode-Stripper mit unterschiedlichem Zeichensatz (ChatTab, CompactChatFeature, SkyblockUtils.cleanColor, SlayerDropTracker.cleanColor), und MentionNotifier und BridgeBotFormatter hängen nur dafür am Slayer-Drop-Tracker. Sichtbare Fehlklassifizierungen sind nicht belegt; es ist vor allem doppelter Code.  
  → Überall SkyblockUtils.cleanColor (oder ein gemeinsames ChatText.strip) verwenden und die privaten Patterns sowie SlayerDropTracker.cleanColor löschen.  
  `features/chat/ChatTab.java:41` · C1-18, X1-23, C6-23
- **[nit · M]** Jede Choice-Option in AlpakaConfigRegistry (z. B. menu_font, wide_health_bar_absorption, item_starlight_mode) schreibt ihr Namensarray dreimal aus: für max, für einen nie aufgerufenen Formatter und für .choices(). Copy-Paste-Fehler zwischen den Kopien bleiben so unbemerkt.  
  → Eine Factory ConfigOption.choice(id, title, desc, category, getter, setter, names, keywords) einführen, die Bereich und Auswahl aus names ableitet.  
  `client/gui/AlpakaConfigRegistry.java:387` · D2-12
- **[nit · S]** MenuStyle.ensureModIconRegistered loggt Fehler per System.err.println und printStackTrace statt über AlpakaAddons.LOGGER, und die Logger des Mods laufen unter fünf verschiedenen Namen. Die explizite Texturregistrierung ist überflüssig, weil blit die Textur selbst lädt.  
  → Über AlpakaAddons.LOGGER bzw. Kind-Logger unter 'alpaka' loggen, die überflüssige Registrierung entfernen und den Helfer private machen.  
  `features/mainmenu/MenuStyle.kt:112` · X1-31, C5-24
- **[nit · S]** Das @Unique-Feld alpaka$graphics in ChatComponentMixin wird gesetzt und geleert, aber nie gelesen, und sein Kommentar suggeriert eine Abhängigkeit, die es nicht gibt.  
  → Das Feld und beide Zuweisungen entfernen.  
  `mixin/ChatComponentMixin.java:73` · C1-15
- **[nit · S]** ChatTab.classify(Component) hat keine Aufrufer; benutzt wird nur die String-Variante.  
  → Die Component-Überladung löschen und die Doku der String-Variante auf ChatTabsFeature verweisen lassen.  
  `features/chat/ChatTab.java:91` · C1-20
- **[nit · S]** In SlayerBossEntityTracker wird 'watching' nur geschrieben, der Boolean-Rückgabewert von retryMobResolution wird ignoriert, und die KDoc 'squared' steht an MOB_RADIUS statt an MOB_RADIUS_SQ.  
  → watching entfernen, retryMobResolution Unit zurückgeben lassen und die 'squared'-Doku an MOB_RADIUS_SQ verschieben.  
  `features/slayer/SlayerBossEntityTracker.kt:126` · C2-18
- **[nit · S]** SlayerDropTracker hat drei ungenutzte Imports, ein isOnSkyblock() ohne Aufrufer und einen verwaisten Javadoc über shareButtons. In SlayerTimer.announce ist 'if (previousBest == null && !isBest) return' unerreichbar.  
  → Imports, Methode und unerreichbaren Zweig löschen und den Javadoc an shareButton(String, ChatFormatting, String, String) setzen.  
  `features/slayer/SlayerDropTracker.java:502` · C2-19
- **[nit · S]** isInSlayerArea und isInTrackerArea in SlayerSessionTracker kopieren denselben 500-ms-Sidebar-Refresh. Seine Begründung ist seit dem gemeinsamen Sidebar-Snapshot in SkyblockUtils veraltet, er bringt nur noch bis zu 500 ms Verzögerung, und resetCurrent ist falsch eingerückt.  
  → Den Refresh in eine private Funktion ziehen und den lokalen Cache entfernen oder seinen Kommentar korrigieren.  
  `features/slayer/SlayerSessionTracker.kt:150` · C2-21
- **[nit · S]** AbstractClientPlayerMixin importiert AlpakaConfig und SmoothPerspectiveFeature, ohne sie zu benutzen, und täuscht damit eine Config-Abhängigkeit vor, die es nicht gibt.  
  → Beide Imports entfernen und einen kurzen Kommentar zum Zoom-Hook ergänzen.  
  `mixin/AbstractClientPlayerMixin.java:3` · C4-12
- **[nit · S]** filterBlazeEntities und hideBlazeNameTags in EntityRendererMixin tun mehr, als ihr Name sagt, und CleanBlazeFeature.shouldHideEntityFire klingt nach einem Prädikat, verändert aber den State. CustomNameTagFeature schreibt Klassen voll qualifiziert aus, obwohl sie importiert sind.  
  → In alpaka$hideEntities, alpaka$hideNameTags und stripEntityFire umbenennen und die Imports verwenden.  
  `mixin/EntityRendererMixin.java:54` · C4-13
- **[nit · S]** WheelMesh.lerpColor und scaleAlpha schneiden ab, ModernGuiUtils.lerpColor und MenuStyle.fade runden, und MenuStyle.heroPanel mischt beide. Leser müssen raten, welcher Helfer gilt; sichtbar ist der Unterschied von höchstens einer Stufe nicht.  
  → Je einen rundenden Helfer behalten und die WheelMesh-Varianten an ihn delegieren oder löschen.  
  `features/wheel/WheelMesh.kt:329` · C5-12
- **[nit · S]** WheelMesh liegt in features.wheel, ist aber der allgemeine GUI-Geometrie-Baukasten für Hauptmenü, Pausenmenü und Config-Menü, und seine Doku spricht noch von der 'Nadel' des Wheels.  
  → Die Klasse nach client/gui verschieben (z. B. als GuiMesh) und die Doku allgemein formulieren.  
  `features/wheel/WheelMesh.kt:22` · C5-13
- **[nit · S]** MenuAvatar.draw bekommt screenWidth und screenHeight übergeben, benutzt sie aber nicht.  
  → Beide Parameter und die Argumente in CustomMainMenuScreen entfernen.  
  `features/mainmenu/MenuAvatar.kt:72` · C5-21
- **[nit · S]** CustomPauseScreen kopiert MenuStyles private Quit-Textfarbe 0xFFFCA5A5 als Magic Number und ruft quitBox mit einem leeren Label auf, das trotzdem jeden Frame zentriert gezeichnet wird.  
  → QUIT_TEXT aus MenuStyle freigeben und eine quitBox-Variante ohne Label anbieten.  
  `features/escapemenu/CustomPauseScreen.java:433` · C5-23
- **[nit · S]** SkyblockUtils liefert die Sidebar-Zeilen in HashMap-Reihenfolge, daher gibt /alpakadebug den Block 'Sidebar lines' durcheinander aus, und man kann ihn nicht mit dem Bildschirm vergleichen.  
  → Den Snapshot wie Vanilla absteigend nach Score sortieren und versteckte Einträge überspringen, oder wenigstens vor der Ausgabe sortieren.  
  `utils/SkyblockUtils.java:150` · C6-24
- **[nit · S]** UpdateChecker und HypixelMayor bauen jeweils einen eigenen HttpClient mit eigenem Selector-Thread und duplizieren Request-, Timeout- und User-Agent-Code ausgerechnet an der Stelle, die für Compliance geprüft wird.  
  → Einen gemeinsamen AlpakaHttp-Client mit einem User-Agent (inkl. ModVersion) für beide einführen.  
  `utils/UpdateChecker.kt:46` · C6-28
- **[nit · S]** ModVersion deklariert MOD_ID = "alpaka" ein zweites Mal. Weicht der Wert je von AlpakaAddons.MOD_ID ab, zeigt das Versionslabel 'unknown' und der UpdateChecker meldet nichts mehr.  
  → AlpakaAddons.MOD_ID verwenden.  
  `utils/ModVersion.java:11` · C6-30
- **[nit · S]** Der 'Boss Spawn Sound' hängt am Feld customSoundNotification (ID custom_sound_notification), und dass der Insane-Drop-Sound den Rare-Drop-Schalter mitbenutzt, steht nirgends.  
  → Das Feld in customSoundBossSpawn umbenennen (mit @SerializedName-alternate, damit alte Configs weiter laden) und eine Notiz zum geteilten Rare-Drop-Schalter ergänzen.  
  `features/sound/CustomSoundFeature.java:122` · C7-15
- **[nit · S]** CustomSoundFeature hat 13 öffentliche, veränderbare SoundEvent-Statics, die niemand von außen liest, eine pitch-Überladung, die immer 1.0 bekommt, und kein Klassen-Javadoc.  
  → Die Felder private machen, die pitch-Überladung streichen und ein Javadoc zu Master-Schalter, UI-Kanal und SoundEngineMixin schreiben.  
  `features/sound/CustomSoundFeature.java:14` · C7-16
- **[nit · S]** Die Input-Mixins (MultiPlayerGameModeMixin, GuiMixin, KeyboardInputMixin) schreiben Klassen voll qualifiziert aus, statt sie zu importieren, und mischen Handler mit und ohne alpaka$-Präfix.  
  → Imports verwenden und allen Injector-Handlern das alpaka$-Präfix geben.  
  `mixin/MultiPlayerGameModeMixin.java:29` · C7-17
- **[nit · S]** AlpakaConfigScreen prüft this.minecraft auf null, obwohl das Feld in 26.2 final und immer gesetzt ist. Dazu kommen ein überflüssiges Math.min, viele voll qualifizierte Namen und Felder, die zwischen den Methoden deklariert sind.  
  → Die Null-Checks streichen, auf Math.max(240, Math.min(640, width - 16)) vereinfachen, Imports ergänzen und die Felder in den State-Block verschieben.  
  `client/AlpakaConfigScreen.java:326` · D1-18
- **[nit · S]** Das ItemPreset-Default-Array mit 18 Argumenten steht dreimal in AlpakaConfig, obwohl der No-Arg-Konstruktor dieselben Werte setzt. commandWheelCommands kopiert CommandWheelPages.DEFAULTS, und sein Kommentar 'Read once ... and then cleared' ist irreführend.  
  → Einen Helfer defaultPresets() nutzen, commandWheelCommands mit null initialisieren und den Kommentar korrigieren.  
  `config/AlpakaConfig.java:646` · D2-16
- **[nit · S]** Im HudEditorScreen ist der Null-Zweig von statusLine() unerreichbar, und NO_SELECTION ist nur ein zweiter Name für HINTS.  
  → statusLine das ausgewählte Element als Parameter übergeben und NO_SELECTION entfernen.  
  `client/hud/HudEditorScreen.kt:381` · D3-18
- **[nit · S]** GuiFont.title() wiederholt die ID-Bildung und das computeIfAbsent-Caching aus style(), sodass jede Änderung daran doppelt gemacht werden muss.  
  → Einen privaten Helfer cached(index, title) einführen und in beiden Methoden nutzen.  
  `client/gui/GuiFont.java:113` · D4-15
- **[nit · S]** Im Slayer HUD wird Layout.labelWidth nie gelesen, und width(font) ignoriert seinen Font-Parameter (ebenso WorldAgeHudRenderer.getHeight). SlayerTimerHudRenderer.height enthält ein überflüssiges .toDouble().  
  → Das Feld und die ungenutzten Parameter entfernen und bounds() mit einem einzigen layout()-Aufruf auskommen lassen.  
  `features/slayer/SlayerHudRenderer.kt:86` · D5-12

### Veraltete Kommentare & Javadoc (27)

- **[low · S]** Die KDoc von bossesSince im Slayer-RNG-Drop-Tracker verspricht lockeres Matching der Dropnamen (Präfixe, Archfiend-Dice-Variante), der Code vergleicht aber exakt (nur ohne Groß-/Kleinschreibung), und dazu stehen an alwaysSprintEnabled, CompactChatFeature.removedIndex und SlayerDropTracker.shareButtons verwaiste Javadocs, die Maintainer in die Irre führen.  
  → KDoc auf exakten, case-insensitiven Vergleich umschreiben, bossesSince und isHeadlineDrop einen gemeinsamen Helper geben und die verwaisten Javadocs an die richtigen Member verschieben.  
  `features/slayer/SlayerRngDropTracker.kt:18` · C2-16, X1-26
- **[low · S]** Die Javadoc von RNG_METER_PATTERN im Slayer-Drop-Tracker beschreibt eine XP-Auswertung und einen Fallback, die es nicht mehr gibt, und die Prüfung in Zeile 339 läuft bei jeder Chatnachricht als Regex, ohne etwas zu verhindern.  
  → RNG_METER_PATTERN samt Prüfung löschen oder die Javadoc auf einen Satz kürzen, der erklärt, warum der Wert bewusst ignoriert wird.  
  `features/slayer/SlayerDropTracker.java:49` · C2-15
- **[low · S]** Kommentare in AvatarRendererMixin, CustomNameTagFeature und PlayerScaleFeature behaupten, 26.2 verwende einen Render-State für alle Spieler wieder und berechne den Schatten in extractRenderState, beides stimmt nicht mehr und führt beim nächsten Port in die Irre.  
  → Die Kommentare auf den Ablauf in 26.2 aktualisieren (frischer State, Schatten in finalizeRenderState) und HEAD nur mit echter Begründung behalten.  
  `mixin/AvatarRendererMixin.java:30` · C4-6
- **[nit · S]** Der Kommentar am Feld alpaka$pass im ChatComponentMixin sagt '0 Hintergrund, 1 Text', tatsächlich ist der Wert beim Hintergrund-Durchgang 1 und beim Text 2, und wer das 'korrigiert', zerstört die Höhe des Glas-Panels beim Einblenden neuer Chatzeilen.  
  → Die Feld-Doku auf 'Anzahl begonnener Durchgänge (1 = Hintergrund, 2 = Text)' ändern oder benannte Konstanten verwenden.  
  `mixin/ChatComponentMixin.java:284` · C1-14
- **[nit · S]** Laut Klassendoku von ChatTabsFeature erscheinen die eigenen Zeilen der Mod in jedem Chat-Tab, tatsächlich landen sie nur unter All und Mods, und nur dringende Party-Einladungen sind überall sichtbar.  
  → Klassendoku anpassen: Party-Einladungen auf jedem Tab, Mod-Zeilen unter All und Mods.  
  `features/chat/ChatTabsFeature.java:27` · C1-16
- **[nit · S]** Die Javadoc von CompactChatFeature.remove() steht verwaist über dem Feld removedIndex, und die Methode selbst hat keine Doku.  
  → Den Kommentar auf remove() verschieben und removedIndex/removedCount zusammen mit takeRemovedLines gruppieren.  
  `features/chat/CompactChatFeature.java:165` · C1-19
- **[nit · S]** Die Klassendoku des ChatScreenMixin erwähnt die Chat Search (Strg+F, abgefangenes Enter, Leistenfarbe) nicht, und der Handler alpaka$tabCyclesChatTabs ist nach dem Tab-Wechsel benannt, obwohl er zuerst Strg+F und Enter behandelt.  
  → Chat Search in die Klassendoku aufnehmen und den Handler umbenennen (z. B. alpaka$chatKeys) oder die Suchtasten in einen eigenen Injector auslagern.  
  `mixin/ChatScreenMixin.java:44` · C1-21
- **[nit · S]** Der Rundungskommentar in SlayerSessionTracker ist verstümmelt ('6 ist näher an der Wahrheit als 6') und sachlich falsch, denn auch Tier 2 ergibt mit 31,25 keine ganze Zahl.  
  → Den Kommentar korrigieren (Runden vermeidet die Abschneide-Verzerrung bei 6,25 und 31,25) oder streichen.  
  `features/slayer/SlayerSessionTracker.kt:347` · C2-17
- **[nit · S]** Die endFrame-Javadocs in HandItemLightingFeature und ItemMotionBlurFeature behaupten, sie liefen nach dem transluzenten Item-Pass, tatsächlich werden sie am Anfang des nächsten submitHandsWithItems aufgerufen.  
  → Die korrekte Formulierung aus HandItemStarlightFeature.endFrame übernehmen.  
  `features/viewmodel/HandItemLightingFeature.java:85` · C3-16
- **[nit · S]** Der Kommentar im EtherwarpDetector begründet die Reihenfolge der Prüfungen damit, dass isOnSkyblock() bei jedem Aufruf das Scoreboard liest, dabei ist das Ergebnis inzwischen 500 ms gecacht.  
  → Begründung umformulieren: zuletzt geprüft, weil nur diese Prüfung (höchstens alle 500 ms) die Sidebar durchgeht.  
  `features/etherwarp/EtherwarpDetector.kt:51` · C3-17
- **[nit · S]** Ein Kommentar im CustomMainMenuScreen sagt, die Stage-Werte seien bei zu schmalem Fenster null, obwohl das über das Boolean hasStage ausgedrückt wird.  
  → Kommentar auf 'hasStage ist false, wenn schmaler als STAGE_MIN_WIDTH' ändern.  
  `features/mainmenu/CustomMainMenuScreen.kt:182` · C5-22
- **[nit · S]** mayorName in HypixelMayor ist als 'für den Diagnose-Befehl' dokumentiert, aber /alpakadebug zeigt weder Bürgermeister noch aktiven Slayer-XP-Perk an, obwohl genau das einem Spieler mit zu niedrigen XP-Werten helfen würde.  
  → Mayor, Perk-Status und letzten Abruf in AlpakaDiagnostics.print() ausgeben oder den Kommentar korrigieren.  
  `utils/HypixelMayor.kt:69` · C6-19
- **[nit · S]** Der Kommentar in BlurRectRenderState beschreibt die Schatten-Kodierung falsch ('in 64steln'), und rounded_rect.vsh dekodiert mit einem fest eingetragenen 64.0, sodass eine Änderung von MAX_SHADOW_PX alle Schatten unbemerkt falsch skaliert.  
  → Den Kommentar auf 'Anteil an MAX_SHADOW_PX' korrigieren und auf beiden Seiten einen Hinweis 'synchron halten mit' setzen.  
  `client/gui/BlurRectRenderState.java:104` · C6-25
- **[nit · S]** Die Klassendokus von RenderPipelinesAccessor und RenderTypeInvoker nennen nur das Block-Overlay, obwohl inzwischen auch die GUI-Pipelines, HandItemStarlightFeature und ItemMotionBlurFeature sie nutzen.  
  → Beide Dokus allgemein formulieren und die Nutzer per {@link} auflisten.  
  `mixin/RenderPipelinesAccessor.java:10` · C6-26
- **[nit · S]** PloppAnimation, die gemeinsame Öffnungsanimation aller AlpakaStyle-Menüs, hat keine Klassendoku und beschriftet ihre Kurven falsch: ease-out-back statt 'elastisch' und exponentielle Annäherung statt 'linear/kubisch'.  
  → Die Kurven korrekt benennen und eine Klassen-Javadoc ergänzen.  
  `client/gui/PloppAnimation.java:7` · C6-27
- **[nit · S]** Die Javadoc von AbstractSoundInstanceAccessor begründet den Zugriff auf das rohe Pitch-Feld mit einer Entscheidung am Methodenanfang, die es nicht mehr gibt, der eigentliche Grund ist, dass getPitch() die Zufallsvariation einrechnet und einen Zufallswert verbraucht.  
  → Den Kommentar auf den tatsächlichen Grund umschreiben.  
  `mixin/AbstractSoundInstanceAccessor.java:11` · C7-11
- **[nit · S]** Der Klassenkommentar des SoundEngineMixin stellt die Reihenfolge falsch dar, denn in 26.2 werden die Untertitel-Listener erst nach calculateVolume benachrichtigt und nicht davor.  
  → Umformulieren: Die Untertitel-Listener werden direkt danach benachrichtigt, unabhängig von der Lautstärke.  
  `mixin/SoundEngineMixin.java:31` · C7-12
- **[nit · S]** Die Javadoc von CustomSoundFeature.playDamageSound verweist noch auf den entfernten Player.getHurtSound-Hook, obwohl der Sound nur noch über die Hurt-Regel im SoundEngineMixin abgespielt wird.  
  → Den Kommentar so ändern, dass er den Ersatz der entity.player.*-Hurt-Sounds im SoundEngineMixin beschreibt.  
  `features/sound/CustomSoundFeature.java:107` · C7-13
- **[nit · S]** alpaka$chatTabsWhilePeeking im HudMixin zeichnet neben der Tab-Leiste beim Peeken auch den Filter-Chip, der nur ohne Peeken erscheint, doch Name und Javadoc verschweigen das, sodass man den Chip dort nicht sucht.  
  → Die Methode umbenennen (z. B. alpaka$chatTabsOverHudChat) und beide Fälle dokumentieren.  
  `mixin/HudMixin.java:73` · C7-14
- **[nit · S]** Die Feldkommentare in AlpakaConfig sind nicht an das Redesign angepasst: chatBlurStrength ist längst die gemeinsame Einstellung HUD Style > Blur Strength (der JSON-Key heißt nur wegen 1.x noch nach dem Chat), und menuFontNunitoApplied wird bei jedem ersten Start unter 2.0 gesetzt, nicht nur wenn die Schrift umgestellt wurde.  
  → Beide Kommentare umschreiben, einschließlich eines Hinweises auf den alten JSON-Key.  
  `config/AlpakaConfig.java:452` · D2-18
- **[nit · S]** Kleinere Kommentarfehler in AlpakaConfig: notificationCorner nennt nur vier Ecken (Top Center fehlt), alwaysSprintEnabled hat zwei Javadocs hintereinander, allowApiCalls zählt 'drei Dinge' mit vier Punkten auf, und '// Slayer boss timer' steht rund 40 Zeilen zu früh.  
  → Top Center ergänzen, die Sprint-Javadocs zusammenführen, die Aufzählung klären und den Abschnittskommentar zu slayerTimerEnabled verschieben.  
  `config/AlpakaConfig.java:121` · D2-19
- **[nit · S]** In DevScreenshotTour stehen mehrere Kommentare über den falschen Methoden bzw. Stopps: die chatBurst-Doku an starlightGlint, die actionBar-Doku an topCenterNotice und der Healthbar-Kommentar über den Notice-Stopps.  
  → Die Kommentare an die richtigen Stellen verschieben und starlightGlint sowie topCenterNotice eigene Einzeiler geben.  
  `client/DevScreenshotTour.java:292` · D3-16
- **[nit · S]** Die Klassendoku des HUD Editor beschreibt eine Leiste mit Auswahl und Position links, tatsächlich steht dort der feste Titel 'HUD Editor', und die Auswahl erscheint in der Statuszeile darunter.  
  → Doku anpassen: Titel links, Reset, Reset All und Done rechts, Auswahl bzw. Hinweise in der Zeile darunter.  
  `client/hud/HudEditorScreen.kt:27` · D3-17
- **[nit · S]** Der Kommentar zu AlpakaStyle.gradientTrack spricht von runden Endkappen, gezeichnet werden aber nur eckige Segmente in einer abgerundeten Haarlinie.  
  → Den Kommentar auf das tatsächlich Gezeichnete ändern oder die Kappen ergänzen.  
  `client/gui/AlpakaStyle.java:174` · D3-20
- **[nit · S]** ChatBlurFeature.ensureTarget() und die Klassendoku behaupten, im Frame nach einem Target-Wechsel würden die Panels ohne Blur gezeichnet, das gilt aber nur für den verzögerten Pfad im Hauptmenü.  
  → Beide Kommentare auf den verzögerten Hauptmenü-Pfad einschränken.  
  `features/chat/ChatBlurFeature.java:283` · D4-13
- **[nit · S]** Laut KDoc von cachedDayComponent alloziert das World Age (Day) HUD pro Frame nichts mehr, doch mit Smooth Font bauen HudPanel.text und GuiFont.restyle jeden Frame neue Komponenten, und getWorldDay läuft dreimal statt wie kommentiert einmal pro Frame.  
  → Die gestylte Komponente cachen (siehe PERF-WA-1) oder beide Kommentare an das tatsächliche Verhalten anpassen.  
  `features/worldage/WorldAgeHudRenderer.java:47` · D5-10
- **[nit · S]** HudElement.id ist als 'für Logging und Gleichheit im Editor' dokumentiert, wird aber nirgends gelesen, denn der HUD Editor vergleicht Elemente per Identität.  
  → id entfernen oder als derzeit ungenutzt dokumentieren und per Test die Eindeutigkeit der IDs sichern.  
  `client/hud/HudElement.kt:30` · D5-13

### Rendering, Schriften & Mixin-Kompatibilität (5)

- **[low · S]** Beim beleuchteten Geisterbild von Swing Motion Blur fehlt `useOverlay()` im RenderSetup, obwohl der geklonte Item-Shader Sampler1 liest; meist bleibt zufällig eine passende Textur gebunden, das Ergebnis hängt aber von der Zeichenreihenfolge ab.  
  → `.useOverlay()` im RenderSetup des beleuchteten Geists ergänzen, wie bei Vanillas itemTranslucent.  
  `features/viewmodel/ItemMotionBlurFeature.java:337` · C3-5
- **[low · S]** Mit Enable Viewmodel Modifiers schwingt der leere Hauptarm (und der Arm unter einer einhändig gehaltenen Karte) mit, wenn die andere Hand schwingt, weil modifySwingProgressHand nicht prüft, welche Hand schwingt.  
  → Den Schwungfortschritt nur ersetzen, wenn der gezeichnete Arm zur schwingenden Hand gehört (über player.getMainArm() mit swingingArm abgleichen).  
  `mixin/ItemInHandRendererMixin.java:201` · C3-6
- **[low · M]** Starlight wird auf zweilagigen Items (gefärbte Lederrüstung, Tränke, Spawn-Eier) doppelt aufgetragen, weil beide deckungsgleichen Schichten den EQUAL-Tiefentest bestehen, sodass diese Items bei gleicher Stärke deutlich kräftiger wirken.  
  → Den Starlight-Pass nur einmal pro Fläche zeichnen, etwa nur für die Quads der ersten Texturschicht.  
  `mixin/ItemFeatureRendererMixin.java:79` · C3-12
- **[low · S]** Die Pipelines für Swing Motion Blur werden erst beim ersten Schwung (und nach jedem Ressourcen-Reload) gebaut und nicht vorab registriert, was genau beim ersten Trail einen kleinen Ruckler durch das Shader-Linken erzeugt.  
  → Eine init() ergänzen, die alle Geister-Typen beim Client-Start baut und per alpaka$register registriert, analog zu HandItemStarlightFeature.init().  
  `features/viewmodel/ItemMotionBlurFeature.java:316` · C3-13
- **[nit · S]** Der Sensei Hat überspringt die root()-Transformation des Modells, die Vanillas Kopf-Layer anwenden; heute unsichtbar, aber Animations- oder Emote-Mods, die root bewegen, lassen den Hut neben dem Kopf schweben.  
  → Vor head.translateAndRotate auch parentModel.root().translateAndRotate(poseStack) aufrufen.  
  `features/cosmetics/ChromaHatLayer.kt:57` · C4-8

### HUDs & Benachrichtigungen (6)

- **[low · S]** AlpakaNotifications zählt die sichtbaren Bossleisten bei bestimmten GUI-Höhen (z. B. 150–152 px) um eins zu hoch, sodass Benachrichtigungen oben mittig 19 px tiefer sitzen als nötig.  
  → Wie Vanilla aufrunden (maxOf(1, (guiHeight/3 − 12 + 18) / 19)), am besten als reine Funktion mit Unit-Test.  
  `features/notification/AlpakaNotifications.kt:90` · D4-4
- **[low · S]** Bei sichtbaren Bossleisten gleiten Benachrichtigungen oben mittig nicht mehr vom Bildschirmrand herein, sondern starten mitten über den Bossleisten und rutschen beim Verschwinden wieder darüber, und der zugehörige Kommentar stimmt nicht mehr.  
  → Den Startpunkt um settledY verschieben (y = settledY − hidden·(settledY + height)) und den Kommentar anpassen.  
  `features/notification/AlpakaNotifications.kt:303` · D4-5
- **[low · S]** Schaltet man Smooth Font der Benachrichtigungen (oder den HUD-Style-Hauptschalter) um, während die Testbenachrichtigung sichtbar ist, wechselt nur der Titel die Schrift, der Text behält die alte.  
  → Das smooth-Flag (oder den fertig gestylten Titel) beim Einreihen in Notice speichern und auch für den Titel verwenden.  
  `features/notification/AlpakaNotifications.kt:364` · D4-8
- **[low · S]** Der Player Model HUD verschluckt Renderfehler jeden Frame ohne Log, sodass der Avatar bei einer Mod-Inkompatibilität einfach fehlt und niemand im Log sieht, warum.  
  → Den ersten Fehler einmalig mit AlpakaAddons.LOGGER.warn samt Exception protokollieren.  
  `features/playermodel/PlayerModelRenderer.kt:243` · D5-8
- **[low · S]** Das KDoc von AlpakaNotifications.send() verspricht Thread-Sicherheit, doch das Umbrechen läuft auf dem aufrufenden Thread über Font.split und den unsynchronisierten STYLE_CACHE von GuiFont, sodass ein künftiger Aufruf aus einem Netzwerk-Thread die Map beschädigen kann.  
  → Entweder ‚nur Client-Thread‘ dokumentieren oder send() per Minecraft.execute auf den Client-Thread umleiten.  
  `features/notification/AlpakaNotifications.kt:151` · D4-9, X1-29
- **[nit · S]** Das Slayer Session HUD setzt Smooth Font selbst per withStyle auf jedes Literal statt wie alle anderen HUDs über HudPanel.text, sodass künftige Änderungen an GuiFont.restyle dieses HUD nicht erreichen.  
  → Die Zeilen über HudPanel.text(…, smooth) bauen und den Cache auf denselben Stil schlüsseln.  
  `features/slayer/SlayerHudRenderer.kt:138` · D5-9

### Konfigurationsmenü (3)

- **[nit · S]** isViewmodelSection() vergleicht den Sektionstitel mit dem Literal "Viewmodel" aus ConfigTree.LAYOUT, sodass ein Umbenennen der Sektion den Setup-Button und den Stepper stillschweigend entfernt.  
  → Eine gemeinsame Konstante ConfigTree.VIEWMODEL_SECTION oder ein setupMode-Flag an Section verwenden.  
  `client/AlpakaConfigScreen.java:337` · D1-17
- **[nit · S]** Eine geöffnete Auswahlliste im Config-Menü bleibt nach Klicks daneben und nach einem Featurewechsel offen, und Esc schließt bei offener Liste oder im Setup-Modus gleich das ganze Menü statt nur die Liste bzw. den Setup-Modus.  
  → openChoice in mouseClicked außerhalb der Liste und in selectFeature leeren und Esc in keyPressed erst die Liste, dann den Setup-Modus schließen lassen.  
  `client/AlpakaConfigScreen.java:885` · D1-19
- **[nit · S]** Sichtbare Texte im Config-Menü sind uneinheitlich: Master Custom Sounds endet mit 'Currently work in progress.', Titel mischen 'Color' und 'Colour' sowie 'Center' und 'Centres', Notification Duration spricht von 'stays in the corner' trotz Top Center, und Disable All Features erscheint wegen des Keywords 'reset' neben Reset All Settings in der Suche.  
  → Eine Schreibweise für alle Titel festlegen, 'work in progress' streichen, 'in the corner' in 'on screen' ändern und 'reset' aus den Keywords von Disable All Features entfernen.  
  `client/gui/AlpakaConfigRegistry.java:1868` · D2-20

### Untermenüs, AlpakaStyle & Konventionen (7)

- **[low · S]** Bei eingeschalteten Custom Sounds spielen Load/Save Preset und Reset Viewmodel den Klicksound doppelt und damit lauter, weil der Screen schon klickt und die Handler in der Registry noch einmal. Der try/catch(Throwable)-Wrapper ist zudem in der Registry und in vier Screens kopiert.  
  → Die zusätzlichen Klicks in der Registry entfernen und einen gemeinsamen Klick-Helfer (z. B. AlpakaStyle.click()) nutzen.  
  `client/AlpakaConfigScreen.java:1042` · X1-13, D2-22, D3-21
- **[low · S]** ModernGuiUtils.hairline() liefert bei GUI-Scale 3 entgegen dem eigenen Javadoc zwei statt einem Pixel (Math.round(1.5) = 2). Dort sind alle abgerundeten Umrandungen im Config-Menü und in den Untermenüs doppelt so dick wie gedacht.  
  → Auf Math.max(1, scale / 2) umstellen oder den Javadoc ans gewollte Verhalten anpassen, nachdem man einen Screenshot bei Scale 3 geprüft hat.  
  `client/gui/ModernGuiUtils.java:96` · C6-11
- **[low · S]** Fügt man im Wheel-Editor einen Befehl hinzu, den es schon gibt, wird die Eingabe kommentarlos auf '/' zurückgesetzt. Es gibt keinen Hinweis, dass der Befehl (vielleicht auf einer anderen Seite) schon existiert.  
  → Die Eingabe stehen lassen und Rückmeldung geben, etwa den Unterstrich kurz in DANGER aufblinken lassen oder zur vorhandenen Zeile scrollen.  
  `client/CommandWheelConfigScreen.kt:473` · D3-12
- **[nit · S]** EditableText.insert(char) zwingt Aufrufer, Code Points abzuschneiden, und Backspace, Delete und Pfeiltasten bewegen sich um eine UTF-16-Einheit. Emoji im Suchfeld werden so zu falschen Zeichen oder einzelnen Surrogates.  
  → insert(int codePoint) mit Character.toChars ergänzen und beim Bewegen und Löschen Surrogate-Paare als Ganzes überspringen.  
  `client/gui/EditableText.java:66` · C6-22
- **[nit · S]** Ist die skalierte Fensterhöhe sehr klein (unter 192 px, z. B. mit Force Unicode Font bei 720p), überlappt im Farbwähler die Trefferfläche des Opacity-Sliders die oberen Pixel von Save/Cancel. Unter 170 px rutscht der Header aus dem Bild.  
  → Save/Cancel vor den Slidern prüfen, die Sliderzeilen aus dem Platz über btnY ableiten und y wie im Wheel-Editor auf mindestens MARGIN/2 begrenzen.  
  `client/ColorPickerScreen.java:139` · D3-11
- **[nit · S]** Der HUD-Editor zeichnet den deaktivierten Reset-Button von Hand nach (drawRoundedRect plus centeredText), weil AlpakaStyle.button kein enabled-Flag hat. Er läuft damit Gefahr, von den Maßen von button() abzuweichen.  
  → AlpakaStyle.button einen enabled-Parameter (oder eine DISABLED-ButtonKind) geben und diesen hier verwenden.  
  `client/hud/HudEditorScreen.kt:115` · D3-14
- **[nit · S]** Im Farbwähler erscheint der Ring einer Vorlage nur beim Hover über 16×16 px, Klicks werden aber auf 20×20 px angenommen. Im Wheel-Editor prüfen Klicks mit inklusiven Kotlin-Ranges, Hover mit dem exklusiven AlpakaStyle.inside, was 1 px Versatz ergibt.  
  → Für Hover und Klick dieselbe Fläche verwenden: AlpakaStyle.inside mit dem gezeichneten Rechteck.  
  `client/ColorPickerScreen.java:280` · D3-22

### Chat (3)

- **[nit · S]** Zwei kurz hintereinander gemachte Screenshots werden parallel geladen, und die Screen Preview zeigt dann womöglich den älteren, während das vermutete Speicherleck beim Verkleinern praktisch nicht erreichbar ist.  
  → Jede show()-Anfrage mit einer Sequenznummer versehen, veraltete install()-Aufrufe verwerfen (Bild schließen) und 'small' im catch vorsorglich schließen.  
  `features/chat/ScreenshotPreview.java:81` · C1-13
- **[nit · S]** Ein fehlerhaftes -Dalpaka.scrollRate (z. B. '7,5') wirft im statischen Initialisierer von ChatScrollAnimator und bringt den Client beim ersten Zeichnen des Chats zum Absturz, auslösbar allerdings nur durch ein bewusst gesetztes JVM-Flag.  
  → Defensiv parsen und mit Log-Warnung auf 7 zurückfallen oder den Dev-Override für das Release entfernen.  
  `features/chat/ChatScrollAnimator.java:17` · C1-23
- **[nit · S]** Klickt man nach 32 neueren Screenshots bei einer älteren Screenshot-Nachricht auf Copy oder Delete, meldet die Mod fälschlich 'That screenshot link is not from this session.'  
  → Die Meldung in etwas wie 'That screenshot button has expired.' ändern, das beide Fälle abdeckt.  
  `features/chat/ScreenshotMessageFeature.java:203` · C1-24

### Haupt-/Pausenmenü & Befehlsrad (2)

- **[low · S]** Die Hover-Animationen im Custom Main Menu und im Custom Escape Menu glätten pro Frame statt pro Zeit und wirken deshalb bei 240 FPS fast sofort, bei 30 FPS dagegen träge.  
  → Wie beim Befehlsrad zeitbasiert glätten: hover += (target − hover) · (1 − exp(−dt · 17.3)).  
  `features/mainmenu/CustomMainMenuScreen.kt:519` · C5-6
- **[nit · S]** Das 1920x1080-Hintergrundbild des Custom Main Menu wird beim ersten Menü-Frame synchron auf dem Render-Thread dekodiert und belegt danach die ganze Spielzeit rund 8 MB Grafikspeicher.  
  → Das Bild beim Ressourcen-Laden vorab registrieren und beim Betreten einer Welt per textureManager.release freigeben.  
  `features/mainmenu/MenuAmbience.kt:70` · C5-17

### Statistik & Speicherung (1)

- **[nit · S]** '/alpakastats folder ~/Dropbox/alpaka' legt im Instanzordner einen Ordner namens '~' an, und relative Pfade landen generell in der Instanz statt am gemeinsamen Speicherort, ohne dass die Mod darauf hinweist.  
  → Ein führendes '~' zu user.home erweitern und relative Pfade mit einem Hinweis ablehnen.  
  `client/AlpakaClient.java:321` · C6-29

### Slayer-Tracking (1)

- **[nit · S]** SlayerType.isSlayerArea prüft in beide Richtungen und bekommt inzwischen jede Sidebar-Zeile, während isTrackerArea nur in eine Richtung prüft. Ein echter Fehltreffer ist nicht belegt, aber die Logik ist uneinheitlich, und der Java-Javadoc nutzt Kotlin-[Links].  
  → Die umgekehrte contains- und die equals-Prüfung streichen und {@link} verwenden.  
  `features/slayer/SlayerType.java:121` · C2-14

### Sounds & Eingabe (2)

- **[nit · S]** SoundEngineMixin merkt sich den geplanten Ersatzsound im statischen Feld alpaka$pendingReplacement, das ein Sound-Listener eines anderen Mods per verschachteltem play() überschreiben kann oder das nach einer Exception hängen bleibt und beim nächsten beliebigen Sound einen Hurt- oder Hit-Ersatz auslöst (heute ohne realistischen Auslöser).  
  → Den Wert per MixinExtras @Share("replacement") LocalRef<Runnable> an den jeweiligen play()-Aufruf binden.  
  `mixin/SoundEngineMixin.java:75` · C7-5
- **[nit · S]** Silence: All World Audio hält bei Bienen, Loren und Reit-Geräuschen nicht, weil diese still starten und ab dem nächsten Tick über calculateVolume(SoundInstance) wieder hörbar werden, das der Hook nicht abdeckt.  
  → Auch calculateVolume(SoundInstance) für vom Mute erfasste Sounds per @ModifyReturnValue auf 0 setzen oder die Ausnahme im Klassenkommentar nennen.  
  `mixin/SoundEngineMixin.java:101` · C7-7

## Umgesetzte Performance-Änderungen

Alle auf Branch `t3/design-rework-code-review-perf`, je ein Commit. Jede Änderung: von einem Reviewer vorgeschlagen, von einem strengen Prüfer auf exakte Verhaltensgleichheit und echten Gewinn geprüft, bei der Umsetzung ein drittes Mal geprüft, dann Build + 99 Unit-Tests + komplette Screenshot-Tour (62 Bilder, Config-Screens pixelgleich zur Baseline, In-World-Bilder per Auge gegen das natürliche Rauschen).

- `698e1d3` perf(gui): smooth-shape meshes start with room for 64 vertices and grow on demand
- `4b43530` perf(config): settings rows scrolled wholly out of the details column are skipped, not drawn and clipped
- `e163b82` perf(config): search results are worked out once per search text, not filtered from the whole tree every frame
- `e364e32` perf(config): labels are cut to fit once per text and width, and the version label is built once
- `7301197` perf(etherwarp): the Tuned Transmission level is read once per held item's custom data, not deep-copied out of it every frame while aiming
- `4328ffc` perf(gui): menu-font styles are kept per font and GUI scale in arrays, not looked up by a font id built on every call
- `8f08540` perf(gui): menu-font widths are measured from the bare text and style, without building a component first
- `1a2aefa` perf(chat): the smooth chat fade hooks only the line's alpha instead of repacking every argument
- `5eebd79` perf(chat): chat search lower-cases each message once per search and the query once per edit, not both for every message on every keystroke
- `41a6169` perf(chat): Ctrl+F lays the chat out once, through the cleared input's own edit, instead of twice
- `e545954` perf(chat): the chat tab row and the filter chip reuse their label components until an unread count changes
- `3f5454f` perf(bridge): the bridge formatter turns away lines without a '>' before stripping their colour codes
- `1e0f051` perf(gui): the item fade hook lets atlas blits through untouched while nothing is fading
- `37da4bd` perf(inventory-hud): the attached panel clears the action bar by vanilla's line instead of asking Fabric's registry for it every frame
- `f1be87d` perf(inventory-hud): the chest-style title is one Component kept for good instead of a String decomposed every frame
- `a75df8e` perf(damage-tags): a splash's decoration is stripped with one pattern compiled once instead of one per call
- `91ad5e4` perf(damage-tags): a young armor stand's name is checked for impossible characters before it is flattened to a String
- `926164b` perf(damage-tags): custom damage tags hand the font their coloured digits directly, and a still gradient is built once per tag
- `29a2919` perf(cosmetics): the sensei hat reads its wedge angles from tables built once and refills six scratch vectors instead of allocating per quad
- `7f29d40` perf(slayer-hud): the session HUD measures its box once a frame for both its position and its click target
- `04140cd` perf(slayer-timer): the boss timer keeps its "Boss" label between frames and measures its line once for both axes
- `99151b0` perf(world-age): the "Day: N" line is kept in the font it is drawn in and measured once a frame for both axes
- `d94870a` perf(hud-editor): the editor measures each HUD once a frame for its anchor and its box
- `816b83d` perf(wheel): the main menu, its status pulse and glint, and the command wheel each keep one smooth-shape mesh per GUI scale instead of building new ones every frame
- `2d91890` perf(wheel): ring sectors carry each step's far edge over as the next step's near edge and take each band's two colours once
- `fe51dd5` perf(wheel): the command wheel builds its labels and measures them once per page instead of twice every frame
- `83d26e3` perf(color-picker): the hex field draws the code it already holds instead of formatting the colour anew every frame
- `8d1d641` perf(confirm): the confirm dialog wraps and orders its message once per layout instead of every frame
- `cd3fd7b` perf(hud-editor): the editor's status line is measured and shortened only when its text, the room for it or the menu font changes
- `dc260a1` perf(slayer): the area tests keep their answer per sidebar snapshot instead of lowercasing every line on every call

<details><summary>27 Vorschläge vom Prüfer abgelehnt</summary>

- **Every setting click writes the file to disk on the render thread** (`config/AlpakaConfig.java`): How often it runs: AlpakaConfig.save() only runs on separate user actions. Those are a click on a setting in AlpakaConfigRegistry, Enter/Escape or unfocusing a text field (AlpakaConfigScreen lines 1033 and 1116), the end of a slider drag (beginDeferredSaves/endDeferredSaves already turn the drag into one write), the Save button in the colour picker (ColorPickerScreen calls onSave only on Save, not while dragging), closing the HUD editor (HudEditorScreen.flush), command wheel edits, preset and reset buttons, and the stats-folder command. Nothing calls it per frame, per tick or per chat message. That makes it cold code. The saving per click is one write of about 10-20 KB (around 290 fields, pretty-printed) plus a rename. That is normally well under a millisecond to a few milliseconds, and the proposal keeps GSON.toJson, which costs about as much, on the render thread. This is not the 'large blocking IO / seconds of work' that would justify changing cold code. The antivirus case is speculative and only costs one frame per click.

It does not behave the same:
(1) Durability changes. Today a setting is on disk as soon as save() returns. With a daemon writer, a crash or kill shortly after a click loses the change. CLIENT_STOPPING does not fire on the crash path (System.exit after the crash report), so the 'flush on stop' does not cover it. A setting whose toggle crashes the game on the next frame would or would not be persisted depending on timing.
(2) The design as proposed has a real ordering bug. resetToDefaults() and recoverUnreadable() 'stay synchronous', but a background write of an older string that is still queued can land after their synchronous write. The file on disk would then go back to the pre-reset settings.
(3) Shutdown ordering is fragile. SessionLifecycle.onStopping runs AlpakaConfig.endDeferredSaves() and then AlpakaStats.flushNow(). flushNow reaches AlpakaStats.save(), then markFolderHadFile(), then AlpakaConfig.save(). So a config save can be queued after any flush placed earlier, and a daemon thread is killed when the JVM exits.

Fixing all of this means a new executor, an AtomicReference, coalescing, routing every write path through the queue, and a carefully ordered flush. That is not simple or local, and it adds risk to the one file whose loss or corruption the code base already works hard to prevent (AtomicJsonFile, backups, quarantine). The gain is too small for that risk and the behaviour change.
- **Memoise Feature.panelOptions() instead of allocating a list per call** (`client/gui/ConfigTree.java`): The equivalence argument holds: settings are mutated only in build() and master is final. The gain is a nanosecond-level micro-tweak, though. Only features whose master is not a switch allocate, e.g. HUD Editor, Command Wheel, Disable All and Reset All in General > Main, so a section has a handful of these. Each allocation is a tiny ArrayList(n+1) with n of 0 to about 13 plus an addAll array copy, roughly 100-300 ns and a few hundred bytes per frame in total. That is negligible next to everything else the screen allocates per frame (Components, Hit records and lambdas, text render states). It does not meet the 'meaningful gain' bar, and memoising adds mutable cached state to a public tree class.
- **Build the version label once instead of splitting the version string every frame** (`client/AlpakaConfigScreen.java`): ModVersion.mod() is memoised and constant, so the change would be safe. The gain is a nanosecond micro-tweak, though. String.split("\\+") takes the JDK fast path for a two-char escaped non-alphanumeric pattern, so no regex is compiled. The per-frame cost is one small ArrayList, a String[] of length 1 or 2, a substring and a concat, about 100-200 ns, once per frame and only while the logo is drawn. That fails criterion 3. A static final would also move the FabricLoader lookup to class-initialisation time, a change for no measurable benefit.
- **Binary-search the cut point in ellipsize instead of a quadratic linear scan** (`client/AlpakaConfigScreen.java`): Not equivalent. The proposal relies on width(prefix + '…') being non-decreasing, but GuiFont.width measures Component.literal(text), and legacy § codes are interpreted there (GuiFont.text's own doc says so: StringDecomposer.iterateFormatted). When the prefix ends in a lone '§', the appended '…' is consumed as an unknown format code and adds zero width. After '§l' the ellipsis gets bold's +1. So f(k) can drop when k ends on '§' and rise again at k+1. The downward scan returns the largest fitting k (e.g. 'AAAA§' + '…', drawn as 'AAAA'). A binary search that probes k-1, finds it too wide and moves lower returns a shorter cut such as 'AAA…', so the rendered output differs. '§' reaches ellipsize from user input. charTyped accepts any code point >= 32 except 127 for TEXT options (ellipsize(value, fieldW - 8)) and for the search box ('Nothing matches "..."'). On a German keyboard '§' is Shift+3. The gain is also doubtful. Labels that overflow only slightly are the common case, and there the downward scan needs only (length - cut) iterations, often 2-5. A binary search always needs about log2(length) ≈ 5-6 measurements, so it would often be slower. It only wins for heavy overflow in very narrow windows or during the brief setup-mode fold.
- **Tour's stop list (119 lambdas) is built at every player's startup** (`client/DevScreenshotTour.java`): The proposal is correct about equivalence but fails on frequency and gain. AlpakaClient.onInitializeClient:38 calls DevScreenshotTour.register(), and that triggers <clinit> before the env check. javap on build/classes confirms 119 invokedynamic sites plus method handles to CustomPauseScreen.<init> and SwingPreview.swing. This runs once per game start. I timed 120 such lambdas in a static List.of in a scratch JVM (JDK 25) after warming the lambda machinery: about 25 ms, plus a few hundred KB of metaspace for the hidden classes. Against a modded client start of 20-60 s that is well under 0.1%. The maintainer's rule only allows cold code when the saving is large (blocking IO or seconds of work), and 25 ms once per launch is not. The holder-class change is harmless, but this is a tidiness fix, not a performance one.
- **Scoreboard score is restyled twice and measured twice per line per frame** (`mixin/ScoreboardEntryFontMixin.java`): Frequency is real: displayScoreboardSidebar runs every frame while the sidebar is shown, with up to 15 lines. But the saving per line is one throwaway wrapper (MutableComponent, ArrayList, Object[]) and one width walk of the score, which on Hypixel is a 1-2 character number or empty because of the blank number format. That is marginal next to the rest of the sidebar (stream sort, per-line name width, drawing). The proposed code is also wrong as written. The bytecode shows PlayerTeam.formatNameForTeam and PlayerScoreEntry.formatValue return MutableComponent. A @ModifyExpressionValue handler must return that exact type, and GuiFont.restyle returns Component, so it would need casts or a new overload. The change also moves the hook to the synthetic lambda `lambda$displayScoreboardSidebar$1`, merges or rewrites two mixins (HudScoreboardFontMixin's docs point to ScoreboardEntryFontMixin) and adds a lambda-name dependency that the mc-26.1.2 backport line would have to re-derive. That is medium risk and not local, for a gain at the noise level. A safe tweak inside the current design would be to reuse the already-restyled score parameter instead of restyling again, but even that saves only about 3 small allocations per line, which is not worth an approval here.
- **Post-chain Identifier is concatenated and validated every frame a blur panel is shown** (`features/chat/ChatBlurFeature.java`): captureFrameNow runs at most once per frame, and only when a blur panel asked for it. The saving is one short String concat and one Identifier validation and allocation per frame, roughly 100 ns. The same call then runs a multi-pass full-screen PostChain on the GPU plus a full-frame texture copy, which costs orders of magnitude more. This is the nanosecond micro-tweak the rules exclude. The equivalence argument is right (Identifier equality is by value, and the radius is 1..MAX_BLUR_RADIUS at that point), but the gain is not meaningful.
- **Notice list is pruned twice per frame and the title Component is rebuilt per notice per frame** (`features/notification/AlpakaNotifications.kt`): The duplicate prune in draw() runs once per frame over a list of at most MAX_KEPT = 8 entries, and the list is empty almost all the time. Removing it saves one capturing lambda allocation (Kotlin's MutableList.removeAll(predicate) is not inline) and one uncontended monitor enter per frame. That is a nanosecond tweak. The title Component is rebuilt only while a notice is on screen (a few seconds at a time, at most 4 notices), which is 1-3 small allocations per visible notice per frame. Not meaningful either. Equivalence is also not airtight across threads. dismiss() and enqueue() may run on other threads and use their own System.currentTimeMillis(). If the wall clock steps backwards between render's prune and draw's prune, a notice can get retireAtMs <= now in that window. Today the second prune removes it, and without the second prune it would still take a stack slot for one frame. That edge is negligible, but the change has no worthwhile gain to offset it. The suggested change to render() ('skip the lock when empty ... inside the lock is enough') is also self-contradictory.
- **Unread counting runs the full classifier once per channel tab** (`features/chat/ChatTabsFeature.java`): Equivalence holds: classify(String) is pure. The gain does not. onMessage runs once per arriving chat message, not on replays, and only with chat tabs enabled. Without the change it costs at most three extra classify calls. The patterns are anchored with ^ and the strings are short, so that is about 20 small Matcher allocations and scans, a few microseconds per message. Hypixel sends a few messages per second, and vanilla's own per-message work (wrapping, plus ArrayList.addFirst shifting the whole line list for every line) is far larger. This is the kind of micro-tweak the maintainer does not want, and it would reshape belongsTo's switch for no measurable benefit.
- **Folding a repeat scans the whole display list twice** (`features/chat/CompactChatFeature.java`): Runs only when compact chat folds a repeat, which is much rarer than once per chat message. trimmedMessages is an ArrayList (Lists.newArrayList in the 26.2 constructor). Both versions stay O(n): subList(first,end).clear() is removeRange, which arraycopies the whole tail, just as removeIf compacts it. Vanilla's own addFirst shifts the entire list for every line of every message, so the saving is a few microseconds per fold next to vanilla's larger per-message cost. Equivalence also rests on an invariant this code does not own: that a message's lines are always one contiguous run. That holds for vanilla, but a fold that stops at the first gap would leave stray lines if anything else inserted or reordered lines. That is too little gain for an added correctness assumption.
- **HUD bounds are computed three times per frame, re-validating the slayer layout about 7 times** (`features/slayer/SlayerHudRenderer.kt`): The extra work is two additional bounds() evaluations per HUD per frame. For the slayer HUD that means layout()'s cached path: an 11-bit toggle mask, GuiFont.style() (made allocation-free by PERF-X1-2), pauseReason() over the cached sidebar lines, and refresh()'s rate-limited guard. For world-age and the boss timer it is one short text measurement; for the player model it is trivial arithmetic. So the saving is on the order of a microsecond per frame. In exchange the proposal adds a new HudElement method and result type and rewrites the placement code in four renderers. The slayer HUD would also take lastDrawnBounds from before renderHud instead of after, which is harmless but a reordering. Low gain for a cross-cutting change does not meet the 'meaningful and local' bar.
- **Every enableScissor in every screen redoes vanilla's scissor math into a mirror stack** (`mixin/GuiGraphicsExtractorMixin.java`): Not equivalent. In 26.2, GuiGraphicsExtractor$ScissorStack.push intersects the new area with requireNonNullElse(peekLast(), screenSize) (bytecode). A top-level scissor is therefore clipped to the screen, and a fully off-screen one becomes ScreenRectangle.empty(); the mirror keeps the raw rectangle. alpaka$currentScissor() feeds the scissorArea of BlurRect/GradientRoundedRect render states in ChatBlurFeature, ScoreboardBlurFeature, HudPanel, ModernGuiUtils, MenuStyle, InventoryHudRenderer, CustomMainMenuScreen and ScreenshotPreview. Changing it changes those states' scissor and bounds, and GuiRenderState uses bounds to assign layers, so it is not a pure no-op. ScissorStack is also a private nested class. The saving is one ScreenRectangle, one transform and one intersection per enableScissor call (a handful per frame), which is micro.
- **slayerBossMap() builds a UUID string and clones SlayerType.values() on every call** (`config/AlpakaStats.java`): The per-frame path (the boss-timer HUD's beatTheRecord -> personalBest while a result is shown, plus 250 ms slayer-HUD rebuilds) costs one UUID.toString(), a values() clone of about 6 elements and a few HashMap lookups: tens to a few hundred nanoseconds per frame, a micro-tweak. The size-based shortcut is also not exactly equivalent. AlpakaStats is read with Gson, which maps an unknown enum key to null. A stored slayerBossMap can then reach VALUES.length entries with a null key while a real SlayerType is missing, and skipping the seeding would leave that type absent, so callers like SlayerDropTracker.get(type) would see null.
- **World-age width re-builds the 'Day: N' component twice per frame despite the component cache** (`features/worldage/WorldAgeHudRenderer.java`): It is exactly equivalent, since dayComponent(day) holds the same text and getWidth is always called with getWorldDay(), the same day as renderHud. But the saving is two string concatenations and two Component.literal allocations per frame. The width is still measured each time, so this is a nanosecond-scale micro-tweak that does not meet the 'meaningful gain' bar.
- **Chat search lowercases the query once per stored message on every keystroke** (`features/chat/ChatSearchFeature.java`): This runs per keystroke in chat search, not per frame. Each keystroke calls rescaleChat(), which re-wraps every stored message with the font (ComponentRenderUtils.wrapComponents); that, and the per-message getString().toLowerCase(), dwarf lowering a short query. String.toLowerCase(Locale.ROOT) also returns the same instance without allocating when the query is already lower case, which is the usual way people type a search. The gain is negligible.
- **Memoize SlayerTimer.format (String.format three times per frame from the Boss HUD)** (`features/slayer/SlayerTimer.kt`): The frequency is as stated. Each frame while the timer HUD is up (a fight running or the 5 s linger), SlayerTimerHudRenderer.render calls format three times: visibleAnchorX->bounds->width, visibleAnchorY->bounds->width, and renderHud. The equivalence also holds: I re-ran the rounded-key check on the project JDK (25.0.4) and every ms in 0..59 999 formats the same as (ms+50)/100 tenths, with 0 mismatches. The gain is what fails. Measured on the same JDK, one SlayerTimer.format call takes about 170 ns and allocates about 555 B. Three calls a frame is about 0.5 us per frame, and only while a fight is shown. That is the nanosecond-level micro-tweak the bar excludes. It would also add mutable memo state to a shared utility that announce() and FightBreakdown.lines() use too, and the rounded key relies on a non-obvious Formatter HALF_UP invariant that would need its own explanation. The renderer's header comment says the per-frame rebuild here is a deliberate trade-off that stays cheap because the line is so short. The bigger redundancy in this path is that bounds() runs twice per frame, each time doing two font.width measurements and building Components. This memo does not touch that.
- **Compute the tab-list 'has a quest' verdict once per tab read, not on every refresh** (`features/slayer/SlayerQuestDetector.kt`): The equivalence holds: cachedTabLines is only assigned in that block and fromScoreboardLine is pure. The gain does not. The scan runs at most 4 times a second with no quest (the REFRESH_MS guard), or 20 times a second when the quest is only on the tab list. It allocates nothing: String.contains over about 80 short entries times 13 boss names, and SlayerType.VALUES is already cached. That is roughly 10 us per pass, about 40 us/s idle. When the tab list does carry the quest, any{} stops at the matching entry and the main loop scans every line anyway, so the change saves at most half of an already cheap per-tick scan. It is a micro-tweak at tick rate, not a meaningful saving.
- **Remember containers whose title already failed the check** (`features/slayer/SlayerMenuXpReader.kt`): The equivalence is fine: Screen.title is final and Hypixel container titles are literal. The cost is negligible, though. The saved work runs at most once per client tick (20 times a second), only while a non-Slayer container is open. It is one Component.getString on a short title, one replaceAll with the precompiled COLOR_PATTERN, and two case-insensitive contains over a two-element list: about a microsecond per tick, tens of microseconds per second. There is no per-frame path, no regex compile and no meaningful allocation volume. Adding another screen-identity field and clearing it is not justified by that gain.
- **Fire-pit snapshot: Pairs, boxed floats, a HashSet copy and 4 BlockPos per pit every frame** (`features/blaze/FirePitFeature.kt`): The per-frame work is real only while pits exist, which means during the player's own Blaze boss fight (or the debug preview). In practice that is a few dozen pit blocks, capped at 256. The proposal removes only small, short-lived allocations (Pair, boxed Float, a HashSet copy and 4 BlockPos per pit). It keeps the same number of hash lookups, just against the LinkedHashMap instead of the copy. That is a few hundred tiny young-gen objects per frame in a rare, short situation, a gain that is not meaningful. In exchange it replaces a readable snapshot with three parallel arrays, a static side table and per-pit bit masks in drawColumn. The behaviour analysis is correct, but the gain does not justify the churn.
- **Own name tag re-runs Bidi and rebuilds every glyph each frame although the name rarely changes** (`features/nametag/CustomNameTagFeature.kt`): Affects exactly one name tag: the local player's, in third person, with Custom Name Tag on. Per frame that is one Bidi pass over a roughly 20-character name plus about 20 Glyph objects. That is a few microseconds, not a meaningful gain. The cache needs four new mutable fields (text, language, player name, glyph list) and a deep MutableComponent.equals over the team-prefix component tree every frame, which itself costs part of what it saves. Glyph would also need a mutable width re-measured every frame to stay correct across resource reloads. That is extra state and invalidation surface for a negligible saving. The side suggestion of building the measuring sequence only once per glyph is a nanosecond micro-tweak and not worth a change on its own.
- **Move the per-layer usesVanillaSheets scan out of the ghost-step loop** (`features/viewmodel/ItemMotionBlurFeature.java`): The change is equivalent, since usesVanillaSheets depends only on submit.quads(), which is the same for every step, but the gain is a micro-tweak. It only runs with itemSizeFeatureEnabled && itemMotionBlurEnabled (motion blur defaults to false) and only inside the trail window after a swing. The scan itself is trivial: per quad, materialInfo() and itemRenderType() are plain field getters, and Sheets.cutoutItemSheet() and the others are bare getstatic returns (checked with javap), so the JIT reduces it to a few loads and reference compares. That is about 8 steps x ~100 quads of compares, well under a microsecond. The same loop then submits each layer once per step, and the feature renderer emits every one of those quads with vertex transforms. That work is O(steps x quads) with a much larger constant, so hoisting the scan does not change the asymptotic cost and saves only a small fraction of it. It would also add a per-frame boolean[] or a static scratch array for no measurable gain.
- **Return early from the camera hook when Smooth Perspective is off** (`mixin/CameraMixin.java`): The change is equivalent: getCameraEntityPartialTicks only reads TickRateManager.isEntityFrozen and DeltaTracker, and the lerps, getViewYRot/XRot and distanceTo only read state. With the feature off, update() itself is just reset() and return null. But Camera.update runs once per frame, not per entity, and the saving is one 3-double Vec3 allocation, a handful of float lerps, one sqrt and an options read: tens of nanoseconds per frame. That is exactly the nanosecond micro-tweak rule 3 excludes. It would also duplicate the smoothPerspectiveEnabled check (and its reset-on-disable side effect) that SmoothPerspectiveFeature.update() already owns, spreading the feature's on/off logic across two files for no measurable gain.
- **slayerBossMap() builds a UUID string and clones SlayerType.values() on every call** (`config/AlpakaStats.java`): The proposed change would not alter behaviour, but the saving is too small to matter. User.getProfileId() is a plain getter on a final field (checked with javap), so the identity cache would work. It is per frame only through SlayerHudRenderer's 'Since <drop>' row, which calls SlayerRngDropTracker.bossesSince, and SlayerTimerHudRenderer.beatTheRecord, which only runs while a finished time is shown. That is 1-2 calls per frame. Each call saves one 36-char UUID string plus its hash. The SlayerType.values() copy is a 7-element local array that C2 normally optimises away anyway. The same bossesSince call already builds a filtered list of drop entries every frame, and the slayer HUD around it builds row objects and calls String.format every frame. Against that, roughly 100 bytes and about 50 ns per frame cannot be measured. In return the change adds two mutable static cache fields to AlpakaStats, which is more state to keep correct across threads and account switches for no real gain.
- **Every picture-in-picture blit computes a record hashCode twice to look up a skin style that is almost never there** (`client/gui/GuiSkinStyle.java`): The early return would not change behaviour: with an empty map, get() returns null and both methods already return vanilla. The gain is negligible, though. PictureInPictureRenderer.blitTexture runs once per picture-in-picture state per frame. In practice that is 0-2 per frame: the PlayerModel HUD's entity preview when enabled, the inventory's player preview, and the main menu avatar. So the guard saves 2-4 record hashCode calls per frame, well under a microsecond, and the cost does not grow with anything the player does, unlike the per-item hook in C6-2. That is a nanosecond-scale tweak, which the brief says to reject. The proposal itself says C6-10 removes this hashing entirely, so keying this map by identity belongs in that proposal, not here.
- **Cache the hero's online line instead of formatting and measuring it every frame** (`features/mainmenu/CustomMainMenuScreen.kt`): This runs per frame, but only on the title screen, inside one widget, and saves a few microseconds: 1-2 String.format calls plus about 4 short width measurements. statusLine would still build its two Components and measure "●" / "● " every frame unless it were reworked too. In exchange the memo needs a five-part key compared every frame (HypixelStatus.state, which itself derives from AlpakaConfig.allowApiCalls, online and failures; online; pingMs; room; GuiFont.style() identity). It also depends implicitly on font metrics, which change on resource reload, and on the JVM default locale used by %,d. Equivalence is achievable with that key and an init() reset, but the extra state and invalidation surface are out of proportion to a microsecond-level gain on a menu screen. That is the kind of micro-tweak the maintainer does not want.
- **Build the main menu Layout and its constant labels in init() instead of every frame** (`features/mainmenu/CustomMainMenuScreen.kt`): This is a nanosecond-level micro-tweak on the title screen. Per frame it saves one small Layout object (25 int fields), a two-element listOf (firstOrNull is inline, so there is no lambda allocation), one substring/concatenation, and three short Components. ModVersion.mod() and minecraft() are already cached in static fields, so there is no FabricLoader lookup to save. The Layout in mouseClicked runs once per click, which is cold. The change would be behaviour-preserving (width and height and the font style only change through init(), via resize, return from a sub-screen, or LoadingOverlay), but a handful of tiny allocations per frame on a menu screen is not a meaningful gain. Moving the Layout to a field also adds state that must stay in step with init() for no measurable benefit.
- **Each TTF is read into native memory six times (30 FreeType faces, about 5.4 MB)** (`res/assets/alpaka/font/nunito_semibold_s2.json`): Frequency: this code only runs at startup and on resource reloads (F3+T, resource pack changes). FontManager.prepare calls safeLoad, which uses supplyAsync for each provider. That reaches TrueTypeGlyphProviderDefinition.load, which runs TextureUtil.readResource and then FT_New_Memory_Face. Nothing here runs per frame, per tick or per glyph. The saving is about 4.5 MB of duplicate jar reads/decompression plus the same amount of native memory, five TTFs totalling about 910 KB read six times each. That work is spread across reload worker threads, so it costs a few milliseconds, not seconds and not blocking IO on the main thread. For a cold path the gain is too small. It mostly saves memory rather than making anything faster.

Equivalence: the proposal says the cache is dropped because a reload closes every provider before new ones load. javap shows the opposite. FontManager.reload runs prepare() first, which loads the new providers asynchronously while the old ones are still alive. Only after that does apply() close the old ones, through the providersToClose.forEach(GlyphProvider::close) call at the start of apply. So during a reload the refcount for each alpaka TTF never reaches zero, and the new providers would reuse the old buffer. If a resource pack overriding alpaka:font/*.ttf is added or removed, the new fonts would quietly keep the old bytes. That is a visible change in behaviour, and the proposed key (Identifier only) cannot catch it. A correct key would need the resource's pack source or a reload generation number.

Threading and complexity: safeLoad runs loads in parallel on several worker threads, and readResource happens outside FreeTypeUtil.LIBRARY_LOCK, so the cache would need its own synchronisation. The change would also have to intercept readResource in load and the error-path memFree in load's catch block (bytecode offset 242 onward), not just the memFree in TrueTypeGlyphProvider.close. That means several new mixin hooks around native memory ownership, where a mistake is a double free or use-after-free crash. That is far from simple and local, and medium risk for a startup-only gain.

</details>


## Abschluss-Audit der Performance-Commits

Drei unabhängige Prüfer haben alle 30 Commits gelesen, jeder mit eigenem Fokus: exakte Verhaltensgleichheit, Caches/Lebenszyklus (Invalidierung, Speicher, Threads) und Handwerk (Stil, wahre Kommentare und Commit-Messages). Ein vierter Agent hat jeden Befund gegengeprüft.

- **Verhaltensgleichheit: kein einziges Problem** in den 30 Commits.
- Bestätigt und korrigiert (je eigener Commit, wieder mit Build + Tests + Tour verifiziert):
  - `d9c3dec` Das Such-Memo der Chat-Suche konnte während einer sehr langen, offenen Suche schon aus der Historie getrimmte Nachrichten festhalten; es wird jetzt geleert, sobald es doppelt so groß wie die Chat-Historie ist.
  - `0da5d8f` Ein neuer Kommentar nannte 7 statt 3 Layout-Abfragen pro Frame.
  - `e37f884` Die „einmal messen, beide Achsen klemmen“-Logik stand dreifach kopiert in den HUD-Renderern; die nutzen jetzt `visiblePlacement()` wie der HUD-Editor.
- Bewusst nicht geändert: drei sehr ähnliche „Mesh pro GUI-Skala behalten“-Helfer (Nit).


## Nachprüfungen


### Portierbarkeit des Redesigns auf 26.1.2

I checked all 43 files changed by `git diff origin/main..HEAD` on design-rework (3f58992): 34 Java/Kotlin files, alpaka.mixins.json, build.gradle, CREDITS.md and the font assets. Each was compared against the 26.1.2 jar, Fabric API 0.155.2+26.1.2 and the translations in 0440626.

How I tested: I cloned the repo to /tmp/compat-chk/repo (the worktree was not touched) and merged 3f58992 into mc-26.1.2 (0440626). I resolved conflicts in design-rework files towards design-rework and in main-only files towards 26.1.2. Then I ran `gradlew --offline compileKotlin compileJava` against 26.1.2. Every in-scope compile error found that way is in the findings. After applying the translations below, and stubbing two pieces of main-only code, both compile steps pass against 26.1.2.

Result: 14 incompatibilities. 12 break compile, 1 crashes at startup, and 1 silently does nothing.

Already fine on 26.1.2, no change needed:
- **Mixins:** BossHealthOverlayAccessor, GuiMessageFontMixin, NativeImageFontMixin, TrueTypeGlyphProviderDefinitionMixin and TrueTypeGlyphProviderMixin. Their target classes have identical member lists in both jars (BossHealthOverlay, GuiMessage, NativeImage aside from getPixelBytes, TrueTypeGlyphProvider, TrueTypeGlyphProviderDefinition), and the INVOKE call sites they hook are the same in both.
- **Existing WideHealthBarMixin hooks:** these already target Gui on mc-26.1.2 and still merge cleanly.
- **Fabric API:** HudStatusBarHeightRegistry, VanillaHudElements, ClientTickEvents and ClientEntityEvents have the same signatures in 0.159.0 and 0.155.2. No changed file uses FabricClientCommandSource.attended().
- **Resources and build:** the font JSON "ttf" providers (GlyphProviderType, FontOption and TrueTypeGlyphProviderDefinition$Shift are the same), the reflection on MouseHandler.xpos/ypos, the mixins json (all entries resolve) and the build.gradle windowSize block all merge or work as they are.

Merge conflicts in design-rework files:
- AlpakaConfigScreen: 1 hunk
- ColorPickerScreen: 1 hunk
- DevScreenshotTour: 2 hunks
- AlpakaConfigRegistry: 11 hunks (Disable All, plus 10 colour options that come from main's swatch change)
- AlpakaNotifications.kt: 1 hunk

The findings below say how each conflict should be resolved.

Not in scope, but the same merge also hits these main-only problems (main's 47 commits since 12e0a6b):
- **HandItemStarlightFeature:** PrimitiveTopology and BindGroupLayouts are missing on 26.1.2, `withVertexBinding`/`withPrimitiveTopology` need translating, and `gameRenderer.mainCamera()` becomes `getMainCamera()`. RenderPipelinesAccessor is also not registered in the 26.1.2 mixins json.
- **ConfigOption.java:164:** `gui.setScreen` needs translating. It is reached by the resolved colour-option hunks in AlpakaConfigRegistry.
- **SmolderingPolarization.kt:42:** `mc.gui.hud.tabList` becomes `mc.gui.tabList`.
- **HudMixin:** modify/delete conflict. Its scoreboard-blur hooks need moving into GuiMixin, otherwise design-rework's scoreboard Blur switch does nothing on 26.1.2.
- **Other conflicts:** GuiMixin, ItemFeatureRendererMixin, ItemInHandRendererMixin and SubmitNodeCollectionMixin.
- **Unverified:** main's new mixins (ChatListenerMixin, ClientPacketListenerChatMixin, LivingEntityMixin, MinecraftScreenshotMixin, PlayerTabOverlayAccessor) were not checked against 26.1.2.

- **[high]** New @WrapOperation targets ContextualBar, which is ContextualBarRenderer on 26.1.2 (startup crash) — `/home/t3/.t3/worktrees/AlpakaAddons/t3-0ab2ba43/mixin/WideHealthBarMixin.java:63`  
  alpaka$levelOntoTheBar wraps ContextualBar.extractExperienceLevel inside extractHotbarAndDecorations. 26.1.2 has no ContextualBar class: Gui.extractHotbarAndDecorations calls ContextualBarRenderer.extractExperienceLevel, which has the same descriptor. The target is a string, so it compiles. The file also auto-merges cleanly with 26.1.2's Hud->Gui translation, so nothing warns about it. When Gui loads, the injector finds 0 targets, and with "defaultRequire": 1 Mixin throws InjectionError ('Critical injection failure'), crashing the game at startup.  
  → 26.2 Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractExperienceLevel(...)V -> 26.1.2 Lnet/minecraft/client/gui/contextualbar/ContextualBarRenderer;extractExperienceLevel(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;I)V
- **[high]** HudScoreboardFontMixin imports and targets Hud, which does not exist on 26.1.2 — `/home/t3/.t3/worktrees/AlpakaAddons/t3-0ab2ba43/mixin/HudScoreboardFontMixin.java:16`  
  Line 5 has `import net.minecraft.client.gui.Hud;` and line 16 has `@Mixin(Hud.class)`. 26.1.2 has no Hud class; the sidebar is Gui.displayScoreboardSidebar. Once renamed to Gui, the @ModifyExpressionValue on Objective.getDisplayName applies unchanged: the method and its single INVOKE exist with the same descriptors.  
  → 26.2 net.minecraft.client.gui.Hud -> 26.1.2 net.minecraft.client.gui.Gui: `import net.minecraft.client.gui.Gui;` and `@Mixin(Gui.class)`. This is the same translation 0440626 made for HudOverlayAccessor and WideHealthBarMixin.
- **[high]** New AlpakaConfirmScreen closes via Minecraft.gui.setScreen — `/home/t3/.t3/worktrees/AlpakaAddons/t3-0ab2ba43/client/AlpakaConfirmScreen.java:118`  
  `this.minecraft.gui.setScreen(parent)` in onClose(). On 26.1.2 Gui is the HUD and has no setScreen; screens are opened through Minecraft.  
  → 26.2 minecraft.gui.setScreen(x) -> 26.1.2 minecraft.setScreen(x) (0440626: 'screens through Minecraft.setScreen/screen instead of Gui')
- **[high]** Disable All / Reset All open AlpakaConfirmScreen via gui.setScreen (lines 159, 170) — `/home/t3/.t3/worktrees/AlpakaAddons/t3-0ab2ba43/client/gui/AlpakaConfigRegistry.java:159`  
  Both new options use `Minecraft.getInstance().gui.setScreen(new AlpakaConfirmScreen(...))`. The Disable All one also conflicts with 26.1.2's translated ConfirmScreen hunk. The file has 11 conflict hunks in total: Disable All, plus 10 colour options whose 26.1.2 side is the old 'Choose Color' button. Take design-rework's side in each, translated.  
  → 26.2 Minecraft.getInstance().gui.setScreen(...) -> 26.1.2 Minecraft.getInstance().setScreen(...) at lines 159 and 170
- **[high]** onClose rewritten to one line with gui.setScreen; conflicts with 26.1.2's translation — `/home/t3/.t3/worktrees/AlpakaAddons/t3-0ab2ba43/client/AlpakaConfigScreen.java:1138`  
  design-rework collapsed the `if (this.minecraft != null) { this.minecraft.gui.setScreen(this.parent); }` block onto one line. mc-26.1.2 had translated the old block to minecraft.setScreen, so the merge conflicts. Taking design-rework's line does not compile.  
  → Resolve as `if (this.minecraft != null) this.minecraft.setScreen(this.parent);` (26.2 gui.setScreen -> 26.1.2 Minecraft.setScreen)
- **[high]** ColorPickerScreen onClose uses gui.setScreen; conflicts with 26.1.2's translation — `/home/t3/.t3/worktrees/AlpakaAddons/t3-0ab2ba43/client/ColorPickerScreen.java:374`  
  Same pattern as C6: the one-line rewrite conflicts with mc-26.1.2's translated block and uses the 26.2 Gui.setScreen.  
  → Resolve as `if (this.minecraft != null) this.minecraft.setScreen(this.parent);`
- **[high]** GuiFont.refreshChat reaches the chat through gui.hud — `/home/t3/.t3/worktrees/AlpakaAddons/t3-0ab2ba43/client/gui/GuiFont.java:145`  
  `minecraft.gui.hud != null ... minecraft.gui.hud.getChat().rescaleChat()`. On 26.1.2, Minecraft.gui is the HUD itself, so it has no hud field.  
  → 26.2 minecraft.gui.hud.getChat() -> 26.1.2 minecraft.gui.getChat(); drop the `minecraft.gui.hud != null` check: `if (minecraft != null && minecraft.gui != null) minecraft.gui.getChat().rescaleChat();`
- **[high]** topCenterTop reads boss bars through mc.gui.hud.bossOverlay — `/home/t3/.t3/worktrees/AlpakaAddons/t3-0ab2ba43/features/notification/AlpakaNotifications.kt:88`  
  The new 'sit below boss bars' code casts `mc.gui.hud.bossOverlay` to BossHealthOverlayAccessor. On 26.1.2 the boss overlay hangs off Gui directly. The accessor itself is fine: the BossHealthOverlay.events field is identical.  
  → 26.2 mc.gui.hud.bossOverlay -> 26.1.2 mc.gui.bossOverlay (Gui.getBossOverlay())
- **[high]** Config-menu check uses mc.gui.screen(); the hunk also conflicts with 26.1.2's hideGui line — `/home/t3/.t3/worktrees/AlpakaAddons/t3-0ab2ba43/features/notification/AlpakaNotifications.kt:241`  
  Line 241 `if (mc.gui.screen() is AlpakaConfigScreen) return` is new. Because it sits directly above the 26.1.2-translated line 242 (`mc.gui.hud.isHidden()` -> `mc.options.hideGui`), the merge conflicts. Taking design-rework's side brings back both the 26.2 `gui.screen()` and the 26.2 `gui.hud.isHidden()`.  
  → 26.2 mc.gui.screen() -> 26.1.2 mc.screen at line 241; keep 26.1.2's `if (mc.options.hideGui || mc.level == null) return` for line 242 (26.2 mc.gui.hud.isHidden() -> 26.1.2 mc.options.hideGui)
- **[high]** Screenshot tour opens and closes screens via gui.setScreen (lines 140, 289, 318; conflicts at 96, 104) — `/home/t3/.t3/worktrees/AlpakaAddons/t3-0ab2ba43/client/DevScreenshotTour.java:140`  
  The new stops and helpers use `Minecraft.getInstance().gui.setScreen(...)`: the viewmodel-setup close at 140, hudStyle at 289 and fontInChatAndScoreboard at 318. The new stops inserted after 'hud-editor-world' (96) and 'chat-open' (104) also create conflict hunks around lines mc-26.1.2 had already translated. Taking design-rework's side reverts those two lines to gui.setScreen too. The class is compiled into the mod even though the tour is dev-only, so this breaks compile.  
  → 26.2 Minecraft.getInstance().gui.setScreen(x) / mc.gui.setScreen(x) -> 26.1.2 Minecraft.getInstance().setScreen(x) / mc.setScreen(x) at 96, 104, 140, 289, 318
- **[high]** Screenshot-preview stop grabs mc.gameRenderer.mainRenderTarget() — `/home/t3/.t3/worktrees/AlpakaAddons/t3-0ab2ba43/client/DevScreenshotTour.java:108`  
  `Screenshot.grab(mc.gameDirectory, "preview-source.png", mc.gameRenderer.mainRenderTarget(), 1, ...)`. On 26.1.2 the main render target belongs to Minecraft. Screenshot.grab(File, String, RenderTarget, int, Consumer) itself exists in both jars.  
  → 26.2 mc.gameRenderer.mainRenderTarget() -> 26.1.2 mc.getMainRenderTarget() (0440626: 'main render target from Minecraft')
- **[high]** Tour writes and clears chat via mc.gui.hud.getChat() (lines 109, 323, 324, 325, 429) — `/home/t3/.t3/worktrees/AlpakaAddons/t3-0ab2ba43/client/DevScreenshotTour.java:109`  
  The new tour code (screenshot message callback, the chat sample in fontInChatAndScoreboard, the wide-health-bar setup) uses `mc.gui.hud.getChat()`. 26.1.2 has no Gui.hud.  
  → 26.2 mc.gui.hud.getChat() -> 26.1.2 mc.gui.getChat() at 109, 323, 324, 325, 429 (as 0440626 did for the existing tour lines)
- **[high]** Tour clears toasts via mc.gui.toastManager() — `/home/t3/.t3/worktrees/AlpakaAddons/t3-0ab2ba43/client/DevScreenshotTour.java:319`  
  `mc.gui.toastManager().clear();` in fontInChatAndScoreboard. On 26.1.2 the ToastManager is on Minecraft, like screens and the chat listener (0440626 already maps gui.chatListener() -> getChatListener()). ToastManager.clear() exists in both.  
  → 26.2 mc.gui.toastManager() -> 26.1.2 mc.getToastManager()
- **[medium]** String target Hud$1DisplayEntry is Gui$1DisplayEntry on 26.1.2; the mixin is silently skipped — `/home/t3/.t3/worktrees/AlpakaAddons/t3-0ab2ba43/mixin/ScoreboardEntryFontMixin.java:16`  
  `@Mixin(targets = "net.minecraft.client.gui.Hud$1DisplayEntry")` compiles because the target is a string. On 26.1.2 the record is Gui$1DisplayEntry, with the same (Component, Component, int) constructor. The mod declares fabricloader >=0.16.0, so the loader gives it mixin compat 14000, and mixin.debug.targets is off by default. MixinInfo.handleTargetError therefore only logs a WARN ('@Mixin target ... was not found') and does not throw. Result: no crash, but scoreboard lines and scores stay in the vanilla font (only the title changes after C2 is fixed), and score widths are not re-measured.  
  → 26.2 net.minecraft.client.gui.Hud$1DisplayEntry -> 26.1.2 net.minecraft.client.gui.Gui$1DisplayEntry (also update the javadoc's Hud.displayScoreboardSidebar to Gui.displayScoreboardSidebar)

### GUI-Shader und Render-Pipelines

I reviewed every file in assets/alpaka/shaders (rounded_rect.vsh/.fsh, blur_rect.fsh, starlight.vsh/.fsh), the 20 chat_blur_rN post effects, and the Java that feeds them: AlpakaGuiPipelines, the three render states, ModernGuiUtils, HudPanel, AlpakaStyle, ChatBlurFeature, ScoreboardBlurFeature, GuiRendererMixin, GuiGraphicsExtractorMixin, WheelMesh, ScreenshotPreview and HandItemStarlightFeature. I compared them against vanilla 26.2 (gui.vsh/fsh, post/box_blur.fsh, the include files, RenderPipelines snippets, DefaultVertexFormat, BufferBuilder, PostChain/PostPass, GlobalSettingsUniform, GlCommandEncoder). I also built a throwaway harness outside the repo (/tmp/shader-review-559167/harness/Harness.java and Rebind.java). It runs the mod's shaders through Mojang's own 26.2 Vulkan path (GlslCompiler.createIntermediary, then IntermediaryShaderModule.rebind), so the Vulkan location claims are reproduced, not inferred. Nothing in the repository was edited.

Checked and fine:
- The DynamicTransforms/Projection/Globals std140 blocks match vanilla byte for byte.
- ENTITY attribute types match the shader inputs: RGB32F→vec3, RGBA8_UNORM→vec4, RG32F→vec2, RG16_SINT→ivec2 twice, RGBA8_SNORM→vec3. The SNORM8 truncation of opacity and shadow (1/127 steps) is harmless.
- The SDF formula is correct.
- Straight-alpha output with the TRANSLUCENT blend is correct, and so is the panel-over-shadow composite (rgb·coverage/alpha).
- gl_FragCoord/textureSize sampling hits texel centres, with no half-pixel error. It stays consistent on both GL and Vulkan because the copy, the render and the sampling all use image-row coordinates.
- The two-triangle gradient split (TL–BR diagonal) is planar for the colour patterns the code uses, so there is no seam.
- All mod shaders compile under shaderc for Vulkan 1.2.
- On macOS GL 4.1 nothing is used beyond GLSL 330 core. copyTextureToTexture on GL is a glBlitFramebuffer, so it does not need GL 4.3.
- The GUI pipelines sit on vanilla's own GUI snippets, so Iris/Sodium should not interfere. This was not run live.
- Gamma: blending and the box blur both work on sRGB-encoded values, as vanilla does. That is acceptable for UI.

Main problems:
1. Glass panels are opaque copies of the world as it was before any GUI was drawn. They therefore erase GUI drawn before them. With default settings the chat panel's bottom padding cuts the tops off the hearts and hunger icons, and it hides scoreboard text. Today's 1280x720 QA screenshots show this.
2. The blur runs at full resolution: 6 passes of radius up to 20, about 126 bilinear taps per pixel. It runs every frame any panel is visible, which is always on a server with a scoreboard. That is about 3.5× vanilla's menu blur per pixel, and the perf counter only times the CPU side.
3. On the Vulkan backend (selectable in 26.2), the starlight shader's flat mat3 varying overlaps vertexColor at location 2 in both stages.

The rest are low or nit polish items:
- The blur cap at GUI scale 4.
- Anti-aliasing lost at non-pixel-aligned quad edges.
- The screenshot preview's shadow depending on unrelated blur state.
- A pop in the starlight every 20 minutes, when GameTime wraps.
- 3.6k lines of duplicated post-effect JSON.
- The varying order in rounded_rect.vsh only works on Vulkan because `shadow` is declared last.
- Fill and border both blend the same anti-aliased corner pixels.
- Banding rings in the drawPanelShadow stack.
- The sin-based dither hash.
- The integer scale used for the chat hover highlight.
- A wrong comment on the outline thickness at GUI scale 3.

The 26.1.2 line has no Vulkan backend and does not ship starlight yet, so the Vulkan items apply to 26.2 (main and design-rework) only.

- **[high]** Chat glass panel overlaps the hearts/hunger row and paints over its top rows — `features/chat/ChatBlurFeature.java:190`  
  Vanilla keeps the chat box bottom at guiHeight-40 (ChatComponent) and starts the health/food row at guiHeight-39 (Hud). That leaves a 1 px gap. submitPanel extends the panel PADDING = 3 chat px below panelBottom (y1 = panelBottom + PADDING), and the drop shadow (SHADOW = 4) reaches further down. The chat is extracted after the hotbar decorations, so the GUI renderer puts the panel on a higher layer. blur_rect.fsh writes alpha 1 inside the shape, using colour from the copy of the world taken before any GUI was drawn. The top 2 GUI rows of every heart, drumstick and armour icon under the chat's x-range are therefore replaced by blurred world and then darkened by the shadow. With the default 320 px chat width this happens at 16:9 with GUI scale ≥3 (1280x720 auto = 3; 1920x1080 auto = 4), whenever chat lines are visible. Vanilla's per-line backgrounds never cross that row.  
  → Do not pad or shadow the panel downward past the chat's own bottom. Keep y1 ≤ panelBottom (+1 GUI px at most), for example pad only up/left/right, or clamp y1 to (guiHeight - 39) / chatScale. Use an upward-only or no shadow along that edge. GS-2 covers the general fix.
- **[high]** Full-resolution 6-pass box blur (up to 126 bilinear taps per pixel) runs every frame a panel is visible — `features/chat/ChatBlurFeature.java:360`  
  Every frame that has a chat, scoreboard, HUD, inventory or notification panel does the following: a full-frame copy, then chat_blur_rN, which is 3× (horizontal + vertical) of vanilla box_blur over the whole frame at full resolution. Each pass costs R+1 taps (the loop from -R+0.5 in steps of 2, plus one). R = 5 × min(guiScale, 4) × strength, so R = 20 at the defaults (chat and scoreboard blur on, strength 100). That is 6 × 21 = 126 taps per pixel plus 7 full-frame render-target round trips. Estimates: 1080p is about 261 M taps per frame, a 3024x1964 Retina about 748 M, 4K about 1.05 G. Vanilla's menu blur (R = 5) is 36 taps per pixel and only runs behind menus. On a server with a sidebar this cost is paid continuously. The passes cover the whole screen although the panels usually cover around 10-20% of it.  
  → Blur at reduced resolution. A quarter-resolution target (or 1/guiScale) with R ≈ 5 looks like R = 20 at full resolution, needs about 50× fewer taps and also removes the GUI-scale-4 cap (GS-5). Downsample with a blit pass; the GL copy is already a glBlitFramebuffer. Then use 2 Gaussian passes with linear-tap weights, or a dual-Kawase chain. blur_rect.fsh:68 then has to divide by the frame size instead of textureSize(Sampler0). The GUI pipelines already include GLOBALS_SNIPPET, so declare Globals and use ScreenSize. Optionally scissor the passes to the union of panel bounds plus 3R.
- **[medium]** Every glass panel erases the GUI drawn before it (scoreboard text under chat, chat under Slayer/Inventory HUD, hotbar under notices) — `mixin/GuiRendererMixin.java:125`  
  The blurred copy is taken before GuiRenderer.prepare(), when no GUI element has been drawn yet. Inside its shape a panel writes coverage 1, so alpha 1, with that pre-GUI world. Wherever a panel overlaps GUI extracted earlier in the frame, that GUI vanishes inside the panel instead of showing through the glass. Vanilla's translucent chat background, by contrast, leaves the scoreboard readable. In the default layout at 1280x720/GUI 3 the 320 px chat panel overlaps the scoreboard sidebar and cuts its lines. HUDs placed in the HUD editor erase each other and the chat.  
  → Put every glass panel into one early stratum, before the rest of the HUD, so all text, icons, the hotbar and other panels' content draw on top of the glass. Alternatively, when a panel overlaps earlier elements, re-capture at that panel's layer using the existing blur-point mechanism. At minimum, keep panel rects out of vanilla HUD areas and let the HUD editor flag overlaps.
- **[low]** Starlight's `flat out mat3 viewToWorld` aliases vertexColor at location 2 on 26.2's Vulkan backend — `res/assets/alpaka/shaders/core/starlight.vsh:22`  
  In 26.2, players can select the Vulkan backend (Video Settings → Graphics API; DEFAULT tries GL first, then Vulkan). Mojang's Vulkan path rewrites each vertex output's Location to its index in the output list: IntermediaryShaderModule.createFromSpirv writes spirv[locationOffset] = i. It numbers fragment inputs with a per-name counter. A mat3 needs 3 locations but gets one index, so viewToWorld spans locations 1-3 while vertexColor is placed at 2, in both stages. That is invalid SPIR-V interface aliasing. Depending on the driver, the pipeline either fails to create or column 1 of the rotation and the tint/strength overwrite each other. Either way the galaxy is distorted or not world-locked, or the colour is wrong. GL links varyings by name and is unaffected.  
  → Do not pass a matrix varying. Pass `flat out vec2 angles` (yaw, pitch), or a vec4 of their cos/sin, and build rotY*rotX in the fragment shader. Alternatively pass three flat vec3 columns. Then test the Vulkan backend once.
- **[low]** Blur width is capped at GUI scale 4, so it is weaker on 1440p, 4K and Retina despite the doc — `features/chat/ChatBlurFeature.java:328`  
  blurRadius clamps the GUI scale to 4, but panel radius and shadow keep scaling with the real GUI scale (HudPanel.java:53-54). Auto GUI scale is 6 at 2560x1440, 8 on a 3024x1964 MacBook Pro and 9 at 4K. There the blur is 2/3 down to under half as wide in GUI pixels as at 1080p, so panels look less frosted on the Mac this mod is tested on. The class doc says the radius is chosen "so the blur is the same width in GUI pixels whatever the window scale".  
  → Downsampling (GS-3) removes the cap naturally: blur at 1/guiScale resolution with a fixed R. Otherwise, fix the doc comment.
- **[low]** Screenshot preview frame's drop shadow depends on whether some other panel requested the blur — `features/chat/ScreenshotPreview.java:174`  
  ScreenshotPreview submits a BlurRectRenderState (opaque white tint, SHADOW 8) only for its rounded frame and shadow, without calling ChatBlurFeature.request(). pipeline() returns blurRect only when isReady(). `ready` is sticky and only set by a capture that another panel requested. With HUD Style → Blur off (no panels ever request), the element falls back to roundedRect. rounded_rect.fsh has no shadow input and discards everything outside the shape, so the preview loses its shadow. With blur on, it binds whatever stale copy exists.  
  → Give rounded_rect.fsh the same analytic shadow (share the shadow code from blur_rect.fsh:49-57), or draw the preview frame with a plain rounded state plus that shadow, so it does not depend on the blur state.
- **[low]** 20 near-identical post effects (3,620 lines); each radius compiles 6 pipelines on first use — `res/assets/alpaka/post_effect/chat_blur_r20.json:30`  
  chat_blur_r1 … r20 differ only in the Radius value. Each chain is created lazily by ShaderManager.getPostChain and precompiles one RenderPipeline per pass. Dragging Blur Strength walks through up to 20 radii, each compiling or linking 6 pipelines (on Vulkan, 6 pipeline creations), which causes hitches in the config screen.  
  → If GS-3's custom downsample pass is adopted, use one pipeline with its own uniform for the radius. Otherwise quantize to a few strengths, or build the PostChainConfig in code instead of shipping 20 JSONs.
- **[nit]** Quad equals the shape, so the outer half of the 1-px AA ramp is lost at fractional edges; Gradient/Blur halfSize is rounded — `client/gui/RoundedRectRenderState.java:51`  
  The shader fades coverage over d ∈ [-0.5, 0.5], but the quad ends exactly at d = 0. When an edge falls on a pixel boundary that is correct. At a fractional position, the partly covered pixel outside the quad is never rasterized, so straight edges are hard and jump by whole pixels while the corners stay smooth. Affected cases: every AlpakaStyle window and the config screen during the 220 ms scaleAround pop; MenuStyle tile hover pop; the chat hover highlight under the scroll offset; the inventory HUD at non-integer HUD scale; BlurRect panels without a shadow. In addition, GradientRoundedRect and BlurRect send halfSize = round(w·scale)/2 while localPos uses the unrounded half. Example: 170 GUI px at toScreen 3.75 gives quad half 318.75 but halfSize 319, so the SDF edge sits 0.25 px outside the quad and the ramp is cut on all four sides.  
  → Grow every rounded quad by 1 screen px, as BlurRect already does for its shadow: grow = 1/scaleToScreen in pose units and localPos ±(half + 1). Send the size at half-pixel precision, e.g. UV1 = round(size·2) and halfSize = vec2(UV1)·0.25 in the vertex shader.
- **[nit]** Starlight nebula and twinkle jump every 20 minutes when GameTime wraps — `res/assets/alpaka/shaders/core/starlight.fsh:95`  
  Vanilla's GameTime is ((gameTime % 24000) + partial) / 24000, so t = GameTime·1200 drops from 1200 to 0 every 24000 ticks. At that moment the noise offset g jumps by (7.2, 4.8, -6.0) and every twinkle phase jumps, so the whole galaxy pops once.  
  → Make the motion periodic in GameTime: drift along sin/cos(2π·GameTime) circles, and use twinkle frequencies that are integer multiples of 2π/1200. Alternatively pass a mod-side clock through a vertex attribute.
- **[nit]** BLUR_CAPTURE timing measures only CPU command recording, so the blur's GPU cost is invisible — `utils/AlpakaPerf.java:49`  
  AlpakaPerf brackets captureFrameNow with System.nanoTime. On GL and Vulkan that only times issuing the copy and the 6 draws. The GPU work from GS-3 never shows up in the perf section, so a 'blur is cheap' reading there would be misleading.  
  → Use the GpuQuery/TimerQuery API in 26.2 (com.mojang.blaze3d.systems.TimerQuery) around the copy and chain.process, or check with a GPU profiler before 2.0.
- **[nit]** On Vulkan, rounded_rect.fsh only lines up because `shadow` is the last vertex output — `res/assets/alpaka/shaders/core/rounded_rect.vsh:33`  
  Mojang's Vulkan rebind numbers vertex outputs by index, but numbers fragment inputs only over the names the fragment actually declares. rounded_rect.fsh does not read `shadow`, and it works only because `shadow` is declared last. Inserting a new output that rounded_rect.fsh does not read before `opacity`, or moving `shadow` up, would silently shift `shape`/`opacity` on Vulkan only. GL would still look fine.  
  → Add a comment that new outputs must go after the ones both fragment shaders read, or keep the in-lists of rounded_rect.fsh and blur_rect.fsh identical.
- **[nit]** drawRoundedPanel blends the fill and the border over the same anti-aliased corner pixels — `client/gui/ModernGuiUtils.java:128`  
  The fill and the outline share the same outer SDF edge. At a corner fringe pixel with coverage c, the result is border·c + bg·c(1−c) + dst(1−c)² instead of border·c + dst(1−c). Example: c = 0.5 with bg 0x22 and border 0x3B over black gives 38 instead of 29.5. Corners look heavier and darker-haloed than the straight edges on every card, button, field and toggle.  
  → Inset the fill by the border thickness (an SDF offset of thicknessPx), or draw fill and border in one quad with a second colour in the band.
- **[nit]** drawPanelShadow is 5 stepped filled rects: contour rings and full-panel overdraw — `client/gui/ModernGuiUtils.java:141`  
  Alphas 28, 22, 17, 11 and 5 (out of 255), each 1 GUI px larger, give cumulative shadow alphas of about 0.29, 0.20, 0.12, 0.06 and 0.02 in steps of 1 GUI px (that is, guiScale screen px). At GUI scale ≥3 these show as rings. Each layer is a filled panel-sized quad drawn under an opaque panel.  
  → Move the smooth analytic shadow from blur_rect.fsh:49-57 into rounded_rect.fsh (normal.x/y already carry it) and draw one grown quad.
- **[nit]** sin-based dither hash on large gl_FragCoord is precision- and GPU-dependent — `res/assets/alpaka/shaders/core/blur_rect.fsh:30`  
  dot(gl_FragCoord.xy, (12.9898, 78.233)) reaches about 2.2e5 rad at 4K. There fp32 resolution is 1/64 rad and GPU sin range reduction is implementation-defined, so the 'noise' can show structured patterns on some GPUs (Apple, AMD). Its amplitude is ±0.5/255, so it is mostly invisible.  
  → Use interleaved gradient noise, fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715)))), or an integer hash on uvec2(gl_FragCoord.xy).
- **[nit]** Chat hover highlight rounds the chat scale to an integer, so the SDF is not in screen pixels — `features/chat/ChatBlurFeature.java:227`  
  RoundedRectRenderState takes an integer guiScale, and submitHoverHighlight passes Math.max(1, Math.round(toScreen)) while the radius is Math.round(3 * toScreen). Example: at chat scale 0.5 and GUI 3, toScreen 1.5 rounds to 2, so one SDF unit is 1.33 screen px. The anti-aliasing then fades over 0.75 px and the radius comes out at 3.75 px instead of 4.5.  
  → Use GradientRoundedRectRenderState with four equal colours; it takes a float scaleToScreen.
- **[nit]** hairline() gives 2 px at GUI scale 3, not the hairline its comment promises — `client/gui/ModernGuiUtils.java:94`  
  Math.round(3 * 0.5f) = Math.round(1.5f) = 2, so outlines are 2 screen px at GUI scale 3. The comment says 'a hairline at GUI scale 2 and 3'.  
  → Use scale / 2 (integer division) for 1 px at 2-3 and 2 px at 4-5, or fix the comment.

### Visuelle QA bei 1280×720, 1920×1080, 2560×1440

All three tours of design-rework (HEAD 3f58992) ran with exit 0 and saved 62 screenshots each: /tmp/alpaka-review/shots/qa-1280x720, qa-1920x1080 and qa-2560x1440. Auto GUI scale gives 1280x720 scale 3 (427x240 GUI), 1920x1080 scale 4 (480x270) and 2560x1440 scale 6 (427x240). So 720p and 1440p have exactly the layout of the 854x480 baseline, and only their pixel-level rendering differs. 1080p is the only size that gets more room.

Fine at every size: no text overflows its box, columns line up, dialogs, colour picker, pause menu and main menu are centred correctly, health bar variants and top-centre/boss-bar placement work. 1280x720 is essentially as clean as the baseline. Problems by size:

- **2560x1440 (scale 6):** the smooth font is drawn from the 4x raster, so stems vary from 4 to 6 px and edges step. Blur is capped at the scale-4 radius, so it is weaker. Panel shadows show visible bands. Knobs, dots and the magnifier, built from GUI-pixel shapes, look square or stepped.
- **Every 427-px-wide size (854, 1280, 2560):** the chat's blurred panel and the top-centre notices erase the left part of the scoreboard sidebar. The config menu also hides sidebar sections and list rows, with no scroll bar.
- **1920x1080:** a 1-px sliver of the first feature row shows at the top of the list. The Quick Commands rows keep scroll-bar padding when there is no scroll bar.
- **Every size (worth fixing for a polished 2.0):** smooth text keeps vanilla's drop shadow and looks doubled. Notices sit over the hotbar and under HUD-editor previews. HUD editor labels run off screen. Glyphs Nunito lacks fall back to pixel art.

Animation timing was ignored, e.g. the empty chat panel in 2560 tour-scoreboard-starlight.png, which only comes from the slow software render. Evidence crops are in /tmp/alpaka-review/qa-crops/ with names starting qa-142250-.

- **[medium]** Chat's blurred panel erases the scoreboard sidebar wherever they overlap (all 427-px-wide GUIs: 854x480, 1280x720, 2560x1440) — `features/chat/ChatBlurFeature.java:187`  
  With default chat width (320) the blur panel reaches x = panelWidth + 8 + PADDING, about 335 GUI px. The sidebar starts at about 293 GUI px on a 427-wide GUI. The panel samples the blurred world captured before the GUI, so the sidebar text under it is wiped out instead of showing through, as it would under vanilla's translucent chat box. In tour-scoreboard-starlight-later the sidebar reads 'acombs(M7)' and 'ed:100%' instead of 'Catacombs(M7)' and 'Cleared:100%' at 854, 1280 and 2560. At 1920x1080 (480 wide) the panel stops short of the sidebar and both are intact.  
  → Draw the sidebar after the chat panel (or exclude the sidebar's rectangle from the panel), or clip the chat panel's right edge at the sidebar's left edge when they overlap. At minimum don't grow the panel past vanilla's box (drop the extra 8 + PADDING on the right).
- **[medium]** Config menu columns hide content with no scroll bar or other hint (sidebar sections, feature rows, settings) — `client/AlpakaConfigScreen.java:560`  
  None of the three scroll areas (drawSidebar, drawFeatures, drawDetails) draws a scroll bar. On a 427x240 GUI (854x480, 1280x720, 2560x1440) the sidebar ends with '• Blaze' cut in half at the panel's bottom edge, and 'Tools' and 'Social' are hidden. The Chat feature list hides 'Better Screenshots' and 'Chat Style'. The Chat Tabs settings end mid-row on 'Tabs While Peeking & Tab Key'. At 1920x1080 the sidebar fits, but Slayer Session HUD's settings still end on a half-visible 'Polar bear effect' row with nothing indicating more. The Quick Commands editor does draw a thin bar, so the config is inconsistent with its own sub menu.  
  → Draw the same 2-px rounded thumb CommandWheelConfigScreen uses (lines 246-252) at the right edge of each column when scroll.max > 0. Optionally fade the last visible row.
- **[low]** At GUI scale 6 (2560x1440) the smooth font is a 4x raster blown up: stems 4-6 px wide, stepped edges — `client/gui/GuiFont.java:84`  
  scaleSuffix() picks _s4 for every scale from 4 up. At 2560x1440 (Auto scale 6) every menu, HUD, chat and scoreboard glyph is drawn from the 4x-oversampled raster and enlarged 1.5x. Pixel analysis of the same description text: at 1920x1080 (s4 at scale 4, 1:1) vertical stems are uniformly 3-4 px. At 2560x1440 they alternate between 4 and 6 px (e.g. scanline y=410 of tour-config-chat.png: runs 6,6,6,6,4,20,6,6,6,6,6,4...). Diagonals of 'A' and 'y' show doubled anti-aliasing pixels in 2-px steps. This confirms review item 508/532 visually; it hits every 1440p monitor and Retina MacBook in fullscreen.  
  → Ship _s5/_s6 (and ideally up to _s8) definitions, or pick the oversample from the actual scale with a generated definition. Failing that, choose the variant whose oversample divides the scale (s3 for scale 6) rather than s4.
- **[low]** Top-centre notices cover the sidebar's left edge on 427-wide GUIs and collide with vanilla toasts at every size — `features/notification/AlpakaNotifications.kt:302`  
  Notices are a fixed 196 GUI px wide and centred: x 115..311 on a 427-wide GUI. The sidebar starts at about 293, so the blurred notice wipes its first characters: '9:40pm' -> '40pm', 'Purse:' -> 'rse:', 'Bits:' -> 's:' (854, 1280, 2560). Vanilla toasts (160 px at the top right, x 267..427) also sit on top of the notice's right end at all sizes. At 1920 (480 wide, toast from x 320, notice to x 338) the 'Advancement Made!' toast still covers the first notice's top-right corner.  
  → Narrow top-centre notices when screenWidth/2 + WIDTH/2 would reach the sidebar or toast column (e.g. width = min(196, screenWidth - 2*(toast/sidebar width) - margins)). Or start them below the toast stack when toasts are showing.
- **[low]** Blur radius is capped at the GUI-scale-4 value, so HUD, chat and notice blur is visibly weaker at 1440p (scale 6) — `features/chat/ChatBlurFeature.java:328`  
  blurRadius() clamps guiScale to 4 and MAX_BLUR_RADIUS is 20 px (effects shipped only up to chat_blur_r20). At scale 6 the radius should be 30 px for the documented '5 screen px per GUI px'. The panels get 3.3 GUI px of blur instead of 5. In tour-third-person the player's legs behind the inventory-HUD panel are a soft smudge at 854 and 1280 but have clearly defined edges at 2560. The class comment says the blur is 'the same width in GUI pixels' at every scale.  
  → Ship chat_blur_r21..r40 (or generate them), or blur a half-resolution copy at high scales so the GUI-relative radius stays constant.
- **[low]** Toggle knobs and track ends are not round at higher scales (integer halving of odd sizes) — `client/AlpakaConfigScreen.java:925`  
  The knob is TOGGLE_H-4 = 5 GUI px with radius 5/2 = 2. The track is 9 px high with radius 9/2 = 4. At 854 the 10-px knob passes for a dot. At 2560 it is a 30x30 px rounded square with 12-px corners, and the track ends have a flat 6-px segment. Every switch in the config, setup mode and sub menus looks like a squircle beside the otherwise round shapes. The hovered slider knob (7 px, radius 3) behaves the same way.  
  → Pass the radius in screen pixels as size*guiScale/2 (submitRounded already takes radiusPx), or make knob and track sizes even.
- **[low]** Magnifier icon and '⋮' dots are built from GUI-pixel squares and turn blocky at 1080p/1440p — `client/AlpakaConfigScreen.java:545`  
  drawSearchIcon draws a 7x7 rounded outline (radius 3, a squircle) plus three 1x1 GUI-px fills as the handle. drawDots (line 656) stacks three 1x1 fills. At 2560 the handle is a detached staircase of 6x6-px blocks, thicker than the 3-px anti-aliased ring, and the 'more settings' dots are 6x6 squares. At 854 both pass as small pixel marks.  
  → Draw the handle as one rotated quad or rounded rect (like drawChevron's smooth strokes) and the dots with drawRoundedRect(…, 1, …) so they stay round.
- **[low]** Glyphs Nunito lacks ('●', '✕') fall back to the pixel font and show as pixel art beside smooth text — `features/mainmenu/MenuStyle.kt:273`  
  nunito_semibold.ttf has no U+25CF or U+2715 (checked in its cmap), so the reference to minecraft:default draws them. The main menu's Hypixel status dot is a blocky 'plus' inside the smooth pulse ring (statusLine centres the ring on font.width of the fallback glyph). The config search's clear button (AlpakaConfigScreen.java:534) is a stair-stepped unifont X. The bigger the GUI scale, the more obvious: at 2560 each source pixel is 3-6 screen px.  
  → Draw the dot with drawRoundedRect, and the ✕ as two smooth strokes (as drawChevron does), or use glyphs the TTF has (U+2022 '•' exists in Nunito).
- **[low]** Smooth font keeps vanilla's 1-GUI-px drop shadow in notices, chat and HUDs: looks doubled — `features/notification/AlpakaNotifications.kt:365`  
  Notice title and body, the smooth chat and the HUD texts are drawn with the default shadow. The shadow is a full GUI pixel offset (3/4/6 screen px at 720p/1080p/1440p) and as heavy as the thin TTF strokes, so letters read as a ghosted double. It is worst for §8 dark-grey chat text ('- a message arriving in a burst'), where text and shadow are nearly the same tone and look hollow. The menus, which draw without shadow, are clean by comparison. This shows at every size and is more prominent the larger the window.  
  → Draw smooth-font HUD/notice text without shadow (the panels already give contrast), or with a lighter/half-pixel shadow when the smooth font is on; for chat, drop the shadow in the GuiMessage mixin when GuiFont.inChat().
- **[low]** Bottom-right notices (the default corner) sit on the hotbar at every tested size — `features/notification/AlpakaNotifications.kt:284`  
  A bottom-corner notice is placed at screenHeight - MARGIN - height and is 196 wide. On a 427-wide GUI it spans x 223..419 and covers hotbar slots 6-9. On 480-wide (1080p) it spans 276..472 and covers slots 7-9 and the item counts there. The hotbar is 182 wide and centred, so the corner would have to be at least 590 GUI px wide to clear it. The placement logic is unchanged from main, but the redesign's larger blurred notices make it more prominent.  
  → For bottom corners start the stack above the hotbar/health rows when screenWidth/2 + 91 > screenWidth - MARGIN - WIDTH (e.g. screenHeight - 22 - hearts/armour clearance), as the top-centre corner already avoids boss bars.
- **[low]** HUD editor labels run off screen and the hint line crosses the Player Model box — `client/hud/HudEditorScreen.kt:203`  
  labelX is centred on the HUD box and never clamped. With default positions 'World Age (Day)' is drawn from x<0 and reads 'orld Age (Day)'. 'Slayer Boss Timer' starts off the left edge (on top of the Slayer HUD's 'Since Dice' row). This happens at all four sizes. The hint line (line 126, y = BAR_Y + BAR_H + 5) is drawn straight across the Player Model preview, so the box's outline runs through 'HUD to select it'.  
  → Clamp labelX to [2, width - labelWidth - 2]. Draw the hint on a small backdrop, or skip it when a HUD box intersects its row.
- **[low]** Chat tabs stay in the pixel font with square boxes beneath the smooth-font, rounded, blurred chat — `features/chat/ChatTabsFeature.java:269`  
  With smooth chat on, the chat panel is rounded and blurred and its lines are in Nunito. The tab row under it ('All Party Guild Co-op PMs Server Mods') is still vanilla pixel text with shadow on square fills, so the two read as different UIs. The mismatch grows with resolution: at 2560 the pixel tabs are 6-px-per-texel blocks right under the anti-aliased chat.  
  → Draw the tab labels through HudPanel.text/GuiFont when GuiFont.inChat() is on, and use rounded fills for the tabs.
- **[nit]** 1920x1080: a 1-GUI-px sliver of the first feature row shows as a stray line at the top of the list — `client/AlpakaConfigScreen.java:601`  
  In the Chat section at 1920x1080 the list overflows by only 21 px. revealSelected scrolls to the end (featureScroll.max = n*23 + 4 - clipH while rows start at clipY + 2), which leaves the bottom 1 GUI px (4 screen px) of the 'Chat Tabs' row visible above 'Chat Search'. It reads as a stray horizontal line under the header. It does not happen at 427x240, where whole rows are cut.  
  → Make the bottom padding match the top (use +2 rather than +4 together with the 2-px top inset) and snap the reveal/end scroll to a row boundary when the overflow is less than one row, or fade rows at the clip edge.
- **[nit]** Panel drop shadow shows hard bands at GUI scale 4-6 — `client/gui/ModernGuiUtils.java:141`  
  drawPanelShadow stacks six rounded rects 1 GUI px apart. At scale 2 each band is 2 screen px and reads as a soft shadow. At 2560x1440 each band is 6 px, so the shadow around the config/setup panel (and every AlpakaStyle.panel) is five distinct stripes of decreasing darkness. This is most visible in viewmodel setup mode, where the panel sits over the world.  
  → Use the shader shadow BlurRectRenderState/RoundedRect already supports (the shadowPx falloff), or step the rings in screen pixels (1/guiScale GUI px) instead of whole GUI pixels.
- **[nit]** HUD editor draws its previews over notices, hiding the notice text — `client/hud/HudEditorScreen.kt:98`  
  In the HUD editor a notice is drawn with the HUD, under the screen, so the Inventory HUD preview box covers the left half of 'Boss Spawned!' and its body at every size. The tour's hud-style stop exists to show that notice. The config screen already calls AlpakaNotifications.renderOverScreen for exactly this; the HUD editor does not.  
  → Call AlpakaNotifications.renderOverScreen(graphics) after drawBar in extractRenderState, as AlpakaConfigScreen does.
- **[nit]** HUD editor bar runs under vanilla's top-right toasts, hiding Done — `client/hud/HudEditorScreen.kt:61`  
  The bar is about 227 GUI px wide and centred, ending at x 327 (427-wide) or 353 (480-wide). Toasts occupy x 267..427 or 320..480 and draw above screens. At 1920x1080 the tutorial toast hides 'Done' except its 'D'. At 427-wide sizes it hides Done and cuts 'Reset All' to 'Reset Al'.  
  → Leave the toast column free: shift the bar left when width/2 + w/2 > width - 160, or put Done at the bar's left/bottom.
- **[nit]** Quick Commands rows always reserve scroll-bar room, so at 1080p (no scroll bar) the right padding is uneven — `client/CommandWheelConfigScreen.kt:210`  
  rowW = listW - SCROLLBAR_ROOM even when maxScrollY == 0. At 1920x1080 the list fits and there is no bar, yet the rows end 6 GUI px short of the input/button row below them. Right insets from the window edge: Done and the header hairline 8 GUI px, bottom row 12, command rows 18, page counter about 23. At 427x240 the bar fills that gap; at 1080p it is just an empty gutter.  
  → Subtract SCROLLBAR_ROOM only when maxScrollY > 0 (compute it before laying out rows), so rows align with the input row.
- **[nit]** Rounded outlines are 2 px at scale 3 (1280x720): visibly heavier than at the other sizes — `client/gui/ModernGuiUtils.java:96`  
  hairline(3) = round(1.5) = 2. Toggle and search-pill outlines are therefore 0.67 GUI px at 720p versus 0.5 at 854, 1080p and 1440p (1/2/3 px), and the 720p switches look bolder than in the baseline. This visually confirms review item 1858.  
  → Use Math.max(1, scale / 2), or accept 2 px and update the comment, after comparing qa-1280x720/tour-config.png.
- **[nit]** Screenshot preview starts visibly soft at 1440p (960-px texture enlarged up to 2.7x) — `features/chat/ScreenshotPreview.java:40`  
  MAX_TEXTURE_WIDTH = 960. At 2560x1440 the first frames of the shrink show the picture at about 1900 px wide, so chat text in it is blurry (tour-shot-preview-flash). At 1280 the enlargement is only 1.3x. It is short and partly under the white flash, but the drop from crisp to soft is noticeable on large screens.  
  → Scale MAX_TEXTURE_WIDTH with the window (e.g. min(window width, 1920)), or keep 960 only for the thumbnail stage.
- **[nit]** Main-menu row icons sit about 1 GUI px right of their tile's centre — `features/mainmenu/MenuStyle.kt:322`  
  iconTile draws the glyph at -font.width/2 + 1. Measured on the Singleplayer tile, the icon centre is 2 px right of the tile centre at 854 and 6 px right at 2560. At 1440p the person, flask and puzzle icons look pushed against the right of their tiles.  
  → Centre on the glyph's visible width (drop the +1 or subtract the bitmap font's trailing advance).
- **[nit]** Colour-picker channel tracks: square gradient inside a rounded outline (the promised round caps are not drawn) — `client/gui/AlpakaStyle.java:174`  
  The comment says 'the rounded ends are drawn as caps', but only square slices are filled inside a radius-2 outline. At 2560 the square gradient ends sit inside the round frame with a dark gap, which looks unfinished compared with the round knobs.  
  → Draw the end slices with drawRoundedRect (radius h/2) or clip the gradient with the rounded pipeline.

## Abdeckung und Grenzen

Every one of the 140 Java/Kotlin files under src/main was listed by name in at least one domain slice. All src/main files changed on design-rework were in D slices, and origin/main has no commits missing from HEAD. Resources were owned by X2 (whole assets tree), D4 (mixins.json, nunito s2 JSONs) and C3 (shaders). Lang files were covered (X2-15). They hold only keybind names, since the UI uses Component.literal throughout. The font JSONs for scales 3 and 4 match the reviewed s2 ones except for oversample. 73 Java/Kotlin files got no confirmed finding. Most are small accessors or files that reviewers explicitly cleared (HudPanel, the font mixins), so having no findings there is not a gap. The real gaps are cross-cutting: porting the redesign to 26.1.2 (javap shows Hud→Gui, the Hud$1DisplayEntry string target, ContextualBar→ContextualBarRenderer and minecraft.gui.setScreen need translating, while the font hook signatures are unchanged), no runtime visual check at other GUI scales or window sizes, and no compatibility check against Sodium, Iris, ImmediatelyFast, SkyHanni or Skyblocker. Working files I wrote: /tmp/alpaka-review/critic-allfiles.txt, critic-slicefiles.txt, critic-covered.txt, critic-findingfiles.txt, critic-prompt-*.txt.

- **26.1.2 backport compatibility of the redesign**: The only slice that touched the 26.1.2 line was X2, and only for version tags (X2-9, plus the OneClient/Modrinth tooling). Nobody checked whether the redesign's new code can be ported to the backport branch. I checked a few targets against the 26.1.2 jar with javap. The font hooks mostly carry over unchanged: GuiMessage.splitLines→ComponentRenderUtils.wrapComponents, TrueTypeGlyphProviderDefinition.load, com.mojang.blaze3d.font.TrueTypeGlyphProvider (ctor/loadGlyph/close FreeType calls), NativeImage.copyFromFont, the FontTexture(Supplier,GlyphRenderTypes,boolean) ctor and BossHealthOverlay.events all exist with the same signatures. The places that do need translating: HudScoreboardFontMixin and WideHealthBarMixin target Hud, but 26.1.2 calls it Gui. ScoreboardEntryFontMixin targets the string "net.minecraft.client.gui.Hud$1DisplayEntry", which is Gui$1DisplayEntry in 26.1.2 and will not show up in a compile error. WideHealthBarMixin's redesign-changed injection targets contextualbar/ContextualBar.extractExperienceLevel, which is the interface ContextualBarRenderer in 26.1.2. The new sub menus call minecraft.gui.setScreen (AlpakaConfirmScreen:118, ColorPickerScreen:374, AlpakaConfigScreen:1138), and GuiFont:145 calls minecraft.gui.hud.getChat(). The merge commit 0440626 says 26.1.2 uses Minecraft.setScreen and puts HUD hooks on Gui. The branch has also drifted: local mc-26.1.2 is 47 commits behind origin/main, and origin/mc-26.1.2 still points at a6dde30. A javap pass over every @Mixin/@At target, plus a port checklist, would let 2.0 ship on both lines without a startup InjectionError (defaultRequire is 1).
- **Runtime visual check of the redesign across GUI scales and window sizes (including Mac Retina)**: Every slice reviewed the code by reading it. The screenshots in /tmp/alpaka-review/shots and the c31/font1/font2 PNGs come from one dev-tour run at one window size and one GUI scale. DevScreenshotTour never changes guiScale or the window size. Several findings only hint at scale problems and were never seen on screen: D4-12 (smooth font is soft at scales above 4), D4-6 (notices overlap boss bars at scale 4), D1 (details-column scroll limit, click areas not clipped to the viewport), C6-11 (hairline at scale 3), and D3 (the HUD editor's top bar covers top-centre HUDs). The user plays on a MacBook Pro, where the framebuffer is about 3024x1964 and auto GUI scale lands above 4. Nothing has been checked at GUI scale 1-2 on a small window (854x480) or at scale 5-6. Text overflow, ellipsis and layout in the 1700-line config screen, the AlpakaStyle sub menus and the HUD panels are therefore unverified. The Xvfb tour setup already exists, so running it at a few options.txt guiScale and window-size combinations is cheap.
- **Compatibility with commonly co-installed client mods (rendering and SkyBlock mods)**: No slice owned this. In all review outputs, Sodium, Iris and ImmediatelyFast are never mentioned, and Skyblocker and Firmament only come up in data-parsing comments. The @Redirect findings (C3-3, C4 LightningBolt, X1) are explicitly speculative; the verifier wrote that it "could not name a specific co-installed mod". These are the concrete exposures. Custom GUI pipelines and core shaders (rounded_rect, blur_rect) run alongside Iris. FontTextureMixin changes the sampler on FontTexture, which ImmediatelyFast's font-atlas changes also touch. LightmapMixin, LevelRendererMixin and ItemInHandRendererMixin can clash with Sodium or Iris and with viewmodel mods. ChatComponentMixin (@ModifyArgs on forEachLine, @WrapOperation on addMessage and addMessageToDisplayQueue) overlaps with SkyHanni and Skyblocker chat hooks. The scoreboard font and blur hooks overlap with SkyHanni's custom scoreboard. Because defaultRequire is 1, any injection conflict means the game will not start. fabric.mod.json declares no breaks or conflicts. A static scan is feasible and specific: take the current 26.2 builds of these mods, collect their mixin targets, and intersect them with ours. A runClient with them in run/mods is the second step.
- **GUI shaders (rounded_rect.vsh/.fsh, blur_rect.fsh)**: C3 was given 'shaders (all files)' but reviewed only starlight.fsh (2 findings). blur_rect.fsh is not mentioned anywhere in the review outputs. rounded_rect.vsh appears once, in C6-25, a comment finding about the shadow encoding. These shaders draw every AlpakaStyle panel, HudPanel, and the chat and scoreboard glass, so the redesign's whole look depends on them. They are small (44+44+95 lines) and unchanged versus origin/main. My spot check found nothing wrong: the SDF works in screen pixels, which gives a 1 px anti-aliased edge; the blur sample uses gl_FragCoord against a frame-sized copy; and the dither is ±0.5/255. A full review is unlikely to find much beyond what the visual-check follow-up would catch.
- **Aggregate runtime performance and AlpakaPerf coverage**: Mixin hot paths were covered piece by piece: each mixin sat in exactly one slice with a hot-path brief, C7 deliberately reported no perf items, and X1 and C1 both caught the @ModifyArgs boxing. All 37 approved perf items, however, rest on reading the code; nothing was measured. AlpakaPerf only times the HUD callbacks, blur capture, chat replay, entity rules, name tags and the slayer tick. It has no section for the redesign's scoreboard font hooks (ScoreboardEntryFontMixin, HudScoreboardFontMixin), ScoreboardBlurFeature, or the C3 per-frame paths: the Etherwarp overlay's CompoundTag copy, the motion-blur step loop, and the item fade in GuiGraphicsExtractorMixin. So /alpakadebug perf cannot show what those changes save. This is only worth doing if the user wants before/after numbers (a JFR allocation profile of the Xvfb tour). It would not find new bugs.
- **Exception containment in render and tick hooks**: No slice owned the policy question of whether server-controlled input (sidebar, tab list, chat, entity names) or a hand-edited config can throw inside a per-frame or per-tick hook. There are 0 catch blocks in mixin/, so any such throw crashes the client. Single instances were found (D1-12 null getText crashing the config screen every frame, C5-20 null wheel pages, C1-23 bad -Dalpaka.scrollRate). A spot check of the numeric parsing in features and utils found every parse guarded (toLongOrNull, toIntOrNull, NumberFormatException catch), so a dedicated pass is unlikely to pay off.

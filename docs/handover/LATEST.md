# Latest state when the old project's threads were closed (2026-11-11 23:10 Brisbane / 13:10 UTC)

This is newer than THREADS.md, and it wins where the two differ. Every Minecraft and ZomboidCraft thread in the old project was stopped and closed at this point. Only the Unreal 5 thread and Cortana R&D stayed there.

## Endless Night: Minecraft

### LANDSCAPE: packed Reach world (mid-install)
- 22:34 his clock: picked **A, match heights and widen the joins**, because the join previews showed the missions' ground 60-600 m apart. In the tight layout the 160 m seams became walls.
- Height-aware layout: about 13 x 11 km, grade at most 1 in 3. Vertical shifts: m35 -506 m, m45 -99, m70 +44, m10 +19. The sea missions (m20 m60 m50 m52) stay fixed. Every mission drives to every other at no worse than about 1 in 2.
- Layout map v4: https://claude.ai/artifact/UoKbaJE3VsM1psx8cTCxTT. **He said "ok" to the respaced layout at 22:56.** The first join previews are on the same page: ground rolls from Sabre into its neighbours. The cloud renderer still shows some Halo far land white, but the game draws it textured.
- 23:04: he picked **Install tonight**. The thread was copying the files when the project split. It backs up first to additions\jar-backups, then installs the new world files plus the latest jar. **The install may be unfinished: check the PC before anything else.**
- Install set: /mnt/project-files/endless-night/reach-world/transit/place6/ in the OLD project's files. It contains reach.json md5 b810ed8b, world/land.zrh 6c372379, world/sky_regions.zsk f37f5647 and 15 .zrv. It goes into the ENDLESS NIGHT profile's config\endlessnight\reach\ and needs a fresh world or chunks.
- All the build tools lived in the cloud container (/tmp/claude-0/pack/h) and are gone. memory/zomboid-reach-packed-build-tech.md records the method, the deltas and the md5s.
- Still to do after install: walk the joins in game. Move the Covenant spawn-sites.json and MP placements.json coordinates by the deltas. Fix the abrupt edges where our land meets Halo's far land cliffs.

### Custom games with bots: done, untested by him
- The custom games went into the playtest jar at 22:38. The lobby is MCC's custom-game screen on MULTIPLAYER with a Bots tab. All 20 Reach MP maps are arenas. Game types: Slayer, Team Slayer, TU DMR, SWAT, Infection, Escalation and Juggernaut. Bots wear their own armour on the animated Spartan body. The game has a HUD, kill feed, carnage report and MCC's real loading screens. Friends can join mid-match. `/en customgame end` ends a stuck match.
- 22:47 Josiah, verbatim: "no map, weapons and character are glitched out. im assuming youve landed me on the multiplayer map in the reach built combined world. i need that to not happen. i need you to take the multiplayer maps and have them built into their own blank canvas so that only the multiplayer map and its surrounding environment/backdrops are present, not as an add on to the reach world map otherwise it will never load properly"
- 22:58 fix in the playtest jar (CI green). Each MP map already sat in its own empty dimension, but the client kept loading the whole Reach world from his save. Now the Reach world does not load while a custom game starts. Any Reach world load still running is dropped when the map opens. Only the map's own pieces, props, backdrop and sky load. **Not yet confirmed by him.** The glitched weapons and Spartan are believed to come from the same overload, but this is unconfirmed: ask for a screenshot if they persist. The 32-player frame-rate check has not been run.

### Spartan body and weapons
- The Spartan third-person body is in playtest jar 20605a5. Preview, approved: https://claude.ai/artifact/59LHPWeCiQKUJiZqerMTnY. He still has to test it in game.
- **Reach inventory icons: waiting on his pick.** A = each weapon's own Reach model side on, like the Tarkov guns (recommended). B = Reach's blue HUD outlines. Preview: https://claude.ai/artifact/67xbx8vwFgt4zVn2MwyX1U (PNG also on the PC at additions\halo-reach\previews\reach-icons-preview.png). Option A is built on a branch with a green build check and goes into playtest once he picks.
- New rule, 12:33 his message "i need the previews viewable here": every preview is posted as an Artifact page (full detail, kept light), not as a PC path or a thread attachment.

### CI and repo housekeeping
- The handover pack PRs merged: ENDLESS-NIGHT #1, ZOMBOIDCRAFT #6. ZC #6 also bumped main's Gradle wrapper to 9.9.0, because Loom 1.20 needs it.
- ENDLESS-NIGHT #2, merged into playtest: build artifacts are kept 1 day. The Freesound fetch artifacts are also kept 1 day. There is a new workflow "Clear old artifacts" (.github/workflows/clear-artifacts.yml, runs from the Actions tab) that deletes every stored artifact. It ran once and deleted 164 artifacts (about 24 GB), because the account's Actions storage was full. The playtest build already continues past a failed artifact upload, so the playtest release jar was never blocked.

## ZomboidCraft (frozen county world)
No ZomboidCraft work happened between the pack and the close. THREADS.md stands as written.

## Stayed in the old project (not yours)
- "Move Endless Night to Unreal 5": the Unreal rebuild. Do not start Unreal work in the Minecraft project.
- "Bring Cortana into the game": Cortana R&D (MaskedMimic, IsaacLab, gestures). The in-game Cortana code already in the EN jar is yours to maintain.

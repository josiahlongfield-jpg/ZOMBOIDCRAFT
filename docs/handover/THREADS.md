# Every thread in the old "ENDLESS NIGHT" project (snapshot 2026-11-11 ~12:25 UTC)

The old project had 48 threads. A new project cannot open them, but Josiah can. Column **Goes to** says which new
project owns that work now: **EN-MC** = "Endless Night: Minecraft" (Reach world, repo ENDLESS-NIGHT),
**ZC** = "ZomboidCraft" (frozen county world, repo ZOMBOIDCRAFT), **UE** = stays in the old project, which becomes the
Unreal 5 version. Decisions from each thread are saved word for word in `memory/` (see the memory notes named in Notes).

Threads still running when this was written (bots, Spartan weapons, LANDSCAPE, Cortana, Unreal) will have moved on.
Before continuing one, ask Josiah for its last message or check the repo branch / playtest log named here.

## Open work (not finished)

| Thread | Goes to | State | Where it stands / next step |
|---|---|---|---|
| Add bots to Reach custom games | EN-MC | working | MCC Reach custom-games lobby (MAP/GAME/OPTIONS/BOTS/START) is in the playtest jar, opened from MULTIPLAYER on the main menu. All 20 Reach MP maps built as arenas on the PC; arena dimension, Slayer/Team Slayer/SWAT/Infection/Escalation/Juggernaut rules with Reach shields (70) + health (45), bots (31 Reach gamertags, own armour, aim/difficulty/teams, drawn with the animated Spartan body), match HUD, kill feed, carnage report, MCC loading screen, friends joining: all ticked at 12:23. NEXT: build, merge into playtest, send jar link, then frame-rate test at 32 players. Josiah (11:51): "continue on with each phase in sequence without asking me first and ill look at the finished product". Memory: reach-bots-custom-games.md. Preview approved 11:35: https://claude.ai/artifact/RxPE2mByt5qwYUfnFv9Tmu (options). |
| Make my Spartan and Reach weapons playable | EN-MC | review ready | Spartan third-person body in playtest jar 20605a5 (Reach's own 303 clips for 21 weapons, Armory armour + colours, left hand pinned to the gun, F5). Preview approved: https://claude.ai/artifact/59LHPWeCiQKUJiZqerMTnY. Josiah must test it. NEXT: Reach inventory icons for Halo weapons (inventory still shows Tarkov-looking icons). Also from memory: AR ammo counter (digit bitmap), beam visuals, scope PNGs vs screenshots. Memory: endless-night-reach-weapons-ingame.md, endless-night-spartan-body.md, endless-night-loadout-command.md. |
| LANDSCAPE | EN-MC (Reach world) / ZC (county history) | working | Building the APPROVED tight packed layout (https://claude.ai/artifact/UoKbaJE3VsM1psx8cTCxTT, ~11x10 km, ~95 km² playable; Josiah 11:40 "prioritize the detail, with less open land ... like for like walkthrough of the reach game"; 11:43 "so long as you can make it flow from one area to another im happy to go ahead"). Done: packed land with blended joins, collision/towers/start spot moved, sky regions moved. NEXT: full-detail previews of the joins, then install on PC (land.zrh, reach.json, sky_regions.zsk, every .zrv; backups first in additions\jar-backups; needs a fresh world) and check in game; then tell Covenant spawn-sites.json + MP placements.json to move by the deltas. Pending install: new jar (no square clouds, no grey city blocks, fog cap 80%). The pipeline lived in /tmp/claude-0/pack (lost with that container): rebuild from memory zomboid-reach-packed-build-tech.md. County LANDSCAPE was paused 2026-10-31 10:22 ("stop development for now"), see zomboid-landscape-pause-after-list.md. |
| PLAN | EN-MC | blocked on Josiah | Waiting for his CAPITALS answers M1-M5 on https://claude.ai/artifact/4XVCdmR8SnyBPfppKS4hWZ (replace every Minecraft effect with Reach; M1 = mute leftover MC sounds/particles as a safety net). Then pull Reach effects from PC, full-detail previews, build. Josiah 11:49: "remove any of the minecraft effects like melee sounds, the run flash when you damage an enemy, the damage tick on a player, any and all minecraft style art or animations. everything needs to be reach". Memory: endless-night-no-minecraft-effects.md. |
| Review Armory previews | EN-MC | review ready | Gunsmith approved and in playtest; in-game check on 29 guns/58 builds done; title/list overlap + "Locked pieces open as you level up" text removal (everything is unlocked) going to playtest. NEXT: fresh screenshots after the re-check; post the base Armory station mock-up. Memory: zomboid-armory-ingame-tech.md, endless-night-gunsmith.md, endless-night-mcc-menus.md, zomboid-reach-armory-tech.md. |
| Review character selection screens | EN-MC | review ready | Main menu in jar; Armory v2 posted; Operator kit = starting gear. Open: pose/weapon pairing per MCC idle pose (moved to Review Armory previews), rank locks (he chose all unlocked). Memory: zomboid-character-screens.md, zomboid-main-menu.md. |
| FPS | EN-MC + ZC | review ready (paused) | Step 1 in a jar; server/hosting choice (options A-D) waiting for Josiah: https://claude.ai/artifact/QfY9K7qNZfvN8WtR1Aj3k8. Memory: zomboid-server-capacity.md, zomboid-performance-tech.md, zomboid-profiler.md. |
| ELEVATOR/ESCALATORS | ZC | blocked | Lift window ignores Josiah's clicks (hover works). Needs his game log via PC access to fix, then merge + jar. County world is frozen, so only if he wants it. Memory: zomboid-lifts-tech.md, zomboid-lifts-direction.md. |
| REFERENCES | EN-MC + ZC | review ready | Photo refs only via his PC (pc_refs.py over Remote Control). Last: 26 loot photos kept. Rule after switch: zomboid-refs-endless-night-switch.md. |
| Bring Cortana into the game | UE (Cortana R&D) / EN-MC (in-game Cortana code) | review ready | MaskedMimic physics legs: Isaac Sim 5.1 + IsaacLab 2.3.2 in WSL Ubuntu 24.04, EULA accepted 12:11; running NVIDIA's walk test, then her six lines → samples. In-game Cortana (B toggle, Heart voice via sherpa-onnx Kokoro, doors, holotable, Warthog call) is in the EN jar. Memory: endless-night-cortana-*.md, zomboid-cortana-hologram.md. |
| Move Endless Night to Unreal 5 | UE | review ready | Phase 0: UE 5.8 installed, 32 GB RAM, C: ~26 GB free; measuring C: use for a cleanup list (nothing deleted until he picks); trial = m20 Tip of the Spear import. Doc https://claude.ai/artifact/SbZhp8CNK595V3RbLXFZL1. Memory: endless-night-unreal-move-scoping.md. |

## Finished threads (decisions live in memory)

| Thread | Goes to | State / last outcome |
|---|---|---|
| UI | EN-MC | done. CONTINUE = last world; NEW SOLDIER = fresh world + Spartan selector; SAVED GAMES row (0.5.2+06ba2a1) opens the world list; click fix; F1 = old title screen. MCC main menu + customisation built. |
| Bring Covenant enemies into Endless Night | EN-MC | done. Grunts, Jackals, Skirmishers, all Elites, Hunters (no Brutes/Drones). Infected: `/en infected 3`, `/en infected clear`; 45 health, no shields, light gravity, Evade dives (traits from Halopedia, the Reach game-type file was unreadable); infected kill → player rises as one. Pack in ENDLESS NIGHT npc folder (3 parts). |
| Commands list and world settings | EN-MC + ZC | done. EN list: library/endless-night/commands/COMMANDS.md and EN-COMMAND-TREE.md (/en weather, config, cinematic, perf, flicker, profile, horde, infected + /enhud). Removed from EN by Josiah: zombie lore, companions, survival, aircraft, digging, building, cars settings; /localgun. RULE: every new command goes to the commands list with a brief description. |
| Build bases with Reach Forge | EN-MC | done. Forge grid/armour stand/trophy wall approved; Monitor mode; stocked racks; Cortana holotable + coloured hologram pad (gunmetal ring, cyan strips, blue emitter); Josiah 2026-11-10 09:04 "thats good for now. you can merge everything". |
| VEHICLES | EN-MC (Warthog/Covenant) + ZC (cars) | done. R34 and Defender pack extras HELD (Josiah picked "Hold it for the purge rework"). Warthog and Covenant drop ships go to EN. |
| Move Endless Night to its own repo | EN-MC | done. Repo published 2026-11-09 09:28; PC switched to config\endlessnight (old config\zomboidcraft left as fallback; offer to clear it). |
| BLENDER | ZC | done. Teaching thread (Rebecca/Mannequin); mannequin files moved to additions\mannequin (no OneDrive). |
| Build Zomboid zombie behaviour | EN-MC + ZC | done. All 50 Zomboid zombie sandbox settings etc. (zomboid-zombie-behaviour-plan.md). |
| AIRCRAFT | ZC | resolved. Not in EN ("no jets"). |
| AUDIO | ZC (+ EN Reach audio rule) | done. Command net, hunt board, strike calls and Overwatch recordings DROPPED by Josiah 2026-11-09 07:55 ("reach audio will be used"); code kept unmerged on its branch. |
| WEATHER/ATMOSPHERE | EN-MC + ZC | done. zomboid-weather-direction.md, photon settings. |
| LOOT | ZC | done; world loot paused under the purge (no world loot in EN); 30 Tarkov guns in loot on the county side. |
| Decide world systems after Tarkov switch | EN-MC | done; superseded by purge direction (zomboid-purge-direction.md, https://claude.ai/artifact/92iDNQQ3vpJUMVT5gpcU8z). |
| Bring Tarkov gear and clothing into game | EN-MC | done; Tarkov grid inventory dropped (purge); full Tarkov gear list (heads, boss gear, masks) waiting for character customisation. |
| NPC | ZC | done; dark elf companion to be removed; no companions in EN. |
| Add Zomboid survivor bodies to the game | EN-MC + ZC | done (zomboid-survivor-*.md). |
| Add Zomboid zombie bodies to the game | EN-MC + ZC | resolved (zomboid-pz-zombies-*.md). |
| Review Halo and Tarkov HUD previews | EN-MC | resolved (zomboid-endless-night-hud.md, zomboid-reach-hud-pull.md). |
| WEAPONS NEW | ZC (Brita) | done; Brita reload work on hold, all Brita guns superseded by Tarkov (2026-11-06 10:51). |
| Build Zomboid survival systems | ZC | done (zomboid-survival-*.md). Not in EN. |
| ENTITY | ZC | resolved (Harvestman etc.). Not in EN. |
| RAIL SYSTEM | ZC | resolved (zomboid-rail-*.md, trains). Not in EN. |
| Make a demo trailer | ZC | resolved (zomboid-trailer-cinematic.md). `/en cinematic` survives in EN. |
| Make wearable clothing and gear | ZC | resolved (zomboid-wearable-clothing.md, zomboid-clothing-*.md). |
| Check Ponytail plugin for token savings | both | resolved (tooling question). |
| Make friend's mod install list | ZC (+EN) | resolved. Friend page https://claude.ai/artifact/6qyBdcVCBhnd79rbM4Qw5E; joiners need Fabric API + playtest jar + Simple Voice Chat + Voice Bridge for e4mc. |
| Fit out every building floor | ZC | resolved (zomboid-floor-fitout.md). |
| Review Project Zomboid systems | ZC | resolved (zomboid-pz-review.md). |
| NETHER | ZC | resolved (zomboid-nether-origin-idea.md). |
| Download city materials for ZomboidCraft | ZC | resolved (zomboid-texture-library.md). |
| Gather 3D props for ZomboidCraft | ZC | resolved (zomboid-asset-intake.md). |
| Fix far flyby crash | ZC | resolved. |
| WEAPONS | ZC | resolved/failed early thread; replaced by later weapons threads. |
| PLAYERS | ZC | resolved. |
| Fix playtest game crash | ZC | resolved. |
| Build dark rainy medieval Minecraft mod | ZC (history) | resolved; "Medieval Gloom" pack PAUSED (minecraft-gloomy-pack.md). |
| Write handover packs for new projects | — | this pack. |

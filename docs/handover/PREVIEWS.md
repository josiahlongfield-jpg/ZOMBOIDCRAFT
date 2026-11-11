# Every preview and plan page from the old project (185 links)

Each link is a private claude.ai page Josiah can open. For each one: its title (where known), the notes that
mention it, and the words around it that say what it was for and whether he approved it. Pages listed first are
the ones that matter most to ZomboidCraft; the rest are kept so nothing is lost.

"Status words near it" are approval/pending words found next to the link. When in doubt, the memory note's text rules.

## The key pages for this project

The county plan map, sound pickers, posing tool, Loop Bench and every landmark preview are listed below with the
memory lines that describe them. Main ones: county master-plan map (memory zomboid-county-plan.md), arms posing tool
(Ur4ZP18eXgMUDALKpCEhyZ, zomboid-arms-posing-tool.md), Loop Bench sound editor (zomboid-loop-bench.md), sound picker
rounds (zomboid-sound-history*.md), friend install page (6qyBdcVCBhnd79rbM4Qw5E), buying list (FCSWHNvVcKoM4WvCe7ivk9),
Purge direction (92iDNQQ3vpJUMVT5gpcU8z, which moved Halo/Tarkov work to Endless Night).

## ZomboidCraft

### [(title not listed: see context)](https://claude.ai/artifact/12Gt4fAHLMcTj92hNqrWy6)
- `memory/zomboid-bought-assets-plan.md`: Shopping list doc (Claude Doc): https://claude.ai/artifact/12Gt4fAHLMcTj92hNqrWy6 ("ZomboidCraft asset shopping list"). Priorities: 1 nature (Kentucky broadleaf trees with dead/fallen variants first), 2 vehicles (80s-90s US), 3 props by place, 4 building details (buildings stay procedural blocks). Optional: characters, weapons, CC0 sound.

### [(title not listed: see context)](https://claude.ai/artifact/17enPyCCXUFEF3wkr2nuyT)
- Status words near it: **paused, picked**
- `memory/zomboid-sound-history-2.md`: - Throwables picker sent 2026-10-27 23:25: https://claude.ai/artifact/17enPyCCXUFEF3wkr2nuyT "Dead Air Throwables" (450 recordings, scratch thrpick/; picks in db collection picks, roles throw.* and world.explosion(.far)). Build script still to write (build_throw_sounds.py) once he picks.
- `memory/zomboid-sound-history-2.md`: - 2026-10-28 01:18 AUDIO PAUSED again (Josiah: all but LANDSCAPE/REFERENCES/PLAYERS). Branch clean, all merged (playtest 58eb2283). Open: pin_pull + ear_ring picks on https://claude.ai/artifact/17enPyCCXUFEF3wkr2nuyT; guns-wave1 not yet in playtest (sounds.json conflict rule told to WEAPONS/UI); item.* sounds for WORLD LOOT roster when it's picked; older items in zomboid-world-events.md.

### [(title not listed: see context)](https://claude.ai/artifact/1Fk4rA7vA9cTnrtWVSptPB)
- `memory/zomboid-sound-history-2.md`: - 2026-10-30 wave-two gun shots picker: https://claude.ai/artifact/1Fk4rA7vA9cTnrtWVSptPB (scratch thrtools/gun3page.py, groups gun3_*, Sonniss sel_gun3.json). Events from WEAPONS NEW (branch claude/project-thread-5nbhdi): heavy_sniper/battle_rifle/suppressed_rifle/machine_pistol/pocket_pistol/grenade_launcher shot(+distant/suppressed). Precision rifle KEEPS its current sounds (Josiah 23:42).

### [(title not listed: see context)](https://claude.ai/artifact/1Hk8jvVcqbsKs4GTSZsbkk)
- `memory/zomboid-characters-direction.md`: **23:11 Josiah:** wants the EXACT mockup-1 'player' model (body A geometry: tapered chest/waist, pelvis, deltoid caps, separate thigh/shin/foot, upper/fore/hand) and its exact walk joint movement, with NO neck (head on body), no face blocks, no thumbs, plain basic skin. Said 'preview first, don't build yet'. Preview: https://claude.ai/artifact/1Hk8jvVcqbsKs4GTSZsbkk (source scratchpad player/make.py from bodies/index.html). Building it in-game means new geometry that the current skins don't map onto 1:1; raise that when he says go.

### [(title not listed: see context)](https://claude.ai/artifact/1WHo4XpTMTs4Z5t5pWLyAs)
- Status words near it: **awaiting**
- `memory/zomboid-brita-weapons.md`: 03:50 REVIEW PAGE https://claude.ai/artifact/1WHo4XpTMTs4Z5t5pWLyAs (14 guns, attachments picker, stats, sounds), awaiting his verdicts. Tools in /mnt/project-files/zomboid-mod/brita/tools/. MOUNT MATHS (verified visually): PZ reads .x meshes with z negated (flip z + swap triangle winding), attachment matrix = T(offset)*R(rotate XYZ deg), part = gunAtt * inverse(partAtt); after the flip long guns are +z up, pistols (Colt1911, SW_M500) need 180 about the muzzle axis. Arsenal sights float on low guns: seat them down onto the gun top. Stats: anchor class x (gun/ref Zomboid gun) ratio, spread ratio sqrt clamped 0.6-1.8, headshot one-kill floor. Open calls for him: DShK placed vs carried, RPG bla ...

### [(title not listed: see context)](https://claude.ai/artifact/24Wcae3iy7hC3DSSiZQfpP)
- `memory/zomboid-spider7-model.md`: Preview: https://claude.ai/artifact/24Wcae3iy7hC3DSSiZQfpP (three.js 0.160 from jsdelivr).

### [(title not listed: see context)](https://claude.ai/artifact/2SqD1eBN9Fhdy5XE5S9zM9)
- `memory/zomboid-sound-history.md`: - R5 UI: https://claude.ai/artifact/2SqD1eBN9Fhdy5XE5S9zM9. Physical foley, no bleeps. ui.tab = light switch, ui.wear = buckle (fanny pack). Josiah supplies ui.drop and ui.skill.

### [(title not listed: see context)](https://claude.ai/artifact/2Zsu1YGwXQV4SRUiuATiCy)
- `memory/zomboid-hollis-tech.md`: - PREVIEWS RULE (applies to every build) (Josiah 2026-10-30 18:48): send him previews of each build. The thread's attachment limit is full, so they go on the gallery artifact https://claude.ai/artifact/2Zsu1YGwXQV4SRUiuATiCy (source: scratchpad gallery/index.html + img/*.jpg; add shots to SHOTS and republish).

### [(title not listed: see context)](https://claude.ai/artifact/31ibYXHDmAv1VbZgwGMTdm)
- Status words near it: **ok**
- `memory/zomboid-bullet-impacts.md`: Picker (2026-10-27 04:40): https://claude.ai/artifact/31ibYXHDmAv1VbZgwGMTdm 'Dead Air Bullet Strikes', 70 recordings; db picks roles = ground/concrete/brick/wood/metal/car/glass/soft/ricochet + notes with hit times. Builder scratch imptools/imppage.py. fetch_freesound.py now allows CC-BY for imp_* groups (ATTRIBUTION_OK) - credit those in CREDITS.md. Concrete/car candidates thin.

### [(title not listed: see context)](https://claude.ai/artifact/37TYpSoi4BxcjaNM9E1ugj)
- Status words near it: **approved**
- `memory/zomboid-nether-origin-idea.md`: PLAN 2026-10-29 23:45 (thread "Build the Nether origin and ground zero"): doc https://claude.ai/artifact/37TYpSoi4BxcjaNM9E1ugj. Breach under Ridgeview Labs (NW, unbuilt) with B1-B4 levels, B4 = sealed ring under a cracked concrete plug; ~30 files in 4 sets; ground zero = 3 rings (cordon 120-150, burn 50-120, core 0-50) with a red-growth block kit and spore haze; new Nether = 5 regions (Ash Wastes, Spore Forest, Molten Rift, Bone Cathedrals, Brood Hollows) via worldgen data overrides, vanilla portals off. ALL SIX RECOMMENDATIONS APPROVED 23:37: breach at Ridgeview Labs; Nether = Eternal gothic mega-fortresses + 2016 grimy rocky hell; haze infects slowly near the core only, gas mask/respirato ...

### [(title not listed: see context)](https://claude.ai/artifact/3QvbfWqfsgb4mJh2LKWkeX)
- Status words near it: **approved**
- `memory/zomboid-characters-direction.md`: **2026-10-25 22:40 (thread "Redesign zombie and player bodies"):** Josiah approved building this plan (via coordinator, 22:26). Mockup 1: https://claude.ai/artifact/3QvbfWqfsgb4mJh2LKWkeX (Today vs A "Zomboid" proportions vs B "Pronounced" big head/brow/nose/mitts vs player; elbows+knees, thumb that closes, torn clothing shell, first-person arms with crowbar). Asked him: A, B or A body + B face (my pick); movement feel; whether player arms replace the gun first-person arms. Building on A body + B face meanwhile. Source: scratchpad only (bodies/index.html), cdnjs is blocked for local screenshots here.

### [(title not listed: see context)](https://claude.ai/artifact/3SyLpN6jAMNdmofNvTbPzf)
- `memory/zomboid-npc-voice-tech.md`: Built 2026-10-31 in the Blender tutorial thread (branch claude/project-thread-488ycs). Josiah wants to do it all himself: run sheet Claude Doc "Voice run sheet" https://claude.ai/artifact/3SyLpN6jAMNdmofNvTbPzf (example character `nova`).

### [(title not listed: see context)](https://claude.ai/artifact/3pGto8WrcuT1cUhKqaxMLZ)
- `memory/zomboid-brita-weapons.md`: 03:58 SIGHT PICTURES page https://claude.ai/artifact/3pGto8WrcuT1cUhKqaxMLZ (49 sights, to-scale MOA canvas drawings, close-up inset, lit toggle, verdict + Copy review; localStorage brita-sights-review). Source: brita/tools/sights/sights.js (spec DSL) + template.html. 9 marked "Please check" (no spec found): Fiber shotgun size, 1P87, 1P63, OKP-7, Phantom XL, Raven, C79, Hensoldt ZF 6x42, Armasight (real one has no reticle). Notes: PK-AS real is 1x (Arsenal says 2x); Nikon M5 drawn as MK1-MOA; G28 PM II = P4FL-based. REFERENCES is sourcing free-licence photos (manufacturer diagrams excluded) into refs/items/weapons-attachments/sights/<id>/.
- `library/zomboid-mod/brita/sights-review-approved.txt`: 2026-11-01 04:02 Josiah: all 49 sight pictures marked "right" on https://claude.ai/artifact/3pGto8WrcuT1cUhKqaxMLZ

### [(title not listed: see context)](https://claude.ai/artifact/4SCtVfYqnbqapofLUaCrp2)
- Status words near it: **chose**
- `memory/zomboid-living-companion.md`: 2026-10-29 23:55. Josiah asked how smart and "living" the Woman companion ([[zomboid-woman-model]]) can be, and whether she can control her body, including moves nobody animated. He chose "discuss first". The plan doc is https://claude.ai/artifact/4SCtVfYqnbqapofLUaCrp2 (Claude Doc "A living companion: plan").

### [(title not listed: see context)](https://claude.ai/artifact/4dSFQRP3JCkfLJz2ajhpsr)
- `memory/zomboid-harvestman-web-room.md`: 2026-10-30 01:45. Josiah asked for "a webpage preview that does the same" as `/zc harvestman room` ([[zomboid-harvestman-goals-tech]]). It's published at https://claude.ai/artifact/4dSFQRP3JCkfLJz2ajhpsr.

### [(title not listed: see context)](https://claude.ai/artifact/4eatHuijbbWZZG68mZaMi8)
- `memory/zomboid-loot-rarity-direction.md`: Design doc: https://claude.ai/artifact/4eatHuijbbWZZG68mZaMi8 (Claude Doc "ZomboidCraft loot and weapon rolls"), revised through 23:05 (rev 14, no open calls). Step 2 build: [[zomboid-rolls-tech]].

### [(title not listed: see context)](https://claude.ai/artifact/52Vjvf7oicaj2q9KwD5JBd)
- Status words near it: **pending**
- `memory/zomboid-brita-weapons.md`: 04:49 DIRECTION CHANGE (Josiah): drop headshot-only kills. Headshot kill speed by CALIBRE (head fastest, one-tap depending on calibre), body kills return, shotguns one-shot head/body depending on distance + hip/aim. Plan doc https://claude.ai/artifact/52Vjvf7oicaj2q9KwD5JBd (calibre head multipliers .22 2.2, .380 2.5, .38 3.2, 9mm/5.7/.45 3, 5.56 2.6, .44/.45LC/.500/.30-30/.308/bolt 2.5, .50 2.0; body 1.0 (was zombieBodyShots 0.33), limbs 0.7; onlyHeadshotsKill default OFF, sandbox option kept; zombie health 20 x toughness 0.6/1/1.5). Shotgun sim: one shell kills normal at <=5 blocks any aim; aimed body 97% at 8. Open calls: .22 two headshots on normal, shotgun aimed pattern tighten?, limbs  ...

### [Loadout Command Mock-up](https://claude.ai/artifact/5Av3EuyWrBFMGScQ3T18N1)
- Last updated: 2026-11-09

### [(title not listed: see context)](https://claude.ai/artifact/5QwwMvHvoMPKSSn7syQLCf)
- `memory/zomboid-gun-art-style.md`: **RULE (Josiah 2026-10-26 00:40):** "I want all guns and attachments made with this same design aesthetic (even wood grain or differing coloured ones). I prefer the black mag." **01:56 he reversed wear-by-condition: "I'd actually prefer all the weapons in their like new form at all times, inclusive of their attachments."** Approved concept: https://claude.ai/artifact/5QwwMvHvoMPKSSn7syQLCf. Every new gun, attachment, finish and camo must go through the same painter. Follows [[zomboid-clothing-style]]: no lines where a part's blocks join, thin outline only round each part's outside and each attachment.
- `memory/zomboid-weapons-direction.md`: - Art style (Josiah 2026-10-25 23:45): the rifle remade in the characters' Borderlands-like block style. Preview https://claude.ai/artifact/5QwwMvHvoMPKSSn7syQLCf. Follows [[zomboid-clothing-style]] incl. the 00:02 outline rule: thin outline only round each part/attachment, no seams inside a part, edge wear as chips not lines. 00:40 he loved it: ALL guns and attachments rebuilt in game in this style (black mag), shipped 01:47. 01:56: every gun and attachment LIKE NEW always (no wear by condition); revolver reshaped from the real Model 10. Tech and rule: [[zomboid-gun-art-style]]. Step 3 (perk columns) still on hold.

### [(title not listed: see context)](https://claude.ai/artifact/5gbKWAMkyYf4FDDtWbihuv)
- `memory/zomboid-hud-direction.md`: Mockup v1 (2026-10-25): https://claude.ai/artifact/5gbKWAMkyYf4FDDtWbihuv. Source is in project files zomboid-mod/hud-ui/v1/ (template.html, assets.py). It uses the real game sprites and 12px pixel moodle icons. Feedback on v1 (10:36): he likes the belt and other pieces vanishing until used. Belt and holster squares should have NO backdrop strap, just the squares. He wants a high-quality v2 "on par with our level of realism and moody design", and it doesn't have to match the inventory UI. Mockup 2 is published at the same link (source in zomboid-mod/hud-ui/v2/). It has bare belt squares with the real 3D item models, enamel moodle plates with sprayed stencil icons, a 7-segment wristwatch and  ...

### [(title not listed: see context)](https://claude.ai/artifact/5i9B3e3EfAviTfeDbjEAXP)
- `memory/minecraft-gloomy-pack.md`: Docs: research https://claude.ai/artifact/EKnQx6hNPK6RRqBQTf7gx3 ; design ideas https://claude.ai/artifact/5i9B3e3EfAviTfeDbjEAXP (decisions unanswered).

### [(title not listed: see context)](https://claude.ai/artifact/6PuJBUc9D8v8qWt3etpeoF)
- `memory/zomboid-command-net-tech.md`: COMMAND NET + HUNT BOARD (2026-11-06, coordinator relay of Josiah's world-systems brief UxHyNZNfCqGbJFzxXd67VJ, "Tarkov switch": marines purge the world). W20 yes: military channel -> command net, evac points -> extraction points. W21 grand hunt: hunt board (district clears, HVTs, objectives, escorts), sandbox never ends. W22 purge map NO. W17 air strikes via radio operator, needs accuracy system. W19 day 0 = mix (sent in deliberately, chopper crashed). Plan doc https://claude.ai/artifact/6PuJBUc9D8v8qWt3etpeoF (calls C1-C7: callsigns Overwatch/Reaper, headsets hear net, entries fed in, marking grid/smoke/designator, civilian hint lines, district cleared at 10%, resupply crates). BUILD NOTHI ...

### [(title not listed: see context)](https://claude.ai/artifact/6WuwTNR9LnhUTxME3XKa5a)
- Status words near it: **waiting**
- `memory/zomboid-penthouse-monument.md`: - 01:45 Josiah: "incredible big millionaire penthouses ... each hotel should have a differently laid out one". Plans page with 7 layouts (Sky Mansion, Gallery Loft, Entertainer, Winter Garden, Collector, Spa Retreat, Velvet Room = strip-club themed per Josiah 01:50, non-explicit decor; each 3 levels, top levels step in for terraces): https://claude.ai/artifact/6WuwTNR9LnhUTxME3XKa5a (source scratchpad penthouse-plans.html). Awaiting his picks + crown style + refs.

### [(title not listed: see context)](https://claude.ai/artifact/6iFtLo6sRF12Zn7jAcQ5dW)
- `memory/zomboid-woman-model.md`: Preview: https://claude.ai/artifact/6iFtLo6sRF12Zn7jAcQ5dW (5 outfits, piece toggles, hair, eyes, 6 hand-set poses). Built in headless Chromium: FBXLoader + mergeVertices into a custom quantised binary (bones deduped by name, root.matrixWorld kept or she lies flat), shipped as base64 model.txt.

### [(title not listed: see context)](https://claude.ai/artifact/6qyBdcVCBhnd79rbM4Qw5E)
- `memory/zomboid-playtest-build.md`: - Friend install page (2026-10-26): https://claude.ai/artifact/6qyBdcVCBhnd79rbM4Qw5E, txt copy at project files zomboid-mod/friend-install/zomboidcraft-friend-install.txt. Joiners need Fabric API + playtest jar + Simple Voice Chat + Voice Bridge for e4mc (not e4mc itself). Photonics had no 26.3 build. Update both if the required mod list changes.

### [(title not listed: see context)](https://claude.ai/artifact/6zFda6ycGpfmpPiJGmcG2M)
- `memory/zomboid-sandbox-settings.md`: Split agreed with the UI thread: COMMANDS owns the backbone (option list taken from B42's own sandbox definitions, per-world store, how each applies, /zc sandbox, client-readable registry, host-only C2S set payload, S2C sync). UI draws the page (mock: Sandbox tab of https://claude.ai/artifact/6zFda6ycGpfmpPiJGmcG2M), host can reopen it from the pause menu. Owning threads wire each setting.
- `memory/zomboid-ui-pz-pieces.md`: Mock-ups sent 2026-10-31: https://claude.ai/artifact/6zFda6ycGpfmpPiJGmcG2M (source in the UI thread scratchpad zui-mock/). GO from Josiah 2026-10-31 05:02 for everything except the moodle restyle (moodles stay). Build order: car figure (efb79586a, in VEHICLES' MechanicsScreen), Sandbox page, death card, level-up card, G menu + queue. NO PRESETS (Josiah 05:06; COMMANDS removed them). COMMANDS: all 269 settings in data/zomboidcraft/sandbox/zomboid.json, TEXT kind, coming CHOICE settings greyed, Map.* settings owned by UI; table /mnt/project-files/zomboid-mod/commands/world-settings.md.

### [(title not listed: see context)](https://claude.ai/artifact/71NefEydsnsbp261u7Wq4A)
- Status words near it: **approved**
- `memory/zomboid-companion-trades-plan.md`: OCCUPATION PICKER 2026-10-31 21:45: built on my branch (77ef48ce0 + b55a560f9) with SURVIVAL's wave 3 branch onqb2b merged in; NpcSkills.occupy/start (occupation + Zomboid hobby traits off-trade, 2-3 levels, passives 5, startBoost x1.75/2/2.25), page: click her past -> occupation list, 12 best skills; /zc npc occupation <id>. Preview https://claude.ai/artifact/71NefEydsnsbp261u7Wq4A sent, APPROVED by Josiah 21:52; merged to playtest 6c5b7493e after SURVIVAL wave 3 (b52a483) landed; CI green, jar sent 22:40.

### [(title not listed: see context)](https://claude.ai/artifact/77fKx8fvZcFXSkAKUHqPAy)
- `memory/zomboid-nightvision-tech.md`: Night vision (weapons thread, branch nightvision, 2026-10-26). Josiah loved the preview (https://claude.ai/artifact/77fKx8fvZcFXSkAKUHqPAy). See [[zomboid-guns-tech]], [[zomboid-playtest-build]].

### [(title not listed: see context)](https://claude.ai/artifact/7CzuWTzRBUNiGB6t6FsmQP)
- Status words near it: **awaiting**
- `memory/zomboid-companion-trades-plan.md`: NPC thread owns PZ review section 18 (companion learns trades from the new systems), handed out 2026-10-31 03:48. Plan doc https://claude.ai/artifact/7CzuWTzRBUNiGB6t6FsmQP (Claude Docs id 3243b2e7-8bae-40fe-9573-cef39c6a058c) sent ~04:00, NOT built yet, awaiting Josiah's notes.

### [(title not listed: see context)](https://claude.ai/artifact/7Fx7rYyRohb7bb2nQLf1JT)
- `memory/zomboid-loop-bench.md`: - 2026-10-28 LOOP BENCH (Josiah edits any mod sound on his phone): https://claude.ai/artifact/7Fx7rYyRohb7bb2nQLf1JT. db collection "edits", doc id = sound path with "/" -> "~". Apply: save the docs to tools/audio/bench_edits/<date>.json, run tools/audio/apply_bench_edits.py on it ONCE, then rebuild the page audio (scratch bench_build.py) and clear the db, since edits are relative to the files the page shows. RULE (Josiah 23:16): a sound set to -18 dB means remove it entirely (the script does it). Round 1 applied 6886622b (playtest c6b3ec01); page refreshed, db cleared.

### [(title not listed: see context)](https://claude.ai/artifact/7rkGg3jdUDUZuHf1QeEvVE)
- Status words near it: **approved**
- `memory/zomboid-base-customisation-plan.md`: From the review thread (Josiah approved 2026-10-31 03:46): YES to the beam rule (floors sag, then fall without support). Note on section 11: Zomboid is the inspiration, but in Minecraft we can make incredible structures, as long as they cost time spent learning skills. This plan also owns B42 crafting stations (review s10), farming beds and grow lights (s9), and Base Claim, safehouses and factions as settings (s17). Standing rule: Zomboid's version of every feature unless he confirmed a change. Review doc https://claude.ai/artifact/7rkGg3jdUDUZuHf1QeEvVE
- `memory/zomboid-pz-review.md`: Doc: https://claude.ai/artifact/7rkGg3jdUDUZuHf1QeEvVE ("Project Zomboid, front to back"), thread "Review Project Zomboid systems", sent 2026-10-31.
- `memory/zomboid-sandbox-settings.md`: 2026-10-31: the COMMANDS thread ("Commands list and world settings") owns review sections 17 and 20 of https://claude.ai/artifact/7rkGg3jdUDUZuHf1QeEvVE: all 269 B42 sandbox settings in 12 groups plus a 13th "ZomboidCraft only" group. Josiah's rule: Zomboid's version unless he has already confirmed a change; confirmed rules show as Fixed.
- `memory/zomboid-world-loot-direction.md`: - ZOMBOID REVIEW HANDOUT (Josiah approved 2026-10-31 03:46; doc https://claude.ai/artifact/7rkGg3jdUDUZuHf1QeEvVE). LOOT owns: sec 8 world stories with payoffs, stash maps, safes; sec 13 power+water (district-by-district failure, delay adjustable in settings; generators; fridges need power); skill magazines, VHS + TV schedule (sec 2); car parts as items (sec 12); Loot group of sec 20 (47 Zomboid settings). RULE: Zomboid's version of every feature unless Josiah confirmed a change.

### [(title not listed: see context)](https://claude.ai/artifact/8TBkissdo4THMexsw8QYxy)
- `memory/zomboid-throwables-direction.md`: - Plan doc (Claude Doc): https://claude.ai/artifact/8TBkissdo4THMexsw8QYxy "ZomboidCraft throwables plan". Roster of 10 (pipe bomb OUT FOR GOOD 2026-10-29, never model/name it): glass bottle, tin can, brick, firecracker string, road flare, wind-up alarm clock (Common/Scarce lures), molotov, smoke grenade, flashbang, frag grenade (Prized, military only). Hold right-click to wind up with arc preview; fuse starts at wind-up; quick-use wheel throws from belt; noise via zombie/Noise.emit.

### [(title not listed: see context)](https://claude.ai/artifact/8YVbj4akbaQCSq8KBa1sm8)
- `memory/zomboid-hud-direction.md`: Update: drops made bigger/brighter after the browser preview (https://claude.ai/artifact/8YVbj4akbaQCSq8KBa1sm8, source in thread scratchpad rain/), in playtest 9b2c7e7.

### [(title not listed: see context)](https://claude.ai/artifact/8d3jqHFdbRyWKHr2zjvo6h)
- `memory/zomboid-hud-direction.md`: Rundown doc: https://claude.ai/artifact/8d3jqHFdbRyWKHr2zjvo6h

### [(title not listed: see context)](https://claude.ai/artifact/8zHx2mMR516xaBf9e7fAoL)
- `memory/zomboid-characters-direction.md`: **2026-10-26 01:25:** Josiah asked for the most detailed painted body skin (not 3D add-ons), moody, free to drop the current face design. Built a procedural painter (scratchpad player/paintskin.js, 1024x1024 standard 64 layout): comic-inked, mottled, cel-shaded, zombie (milky eyes, dark eye mask, torn cheek/teeth, veins, lividity, bites, bone at higher decay) and survivor variants, 3 tones, 3 decay levels. Preview: https://claude.ai/artifact/8zHx2mMR516xaBf9e7fAoL. Local check trick: Chromium + playwright can render the 2D skin canvas (sheet_test.html, shot.js) even though three.js can't load offline.

### [(title not listed: see context)](https://claude.ai/artifact/98TrFVLzMteUKAXQKM6v9Z)
- `memory/zomboid-broadcasts-plan.md`: Plan doc: https://claude.ai/artifact/98TrFVLzMteUKAXQKM6v9Z (posted 2026-10-31 ~04:00).

### [(title not listed: see context)](https://claude.ai/artifact/9BSczHZAnNq7iyXbUYq5zS)
- `memory/zomboid-npc-models.md`: - **Dark elf** (ELF.zip): One glb (THREE.GLTFExporter, 20k tris, one 2k-ish baked colour texture, looks AI-generated, no signs of a game rip). NOT rigged and stands in an A-pose, so walking needs a rig (our ZBody bones or a skinned rig). Preview artifact: https://claude.ai/artifact/9BSczHZAnNq7iyXbUYq5zS. Files: zomboid-mod/assets/dark-elf/ELF.zip, previews/npc-dark-elf-raw-vs-toon.png, characters/npc/render_raw_vs_toon.py (numpy software raster: python3 -I <glb> <out> <title>).

### [(title not listed: see context)](https://claude.ai/artifact/9DYoHXjFGEPZtwW4Tg4sef)
- `memory/zomboid-building-detail-direction.md`: (2026-10-26 11:27, landscape thread) "once youve finished the aircraft carrier id like for you to focus on all the buildings of all types including city buildings, exterior and interior, cover everything". Plan doc: https://claude.ai/artifact/9DYoHXjFGEPZtwW4Tg4sef (about 60 types, state today, order). Proposed order: room planner + main street shops, town civic, downtown, roadside, suburbs (mansions, trailers), farm, military, port + cargo ship. Order card sent 11:50 (main street recommended). Building photos requested from the photo thread in that order (refs/town|city|roadside|houses|farm|military|port/<topic>/). Audit: shops/civic/offices/walk-ups sparse inside; Farmstead and Camp are th ...
- `memory/zomboid-overnight-mandate.md`: - Queue as of 14:35: carrier officer spaces, roads between towns, trees and landscape, town civic, downtown, roadside, suburbs, farm, military, port + cargo ship. Building batch plan: https://claude.ai/artifact/9DYoHXjFGEPZtwW4Tg4sef

### [(title not listed: see context)](https://claude.ai/artifact/9cnuMAwguk7eCVmwhdBv3J)
- `memory/zomboid-county-plan.md`: Map page: https://claude.ai/artifact/9cnuMAwguk7eCVmwhdBv3J (db capability). Josiah drags places and leaves notes there. Read them with ArtifactData `list` on collection `edits` (doc id = place id, {x,z,note,at}) and `get` on `general/notes` {text}. Rules come from [[zomboid-landscape-direction]].

### [(title not listed: see context)](https://claude.ai/artifact/9ucWvBkjA5g5cmnckvd2kU)
- `memory/zomboid-sound-history.md`: - R11 built daaa319 (build_hunting_rifle_sounds.py; shot +4 dB limiter; bolt trimmed to 0.40-1.25 s, the weapons thread retimed the animation to it). Merged into combat. R12 suppressed hunting rifle: https://claude.ai/artifact/9ucWvBkjA5g5cmnckvd2kU (db pick weapon_hunting_rifle_shot_suppressed). Josiah then said to just reuse the AR suppressor sound: shot_suppressed(+_distant) point at weapon/rifle/shot_suppressed files, att 48 (a5a4c9c). R12 picker is moot.

### [(title not listed: see context)](https://claude.ai/artifact/AEwQwvHABaZw7EvgLs9y6Q)
- `memory/zomboid-weapons-direction.md`: - Previews: Gunsmith Bench (rifle) https://claude.ai/artifact/AEwQwvHABaZw7EvgLs9y6Q; Armoury (melee, shaders on/off) https://claude.ai/artifact/NMxfr8BG9BAruWfKkg4Bj3. Photon needs Materials > Normal/Specular Mapping on to show the maps ([[photon-shader-settings]]).

### [(title not listed: see context)](https://claude.ai/artifact/AxW4KmHnv8RueKjxfV5ZqX)
- `memory/zomboid-sound-history-2.md`: - R16 new guns (wave one, from WEAPONS): https://claude.ai/artifact/AxW4KmHnv8RueKjxfV5ZqX "Dead Air New Guns", scratch gm16.py / zpick16. Events: weapon.rimfire.{shot,shot_distant,shot_suppressed,dry,mag_out,mag_in,cock}, weapon.smg.{same}, weapon.sawn_off.{shot,shot_distant,dry,open,shell_in,close}, weapon.magnum.{shot,shot_distant,dry,cock,open,eject,round_in,close}, weapon.lever_action.{shot,shot_distant,dry,lever,round_in}, weapon.crossbow.{shot,cock,hit}. Distants derived from the kept shot. WEAPONS registers names + stand-ins; tell WEAPONS when real oggs land.

### [(title not listed: see context)](https://claude.ai/artifact/BN2LWT8x6hRxTJRoBvYVKJ)
- Status words near it: **picked**
- `memory/zomboid-sound-history.md`: - R9 result: Josiah picked sx6cc09fea@7 for idle and driving (no car.drive), 168f91d. R10 guns: https://claude.ai/artifact/BN2LWT8x6hRxTJRoBvYVKJ ("Dead Air Pistol & Shotgun Picker", 171 clips; db picks weapon_pistol_shot/_shot_distant/_dry/_mag_out/_mag_in/_slide, weapon_shotgun_shot/_shot_distant/_pump/_shell_in).

### [New Alexandria Tower Floors](https://claude.ai/artifact/BcQdAF3qTaxvTdGnBktfy4)
- Last updated: 2026-11-10

### [(title not listed: see context)](https://claude.ai/artifact/BsXevvmzSThmP1JxtfEUtP)
- Status words near it: **picked**
- `memory/zomboid-wave1-guns.md`: Josiah picked "New ways to fight, mixed rarity" on 2026-10-26 08:34. Branch `guns-wave1` (WEAPONS thread). Preview page sent: https://claude.ai/artifact/BsXevvmzSThmP1JxtfEUtP (Armoury Wave One).

### [(title not listed: see context)](https://claude.ai/artifact/C6taAXAhf6Wv315ebLKA7x)
- Status words near it: **superseded**
- `memory/zomboid-sound-history-2.md`: - Ambient music round 2 (sent 12:50): Drones https://claude.ai/artifact/C6taAXAhf6Wv315ebLKA7x (303 Sonniss), Dark Ambient https://claude.ai/artifact/SXv5jbf9EN4jV6B6ezY8zh (277 free), Freesound https://claude.ai/artifact/T92AmMUsVKtaSFjrG1DzZM (137 CC0). Built by scratch musictools/ambpages.py; picks in db collection picks (roles). Old melodic pages superseded.

### [(title not listed: see context)](https://claude.ai/artifact/CJwiGemVvUK1YWHFfuUfkQ)
- Status words near it: **approved**
- `memory/zomboid-inventory-ui-direction.md`: - Mockup v3 approved ("thats much better"): https://claude.ai/artifact/CJwiGemVvUK1YWHFfuUfkQ. Source is in project files zomboid-mod/inventory-ui/v3/.

### [(title not listed: see context)](https://claude.ai/artifact/CYbY4oXX947eEuxJKbXRJD)
- `memory/zomboid-radios-tech.md`: Radios + comms headset (weapons thread, branch `radios`, 2026-10-26). Preview: https://claude.ai/artifact/CYbY4oXX947eEuxJKbXRJD. See [[zomboid-gear-attachments-direction]], [[zomboid-nightvision-tech]], [[zomboid-playtest-build]].

### [(title not listed: see context)](https://claude.ai/artifact/CZDDdHmbKxTU6o4PVRSeZf)
- `memory/zomboid-kit-screen-destiny.md`: Mockup v1 (Design canvas, 3 interactive boards): https://claude.ai/artifact/CZDDdHmbKxTU6o4PVRSeZf. Sources and the three.js renderer are in project files zomboid-mod/inventory-ui/destiny-v1/. The renderer draws the mod's real Blockbench models to PNG with Playwright; clothing and supplies that aren't items yet are procedural box models.

### [(title not listed: see context)](https://claude.ai/artifact/CuTeHbha6ZRezp7kdsZLTV)
- `memory/zomboid-ridgeview-kit-tech.md`: Built 2026-10-29 in thread "Build the Nether origin and ground zero", branch claude/project-thread-nch2s7 (commit f7655335). Preview page: https://claude.ai/artifact/CuTeHbha6ZRezp7kdsZLTV.

### [(title not listed: see context)](https://claude.ai/artifact/D4e7E1SfHppWv6RfL5ZUBh)
- `memory/zomboid-trailer-cinematic.md`: - Trailer page: https://claude.ai/artifact/D4e7E1SfHppWv6RfL5ZUBh (source /mnt/project-files/zomboid-mod/trailer/trailer-shot-list.html). 75 s, 13 shots, title cards, edit notes. He films with OBS/ShadowPlay + Replay Mod.

### [(title not listed: see context)](https://claude.ai/artifact/DBoctf4XSfrrkVvTwS43Rf)
- Status words near it: **paused**
- `memory/zomboid-sound-history-2.md`: - Audit 2026-10-26 11:40 (Josiah: "make sure all choices are accurate"): all shipped Gamemaster takes == his picks (scratch cmpogg.py decodes git HEAD vs rebuild). Fixes 62975a57: SMG/.22 shot picks are 50-90 ms auto-string slices -> TAILS adds the gun's own tail_only; far-off shots for rifle/pistol/hunting(+suppressed)/revolver/double-barrel now DISTANT from own near pick (shotgun keeps his SPAS-12 pick); revolver.eject = magnum bounces, double_barrel.open = sawn-off open (interim). 80cf924f adds weapon.crossbow.dry/.load/.hit_block (WEAPONS wired them). R17 picker (last 8 stand-ins): https://claude.ai/artifact/DBoctf4XSfrrkVvTwS43Rf (gm17.py, zpick17/).
- `memory/zomboid-sound-history-2.md`: - 2026-10-26 14:30: AUDIO PAUSED by Josiah (all threads but landscape stopped). State: branch claude/project-thread-wh9qba at cabd0f39 is fully merged into playtest (fa203d83) and the jar was sent. Still open: round 17 picks (https://claude.ai/artifact/DBoctf4XSfrrkVvTwS43Rf), hail picks, the spotting screams/ui.drop/ui.skill files he will supply, and HammerItem should use item.hammer.nail.

### [(title not listed: see context)](https://claude.ai/artifact/DcsBb1amge4bMmwf1tp9Pg)
- `memory/zomboid-reloads-tech.md`: - Previews page (he asked 05:25, easier than project files): private artifact https://claude.ai/artifact/DcsBb1amge4bMmwf1tp9Pg, source scratchpad rvids/ (index.html + v/*.mp4); republish it with each new preview round.

### [(title not listed: see context)](https://claude.ai/artifact/DgHbLUEQ1Xz5HgpxqBAS3K)
- `memory/zomboid-trains-direction.md`: **Ideas doc:** https://claude.ai/artifact/DgHbLUEQ1Xz5HgpxqBAS3K (Claude Doc). The section "Your answers" holds his decisions.

### [(title not listed: see context)](https://claude.ai/artifact/Dwuangqe3QfnRXU2uL24sT)
- Status words near it: **awaiting, ok**
- `memory/zomboid-brita-weapons.md`: 2026-11-01 PILOT (14 guns, first of each class): sound picks in /mnt/project-files/zomboid-mod/brita/pilot-sound-picks.txt (board https://claude.ai/artifact/Dwuangqe3QfnRXU2uL24sT). Arsenal shots .45/.308/.45LC/.50/12g/SD5.56 have baked echo slaps that sound like a burst (he noticed); fix = brita/single_shot_fix.py (keep first hit, smooth tail), "(single)" options on board v2, awaiting his OK. Look test https://claude.ai/artifact/Y9b2iMCmp8UvZcXEp2Jwj1 published 03:10, awaiting picks. Orientation: PZ models +Y muzzle, Z up; long guns are modelled upside down (only Colt1911/SW_M500 upright) so flip 180 about the muzzle axis. Tools copied to brita/tools/.

### [(title not listed: see context)](https://claude.ai/artifact/DxX1cErgwr3AvxeE9HLS6k)
- `memory/zomboid-bunker-wave2-tech.md`: LANDSCAPE thread, branch claude/project-thread-d61kbn. Plan doc https://claude.ai/artifact/DxX1cErgwr3AvxeE9HLS6k has each kind's way in + clue. Harbour fort built 2026-11-06 (8c03b3810): see [[zomboid-harbour-fort-tech]]. Wave 2 is complete.
- `memory/zomboid-bunkers-direction.md`: (2026-10-25, thread "Build the ZomboidCraft landscape". Plan doc: https://claude.ai/artifact/DxX1cErgwr3AvxeE9HLS6k)

### [(title not listed: see context)](https://claude.ai/artifact/EGQKPoJbpcbrdWkF2UmC6B)
- Status words near it: **awaiting**
- `memory/zomboid-world-loot-direction.md`: - 2026-10-31 03:01 Josiah: wave 4 ask = no more food; lean on medical, military and base decor (lava lamps, arcades, pool tables, neon...), broad and creative, rarities kept in mind. 03:02: use Zomboid's loot rarity scaling and variety as the BASE but add even more variety and rarity than Zomboid. Proposal doc "World loot wave 4" https://claude.ai/artifact/EGQKPoJbpcbrdWkF2UmC6B (190 items: 46 med, 44 mil, 100 decor; ~830 looks via variants; 7 calls awaiting his picks). Zomboid B42 refs: 6 rarity factors (0.05/0.2/0.6/1/2/3) x 21 categories, ZombiePopLootEffect 0-20, pre-looted 25% by day 90, ~4,500-4,900 items.

### [(title not listed: see context)](https://claude.ai/artifact/EKnQx6hNPK6RRqBQTf7gx3)
- `memory/minecraft-gloomy-pack.md`: Docs: research https://claude.ai/artifact/EKnQx6hNPK6RRqBQTf7gx3 ; design ideas https://claude.ai/artifact/5i9B3e3EfAviTfeDbjEAXP (decisions unanswered).

### [(title not listed: see context)](https://claude.ai/artifact/EMmSzx3biJoiFpkww4nkpz)
- Status words near it: **picked**
- `memory/zomboid-sound-history.md`: - R7 ambience 2: https://claude.ai/artifact/EMmSzx3biJoiFpkww4nkpz (hail2, alarm; unpicked). 2026-10-26 Josiah: NO animal sounds (crow, owl, seagull bed removed, 0280231). Shipped round-6 ambience in playtest request 33d7654.

### [(title not listed: see context)](https://claude.ai/artifact/Eia4cvkuJpMePyvVzFxHxe)
- Status words near it: **picked**
- `memory/zomboid-sound-history-2.md`: - 2026-10-30 Harvestman rage scream picker: https://claude.ai/artifact/Eia4cvkuJpMePyvVzFxHxe (scratch thrtools/ragepage.py, ragedesign.py layered 4.3 s takes). Event creature.harvestman.rage; ENTITY thread (cmsg_01LvQiYGyHLhwyRNcM6wP1DpN6ybf8YV52mkT69iBQ2pHU) switches rage() to it once picked. Wave-two gun shots shipped in playtest 6d16014d (build_gun3_sounds.py); minigun events wired by WEAPONS NEW.

### [(title not listed: see context)](https://claude.ai/artifact/Eww3VbWcmb4wGdoJFs83N8)
- Status words near it: **pending**
- `memory/zomboid-pz-install-extract.md`: 2026-11-01 03:20 Tomb comparison preview: https://claude.ai/artifact/Eww3VbWcmb4wGdoJFs83N8 (vanilla vs Tomb's Player Body 3429790870 mesh with its own skin, Goth 3531765630, Detailed + Evil's Ink on Tomb mesh; 18 Tomb faces as overlays; wireframe toggle). Facts: Tomb mesh 5172/4590 tris vs vanilla 984/916; SAME UV layout as vanilla (Tomb's islands subdivide vanilla's), so all body skins/clothes textures fit both meshes. FBX is right-handed: mirror z (and flip triangle winding) to match .x; then the bone fit is scale 1.000, residual <2cm. Tomb "Textures" and Goth have their own dark underwear covering at least vanilla's area (checked by mask); DOLL/NUDE excluded. Faces: Faces/<Name>/{Male|Fe ...

### [(title not listed: see context)](https://claude.ai/artifact/F291crDurHiKDZpJQaFsTj)
- Status words near it: **rejected**
- `memory/zomboid-sound-history.md`: - R1-3 zombies: https://claude.ai/artifact/F291crDurHiKDZpJQaFsTj. Human sounds (choking, rasps, moans), not growls. All horde recordings rejected, so hordes are MIXED from idle voices (dormant) and chase/alert/aggressive voices (angry), "not too overpowering". KEEP dict in build_zombie_sounds.py. Open: less-human alert screams (Josiah sourcing spotting screams).

### [(title not listed: see context)](https://claude.ai/artifact/F8eWMK5bzijxZNxbf7btLi)
- Status words near it: **rejected, waiting**
- `memory/zomboid-carrier-direction.md`: **Power (decided 2026-10-26 08:54):** ships board with FAILING power: main lights dead or flickering, red night lights and battery battle lanterns lit, sick bay and engine control on an emergency generator. A generator you start yourself was the rejected/later option. Plan doc: https://claude.ai/artifact/F8eWMK5bzijxZNxbf7btLi (order: shared kit + lights, carrier passageways + hangar, mess/berthing, island/gallery/magazines/machinery, carrier exterior, cargo ship).
- `memory/zomboid-ship-fitout-tech.md`: Started 2026-10-26 (landscape thread) for Josiah's "a hell of a lot more detail" ask on the carrier and cargo ship. Plan doc: https://claude.ai/artifact/F8eWMK5bzijxZNxbf7btLi. Steps 1-5 shipped in playtest by 2026-10-26 11:20 (kit, passages, hangar, berthing, mess, galley, sick bay, bridge, Pri-Fly, machinery, engine control room, exterior: island catwalk on level 3, number bulbs, visors, searchlights, hull louvres/side doors, bow number at x0=246). Left: 3b wardroom/cabins and 6 cargo ship (waiting for photos). Photos: /mnt/project-files/zomboid-mod/refs/ships/ (see [[zomboid-photo-reference-rule]]).

### [(title not listed: see context)](https://claude.ai/artifact/FCSWHNvVcKoM4WvCe7ivk9)
- `memory/zomboid-asset-intake.md`: - Buying list for Josiah (Claude Doc): https://claude.ai/artifact/FCSWHNvVcKoM4WvCe7ivk9. It has verdicts on his 7 nature links: buy Ultimate Trees Pack (DEEZL) + one rock pack, skip the stylized pine kit, Big Environment Pack Reforged (6.2 GB fantasy), and grass. The top-five buys are there too.
- `memory/zomboid-bought-assets-plan.md`: **2026-10-26 10:41 asset thread result:** 253 CC0 Poly Haven models (glTF + 1K JPG, 579 MB) in /mnt/project-files/zomboid-mod/assets/polyhaven-props/ with INDEX.md by place (street, homes, office/shop, garage/industry/military/port, nature) and LICENSE.txt. Quaternius/Kenney skipped (cartoon). Buying list with verdicts on his 7 nature links (buy Ultimate Trees Pack + one rock pack, skip the rest): https://claude.ai/artifact/FCSWHNvVcKoM4WvCe7ivk9. Project files 8.3 of 10 GB.

### [(title not listed: see context)](https://claude.ai/artifact/FJes8oxh25XzFrWAapcBda)
- `memory/zomboid-sound-history.md`: - R6 ambience: https://claude.ai/artifact/FJes8oxh25XzFrWAapcBda. Wanted heavier hail, far animals, car/house alarms.

### [(title not listed: see context)](https://claude.ai/artifact/FmonNJKhSCSrcqLMvxt6s3)
- `memory/zomboid-survivor-fp-tech.md`: - Mock-up page (thread at its 24-attachment cap): https://claude.ai/artifact/FmonNJKhSCSrcqLMvxt6s3, source scratchpad mock/survivor-arms.html; republish with each mock-up.

### [(title not listed: see context)](https://claude.ai/artifact/FqHpQgo523mi17HVt3TBYq)
- `memory/zomboid-landscape-review-plan.md`: Doc "Auto shops and the world systems: plan to discuss": https://claude.ai/artifact/FqHpQgo523mi17HVt3TBYq (sent 2026-10-31). Nothing gets built until Josiah answers. Follows [[zomboid-match-zomboid-rule]]; part of [[zomboid-pz-review]].

### [(title not listed: see context)](https://claude.ai/artifact/G5dddELPz2kzEqYt4DGZHF)
- `memory/zomboid-brita-wave2.md`: - Review page v1 https://claude.ai/artifact/G5dddELPz2kzEqYt4DGZHF (localStorage brita-wave2-review): look, Arsenal attachments, stats; no irons/sounds yet. Tools copied to brita/tools/wave2/ (stats2.py anchors+refs list, build_data2.py, build_parts2.py, wave2_conv.py).

### [(title not listed: see context)](https://claude.ai/artifact/GDo3hnuc3f7yobpPex2FX6)
- Status words near it: **awaiting**
- `memory/zomboid-called-strikes-plan.md`: Preview doc "Calling in air strikes": https://claude.ai/artifact/GDo3hnuc3f7yobpPex2FX6 (Claude Doc 7b4261e3-0b77-48ea-bd42-fa9733a1bc81). Sent 2026-11-06 11:50, awaiting calls A1-A8:

### [(title not listed: see context)](https://claude.ai/artifact/GWKvrbx7c2NyUdoU7damkk)
- `memory/zomboid-broadcasts-plan.md`: PICKER posted 2026-10-31 05:20: https://claude.ai/artifact/GWKvrbx7c2NyUdoU7damkk (176 options; builder scratch thrtools/bcpage.py, page dir bcpick/, firefight mixes in bcmade/; picks in db collection `picks`). Slots: radio.eas.header/attention/eom, radio.static, radio.tune, world.meta.gunfire, world.meta.scream, world.alarm.house.bell/siren, block.alarm_panel.beep/disarm.

### [Expressive Voice Samples](https://claude.ai/artifact/GZaHTZKrEyg7WEnHwTea5B)
- Last updated: 2026-11-10

### [(title not listed: see context)](https://claude.ai/artifact/H5RCRNRHHZsc5ZCnLEqQyV)
- `memory/zomboid-enemies-direction.md`: RULE (Josiah 20:42): ZomboidCraft is never sold or redistributed, so designs, names and ideas may be drawn directly from other games (supersedes "no franchise names" for design borrowing; still don't rip other games' art/sound files). He asked for research on other games' paranoia/"seeing things" and killable enemy types: doc https://claude.ai/artifact/H5RCRNRHHZsc5ZCnLEqQyV ("Horror and enemy research: what other games do").

### [(title not listed: see context)](https://claude.ai/artifact/HHD4XorcfKYd7L7SMJZXt5)
- Status words near it: **approved**
- `memory/zomboid-pz-install-extract.md`: 2026-11-01 02:12 Survivor line-up preview: https://claude.ai/artifact/HHD4XorcfKYd7L7SMJZXt5 (male + female std / Detailed Player Texture 3578569664 / Evil's Ink 3408780732; Bob/Kate idle/walk/run, Bob sprint/sneak). Both mods are NUDE female retextures (mod.info says NSFW/Nude): 02:20 Josiah: no nude, but full-body detail; base = bra+briefs. v2: both mods full body with Zomboid vanilla bra+briefs painted over (uwtex.py: vanilla bluish pixels, per-column band fill + 3px grow at 256), checked front+back in 3D. Outfit toggle Underwear/Starter. Never ship the mods without that underwear layer. Workshop folder approved for this thread too.

### [(title not listed: see context)](https://claude.ai/artifact/HSRsAcZt2fr2doUjAwYRST)
- Status words near it: **waiting**
- `memory/zomboid-brita-weapons.md`: 02:25 Josiah: wave 1 = 5 guns from each category + ALL attachments. Pick page sent: https://claude.ai/artifact/HSRsAcZt2fr2doUjAwYRST (372 guns, 14 groups; variants folded; he pastes picks back as "Cat: Name [id]; ..."). Source: scratchpad pz/cat.py -> brita_cats.json -> pick_data.json + pick_template.html. Awaiting his picks.

### [(title not listed: see context)](https://claude.ai/artifact/HSqxssuzVqTUWwtebz3Z1L)
- `memory/zomboid-harvestman-direction.md`: Preview: https://claude.ai/artifact/HSqxssuzVqTUWwtebz3Z1L. FOUR legs to points, not spider-like.
- `memory/zomboid-harvestman-tech.md`: Preview: https://claude.ai/artifact/HSqxssuzVqTUWwtebz3Z1L (v26 now). Full page /mnt/project-files/zomboid-mod/characters/preview-src/harvestman.html, built by concatenating r5_head.css.html + r5_body.html + r5_a.js + r5_b.js + r5_c.js + r5_d.js (same folder; r4_* = the old big-body round). Direction: [[zomboid-harvestman-direction]].

### [Mission Scenery Preview](https://claude.ai/artifact/HXPQkGti34VwkioifKn3Ug)
- Last updated: 2026-11-10

### [(title not listed: see context)](https://claude.ai/artifact/Hd12DbkzkbNz84KTff3qMy)
- `memory/zomboid-floor-fitout.md`: Status 2026-10-28 00:07: merged into playtest (branch claude/project-thread-l1uzx6). Preview page: https://claude.ai/artifact/Xwb4WB9cEAjktp5mzaNBsS (ideas page: https://claude.ai/artifact/Hd12DbkzkbNz84KTff3qMy).
- `memory/zomboid-floor-purpose-rule.md`: Josiah 22:40-22:41 on the floor plan ideas (https://claude.ai/artifact/Hd12DbkzkbNz84KTff3qMy): "You can add all the floors and types so long as they actually make sense for their location. Like have a tool floor in a skyscraper doesn't really make sense where as in an industrial park it could". Then: "Having variety in a building doesn't mean fitting it up with anything to make it different, the floors still need to make sense relative to their purpose".

### [(title not listed: see context)](https://claude.ai/artifact/HdEjrBsrAfZyyhjh921amz)
- Status words near it: **awaiting**
- `memory/zomboid-harvestman-presence-tech.md`: Full-potential ideas doc (89 numbered options, sized S/M/L): https://claude.ai/artifact/QELQLccAaw2LVobQZubMrk. Suggested next picks are #1 routes, #14 mimicry, #45 blood trail, #65 rescue a friend, #71 kill rewards and #73 field journal. 2026-10-29 01:41 Josiah marked YES on 56: 18-27, 29-32, 34-43, 45, 46, 49, 51, 52, 64, 65, 67-76, 78, 81-83, 87, 89. His notes: #22 a killed player's corpse is left at their door 1-2 days after respawn; #75 the lab one is a BOSS (military were modifying it as a weapon vs the others, unfinished): stronger, smarter, far more imposing look; #76 the Queen is an activity, NOT the end goal (else the zombies don't make sense). My thoughts doc with 7 open questions ...

### [(title not listed: see context)](https://claude.ai/artifact/JfAGDXcErswEnvCQndH1WJ)
- `memory/zomboid-cases-tech.md`: - Preview: https://claude.ai/artifact/JfAGDXcErswEnvCQndH1WJ (drag-to-spin frames rendered with the scratch item viewer at fov 30, 3x supersampled).

### [(title not listed: see context)](https://claude.ai/artifact/Km6Si49ZcuY73Bvw7bvgmG)
- `memory/zomboid-broadcasts-plan.md`: SIRENS FOR VEHICLES (2026-10-31 07:05): VEHICLES asked for car.siren (US wail, seamless 4-6 s loop, range ~160) and car.alarm (classic cycling alarm, ~4 s loop), registered in sounds.json under those ids; VEHICLES registers the SoundEvents itself. Picker https://claude.ai/artifact/Km6Si49ZcuY73Bvw7bvgmG (87 options; builder scratch thrtools/carpage.py, synth carsynth.py; fetch groups car_siren/car_alarm, Sonniss rows sel_car.json). After picks: cut loops, credits, playtest, tell VEHICLES (thread cmsg_01LvQiYGyHLhwyRNcM6wP1DpFXa9FDBNk7bNGWp7LGSDPi).

### [(title not listed: see context)](https://claude.ai/artifact/LUE9X3xwNMfDcpHLJ5XSR7)
- `memory/zomboid-shion-declined.md`: Preview: https://claude.ai/artifact/LUE9X3xwNMfDcpHLJ5XSR7.

### [(title not listed: see context)](https://claude.ai/artifact/LZ3jaerKbnyaYPQcbSoyB6)
- Status words near it: **chose**
- `memory/zomboid-blender-tutorial.md`: 22:55 MANNEQUINS (Josiah does it solo): run sheet doc https://claude.ai/artifact/LZ3jaerKbnyaYPQcbSoyB6. Mesh named "Mannequin", clips prefixed mq_, files mannequin_mix.json/mannequin_mixN.json (never touch Rebecca's mix*.json), spec clips pose_1..N (game holds frame 0; he trims in Mixamo), add_clothes 4th arg Mannequin, stand = rigid "bone": mixamorigHips. export.html got ?skin= (cf6bd2dec playtest). MY PART when he sends pose list: replace ml_mannequin blocks (MallShop windows FASHION/JEANS/SHOES) + clothing floors with posed .zsk props, random pose/outfit sets, some knocked over. He chose LOCAL ONLY (23:02): mannequin.zsk lives in his npc folder; game code must fall back to the old block  ...
- `memory/zomboid-mannequin-run.md`: 2026-10-31, Blender tutorial thread. Mannequin run sheet: https://claude.ai/artifact/LZ3jaerKbnyaYPQcbSoyB6. Related: [[zomboid-blender-tutorial]].

### [(title not listed: see context)](https://claude.ai/artifact/Lez5BQYtKQYAehuQYXHosr)
- `memory/zomboid-car-maintenance-plan.md`: - Plan doc https://claude.ai/artifact/Lez5BQYtKQYAehuQYXHosr. Answers (03:48): hand pump also RAISES; 3 classes, class-locked (no cross-fit); Mechanics lost on death; no parts-list changes for now. PZ review 7rkGg3jdUDUZuHf1QeEvVE sec 12 + sec 20 'Vehicles and fuel' (19 settings) are VEHICLES' to build (settings keys go to COMMANDS).

### [(title not listed: see context)](https://claude.ai/artifact/MDso4csQs6mtqMmaFf64Z8)
- `memory/zomboid-sound-history.md`: - R8 cars: https://claude.ai/artifact/MDso4csQs6mtqMmaFf64Z8. Scratch clips8/, zpick8/. Also 2026-10-25: weapon.bullet.flesh range 16 -> 64 (Josiah wanted hits heard further), b5fbd35.

### [(title not listed: see context)](https://claude.ai/artifact/MMoKYZqhTfLHeSzWq8RH7S)
- Status words near it: **awaiting**
- `memory/minecraft-zomboid-mod.md`: Mood/weather/streets (Josiah, 2026-10-24): world must be permanently dark and moody: NO sunny days (day = dark grey clouds/storms, night = dark but the moon may break through clouds at times for a little ambient light; days must never be bright), harsh weather events; wants more colour from neon signs, plus shops (convenience, grocery, restaurants) and street clutter to fill empty streets. Ideas doc: https://claude.ai/artifact/MMoKYZqhTfLHeSzWq8RH7S (proposed v0.4.0 weather, v0.4.1 shops+signs, v0.4.2 street fill; awaiting his answers).

### [(title not listed: see context)](https://claude.ai/artifact/MaGwmRQ7NHRjUuJfq4HsiC)
- `memory/zomboid-survivor-clothes-tech.md`: - Dressing-room preview: https://claude.ai/artifact/MaGwmRQ7NHRjUuJfq4HsiC

### [(title not listed: see context)](https://claude.ai/artifact/Mnik6r9hRkYoARfsLTdBxU)
- Status words near it: **approved, awaiting, waiting**
- `memory/zomboid-clothing-style.md`: **All-outfit preview (2026-10-25):** https://claude.ai/artifact/Mnik6r9hRkYoARfsLTdBxU shows all 15 zombie types in kits (kits.js in the scratchpad: shared shirt/pants/boots/sneakers/hair/hat/watch/glove builders, then KITS per outfit). Not built into the mod; waiting for Josiah's feedback and go.
- `memory/zomboid-dismember-direction.md`: **Plan posted 09:45 (awaiting his answers):** per-limb hit zones (forearm, upper arm, shin, thigh, from the hit point in the zombie's frame and whether its arms are out); per-limb health with per-gun multipliers in config (weapon thread tunes the numbers); limb breaks at the joint above the hit, flies along the shot, tumbles and lies on the ground; torn stump with bone + blood burst stays on the zombie; no arms = no grab, one leg = crawler, both = legless crawl. Asked: melee dismemberment too? Killing headshots sometimes blow off head or jaw? Preview shipped 09:58 in kits artifact v15 (https://claude.ai/artifact/Mnik6r9hRkYoARfsLTdBxU): "Shoot it" gun picker, click to shoot; limb toughness e ...
- `memory/zomboid-movement-v2.md`: Preview: kits artifact v14 https://claude.ai/artifact/Mnik6r9hRkYoARfsLTdBxU (move.js patched in by patch_move.py; sources in /mnt/project-files/zomboid-mod/characters/preview-src/). 07:00 tweak: idle legs stay planted, only the body sways ("Much better").
- `memory/zomboid-zbody-tech.md`: Built 2026-10-26 (characters thread) from the approved preview (kits.html, artifact https://claude.ai/artifact/Mnik6r9hRkYoARfsLTdBxU). Direction: [[zomboid-characters-direction]], movements: [[zomboid-movement-v2]].

### [(title not listed: see context)](https://claude.ai/artifact/MsV98kR3JgUFt54wDVhkFx)
- `memory/zomboid-base-rooms-plan.md`: Plan doc https://claude.ai/artifact/MsV98kR3JgUFt54wDVhkFx sent 2026-11-06. Room = enclosed roofed space via LightSwitch.room (3x3 to 400 floor), inside a claim/outpost; type by majority key fixtures; grades Basic/Equipped/Full; power-gated grades fall back. Rooms: bunk, kitchen+stores, med bay, armoury, workshop (B42 stations), motor pool, comms post, power+water, farm (may be outdoor; greenhouse = Full). Outposts: marker in a purged district + supply cache + radio post + 4 sandbags, 24-block claim, no zombie respawn, max 3 +1 per comms grade.

### [(title not listed: see context)](https://claude.ai/artifact/N8Grqa2vWW4qboSBtjBf7t)
- `memory/zomboid-aircraft-sounds.md`: Picker (sent 2026-10-27): https://claude.ai/artifact/N8Grqa2vWW4qboSBtjBf7t "Dead Air Aircraft", 201 full-length recordings. Picks are in db collection `picks` (roles = event short names like jet.idle, plus a note giving time ranges). Builder: scratch airtools/airpage.py (template musictools/template.html). Sources: Sonniss air_* in /mnt/project-files/zomboid-mod/sound-raw/sonniss/ (sel_air.json), Freesound air_* on branch sounds-raw (run 18; afterburner/jet distant groups returned nothing), Gamemaster bonus explosions.

### [(title not listed: see context)](https://claude.ai/artifact/NJZpZhoTsXzLjLCZjpaYLH)
- Status words near it: **waiting**
- `memory/zomboid-world-loot-tech.md`: - 07:40 toon mesh test: tools/items/loot/mesh.py (monkeypatches Model.seg/arc to exact angles in-process only; rods -> cylinders, sh.ball(tag) -> spheres, UVs projected onto the stepped boxes' painted faces). Preview https://claude.ai/artifact/NJZpZhoTsXzLjLCZjpaYLH (scratch view/mview.html + mshots.js, page4/). Awaiting Josiah's go; then write .zmesh (v3) + special item JSON once WEAPONS' build is green.

### [(title not listed: see context)](https://claude.ai/artifact/NMxfr8BG9BAruWfKkg4Bj3)
- `memory/zomboid-weapons-direction.md`: - Previews: Gunsmith Bench (rifle) https://claude.ai/artifact/AEwQwvHABaZw7EvgLs9y6Q; Armoury (melee, shaders on/off) https://claude.ai/artifact/NMxfr8BG9BAruWfKkg4Bj3. Photon needs Materials > Normal/Specular Mapping on to show the maps ([[photon-shader-settings]]).

### [Ammo HUD Mock-up](https://claude.ai/artifact/NTrXMZu49FXqeqD1Heqzzr)
- Last updated: 2026-11-08

### [(title not listed: see context)](https://claude.ai/artifact/P3ZSkR1F5eGn9gCMMcqHe2)
- `memory/zomboid-world-loot-tech.md`: - All-models preview (2026-10-29 03:40): https://claude.ai/artifact/P3ZSkR1F5eGn9gCMMcqHe2 (563 thumbnails, search + rarity filter). Rebuild: gen_loot.py --out /tmp/claude-0/loot/pvall; serve /tmp/claude-0/loot on :8765; view/thumbs.js -> thumbs3/; page3/build.py + template.html.

### [(title not listed: see context)](https://claude.ai/artifact/PgV8knaxyt8Mqg62gkRCrd)
- `memory/zomboid-hunt-board.md`: - Mock: https://claude.ai/artifact/PgV8knaxyt8Mqg62gkRCrd (source in UI scratchpad huntboard/hunt-board.html). Kit style; radio faceplate (CMD 41.25, Overwatch/Reaper), purge credit total, tabs (districts, targets, objectives, escorts), list + detail with Overwatch caption, net log strip, Air support box, Track pins one line to the HUD. Opens from the G menu when carrying something that hears 41.25, or right-click the military radio.

### [(title not listed: see context)](https://claude.ai/artifact/Pp1bQP48f9LZxhCEhK3v2L)
- Status words near it: **waiting**
- `memory/josiah-world-art-direction.md`: (2026-10-25 23:48) He liked the character thread's "cartoonish but believable, Borderlands-like" style (Soldier Player Body, https://claude.ai/artifact/Pp1bQP48f9LZxhCEhK3v2L): each part its own box, dark ink border, lit line under the top edge, lighter tops, darker undersides, flat bold colours with grime. Landscape take: fits props, furniture, trims and add-ons; big walls/floors get no per-block ink edges (grid look).
- `memory/zomboid-characters-direction.md`: **23:20:** soldier skin on the mockup player body (body A v2, skin sampled by vertical slices of each vanilla part; head+body addon pieces scaled in, limb pieces skipped): https://claude.ai/artifact/Pp1bQP48f9LZxhCEhK3v2L (scratchpad player/skinned.js). Preview only, waiting for go.
- `memory/zomboid-clothing-style.md`: **What the style is** (from the soldier preview https://claude.ai/artifact/Pp1bQP48f9LZxhCEhK3v2L):
- `memory/zomboid-loot-rarity-direction.md`: - 23:44 (characters thread, via coordinator): all clothing and worn gear (helmets, vests, plate carriers, watches, NVG mounts) use that thread's cartoonish-but-believable 3D-block style (he calls it Borderlands-like). Check its soldier preview https://claude.ai/artifact/Pp1bQP48f9LZxhCEhK3v2L before modelling any gear. Guns and melee keep [[zomboid-weapons-direction]].

### [(title not listed: see context)](https://claude.ai/artifact/QELQLccAaw2LVobQZubMrk)
- Status words near it: **awaiting**
- `memory/zomboid-harvestman-presence-tech.md`: Full-potential ideas doc (89 numbered options, sized S/M/L): https://claude.ai/artifact/QELQLccAaw2LVobQZubMrk. Suggested next picks are #1 routes, #14 mimicry, #45 blood trail, #65 rescue a friend, #71 kill rewards and #73 field journal. 2026-10-29 01:41 Josiah marked YES on 56: 18-27, 29-32, 34-43, 45, 46, 49, 51, 52, 64, 65, 67-76, 78, 81-83, 87, 89. His notes: #22 a killed player's corpse is left at their door 1-2 days after respawn; #75 the lab one is a BOSS (military were modifying it as a weapon vs the others, unfinished): stronger, smarter, far more imposing look; #76 the Queen is an activity, NOT the end goal (else the zombies don't make sense). My thoughts doc with 7 open questions ...

### [(title not listed: see context)](https://claude.ai/artifact/QcWrQW8iUpZ4sysoCx5Vhb)
- `memory/zomboid-brita-wave3-tech.md`: State 2026-11-06: all 88 new guns are pulled from his Brita + Arsenal workshop folders, converted to toon meshes, given stats (scaled against an anchor plus a Zomboid reference gun, same as stats2) and their Arsenal-designated attachments. Review page (private artifact): https://claude.ai/artifact/QcWrQW8iUpZ4sysoCx5Vhb. This is LOCAL ONLY data and must never go in the repo, jar or CI.

### [Server plan: hordes, mods and hosting](https://claude.ai/artifact/QfY9K7qNZfvN8WtR1Aj3k8)
- Last updated: 2026-11-08
- `memory/zomboid-server-capacity.md`: Josiah 2026-11-08 10:26 asked for an honest view on whether a Minecraft server can handle the purge plan; 10:28 "start with step 1 but look into step 2 and anything else ... then give me options for step 3". Write-up doc: https://claude.ai/artifact/QfY9K7qNZfvN8WtR1Aj3k8 (Docs id bfa3a52b-374f-446e-be58-f69d4ec5c0ad).

### [(title not listed: see context)](https://claude.ai/artifact/R4k3dc38HqkgGSkZRomZAS)
- `memory/zomboid-characters-direction.md`: **2026-10-25 22:53 Josiah:** remove the thumbs; KEEP today's skins (he likes their detail) and add the joints (knees, elbows) and the slimmer look. Built on branch claude/project-thread-wfvzb7: WalkerModel splits limbs (ELBOW 5, WRIST 4, KNEE 6) sampling the same player-layout skin rows, limb xz scale 0.8/0.86, head 0.9, optional face blocks; gen_walker_addons.py moves pieces below a joint onto right_forearm/right_shin etc. Soldier preview https://claude.ai/artifact/R4k3dc38HqkgGSkZRomZAS. Asked: keep raised face blocks or flat painted face? Playtest got the thumb version at 175e050 (not sent to him). Player body/arms touch the weapons thread's PlayerModelMixin/HoldPoses/RifleArms: coordinat ...

### [(title not listed: see context)](https://claude.ai/artifact/RD5T3BiyPu3zxoMwpWCa5o)
- `memory/zomboid-sound-history-2.md`: - 2026-10-29 pin/ring round 2 (Josiah: none of round 1 fitted): https://claude.ai/artifact/RD5T3BiyPu3zxoMwpWCa5o, scratch thrtools/pinpage.py (skips round-1 keys), synth tones in scratch synthring/. Freesound groups thr_pin, thr_ring2 (ring search stalls; fetch now has socket timeout + step timeout).

### [(title not listed: see context)](https://claude.ai/artifact/RgAkTAA4GbYS7KURcuC75W)
- `memory/zomboid-blender-tutorial.md`: 2026-10-30 06:31. Josiah has never used Blender and wants to learn adding a character himself, one small step at a time (a few clicks, then he sends a screenshot). Written guide: https://claude.ai/artifact/RgAkTAA4GbYS7KURcuC75W.

### [(title not listed: see context)](https://claude.ai/artifact/RzeiRdLD8NsZJs1vAHZaWp)
- `memory/zomboid-clothing-wave2.md`: Plan doc (Claude Doc): https://claude.ai/artifact/RzeiRdLD8NsZJs1vAHZaWp "ZomboidCraft clothing wave 2". 44 military + 16 useful kit + 28 civilian + 8 Prized/Grail (bomb suit, shark mesh suit, museum knight armour, special unit carrier...) + 122 from PZ (pads/limb armour, survivor-made B42 armour, headwear, face/eyes, tops, vests/aprons, legs/socks, jewellery/canteens).

### [(title not listed: see context)](https://claude.ai/artifact/SXv5jbf9EN4jV6B6ezY8zh)
- Status words near it: **superseded**
- `memory/zomboid-sound-history-2.md`: - Ambient music round 2 (sent 12:50): Drones https://claude.ai/artifact/C6taAXAhf6Wv315ebLKA7x (303 Sonniss), Dark Ambient https://claude.ai/artifact/SXv5jbf9EN4jV6B6ezY8zh (277 free), Freesound https://claude.ai/artifact/T92AmMUsVKtaSFjrG1DzZM (137 CC0). Built by scratch musictools/ambpages.py; picks in db collection picks (roles). Old melodic pages superseded.

### [(title not listed: see context)](https://claude.ai/artifact/Sfr793TzeSendKLXEUPD3K)
- `memory/zomboid-holsters-spawn-direction.md`: Mockup v1 (Design canvas, 3 boards): https://claude.ai/artifact/Sfr793TzeSendKLXEUPD3K. Sources are in project files zomboid-mod/inventory-ui/holsters-spawn-v1/. The map is county.svg from make_map.py over plan_dump.js output of grayhaven-plan-draft3.

### [(title not listed: see context)](https://claude.ai/artifact/T7GqDNuxrnjgeSJZbEM4fL)
- `memory/zomboid-reloads-tech.md`: RELOAD EDITOR (built 2026-11-03 22:5x at his ask): private artifact https://claude.ai/artifact/T7GqDNuxrnjgeSJZbEM4fL. Three views, each its OWN reload: fp, tp = third person standing (used when NOT moving), ta = third person aimed (from tpaim pose; used while MOVING, the main one other players see; his rule 22:28-22:30). Aimed (ta) default keeps the gun up in the aim the whole time (his ask 23:01); hand keys have sw = elbow swing (deg, about shoulder-wrist line, JS engine only). FP now draws the upper arm too (static trA2; he wants it in the game FP reloads as well, 23:04); hand keys also have sho = shoulder fit offset (m, view space); spec.armfit.{L,R} = {sw, sho} applies to every key of t ...

### [(title not listed: see context)](https://claude.ai/artifact/T8LZVy8JASLZqNcu3RVWdo)
- `memory/zomboid-design-palette.md`: - Preview pages: Barlow + Barlow Condensed (uppercase, letter-spaced) from Google Fonts, the dark palette above, section headings with a hairline running off them, cards like KitCard. Reference mockup source: project files zomboid-mod/inventory-ui/destiny-v1/Main.dc.html. Example page: https://claude.ai/artifact/T8LZVy8JASLZqNcu3RVWdo
- `memory/zomboid-rolls-tech.md`: Step 2 of the loot doc ([[zomboid-loot-rarity-direction]]), built 2026-10-25 on branch `rolls` (from `classics`); SHIPPED in playtest c5fdab2 at 23:43. Preview: https://claude.ai/artifact/T8LZVy8JASLZqNcu3RVWdo (source in the weapons thread scratchpad rollsprev/, template.html + build.py).

### [(title not listed: see context)](https://claude.ai/artifact/T8T4SFwD6sEXFCAwgcEP8F)
- Status words near it: **approved**
- `memory/zomboid-survival-plan.md`: Thread "Build Zomboid survival systems" (SURVIVAL) started 2026-10-31. Plan doc https://claude.ai/artifact/T8T4SFwD6sEXFCAwgcEP8F sent; approved order 03:46, building wave 1.

### [(title not listed: see context)](https://claude.ai/artifact/T92AmMUsVKtaSFjrG1DzZM)
- Status words near it: **superseded**
- `memory/zomboid-sound-history-2.md`: - Ambient music round 2 (sent 12:50): Drones https://claude.ai/artifact/C6taAXAhf6Wv315ebLKA7x (303 Sonniss), Dark Ambient https://claude.ai/artifact/SXv5jbf9EN4jV6B6ezY8zh (277 free), Freesound https://claude.ai/artifact/T92AmMUsVKtaSFjrG1DzZM (137 CC0). Built by scratch musictools/ambpages.py; picks in db collection picks (roles). Old melodic pages superseded.

### [(title not listed: see context)](https://claude.ai/artifact/THFbk2CoWgyUanmjS9fhAZ)
- Status words near it: **go ahead**
- `memory/zomboid-wearable-clothing.md`: Batch 1 IN PLAYTEST 2026-10-29 ~03:05 (merge 345d75f9). Josiah 03:24 said go ahead without his feedback. Batches 2 headwear, 3 tops/outer, 4 legs/feet/hands/neck in playtest by 04:20 and placed by the world-loot thread; batch 5 uniforms/armour/aircrew merged 04:30 (efd6220b). ALL wearables made (91). Not made as clothing because they already exist in ModItems: combat_helmet, plate_carrier (a BagItem carry vest; registering it twice crashes the server), leather/tool/duty belts. Full wardrobe preview: https://claude.ai/artifact/THFbk2CoWgyUanmjS9fhAZ (scratchpad build_page.py). Remaining: recheck all against refs/items/clothing photos when they land; await his feedback. Playtest merges always  ...

### [(title not listed: see context)](https://claude.ai/artifact/TTEJpUcpSHVdUEKZJyxrgD)
- `memory/zomboid-world-loot-tech.md`: - Roster source of truth: tools/items/loot/roster.py (rows `id | Name | rarity | value | kg | place.weight...`, PLACES legend, * = item already exists). ~704 rows / ~750 items incl. skill books x5. Roster page (v2 2026-10-27 23:1x, wave-1 renders + Use per item + crafting draft): https://claude.ai/artifact/TTEJpUcpSHVdUEKZJyxrgD; rebuild from scratch /tmp/claude-0/loot/template2.html + pagedata2.json.

### [(title not listed: see context)](https://claude.ai/artifact/TcJGSnMYqhLtR2vjNna63s)
- `memory/zomboid-sound-history.md`: - R13 classics (branch classics): https://claude.ai/artifact/TcJGSnMYqhLtR2vjNna63s; db picks weapon_double_barrel_shot/_shot_distant/_dry/_open/_shell_in/_close and weapon_revolver_shot/_shot_distant/_dry/_cock/_open/_eject/_round_in/_close. Clip files live under pool folders (double_barrel.action, revolver.small, revolver.cylinder); scratch clips13/, zpick13/.

### [(title not listed: see context)](https://claude.ai/artifact/TgfqRgPiao5wQ5cxVWbLSU)
- Status words near it: **awaiting**
- `memory/zomboid-population-direction.md`: Sizing doc sent 2026-10-27 ~09:45: https://claude.ai/artifact/TgfqRgPiao5wQ5cxVWbLSU ("Zombie population: how many, where"): ~115,000 per world (seed 12345: 25 downtowns; metro 3,000, city 2,000, old city 1,400, county seat 450, main street 250, hamlet 80, suburbs 90-240, farms 5-12, ~10 per empty 512 cell), hotspot building bonuses, ~1/4 houses empty, population.density 0.25-4, shared real cap 250, real within 96 / fold past 112. Four questions at the end; awaiting answers. Counting tool: javac -sourcepath src/main/java County.java + a small main using County.placesNear (CountPlaces in old scratchpad).

### [(title not listed: see context)](https://claude.ai/artifact/TufN8zkGBfc8Tq7wZTBmQY)
- `memory/zomboid-base-customisation-plan.md`: Plan doc https://claude.ai/artifact/TufN8zkGBfc8Tq7wZTBmQY (5 picks, answered below).
- `memory/zomboid-pz-review.md`: 2026-10-31 03:25: the base thread's plan https://claude.ai/artifact/TufN8zkGBfc8Tq7wZTBmQY is the one Josiah picks from for bases (plumbing is a wrench job there, not a skill; hoist frame Metalworking 7, hoist circuit Electrical 3). It will merge this doc's beam rule, furniture classes and edit mode if he agrees, so section 11 here is input only.

### [(title not listed: see context)](https://claude.ai/artifact/U5yxeaVBdABFCpq5LDond6)
- Status words near it: **awaiting, chose, rejected**
- `memory/zomboid-performance-tech.md`: GOAL (Josiah 09:22): for the finished game he wants HUNDREDS of zombies filling areas at once. Proposed: virtual zombies (records far from players, real entities only near; ties in with roaming hordes in [[zomboid-hordes-death-base-direction]]), shared flow-field horde paths, then GPU-drawn near zombies. He chose "plan it now": Claude Doc https://claude.ai/artifact/U5yxeaVBdABFCpq5LDond6 ("Hundreds of zombies: horde engine plan", 09:30): step 0 /zc perf readout + stress command, 1 virtual zombies (Population per county cell, Herds, Materializer; ~200-250 real per player; fold back past ~96), 2 flow-field paths + distance think rates + grid pushing + lighter network, 3 GPU drawing (Photon tes ...
- `memory/zomboid-population-direction.md`: Josiah 2026-10-27 09:36, answering the horde engine plan (https://claude.ai/artifact/U5yxeaVBdABFCpq5LDond6):

### [(title not listed: see context)](https://claude.ai/artifact/UCUCueszUoTCCTDEedJ7H6)
- `memory/zomboid-hordes-death-base-direction.md`: Plan doc (Claude Doc, 11:10): https://claude.ai/artifact/UCUCueszUoTCCTDEedJ7H6 ("Hordes, body recovery and base building"). It recommends this order:

### [(title not listed: see context)](https://claude.ai/artifact/ULgu4oKzaDqFKrZXbwEomg)
- Status words near it: **picked, waiting**
- `memory/zomboid-old-guns-removed.md`: 2026-11-02 07:21 Josiah picked "Build Arsenal-places loot now" + "need to do it the right way first time". Check page https://claude.ai/artifact/ULgu4oKzaDqFKrZXbwEomg (source /mnt/project-files/zomboid-mod/brita/loot/: gun-loot-check.html, pagedata.json, parse.py/data.py parse Arsenal's Brita_06Multi_Distro.lua staged at /mnt/user-data/uploads/108600/2297098490/.../media/lua/server/Items/). Awaiting his Copy review.

### [(title not listed: see context)](https://claude.ai/artifact/Ur4ZP18eXgMUDALKpCEhyZ)
- `memory/zomboid-arms-attachments-tech.md`: Josiah asked 2026-11-03 for "foregrips and scopes" in the posing tool (https://claude.ai/artifact/Ur4ZP18eXgMUDALKpCEhyZ, v19). Related: [[zomboid-arms-posing-tool]], [[zomboid-brita-irons-tech]].
- `memory/zomboid-arms-posing-tool.md`: Private artifact https://claude.ai/artifact/Ur4ZP18eXgMUDALKpCEhyZ (v41, 2026-11-06), 129 Brita guns (14 pilot + 27 wave 2 + 88 wave 3). Version history: [[zomboid-arms-posing-history]].

### [(title not listed: see context)](https://claude.ai/artifact/VLtHCbjNs3PetBZcdR2FYz)
- Status words near it: **waiting**
- `memory/zomboid-pz-install-extract.md`: 2026-11-01 02:10 PZ zombie preview sent: https://claude.ai/artifact/VLtHCbjNs3PetBZcdR2FYz (MaleBody + M_ZedBody01 rot 1-3, Bob_Trousers denim, Bob_JacketLeather black, Bob_Hair_Messy, tshirt/socks/trainers texture layers, Blood/GrimeOverlay; clips Walk/Walk2/Walk3/Walk5_Faster/Sprint/Sprint02/Idle). Tools (no PZ data) in /mnt/project-files/zomboid-mod/pz-extract/tools/ (xfile.py .x text parser, export.py -> json, textures.py, page.html, build.py). Facts: .x is TEXT, quats stored w,x,y,z and need conjugate for three; D3D matrices -> THREE fromArray directly; UVs flipY=false; clothes textures share body UV layout; m_Masks index: 3 LArm 5 RArm 7 LLeg 9 RLeg 12 Chest 13 Waist 14 Belt 15 Crotch  ...

### [(title not listed: see context)](https://claude.ai/artifact/VV4LMsng7U6hgiVFs8sXCa)
- `memory/zomboid-command-net-tech.md`: Voice (Josiah 20:30 "need to add voice to the comms"): Piper voice picker https://claude.ai/artifact/VV4LMsng7U6hgiVFs8sXCa (12 voices, scratch vox/make.py radio filter, piper models in scratch piper/; db picks, not watched). Plan: pre-render every Overwatch line like OTTO (tools/trains/otto_voice.py), numbers/places as stitched pieces, player names spoken as "friendlies". Alt: fish.audio on his PC.

### [Heart and More Voices](https://claude.ai/artifact/VhfitkthrtV2mMbuVHfeLK)
- Last updated: 2026-11-10

### [(title not listed: see context)](https://claude.ai/artifact/VonMFjCeJDCZM9hXPwPFE7)
- `memory/zomboid-sound-history.md`: - R14 Gamemaster (paid packs): FULL page https://claude.ai/artifact/VonMFjCeJDCZM9hXPwPFE7 (all 828 sounds, unaltered; Josiah: never cut short or alter, show ALL options). Picks keyed by clip key '<pack>/<stem>'; one sprite ogg per section (off/dur), scratch gm15.py. First clip 'current' = what ships now. Raw: /mnt/project-files/weapons.zip. Raw files never go in the repo. Built 1e95e68 (tools/audio/build_gamemaster_sounds.py, takes used whole). AR bolt + suppressed + distants kept.

### [(title not listed: see context)](https://claude.ai/artifact/Vs6KaSGs2JErxbA2oD7oPY)
- `memory/zomboid-enemies-direction.md`: Options doc sent 18:4x: https://claude.ai/artifact/Vs6KaSGs2JErxbA2oD7oPY ("New enemies: a mini boss and more").

### [(title not listed: see context)](https://claude.ai/artifact/W72i2AvsVVtwznqjM5nasf)
- `memory/zomboid-wearable-clothing.md`: Batch 1 preview: https://claude.ai/artifact/W72i2AvsVVtwznqjM5nasf (source scratchpad clothes/template.html + build_page.py). Not yet checked against photos.

### [(title not listed: see context)](https://claude.ai/artifact/WWzGJEyT25vTMRJN5FTbm5)
- `memory/zomboid-weapons-review-share.md`: Plan doc: https://claude.ai/artifact/WWzGJEyT25vTMRJN5FTbm5 (sent 2026-10-31 03:55).

### [Voice Picks, Round 2](https://claude.ai/artifact/WixHj7vWMhzCe1yMPgVioE)
- Last updated: 2026-11-10

### [(title not listed: see context)](https://claude.ai/artifact/WkvvhHXdy7xcx14hbUjTYj)
- `memory/zomboid-sound-history.md`: - R4 loot: https://claude.ai/artifact/WkvvhHXdy7xcx14hbUjTYj. Containers get ONLY open/close sounds, no rummaging. Zombies were "incredibly loud", so -6 dB.

### [(title not listed: see context)](https://claude.ai/artifact/WmTStX6bxjqG5CvMVnXPep)
- `memory/zomboid-command-net-tech.md`: 20:38 Josiah: he records Overwatch himself. Recording script Claude Doc https://claude.ai/artifact/WmTStX6bxjqG5CvMVnXPep (128 clips, ids like in_1, task_purge, copy_hammer_laser, place_kingsbridge, num_35; source scratch script/build.py + script.json). One long take, I cut. Captions must change to match the voice (ETA/kills rounded to the num_ set, names -> "friendlies", escort "Get them..."). Piper picker now moot.

### [(title not listed: see context)](https://claude.ai/artifact/WpTFknZM22vWNNa6p388LB)
- Status words near it: **ok**
- `memory/zomboid-car-maintenance-plan.md`: - Vehicle settings (Josiah 06:37 'yes'): build all 20 of Zomboid's Vehicles-page settings the Zomboid way at Zomboid defaults, incl. new car fuel+dash gauge, working pumps (power or generator), door locks+hotwire, alarms, sirens, survivor cars, crash injuries. Plan doc https://claude.ai/artifact/WpTFknZM22vWNNa6p388LB. Wire via SandboxOptions.wires() owner VEHICLES. BUILT 07:10 on branch: Fuel.java (pumps, file zomboidcraft_pumps.json, LOOT GasCanItem + PowerGrid + rubber_hose), CarProp fuel/locks/alarm/siren(double-tap H)/crashHurt/survivor(); LOOT owns car/survivor table; LANDSCAPE owns TrafficJam (world.trafficJams); AUDIO making car.siren/car.alarm (Josiah picks). All waves merged to pla ...

### [Inventory In Game](https://claude.ai/artifact/XBqKLWd87x7CiZEF5f2GMG)
- Last updated: 2026-11-07

### [(title not listed: see context)](https://claude.ai/artifact/XhJ2KiQ7mRbrwN3ghCPFVg)
- Status words near it: **waiting**
- `memory/zomboid-npc-models.md`: **08:58 companion:** elf rigged (tools/npc/rig.py, rigs/dark_elf.json, pose.py ported to NpcPose.java), follows/waits on G, flees zombies, picks up guns and shoots (NpcBrain). Animated preview https://claude.ai/artifact/XhJ2KiQ7mRbrwN3ghCPFVg. Josiah: "make the animations look natural?" Card offered: Mixamo rig+mocap (recommended), CC0 mocap on our rig, or polish procedural. Awaiting pick.

### [(title not listed: see context)](https://claude.ai/artifact/XkK8zUaqN51riTdqtz5V3x)
- `memory/zomboid-harvestman-direction.md`: 00:32-00:38 ATTRIBUTES/BRAIN asks: a roaming AI entity that thinks for itself (needs, traits, memory, decides to move/trap/feed); climbs walls and sits in dark corners; learns to stalk properly, learns player movements, follows them, works out traps; mind games (drags bodies, poses them, suspends them); learns and EVOLVES in game. It CHOOSES ONE PLAYER, remembers them by account across log-offs, notices when they log back in, prioritises hunting them with targeted paranoia (per-player layer). Admin /zc harvestman target <player>|clear (built). Doc: https://claude.ai/artifact/XkK8zUaqN51riTdqtz5V3x: 5 stages, utility AI + Alien: Isolation-style director.

### [(title not listed: see context)](https://claude.ai/artifact/Xwb4WB9cEAjktp5mzaNBsS)
- `memory/zomboid-floor-fitout.md`: Status 2026-10-28 00:07: merged into playtest (branch claude/project-thread-l1uzx6). Preview page: https://claude.ai/artifact/Xwb4WB9cEAjktp5mzaNBsS (ideas page: https://claude.ai/artifact/Hd12DbkzkbNz84KTff3qMy).

### [(title not listed: see context)](https://claude.ai/artifact/Y12ckZkKCgzzdvVqQ9nffi)
- `memory/zomboid-sound-history.md`: - R10 built 4683c31 (tools/audio/build_gun2_sounds.py). R11 hunting rifle: https://claude.ai/artifact/Y12ckZkKCgzzdvVqQ9nffi (db picks weapon_hunting_rifle_shot/_shot_distant/_dry/_bolt/_mag_out/_mag_in; mag moments share one pool). Scratch clips11/, zpick11/.

### [(title not listed: see context)](https://claude.ai/artifact/Y2XxL5R2jdVjsL9L11foiH)
- `memory/minecraft-zomboid-mod.md`: Design doc 'ZomboidCraft Design Bible' (Claude Doc, all sections filled 2026-10-24): https://claude.ai/artifact/Y2XxL5R2jdVjsL9L11foiH

### [(title not listed: see context)](https://claude.ai/artifact/Y9b2iMCmp8UvZcXEp2Jwj1)
- Status words near it: **awaiting, ok**
- `memory/zomboid-brita-weapons.md`: 2026-11-01 PILOT (14 guns, first of each class): sound picks in /mnt/project-files/zomboid-mod/brita/pilot-sound-picks.txt (board https://claude.ai/artifact/Dwuangqe3QfnRXU2uL24sT). Arsenal shots .45/.308/.45LC/.50/12g/SD5.56 have baked echo slaps that sound like a burst (he noticed); fix = brita/single_shot_fix.py (keep first hit, smooth tail), "(single)" options on board v2, awaiting his OK. Look test https://claude.ai/artifact/Y9b2iMCmp8UvZcXEp2Jwj1 published 03:10, awaiting picks. Orientation: PZ models +Y muzzle, Z up; long guns are modelled upside down (only Colt1911/SW_M500 upright) so flip 180 about the muzzle axis. Tools copied to brita/tools/.

### [(title not listed: see context)](https://claude.ai/artifact/YXACnbqGTpnovxxNH8bCsS)
- `memory/zomboid-companion-bond-tech.md`: - Test room v3 has the "Her and you" panel: https://claude.ai/artifact/YXACnbqGTpnovxxNH8bCsS
- `memory/zomboid-companion-work-tech.md`: - Test room (same URL, v2): https://claude.ai/artifact/YXACnbqGTpnovxxNH8bCsS. It has the bed, chest, planters, campfire, barricaded window and three containers outside, plus job, shift and skip buttons. ?sim=N runs N sim steps per frame for headless checks. Source is in project files zomboid-mod/assets/woman/tools/room/.
- `memory/zomboid-living-companion.md`: 00:28 Step 1 done: NpcMind.java (needs 0..100, traits -1..1, diary saved as text in "npcMind"), Forage goal, need lines, /zc npc mind|trait|feel. Merged to playtest as 7036f4e. Test room artifact: https://claude.ai/artifact/YXACnbqGTpnovxxNH8bCsS. Source in project files zomboid-mod/assets/woman/tools/room/ (index.html + app.js + pack_room.py run from the repo root on woman.zsk, a JS copy of NpcMind/NpcBrain). Model files are woman.txt (base64 deflated lod0, since .bin isn't a served type) and tex_*.

## Other pages (mostly Endless Night: Minecraft history)

### [Reach Realism Previews](https://claude.ai/artifact/1YWUtHBKxMq6zA2MLw7xjt)
- Last updated: 2026-11-10
- `memory/zomboid-reach-realism-direction.md`: - 2026-11-09 12:40 OVERNIGHT PREVIEW PAGE (keep updating, same URL): https://claude.ai/artifact/1YWUtHBKxMq6zA2MLw7xjt (source scratchpad reach-realism.html; Sword Base update 1 posted).
- `memory/zomboid-reach-shader-tech.md`: - Preview page (all 9 missions): https://claude.ai/artifact/1YWUtHBKxMq6zA2MLw7xjt (scratchpad reach-realism.html + rr/img).

### [Reach Atlas](https://claude.ai/artifact/2MKmXgXtvXcVDWz73z4es7)
- Last updated: 2026-11-08
- Status words near it: **superseded**
- `memory/zomboid-reach-world-direction.md`: - Josiah 2026-11-08 21:30 SEPARATE WORLD (overrides 'entire map changed'): the current county world stays complete as ITS OWN package/world, untouched; the Reach world is a BRAND NEW world (own layout, not mapped onto county spots). R19 county-spot mapping and R16 fallback are superseded by this. Layout page: https://claude.ai/artifact/F94zL9sYymxQA7eN5Fu6Av (Anniversary layer off per R3). Scripts in /mnt/project-files/zomboid-mod/halo/tools/worldlay/. Atlas: https://claude.ai/artifact/2MKmXgXtvXcVDWz73z4es7.

### [Cortana Talking Samples](https://claude.ai/artifact/2MWrQVCMjebc2Y2j6GCKX8)
- Last updated: 2026-11-11

### [Reach campaign areas: path to 100%](https://claude.ai/artifact/3o64gTmJ6EzoCrxtpxVCLg)
- Last updated: 2026-11-10

### [Covenant Vehicle Crews](https://claude.ai/artifact/4C7gviHtXp5NtU3VjSbt9m)
- Last updated: 2026-11-10

### [Endless Night: replace every Minecraft effect with Reach](https://claude.ai/artifact/4XVCdmR8SnyBPfppKS4hWZ)
- Last updated: 2026-11-11
- Status words near it: **waiting**
- `memory/endless-night-no-minecraft-effects.md`: Plan doc: https://claude.ai/artifact/4XVCdmR8SnyBPfppKS4hWZ (Docs 1c8d1bba-0b81-46a3-8b66-cba9f69bbbda, body 493c94fe-6f6a); picks M1 safety-net mute of all minecraft: sounds/particles, M2 operators keep Tarkov sounds, M3 Reach death/respawn, M4 drop drown/burn/freeze, M5 Reach chat+scoreboard. Awaiting answers.

### [Livelier Cortana Voice](https://claude.ai/artifact/4nX5bgeqBuuDHmFdhU72My)
- Last updated: 2026-11-10

### [Endless Night: new repo and Modrinth instance](https://claude.ai/artifact/4rKhfvR6rEpYTUx2tJFkA8)
- Last updated: 2026-11-09
- Status words near it: **awaiting**
- `memory/endless-night-new-repo.md`: Plan doc (thread "Move Endless Night to its own repo"): https://claude.ai/artifact/4rKhfvR6rEpYTUx2tJFkA8 (Docs id 1f2e62c0-7876-44bb-a9bc-a82f9ab21cfd). Decisions R1-R9 awaiting his picks. Recommended: repo `endless-night` private; copy playtest then strip county/retired systems (strip list previewed first); fresh history; mod id -> `endlessnight` (packages com.endlessnight, config/endlessnight); same CI + one-jar rule, new playtest-build link; old ZOMBOIDCRAFT frozen as county world package; keep additions folder.

### [(title not listed: see context)](https://claude.ai/artifact/54yQY2ovySjamUDo56gD1b)
- Status words near it: **ok**
- `memory/tarkov-gear-clothing-intake.md`: DATA: Tarkov item DB from github paulov-t/Paulov.Tarkov.Db (database/templates/items.json, locales/global/en.json, handbook.json; July 2025; sp-tarkov repos are LFS = unreachable). Script + output: zomboid-mod/tarkov/gear/{build.py,tarkov_gear.json} (362 items: grids, cells, armour class/zones/plates, penalties, blocks, Tarkov RarityPvE+price -> our tiers; class5>=Prized, class6=Grail; where/use heuristics). Ledger page https://claude.ai/artifact/54yQY2ovySjamUDo56gD1b sent 09:2x. No Tarkov grid inventory in code yet (carry/ is slot based); next = grid inventory mock-up for his OK, then batches.

### [Noble Six Third Person](https://claude.ai/artifact/59LHPWeCiQKUJiZqerMTnY)
- Last updated: 2026-11-11

### [Tarkov Inventory Copy](https://claude.ai/artifact/5dJAYHen2tryTazFePaHaK)
- Last updated: 2026-11-07
- Status words near it: **waiting**
- `memory/tarkov-gear-clothing-intake.md`: Grid inventory mock-up https://claude.ai/artifact/5dJAYHen2tryTazFePaHaK (src gear/inv-mockup: head+imgs.json+tail): worn slots (headwear, earpiece, face, eyes, armband, top, armour, rig, trousers, backpack, holster, sheath, primary), 4 1x1 pockets, rig/pack grids, 11x15 crate, drag/R rotate, containers carry contents, nested packs only if empty. Awaiting his approval (09:2x).

### [Covenant Vehicle Animations](https://claude.ai/artifact/6WWv3C6uM3b4jxtnBaCDRV)
- Last updated: 2026-11-10

### [Reach World Build Log (older)](https://claude.ai/artifact/6kgecYTWoFJJKcmfEB1q9j)
- Last updated: 2026-11-09

### [Cortana, Directed](https://claude.ai/artifact/6oJ4y8ej68U6sVGEZzkXVH)
- Last updated: 2026-11-10

### [(title not listed: see context)](https://claude.ai/artifact/8rEXe85MURXjDFveYsgz5p)
- Status words near it: **awaiting, ok**
- `memory/tarkov-weapon-batch2.md`: Bundles all in scratch raw1 (08:1x; staged via RC-made zips additions\tarkov-pilot\packs\b2_0..2.zip + pack_batch2.ps1). Kit export: tk/b1/kitexp_b2.py (TURN fix: Deagle Mk19 receiver root identity), shots tk/b2shots, page https://claude.ai/artifact/8rEXe85MURXjDFveYsgz5p sent 08:2x awaiting OK. SCAR-H kit uses 16in barrel. Next after OK: builds like batch 1.

### [ZomboidCraft: Purge the World direction](https://claude.ai/artifact/92iDNQQ3vpJUMVT5gpcU8z)
- Last updated: 2026-11-08
- `memory/zomboid-purge-direction.md`: Plan doc: https://claude.ai/artifact/92iDNQQ3vpJUMVT5gpcU8z (Docs id 4106406a-612d-4e84-8fe5-d6d7dddc9b63, body fba88dbe-3bf0). His answers are CAPITALS inline in the doc (09:28).
- `library/zomboid-mod/purge/purge-world-placements.md`: From the purge plan (https://claude.ai/artifact/92iDNQQ3vpJUMVT5gpcU8z), Josiah's answers 2026-11-08. Counts are starting numbers; sandbox settings tune most of them.
- `library/endless-night/purge/purge-world-placements.md`: From the purge plan (https://claude.ai/artifact/92iDNQQ3vpJUMVT5gpcU8z), Josiah's answers 2026-11-08. Counts are starting numbers; sandbox settings tune most of them.

### [Endless Night HUD](https://claude.ai/artifact/95W29RJjoiYmXupmGXURhw)
- Last updated: 2026-11-08
- `memory/zomboid-endless-night-hud.md`: - Mock (UI thread): https://claude.ai/artifact/95W29RJjoiYmXupmGXURhw (scratchpad halohud/hud.html). Spartan: shield + health pips top centre, weapon/mag/ticks/carried/second gun/grenades top right, 25 m motion tracker bottom left (red foes, yellow mates), armour ability, waypoints, nameplates, Reach-style "Hold G to swap", kill feed, tracked hunt line + district Covenant-held %. Operator: near-empty; stamina bars bottom centre while draining, status icons bottom left only when wrong, Tab health body, gun line (name, fire mode, mag/carried) after reload/switch, stance figure.

### [(title not listed: see context)](https://claude.ai/artifact/97P1rAN54jfpHhCRx7j5m7)
- `memory/zomboid-zombie-behaviour-plan.md`: Plan doc sent 2026-10-31 ~04:10: https://claude.ai/artifact/97P1rAN54jfpHhCRx7j5m7 ("Zombie behaviour plan"). Zomboid defaults from https://pindrop.gg/project-zomboid/sandbox-settings (B42.21 Apocalypse): Speed Random (sprinter 0%, ours 15 kept), Toughness Random, Sight/Hearing Random Normal-Poor, Memory Normal, Cognition Basic, Drag Down on, Lunge on, Armor 2/85, attached weapon 6%, fake dead World Zombies, fences 25 zombies; population: migration on, peak 1.5 at day 28, redistribute 12 h, follow sound 100, rally 20 (+-50%), travel 20, separation 15, radius 3, delete past 300 (ours folds to records). Helicopter Once (day 6-9).
- `library/zomboid-mod/zombie-behaviour/INVENTORY-for-endless-night.md`: - Plan doc: https://claude.ai/artifact/97P1rAN54jfpHhCRx7j5m7, with Josiah's answers in it.
- `library/endless-night/zombies/INVENTORY-for-endless-night.md`: - Plan doc: https://claude.ai/artifact/97P1rAN54jfpHhCRx7j5m7, with Josiah's answers in it.

### [Endless Night: the Reach world plan](https://claude.ai/artifact/AAttusu1HZswrkuKKx1tvX)
- Last updated: 2026-11-08
- `memory/zomboid-reach-world-direction.md`: - PLAN DOC (2026-11-08 12:45): https://claude.ai/artifact/AAttusu1HZswrkuKKx1tvX. Choices R1-R20 with my recs, answers in CAPITALS + summary table column. Build order: 1 Sword Base in game (render/Photon test) -> 2 survey all 44 maps -> 3 layout preview -> 4 first New Alexandria block -> 5 districts -> 6 gameplay layer -> 7 switch-over (old county kept as fallback).

### [Cortana Voice Sorter](https://claude.ai/artifact/BV9SuRAYs9yve3LcVdz9y4)
- Last updated: 2026-11-09
- `memory/zomboid-cortana-hologram.md`: - 07:36 2026-11-09 Josiah RULE: only use Cortana lines in her NORMAL voice, never the distorted ones (m90 nudge/defend lines used in the gesture reel are distorted). Audio features can't sort it reliably (cpp90 / voicing hints only), so built sorter artifact https://claude.ai/artifact/BV9SuRAYs9yve3LcVdz9y4 (all 896 lines, audio + transcripts, my guess; his choices in db collection 'marks' doc=index {v:'n'|'d'}; unmarked = guess). Source: tools /tmp/claude-0/voxa (feat/cpp/tr.json), copy endless-night/cortana/. PC zip additions\halo-4\cortana\voice_all.zip. NEXT: read marks, re-pair gestures with normal lines, write normal-only list to PC.

### [Endless Night HUD and screens](https://claude.ai/artifact/CfBA45dYMXzyEBv4aLxpyb)
- Last updated: 2026-11-08
- `memory/zomboid-endless-night-hud.md`: - Doc "Endless Night HUD and screens": https://claude.ai/artifact/CfBA45dYMXzyEBv4aLxpyb (Docs id 5e690c24-77af-4e2b-ba56-272679f91a16). Retires moodle bar, belt hotbar, quick wheel, watch, skill tape, level-up card, body figure; rain on face stays. Screens table (field kit -> Spartan status page / simplified operator inventory; new creation screen; UNSC-terminal hunt board; purple district map; Reach-style death screen; mechanics cut to fuel+repair).

### [Endless Night: Tarkov weapons vs the Covenant](https://claude.ai/artifact/Cvy9iiDKbUR9qgxSkxPwHU)
- Last updated: 2026-11-09
- `memory/tarkov-covenant-damage-plan.md`: Josiah 2026-11-09 02:17: "plan the damage that tarkov weapons will do to covenant enemies". Doc: https://claude.ai/artifact/Cvy9iiDKbUR9qgxSkxPwHU (Docs id 609d8c87-5a33-4a5e-9dbb-2960488b8dd3, body b8592030-ca61). Thread "Plan the purge-the-world direction".

### [Reach In-Game Check](https://claude.ai/artifact/DRKt89iPdKMgCp5qjNDLL4)
- Last updated: 2026-11-10

### [Reach World Layout](https://claude.ai/artifact/F94zL9sYymxQA7eN5Fu6Av)
- Last updated: 2026-11-08
- Status words near it: **superseded**
- `memory/zomboid-reach-world-direction.md`: - Josiah 2026-11-08 21:30 SEPARATE WORLD (overrides 'entire map changed'): the current county world stays complete as ITS OWN package/world, untouched; the Reach world is a BRAND NEW world (own layout, not mapped onto county spots). R19 county-spot mapping and R16 fallback are superseded by this. Layout page: https://claude.ai/artifact/F94zL9sYymxQA7eN5Fu6Av (Anniversary layer off per R3). Scripts in /mnt/project-files/zomboid-mod/halo/tools/worldlay/. Atlas: https://claude.ai/artifact/2MKmXgXtvXcVDWz73z4es7.

### [(title not listed: see context)](https://claude.ai/artifact/H38FNjVwJwFRbY61Pc28N2)
- `memory/tarkov-zomboid-scoping.md`: Options doc: https://claude.ai/artifact/H38FNjVwJwFRbY61Pc28N2 (thread "Scope first-person Zomboid in Tarkov").

### [Reach World Build Log](https://claude.ai/artifact/JgQSXHzjoEUzfrTLhiWJQ6)
- Last updated: 2026-11-09
- `memory/zomboid-reach-world-tech.md`: - Previews: build log artifact https://claude.ai/artifact/JgQSXHzjoEUzfrTLhiWJQ6 (scratchpad reachbuild/index.html + img/). Thread attachments are maxed (24), so link previews there. Preview tools + land pass: [[zomboid-reach-land-tech]]. Viewer web/view3.html (c=target, d=dir, r=dist; 'e' ignored) draws voxels with block grid.

### [Cortana Voice and Ideas](https://claude.ai/artifact/KMDHViHrRx34U6YUj1Y1pc)
- Last updated: 2026-11-10

### [Reload Sounds Previews](https://claude.ai/artifact/L9aEK4v5GnmDTBnC6PtWmz)
- Last updated: 2026-11-08
- Status words near it: **approved, picked**
- `memory/tarkov-reload-sounds-tech.md`: Josiah 2026-11-08 10:58: many guns miss reload audio; MP-18 shows no round going into the chamber. Plan (sound timeline per gun + MP-18 round mesh, previews of M4A1 + MP-18 first) approved 11:17. Previews sent 11:3x: https://claude.ai/artifact/L9aEK4v5GnmDTBnC6PtWmz (videos also in /mnt/project-files/zomboid-mod/tarkov/previews/reload-sounds/), Josiah 11:41: sounds fine; fixed preview M4 left hand (renderer gripHand bent wrist; vid3.js now takes env GRIPIK=0, game M4 has no holds) + MP-18 round (zc_round/zc_case q must be IDENTITY under patron_in_weapon_000: mesh is along Y tip -Y; the ammo prefab's -90X stood it upright). Re-sent v2 11:5x. BUILT 12:0x: code 14bcec87e+8de6da2cf (branch), che ...

### [Tarkov Batch 3](https://claude.ai/artifact/LmrQPZLUVbEXXDaaDaKoDq)
- Last updated: 2026-11-08
- Status words near it: **awaiting, ok**
- `memory/tarkov-weapon-batch3.md`: 13:5x-14:1x: 8 previews + bench sheets on page https://claude.ai/artifact/LmrQPZLUVbEXXDaaDaKoDq (media b3_<g>_p1.*; scratch b3page), awaiting his OK. Rhino had no mod_aim_camera ('aim_camera' is the player camera, NOT a sight point): synthetic mod_aim_camera added to web/data/b3_rhino.json (pl15's offset from rear sight). ads.js/exp2 accept ^mod_aim_camera (AUG/G36 use _001/_002). Bench previews: b2/loadouts_b3.json, fit.py+preview.py know b3b. M16A2 still to stage (folder tools missing after RC stop).

### [Cortana Made-Up Moves](https://claude.ai/artifact/Mb1pjkVS5Xpkj1AFhm1N1x)
- Last updated: 2026-11-10

### [(title not listed: see context)](https://claude.ai/artifact/PCGaa9UXTKtXeCRRa5reJB)
- Status words near it: **awaiting, ok**
- `memory/tarkov-bench-fitting.md`: MOVEMENT (07:11 ask, 2026-11-07): Gait.java (Tarkov paces, guns.tarkovPace), TarkovMotion (motion.json, gun = root displacement+turn about the base bone pivot), TP walk/run/sprint clips in new operator.zsk; commits f799e5c22, 01812590a; preview https://claude.ai/artifact/PCGaa9UXTKtXeCRRa5reJB awaiting OK. motion.json + new operator.zsk on his PC since 08:21.

### [Tarkov Batch 2](https://claude.ai/artifact/QpM8ocZrBrUULEdezqNwpn)
- Last updated: 2026-11-07
- Status words near it: **awaiting, ok**
- `memory/tarkov-weapon-batch2.md`: BUILT 09:4x (kits OK'd 08:48): scratch /tmp/claude-0/tk/b2b (pack/ zsks, local/guns + reloads cues, mkguns.py, vids/plan2.py+run2.sh) and tk/b2 (208 new tkp parts, parts_game.json = new parts.json). AA-12 = pellets 8 + ammo shotgun_shell; LocalGuns makes a magazine gun with own pellets>1 use ShotgunStats (commit 120c0aaef on branch, not yet merged). Preview page https://claude.ai/artifact/QpM8ocZrBrUULEdezqNwpn sent, awaiting OK. After OK: stage to local-pc (npc zsks, guns/*.json, guns/reloads cues, guns/tarkov/parts.json, 208 tkp zips, sound pack tk/b1/pack_snd), copy to PC, merge to playtest. Flip-up irons stay up behind optics (co-witness); offered Tarkov-style folding. Unreachable at ben ...

### [Reach custom games with bots: options](https://claude.ai/artifact/RxPE2mByt5qwYUfnFv9Tmu)
- Last updated: 2026-11-11
- `memory/reach-bots-custom-games.md`: - Josiah 10:13 (STRICT): just add it to the EXISTING main menu and route into MCC's EXACT Reach custom games UI/setup, everything the same, only a Bots option added. Plan doc https://claude.ai/artifact/RxPE2mByt5qwYUfnFv9Tmu: MULTIPLAYER entry -> MCC multiplayer screen -> Custom Games lobby; Bots row under Game Options (count 0-15, Easy/Normal/Heroic/Legendary, my team/against/split/FFA, per-bot edit); unique armour per bot. Asked: cap 15?, gamertag names?, persistent looks? Needs his MCC screenshots of the custom games flow (MP screen, lobby, change map/game, options, loading, invite) into endless-night/ui/refs/.

### [Cortana as Heart](https://claude.ai/artifact/S9kkwyLgu1ahVGK1VW3YZ6)
- Last updated: 2026-11-10

### [Cortana Talking Indicator](https://claude.ai/artifact/STofmEDkSVPwxWxHsHZaKA)
- Last updated: 2026-11-10

### [Moving Endless Night to Unreal Engine 5](https://claude.ai/artifact/SbZhp8CNK595V3RbLXFZL1)
- Last updated: 2026-11-11
- `memory/endless-night-unreal-move-scoping.md`: Josiah asked 2026-11-11 if Minecraft is the best engine. Doc "Moving Endless Night to Unreal Engine 5" (Claude Doc, https://claude.ai/artifact/SbZhp8CNK595V3RbLXFZL1), thread "Scope moving Endless Night to Unreal 5".

### [Endless Night MCC Menus](https://claude.ai/artifact/SeF5FdjZwFjSMFwJEY9ndF)
- Last updated: 2026-11-10

### [(title not listed: see context)](https://claude.ai/artifact/SmFemxnSpHc8JhvxS1GLMo)
- `memory/tarkov-weapon-batch1.md`: Picked 2026-11-06 20:36 in the doc "Tarkov weapons list" (https://claude.ai/artifact/SmFemxnSpHc8JhvxS1GLMo, ticks = picks): Makarov PM, Glock 17, MP5K, AK-102, AS VAL, SVDS, MDR 7.62x51, SV-98, Remington 870, MP-18, F-1 grenade, Ka-Bar, M48 Kukri. (M4A1 already in game: [[tarkov-operator-ingame-tech]].)

### [Tarkov Melee](https://claude.ai/artifact/TSFbioZPu27dYB4FXcspZW)
- Last updated: 2026-11-07
- Status words near it: **ok**
- `memory/tarkov-melee-only.md`: BUILT 11:4x: 22 FP packs scratch /tmp/claude-0/tk/melee/pack (operator_tarkov_<g>.zsk), hands jsons tk/melee/local/hands (sounds "tarkov_kabar"), anchors: knives->kitchen_knife, sp8/taiga->machete, m2sword/gladius/scythe->katana, axes+labrys+rebel->fire_axe, kiba/voodoo->tomahawk, crowbar, mpl50->shovel, db2020->sledgehammer, taran->lead_pipe. db2020/scythe/labrys FP clips are blank stubs (Tarkov swings them with full-body SledgeHammer_* clips in character_animations); they use kiba's axe clips instead (heavy.py body->FP try parked). Preview https://claude.ai/artifact/TSFbioZPu27dYB4FXcspZW OK'd 11:39; all 22 zsk + hands jsons copied to profile npc/ + hands/ 11:4x (staged /mnt/project-files/ ...

### [Reach Weapons Pull](https://claude.ai/artifact/TskRqJdSfVLXdjbm11TkSp)
- Last updated: 2026-11-09
- Status words near it: **awaiting, pending**
- `memory/zomboid-halo-weapons.md`: DONE 2026-11-09 ~07:45 (was pending 23:45): first-person preview of ALL weapons + Noble arms on https://claude.ai/artifact/TskRqJdSfVLXdjbm11TkSp v2 (#first-person, #noble-arms); awaiting his feedback. FP GLBs only on his PC (additions\halo-reach\animations\first-person, spartan_fp_<w>.glb, clips incl. idle/fire/reload/melee); PC offline at 23:45 -> render Spartan FP video per weapon when PC is back. Preview page https://claude.ai/artifact/TskRqJdSfVLXdjbm11TkSp (thread at 24-attachment cap).

### [Reach Build Tour](https://claude.ai/artifact/UjWqKsSi6FKNWnyNK44RPP)
- Last updated: 2026-11-09
- `memory/zomboid-reach-realism-direction.md`: - 2026-11-09 11:28 build tour: https://claude.ai/artifact/UjWqKsSi6FKNWnyNK44RPP (shots in endless-night/reach-world/previews/tour-2026-11-09/). Issues seen: dark fog band hides land, backdrop undersides show, Winter Contingency trees white, Exodus untextured, PoA building blown white, flat lighting. Josiah wants shots viewable on phone (use an Artifact page; thread attachment cap is full).

### [Reach Packed Layout](https://claude.ai/artifact/UoKbaJE3VsM1psx8cTCxTT)
- Last updated: 2026-11-11

### [(title not listed: see context)](https://claude.ai/artifact/UxHyNZNfCqGbJFzxXd67VJ)
- `memory/zomboid-base-rooms-plan.md`: Marine direction (Josiah 2026-11-06 11:22): squad of marines purging the world; base building is a main pillar. Brief https://claude.ai/artifact/UxHyNZNfCqGbJFzxXd67VJ section 3; W6 (base functions) and W26 (outposts) answered yes. Coordinator asked BASES (2026-11-06 11:45) to plan rooms + outposts, preview first, build NOTHING until Josiah signs off. Fortification set (W7) waits: photos parked.
- `memory/zomboid-world-systems-tarkov.md`: Doc: https://claude.ai/artifact/UxHyNZNfCqGbJFzxXd67VJ (Claude Docs id e25f1c49-1bf3-4cb1-aec1-ed3c5d10ed49, body node a6f25050-44c7). Loot/gear/guns/melee/carry/starter kit are NOT here; they live in the Tarkov loot plan YQBXTTQ1C98wNZaxjzgzhJ ([[tarkov-zomboid-scoping]]).

### [Reach Infected Preview](https://claude.ai/artifact/W8iHYcb6YxdFKn49axjU6b)
- Last updated: 2026-11-10

### [Reach World Review](https://claude.ai/artifact/WeseZoaFLD7Kjja41f8ugL)
- Last updated: 2026-11-10

### [Tarkov Batch 3 Kits](https://claude.ai/artifact/XKyMaCog8cU66Jk9PJJL83)
- Last updated: 2026-11-07
- `memory/tarkov-weapon-batch3.md`: Kit export: tk/b1/kitexp_b3.py (ROOTFILE: Rhino 9x19 bundle's meshes live in its _200ds_9x33R_model prefab; TURN g36 stock = identity). Shots tk/b3shots, page kits3 https://claude.ai/artifact/XKyMaCog8cU66Jk9PJJL83 (8 guns, M16A2 to add). G36 kept its own flash hider (no can; cans fit via AR-15 device at the bench).

### [(title not listed: see context)](https://claude.ai/artifact/YQBXTTQ1C98wNZaxjzgzhJ)
- Status words near it: **awaiting**
- `memory/tarkov-zomboid-scoping.md`: 10:43 FACES CHANGED: Josiah: use Tarkov's own player heads ("never republished, purely for myself and friends with the game files"). Use USEC/BEAR player heads (not boss heads); masks stay optional per operator. Loot plan doc https://claude.ai/artifact/YQBXTTQ1C98wNZaxjzgzhJ filled (29 systems: 9 keep/6 replace/9 change/5 leave out, 9 calls), awaiting his sign-off.

### [Cortana Voice Lines](https://claude.ai/artifact/Ya8ynAnEDnJZVxn8SKCcDW)
- Last updated: 2026-11-10

### [Reach Surroundings Previews](https://claude.ai/artifact/YbkLMF1VC3VepFf65oNWmd)
- Last updated: 2026-11-11


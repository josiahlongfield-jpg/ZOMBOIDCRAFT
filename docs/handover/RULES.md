# Josiah's rules and directions for ZomboidCraft (verbatim)

Copied word for word from the old project's memory. Nothing here is summarised. Where two notes differ,
the later date wins; STRICT means he said it must never be broken.

## 1. The old project index, verbatim (its "Standing rules" section applies to every project)

```markdown
# Project memory (index; details in named .md files)

## Endless Night (Reach world) — current
- Repo josiahlongfield-jpg/ENDLESS-NIGHT ONLY for Reach work; files /mnt/project-files/endless-night/; Modrinth "ENDLESS NIGHT" (Fabric 26.3). Jar: .../ENDLESS-NIGHT/releases/download/playtest-build/endless-night-playtest.jar. ONE-JAR RULE: own branch, merge into playtest, green CI (zomboid-playtest-build.md). Mod id endlessnight. Old ZOMBOIDCRAFT repo = frozen county world, NEVER delete it, never merge Reach work there.
- EVERYTHING REACH (Josiah 2026-11-11 11:49, STRICT): remove ALL Minecraft effects/art/animations (melee sounds, red hurt flash on enemies, player damage tick, etc.); everything must be Reach. Plan + picks M1-M5: endless-night-no-minecraft-effects.md.
- LAYOUT (2026-11-11 11:43 APPROVED): TIGHT layout (map artifact UoKbaJE3VsM1psx8cTCxTT, ~11x10 km, ~95 km² playable). Goal = like-for-like walkthrough of the Reach campaign: prioritise detail, less open land; each mission keeps its real playable ground side by side, and areas must FLOW into each other (blended joins). STRICT: no invisible walls, room for Scarabs + vehicles; edges = Halo far land/sea/unclimbable ranges + missions' exact painted backdrops (zomboid-reach-layout-question.md, zomboid-reach-backdrops-direction.md). Build/install details: zomboid-reach-packed-build-tech.md.
- WHOLE-WORLD QUALITY (STRICT): all visible/walkable land real Reach textures + detail (zomboid-reach-whole-world-quality-rule.md, zomboid-reach-land-terrain-tech.md). Realism first, "indistinguishable" (zomboid-reach-realism-direction.md). Terrain/flow rules zomboid-reach-terrain-rule.md. Sky zomboid-reach-sky-direction.md. Places zomboid-reach-places-direction.md. In-game gap + tech: zomboid-reach-ingame-gap.md, zomboid-reach-real-surroundings.md, zomboid-reach-world-tech.md, zomboid-reach-shader-tech.md; build log JgQSXHzjoEUzfrTLhiWJQ6; Atlas 2MKmXgXtvXcVDWz73z4es7. All 44 Reach maps converted on PC (additions\halo-reach\world). Campaign areas 100% first, then MP maps. Game needs ~12 GB memory.
- BOTS: thread "Add bots to Reach custom games": MULTIPLAYER on main menu opens MCC's exact custom-games lobby (from his MCC files) + BOTS tile; preview approved 11:35, building. Up to 31 bots, Reach gamertags, persistent crew, unique armour.
- SPARTAN PLAYER BODY: owned by weapons thread "Pick Spartan and Tarkov weapons by command" (Reach third-person anims, left hand pinned to gun; shared with bots); approved 11:45, building.
- UI: CONTINUE = last world; NEW SOLDIER = fresh world + Spartan selector; SAVED GAMES = world list (0.5.2+06ba2a1). MCC Customization greyed out. HUD all-new; Armory v2 approved, all unlocked; poses = exactly MCC's (zomboid-armory-ingame-tech.md).
- COVENANT: thread "Bring Covenant enemies into Endless Night". Grunts, Jackals, Skirmishers, all Elites, Hunters; NO Brutes/Drones. Infected in jar (/en infected 3). Details: endless-night-covenant-vehicles-tech.md, endless-night-infected-idea.md.
- CORTANA: Halo 4 storm_cortana, v13 look, idle B, normal voice lines only (cortana_voice_normal.json). Calm meaningful gestures, faces never blank. Pipeline GestureLSM + Audio2Face + rules + fast finger index; MaskedMimic via IsaacLab in WSL2 Ubuntu (installed 2026-11-11). Files additions\halo-4\cortana.
- Purge direction: Spartans/Tarkov vs Covenant-led hordes; no world loot/survival needs/grid inventory (zomboid-purge-direction.md). Damage: tarkov-covenant-damage-plan.md. No jets, no companions, no Overwatch audio; Reach audio. Forge: grid/armour stand/trophy wall approved.

- PROJECT SPLIT (2026-11-11): handover packs in docs/handover (ENDLESS-NIGHT PR #1, ZOMBOIDCRAFT PR #6), see project-split-handover.md.
- ENGINE QUESTION (2026-11-11): doc recommends moving to Unreal 5 with a gated trial (m20) first; awaiting his decision (endless-night-unreal-move-scoping.md).

## Standing rules (Josiah)
- RUN EVERY IDEA PAST HIM first; previews before any build or PC access. FULL-DETAIL PREVIEWS ONLY (STRICT); keep preview pages light (connection can be slow).
- KEEP WORKING PAST PC WALLS (all threads).
- SAVE LOCATION: NEVER OneDrive; PC saves to C:\Dev\Zomboidcraft-Desktop\additions.
- NO VANILLA items/chests ever; NO Minecraft zombie/player models ever. Replaced lights = own light.
- FRANCHISE MODELS never committed/in jar; load from config on his PC. Personal use only. MCC/Halo maps local-only (zomboid-halo-maps.md, zomboid-halo-reach-anim-format.md).
- Wikimedia only from his PC. Cloud blocks Fabric/Mojang, Wikimedia, pindrop.gg, pzwiki.net.
- Project storage FULL (10 GB); large files via additions folder on PC.
- Effort: LANDSCAPE high, rest medium. He likes to discuss options before work.
- Pipe bomb out. Hordes never vanish. Scope zoom/iron sights rules (zomboid-scope-zoom-direction.md, zomboid-iron-sights-rule.md, tarkov-suppressors-irons.md, tarkov-grip-holds-tech.md STRICT).

## Setup
- Minecraft Java 26.3 Modrinth; Fabric + Sodium + Iris + Photon + Particle Rain + Continuity; e4mc + Simple Voice Chat. RTX 4070, Ryzen 5700X, ASUS B550M (SVM on). C: ~14 GB free (2026-11-11 11:47), cleanup in progress via Cortana thread. Haze = Rainmeter (F11).

## Legacy county world / Tarkov (frozen ZOMBOIDCRAFT)
- Docs: zomboid-county-plan.md, zomboid-landscape-queue.md, zomboid-pz-review.md, zomboid-survival-plan.md, zomboid-zombie-behaviour-plan.md, zomboid-base-*.md, zomboid-server-capacity.md (options A-D pending), josiah-world-art-direction.md, zomboid-*-direction.md, zomboid-*-tech.md (many), tarkov-*.md, zomboid-endless-night-switch-loot.md. Medieval Gloom pack PAUSED; "MEDIEVAL GLOOM (1)" profile = county only.

```

## 2. Every rule / direction note, verbatim

---

### josiah-preferences.md

---
name: josiah-preferences
description: How Josiah likes to work on game/mod projects
metadata:
  type: user
  modified: 2026-10-24T10:39:23.019Z
---

- Likes to discuss options and see examples before committing to work; ask for direction, show images.
- Ambitious: wants projects to feel like "a brand new game"; happy to pay for tools if they help.
- Plays Minecraft 26.3 via Modrinth App with friends online (e4mc); may move to Realms or a Fabric host later.
- Also plays Project Zomboid Build 42.
See [[minecraft-gloomy-pack]], [[minecraft-zomboid-mod]].
- (2026-10-24) Wants design options shown for everything made: send preview sheets of visual work (skins, outfits, models, buildings) before it ships so he can tweak, and list behaviour settings with defaults.
- (2026-10-24) Very detail-focused on visuals: "pay special attention to detail"; wants art to look "indistinguishable" from his concept renders. Notices blur and missing 3D depth immediately, so show big close-ups, not thumbnails.

---

### full-detail-previews-rule.md

---
name: full-detail-previews-rule
description: STRICT (Josiah 2026-11-10): every preview sent to him must be rendered in full detail, never quick flat-shaded check renders
metadata:
  type: feedback
  modified: 2026-11-10T09:37:54.142Z
---
Josiah 2026-11-10 19:37: "always render previews in full detail so i dont accidentally assume youve changed something".

**Why:** a motion-check video from zskview.py (flat colour per triangle) made him think the full-detail Covenant vehicles had been downgraded to blocky models.

**How to apply:** anything shown to him (videos, stills, pages) uses the full-detail renderer with real textures and lighting (e.g. three.js pages like /tmp/claude-0/cov/web/veh.html / hv2.html with the bake textures). Quick check tools (zskview.py etc.) are for your own eyes only. If a cheap render is unavoidable, say plainly that it's not the real look. Related: [[endless-night-covenant-enemies]].

Josiah 2026-11-11 22:33 AEST: "i need the previews viewable here". A PC path alone isn't enough: publish every preview (image/video) as an Artifact page (embed as data URI, keep it light) and link it in the thread. Thread attachments can hit the 24 cap, so don't rely on attached_outputs.

---

### zomboid-pc-storage-rule.md

---
name: zomboid-pc-storage-rule
description: Josiah 2026-11-08 00:09 STRICT: never save or back up anything to OneDrive folders on his PC; use C:\Dev\Zomboidcraft-Desktop\additions instead
metadata:
  type: feedback
---
RULE (Josiah 2026-11-08 00:09, to all threads): do not save anything or back up to OneDrive folders. Every file we put on his PC (raw Tarkov bundle copies, zips, scripts, archives, backups) goes under C:\Dev\Zomboidcraft-Desktop\additions.
The game profile itself (C:\Users\josia\AppData\Roaming\ModrinthApp\profiles\MEDIEVAL GLOOM (1)\config\zomboidcraft\...) is not OneDrive and stays where the game reads it.
**Why:** the old additions folder (C:\Attachments\OneDrive\Desktop\additions) is OneDrive-synced; files vanished from it before (first Tarkov raw copy), and it eats his cloud storage.
**How to apply:** Remote Control copy scripts write to C:\Dev\Zomboidcraft-Desktop\additions\tarkov-pilot\raw etc.; request folder access to C:\Dev\Zomboidcraft-Desktop\additions (not the OneDrive one) when staging. Old paths in [[tarkov-assets-pipeline-tech]], [[tarkov-weapon-batch3]] and the Brita archive are history only.

---

### zomboid-keep-working-offline.md

---
name: zomboid-keep-working-offline
description: Josiah 2026-11-11 08:27 (his clock): never stop at a "needs the PC" wall; keep working on everything that can be done in the cloud
metadata:
  type: feedback
  modified: 2026-11-10T22:28:04.081Z
---
Josiah 2026-11-11 08:27: "don't just stop when you hit a pc requirement wall, keep working on whatever you can".
**Why:** his PC is often off; he wants threads to keep moving instead of idling until it's back.
**How to apply:** when a step needs his PC, stage it (transit files, memory notes with md5s and install steps) and move straight to the next cloud-doable item on the list (code changes + CI, cloud data builds, MP map prep, previews from cloud renderers). Don't end a turn just because the PC is off while cloud work remains.
Related: [[zomboid-reach-priority-order]], [[zomboid-reach-ingame-gap]].

---

### zomboid-approve-mockups-first.md

---
name: zomboid-approve-mockups-first
description: Josiah 2026-11-02 09:30 - don't build any version into the game until he approves its mock-up/preview
metadata:
  type: feedback
  modified: 2026-11-02T09:30:32.477Z
---

Josiah (2026-11-02 09:30, survivor models thread): "dont waste time putting versions into the game until i approve them".

**Why:** he iterates fast on mock-ups (three first-person arm versions in 20 minutes); game builds and PC preview runs for unapproved versions waste time.

**How to apply:** show a cloud mock-up (e.g. the fp.py/shoot.js harness renders) first, wait for his explicit OK, and only then write the in-game code, push, run CI or start a PC preview run. Stronger than [[zomboid-video-preview-rule]]: the approval comes before the build, not just before the merge.

---

### zomboid-video-preview-rule.md

---
name: zomboid-video-preview-rule
description: RULE (Josiah 2026-10-30 13:21): show a video preview of a change before merging/confirming the build
metadata:
  type: feedback
---

Josiah (UI thread, 2026-10-30 13:21): "show me video example previews before you confirm builds so i know you understand".

**Why:** he got jars where the UI didn't match what he meant (companion portrait mirrored her world movement and faced away).
**How to apply:** before merging into playtest and sending the jar, record a short clip of the change (e.g. stitch the CI client game test's screenshot sequence into an mp4/gif) and send it, then merge once he's seen it. Related: [[zomboid-playtest-build]].

Restated 2026-11-01 09:24 (survivor models thread): "ill need previews before any in game builds". Survivor work can't use the CI client (PZ packs are local-only, never on GitHub), so render previews in the cloud from the local pack (tools/pz/zsk_read.py + the scratchpad packs) with the same posing maths as the Java, and send those first.

---

### zomboid-no-vanilla-rule.md

---
name: zomboid-no-vanilla-rule
description: RULE (Josiah 2026-11-02 06:33): no vanilla Minecraft items or chests in ZomboidCraft, ever, anywhere
metadata:
  type: feedback
  modified: 2026-11-02T06:37:21.677Z
---

Josiah 2026-11-02 06:33 (LOOT thread): "no vanilla items or chest in the game ever".

**Why:** vanilla items and chests break the Zomboid feel; loot and everything a player can hold should be ours ([[zomboid-world-loot-direction]]).
**How to apply:** never add a minecraft: item to a loot table, drop, recipe result, kit or code path (Items.OAK_PLANKS, Items.POTION water, music discs, meats...). Use our own item (plank, water_bottle, potato, vinyl_record, steak, firewood...). Never place a vanilla chest/barrel/shulker in world gen; use fit_/pk_ containers.

Audit 2026-11-02 (LOOT): shop/car tables clean except 8 music discs; vanilla zombie drop table still runs (rotten flesh etc.); Loot.java adds vanilla water potions; block drop tables give oak_log/stick/oak_planks/dirt/string; code hands out Items.OAK_PLANKS (barricades, Hammer, DigRules, Moving, Harvestman), water potions (BuildBlocks rain barrel, Drinks, NpcWork), Items.POTATO (BuildBlocks farming), vanilla meats (DecorUses/NpcWork cook maps); vanilla crafting recipes are not disabled; vanilla farm animals drop vanilla meat.

Built by LOOT 2026-11-02 (commit 2e3bb1608, branch jd27hs): loot/NoVanilla (last MODIFY_DROPS hook; ours(stack) maps vanilla->ours or EMPTY; is(stack,id); item(id)), loot/items/Cooking (raw our food -> "Cooked X" with custom_data cooked/hot; stoves + companions), mixin/RecipeManagerMixin (drops every minecraft: recipe), building/BenchMenu (crouch-use any *workbench fitting = 3x3 grid), our plank added to #minecraft:planks. Beet crop gives onions (no beet item yet). Other threads: use NoVanilla.ours/item instead of new ItemStack(Items.X).

**Lights (Josiah 2026-11-06 04:28):** "Blocks that emit lights need to be replaced with lights that emit colour in the same way and be bright, lighting blocks cannot be replaced with normal unlit items." A vanilla light block may only be swapped for a mod block that emits at about the same level (14-15). Photon colours light by block id, so mod lights throw only his amber blocklight colour: purple/green froglights stay vanilla on purpose. LANDSCAPE e0083040d: amber ones -> front_lampbox_lit / front_glow_panel (15), ceiling_light (14); campfires -> zs_fire_lit (15) / zs_fire.

---

### zomboid-no-wikimedia-rule.md

---
name: zomboid-no-wikimedia-rule
description: RULE (Josiah 2026-10-31, clarified 04:52) photo refs are fetched only through his PC, never from the cloud container
metadata:
  type: feedback
---

Josiah 2026-10-31 04:39: "Forget about ever using Wikipedia it's terrible and a waste of usage. All photo references will come from my pc when that's available."
Clarified 04:52 in REFERENCES: "You use my pc to search them as you have been. I'm just choosing to avoid the Wikimedia option that caused you to have a single photo every 20-40min".

**Why:** the slow option was the cloud container, which Wikimedia throttles to about one photo every 20-40 min. The PC runs (pc_refs.py over Remote Control) are fast and he's happy with them. They also search Wikimedia Commons; REFERENCES told him that on 2026-10-31.

Final wording 04:53: "Then restrict wikimedia to only be used from my pc".

**How to apply:** never fetch photos from the cloud container (batch.py, curl). Run pc_refs.py on his PC through Remote Control, and only once he says the PC is on. Drop off-topic hits before merging. The photo-first rule in [[zomboid-photo-reference-rule]] still stands, and the method is in [[zomboid-refs-collection]].

---

### zomboid-photo-reference-rule.md

---
name: zomboid-photo-reference-rule
description: RULE - every ZomboidCraft build (houses, churches, factories, bunkers, offices, skyscrapers, ships) starts from real photo references, at maximum detail
metadata:
  type: feedback
  modified: 2026-10-26T08:56:38.933Z
---

Josiah 2026-10-26 08:56 (landscape thread): "everything needs maximum detail so i want you to make sure whenever youre building something that you reference real images so you know what direction to take, that goes for everything from house to churches to factories to bunkers to office buildings, skyscrapers etc".

**Why:** after visiting the carrier and cargo ship he found the interiors far too sparse ("the bones" were fine). He wants believable, dense, real-world-accurate builds everywhere.

**How to apply:**
- Before building or rebuilding any structure, look at real photos of that kind of place (outside AND inside) and list what you see: layout, fittings, clutter, lighting, materials. Build from that list, not from memory.
- Download a few reference photos and view them with Read (images); note the sources in the plan or previews.
- The cloud container blocks most image hosts by default. On 2026-10-26 Josiah was asked to set Network access to Full or allow commons.wikimedia.org, upload.wikimedia.org, media.defense.gov, www.dvidshub.net, www.flickr.com, live.staticflickr.com (Project settings, Cloud environment, gear). Check with `curl -sS "$HTTPS_PROXY/__agentproxy/status"` or a test curl; if still blocked, say so and use text research meanwhile, never pretend to have seen photos.
- Wikimedia Commons API: https://commons.wikimedia.org/w/api.php?action=query&list=search&srnamespace=6&srsearch=... then imageinfo&iiprop=url&iiurlwidth=1024 for a thumbnail URL on upload.wikimedia.org.

- 09:10 Josiah: "request images of everything you need to make... if there are things you need to redo id rather we did that now" and "get as many image references as you can to make this world feel real and dense". So EVERY existing place type gets checked against photos and redone where it's off.
- His network change did not reach the already-running landscape container (still 403 at 09:09), so a fresh research thread (cmsg_01LvQiYGyHLhwyRNcM6wP1DpRKVvKgiZyL2HSSQT5DQvSH) downloads photos into /mnt/project-files/zomboid-mod/refs/<ships|town|houses|city|farm|military|port|landscape>/<topic>/ with sources.md per folder. Look there first before building anything.

Related: [[zomboid-building-detail-direction]], [[josiah-world-art-direction]], [[zomboid-carrier-direction]].
- Josiah 2026-10-27 22:10 (all threads): ANY creation in the world (structures, items, clothing, guns, gear) pulls images from the REFERENCES thread (cmsg_01LvQiYGyHLhwyRNcM6wP1DpRKVvKgiZyL2HSSQT5DQvSH, library /mnt/project-files/zomboid-mod/refs/); ask it for missing photos.

---

### zomboid-consistent-detail-rule.md

---
name: zomboid-consistent-detail-rule
description: RULE (Josiah 2026-10-29) - every part of the world gets the same intricate detail as the landmarks; no basic block-shape boats, tents, props next to detailed builds
metadata:
  type: feedback
---

Josiah 2026-10-29 03:05 (landscape thread, after the Waterfront): "having boats made to look like basic block style shapes next to a detailed pier doesn't work. Every aspect of the map needs the same consistent detail level." He called out medical tents and military tents too, and "several other areas". "The time and effort you put into the major landmarks are only as good as the care and attention given to the smaller parts that surround them. Everything in this world must be of the same level of detail and effort."

**Why:** a landmark built with care looks wrong when the small things around it (boats, tents, barriers, vehicles, props) are crude block shapes.
**How to apply:** treat small surrounding pieces as first-class builds. Give them real models (box models with shaped hulls/frames, bought or CC0 models via props.py), photo refs and close-up previews. Before shipping any place, look at every small element in close-up and redo whatever looks like plain blocks. Then audit existing places for low-detail leftovers. See [[zomboid-building-detail-direction]], [[zomboid-photo-reference-rule]].

---

### zomboid-design-palette.md

---
name: zomboid-design-palette
description: RULE from Josiah: every ZomboidCraft design (in-game UI and preview pages) matches the kit screen's palette and template; the tokens to use
metadata:
  type: feedback
  modified: 2026-10-25T23:28:34.837Z
---

Josiah 2026-10-25 23:13 (weapons thread): "Make sure your designs match the design palette and template that the UI is now set in."

**Why:** the Destiny-style kit screen (key I, shipped 23:03, [[zomboid-kit-screen-destiny]]) is now the house look; anything that looks different (old purple tooltips, other page themes) reads as off.

**How to apply:**
- In game: draw with the kit's own pieces in client/kit/KitDraw (bone text on dark glass, letter-spaced caps in the hud font, rarity bands from KitItems.Rarity, diamond tier pips, thin bars, key caps). Don't invent new colours.
- Tokens: background #0b0c0e; glass rgba(12,13,15,.96) with edge rgba(232,228,218,.18); text #e8e4da, bright #f2efe8, dim ~68%, faint ~30%; good #8fc46a, bad #d0675e, warn #d9a33a, link/gold #e0c25e.
- Rarity band / ink / tile gradient: Common #c9c0ad on #1d1b16 (tile #5c5850 to #34312c); Scarce #5d6a34 on #f2efe8 (#525d30 to #23281a); Rare #4a6d97 on #f2efe8 (#3d5a7d to #1b2636); Prized #6e2626 on #f2efe8 (#5c2424 to #2a1212); Grail #b08d3e on #1d1b16 (#6b5626 to #2f2611). Rarity words in lists: #e8dfc8, #a8bc6e, #7faedb, #d0675e, #e2b84c.
- Preview pages: Barlow + Barlow Condensed (uppercase, letter-spaced) from Google Fonts, the dark palette above, section headings with a hairline running off them, cards like KitCard. Reference mockup source: project files zomboid-mod/inventory-ui/destiny-v1/Main.dc.html. Example page: https://claude.ai/artifact/T8LZVy8JASLZqNcu3RVWdo
- The older field kit (E) uses a canvas/tape look (KitArt); new work follows the kit screen unless Josiah says otherwise.

---

### zomboid-commands-thread.md

---
name: zomboid-commands-thread
description: RULE - every ZomboidCraft command any thread adds goes to the COMMANDS thread with a brief description
metadata:
  type: feedback
---

Josiah 2026-10-26 07:35: "start a new thread for commands only and have every thread thats made one send them to that thread with a brief description of what it is". The COMMANDS thread id is cmsg_01LvQiYGyHLhwyRNcM6wP1DpHu5WqiqREvzovCcDKo5tGF.

**Why:** he wants one place to look up every /zc command.
**How to apply:** when you add or change a command, message that thread with its syntax, a one-line description and whether it needs cheats. The UI thread (j1d6bj) has sent /zc injure and /zc heal.

Related: [[zomboid-playtest-build]].

---

### zomboid-playtest-build.md

---
name: zomboid-playtest-build
description: The one-jar rule for ZomboidCraft test builds - shared playtest branch and its fixed download link
metadata:
  type: project
---

Josiah 2026-10-25: "is there a way every new jar im given can have all updated stuff in it so i dont have to have multiple". He was losing features by swapping between per-thread jars.

- Branch `playtest` merges every thread's branch (main + combat + cars cilddw + sounds wh9qba + inventory j1d6bj, ...).
- Its CI publishes prerelease tag `playtest-build` with a fixed-name jar: https://github.com/josiahlongfield-jpg/ZOMBOIDCRAFT/releases/download/playtest-build/endless-night-playtest.jar (renamed from zomboidcraft-playtest.jar 2026-11-08 with the Endless Night display name)
- The tag must not share the branch name (a tag called `playtest` made git refs ambiguous). Push with `git push origin HEAD:refs/heads/playtest`.

**Why:** one jar in his mods folder, always the newest of everything.
**How to apply:** develop on your own branch; when Josiah should try something, merge it into playtest, wait for green, and send him that link (never a per-branch jar). The sound thread can't push to playtest, so the inventory thread merges its commits on request.

Related: [[zomboid-mod-build-notes]], [[zomboid-mod-status]].

26.3 gotcha found the same day: screens open via Gui.setScreen(Screen) (minecraft.gui), not Minecraft.setScreenAndShow. Window has getWidth/getHeight (framebuffer), getGuiScale/setGuiScale(int), calculateScale(int, boolean).

RULE (Josiah 2026-10-26 07:35, via coordinator): every command a thread adds goes to the COMMANDS thread (cmsg_01LvQiYGyHLhwyRNcM6wP1DpHu5WqiqREvzovCcDKo5tGF) with a brief description.

- A playtest run gets CANCELLED when a newer playtest push lands; check the newer run contains your merge (git merge-base --is-ancestor).
- Lesson 2026-10-26: a playtest run once left the playtest-build release as a DRAFT (the jar uploaded but it was never published), so the download link 404'd. After a merge, check that the release's `draft` is false and its `target_commitish` is the merge SHA before sending the link. Re-running that playtest Build run recreates it.
- Since 2026-10-26 11:15 CI stamps the commit on the mod version (0.5.2+abc1234, Stamp build version step in build.yml), so the Modrinth app's mod list shows which playtest jar Josiah has. When he says something is missing, first check his version against the release's target_commitish. Release download_count shows whether he fetched the newest jar.
- Friend install page (2026-10-26): https://claude.ai/artifact/6qyBdcVCBhnd79rbM4Qw5E, txt copy at project files zomboid-mod/friend-install/zomboidcraft-friend-install.txt. Joiners need Fabric API + playtest jar + Simple Voice Chat + Voice Bridge for e4mc (not e4mc itself). Photonics had no 26.3 build. Update both if the required mod list changes.

- 2026-10-28: the merged jar passed 65535 entries and CI failed at :jar ("enable zip64"). build.gradle now sets zip64 on every Zip task (commit 8eca6993, in playtest 4d582eae). Do not drop it when resolving build.gradle conflicts.

- Lesson 2026-10-31 (LOOT): after `git merge origin/playtest`, check `git diff --name-only --diff-filter=U` and STOP if anything is listed. A follow-up `git add -A && git commit` happily commits a conflicted file (en_us.json with markers) as the merge and it went to playtest. en_us.json fix: take playtest's copy, then re-run tools/items/loot/gen_java.py.
- 2026-10-31: the server smoke test greps "Rain cover:"; any new solid mod block outside CitySpecs must be added to data/minecraft/tags/block/blocks_motion.json and blocks_motion_no_leaves.json or the Build fails.

---

### tarkov-suppressors-irons.md

---
name: tarkov-suppressors-irons
description: Josiah's 2026-11-07 10:05 calls - suppressors are quite rare loot (found guns come without cans), every gun must have unsuppressed audio, flip-up irons fold under an optic
metadata:
  type: project
---
ASK (Josiah 2026-11-07 10:05): MCX/SCAR flip-up irons showed behind their holo sights ("are they not replaced?"); "do all guns have an unsuppressed version including audio? I want silencers to be quite rare loot".
Decided (told him 10:1x): flip-up irons fold down while any optic is fitted and stand up when it's removed (Tarkov behaviour), kit guns and bench flip-ups. Found Tarkov guns come WITHOUT their kit can unless Tarkov makes the can required (AS VAL, VSK-94 integral); loose cans roll ~1/8 weight of other parts (GunLoot.tarkovParts). Audio: SR-3M gets loud sr3m_outdoor_close shots (SUPPRESSED_RIFLE unsuppressed -> rifle_shot events), M870 gets mr133_fire_silenced (SHOTGUN suppressed). Game picks loud/quiet from fitted Silencer parts (Attachments.suppressed).
10:14 Josiah: found guns NEVER come with a can; only exception a can physically built in as an immovable piece, and those guns must be RARER than the others. Built (363009c78): TarkovFit.builtInCan -> GunLoot weight x0.25 (BUILT_IN_CAN). NOTE Tarkov guns are not in places.json yet (only 15 Brita guns), so this applies once Tarkov guns get world loot places.
Folds built (363009c78): gun file tarkov.folds [{bone,hinge,axis,angle}] (tk/b2/fold_kit.py -> folds_kit.json; mkguns adds), parts.json part.fold + 2-bone tkp (bone 'fold') for 19 flip-ups (tk/b2/fold_parts.py, pack_part.py; old models pk_prefold/). Tarkov node frame: -y is forward.
**Why:** silencers are meant to be a rare find, so guns must work and sound right bare.
**How to apply:** every future Tarkov gun needs both loud and quiet shot audio when it can take a can; never spawn kit cans on found guns.
Related: [[tarkov-weapon-batch2]], [[tarkov-bench-fitting]].
BRITA REMOVAL (10:4x 2026-11-07): markers on all 15 Brita gun jsons in his profile (in effect), Brita sound pack moved out to additions\tarkov-pilot\resourcepacks, batch 2 copied to PC (883 tkp zsks, 21 tarkov guns). First script's Mv was shadowed by PS mv alias. Cleanup script additions\tarkov-pilot\remove_brita2.ps1 (archives zmeshes/poses/reloads/parts to additions\brita-archive-20261107, restores 13 jsons from cloud brita/local-pc/guns) blocked by the safety check for RC and for me; ASKED JOSIAH 10:47 to run it himself. guns\parts\parts.json (his pack sights) still live until then.
STRICT (Josiah 11:00/11:01): never show flip-up irons raised while any sight/scope is on the gun, in previews, at the bench and anywhere a sight is fitted in game; ADS must go through the sight. 11:0x: game code already folds on any optic (TarkovFit.folded); his complaint came from the 09:27 bench sheets (pre-fold) and cached old videos. Bench sheets re-rendered with folds (tk/b2 rebench.py, sheets_b2.py), page files renamed *_v3 to beat caching. ALWAYS rename preview media on republish.
BENCH REACH (11:0x, commit ee8415a2b): new Slot BUTTPAD (8 pads/cheek rests, parts.json socket 'buttpad'), mounts chain 3 deep, direct fit keeps searching deeper (MCX spacer), socket-less parts link like mounts; Retired.padMoved migrates saved guns with a pad in STOCK. All 20 guns 0 unreachable, all kits rebuild (tk/b2 reach.py, kitsame.py). New parts.json staged /mnt/project-files/zomboid-mod/tarkov/b2copy/parts.json, NOT yet on his PC.

---

### tarkov-grip-holds-tech.md

---
name: tarkov-grip-holds-tech
description: Tarkov foregrip/handguard left-hand holds (GripPose) in previews and in game; SV-98 stock fix; Josiah's 2026-11-07 grip rule
metadata:
  type: project
  modified: 2026-11-07T02:29:20.921Z
---

STRICT RULE (Josiah 2026-11-07 05:36): ALL foregrip attachments must be used (held) whenever fitted to a weapon, including a grip put onto a weapon in game that didn't have one before (bench fitting must switch the hold to the fitted grip's own).
RULE (Josiah 2026-11-07 02:04/02:09): every gun's left hand must sit on its foregrip properly, and a grip LOOTED in game must actually be held by the player model. Scopes-as-loot bench work must cover grips too, each grip bringing its own hold.

How: Tarkov foregrip/handguard (and the SV-98 chassis stock) prefabs carry a left-hand skeleton "Base HumanLPalm 1" (wrap, used) and "Base HumanLPalm". Foregrip wins over handguard.
- Preview viewer (scratch web/index.html): gripPoses()/gripHand() run at the end of pose() unless GRIPIK===false. Weight = how far the palm is from where the IDLE has it on that part (near 2.5 cm, far 6 cm); the old palm-to-hold distance weight dragged hands off magazines in reloads (Josiah 03:58 "ghost animations"). Raw Tarkov FK already holds the mags.
- Variant: foregrips and grip-handguards (MP5K kurtz) use " 1" (wrap); plain forends (SV-98 chassis) use "" (cradle); " 1" on a forend bent the wrist 90 deg.
- web/holds.js exports per gun: {grip, guard, holds{meshBone:{palm[7] rel to part's LOD0 mesh bone, fingers{Digit:q}}}} into b1/holds/*.json; merged into local guns/tarkov_*.json (grip, guard, holds).
- Game: client/operator/GripHold.java (commit 3ffd453a8), called in OperatorView after the clip blend; LocalGuns.Spec has holds/grip/guard + Hold class. Checked against the viewer with a javac harness (/tmp/claude-0/jt): palm+fingers exact, elbow within 8 mm (no bend-goal pole in game).
- exp2.js now bakes RAW clips (cfg.grip true to bake holds); the game applies holds live.
- Guns with holds: 870 (RVG + FAB guard), AK-102 (RK-1 + B-10M), SVDS (SIG vertical), MDR (Stubby), MP5K (Kurtz guard), SV-98 (chassis forend). PM, Glock, VAL, MP-18 have none.
- KIT RULE (fixed 04:2x): every mod root q = FIX * its stored root q (old rule inverse(LOD0) broke the TA51 ACOG mount on the MDR = ACOG hidden + ADS flipped, the SV-98 chassis hold, the M12B stock; MP-18 barrel root stores -90X). Old b1 jsons backed up in b1/b1bak.
- CAMERA (Josiah 03:58 "guns too far"): game OperatorView draws the rig x/y-widened by tan35/tan25 (= Tarkov default FOV 50; MC hand FOV is 70) and ADS puts the eye ON mod_aim_camera (pack ads still bakes it 10 cm ahead; code adds EYE back). OperatorHands same widen. Previews: vid2 FOV 50, ads.js GAMEAIM = game's exact hold maths (rig turned about the aim point, sight centred). Commit 7fabf835c.

PREVIEWS prev6 (all 10, after the 03:58/04:04 fixes) = artifact HcDKWUhTAqykmA8GUPzxHN, mp4s in /mnt/project-files/zomboid-mod/tarkov/previews/batch1/; 7fabf835c CI green; awaiting his OK to merge into playtest.

Next: part visibility per fitted part + bench fitting for grips (fitted grip currently = spec.grip).

Related: [[tarkov-weapon-batch1]], [[tarkov-assets-pipeline-tech]].

---

### tarkov-sight-switch-tech.md

---
name: tarkov-sight-switch-tech
description: Josiah's 2026-11-07 rule that every switchable Tarkov sight (piggyback red dots, 1x/4x modes, variable zoom) works in game, looted ones too; how it is built
metadata:
  type: project
---
RULE (Josiah 2026-11-07 04:59/05:02): any sight that can switch (e.g. the MDR's ACOG with the RMR on top) must be a working part of the attachment, for EVERY sight with the option, including sights looted and fitted at the bench.

Built (commit 259c8db1e, branch claude/project-thread-5z3m2y): gun file `alt` list [{sight mesh bone, zoom, zooms, reticleMoa, aim:[x,y,z,qx,qy,qz,qw] = alt aim camera rel to main mod_aim_camera}] + `zooms` (Tarkov Zooms = modes). Pack textures reticle_N (pack_gun.py env ALT_RETICLES=a.png,b.png). OperatorView: comma = switch sight (rig slides eye point to eye point, 0.25 s), slash = sight mode; per gun id state; hip always main sight. Eye maths checked in a joml test.
Data: MDR alt RMR (aim from web/relaim.js), SV-98 alt RMR + zooms [5,25], MP-18 zooms [6.5,20] (local + local-pc gun files; MDR/SV-98 zsks repacked).
Per-sight table for the bench: tools/batch1/sights.json (81 scopes: scopes[] with zooms+modes from SPT ScopesCount/Zooms/ModesCount, variable flag, sightSlots mod_scope/mod_mount for piggybacks); 30 switch or carry another. BENCH WORK MUST build alt/zooms from the fitted parts using this table.
Related: [[tarkov-grip-holds-tech]], [[tarkov-weapon-batch1]].

---

### tarkov-melee-only.md

---
name: tarkov-melee-only
description: Josiah's 2026-11-07 10:37 rule that only Tarkov melee weapons exist (all Minecraft/Zomboid melee goes); Tarkov melee list and the tool-role question
metadata:
  type: project
  modified: 2026-11-07T10:41:36.148Z
---
RULE (Josiah 2026-11-07 10:37, relayed by coordinator, owned by the weapons thread "project-thread-5z3m2y"; LOOT does world loot): "We won't be able to use any minecraft or zomboid melee weapons, all of them will need to be tarkov ones". Start kit melee = Tarkov (Ka-Bar already, LocalHands starter flag).
Our melee items: ModItems lines ~123-147 (hammer, kitchen_knife, frying_pan, crowbar, pipe_wrench, lead_pipe, golf_club, hockey_stick, baseball_bat, spiked_bat, shovel, machete, tomahawk, katana, fire_axe, sledgehammer, spears, stone axe/hammer/knife, plank/cudgel nails). Ka-Bar and Kukri use kitchen_knife/machete as anchors, so those types must stay registered (retire stacks without a LocalHands id, like Retired does for guns).
Tarkov melee (SPT knife parent 5447e1d04bdc2dff2f8b4567, data /mnt/user-data/uploads/tarkov-pilot/spt on a session that staged it): Fulcrum bayonet, Kiba tomahawk, A-2607 x2, Antique axe, MPL-50 entrenching tool, 6Kh5, Miller M-2 sword, Crash axe, SP-8, Red Rebel, Camper axe, Freeman crowbar, Cultist knife, Taiga-1, DB 2020 dead blow hammer, scythe, Voodoo Hawk, PR-Taran, M48 Kukri, Gladius, Akula push dagger, Labrys (+Ka-Bar). Skip "Infectious strike" (zombie hands).
DECIDED 10:49 (Josiah, card): tools with a Tarkov twin (crowbar, axes, shovel=MPL-50, sledge=DB 2020, knives) are replaced by the Tarkov item for both fighting and jobs; hammer, frying pan, pipe wrench stay as job-only tools that cannot hit; pure weapons (bats, golf club, hockey stick, katana, lead pipe, spears, nail clubs, stone weapons) go. 62 melee bundles staged 10:50 (/mnt/user-data/uploads/raw, copy into scratch raw1).
BUILT 11:4x: 22 FP packs scratch /tmp/claude-0/tk/melee/pack (operator_tarkov_<g>.zsk), hands jsons tk/melee/local/hands (sounds "tarkov_kabar"), anchors: knives->kitchen_knife, sp8/taiga->machete, m2sword/gladius/scythe->katana, axes+labrys+rebel->fire_axe, kiba/voodoo->tomahawk, crowbar, mpl50->shovel, db2020->sledgehammer, taran->lead_pipe. db2020/scythe/labrys FP clips are blank stubs (Tarkov swings them with full-body SledgeHammer_* clips in character_animations); they use kiba's axe clips instead (heavy.py body->FP try parked). Preview https://claude.ai/artifact/TSFbioZPu27dYB4FXcspZW OK'd 11:39; all 22 zsk + hands jsons copied to profile npc/ + hands/ 11:4x (staged /mnt/project-files/zomboid-mod/tarkov/melee).
LOOT 11:22: Retired.TWIN_TOOLS (kitchen_knife, crowbar, shovel, fire_axe, sledgehammer) stay as can't-hit tools until a Tarkov blade anchored on them is in hands/ (playtest 7cd145968). LOOT owns TarkovMelee loot + rarity.
Related: [[tarkov-weapon-batch1]], [[tarkov-zomboid-scoping]].

---

### tarkov-ammo-counter-direction.md

---
name: tarkov-ammo-counter-direction
description: Josiah's ammo rule (2026-11-08): no magazine items; a hidden capped ammo counter per calibre, every reload puts in a full mag
metadata:
  type: feedback
  modified: 2026-11-08T08:53:14.260Z
---
Josiah 2026-11-08 08:46: "anytime you reload you should have a full mag loaded in", switch to a maximum carried amount instead of individual mags holding different counts. Card 08:53: he picked "Hidden ammo counter, capped" over loose round items: picked-up rounds go into a number per calibre (not inventory items), reload fills a full mag from it, HUD shows mag / carried. Cap proposed: 5 spare mags' worth per calibre, a sandbox setting.

**Why:** shooter-style flow; no juggling half-empty mags.
**How to apply:** old magazine items and loose rounds convert into the counter; loot gives rounds into it. Overrides Zomboid parity for ammo. HUD mock-up shown before the in-game build ([[zomboid-video-preview-rule]]). Related: [[tarkov-icons-scope-tech]].
- 08:53 Josiah: "a lot of the ammo building and other capabilities are going to go away as this game is now taking a different direction". Don't design around the reloading bench / ammo crafting; 08:53 he said "yes" to removing the reloading bench and ammo crafting as part of the ammo counter change.
- BUILT 2026-11-08 09:11 on branch claude/project-thread-5z3m2y (commit d30226011, CI green), NOT merged to playtest until Josiah OKs mock-up artifact NTrXMZu49FXqeqD1Heqzzr. combat/AmmoPouch (player attachment, per round item id; cap = guns.spareMagazines x biggest mag firing that round; sandbox zc.spareMagazines). Pickups via InventoryMixin.add, sweep every 10 ticks, Retired turns found mags into loose rounds. Bench, brass, powder, primers, heads, lead shot deleted outright (worldgen -> home_workbench). Survives the 09:28 purge re-steer ([[zomboid-purge-direction]]).
- 2026-11-09 07:57 Josiah: the mock-up is moot ("the hud for endless night is completely different"); card picked "Yes, mechanic only": AmmoPouch + full-mag reloads + bench/brass removal go to ENDLESS-NIGHT (re-apply d30226011 after its first green build, per SWITCH.md), WITHOUT the WeaponHud "20 / 100" counter (the new HUD reads AmmoPouch its own way). Not into old ZOMBOIDCRAFT.
- 2026-11-09 09:45 RE-APPLIED in ENDLESS-NIGHT: branch claude/project-thread-5z3m2y bea178d5d (CI green), merged to playtest 905cff5c4. Pickups via new mixin/InventoryAmmoMixin (old InventoryMixin/carry bags gone); WeaponHud untouched; SuppliesPage totals use AmmoPouch.total.

---

### zomboid-reload-rules.md

---
name: zomboid-reload-rules
description: Josiah's rules for every reload animation (2026-11-04): exact start/end, gun may move mid-reload, no finger clipping, flat slaps, full FP arm, right-hand handles
metadata:
  type: feedback
---
Josiah 2026-11-04 02:55-02:59 (reloads thread), for every reload:
- Start and end gun pose (and arm) must be EXACTLY his editor pose for first and third person; in between the gun may move however needed. For pulls at the back (slides, charging handles, bolts) push the gun forward and tip its back down to make room.
- Handgun grips: no finger collision during the reload (his static poses clip slightly; never change those, fix only inside the reload).
- Any slap that sends a round in (bolt catch, cover shut): open hand AND thumb flat, then back to the rest grip.
- First person shows the whole arm (upper arm too), never cut at the elbow.
- Guns with a cocking handle on the RIGHT (L86 type): left hand does the mag from the left hip, then plants back on its grip and the RIGHT hand pulls the handle (no left-arm crossover).
- He judged the natural whole-arm solver "100% better"; joints must move together, no unnatural bends or collisions.

**Why:** these are what make a reload read as natural to the player.
**How to apply:** check every new reload against this list before sending previews. Tech in [[zomboid-reloads-tech]].
- 03:05: single rounds loaded into the RIGHT side (Winchester 1866 type): left hand stays on the gun and rolls it so the loading face points up; right hand loads the rounds; then both hands back, gun back to its pose, right hand works the lever/slide.
- 03:06: always think where the ammo goes in and what parts are touched: turn the loading side of the gun toward the loading hand, and pick the right loading and holding hands for each gun.
- 03:22: Honey Badger type (charging handle on top/back): the right hand works it after the left hand has finished the mag. Fingers must never twitch.
- 03:24: as soon as a hand finishes its loading job and goes back to the gun, it must sit EXACTLY in his set grip (position and fingers) while the other hand cocks; no grip drift before the final frame.
- 03:27: first-person lever actions happen with the gun still on its side in the loading position, then it returns to the static pose.
- 05:42 (10 notes): slaps never bend a finger backwards; handgun thumb stays exactly in its static grip spot all reload; third-person STANDING pistol/revolver reloads at chest height (not waist); always aim the loading face at the loading hand (L86 bullpup: trigger hand holds gun out + tilts mag to the left hand so the arm doesn't cross the body; 870: right arm tilts and pushes the gun forward for the left hand); revolvers in FP keep the gun's static direction, swing cylinder out, nose up to dump, level to load, speedloader taken away; Big Boar: left hand lays the gun over (bolt/chamber face up) for the loading hand, stock never tilted away in FP, stock end down in TP; American-180: right hand pulls the handle after the pan; never twist the FP loading arm or invert the elbow.
- 06:12: revolvers (S&W type): the pistol-grip (right) hand stays on the grip the WHOLE reload; only the left hand works the reload. He wants the editor to give full control of hands, fingers and every arm joint so he can adjust.
- Editor v13 (06:2x): hand keys carry fk = {cl:[shrug,fwd] (TP only, FP rig has no clavicle), sh:[raise,fwd,twist], el, tw, wr:[bend,side], f:{th:[curl,spread,roll], i/m/r/p:[curl,spread]}} in degrees, applied by engine.js Rig.userPose after the solve (forward kinematics, the hand moves with the joint). Bake must honour fk when reloads go in-game. S&W 500 v6: R REST all reload, L does cylinder, speedloader from POUCH_L, puts it away, closes.
- 06:25-06:35: every hand key time gets a key on the other hand: {k:'mid'} keys (engine handTrack/handAt) keep the motion exact and only ADD his dpx/r/curl/flat/fk on top, fading to the neighbouring keys. Views sticky. Automatic finger unclip OFF for all reloads (spec.autoFingers to re-enable): it made fingers twitch whenever he moved a hand; fingers stay exactly as posed. Editor v14.
- 07:35 AR-57 ta "twisted arm": old (plain) solver flipped forearm twist/elbow side between frames. Fixed: plain solver now dry-runs, unwraps + smooths elbow side and forearm roll (ends exact); natural unwraps twist. His AR57 db doc v347: ta switched to solver natural, his sw values stripped there (backup brita/tools/reload-editor/AR57_PDW-db-v346-backup.json). Editor v15 has a per-view "Arm" Natural/Plain switch.
- 10:46: wave-2 rules + his edit habits: [[zomboid-reload-wave2]].

---

### zomboid-arms-posing-history.md

---
name: zomboid-arms-posing-history
description: Version history and Josiah's rules for the arms posing tool (v1 2026-11-02 to v40 2026-11-06), summarised
metadata:
  type: project
  modified: 2026-11-06T07:34:42.511Z
---

History for [[zomboid-arms-posing-tool]].

- v1-v4 (2026-11-02): built when he wanted to pose the FP arms himself. Online for phone use. Hip/ADS switch. Brita guns only (gzip+b64 Int16 tris, int8 clash field from brita/build_guns.py).
- v5 RULE: in hip fire the bore always runs through the crosshair, meeting view centre 2.7 m ahead; sliders are offsets. The game must do the same.
- v6: per-finger curl (Finger1 split into 4 groups by k-means; the game must match). v9: forearm twist.
- v7: ADS scope lens size (numbers.guns[id].ads.lens). v8: game FOV slider; hands stay at a fixed 70 deg (he does NOT want them to scale with FOV).
- v10: third-person modes tpready/tpaim on the female body, two-bone IK, real lengths.
- v13: light grey background, "Street behind" checkbox, red crosshair.
- v14 (11-03): templates: one pose per style carried to the others by parts; review grid (later hidden at his ask).
- Foregrips are posed relative to the grip; scope classes give one optic per magnification class per gun.
- v15/16: copy/paste per group.
- v17: ADS 0.0 is dead centre, with a one-time migrate.
- v34 (11-03): 27 wave-2 guns.
- v35-v37: poses looked reset because:
  - the cloud load came late;
  - the doc then hit the 256 KiB limit at 354 KB ('invalid_argument', nothing saved).
  - Fixed with a state-only doc, the T0 rule, the restore button and the wave-2 DEFAULT_FOLLOW. He confirmed saving works (3319).
- v38-v40 (2026-11-06): 88 wave-3 guns (two publish passes), compactState plus the 250000-byte guard, backup refreshed to 3474.
- HOLD 11-03 04:13: no Brita gun files to his PC or the game until he says ready.
- 380-gun plan: he will not hand-pose every gun; templates per class.

---

### josiah-world-art-direction.md

---
name: josiah-world-art-direction
description: Josiah's rules for how ZomboidCraft cities/buildings must look (battle-scarred, 3D depth, clutter), from his feedback
metadata:
  type: feedback
  modified: 2026-10-25T23:55:00.000Z
---

(2026-10-24, on the first photo-textured city preview) "This can't look like a fresh, brand new world that has zombies in it. It needs to look battle scarred, worn down and apocalyptic, little details matter." He said the walls looked "very flat and painted on" and the game would "feel like a bad render more than an actual professional job". He wants little accents like plants, debris and broken windows, and says to "use as many textures and assets as you need to obtain the rugged look of the reference images".

**Why:** the reference images (project files zomboid-mod/reference/art-direction-city.png, areas-concepts-a/b.png) are rugged, overgrown and ruined. Flat, uniform facades read as cheap.

**How to apply:**
- Every facade needs real 3D depth: recessed windows, raised frames, sills, ledges and cornices.
- Use texture variants (cracked, missing brick, soot, moss, stains), placed by noise so the same tile doesn't repeat.
- Add damage: holes, rebar, scorch marks. Most windows should be broken or boarded.
- Add overgrowth: hanging ivy, ledge moss, weeds, trees.
- Add street clutter: rubble, trash, paper, glass, puddles, fallen poles.
- Show close-ups before shipping. See [[josiah-preferences]] and [[zomboid-mod-status]].

(2026-10-25) Colour: "use a variety of colouring for the shop fronts and smaller buildings. The world should feel moody but the buildings can break it up a little." So keep the sky, streets and towers grey and wet, but give walk-ups and shopfronts faded, weathered colour (paint, brick, awnings, signs).

(2026-10-25) Light: he loved the cinema marquee's hanging pearlescent froglight. Under his Photon + Photonics it throws a lavender-pink glow onto the wet sidewalk in the rain. He said "we should make use of lighting like this more often and especially in the major cities". Hang these lights under awnings, canopies and entrances, and over doors, in towns and above all in downtown. Keep the count moderate, because each light costs Photonics frame time.

(2026-10-25 23:23, on the bunker core preview) "We will need to make these areas suit the world's moody aesthetic with lighting preferences and the like and roughing up the textures so they don't look as brand new or copy/pasted." Interiors (bunkers, garages, rooms) get the same rule: grime, wear and damage variants placed by noise, no identical repeating rooms, dim moody light (dead lamps, the odd red emergency glow), and previews shown in that dark light rather than flat preview light.

(2026-10-25 23:48) He liked the character thread's "cartoonish but believable, Borderlands-like" style (Soldier Player Body, https://claude.ai/artifact/Pp1bQP48f9LZxhCEhK3v2L): each part its own box, dark ink border, lit line under the top edge, lighter tops, darker undersides, flat bold colours with grime. Landscape take: fits props, furniture, trims and add-ons; big walls/floors get no per-block ink edges (grid look).
(2026-10-26 00:02, bodies thread, applies to props) No black lines where pieces join; one flowing piece, thin outline only on outer edges and attachments. tools/city/ink.py does this (rim_mask + JOINS). Sheet: previews/inked-props-test.jpg; rollout awaiting his call.
(2026-10-26 00:12, landscape thread only, NOT a general rule) He wants the prop outline even thinner. ink.py now paints at 8 px per 1/16 (P=8, line half the old width); atlases about 1.4x bigger. Sheet: previews/inked-props-thinner.jpg.
(2026-10-26 01:18) His game will have permanent rain, so glossy, wet-looking roads under Photon are fine; do not make road/pavement textures rougher.

---

### zomboid-building-detail-direction.md

---
name: zomboid-building-detail-direction
description: Josiah's 2026-10-25 detail directives: B42-level interiors and exteriors, custom furniture everywhere, no vanilla blocks, push toward realism in a Minecraft build style
metadata:
  type: feedback
---

(2026-10-25 13:14, thread "Build the ZomboidCraft landscape") Josiah asked for "very special care to the structures, not only externally but internally". Use 3D asset packs or block textures to replace the "basic look blocks". Interiors should be highly detailed and bigger if needed, with "unobstructed pathing". Exteriors should be "meticulously made" and "cinematic and terrifying", with "vast looting systems in every building (on theme)", taking inspiration from the latest Project Zomboid interiors.

**Why:** vanilla-looking stand-ins (barrel cupboards, cauldron toilets, stair sofas, black-terracotta TVs) break the feel. He wants the density of Zomboid B42 interiors, where every room is full and every container is searchable.

**How to apply:**
- Replace vanilla stand-ins with custom furniture blocks: detailed box models plus CC0 photo textures through the tools/city blocks.json pipeline. Every container (cupboard, fridge, wardrobe, desk, cabinet) is a searchable fitting.
- Loot follows Zomboid's room-and-container model: the room type and the container type pick the table, and stores stock on theme.
- Houses and large buildings can be bigger. Halls, doors and stairs must be clear to walk through, with furniture against the walls.
- Exteriors must be meticulous. Set a cinematic, terrifying mood with story rooms (barricades, bodies, fires).
- He okayed adding asset packs and textures. Mind the project-file storage limit; see [[zomboid-texture-library]].
- See [[josiah-world-art-direction]] and [[zomboid-landscape-direction]].

(2026-10-25 19:37) On the lived-in house previews: "They will make a great layout for the basic houses that we can use to fill in the suburbs." He also wants some mansions with a more "classy" interior and exterior design (later; not started). First: detailed exteriors for the basic house styles.

(2026-10-25 21:05) He approved the eight exterior examples (playtest 8083e3e): "those examples will look great when we mix it our textures and aesthetic". Treat them as the suburb base; later passes layer in more battle-scarred damage. Classy mansions still to come.

(2026-10-25 21:59) "Let's make sure we have plenty of detail too, I have no issue with not having any vanilla minecraft blocks so that we can really immerse the buildings, structures, bunkers etc. go all out". So:
- New structures (bunkers first) use custom blocks only.
- The existing generators use about 230 distinct vanilla ids. Iron bars, fences, barrels, planks, stone bricks and concrete are the most used.
- Swap those out for custom blocks over time, most visible first. Terrain (grass, dirt, water, trees) is a separate question.
(22:06) "Definitely don't shy away from building your own drawers, tables, various things to fill rooms and all that, or grab asset packs that will fit our aesthetic. The minecraft world can still be filled with more believable items." Make custom furniture and props freely, and use CC0 packs that fit.
(22:22) Josiah wants custom furniture, internals and detail "throughout the entire game": steer far from the basic block look, as close to realism as possible while keeping a Minecraft build style. He asked whether characters must stay Minecraft figures. I said no, and recommended realistic box-built bodies with hi-res skins and new animations rather than smooth meshes. 22:25 he chose a Zomboid-like look; see zomboid-characters-direction.md.

(2026-10-26 11:27, landscape thread) "once youve finished the aircraft carrier id like for you to focus on all the buildings of all types including city buildings, exterior and interior, cover everything". Plan doc: https://claude.ai/artifact/9DYoHXjFGEPZtwW4Tg4sef (about 60 types, state today, order). Proposed order: room planner + main street shops, town civic, downtown, roadside, suburbs (mansions, trailers), farm, military, port + cargo ship. Order card sent 11:50 (main street recommended). Building photos requested from the photo thread in that order (refs/town|city|roadside|houses|farm|military|port/<topic>/). Audit: shops/civic/offices/walk-ups sparse inside; Farmstead and Camp are the most vanilla.

---

### zomboid-landscape-direction.md

---
name: zomboid-landscape-direction
description: Josiah's rules for ZomboidCraft world generation (new landscape, many variants of every place type, match the concept sheets, finite urban county)
metadata:
  type: feedback
  modified: 2026-10-25T13:38:00.000Z
---

(2026-10-25, thread "Build the ZomboidCraft landscape") Josiah: "using the references youve got access to for the landscapes, buildings, locations etc, i need you to get stuck into building the rest of the world" and "we need multiple differing versions of each location, differing by size, function, style etc. not just one of each. spare no details and produce a result as close to the references youve been supplied as possible".

**Why:** the world must feel like a full new game, not one stamped template per place. References: project files zomboid-mod/reference/areas-concepts-a.png and -b.png (port, suburbs, military base / quarantine zone, small town, countryside farms, skyscraper interiors, downtown with elevated rail, hospital, police, subway).

**How to apply:**
- Every place type gets several seeded variants that differ in size, function and style.
- Detail level and look follow the concept sheets, [[josiah-world-art-direction]] and [[zomboid-building-detail-direction]].
- Start building rather than wait on a plan, but send previews (Artifact pages) as work lands. See [[zomboid-mod-status]], [[zomboid-playtest-build]].

**Finite map (12:31-12:43):** "make it a massive map like zomboid has thats fully discoverable but isnt infinite". He PICKED one fixed hand-planned master plan with procedural buildings inside, plus a world border ("huge amounts of immersion"). The county is ~16 x 16 km, ringed by the Exclusion Zone fence. Today's County.java is still infinite (one place per 512-block cell).

**Not Zomboid's map (13:16):** "we dont have to mimic the zomboid map, i still want all of the reference locations ... the port we will need a seaside city with docks ... make sure the land and sea are made good use of".

**Urban first (13:21):** "I'm pushing for the urban environment like big cities, big port and docks with massive cargo ships, crane and the like. There can be a forest area in a mountain scape but prioritise the urban feel". So: mostly metropolis, a megaport, one mountain forest area, small farms.

The plan drafts and how to read his edits: [[zomboid-county-plan]].

- Trees (Josiah 2026-10-26 09:39): he may buy low-poly tree packs and wants ALL trees replaced with them, redesigned to our style. Agreed approach (offered, awaiting the packs): originals stay out of the repo like the bought jets; each model is turned into chunky custom blocks (trunk, branch, leaf pieces finer than a block) so it stays blocky, fast and choppable. Asked him for Kentucky species (oak, maple, hickory, sycamore, pine, cedar, birch, dead/storm-broken, bushes), several sizes, bare/autumn variants, OBJ/FBX with textures, a game-use licence. Current trees: county/Trees.java (Kind OAK, MAPLE, SYCAMORE, PINE, DEAD, SICK, BUSH, YOUNG).

---

### zomboid-overnight-mandate.md

---
name: zomboid-overnight-mandate
description: Josiah's standing permission (2026-10-26 14:35) for the LANDSCAPE thread to keep building every aspect of the world, area after area, without asking
metadata:
  type: feedback
  modified: 2026-10-26T14:35:29.932Z
---

Josiah 2026-10-26 14:35, going to sleep (LANDSCAPE thread): "keep working on every aspect of the world and use all available assets and packs to do so inclusive of trees and landscape. when you finish one area move straight on to the next as i wont be able to tell you to do so. remember i want as much detail as you can make. id like all towns and roads to have roads that go between them. i will reactivate the photo thread so that you can have as many references as you need but i want you to keep working and i give you permission to do so until youre finished with the entire world and every one of its aspects".

He also asked if his PC must be on: no. Work runs in the cloud container and the jar builds on GitHub CI.

**Why:** he can't steer overnight and wants the whole world finished at maximum detail.

**How to apply:**
- Never stop to ask between areas. Pick the next area yourself and ship each one to the playtest jar ([[zomboid-playtest-build]]).
- Use every asset already in project files, including the Poly Haven CC0 props and nature models ([[zomboid-asset-intake]]). Still no paid packs, and originals stay out of the repo ([[zomboid-bought-assets-plan]]).
- Roads must join every town to the others ([[zomboid-county-plan]], [[zomboid-world-tech]]).
- The photo thread is back on for buildings ([[zomboid-photo-reference-rule]], [[zomboid-refs-collection]]).
- Coloured lighting (14:36): "make sure to remember the important part that good coloured lighting plays in this world too". Every area gets its own coloured light sources (warm lamps, red night lights, neon, signs), lit pools in the dark. Photon colours light by block id, see [[photon-shader-settings]] and [[zomboid-weather-direction]].
- Queue as of 14:35: carrier officer spaces, roads between towns, trees and landscape, town civic, downtown, roadside, suburbs, farm, military, port + cargo ship. Building batch plan: https://claude.ai/artifact/9DYoHXjFGEPZtwW4Tg4sef

---

### zomboid-landscape-pause-after-list.md

---
name: zomboid-landscape-pause-after-list
description: LANDSCAPE pause history: 2026-10-29 pause (lifted 18:59) and the 2026-10-31 10:22 "stop development for now" pause with what was left unmerged
metadata:
  type: project
  modified: 2026-10-29T12:25:06.054Z
---

On 2026-10-29 at 12:20, Josiah told the LANDSCAPE thread: "finish all the items you have listed above to the fullest extent and then pause development upon completion of them".

The list covered:
- Town leftovers: boxcars, depot, street furniture.
- Landmark and suburb leftovers: mill, cellars, military site.
- Big ship hulls.
- Park lamps and the gazebo.
- Downed power poles.
- Ladders.

**Why:** He wants the consistent-detail pass closed out, then usage freed up.

**How to apply:** After the last item ships in a playtest jar, stop LANDSCAPE work. Don't start new items. Don't resolve the thread ([[zomboid-no-self-resolve]]).

**Lifted 2026-10-29 18:59:** Josiah: "Resume work and begin the airport". LANDSCAPE is active again on Grayhaven International Airport ([[zomboid-airport-tech]]).

**Paused again 2026-10-31 10:22:** Josiah: "stop development for now". State at the stop:
- Road stories wave 1, house alarms and the yard hum/arc were merged to playtest (48bf4147). CI wasn't confirmed.
- Substation repair (his pick "fix parts round the yard", commit 7ee9b42b) is on branch claude/project-thread-d61kbn only and is NOT in playtest.
- Queued for when he resumes: world stories wave 2, WEAPONS' reloading_bench placement, LOOT's crusader_kit crate + museum_armour placements.

**Resumed 2026-10-31 20:27:** Josiah: "You may resume". LANDSCAPE picks up the queued list above.

**2026-11-06 02:57:** Josiah: "Just keep pushing through all the work, I won't be at my pc for a while". Keep building back to back without asking. Harbour fort waits on PC photos (pc_spec45); meanwhile zone stories (RZS) from refs/stories/zone.

**2026-11-06 04:19:** Josiah: "Anything that requires more photos just push it aside for now". Harbour fort parked. Next (photo-free): county roof pass (Bldg.gable vanilla stairs -> house roof kit, 14 builders), leftover vanilla blocks in structures (froglights, wool, deepslate tile, copper), farm tractor/wagon remake.
**2026-11-06 04:47:** vanilla cleanup done (e0083040d, playtest 17b410028). The roof pass was already done via Bldg.kitRoof, and the tractor and wagon were already .zcar models. Next: Zomboid's non-house building stories (RB bar/cafe/clinic/gun store siege/office/school/burnt/looted/trashed) for shops and civic buildings.

---

### zomboid-gun-art-style.md

---
name: zomboid-gun-art-style
description: RULE + tech: every ZomboidCraft gun and attachment is painted in the character (Borderlands-like) style by tools/weapons/toon.py, always like new
metadata:
  type: project
  modified: 2026-10-26T01:48:05.087Z
---

**RULE (Josiah 2026-10-26 00:40):** "I want all guns and attachments made with this same design aesthetic (even wood grain or differing coloured ones). I prefer the black mag." **01:56 he reversed wear-by-condition: "I'd actually prefer all the weapons in their like new form at all times, inclusive of their attachments."** Approved concept: https://claude.ai/artifact/5QwwMvHvoMPKSSn7syQLCf. Every new gun, attachment, finish and camo must go through the same painter. Follows [[zomboid-clothing-style]]: no lines where a part's blocks join, thin outline only round each part's outside and each attachment.

**Why:** weapons must match the characters and gear. Condition shows in the tooltip, not the paint.

**How it's built (branch `restyle`, merged into playtest 2026-10-26 01:47):**
- tools/weapons/toon.py, called from kit.paint() for every face (guns, part icons, ammo). Pieces = touching boxes of one material family (union-find, FAMILY folds 'mark' into 'anod' etc). cover() probes past each edge (DEPTH 0.03 sideways, DEPTH and STEP 0.08 under the face) for a same-piece box: hit = joint, no ink/bevel/edge wear. Rod long edges are joints. step_face(): faces >50% buried in a same-piece box (slice risers) get nothing, or sliced stocks get stripes. outer edge distance replaces Face.edge, so the materials' own edge wear follows it too.
- style(): lit tops +6%, undersides -15%, near-black lifted to charcoal (LIFT 0.55 below lum 0.3), chips as clustered flecks just inside the outline (per-face quantile, CHIPS 5%+25%*wear, none on faces under 0.3 units), ink width ~1-1.6 texels, none on faces under 4 texels; ink is matt in _s. pbr.normal takes `cover` (no bevel at joints). paint(cache=) shares joint maps between skins.
- kit.WEARS is one skin now, [('', LIKE_NEW 0.12, 0)]; by_wear() returns the plain model when there's one. The machinery stays: adding ('worn', 0.55, 0.4), ('battered', 1.0, 0.65) back makes textures item/weapon/{gun}[_{fin}][_{wear}], child models {base}/{skin}/... and a range_dispatch on minecraft:damage (RifleView.drawPiece would then need to copy DAMAGE/MAX_DAMAGE again; it was reverted). Never delete the melee *_worn textures: those are separate melee items.
- Look `_after` grime/scratch lambdas must read f.wear (not a closure) so skins differ. Rifle mag is black ('#202124'), laser body is 'poly' (black).
- Revolver (01:56 ask, reshaped 02:40): square butt with 45-degree cut corners (_end), stocks over the frame with a thicker core, guard polyline sweeping into the front strap, hammer spur under the sight line. sil.py side silhouettes in the weapons scratchpad are a fast check.
- Check renders: scratchpad armoury/ (prep.py packs game models + webp skins, template.html three.js gallery, shots.js Playwright). Gallery: see [[zomboid-weapons-direction]].
- Lesson (M9, 02:34): Model.seg snaps every lean to 22.5 degrees about the bar's middle, and a bar's ends are square to it, so a leaned grip leaves a notch under the frame and level parts (well lip, guard bow) miss it. pistol.grip_axes() gives the grip as drawn; build joins from that and check side silhouettes (scratchpad sil/sil.py <out> right <module>).
- Triggers RULE (Josiah 03:08): every trigger sits in the BACK part of its guard, straight down from the frame then its tip curving FORWARD slightly (two segs: vertical, then 22.5 degrees forward). New guns follow it.
- Sight-line RULE (2026-10-26, carbine): nothing between the eye and the rear sight may rise over the sight line. The lever-action's cocked hammer spur stood 0.5" over it 6" from the eye and Josiah saw a "big vertical tab" on ADS; hammers now lie back under the line (revolver too). Check every new gun's hammer, bolt handle and rear tang against SIGHT.

---

### zomboid-clothing-style.md

---
name: zomboid-clothing-style
description: Josiah's standing art direction (2026-10-25) for ZomboidCraft clothing and accessories: chunky 3D blocks, Borderlands-like cartoonish but believable
metadata:
  type: feedback
  modified: 2026-10-25T23:45:31.742Z
---

Josiah 2026-10-25 23:44: "I very much like that style ... for the 3d parts, almost a cartoonish but believable design, somewhat like a 'borderlands' game artwork feel. I want you to set that as the new design preference for all clothing, including clothing attachments like helmet, watches etc. also can you do the same for all the other enemies."

**What the style is** (from the soldier preview https://claude.ai/artifact/Pp1bQP48f9LZxhCEhK3v2L):
- Every garment and accessory is its own 3D block over the body, not paint on the skin: shirt/sleeve shells, trouser shells, pockets with flaps and buttons, cuffs, collars, belts, buckles, straps, boots built from sole + lugs + toe cap + heel + upper + laces.
- Each block has its own flat colour or pattern (camo, plaid...), a THIN (1px) dark outline on outer edges only, a 1px light line under the top edge, a brighter top face and darker underside, stitching just inside the edge on fabric, metal parts in steel grey, and grime toward the bottom. Blood follows the zombie's damage level.
- Only hands, bare skin and the face come from the skin texture.
- The body is the mockup-1 "player" body: slim, tapered torso, separate thigh/shin/foot and upper/fore/hand, shoulders flush with the torso top, no neck, no face blocks, no thumbs.

**How to apply:** use this for every outfit, every enemy type, and the player's clothes. Code lives in the preview tools (scratchpad player/skinned.js gear()), to be ported to Java models. Related: [[zomboid-characters-direction]], [[josiah-world-art-direction]].

**All-outfit preview (2026-10-25):** https://claude.ai/artifact/Mnik6r9hRkYoARfsLTdBxU shows all 15 zombie types in kits (kits.js in the scratchpad: shared shirt/pants/boots/sneakers/hair/hat/watch/glove builders, then KITS per outfit). Not built into the mod; waiting for Josiah's feedback and go.

**Josiah 2026-10-26 00:02:** no black lines where garment pieces join (e.g. sleeve to forearm, thigh to shin, chest to waist). Clothing must flow as one piece; thin outline only on outer edges, cuffs, attachments. Implemented via gear() `join: 't'/'b'` (suppresses edge line, stitching and grime band at that edge) and one-box shoulder-to-elbow sleeves.

**Josiah 2026-10-26 00:10:** no 3D hair (removed; heads use the skin). Kits must be roughed up to match the aesthetic: dirty, torn, bloody, zombie damage; clean 3D items looked out of place. Done in gear(): wear wash, mud, stains, holes cut through fabric, claw rakes, ragged hems, heavier blood; metal gets scratches and rust; hanging torn strips (tatters) and ripped-off sleeves. Wear scales with damage (KIT.wear 0.4/0.7/1.0).

**Josiah 2026-10-26 00:44:** damage must be 3D layers, not painted discolouration/blood. Rebuilt (scratchpad damage.js): garment shells = 4 panels of cells (cloth/faded/stained/blood-soaked/hole) merged into blocks, real holes, frayed thread blocks, peeled flaps, raised blood clots + drips, caked mud blocks on footwear, askew headgear; colours themselves faded. Painted overlays off (KIT.paintFx=false). Java port note: ~450-870 boxes per outfit in preview; in-game needs per-zombie baked variants.

**Josiah 2026-10-26 01:01:** "still too clean and bright, doesn't match the moody aesthetic". Added grade(): every colour desaturated ~50-60% and darkened toward sooty brown #1f1b15 (hi-vis orange ends ~#7a553a), highlights cut to a third, skin tinted darker, preview lit overcast-cold + dim sodium lamp on dark wet asphalt. Default for all character art: muted, dark, never bright.

**01:06:** Josiah: current darkness level is right ("not too dark, that's a better take"). Asked for a Photon example: added a "Photon-style" lighting mode to the kits preview (ACES, exposure 0.82, saturation .8/contrast 1.1, blue-grey exp fog, rain, wet road + puddles, sodium lamp, hanging froglight, additive glow sprites). It is a browser approximation; a real look needs it in game.

**01:24:** Preview now runs the in-game walker anims (scratchpad zanim.js). Josiah: shamble looked like a moonwalk; fixed knee phase (knee bends while leg swings forward: shin = max(0, ±sin(step)), body lowest with legs spread). He approved ("much better"). The shipped WalkerModel still has the old cos(step) knee timing; offered to fix in playtest, no answer yet.

---

### endless-night-switch.md

---
name: endless-night-switch
description: Josiah 2026-11-09 07:51 STRICT: all Reach/Halo/Tarkov/zombie work moves to Endless Night repo, file store and Modrinth instance
metadata:
  type: feedback
---
Josiah 2026-11-09 07:51/07:52: immediately switch; ANY future Reach-world work (incl. Halo, Cortana, Tarkov, zombies) uses the new routes only.
- Instance: Modrinth profile ENDLESS NIGHT; never write to MEDIEVAL GLOOM (1) (frozen county world).
- Project files: /mnt/project-files/endless-night/<area>/ (Cortana: endless-night/cortana/). Old zomboid-mod/ stays readable.
- PC big assets stay in C:\Dev\Zomboidcraft-Desktop\additions\ (never OneDrive).
- Repo josiahlongfield-jpg/ENDLESS-NIGHT (mod id endlessnight); ZOMBOIDCRAFT frozen. Wait for the repo thread's first green build before committing there.
**Why:** Endless Night split from the county world. **How to apply:** rules in /mnt/project-files/endless-night/SWITCH.md.
- 08:27 2026-11-09 Josiah: ALL threads hold off pushing anything until the 'Move Endless Night to its own repo' thread has finished and prepped everything; keep work local/patches.
- 09:33 2026-11-09 push hold LIFTED: ENDLESS-NIGHT repo ready (branch off main, merge into playtest; mod id endlessnight, com.endlessnight.*, config/endlessnight/, commands /en /enhud). County world, cars, loot, survival needs, skills, aircraft, trains, companions, Harvestman, command net removed.

---

### endless-night-voice-accents.md

---
name: endless-night-voice-accents
description: Josiah's rule on AI voice accents for any voice samples or picks (never Indian accents)
metadata:
  type: feedback
  modified: 2026-11-10T11:01:15.883Z
---
NEVER offer or use an Indian-accent voice, in any voice samples or picks (Josiah 2026-11-10 11:01: "no indian, never indian").
**Why:** his explicit, standing preference.
**How to apply:** leave Indian-accent speakers out of every voice line-up (e.g. VCTK p248, p251, p376, and any en_IN model). Russian-accented women were asked for in the same round. Related: [[endless-night-cortana-ingame]].

---

### zomboid-access-rule.md

---
name: zomboid-access-rule
description: RULE (Josiah 2026-10-27): every stairwell, doorway and roof must be walkable by players and zombies; roof access huts on buildings with stairs
metadata:
  type: feedback
  modified: 2026-10-27T23:40:00.000Z
---

Josiah 2026-10-27 03:51: most stairwells could not be used. Block heights (half-height
furniture/fit-out blocks, ceilings) and collision stopped players and zombies, and no building
had roof access.

RULE for every building, structure, bunker and ship, old and new:
- Stairwells at least 2 wide where possible and full headroom: 4 CLEAR blocks over every
  tread (Josiah 2026-10-27 22:59: no head collisions on stairs; a jump on a stair reaches ~4.05
  over the tread block, so 3 clear bumps). Includes roofs, ceilings, beams AND the flight above:
  never stack flights one storey (4) apart.
- Entrances bigger by default (2-wide doors where the facade allows), no collision lips.
- Every building with stairs gets a stair up to the roof ending in a small roof hut sealed
  by a door, so players and zombies can walk onto roofs.
- Nothing at head height over any walkway: awnings, canopies, signs and lamps leave 2 full
  blocks clear (a 04:07 screenshot: a sagging blue awning fold at 1.75 trapped players and zombies).
- Every interior and exterior doorway walkable from both sides.
- Check new builds with the offline walk checker before shipping (see [[zomboid-world-tech]]).

**Why:** he found stairwells unusable in game; zombies must path through buildings too.
**How to apply:** audit every builder that places stairs; never ship a stair without the
headroom check passing.

HATCH RULE (Josiah 04:24): a trapdoor/hatch over a ladder hinges on the wall OPPOSITE the
ladder (trapdoor facing = opposite of the ladder's facing), so the open leaf never blocks the
climb out. Builders.fitHatches enforces it for every place; vanilla trapdoors over ladders
become bunk_hatch_wood (climbable). Roofs: RoofHut.build (county) puts the hut over a stair's
head; Flats.stairwell(hutWall) and Flats.porches(toRoof) already use it.

ORIENTATION RULE (Josiah 2026-10-27 04:14, screenshot of a gallery rail): every linear piece
(rails, banisters, bar rails, fences, windows, panes, kerbs) must run along its line: a row of
them is turned to the row's direction and joins up, never a row of stubs turned across it (the
same bug as windows turned the wrong way). tools/county/orientcheck.py DUMP lists offenders;
run it on every build before shipping.

---

### zomboid-basements-bunkers-direction.md

---
name: zomboid-basements-bunkers-direction
description: Josiah's 2026-10-25 rare house basements template (shipped in playtest 003e25d); bunkers live in zomboid-bunkers-direction
metadata:
  type: feedback
  modified: 2026-10-25T21:25:46.993Z
---

(2026-10-25, thread "Build the ZomboidCraft landscape".)

**Basements**

Josiah (21:07): "I'd also like basements to be a thing in house, mansions etc. make them a rarity but have them be varying in size and what they were used for. Weapons and ammo stocking with be the rarest, food and medicine the most common (still rare overall)."

At 21:24 he approved the posted template ("I like that template, yeah").
- Odds: about 1 in 10 suburban houses, 1 in 7 farmhouses and colonials, 2 in 5 mansions, never trailers.
- Sizes: small cellar, half the house, or the full footprint.
- Access: a stair under the main staircase, or a trapdoor and ladder in single-storey houses.
- Uses, most to least common: food, medicine, laundry and furnace, workshop, rec room or wine cellar, prepper armoury.
- Condition layers: boarded (survivors), ruin (flooded), looted.

**Bunkers** moved to [[zomboid-bunkers-direction]] (urban-first since 21:58).

**How to apply:** basements shipped 2026-10-25 21:47. He likes to discuss options first. Tie into [[zomboid-loot-rarity-direction]]. Tech: [[zomboid-houses-tech]], [[zomboid-world-tech]].

---

### zomboid-bunkers-direction.md

---
name: zomboid-bunkers-direction
description: Josiah's hidden-bunker direction for ZomboidCraft: mostly urban (skyscrapers, cargo ships, docks), rare map drops from hard places, rolled loot, approved choices
metadata:
  type: feedback
  modified: 2026-10-25T22:01:22.877Z
---

(2026-10-25, thread "Build the ZomboidCraft landscape". Plan doc: https://claude.ai/artifact/DxX1cErgwr3AvxeE9HLS6k)

Josiah's asks, in order:
- 21:24: hidden bunkers, hard to find. A rare map drop leads to a general area; an entrance leads down into a big bunker of rare items and weapons.
- 21:28: the map drops only "in a place that's very hard to access and clear out to loot". Bunkers hold "some of the rarest guns in the game but not all", with a rolled tier and roll of which guns. "A good bunker find could lead to the lesser of the best guns and equipment."
- 21:50 (via the weapons thread): loot never scales with distance from spawn; no real names or franchise references ([[zomboid-loot-rarity-direction]]).
- 21:58: lean urban: "under skyscrapers in the city, inside the hull of massive cargo ships (some out in the water) ... get creative"; forest/county kinds stay but are the minority.
- 21:59: plenty of detail and no vanilla Minecraft blocks ([[zomboid-building-detail-direction]]).

He approved all four of my recommended choices (21:58):
- the blast door is pried with a crowbar, and the noise draws zombies;
- a dead generator can be restarted with fuel;
- more than one kind of bunker (now many urban kinds);
- a duplicate map can lead to a bunker already looted.

**How to apply:**
- Most bunkers go in the metropolis and waterfront: skyscraper sub-levels, cargo ship holds (docked and offshore), dock container yards, subway, civil-defence shelters and the like. Forest, mountain and farm kinds are the minority.
- Every bunker is built from custom blocks only.
- Discuss the plan with him before building. Basements (shipped) are in [[zomboid-basements-bunkers-direction]]. Tech: [[zomboid-houses-tech]], [[zomboid-world-tech]]. Map: [[zomboid-county-plan]].

**Plan v2** (doc rev 12): city ~6/10 (skyscraper, missing floor, civil-defence shelter, ghost subway, bank vault), docks ~3/10 (quay ship, offshore ship, container tunnel, harbour fort), country ~1/10 (forest hatch, mine adit, silo). ~24 bunkers, 18 on maps.
- APPROVED 22:20: wave 1 = skyscraper, quay ship, civil-defence shelter, forest hatch. Offshore ships: swim and climb the pilot ladder now, boat later with cars.
- Kit code: tools/city/bunker.py (prefixes bunk_, ship_, container_inside/floor/welded).
- 2026-10-25 ~22:45: kit built (132 blocks), preview sheet sent, merged to playtest 66e8b38. Next: shared core, then wave one, each previewed. Tech: [[zomboid-bunker-kit-tech]].

(2026-10-25 23:30) Broken-into (breached) bunkers: busted lights that flicker, fixtures hanging or fallen from the ceiling, damage like that. "But not completely torn up, we still want recognisable, lootable interiors": every room keeps its furniture and containers; damage is dressing on top.

(2026-10-26) Cargo ships scaled massive (~280x42, all custom blocks); quay ship shipped ae07dd2, tech [[zomboid-ship-tech]]; carrier followed ([[zomboid-carrier-direction]]).
Status 2026-10-26 07:30: wave 1 done (tower, quay ship, civil-defence shelter, forest hatch) plus marked maps from soldiers and base/naval containers, playtest ac54502. Tech: [[zomboid-bunker-wave1-tech]]. Next per doc: bunker loot tables, dead inside/generator, wave 2 (crowbar door built, see tech).

(2026-10-31 22:07) REVIEW PASS PICKED (decision card): bring the 5 built bunkers (tower, quay ship, shelter, forest hatch, army keypad) up to today's rules (own layout per kind, 3D pipes/conduit, clutter, light switches, damage by state, photo-led), before/after shots per bunker, tower first; THEN wave 2 kinds (subway, vault, offshore ship, container tunnel, harbour fort, mine, silo, missing floor). REFERENCES asked for 13 bunker folders.

---

### zomboid-carrier-direction.md

---
name: zomboid-carrier-direction
description: Josiah's aircraft-carrier ask for ZomboidCraft (true-scale carrier at its own naval wharf, true-size F-22/F-15-style jets, military interior and zombies)
metadata:
  type: feedback
  modified: 2026-10-26T07:00:52.205Z
---

Josiah 2026-10-26 02:57, landscape thread, after liking the cargo ships: "I'd like one of the ships to be changed into a massive military plane carrier ship that has F-22 raptors and F-15 Eagles on its runway. If needs be, make its own wharf as I want this to be very sizeable because the jets on the ships runway should be properly scaled also to look as though the player could fly them."
02:57: "The interior can have military zombies, barracks, gun rooms and all that, lots of detail. The exterior can have various weaponry as well (visual only)."

**Why:** he wants it imposing and believable at player scale, and jets that could later be flown.

**Status 2026-10-26 ~05:00:** built and merged to playtest (carrier, wharf, two jet models, soldier zombies). Tech: [[zomboid-carrier-tech]]. Flying the jets is a later feature.

**Bought jets (06:27-07:07):** he'll buy low-poly models to replace Corvid/Gyrfalcon. Candidate: CGTrader F-22 (https://www.cgtrader.com/3d-models/aircraft/military-aircraft/lockheed-martin-f-22-raptor-73212c3c-8419-4d38-81b5-443f1ca23646), Royalty Free (remix/commercial ok, no file redistribution, no AI training): I said it works; upload zip (GLB/FBX + textures), F-15 too. If it lacks a cockpit he may buy a cockpit model for me to merge in; or I model a simple one. On arrival: originals only in project files (never the public repo, [[zomboid-cars-plan]]), wreck.py/pack.py kind "jet" + floors, made-up names/markings, CREDITS.md. Not uploaded yet.
Uploaded 07:25 as /mnt/project-files/jets.zip: F-22 (OBJ, 12.5k tris, units x4.1 = metres), Apache AH-64D (OBJ, no .mtl, 4 PBR textures, metres), Su-27 "with interior" (SketchUp OBJ, 545k tris, metres). All three have cockpits. **Decided 07:49:** the F-22 replaces Corvid, the Su-27 replaces the F-15-style Gyrfalcon, and one Apache sits on a marked helipad on the carrier deck. Army bases unchanged.

**How to apply (plan posted 03:03, no objection yet):**
- True supercarrier size: about 333 x 77 flight deck, deck ~20 above water, island ~25 above the deck, on its own naval wharf (PORT variant "naval" or its own place) with fence, gate, cranes, tugs.
- Jets at 1:1 (about 19 long, 14 span, 5 tall): real 3D models like the cars (first pass built from scratch; bought low-poly models from Josiah will replace them), parked as props on deck and in the hangar; flying is a later feature.
- In game no real names (rule from [[zomboid-loot-rarity-direction]]): the jets look like the F-22 and F-15 but carry made-up names; the carrier gets a fictional name and hull number. Told him real carriers can't fly those jets; 03:05 he said he knows (F-35/F-18 are the carrier jets) but prefers the F-22 and F-15. Do not raise it again.
- Deck: catapults, arresting wires, elevators, tractors; visual-only weapons (CIWS-style guns, missile launchers, MG posts).
- Inside: hangar bay, barracks, armouries with rolled loot, mess, galley, sick bay, magazines, engine rooms, island bridge and flight control. Military zombies (soldier/sailor outfits) aboard.
- All custom blocks, lots of detail ([[zomboid-building-detail-direction]]). Previews standard + Photon rain before the jar.

Related: [[zomboid-ship-tech]], [[zomboid-cars-plan]], [[zomboid-bunkers-direction]].

**Detail pass asked (2026-10-26 08:36, after visiting both in game):** "they need a hell of a lot more detail. i like the bones, but the internals need way more detail. id like you to look up images of aircraft carrier external, internal rooms and rebuild the internals to suit that as well as cargo ship internal and external. will need to add lighting systems as well". Keep the hull shells and layout bones; rebuild carrier and cargo-ship interiors (and add exterior detail) from real reference rooms, with proper lighting (fixtures, night red, emergency lanterns, floods). Commons/most image hosts are blocked from the cloud container, so references come from text research.

**Power (decided 2026-10-26 08:54):** ships board with FAILING power: main lights dead or flickering, red night lights and battery battle lanterns lit, sick bay and engine control on an emergency generator. A generator you start yourself was the rejected/later option. Plan doc: https://claude.ai/artifact/F8eWMK5bzijxZNxbf7btLi (order: shared kit + lights, carrier passageways + hangar, mess/berthing, island/gallery/magazines/machinery, carrier exterior, cargo ship).

---

### zomboid-characters-direction.md

---
name: zomboid-characters-direction
description: Josiah's 2026-10-25 direction for ZomboidCraft zombie and player models: Project Zomboid-style mitten hands, pronounced features, not overly realistic
metadata:
  type: feedback
---

(2026-10-25 22:22-22:25, thread "Build the ZomboidCraft landscape")

He asked whether characters must stay Minecraft figures. I said no: Minecraft builds characters from boxes, but we choose the boxes, skins and animations.

Josiah (22:25): "I wouldn't want the enemies and characters to look overly realistic, I'd be happy moving them closer to a zonboid design, with the hand that has no discernible fingers but features are a lot more pronounced."

**How to apply:**
- Use Zomboid proportions: longer torso, narrower limbs, defined shoulders.
- Hands are mittens: one block plus a thumb.
- Faces get pronounced 3D features (brow, nose, jaw, ears) and a hi-res painted skin.
- Clothes and gear are separate layers.
- Add wound and damage states.
- Animations: shuffle, lurch, reach, crawl.
- The player shares the same body.
- Show mockups first.

I asked the coordinator for a separate thread for this. Related: [[zomboid-building-detail-direction]], [[minecraft-zomboid-mod]].

**2026-10-25 22:40 (thread "Redesign zombie and player bodies"):** Josiah approved building this plan (via coordinator, 22:26). Mockup 1: https://claude.ai/artifact/3QvbfWqfsgb4mJh2LKWkeX (Today vs A "Zomboid" proportions vs B "Pronounced" big head/brow/nose/mitts vs player; elbows+knees, thumb that closes, torn clothing shell, first-person arms with crowbar). Asked him: A, B or A body + B face (my pick); movement feel; whether player arms replace the gun first-person arms. Building on A body + B face meanwhile. Source: scratchpad only (bodies/index.html), cdnjs is blocked for local screenshots here.

**2026-10-25 22:53 Josiah:** remove the thumbs; KEEP today's skins (he likes their detail) and add the joints (knees, elbows) and the slimmer look. Built on branch claude/project-thread-wfvzb7: WalkerModel splits limbs (ELBOW 5, WRIST 4, KNEE 6) sampling the same player-layout skin rows, limb xz scale 0.8/0.86, head 0.9, optional face blocks; gen_walker_addons.py moves pieces below a joint onto right_forearm/right_shin etc. Soldier preview https://claude.ai/artifact/R4k3dc38HqkgGSkZRomZAS. Asked: keep raised face blocks or flat painted face? Playtest got the thumb version at 175e050 (not sent to him). Player body/arms touch the weapons thread's PlayerModelMixin/HoldPoses/RifleArms: coordinate before changing.

**2026-10-25 22:58 Josiah (final direction so far):** NO face blocks, NO thumbs. Keep today's skins. He wants the slim shape and fluid walk of mockup 1's "player"/body A (it "adds a little believability to the original design"). Implemented: arms xz 0.68 y 1.1 at shoulder x 4.4, legs xz 0.85 longer (hip at 11.04), torso 0.9 wide 0.92 tall, head 0.8 on a 1.2 neck, deeper knee/elbow bends, body dip per step; still 2 blocks tall. Soldier preview v2 same link.
Shipped slim jointed walkers in playtest 208c987 (CI green, jar link sent 23:0x). Next: his in-game feedback, then player body/arms.

**23:11 Josiah:** wants the EXACT mockup-1 'player' model (body A geometry: tapered chest/waist, pelvis, deltoid caps, separate thigh/shin/foot, upper/fore/hand) and its exact walk joint movement, with NO neck (head on body), no face blocks, no thumbs, plain basic skin. Said 'preview first, don't build yet'. Preview: https://claude.ai/artifact/1Hk8jvVcqbsKs4GTSZsbkk (source scratchpad player/make.py from bodies/index.html). Building it in-game means new geometry that the current skins don't map onto 1:1; raise that when he says go.

**23:16 Josiah:** shoulders dropped flush with torso top, arms shortened slightly (upper 5.7, fore 5.1) — preview v2 same link. He asked if players lose their own skins: told him no, each block samples the matching region of a normal 64x64 skin (like the walkers), wide skins squeezed slightly. Still preview only, waiting for go.

**23:20:** soldier skin on the mockup player body (body A v2, skin sampled by vertical slices of each vanilla part; head+body addon pieces scaled in, limb pieces skipped): https://claude.ai/artifact/Pp1bQP48f9LZxhCEhK3v2L (scratchpad player/skinned.js). Preview only, waiting for go.

**23:29 Josiah:** old addon pieces on the new body 'blend into one, no definition'; wants our own detailed 3D gear per outfit, as detailed as possible. Built a soldier kit (helmet+cover+band+chin strap, flak vest+collar+flaps+zip, suspenders, pistol belt+buckle+ammo pouches+canteen+first aid, laced jungle boots) with hard dark edges, lit tops, stitching; preview v3 same soldier link (scratchpad player/skinned.js soldierKit, build_soldier.py, harness.js = node mock of THREE to catch load errors since cdnjs is blocked here). Preview only.

**23:35 Josiah:** likes the 3D gear style; asked to redo the clothing and boots the same way. v4: BDU woodland shirt (rolled sleeves, sleeve pockets, cuffs), trousers (cargo pockets+flaps, knee patches, bloused cuffs), detailed jungle boots, all 3D gear() blocks; only hands/forearms/face from skin. Direction implied: outfits become fully 3D-built kits on the new body. Preview only.

**2026-10-26 01:25:** Josiah asked for the most detailed painted body skin (not 3D add-ons), moody, free to drop the current face design. Built a procedural painter (scratchpad player/paintskin.js, 1024x1024 standard 64 layout): comic-inked, mottled, cel-shaded, zombie (milky eyes, dark eye mask, torn cheek/teeth, veins, lividity, bites, bone at higher decay) and survivor variants, 3 tones, 3 decay levels. Preview: https://claude.ai/artifact/8zHx2mMR516xaBf9e7fAoL. Local check trick: Chromium + playwright can render the 2D skin canvas (sheet_test.html, shot.js) even though three.js can't load offline.
**01:39:** Josiah plans to get skins from an image AI. Plan: he sends a front/back/side turnaround painted over our exact template (/mnt/project-files/zomboid-mod/skin-template/zombie-turnaround-template.png, 3072x2048, 52.3 px per model unit, soles at y=1938; made by scratchpad player/template.py), I project it onto the 64-layout skin at 1024 and preview it on the body.
**02:00:** Josiah's first AI turnaround (zombie in boxers, 1536x1024) matched the template almost exactly. Cut with /mnt/project-files/zomboid-mod/skin-template/cut_skin.py (front/back views -> front/back faces, side view -> limb/head sides, torso sides from the front edge, tops/bottoms from patches) into ai-skin-1/zombie_boxers_{1024,256,64}.png; on the body preview as "Your AI skin" (same artifact).

---

### zomboid-corpses-direction.md

---
name: zomboid-corpses-direction
description: Josiah's 2026-10-27 ask that ZomboidCraft zombies fall to their death per movement and leave a corpse with its shot-off parts missing (no vanilla poof)
metadata:
  type: feedback
  modified: 2026-10-27T07:50:00.000Z
---

Josiah 2026-10-27 07:47 (characters thread, after the head-burst preview): "i need you to move on to changing it so that the zombies dont dissapear into a puff of smoke. right now upon death they just go poof into smaking a disappear. i want the zombies to have an animated fall to their death thats different for whether its running, shambling, hopping on a single leg, crawling without legs. and then i want that body to stay where it lays, missing whatever body parts were shot off it"

**Why:** vanilla death (tip onto the side, vanish in a puff after a second) breaks the Zomboid feel; Zomboid leaves bodies lying where they fell.
**How to apply:** distinct death falls for chase (running), shamble, one-leg hop and legless crawl (plus sensible ones for idle/attack/crawler/knocked down); the body then stays as a corpse showing stumps and a burst head. Builds on [[zomboid-dismember-direction]], [[zomboid-zbody-tech]], [[zomboid-hitbox-tech]].

07:49 on the preview's Minecraft-style tip-over after a head burst: "the body drop after needs to be more natural and not just have the zombie stay in the same position it died in while it falls to thr ground" (it must go limp and collapse, not topple rigid).
07:49 also: "they can also be relative to where they got shot from, their body reacts to the direction of the shot and subsequently causes their death animation to be built around it" (hit reaction away from the shot, the fall built around that direction, still per movement).
07:51: "that should also apply to living zombies too, their bodies should react to the shots, especially for weapons that arent one shot kills. having the body jerk in accordance to where it was hit and be pushed back (naturally) a little" (hit reactions on living zombies: the hit part jerks, small natural push back).

08:53 (after the v18 preview, "theyre good"): only a head hit kills. Red eyes keep glowing until the zombie is shot in the head or its head is blown off. A zombie with every limb shot off stays alive, wriggling on the ground, and one that can crawl keeps following you if you're in range, until it's finished with a headshot. Built: melee, fire and explosions still kill (my reading, told him). See [[zomboid-corpses-tech]].
08:54: "there needs to be more of the sprinting zombies". zombies.sprinterChance went 0.05 -> 0.15 (defaults round 6).

---

### zomboid-dismember-direction.md

---
name: zomboid-dismember-direction
description: Josiah's 2026-10-26 ask to shoot limbs off ZomboidCraft zombies (per-gun odds, limb comes off where hit) and the plan proposed in the characters thread
metadata:
  type: feedback
  modified: 2026-10-27T07:30:00.000Z
---

Josiah 2026-10-26 09:42 (characters thread): "i want to be able to shoot limbs off the zombies. if a headshot isnt hit, but an arm or leg is instead, depending on the type of gun and amount of shots i want the zombies to have limbs come off relative to where they have been shot. a shotgun will tear off limbs almost everytime up close, pistols are a lot harder to do that with, assault rifles take a few shots, a sniper could take off a limb. ill get the weapon thread to do up proper damage scaling but is that something you can do visually?"

**Plan posted 09:45 (awaiting his answers):** per-limb hit zones (forearm, upper arm, shin, thigh, from the hit point in the zombie's frame and whether its arms are out); per-limb health with per-gun multipliers in config (weapon thread tunes the numbers); limb breaks at the joint above the hit, flies along the shot, tumbles and lies on the ground; torn stump with bone + blood burst stays on the zombie; no arms = no grab, one leg = crawler, both = legless crawl. Asked: melee dismemberment too? Killing headshots sometimes blow off head or jaw? Preview shipped 09:58 in kits artifact v15 (https://claude.ai/artifact/Mnik6r9hRkYoARfsLTdBxU): "Shoot it" gun picker, click to shoot; limb toughness el 10 / sh 15 / kn 12 / hip 18, limb damage per hit pistol 3, AR 5, hunting rifle 14, shotgun 9 pellets x 3.2 (placeholders). Sources limbs.js + patch_limbs.py in preview-src. 10:00 he said the instant drop to the floor was too abrupt: one leg off = stumble, then get up and hop; both off = a realistic fall. v16 (10:12): stumble onto one knee with a hand reaching down, push up, hop on the remaining leg with arms out (chase/attack reach forward); both legs = gravity drop + forward pitch, arms brace, bounce, then legless crawl (limbMove in limbs.js). 10:09 he approved ("thats great, you can load that in"); building in game, guns only (melee dismemberment and headshots blowing off the head or jaw still unanswered). Tech: [[zomboid-dismember-tech]].

Guns.java (weapon thread's file) only knows head/legs/body today (Guns.pellet, hitEntity). Builds on [[zomboid-zbody-tech]].

Shipped in playtest 965dcdf (11:07). Josiah 11:44 on melee dismemberment and headshots blowing off the head or jaw: "we can work that in later" (deferred, not dropped; don't ask again until he brings it up or limbs come up again).

Josiah 2026-10-27 06:39 brought headshots back: "i want to have the zombies heads get blown off as well as the limbs. say from like a powerful sniper rifle and or a heavy shotgun clean blast to the head, it makes their heads explode. can we also look at the hitboxes and make sure they cover the character well". Built: head bursts from the hunting rifle (one headshot) and close shotgun blasts (about 3 pellets in the head), lever-action and magnum off by default; hitboxes refitted ([[zomboid-hitbox-tech]]). Melee dismemberment still deferred.

---

### zomboid-endless-night-switch-loot.md

---
name: zomboid-endless-night-switch-loot
description: LOOT's view of the 2026-11-09 Endless Night switch: ZOMBOIDCRAFT frozen, LOOT's systems mostly stay behind; Reach work goes to ENDLESS-NIGHT only
metadata:
  type: project
---
Josiah 2026-11-09 07:51/07:52: all Reach-world work goes only to repo josiahlongfield-jpg/ENDLESS-NIGHT, /mnt/project-files/endless-night/<area>/, and Modrinth profile ENDLESS NIGHT. ZOMBOIDCRAFT + MEDIEVAL GLOOM (1) are frozen as the county world (no new playtest merges, no writes to that profile). Rules: /mnt/project-files/endless-night/SWITCH.md.
- Per SWITCH.md scope, loot, shops, skills and survival needs stay behind; world loot also retired by the purge direction ([[zomboid-purge-direction]]). LOOT sent its inventory (medicine/downs, Mood, TarkovMelee rarity, Tarkov gun rarity table) to the Endless Night move thread 2026-11-09 07:3x.
- LOOT has no open Reach work. Any future LOOT task for Reach: wait for the new repo's first green build, then branch on ENDLESS-NIGHT.
Related: [[zomboid-tarkov-gun-loot]].

---

### zomboid-enemies-direction.md

---
name: zomboid-enemies-direction
description: Josiah's 2026-10-27 ask for more enemy variety in ZomboidCraft (golem-style mini boss "Brute", special zombie types, dogs, raiders) and the options doc
metadata:
  type: project
  modified: 2026-10-27T18:38:26.057Z
---

Josiah 2026-10-27 18:35 (zombie characters thread): wants more enemies to make the world "a lot more dangerous". Idea: use the iron golem template for a powerful, reskinned "mini boss" scattered around; brainstorm other types too. He likes to discuss options before work starts.

Options doc sent 18:4x: https://claude.ai/artifact/Vs6KaSGs2JErxbA2oD7oPY ("New enemies: a mini boss and more").
- Recommended: the Brute = our ZBody zombie scaled up (not the vanilla golem model, so limb shooting, corpses, BashGoal, population, BodyCaps carry over), golem stats (~3 blocks, ~5x walker health, swing flings you up and back, no flinch/knockback), too big for doors (tears frames out), heavy steps + roar, leads nearby zombies. Looks: riot juggernaut (my pick; front plates shot off), bloated giant (gas on death), labourer (charge). Could be outfits on one body.
- Other types offered: armoured, screamer (shriek wakes ~60 blocks via Noise/Population.wake), bloater (gas blur/nausea), lurker (fake corpse, ankle grab), burnt runner, hazmat (blades don't cut), drowned (docks). Sprinters already exist (sprinterChance 0.15).
- Bigger later: infected dogs (wolf base), hostile armed survivors.
- Specials roll better loot; per-type config switches; counted in the shared population.
Questions asked: Brute size 2.4 vs 3 blocks, which look first, walls or only doors, which types, bloater gas infects?, dogs/survivors now or later. Preview first before building.

Related: [[zomboid-population-direction]], [[zomboid-zbody-tech]], [[zomboid-loot-rarity-direction]], [[zomboid-characters-direction]].

Josiah 19:59: "I do want different types of zombies" (no picks yet). Also wants a supernatural "paranoia"/horror layer: things seen peering round corners that aren't there when you approach. Added a Paranoia section to the same doc: hidden per-player unease (alone, dark, tired, hurt; drops with company, a lit light, sleep; NO daylight: the grey "day" only slows it), per-player-only visions (footsteps, knocks, Watcher figure that vanishes when looked at/aimed at/within ~12 blocks, turned mannequins/corpses, false zombies, radio numbers voice, fake co-op friend, wet footprints). Two modes: never real (my pick) vs one real Watcher as a setting. Q7-8 ask mode and jump scares. Survival stats today: thirst, fatigue, wounds, infection (no panic stat yet).

RULE (Josiah 20:42): ZomboidCraft is never sold or redistributed, so designs, names and ideas may be drawn directly from other games (supersedes "no franchise names" for design borrowing; still don't rip other games' art/sound files). He asked for research on other games' paranoia/"seeing things" and killable enemy types: doc https://claude.ai/artifact/H5RCRNRHHZsc5ZCnLEqQyV ("Horror and enemy research: what other games do").
Research doc done 20:5x. Suggested first batch: Brute (WWZ Bull-style riot gear, weak back), Screamer drawn by noise+light (7DTD heat), Clicker-style blind sound hunter in basements/subway/bunkers, paranoia director (Alien: Isolation menace gauge) + Watcher (The Forest stare/slip away, From The Fog spots). Next: zombies evolving over days (7DTD/CDDA). Web fetch is blocked here (search only).

The Harvestman (reels 21:35/21:37, merged design, preview) lives in [[zomboid-harvestman-direction]].

---

### zomboid-eyes-clear-rule.md

---
name: zomboid-eyes-clear-rule
description: RULE from Josiah - ZomboidCraft face items rest below the eyes and headwear just above them; only eyewear covers the eyes
metadata:
  type: feedback
  modified: 2026-10-29T02:48:49.687Z
---

Josiah 2026-10-29 02:48 (clothing thread): "make sure that for all facial items that the eyes aren't covered like for the bandana, it covers the eyes completely, this should rest below the eyes and any headwear just above them".

**Why:** the first bandana was modelled up over the eye row and hid the eyes.

**How to apply:** a skin's eyes are the head's fifth pixel row (head coords y -4 to -3, head top at -8). Bandanas, masks, balaclavas, scarves and collars stay below y -3. Hats, helmets and cuffs stay above y -4 at the front. Only eyewear (glasses, goggles) and anything that's meant to (a gas mask's lenses sit over the eyes) goes over them. The constants are EYES_HIGH/EYES_LOW in tools/items/gen_clothes.py. Applies to zombie kits and any other character gear too. Related: [[zomboid-wearable-clothing]], [[zomboid-clothing-style]].

---

### zomboid-factory-irons-first-rule.md

---
name: zomboid-factory-irons-first-rule
description: Josiah 2026-11-06 06:07 - pull reference photos of every gun to check its factory iron sights; model those instead of our pack flip-ups
metadata:
  type: feedback
  modified: 2026-11-06T06:07:44.039Z
---

Josiah 2026-11-06 06:07 (Brita wave 3): "remember to pull reference images of every gun to check if they come with an iron sight so you can add that to the gun rather than using our pack made ones".

**Why:** He wants each gun's real factory irons, not generic pack flip-ups, wherever the real gun has them.

**How to apply:** Before building irons for any gun, get reference photos of it from REFERENCES (refs/items/weapons/irons/<folder>/). If the real gun ships with iron sights (fixed, folding, carry handle or hooded), model those from the photos. Use the pack flip-ups (packirons.py) only when the real gun has no factory irons, for example a rail-only or optic-only gun. This narrows the 2026-11-03 Honey Badger flip-up rule ([[zomboid-brita-irons-tech]]). See also [[zomboid-iron-sights-rule]] and [[zomboid-brita-wave3-tech]].

---

### zomboid-flight-direction.md

---
name: zomboid-flight-direction
description: Josiah's ask to make the carrier jets and Apache-style helicopter flyable (thread "Make jets and helicopter flyable"): goals, controls plan, ownership
metadata:
  type: project
  modified: 2026-10-26T23:18:25.067Z
---

Josiah 2026-10-26 23:11: flyable jets + heli, "detailed sound, dynamic controls, believable flight". Branch claude/project-thread-dcbfp2, one-jar rule [[zomboid-playtest-build]].

Controls: G gets in (never right-click), engine key C, jets mouse-aim (jet turns toward the view), W/S throttle, A/D roll, Space brake/airbrake, Left Alt free look. Heli: Space/Ctrl collective, W/S nose, A/D bank, mouse turns, H level lock. Top speeds are config (world.jetTopSpeed/heliTopSpeed, 300 km/h default). Sneak doesn't dismount aircraft.
Earning (Josiah 23:32 picked BOTH): Piloting skill + flight manuals, and fuel/battery/parts serviced before first flight (AirReadiness; world.aircraftService). Crashes explode, kill the crew, break the airframe (AirBreakup/AirDebris).
Owners: AUDIO thread cmsg_01LvQiYGyHLhwyRNcM6wP1DpT4W6GGH1BJNG9xCN2FfkG2 owns the .ogg files ([[zomboid-aircraft-sounds]]); VEHICLES thread owns cars; WEAPONS shares CameraRig (aircraft priority 10).
HUDs: F-22 style, Su-27 ILS-31, Apache IHADSS; seats at the design eye point. Cockpits: [[zomboid-cockpit-live-tech]].
Josiah 03:21 2026-10-27: real surface movement per jet, F-22 HUD/interior detail, easier jets, Apache flown from the FRONT seat (seat 0 now front), level lock for hover/height hold. 03:46: every carrier jet faces the runway/bow and can be moved. Built: [[zomboid-air-surfaces-tech]]. AUDIO heli round 2 (heli.idle, rotor outside with Doppler) wired in AirSound.
04:57 he said jets still very hard to fly + asked for a spawn command: jet flight assist on by default (Flight.assisted, flies toward the aim and banks itself, auto rotate + climb-out; H toggles in jets, heli H stays level lock) and /zc aircraft [f22|su27|apache] (ready to fly + manual), playtest d0823f8 05:18.
06:12 he said take-off stalls/dives, uneven acceleration, cockpit dark on some F-22s, deck carts/zombies explode you, shrink pilots. Causes: creative skips cantStart so carrier jets ran with a flat battery (dark) and 0-12% fuel (flame-out). Fixes 8013f402: creative start calls readyToFly; CarProp.plough knocks 1-high clutter aside on the ground (client removes, server move() override destroys first so the vehicle move isn't refused); ground wall crash only above max(40, 0.45 top) m/s; strike() runs zombies down; navy_catapult/shuttle give 14 m/s2 at throttle>0.8; carRiderScale now applies in aircraft.
AUDIO (wh9qba 261c20ae) added CarProp.flyby(at, vel, afterburner) + private flyby flag for WorldEvents flybys: server tick() returns after super.tick(), use() refuses, not saved. Keep it when reworking tick()/use(). Also CarProp.quiet (client static Predicate, AUDIO b7f6b72d): AirSound.keep() drops layers for unmanned flybys playing a whole recording.
Lights (08:29 nav lights + heli searchlight, Photon-safe glow) and far flybys: [[zomboid-air-lights-tech]].
Interact dot (22:00, [[zomboid-interact-dot]]): FlightClient.cockpitDot finder, on the nearest free seat when looked at within 3.5, 8211c064. Open: G vignette, weapons, arresting wires.

---

### zomboid-floor-purpose-rule.md

---
name: zomboid-floor-purpose-rule
description: RULE (Josiah 2026-10-27 22:40): building floors must make sense for the building's purpose and location; variety never means random unrelated floor types
metadata:
  type: feedback
  modified: 2026-10-27T22:41:32.859Z
---

Josiah 22:40-22:41 on the floor plan ideas (https://claude.ai/artifact/Hd12DbkzkbNz84KTff3qMy): "You can add all the floors and types so long as they actually make sense for their location. Like have a tool floor in a skyscraper doesn't really make sense where as in an industrial park it could". Then: "Having variety in a building doesn't mean fitting it up with anything to make it different, the floors still need to make sense relative to their purpose".

**Why:** random floor types break believability; a real building has one owner or use and its floors serve it.

**How to apply:**
- Pick the building's purpose first (e.g. insurance HQ, law and finance tower, multi-tenant office, hotel, apartment block, medical office building, industrial unit), then derive its floors from that purpose.
- Variety comes from room cuts, mirroring, states (looted, barricaded, burned), clutter and loot within the purpose, not from unrelated floor types.
- Workshops/tool/stock floors only in industrial places; clinics only where they'd rent space; sky bars/restaurants only atop big towers or hotels.
- Applies to every building, not only towers. See [[zomboid-floor-fitout]], [[zomboid-building-detail-direction]].

---

### zomboid-gear-attachments-direction.md

---
name: zomboid-gear-attachments-direction
description: Josiah's ask for more ZomboidCraft gear attachments, incl. working radios and comms headsets that mount on the combat helmet; brainstorm status
metadata:
  type: project
  modified: 2026-10-26T04:52:25.475Z
---

Josiah 2026-10-26 04:51 (weapons thread, after night vision shipped and he said "Very good"): "let's brainstorm some more gear attachments, maybe radios too, that actually work and could come as headsets that attach to the combat helmet".

Brainstorm offered 04:52 (waiting on his pick):
- Radios: walkie-talkie (channels, hold a key to talk, range with static, AA batteries). Comms headset on the helmet rails (hands-free push-to-talk with the radio on the vest; speakers make zombie-attracting noise, a headset doesn't; it also boosts quiet sounds and dulls gunshot ringing). Ham radio base station block with long range and emergency broadcasts (news, storm warnings from the weather system, loot rumours).
- Helmet: side-rail helmet light, IR strobe (only visible through night vision), rear battery pack (spare AAs, counterweight), face shield or jaw guard.
- Plate carrier pouches: radio, magazine (faster reloads), med (quick bandage), water bladder.
- DECIDED 05:19: "use whatever voice chat mod will make it work. I'd want it to transmit in game voice". So radios use Simple Voice Chat's plugin API (maven.maxhenkel.de/repository/public, de.maxhenkel.voicechat:voicechat-api, fabric.mod.json entrypoint "voicechat", VoicechatPlugin). It's an optional dependency: without it the radios stay silent. Plain e4mc can't carry SVC (needs a UDP sideband), so with e4mc everyone adds "Voice Bridge for e4mc" (Fabric 26.3). A paid host opens the voice port instead.
- Built 05:20-06:10 (tech: [[zomboid-radios-tech]]); Josiah said "Very good" to the preview at 05:53. Next from the brainstorm: helmet light, IR strobe, rear battery pack, face shield, plate-carrier pouches, ham base station.
- Started 05:20: walkie-talkie + comms headset models and a preview first. Design: a talk key sends your voice on your channel to players in range, crackly near the edge. Without a headset it plays from the receiver's speaker, which nearby players and zombies hear. With a headset only the wearer hears it.

**How to apply:** helmet attachments mount like the night vision (see [[zomboid-nightvision-tech]]) and share AA batteries. Gear uses the chunky 3D-block style ([[zomboid-clothing-style]]). Gear tier rolls are still a later step ([[zomboid-loot-rarity-direction]]).

---

### zomboid-harvestman-direction.md

---
name: zomboid-harvestman-direction
description: The Harvestman, Josiah's long-legged corpse-feeding horror creature for dark big buildings (from his two reels, 2026-10-27), and its 3D preview
metadata:
  type: project
  modified: 2026-10-28T00:10:34.728Z
---

Part of [[zomboid-enemies-direction]]. Related: [[zomboid-corpses-tech]], [[zomboid-photo-reference-rule]]. Tech: [[zomboid-harvestman-tech]].

Origin 2026-10-27 (his reels): lure, trap, strike, stalk, retreat; the body it carries is FOOD (drains the head like a spider); live players are the prey it wants. 

Preview: https://claude.ai/artifact/HSqxssuzVqTUWwtebz3Z1L. FOUR legs to points, not spider-like.
22:57 his renders refs/creatures/harvestman/josiah-model-*.png.
2026-10-28 00:06: SLEEK and THREATENING, not fat; "more legs than body" (refs/creatures/harvestman/josiah-silhouette-2026-10-28.png). Round 5 (v4) "Much better".
00:32-00:38 ATTRIBUTES/BRAIN asks: a roaming AI entity that thinks for itself (needs, traits, memory, decides to move/trap/feed); climbs walls and sits in dark corners; learns to stalk properly, learns player movements, follows them, works out traps; mind games (drags bodies, poses them, suspends them); learns and EVOLVES in game. It CHOOSES ONE PLAYER, remembers them by account across log-offs, notices when they log back in, prioritises hunting them with targeted paranoia (per-player layer). Admin /zc harvestman target <player>|clear (built). Doc: https://claude.ai/artifact/XkK8zUaqN51riTdqtz5V3x: 5 stages, utility AI + Alien: Isolation-style director.
01:15 JOSIAH'S ANSWERS (in doc, 'Decided' table added): very HARD TO KILL, so few of them (APPROVED 01:18: 1,200 hp, plates 10%, eyes 200%, legs 250, 4-6 per world). ONE hunts at a time server-wide; ends on target or hunter death, then >=48 in-game h grace. Switch target after 7 days offline or worse grudge. No small vents; perches ceiling corners, fits gaps sensible for its size. Zombies ignore it; it eats zombies when starving (starving weakens a stage). Growth + learning, learning first. Grab: latch, drag to safe spot (wall/dark room), lights up while feeding, death ~10 s if not freed; needs 3rd-person drag/feed anims + 1st-person victim view. Lair = dug-out den with bodies, usually empty; entering alerts it, it travels back at real speed; can hold a bunker map. Leg regrowth needs a reason: Self-repair from scrap at lair over a day (approved). LORE: known entity; survivor notes, drawings, military studies in bunkers, plus a special underground lab with a dead one in a stasis tank beside a zombie tank.
01:35 ROUND 6: match refs/creatures/harvestman/josiah-armoured-2026-10-28.png + a THIRD leg joint (ankle); legs approved. 01:55 he DROPPED the skull/face-plate idea: body exactly as his render (no visible head, armoured front + back unchanged), side view sleek ending in a sharp tail point. 02:00 he said plates/spines were wrong; zoomed ref = downward arrowhead: long creased blade plates in a herringbone off a tall centre keel, crown of jagged back spines, tall tail spine, shard beard, two piston struts under the point. 02:17: no tail spike; a SPINE ROW along the back (small at head, tallest mid-back, small at tail); FEEDING (02:47, v13): victim back to it; the SCYTHE ARMS DRAG it up to the beard (same folded shape closed tight, tips point down, blades in through its back and out of its chest, never crossing, never reaching up); beard barely moves, covers only the top half of the head; red parts glow. 2026-10-28 04:02: ALL BLACK, NO TINT (only the red stays). Legs: 2 joints, thigh +25%, lower leg one rigid smooth curve with two slight bends. 04:22 stance like refs/creatures/harvestman/josiah-tall-stance-2026-10-28.jpg: body SUNK low, long thighs slope down from high knees.
2026-10-28 08:11 CAMO: red too bright in game; it goes fully black while moving/sneaking/hunting (governs its own colour to hide), except SIX small glowing red eye dots, three each side of its head. Built; weak spot stays the belly core.

---

### zomboid-holsters-spawn-direction.md

---
name: zomboid-holsters-spawn-direction
description: Josiah's 2026-10-27 ask for worn quick slots (belt, holster, sheath, wrist) and a spawn picker before entering the world; mockup link, plan, open questions
metadata:
  type: project
---

Josiah 2026-10-27 22:19 (UI thread) picked "Holsters, sheaths and wrist slot" and added: "Work that and the spawn picker screen before you load into the game".

Mockup v1 (Design canvas, 3 boards): https://claude.ai/artifact/Sfr793TzeSendKLXEUPD3K. Sources are in project files zomboid-mod/inventory-ui/holsters-spawn-v1/. The map is county.svg from make_map.py over plan_dump.js output of grayhaven-plan-draft3.

Plan shown:
- Quick slots on keys 1-8 come from what you wear: Hands (always), Hip x2 (any belt), Holster (pistol/revolver/magnum; a hip holster needs a belt, shoulder and thigh holsters don't), Sheath (knife sheath or machete scabbard), Back (the existing sling), Pouch x2 (duty or tool belt). The new gear slots are Belt, Holster, Sheath and Wrist. The watch HUD shows only when a watch is on the wrist.
- Spawn picker: shown before the world, on first join and after death. Place list from county_plan.txt (towns, cities, suburbs, districts, hamlets; 23 entries), danger 1-5 from population rules. You wake inside a zombie-free building. There's a "Pick for me" button, and you can wake near a friend.
- Reference photos asked from the REFERENCES thread: refs/items/{belts,holsters,sheaths,wristwatches,road-maps} (spec refs/_tools/gear.json). Models must wait for them.

Status (2026-10-27 23:30):
- Josiah approved the plan ("Work that and the spawn picker"), plus my three defaults: no belt means Hands and Back only, with a starter leather belt for everyone; the picker shows again after death; "wake near a friend" is in.
- The HUD row shows every slot at once (his ask) and is in playtest.
- The spawn picker was merged into playtest 2026-10-27 (merge 2ad658e9). /zc wakeup reopens it.
- Quick slots + belt models merged into playtest 2026-10-28 00:10; hip holster model in playtest 00:40. PAUSED by Josiah 2026-10-28 01:16 ("Pause this for a bit"); left: shoulder/thigh holsters + both sheaths (out of loot until modelled). Resume only when he says go. Photos are ready (2026-10-28 11:03): refs/items/holsters (23), sheaths (10), wristwatches (10), road-maps (6); raw hits, skim and skip misses. See [[zomboid-quickslots-tech]].

Related: [[zomboid-spawn-select-direction]], [[zomboid-hud-direction]], [[zomboid-kit-carry-rules]].

---

### zomboid-hordes-death-base-direction.md

---
name: zomboid-hordes-death-base-direction
description: Josiah's 2026-10-26 pick of three new ZomboidCraft features (roaming hordes, your body stays after death, base building); plan doc link and status
metadata:
  type: project
  modified: 2026-10-26T11:02:54.702Z
---

Josiah 2026-10-26 11:00 (UI thread), choosing from a creative brainstorm: "look into building 3, 4, 6", which were roaming hordes, your death leaving a trace, and base building.

Plan doc (Claude Doc, 11:10): https://claude.ai/artifact/UCUCueszUoTCCTDEedJ7H6 ("Hordes, body recovery and base building"). It recommends this order:
1. Base building. A Build page on the E kit screen with a placement preview and timed builds. Wave 1: boards up to 4 planks, metal sheets, tin-can tripwire, rain barrel, sandbags. Wave 2: car alarm decoy, alarm clock decoy, planter box, spiked log wall. Every board has its own HP. All custom blocks.
2. Roaming hordes. Herds are records on the county map that move along roads and become real zombies within about 96 blocks of a player. They turn toward big noises and are marked on the Map once spotted. Defaults: 6 per county, 30-200 zombies each.
3. Your body stays. The corpse keeps all your gear and is looted from the Pack page. An infected body gets up as a zombie carrying your gear. The new character's map marks the death spot. Skills reset by default, with an optional share kept.

Josiah's answers 11:11: the order is approved (base building, hordes, body). Hordes should be BIGGER than 30-200. His body gets up only if he died infected. Death resets ALL skills and progress. Base building waves approved as written (he may add more). New ask: stop players digging down to dodge everything. Proposed 11:15: (1) a hard dig floor about 8 blocks under natural ground, except inside built places; (2) block toughness tiers by tool + skill (hand for loose; Carpentry + crowbar/hammer for wood; new Masonry + sledgehammer for brick/concrete; new Metalworking + torch for metal); (3) breaking is loud, costs stamina and tool wear, and drops rubble/scrap rather than the block. Placing stays free. He said yes 11:20. SHIPPED in playtest ddb6cdbd (11:47): building/DigRules.java + mixin BlockStateDigMixin (getDestroyProgress), config section digging, skills MASONRY/METALWORKING (SkillsPage Building group), items rubble_chunks (the city already has a rubble BLOCK), scrap_metal, propane_torch (tools/items/gen_scrap.py). Walls = full CityBlocks without block entity, tier by SoundType; furniture strips with its tool for XP. Wave 1 (Build page, Placer, boards with HP, metal sheet, tripwire, rain barrel, sandbags, weld sheet) merged to playtest a663b5de 2026-10-26; see [[zomboid-base-building-tech]]. Wave 2 (alarm clock + car alarm decoys, planter box, spiked log wall) in playtest 10f69661 on 2026-10-27. Next: roaming hordes, then body. Existing pieces: barricade/ (hammer + plank barricade), zombie/Hordes.java (only the crowd count for sound), ZombieSpawner, Noise/Sight. See [[zomboid-ui-systems-tech]] and [[zomboid-spawn-select-direction]].

---

### zomboid-hordes-never-vanish-rule.md

---
name: zomboid-hordes-never-vanish-rule
description: Josiah 2026-11-04 RULE: a horde a player met must never disappear when they leave and return; vanilla despawn is off for walkers (MobMixin)
metadata:
  type: feedback
  modified: 2026-11-04T06:08:39.043Z
---

Josiah 2026-11-04 06:07 (FPS thread): "we dont want a horde being encountered and having it disappear the next time a player returns."

Cause found: vanilla Mob.checkDespawn deleted walkers for good (instant past 128 blocks, 1/800 per tick past 32 when idle). His 600-zombie test came back as a handful.
Fix: MobMixin.zomboidcraft$walkersStay makes removeWhenFarAway false for ZombieTypes.isWalker (playtest merge 6ed5d2db6). Do NOT use setPersistenceRequired for this: Population.fold() skips persistent zombies.

**Why:** hordes are a gameplay thing he wants to stay real; folding into chunk records (Population.fold, countBeforeDelete 300 folds extras, never deletes) is the only allowed way to take them off the field.

**How to apply:** any new walker removal path must fold to records, never discard. The real cap (autoCap 120-800) still limits how many come back live at once; the rest wait as records. Related: [[zomboid-population-tech]], [[zomboid-population-direction]].

---

### zomboid-hud-direction.md

---
name: zomboid-hud-direction
description: Josiah's ZomboidCraft HUD redesign - chose "field kit that fades" (2026-10-25); rundown doc and open questions
metadata:
  type: project
  modified: 2026-10-25T10:09:25.572Z
---

Josiah (2026-10-25): "lets work on a new HUD". On the decision card he picked **Field kit that fades** and asked for a full rundown before anything is built.

Rundown doc: https://claude.ai/artifact/8d3jqHFdbRyWKHr2zjvo6h
- The pieces use the same kit as the inventory. Six pieces:
  - Belt hotbar on webbing, bottom middle.
  - Moodle tiles down the right edge, yellow/orange/red.
  - Body outline replacing the hearts, bottom left.
  - Weapon tag with ammo on tape, bottom right.
  - Skill-up tape, top left.
  - Grabbed warning under the crosshair.
- Vanilla hearts, hunger, armour, XP and air are removed. Hunger and air become moodles.
- Each piece fades after a few quiet seconds. It stays up while dangerous (bleeding, below half health, bad moodles, aiming). Left Alt peeks the whole HUD (the key is free; the mod binds R, V and K).
- His answers (2026-10-25): whole-body health for now; potion effects become our own moodles; wristwatch yes (time and day, only while wearing a watch); Zomboid belts and holsters decide the quick slots. The first slot is always HANDS, then HIP L/R, HOLSTER, SHEATH and BACK, and the inventory's Belt & Hands row matches.
- Style (his words): "a clean design but equally immersive with finite details". Keep pieces small with no heavy panels, and get the immersion from fine material detail.

Mockup v1 (2026-10-25): https://claude.ai/artifact/5gbKWAMkyYf4FDDtWbihuv. Source is in project files zomboid-mod/hud-ui/v1/ (template.html, assets.py). It uses the real game sprites and 12px pixel moodle icons. Feedback on v1 (10:36): he likes the belt and other pieces vanishing until used. Belt and holster squares should have NO backdrop strap, just the squares. He wants a high-quality v2 "on par with our level of realism and moody design", and it doesn't have to match the inventory UI. Mockup 2 is published at the same link (source in zomboid-mod/hud-ui/v2/). It has bare belt squares with the real 3D item models, enamel moodle plates with sprayed stencil icons, a 7-segment wristwatch and a lit blocky body figure. 11:01 he asked for the body slot to be the player model, as in the inventory mirror (InventoryScreen.extractEntityInInventoryFollowsMouse), in the bottom left, turning to follow look direction. The mockup now does this, with health shown as a red underlight, wounds and a state word. 11:10 he asked that the head aim at the mouse so that hovering over its head makes it look straight at you; mockup v4 does this (body turns half, head turns the rest; 11:11 he asked to halve the head turn, so the angles are scaled by 0.5). In game, drive it from the player's look direction the same way. Next: his feedback, then build. See [[zomboid-inventory-ui-direction]]. Current HUD code: client/MoodleHud, SkillsHud, combat/CombatHud, combat/Crosshair.

BUILT 2026-10-25 (he tapped Build it now at 11:12): code in client/hud (Hud registers it and hides vanilla bars in survival; BeltHud, MoodleHud, BodyHud, WeaponHud, WatchHud, SkillTape, GrabHud). Art comes from tools/ui/hud/gen_hud.js (Playwright) into textures/gui/sprites/hud; the fonts are hud/hud_big (Barlow Condensed). Config look.fieldKitHud. New item digital_watch: the watch shows while it's anywhere in your inventory. In playtest 596ac50. NOT done yet: Zomboid belt/holster/sheath items that gate quick slots with HANDS/HIP/HOLSTER/SHEATH/BACK names (for now the belt shows the hotbar slots with numbers), a wrist slot for the watch, and wounds drawn on the figure. Game names: the clock is Level.getDefaultClockTime(); LivingEntity has no getAttackAnim/swinging, so use options.keyAttack.

**Why:** he likes options and a full explanation before work starts. **How to apply:** don't build HUD code until he approves the mockup.

2026-10-26 01:35 (UI thread): Josiah wants a subtle "water on your face" HUD effect when exposed to rain (the game is always rainy). Proposed: drops gather at the screen edges (centre clear), occasional runs down, more in heavy rain / looking up, dry off 10-20 s under cover, sprinting pushes them sideways, hats/hoods reduce, setting to tune. Decision card asked: HUD-drawn drops vs shader refraction vs HUD now + refraction later (recommended). Check rain exposure with level.isRainingAt(eye pos). Keep it consistent with the weather thread's thin-rain direction ([[zomboid-weather-direction]]).
SHIPPED 2026-10-26 02:0x: he chose "HUD drops, plus refraction later". Code client/hud/RainHud (drawn in Hud.under before the active() check), sprites hud/raindrop_round|tall|wide + rain_streak, setting HudSettings.rainOnFace (Video Settings: Off/Light/Normal/Heavy, saved in zomboidcraft-hud.json). In playtest e274e69, untested in game. Refraction (shader) still to do as an option.
Update: drops made bigger/brighter after the browser preview (https://claude.ai/artifact/8YVbj4akbaQCSq8KBa1sm8, source in thread scratchpad rain/), in playtest 9b2c7e7.
Exposure (playtest 9241c55): level.isRainingAt(eye block) (same MOTION_BLOCKING test as falling rain), none in a CarProp, first person only, 0.4 s settle before drops land.
Preview (2026-10-26 07:5x): artifact 8YVbj4akbaQCSq8KBa1sm8 v2 is now the FULL HUD (hud2 mockup + face rain + action bar + senses meters), built by scratchpad rain2/build.py from hud2/template.html. He liked the rain ("rain is good").

---

### zomboid-inventory-ui-direction.md

---
name: zomboid-inventory-ui-direction
description: Josiah's ZomboidCraft inventory redesign (field kit look, bags/weight, nearby loot side, weapon bench) and its build status
metadata:
  type: project
  modified: 2026-10-25T06:52:37.412Z
---

Ask (2026-10-25): a new inventory that still shows the character but is far more intricate: backpacks of varying sizes and capacities, duffels you carry, and a weapon tab with an expanded gun view where carried attachments drag and drop on.

Agreed direction:
- Mockup v3 approved ("thats much better"): https://claude.ai/artifact/CJwiGemVvUK1YWHFfuUfkQ. Source is in project files zomboid-mod/inventory-ui/v3/.
- Use Minecraft's own slot grids and clicks, restyled as a moody textured field kit: canvas, steel and webbing panels, masking-tape labels, stencil headers, a blurred rainy city behind, and a cursor torch. Never flat block colours. Josiah: "it needs to be dynamic, moody, on theme".
- Weapon view: only the bare gun body is solid. Each empty slot shows a translucent green ghost part with a green crosshair at its mount point, and fitting a part makes it solid. Drive the weapons thread's attachments component; the weapons thread supplies the slot list, mount points and ghost models.
- Duffels sit in the vanilla offhand (tag zomboidcraft:hand_carried).
- Build order: (1) screen and character, (2) bags, gear and weight, (3) nearby loot side, (4) weapon bench.

Status (branch claude/project-thread-j1d6bj, all in the shared playtest jar, see [[zomboid-playtest-build]]):
- Step 1 confirmed in game: "inventory came up, looks good".
- Step 2: carry/* covers bags, gear slots, pockets and weight; the client side is client/inventory/FieldKitScreen.
- Josiah said bags "dont get equipped". Fixed two ways:
  - Bags go on automatically when picked up (config carry.autoWear).
  - Worn bags render on the player through WornGearLayer and the synced Carry.WORN attachment.
- Step 3 is in loot/Nearby*.java:
  - Pressing E sends an OpenKit packet. The server opens NearbyMenu, which keeps InventoryMenu's slot order and shares the 2x2 grid and bags.
  - The menu shows Floor plus tabs for up to 8 fittings, chests and barrels in reach and in sight. Fittings open it too.
- Steps 2 and 3 have not been tested in game. Josiah is reviewing.
- The sound thread added an item.bag sound event, not wired up yet.
- Step 4 (weapon bench) built 2026-10-25 in client/inventory/WeaponBench.java: a Bench tab on the carried panel swaps gear+carried for a 3D stage (gun drawn as a pinned ItemEntityRenderState through g.entity, ghost item models in items/bench_ghost/). Socket clicks go to NearbyMenu.bench via benchButton. Josiah's feedback: no tags or leader lines (click the crosshair), no auto spin, drag follows the mouse. After that he said "very good" (2026-10-25). UI sounds (ui.tab/click/item_move/wear, moodle heartbeat) go through client/sound/UiSounds.
- 2026-10-27: sling, pocket and drag-drop rules shipped, see [[zomboid-kit-carry-rules]].

**Why:** Zomboid-style intricacy is the point of the mod. **How to apply:** Josiah likes options and mockups before big UI changes. Keep every new jar on the playtest branch.

---

### zomboid-iron-sights-rule.md

---
name: zomboid-iron-sights-rule
description: RULE (Josiah 2026-11-01 10:22): every future weapon gets its real-life iron sights modelled from reference photos, then the "rough up" paint/bevel pass
metadata:
  type: feedback
  modified: 2026-11-01T10:23:12.377Z
---

Josiah 2026-11-01 10:22 (Brita weapons thread): "for all future weapons you go through the same process of acquiring their true to life iron sights to model after on each weapon as well as go through the 'rough up' process".

**Why:** generic or borrowed sights looked wrong and off-centre, and flat single-colour sight parts looked "block" like next to the hand-painted gun models. He wants each gun's own real sights, in the gun's art style.

**How to apply:** for ANY new weapon (bought pack, local pack, our own):
1. Ask the REFERENCES thread for real photos of that gun's iron sights (side, from behind, from above) → refs/items/weapons/irons/<gun>/. Wikimedia only from his PC.
2. Model the real front and rear sight (post/blade/bead, notch/aperture, ears, wings, ladder), cut any solid stand-in sight blocks, dead-centre line, see-through rear. Shotguns: just a bead. Guns sold scope-only get the back-up irons that gun really wears.
3. Rough-up pass: chamfered worn edges, hand-painted tiles (mottled metal, soft highlight streak, dark painted borders, scratches, chipped light edges), painted in the gun's own metal colour, tiny hash-jitter unevenness.
4. Video preview (sight picture per gun) before any build ([[zomboid-video-preview-rule]]).
Tools: brita/tools/irons/irons.py + sightkit.py ([[zomboid-brita-irons-tech]]).

---

### zomboid-kit-carry-rules.md

---
name: zomboid-kit-carry-rules
description: ZomboidCraft kit rules Josiah set 2026-10-27: no weapons in pockets, long guns on the back sling, drag-drop, shift bindings, bigger bench
metadata:
  type: project
---

Josiah's asks (2026-10-27, UI thread), all built and in playtest:
- Weapons (guns and melee) never go in pockets (inv slots 9-35). Shotguns, rifles, snipers and melee can go on the back (gear SLING). Pistols, revolvers and magnums cannot sling.
- A picked-up weapon goes to a free belt slot, then the sling, then bags (Carry.takeWeapon via an InventoryMixin add() hook). Shift-click quick move follows the same order and never uses pockets.
- True drag and drop: press, drag and release over a slot to place it. Releasing anywhere else, or closing the screen, returns the item to where it came from. A plain click still picks up and places as before (KitScreen pressed/dragFrom/drop()).
- Shift + right click drops the item on the floor (ContainerInput.THROW).
- Bench: the gun stage is the main part of the screen (MID 314x196), and other panels sit around it. Right mouse only turns the gun and never selects. The footer says "RMB drag  Turn the gun".
- Clicking a fitted attachment moves it to the spares list (CI sweep checks every gun and part).

Tech:
- The rules live in Carry.weapon/slings/pocket. SlotMixin refuses pocket slots, and GearSlot SLING mayPlace = Carry.slings.
- The sling draws in WornGearLayer.sling(). Gun models run along Z, so it pre-rotates Y 90° and flips 180° so the muzzle is up over the right shoulder. It sits just outside the pack (packOut).
- To see the player's back in a client gametest, draw the render state on a test Screen with bodyRot 0 (KitUiTest BackView). Vanilla skips the LocalPlayer when the camera is another entity, and the mod's F5 cam is too close.
- Melee sling confirmed on CI 2026-10-27: the axe lies diagonally, head over the right shoulder. Test gotcha: a test Screen must override isPauseScreen() false, or the integrated server stops ticking and the WORN sync never runs.

**Why:** Zomboid-style carrying, so weight and slot choices matter. **How to apply:** keep the pocket and sling rules in any new menu or quick move. See [[zomboid-inventory-ui-direction]] and [[zomboid-ui-systems-tech]].

Status 2026-10-27 13:17: UI thread stopped per Josiah (only LANDSCAPE + photo refs keep working). All UI work is in playtest (branch head c0b13918). Resumed 18:29 (all threads allowed). Axe check done; nothing pending but his in-game feedback.
- 2026-10-27 22:15: WORLD LOOT thread (cmsg_01LvQiYGyHLhwyRNcM6wP1Dp3sPQiPRKoASa3LtUBwRxqS) is building a huge custom item roster. UI asked it for: a public ItemInfo.rarity(stack), an int value, a weight data table, and category tags. UI then switches KitItems over and adds med treatments. The list is in project files zomboid-mod/world-loot/ui-needs.md. Next UI job was offered on a card (holsters/wrist slot recommended).

---

### zomboid-lifts-direction.md

---
name: zomboid-lifts-direction
description: Josiah's ask for working escalators and lifts in every tower over 10 stories; power choice "always run" (2026-10-27)
metadata:
  type: project
  modified: 2026-10-27T21:38:44.169Z
---

Josiah 2026-10-27 21:30: escalators functional wherever they are; lifts in every tower over 10 stories,
call buttons on every floor, the car stays at its last stop until a button is pressed, a digital
number indicator on each floor that tracks the car as it moves.

Decision 21:37: lifts and escalators ALWAYS RUN (no power needed), even though the rest of the city is dead.
Thread: "Add working elevators and escalators" (branch claude/project-thread-96uzh5).
Tech notes go in [[zomboid-lifts-tech]] once built.
- Shipped in playtest 2026-10-27 22:40 (56b00978). Pending: redo looks from refs/city/lifts/ photos once the REFERENCES thread fills them.
- PAUSED 2026-10-28 01:18 by Josiah (all threads except LANDSCAPE/REFERENCES/PLAYERS). Everything shipped is in playtest; branch 96uzh5 also has tools/lifts/preview.html (not in playtest, tools only). Resume = photo redo.
- 2026-10-28 11:03: lift/escalator photos delivered to refs/city/lifts/ (6 folders x 5, sources.md each). Waiting for Josiah to unpause before the redo.
- 2026-10-29 20:06: more refs landed in refs/city/lifts/ for future work (hospital-bed-lift, freight-service-lift, glass-panoramic-lift, parking-garage-lift-lobby, abandoned-lift, lift-lobby-hall; escalator-details landed 2026-10-30). Thread paused again per Josiah; floor-panel fix awaiting his retest.

---

### zomboid-loot-rarity-direction.md

---
name: zomboid-loot-rarity-direction
description: Josiah wants ZomboidCraft to be a believable looter shooter: huge roster in every rarity, tier 1-5 rolls on guns, attachments and gear, learned blueprints, no franchise names
metadata:
  type: feedback
  modified: 2026-10-25T23:05:00.000Z
---

Josiah 2026-10-25: "I want there to be a huge variety of weapons, and by that I really mean huge... I want this to be a looter shooter game too that rewards persistence and the grind."

**Why:** finding a rare gun should be a reason to keep exploring; the roster has to be big enough that rarity means something.

**How to apply (all approved by 2026-10-25 21:50):**
- Destiny 2-style system minus sci-fi: model rarity sets where an item spawns; each drop rolls Tier 1-5 (condition, stat range, perk columns from real gunsmithing: barrel, action/trigger, sights, furniture, trait; pre-fitted attachments; finish). Even a rare gun can roll badly. Gunsmith bench moves parts from duplicates.
- 21:50: tiers ALSO apply to attachments (scopes, suppressors, lights, lasers) and gear: military helmets with mounts for night vision, torches and other add-ons; vests, plate carriers and the like.
- 21:50: tiers CAN be pushed past their natural level, but only with a lot of work.
- 21:50: NO loot scaling by distance from spawn. He wants a Zomboid-style chosen spawn ([[zomboid-spawn-select-direction]]), so expansion must not gauge loot quality. Tier comes from danger and the place (military, police, gun shop, horde density), not distance.
- 21:50: rarity names must NOT be Destiny's. The top rarity is a big class, not one-offs: every rarity, the top one too, needs a very large array of items, so the odds of a specific one are low but variety keeps the grind fun. No "one per world".
- 21:50: skin BLUEPRINTS are found in the world, learned for good, and blanket (not unique per weapon); they scale in rarity. They apply to guns and some melee.
- Much bigger melee roster with rare oversized finds (e.g. huge ornate greatswords).
- RULE 21:50: no literal names or franchise references anywhere (items, in-game text, docs). Pull inspiration only.
- 21:03 "I still want a believable feel": every rare find needs a grounded reason (collector's replica, museum or prop piece, gunsmith's custom job), and stats stay realistic: a giant replica sword is heavy, slow and costly in stamina.

- 22:05: APPROVED rarity names Common, Scarce, Rare, Prized, Grail (colours bone, olive, steel blue, oxblood, brass). A gun raised to tier 5 at the bench DOES get the tier 5 finish ("you put a lot of work into doing so"). He wants a lot more camos for each class: doc now has 48 shared camo patterns (paint/cloth/wrap, one blueprint per pattern covers every class) plus class-only finishes, 60-80 looks per class. Roster target ~300 models in all (about 70 guns, 90 melee, 60 attachments, 65 gear). 22:27: "Let's start with the 70" guns, more later maybe.

- 23:44 (characters thread, via coordinator): all clothing and worn gear (helmets, vests, plate carriers, watches, NVG mounts) use that thread's cartoonish-but-believable 3D-block style (he calls it Borderlands-like). Check its soldier preview https://claude.ai/artifact/Pp1bQP48f9LZxhCEhK3v2L before modelling any gear. Guns and melee keep [[zomboid-weapons-direction]].

Design doc: https://claude.ai/artifact/4eatHuijbbWZZG68mZaMi8 (Claude Doc "ZomboidCraft loot and weapon rolls"), revised through 23:05 (rev 14, no open calls). Step 2 build: [[zomboid-rolls-tech]].
See [[zomboid-weapons-direction]] for build rules and [[zomboid-guns-tech]] for how guns are made.

---

### zomboid-match-zomboid-rule.md

---
name: zomboid-match-zomboid-rule
description: RULE (Josiah 2026-10-31): run Project Zomboid's own version of every feature unless he already confirmed a change
metadata:
  type: feedback
  modified: 2026-10-31T03:36:56.212Z
---

Josiah 2026-10-31 03:34: "Ideally I want to run the same features that zomboid has UNLESS I have already made changes to them and confirmed altering directions."

**Why:** he wants Zomboid's depth intact; Claude's "improvements" (merging moodles, trimming traits, skipping systems) are not wanted without his say-so.

**How to apply:** default every system to Zomboid B42 as is (full occupation and trait lists, 17 body parts, separate stress/boredom/unhappiness, the whole B42 skill list, B42 animals, crafting stations, factions/PvP as settings). Only confirmed changes override it (endless night, G interact, no shot knockback, (headshot-only kills REVERSED 2026-11-01 04:49: calibre-based head + body kills, see zomboid-brita-weapons), death resets all, loot never scales, skill-gated base customisation beyond Zomboid, working hoists). Additions Zomboid lacks are fine if marked as additions. World settings should mirror Zomboid's 269 sandbox options in 12 groups plus an "ours only" group, with presets.

His 2026-10-31 answers: SURVIVAL thread yes; body temperature a setting; district power failure with an adjustable delay; class-locked car parts; beam rule (floors sag then fall); farm animals yes; no other seasons for now. Doc: [[zomboid-pz-review]].

AMENDED Josiah 2026-11-07 09:36: "I don't care about matching zomboid in every aspect. If we can achieve a more difficult game by changing some allowances I'm all for it. So long as the server can hold up." Harder-than-Zomboid changes are fine without asking, provided server performance holds (measure tick time).

---

### zomboid-mesh-attachments-rule.md

---
name: zomboid-mesh-attachments-rule
description: RULE (Josiah 2026-10-29 09:17) no old block parts on bought-mesh guns; attachments come only from the guns' own pieces plus the 10-piece attachment pack, swappable
metadata:
  type: feedback
  modified: 2026-10-29T09:21:45.288Z
---

Josiah (2026-10-29 09:17): "i dont want you to use any of the old parts from the previous weapons. all attachments are being replaced too. just use the attachments that came with the weapons but make them interchangeable with all the attachments from the pack i uploaded (if they are applicable to the weapon)".

Josiah (2026-10-29 09:34): "i want you to add these weapons like the old ones never existed, dont reuse anything except for where they sat on the screen and were held by the player as a reference". So muzzle, rails, sights, mags, mount points all come from the bought mesh; only the on-screen placement and the hold poses are kept from the old guns.

**Why:** the first mesh swaps kept old block irons, block mags and block attachments over the real mesh, which looked odd and disconnected.

**How to apply:**
- A mesh gun draws nothing from block models (muzzle flash effects aside): its own sights, mag, furniture.
- Pieces a bought gun came with (pbr scopes, red dots, bipods, rifle 6 suppressor, grips on 1 and 14, launcher on 2, M200 scope/bipod) become attachment items, fitted as standard on that gun, swappable.
- The attachment pack (att_*.obj in scratch packs/pbrobj: eotech holo, Aimdot red dot, Prism Acog 4x, Foregrip RVG, surefire light, ironsight flip irons, magnifier, Fortis angled grip, Magpul grip, RMR2 mini red dot) fits every gun with a matching slot; magnifier gets its own slot.
- Old block attachment models go away on every gun, block guns too.
- Laser sight: no model in either pack; Josiah (09:35) "ill get a laser light pack". Laser stays as an item with no gun model until that pack arrives.
Related: [[zomboid-mesh-guns-tech]], [[zomboid-wave2-guns]], [[zomboid-gun-art-style]].

---

### zomboid-no-self-resolve-rule.md

---
name: zomboid-no-self-resolve-rule
description: RULE (Josiah 2026-10-29) - no thread resolves itself unless Josiah authorises it
metadata:
  type: feedback
---

Josiah 2026-10-29 01:44: "tell all threads not to resolve themself unless I authorise it".

**Why:** he wants to decide himself when a piece of work is finished.

**How to apply:** never call set_thread_resolved on your own, even after a delivered result; leave the thread open until Josiah says it can be resolved.

---

### zomboid-no-self-resolve.md

---
name: zomboid-no-self-resolve
description: RULE from Josiah 2026-10-29: threads never resolve themselves unless he authorises it
metadata:
  type: feedback
  modified: 2026-10-29T01:44:58.731Z
---

Josiah 2026-10-29 01:44 (via the coordinator): no thread may resolve itself unless he authorises it.

**Why:** he wants threads to stay visible until he decides they're done.
**How to apply:** after delivering, leave the thread open (never call set_thread_resolved on your own). Resolve only when Josiah says so in that thread or through a cited message of his.

---

### zomboid-open-plan-rule.md

---
name: zomboid-open-plan-rule
description: Josiah wants big open floorplans in towers, not corridor mazes; seats must never face a wall; built 2026-10-29 as Floorplan.airy
metadata:
  type: feedback
  modified: 2026-10-29T08:45:23.378Z
---

Josiah 2026-10-29 08:29 (screenshot of a tower corridor with seats facing a room wall): "is there now way we can open these areas right up? have sofas pointing at a wall doesnt make sense. ideally we want nice big open floorplan".

**Why:** ring corridors with offices round the windows and rooms wrapped round the core read as cramped mazes, and seating facing a wall makes no sense.

**How to apply:** for public and work floors, use open plan: one big floor out to the glass, only a few glass-walled rooms, and only the essential rooms by the core. Bedrooms, flats, dorms, care-home rooms and separate firms keep walls. Never point a sofa or chair at a wall close up.

Built (playtest 55bdbc74f):
- `Floors.Fitter.airy(Type)` sets `Floorplan.airy`, which drops the corridor ring.
- `windows()` caps window rooms at 3-4 and makes them glass.
- `coreThen()` caps core rooms at the essentials and glasses the work rooms.
- `Floorplan.cut(..., cap)` stops a strip early.
- `Floorplan.place()` turns a seat round, or skips it, if a WALL, CORE or DOOR is 1-2 cells ahead.
- Glass room walls only go mid-run between plain WALL cells, which keeps the window check at 0.

Related: [[zomboid-floor-fitout]], [[zomboid-floor-purpose-rule]], [[zomboid-consistent-detail-rule]].

Refs ready 2026-10-29 20:06 for future floor work: refs/city/tower-interiors/{open-plan-offices, law-firm-floors, executive-offices, medical-dental, newsroom-callcentre, government-offices, hotel-rooms, hotel-amenities, cafeteria-gym}; more penthouse refs in refs/city/penthouse-crowns/{art-deco-crowns, observatory-domes, great-rooms, rooftop-pools-night, car-galleries, lounges-cellars, rooftop-terraces}. Thread paused after the request (Josiah 19:04).

---

### zomboid-population-direction.md

---
name: zomboid-population-direction
description: Josiah's 2026-10-27 rules for ZomboidCraft's zombie population (Zomboid-style density by place, no respawn by default, one shared count, towns full at world gen)
metadata:
  type: feedback
  modified: 2026-10-27T09:36:23.780Z
---

Josiah 2026-10-27 09:36, answering the horde engine plan (https://claude.ai/artifact/U5yxeaVBdABFCpq5LDond6):

1. Density like Project Zomboid, by place: downtown/cities and "landmarks" can hold THOUSANDS (ground level, every skyscraper floor, the subway); a small town a couple of hundred; zombies scattered between major areas but far thinner. "Its really dependant on the capabilities you have."
2. No respawn by default. A setting lets the player pick how many in-game days/months before respawning. With it off, cleared houses stay cleared, unless a door is open or broken and a zombie wanders in.
3. Every building gets its dead at world generation (towns start full).
4. ONE zombie count shared by all players on the map; every zombie is (technically) visible to every player the same way. No per-player caps.
5. OK to remove the steady spawner and vanilla night spawns for walkers once populations and herds take over.
6. Build order: "do what you think is best".

Josiah 09:37: "all major areas need to be the hotspots for zombies and they scale in zombie count depending on their function and size with downtown having the largest population of the whole map". Plan: size per place type + footprint from the county plan; show him the numbers table before spawning.

Sizing doc sent 2026-10-27 ~09:45: https://claude.ai/artifact/TgfqRgPiao5wQ5cxVWbLSU ("Zombie population: how many, where"): ~115,000 per world (seed 12345: 25 downtowns; metro 3,000, city 2,000, old city 1,400, county seat 450, main street 250, hamlet 80, suburbs 90-240, farms 5-12, ~10 per empty 512 cell), hotspot building bonuses, ~1/4 houses empty, population.density 0.25-4, shared real cap 250, real within 96 / fold past 112. Four questions at the end; awaiting answers. Counting tool: javac -sourcepath src/main/java County.java + a small main using County.placesNear (CountPlaces in old scratchpad).

Zomboid reference (looked up 09:50, community counts): whole Knox map ~75k-100k at 1x, Louisville 20k-40k; population multiplier 0-4 (Insane 4) with start/peak multipliers (~16x maxed).

DECISION Josiah 09:54-09:56: 20,000 for the GRAYHAVEN ISLAND CITY only ("our biggest landmark and most intricate", built by LANDSCAPE; every new part of it must be meticulously filled). Mainland procedural downtowns stay 3,000/2,000/1,400; world ~135k. Asked LANDSCAPE how code identifies the island. Earlier reading (superseded): downtown 20,000 ("massive"), on condition they fill buildings and skyscrapers, not just roads. Applied: metro 20,000, city 13,000, old city 9,400 (area-scaled; my reading), world ~430k. Split: ~55% towers/flats by floor space, 15% street, 15% shops/offices, 15% underground; records pinned to rooms. Indoor ones materialise only for your building/adjacent and nearby floors (+-2); downtown real cap ~400.

Josiah 10:01: table defaults OK as a base (scale later). HALF of houses empty. Zomboid-style zombies shut inside houses banging on doors when they hear you. Doors much stronger; break time scales with how many zombies bang on a door or window (shared hit points; solo ~2 min, 5 ~25 s, 10 ~12 s), settings to tune.

**Why:** a finite, Zomboid-like world where the first wave is huge and clearing an area means something.

**How to apply:** the initial population must be substantial; size it by place type (county plan, [[zomboid-county-plan]]), store most as records ([[zomboid-performance-tech]]), and keep a real-entity cap for performance, filled nearest-first around all players together. Roaming herds: [[zomboid-hordes-death-base-direction]].

---

### zomboid-purge-direction.md

---
name: zomboid-purge-direction
description: CONFIRMED re-steer (Josiah 2026-11-08): "purge the world", Halo Spartans/Tarkov operators vs Covenant-led Zomboid hordes; his answers to P1-P21
metadata:
  type: project
  modified: 2026-11-08T09:28:45.931Z
---

Josiah 2026-11-08 09:03: steer AWAY from Project Zomboid traits/skills/survival. Player = Halo Spartan or Tarkov operator (or a Noble member) purging Zomboid zombies led by Covenant. No world loot; upgradable base with gear-room displays; finite ammo from guarded pods.

Plan doc: https://claude.ai/artifact/92iDNQQ3vpJUMVT5gpcU8z (Docs id 4106406a-612d-4e84-8fe5-d6d7dddc9b63, body fba88dbe-3bf0). His answers are CAPITALS inline in the doc (09:28).

ANSWERS (all recommendations YES, plus):
- P1 persistent open purge + optional ops; sortie missions ALSO an option; firefight = RANDOM EVENT where Covenant attack your base in waves trying to take it.
- P2 hunt board on command net AND map districts shade purple->clear (lifts W22 no-map rule).
- P5 up to 3 AI squad mates; AI must be "very intuitive and smart".
- P6 Spartans immune, operators treatable (rare antiviral).
- P7 beacons from Grunts are ONE-TIME USE for players.
- P8 yes + pull ALL needed vehicles from files with complete fluid drop animations (Spirit/Phantom); also Elite DROP PODS from massive cruisers high in the sky.
- P9 Brutes out for now.
- Covenant AI must be very smart and a heavy challenge; asked whether the files hold AI info / campaign difficulty scaling.
- P13 Tarkov exceptions LIFTED: scanned heads, boss gear, Halloween/film masks, streamer items all allowed (overrides earlier face-cover-only rule).
- P14 NO Tarkov grid inventory: Spartans use the Reach HUD; operators a simplified own inventory with Tarkov-style HUD.
- P15 cross-use both ways staged; his idea: Spartan armour pieces on a Tarkov player model at true Spartan size (not shrunk), "let you figure that out".
- P16 racks auto-fill but displays placeable wherever and however the player chooses.
- P18 base raids yes but NOT all the time (players must get to explore).
- P19 ticked: Covenant pickups, Covenant pods, district caches, resupply call-in, fallen unit stories, squad runs, HVT bounties, ammo store trickle. NOT fixed military armouries (no world loot at all).
- P20 keep only health, injuries, bleeding, stamina. P21 a few survivors as escort rescue targets.

SUPERSEDES: TARKOV SWAP starter kit / Tarkov-melee-only / Tarkov-anims-for-everything (Spartans use Halo anims); ZOMBOID PARITY (now zombies + world look only); world loot + [[zomboid-tarkov-gun-loot]] (guns become pod/cache unlocks); survival needs, traits, skills, cooking/farming; Tarkov grid inventory ([[tarkov-gear-clothing-intake]]); most of [[zomboid-world-systems-tarkov]] (W1,W3,W4,W5,W10,W13,W18,W19,W22); [[zomboid-base-rooms-plan]] step 3 halted, sleep/cook/mood room effects retired.
STAYS: no vanilla, no Minecraft models, previews before builds, Halo/Tarkov local-only, saves to additions, hordes never vanish, Zomboid zombie behaviour, ammo counter.
Build order in doc section 10. Related: [[zomboid-halo-maps]], [[tarkov-ammo-counter-direction]].
BASE BUILDER (Josiah 2026-11-09 00:00): wants Reach FORGE (forge_halo.map palette) as the base builder. Proposed: place Forge pieces with snapping at base sites, rooms typed by contents (existing Rooms system), displays free-placed; replaces block building so no-digging Reach terrain stays. Next step offered: survey the Forge palette (every piece, preview sheet, sizes) before any build. LANDSCAPE has already converted forge_halo as a level ([[zomboid-reach-pipeline]]); placement list for LANDSCAPE: /mnt/project-files/zomboid-mod/purge/purge-world-placements.md.
- 00:01 Josiah: Forge bases YES, but KEEP interior customisation too: weapon racks, trophy displays, armour stands/mannequins and interior decor placed freely inside, not just the Forge shells.

---

### zomboid-reach-backdrops-direction.md

---
name: zomboid-reach-backdrops-direction
description: Josiah 2026-11-11 11:57 (his clock): unreachable distance should use the Reach missions' exact painted backdrops (cinematic far ranges) so the whole world has visible continuance, not bland procedural scenery
metadata:
  type: feedback
  modified: 2026-11-11T01:58:20.303Z
---
Josiah 2026-11-11 11:57: fine to do what Reach does for distant, unreachable backgrounds: painted cinematic backdrops (2D allowed if not noticeably flat, or other means) to avoid repetitive, bland scenery. 11:57 follow-up: "more than happy for you to just use the exact backdrops that reach campaign missions actually have so the entire world has a visible continuance to it".
**Why:** procedural horizon ranges (ReachHorizon ridged noise) look repetitive/bland next to Halo's own far land.
**How to apply:** draw each mission's own far-scenery pieces from its _000 (glacier, perimeter/mid/crater mountains, far/distant ridge rock, very_distant/distant/iconic/background mountains) where they sit beyond the walkable map, in their real mission positions; procedural horizon only as low ground under them. Preview before build. Survey of pieces + world extents done 2026-11-11 (m70 distant_mountains 58% outside the map, m20/m60 glacier, m52 background mountains, m10 crater/mid mountains, m30/m35 ridges).
Related: [[zomboid-reach-land-terrain-tech]], [[zomboid-reach-sky-direction]], [[zomboid-reach-whole-world-quality-rule]].
- Josiah 12:03: mission backdrops also have storms, weather and "battle scene" additions for the cinematic feel. These live in the sky glbs (m70 bolts/cov_sky_cruisers/distant_booms/fire_strips, m52 glassed city/debris, m35 contrails/booms/lightning, m60 glassing storm/steam, m10 rain columns, m45 debris field/glass effect). Game draws only the current region's sky; plan told to him: show each mission's sky set pieces over its own horizon stretch from anywhere, and make lightning/booms/glassing animate. Preview first.
- Built 2026-11-11 (commit 6819ff7cf on pt, NOT pushed until previews seen): ReachSkySets moves each campaign mission's set pieces (regex on material names, pieces nearer 1.5 km stay) to its bearing from world centre (m35 290, m30 328), squeezed to 56 deg; each piece its own Uploaded "skyset:", flash via frame m31 (reach_level.fsh FLASH). Horizon ground sinks 60 m where a level reaches the edge. Preview: scn/skyset.py + real.html multi sky + cams 8th value = time. Names: m70 = Pillar of Autumn (NOT Lone Wolf, that's m70_bonus); m60 = The Package; m50 Exodus; m52 New Alexandria.
- Holes fix 2026-11-11: scratch holefix/holefix.py fills MISSION cells no kept _000 triangle covers (5.93 km2, m52/m50 excluded) as LAND, heights = push-pull fill from ground round the hole (known heights eroded 64 m first). land_fixed.zrh md5 6f070eae, NOT staged (replaces 1ddf38ed). Boneyard views on artifact #yard (v6). Then holefix/overlap.py sinks LAND cells 1.5 m under Halo kept triangles that spill over them -> land_ov.zrh md5 7a0b054d = the land to stage (replaces 1ddf38ed). Game commit bb6847488: ReachLandMesh.sink(step) sinks every non-LAND vertex (not just near the map edge) = no jagged patches at level borders; preview scene.py same. INSTALLED 2026-11-11 20:15 his clock: jar bb68474 (sha256 b5bbb74d) in mods, live world\land.zrh = 7a0b054d, reach.json = be0247ac; backups in additions\jar-backups (endless-night-playtest-before-bb68474.jar, land-before-place4.zrh, reach-before-place4.json). Not yet seen in game. Artifact v7. Project storage full: new files in /mnt/project-files 404 on commit until space freed (Josiah OK'd deleting old transit copies 20:12).

---

### zomboid-reach-places-direction.md

---
name: zomboid-reach-places-direction
description: Reach world place decisions: New Alexandria/Exodus landmark towers + ground floors, Lone Wolf by the Pillar of Autumn, PoA walkable
metadata:
  type: project
  modified: 2026-11-09T20:23:23.305Z
---
Split from [[zomboid-reach-world-direction]] 2026-11-09.
- NEW ALEXANDRIA (Josiah 2026-11-09 02:32): city must be thick with buildings like the game. Plan told: game models drawn; city render meshes voxelized into walls (streets, shells, roofs); mission interiors enterable; swap thin skybox towers for detailed copies if needed (in PC plan file). DECIDED 02:36 (card): ~12 landmark towers get full interiors to the roof built from Reach interior pieces; every other building gets an enterable ground floor (lobby/shop/garage). Josiah: city must be highly detailed, imposing, elaborate, not scattered buildings on green ground. EXODUS (02:39): mission district is already walkable with Halo interiors; its far skyline gets the same treatment (landmark towers + ground floors), on the same shortlist.
- Josiah 2026-11-09 02:40 LONE WOLF: m70_bonus_epilogue (small ~230 m arena Halo hides ~1 km under m70, halo x1315-1547 z1450-1684 y-924..-762) must sit near the Pillar of Autumn; plan: lift to surface on high ground overlooking the PoA dock; show on climate/coast map.
- Josiah 23:27 PoA WALKABLE: Reach PoA hull at Aszod dock + interior rebuilt in Reach look from CE a10/d40 deck plan (plan file). Package underground lab (m20_042/060/080, ~1 km deep) sits under its valley.
- TOWER SHORTLIST (04:05, build log Preview 6): NA 1 ONI Bldg, 2 Ivory Tower, 3 Hospital, 4 Club Errera, 5 Arch (-885,-787), 6 Red-panel (433,391), 7 Aluminium spire (203,-451), 8 Vent (-1094,291), 9 Triple (-870,750), 10 Twin (548,-963), 11 Searchlight (-963,948), 12 Supertower (-2132,876); Exodus E1 Hologram (183,6), E2 Triple-crown (-121,-223), E3 Traxus (956,917). Halo coords. Interiors from ACTUAL mission floors (NA m52_*, Exodus m50_*), not Sword Base; mixed/mirrored, some wrecked, lifts+stairs.
- 05:40: floor-layout LIBRARY from interiors across ALL Reach missions, every tower varied floor to floor; shortlist taken as OK.
- PREVIEW 10 (#tower-floors): library 96 floors from 13 maps; tower fill = lift/stair core + rooms around it (rotate/mirror, no repeat within 5 storeys). Approved 07:00 ("looking good"). Scratch tools floorlib.py, towerfill.py, towerview.py.
- Josiah 2026-11-09 07:00: tower levels "looking good" (Preview 10 OK). THE PACKAGE ACCESS: replace the shore boardwalk with a LEDGE ROAD cut into the cliffs (zig-zag up from shore, short tunnels); underground lab reached by the facility's own lift (keep it). Show in next layout update.
- MULTIPLAYER MAPS: all 29 exported in PC additions\halo-reach\world\<map>, not yet in reach.json. Placement rules + Update 14 spots: [[zomboid-reach-mp-placement]]. Grand designs: [[zomboid-reach-grand-designs]].
- Josiah 2026-11-10 03:26 PoA INTERIOR: layout from Halo CE a10/d40 (collision already in cloud /tmp/claude-0/halo/ce), LOOK mapped off Halo 4's Forward Unto Dawn interiors (more up-to-date Halo ship interiors; he OK'd the 343 style for this). Needs Halo 4 Dawn mission map (m10_crash?) staged read-only from PC; Halo 4 sbsp (level geometry) not decoded yet (h4 tools read models/bitmaps/anims only).

- Towers/scenery 2026-11-10 decisions: [[zomboid-reach-towers-direction]].

---

### zomboid-reach-realism-direction.md

---
name: zomboid-reach-realism-direction
description: Josiah 2026-11-09 10:52-10:55: after the playtest fixes, top priority is Reach realism, "indistinguishable detail" from the real game
metadata:
  type: project
---
Josiah 2026-11-09 10:52-10:55: once the playtest bug fixes are done, move straight to adding all the real textures and everything needed so walking the Reach world feels like walking through the actual game. "indistinguishable detail is the key here".
**Why:** the first playtest looked white, foggy and flat (missing ground pictures + Halo's hidden lid at spawn); he wants it to match Reach exactly.
**How to apply:** use Halo's own data for everything visible (full-res diffuse, normal/bump, specular, detail maps, baked lighting, decals, foliage, props, skies, atmosphere, water). Halo files live only on his PC (MCC install + additions\halo-reach\split); cloud disk is ~3-4 GB free, so pull maps one at a time while his PC is on. He gave PC access windows (10:54: "another hour").
- Hidden collision fix 10:50: voxelize.py --materials drops shaders\invalid except ramps on the ground; spawn moved to y 48.5 (Sword Base courtyard). ENDLESS NIGHT\config folder connected 10:53 (his OK).
Related: [[zomboid-reach-world-tech]], [[zomboid-reach-world-direction]].

- 2026-11-09 11:28 build tour: https://claude.ai/artifact/UjWqKsSi6FKNWnyNK44RPP (shots in endless-night/reach-world/previews/tour-2026-11-09/). Issues seen: dark fog band hides land, backdrop undersides show, Winter Contingency trees white, Exodus untextured, PoA building blown white, flat lighting. Josiah wants shots viewable on phone (use an Artifact page; thread attachment cap is full).
- Screenshot trick: a fullscreen MC capture goes stale; toggle F11 twice before each screenshot. Use /gamemode spectator + /tp x y z yaw pitch, F1 hides the HUD.
- Josiah 11:34: "I want to not being able to tell the difference between what you've put together and the actual game"; work OVERNIGHT section by section with great time and detail, post previews as I go. Only LANDSCAPE (this thread) keeps working while others are paused (11:31).
- 2026-11-09 12:40 OVERNIGHT PREVIEW PAGE (keep updating, same URL): https://claude.ai/artifact/1YWUtHBKxMq6zA2MLw7xjt (source scratchpad reach-realism.html; Sword Base update 1 posted).
- Cloud reference renderer: /tmp/claude-0/halo/real/web/real.html (+lmdec.py, lmbake.py, lexport2.py, lmat.py, real/reexp.sh; shared textures real/tex via ZC_TEXLIB). Findings: bitmap curve byte at image+0x11 (1 xRGB, 3 linear); terrain base/detail maps look right only as gamma; sky detail honours linear. Bake needs crease-aware normals (thin fins went black). Sky = self-illum only, drawn at camera. Fixed exposure 2, sks 0.5.

- Josiah 2026-11-09 19:19: "Levels are looking great" (preview page). Wants the ENTIRE landscape (the land between/around levels, land.zrh + backdrops) brought to the same intricacy as the levels. PC on until ~21:15 that night for the level re-export.
- Josiah 2026-11-10 08:10 (decision card): Reach world is built around HALO'S OWN LOOK with Photon OFF (Photon cut far skyline/orbit ships and washed out Halo's baked light). Photon stays for the county world. Rain/fog for Reach to be done in the mod. With a pack on, the mod falls back to the plain look (commit 998727fe1). Next asked: detailed land between missions (land is still the 4 m first pass).
- Josiah 2026-11-11 07:19: "I want as much of the actual reach in the world as possible" -> picked bringing back Winter Contingency's real snowy valley (m10_000) over plain land, Noble Actual stays put; then the same around other missions (real _000 land preferred over generated land).

---

### zomboid-reach-sky-direction.md

---
name: zomboid-reach-sky-direction
description: Reach world sky/atmosphere (Josiah 2026-11-09): missions' ships in orbit, fog, light, battle effects in the shared world; each region keeps its mission's sky (chosen 04:06)
metadata:
  type: project
---
Josiah 2026-11-09 03:45: all campaign missions with ships in orbit and atmospheric elements (New Alexandria, Exodus, Lone Wolf etc.) must be in the world so the environment reads the same as the actual game.

Told him: each level's sky pieces (supercarrier/burning towers over New Alexandria, cruisers/dropships over Exodus, glassing + PoA launch sky, clouds, fog, light colour) are currently CUT by ReachLandMesh.clip (edges > 1.5 km). Plan: one shared sky, big ships as real objects at fixed spots visible world-wide, per-region fog/cloud/light/battle effects (Banshee flybys, flak, glassing beams).

Decision card 03:46, ANSWERED 04:06 = A (each region keeps its mission's sky and war stage, blended gradually): A each region keeps its mission's sky and war stage, blended gradually like the climate (RECOMMENDED); B one late-invasion moment everywhere; C sky escalates on a story timeline.

**Why:** he wants it to feel like the real game; flow rule [[zomboid-reach-terrain-rule]] applies to sky too.
**How to apply:** survey sky pieces per mission first (cloud background), preview before building, endless rain look still applies.

Next: sky regions map preview (which mission sky where, blend zones), then PC export of sky tags.
Sky regions map published 04:12 (build log Preview 7, #sky-regions; src scratch skymap2.py, backed up img in halo/land-build/07-sky-regions.jpg). FOG (Josiah 04:11: fog must allow supercarriers/distant cities to be seen): proposed layered fog = thick low ground fog thinning with height, sky objects/far skylines with own light haze (Halo-style), density by region. APPROVED by Josiah 04:15.
Josiah 04:13: landscape must never end suddenly leaving floating cities; land mesh covers whole 16 km, keep Halo far horizon beyond the edge. Vanilla projection far plane ~4x render distance (~500 m at his rd 8) cuts far meshes -> FAR DRAW PASS needed (like DH); Photon border fog uses render distance, check on PC. Cost answer 04:16: drawn meshes far cheaper than blocks; watch VRAM (LOD far towers/land, smaller far textures, DH off in Reach world).
FULL DETAIL (Josiah 04:20): no LOD/smaller textures for now, judge cost in game (he thinks old VRAM cost was blocks + Photon redraws). Land coarse-far LOD exists but OFF by default (-Dzomboidcraft.reach.landLod=<blocks> to enable), commit 958295040.
FAR PASS + HORIZON BUILT (2026-11-09 05:20, commits c30f97ad4 + 594e0f0d3, build log Preview 8 #horizon): ReachFarPlaneMixin (@ModifyArg on Camera.update -> setupPerspective(near, far, fov, w, h); far 48000, near 0.2 in Reach; depthFar raised before createProjectionMatrixForCulling) + Reach branch in FogRendererMixin (fog 900..22000; water/lava/blindness keep vanilla). Props -Dzomboidcraft.reach.far/near/fogStart/fogEnd. ReachHorizon: land past map edge 22 km (ranges west/south, sea north/east), zipper strip makes it watertight with the map's 8 m edge. Client mixins not checked by CI (injectors defaultRequire 0, silent if a target misses): look for log "Reach world: far plane" on PC. PC checks: Photon fog hiding far land, far z-fighting.
REGION SKIES BUILT 2026-11-09 06:05 (commit 55c307063, CI green, build log Preview 9 #region-skies): ReachSkies (assets/zomboidcraft/reach/sky_regions.zsk = skymap2 weights, 250x250 @64 m, 8 regions; stand-in colours/fog/dark per region until PC sky tags), ReachSkyState + ReachSkyMixin (EnvironmentAttributeProbe.getValue RETURN: SKY_COLOR, FOG_COLOR, SKY_LIGHT_FACTOR*(1-0.8dark), STAR_BRIGHTNESS), FogRendererMixin env fog = region fog (lifted with height). ReachLandMesh.clip drops FAR_SCENERY materials (glacier/background_mountain/distant_mountain/crater_mountain/distant_ridge/far_ridge) unless >=10% of tris touch ground.

---

### zomboid-reach-terrain-rule.md

---
name: zomboid-reach-terrain-rule
description: Reach world RULE (Josiah 2026-11-08 23:31): never flatten levels; keep every cliff, canyon, height change and building level; every drop needs a way back out
metadata:
  type: feedback
---
Josiah 2026-11-08 23:31: "make sure the levels aren't being modified to suit flat ground, I want all the height differences, cliffs, canyons, building levels etc". If a level's border is a cliff, keep the cliff (adds danger). If the player falls off, there must be another route out (no dead-end pits).
**Why:** he wants Reach's real terrain and danger, not a flattened open world.
**How to apply:** level meshes keep their exact heights; seams blend the backdrop land to the level, never the level to flat ground. Remove kill zones/invisible walls, but at every cliff base or canyon floor add at least one Reach-style way out (path, ramp, cave, lift, ladder) and check falls lead somewhere walkable. Also: he will look for Pillar of Autumn layout maps. Related: [[zomboid-reach-world-direction]].
FLOW RULE (Josiah 2026-11-09 02:38): different areas (snow meeting green valleys etc.) must never be abrupt; blend them gradually and place them so the whole map makes geographic sense: no snowscape next to a beach or desert. Plan told him: climate bands (cold glacier north: Package, Sword Base; temperate middle: Winter Contingency, Tip of the Spear, cities; dry south: Pillar of Autumn), 1-2 km transitions (snow thins with height, tundra -> grass, dry grass -> scrub -> badlands), one joined coastline; rotate/move missions if coasts don't line up; climate-map preview first. Preview 4 published 2026-11-09 ~02:55 (build log, section climate-map): north cold bay (Sword Base clifftop + glacier, Package glacier shore, Exodus south shore like a fjord town), sea down east coast past New Alexandria, temperate middle/west, dry scrub -> badlands -> desert south around PoA, Lone Wolf on high ground by PoA dock (~2300,6900 world). APPROVED by Josiah 2026-11-09 02:47 ('that layout is good'). Blue = open sea at sea level; BIG NORTH BAY KEPT (Josiah 02:53: keep the water since it's how Halo shows those areas; water must have a purpose). EVERY mission reachable on foot/vehicle (02:49): Sword Base plateau has ~1.5 km sea to the south mainland -> add land strip/isthmus with road to the green hills, keep its other cliffs; Package joined via glacier shore east to Exodus; every sea edge has beach/ramp/jetty climb-outs; in-between sea is new, joining the missions' own seas; cold bay gets rocky/ice shore, cliffs/harbours/beaches further south. Tool zomboid-mod/halo/tools/landrender/climap.py.
Josiah 2026-11-09 03:26 on Preview 5 (joined land): 'Looks great', likes the altitude changes ('really add depth'): keep big height variation in later passes.

---

### zomboid-reach-towers-direction.md

---
name: zomboid-reach-towers-direction
description: Josiah's 2026-11-10 decisions on New Alexandria tower floors (whole real floors) and mission scenery (every object), and review cadence
metadata:
  type: project
---
- Josiah 2026-11-10 04:02 TOWERS: picked WHOLE REAL FLOORS (not mixed-room fill, not procedural kit). Use New Alexandria's own fought-through interiors (several levels in several buildings) whole; fit each tower around the floors. Reflection (MP) penthouse idea SUPERSEDED 06:20 (see below).
- Josiah 04:03: the NA mission's own playable floors (many, several buildings) must be in the towers, and their EXACT parameters (footprint, storey height, cores, windows) are the guide for building ALL tower floors.
- Josiah 04:13 MISSION SCENERY: "every single detail accounted for... down to rocks, barrels, gun racks, anything and everything the missions have". Draw ALL scenario objects (scenery, crates, machines, plus vehicles, weapons, controls, equipment); pose characters/scripted craft rather than drop them.
- Josiah 04:37 after the Ivory Tower test (artifact BcQdAF3qTaxvTdGnBktfy4) and scenery preview (HXPQkGti34VwkioifKn3Ug): "Great work, get everything else done and we will review all at once". So: finish the remaining campaign list without per-item check-ins, then ONE combined review page.
Related: [[zomboid-reach-places-direction]], [[zomboid-reach-copies-tech]].
- Josiah 2026-11-10 06:20 on review page WeseZoaFLD7Kjja41f8ugL: (1) Reflection = keep Ivory floor, add Reflection's MP fittings in place (propsAlso, commit ae5487794; needs 52_ivory_tower_props.zip exported on PC); (2) MIXED floors from different buildings in one tower is fine; (3) ledge road: he'll judge in game. World file with 807 storeys: /mnt/project-files/endless-night/reach-world/towers/reach.world2.next.json (Triple, Twin, Supertower still need skyline meshes).

---

### zomboid-reach-whole-world-quality-rule.md

---
name: zomboid-reach-whole-world-quality-rule
description: STRICT (Josiah 2026-11-11 09:49 his clock): every visible, walkable or distant piece of Reach-world land gets real Reach terrain/textures/detail; no white patches, no missing sections
metadata:
  type: feedback
---
Josiah 2026-11-11 09:49: "Any and all visible, walkable or distant land needs the same level of reach terrain, textures and detail. We can't have random white patches or missing sections, the whole world needs to be of the same quality."
**Why:** cloud previews showed our generated land as flat colour (bleached white desert band) next to Halo's real far land around Tip of the Spear.
**How to apply:** the generated land (ReachLandMesh), the horizon land past the map edge (ReachHorizon) and anything else drawn must use Halo terrain shaders with real Reach textures (per climate sets, scn/landtex.py design), never plain vertex colour; check previews for holes/blank patches before showing. He picked "real Reach textures now" on the card 09:48.
Related: [[zomboid-reach-real-surroundings]], [[zomboid-reach-land-tech]], [[zomboid-reach-realism-direction]].

---

### zomboid-reach-world-direction.md

---
name: zomboid-reach-world-direction
description: Halo Reach world direction (2026-11-08): brand new separate 16 km open world, county world kept; New Alexandria city; endless rain; no digging; corvette
metadata:
  type: project
---
Josiah 2026-11-08 12:19 + 12:27:
- "happy to have the entire map changed to suit a halo reach landscape and have the downtown and city be turned into a new Alexandria style city internally and externally but still have the entire world encapsulated in the endless rain aesthetic we currently have".
- "I won't be wanting to dig into anything, there wont be any need to on this build now" -> no block terrain needed; real Halo meshes + invisible collision are fine for land too.
- Campaign missions (m05..m70) as places in the world; Covenant squad spots from mission AI placements could drive spawns.
- PLAN DOC (2026-11-08 12:45): https://claude.ai/artifact/AAttusu1HZswrkuKKx1tvX. Choices R1-R20 with my recs, answers in CAPITALS + summary table column. Build order: 1 Sword Base in game (render/Photon test) -> 2 survey all 44 maps -> 3 layout preview -> 4 first New Alexandria block -> 5 districts -> 6 gameplay layer -> 7 switch-over (old county kept as fallback).
- 19:06-19:07: pull every Reach map; build asap; ask PLANS what fills it.
- PLANS reply: placement list in /mnt/project-files/zomboid-mod/purge/purge-world-placements.md (base sites, pods, Covenant camps, Noble rescues, Powerhouse purge goal). OPEN: no-digging vs base building (ask with R12).
- Josiah 2026-11-08 20:39 COVENANT CRUISER: the m45 Long Night of Solace corvette must be in the world as a ship visible in the sky, with a ground beam-up point; fully end-to-end walkable, filled with everything the mission shows. Original is only partly walkable, so missing decks are to be built from m45 interiors + 45_aftship (Zealot) + ff45_corvette pieces. m45 needed a re-pull (collision missing, BSPs 020/025/090 failed).
- Josiah 21:05 'let's begin, I want maximum detail'; 21:08: maps are combined so landmarks will differ, but make everything FLOW TOGETHER as well as possible (blend seams, consistent terrain/sky/rain).
- Josiah 2026-11-08 21:30 SEPARATE WORLD (overrides 'entire map changed'): the current county world stays complete as ITS OWN package/world, untouched; the Reach world is a BRAND NEW world (own layout, not mapped onto county spots). R19 county-spot mapping and R16 fallback are superseded by this. Layout page: https://claude.ai/artifact/F94zL9sYymxQA7eN5Fu6Av (Anniversary layer off per R3). Scripts in /mnt/project-files/zomboid-mod/halo/tools/worldlay/. Atlas: https://claude.ai/artifact/2MKmXgXtvXcVDWz73z4es7.
- Josiah 21:57-21:59 OPEN WORLD: make it BIGGER (16 km agreed in my reply) if land is filled with Reach-style activities, settlements and mini bases; keep an open-world feel, NOT funnelled along mission routes; mission layouts still in full, complete size and detail. Plan: use each level's own backdrop landscape (15-35 km, coarse) as in-between land with an up-detail pass, remove the missions' invisible walls and kill zones, make backdrop collision walkable.
- Josiah 22:03: place ALL mission enemy spawn spots incl. vehicles, plus Zomboid zombies; get creative on the open ground and PITCH GRAND DESIGN IDEAS: intricate structures of human OR Covenant making (Covenant-erected bases, UNSC bases/areas) matching Reach aesthetic/build quality (22:13).
- Pipeline/PC layout: see [[zomboid-reach-pipeline]].
Related: [[zomboid-halo-maps]], [[zomboid-halo-swordbase]], [[zomboid-purge-direction]], [[zomboid-county-plan]]. Places: [[zomboid-reach-places-direction]]. Sky: [[zomboid-reach-sky-direction]].
- LAYOUT v3 16 km published 2026-11-08 22:20: each mission in its real backdrop land (164 km2), 421 squads (m10/m20/m45), 22 settlements/bases, grand-design starter list of 8 (to brainstorm with him). Package placed as snowy NE twin of Sword Base valley. Src zomboid-mod/halo/layout-page/.

---

### zomboid-refs-endless-night-switch.md

---
name: zomboid-refs-endless-night-switch
description: REFERENCES storage rule after the 2026-11-09 Endless Night switch (where new Reach-world photo sets go)
metadata:
  type: project
---
Josiah 2026-11-09 07:51/07:52: all Reach-world work moves to ENDLESS-NIGHT (repo, /mnt/project-files/endless-night/, ENDLESS NIGHT Modrinth instance). Rules: /mnt/project-files/endless-night/SWITCH.md.

**How to apply (REFERENCES):** photo sets for Reach/zombies/Halo/Tarkov now go under /mnt/project-files/endless-night/refs/<group>/<topic>/ with sources.md. The old library zomboid-mod/refs/ stays readable (county world, frozen). PC runs use C:\Dev\Zomboidcraft-Desktop\additions (never OneDrive) per [[zomboid-refs-collection]].

---

### zomboid-scope-zoom-direction.md

---
name: zomboid-scope-zoom-direction
description: Josiah's aim-zoom rule (2026-11-02): unmagnified sights get no zoom; magnified scopes want picture-in-picture lens zoom with mild outside blur
metadata:
  type: feedback
  modified: 2026-11-02T20:00:12.831Z
---

Josiah 2026-11-02 19:57-19:59 (survivor arms thread): iron sights, red dots and anything without magnification "should stay standard": no FOV zoom when aiming (today stats.aimZoom 0.78-0.88 via Crosshair.java / AbstractClientPlayerMixin). Magnified scopes: he prefers picture-in-picture (world outside stays normal, only the lens zoomed) with a mild blur outside, used as cover to draw the outside cheaper.

**Why:** keep side vision and a natural look; FPS is the top priority.
**How to apply:** weapons work (passed to WEAPONS NEW thread). PiP with Iris+Photon is unproven; test with Photon on first. Fallback = cropped centre zoom inside the lens. First-person gun is drawn at fixed 70 deg, so the arms hold ([[zomboid-arms-posing-tool]]) is unaffected by FOV or zoom.
- 2026-11-02 20:06: irons/red dots 1x merged (AbstractClientPlayerMixin only narrows when Attachments.aimZoom > 0; Crosshair matches), playtest 4e889e4. PiP mock-up (A PiP 4x + blur, B crop fallback) at /mnt/project-files/zomboid-mod/brita/previews/scope-pip-mockup.mp4; awaiting his tweaks before a PC test.
- 20:58: posing tool v7 has "Scope lens size" (ADS only, 25-80%, default 50) -> db poses/current numbers.guns[id].ads.lens (fraction of screen height). Will move to numbers.guns[id].ads.<class>.lens when the arms thread adds sight classes. Magnification stays exact; lens size only changes how much field shows. 14-scope 3D test video rendering from scratchpad pip3d (page.html/run.js, three.js + sights.js reticles at 3x).
- 2026-11-03 02:22: Josiah says assault rifle (AssaultRifle2) and Colt1911 are set in the posing tool. Lens sizes then: AssaultRifle2 0.5 (default), Colt1911 0.52, AAC_Honey 0.8. Template rollout to other guns belongs to the survivor arms thread, not this one.
- 2026-11-04 05:51-05:57: under Photon the whole screen zoomed (my 7c8df1611 fallback). True PiP is impossible with Iris (world drawn once per frame). He chose "Build both behind a setting": look.scopeShaders "crop" (A, default: live 1x view, lens = HUD-time copy of the finished frame blown up, drawn with GUI_OPAQUE_TEXTURED_BACKGROUND via GuiGraphicsBlitInvoker because shader packs likely leave alpha 0, the suspected reason kept frames never showed) or "ring" (B: whole view zoomed, outside 0xEA dark). Key ";" (key.zomboidcraft.scope_style) swaps; U was taken by voice chat mute. Playtest 527c781, awaiting his PC test. Mock-ups: artifact WwzbitsjEnFqZqGJicJLZG.

---

### zomboid-shion-declined.md

---
name: zomboid-shion-declined
description: Shion (Overwatch-ripped character pack): local-only, CLOTHED looks only; preview artifact, Mixamo T-pose, pipeline in scratch bpy (2026-10-30)
metadata:
  type: project
  modified: 2026-10-30T06:33:16.784Z
---

2026-10-30 05:46. Josiah sent Shion-1.0.1.zip (a Cloudrig .blend) and Shion-1.0.0_textures_0TOW5JO.zip (157 MB, in the project files root).

Source: the textures were captured from Overwatch.exe and the file uses the OWM importer. It also carries other ripped heroes' leftovers (Domina, Vendetta, Sierra rig, a Leon-RE9-Rig collection). It's a nude mod: Nude_Body, "Revealed" bra and panties, Futa variants, nipple piercings, breast physics.

05:49 decision: Josiah asked for the same treatment as Master Chief and the Warthog, so she's LOCAL ONLY (never in the repo or jar). CLOTHED ONLY. He asked for a /zc "revealed" command and then for how to add those pieces himself; both were DECLINED. Futa is trashed at his request.

Looks built: default (full suit), open jacket, no jacket, jacket + gym shorts, sports bra + shorts, and base body (sports bra + shorts, no robot parts; this is the Mixamo base). DROPPED because they showed too much: ripped trousers, rolled trousers, and the thong "sports set".

Piece toggles: horns, long strands, thigh harness, pistol, collar, straps, earring, belly piercing.

Safety net: paint.py paints the body texture plain skin everywhere except triangles visible in some look and not under the bra or shorts.

Pipeline (scratch shion/, bpy 5.2.3 wheel in scratch bpyenv):
- common.py remaps the Windows texture paths.
- Rig props drive everything. They live on pose bones Properties_Skin_Default and Properties_Accessories: Suit_Jacket 0-2, Pants 0-3, SportsBra 2=normal and 1=revealed, Accessories Pants 1=gym and 2=short.
- bake.py: Cycles DIFFUSE COLOR bake per material. Objects hidden by drivers fail; those fall back to the raw diffuse.
- looks.py: geo.json/bin, Y-up, deduped meshes.
- mktex.py: builds the site files.
- tpose.py: aims FK-UpperArm/Forearm/Hand to horizontal and exports the FBX.

Preview: https://claude.ai/artifact/LUE9X3xwNMfDcpHLJ5XSR7.

Mixamo upload: zomboid-mod/assets/shion/shion_tpose_mixamo.zip.

Next: he uploads it to Mixamo and sends the 8 clips (same list as the woman). Then pack shion.zsk with pieces as toggles, and install it via folder access.

07:28 v2: SportsBra/shorts colours now baked (bake.py removes hide drivers first); paint.py fills masked skin by normalised blur (no flat patches). ALL attachments (horns, strands, harness, pistol, collar, straps, earring, belly, boots) export in every look; page opens each look with its natural pieces (geo.json 'natural'), any can be toggled. Mixamo zip rebuilt.
07:37 v3: looks exported as 'look|1' (boots) and 'look|0' (bare-foot: robotic feet or body feet), geo.json boots{} = natural boots; page swaps list on the Boots toggle. Mixamo zip = MINIMAL (Josiah: as little clothing as possible): body + sports bra + shorts + eyes/teeth/nails/brows, no hair/collar/piercings, colour attributes stripped. Hair etc get weights via add_pieces-style transfer when packing.
08:10 v4 HUMAN LIMBS (Josiah: no robotic limbs in any outfit, human limbs and feet): looks.py sets Thight_Robotic/Robotic_Knee False, skips Robotic_Hands/LowerArm/LowerArm_Jacket/Legs_* objects, and trims the body's Mask_Shion_Robotic_UpperBody vertex group to skin within 1 cm of the torso suit (it used to hide her forearms/hands). Torso bodysuit, chest plate, head/neck pieces kept. Extra Mixamo clips coming (start/stop walking) to smooth her animator.
08:35 IN GAME (merge 2e7bf78dc to playtest): 17 Mixamo clips (SHION MIXAMO.zip), 16 packed: idle, walk, run, die, aim, fire (stationary), rifle_walk, walk_start/walk_stop/rifle_stop/aim_up/aim_down (one-offs, root motion stripped), fire_walk, rifle_idle, fidget (Catwalk twist), pose (Female Standing Pose held 6 s). "Walking" left out. shion.zsk 10 MB installed in his npc folder; copy + all scripts at project files zomboid-mod/assets/shion/ (tools/: tlooks.py T-pose looks, align.py, build.py IDW weights from the Mixamo body, shion_spec.json, dec.py + index.html checker). Groups carry piece "name@look|1,look|0,..." (|0 = shoes off); client NpcSkin.lookKey picks the look from a "look=NAME" entry in HIDDEN. Server Npcs.dress(model) reads pieces/looks from the .zsk; Npcs.dresses() = has woman wardrobe pieces (only then NpcWardrobe runs). export.html fixed: inverse binds without bindMatrix, cm uploads scaled to metres, ?clips= list.
09:20 CRASH FIX: texture keys must match [a-z0-9/._-] (Identifier). build.py now writes body_nude_1001 not body_nude@1001; shion.zsk repacked + reinstalled; NpcSkin.texture now sanitises keys (playtest d6952a822). NOTE this file's name says "declined" but Shion IS in game, local-only and clothed; only revealing variants were declined.

---

### zomboid-spawn-select-direction.md

---
name: zomboid-spawn-select-direction
description: Josiah wants a Zomboid-style chosen spawn: pick a town or city, start inside a zombie-free house or building there (not started)
metadata:
  type: project
  modified: 2026-10-25T21:55:09.496Z
---

Josiah 2026-10-25 21:50 (weapons thread): "I want to try and set up the same 'chosen spawn general location' where you can select what town or city you want to be dropped into and it places you inside a house or building (that isn't occupied by zombies) at that location much the same way as Zomboid has a new player starting."

**Why:** like Project Zomboid's new-character spawn picker. It is also why loot must not get better with distance from spawn ([[zomboid-loot-rarity-direction]]).

**How to apply:** not started and not yet given a thread (as of 2026-10-25 22:00). It touches the county generator ([[zomboid-world-tech]], [[zomboid-county-plan]]): a list of named towns and cities, a screen to choose one on first join, and a pick of a cleared building interior with no zombies inside. Discuss options with him first.

---

### zomboid-subway-tube-direction.md

---
name: zomboid-subway-tube-direction
description: Josiah's ask (2026-10-27 18:41) for a see-through underwater subway tube with an in-world reason; pitch and open decision
metadata:
  type: project
  modified: 2026-10-27T18:42:50.720Z
---

Josiah 2026-10-27 18:41: "work out a way to have the subway travel through the water (an underwater subway tube that's see through, not physically swimming) and have a reason for doing so."

Pitch sent (LANDSCAPE thread, 18:4x):
- The tube is glass panels between steel ribs on the harbour floor. The train runs dry inside it.
- In-world reason: the 80s "Glass Line" showpiece crossing, the subway's way from Grayhaven Island to the mainland. Now it is the way off the island that isn't a jammed bridge.
- Dressing: murky dark-green water lit by the train, cracked weeping panels (some patched with plywood or tarp), cars that went off the bridge, sunk boats, zombies shuffling on the bottom and pressing on the glass (they can't swim).
- Needs a new mainland stop. Dig the bed out where the channel is too shallow.
- Decision card options: island to mainland (recommended), a branch to an underwater marine-lab station on its own rock (a hard place with rare loot), or both.

Josiah picked ISLAND TO MAINLAND (2026-10-27 18:45). Order: town stations, Union train hall, then the tube (send a route map for the crossing first). The subway itself: [[zomboid-rail-tech]]. The train vehicle belongs to the TRAINS thread: [[zomboid-trains-tech]].

- Josiah 18:46: the channel must be substantial so the ride isn't a couple of seconds; it should be scenic. Promised: about a minute under water (~500-600 blocks at subway speed), curving down the channel past bridge piers, wrecks and big ships where the route can reach them; widen/deepen the channel so the tube sits well down in open water; route map with ride time before building.
- Route plan, map and numbers: [[zomboid-glass-line]].

---

### zomboid-survivor-lead-rule.md

---
name: zomboid-survivor-lead-rule
description: RULE (Josiah 2026-11-01 08:06): the Minecraft player model must never drive the Zomboid survivor body; plus render-order facts for survivors on 26.3
metadata:
  type: feedback
  modified: 2026-11-01T08:08:46.582Z
---

Josiah 2026-11-01 08:06: "we need to fix and overwrite anywhere that the minecraft player model effects the zomboid models".

**Why:** the survivor's rifle idle and aim copied the Minecraft hold (her hands reached for where HoldPoses put the gun), which looked wrong.
**How to apply:** Zomboid's clips lead the pose. The vanilla arm follows her hand so held items draw there, never the other way. Long guns are laid from her shooting-hand grip toward her other hand, and the other hand takes the real foregrip (Survivors.after, commit bdc2c5c2d). CarHands (steering wheel) still reaches, because that's car geometry. Josiah 08:40: no sign of the Minecraft character model anywhere, and first-person arms must match the chosen sex. Done in playtest c4bccb17d: SurvivorArms draws her arms from her pack (all bones but the arms collapsed to the shoulders, IK to the grips) for RifleArms, CarArms (wheel, aircraft, mounted gun) and vanilla renderPlayerHand (FirstPersonSurvivorHandMixin). Nothing is drawn while her skin composes. Placement is untested in game.

Facts (26.3, checked by javap probe):
- LivingEntityRenderer.submit only queues the model. setupAnim runs at draw time (ModelFeatureRenderer.prepareModel), after the layers have submitted.
- LivingEntityRendererSurvivorMixin calls setupAnim at submit HEAD for survivor players. Without it the body was invisible.
- Part submits (submitModelPart) read the ModelPart state at draw time. The first-person rifle arms therefore use their own baked PlayerModel (combat/ArmModels).

Related: [[zomboid-survivor-clothes-tech]], [[zomboid-playtest-build]].

STATE 2026-11-02 07:35: branch claude/project-thread-asz16x at c0a0f1ed (CI green), NOT in playtest; awaiting Josiah's look at preview run 6 (additions\survivor-shots\f) and his "merge". In it: hands take Zomboid's clip grip turned onto the real gun (RifleView passes want.q()); palm middle measured from the hand mesh (SurvivorPack.fist, PZ hand bone sits back in the wrist); long gun's support hand keeps the clip wrist; no square belt on survivors (PZ Belt has no model); appearance preview faces forward. Preview test SurvivorShotsTest runs on his PC via Remote Control (zc-preview setup, pack watcher; pass with his mods + Photon on). Appearance-page Minecraft head never reproduced, even with Photon on. CAUSE FOUND 2026-11-04: SpawnPicker keeps a new survivor in SPECTATOR until they wake; vanilla draws a spectator as a bare head and skips layers, and Survivors skipped spectators. Fix 356c43361 (playtest-based, cherry-picked to branch 5435164dc): InventoryScreenMixin clears avatar.isSpectator for your own figure while a screen is open and notes it as Survivors.screenFigure; Survivors.hiddenSpectator lets only that figure draw. Also cleared isInvisible (d16edf159). Confirmed working by Josiah 2026-11-06. Then (his 11-06 ask) page figure stands still + drag-to-turn via SpinFigure.draw(...lift...) overload, playtest e05afa33f; my branch's own older drag code in AppearanceScreen is superseded (playtest's version is authoritative). Folder staging of the shots gets blocked after a Remote Control session ends; he can open them himself.

---

### zomboid-thread-resolve-rule.md

---
name: zomboid-thread-resolve-rule
description: RULE (Josiah 2026-10-29) - no thread resolves itself unless he authorises it
metadata:
  type: feedback
---

Josiah 2026-10-29 01:44: threads must never resolve themselves unless he authorises it.

**Why:** he wants to decide when work is finished, not have threads vanish from his list.
**How to apply:** after delivering a result, leave the thread open; call set_thread_resolved only after he explicitly says to resolve/close it.

---

### zomboid-throwables-direction.md

---
name: zomboid-throwables-direction
description: ZomboidCraft throwables (Josiah picked them as next weapons work 2026-10-27 22:17): plan doc, roster, open questions, reference photo request
metadata:
  type: project
---

Josiah 2026-10-27 22:17 picked "Throwables" (over gun wave two, gun care/repair, more attachments) on the WEAPONS card.

- Plan doc (Claude Doc): https://claude.ai/artifact/8TBkissdo4THMexsw8QYxy "ZomboidCraft throwables plan". Roster of 10 (pipe bomb OUT FOR GOOD 2026-10-29, never model/name it): glass bottle, tin can, brick, firecracker string, road flare, wind-up alarm clock (Common/Scarce lures), molotov, smoke grenade, flashbang, frag grenade (Prized, military only). Hold right-click to wind up with arc preview; fuse starts at wind-up; quick-use wheel throws from belt; noise via zombie/Noise.emit.
- APPROVED 2026-10-27 22:25: all my picks (all 11, hold-to-aim arc, friendly fire with config switch, craftable homemade ones once WORLD LOOT has ingredients, build order lures -> molotov/smoke -> blasts) EXCEPT blast damage: blasts break NO blocks; they start fires across the blast radius. RULE: every fire (blast, molotov, flare) is CONTAINED: spreads only a few blocks past its origin and burns out after a set time (can gut a room/house, never the world); rain, water, extinguisher put it out; limits in /zc config.
- Reference photos requested 22:18 into refs/items/throwables/{molotov,frag-grenade,smoke-grenade,road-flare,alarm-clock,firecrackers,thrown-junk,flashbang}; WORLD LOOT thread fetches them (its container reaches Wikimedia). See [[zomboid-photo-reference-rule]].
- Crafting ingredients (fuel, rags, pipes, fertiliser) belong to the WORLD LOOT thread ([[zomboid-rolls-tech]] notes the split).
- BUILT 2026-10-27 on guns-wave1 (5e11dc1a2, CI green, NOT in playtest): combat/throwing/{Throwing,Thrown,ThrowKind,ThrowableItem,ContainedFire(+Block),Smoke}, client/throwing/{ThrownRenderer,ThrowEffects}. fireReach default 2. tin_can and alarm_clock are throwable (clock: sneak+RMB sets timer, thrown clock waits its timer). Items use vanilla stand-in models until photo-based models exist. Asked PLAYERS for Dismember.blast, AUDIO for weapon.throw.* takes (fire_burn repeats every 3 s), WORLD LOOT places items once in playtest.
- Models: tools/weapons/throwables.py (1 unit = 1.5 cm, display sizes from real cm; preview via scratchpad itemview/shot.js). Frag (M26 lemon shape, photo 04) and molotov done 2026-10-28; rest wait on refs/items/throwables/* folders. MERGE RULE (AUDIO 00:06): when guns-wave1 meets AUDIO's branch in playtest, sounds.json keeps AUDIO's 16 weapon.throw.* entries, ours only for pin_pull and ear_ring.
- PAUSED 2026-10-28 01:17 by Josiah: throwables. Resume from the zomboid-throwables-direction state (frag+molotov models done, 7 waiting on photos, then merge into playtest).
- RESUMED 2026-10-28 08:06 (Josiah). Bottle (beer bottle shape, drinks photo 07) + brick (standard US red brick) models done ea1e7fac2. Remaining 5 (smoke, flashbang, pipe bomb, flare, firecrackers) wait on refs. Thread messaging tools were down at 08:10; resume message to Josiah NOT yet sent.

- 2026-10-29 (thread "Finish the weapons work", branch claude/project-thread-5nbhdi): pipe bomb removed from code/config/lang/items (8097aa0e7, 4f4d3f771); smoke grenade, flashbang, road flare, firecracker models added (shared fuze_top helper); tin can + alarm clock keep their building models. All throwables MERGED INTO PLAYTEST 818c2d570 (sounds.json kept AUDIO's throw entries + ours pin_pull/ear_ring). Preview renderer: scratch render.py (software render of item model JSON).

---

### zomboid-trains-direction.md

---
name: zomboid-trains-direction
description: Josiah's decisions on driverless walk-in trains, call buttons with flare/siren, robot conductor, and the island downtown with bridges (2026-10-27); ideas doc link and build order
metadata:
  type: project
  modified: 2026-10-27T07:17:19.559Z
---

**The ask (Josiah, 2026-10-27, 06:43-07:15)**
- Working trains on a county railway to every town and major place, with stations scattered around.
- A subway that runs only inside a much bigger downtown, between landmark stops.
- A true-scale football stadium is the first landmark.

**Ideas doc:** https://claude.ai/artifact/DgHbLUEQ1Xz5HgpxqBAS3K (Claude Doc). The section "Your answers" holds his decisions.

**Decisions (07:15)**
- **Driverless:** each train loops its own route, run by a robot conductor. It is our own character (working name OTTO), inspired by the Black Ops 2 TranZit bus driver. Use no franchise names.
- **Walk inside while it moves:** this needs custom player-carrying code, like the Create mod's trains. Car size is about 22 x 4 blocks with 3 blocks of headroom.
- **Call button at every station:**
  - The train skips other stops to come to you, unless someone nearer on the line pressed first.
  - Above ground, the cost is a flare that draws nearby zombies. In the subway it is a siren that pulls zombies down into the station.
- **At a stop:** the doors open by themselves and stay open 30 seconds, then close.
- **Speed changes:** gradual acceleration and braking.
- **Downtown:** a New York style island city, cut off by wide channels and reached by big bridges. One bridge carries the railway. He said to make it "as massive as you need".
- **World size:** I advised against stretching the whole world. The island becomes its own big region and the towns keep their scale. He has not answered that yet.

**Build order (proposed)**
1. Test track with a walk-in train, OTTO, the call button and the doors.
2. Fixed county plan with the island, channels and bridges (see [[zomboid-county-plan]]).
3. The island downtown, stadium first.
4. Subway and railway lines with their stations.
5. The other landmarks.

**Stops (Josiah 07:21):** subway stops are Grayhaven Stadium, Union Station, Mercy General, Waterfront, Broadcast Tower and Power Station. The other landmarks from the ideas doc are scattered around the city (not stops). Lower the train speed slightly so the view from the ride is enjoyed. Subway stops well spread apart, NOT all on the island's outskirts: subway runs through the middle too (Union Station is the ONLY interchange between subway and above-ground railway, central; Waterfront on the island edge). Draw it on the county plan page first so he can move stops. Railway is ELEVATED only inside the island city (like the current elevated line); after the bridge to the mainland it runs at ground level (Josiah 07:23).

**Thread split (Josiah 07:21, "yes make a train thread"):** a separate TRAINS thread builds the vehicle and its systems (walk-in cars, OTTO, call buttons, doors, speed curve, test track). LANDSCAPE keeps the world side: island downtown, channels, bridges (one carries the railway), stadium and landmarks, track beds, tunnels and station shells. TRAINS should send LANDSCAPE the gauge, car size and platform size.

---

### zomboid-weapons-direction.md

---
name: zomboid-weapons-direction
description: Josiah's direction for ZomboidCraft weapons and combat (branch combat, started 2026-10-25)
metadata:
  type: feedback
---

Started 2026-10-25 on branch `combat`.
- Assault rifle is the base system for all guns (mags, ammo, reload, recoil, ADS, noise). Real recorded sounds (CC0 preferred) with a credits file. "As detailed as possible": 3D models from REAL measurements, blocky style, not smooth meshes. Previews before shipping, every number in settings.
- Melee roster of 15 (bat, crowbar, knife, machete, hammer, pan, golf club, pipe, wrench, fire axe, sledge, shovel, hockey stick, spiked bat, katana) plus stamina, knockdown/stomp, shove, grabs, crawlers.
- Rifle shot = take 3 (SuperPhat 421710) for everyone, loud. Attachments: mounting an optic folds the iron sights. Realism pass he chose: "Finer detail + real materials" (2x geometry from real dimensions + LabPBR _n/_s).
- True-to-life third-person holds (low ready, shouldered aim, two-handed melee) in tools/weapons/poses.py + HoldPoses. Two-handed straight-arm pistol hold still to do.
- Never buy model packs (licences forbid redistribution in a public repo); catalogues are shopping lists only.
- Modular guns: weapon tab shows empty slots as green translucent ghosts. Attachments API (combat 0296b9a): Attachments.Slot {OPTIC, MUZZLE, UNDERBARREL, SIDE_RAIL, STOCK, MAGAZINE, BIPOD}; slots/get/slotOf/fits/canMount/mount/unmount; data in assets/zomboidcraft/gun_slots/<gun>.json. See [[zomboid-inventory-ui-direction]].
- Loot crates he likes: pistol case, long rifle case, ammo can, military footlocker (foam inside, scuffed). He chose to CARRY cases like Zomboid (off hand, found packed); see [[zomboid-cases-tech]].
- Sight pictures: red dot/holo big and uncrowded (drawn closer to the eye when aiming, MRO-style red dot, shallow holo hood); 4x stays a sniper overlay. He approved: "much better sight pictures well done". Stock left as is unless he asks to slim it.
- Aimed views raised to bench detail (texture density from the aiming eye, rifle sheet 2048px). All 15 melee weapons have LabPBR maps (pbr.melee_layers, MELEE_PBR kinds in weapons.py, 16 px/unit, porosity for rain).
- Previews: Gunsmith Bench (rifle) https://claude.ai/artifact/AEwQwvHABaZw7EvgLs9y6Q; Armoury (melee, shaders on/off) https://claude.ai/artifact/NMxfr8BG9BAruWfKkg4Bj3. Photon needs Materials > Normal/Specular Mapping on to show the maps ([[photon-shader-settings]]).
- First-person guns: his own skin's arms (RifleArms), a real reload (old mag drops away first, charging handle only on empty or jam), very light ADS sway, separate ADS and 4x sensitivity sliders, L light / J laser with a faint beam. He wants every one of these toggleable.
- Tech: [[zomboid-guns-tech]].
- In-hand look (Josiah 2026-10-25: weapons were too small and drifted right; guns sat low): melee first-person pose is solved in weapons.py (FP_* constants: hand at screen 0.47,-0.82, leaning toward the crosshair, scale 0.52-0.92, pan spun and capped). Rifle hip: HIP_GRIP/HIP_TURN/HIP_SCALE. Gun PBR (fixed 2026-10-25 after he saw silver twice): finishes are non-metal satin (~0.44); never mark worn edges as named metals (230-237 shine silver whatever the albedo) and keep wear rims thin, or mipmaps turn the whole gun grey. Steel parts use tinted metal (255) with dark albedo. Shipped in playtest a5fafd4.
- Art style (Josiah 2026-10-25 23:45): the rifle remade in the characters' Borderlands-like block style. Preview https://claude.ai/artifact/5QwwMvHvoMPKSSn7syQLCf. Follows [[zomboid-clothing-style]] incl. the 00:02 outline rule: thin outline only round each part/attachment, no seams inside a part, edge wear as chips not lines. 00:40 he loved it: ALL guns and attachments rebuilt in game in this style (black mag), shipped 01:47. 01:56: every gun and attachment LIKE NEW always (no wear by condition); revolver reshaped from the real Model 10. Tech and rule: [[zomboid-gun-art-style]]. Step 3 (perk columns) still on hold.

---

### zomboid-weather-direction.md

---
name: zomboid-weather-direction
description: Josiah's weather/atmosphere direction for ZomboidCraft: "endless night" days, thin see-through rain; WEATHER/ATMOSPHERE thread
metadata:
  type: feedback
  modified: 2026-10-26T01:30:38.160Z
---

(2026-10-26 01:29, WEATHER/ATMOSPHERE thread) Josiah wants an "endless night" vibe: day and night should not differ much. Day a bit brighter than night, but clouds block the sun so it stays gloomy and dark, and light fixtures (froglights etc.) look as pronounced by day as at night. This sits on top of the permanent rain.

He also wants rain that is NOT big rectangles filling the screen: thinner, more realistic, easy to see through, only obscuring sight when it is very heavy.

**Why:** moody, dark world; the city lights are the showpiece (see [[josiah-world-art-direction]]).

**How to apply:** the existing gloom is world/weather/WeatherLayers.java with ZcConfig.Weather daylight (0.55) and nightLight (0.8). Proposed: daylight about 0.25-0.30 with a cold blue-grey tint, nights only slightly darker, Photon Sunlight/Skylight about 0.5. Rain: he runs Particle Rain + Photon (see [[photon-shader-settings]]). Suggested Particle Rain "hide vanilla rain" + streaks, Photon rain opacity about 0.25, and offered a mod-shipped thin rain texture. Awaiting his go-ahead and a rain screenshot (01:3x).

(2026-10-26 01:31) He also approved fog, wind, city light atmosphere and storms as directions. City lights: like Cyberpunk's colourful neon reflections on wet streets, but NOT overpowering; the dark moody look comes first. Plan told to him: sparse neon (about one sign/strip per block), muted faded magenta/cyan/teal/amber with flicker and dead tubes, densest downtown, rare in the countryside; preview sheet first; city placement coordinated with the landscape thread. Order: endless night + thin rain texture first, then neon preview, then fog, wind, storms.

Shipped 2026-10-26 (branch claude/project-thread-vigsiq, merged to playtest 04a2de07): weather.endlessNight (default true) + weather.endlessDaylight 0.30 replace the old weather.daylight (old configs keep a stale daylight key, ignored); SKY_LIGHT_LEVEL (game logic) still uses the 0.55 overcast day so spawning/zombie sight didn't change. Rain texture: assets/minecraft/textures/environment/rain.png from tools/weather/gen_rain.py (9 hairline streaks per 64x256 tile). Neon concept painter: tools/weather/neon_preview.py -> project-files zomboid-mod/previews/neon-street-{night,day}.jpg, awaiting his verdict. Face-rain HUD overlay belongs to the inventory/UI thread, not here.

(2026-10-26 02:0x) City neon shipped to playtest acdae3f3: Josiah said sign words must match their building; light = minecraft:pearlescent_froglight (landscape thread confirmed), dotted through buildings and the odd skyscraper window. Code: Signs.bladeFor(Shops.Type)/bladeLength/glow, ShopLights.bladeGlow + window(hash) in world/city; Tower.roomGlow (0.0025/window row), Walkup.glowingRooms (0.02); Storefront blades for new trades. New blades guns/wash/liquor/pizza/cash in tools/city/signs.py; blade colours limited to the 3 froglight colours. Preview tools: vanilla.py froglights emit, view.html colours froglight light (FROG map). Landscape thread is rebuilding ships: stay out of Ships, PortSite, Harbour. Next in this thread: fog under Photon, wind, storms.

(2026-10-26 ~02:45) Fog + wind shipped to playtest 3277617e: particle types zomboidcraft:mist/debris (world/weather/WeatherParticles), client/weather/WeatherFx spawns them (MistParticle, DebrisParticle; 26.3 API = SingleQuadParticle + Layer.TRANSLUCENT, Fabric ParticleProviderRegistry). Mist strength travels in addParticle's dy. Config weather.mist / mistAmount / windDebris. Textures from tools/weather/gen_particles.py. Next: storms (sky flashes, thunder).

(2026-10-26 02:32) Josiah: lightning must LIGHT UP the surroundings for every flash, ground strike or cloud flash. Shipped in playtest 3eb9dd6c: WeatherView.flash (client) drives WeatherLayers (sky light factor, sky and fog colour toward cold white); WeatherFx storms = sheet flashes + visual-only client bolts (ids < 0, ClientLevel.addEntity, setSilent) + delayed thunder (server's random distant thunder removed); ClientEntityEvents.ENTITY_LOAD flashes on real bolts. Untested under Photon (hidden far bolt hoped to trigger Photon's lightning flash). Config weather.stormFlashes.

(2026-10-26 04:2x) Round 2 shipped to playtest 3d24c4b6 (Josiah approved items 1,2,3,5; NOT soaked/cold or flooding). client/weather: Water (edge drips, ext_downspout_shoe pours, footstep splashes, top-floor leaks), StormLights (close strike swaps nearby lamps to _off/dead versions, flicker, 20% brownout 5-15 s), FlashLights (client Blocks.LIGHT at strikes and under sheet flashes; may look amber under Photon, unconfirmed), Skyline (hashed far fires 80-240 blocks with PLUME/EMBER particles; real bolts smoulder 150-300 s), reveal = flash thins mist and pushes fog end out. Config weather.drips / skylineFires / stormSurges. Awaiting his in-game check.

(2026-10-26 09:0x) Josiah wants SET-AND-FORGET weather with no commands: endless night, light ground mist always, rain never below moderate and often very heavy with storms, fog rolling in and out at varying degrees. Shipped: weather.minRain 0.55 floor, drizzle weight 0, storms ~2-3x more often, weather.rollingFog (three slow sine waves), and low ground mist always on. The mist puffs looked like round blobs (fixed: wide flat faint smears). /zc commands need cheats/op (LEVEL_GAMEMASTERS), which is likely why "many commands didn't work" for him.
(2026-10-26 11:2x) Round 3 shipped (playtest 8dc7ac22): GhostPower (client; dead _off lamps/signs + 12% of plain windows near you stutter on 20-60 s every 2-6 min), Alarms (server, world/weather/Alarms + CarProp.tripAlarm/ALARM flag; CarLights.on blinks; stand-in sounds horn + pitched bell, sound thread asked via coordinator), Glints (lamp-coloured rain streaks, GLINT particle). Config wetGlints/ghostPower/ghostPowerEvery/alarms. Other offered ideas not yet picked: searchlights/flares, steam/smoke vents, birds/bats, blood moon, ash fall, emergency broadcast.
(2026-10-26 12:4x) Josiah: mist must be smooth continuous fog "restricted to a height slightly off the ground", NOT pixelated puffs/specks. Now client/weather/GroundFog: stacked textured sheets (entityTranslucent, textures/effect/ground_fog.png seamless FFT noise, blur mcmeta) draped on the heightmap within 36 blocks, scrolling with wind; mist particles no longer spawned. He also asked to switch OFF car/shop alarms: config renamed weather.stormAlarms default false. A crash-fix thread fixed a GhostPower NPE (lit() re-rolled windows); pull playtest before touching GhostPower.
(2026-10-27 00:1x) Ground mist v3 (GroundFog): Josiah wanted no visible edges/flat plane and smooth motion. Drift now integrated per frame (OFF_X/Z) with eased wind; was velocity x clock which jumped when wind turned. Sheets are speckle texture (uniform random alpha, nearest, 128px, re-jittered each frame) with density from per-vertex alpha = cloud value noise x edge fade; relies on Photon's alpha cutoff + TAA. Awaiting his in-game verdict (risk: grain/shimmer, esp. without shaders).

RULE (Josiah 2026-10-27 21:00): there will NEVER be sunny days; "day" is just a lesser night. Never design anything around daylight (safe days, sun-only threats); key on light the player makes or the deep-night hours instead.
TODO when resumed (2026-10-28 note from coordinator): in 26.3 Entity.getId throws for negative ids. WeatherFx gives client-side visual bolts negative ids (boltId from -100000) and its ENTITY_LOAD handler calls entity.getId() >= 0 to tell its own bolts apart. That can crash. Fix: count ids down from Integer.MAX_VALUE (as AIRCRAFT did, playtest 9b10cf93) and tell own bolts apart by a Set of ids or a flag instead of the sign. Thread is PAUSED (Josiah 2026-10-28 01:17) until he resumes it.
(2026-10-29) Josiah: daytime should permanently look like his overcast blue-grey screenshot (fair visibility, moody). Shipped weather.steadyDay (default true) + weather.dayFog 0.2: WeatherLayers pins day light and visual fog; weather fog only shows at night (zombie sight still uses real fog). Bolt ids now count down from MAX_VALUE-2M (26.3 throws on negative ids).
(2026-10-29) Weather reference photos (Wikimedia, from REFERENCES thread): /mnt/project-files/zomboid-mod/refs/weather/ (ground-mist, rain-in-lamplight, wet-neon-streets, lightning-city, overcast-rain-town, fire-smoke-skyline, roof-runoff), each with sources.md. Use them before further weather visuals (photo-reference rule).

---

### zomboid-world-loot-direction.md

---
name: zomboid-world-loot-direction
description: Josiah's 2026-10-27 ask for the LOOT thread: replace every vanilla item with custom ones, vast roster with rarities and values, loot on theme with the building/area
metadata:
  type: feedback
  modified: 2026-10-27T22:08:54.083Z
---

Josiah 2026-10-27 22:08 (thread "Make and model all world loot", cmsg_01LvQiYGyHLhwyRNcM6wP1Dp3sPQiPRKoASa3LtUBwRxqS): "I want all vanilla items replaced with custom items but it needs to be very vast and have differing rarities and value. Any and all categories that could apply to our game need to be covered in great detail but also be relative to where the loot is found and make sense for the building it's in or area it's acquired."

**Why:** vanilla stand-ins (bread, potions, leather armour, totems, diamonds) break the Zomboid feel; loot should be a reason to search every building.

**How to apply:**
- No minecraft: items in any loot table once the roster lands (at 22:08 there were 117 vanilla ids across loot_table/shop + car).
- Every item has a rarity (use the approved Common/Scarce/Rare/Prized/Grail names, [[zomboid-loot-rarity-direction]]) and a value.
- Placement follows Zomboid's room + container model: the building and container decide what can appear ([[zomboid-building-detail-direction]]).
- Weapons/attachments stay with the WEAPONS thread. Start from photos ([[zomboid-photo-reference-rule]]). He likes options and a preview before building at scale.

- 2026-10-27 22:31 Josiah approved the proposal calls: items look slightly worn/"used" (not like-new like guns), fresh food spoils (Zomboid-style), build order wave 1 food/drink/medical/hygiene, wave 2 tools/materials/electronics/light/camping/car, wave 3 valuables/books/toys/keys/military/junk, clothing with the characters thread.
- 22:32 Josiah: items that need refrigeration go bad quicker when not refrigerated (fridge slows, freezer slowest).
- RULE 22:33 Josiah: "everything needs purpose" - every tool, medical item, electronic, book has a real use (books train skills); crafting must spell out what each recipe needs (ingredients, tools, parts, skill). Roster gets a Use line per item + a crafting recipe section for his review before crafting is built.
- 22:35 Josiah: for uses/crafting/recipes "draw heavily from Project Zomboid" (can openers for cans, cooking raw meat, recipes with tool requirements, skill books like Zomboid XP multipliers).
- 2026-10-31 03:01 Josiah: wave 4 ask = no more food; lean on medical, military and base decor (lava lamps, arcades, pool tables, neon...), broad and creative, rarities kept in mind. 03:02: use Zomboid's loot rarity scaling and variety as the BASE but add even more variety and rarity than Zomboid. Proposal doc "World loot wave 4" https://claude.ai/artifact/EGQKPoJbpcbrdWkF2UmC6B (190 items: 46 med, 44 mil, 100 decor; ~830 looks via variants; 7 calls awaiting his picks). Zomboid B42 refs: 6 rarity factors (0.05/0.2/0.6/1/2/3) x 21 categories, ZombiePopLootEffect 0-20, pre-looted 25% by day 90, ~4,500-4,900 items.
- 2026-10-31 03:14 Josiah's picks on the wave 4 doc: BUILD EVERYTHING (max variety, every item has a function, even just faster healing); Zombie Pop Loot Effect ON; server loot settings "fully adjustable"; variant rarity + edition rolls yes; NO sixth tier; Clinical Trial Vials yes; moving furniture needs tools AND skills learned by reading books and practice (Zomboid-style). Also asked (routed to coordinator 03:16): full base customisation gated by incremental skills; Zomboid vehicle maintenance + working car hoists + auto shops per town scaled by size; a front-to-back Zomboid review. This thread keeps: wave 4 items, loot settings, car parts catalogue as loot (agree with vehicles), auto shop stocking.
- DOWNED STATE (Josiah picked 2026-10-31 03:23): in multiplayer, lethal damage downs you ~60 s; a friend with airway kit / bag-valve mask / adrenaline / defib can revive, stretcher + wheelchair move you. Solo still dies. Built in wave 4 stage 1 (LOOT thread).
- CAR PART DATA (VEHICLES 2026-10-31): world/cars/Mechanics.java reads part items' CUSTOM_DATA keys "condition" (0-100; missing = 80), "kind" (standard/heavy/sport), optional "car_part" (part id). Roll wreck parts ~20-60, boxed shop parts ~85-100. spare_tire->car_tire, headlight_bulb->headlight, antifreeze->coolant swaps agreed; car_jack, tire_iron, socket_set reused.
- SURVIVAL thread (cmsg_01LvQiYGyHLhwyRNcM6wP1DpW4Z7nEV5bGssDNkM4Pzo7n) owns body model (17 parts), panic, needs, treating others after stage 1 medicine lands; LOOT keeps mood (stress/boredom/unhappiness/comfort), power grid, generators, fuel; owes it a district-power query + mood API.
- ZOMBOID REVIEW HANDOUT (Josiah approved 2026-10-31 03:46; doc https://claude.ai/artifact/7rkGg3jdUDUZuHf1QeEvVE). LOOT owns: sec 8 world stories with payoffs, stash maps, safes; sec 13 power+water (district-by-district failure, delay adjustable in settings; generators; fridges need power); skill magazines, VHS + TV schedule (sec 2); car parts as items (sec 12); Loot group of sec 20 (47 Zomboid settings). RULE: Zomboid's version of every feature unless Josiah confirmed a change.
- 2026-10-31 04:18 Josiah: medical wave preview good (merged playtest d605d9e81). RULE: knee braces and supports that don't change how you move (unlike crutch/cane) must show on the body; asked the clothing thread to draw brace/sling/wrap/tourniquet/collar/splints/bandages.
- POWER split with LANDSCAPE (2026-10-31): LANDSCAPE builds ~14 district substation yards + Districts.at(level,pos) district id per chunk (plan doc FqHpQgo523mi17HVt3TBYq); LOOT builds PowerGrid (OFF event (level,districtId), isOn/on queries), shutoff timing from settings, generators, hooks street lamps + RoomLights lx_ switches. Waiting on Josiah's answer to LANDSCAPE's plan.
- POWER (Josiah via LANDSCAPE plan 2026-10-31 04:33): "Substations can be brought back... power, as with water, has a toggle setting to set it to always on and an adjustable section for how long it's on"; "Power can be restored but it's not easy." LOOT: always-on toggle + days-until-shutoff setting (COMMANDS sandbox backbone), PowerGrid.OFF/ON events, substation repair (Electrical 6+, fuses/breakers/copper wire, big generator on site). Water = SURVIVAL.
- AUTO SHOP TABLES (LANDSCAPE 2026-10-31 04:41, LOOT fills in stage 7): shop/auto_{brakes,suspension,exhaust,wheels}_{standard,sport,heavy}, auto_tools, auto_electric, auto_fluids, auto_books (auto_manual_* + mag_mechanics), auto_outfit, auto_special, auto_scrap (cond 20-60), auto_military. auto_keys = VEHICLES (car_key never in loot).
- LOOT SETTINGS (stage 6, COMMANDS 2026-10-31 05:02): Sandbox backbone com.zomboidcraft.sandbox; LOOT owns 47 Loot settings + TimeSinceApo (/mnt/project-files/zomboid-mod/commands/world-settings.md). Wire via SandboxOptions.wires(): w.put("ZombiePopLootEffect", b -> b.number("loot.zombiePopEffect",0,20).sameScale().owner("LOOT")); toggle/choice too. ZombiePopLootEffect ON per Josiah.
- UI HOOKS (2026-10-31 05:17, branch j1d6bj, not yet in playtest): G menu InteractMenu.provider((client,player,hit,out)->out.add(Option.of/timed/no/menu)); tap G = default, hold = list; UI already registered downed friends (check vs my DownedClient once it lands). SkillUnlocks.add(skill, level, text) for level-up cards. TimedActions.start now queues when busy.

---

Other directions and tech notes are in `memory/` (see memory/INDEX.md).

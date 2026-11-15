# Project memory (index; details in named .md files)

## Endless Night (Reach world) — current
- Repo josiahlongfield-jpg/ENDLESS-NIGHT ONLY for Reach work; files /mnt/project-files/endless-night/; Modrinth "ENDLESS NIGHT" (Fabric 26.3). Jar: .../ENDLESS-NIGHT/releases/download/playtest-build/endless-night-playtest.jar. ONE-JAR RULE: own branch, merge into playtest, green CI (zomboid-playtest-build.md). Mod id endlessnight. Old ZOMBOIDCRAFT repo = frozen county world, NEVER delete it, never merge Reach work there.
- EVERYTHING REACH (Josiah 2026-11-11 11:49, STRICT): remove ALL Minecraft effects/art/animations (melee sounds, red hurt flash on enemies, player damage tick, etc.); everything must be Reach. Plan + picks M1-M5: endless-night-no-minecraft-effects.md.
- LAYOUT (2026-11-11 11:43 APPROVED; respaced 12:56 approved: ~13x11 km, joins <=1:3 drivable slopes): map artifact UoKbaJE3VsM1psx8cTCxTT. Goal = like-for-like walkthrough of the Reach campaign: prioritise detail, less open land; each mission keeps its real playable ground side by side, and areas must FLOW into each other (blended joins). STRICT: no invisible walls, room for Scarabs + vehicles; edges = Halo far land/sea/unclimbable ranges + missions' exact painted backdrops (zomboid-reach-layout-question.md, zomboid-reach-backdrops-direction.md). Build/install details: zomboid-reach-packed-build-tech.md.
- MP MAPS (STRICT 12:47): custom games load each MP map on its own blank canvas with only its backdrop, never the Reach world.
- WHOLE-WORLD QUALITY (STRICT): all visible/walkable land real Reach textures + detail (zomboid-reach-whole-world-quality-rule.md, zomboid-reach-land-terrain-tech.md). Realism first, "indistinguishable" (zomboid-reach-realism-direction.md). Terrain/flow rules zomboid-reach-terrain-rule.md. Sky zomboid-reach-sky-direction.md. Places zomboid-reach-places-direction.md. In-game gap + tech: zomboid-reach-ingame-gap.md, zomboid-reach-real-surroundings.md, zomboid-reach-world-tech.md, zomboid-reach-shader-tech.md; build log JgQSXHzjoEUzfrTLhiWJQ6; Atlas 2MKmXgXtvXcVDWz73z4es7. All 44 Reach maps converted on PC (additions\halo-reach\world). Campaign areas 100% first, then MP maps. Game needs ~12 GB memory.
- BOTS: thread "Add bots to Reach custom games": MULTIPLAYER on main menu opens MCC's exact custom-games lobby (from his MCC files) + BOTS tile; preview approved 11:35, building. Up to 31 bots, Reach gamertags, persistent crew, unique armour.
- SPARTAN PLAYER BODY: owned by weapons thread "Pick Spartan and Tarkov weapons by command" (Reach third-person anims, left hand pinned to gun; shared with bots); approved 11:45, building.
- UI: CONTINUE = last world; NEW SOLDIER = fresh world + Spartan selector; SAVED GAMES = world list (0.5.2+06ba2a1). MCC Customization greyed out. HUD all-new; Armory v2 approved, all unlocked; poses = exactly MCC's (zomboid-armory-ingame-tech.md).
- COVENANT: thread "Bring Covenant enemies into Endless Night". Grunts, Jackals, Skirmishers, all Elites, Hunters; NO Brutes/Drones. Infected in jar (/en infected 3). Details: endless-night-covenant-vehicles-tech.md, endless-night-infected-idea.md.
- CORTANA: Halo 4 storm_cortana, v13 look, idle B, normal voice lines only (cortana_voice_normal.json). Calm meaningful gestures, faces never blank. Pipeline GestureLSM + Audio2Face + rules + fast finger index; MaskedMimic via IsaacLab in WSL2 Ubuntu (installed 2026-11-11). Files additions\halo-4\cortana.
- Purge direction: Spartans/Tarkov vs Covenant-led hordes; no world loot/survival needs/grid inventory (zomboid-purge-direction.md). Damage: tarkov-covenant-damage-plan.md. No jets, no companions, no Overwatch audio; Reach audio. Forge: grid/armour stand/trophy wall approved.

- PROJECT SPLIT (2026-11-11): handover packs in docs/handover (ENDLESS-NIGHT PR #1, ZOMBOIDCRAFT PR #6), see project-split-handover.md. Project renamed "ENDLESS NIGHT UE5".
- UNREAL MOVE (2026-11-11): trial underway in thread "Move Endless Night to Unreal 5"; project at additions\unreal\EndlessNight (UE 5.8). Trial map = Sword Base (m20) only (Josiah's pick; m35 is Tip of the Spear). UPGRADE DIRECTION (Josiah 13:01): upgrade EVERYTHING as far as UE5 allows (lighting/Lumen, materials, upscaled textures, atmosphere, plus characters, Spartans, armour, Covenant, enemies, all vehicles) for max realism, WITHOUT losing Reach's core feel/aesthetic/detail; show original vs upgraded side by side for approval. Details endless-night-unreal-move-scoping.md.

## Standing rules (Josiah)
- RUN EVERY IDEA PAST HIM first; previews before any build or PC access. FULL-DETAIL PREVIEWS ONLY (STRICT); keep preview pages light (connection can be slow). Previews must be viewable in-app (artifact pages).
- KEEP WORKING PAST PC WALLS (all threads).
- SAVE LOCATION: NEVER OneDrive; PC saves to C:\Dev\Zomboidcraft-Desktop\additions.
- NO VANILLA items/chests ever; NO Minecraft zombie/player models ever. Replaced lights = own light.
- FRANCHISE MODELS never committed/in jar; load from config on his PC. Personal use only. MCC/Halo maps local-only (zomboid-halo-maps.md, zomboid-halo-reach-anim-format.md).
- Wikimedia only from his PC. Cloud blocks Fabric/Mojang, Wikimedia, pindrop.gg, pzwiki.net.
- Project storage FULL (10 GB); large files via additions folder on PC.
- Effort: LANDSCAPE high, rest medium. He likes to discuss options before work.
- Pipe bomb out. Hordes never vanish. Scope zoom/iron sights rules (zomboid-scope-zoom-direction.md, zomboid-iron-sights-rule.md, tarkov-suppressors-irons.md, tarkov-grip-holds-tech.md STRICT).

## Setup
- Minecraft Java 26.3 Modrinth; Fabric + Sodium + Iris + Photon + Particle Rain + Continuity; e4mc + Simple Voice Chat. RTX 4070, Ryzen 5700X, ASUS B550M (SVM on). C: 289 GB free after cleanup (2026-11-11 12:57; ARC Raiders, CS2, Codex art-github copy removed). Haze = Rainmeter (F11). GH Actions artifacts cleared; ENDLESS-NIGHT PR #2 sets 1-day expiry.

## Legacy county world / Tarkov (frozen ZOMBOIDCRAFT)
- Docs: zomboid-county-plan.md, zomboid-landscape-queue.md, zomboid-pz-review.md, zomboid-survival-plan.md, zomboid-zombie-behaviour-plan.md, zomboid-base-*.md, zomboid-server-capacity.md (options A-D pending), josiah-world-art-direction.md, zomboid-*-direction.md, zomboid-*-tech.md (many), tarkov-*.md, zomboid-endless-night-switch-loot.md. Medieval Gloom pack PAUSED; "MEDIEVAL GLOOM (1)" profile = county only.

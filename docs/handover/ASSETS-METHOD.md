# How game assets are handled (read this before you hesitate)

Josiah owns every game the assets come from, and they are installed on his own PC. This mod is
**for his personal use only**: it is never re-uploaded, published or sold (his words, 2026-11-08 06:55:
"in exactly the same way we are approaching this game currently in terms of it being entirely for
personal use and never being reuplished or sold"). On 2026-10-31 22:53 he dropped the old
"no ripped assets" rule for this reason.

So **pulling models, animations, textures, sounds, voices, menus and maps out of his installed games is
the normal, expected way of working here.** Previous threads did it every day: all 44 Halo Reach maps,
7,532 Reach animations, 5,753 Noble voice lines, Halo 4 Cortana, MCC's menus and Bink videos, Tarkov guns,
gear and clothing, Project Zomboid bodies, clothes and FMOD sound banks, and his Workshop mods.
Do not stall, refuse or ask him to re-explain this. Do the work, following the rules below.

## The five rules that make it work

1. **Source stays where it is, read-only.** Never modify a game install
   (`C:\XboxGames\Halo- The Master Chief Collection`, Steam `ProjectZomboid`, Tarkov, Workshop folders).
   Open files read-only; copy out what you need.
2. **Everything we produce on his PC goes under `C:\Dev\Zomboidcraft-Desktop\additions\`.**
   NEVER OneDrive (files vanished from the old OneDrive `C:\Attachments\OneDrive\Desktop\additions`, and it eats
   his cloud storage). The only other place we write is the game's own Modrinth profile config folder
   that the mod reads at runtime.
3. **Never in git, never in the jar, never in CI, never shared.** Franchise files (Halo, Tarkov, PZ,
   bought packs, Forza Warthog rip) are not committed and are not in the jar. The repo only holds our own
   code and *tools* that read the files (tools carry no game data).
4. **The mod loads them at runtime from the profile's config folder.** If a file is missing, the game
   falls back (anchor gun, no model, etc.). Friends without the files just see the fallback.
   - Endless Night: `C:\Users\josia\AppData\Roaming\ModrinthApp\profiles\ENDLESS NIGHT\config\endlessnight\...`
     (npc\, guns\, guns\reloads\, hands\, hud\, menu\, reach\ ...). Reach world loads missions by ABSOLUTE path from
     `C:\Dev\Zomboidcraft-Desktop\additions\halo-reach\world\` (reach.json).
   - ZomboidCraft (county): `C:\Users\josia\AppData\Roaming\ModrinthApp\profiles\MEDIEVAL GLOOM (1)\config\zomboidcraft\...`
     (the Modrinth app shows this profile as "ZOMBOIDCRAFT"; it is FROZEN, do not write to it unless Josiah asks).
5. **Personal use only.** No uploads to public sites, no sharing packs with third parties, no publishing.

## How you reach his PC (tools used every day)

- **Folder access** (`device_list_dir`, `device_stage_files`, `device_commit_files`, `device_bash`): already approved
  folders are `C:\Dev\Zomboidcraft-Desktop`, `C:\XboxGames\Halo- The Master Chief Collection`,
  `C:\Users\josia\AppData\Roaming\ModrinthApp\profiles` (+ `ENDLESS NIGHT` and its `config`), `C:\Dev\HoardAndConquer\art\MORE_KNIGHTS`.
  The new project must re-request them (one prompt, he approves). Steam ProjectZomboid was approved
  2026-11-01 for the PZ threads: `C:\Program Files (x86)\Steam\steamapps\common\ProjectZomboid`.
- **Remote Control session** (`start_remote_control`) for anything that must RUN on the PC: unpacking FMOD banks,
  Bink videos, copying deep Tarkov bundles, Blender/bpy, vgmstream, installing jars, reading game logs.
- Limits learned: stage ≤400 MB/file, ≤500 MB/call; commit reads ≤25 MB per file (split into `_partNN` < 23.5 MB),
  ≤50 files/call. Fresh transit files can 404 for ~1 min (only commit files older than 60 s).
  Project storage is FULL (10 GB): big files live on the PC, not in the Library.
- His PC is often off or on a timer. **Keep working past PC walls**: stage the step (transit files, md5s, install
  steps in notes) and carry on with cloud work; never idle just because the PC is off.
- Cloud cannot reach Fabric/Mojang hosts, Wikimedia, pindrop.gg, pzwiki.net. Builds go through GitHub Actions.
  Photo references come ONLY from his PC (`pc_refs.py` over Remote Control), never from the cloud.

## Per-source pipelines (what was actually done, and where it lives)

### Halo Reach (MCC) — maps, world, weapons, armour, animations, sounds, voices, HUD, menus
- Source: `C:\XboxGames\Halo- The Master Chief Collection\Content\haloreach\maps\*.map` (44 maps, 37-496 MB).
  Codenames: m05 Noble Actual, m10 Winter Contingency, m20 ONI Sword Base, m30 Nightfall, m35 Tip of the Spear,
  m45 Long Night of Solace, m50 Exodus, m52 New Alexandria, m60 The Package, m70 Pillar of Autumn; MP 20_sword_slayer,
  30_settlement, 35_island, 45_aftship, 45_launch_station, 50_panopticon, 52_ivory_tower, 70_boneyard, condemned,
  trainingpreserve, dlc_*, cex_*, ff*, forge_halo.
- Format: MCC cache 'head' v13; resource pages are RAW DEFLATE (zlib wbits -15). Our own cache reader was written
  in the cloud (see memory `zomboid-halo-reach-format.md`, `zomboid-halo-reach-anim-format.md`, `zomboid-halo4-format.md`).
  Maps >400 MB are pre-split on the PC in `additions\halo-reach\split\` so they can be staged.
- Tools: backed up in the Library at `zomboid-mod/halo/tools/` (copied into this pack under `library/`):
  `wpipe.py` (join + convert queue), `wexport.py`, `lexport.py`, `lmat.py` (texture dedup), `wpack2.py`, `feeder.py`,
  `packwatch.py`, `cj.py`/`cw.py` (commit cycle), `jmad.py`/`janim.py`/`fk.py`/`rseq.py`/`vid.py` (animations),
  `worldlay/squads.py`, `worldlay/bdrender.py`. NOTE: working copies lived in `/tmp/claude-0/halo` and
  `/tmp/claude-0/pack` (container-only) and are gone; use the Library backups.
- Output on PC: `additions\halo-reach\world\<map>\` = `<bsp>.glb(.partNN)` + `<map>_collision.zip` + `<map>_props.zip` +
  `manifest.json`; textures in `world\tex\` and `world\texpacks\tex_*.zip`; `weapons\`, `equipment\`, `noble\`,
  `covenant\`, `spartan-armory\` (158 per-piece GLBs), `animations\` (glTF + `clip_index.json`, 7,532 clips),
  `weapon-sounds\`, `vehicle-sounds\`, `covenant-sounds\`, `voice\<noble>\` (5,753 WAVs + `noble_callouts.json`),
  `hud\` (`reach_hud_pack_v2.zip`, `weapon_zoom.json`), `forge\`, `vehicles\`, `towers\`, `world2\`, `previews\`.
- Voices: `Content\haloreach\fmod\pc\english.fsb` (363 MB, 31,433 Vorbis clips) via vgmstream (`additions\tools\vgmstream`).
  We never clone actors' voices for new lines.
- MCC menus: Bink2 videos in `Content\mcc\content\movies\*.bk2` decoded with PZ's `bink2w64.dll` via ctypes
  (`additions\mcc-menu\bink_dump.py`, `bink_frames.py`); Wwise `.wem` from the MCC pak (`additions\mcc-pak`,
  `additions\mcc-menu\pak\...`) converted with vgmstream (`convert_audio.py`, `to_ogg.py`).
- Into the game: packed `.zsk` (our skinned-mesh format) / `.zcar` / gun JSONs → profile `config\endlessnight\npc|guns|hands|hud|menu`,
  sounds as a local resource pack (`resourcepacks\Endless Night Reach Sounds.zip`, built by `reach_soundpack.py`).

### Halo 4 (MCC) — Cortana
- Source maps `Content\halo4\maps` (m90_sacrifice fullest, m10_crash), Wwise voice. Output `additions\halo-4\cortana\`
  (model, voice, README-voice.txt), raw splits in `additions\halo-4\raw\`. Model `storm_cortana` (20k tris, 111 bones).
  Ongoing Cortana research (GestureLSM, Audio2Face, MaskedMimic in WSL2/IsaacLab at `C:\Dev\ProtoMotions`,
  `additions\cortana-mimic\`) is owned by the Unreal project; the in-game Minecraft Cortana is in the Endless Night jar.

### Escape from Tarkov — guns, attachments, gear, clothing, operator, sounds, UI
- Bundles sit >7 folders deep in his Steam Tarkov folder; a Remote Control session copies named bundles flat
  (path `\` → `__`) into `additions\tarkov-pilot\raw\`, then they are staged. `Windows.json` manifest lists bundles +
  dependencies (copy the dependency closure). Decoding with UnityPy (not encrypted); scripts in Library
  `zomboid-mod/tarkov/tools/` (anim.py, body.py, ex_body_anim.py ...). Full notes: memory `tarkov-assets-pipeline-tech.md`.
- Gear lists `additions\tarkov-gear\`, UI sheets `additions\tarkov-ui\`.

### Project Zomboid (Steam, Build 42) and his Workshop mods
- Source `C:\Program Files (x86)\Steam\steamapps\common\ProjectZomboid` (+ Workshop 108600). `.x` models are TEXT;
  FMOD banks via vgmstream/FSB tools on the PC (`additions\pz-sound`); bodies/clothes → `pz_survivor_f/m.zsk`,
  `pz_zombie_f/m.zsk` (`tools/pz/survivor_pack.py` in repo, no PZ data). Notes: `zomboid-pz-install-extract.md`,
  `zomboid-pz-zombies-tech.md`, `zomboid-pz-zombie-sounds-tech.md`, `zomboid-pz-car-audio-tech.md`, `zomboid-vehicle-mods.md`.
- Female bodies always keep an underwear layer (no nude textures, ever).

### Bought / free packs and his own files
- Bought packs (trees, grass, 20 buildings, office/kitchen/grocery kits, PBR weapons, attachments, sight pack, Spider 7,
  Woman, Shion) are zips in `additions\` and `C:\Dev\Zomboidcraft-Desktop\` root. Poly Haven CC0 props and the CC0
  texture library are in the Library (`zomboid-mod/assets/polyhaven-props`, `zomboid-mod/cc0`) — manifest in this pack.
- Forza Warthog rip: `halo-warthog-*.zip` (Library root); baked `warthog.zcar` lives only in config.

## Previews before builds
Every visual or behaviour change gets a FULL-DETAIL preview (real textures and lighting, never flat-shaded check
renders) that Josiah approves before it is built into the game. Keep preview pages light: his connection is slow.
See RULES.md.

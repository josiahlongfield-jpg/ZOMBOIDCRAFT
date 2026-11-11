---
name: zomboid-sound-design
description: ZomboidCraft sound thread: ownership, pipeline, branch/merge rule, current state and queue
metadata:
  type: project
  modified: 2026-10-25T09:53:50.946Z
---

The sound thread owns ALL audio. The build/weapons threads own ModSounds.java and playSound calls; the sound thread owns the .ogg files, assets/zomboidcraft/sounds.json, assets/minecraft/sounds.json, CREDITS.md and tools/audio/. Round log: [[zomboid-sound-history]]. See [[zomboid-weapons-direction]], [[zomboid-playtest-build]].
Josiah wants sounds that "make people genuinely fear", that scale with zombie count (no horde bed for one zombie), sampled or licensed audio with credits, and options shown before shipping.
Pipeline:
- Freesound CC0 only via CI (.github/workflows/sounds.yml triggers on push to the sound branch; this cloud IP gets 403). Results go to branch sounds-raw.
- Sonniss via archive.org mirrors (tools/audio/fetch_sonniss.py). Raw files stay PRIVATE in /mnt/project-files/zomboid-mod/sound-raw/sonniss/ and never go in the public repo. Licence (checked 2026-10-26): games ok, no attribution needed, no sharing raw files, no AI training; loose processed oggs in a public repo are a grey area (told Josiah a private repo closes it).
- Picker pages are built from tools/audio/slice_candidates.py clips.
- Build scripts: build_zombie_sounds.py (merge_vanilla() for minecraft/sounds.json), build_loot_sounds.py (shared build()), build_ui_sounds.py, build_ambience_sounds.py. Files are MONO for 3D; ambience beds are stereo 24 s loops, streamed.
Branch rule: develop on claude/project-thread-wh9qba only, never push to playtest or combat. After a push, ask the inventory thread (cmsg_01LvQiYGyHLhwyRNcM6wP1Dp7gZePrJawC2SWgKAK2146C) to merge into playtest; Josiah only gets the playtest jar.
In-game code (2026-10-25):
- client/sound/SoundVolumes groups, including AMBIENCE "Weather & Ambience", plus the wall_muffling slider.
- Muffler + ChannelMixin: EFX low-pass by walls on the ear-to-sound line.
- client/sound/Ambience: rain/roof/window/drip/wind/night beds and creak/crow/distant one-shots. It finds indoors by looking up for a roof, because the heightmap ignores city blocks. Vanilla weather.rain is silenced and lightning thunder is replaced with amb/thunder. The server elytra wind gust was removed from WeatherDirector.
Queue:
- Ambience round 2 (heavier hail, alarms, far animals).
- Shotgun (870, branch combat): weapon.shotgun.shot, shot_distant, dry, pump (full rack), shell_in; stand-ins are rifle files pitched down.
- Pistol: the weapons thread put placeholder events on branch combat: weapon.pistol.shot, shot_distant (att 96), shot_suppressed(+_distant, already real), dry, mag_out, mag_in, slide. The names are fixed; replace the sounds.json entries and oggs. The 870 pump shotgun and a bolt-action rifle come next.
- Cars for cmsg_01LvQiYGyHLhwyRNcM6wP1DpFXa9FDBNk7bNGWp7LGSDPi: car.start, car.idle loop (pitch by speed), car.horn, car.crash (+car.door, car.stall).
Note: HammerItem plays ZOMBIE_ATTACK_WOODEN_DOOR (now body-on-door); hammer should use item.hammer.nail.
- Hunting rifle (.308, 700-style, branch combat e28ab07): weapon.hunting_rifle.shot, shot_distant (fixed range 176), dry, mag_out, mag_in, bolt (ONE take for lift/back/forward/down, ~0.33 s, transient at start; plays 0.4 s after each shot). Shot should be heavier/louder than the AR with longer tail. Replace the stand-ins in sounds.json; combat takes our side.
Paid packs (2026-10-26): Josiah finds the free takes "too basic". Paid licences forbid uploading raw files, so work on paid packs runs on his PC (Remote Control); only finished clips leave it. Recommended Gamemaster Gun Sound Pack (~$20) + Silenced Gun Sounds (~$25) on Sonniss; alternatives Coll Anderson Guns Library $65, Pole Position single-gun libraries ~$125. Fusehive Weapons of War is cinematic military, a poor fit.

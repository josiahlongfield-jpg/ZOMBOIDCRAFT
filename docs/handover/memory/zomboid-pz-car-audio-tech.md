---
name: zomboid-pz-car-audio-tech
description: How converted PZ cars get Zomboid's own car audio (FMOD bank extraction, local pack, per-model sound sets); state 2026-11-01
metadata:
  type: project
  modified: 2026-11-01T07:13:59.654Z
---

Josiah 2026-11-01 asked for all of Zomboid's vehicle audio coded in per car (VEHICLES thread).

- Source: PZ B42 sounds live in C:\Program Files (x86)\Steam\steamapps\common\ProjectZomboid\media\sound\banks\Desktop\ZomboidSound.bank (647 MB FSB5, Vorbis mode 15). Loose media/sound/*.wav and newCar/ zb_* are the OLD sounds. Staging cap is 400 MB, so an RC session split it into pzbank\part0/part1 in his MEDIEVAL GLOOM (1) profile (sha256 958AF426...). Those parts can be deleted later (ask first).
- tools/cars/pzsounds.py BANK OUT: rebuilds the Ogg headers with ffmpeg's libvorbis (the setup CRC matches 48k mono at q4.0 and stereo at q4.3; pip fsb5 was blocked as external code) and writes the "ZomboidCraft PZ Car Sounds" resource pack: 219 pz.* events / 408 oggs, 17 MB. It is local only and goes in his profile resourcepacks; he must tick it on.
- Engine sets: sports = Chevalier Cossette (1000-6000), wagon = CeriseWagon (1000-4000), offroad = Dash Rancher (8 bands), van = Step Van (5). Mapping script event -> set is my inference (SportsCar -> Cossette, OffRoad -> Rancher). There's no jeep horn sample; jeep uses standard.
- Model row "sounds": {engine, door, horn}, written by pzcar.py pz_sounds(). CarSounds.of(model, kind, "x.%.y", ours) gives a direct Holder of pz.*; CarProp.zs()/sounded(). Client PzEngineSound plays engine+exhaust loops per band, on/off load, crossfaded, through 5 gears; it falls back to CarEngineSound if the pack is missing.
- Code is on branch cilddw (482b8c9 green). Previews: /mnt/project-files/zomboid-mod/previews/{r34,defender}-audio.mp4 (sim scratch /tmp/claude-0/pzprev/sim.py). AUDIO's siren/alarm kept (his earlier picks).
- Pending at 07:15: his OK on the preview, then install the pack + rebuilt rows (scratchpad lids/). The playtest merge is waiting on his OK (the classifier blocked an unasked push).
- Also fixed: /zc car ready emptied the tank (the first-tick rollFuel); readyToDrive now sets fuelRolled + rolls parts at 100 (3040fa9).

Related: [[zomboid-vehicle-mods]], [[zomboid-playtest-build]].
- 2026-11-04 (Josiah, via AUDIO): Defender acceleration "hunts" (its note loop swelled 0.35-1.0 inside 1.15 s + pitch followed raw tick speed) and R34 note was 11 dB quieter. AUDIO commit 2d7b6997a: pzsounds.steady() flattens the loop envelope (60 ms window) + NOTE_LOUDNESS -12 dB RMS for every note; PzEngineSound smooths speed (0.1/tick), pitch rate 0.08, weight 0.6 idle -> 1.0 by half speed. Rebuilt pack (notes only changed): /mnt/project-files/zomboid-mod/cars/pz-sounds/v2-smooth-notes/. Preview audio-previews/engine-smooth-preview.mp4. Awaiting his OK, then merge + copy pack to his profile resourcepacks (needs folder access to C:\Users\josia\AppData\Roaming\ModrinthApp\profiles).
- 2026-11-04 06:23 Josiah: "put the audio that comes with the pack for each vehicle's acceleration back". AUDIO fd1aa5bdd: PzEngineSound plays the band loops again (e1c93ab2b design) but NO gear changes (his 11-01 ask), smoothed speed (0.1), rpm rate 0.1, load eased 0.06, level 0.7 idle/coast -> 1.0 under load/half speed. The single note (pz.engine.<set>.note) is unused now; the v2 pack is not needed (band loops are unchanged in his installed pack). Preview audio-previews/engine-recorded-preview.mp4. He approved ("much better"); MERGED playtest 6cd103c7b (release verified 2026-11-04 06:51). Offered gear changes back.

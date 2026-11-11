---
name: zomboid-world-events
description: Shared world events (siren, explosions, jet/heli flybys) everyone hears from where they stand: design, files, Josiah's asks
metadata:
  type: project
  modified: 2026-10-27T06:36:16.955Z
---

Josiah 2026-10-27 06:08: the air-raid siren played for him but not his friend, because Ambience's far-off one-shots are rolled per player. 06:18: creaks and ambience stay per player, but major events (raid siren, explosions, choppers, jets) must be heard by everybody, specific to where each player is, and timed right for scattered players. Fly real 3D aircraft randomly across the map. 06:33: random mix of a chopper, a lone jet or a few jets; they must be visible with believable Doppler.

Built by AUDIO (branch claude/project-thread-wh9qba, 261c20ae + 776b6d71):
- world/events/WorldEvents.java (server): every ~worldEventMinutes (config world.worldEvents / worldEventMinutes, default 10) it picks JET 35%, HELI 25%, EXPLOSION 20%, SIREN 20% around a random overworld player and sends an Event payload to all. A third of jet events have 1-3 wingmen (separate Events). It spawns a CarProp only while a player is within 192 blocks of the line, and calls CarProp.flyby() each tick (the flyby flag means no save, no boarding, server tick skipped). Explosions get particles and a smoke column. /zc event jet|heli|siren|explosion.
- client/sound/WorldEventSound.java: siren/explosion start after distance/343 s. Flyby with sound=N plays world.flyby.{jet,heli}.N (whole recordings aligned so the aircraft is overhead at PEAK=10 s, by tools/audio/build_flyby_sounds.py), started so that moment arrives with the closest-pass sound, and positioned at the retarded point on the line. CarProp.quiet silences that entity's AirSound. sound=0 = the far-off loop with computed Doppler until the entity is near.
- Sounds: world.siren (moved from amb/distant/4), world.explosion = aircraft/crash, world.explosion.far = amb/distant/1 (stand-ins; Josiah has not picked explosions).
Related: [[zomboid-aircraft-sounds]], [[zomboid-flight-direction]].
08:05-08:30 (playtest 56b9e80a): flyby jets 75-105 m/s, helis 32-44 m/s (Josiah: slower); every flyby is exactly ONE whole recording per aircraft (loop mode and the entity's own AirSound are off for all flybys). Shooter's zombie hits within guns.hitWhizzRange (48) play weapon.bullet.whizz = the VisceralBulletImpacts takes, to the shooter only (he rejected the old ricochet whizz). Nav lights and heli fog spotlight asked 08:29, handed to the flight thread.
09:28 (playtest 150dc41a): jet takes = 1-2 Josiah's F-16 passes (70%), 3 F/A-18, 4 F-22, 5 MiG-15; wingmen 3-5 s apart. Siren: always played far off (Attenuation NONE, 64 blocks away in its real direction, vol 0.6, highpass/lowpass/echo in build_aircraft_sounds AMBIENT) for every overworld player; picks 6% of events and at least 1 hour apart (else explosion). Event weights now JET 40 / HELI 30 / EXPLOSION 24 / SIREN 6.
11:15: siren fades in (Distant volume 0→0.6 over 160 ticks, squared curve) on branch 30f4b871.
11:16: jets 35-60 above ground, side ±90, all flybys clear the tallest heightmap along ±240 of the closest point by 18 (caa58af86).
11:38: jet packs 70% of jet events (2 or 3 jets), lone 30%.
11:40: heli pairs 60% of heli events (wingman 3-5 s behind).
12:35: jets silent for Josiah (12:28). Likely cause: Minecraft has 8 streaming channels; amb.rain/roof/window/drip/wind/night + music.* are stream loops, so stream flybys failed silently. Flybys + siren now non-stream (sounds.json + both build scripts). RULE: never mark one-shot event sounds stream:true.
13:20: Josiah paused all threads but LANDSCAPE/refs. AUDIO's last change: d16be82d (non-streamed flybys/siren) sent to UI for playtest. Open AUDIO items when resumed: confirm jets audible in rain; explosions are stand-ins (no picks); round 17 gun picks; hail picks; spotting screams/ui.drop/ui.skill files from Josiah; HammerItem should use item.hammer.nail; wood bullet strike is a stand-in.

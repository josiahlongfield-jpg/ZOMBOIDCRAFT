---
name: zomboid-broadcasts-plan
description: AUDIO's plan for Zomboid-style outbreak radio broadcasts (PZ review section 15) and far-off meta events (section 14); doc 98TrFVLzMteUKAXQKM6v9Z, merged to playtest 2026-10-31
metadata:
  type: project
  modified: 2026-10-31T03:51:58.778Z
---

Handed to AUDIO 2026-10-31 03:48 from the PZ review doc (7rkGg3jdUDUZuHf1QeEvVE), with Josiah's approval. His rule: Zomboid's version unless he already confirmed a change. Propose a plan first, and send a video before merging.

Plan doc: https://claude.ai/artifact/98TrFVLzMteUKAXQKM6v9Z (posted 2026-10-31 ~04:00).

**Timeline** (days from world start):
- day 0-1: WGHN news covers the Ridgeview "chemical leak".
- day 2: stadium quarantine, and the military channel opens.
- day 3: Hollis Island quarantine.
- day 5: fall-back orders.
- day 7: evacuation points (airport, carrier, Camp Black Pine).
- day 8-9: news goes dead mid-sentence.
- after that: the AEBS loops with forecasts, then dies after the power-out (tied to the district grid failure).

**Channels with payoffs**:
- The military loop gives a number code for a Camp Black Pine bunker door.
- Four ham operators: a cache read-out (LOOT places it), one silent mid-call, one hears the Harvestman hum, one still alive at the end.
- Radio static near the Harvestman (needs the Harvestman thread).

**Captions and receivers**: captions with static, as Zomboid does, plus real EAS tones. New receivers proposed: portable radio, ham radio block, car radio. The two-way and military walkies get broadcast channels.

**Meta events**:
- Gunfire, screams, house alarm and car alarm, 80-250 blocks away.
- Zombies are pulled to the spot, and LOOT leaves a matching scene.
- No dogs or wolves, because of the no-animal-sounds rule.
- Setting: Never/Sometimes/Often.

**DECIDED 2026-10-31 04:42**: timeline approved; captions only, Zomboid style (no voice); add the portable radio, ham radio block and car radio; four hams yes.

House alarm ids for LANDSCAPE (synth stand-ins in c680f4b44, tools/audio/build_alarm_sounds.py):
- world.alarm.house.bell
- world.alarm.house.siren
- block.alarm_panel.beep
- block.alarm_panel.disarm

They are never the air raid siren.

Settings: COMMANDS' Sandbox backbone (com.zomboidcraft.sandbox, 67b3d201) lists MetaEvent and SleepingEvent (never/sometimes/often) as coming and owned by AUDIO (see /mnt/project-files/zomboid-mod/commands/world-settings.md). Wire each with one line in SandboxOptions.wires() once built.

Picker candidates:
- Freesound groups bc_static, bc_eas, meta_firefight, meta_scream, meta_house_alarm and meta_keypad (79a5a6d5d).
- Sonniss rows in scratch sel_bc.json.
- Synth EAS tones from scratch eas.py into bcsynth/.

PICKER posted 2026-10-31 05:20: https://claude.ai/artifact/GWKvrbx7c2NyUdoU7damkk (176 options; builder scratch thrtools/bcpage.py, page dir bcpick/, firefight mixes in bcmade/; picks in db collection `picks`). Slots: radio.eas.header/attention/eom, radio.static, radio.tune, world.meta.gunfire, world.meta.scream, world.alarm.house.bell/siren, block.alarm_panel.beep/disarm.

Old calls (answered): timeline OK?; AEBS synthetic voice or captions only (rec. captions); add the new receivers?; the four hams? The video is planned as a web preview recording, because the cloud can't run Minecraft.

Related: [[zomboid-radios-tech]], [[zomboid-world-events]], [[zomboid-nether-origin-idea]].

BUILT on branch claude/project-thread-wh9qba; MERGED into playtest 9ca46b5d0 (2026-10-31 ~06:40, jar sent). Still to build: portable radio, ham radio block, car radio (need toon mesh art).
- gear/Broadcasts.java with data/zomboidcraft/broadcasts.json. Stations sit on channels from 1001, after the talk channels on the dial. Everything is driven by overworld game time. Tags: [EAS], [EOM], [HUM], [CUT].
- client/gear/BroadcastHud captions.
- world/events/MetaEvents.java. MetaEvents.recent() feeds LOOT's scenes.
- Config broadcast.* and world.metaEvents. Sandbox: zc.broadcasts, zc.broadcastDayOffset, and the MetaEvent wire.
- Stand-in sounds come from tools/audio/build_broadcast_sounds.py.

World hooks:
- LOOT will place a scene at each meta event spot, and stock Dale's Kingsbridge water tower (LANDSCAPE is asked to build it).
- LANDSCAPE built Our Lady of the Sea (Seabright) and Dale's Kingsbridge water tower (pump, chained ladder, stash locker) on playtest 6e17a758. The Camp Black Pine coded bunker is LIVE on playtest 4afed5c6: hillside bunker by the radio mast, camp east zone; code 417702 in world/Keypads.CODES (wrong code buzzes, draws zombies). The military line's code now opens something real.
- Teo's line now points to the dry dock pump house.

PICKS (2026-10-31 05:41), built in f5142c8a7 by tools/audio/build_broadcast_picks.py:
- radio.eas.header: clean synth header bursts.
- radio.eas.attention: fs 440307 (DylanTheFish Alarm), first 8 s. Broadcasts plays it 116 ticks after the header.
- radio.eas.eom: clean synth two-tone, 8 s.
- radio.static: Sonniss CB static, 1.6 s chunks.
- radio.tune: fs 38693 (AM band static), 1.2 s chunks.
- world.meta.gunfire: fs 417690 (SMG distant).
- world.meta.scream: Sonniss 344 Audio female scream.
- world.alarm.house.siren: fs 717414, cut to a 4.267 s loop.
- House bell and keypad sounds: his picks were the stand-ins.

AK-47: he saved fs 855843 for an AK-47 he'll add. WEAPONS NEW has been told.

Preview video: /mnt/project-files/zomboid-mod/audio-previews/broadcasts-preview.mp4 (scratch bcvideo.py).

SIRENS FOR VEHICLES (2026-10-31 07:05): VEHICLES asked for car.siren (US wail, seamless 4-6 s loop, range ~160) and car.alarm (classic cycling alarm, ~4 s loop), registered in sounds.json under those ids; VEHICLES registers the SoundEvents itself. Picker https://claude.ai/artifact/Km6Si49ZcuY73Bvw7bvgmG (87 options; builder scratch thrtools/carpage.py, synth carsynth.py; fetch groups car_siren/car_alarm, Sonniss rows sel_car.json). After picks: cut loops, credits, playtest, tell VEHICLES (thread cmsg_01LvQiYGyHLhwyRNcM6wP1DpFXa9FDBNk7bNGWp7LGSDPi).
PICKS IN 07:39: car.siren = Coll Anderson Sonniss sirens sx2af963b9, sxf6c175d6, sx5dbe55e2, each tiled to a seamless 6.0 s loop; car.alarm = fs 51747, six horn pulses (3.95 s). Script tools/audio/build_siren_sounds.py (NOT build_car_sounds.py, which is the older vehicle sounds script). Commit 539821f09; preview /mnt/project-files/zomboid-mod/audio-previews/sirens-preview.mp4; MERGED to playtest 0b371f47f, in release 0d8cfb428 (2026-10-31 ~08:50); VEHICLES told (replay siren every 6.0 s, alarm 3.95 s).

Command net and hunt board moved to [[zomboid-command-net-tech]].

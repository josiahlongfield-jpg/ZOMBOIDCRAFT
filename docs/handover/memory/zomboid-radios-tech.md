---
name: zomboid-radios-tech
description: ZomboidCraft radios tech: voice through Simple Voice Chat plugin, Radios snapshot, B talk key + PTT mixin, voice key fixer, headset on helmet rails, filter, sounds, loot
metadata:
  type: project
---

Radios + comms headset (weapons thread, branch `radios`, 2026-10-26). Preview: https://claude.ai/artifact/CYbY4oXX947eEuxJKbXRJD. See [[zomboid-gear-attachments-direction]], [[zomboid-nightvision-tech]], [[zomboid-playtest-build]].

Items: toy_radio / two_way_radio / military_radio = gear/RadioItem (Type enum: range + channels from config `radio`). comms_headset = gear/HeadsetItem. combat_helmet is `new HeadGearItem(p, true)` (rails). Models: tools/weapons/radios.py and headgear.py (comms_headset, comms_headset_worn).

Components: `zomboidcraft:radio` = Radios.Tuning(channel, on). Battery reuses Headgear.CHARGE (seconds). `zomboidcraft:headset` = Headgear.Rail(stack) on the helmet. CUSTOM_MODEL_DATA strings are now [goggles key or "none", up/down, headset key or "none"]; always go through Headgear.mountHeadset/unmountHeadset/refreshModel.

Server (gear/Radios): each tick publishes an immutable `Map<UUID, Spot>` (volatile) for the voice thread. KEYED = players holding the talk key with a live radio. Without a headset you need the radio in a hand; with one (or radio.holdToTalk false) any live radio you carry works. Key-up and squelch are ordinary sounds on other radios. Speaker playback calls Noise.emit via server.execute, throttled to 1/s.

Voice (voice/RadioVoice, the "voicechat" entrypoint in fabric.mod.json; compileOnly voicechat-api 2.6.25 from maven.maxhenkel.de): on MicrophonePacketEvent, decode once and run a RadioFilter per talker>listener stream (band-pass per type, tanh drive, hiss, edge dropouts and pops). Headset: sendStaticSoundPacketTo. Speaker: an entity sound at the listener sent to everyone within speakerRange. Volume category "zc_radio".

Client: RadioClient (B key = CombatNet.Action RADIO_DOWN 6 / RADIO_UP 7, `transmitting` flag, wearingHeadset()). @Pseudo mixins VoicePttMixin (PTTKeyHandler.isPTTDown/isAnyDown) and VoicePttProcessorMixin (PTTMicrophoneProcessor.isPttButtonDown) force the voice mod's PTT while transmitting. One-time key fix: the voice mod defaults (M mute, N disable, H hide icons, V menu) clash with ours, so on first run any still on a clashing default moves (menu to Y, mute to U, the others unbound). The flag file is config/zomboidcraft-voicekeys.txt; delete it to re-run. HeadGearLayer draws the headset (not pivoted). SoundVolumes dulls ".shot" sounds for headset wearers (radio.headsetGunshotVolume).

Sounds: tools/audio/build_radio_sounds.py makes them from scratch with numpy/scipy and ffmpeg, writing item.radio.* (key_up, squelch, ptt, channel, power_on, power_off).

Loot: patched additively into the shop/* and car/* JSONs (toys, garage, boxes, thrift, pawn, hardware, office, storage, guns, basement_guns, basement_supplies, police, swat, work, fire, ambulance, trunk). Toy and two-way drop 1-2 (pairs).

Untested with real voice as of the first build. To test: install Simple Voice Chat (Fabric 26.3). With e4mc hosting, everyone also needs "Voice Bridge for e4mc".

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

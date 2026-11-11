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

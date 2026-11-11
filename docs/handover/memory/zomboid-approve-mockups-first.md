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

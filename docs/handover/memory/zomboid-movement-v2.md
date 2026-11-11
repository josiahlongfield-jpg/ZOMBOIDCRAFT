---
name: zomboid-movement-v2
description: Josiah's 2026-10-26 zombie movement brief (idle sway, whole-body shamble, alert head turn, arms-straight chase, attack, crawl, legless variants) and where the preview lives
metadata:
  type: project
  modified: 2026-10-26T04:03:44.593Z
---

Josiah 2026-10-26 03:55: "A swaying idle, a slow shuffle that the whole body moves in relation with, a new alert like a head turn to the direction of the player/s, a new chase, more arms out straight than up, a new attack, a better crawl (also need a version of each zombie that is legless)". Then: "Make full use of the new joints" (spine, neck, elbows, wrists, knees, ankles).

Preview: kits artifact v14 https://claude.ai/artifact/Mnik6r9hRkYoARfsLTdBxU (move.js patched in by patch_move.py; sources in /mnt/project-files/zomboid-mod/characters/preview-src/). 07:00 tweak: idle legs stay planted, only the body sways ("Much better").

07:34 "definitely start building that": ported into the game 2026-10-26 (ZMove.java, see [[zomboid-zbody-tech]]). Related: [[zomboid-characters-direction]], [[zomboid-skin-style-reference]].
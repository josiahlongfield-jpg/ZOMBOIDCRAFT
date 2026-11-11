---
name: full-detail-previews-rule
description: STRICT (Josiah 2026-11-10): every preview sent to him must be rendered in full detail, never quick flat-shaded check renders
metadata:
  type: feedback
  modified: 2026-11-10T09:37:54.142Z
---
Josiah 2026-11-10 19:37: "always render previews in full detail so i dont accidentally assume youve changed something".

**Why:** a motion-check video from zskview.py (flat colour per triangle) made him think the full-detail Covenant vehicles had been downgraded to blocky models.

**How to apply:** anything shown to him (videos, stills, pages) uses the full-detail renderer with real textures and lighting (e.g. three.js pages like /tmp/claude-0/cov/web/veh.html / hv2.html with the bake textures). Quick check tools (zskview.py etc.) are for your own eyes only. If a cheap render is unavoidable, say plainly that it's not the real look. Related: [[endless-night-covenant-enemies]].

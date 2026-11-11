---
name: endless-night-unreal-move-scoping
description: 2026-11-11 decision doc scoping a move of Endless Night from Minecraft Fabric to Unreal 5 (vs MCC mod tools); awaiting Josiah's decision
metadata:
  type: project
  modified: 2026-11-11T12:05:53.834Z
---
Josiah asked 2026-11-11 if Minecraft is the best engine. Doc "Moving Endless Night to Unreal Engine 5" (Claude Doc, https://claude.ai/artifact/SbZhp8CNK595V3RbLXFZL1), thread "Scope moving Endless Night to Unreal 5".
Recommendation: move to UE5 via phases with gates; skip MCC mod tools (perfect campaign but can't add Endless Night systems; MCC mods need Steam MCC, his is Xbox app version). Phase 0 = second NVMe drive (~200 GB needed, C: ~14 GB free), check RAM (unknown), install UE5 + VS. Phase 1 = 2-3 week trial importing m20 Tip of the Spear, gate = he plays it. Then whole world, core loop, multiplayer/bots/lobby, rest. Rough total 2-3 months to parity (revised from 6-9 after Josiah pointed out the project pace; slowdown vs Minecraft = builds/tests must run in the UE editor on his PC).
**How to apply:** nothing in the Minecraft game or other threads changes until Josiah decides. Note his PC has Unity "My project" folders (he has tried Unity).
Related: [[zomboid-reach-packed-build-tech]], [[zomboid-reach-layout-question]].
- 2026-11-11 22:14 (his clock) Josiah: "start immediately" (Phase 0 begun). PC facts: 32 GB RAM (2x16 DDR4-3200, 2 slots free), ONE drive Kingston NV2 2 TB NVMe, C: 25.7 GB free; ASUS PRIME B550M-A WIFI II, 1 spare M.2 slot. UE 5.8 ALREADY installed (C:\Program Files\Epic Games\UE_5.8). No Visual Studio. additions\halo-reach 26 GB, halo-4 4.2 GB. Full-drive scans thrash his PC: measure gently. Trial can run without VS (Python editor scripting + Blueprints).

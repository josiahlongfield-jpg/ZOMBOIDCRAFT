---
name: zomboid-server-capacity
description: Server capacity plan 2026-11-08 (Josiah asked if a MC server can handle the purge plan): horde savings, CI horde bench numbers, mods to add, hosting options awaiting his pick
metadata:
  type: project
  modified: 2026-11-08T11:17:08.662Z
---

Josiah 2026-11-08 10:26 asked for an honest view on whether a Minecraft server can handle the purge plan; 10:28 "start with step 1 but look into step 2 and anything else ... then give me options for step 3". Write-up doc: https://claude.ai/artifact/QfY9K7qNZfvN8WtR1Aj3k8 (Docs id bfa3a52b-374f-446e-be58-f69d4ec5c0ad).

Step 1 (FPS thread, playtest 9439bbc1c): zombie/perf/ Resting (walker standing still skips LivingEntity.travel + applyEffectsFromBlocks; full move every 10th tick), QuietCollisions (skip Level.getEntityCollisions in Entity.collide while no canBeCollidedWith entity is loaded; our cars/lifts/trains all return false), SharedPaths (redirect PathFinder.findPath in PathNavigation.createPath: key = start (x>>1, y, z>>1) + target + accuracy, kept 20 ticks, copies node list), InvestigateNoiseGoal re-plans only past max(3, 0.2*dist). Config performance.restingWalkers / quietCollisions / sharedPaths. HordeStats counts; /zc perf prints them.

CI bench: .github/workflows/hordebench.yml + gametest HordeBenchTest (800 walkers, walled yard, pillar, moving noise; matrix plain / Lithium via -Pbenchlithium). Run 42473637428: none 26.2 ms, ours 20.8, Lithium 17.5, both 15.9. Lithium 0.26.3 loads with our mod; its own collide redirect wins over QuietCollisions (require=0).

Step 2 advice: add Lithium, FerriteCore, Chunky now; Spark + Krypton with a server; try EntityCulling/ImmediatelyFast on his PC; C2ME/ScalableLux later (alpha); skip ServerCore (mob sleeping vs hordes-never-vanish). Offered to install via RC.
Step 3 options (awaiting pick): A separate server on his PC (16 cores) [recommended start], B rented Sydney MC host (~US$2.50/GB), C rented whole Sydney machine (OVH Game 9800X3D, price unconfirmed), D home server PC.

Next code levers: chasing zombies re-planning, crowd step-up collision checks; Covenant/Noble AI must time-slice thinking.
SWITCH 2026-11-09 07:51 (Josiah): all future FPS/horde work goes to repo ENDLESS-NIGHT (com.endlessnight), instance ENDLESS NIGHT, files /mnt/project-files/endless-night/perf/; old ZOMBOIDCRAFT frozen (rules /mnt/project-files/endless-night/SWITCH.md, [[endless-night-new-repo]]). Mod advice above now applies to the ENDLESS NIGHT instance.
Related: [[zomboid-render-distance-limit]], [[zomboid-hordes-never-vanish-rule]], [[zomboid-purge-direction]].

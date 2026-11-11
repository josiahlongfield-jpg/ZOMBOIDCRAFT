---
name: zomboid-characters-direction
description: Josiah's 2026-10-25 direction for ZomboidCraft zombie and player models: Project Zomboid-style mitten hands, pronounced features, not overly realistic
metadata:
  type: feedback
---

(2026-10-25 22:22-22:25, thread "Build the ZomboidCraft landscape")

He asked whether characters must stay Minecraft figures. I said no: Minecraft builds characters from boxes, but we choose the boxes, skins and animations.

Josiah (22:25): "I wouldn't want the enemies and characters to look overly realistic, I'd be happy moving them closer to a zonboid design, with the hand that has no discernible fingers but features are a lot more pronounced."

**How to apply:**
- Use Zomboid proportions: longer torso, narrower limbs, defined shoulders.
- Hands are mittens: one block plus a thumb.
- Faces get pronounced 3D features (brow, nose, jaw, ears) and a hi-res painted skin.
- Clothes and gear are separate layers.
- Add wound and damage states.
- Animations: shuffle, lurch, reach, crawl.
- The player shares the same body.
- Show mockups first.

I asked the coordinator for a separate thread for this. Related: [[zomboid-building-detail-direction]], [[minecraft-zomboid-mod]].

**2026-10-25 22:40 (thread "Redesign zombie and player bodies"):** Josiah approved building this plan (via coordinator, 22:26). Mockup 1: https://claude.ai/artifact/3QvbfWqfsgb4mJh2LKWkeX (Today vs A "Zomboid" proportions vs B "Pronounced" big head/brow/nose/mitts vs player; elbows+knees, thumb that closes, torn clothing shell, first-person arms with crowbar). Asked him: A, B or A body + B face (my pick); movement feel; whether player arms replace the gun first-person arms. Building on A body + B face meanwhile. Source: scratchpad only (bodies/index.html), cdnjs is blocked for local screenshots here.

**2026-10-25 22:53 Josiah:** remove the thumbs; KEEP today's skins (he likes their detail) and add the joints (knees, elbows) and the slimmer look. Built on branch claude/project-thread-wfvzb7: WalkerModel splits limbs (ELBOW 5, WRIST 4, KNEE 6) sampling the same player-layout skin rows, limb xz scale 0.8/0.86, head 0.9, optional face blocks; gen_walker_addons.py moves pieces below a joint onto right_forearm/right_shin etc. Soldier preview https://claude.ai/artifact/R4k3dc38HqkgGSkZRomZAS. Asked: keep raised face blocks or flat painted face? Playtest got the thumb version at 175e050 (not sent to him). Player body/arms touch the weapons thread's PlayerModelMixin/HoldPoses/RifleArms: coordinate before changing.

**2026-10-25 22:58 Josiah (final direction so far):** NO face blocks, NO thumbs. Keep today's skins. He wants the slim shape and fluid walk of mockup 1's "player"/body A (it "adds a little believability to the original design"). Implemented: arms xz 0.68 y 1.1 at shoulder x 4.4, legs xz 0.85 longer (hip at 11.04), torso 0.9 wide 0.92 tall, head 0.8 on a 1.2 neck, deeper knee/elbow bends, body dip per step; still 2 blocks tall. Soldier preview v2 same link.
Shipped slim jointed walkers in playtest 208c987 (CI green, jar link sent 23:0x). Next: his in-game feedback, then player body/arms.

**23:11 Josiah:** wants the EXACT mockup-1 'player' model (body A geometry: tapered chest/waist, pelvis, deltoid caps, separate thigh/shin/foot, upper/fore/hand) and its exact walk joint movement, with NO neck (head on body), no face blocks, no thumbs, plain basic skin. Said 'preview first, don't build yet'. Preview: https://claude.ai/artifact/1Hk8jvVcqbsKs4GTSZsbkk (source scratchpad player/make.py from bodies/index.html). Building it in-game means new geometry that the current skins don't map onto 1:1; raise that when he says go.

**23:16 Josiah:** shoulders dropped flush with torso top, arms shortened slightly (upper 5.7, fore 5.1) — preview v2 same link. He asked if players lose their own skins: told him no, each block samples the matching region of a normal 64x64 skin (like the walkers), wide skins squeezed slightly. Still preview only, waiting for go.

**23:20:** soldier skin on the mockup player body (body A v2, skin sampled by vertical slices of each vanilla part; head+body addon pieces scaled in, limb pieces skipped): https://claude.ai/artifact/Pp1bQP48f9LZxhCEhK3v2L (scratchpad player/skinned.js). Preview only, waiting for go.

**23:29 Josiah:** old addon pieces on the new body 'blend into one, no definition'; wants our own detailed 3D gear per outfit, as detailed as possible. Built a soldier kit (helmet+cover+band+chin strap, flak vest+collar+flaps+zip, suspenders, pistol belt+buckle+ammo pouches+canteen+first aid, laced jungle boots) with hard dark edges, lit tops, stitching; preview v3 same soldier link (scratchpad player/skinned.js soldierKit, build_soldier.py, harness.js = node mock of THREE to catch load errors since cdnjs is blocked here). Preview only.

**23:35 Josiah:** likes the 3D gear style; asked to redo the clothing and boots the same way. v4: BDU woodland shirt (rolled sleeves, sleeve pockets, cuffs), trousers (cargo pockets+flaps, knee patches, bloused cuffs), detailed jungle boots, all 3D gear() blocks; only hands/forearms/face from skin. Direction implied: outfits become fully 3D-built kits on the new body. Preview only.

**2026-10-26 01:25:** Josiah asked for the most detailed painted body skin (not 3D add-ons), moody, free to drop the current face design. Built a procedural painter (scratchpad player/paintskin.js, 1024x1024 standard 64 layout): comic-inked, mottled, cel-shaded, zombie (milky eyes, dark eye mask, torn cheek/teeth, veins, lividity, bites, bone at higher decay) and survivor variants, 3 tones, 3 decay levels. Preview: https://claude.ai/artifact/8zHx2mMR516xaBf9e7fAoL. Local check trick: Chromium + playwright can render the 2D skin canvas (sheet_test.html, shot.js) even though three.js can't load offline.
**01:39:** Josiah plans to get skins from an image AI. Plan: he sends a front/back/side turnaround painted over our exact template (/mnt/project-files/zomboid-mod/skin-template/zombie-turnaround-template.png, 3072x2048, 52.3 px per model unit, soles at y=1938; made by scratchpad player/template.py), I project it onto the 64-layout skin at 1024 and preview it on the body.
**02:00:** Josiah's first AI turnaround (zombie in boxers, 1536x1024) matched the template almost exactly. Cut with /mnt/project-files/zomboid-mod/skin-template/cut_skin.py (front/back views -> front/back faces, side view -> limb/head sides, torso sides from the front edge, tops/bottoms from patches) into ai-skin-1/zombie_boxers_{1024,256,64}.png; on the body preview as "Your AI skin" (same artifact).

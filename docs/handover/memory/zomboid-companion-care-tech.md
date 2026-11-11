---
name: zomboid-companion-care-tech
description: Companion brain tech (2026-10-31): NpcCare (offers, real ammo, kit, LookAfter/Gather goals), NpcSkills on shared Skill names, learn-by-watching hook, CompanionCareTest film
metadata:
  type: project
---

Built 2026-10-31 on claude/project-thread-pd5a26 from Josiah's answers in [[zomboid-companion-trades-plan]].

- NpcSkills: xp Map keyed by shared com.zomboidcraft.skills.Skill NAME (AIMING, FIRST_AID, COOKING, CARPENTRY, FORAGING, AGRICULTURE for her six work skills; enum NpcSkills.Skill keeps AIM..FARMING constants with .key). Player curve 25n(n+1). Save "v2 KEY=xp read:KEY=bits"; old 6-float saves convert keeping level+progress. practise() x world xpMultiplier x her book boost. Skills.addXp calls NpcSkills.watched (bonded = in npc.bonds set, 8 blocks, line of sight, companions.watchShare 0.25).
- NpcCare: offers are a CUSTOM_DATA mark zcOfferFor/zcOfferAt on the stack in her pack (gold-edged in her page's bag, TAKE from the page clears it and ignores trust). important(): any gun, attachment, BACK bag, Rare+ melee, bandage/splint while you're <75%, skill book you haven't read for your level, ammo/mag for a gun you carry. Unanswered 24000 ticks -> hers. Talk acts NpcNet OFFER_TAKE 52, OFFER_KEEP 53, CARRYING 54; TalkScreen rows 10 (key 0) carrying, 11/12 offer rows when npc.offer() (synced OFFER string) non-empty.
- Real ammo: gun's GunState.rounds is her magazine; reload draws loose rounds then MAG_ROUNDS from her pack; out => armed() false => Flee as unarmed. companions.endlessAmmo restores old behaviour.
- kit(): BACK bag => pack = 9 + bag slots (NpcWork.resize, bag saved as npcBag before Items load); best gun she has rounds for; attachments into free slots (never magazines). keeps(): offered, her ammo, bandages, unread books, fitting attachments stay out of deposits; packEmpty() ignores kept items.
- Goals: LookAfter prio 4 (eat from pack, kit, heal self <60% with bandage, read 15 s under light >=7 when you stand still), Gather prio 5 (following only: ground items within 8 / leader 14, fresh RandomizableContainer with getLootTable()!=null, takes 3 wanted stacks). TakeGun removed.
- Film: src/gametest/.../client/npc/CompanionCareTest + .github/workflows/companionshots.yml push frames + log.txt to branch shots/pd5a26; stitch with ffmpeg in scratch.

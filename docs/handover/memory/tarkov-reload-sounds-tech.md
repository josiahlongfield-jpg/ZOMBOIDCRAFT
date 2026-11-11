---
name: tarkov-reload-sounds-tech
description: Where Tarkov keeps reload sound events (container bundle StaticData, not AnimationClip events), the 2026-11-08 audit of our 30 guns, and the MP-18 missing-round finding
metadata:
  type: project
  modified: 2026-11-08T19:25:00.000Z
---
Josiah 2026-11-08 10:58: many guns miss reload audio; MP-18 shows no round going into the chamber. Plan (sound timeline per gun + MP-18 round mesh, previews of M4A1 + MP-18 first) approved 11:17. Previews sent 11:3x: https://claude.ai/artifact/L9aEK4v5GnmDTBnC6PtWmz (videos also in /mnt/project-files/zomboid-mod/tarkov/previews/reload-sounds/), Josiah 11:41: sounds fine; fixed preview M4 left hand (renderer gripHand bent wrist; vid3.js now takes env GRIPIK=0, game M4 has no holds) + MP-18 round (zc_round/zc_case q must be IDENTITY under patron_in_weapon_000: mesh is along Y tip -Y; the ammo prefab's -90X stood it upright). Re-sent v2 11:5x. BUILT 12:0x: code 14bcec87e+8de6da2cf (branch), cherry-picked to playtest 6a5983ec9 (ammo counter NOT on playtest). Tools scratch /tmp/claude-0/tk/snd_all (segments.py=b2/b3 piece offsets, gen.py=cues, banks.sh, tksounds.py, stall_<g>.json per-controller states). INSTALLED on his PC 2026-11-08 ~19:2x (staged copy in tarkov/local-pc/reload-sounds-20261108: 30 cues, MP-18 zsk+gun json 'shown', merged pack sounds.json 566 entries + 700 oggs in 'ZomboidCraft Tarkov Sounds' pack). Playtest 13896af8c CI green. LocalGuns.Cues (sounds/tactical/load lists, tk.<name> events); Guns.java now plays Cue lists (playCues per part, shells 'shell<k>', finish 'end' by clock via State.later queue, pump by step). Conditions: mode2 = param<=v, mode3 = param>=v (pairs); see snd_all/conds.py.

WHERE THE EVENTS ARE: AnimationClips have NO m_Events. Each gun's `<gun>__weapon_..._container.bundle` holds MonoBehaviours (read MonoScript m_ClassName):
- AnimationEventsStateBehaviour: FullName "Hands.RELOAD OUT" etc, EventsData PPtr + EventsListId.
- AnimatorControllerStaticData._stateHashToEventsCollection[EventsListId]._animationEvents: _functionName "Sound" + Parameter.StringParam (e.g. MagOut), _time = NORMALISED 0..1 of the state's clip, EventConditions (non-empty = conditional, e.g. BoltOut? on M4 charge).
- WeaponSoundPlayer.AdditionalSounds: EventName "Snd"+StringParam -> SoundClips (variants; some m_FileID!=0 = dependency bundles).
- AnimatorController (same bundle) maps states to clips: m_Controller.m_StateMachineArray[].data.m_StateConstantArray[].data -> m_BlendTreeConstantArray -> m_NodeArray m_ClipID into m_AnimationClips (load client_assets alongside to name them).
Some containers have no StaticData; their animator is a sibling's: ak102->ak105, vepr->akm vpo_209, m1911a1->m45a1, mdr762->mdr 556, mp5k->mp5 navy3, pm->pm threaded, rhino 200ds->50ds, scar->scar fde, vector 45->vector 9x19, val/sr3m->probably vss (verify).
Raw bundle names on his PC: C:\Dev\Zomboidcraft-Desktop\additions\tarkov-pilot\raw\EscapeFromTarkov_Data__StreamingAssets__Windows__assets__content__weapons__<dir>__..., EXCEPT the pilot M4A1 files, which start "assets__content__weapons__m4a1__". Steam folder files are 8 deep (can't stage).

AUDIT: Tarkov reloads play 8-12 sounds (RELOAD OUT: Button MagOut MagSbrosnik; RELOAD IN: MagPullOut GunFlip MagIn; CHARGE END: HandleOut BoltOut HandleIn BoltIn HandOn...). We play 3 composed events (mag_out, mag_in, bolt/slide via Guns.cueSounds), and only 9 guns have reloads/<id>.cues.json; the other 21 use fixed GunType ticks.
MP-18: operator_tarkov_mp18.zsk has no patron mesh (rig animates patron_in_weapon_000; mesh lives in an ammo bundle). M870 zsk has patron_12x70_full_LOD0.
Related: [[tarkov-assets-pipeline-tech]], [[tarkov-weapon-batch1]], [[tarkov-operator-ingame-tech]].

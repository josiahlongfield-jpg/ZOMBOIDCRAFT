---
name: endless-night-cortana-wholebody
description: Cortana rounds 20-21 (2026-11-11): full TWH fingers + shoulders published v21; round 21 purposeful head/spine/breath/hips/knees layers
metadata:
  type: project
  modified: 2026-11-11T04:35:28.739Z
---
Continues [[endless-night-cortana-zeggs]]. Samples artifact 2MWrQVCMjebc2Y2j6GCKX8 (v21 = round 20, clips body/l{i}_twhfull.mp4 vs round 19 _twh).
- 04:31 Josiah: "give every part of the body the same dynamic movement capabilities" (purpose-driven, like fingers/shoulders).
- Round 21 (compose.py body_plan + apply_body, backup compose_r20.py; spec 'body' false disables; 'nods' and 'stress' word lists, stress defaults to mark words):
  head nods on AFFIRM words / spec nods, small beats on stressed words, shake on NEGATE words, tilt+lift over questions and when tender;
  spine lean in tender/sad/stress, slump on sigh, back on laugh/chuckle (bounce), upright urgent; chest rise (joint 9 -x) before sentences, sigh in-breath, slow breaths in pauses >1.2 s;
  weight shift per sentence in relaxed moods (pelvis roll 3.5, spine counter-roll, trans x 4.5 cm after keep_root 0.6, small drop), squared + 2.5 cm sink when urgent; knees bend via planted-feet leg IK in rt.
  Sign checks: spine +x = lean forward, pelvis global +z = left hip up.
- MaskedMimic on his PC still waiting for him to say he's on.
- 04:44 Josiah: shoulders "stuck in a rigid upward position", too square. Cause: rt aimed her clav at the capture's absolute collar angle (SMPL rest slopes up 28 deg; her rig rest slopes down ~10). Fix in rt.py: CLAV_DELTA (env CLAVD) maps capture's relaxed collar dir (CAP_RELAXED, mean of 6 lines in chest frame J12-J3/J13-J14) onto her rig rest lowered CLAV_DROP 4 deg (env CLAVDROP); only movements on top. Published v22 = round 21 (clips body/l{i}_body.mp4 vs round 20). Raise CLAV_DROP if still high.
- 05:09 legs (round 22): body.py _leg 'heel' (ball of foot fixed), mk loads <gx>.legs.npy (weight side V) -> free leg heel 12, fwd 3 cm; weight shift pelvis roll 5, 6 cm, drop 1.2 cm; knee dips on stress. Backups body_r21/rt_r21/mk_r21.
- 05:11 he asked if tuning translates to game: told him yes (rules run on any line; needs PC runtime). 05:18 he OK'd fast finger index: twh/twf.py (PCA16 of 6-frame window feats + cKDTree top-64 rerank) fidx.pkl 726 MB, 0.25 s/line vs ~60 s, ~2 mm diff; run20.sh uses twf.
- Round 22 published v23 (body/l{i}_legs.mp4 vs round 21). Fix: base GestureLSM already stood with one knee ~28 deg bent, both bent after shift; rt.pose(weight=(side,amt)) sets hip height so weight leg ~9 deg (need() frac .997, free leg allowed +3.5 cm), free foot heel 18 + forward 7 cm -> free knee 25-50 deg. Front camera hides forward knee bend.
- MaskedMimic on PC (2026-11-11 19:54 his clock he said "proceed"; 20:22 "install it" = pip OK): C:\Dev\ProtoMotions at v3.2 (last with SMPL MaskedMimic ckpt, 229 MB; main dropped it Oct 28). Backend Newton 1.0 GPU (Windows native), venv C:\Dev\ProtoMotions\.venv_newton (~3.5 GB torch cu128 + newton + mujoco). Ckpt trained in IsaacLab: balance under Newton unproven; fallback WSL2+IsaacLab ~30 GB (ask first). Plan: fixed_conditioning Head+hands+Pelvis, ankles free; script additions\cortana-mimic\mimic_from_npz.py.
- 20:5x his clock: installed (.venv_newton 5.6 GB, total 10.6 GB). Under Newton the ckpt FALLS in ~1 s even on NVIDIA's own eval walk (MuJoCo worse); 0.2x realtime. additions\cortana-mimic\mimic_from_npz.py (SMPL-X npz -> pinned head/hands/pelvis -> out.npz SMPL 72) + run_inference_win.py (no NCCL). C: free 28.8 GB (was 60). Decision card posted: A WSL2+IsaacLab (rec; he must run admin `wsl --install -d Ubuntu-24.04` + reboot) / B finetune in Newton / C stop + delete.
- 20:52 WSL blocked: SVM off in BIOS (Ryzen 7 5700X, ASUS PRIME B550M-A WIFI II: Del, F7, Advanced > CPU Configuration > SVM Mode). Told him + `wsl --install --no-distribution`; he replies "BIOS done". C: 37.6 GB free after restart. He has no other drive.
- 21:5x: Windows test venv deleted (~6 GB; ProtoMotions code+ckpt kept, Ubuntu uses them). Ubuntu user josiajosiah_unix; ~/env_isaaclab venv: Isaac Sim 5.1 + IsaacLab 2.3.2 + torch (18 GB). Waiting on him to accept Omniverse EULA (python -c "import isaacsim" in Ubuntu). NordVPN running (maybe dropped downloads). Disk thrash = install + scan (scan stopped).

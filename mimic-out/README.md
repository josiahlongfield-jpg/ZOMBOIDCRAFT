# MaskedMimic outputs for Cortana's six lines (motion arrays only)

Made on the PC with NVIDIA MaskedMimic (ProtoMotions v3.2, SMPL checkpoint, IsaacLab CPU PhysX).
Conditioning: head + both hands (position + rotation), pelvis position (soft), legs free.
Simulated body is the mean-shape SMPL humanoid (betas are not used). 30 fps, same frame count as the input clip.

Keys (use the *_input_frame ones to drop straight back onto the clip: Y-up, facing +z, same place as the input `trans`):
- `poses_input_frame` (T x 72 SMPL axis-angle; the first 66 match SMPL-X body joints 0..21), `trans_input_frame` (T x 3, pelvis position, same convention as the input `trans`)
- `sim_body_pos_input_frame` (T x 24 x 3)
- `poses`, `trans`, `sim_body_pos`, `sim_body_rot` (xyzw), `ref_body_pos`: the same in the simulator's Z-up world (faces -y)
- `body_names`, `fps`, `fell` (per frame), `track_err_<Head|L_Hand|R_Hand|Pelvis|L_Ankle|R_Ankle>` (m, per frame), `sim_to_input_offset`, `input_up`

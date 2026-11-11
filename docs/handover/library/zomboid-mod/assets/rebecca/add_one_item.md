# Add one new clothing item to Rebecca

Every round uses a new number N (2, 3, 4...). Last time's result is `mix_clothes.json`; the first time you do this, use that as the "last result".

## 1. Blender
1. Open `Rebecca_pSfTNBj_zc.blend`. Don't touch the pose.
2. Outliner: turn the new piece's eye, monitor and camera **on**.
3. Spanner (Modifiers) tab on the piece: Subdivision / 細分 / outline (GeometryNodes, Line) have **monitor and camera off**. Armature stays on.
4. Outliner: click **Vanilla**, then Ctrl-click the new piece. Exactly 2 should be selected.
5. File > Export > FBX: Limit to Selected Objects ✔, Object Types **Mesh** only, Apply Modifiers ✔.
6. Save as e.g. `ITEM2.fbx` straight into `C:\Dev\zomboidcraft\tools\npc\skinned`.

## 2. Command Prompt
```
cd /d C:\Dev\zomboidcraft\tools\npc\skinned
node run.js "clothes.html?file=ITEM2.fbx" clothes2.json text
python add_clothes.py mix_clothes.json clothes2.json mix_clothes2.json
```
- The first command prints `meshes Vanilla.Skin:... NEWNAME:1234`. Write down **NEWNAME**: that's the mesh name for the spec.
- The second should say `miss 0.00 cm` (anything under 1 cm is fine).

## 3. Find its texture
In Blender, click the piece, open the red-ball **Material** tab, and note the material name, for example `MI_R2T1RebeccaMd10011Up1`. The matching picture in `rebecca\textures` ends in `_D.png`, for example `T_R2T1RebeccaMd10011Up1_D.png`.

## 4. Edit rebecca_spec.json
Open `C:\Attachments\OneDrive\Desktop\rebecca\rebecca_spec.json` in Notepad.

**a) In "groups"**, after the last `}` of the last group (before the `]`), add a comma and:
```
  {"mesh": "NEWNAME", "piece": "PIECENAME", "texture": "TEXKEY", "both": true, "push": 0.003, "lods": [1.0, 0.4]}
```
- **PIECENAME:** what you'll type in game. Use lowercase and no spaces, e.g. `hat`, `belt`, `top.vest`.
- **Sharing a slot:** start the name with the same word and a dot, e.g. `top.` like `top.jacket` and `top.hoodie`, and only one of them can be worn at a time.
- `both: true` draws the inside too (good for jackets and skirts), and `push` lifts the piece off the skin a little so it doesn't flicker.

**b) In "textures"**, if it's a picture not already listed, add a comma after the last entry and:
```
  "TEXKEY": {"file": "textures/THE_FILE_D.png", "size": 1024}
```
If it uses one that's already listed (`up`, `up1`, `down`, `gloves`...), skip this and just use that key.

Save. If the pack step later says "JSON" or "Expecting", a comma is missing or extra.

## 5. Pack and install
```
cd /d C:\Attachments\OneDrive\Desktop\rebecca
python C:\Dev\zomboidcraft\tools\npc\skinned\pack.py rebecca C:\Dev\zomboidcraft\tools\npc\skinned\mix_clothes2.json rebecca_spec.json out
```
Copy `out\rebecca.zsk` into `...\MEDIEVAL GLOOM (1)\config\zomboidcraft\npc`, restart the game, then `/zc npc rebecca` and `/zc npc wear PIECENAME`.

## Next time
Use `mix_clothes2.json` as the input and make `clothes3.json` / `mix_clothes3.json`, and so on. Never delete `mix.json`.

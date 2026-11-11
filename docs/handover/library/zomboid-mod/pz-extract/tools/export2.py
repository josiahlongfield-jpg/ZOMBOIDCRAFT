"""Export male + female PZ player characters. usage: python3 -I export2.py <media> <out.json>"""
import sys, json
sys.path.insert(0, sys.argv[0].rsplit('/', 1)[0])
import export as E  # reuse helpers (runs zombie export side effects guarded below)
media, out = sys.argv[1].rstrip('/'), sys.argv[2]
def char(body, parts, clips):
    root, b = E.mesh_data(f'{media}/models_X/Skinned/{body}')
    fr = E.frames(root)
    skel = [{'name': f['name'], 'parent': f['parent'], 'm': [round(float(x), 6) for x in f['m'].flatten()]} for f in fr]
    P = {'body': b}
    for k, (path, keep) in parts.items():
        _, ms = E.mesh_data(f'{media}/models_X/Skinned/{path}', keep)
        P[k] = ms
    C = {k: E.anim(f'{media}/anims_X/{v}') for k, v in clips.items()}
    for k, m in P.items(): print(body, k, [(x['name'], len(x['pos'])//9) for x in m])
    return {'skel': skel, 'parts': P, 'clips': C}
male = char('MaleBody.x', {'trousers': ('Clothes/Bob_Trousers.x', {'Bob_Trousers'}), 'hair': ('Hair/Bob_Hair_Short.x', None)},
            {'idle': 'Bob/Bob_Idle.x', 'walk': 'Bob/Bob_Walk.x', 'run': 'Bob/Bob_Run.x', 'sprint': 'Bob/Bob_Sprint.x', 'sneak': 'Bob/Bob_RunSneak.x'})
female = char('FemaleBody.x', {'trousers': ('Clothes/Kate_Trousers.x', None), 'hair': ('Hair/Kate_Hair_Long.x', None)},
            {'idle': 'Kate/Kate_Idle.x', 'walk': 'Kate/Kate_Walk.x', 'run': 'Kate/Kate_Run.x', 'sprint': 'Bob/Bob_Sprint.x', 'sneak': 'Bob/Bob_RunSneak.x'})
json.dump({'male': male, 'female': female}, open(out, 'w'), separators=(',', ':'))

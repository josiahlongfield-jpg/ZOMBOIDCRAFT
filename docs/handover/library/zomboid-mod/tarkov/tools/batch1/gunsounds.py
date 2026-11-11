"""Adds one Tarkov gun's sounds to the local pack "ZomboidCraft Tarkov Sounds" (LOCAL ONLY, never the repo or the jar).
Each of our events gets Tarkov's own takes for that gun, layered where its clip moves the part, at Tarkov's own levels.
usage: gunsounds.py SND_DIR PACK_DIR GUN_ID SPEC_JSON   (SPEC: {event: [[wav, delay s, gain], ...] or [[[...]], ...] variants, "_att": {event: blocks}})"""
import json, os, subprocess, sys, shutil, tempfile
SND, PACK, G, SPEC = sys.argv[1], sys.argv[2], sys.argv[3], json.loads(sys.argv[4])
att = SPEC.pop('_att', {})
snd = os.path.join(PACK, 'assets', 'zomboidcraft', 'sounds', 'local', G)
shutil.rmtree(snd, ignore_errors=True); os.makedirs(snd)
sj = os.path.join(PACK, 'assets', 'zomboidcraft', 'sounds.json')
out = json.load(open(sj)) if os.path.exists(sj) else {}
for k in [k for k in out if k.startswith(f'weapon.local/{G}/')]: del out[k]
work = tempfile.mkdtemp()
for ev, variants in SPEC.items():
    if variants and isinstance(variants[0][0], str): variants = [variants]
    entries = []
    for vi, layers in enumerate(variants):
        inp, flt = [], []
        for i, (n, d, g) in enumerate(layers):
            inp += ['-i', os.path.join(SND, n + '.wav')]; ms = int(d * 1000)
            flt.append(f'[{i}:a]aformat=sample_rates=44100:channel_layouts=mono,volume={g},adelay={ms}[a{i}]')
        mix = ''.join(f'[a{i}]' for i in range(len(layers))) + f'amix=inputs={len(layers)}:normalize=0,alimiter=limit=0.97[o]'
        file = ev.replace('weapon.', '').replace('.', '_') + ('_%d' % (vi + 1) if len(variants) > 1 else '')
        subprocess.run(['ffmpeg', '-v', 'error', '-y'] + inp + ['-filter_complex', ';'.join(flt) + ';' + mix, '-map', '[o]',
                        '-ac', '1', '-c:a', 'libvorbis', '-q:a', '5', os.path.join(snd, file + '.ogg')], check=True)
        entries.append({'name': f'zomboidcraft:local/{G}/{file}', 'attenuation_distance': att.get(ev, 16)})
    out[f'weapon.local/{G}/{ev}'] = {'sounds': entries}
    print(ev, len(entries))
json.dump(out, open(sj, 'w'), indent=1)
mc = os.path.join(PACK, 'pack.mcmeta')
if not os.path.exists(mc):
    json.dump({'pack': {'description': 'ZomboidCraft: Tarkov operator sounds (local only)', 'pack_format': 15,
                        'supported_formats': [15, 999], 'min_format': 15, 'max_format': 999}}, open(mc, 'w'), indent=1)
shutil.rmtree(work)

"""The operator's kitted M4A1 sounds as a local resource pack, "ZomboidCraft Tarkov Sounds" (2026-11-06).
Each sound goes in under weapon.local/tarkov_m4a1/<our event> (combat/LocalGuns.sound), loudness matched to our old event.
Handling sounds are layered so each lands where Tarkov's clip moves the part (cues in reloads/tarkov_m4a1.cues.json).
LOCAL ONLY: the pack lives on Josiah's PC, never the repo or the jar.
usage: python3 tarkov_soundpack.py SND_DIR REF_DIR OUT_DIR"""
import json, os, subprocess, sys, shutil, tempfile
import numpy as np

SND, REF, OUT = sys.argv[1:4]
G = 'tarkov_m4a1'
# event -> (reference ogg, attenuation, [variants], each variant = [(wav, delay s, gain)])
SHOTC = [[('m4a1_outdoor_silenced_close_onseshot_0%d' % k, 0, 1)] for k in range(1, 9)]
SHOTD = [[('m4a1_outdoor_silenced_distant_onseshot_0%d' % k, 0, 1)] for k in range(1, 9)]
EV = {
    'weapon.rifle.shot_suppressed': ('shot_suppressed', 40, SHOTC),
    'weapon.rifle.shot_suppressed_distant': ('shot_suppressed_distant', 40, SHOTD),
    # the release button, then the magazine sliding out
    'weapon.rifle.mag_out': ('mag_out', 16, [[('mcx_magrelease_button', 0, 1), ('mcx_mag_out1', 0.10, 1)]]),
    # a fresh magazine pulled from the pouch, then seated 0.9 s later
    'weapon.rifle.mag_in': ('mag_in', 16, [[('generic_mag_pouch_out7', 0, 0.8), ('mcx_mag_in2', 0.90, 1)]]),
    # the charging handle back, then let go
    'weapon.rifle.bolt': ('bolt', 16, [[('m4_check_boltout', 0, 1), ('m4_check_boltin', 0.33, 1)]]),
    'weapon.rifle.dry': ('dry', 16, [[('weap_trigger_empty', 0, 1)]]),
    # client only (operator first person): the rifle brought up, and the grip shifts of the inspect
    'draw': ('mag_in', 12, [[('weap_in', 0, 1)]]),
}


def env_db(path):
    a = subprocess.run(['ffmpeg', '-v', 'error', '-i', path, '-ac', '1', '-ar', '44100', '-f', 'f32le', '-'],
                       capture_output=True, check=True).stdout
    x = np.frombuffer(a, np.float32)
    if len(x) < 2205:
        return -60
    w = 2205
    r = np.sqrt(np.convolve(x * x, np.ones(w) / w, 'valid'))
    return 20 * np.log10(max(r.max(), 1e-6))


work = tempfile.mkdtemp()
pack = os.path.join(OUT, 'ZomboidCraft Tarkov Sounds')
shutil.rmtree(pack, ignore_errors=True)
snd = os.path.join(pack, 'assets', 'zomboidcraft', 'sounds', 'local', G)
os.makedirs(snd)
out = {}
for ev, (ref, att, variants) in EV.items():
    entries = []
    refdb = env_db(os.path.join(REF, ref + '.ogg'))
    for vi, layers in enumerate(variants):
        tmp = os.path.join(work, 'v.wav')
        inp, flt = [], []
        for i, (n, d, g) in enumerate(layers):
            inp += ['-i', os.path.join(SND, n + '.wav')]
            ms = int(d * 1000)
            flt.append(f'[{i}:a]aformat=sample_rates=44100:channel_layouts=mono,volume={g},adelay={ms}[a{i}]')
        mix = ''.join(f'[a{i}]' for i in range(len(layers))) + f'amix=inputs={len(layers)}:normalize=0[o]'
        subprocess.run(['ffmpeg', '-v', 'error', '-y'] + inp + ['-filter_complex', ';'.join(flt) + ';' + mix,
                        '-map', '[o]', '-ac', '1', tmp], check=True)
        gain = min(8.0, refdb - env_db(tmp))   # quiet takes are not pushed up into hiss
        file = ev.replace('weapon.', '').replace('.', '_') + ('_%d' % (vi + 1) if len(variants) > 1 else '')
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', tmp, '-af', f'volume={gain:.2f}dB,alimiter=limit=0.97',
                        '-c:a', 'libvorbis', '-q:a', '5', os.path.join(snd, file + '.ogg')], check=True)
        entries.append({'name': f'zomboidcraft:local/{G}/{file}', 'attenuation_distance': att})
        print(f'{ev:40} {file:34} {gain:+.1f} dB')
    out[f'weapon.local/{G}/{ev}'] = {'sounds': entries}
with open(os.path.join(pack, 'assets', 'zomboidcraft', 'sounds.json'), 'w') as f:
    json.dump(out, f, indent=1)
with open(os.path.join(pack, 'pack.mcmeta'), 'w') as f:
    json.dump({'pack': {'description': 'ZomboidCraft: Tarkov operator sounds (local only)', 'pack_format': 15,
                        'supported_formats': [15, 999], 'min_format': 15, 'max_format': 999}}, f, indent=1)
shutil.rmtree(work)

"""Josiah's Brita sound picks (pilot-sound-picks.txt v2) as a local resource pack: each local gun's sound goes in under
weapon.local/<gun>/<our event> (combat/LocalGuns.sound), loudness and hearing distance matched to our event it replaces.
Distant shots are made from the picked shot. Personal use only: the pack lives on his PC, never the repo or the jar.
usage: python3 soundpack.py ARSENAL_SOUND BRITA_SOUND OURS_ASSETS OUT_DIR"""
import json, os, subprocess, sys, re, tempfile, shutil
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import single_shot_fix as fix

ARS, BRI, OURS, OUT = sys.argv[1:5]
# gun -> {our event: (picked file, how)}; how: None as is, 'single' one clean shot, 'far' a distant take of it
SHOT = lambda ev, f, single=False: {ev + '.shot': (f, 'single' if single else None), ev + '.shot_distant': (f, 'far')}
SD = lambda ev, f: {ev + '.shot_suppressed': (f, None), ev + '.shot_suppressed_distant': (f, 'far')}
PICKS = {
    'Colt1911': {**SHOT('weapon.pistol', '[1]Shot_45', True), **SD('weapon.pistol', 'SD[1]Shot_45'),
                 'weapon.pistol.mag_out': ('Stormy1911ClipEject', None), 'weapon.pistol.mag_in': ('Stormy1911ClipLoad', None),
                 'weapon.pistol.slide': ('1911rack', None)},
    'SW_M500': {**SHOT('weapon.magnum', '[1]Shot_50MAG')},
    'AR57_PDW': {**SHOT('weapon.smg', '[1]Shot_57'), 'weapon.smg.shot_suppressed': ('SD[1]Shot_57', None),
                 'weapon.smg.mag_out': ('StormyARClipEject', None), 'weapon.smg.mag_in': ('StormyP90ClipLoad', None),
                 'weapon.smg.cock': ('P90rack', None)},
    'M2Carbine': {**SHOT('weapon.smg', '[1]Shot_9'), 'weapon.smg.shot_suppressed': ('SD[1]Shot_9', None),
                  'weapon.smg.mag_out': ('StormySUB2000ClipEject', None), 'weapon.smg.mag_in': ('StormySUB2000ClipLoad', None),
                  'weapon.smg.cock': ('SUB2000rack', None)},
    'AAC_Honey': {**SHOT('weapon.rifle', '[1]Shot_556'), **SD('weapon.rifle', 'SD[1]Shot_556'),
                  'weapon.rifle.mag_out': ('StormyARClipEject', None), 'weapon.rifle.mag_in': ('StormyARClipLoad', None),
                  'weapon.rifle.bolt': ('ARrack', None)},
    # AR-15: Brita gives it the same M16 sound set as the Honey Badger and the L86 (its script), so it takes his 5.56 picks
    'Bush_AR15_MOE': {**SHOT('weapon.rifle', '[1]Shot_556'), **SD('weapon.rifle', 'SD[1]Shot_556'),
                      'weapon.rifle.mag_out': ('StormyARClipEject', None), 'weapon.rifle.mag_in': ('StormyARClipLoad', None),
                      'weapon.rifle.bolt': ('ARrack', None)},
    'L86': {**SHOT('weapon.rifle', '[1]Shot_556'), **SD('weapon.rifle', 'SD[1]Shot_556'),
            'weapon.rifle.mag_out': ('StormyARClipEject', None), 'weapon.rifle.mag_in': ('StormyARClipLoad', None),
            'weapon.rifle.bolt': ('ARrack', None)},
    'AssaultRifle2': {**SHOT('weapon.battle_rifle', '[1]Shot_308', True), **SD('weapon.battle_rifle', 'SD[1]Shot_308'),
                      'weapon.rifle.mag_out': ('StormyFALClipEject', None), 'weapon.rifle.mag_in': ('StormyFALClipLoad', None),
                      'weapon.rifle.bolt': ('M1rack', None)},
    'BOAR': {**SHOT('weapon.heavy_sniper', '[1]Shot_50'), **SD('weapon.heavy_sniper', 'SD[1]Shot_50'),
             'weapon.hunting_rifle.bolt': ('Bolt1Rack', None), 'weapon.hunting_rifle.mag_in': ('StormyM82ClipLoad', None),
             'weapon.hunting_rifle.mag_out': ('StormyM82ClipEject', None)},
    'DSHK': {**SHOT('weapon.heavy_sniper', '[1]Shot_50'),
             'weapon.hunting_rifle.mag_out': ('StormyM240ClipEject', None), 'weapon.hunting_rifle.mag_in': ('StormyM240ClipLoad', None),
             'weapon.hunting_rifle.bolt': ('M240Rack', None)},
    'M870_CQB': {**SHOT('weapon.shotgun', '[1]Shot_12g'),
                 'weapon.shotgun.shell_in': ('R11-87StormyShotgunInsertRound', None), 'weapon.shotgun.pump': ('WinstormyShotgunPump', None)},
    'Winchester1866': {**SHOT('weapon.lever_action', '[1]Shot_45LC'), 'weapon.lever_action.round_in': ('MR2_insert', None)},
    'American180': {**SHOT('weapon.rimfire', '[1]Shot_22'), 'weapon.rimfire.shot_suppressed': ('SD[1]Shot_22', None),
                    'weapon.rimfire.mag_out': ('AmericanClipEject', None), 'weapon.rimfire.mag_in': ('AmericanClipLoad', None),
                    'weapon.rimfire.cock': ('AmericanRack', None)},
    'RPG_7': {**SHOT('weapon.grenade_launcher', '[1]Shot_Rocket'),
              'weapon.shotgun.shell_in': ('M32StormyShotgunInsertRound', None), 'weapon.rifle.mag_out': ('StormyM32ClipEject', None),
              'weapon.shotgun.pump': ('M32Rack', None)},
    'TenPointTurbo_XLT': {'weapon.crossbow.shot': ('TAC15Shot', None), 'weapon.crossbow.cock': ('TAC15Reload', None),
                          'weapon.crossbow.load': ('TAC15Insert', None)},
}


def src(name):
    for d in (BRI, ARS):
        for ext in ('.wav', '.ogg'):
            p = os.path.join(d, name + ext)
            if os.path.exists(p):
                return p
    raise FileNotFoundError(name)


def loudness(path):
    """How loud a take is where it's loudest: the highest 50 ms RMS, in dB (short clicks and long shots alike)."""
    import numpy as np
    a = fix.load(path)
    if len(a) == 0:
        return -60
    return 20 * np.log10(fix.env(a, 50).max())


ours = json.load(open(os.path.join(OURS, 'sounds.json')))
work = tempfile.mkdtemp()
pack = os.path.join(OUT, 'ZomboidCraft Brita Sounds')
shutil.rmtree(pack, ignore_errors=True)
snd = os.path.join(pack, 'assets', 'zomboidcraft', 'sounds', 'local')
os.makedirs(snd)
out = {}
for gun, picks in PICKS.items():
    g = re.sub(r'[^a-z0-9_.-]', '_', gun.lower())
    os.makedirs(os.path.join(snd, g), exist_ok=True)
    for ev, (name, how) in picks.items():
        base = ours[ev]['sounds'][0]
        ref = os.path.join(OURS, 'sounds', base['name'].split(':')[1] + '.ogg')
        raw = src(name)
        tmp = os.path.join(work, g + ev + '.wav')
        if how == 'single':
            fix.clean(raw, tmp, 24, 0.9)
        elif how == 'far':
            # heard from far off: the crack gone, a low rolling report
            subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', raw, '-ac', '1', '-ar', '44100', '-af',
                            'lowpass=f=1100,lowpass=f=1400,aecho=0.8:0.55:140|320:0.35|0.22,apad=pad_dur=0.4', tmp], check=True)
        else:
            subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', raw, '-ac', '1', '-ar', '44100', tmp], check=True)
        gain = loudness(ref) - loudness(tmp)
        file = ev.replace('weapon.', '').replace('.', '_')
        dst = os.path.join(snd, g, file + '.ogg')
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', tmp, '-af', f'volume={gain:.2f}dB,alimiter=limit=0.97',
                        '-c:a', 'libvorbis', '-q:a', '5', dst], check=True)
        entry = {k: v for k, v in base.items() if k != 'name'}
        entry['name'] = f'zomboidcraft:local/{g}/{file}'
        out[f'weapon.local/{g}/{ev}'] = {'sounds': [entry]}
        print(f'{gun:18} {ev:40} <- {name:32} {gain:+.1f} dB')
with open(os.path.join(pack, 'assets', 'zomboidcraft', 'sounds.json'), 'w') as f:
    json.dump(out, f, indent=1)
with open(os.path.join(pack, 'pack.mcmeta'), 'w') as f:
    json.dump({'pack': {'description': 'ZomboidCraft: Brita gun sounds (local only)', 'pack_format': 15,
                        'supported_formats': [15, 999], 'min_format': 15, 'max_format': 999}}, f, indent=1)
shutil.rmtree(work)

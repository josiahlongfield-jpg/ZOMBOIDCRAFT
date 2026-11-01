"""Builds the local-only "Zomboid zombie sounds" resource pack from Project Zomboid's own sound bank.

Josiah (2026-11-01): Zomboid's zombie audio completely replaces ours. Every zombie sound the game plays goes through the
zomboidcraft:pz.zombie.* events (ZedSounds); the jar's sounds.json points each one at our old sound so the game still
works without this pack, and this pack replaces them all with Zomboid's own samples. It also replaces the two horde
beds with crowds mixed from Zomboid's voices. The audio is Zomboid's: the pack goes in his own profile's resourcepacks
only, never in the repo, the jar or shared files.

    python3 -I tools/pz/zombie_sounds_pack.py ZomboidSound.bank OUTDIR

Voices are Zomboid's own: three per sex for shamblers (A, B, C) and two per sex for sprinters, each with its states.
A voice missing a state borrows it from another voice of the same sex (Zomboid's shamblers have no death cry: they
drop silent, so their death is the body falling, as in Zomboid). Needs ffmpeg.
"""
import json, os, random, re, shutil, subprocess, sys, tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pz_sounds  # noqa: E402

VOICES = ['fa', 'fb', 'fc', 'ma', 'mb', 'mc', 'sfa', 'sfb', 'sma', 'smb']
STATES = ['idle', 'aggro', 'chase', 'swing', 'bite', 'hurt', 'pushed', 'eating', 'reanimate', 'death']
# what's left once the voice's own name is taken off, first match wins
STATE_WORDS = [('reanimate', 'reanimate'), ('transition', None), ('idle', 'idle'), ('aggro', 'aggro'),
               ('attack', 'chase'), ('swing', 'swing'), ('bite', 'bite'), ('hurt', 'hurt'), ('pushed', 'pushed'),
               ('eating', 'eating'), ('death', 'death')]

# everything else a zombie makes: event -> sample name patterns (regex on the lower-case name)
OTHER = {
    'step.grass': r'^player_fs_grass_sneakers_walk', 'step.dirt': r'^player_fs_dirt_sneakers_walk',
    'step.gravel': r'^player_fs_gravel_sneakers_walk', 'step.snow': r'^player_fs_snow_sneakers_walk',
    'step.mud': r'^player_fs_mud_sneakers_walk', 'step.leaves': r'^player_fs_leaves_walk',
    'step.concrete': r'^player_fs_concrete_sneakers_walk', 'step.ceramic': r'^player_fs_ceramic_sneakers_walk',
    'step.wood': r'^player_fs_wood_hard_sneakers_walk', 'step.metal': r'^player_fs_metal_sneakers_walk',
    'step.carpet': r'^player_fs_carpet_sneakers_walk', 'step.glass': r'^player_fs_glass_walk',
    'step.water': r'^player_fs_water_walk',
    'thump.wood': r'^object_door_thump_wood_(no_detail|low_damage)', 'thump.wood_heavy': r'^object_door_thump_wood_high_damage',
    'thump.metal': r'^object_door_thump_metal_(no_detail|low_damage)', 'thump.metal_heavy': r'^object_door_thump_metal_high_damage',
    'thump.window': r'^object_window_thump(_thin_hit)?_\d', 'thump.fence': r'^object_fence_wood_thump_(no_detail|low_damage)',
    'thump.chainlink': r'^object_door_thump_chain_link_fence_(no_detail|low_damage)',
    'thump.garage': r'^object_door_thump_gate_garage', 'thump.car': r'^vehicle_impact_zombie_bash_(side|front)',
    'thump.car_window': r'^vehicle_impact_zombie_bash_window',
    'break.door': r'^object_door_(break|collapse_wood)', 'break.barricade': r'^object_barricade_plank_break',
    'break.barricade_metal': r'^object_barricade_metal_break', 'break.window': r'^object_window_smash_light_v2',
    'break.chainlink': r'^object_door_collapse_chain_link_fence_destroy',
    'claw.wood': r'^object_zombie_claw_wood', 'claw.metal': r'^object_zombie_claw_metal',
    'climb.window': r'^zombie_climb_window_trip',
    'bite.neck': r'^zombie_bite_neck', 'scratch': r'^zombie_scratch_gore_short',
    'bodyfall.concrete': r'^melee_body_fall_concrete', 'bodyfall.grass': r'^melee_body_fall_grass',
    'bodyfall.metal': r'^melee_body_fall_metal', 'bodyfall.wood': r'^melee_body_fall_wood',
}


def voice_of(name):
    """(voice, state) of a zombie vocal sample, or None."""
    n = name.lower()
    if not n.startswith('zombie_') or 'climb' in n or 'scratch' in n or 'bite_neck' in n:
        return None
    sprint = 'sprinter' in n
    if 'female' in n:
        sex = 'f'
    elif 'male' in n or n.startswith('zombie_vocal_b'):
        sex = 'm'
    else:
        return None
    toks = n.split('_')
    letter = next((t for t in toks if t in ('a', 'b', 'c')), None)
    if letter is None:
        letter = 'b' if 'gore_b' in n or '_b_' in n else 'a'
    rest = n
    for word, state in STATE_WORDS:
        if word in rest:
            if state is None:
                return None
            v = ('s' if sprint else '') + sex + letter
            return (v, state) if v in VOICES else None
    return None


def encode(src, dst, gain_db=0.0):
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', src, '-ac', '1', '-af', f'volume={gain_db}dB',
                    '-c:a', 'libvorbis', '-q:a', '5', dst], check=True)


def main():
    bank, out = sys.argv[1], sys.argv[2]
    f, data0, mode, ss = pz_sounds.samples(bank)
    tmp = tempfile.mkdtemp(prefix='pzs')
    voices = {v: {s: [] for s in STATES} for v in VOICES}
    other = {k: [] for k in OTHER}
    for s in ss:
        name = s.get('name', '')
        vs = voice_of(name)
        if vs:
            voices[vs[0]][vs[1]].append(s)
            continue
        for k, pat in OTHER.items():
            if re.search(pat, name.lower()):
                other[k].append(s)
                break
    if os.path.isdir(out):
        shutil.rmtree(out)
    snd = os.path.join(out, 'assets', 'zomboidcraft', 'sounds', 'pz')
    table = {}

    def write(s, rel):
        f.seek(data0 + s['off'])
        raw = os.path.join(tmp, 'x.ogg')
        open(raw, 'wb').write(pz_sounds.to_ogg(f.read(s['end'] - s['off']), s['ch'], s['rate'], s['crc']))
        encode(raw, os.path.join(snd, rel + '.ogg'))
        return 'zomboidcraft:pz/' + rel

    made = {}
    for v in VOICES:
        for st in STATES:
            for i, s in enumerate(sorted(voices[v][st], key=lambda x: x['name'])):
                made.setdefault((v, st), []).append(write(s, f'zombie/{v}/{st}/{i + 1}'))
    for v in VOICES:
        sex = v[-2]
        for st in STATES:
            names = made.get((v, st))
            if not names and st == 'death' and not v.startswith('s'):
                names = []    # Zomboid's shamblers die without a sound: the body fall plays
            elif not names:
                # borrow: same sex and pace first, then same sex, then anyone
                pool = [w for w in VOICES if w[-2] == sex and (w[0] == 's') == (v[0] == 's')] + \
                       [w for w in VOICES if w[-2] == sex] + VOICES
                names = next((made[(w, st)] for w in pool if made.get((w, st))), None)
            table[f'pz.zombie.{v}.{st}'] = names or []
    for k, lst in other.items():
        table['pz.zombie.' + k] = [write(s, 'zombie/' + k.replace('.', '/') + '/' + str(i + 1))
                                   for i, s in enumerate(sorted(lst, key=lambda x: x['name']))]
    # horde beds: Zomboid's own voices, layered into a crowd
    beds = {'dormant': ['idle'], 'angry': ['aggro', 'chase']}
    rnd = random.Random(42)
    hb = os.path.join(out, 'assets', 'zomboidcraft', 'sounds', 'pz', 'horde')
    os.makedirs(hb, exist_ok=True)
    horde = {}
    for bed, states in beds.items():
        clips = [os.path.join(snd, 'zombie', p.split('pz/zombie/')[1] + '.ogg')
                 for v in VOICES if not v.startswith('s') for st in states for p in made.get((v, st), [])]
        horde[bed] = []
        for take in range(3):
            pick = rnd.sample(clips, min(len(clips), 16))
            inputs, filt = [], []
            for i, c in enumerate(pick):
                inputs += ['-i', c]
                delay = rnd.randint(0, 9000)
                filt.append(f'[{i}]adelay={delay},volume={rnd.uniform(0.35, 0.8):.2f},aecho=0.6:0.4:{rnd.randint(40, 90)}:0.25[a{i}]')
            mix = ''.join(f'[a{i}]' for i in range(len(pick)))
            filt.append(f'{mix}amix=inputs={len(pick)}:normalize=0,atrim=0:10,afade=t=in:d=0.6,afade=t=out:st=9.2:d=0.8,'
                        f'lowpass=f={3800 if bed == "dormant" else 6000},dynaudnorm[out]')
            dst = os.path.join(hb, f'{bed}_{take + 1}.ogg')
            subprocess.run(['ffmpeg', '-v', 'error', '-y', *inputs, '-filter_complex', ';'.join(filt), '-map', '[out]',
                            '-ac', '1', '-c:a', 'libvorbis', '-q:a', '4', dst], check=True)
            horde[bed].append(f'zomboidcraft:pz/horde/{bed}_{take + 1}')
    sounds = {}
    for k, names in table.items():
        sounds[k] = {'replace': True, 'sounds': [{'name': n, 'stream': False} for n in names]}
    for bed, names in horde.items():
        sounds[f'zombie.horde_{bed}'] = {'replace': True, 'sounds': [{'name': n, 'stream': True} for n in names]}
    with open(os.path.join(out, 'assets', 'zomboidcraft', 'sounds.json'), 'w') as fh:
        json.dump(sounds, fh, indent=1, sort_keys=True)
    with open(os.path.join(out, 'pack.mcmeta'), 'w') as fh:
        json.dump({'pack': {'description': "Project Zomboid's own zombie sounds for ZomboidCraft (local only)",
                            'pack_format': 97, 'min_format': 97, 'max_format': 999}}, fh, indent=1)
    shutil.rmtree(tmp)
    count = sum(len(v['sounds']) for v in sounds.values())
    print(f'{len(sounds)} events, {count} sound entries in {out}')
    for v in VOICES:
        print(v, {st: len(made.get((v, st), [])) for st in STATES})
    print({k: len(v) for k, v in other.items()})


if __name__ == '__main__':
    main()

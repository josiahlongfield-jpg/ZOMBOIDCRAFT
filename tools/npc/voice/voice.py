"""Voices a character's lines.txt with fish.audio, and turns clips you made yourself into ones the game plays.

    python tools/npc/voice/voice.py FOLDER            voice every line that has no clip yet (asks fish.audio)
    python tools/npc/voice/voice.py FOLDER --check    no voicing: lists each moment, its lines and their clips,
                                                      what's missing and any mistakes, and writes voice/index.txt
    python tools/npc/voice/voice.py FOLDER --convert  turns every .mp3/.wav/.flac in FOLDER/voice/ into the .ogg the game
                                                      plays (for clips made on the website or recorded yourself)
    python tools/npc/voice/voice.py FOLDER --redo CLIP [CLIP ...]   voice these clips again (a name from index.txt,
                                                      or a moment's name to redo all its lines)

FOLDER is the character's own folder: config/zomboidcraft/npc/MODEL (the one next to MODEL.zsk), holding lines.txt.
The game reads the same file (NpcMoments.java): keep the two in step.

A clip's name is the moment's name, _, and the first 8 hex digits of the line's SHA-1, as written (tone tags and all),
so changing a line's words or tone gives it a new clip and the old one is just left over.

Settings at the top of lines.txt, above the first [MOMENT]:
    voice: <the voice's ID on fish.audio>   (required to voice)
    model: s1                               (fish.audio's voice model; s1 by default)

The API key is read from the FISH_API_KEY environment variable, or from fish_key.txt in FOLDER or the folder above it.
Never put the key in the repo.

Needs: pip install numpy soundfile
"""
import hashlib
import json
import os
import re
import sys
import urllib.error
import urllib.request

import numpy as np
import soundfile as sf

API = 'https://api.fish.audio/v1/tts'
EVENTS = {'she_kills', 'you_kill', 'you_hurt', 'you_down', 'sees_corpse'}
SITUATIONS = {'night', 'day', 'raining', 'dry', 'inside', 'outside', 'alone', 'near_you', 'far_from_you', 'following',
              'waiting', 'working', 'asleep', 'awake', 'armed', 'unarmed', 'zombies_near', 'no_zombies', 'in_car',
              'near_fire', 'near_corpse', 'hurt_badly', 'together', 'holding_hands'}
NUMBERS = {'hunger', 'thirst', 'tired', 'pain', 'fear', 'adrenaline', 'lonely', 'morale', 'trust', 'affection',
           'attraction', 'brave', 'kind', 'talkative', 'loyal', 'optimist', 'health', 'time', 'mood', 'job'}
# the moments built into the game (NpcLines.Kind), which fire where the game always said them
BUILT_IN = ['follow', 'wait', 'spotted', 'flee', 'reload', 'hurt', 'gun', 'clear', 'hungry', 'thirsty', 'tired', 'pain',
            'scared', 'panic', 'wired', 'lonely', 'missed', 'low', 'good', 'eat', 'work', 'work_done', 'level', 'alarm',
            'sleepy', 'wake', 'nightmare', 'flirt', 'romance', 'jealous', 'saved', 'outfit', 'reply']
COMPARE = re.compile(r'([a-z_]+)\s*(>=|<=|!=|>|<|=)\s*([a-z0-9_. ]+)$')
TONE = re.compile(r"\(\s*[A-Za-z][A-Za-z '-]{0,30}\)\s*")


def clip_name(moment, raw):
    return moment + '_' + hashlib.sha1(raw.encode('utf-8')).hexdigest()[:8]


def parse(path):
    """Settings, moments (name -> {'when': [...], 'lines': [(raw, clip)]}) and problems, read as the game reads them."""
    settings, moments, problems, m, mname = {}, {}, [], None, None
    with open(path, encoding='utf-8-sig') as f:
        for row, raw in enumerate(f, 1):
            t = raw.strip()
            if not t or t.startswith('#') or t.startswith('//'):
                continue
            if t.startswith('[') and t.endswith(']'):
                name = t[1:-1].strip().lower().replace(' ', '_')
                if not re.fullmatch(r'[a-z0-9_]+', name):
                    problems.append(f'line {row}: a moment\'s name can only have letters, numbers and _')
                    m = None
                    continue
                m, mname = moments.setdefault(name, {'when': [], 'lines': []}), name
                continue
            key, _, value = t.partition(':')
            key = key.strip().lower() if _ else ''
            if m is None:
                if _:
                    settings[key] = value.strip()
                else:
                    problems.append(f'line {row}: a line before any [MOMENT] heading')
                continue
            if key == 'when':
                for part in value.lower().split(','):
                    p = part.strip()
                    if not p:
                        continue
                    c = COMPARE.match(p)
                    if c and c.group(1) not in NUMBERS:
                        problems.append(f'line {row}: don\'t know "{c.group(1)}"')
                    elif not c and p not in SITUATIONS and p not in EVENTS and p not in BUILT_IN:
                        problems.append(f'line {row}: don\'t know "{p}"')
                    m['when'].append(p)
            elif key in ('wait', 'chance'):
                if not re.sub(r'[^0-9]', '', value):
                    problems.append(f'line {row}: {key} needs a number')
            else:
                m['lines'].append((t, clip_name(mname, t)))
    for name, m in moments.items():
        if name not in BUILT_IN and not m['when']:
            problems.append(f'[{name.upper()}] isn\'t a built-in moment and has no when:, so it never fires')
        if not m['lines']:
            problems.append(f'[{name.upper()}] has no lines yet')
    return settings, moments, problems


def api_key(folder):
    if os.environ.get('FISH_API_KEY'):
        return os.environ['FISH_API_KEY'].strip()
    for d in (folder, os.path.dirname(os.path.abspath(folder))):
        p = os.path.join(d, 'fish_key.txt')
        if os.path.exists(p):
            return open(p, encoding='utf-8-sig').read().strip()
    return None


def tidy(data, rate):
    """Mono, quiet ends trimmed (a little left on), peak at -1 dB: a clip that starts when her mouth does."""
    if data.ndim > 1:
        data = data.mean(1)
    data = data.astype(np.float32)
    loud = np.flatnonzero(np.abs(data) > 0.02)
    if len(loud):
        pad = int(rate * 0.04)
        data = data[max(0, loud[0] - pad):loud[-1] + pad]
    peak = float(np.abs(data).max()) if len(data) else 0
    if peak > 0:
        data = data * (0.89 / peak)
    return data


def write_ogg(data, rate, out):
    if rate not in (22050, 32000, 44100, 48000):
        # Vorbis wants a usual rate: the nearest by plain resampling is fine for speech
        target = 44100
        x = np.linspace(0, len(data) - 1, int(len(data) * target / rate))
        data, rate = np.interp(x, np.arange(len(data)), data).astype(np.float32), target
    sf.write(out, data, rate, format='OGG', subtype='VORBIS')


def voice(text, key, voice_id, model):
    body = json.dumps({'text': text, 'reference_id': voice_id, 'format': 'wav', 'normalize': True,
                       'latency': 'normal'}).encode('utf-8')
    req = urllib.request.Request(API, data=body, method='POST', headers={
        'Authorization': 'Bearer ' + key, 'Content-Type': 'application/json', 'model': model})
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            return r.read()
    except urllib.error.HTTPError as e:
        msg = e.read().decode('utf-8', 'replace')[:300]
        hint = {401: 'the API key is wrong', 402: 'your fish.audio account is out of credit',
                404: 'no voice with that ID (check voice: in lines.txt)', 429: 'too many at once: wait a minute and run it again'}
        raise SystemExit(f'fish.audio said {e.code}: {hint.get(e.code, msg)}')


def main():
    args = sys.argv[1:]
    if not args or args[0] in ('-h', '--help'):
        print(__doc__)
        return
    folder = args[0]
    lines_txt = os.path.join(folder, 'lines.txt')
    voice_dir = os.path.join(folder, 'voice')
    if not os.path.isdir(folder):
        raise SystemExit(f'No folder {folder}. Give the character\'s folder, e.g. ...\\config\\zomboidcraft\\npc\\woman')
    os.makedirs(voice_dir, exist_ok=True)

    if '--convert' in args:
        done = 0
        for f in sorted(os.listdir(voice_dir)):
            base, ext = os.path.splitext(f)
            if ext.lower() not in ('.mp3', '.wav', '.flac'):
                continue
            try:
                data, rate = sf.read(os.path.join(voice_dir, f))
            except Exception as e:
                print(f'  {f}: can\'t read it ({e}). Save it as .wav and try again.')
                continue
            write_ogg(tidy(data, rate), rate, os.path.join(voice_dir, base + '.ogg'))
            os.makedirs(os.path.join(voice_dir, 'originals'), exist_ok=True)
            os.replace(os.path.join(voice_dir, f), os.path.join(voice_dir, 'originals', f))
            print(f'  {f} -> {base}.ogg')
            done += 1
        print(f'Converted {done} clip(s). The originals are in voice\\originals.')
        if not os.path.exists(lines_txt):
            return
        args.append('--check')

    if not os.path.exists(lines_txt):
        raise SystemExit(f'No lines.txt in {folder}. Make one first (an empty text file named lines.txt).')
    settings, moments, problems = parse(lines_txt)
    have = {os.path.splitext(f)[0] for f in os.listdir(voice_dir) if f.endswith('.ogg')}

    index = []
    missing = []
    for name, m in moments.items():
        kind = 'built-in' if name in BUILT_IN else 'your own'
        index.append(f'[{name.upper()}]  ({kind}' + (', when: ' + ', '.join(m['when']) if m['when'] else '') + ')')
        for raw, clip in m['lines']:
            mark = 'ok     ' if clip in have else 'MISSING'
            index.append(f'  {mark} {clip}.ogg   {raw}')
            if clip not in have:
                missing.append((raw, clip))
        index.append('')
    with open(os.path.join(voice_dir, 'index.txt'), 'w', encoding='utf-8') as f:
        f.write('Every line in lines.txt and the clip file it plays (made by voice.py).\n'
                'To use your own recording for a line, save it in this folder under the clip\'s name '
                '(.mp3 or .wav is fine) and run voice.py with --convert.\n\n' + '\n'.join(index))

    if '--check' in args:
        print('\n'.join(index))
        print(f'{len(missing)} line(s) without a clip.')
        for p in problems:
            print('  PROBLEM: ' + p)
        if not problems:
            print('No problems found.')
        return

    for p in problems:
        print('  note: ' + p)
    redo = args[args.index('--redo') + 1:] if '--redo' in args else []
    todo = missing
    if redo:
        todo = [(raw, clip) for name, m in moments.items() for raw, clip in m['lines']
                if clip in redo or name in [r.lower() for r in redo]]
        if not todo:
            raise SystemExit('Nothing matches ' + ' '.join(redo) + ' (use a clip name from voice\\index.txt or a moment name)')
    if not todo:
        print('Every line already has a clip. Nothing to do.')
        return
    key = api_key(folder)
    if not key:
        raise SystemExit('No API key. Put it in fish_key.txt in the npc folder (or set FISH_API_KEY).')
    voice_id = settings.get('voice')
    if not voice_id:
        raise SystemExit('No voice. Add a line "voice: <the voice\'s ID>" at the top of lines.txt.')
    model = settings.get('model', 's1')
    print(f'Voicing {len(todo)} line(s) with voice {voice_id} ({model})...')
    for i, (raw, clip) in enumerate(todo, 1):
        wav = os.path.join(voice_dir, clip + '.wav')
        with open(wav, 'wb') as f:
            f.write(voice(raw, key, voice_id, model))
        data, rate = sf.read(wav)
        write_ogg(tidy(data, rate), rate, os.path.join(voice_dir, clip + '.ogg'))
        os.remove(wav)
        print(f'  {i}/{len(todo)} {clip}.ogg  {raw}')
    print('Done. In game the new lines play the next time each moment comes up (no restart needed).')


if __name__ == '__main__':
    main()

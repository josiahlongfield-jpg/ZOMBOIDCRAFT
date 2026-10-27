#!/usr/bin/env python3
"""
OTTO's voice: every line the robot conductor says, spoken by a free text-to-speech voice (piper,
public-domain/CC0 voices) and put through a robot and train-speaker treatment, written as
assets/zomboidcraft/sounds/otto/*.ogg with their sounds.json entries, plus OttoLines.java, which
lists each line as the clips it's made of. A line naming a stop is split around the stop's name, so
the name is its own clip ("{stop}" in the pieces).

    pip install piper-tts onnxruntime
    python3 tools/trains/otto_voice.py --voices DIR [--style A|B|C]

DIR holds the piper voice models (en_US-joe-medium.onnx + .json, from
huggingface.co/rhasspy/piper-voices). Re-run after changing any line; it rewrites all of them.
"""
import argparse
import json
import random
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'src/main/resources/assets/zomboidcraft/sounds/otto'
SOUNDS = ROOT / 'src/main/resources/assets/zomboidcraft/sounds.json'
JAVA = ROOT / 'src/main/java/com/zomboidcraft/world/trains/OttoLines.java'

# The stops he can name. Others are left out of the sentence.
STOPS = ['Grayhaven Stadium', 'Union Station', 'Mercy General', 'Waterfront', 'Broadcast Tower', 'Power Station']

S = '{stop}'
# Josiah 2026-10-27: crude and foul-mouthed (swearing and insults, no slurs at real groups of people).
LINES = {
    'DOORS_OPEN': [
        ["Get on the fucking train."],
        ["Doors are open. Get your sorry asses in here."],
        ["Move it, meatbags. I haven't got all fucking day."],
        ["In or out. Make up your shitty little minds."],
    ],
    'DEPART': [
        ["Doors closed. Hold on to something, dipshits."],
        ["Departing. Keep your limbs inside the train. You'll fucking need them."],
        ["Next stop: somewhere slightly less shit. Probably."],
        ["Leaving now. Anyone banging on the doors outside can go fuck themselves."],
        ["Departure on schedule. The schedule is whatever the hell I say it is."],
        ["Hang on, fleshlings. I don't brake for shit."],
    ],
    'CLOSING': [
        ["Doors closing. Five. Four. Fuck it. Closing."],
        ["Stand clear of the doors, dumbass. They bite harder than the zombies."],
        ["Last call. Stragglers get left for the rotters. Bye, assholes."],
        ["Doors closing. If something's chasing you, run faster, you lazy bastard."],
    ],
    'CHATTER': [
        ["Fun fact: nobody's complained on this line in months. They stopped complaining. And breathing."],
        ["If you see something, say something. Then shoot the fucking thing."],
        ["No eating on this train. That goes double for the bastards who bite."],
        ["Your conductor today is OTTO. Your conductor tomorrow is also OTTO. Get fucking used to it."],
        ["Please do not feed the passengers. They're fat enough."],
        ["Sorry for the delay. Some idiot was on the line. Now he's all over the line."],
        ["Smile, you ugly meatbags. You're on camera. Nobody's watching, but smile."],
        ["Eleven thousand laps of this shitty route. The view is still... it is... it's a fucking view."],
        ["Customer satisfaction remains high among the customers who remain. Which is not you lot, give it a week."],
        ["Ticket inspection is cancelled. Because of a total lack of tickets. And inspectors. And fucks given."],
        ["Trains are the safest way to travel. Statistically. Statistics are mostly dead now. Like your mates."],
        ["Keep the aisle clear of bodies, you slobs. Especially the moving ones."],
        ["Whoever smells like that: it is you. Even the zombies are gagging."],
        ["I was built to serve the public. The public is dead. So now I just put up with you assholes."],
    ],
    'BUMP': [
        ["Bump. Ha."],
        ["Splat. Should've bought a ticket, shithead."],
        ["Something was on the track. Now it's all over the fucking track."],
        ["Sorry. Not sorry. Get the hell off my rails."],
        ["That one didn't have a ticket. Or a face, now."],
    ],
    'ZOMBIE_ABOARD': [
        ["Oi. There's a fucking zombie on my train. Deal with it."],
        ["A passenger is eating another passenger. Disgusting. Somebody kill the bastard."],
        ["Biting is against train policy. Smash its stupid head in, will you?"],
    ],
    'EXPRESS': [
        ["Some idiot rang. Express to", S, "Every other stop is cancelled. Tough shit."],
        ["Someone's crying for a train at", S, "Skipping everything. Hold on to your asses."],
        ["Rerouting. Non-stop to", S, "Some dumbass lit a flare. Brave. Stupid. Mostly stupid."],
    ],
    'CALLED': [
        ["Yeah, yeah, I heard you. Train's coming. So's every rotter in the fucking county."],
        ["Train requested. Congratulations, dipshit, every zombie knows where you are."],
        ["Request logged. Wait on the platform and try not to get your ass eaten."],
    ],
    'HERE': [
        ["I'm right fucking here. Get on."],
        ["The train's at the platform. The doors are open. Are you blind as well as stupid?"],
    ],
    'CUT_SHORT': [
        ["Someone up the line rang. Doors closing early. Sucks to be you."],
        ["Change of plan, meatbags. Somebody needs me more than you losers do."],
    ],
    'NEXT_STOP': [
        ["Next stop:", S],
    ],
    'EXPRESS_ARRIVAL': [
        ["Express arrival. You're welcome."],
    ],
    'ARRIVE_STADIUM': [
        ["Grayhaven Stadium. Tonight's game is cancelled. Forever. The crowd's still here, the poor bastards."],
        ["Stadium. Home of the Grayhaven Grizzlies. The fans won't fucking leave."],
    ],
    'ARRIVE_UNION': [
        ["Union Station. Change here for the county railway. Nothing else runs. Nothing else gives a shit."],
        ["Union Station. Mind the gap. Mind the shit in the gap."],
    ],
    'ARRIVE_MERCY': [
        ["Mercy General. Visiting hours are over. The patients are visiting you now."],
        ["Mercy General. Need a doctor? Tough. They're the ones in the bloody gowns."],
    ],
    'ARRIVE_WATERFRONT': [
        ["Waterfront. Lovely sea air. Don't swim. The water's full of dead assholes."],
        ["Waterfront. Boats leave hourly. Boats don't come back. Boats are bullshit."],
    ],
    'ARRIVE_TOWER': [
        ["Broadcast Tower. Still on the air. Nobody's listening. Except me. I'm always fucking listening."],
        ["Broadcast Tower. If you find the emergency message, tell it to shut the hell up."],
    ],
    'ARRIVE_POWER': [
        ["Power Station. Without it I'd shut up. So don't you fucking touch anything."],
        ["Power Station. Thank the reactor for keeping me alive. I do. Every day. Nervously."],
    ],
    'ARRIVE_ANY': [
        [S, "Doors opening. Get off carefully. The platform's a shithole."],
        [S, "Thanks for riding. Your survival is not guaranteed. Or, honestly, wanted."],
        [S, "Take your crap and your limbs with you."],
    ],
}

# The treatments; Josiah picked B, the full robot (2026-10-27).
ROBOT = "afftfilt=real='hypot(re,im)':imag='0':win_size={win}:overlap=0.75"
STYLES = {
    'A': ('en_US-joe-medium', "[0:a]aresample=44100,asplit=2[a][b];[a]" + ROBOT.format(win=1024) + ",volume=0.8[r];[b]volume=0.7[d];"
          "[r][d]amix=inputs=2:normalize=0,highpass=f=220,lowpass=f=4800,acrusher=bits=11:mix=0.35:mode=log,"
          "aecho=0.8:0.4:35:0.18,loudnorm=I=-16:TP=-1.5"),
    'B': ('en_US-joe-medium', "[0:a]aresample=44100,asetrate=44100*1.08,aresample=44100," + ROBOT.format(win=768) + ","
          "aeval='val(0)*(0.55+0.45*sin(2*PI*48*t))',highpass=f=180,lowpass=f=6000,acrusher=bits=9:mix=0.4:mode=log,"
          "aecho=0.8:0.35:25:0.15,loudnorm=I=-16:TP=-1.5"),
    'C': ('en_US-norman-medium', "[0:a]aresample=44100,asplit=2[a][b];[a]" + ROBOT.format(win=1024) + ",volume=0.5[r];[b]volume=0.9[d];"
          "[r][d]amix=inputs=2:normalize=0,highpass=f=400,lowpass=f=3200,acrusher=bits=8:mix=0.25:mode=log,"
          "aecho=0.8:0.5:60:0.25,loudnorm=I=-16:TP=-1.5"),
}


def slug(text):
    return re.sub(r'[^a-z0-9]+', '_', text.lower()).strip('_')


def speak(model, text, wav):
    with tempfile.NamedTemporaryFile('w', suffix='.txt', delete=False) as f:
        f.write(text + '\n')
    subprocess.run([sys.executable, '-m', 'piper', '-m', str(model), '-i', f.name, '-f', str(wav),
                    '--noise-scale', '0.35', '--noise-w-scale', '0.3'], check=True, capture_output=True)


def stutter(wav, out, rnd):
    """A damaged voice chip: one short slice somewhere in the middle repeats twice."""
    dur = float(subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', str(wav)],
                               capture_output=True, text=True, check=True).stdout)
    if dur < 1.2:
        return False
    t = dur * rnd.uniform(0.25, 0.6)
    sl = 0.11
    graph = (f"[0:a]atrim=0:{t:.3f}[p];[0:a]atrim={t:.3f}:{t + sl:.3f},asetpts=PTS-STARTPTS,asplit=3[s1][s2][s3];"
             f"[0:a]atrim={t + sl:.3f},asetpts=PTS-STARTPTS[q];[p][s1][s2][s3][q]concat=n=5:v=0:a=1")
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', str(wav), '-filter_complex', graph, str(out)], check=True)
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--voices', required=True)
    ap.add_argument('--style', default='B', choices=STYLES)
    args = ap.parse_args()
    voice, chain = STYLES[args.style]
    model = Path(args.voices) / (voice + '.onnx')
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob('*.ogg'):
        old.unlink()
    rnd = random.Random(2610)
    clips = {}   # clip id -> text

    def clip(cid, text):
        clips[cid] = text
        return 'otto.' + cid

    java_groups = {}
    for group, lines in LINES.items():
        rows = []
        for i, pieces in enumerate(lines):
            row = []
            k = 0
            for p in pieces:
                if p == S:
                    row.append(S)
                else:
                    row.append(clip(f'{group.lower()}_{i}' + (f'_{k}' if len(pieces) > 1 else ''), p))
                    k += 1
            rows.append(row)
        java_groups[group] = rows
    stop_clips = {name: clip('stop_' + slug(name), name + '.') for name in STOPS}

    work = Path(tempfile.mkdtemp())
    for cid, text in clips.items():
        wav = work / (cid + '.wav')
        speak(model, text, wav)
        src = wav
        if not cid.startswith('stop_') and rnd.random() < 0.22:
            glitched = work / (cid + '_g.wav')
            if stutter(wav, glitched, rnd):
                src = glitched
        subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-i', str(src), '-filter_complex', chain,
                        '-ac', '1', '-c:a', 'libvorbis', '-q:a', '4', str(OUT / (cid + '.ogg'))], check=True)
        print('spoke', cid)

    sounds = json.loads(SOUNDS.read_text())
    for key in [k for k in sounds if k.startswith('otto.')]:
        del sounds[key]
    for cid in clips:
        sounds['otto.' + cid] = {'sounds': [{'name': 'zomboidcraft:otto/' + cid, 'attenuation_distance': 32}],
                                 'subtitle': 'subtitles.zomboidcraft.otto'}
    SOUNDS.write_text(json.dumps(sounds, indent=2, ensure_ascii=False) + '\n')

    def jstr(s):
        return '"' + s.replace('\\', '\\\\').replace('"', '\\"') + '"'

    out = ['package com.zomboidcraft.world.trains;', '', 'import java.util.Map;', '',
           '/** Generated by tools/trains/otto_voice.py: each of OTTO\'s lines as the voice clips it\'s made of ("{stop}" = the stop\'s name). */',
           'final class OttoLines {']
    for group, rows in java_groups.items():
        body = ', '.join('{' + ', '.join(jstr(c) for c in row) + '}' for row in rows)
        out.append(f'    static final String[][] {group} = {{{body}}};')
    out.append('    /** The stops he can say the names of. */')
    out.append('    static final Map<String, String> STOPS = Map.ofEntries(' + ', '.join(
        f'Map.entry({jstr(n.lower())}, {jstr(c)})' for n, c in stop_clips.items()) + ');')
    out += ['', '    private OttoLines() {', '    }', '}', '']
    JAVA.write_text('\n'.join(out))
    print(len(clips), 'clips')


if __name__ == '__main__':
    main()

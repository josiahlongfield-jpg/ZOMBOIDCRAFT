"""Builds the local "ZomboidCraft PZ Car Sounds" resource pack from Project Zomboid's own sound bank.

    pzsounds.py ZomboidSound.bank OUT_PACK_DIR

Zomboid keeps its sounds in an FMOD bank (an FSB5 inside a RIFF), each sample Vorbis-coded with the
three Ogg header packets left out: the identification and setup headers come from the encoder, the
setup one named only by its CRC32. libvorbis rebuilds them: the same encoder settings give the same
setup packet, so the script encodes a moment of noise at each quality through ffmpeg's libvorbis
until the CRC matches, then wraps every sample's raw packets in a fresh Ogg stream.

Only the car sounds come out, into assets/zomboidcraft/sounds/pz/ with a sounds.json naming them
pz.* events that CarSounds/PzEngineSound play per model (models.json "sounds"). The pack is for
Josiah's own game (personal use); it never goes in the repo or the jar.
"""
import json
import mmap
import os
import re
import struct
import subprocess
import sys
import zlib

FREQ = {1: 8000, 2: 11000, 3: 11025, 4: 16000, 5: 22050, 6: 24000, 7: 32000, 8: 44100, 9: 48000}


def samples(m):
    """Every sample in the bank's FSB5: name, rate, channels, data span, length and setup CRC."""
    pos = m.find(b'FSB5')
    ver, num, shdr, names, data, mode = struct.unpack_from('<IIIIII', m, pos + 4)
    if mode != 15:
        raise SystemExit(f'not a Vorbis bank (mode {mode})')
    p = pos + (0x3c if ver == 1 else 0x40)
    names_off = p + shdr
    base = names_off + names
    offs = struct.unpack_from(f'<{num}I', m, names_off)
    out = []
    for i in range(num):
        raw, = struct.unpack_from('<Q', m, p)
        p += 8
        more, ch, rate, crc = raw & 1, ((raw >> 5) & 1) + 1, FREQ.get((raw >> 1) & 0xf), None
        while more:
            c, = struct.unpack_from('<I', m, p)
            p += 4
            more, size, kind = c & 1, (c >> 1) & 0xffffff, c >> 25
            if kind == 1:
                ch = m[p]
            elif kind == 2:
                rate, = struct.unpack_from('<I', m, p)
            elif kind == 11:
                crc, = struct.unpack_from('<I', m, p)
            p += size
        o = names_off + offs[i]
        out.append(dict(name=m[o:m.find(b'\0', o)].decode('latin1'), ch=ch, rate=rate, crc=crc,
                        off=base + ((raw >> 6) & 0x0fffffff) * 16, length=raw >> 34))
    for a, b in zip(out, out[1:]):
        a['end'] = b['off']
    out[-1]['end'] = base + data
    return out


def ogg_packets(b):
    cur, i = b'', 0
    while i < len(b):
        n = b[i + 26]
        lace = b[i + 27:i + 27 + n]
        k = i + 27 + n
        for l in lace:
            cur += b[k:k + l]
            k += l
            if l < 255:
                yield cur
                cur = b''
        i = i + 27 + n + sum(lace)


def headers(ch, rate, crc):
    """The identification and setup packets libvorbis writes for a stream whose setup CRC is crc."""
    for q10 in range(-10, 101):
        r = subprocess.run(['ffmpeg', '-v', 'error', '-f', 'lavfi', '-i', f'anoisesrc=r={rate}:d=0.3', '-ac', str(ch),
                            '-c:a', 'libvorbis', '-q:a', str(q10 / 10), '-f', 'ogg', '-'], capture_output=True)
        if not r.stdout:
            continue
        pk = list(ogg_packets(r.stdout))
        if zlib.crc32(pk[2]) & 0xffffffff == crc:
            return pk[0], pk[2]
    raise SystemExit(f'no libvorbis setup matches {ch} ch {rate} Hz crc {crc}')


CRC_TABLE = []
for _i in range(256):
    _r = _i << 24
    for _ in range(8):
        _r = ((_r << 1) ^ 0x04c11db7) & 0xffffffff if _r & 0x80000000 else (_r << 1) & 0xffffffff
    CRC_TABLE.append(_r)


def page(packet, seq, gran, flags):
    lace, n = [], len(packet)
    while n >= 255:
        lace.append(255)
        n -= 255
    lace.append(n)
    p = bytearray(b'OggS' + bytes([0, flags]) + struct.pack('<qIII', gran, 1, seq, 0) + bytes([len(lace)]) + bytes(lace) + packet)
    c = 0
    for b in p:
        c = ((c << 8) & 0xffffffff) ^ CRC_TABLE[((c >> 24) ^ b) & 0xff]
    struct.pack_into('<I', p, 22, c)
    return bytes(p)


COMMENT = b'\x03vorbis' + struct.pack('<I', 3) + b'fsb' + struct.pack('<I', 0) + b'\x01'


def to_ogg(m, s, hdr):
    ident, setup = hdr
    ident = bytearray(ident)
    ident[11] = s['ch']
    struct.pack_into('<I', ident, 12, s['rate'])
    out = [page(bytes(ident), 0, 0, 2), page(COMMENT, 1, 0, 0), page(setup, 2, 0, 0)]
    p, pk = s['off'], []
    while p + 2 <= s['end']:
        n, = struct.unpack_from('<H', m, p)
        if n == 0:
            break
        pk.append(m[p + 2:p + 2 + n])
        p += 2 + n
    for k, x in enumerate(pk):
        last = k == len(pk) - 1
        out.append(page(x, 3 + k, s['length'] if last else -1, 4 if last else 0))
    return b''.join(out)


# Zomboid's four recorded engines: the name each sample starts with, its rev bands (the rpm each was
# recorded at, and the words in the file name), how its on-load (throttle open) and off-load
# (coasting) loops are named, and its start, failed start and switch-off.
ENGINES = {
    'sports': dict(stem='VEHCar_Chevalier_Cossette_', bands=[(r, str(r)) for r in range(1000, 7000, 1000)],
                   on='{b}_On_Load_{L}', off='{b}_Off_Load_{L}', start='Start', fail='ignition_Fail', stop='Stop'),
    'wagon': dict(stem='VEHCar_Chevalier_CeriseWagon_', bands=[(r, str(r)) for r in range(1000, 5000, 1000)],
                  on='{b}_On_{L}', off='{b}_Off_{L}', start='Ignition_Start', fail='Ignition_Fail', stop='Stop'),
    'offroad': dict(stem='VEHCar_Dash_Rancher_', bands=list(zip(range(1000, 5800, 600), [
        'Very_Slow', 'Slow', 'Medium_Slow', 'Medium', 'Medium_Fast', 'Fast', 'Very_Fast', 'Fastest'])),
                    on='{b}_On_Load_{L}', off='{b}_Off_Load_{L}', start='ignition_start', fail='ignition_fail', stop='Stop'),
    'van': dict(stem='VEHCar_Step_Van_', bands=list(zip(range(1000, 4750, 750), ['Very Slow', 'Slow', 'Medium', 'Fast', 'Very Fast'])),
                on='On Load {b}_{L}', off='Off Load {b}_{L}', start='Ignition_Start', fail='Ignition_Fail', stop='Stop'),
}
DOORS = {'sports': 'Sports', 'standard': 'Standard', 'jeep': 'Jeep', 'van': 'Van'}
# everything else a car makes in Zomboid: event -> sample names (every take of each is a variant)
OTHER = {
    'horn.sports': ['VEHHorn_Sports_Car_Loop'], 'horn.sports.end': ['VEHHorn_Sports_Car_Loop_End'],
    'horn.standard': ['VEHHorn_Standard_Car_Loop'], 'horn.standard.end': ['VEHHorn_Standard_Car_Loop_End'],
    'glovebox.open': ['object_container_vehicle_glovebox_open'], 'glovebox.close': ['object_container_vehicle_glovebox_close'],
    'glovebox.item': ['object_container_vehicle_glovebox_transferitem'], 'trunk.item': ['object_container_vehicle_trunk_transferitem'],
    'seat.item': ['object_container_vehicle_carseat_transferitem'], 'seat.change': ['player_foley_vehicle_change_seat'],
    'skid.light': ['skid_brake_light_road'], 'skid.medium': ['skid_brake_medium_road'], 'skid.heavy': ['skid_brake_heavy_road'],
    'skid.dirt.light': ['skid_brake_light_dirt'], 'skid.dirt.medium': ['skid_brake_medium_dirt'], 'skid.dirt.heavy': ['skid_brake_heavy_dirt'],
    'skid.turn.light': ['skid_turn_light_road'], 'skid.turn.medium': ['skid_turn_medium_road_v2_preview'],
    'skid.turn.heavy': ['skid_turn_heavy_road_v2_preview'],
    'brake.pedal': ['vehicle_brake_pedal'], 'brake.squeal': ['vehicle_brake_squeel_loop'],
    'handbrake': ['vehicle_brake_hand_pull_chunky'], 'handbrake.flimsy': ['vehicle_brake_hand_pull_flimsy'],
    'crash.light': ['vehicle_impact_crash_light_front', 'vehicle_impact_crash_light_rear', 'vehicle_impact_crash_light_side'],
    'crash.heavy': ['vehicle_impact_crash_heavy_front', 'vehicle_impact_crash_heavy_rear'],
    'crash.tree': ['vehicle_impact_crash_tree_heavy'], 'crash.hedge': ['vehicle_impact_crash_hedge'],
    'hit.zombie': ['vehicle_impact_hit_zombie_front_heavy', 'vehicle_impact_hit_zombie_side_heavy', 'vehicle_impact_hit_zombie_rear_heavy'],
    'zombie.bash': ['vehicle_impact_zombie_bash_front', 'vehicle_impact_zombie_bash_side', 'vehicle_impact_zombie_bash_rear'],
    'zombie.bash.window': ['vehicle_impact_zombie_bash_window'], 'run_over': ['vehicle_impact_run_over_body_loop'],
    'hit.weapon': ['vehicle_impact_hit_with_weapon_front', 'vehicle_impact_hit_with_weapon_side', 'vehicle_impact_hit_with_weapon_rear'],
    'hit.weapon.window': ['vehicle_impact_hit_with_weapon_window'], 'scrape.plant': ['vehicle_impact_scrape_plant_loop'],
    'gear': ['vehicle_car_gear_shift'], 'reverse_beep': ['vehicle_reverse_beep'],
    'headlights.on': ['vehicle_car_headlights_on'], 'headlights.off': ['vehicle_car_headlights_off'],
    'window.smash': ['vehicle_car_window_smash'], 'window.handle.open': ['vehicle_car_window_handle_open'],
    'window.handle.close': ['vehicle_car_window_handle_close'], 'window.electric.open': ['vehicle_car_windows_electric_open'],
    'window.electric.close': ['vehicle_car_windows_electric_close'],
    'door.locked': ['vehicle_door_locked'], 'lock.console': ['vehicle_car_door_lock_console'],
    'unlock.console': ['vehicle_car_door_unlock_console'], 'key.lock': ['VEHCar_Van_Key_Lock'], 'key.unlock': ['VEHCar_Van_Key_Unlock'],
    'alarm': ['vehicle_car_alarm'], 'alarm.door': ['vehicle_car_door_alarm'],
    'siren.fast': ['siren_synth_ambulance_fast_loop'], 'siren.slow': ['siren_synth_ambulance_slow_loop'],
    'hotwire.start': ['vehicle_hotwire_start'], 'hotwire.loop': ['vehicle_hotwire_loop'],
    'hotwire.success': ['vehicle_hotwire_success'], 'hotwire.fail': ['vehicle_hotwire_fail'],
    'tyre.explode': ['vehicle_damage_tyre_explode'], 'tyre.explode.tail': ['vehicle_damage_tyre_explode_tail'],
    'damage.ticking': ['vehicle_damage_engine_ticking_loop'], 'damage.rattle': ['vehicle_damage_external_rattling_loop'],
    'damage.rattle.slow': ['vehicle_damage_internal_rattling_loop_slow'],
    'damage.rattle.medium': ['vehicle_damage_internal_rattling_loop_medium'],
    'damage.rattle.fast': ['vehicle_damage_internal_rattling_loop_fast'],
    'damage.grinding': ['vehicle_damage_missing_wheel_grinding_loop'], 'damage.creak': ['vehicle_damage_turn_creaks_loop'],
    'fuel.pump.start': ['vehicle_fuel_car_from_gaspump_start'], 'fuel.pump.loop': ['vehicle_fuel_car_from_gaspump_loop'],
    'fuel.pump.stop': ['vehicle_fuel_car_from_gaspump_stop'], 'fuel.can.start': ['vehicle_fuel_car_from_canister_start'],
    'fuel.can.loop': ['vehicle_fuel_car_from_canister_loop'], 'fuel.can.stop': ['vehicle_fuel_car_from_canister_stop'],
    'heater.button': ['vehicle_car_AC_Heater_Button'], 'heater.temp': ['vehicle_car_ac_heater_set_temperature'],
    'menu.open': ['ui_vehicle_menu_open'], 'menu.close': ['ui_vehicle_menu_close'],
}


def plan():
    """event -> list of exact sample names (a trailing digit group in the bank marks takes of one sound)."""
    ev = {}
    for key, e in ENGINES.items():
        st = e['stem']
        ev[f'engine.{key}.idle.engine'] = [st + 'Idle_Engine']
        ev[f'engine.{key}.idle.exhaust'] = [st + 'Idle_Exhaust']
        for i, (_, b) in enumerate(e['bands'], 1):
            for load in ('on', 'off'):
                for layer in ('Engine', 'Exhaust'):
                    ev[f'engine.{key}.{i}.{load}.{layer.lower()}'] = [st + e[load].format(b=b, L=layer),
                                                                       st + e['on'].format(b=b, L=layer)]
        for k in ('start', 'fail', 'stop'):
            ev[f'engine.{key}.{k}'] = [st + e[k]]
    for key, d in DOORS.items():
        for part, word in (('door', 'Door'), ('hood', 'Hood'), ('trunk', 'Trunk')):
            for act in ('Open', 'Close'):
                ev[f'{part}.{key}.{act.lower()}'] = [f'VEHDoor_{d}_Car_{word}_{act}']
        ev[f'lock.{key}'] = [f'VEHDoor_{d}_Car_Lock', 'VEHDoor_Standard_Car_Lock']
        ev[f'unlock.{key}'] = [f'VEHDoor_{d}_Car_Unlock', 'VEHDoor_Standard_Car_Unlock']
    for k, v in OTHER.items():
        ev[k] = v
    return ev


def peak(path):
    r = subprocess.run(['ffmpeg', '-hide_banner', '-i', path, '-af', 'volumedetect', '-f', 'null', '-'], capture_output=True, text=True)
    m = re.search(r'max_volume: (-?[\d.]+) dB', r.stderr)
    return float(m.group(1)) if m else 0.0


def louden(out, sounds, top=-1.0):
    """Zomboid mixes its car recordings low and lifts them in FMOD; Minecraft can't play a sound above full volume,
    so the takes are raised here: each one-shot event until its loudest take peaks at top dB, each engine's whole
    set of loops by one shared amount (so its rev bands still match as they crossfade)."""
    d = f'{out}/assets/zomboidcraft/sounds/pz/'
    groups = {}
    for ev, v in sounds.items():
        g = '.'.join(ev.split('.')[:3]) if ev.startswith('pz.engine.') and not ev.endswith(('.start', '.fail', '.stop')) else ev
        if '.idle.' in ev:
            g += '.idle'    # ticking over has its own lift, or it would be a murmur under the flat-out takes
        groups.setdefault(g, set()).update(x['name'].split('/')[-1] for x in v['sounds'])
    done = set()
    for names in groups.values():
        names = sorted(names - done)
        if not names:
            continue
        gain = top - max(peak(d + n + '.ogg') for n in names)
        if gain > 0.5:
            for n in names:
                subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', d + n + '.ogg', '-af', f'volume={gain:.1f}dB',
                                '-c:a', 'libvorbis', '-q:a', '6', d + n + '.tmp.ogg'], check=True)
                os.replace(d + n + '.tmp.ogg', d + n + '.ogg')
        done.update(names)


def main():
    bank, out = sys.argv[1:3]
    f = open(bank, 'rb')
    m = mmap.mmap(f.fileno(), 0, access=mmap.ACCESS_READ)
    every = samples(m)
    by = {}
    for s in every:
        by.setdefault(s['name'], []).append(s)
        base = re.sub(r'_\d+$', '', s['name'])
        if base != s['name']:
            by.setdefault(base, []).append(s)
    hdr, sounds, missing = {}, {}, []
    os.makedirs(f'{out}/assets/zomboidcraft/sounds/pz', exist_ok=True)
    for event, wanted in plan().items():
        takes = next((by[w] for w in wanted if w in by), None)
        if takes is None:
            missing.append(event)
            continue
        names = []
        for s in sorted(takes, key=lambda s: s['name']):
            fn = re.sub(r'[^a-z0-9_]', '_', s['name'].lower())
            if fn in names:
                fn += f'_{len(names)}'
            key = (s['ch'], s['rate'], s['crc'])
            if key not in hdr:
                hdr[key] = headers(*key)
            open(f'{out}/assets/zomboidcraft/sounds/pz/{fn}.ogg', 'wb').write(to_ogg(m, s, hdr[key]))
            names.append(fn)
        sounds['pz.' + event] = {'sounds': [{'name': f'zomboidcraft:pz/{n}'} for n in names]}
    louden(out, sounds)
    json.dump(sounds, open(f'{out}/assets/zomboidcraft/sounds.json', 'w'), indent=1)
    json.dump({'pack': {'description': "Project Zomboid's own car sounds for ZomboidCraft (local, personal use)",
                        'pack_format': 15, 'supported_formats': [15, 999], 'min_format': 15, 'max_format': 999}},
              open(f'{out}/pack.mcmeta', 'w'), indent=1)
    print(len(sounds), 'events,', sum(len(v['sounds']) for v in sounds.values()), 'files')
    if missing:
        print('not in the bank:', *missing)


if __name__ == '__main__':
    main()

"""Builds the car files the mod ships from the source car pack.

The source models (CGTrader "American Cars 80s Pack", Royalty Free licence) must never be
committed: the licence forbids redistributing them, and the repo is public. Keep them in the
project's files (zomboid-mod/cars/source/) and run

    python3 tools/cars/pack.py SOURCE_DIR

which weathers every model (tools/cars/wreck.py) and writes, per model,
    src/main/resources/assets/zomboidcraft/cars/<id>.zcar   meshes + textures, packed and scrambled
and one collision/placement index for the server,
    src/main/resources/data/zomboidcraft/zc_cars/models.json

A .zcar is zlib-deflated, then XORed with an xorshift32 stream seeded from the model id, so
the files in the jar aren't usable models on their own. Deflated payload, big-endian:
    "ZCAR" int version (3), int variants, then per variant:
    UTF name, int jpegLength, jpeg bytes (the texture), mesh (the fixed body),
    int lids, then per lid (a boot lid, tailgate or back door that opens when the car is searched):
        byte axis (0 x, 1 y), float pivot x,y,z (m), float angle (radians, fully open), mesh
    int wheels, then per wheel: float centre x,y,z (m), float radius (m), byte steers, mesh
where a mesh is int opaque triangles, int glass triangles (drawn see-through, after the opaque
ones), then all of them * 3 * (short x,y,z in mm, char u,v).
"""
import argparse, glob, io, json, os, struct, sys, zlib
from multiprocessing import Pool
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import wreck
import cabin
import glass

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
VARIANTS = ['usable', 'abandoned', 'wrecked', 'burnt']

# source file -> (id, kind, how often it turns up on a street)
MODELS = {
    '10_SEDAN_01': ('sedan_ltd', 'car', 10), '14_SEDAN_02': ('sedan', 'car', 10),
    '17_WAGON_01': ('wagon', 'car', 8), '32_hatchback': ('hatchback', 'car', 9),
    '33_minivan': ('minivan', 'car', 7), '08_COUPE': ('coupe', 'car', 6),
    '31_COUPE': ('sports_coupe', 'car', 3), '09_CONVERTIBLE': ('convertible', 'car', 3),
    '35_LIMOUSINE': ('limousine', 'car', 1), '16_PICKUP_01': ('pickup', 'car', 8),
    'SUV_02': ('suv', 'car', 7), 'jeep_type1': ('jeep_open', 'car', 2), 'jeep_type2': ('jeep', 'car', 3),
    '14_SEDAN_TAXI_01': ('taxi', 'car', 4), '07_VAN': ('van', 'van', 5),
    'stepvan_delivery': ('step_van', 'van', 2), 'MOVING_TRUCK': ('moving_truck', 'truck', 2),
    '036_TOW_TRUCK': ('tow_truck', 'truck', 1), '10_SEDAN_POLICE_01': ('police_sedan', 'emergency', 3),
    '12_SUV_POLICE_01': ('police_suv', 'emergency', 2), 'stepvan_swat': ('swat_van', 'emergency', 1),
    '06_AMBULANCE': ('ambulance_van', 'emergency', 1), 'AMBULANCE_V2': ('ambulance', 'emergency', 1),
    '05_FIRE_TRUCK': ('fire_truck', 'emergency', 1), '03_CITY_TRUCK': ('box_truck', 'truck', 2),
    '04_1_GARBAGE_TRUCK': ('garbage_truck', 'truck', 1), '04_2_GARBAGE_TRUCK': ('garbage_truck_rear', 'truck', 1),
    '01_CITY_BUS': ('city_bus', 'bus', 1), '02_SCHOOL_BUS': ('school_bus', 'bus', 1),
}

CELL = 0.25  # collision height grid, metres

# what opens when the car is searched: 'car' finds back doors or cuts a lid, 'truck' a roll-up door
BOOTS = {'city_bus': None, 'school_bus': None, 'garbage_truck': None, 'garbage_truck_rear': None,
         'fire_truck': None, 'tow_truck': None, 'box_truck': 'truck', 'moving_truck': 'truck'}
# what's lying in the boot, matching the loot tables (CarProp.lootTable)
THEMES = {'swat_van': 'swat', 'ambulance': 'ambulance', 'ambulance_van': 'ambulance', 'police_sedan': 'police',
          'police_suv': 'police', 'van': 'work', 'step_van': 'work', 'pickup': 'work', 'tow_truck': 'work',
          'box_truck': 'cargo', 'moving_truck': 'cargo'}


def java_hash(s):
    h = 0
    for ch in s:
        h = (31 * h + ord(ch)) & 0xFFFFFFFF
    return h


def scramble(data, model_id):
    """XOR with xorshift32; CarAssets.java undoes it."""
    x = (0x5A0C4A55 ^ java_hash(model_id)) & 0xFFFFFFFF or 1
    out = bytearray(len(data))
    for i in range(0, len(data), 4):
        x ^= (x << 13) & 0xFFFFFFFF; x ^= x >> 17; x ^= (x << 5) & 0xFFFFFFFF
        k = x.to_bytes(4, 'big')
        for j in range(min(4, len(data) - i)):
            out[i + j] = data[i + j] ^ k[j]
    return bytes(out)


def heights(P):
    """Highest point of the car over each CELL x CELL column, sampling across every triangle."""
    lo = P.reshape(-1, 3).min(0); hi = P.reshape(-1, 3).max(0)
    nx = int(np.ceil((hi[0] - lo[0]) / CELL)); nz = int(np.ceil((hi[2] - lo[2]) / CELL))
    grid = np.zeros((nz, nx))
    for a in np.linspace(0, 1, 9):
        for b in np.linspace(0, 1 - a, max(2, int(9 * (1 - a)))):
            q = P[:, 0] * (1 - a - b) + P[:, 1] * a + P[:, 2] * b
            ix = np.clip(((q[:, 0] - lo[0]) / CELL).astype(int), 0, nx - 1)
            iz = np.clip(((q[:, 2] - lo[2]) / CELL).astype(int), 0, nz - 1)
            np.maximum.at(grid, (iz, ix), q[:, 1])
    return lo, nx, nz, grid


def build(path):
    stem = os.path.basename(path)[:-4]
    mid, kind, weight = MODELS[stem]
    car = wreck.prepare(path)
    variants = [wreck.build_variant(car, v, java_hash(mid) % 100000 * 10 + i, BOOTS.get(mid, 'car'), THEMES.get(mid, 'trunk'))
                for i, v in enumerate(VARIANTS)]
    base = variants[0]['tris']
    lo, hi = base.reshape(-1, 3).min(0), base.reshape(-1, 3).max(0)
    centre = np.array([(lo[0] + hi[0]) / 2, 0, (lo[2] + hi[2]) / 2])

    def mesh(b, m):
        order = np.concatenate([np.where(m & ~b['glass'])[0], np.where(m & b['glass'])[0]])
        Q = np.round((b['tris'][order] - centre) * 1000).astype('>i2').reshape(-1, 3)
        U = np.round(np.clip(b['uv'][order], 0, 1) * 65535).astype('>u2').reshape(-1, 2)
        buf.write(struct.pack('>ii', int((m & ~b['glass']).sum()), int((m & b['glass']).sum())))
        buf.write(np.concatenate([Q.view('>u2'), U], 1).astype('>u2').tobytes())

    buf = io.BytesIO()
    buf.write(b'ZCAR'); buf.write(struct.pack('>ii', 3, len(VARIANTS)))
    for name, b in zip(VARIANTS, variants):
        jpg = io.BytesIO(); b['colour'].save(jpg, 'JPEG', quality=90, subsampling=0); jpg = jpg.getvalue()
        nb = name.encode()
        buf.write(struct.pack('>H', len(nb))); buf.write(nb)
        buf.write(struct.pack('>i', len(jpg))); buf.write(jpg)
        mesh(b, (b['lid'] < 0) & (b['wheel'] < 0))
        buf.write(struct.pack('>i', len(b['lids'])))
        for k, (pivot, axis, a) in enumerate(b['lids']):
            buf.write(struct.pack('>b4f', 0 if axis == 'x' else 1, *(np.asarray(pivot, float) - centre), a))
            mesh(b, b['lid'] == k)
        buf.write(struct.pack('>i', len(b['wheels'])))
        for k, (c, r, steers) in enumerate(b['wheels']):
            buf.write(struct.pack('>4fb', *(np.asarray(c, float) - centre), r, 1 if steers else 0))
            mesh(b, (b['wheel'] == k) & (b['lid'] < 0))
    lids = ', '.join(str(len(b['lids'])) for b in variants)
    blob = scramble(zlib.compress(buf.getvalue(), 9), mid)
    out = os.path.join(ROOT, 'src/main/resources/assets/zomboidcraft/cars', mid + '.zcar')
    with open(out, 'wb') as f:
        f.write(blob)
    glo, nx, nz, grid = heights(base - centre)
    # the windows, per variant, and which window each glass triangle of each mesh (body, then each lid) is
    wins = [glass.windows(car, b, centre) for b in variants]
    gtris = [glass.glass_tris(b, [(b['lid'] < 0) & (b['wheel'] < 0)] + [b['lid'] == k for k in range(len(b['lids']))])
             for b in variants]
    axles = sorted({round(float(c[2]), 1) for c, _, _ in variants[0]['wheels']})
    wheelbase = axles[-1] - axles[0] if len(axles) > 1 else 0.6 * (hi[2] - lo[2])
    size = hi - lo
    print(f'{mid:20s} {len(blob) // 1024:5d} KB  {size[0]:.1f} x {size[1]:.1f} x {size[2]:.1f} m  atlas {car.atlas}  lids {lids}', flush=True)
    return {'id': mid, 'kind': kind, 'weight': weight, 'size': [round(float(x), 3) for x in size],
            'cell': CELL, 'x0': round(float(glo[0]), 3), 'z0': round(float(glo[2]), 3), 'nx': nx, 'nz': nz,
            'heights': [int(round(h * 100)) for h in grid.ravel()],
            'seats': [[round(float(v), 3) for v in seat - centre] for seat in wreck.seats(car)],
            'wheelbase': round(float(wheelbase), 3), **cabin.info(car, centre), 'windows': wins, 'glassTris': gtris}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('source')
    ap.add_argument('--jobs', type=int, default=4)
    a = ap.parse_args()
    files = sorted(f for f in glob.glob(os.path.join(a.source, '**', '*.glb'), recursive=True)
                   if os.path.basename(f)[:-4] in MODELS)
    os.makedirs(os.path.join(ROOT, 'src/main/resources/assets/zomboidcraft/cars'), exist_ok=True)
    with Pool(a.jobs) as pool:
        models = pool.map(build, files)
    idx = os.path.join(ROOT, 'src/main/resources/data/zomboidcraft/zc_cars')
    os.makedirs(idx, exist_ok=True)
    # the aircraft (tools/cars/aircraft.py), boats (tools/cars/boats.py) and farm machines
    # (tools/cars/farm_machines.py) aren't from the car pack: keep them
    try:
        with open(os.path.join(idx, 'models.json')) as f:
            models += [m for m in json.load(f)['models'] if m.get('kind') in ('jet', 'boat', 'farm')]
    except FileNotFoundError:
        pass
    models.sort(key=lambda m: m['id'])
    with open(os.path.join(idx, 'models.json'), 'w') as f:
        json.dump({'variants': VARIANTS, 'models': models}, f, separators=(',', ':'))
    print(len(models), 'models')


if __name__ == '__main__':
    main()

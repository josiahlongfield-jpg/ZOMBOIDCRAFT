"""The army's and the security guards' own loot (2026-11-02, with the Arsenal-places gun loot): before this an army
armoury rolled the gun shop's table and a security office had none. shop/army and car/military take the military kit
LOOT built for the SWAT van, with more rounds and the Heavy Sniper's parts; shop/security is a guard's locker. The guns
themselves are added at runtime (combat/GunLoot), by Arsenal's places, never from these files."""
import copy, json, os

ROOT = os.path.join(os.path.dirname(__file__), '..', '..', '..', 'src', 'main', 'resources', 'data', 'zomboidcraft', 'loot_table')


def item(name, weight, lo=None, hi=None, damage=None):
    e = {'type': 'minecraft:item', 'name': 'zomboidcraft:' + name, 'weight': weight}
    fn = []
    if lo is not None:
        fn.append({'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}})
    if damage is not None:
        fn.append({'function': 'minecraft:set_damage', 'damage': {'type': 'minecraft:uniform', 'min': 0, 'max': damage}})
    if fn:
        e['functions'] = fn
    return e


def write(path, table):
    table['random_sequence'] = 'zomboidcraft:' + path
    with open(os.path.join(ROOT, path + '.json'), 'w') as f:
        json.dump(table, f, indent=2)
        f.write('\n')


def main():
    swat = json.load(open(os.path.join(ROOT, 'car', 'swat.json')))
    # the boot of an army truck or jeep: the SWAT van's military kit as it is
    write('car/military', copy.deepcopy(swat))
    # an armoury: the same kit, more rounds and the long-range gear
    army = copy.deepcopy(swat)
    army['pools'][0]['entries'] += [
        item('heavy_ammo', 60, 5, 15), item('heavy_sniper_magazine', 24), item('rifle_ammo', 180, 20, 40),
        item('long_range_scope', 8), item('sniper_suppressor', 6), item('bipod', 8), item('gun_oil', 48),
        item('gun_parts', 36)]
    write('shop/army', army)
    security = {'type': 'minecraft:chest', 'pools': [
        {'rolls': {'type': 'minecraft:uniform', 'min': 1, 'max': 3}, 'entries': [
            item('pistol_ammo', 40, 6, 15), item('revolver_ammo', 24, 6, 12), item('shotgun_shell', 16, 4, 10),
            item('pistol_case', 6), item('two_way_radio', 30), item('aa_battery', 30, 2, 6), item('big_flashlight', 20),
            item('flashlight', 20), item('security_shirt', 24), item('bandage', 16, 1, 2), item('digital_watch', 8),
            item('zip_ties', 8), item('street_map', 6), item('hip_holster', 8), item('duty_belt', 6),
            item('police_scanner', 4), item('donut_box', 6)]},
        {'rolls': 1, 'conditions': [{'condition': 'minecraft:random_chance', 'chance': 0.06}], 'entries': [
            item('police_vest', 2, damage=0.4), item('riot_helmet', 1, damage=0.4), item('shoulder_holster', 1),
            item('lockpick_set', 1), item('binoculars', 1)]}]}
    write('shop/security', security)
    print('wrote car/military, shop/army, shop/security')


main()

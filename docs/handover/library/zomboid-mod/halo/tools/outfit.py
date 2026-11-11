import json
def selector(man,choice):
    """choice: {region: perm}; regions not given fall back to 'default' where present. Returns node-name predicate."""
    on=set()
    for rn,perms in man.items():
        p=choice.get(rn, 'default' if 'default' in perms else None)
        if p is None or p not in perms: continue
        e=perms[p]
        if e['mesh']: on.add(e['mesh'])
        for pc in e['pieces']:
            on.add('inst__'+pc.split('__(')[0]+'__'+(pc.split('__(')[1].rstrip(')') if '__(' in pc else ''))
    return lambda n: n in on
def character(man,who):
    return {rn:(who if who in perms else ('default' if 'default' in perms else None)) for rn,perms in man.items() if rn!='armor_fx'}

import json, math
R={'9x18':(50,12),'9x19':(54,20),'.45':(72,25),'.50AE':(85,40),'5.56':(54,31),'.300':(60,30),'7.62x39':(61,35),'7.62x51':(80,41),'7.62x54R':(81,42),'9x39':(71,28),'12g':(39,3)}
MA37=(5.834+6.788)/2
K={'auto':MA37/80,'semi':17.5/80,'bolt':80/81,'shot':10/39}
G=[('PM','9x18','semi'),('Glock 17','9x19','semi'),('PL-15','9x19','semi'),('USP .45','.45','semi'),('M1911A1','.45','semi'),('Desert Eagle','.50AE','semi'),('Rhino 200DS','9x19','semi'),
('MP5K','9x19','auto'),('Vector 9x19','9x19','auto'),('AK-102','5.56','auto'),('M4A1','5.56','auto'),('HK 416A5','5.56','auto'),('M16A2','5.56','auto'),('ADAR 2-15','5.56','semi'),('MCX','.300','auto'),('AUG A1','5.56','auto'),('G36','5.56','auto'),
('Vepr-KM','7.62x39','semi'),('SCAR-H','7.62x51','auto'),('MDR 7.62','7.62x51','auto'),('RSASS','7.62x51','semi'),('AS VAL','9x39','auto'),('VSK-94','9x39','semi'),('SR-3M','9x39','auto'),('SVDS','7.62x54R','semi'),('SV-98','7.62x54R','bolt'),('MP-18','7.62x54R','bolt'),
('M870','12g','shot'),('AA-12','12g','shot')]
UNSC=[('MA37 (Reach)',MA37,'auto',35),('DMR (Reach)',17.5,'semi',41),('Magnum (Reach)',17.5,'semi',40),('SRS99 sniper (Reach)',80,'bolt',60),('M45 shotgun (Reach, per pellet)',10,'shot',3)]
# class: body, shield (normal), body, shield (legendary)
C={'Grunt':(40,0,60,0),'Jackal':(40,0,80,0),'Skirmisher':(60,0,90,0),'Elite Minor':(70,75,100,75),'Elite Officer':(85,75,100,155),'Elite Ultra':(90,135,120,225),'Elite General':(90,175,130,245),'Hunter':(250,0,500,0)}
JSH=40
def tier(p): return 'low' if p<30 else ('high' if p>=45 else 'med')
def cell(cls,d,fam,pen,leg):
    b,s=(C[cls][2],C[cls][3]) if leg else (C[cls][0],C[cls][1])
    pel = 8 if fam=='shot' else 1
    if fam=='shot': d_shot=d*pel
    else: d_shot=d
    t=tier(pen)
    if cls=='Hunter':
        n=math.ceil(b/d_shot); return f'{n} flesh'
    if cls in('Grunt','Skirmisher'):
        n=math.ceil(b/d_shot)
        h='1 head' if fam!='shot' else f'{math.ceil(b/(2*d_shot))} head'
        return f'{n} body, {h}'
    if cls=='Jackal':
        n=math.ceil(b/d_shot); h='1 head' if fam!='shot' else f'{math.ceil(b/(2*d_shot))} head'
        if t=='low': front='shield stops it'
        elif t=='med': front=f'{math.ceil(JSH/d_shot)} to pop shield'
        else: front='goes through shield'
        return f'{front}; {n} body, {h}'
    # elites
    ns=math.ceil(s/d_shot)
    af=0.8 if t=='low' else 1.0
    nb=math.ceil(b/(d_shot*af))
    if fam in('semi','bolt'): nh=1
    else: nh=math.ceil(b/(2*d_shot*af))
    return f'{ns+nb} body ({ns}+{nh} head)'
rows=[]
def mk(name,cal,fam,d,pen):
    row={'gun':name,'calibre':cal,'family':fam,'halo_dmg':round(d*(8 if fam=='shot' else 1),1),'pen':pen}
    for leg in (0,1):
        for c in C: row[('L ' if leg else 'H ')+c]=cell(c,d,fam,pen,leg)
    return row
COUSIN={'auto':MA37,'semi':17.5,'bolt':80,'shot':10}
CAP=0.9  # Josiah 2026-11-09 07:32 card: Tarkov guns always take a little more to kill than their UNSC cousin
for n,cal,fam in G:
    dmg,pen=R[cal]; rows.append(mk(n,cal,fam,min(dmg*K[fam],CAP*COUSIN[fam]),pen))
for n,d,fam,pen in UNSC: rows.append(mk(n,'-',fam,d,pen))
json.dump({'k':K,'rows':rows},open('/mnt/project-files/zomboid-mod/purge/damage/rounds_to_kill.json','w'),indent=1)
for r in rows: print(r['gun'],r['halo_dmg'],r['H Elite Minor'],'|',r['H Jackal'],'|',r['H Hunter'],'|',r['L Elite General'])
print(K)

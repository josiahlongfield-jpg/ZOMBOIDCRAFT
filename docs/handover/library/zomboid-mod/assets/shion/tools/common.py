import bpy, os
HERE=os.path.dirname(os.path.abspath(__file__))
BAN=('Futa','Revealed','Nipple','Phy-','Bike','Weapon_Default','Refraction','EyeWetness','EyeShadow','Endoskeleton')
def open_blend():
    bpy.ops.wm.open_mainfile(filepath=os.path.join(HERE,'in/Shion-1.0.1-Cloudrig.blend'))
    for im in bpy.data.images:
        p=im.filepath.replace('\\','/')
        base=os.path.basename(p)
        cand=os.path.join(HERE,'in/textures',base)
        if base and (os.path.exists(cand) or '<UDIM>' in base):
            im.filepath=cand
            try: im.reload()
            except Exception: pass
    r=bpy.data.objects['RIG-Shion']
    r.pose.bones['Properties']['Quality']=1
    r.pose.bones['Properties_Physics']['Physics']=False
    r.pose.bones['Properties']['Futa']=False
    return r
def banned(name): return any(b in name for b in BAN)

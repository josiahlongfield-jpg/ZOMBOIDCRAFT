from PIL import Image
import numpy as np, glob, sys
P='/mnt/user-data/uploads/additions/tarkov-ui/png/'
def part(n): return Image.open(glob.glob(P+'S_character_pmc_health2_%s__*'%n)[0]).convert('RGBA')
LAY={'head':(162,0),'chest':(124,78),'stomach':(130,228),'rightarm':(30,92),'leftarm':(262,88),'rightleg':(82,352),'leftleg':(192,350)}
def body(colors=None,W=400,H=720):
    cv=Image.new('RGBA',(W,H),(0,0,0,0))
    for n,(x,y) in LAY.items():
        im=part(n)
        if colors and n in colors:
            a=np.array(im).astype(float); c=np.array(colors[n])/255
            lum=a[...,:3].mean(-1,keepdims=True)/255
            a[...,:3]=np.clip(lum*c*1.25,0,1)*255
            im=Image.fromarray(a.astype('uint8'),'RGBA')
        cv.alpha_composite(im,(x,y))
    return cv
if __name__=='__main__':
    bg=Image.new('RGBA',(400,720),(25,25,25,255)); bg.alpha_composite(body()); bg.save('body_test.png')

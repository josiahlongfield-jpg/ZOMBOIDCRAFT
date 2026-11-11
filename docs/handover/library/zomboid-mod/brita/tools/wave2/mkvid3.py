import json, os, subprocess, shutil
from PIL import Image, ImageDraw, ImageFont
D='/mnt/project-files/zomboid-mod/brita/local-pc/guns/'
DESC={
 'Automag':('.44 AutoMag','front: tall ramped blade on the barrel rib','rear: adjustable leaf, square notch, stainless'),
 'G17':('Glock 17','front: polymer post with a white dot','rear: U notch with a white outline'),
 'HK_USP':('H&K USP','front: post with a white dot','rear: square notch with two white dots (3-dot)'),
 'Colt1903':('Colt 1903 Pocket Hammerless','front: tiny half-round blade on the slide nose','rear: shallow groove notch at the back of the slide'),
 'SW_327':('S&W 327','front: blade with a red insert','rear: adjustable square notch'),
 'Colt_Service38':('Colt New Service','front: half-moon blade on the barrel','rear: groove along the top strap ending in a notch'),
 'CAR15SMG':('CAR-15 SMG','front: A1 post between the triangle\'s ears','rear: aperture in the carrying handle, between guard wings'),
 'MP7':('H&K MP7 (flip-up rule)','front: your pack\'s flip-up front on the rail','rear: your pack\'s flip-up rear (Brita\'s folded ones cut)'),
 'UZI':('IMI Uzi','front: post between Brita\'s front ears (cocking knob kept)','rear: flip L aperture between the rear guards'),
 'AUG9':('Steyr AUG 9mm','front: emergency blade on top of the optic','rear: emergency notch on top of the optic'),
 'Bush_AR15_MOE':('AR-15 MOE','front: A2 post between the triangle\'s ears','rear: your pack\'s flip-up rear on the flat-top'),
 'AK47':('AK-47','front: hooded post on the front sight block','rear: tangent leaf with a U notch ahead of the receiver'),
 'F2000':('FN F2000 (flip-up rule)','front: your pack\'s flip-up on the rail','rear: your pack\'s flip-up on the rail'),
 'G36C':('H&K G36C','front: post between ears on the carry-handle rail','rear: aperture with guard wings at the back of the handle'),
 'SCARL':('FN SCAR-L (flip-up rule)','front: your pack\'s flip-up (Brita\'s folded one cut)','rear: your pack\'s flip-up'),
 'FAL':('FN FAL','front: post between protective ears on the sight block','rear: aperture with protective ears on the receiver cover'),
 'SCARH':('FN SCAR-H (flip-up rule)','front: your pack\'s flip-up, same as the SCAR-L','rear: your pack\'s flip-up'),
 'M1':('M1 Garand','front: post between two wings at the muzzle','rear: aperture with guard wings and its two knobs on the receiver heel'),
 'M82':('Barrett M82A1 (flip-up rule)','front: your pack\'s flip-up, front of the scope rail','rear: your pack\'s flip-up, back of the scope rail'),
 'M40A3':('M40A3 (it ships scope-only)','front: back-up flip-up on the scope rail','rear: back-up flip-up, so it has irons to start with'),
 'SV_98':('SV-98','front: hooded post on its front sight block','rear: diopter (aperture with guard wings) at the back of the receiver'),
 'UTS15':('UTS-15 (flip-up rule)','front: your pack\'s flip-up on the top rail','rear: your pack\'s flip-up on the top rail'),
 'M1014_Wick':('Benelli M1014','front: post between protective ears','rear: ghost ring between tall protective ears'),
 'SPAS12_Fixed':('Franchi SPAS-12','front: tall blade at the muzzle','rear: aperture on the receiver hump'),
 'Ruger_No1_308':('Ruger No. 1','front: ramp with a brass bead on a barrel band','rear: folding leaf with a shallow notch, clear of the quarter rib'),
 'M60':('M60','front: tall blade on its sloped base','rear: folding leaf raised, aperture at the top'),
 'PKM':('PKM','front: hooded post on top of the tall front tower','rear: tangent leaf with a U notch on the top cover'),
}
order=list(DESC)
NAMES={g:json.load(open(D+g+'.json'))['name'] for g in order}
B='/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'; R_='/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
F=ImageFont.truetype(B,24); f=ImageFont.truetype(R_,18)
out='vid3'; shutil.rmtree(out,ignore_errors=True); os.makedirs(out); n=0
def card(im,g,line,k):
    im=im.convert('RGB').copy(); d=ImageDraw.Draw(im)
    d.rectangle([0,0,960,94],fill=(20,22,26)); d.text((14,6),f'{k}/27  {NAMES[g]}',font=F,fill='white'); d.text((14,38),DESC[g][0],font=f,fill=(200,205,215)); d.text((14,64),line,font=f,fill=(230,200,120))
    return im
def save(im,k=1):
    global n
    for _ in range(k): im.save(f'{out}/{n:05d}.png'); n+=1
op=Image.new('RGB',(960,540),(20,22,26)); d=ImageDraw.Draw(op)
d.text((40,170),'Brita wave 2: real-life iron sights (27 guns)',font=ImageFont.truetype(B,32),fill='white')
for i,t in enumerate(['Each gun gets its real sights from the reference photos, roughed up like the first',
                      'batch. Guns whose real sights fold up use your attachment pack\'s flip-ups.',
                      'Per gun: a turn round it, the front sight, the rear sight, then the',
                      'sight picture from behind (front sight centred in the rear).',
                      'Preview only: nothing is in the game or the posing page.']):
    d.text((40,230+i*28),t,font=f,fill=(230,200,120) if i<2 else (200,200,200))
save(op,125)
for k,g in enumerate(order,1):
    V=f'v3/{g}'
    for i in range(12): save(card(Image.open(f'{V}/orb{i:02d}.png'),g,'the gun',k),2)
    save(card(Image.open(f'{V}/front.png'),g,DESC[g][1],k),38)
    save(card(Image.open(f'{V}/rear.png'),g,DESC[g][2],k),38)
    save(card(Image.open(f'{V}/pic.png'),g,'sight picture from behind',k),45)
subprocess.run(['ffmpeg','-y','-loglevel','error','-framerate','25','-i',f'{out}/%05d.png','-c:v','libx264','-pix_fmt','yuv420p','-crf','23','/mnt/project-files/zomboid-mod/brita/previews/brita-wave2-iron-sights.mp4'],check=True)
print(n/25,'s')

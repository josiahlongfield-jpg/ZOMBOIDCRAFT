import subprocess
fps=30.0
ev=[]
for seg,base in (('plain',180),('kit',329)):
    shots=['m4a1_outdoor_close_onseshot_0%d'%k for k in (1,2,3)] if seg=='plain' else ['m4a1_outdoor_silenced_close_onseshot_0%d'%k for k in (1,2,3)]
    for k,f in enumerate((15,22,29)): ev.append((shots[k],base+f,1.0))
    ev+= [('mcx_magrelease_button',base+58,1.0),('mcx_mag_out1',base+61,1.0),('generic_mag_pouch_out7',base+88,0.8),('mcx_mag_in2',base+115,1.0),('hand_grip3',base+134,0.8)]
inp=[];flt=[]
for i,(n,f,v) in enumerate(ev):
    inp+=['-i','snd/%s.wav'%n]; ms=int(f/fps*1000)
    flt.append(f'[{i}:a]aformat=sample_rates=44100:channel_layouts=stereo,volume={v},adelay={ms}|{ms}[a{i}]')
mix=''.join(f'[a{i}]' for i in range(len(ev)))+f'amix=inputs={len(ev)}:normalize=0,alimiter=limit=0.95[out]'
cmd=['ffmpeg','-y','-framerate','30','-i','fr/final/f%04d.png']+inp+['-filter_complex',';'.join(flt)+';'+mix,'-map','0:v','-map','[out]','-c:v','libx264','-pix_fmt','yuv420p','-crf','20','-c:a','aac','-b:a','192k','-shortest','tarkov-m4a1-pilot.mp4']
# input indices shift by one because video is input 0
cmd=['ffmpeg','-y','-framerate','30','-i','fr/final/f%04d.png']+inp+['-filter_complex',';'.join(x.replace(f'[{i}:a]',f'[{i+1}:a]',1) for i,x in enumerate(flt))+';'+mix,'-map','0:v','-map','[out]','-c:v','libx264','-pix_fmt','yuv420p','-crf','20','-c:a','aac','-b:a','192k','tarkov-m4a1-pilot.mp4']
r=subprocess.run(cmd,capture_output=True,text=True); print(r.returncode, r.stderr[-600:])

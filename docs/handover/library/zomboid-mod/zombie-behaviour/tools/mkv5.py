import os,subprocess,shutil
d='shots'; files=sorted(f for f in os.listdir(d) if f.endswith('.png'))
def cap(n):
    if n<20: return "Downtown, night. About 600 of the dead up and walking round you"
    if n<50: return "A rifle shot every 6 seconds pulls them out of the doors and onto the balconies"
    if n<80: return "Around 400 within 24 blocks"
    if n<120: return "Looking down: the street under you packed"
    return "On the slow test machine the server ran about 18 ticks a second (20 is full speed)"
shutil.rmtree('v5',ignore_errors=True); os.makedirs('v5')
lst=open('v5.txt','w')
for i,f in enumerate(files):
    n=int(f[-7:-4]); out=f'v5/{i:04d}.png'
    c=cap(n).replace(',','\\,').replace(':','\\:')
    subprocess.run(['ffmpeg','-loglevel','error','-y','-i',f'{d}/{f}','-vf',f"drawtext=fontfile=/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf:text='{c}':x=(w-tw)/2:y=h-50:fontsize=24:fontcolor=white:box=1:boxcolor=black@0.55:boxborderw=10",out],check=True)
    lst.write(f"file '{out}'\nduration 0.2\n")
lst.write(f"file '{out}'\n"); lst.close()
subprocess.run(['ffmpeg','-loglevel','error','-y','-f','concat','-safe','0','-i','v5.txt','-vf','fps=25,format=yuv420p','-c:v','libx264','-crf','23','/mnt/project-files/zomboid-mod/zombie-behaviour/swarm-downtown.mp4'],check=True)

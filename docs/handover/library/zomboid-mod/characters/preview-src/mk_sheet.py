import sys
t=open('sheet_test.html').read()
pre=t[:t.index('const TONES')]; post=t[t.index('const o = document'):]
open('sheet_test.html','w').write(pre+open('paintskin.js').read()+'\n'+post)

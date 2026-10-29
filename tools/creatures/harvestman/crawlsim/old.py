from walls import *
import walls
w=World(); w.box(-12,-1,-12,12,0,12); w.box(-13,0,-13,13,7,-12); w.box(-13,0,12,13,7,13); w.box(-13,0,-12,-12,7,12); w.box(12,0,-12,13,7,12); w.box(-13,7,-13,13,8,13)
w.box(2,0,2,4,2,4)
run('room: to a corner',w,(0,2.5,0),(8,1,8))
run('room: up a wall',w,(0,2.5,0),(11.0,5,0))
run('room: ceiling',w,(0,2.5,0),(0,6.0,0))
w2=World(); w2.box(-30,-1,-30,30,0,30); w2.box(0,0,0,6,8,6)
run('roof->ground',w2,(3,8+2.5,3),(12,1.0,3))
run('ground->roof',w2,(14,2.5,3),(3,9.5,3))
# two-storey house: prey upstairs, stairs inside (a ramp of blocks), door at the front
w3=World(); w3.box(-30,-1,-30,30,0,30)
w3.box(-6,0,4,6,9,5); w3.box(-6,0,16,6,9,17); w3.box(-6,0,4,-5,9,17); w3.box(5,0,4,6,9,17); w3.box(-6,9,4,6,10,17)
w3.box(-5,4,4,5,5,17)                               # the upstairs floor
for k in range(5): w3.box(-4+k,0,12,-3+k,k+1,16)    # stairs up along x
for k in range(4): w3.solid.discard((-4+k+1,4,12)); w3.solid.discard((-4+k+1,4,13)); w3.solid.discard((-4+k+1,4,14)); w3.solid.discard((-4+k+1,4,15))
for y in range(3): w3.solid.discard((0,y,4))         # a one-wide front door
run('house: prey upstairs',w3,(0,2.5,-6),(-3,5,8))

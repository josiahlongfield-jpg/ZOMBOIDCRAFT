"""Scratch: the Glass Line as its own shuttle: Mercy (under the hospital block) to Eastside Quay."""
import math, json

class Turtle:
    def __init__(s, x, z, deg):
        s.x, s.z, s.t = x, z, math.radians(deg)
        s.pts = [(x, z)]
    def fwd(s, d):
        n = max(1, int(round(d)))
        for i in range(n):
            s.x += math.cos(s.t) * d / n; s.z += math.sin(s.t) * d / n
            s.pts.append((s.x, s.z))
    def turn(s, r, deg):
        a = math.radians(deg); n = max(2, int(abs(a) * r))
        for i in range(n):
            s.t += a / n
            step = abs(a) / n * r
            s.x += math.cos(s.t - a / n / 2) * step; s.z += math.sin(s.t - a / n / 2) * step
            s.pts.append((s.x, s.z))
    def to_z(s, z): s.fwd(abs(z - s.z) / max(1e-9, abs(math.sin(s.t))))
    def to_x(s, x): s.fwd(abs(x - s.x) / max(1e-9, abs(math.cos(s.t))))
    def here(s): return (round(s.x, 1), round(s.z, 1))

S, E, N, W = 90, 0, -90, 180
GAP = 12
R1, R2, RA, RC, RB, RB2 = 100, 158, 64, 40, 40, 52
marks = {}
# A: east from the island turning loop, under the subway's Mercy platform
a = Turtle(2790, -400, E)
marks['mercy_a'] = a.here()
a.to_x(2908)
marks['mercy_b'] = a.here()
a.turn(RC, 90)                      # right, south toward the river
a.to_z(-80)
a.turn(R1, -90)                     # left, east under the el
a.to_x(3230)
a.turn(R2, 50); a.turn(R2, -50)     # S-bend to the south bank
a.fwd(150)                          # along the quay (B, on the glass side, stops)
a.turn(RA, 90)                      # right, into Eastside
a.fwd(40)
a.turn(RB, 60); a.turn(RB2, -300); a.turn(RB, 60)   # the mainland turning loop
print("after mainland loop", a.here(), round(math.degrees(a.t)) % 360)
a.fwd(40)
a.turn(RA + GAP, -90)               # left, west along the quay
marks['quay_a'] = a.here()
a.fwd(150)
marks['quay_b'] = a.here()
a.turn(R2 - GAP, 50); a.turn(R2 + GAP, -50)
a.to_x(3048)
a.turn(R1 - GAP, 90)
a.to_z(-360)
a.turn(RC + GAP, -90)
a.to_x(2790)
print("before island loop", a.here(), round(math.degrees(a.t)) % 360)
a.turn(RB, 60); a.turn(RB2, -300); a.turn(RB, 60)
print("closed at", a.here(), "start", a.pts[0], round(math.degrees(a.t)) % 360)
P = a.pts
s = [0.0]
for i in range(1, len(P)):
    s.append(s[-1] + math.hypot(P[i][0]-P[i-1][0], P[i][1]-P[i-1][1]))
print("lap", round(s[-1]))
json.dump({'pts': P, 'marks': marks, 's': s}, open('/tmp/claude-0/plan/tube/shuttle.json', 'w'))

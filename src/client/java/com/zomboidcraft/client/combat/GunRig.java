package com.zomboidcraft.client.combat;

import java.util.Map;

/**
 * One gun's first-person rig, generated into {@link GunRigs}. Points are in item model pixels, before the display
 * transform.
 *
 * @param hipGrip      where the grip sits in the hand camera at the hip (blocks)
 * @param hipTurn      the gun's turn at the hip (degrees: muzzle up, muzzle in, roll)
 * @param magOut       the magazine steps' cant (degrees) and drop (blocks), applied to the hip pose
 * @param charge       the charging step's cant and drop
 * @param sights       each sight's aiming pose: a translation (pixels), a scale, and for a bead gun how far it's
 *                     tipped up (degrees) to bring the bead onto the eye line
 * @param handleTravel how far the charging handle (a pistol's slide) comes back
 * @param chargeTurn   how the gun turns about the grip to work the handle (degrees)
 * @param chargePush   and how far it moves (blocks)
 * @param loadTurn     how a tube gun turns about the grip while it's loaded (degrees), on top of its magOut cant
 * @param loadPush     and how far it moves (blocks)
 * @param boltLift     a bolt gun's: degrees the bolt turns up to open (about boltAxis), before it runs back handleTravel
 * @param magAxis      the way a magazine slides out of the well
 * @param locksBack    the handle (slide) stays back on an empty gun
 * @param cycles       it runs back with every shot
 * @param laser        the laser's lens, on the right rail: its beam runs parallel to the bore
 * @param light        the light's lens, on the left rail
 * @param quietMuzzle  where a suppressed shot's smaller flash starts: the end of the suppressor
 * @param boltAxis     a point on the bore, which a bolt turns about
 * @param supportGrip  the off hand wraps the grip (a pistol's), rather than cupping a handguard
 * @param pump         the off hand rides a pump (the handle) and works it, and shells go in one at a time through
 *                     the port (magTop), each rocked about its head (magCentre)
 * @param wristGrip    the shooting hand holds a stock's wrist rather than a pistol grip
 * @param bolt         the handle is a bolt the shooting hand works (its knob is handle) after each shot, and to
 *                     chamber a round after a fresh magazine on an empty gun
 * @param hinge        what the handle (a double's barrels, a revolver's cylinder and crane) turns about to open
 * @param openAxis     the axis it turns about there
 * @param openAngle    how far it turns open (degrees)
 * @param cylAxis      a point on a cylinder's axis (closed), which its rounds turn about
 * @param chamberStep  from one chamber to the next: item pixels along x side by side, or degrees round a cylinder
 * @param opens        loaded by hand with the action open; the loose round (in magTop's chamber when home) goes in
 *                     one chamber at a time
 * @param cylinder     the rounds sit in a cylinder that turns a chamber with each shot
 * @param lever        a tube gun worked by a lever (the handle): it turns openAngle about the hinge with the shooting
 *                     hand's fingers in its loop, and its bolt (the slide) rides back handleTravel with it; the rounds
 *                     go in through a gate on the right, each swung in about its nose (magCentre)
 */
record GunRig(float[] hipGrip, float[] hipTurn, float hipScale, float[] magOut, float[] charge,
              Map<String, float[]> sights, float handleTravel, float[] chargeTurn, float[] chargePush,
              float[] loadTurn, float[] loadPush, float boltLift,
              float[] grip, float[] support, float[] foregrip, float[] magTop, float[] magHold, float[] magCentre,
              float[] handle, float[] laser, float[] light, float[] muzzle, float[] quietMuzzle, float[] boltAxis,
              float[] magAxis,
              boolean locksBack, boolean cycles, boolean supportGrip, boolean pump, boolean wristGrip, boolean bolt,
              float[] hinge, float[] openAxis, float openAngle, float[] cylAxis, float chamberStep, boolean opens,
              boolean cylinder, boolean lever) {
}

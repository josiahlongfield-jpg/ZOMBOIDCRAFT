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
 * @param sights       each sight's aiming pose: a translation (pixels) and a scale
 * @param handleTravel how far the charging handle (a pistol's slide) comes back
 * @param chargeTurn   how the gun turns about the grip to work the handle (degrees)
 * @param chargePush   and how far it moves (blocks)
 * @param magAxis      the way a magazine slides out of the well
 * @param locksBack    the handle (slide) stays back on an empty gun
 * @param cycles       it runs back with every shot
 * @param laser        the laser's lens, on the right rail: its beam runs parallel to the bore
 * @param light        the light's lens, on the left rail
 * @param quietMuzzle  where a suppressed shot's smaller flash starts: the end of the suppressor
 * @param supportGrip  the off hand wraps the grip (a pistol's), rather than cupping a handguard
 */
record GunRig(float[] hipGrip, float[] hipTurn, float hipScale, float[] magOut, float[] charge,
              Map<String, float[]> sights, float handleTravel, float[] chargeTurn, float[] chargePush,
              float[] grip, float[] support, float[] foregrip, float[] magTop, float[] magHold, float[] magCentre,
              float[] handle, float[] laser, float[] light, float[] muzzle, float[] quietMuzzle,
              float[] magAxis,
              boolean locksBack, boolean cycles, boolean supportGrip) {
}

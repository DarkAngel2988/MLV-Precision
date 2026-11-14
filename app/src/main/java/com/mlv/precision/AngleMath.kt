package com.mlv.precision

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot

/** Coordinates of the two manually traced LINE SEGMENTS in image pixels. */
data class Coordinate(val x: Double, val y: Double)

/**
 * Returns the smaller unoriented angle, 0..90 degrees, between two lines.
 * Null means that one of the lines has no length. The result is a PIXEL-
 * PLANE angle, not a physical measurement of real-world squareness.
 */
object AngleMath {
    fun acuteAngleDegrees(a: Coordinate, b: Coordinate, c: Coordinate, d: Coordinate): Double? {
        val ax = b.x - a.x
        val ay = b.y - a.y
        val bx = d.x - c.x
        val by = d.y - c.y
        val norm = hypot(ax, ay) * hypot(bx, by)
        if (!norm.isFinite() || norm <= 1e-10) return null
        val cosine = abs((ax * bx + ay * by) / norm).coerceIn(0.0, 1.0)
        return Math.toDegrees(acos(cosine))
    }

    fun deviationFromSquareDegrees(angle: Double): Double = abs(90.0 - angle)
}

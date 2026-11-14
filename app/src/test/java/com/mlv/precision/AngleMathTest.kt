package com.mlv.precision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AngleMathTest {
    private val origin = Coordinate(0.0, 0.0)
    @Test fun perpendicularLines() {
        assertEquals(90.0, AngleMath.acuteAngleDegrees(origin, Coordinate(20.0,0.0),
            origin, Coordinate(0.0,40.0))!!, 0.00001)
    }
    @Test fun fortyFiveDegrees() {
        assertEquals(45.0, AngleMath.acuteAngleDegrees(origin, Coordinate(1.0,0.0),
            origin, Coordinate(1.0,1.0))!!, 0.00001)
    }
    @Test fun reversingDirectionDoesNotChangeLineAngle() {
        assertEquals(90.0, AngleMath.acuteAngleDegrees(Coordinate(5.0,0.0),origin,
            origin, Coordinate(0.0,-10.0))!!, 0.00001)
    }
    @Test fun parallelLines() {
        assertEquals(0.0, AngleMath.acuteAngleDegrees(origin,Coordinate(8.0,0.0),
            Coordinate(0.0,9.0),Coordinate(8.0,9.0))!!, 0.00001)
    }
    @Test fun identicalPointsCannotDefineLine() {
        assertNull(AngleMath.acuteAngleDegrees(origin,origin,origin,Coordinate(1.0,0.0)))
    }
    @Test fun deviationFromSquare() {
        assertEquals(2.0, AngleMath.deviationFromSquareDegrees(88.0), 0.00001)
    }
}

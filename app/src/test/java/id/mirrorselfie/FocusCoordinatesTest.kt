package id.mirrorselfie

import org.junit.Assert.*
import org.junit.Test

class FocusCoordinatesTest {
    @Test fun mirroredMonitorReversesHorizontalCoordinatesOnly() {
        assertEquals(.75f, FocusCoordinates.fromMonitor(180f, 400f, 720, 1600, true)!!.first, .0001f)
        assertEquals(.25f, FocusCoordinates.fromMonitor(180f, 400f, 720, 1600, true)!!.second, .0001f)
    }
    @Test fun landscapeMonitorUsesActualAspectFitSurfaceNotTheBlackBars() {
        val point = FocusCoordinates.fromMonitor(81f, 180f, 324, 720, false)!!
        assertEquals(.25f, point.first, .0001f)
        val mapped = FocusCoordinates.intoPreview(point.first, point.second, 1080, 2400, 0, 0, 1080, 2400)!!
        assertEquals(270f, mapped.first, .0001f); assertEquals(600f, mapped.second, .0001f)
    }
    @Test fun previewInsetsAreRemovedBeforeCameraMetering() {
        val point = FocusCoordinates.intoPreview(.5f, .5f, 1080, 2400, 0, 120, 1080, 2160)!!
        assertEquals(540f, point.first, .0001f); assertEquals(1080f, point.second, .0001f)
        assertNull(FocusCoordinates.intoPreview(.5f, 0f, 1080, 2400, 0, 120, 1080, 2160))
    }
    @Test fun invalidAndUnlaidOutCoordinatesCannotReachTheCamera() {
        assertNull(FocusCoordinates.fromMonitor(0f, 0f, 0, 0, false))
        assertNull(FocusCoordinates.fromMonitor(Float.NaN, 0f, 720, 1600, false))
        assertNull(FocusCoordinates.fromMonitor(-1f, 0f, 720, 1600, false))
        assertNull(FocusCoordinates.intoPreview(Float.POSITIVE_INFINITY, .5f, 1080, 2400, 0, 0, 1080, 2400))
    }
}

package id.mirrorselfie

import org.junit.Assert.*
import org.junit.Test

class CameraSnapshotTest {
    @Test fun recordingAllowsStopButBlocksPhoto() {
        val recording=CameraSnapshot(available=true,recording=true)
        assertTrue(recording.canToggleVideo)
        assertFalse(recording.canTakePhoto)
    }
    @Test fun inactiveOrBusyCameraCannotBeOperated() {
        listOf(CameraSnapshot(),CameraSnapshot(available=false,recording=true),CameraSnapshot(available=true,busy=true)).forEach {
            assertFalse(it.canTakePhoto); assertFalse(it.canToggleVideo)
        }
        val ready=CameraSnapshot(available=true)
        assertTrue(ready.canTakePhoto); assertTrue(ready.canToggleVideo)
    }
}

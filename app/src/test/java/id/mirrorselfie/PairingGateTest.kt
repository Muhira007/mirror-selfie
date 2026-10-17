package id.mirrorselfie

import org.junit.Assert.*
import org.junit.Test

class PairingGateTest {
    @Test fun rejectsWrongTokenWithoutConsumingPairing() {
        val gate = PairingGate("secret", 500)
        assertFalse(gate.accept("wrong", 100))
        assertTrue(gate.accept("secret", 101))
        assertFalse(gate.accept("secret", 102))
    }
    @Test fun expiresAtTheDeadline() {
        assertFalse(PairingGate("secret", 500).accept("secret", 500))
        assertFalse(PairingGate("secret", 500).accept("secret", 501))
        assertTrue(PairingGate("secret", 500).accept("secret", 499))
    }
    @Test fun concurrentPairingHasExactlyOneWinner() {
        val gate = PairingGate("secret", 1000)
        val wins = java.util.concurrent.atomic.AtomicInteger()
        val threads = List(20) { Thread { if (gate.accept("secret", 100)) wins.incrementAndGet() } }
        threads.forEach { it.start() }; threads.forEach { it.join() }
        assertEquals(1, wins.get())
    }
    @Test fun screenSizePreservesFrameWithinBoundingBox() {
        listOf(1080 to 2400, 2400 to 1080, 1080 to 1920, 1920 to 1080).forEach { (w, h) ->
            val (scaledW, scaledH) = RtcEngine.scaledSize(w, h)
            assertTrue(maxOf(scaledW, scaledH) <= 1280)
            assertTrue(minOf(scaledW, scaledH) <= 720)
            assertEquals(w.toDouble() / h, scaledW.toDouble() / scaledH, 0.005)
            assertEquals(0, scaledW % 2); assertEquals(0, scaledH % 2)
        }
    }
}

package id.mirrorselfie

import org.junit.Assert.*
import org.junit.Test

class ResumeGateTest {
    @Test fun originalQrCannotBeUsedAsResumeCredential() {
        val gate = ResumeGate("resume-secret")
        gate.disconnected(1000)
        assertFalse(gate.accept("qr-secret", 1100)); assertTrue(gate.accept("resume-secret", 1100))
    }
    @Test fun deadlineRejectsEvenTheCorrectMonitor() {
        val gate = ResumeGate("secret", 100)
        gate.disconnected(1000)
        assertTrue(gate.accept("secret", 1099)); assertFalse(gate.accept("secret", 1100))
        assertTrue(gate.expired(1100))
    }
    @Test fun repeatedDisconnectNotificationsDoNotExtendTheDeadline() {
        val gate = ResumeGate("secret", 100)
        gate.disconnected(1000); gate.disconnected(1090)
        assertTrue(gate.expired(1100))
    }
    @Test fun successfulResumeAllowsAnotherTemporaryDrop() {
        val gate = ResumeGate("secret", 100)
        gate.disconnected(1000); assertTrue(gate.accept("secret", 1050)); gate.connected()
        assertFalse(gate.expired(2000)); gate.disconnected(3000)
        assertTrue(gate.accept("secret", 3099)); assertFalse(gate.accept("secret", 3100))
    }
}

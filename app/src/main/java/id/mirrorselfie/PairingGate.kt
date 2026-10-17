package id.mirrorselfie

import java.security.MessageDigest

/** Expiry uses monotonic time supplied by the host, never the viewer's wall clock. */
class PairingGate(private val secret: String, private val expiresAt: Long) {
    private var consumed = false
    @Synchronized fun accept(candidate: String, now: Long): Boolean {
        if (consumed || now >= expiresAt || !MessageDigest.isEqual(secret.toByteArray(), candidate.toByteArray())) return false
        consumed = true
        return true
    }
}

package id.mirrorselfie

import java.security.MessageDigest

/** Separate from the one-use QR token. Never persisted; only the authenticated monitor receives it. */
class ResumeGate(private val secret: String, private val windowMs: Long = 120_000) {
    private var disconnectedAt: Long? = null
    fun disconnected(now: Long) { if (disconnectedAt == null) disconnectedAt = now }
    fun connected() { disconnectedAt = null }
    fun accept(candidate: String, now: Long): Boolean =
        (disconnectedAt?.let { now - it < windowMs } ?: true) &&
            MessageDigest.isEqual(secret.toByteArray(), candidate.toByteArray())
    fun expired(now: Long): Boolean = disconnectedAt?.let { now - it >= windowMs } ?: false
}

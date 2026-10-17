package id.mirrorselfie

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.math.BigInteger
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import java.util.UUID
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.security.auth.x500.X500Principal

data class Pairing(val session: String, val host: String, val port: Int, val fingerprint: String, val token: String) {
    fun code(): String = "mirror1:" + Base64.encodeToString(JSONObject().apply {
        put("v", 1); put("s", session); put("h", host); put("p", port)
        put("f", fingerprint); put("t", token)
    }.toString().toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    companion object {
        fun parse(code: String): Pairing {
            require(code.length <= 2048 && code.startsWith("mirror1:")) { "Kode koneksi tidak valid" }
            val json = JSONObject(String(Base64.decode(code.trim().substringAfter(":"), Base64.URL_SAFE)))
            require(json.getInt("v") == 1) { "Versi koneksi tidak didukung" }
            val result = Pairing(json.getString("s"), json.getString("h"), json.getInt("p"), json.getString("f"), json.getString("t"))
            // A connection code must never turn the LAN-only client into a public-network client.
            require(result.host.matches(Regex("[0-9.]+"))) { "Alamat harus IPv4 lokal" }
            val ip = java.net.InetAddress.getByName(result.host)
            require(ip is Inet4Address && ip.isSiteLocalAddress && !ip.isLoopbackAddress) { "Alamat harus jaringan lokal" }
            require(result.port in 1024..65535 && result.fingerprint.matches(Regex("[a-f0-9]{64}")))
            require(result.token.matches(Regex("[a-f0-9]{64}")) && result.session.length == 36)
            return result
        }
    }
}

class SessionIdentity : AutoCloseable {
    private val alias = "mirror-session-${UUID.randomUUID()}"
    val store: KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val fingerprint: String
    val context: SSLContext

    init {
        // A killed process cannot run close(); discard only this app's abandoned session keys.
        store.aliases().toList().filter { it.startsWith("mirror-session-") }.forEach { store.deleteEntry(it) }
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
        generator.initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
            .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
            // Conscrypt supplies the already-computed TLS signature digest to the hardware key.
            .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512)
            .setCertificateSubject(X500Principal("CN=Mirror Selfie"))
            .setCertificateSerialNumber(BigInteger.valueOf(System.currentTimeMillis()))
            .setCertificateNotBefore(Date(System.currentTimeMillis() - 86400000L))
            .setCertificateNotAfter(Date(System.currentTimeMillis() + 86400000L)).build())
        generator.generateKeyPair()
        fingerprint = sha256(store.getCertificate(alias).encoded)
        val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, null) }
        context = SSLContext.getInstance("TLS").apply { init(managers.keyManagers, null, SecureRandom()) }
    }

    override fun close() { store.deleteEntry(alias) }
    companion object {
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        fun randomToken(): String = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        fun clientContext(pin: String): SSLContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = throw java.security.cert.CertificateException("Not a server")
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    if (chain.isEmpty() || !MessageDigest.isEqual(sha256(chain[0].encoded).toByteArray(), pin.toByteArray()))
                        throw java.security.cert.CertificateException("Fingerprint berbeda")
                    chain[0].checkValidity()
                }
            }), SecureRandom())
        }

        fun localAddresses(): List<String> = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.name.startsWith("tun") }
            .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("ap") || it.name.startsWith("swlan")) 0 else 1 }
            .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>()
            .filter { it.isSiteLocalAddress }.mapNotNull { it.hostAddress }.distinct()
    }
}

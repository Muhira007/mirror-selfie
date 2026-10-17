package id.mirrorselfie

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.junit.Assert.*
import org.junit.Test

class QrDecoderTest {
    @Test fun readsDensePairingCodeAndQuarterTurn() {
        val code="mirror1:"+"aZ19-_".repeat(80)
        val matrix=MultiFormatWriter().encode(code,BarcodeFormat.QR_CODE,600,600)
        val bytes=ByteArray(600*600) { if(matrix[it%600,it/600]) 0 else 255.toByte() }
        val rotated=ByteArray(bytes.size)
        for(y in 0 until 600) for(x in 0 until 600) rotated[x*600+599-y]=bytes[y*600+x]
        val decoder=QrDecoder()
        assertEquals(code,decoder.decode(bytes,600,600))
        assertEquals(code,decoder.decode(rotated,600,600))
    }
    @Test fun rejectsEmptyFramesAndCanReadNextFrame() {
        val decoder=QrDecoder()
        assertNull(decoder.decode(ByteArray(0),0,0))
        assertNull(decoder.decode(ByteArray(8),10,10))
        assertNull(decoder.decode(ByteArray(400*400){255.toByte()},400,400))
        val code="mirror1:another-test-code"
        val matrix=MultiFormatWriter().encode(code,BarcodeFormat.QR_CODE,400,400)
        assertEquals(code,decoder.decode(ByteArray(400*400){if(matrix[it%400,it/400]) 0 else 255.toByte()},400,400))
    }
}

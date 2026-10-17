package id.mirrorselfie

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/** Offline decoder shared with JVM tests. Images and connection codes are never logged. */
class QrDecoder {
    private val reader=MultiFormatReader().apply { setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),DecodeHintType.TRY_HARDER to true)) }
    fun decode(bytes:ByteArray,width:Int,height:Int):String? {
        if(width<=0 || height<=0 || bytes.size.toLong()<width.toLong()*height) return null
        return try {
            val source=PlanarYUVLuminanceSource(bytes,width,height,0,0,width,height,false)
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch(_:com.google.zxing.ReaderException) { null }
        finally { reader.reset() }
    }
}

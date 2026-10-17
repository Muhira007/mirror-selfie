package id.mirrorselfie

import android.util.Range
import androidx.camera.core.CameraInfo
import androidx.camera.core.DynamicRange
import androidx.camera.core.Preview
import androidx.camera.core.SessionConfig
import androidx.camera.video.*

data class VideoMode(val quality:Quality,val fps:Int) {
    val label:String get()="${when(quality) {Quality.UHD->"4K"; Quality.FHD->"1080p"; Quality.HD->"720p"; else->"480p"}} · ${if(fps==0) "FPS otomatis" else "$fps fps"}"
}

object VideoModes {
    /** Query each exact resolution with Preview + Video, rather than assuming every
     * advertised AE range is available at every resolution. No vendor-name rules. */
    fun supported(info:CameraInfo):List<VideoMode> {
        val supported=Recorder.getVideoCapabilities(info).getSupportedQualities(DynamicRange.SDR)
        return listOf(Quality.UHD,Quality.FHD,Quality.HD,Quality.SD).filter{it in supported}.flatMap { quality ->
            val recorder=Recorder.Builder().setQualitySelector(QualitySelector.from(quality)).build()
            val video=VideoCapture.withOutput(recorder)
            val preview=Preview.Builder().setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_16_9).build()
            val ranges=runCatching { info.getSupportedFrameRateRanges(SessionConfig(preview,video)) }.getOrDefault(emptySet())
            val fps=listOf(30,60).filter { target -> ranges.any{it.contains(target)} }
            if(fps.isEmpty()) listOf(VideoMode(quality,0)) else fps.map{VideoMode(quality,it)}
        }
    }
    fun recorder(mode:VideoMode)=Recorder.Builder().setQualitySelector(QualitySelector.from(mode.quality)).build()
    fun session(preview:Preview,video:VideoCapture<Recorder>,mode:VideoMode)=
        if(mode.fps==0) SessionConfig(preview,video)
        else SessionConfig(useCases=listOf(preview,video),frameRateRange=Range(mode.fps,mode.fps))
}

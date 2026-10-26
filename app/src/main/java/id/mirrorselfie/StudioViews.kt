package id.mirrorselfie

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.View

internal object Studio {
    val background = Color.rgb(9, 19, 25)
    val surface = Color.rgb(18, 33, 41)
    val raised = Color.rgb(25, 45, 53)
    val border = Color.rgb(42, 64, 73)
    val mint = Color.rgb(118, 240, 202)
    val ink = Color.rgb(10, 38, 34)
    val text = Color.rgb(242, 248, 248)
    val muted = Color.rgb(163, 184, 191)
    val coral = Color.rgb(255, 132, 128)

    fun shape(context: Context, color: Int, radius: Int = 24, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius * context.resources.displayMetrics.density
            stroke?.let { setStroke((context.resources.displayMetrics.density).toInt().coerceAtLeast(1), it) }
        }

    fun touch(context: Context, color: Int, radius: Int = 20, stroke: Int? = null) =
        RippleDrawable(ColorStateList.valueOf(0x2476F0CA), shape(context, color, radius, stroke), shape(context, Color.WHITE, radius))
}

/** Small, consistent native line icons: no font glyphs or remote assets. */
internal class StudioIcon @JvmOverloads constructor(context: Context, private val name: String = "info", private val color: Int = Studio.mint) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; strokeWidth = 1.7f }
    private val path = Path()
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val save = canvas.save()
        val size = minOf(width, height).toFloat()
        canvas.translate((width-size)/2, (height-size)/2)
        canvas.scale(size/24, size/24)
        paint.color = color
        fun line(x: Float, y: Float, a: Float, b: Float) = canvas.drawLine(x, y, a, b, paint)
        fun rect(l: Float, t: Float, r: Float, b: Float, radius: Float = 3f) = canvas.drawRoundRect(l,t,r,b,radius,radius,paint)
        when (name) {
            "hide" -> { path.reset(); path.apply { moveTo(2f,12f); quadTo(12f,1f,22f,12f); quadTo(12f,23f,2f,12f); close() }; canvas.drawPath(path,paint); canvas.drawCircle(12f,12f,3f,paint); line(3f,3f,21f,21f) }
            "torch" -> { line(12f,3f,12f,11f); line(8f,11f,16f,11f); line(9f,11f,10f,21f); line(15f,11f,14f,21f); line(10f,21f,14f,21f); line(7f,5f,5f,3f); line(17f,5f,19f,3f) }
            "timer" -> { canvas.drawCircle(12f,13f,8f,paint); line(12f,13f,12f,8f); line(12f,13f,15f,13f); line(10f,2f,14f,2f) }
            "grid" -> { rect(3f,3f,21f,21f,2f); line(9f,3f,9f,21f); line(15f,3f,15f,21f); line(3f,9f,21f,9f); line(3f,15f,21f,15f) }
            "zoomIn" -> { canvas.drawCircle(11f,11f,6f,paint); line(15.5f,15.5f,21f,21f); line(11f,8f,11f,14f); line(8f,11f,14f,11f) }
            "zoomOut" -> { canvas.drawCircle(11f,11f,6f,paint); line(15.5f,15.5f,21f,21f); line(8f,11f,14f,11f) }
            "camera" -> { rect(3f,7f,21f,20f); canvas.drawCircle(12f,13.5f,3.4f,paint); line(7f,7f,9f,4f); line(9f,4f,15f,4f); line(15f,4f,17f,7f) }
            "monitor" -> { rect(2.5f,4f,21.5f,17f,2.5f); line(12f,17f,12f,21f); line(8f,21f,16f,21f) }
            "wifi" -> { canvas.drawArc(1f,3f,23f,24f,224f,92f,false,paint); canvas.drawArc(5f,8f,19f,23f,224f,92f,false,paint); canvas.drawArc(9f,13f,15f,21f,224f,92f,false,paint); canvas.drawCircle(12f,20f,.7f,paint) }
            "arrow" -> { line(4f,12f,20f,12f); line(14f,6f,20f,12f); line(20f,12f,14f,18f) }
            "back" -> { line(20f,12f,4f,12f); line(10f,6f,4f,12f); line(4f,12f,10f,18f) }
            "copy" -> { rect(8f,8f,21f,21f,2f); path.reset(); path.apply { moveTo(16f,4f); lineTo(16f,3f); lineTo(3f,3f); lineTo(3f,16f); lineTo(4f,16f) }; canvas.drawPath(path,paint) }
            "mirror" -> { line(12f,3f,12f,21f); path.reset(); path.apply { moveTo(8f,6f); lineTo(3f,18f); lineTo(8f,18f); close(); moveTo(16f,6f); lineTo(21f,18f); lineTo(16f,18f); close() }; canvas.drawPath(path,paint) }
            "record" -> { canvas.drawCircle(12f,12f,8f,paint); paint.style=Paint.Style.FILL; canvas.drawCircle(12f,12f,3.5f,paint); paint.style=Paint.Style.STROKE }
            "close" -> { line(6f,6f,18f,18f); line(18f,6f,6f,18f) }
            "check" -> { line(5f,12f,10f,17f); line(10f,17f,20f,7f) }
            "shield" -> { path.reset(); path.apply { moveTo(12f,3f); lineTo(20f,6f); lineTo(20f,12f); quadTo(20f,18f,12f,22f); quadTo(4f,18f,4f,12f); lineTo(4f,6f); close() }; canvas.drawPath(path,paint); line(8f,12f,11f,15f); line(11f,15f,16f,10f) }
            "link" -> { canvas.save(); canvas.rotate(-35f,12f,12f); rect(3f,8f,13f,16f,4f); rect(11f,8f,21f,16f,4f); canvas.restore() }
            "settings" -> { canvas.drawCircle(12f,12f,4f,paint); for(i in 0..7) { canvas.save(); canvas.rotate(i*45f,12f,12f); line(12f,2f,12f,5f); canvas.restore() }; canvas.drawCircle(12f,12f,8f,paint) }
            else -> { canvas.drawCircle(12f,12f,9f,paint); line(12f,11f,12f,17f); canvas.drawCircle(12f,7f,.4f,paint) }
        }
        canvas.restoreToCount(save)
    }
}

/** A lightweight illustration drawn once by Android, with no looping animation. */
internal class StudioArtwork(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val screen = RectF()
    private val clip = Path()
    private val hills = Path()
    private val sky = LinearGradient(0f,0f,0f,1f,intArrayOf(0xFF224E59.toInt(),0xFF91CBB9.toInt()),null,Shader.TileMode.CLAMP)
    private val skyMatrix = Matrix()
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val save = c.save()
        val scale = minOf(width/320f, height/152f)
        c.translate((width-320*scale)/2, (height-152*scale)/2)
        c.scale(scale, scale)
        fun round(l:Float,t:Float,r:Float,b:Float,rad:Float,color:Int) { p.color=color; p.shader=null; p.style=Paint.Style.FILL; c.drawRoundRect(l,t,r,b,rad,rad,p) }
        p.color=0x1476F0CA; c.drawCircle(166f,76f,72f,p)
        p.color=0x0C76F0CA; c.drawCircle(166f,76f,95f,p)
        fun phone(x:Float,y:Float,w:Float,h:Float,angle:Float,source:Boolean) {
            c.save(); c.rotate(angle,x+w/2,y+h/2)
            round(x+2,y+5,x+w+2,y+h+5,14f,0x50000000)
            round(x,y,x+w,y+h,14f,Studio.border)
            round(x+1,y+1,x+w-1,y+h-1,13f,if(source) Studio.mint else Color.rgb(114,187,203))
            round(x+3,y+3,x+w-3,y+h-3,11f,Studio.background)
            screen.set(x+7,y+11,x+w-7,y+h-10)
            c.save(); clip.reset(); clip.addRoundRect(screen,7f,7f,Path.Direction.CW); c.clipPath(clip)
            skyMatrix.setScale(1f,screen.height()); skyMatrix.postTranslate(0f,screen.top); sky.setLocalMatrix(skyMatrix); p.shader=sky
            c.drawRect(screen,p); p.shader=null
            p.color=0xFFFFCB97.toInt(); c.drawCircle(x+w*.72f,y+h*.28f,w*.14f,p)
            p.color=0xFF426F70.toInt(); hills.reset(); hills.apply { moveTo(screen.left,screen.bottom); lineTo(screen.left,y+h*.65f); quadTo(x+w*.23f,y+h*.43f,x+w*.54f,y+h*.7f); quadTo(x+w*.8f,y+h*.46f,screen.right,y+h*.6f); lineTo(screen.right,screen.bottom); close() }; c.drawPath(hills,p)
            // A simple human silhouette with a camera framing guide.
            p.color=0xFF173A3E.toInt(); c.drawCircle(x+w*.47f,y+h*.49f,w*.09f,p)
            c.drawOval(x+w*.29f,y+h*.57f,x+w*.65f,y+h*1.1f,p)
            p.style=Paint.Style.STROKE; p.strokeWidth=.8f; p.color=0x66FFFFFF
            for(i in 1..2) { val xx=screen.left+screen.width()*i/3; c.drawLine(xx,screen.top,xx,screen.bottom,p); val yy=screen.top+screen.height()*i/3; c.drawLine(screen.left,yy,screen.right,yy,p) }
            p.strokeWidth=1.4f; p.color=Studio.mint
            val fx=x+w*.25f; val fy=y+h*.35f; val fr=x+w*.7f; val fb=y+h*.8f
            c.drawLine(fx,fy,fx+7,fy,p); c.drawLine(fx,fy,fx,fy+7,p); c.drawLine(fr,fy,fr-7,fy,p); c.drawLine(fr,fy,fr,fy+7,p)
            c.drawLine(fx,fb,fx+7,fb,p); c.drawLine(fx,fb,fx,fb-7,p); c.drawLine(fr,fb,fr-7,fb,p); c.drawLine(fr,fb,fr,fb-7,p)
            p.style=Paint.Style.FILL; c.restore()
            round(x+w*.35f,y+5,x+w*.65f,y+8,2f,Studio.raised)
            if(source) { p.color=Studio.coral; c.drawCircle(x+w-15,y+19,2.5f,p) }
            c.restore()
        }
        phone(160f,6f,80f,136f,9f,true)
        phone(88f,21f,70f,119f,-10f,false)
        round(132f,105f,192f,131f,13f,Studio.mint)
        p.color=Studio.ink; p.strokeWidth=1.5f; p.style=Paint.Style.STROKE
        c.drawLine(145f,118f,178f,118f,p); c.drawLine(174f,114f,178f,118f,p); c.drawLine(178f,118f,174f,122f,p)
        p.style=Paint.Style.FILL
        c.restoreToCount(save)
    }
}

/** Grid overlay for Rule of Thirds */
internal class GridOverlayView(context: Context) : View(context) {
    private val paint = Paint().apply {
        color = 0x55FFFFFF.toInt()
        strokeWidth = 1f * context.resources.displayMetrics.density
        style = Paint.Style.STROKE
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        canvas.drawLine(w / 3f, 0f, w / 3f, h, paint)
        canvas.drawLine(2f * w / 3f, 0f, 2f * w / 3f, h, paint)
        canvas.drawLine(0f, h / 3f, w, h / 3f, paint)
        canvas.drawLine(0f, 2f * h / 3f, w, 2f * h / 3f, paint)
    }
}

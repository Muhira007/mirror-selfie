
const fs = require('fs');
let s = fs.readFileSync('app/src/main/java/id/mirrorselfie/StudioViews.kt', 'utf8');

const oldCases = 'when (name) {';
const newCases = `when (name) {
            "torch" -> { line(12f,3f,12f,11f); line(8f,11f,16f,11f); line(9f,11f,10f,21f); line(15f,11f,14f,21f); line(10f,21f,14f,21f); line(7f,5f,5f,3f); line(17f,5f,19f,3f) }
            "timer" -> { canvas.drawCircle(12f,13f,8f,paint); line(12f,13f,12f,8f); line(12f,13f,15f,13f); line(10f,2f,14f,2f) }
            "grid" -> { rect(3f,3f,21f,21f,2f); line(9f,3f,9f,21f); line(15f,3f,15f,21f); line(3f,9f,21f,9f); line(3f,15f,21f,15f) }
            "zoomIn" -> { canvas.drawCircle(11f,11f,6f,paint); line(15.5f,15.5f,21f,21f); line(11f,8f,11f,14f); line(8f,11f,14f,11f) }
            "zoomOut" -> { canvas.drawCircle(11f,11f,6f,paint); line(15.5f,15.5f,21f,21f); line(8f,11f,14f,11f) }`;
s = s.replace(oldCases, newCases);

// Also add a GridOverlay view
const gridClass = `
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
`;

s = s + gridClass;
fs.writeFileSync('app/src/main/java/id/mirrorselfie/StudioViews.kt', s, 'utf8');
console.log('StudioViews patched');


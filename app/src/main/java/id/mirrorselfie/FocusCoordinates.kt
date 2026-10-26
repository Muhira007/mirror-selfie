package id.mirrorselfie

/** Coordinates are normalized against the whole captured display, not the cropped sensor. */
object FocusCoordinates {
    fun fromMonitor(x: Float, y: Float, width: Int, height: Int, mirrored: Boolean): Pair<Float, Float>? {
        if (width <= 0 || height <= 0 || !x.isFinite() || !y.isFinite()) return null
        val nx = x / width; val ny = y / height
        if (nx !in 0f..1f || ny !in 0f..1f) return null
        return (if (mirrored) 1f - nx else nx) to ny
    }
    fun intoPreview(x: Float, y: Float, screenWidth: Int, screenHeight: Int,
                    left: Int, top: Int, width: Int, height: Int): Pair<Float, Float>? {
        if (!x.isFinite() || !y.isFinite() || x !in 0f..1f || y !in 0f..1f || width <= 0 || height <= 0 || screenWidth <= 0 || screenHeight <= 0) return null
        val px = x * screenWidth - left; val py = y * screenHeight - top
        return if (px in 0f..width.toFloat() && py in 0f..height.toFloat()) px to py else null
    }
}

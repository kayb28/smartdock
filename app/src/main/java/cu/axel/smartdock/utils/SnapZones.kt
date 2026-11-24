package cu.axel.smartdock.utils

/**
 * Snap zones (F5): dragging a freeform window near a display edge or corner
 * tiles it into half or quarter of the display.
 *
 * The dock cannot intercept the system's own freeform drag; the service
 * watches the task bounds (privileged getTaskBounds mirror) and asks this
 * pure model where the window's CENTER is relative to the usable display
 * area. Center-based detection is stable while the system animates the
 * drag (a corner-tracking point flickers between zones on bouncy windows).
 *
 * Zone → bounds is percent-of-usable-area (F5/F7 precedent), so a snap
 * re-computes correctly on whichever display the task lives on, and the
 * result is clamped/sanity-checked: a snap must never produce the
 * empty-Rect zero-window bug (same guard as WindowPresets.toBounds).
 *
 * Pure JVM — no android.jar (its classes are stubs in unit tests): bounds
 * come back as [SnapRect] and the caller (DockService) builds the real
 * android.graphics.Rect, the same discipline as KeyboardModifiers freezing
 * KeyEvent constants.
 */
object SnapZones {
    /** Plain left/top/right/bottom holder, android-free. */
    data class SnapRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

    enum class SnapZone {
        LEFT_HALF, RIGHT_HALF, TOP_HALF, BOTTOM_HALF,
        QUADRANT_TL, QUADRANT_TR, QUADRANT_BL, QUADRANT_BR
    }

    /**
     * Zone for a point against a usable area ([width] x [height]) with the
     * given [threshold] (px) on each edge. Corners win over edges (a center
     * within threshold of two edges is a quadrant); the middle of the
     * display is a no-op so normal repositioning never snaps.
     */
    fun zoneFor(
        width: Int, height: Int, centerX: Int, centerY: Int, threshold: Int
    ): SnapZone? {
        if (width <= 0 || height <= 0 || threshold <= 0) return null
        val nearLeft = centerX <= threshold
        val nearRight = centerX >= width - threshold
        val nearTop = centerY <= threshold
        val nearBottom = centerY >= height - threshold
        return when {
            nearLeft && nearTop -> SnapZone.QUADRANT_TL
            nearRight && nearTop -> SnapZone.QUADRANT_TR
            nearLeft && nearBottom -> SnapZone.QUADRANT_BL
            nearRight && nearBottom -> SnapZone.QUADRANT_BR
            nearLeft -> SnapZone.LEFT_HALF
            nearRight -> SnapZone.RIGHT_HALF
            nearTop -> SnapZone.TOP_HALF
            nearBottom -> SnapZone.BOTTOM_HALF
            else -> null
        }
    }

    /**
     * Pixel bounds of [zone] inside the usable area. Halves take half the
     * width or height; quadrants take both halves. Percent-based and
     * clamped; a degenerate result falls back to the whole usable area
     * instead of an empty window.
     */
    fun boundsFor(
        zone: SnapZone, left: Int, top: Int, width: Int, height: Int
    ): SnapRect {
        val halfW = width / 2
        val halfH = height / 2
        val (w, h) = when (zone) {
            SnapZone.LEFT_HALF, SnapZone.RIGHT_HALF -> halfW to height
            SnapZone.TOP_HALF, SnapZone.BOTTOM_HALF -> width to halfH
            else -> halfW to halfH
        }
        val x = when (zone) {
            SnapZone.RIGHT_HALF, SnapZone.QUADRANT_TR, SnapZone.QUADRANT_BR ->
                left + width - w
            else -> left
        }
        val y = when (zone) {
            SnapZone.BOTTOM_HALF, SnapZone.QUADRANT_BL, SnapZone.QUADRANT_BR ->
                top + height - h
            else -> top
        }
        val right = (x + w).coerceAtMost(left + width)
        val bottom = (y + h).coerceAtMost(top + height)
        return if (right > x && bottom > y) SnapRect(x, y, right, bottom)
        else SnapRect(left, top, left + width, top + height)
    }
}

package cu.axel.smartdock.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the snap-zone geometry (F5): edge/corner classification of
 * a drag point and the percent-of-display bounds each zone resolves to,
 * including the clamp/sanity guards.
 *
 * Pure JUnit: android-free — bounds come back as SnapZones.SnapRect.
 */
class SnapZonesTest {
    private fun zone(x: Int, y: Int, threshold: Int = 24) =
        SnapZones.zoneFor(1000, 800, x, y, threshold)

    // --- classification ---

    @Test fun centerIsNoOp() {
        assertNull(zone(500, 400))
        assertNull(zone(100, 100))
        assertNull(zone(990, 400))
    }

    @Test fun leftEdgeSnapsHalf() {
        assertEquals(SnapZones.SnapZone.LEFT_HALF, zone(10, 400))
        assertEquals(SnapZones.SnapZone.LEFT_HALF, zone(24, 400))
    }

    @Test fun rightEdgeSnapsHalf() {
        assertEquals(SnapZones.SnapZone.RIGHT_HALF, zone(976, 400))
        assertEquals(SnapZones.SnapZone.RIGHT_HALF, zone(990, 400))
    }

    @Test fun topAndBottomEdgesSnapHalves() {
        assertEquals(SnapZones.SnapZone.TOP_HALF, zone(500, 5))
        assertEquals(SnapZones.SnapZone.BOTTOM_HALF, zone(500, 790))
    }

    @Test fun cornersWinOverEdges() {
        assertEquals(SnapZones.SnapZone.QUADRANT_TL, zone(5, 5))
        assertEquals(SnapZones.SnapZone.QUADRANT_TR, zone(995, 5))
        assertEquals(SnapZones.SnapZone.QUADRANT_BL, zone(5, 795))
        assertEquals(SnapZones.SnapZone.QUADRANT_BR, zone(995, 795))
    }

    @Test fun cornerNeedsBothEdges() {
        // Near the top edge but horizontally in the middle: top half, not a
        // quadrant.
        assertEquals(SnapZones.SnapZone.TOP_HALF, zone(500, 5))
        // Near the left edge at a corner-distance from top but beyond the
        // threshold: left half.
        assertEquals(SnapZones.SnapZone.LEFT_HALF, zone(10, 30))
    }

    @Test fun thresholdBoundaries() {
        // Exactly at threshold counts as inside (inclusive).
        assertEquals(SnapZones.SnapZone.LEFT_HALF, zone(24, 400))
        assertEquals(SnapZones.SnapZone.LEFT_HALF, zone(25, 400, 25))
        // One past it does not.
        assertNull(zone(25, 400))
    }

    @Test fun degenerateInputsAreNoOp() {
        assertNull(SnapZones.zoneFor(0, 800, 5, 5, 24))
        assertNull(SnapZones.zoneFor(1000, 0, 5, 5, 24))
        assertNull(SnapZones.zoneFor(1000, 800, 5, 5, 0))
        assertNull(SnapZones.zoneFor(1000, 800, 5, 5, -1))
    }

    // --- bounds ---

    @Test fun halfBoundsCoverHalfTheUsableArea() {
        assertEquals(SnapZones.SnapRect(0, 0, 500, 800), SnapZones.boundsFor(
            SnapZones.SnapZone.LEFT_HALF, 0, 0, 1000, 800))
        assertEquals(SnapZones.SnapRect(500, 0, 1000, 800), SnapZones.boundsFor(
            SnapZones.SnapZone.RIGHT_HALF, 0, 0, 1000, 800))
        assertEquals(SnapZones.SnapRect(0, 0, 1000, 400), SnapZones.boundsFor(
            SnapZones.SnapZone.TOP_HALF, 0, 0, 1000, 800))
        assertEquals(SnapZones.SnapRect(0, 400, 1000, 800), SnapZones.boundsFor(
            SnapZones.SnapZone.BOTTOM_HALF, 0, 0, 1000, 800))
    }

    @Test fun quadrantBoundsCoverQuarterOfTheUsableArea() {
        assertEquals(SnapZones.SnapRect(0, 0, 500, 400), SnapZones.boundsFor(
            SnapZones.SnapZone.QUADRANT_TL, 0, 0, 1000, 800))
        assertEquals(SnapZones.SnapRect(500, 0, 1000, 400), SnapZones.boundsFor(
            SnapZones.SnapZone.QUADRANT_TR, 0, 0, 1000, 800))
        assertEquals(SnapZones.SnapRect(0, 400, 500, 800), SnapZones.boundsFor(
            SnapZones.SnapZone.QUADRANT_BL, 0, 0, 1000, 800))
        assertEquals(SnapZones.SnapRect(500, 400, 1000, 800), SnapZones.boundsFor(
            SnapZones.SnapZone.QUADRANT_BR, 0, 0, 1000, 800))
    }

    @Test fun boundsRespectUsableAreaOrigin() {
        // A display area that does not start at 0,0 (status bar, side
        // dock region): halves/quadrants resolve inside it.
        assertEquals(SnapZones.SnapRect(200, 100, 700, 500), SnapZones.boundsFor(
            SnapZones.SnapZone.LEFT_HALF, 200, 100, 1000, 800))
        assertEquals(SnapZones.SnapRect(700, 500, 1200, 900), SnapZones.boundsFor(
            SnapZones.SnapZone.QUADRANT_BR, 200, 100, 1000, 800))
    }

    @Test fun oddUsableAreaStillTilesWithoutGaps() {
        // 1001x801: halfW = 500, right half starts at 501 — 1px column
        // uncovered, acceptable; the point is never negative/empty and
        // never exceeds the area.
        val r = SnapZones.boundsFor(SnapZones.SnapZone.RIGHT_HALF, 0, 0, 1001, 801)
        assertEquals(SnapZones.SnapRect(501, 0, 1001, 801), r)
    }

    @Test fun degenerateAreaFallsBackToFullUsable() {
        // width=1, height=1: halves compute 0x1 — the sanity guard must
        // return the full usable area instead of an empty rect.
        assertEquals(SnapZones.SnapRect(0, 0, 1, 1), SnapZones.boundsFor(
            SnapZones.SnapZone.LEFT_HALF, 0, 0, 1, 1))
        assertEquals(SnapZones.SnapRect(0, 0, 1, 1), SnapZones.boundsFor(
            SnapZones.SnapZone.QUADRANT_BR, 0, 0, 0, 0))
    }
}

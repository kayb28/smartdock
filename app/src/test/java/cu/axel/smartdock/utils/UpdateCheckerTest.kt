package cu.axel.smartdock.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for UpdateChecker's version-tag parser, extracted as pure-JVM
 * logic so they run under `./gradlew test` without android.jar. Covers the
 * cases the task names: malformed tags, tags without a `-fork.N` suffix, and
 * same-version fork iterations (1.15.10 vs 1.15.9, fork.10 vs fork.9). The
 * parser never throws on bad input — `parseVersionTuple` returns an empty
 * list (non-numeric parts are dropped via `mapNotNull`) and `parseForkN`
 * returns 0 — so malformed cases assert deterministic values, not exceptions.
 *
 * `parseVersionTuple` / `parseForkN` are `internal` (module-visible) so the
 * test can exercise them directly; `isNewer` is public and is tested for the
 * comparison semantics a caller relies on (the startup check, the Settings
 * live check, and the fork.10 release self-recognition check).
 */
class UpdateCheckerTest {

    // --- parseVersionTuple: "v1.15.5-fork.9" -> [1, 15, 5] ---

    @Test fun tupleFromForkTag() =
        assertEquals(listOf(1, 15, 5), UpdateChecker.parseVersionTuple("v1.15.5-fork.9"))

    @Test fun tupleWithoutForkSuffix() =
        assertEquals(listOf(1, 15, 5), UpdateChecker.parseVersionTuple("v1.15.5"))

    @Test fun tupleWithDoubleDigitPatch() =
        assertEquals(listOf(1, 15, 10), UpdateChecker.parseVersionTuple("v1.15.10-fork.1"))

    @Test fun tupleWithMajorBump() =
        assertEquals(listOf(2, 0, 0), UpdateChecker.parseVersionTuple("v2.0.0-fork.1"))

    /** The fork suffix and anything after it are stripped before splitting. */
    @Test fun tupleIgnoresSuffixBeyondForkN() =
        assertEquals(listOf(1, 15, 5), UpdateChecker.parseVersionTuple("v1.15.5-fork.9-rc1"))

    // --- parseVersionTuple: malformed -> empty / partial, never throws ---

    @Test fun tupleFromGarbageIsEmpty() =
        assertEquals(emptyList<Int>(), UpdateChecker.parseVersionTuple("garbage"))

    @Test fun tupleFromEmptyIsEmpty() =
        assertEquals(emptyList<Int>(), UpdateChecker.parseVersionTuple(""))

    /** Non-numeric parts are dropped, the numeric ones are kept in order. */
    @Test fun tupleDropsNonNumericPart() =
        assertEquals(listOf(1, 5), UpdateChecker.parseVersionTuple("v1.abc.5-fork.9"))

    // --- parseForkN: "v1.15.5-fork.9" -> 9 ; no suffix -> 0 ---

    @Test fun forkNFromForkTag() =
        assertEquals(9, UpdateChecker.parseForkN("v1.15.5-fork.9"))

    @Test fun forkNDoubleDigit() =
        assertEquals(10, UpdateChecker.parseForkN("v1.15.5-fork.10"))

    @Test fun forkNWithoutSuffixIsZero() =
        assertEquals(0, UpdateChecker.parseForkN("v1.15.5"))

    /** "-fork" without the trailing dot is not the fork-N marker. */
    @Test fun forkNWithoutDotIsZero() =
        assertEquals(0, UpdateChecker.parseForkN("v1.15.5-fork"))

    /** A non-numeric fork N degrades to 0 instead of throwing. */
    @Test fun forkNNonNumericIsZero() =
        assertEquals(0, UpdateChecker.parseForkN("v1.15.5-fork.abc"))

    /** Trailing suffix after the fork N is ignored. */
    @Test fun forkNIgnoresTrailingSuffix() =
        assertEquals(9, UpdateChecker.parseForkN("v1.15.5-fork.9-rc1"))

    // --- isNewer: same-version fork iterations ---

    /** The case that motivated tuple + forkN comparison: fork.10 on 1.15.5
     *  must be newer than fork.9 on 1.15.5. */
    @Test fun higherForkIterationIsNewer() =
        assertTrue(UpdateChecker.isNewer("v1.15.5-fork.10", "v1.15.5-fork.9"))

    @Test fun lowerForkIterationIsNotNewer() =
        assertFalse(UpdateChecker.isNewer("v1.15.5-fork.9", "v1.15.5-fork.10"))

    @Test fun equalTagsAreNotNewer() =
        assertFalse(UpdateChecker.isNewer("v1.15.5-fork.9", "v1.15.5-fork.9"))

    // --- isNewer: version tuple wins over fork iteration ---

    /** 1.15.10 > 1.15.9 even though fork.9 > fork.1: the version tuple is
     *  compared first, so a higher patch beats a higher fork iteration. */
    @Test fun higherPatchBeatsHigherForkIteration() =
        assertTrue(UpdateChecker.isNewer("v1.15.10-fork.1", "v1.15.9-fork.9"))

    @Test fun minorBumpIsNewer() =
        assertTrue(UpdateChecker.isNewer("v1.16.0-fork.1", "v1.15.5-fork.9"))

    @Test fun majorBumpIsNewer() =
        assertTrue(UpdateChecker.isNewer("v2.0.0-fork.1", "v1.15.5-fork.9"))

    // --- isNewer: missing fork suffix ---

    /** A tag without `-fork.N` (forkN == 0) is older than any fork.N > 0 on
     *  the same version — so an upstream-style tag is never "newer" than a
     *  fork release on the same version. */
    @Test fun noForkSuffixIsOlderThanForkIteration() =
        assertFalse(UpdateChecker.isNewer("v1.15.5", "v1.15.5-fork.9"))

    @Test fun forkIterationIsNewerThanNoSuffix() =
        assertTrue(UpdateChecker.isNewer("v1.15.5-fork.9", "v1.15.5"))

    // --- isNewer: malformed input never throws ---

    @Test fun garbageLatestIsNotNewer() =
        assertFalse(UpdateChecker.isNewer("garbage", "v1.15.5-fork.9"))

    @Test fun emptyLatestIsNotNewer() =
        assertFalse(UpdateChecker.isNewer("", "v1.15.5-fork.9"))

    /** A malformed `current` parses to an all-zero tuple, so any real
     *  version compares "newer" — degenerate but never throws. In practice
     *  `current` is always the well-formed `fork_release_tag`. */
    @Test fun realVersionIsNewerThanGarbageCurrent() =
        assertTrue(UpdateChecker.isNewer("v1.15.5-fork.9", "garbage"))

    /** fork.10 self-recognition: the release this build ships as must not
     *  report itself as newer than itself (the fork.10 release check). */
    @Test fun ownReleaseIsNotNewerThanItself() =
        assertFalse(UpdateChecker.isNewer("v1.15.6-fork.10", "v1.15.6-fork.10"))
}

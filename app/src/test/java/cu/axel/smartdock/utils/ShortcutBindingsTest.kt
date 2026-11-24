package cu.axel.smartdock.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the shortcut-editor model (F8): chord normalization and
 * display, save-time validation (AltGr reservation, modifier requirement),
 * conflict detection, dispatch resolution and the JSON persistence
 * round-trip with its degradation contract.
 *
 * Pure JUnit (no android.jar): the KeyEvent constant values live in
 * ShortcutBindings/KeyboardModifiers as hardcoded Ints, verified against
 * AOSP KeyEvent.java (2026-11-24).
 */
class ShortcutBindingsTest {
    // KeyEvent.KEYCODE_* frozen values (verified against AOSP)
    private val KEY_A = 29
    private val KEY_K = 39
    private val KEY_L = 40
    private val KEY_N = 42
    private val KEY_F12 = 142
    private val KEY_DPAD_UP = 19

    private val M = ShortcutBindings

    // --- modsOf / normalize ---

    @Test fun modsOfCombinesFlags() {
        assertEquals(
            M.MOD_ALT or M.MOD_SHIFT,
            M.modsOf(ctrl = false, alt = true, shift = true, meta = false)
        )
        assertEquals(0, M.modsOf(ctrl = false, alt = false, shift = false, meta = false))
        assertEquals(
            M.MOD_META,
            M.modsOf(ctrl = false, alt = false, shift = false, meta = true)
        )
    }

    @Test fun normalizeDropsUnknownBits() {
        // A real event.metaState carries wide non-contiguous bits; only our
        // four survive normalization.
        val dirty = M.MOD_ALT or 0x4000 or 0x20000000
        assertEquals(M.MOD_ALT, M.normalize(dirty))
    }

    // --- validation ---

    @Test fun validChordWithAltModifier() {
        assertEquals(
            ShortcutBindings.Validation.OK,
            M.validate(M.MOD_ALT, KEY_K)
        )
    }

    @Test fun validChordWithMetaOnlyModifier() {
        assertEquals(
            ShortcutBindings.Validation.OK,
            M.validate(M.MOD_META, KEY_K)
        )
    }

    @Test fun ctrlAltChordRejectedAsAltGrReserved() {
        // Ctrl+Alt chords are exactly what AltGr delivers (#167); they can
        // never fire because the dock passes them through untouched, so they
        // are rejected at save time instead of becoming a dead binding.
        assertEquals(
            ShortcutBindings.Validation.ALTGR_RESERVED,
            M.validate(M.MOD_CTRL or M.MOD_ALT, KEY_K)
        )
    }

    @Test fun bareKeyRejected() {
        assertEquals(
            ShortcutBindings.Validation.NO_MODIFIER,
            M.validate(0, KEY_K)
        )
    }

    @Test fun shiftOnlyRejected() {
        // Shift alone would fire while typing capital letters.
        assertEquals(
            ShortcutBindings.Validation.NO_MODIFIER,
            M.validate(M.MOD_SHIFT, KEY_K)
        )
    }

    @Test fun missingKeyRejected() {
        assertEquals(
            ShortcutBindings.Validation.NO_KEY,
            M.validate(M.MOD_ALT, 0)
        )
    }

    @Test fun everyDefaultChordIsValid() {
        // The catalog must never contain a chord the editor itself rejects.
        for (b in M.defaults())
            assertEquals(
                "default chord ${M.formatChord(b.mods, b.keyCode)}",
                ShortcutBindings.Validation.OK,
                M.validate(b.mods, b.keyCode)
            )
    }

    @Test fun defaultChordsNeverCollideWithAltGrRule() {
        // Dock shortcuts never use Ctrl+Alt so AltGr pass-through stays intact.
        for (b in M.defaults())
            assertFalse(M.isAltGrReserved(b.mods))
    }

    // --- conflicts / resolution ---

    @Test fun conflictFindsExistingBinding() {
        val bindings = listOf(
            ShortcutBindings.Binding(M.MOD_ALT, KEY_L, "lock_desktop")
        )
        assertEquals(
            "lock_desktop",
            M.conflictOf(bindings, M.MOD_ALT, KEY_L)?.action
        )
    }

    @Test fun conflictNormalizesModsBeforeMatch() {
        // Dirty metaState bits must not dodge a conflict.
        val bindings = listOf(
            ShortcutBindings.Binding(M.MOD_ALT, KEY_L, "lock_desktop")
        )
        assertEquals(
            "lock_desktop",
            M.conflictOf(bindings, M.MOD_ALT or 0x4000, KEY_L)?.action
        )
    }

    @Test fun noConflictWhenChordDiffers() {
        val bindings = listOf(
            ShortcutBindings.Binding(M.MOD_ALT, KEY_L, "lock_desktop")
        )
        assertNull(M.conflictOf(bindings, M.MOD_ALT, KEY_K))
        assertNull(M.conflictOf(bindings, M.MOD_CTRL, KEY_L))
        assertNull(M.conflictOf(bindings, M.MOD_ALT or M.MOD_SHIFT, KEY_L))
    }

    @Test fun resolveMapsChordToAction() {
        val bindings = listOf(
            ShortcutBindings.Binding(M.MOD_META, KEY_K, "open_settings")
        )
        assertEquals(
            ShortcutBindings.ShortcutAction.OPEN_SETTINGS,
            M.resolve(bindings, M.MOD_META, KEY_K)
        )
    }

    @Test fun resolveReturnsNullForUnknownChord() {
        assertNull(M.resolve(emptyList(), M.MOD_ALT, KEY_L))
    }

    @Test fun resolveReturnsNullForUnknownActionId() {
        // Old/foreign storage with an action the catalog no longer has must
        // degrade to "no binding", never crash the dispatch path.
        val bindings = listOf(
            ShortcutBindings.Binding(M.MOD_ALT, KEY_L, "vanished_action")
        )
        assertNull(M.resolve(bindings, M.MOD_ALT, KEY_L))
    }

    @Test fun resolveNormalizesMods() {
        val bindings = listOf(
            ShortcutBindings.Binding(M.MOD_ALT, KEY_L, "lock_desktop")
        )
        assertEquals(
            ShortcutBindings.ShortcutAction.LOCK_DESKTOP,
            M.resolve(bindings, M.MOD_ALT or 0x4000, KEY_L)
        )
    }

    // --- display ---

    @Test fun formatChordOrdersModifiersAndNames() {
        assertEquals("Alt+L", M.formatChord(M.MOD_ALT, KEY_L))
        assertEquals("Ctrl+Shift+K", M.formatChord(M.MOD_CTRL or M.MOD_SHIFT, KEY_K))
        assertEquals("Alt+Shift+N", M.formatChord(M.MOD_ALT or M.MOD_SHIFT, KEY_N))
        assertEquals("Meta+F12", M.formatChord(M.MOD_META, KEY_F12))
        assertEquals("Alt+Up", M.formatChord(M.MOD_ALT, KEY_DPAD_UP))
    }

    @Test fun keyNamesForCatalogRanges() {
        assertEquals("A", M.keyName(KEY_A))
        assertEquals("L", M.keyName(KEY_L))
        assertEquals("1", M.keyName(8))
        assertEquals("0", M.keyName(7))
        assertEquals("F12", M.keyName(KEY_F12))
        assertEquals("F3", M.keyName(133))
        assertEquals("Down", M.keyName(20))
        // Unknown keycodes stay honest instead of pretending a name.
        assertEquals("Key 999", M.keyName(999))
    }

    @Test fun displayChordFallsBackToDefault() {
        val action = ShortcutBindings.ShortcutAction.LOCK_DESKTOP
        assertEquals("Alt+L", M.displayChord(action, emptyList()))
        assertEquals(
            "Ctrl+K",
            M.displayChord(
                action, listOf(ShortcutBindings.Binding(M.MOD_CTRL, KEY_K, "lock_desktop"))
            )
        )
    }

    // --- JSON persistence ---

    @Test fun jsonRoundTrip() {
        val bindings = listOf(
            ShortcutBindings.Binding(M.MOD_ALT, KEY_L, "lock_desktop"),
            ShortcutBindings.Binding(M.MOD_META, KEY_K, "open_settings")
        )
        val text = M.toJson(bindings)
        assertEquals(
            """[{"mods":4,"key":40,"action":"lock_desktop"},{"mods":8,"key":39,"action":"open_settings"}]""",
            text
        )
        assertEquals(bindings, M.fromJson(text))
    }

    @Test fun jsonRoundTripEmpty() {
        assertEquals("[]", M.toJson(emptyList()))
        assertEquals(emptyList<ShortcutBindings.Binding>(), M.fromJson("[]"))
    }

    @Test fun jsonDegradesToEmptyOnGarbage() {
        assertEquals(emptyList<ShortcutBindings.Binding>(), M.fromJson(null))
        assertEquals(emptyList<ShortcutBindings.Binding>(), M.fromJson(""))
        assertEquals(emptyList<ShortcutBindings.Binding>(), M.fromJson("  "))
        assertEquals(emptyList<ShortcutBindings.Binding>(), M.fromJson("not json"))
        assertEquals(emptyList<ShortcutBindings.Binding>(), M.fromJson("{\"mods\":4}"))
        assertEquals(emptyList<ShortcutBindings.Binding>(), M.fromJson("[{]"))
    }

    @Test fun jsonKeepsValidEntriesAroundBrokenOnes() {
        // A truncated or half-broken tail must not discard earlier bindings.
        val text = """[{"mods":4,"key":40,"action":"lock_desktop"},{"mods":"""
        assertEquals(
            listOf(ShortcutBindings.Binding(M.MOD_ALT, KEY_L, "lock_desktop")),
            M.fromJson(text)
        )
    }

    @Test fun jsonSkipsEntriesMissingFields() {
        val text =
            """[{"mods":4,"key":40},{"mods":8,"key":39,"action":"open_settings"}]"""
        assertEquals(
            listOf(ShortcutBindings.Binding(M.MOD_META, KEY_K, "open_settings")),
            M.fromJson(text)
        )
    }

    @Test fun jsonSkipsEntriesWithEmptyAction() {
        val text = """[{"mods":4,"key":40,"action":""},{"mods":4,"key":41,"action":"open_music"}]"""
        assertEquals(
            listOf(ShortcutBindings.Binding(M.MOD_ALT, 41, "open_music")),
            M.fromJson(text)
        )
    }

    @Test fun jsonToleratesWhitespaceAndExtraFields() {
        val text =
            """[ {"mods" : 4 , "extra":true, "key":40, "action":"lock_desktop"} ]"""
        assertEquals(
            listOf(ShortcutBindings.Binding(M.MOD_ALT, KEY_L, "lock_desktop")),
            M.fromJson(text)
        )
    }

    @Test fun jsonNormalizesModsOnRead() {
        // Stored dirty bits (e.g. a hand-edited file) come back canonical.
        val text = """[{"mods":16404,"key":40,"action":"lock_desktop"}]"""
        assertEquals(
            listOf(ShortcutBindings.Binding(M.MOD_ALT, KEY_L, "lock_desktop")),
            M.fromJson(text)
        )
    }

    @Test fun jsonEscapingRoundTrip() {
        // Action ids come from the catalog, but the format must survive
        // quotes/backslashes anyway (defense against hand-edited storage).
        val bindings = listOf(
            ShortcutBindings.Binding(M.MOD_ALT, KEY_L, "a\"b\\c")
        )
        assertEquals(bindings, M.fromJson(M.toJson(bindings)))
    }

    @Test fun jsonNegativeModsNormalizeToCanonicalBits() {
        val text = """[{"mods":-1,"key":40,"action":"lock_desktop"}]"""
        // normalize(-1) keeps all four known bits.
        assertEquals(
            listOf(
                ShortcutBindings.Binding(
                    M.MOD_SHIFT or M.MOD_CTRL or M.MOD_ALT or M.MOD_META,
                    KEY_L, "lock_desktop"
                )
            ),
            M.fromJson(text)
        )
    }

    // --- catalog sanity ---

    @Test fun catalogIdsAreUnique() {
        val ids = ShortcutBindings.ShortcutAction.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun fromIdResolvesCatalogIds() {
        for (action in ShortcutBindings.ShortcutAction.entries)
            assertEquals(action, ShortcutBindings.ShortcutAction.fromId(action.id))
        assertNull(ShortcutBindings.ShortcutAction.fromId("nope"))
    }
}

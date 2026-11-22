package cu.axel.smartdock.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the keyboard-modifier state machine extracted from
 * DockService.onKeyEvent, covering the two regression-prone acordes:
 *
 * - AltGr firing shortcuts (#167): AltGr+Q must deliver "@" without opening
 *   quick settings, AltGr+L must not lock the desktop. isAltGrPassThrough
 *   returns true for those chords so the dock hands them back untouched.
 * - Meta+X opening the app menu on Meta's release (#267): when Meta is held as
 *   a modifier, its release must not toggle the app menu. MetaMenuTracker
 *   records that and exposes usedAsModifier.
 *
 * Pure JUnit (no android.jar): the KeyEvent constant values live in
 * KeyboardModifiers as hardcoded Ints.
 */
class KeyboardModifiersTest {

    // --- isAltGrPassThrough (#167) ---

    @Test fun altGrViaMetaAltRightFlagPassesThrough() {
        // AltGr+Q on a layout where it produces "@": KEY_UP with the
        // META_ALT_RIGHT_ON flag set. Must pass through (no quick settings).
        assertTrue(
            KeyboardModifiers.isAltGrPassThrough(
                action = KeyboardModifiers.ACTION_UP,
                metaState = KeyboardModifiers.META_ALT_RIGHT_ON,
                isAltPressed = false,
                isCtrlPressed = false
            )
        )
    }

    @Test fun altGrViaCtrlPlusAltPassesThrough() {
        // Some IMEs deliver AltGr as Ctrl+Alt on KEY_UP.
        assertTrue(
            KeyboardModifiers.isAltGrPassThrough(
                action = KeyboardModifiers.ACTION_UP,
                metaState = 0,
                isAltPressed = true,
                isCtrlPressed = true
            )
        )
    }

    @Test fun plainLeftAltDoesNotPassThrough() {
        // A real Alt shortcut (left Alt, no Ctrl): must NOT pass through, so
        // Alt+L locks the desktop as intended.
        assertFalse(
            KeyboardModifiers.isAltGrPassThrough(
                action = KeyboardModifiers.ACTION_UP,
                metaState = 0,
                isAltPressed = true,
                isCtrlPressed = false
            )
        )
    }

    @Test fun ctrlAloneDoesNotPassThrough() {
        assertFalse(
            KeyboardModifiers.isAltGrPassThrough(
                action = KeyboardModifiers.ACTION_UP,
                metaState = 0,
                isAltPressed = false,
                isCtrlPressed = true
            )
        )
    }

    @Test fun altGrOnlyMattersOnKeyUp() {
        // The chord is detected on KEY_UP only; KEY_DOWN must not swallow the
        // event (the character is delivered on UP anyway).
        assertFalse(
            KeyboardModifiers.isAltGrPassThrough(
                action = KeyboardModifiers.ACTION_DOWN,
                metaState = KeyboardModifiers.META_ALT_RIGHT_ON,
                isAltPressed = false,
                isCtrlPressed = false
            )
        )
    }

    @Test fun noModifiersDoesNotPassThrough() {
        assertFalse(
            KeyboardModifiers.isAltGrPassThrough(
                action = KeyboardModifiers.ACTION_UP,
                metaState = 0,
                isAltPressed = false,
                isCtrlPressed = false
            )
        )
    }

    // --- MetaMenuTracker (#267) ---

    @Test fun metaDownAloneArmsTracker() {
        val t = MetaMenuTracker()
        t.onKey(KeyboardModifiers.ACTION_DOWN, KeyboardModifiers.KEYCODE_META_LEFT, repeatCount = 0)
        assertTrue(t.metaLeftDown)
        assertFalse(t.usedAsModifier)
    }

    @Test fun metaDownThenUpWithNoOtherKeyDoesNotMarkModifier() {
        // Meta pressed and released alone → the menu SHOULD open, so
        // usedAsModifier must stay false.
        val t = MetaMenuTracker()
        t.onKey(KeyboardModifiers.ACTION_DOWN, KeyboardModifiers.KEYCODE_META_LEFT, 0)
        t.onKey(KeyboardModifiers.ACTION_UP, KeyboardModifiers.KEYCODE_META_LEFT)
        assertFalse(t.usedAsModifier)
        assertFalse(t.metaLeftDown)
    }

    @Test fun metaHeldWhileOtherKeyDownMarksModifier() {
        // Meta+X: Meta is held, another key goes down → used as a modifier.
        val t = MetaMenuTracker()
        t.onKey(KeyboardModifiers.ACTION_DOWN, KeyboardModifiers.KEYCODE_META_LEFT, 0)
        t.onKey(KeyboardModifiers.ACTION_DOWN, 51 /* KEYCODE_Q */, 0) // some non-Meta key
        assertTrue(t.usedAsModifier)
        assertTrue(t.metaLeftDown)
    }

    @Test fun metaReleaseAfterModifierUseKeepsFlag() {
        // Meta+X then Meta UP: usedAsModifier stays true → on Meta UP the dock
        // reads it and does NOT open the app menu (#267).
        val t = MetaMenuTracker()
        t.onKey(KeyboardModifiers.ACTION_DOWN, KeyboardModifiers.KEYCODE_META_LEFT, 0)
        t.onKey(KeyboardModifiers.ACTION_DOWN, 51 /* Q */, 0)
        t.onKey(KeyboardModifiers.ACTION_UP, KeyboardModifiers.KEYCODE_META_LEFT)
        assertTrue(t.usedAsModifier)
        assertFalse(t.metaLeftDown)
    }

    @Test fun metaAutoRepeatDoesNotReArmTracker() {
        // A Meta auto-repeat arrives with repeatCount > 0; it must not reset
        // usedAsModifier mid-chord (otherwise Meta+X, hold, repeat would clear
        // the flag and re-open the menu on release).
        val t = MetaMenuTracker()
        t.onKey(KeyboardModifiers.ACTION_DOWN, KeyboardModifiers.KEYCODE_META_LEFT, 0)
        t.onKey(KeyboardModifiers.ACTION_DOWN, 51 /* Q */, 0) // usedAsModifier = true
        t.onKey(KeyboardModifiers.ACTION_DOWN, KeyboardModifiers.KEYCODE_META_LEFT, 3) // repeat
        assertTrue(t.usedAsModifier)
        assertTrue(t.metaLeftDown)
    }

    @Test fun metaRepeatAloneDoesNotArm() {
        // A repeat event without a preceding initial DOWN must not arm the
        // tracker (defensive: should never happen, but must not crash or arm).
        val t = MetaMenuTracker()
        t.onKey(KeyboardModifiers.ACTION_DOWN, KeyboardModifiers.KEYCODE_META_LEFT, 5)
        assertFalse(t.metaLeftDown)
        assertFalse(t.usedAsModifier)
    }

    @Test fun nonMetaKeyWithoutMetaDownDoesNothing() {
        val t = MetaMenuTracker()
        t.onKey(KeyboardModifiers.ACTION_DOWN, 51 /* Q */, 0)
        assertFalse(t.metaLeftDown)
        assertFalse(t.usedAsModifier)
    }

    @Test fun metaDownThenAltGrStyleChordStillMarksModifier() {
        // AltGr+L (#167) is handled by isAltGrPassThrough, but if Meta happens
        // to be held too, the tracker must still record modifier use so Meta's
        // later release does not open the menu.
        val t = MetaMenuTracker()
        t.onKey(KeyboardModifiers.ACTION_DOWN, KeyboardModifiers.KEYCODE_META_LEFT, 0)
        t.onKey(KeyboardModifiers.ACTION_DOWN, 46 /* KEYCODE_L */, 0)
        assertTrue(t.usedAsModifier)
    }
}

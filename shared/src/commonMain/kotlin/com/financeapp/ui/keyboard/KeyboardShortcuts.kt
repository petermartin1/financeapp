package com.financeapp.ui.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*

/**
 * Keyboard shortcut definition
 */
data class KeyboardShortcut(
    val key: Key,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val meta: Boolean = false,
    val description: String,
    val category: ShortcutCategory,
    val action: () -> Unit
)

/**
 * Shortcut categories for organization
 */
enum class ShortcutCategory(val displayName: String) {
    NAVIGATION("Navigation"),
    ACTIONS("Actions"),
    EDITING("Editing"),
    SEARCH("Search & Filter"),
    VIEWS("Views"),
    GENERAL("General")
}

/**
 * Global keyboard shortcuts registry
 */
object KeyboardShortcutsRegistry {
    private val shortcuts = java.util.concurrent.ConcurrentHashMap<String, KeyboardShortcut>()

    fun register(id: String, shortcut: KeyboardShortcut) {
        shortcuts[id] = shortcut
    }

    fun unregister(id: String) {
        shortcuts.remove(id)
    }

    fun getAllShortcuts(): List<KeyboardShortcut> {
        return shortcuts.values.toList()
    }

    fun getShortcutsByCategory(category: ShortcutCategory): List<KeyboardShortcut> {
        return shortcuts.values.filter { it.category == category }
    }
}

/**
 * Modifier to add keyboard shortcut handling
 */
fun Modifier.handleKeyboardShortcuts(
    shortcuts: List<KeyboardShortcut>
): Modifier = this.onKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false

    shortcuts.forEach { shortcut ->
        if (matchesShortcut(event, shortcut)) {
            shortcut.action()
            return@onKeyEvent true
        }
    }
    false
}

/**
 * Check if a key event matches a shortcut
 */
private fun matchesShortcut(event: KeyEvent, shortcut: KeyboardShortcut): Boolean {
    val ctrlOrMeta = event.isCtrlPressed || event.isMetaPressed

    return event.key == shortcut.key &&
            (if (shortcut.ctrl || shortcut.meta) ctrlOrMeta else !ctrlOrMeta) &&
            event.isShiftPressed == shortcut.shift &&
            event.isAltPressed == shortcut.alt
}

/**
 * Common keyboard shortcuts builder
 */
class ShortcutBuilder {
    private val shortcuts = mutableListOf<KeyboardShortcut>()

    fun shortcut(
        key: Key,
        description: String,
        category: ShortcutCategory = ShortcutCategory.GENERAL,
        ctrl: Boolean = false,
        shift: Boolean = false,
        alt: Boolean = false,
        meta: Boolean = false,
        action: () -> Unit
    ) {
        shortcuts.add(
            KeyboardShortcut(
                key = key,
                ctrl = ctrl,
                shift = shift,
                alt = alt,
                meta = meta,
                description = description,
                category = category,
                action = action
            )
        )
    }

    fun ctrlShortcut(key: Key, description: String, category: ShortcutCategory, action: () -> Unit) {
        shortcut(key, description, category, ctrl = true, action = action)
    }

    fun cmdShortcut(key: Key, description: String, category: ShortcutCategory, action: () -> Unit) {
        shortcut(key, description, category, meta = true, action = action)
    }

    fun ctrlShiftShortcut(key: Key, description: String, category: ShortcutCategory, action: () -> Unit) {
        shortcut(key, description, category, ctrl = true, shift = true, action = action)
    }

    fun build(): List<KeyboardShortcut> = shortcuts.toList()
}

/**
 * Helper to create shortcuts DSL-style
 */
fun buildShortcuts(block: ShortcutBuilder.() -> Unit): List<KeyboardShortcut> {
    return ShortcutBuilder().apply(block).build()
}

/**
 * Format shortcut for display (e.g., "Ctrl+N" or "Cmd+N")
 */
fun KeyboardShortcut.formatForDisplay(useMacStyle: Boolean = true): String {
    val parts = mutableListOf<String>()

    if (ctrl || meta) {
        parts.add(if (useMacStyle) "⌘" else "Ctrl")
    }
    if (shift) {
        parts.add(if (useMacStyle) "⇧" else "Shift")
    }
    if (alt) {
        parts.add(if (useMacStyle) "⌥" else "Alt")
    }

    parts.add(formatKeyName(key))

    return parts.joinToString(if (useMacStyle) "" else "+")
}

/**
 * Format key name for display
 */
private fun formatKeyName(key: Key): String {
    return when (key) {
        Key.Spacebar -> "Space"
        Key.Escape -> "Esc"
        Key.Backspace -> "⌫"
        Key.Enter -> "↵"
        Key.Tab -> "⇥"
        Key.DirectionUp -> "↑"
        Key.DirectionDown -> "↓"
        Key.DirectionLeft -> "←"
        Key.DirectionRight -> "→"
        Key.Delete -> "Del"
        Key.PageUp -> "PgUp"
        Key.PageDown -> "PgDn"
        Key.MoveHome -> "Home"
        Key.MoveEnd -> "End"
        Key.F1 -> "F1"
        Key.F2 -> "F2"
        Key.F3 -> "F3"
        Key.F4 -> "F4"
        Key.F5 -> "F5"
        Key.F6 -> "F6"
        Key.F7 -> "F7"
        Key.F8 -> "F8"
        Key.F9 -> "F9"
        Key.F10 -> "F10"
        Key.F11 -> "F11"
        Key.F12 -> "F12"
        Key.Insert -> "Ins"
        else -> {
            val code = key.keyCode.toInt()
            val ch = code.toChar()
            // Only convert to char for printable ASCII range
            if (code in 32..126) ch.uppercase() else "Key($code)"
        }
    }
}

/**
 * Common application shortcuts
 */
object CommonShortcuts {
    fun newItem(action: () -> Unit) = KeyboardShortcut(
        key = Key.N,
        ctrl = true,
        meta = true,
        description = "New Item",
        category = ShortcutCategory.ACTIONS,
        action = action
    )

    fun search(action: () -> Unit) = KeyboardShortcut(
        key = Key.F,
        ctrl = true,
        meta = true,
        description = "Search",
        category = ShortcutCategory.SEARCH,
        action = action
    )

    fun save(action: () -> Unit) = KeyboardShortcut(
        key = Key.S,
        ctrl = true,
        meta = true,
        description = "Save",
        category = ShortcutCategory.ACTIONS,
        action = action
    )

    fun delete(action: () -> Unit) = KeyboardShortcut(
        key = Key.Delete,
        description = "Delete",
        category = ShortcutCategory.EDITING,
        action = action
    )

    fun escape(action: () -> Unit) = KeyboardShortcut(
        key = Key.Escape,
        description = "Close/Cancel",
        category = ShortcutCategory.GENERAL,
        action = action
    )

    fun help(action: () -> Unit) = KeyboardShortcut(
        key = Key.Slash,
        ctrl = true,
        meta = true,
        shift = true,
        description = "Show Shortcuts",
        category = ShortcutCategory.GENERAL,
        action = action
    )

    fun refresh(action: () -> Unit) = KeyboardShortcut(
        key = Key.R,
        ctrl = true,
        meta = true,
        description = "Refresh",
        category = ShortcutCategory.GENERAL,
        action = action
    )
}

/**
 * Navigation shortcuts for numbered items
 */
fun navigationShortcut(number: Int, description: String, action: () -> Unit): KeyboardShortcut {
    val key = when (number) {
        0 -> Key.Zero
        1 -> Key.One
        2 -> Key.Two
        3 -> Key.Three
        4 -> Key.Four
        5 -> Key.Five
        6 -> Key.Six
        7 -> Key.Seven
        8 -> Key.Eight
        9 -> Key.Nine
        else -> throw IllegalArgumentException("Number must be 0-9")
    }

    return KeyboardShortcut(
        key = key,
        ctrl = true,
        meta = true,
        description = description,
        category = ShortcutCategory.NAVIGATION,
        action = action
    )
}

package com.shellbridge.app.terminal

import androidx.compose.ui.graphics.Color

data class TerminalChar(
    val char: Char = ' ',
    val foreground: Color = Color.Unspecified,
    val background: Color = Color.Unspecified,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val reverse: Boolean = false
)

data class TerminalCursor(
    val row: Int = 0,
    val col: Int = 0,
    val visible: Boolean = true
)

class TerminalBuffer(
    val rows: Int = 24,
    val cols: Int = 80
) {
    val lines: MutableList<MutableList<TerminalChar>> = MutableList(rows) {
        MutableList(cols) { TerminalChar() }
    }
    var cursor = TerminalCursor()
        private set
    private var scrollRegionTop = 0
    private var scrollRegionBottom = rows - 1
    private var currentForeground: Color = Color.Unspecified
    private var currentBackground: Color = Color.Unspecified
    private var bold = false
    private var italic = false
    private var underline = false
    private var reverse = false
    private val savedCursor = mutableListOf<TerminalCursor>()
    private var pendingEscapeBuffer: StringBuilder? = null
    private var lastCharWasCR = false
    private var wrapPending = false

    fun resize(newRows: Int, newCols: Int) {
        val newLines = MutableList(newRows) { row ->
            MutableList(newCols) { col ->
                if (row < lines.size && col < lines[row].size) lines[row][col] else TerminalChar()
            }
        }
        lines.clear()
        lines.addAll(newLines)
        scrollRegionTop = 0
        scrollRegionBottom = newRows - 1
    }

    fun write(text: String) {
        var i = 0
        val fullText = if (pendingEscapeBuffer != null) {
            val pending = pendingEscapeBuffer.toString()
            pendingEscapeBuffer = null
            pending + text
        } else {
            text
        }

        if (lastCharWasCR) {
            lastCharWasCR = false
            if (fullText.isNotEmpty() && fullText[0] == '\n') {
                i = 1
            }
        }

        while (i < fullText.length) {
            val c = fullText[i]
            when {
                c == '\u001b' -> {
                    val consumed = parseEscapeSequence(fullText, i)
                    if (consumed > 0) {
                        i += consumed
                    } else {
                        pendingEscapeBuffer = StringBuilder(fullText.substring(i))
                        i = fullText.length
                    }
                }
                c == '\r' -> {
                    wrapPending = false
                    cursor = cursor.copy(col = 0)
                    if (i + 1 < fullText.length && fullText[i + 1] == '\n') {
                        i++
                    } else if (i + 1 == fullText.length) {
                        lastCharWasCR = true
                    }
                }
                c == '\n' -> {
                    wrapPending = false
                    moveCursorDown()
                }
                c == '\t' -> {
                    wrapPending = false
                    val nextTab = (cursor.col / 8 + 1) * 8
                    cursor = cursor.copy(col = minOf(nextTab, cols - 1))
                }
                c == '\b' || c == '\u0008' -> {
                    wrapPending = false
                    if (cursor.col > 0) cursor = cursor.copy(col = cursor.col - 1)
                }
                c == '\u0007' -> { /* bell - ignore */ }
                c == '\u0000' -> { /* NUL - ignore */ }
                else -> putChar(c)
            }
            i++
        }
    }

    private fun putChar(c: Char) {
        if (wrapPending) {
            wrapPending = false
            cursor = cursor.copy(col = 0)
            moveCursorDown()
        }

        if (cursor.row in lines.indices && cursor.col in lines[cursor.row].indices) {
            val fg = if (reverse) currentBackground else currentForeground
            val bg = if (reverse) currentForeground else currentBackground
            lines[cursor.row][cursor.col] = TerminalChar(
                char = c,
                foreground = fg,
                background = bg,
                bold = bold,
                italic = italic,
                underline = underline
            )
        }
        if (cursor.col < cols - 1) {
            cursor = cursor.copy(col = cursor.col + 1)
        } else {
            wrapPending = true
        }
    }

    private fun moveCursorDown() {
        if (cursor.row == scrollRegionBottom) {
            scrollUp()
        } else if (cursor.row < rows - 1) {
            cursor = cursor.copy(row = cursor.row + 1)
        }
    }

    private fun scrollUp() {
        lines.removeAt(scrollRegionTop)
        lines.add(scrollRegionBottom, MutableList(cols) { TerminalChar() })
    }

    private fun parseEscapeSequence(text: String, start: Int): Int {
        if (start + 1 >= text.length) return 0
        val next = text[start + 1]
        wrapPending = false
        return when (next) {
            '[' -> parseCSI(text, start)
            ']' -> parseOSC(text, start)
            '(' -> 2 // charset selection
            ')' -> 2
            '#' -> 2
            '7' -> { savedCursor.add(cursor); 2 }
            '8' -> { if (savedCursor.isNotEmpty()) cursor = savedCursor.removeLast(); 2 }
            'D' -> { moveCursorDown(); 2 }
            'M' -> { moveCursorUp(); 2 }
            'c' -> { reset(); 2 }
            else -> {
                // Unknown escape: consume at least 2 chars to prevent infinite loop
                // If next char is a letter, consume just that pair
                // Otherwise consume 1 more character (skip garbage)
                if (next.isLetter()) 2 else minOf(3, text.length - start)
            }
        }
    }

    private fun parseCSI(text: String, start: Int): Int {
        var i = start + 2
        val params = mutableListOf<Int>()
        var currentParam = 0
        var hasParam = false

        // Handle private mode sequences: \x1b[?... or \x1b[>...
        if (i < text.length && (text[i] == '?' || text[i] == '>' || text[i] == '!')) {
            i++ // skip the private mode prefix
            // Skip digits and semicolons until we hit the final command letter
            while (i < text.length) {
                val c = text[i]
                if (c.isLetterOrDigit() || c == ';') {
                    i++
                } else {
                    // This is the final command letter - consume it
                    i++
                    return i - start
                }
            }
            return i - start
        }

        while (i < text.length) {
            val c = text[i]
            when {
                c.isDigit() -> {
                    currentParam = currentParam * 10 + (c - '0')
                    hasParam = true
                    i++
                }
                c == ';' -> {
                    params.add(if (hasParam) currentParam else 0)
                    currentParam = 0
                    hasParam = false
                    i++
                }
                else -> {
                    if (hasParam) params.add(currentParam)
                    executeCSI(c, params)
                    return i - start + 1
                }
            }
        }
        return 0
    }

    private fun executeCSI(command: Char, params: List<Int>) {
        wrapPending = false
        val p = params.ifEmpty { listOf(0) }
        when (command) {
            'A' -> { // Cursor Up
                val n = p[0].coerceAtLeast(1)
                cursor = cursor.copy(row = (cursor.row - n).coerceAtLeast(0))
            }
            'B' -> { // Cursor Down
                val n = p[0].coerceAtLeast(1)
                cursor = cursor.copy(row = (cursor.row + n).coerceAtMost(rows - 1))
            }
            'C' -> { // Cursor Forward
                val n = p[0].coerceAtLeast(1)
                cursor = cursor.copy(col = (cursor.col + n).coerceAtMost(cols - 1))
            }
            'D' -> { // Cursor Back
                val n = p[0].coerceAtLeast(1)
                cursor = cursor.copy(col = (cursor.col - n).coerceAtLeast(0))
            }
            'E' -> { // Cursor Next Line
                val n = p[0].coerceAtLeast(1)
                cursor = cursor.copy(row = (cursor.row + n).coerceAtMost(rows - 1), col = 0)
            }
            'F' -> { // Cursor Previous Line
                val n = p[0].coerceAtLeast(1)
                cursor = cursor.copy(row = (cursor.row - n).coerceAtLeast(0), col = 0)
            }
            'G' -> { // Cursor Horizontal Absolute
                val n = p[0].coerceAtLeast(1)
                cursor = cursor.copy(col = (n - 1).coerceIn(0, cols - 1))
            }
            'H', 'f' -> { // Cursor Position
                val row = (if (p.size >= 1) p[0] else 1).coerceIn(1, rows)
                val col = (if (p.size >= 2) p[1] else 1).coerceIn(1, cols)
                cursor = cursor.copy(row = row - 1, col = col - 1)
            }
            'J' -> { // Erase in Display
                when (p[0]) {
                    0 -> { // Clear from cursor to end
                        for (c in cursor.col until cols) lines[cursor.row][c] = TerminalChar()
                        for (r in (cursor.row + 1) until rows) {
                            for (c in 0 until cols) lines[r][c] = TerminalChar()
                        }
                    }
                    1 -> { // Clear from start to cursor
                        for (r in 0 until cursor.row) {
                            for (c in 0 until cols) lines[r][c] = TerminalChar()
                        }
                        for (c in 0..cursor.col) lines[cursor.row][c] = TerminalChar()
                    }
                    2, 3 -> { // Clear entire screen
                        for (r in 0 until rows) {
                            for (c in 0 until cols) lines[r][c] = TerminalChar()
                        }
                        cursor = TerminalCursor()
                    }
                }
            }
            'K' -> { // Erase in Line
                when (p[0]) {
                    0 -> for (c in cursor.col until cols) lines[cursor.row][c] = TerminalChar()
                    1 -> for (c in 0..cursor.col) lines[cursor.row][c] = TerminalChar()
                    2 -> for (c in 0 until cols) lines[cursor.row][c] = TerminalChar()
                }
            }
            'L' -> { // Insert Lines
                val n = p[0].coerceAtLeast(1)
                for (i in 0 until n) {
                    lines.removeAt(scrollRegionBottom)
                    lines.add(cursor.row, MutableList(cols) { TerminalChar() })
                }
            }
            'M' -> { // Delete Lines
                val n = p[0].coerceAtLeast(1)
                for (i in 0 until n) {
                    if (cursor.row < lines.size) lines.removeAt(cursor.row)
                    lines.add(scrollRegionBottom, MutableList(cols) { TerminalChar() })
                }
            }
            'P' -> { // Delete Characters
                val n = p[0].coerceAtLeast(1)
                val row = lines[cursor.row]
                for (i in cursor.col until (cols - n).coerceAtLeast(0)) {
                    row[i] = row.getOrElse(i + n) { TerminalChar() }
                }
                for (i in (cols - n).coerceAtLeast(0) until cols) row[i] = TerminalChar()
            }
            'S' -> { // Scroll Up
                val n = p[0].coerceAtLeast(1)
                for (i in 0 until n) scrollUp()
            }
            'T' -> { // Scroll Down (insert blank lines at top, push content down)
                val n = p[0].coerceAtLeast(1)
                for (i in 0 until n) {
                    if (cursor.row <= scrollRegionBottom) {
                        lines.removeAt(scrollRegionBottom)
                        lines.add(cursor.row, MutableList(cols) { TerminalChar() })
                    }
                }
            }
            'X' -> { // Erase Characters
                val n = p[0].coerceAtLeast(1)
                for (c in cursor.col until (cursor.col + n).coerceAtMost(cols)) {
                    lines[cursor.row][c] = TerminalChar()
                }
            }
            'd' -> { // Vertical Position Absolute
                val n = p[0].coerceIn(1, rows)
                cursor = cursor.copy(row = n - 1)
            }
            'm' -> { // SGR - Select Graphic Rendition
                applySGR(p)
            }
            'r' -> { // Set Scrolling Region
                scrollRegionTop = (if (p.size >= 1) p[0] - 1 else 0).coerceIn(0, rows - 1)
                scrollRegionBottom = (if (p.size >= 2) p[1] - 1 else rows - 1).coerceIn(0, rows - 1)
                if (scrollRegionTop > scrollRegionBottom) {
                    val temp = scrollRegionTop
                    scrollRegionTop = scrollRegionBottom
                    scrollRegionBottom = temp
                }
                cursor = TerminalCursor()
            }
            's' -> savedCursor.add(cursor)
            'u' -> { if (savedCursor.isNotEmpty()) cursor = savedCursor.removeLast() }
            'h', 'l' -> { /* Mode set/reset - ignore for now */ }
            'n' -> { /* Device status report - ignore */ }
            'c' -> { /* Device attributes - ignore */ }
        }
    }

    private fun applySGR(params: List<Int>) {
        if (params.isEmpty()) {
            resetAttributes()
            return
        }
        var i = 0
        while (i < params.size) {
            when (params[i]) {
                0 -> resetAttributes()
                1 -> bold = true
                2 -> { /* dim - ignore */ }
                3 -> italic = true
                4 -> underline = true
                5, 6 -> { /* blink - ignore */ }
                7 -> reverse = true
                8 -> { /* hidden - ignore */ }
                9 -> { /* strikethrough - ignore */ }
                22 -> bold = false
                23 -> italic = false
                24 -> underline = false
                27 -> reverse = false
                30 -> currentForeground = Color.Black
                31 -> currentForeground = Color(0xFFCC241D)
                32 -> currentForeground = Color(0xFF98971A)
                33 -> currentForeground = Color(0xFFD79921)
                34 -> currentForeground = Color(0xFF458588)
                35 -> currentForeground = Color(0xFFB16286)
                36 -> currentForeground = Color(0xFF689D6A)
                37 -> currentForeground = Color(0xFFA89984)
                38 -> {
                    if (i + 1 < params.size) {
                        when (params[i + 1]) {
                            5 -> {
                                if (i + 2 < params.size) {
                                    currentForeground = ansi256ToColor(params[i + 2])
                                    i += 2
                                }
                            }
                            2 -> {
                                if (i + 3 < params.size) {
                                    currentForeground = Color(params[i + 2], params[i + 3], params[i + 4])
                                    i += 3
                                }
                            }
                        }
                    }
                }
                39 -> currentForeground = Color.Unspecified
                40 -> currentBackground = Color.Black
                41 -> currentBackground = Color(0xFFCC241D)
                42 -> currentBackground = Color(0xFF98971A)
                43 -> currentBackground = Color(0xFFD79921)
                44 -> currentBackground = Color(0xFF458588)
                45 -> currentBackground = Color(0xFFB16286)
                46 -> currentBackground = Color(0xFF689D6A)
                47 -> currentBackground = Color(0xFFA89984)
                48 -> {
                    if (i + 1 < params.size) {
                        when (params[i + 1]) {
                            5 -> {
                                if (i + 2 < params.size) {
                                    currentBackground = ansi256ToColor(params[i + 2])
                                    i += 2
                                }
                            }
                            2 -> {
                                if (i + 3 < params.size) {
                                    currentBackground = Color(params[i + 2], params[i + 3], params[i + 4])
                                    i += 3
                                }
                            }
                        }
                    }
                }
                49 -> currentBackground = Color.Unspecified
                90 -> currentForeground = Color(0xFF5C6370)
                91 -> currentForeground = Color(0xFFE06C75)
                92 -> currentForeground = Color(0xFF98C379)
                93 -> currentForeground = Color(0xFFE5C07B)
                94 -> currentForeground = Color(0xFF61AFEF)
                95 -> currentForeground = Color(0xFFC678DD)
                96 -> currentForeground = Color(0xFF56B6C2)
                97 -> currentForeground = Color(0xFFFFFFFF)
                100 -> currentBackground = Color(0xFF5C6370)
                101 -> currentBackground = Color(0xFFE06C75)
                102 -> currentBackground = Color(0xFF98C379)
                103 -> currentBackground = Color(0xFFE5C07B)
                104 -> currentBackground = Color(0xFF61AFEF)
                105 -> currentBackground = Color(0xFFC678DD)
                106 -> currentBackground = Color(0xFF56B6C2)
                107 -> currentBackground = Color(0xFFFFFFFF)
            }
            i++
        }
    }

    private fun ansi256ToColor(n: Int): Color {
        if (n < 16) {
            val colors = intArrayOf(
                0x000000, 0xCC241D, 0x98971A, 0xD79921,
                0x458588, 0xB16286, 0x689D6A, 0xA89984,
                0x928374, 0xFB4934, 0xB8BB26, 0xFABD2F,
                0x83A598, 0xD3869B, 0x8EC07C, 0xEBDBB2
            )
            val c = colors.getOrElse(n) { 0xFFFFFF }
            return Color((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
        }
        if (n < 232) {
            val idx = n - 16
            val r = idx / 36
            val g = (idx % 36) / 6
            val b = idx % 6
            return Color(
                if (r == 0) 0 else 55 + r * 40,
                if (g == 0) 0 else 55 + g * 40,
                if (b == 0) 0 else 55 + b * 40
            )
        }
        val gray = 8 + (n - 232) * 10
        return Color(gray, gray, gray)
    }

    private fun moveCursorUp() {
        if (cursor.row > 0) {
            cursor = cursor.copy(row = cursor.row - 1)
        }
    }

    private fun parseOSC(text: String, start: Int): Int {
        var i = start + 2
        while (i < text.length) {
            if (text[i] == '\u0007') return i - start + 1
            if (text[i] == '\u001b' && i + 1 < text.length && text[i + 1] == '\\') return i - start + 2
            i++
        }
        return 0
    }

    private fun resetAttributes() {
        currentForeground = Color.Unspecified
        currentBackground = Color.Unspecified
        bold = false
        italic = false
        underline = false
        reverse = false
    }

    private fun reset() {
        for (r in 0 until rows) {
            for (c in 0 until cols) lines[r][c] = TerminalChar()
        }
        cursor = TerminalCursor()
        scrollRegionTop = 0
        scrollRegionBottom = rows - 1
        resetAttributes()
        savedCursor.clear()
    }
}

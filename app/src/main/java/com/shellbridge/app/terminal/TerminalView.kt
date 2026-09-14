package com.shellbridge.app.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.shellbridge.app.ui.theme.currentPalette

@Composable
fun TerminalView(
    terminal: TerminalBuffer,
    modifier: Modifier = Modifier,
    fontSize: Int = 14,
    onUserInput: (String) -> Unit
) {
    val palette = remember { derivedStateOf { currentPalette() } }
    val textMeasurer = rememberTextMeasurer()
    val fontFamily = FontFamily.Monospace

    var scrollOffset by remember { mutableFloatStateOf(0f) }
    var pinchScale by remember { mutableFloatStateOf(1f) }

    val charWidth = remember(fontSize) {
        val measured = textMeasurer.measure(
            "W",
            style = TextStyle(fontSize = fontSize.sp, fontFamily = fontFamily)
        )
        measured.size.width.toFloat()
    }
    val charHeight = remember(fontSize) { fontSize.sp.value * 1.3f }

    LaunchedEffect(terminal.cursor.row, terminal.cursor.col) {
        val maxScroll = (terminal.rows * charHeight) - 400f
        if (maxScroll > 0) {
            val cursorY = terminal.cursor.row * charHeight
            if (cursorY - scrollOffset > 400f - charHeight * 2) {
                scrollOffset = (cursorY - 400f + charHeight * 3).coerceAtLeast(0f)
            } else if (cursorY - scrollOffset < charHeight) {
                scrollOffset = (cursorY - charHeight).coerceAtLeast(0f)
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val col = (offset.x / charWidth.toFloat()).toInt().coerceIn(0, terminal.cols - 1)
                    val row = ((offset.y + scrollOffset) / charHeight.toFloat()).toInt().coerceIn(0, terminal.rows - 1)
                    // Tap positioning could be used for cursor placement
                }
            }
    ) {
        val bg = palette.value.background
        drawRect(color = bg, size = size)

        val visibleRows = (size.height / charHeight).toInt() + 2
        val startRow = (scrollOffset / charHeight).toInt().coerceAtLeast(0)
        val endRow = (startRow + visibleRows).coerceAtMost(terminal.rows)

        for (row in startRow until endRow) {
            val y = row * charHeight - scrollOffset
            drawTerminalRow(
                terminal = terminal,
                row = row,
                x = 0f,
                y = y,
                charWidth = charWidth,
                charHeight = charHeight,
                palette = palette.value,
                textMeasurer = textMeasurer,
                fontFamily = fontFamily,
                fontSize = fontSize
            )
        }

        if (terminal.cursor.visible) {
            val cursorX = terminal.cursor.col.toFloat() * charWidth
            val cursorY = terminal.cursor.row.toFloat() * charHeight - scrollOffset
            drawRect(
                color = palette.value.cursor.copy(alpha = 0.8f),
                topLeft = Offset(cursorX, cursorY),
                size = Size(charWidth, charHeight),
                style = Fill
            )
        }
    }
}

private fun DrawScope.drawTerminalRow(
    terminal: TerminalBuffer,
    row: Int,
    x: Float,
    y: Float,
    charWidth: Float,
    charHeight: Float,
    palette: com.shellbridge.app.ui.theme.TerminalPalette,
    textMeasurer: TextMeasurer,
    fontFamily: FontFamily,
    fontSize: Int
) {
    if (row !in terminal.lines.indices) return
    val line = terminal.lines[row]

    var col = 0
    while (col < line.size) {
        val cell = line[col]
        val char = cell.char

        val bg = cell.background.takeIf { it != Color.Unspecified } ?: palette.background
        val fg = cell.foreground.takeIf { it != Color.Unspecified } ?: palette.foreground

        val startCol = col
        val textBuilder = StringBuilder()
        val startBold = cell.bold
        val startItalic = cell.italic
        val startUnderline = cell.underline
        while (col < line.size &&
            line[col].background == line[startCol].background &&
            line[col].foreground == line[startCol].foreground &&
            line[col].bold == startBold &&
            line[col].italic == startItalic &&
            line[col].underline == startUnderline
        ) {
            textBuilder.append(line[col].char)
            col++
        }

        val blockWidth = (col - startCol) * charWidth
        drawRect(
            color = bg,
            topLeft = Offset(x + startCol * charWidth, y),
            size = Size(blockWidth, charHeight)
        )

        val text = textBuilder.toString().trimEnd()
        if (text.isNotBlank()) {
            val style = TextStyle(
                color = fg,
                fontSize = fontSize.sp,
                fontFamily = fontFamily,
                fontWeight = if (startBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (startItalic) FontStyle.Italic else FontStyle.Normal,
                textDecoration = if (startUnderline) TextDecoration.Underline else null
            )
            val measured = textMeasurer.measure(text, style)
            drawText(
                measured,
                topLeft = Offset(x + startCol * charWidth, y)
            )
        }
    }
}

package com.shellbridge.app.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shellbridge.app.ui.theme.currentPalette

@Composable
fun KeyboardBar(
    modifier: Modifier = Modifier,
    onKey: (String) -> Unit,
    onDisconnect: () -> Unit,
    onToggleTheme: () -> Unit
) {
    val palette = remember { derivedStateOf { currentPalette() } }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.value.surface)
    ) {
        HorizontalDivider(color = palette.value.accent.copy(alpha = 0.3f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KeyboardKey("Esc") { onKey("\u001b") }
            KeyboardKey("Tab") { onKey("\t") }
            KeyboardKey("Ctrl") { onKey("\u0003") }
            KeyboardKey("Alt") { onKey("\u001b") }

            Spacer(modifier = Modifier.width(4.dp))

            KeyboardKey(icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft) { onKey("\u001b[D") }
            KeyboardKey(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight) { onKey("\u001b[C") }
            KeyboardKey(icon = Icons.Filled.KeyboardArrowUp) { onKey("\u001b[A") }
            KeyboardKey(icon = Icons.Filled.KeyboardArrowDown) { onKey("\u001b[B") }

            Spacer(modifier = Modifier.width(4.dp))

            KeyboardKey("F1") { onKey("\u001bOP") }
            KeyboardKey("F2") { onKey("\u001bOQ") }
            KeyboardKey("F3") { onKey("\u001bOR") }
            KeyboardKey("F4") { onKey("\u001bOS") }
            KeyboardKey("F5") { onKey("\u001b[15~") }
            KeyboardKey("F6") { onKey("\u001b[17~") }
            KeyboardKey("F7") { onKey("\u001b[18~") }
            KeyboardKey("F8") { onKey("\u001b[19~") }
            KeyboardKey("F9") { onKey("\u001b[20~") }
            KeyboardKey("F10") { onKey("\u001b[21~") }
            KeyboardKey("F11") { onKey("\u001b[23~") }
            KeyboardKey("F12") { onKey("\u001b[24~") }

            Spacer(modifier = Modifier.width(4.dp))

            KeyboardKey("Paste") { /* paste from clipboard */ }
            KeyboardKey("Theme", onClick = onToggleTheme)
            KeyboardKey("Disconnect", color = palette.value.red, onClick = onDisconnect)
        }
    }
}

@Composable
private fun KeyboardKey(
    text: String = "",
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    color: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    val palette = remember { derivedStateOf { currentPalette() } }
    val bgColor = if (color != Color.Unspecified) color.copy(alpha = 0.2f) else palette.value.surfaceVariant
    val fgColor = if (color != Color.Unspecified) color else palette.value.foreground

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Text(
                text = text,
                color = fgColor,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun ComposeBar(
    modifier: Modifier = Modifier,
    onSend: (String) -> Unit
) {
    val palette = remember { derivedStateOf { currentPalette() } }
    var text by remember { mutableStateOf("") }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.value.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = {
                Text("Type command...", color = palette.value.foreground.copy(alpha = 0.5f))
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = palette.value.foreground,
                unfocusedTextColor = palette.value.foreground,
                cursorColor = palette.value.accent,
                focusedBorderColor = palette.value.accent,
                unfocusedBorderColor = palette.value.surfaceVariant
            ),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        )

        IconButton(
            onClick = {
                if (text.isNotEmpty()) {
                    onSend(text + "\n")
                    text = ""
                }
            }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = palette.value.accent
            )
        }
    }
}

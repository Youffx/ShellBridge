package com.shellbridge.app.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shellbridge.app.ui.theme.TerminalPalette
import com.shellbridge.app.ui.theme.TerminalThemes
import com.shellbridge.app.ui.theme.currentPalette
import com.shellbridge.app.ui.theme.setCurrentPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SSHInputScreen(
    onConnect: (String) -> Unit
) {
    val palette = remember { derivedStateOf { currentPalette() } }
    var sshInput by remember { mutableStateOf("") }
    var showThemeSheet by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.value.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "SHELLBRIDGE",
                color = palette.value.accent,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "SSH TERMINAL",
                color = palette.value.foreground.copy(alpha = 0.5f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            OutlinedTextField(
                value = sshInput,
                onValueChange = { sshInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text("SSH Input", color = palette.value.foreground.copy(alpha = 0.7f))
                },
                placeholder = {
                    Text(
                        "runner@host.trycloudflare.com",
                        color = palette.value.foreground.copy(alpha = 0.3f)
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Terminal,
                        contentDescription = null,
                        tint = palette.value.accent
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = palette.value.foreground,
                    unfocusedTextColor = palette.value.foreground,
                    cursorColor = palette.value.accent,
                    focusedBorderColor = palette.value.accent,
                    unfocusedBorderColor = palette.value.surfaceVariant,
                    focusedLeadingIconColor = palette.value.accent,
                    unfocusedLeadingIconColor = palette.value.foreground.copy(alpha = 0.5f)
                ),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (sshInput.isNotBlank()) {
                        onConnect(sshInput.trim())
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = sshInput.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.value.accent,
                    contentColor = palette.value.background,
                    disabledContainerColor = palette.value.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "CONNECT",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = { showHelp = !showHelp }) {
                    Text(
                        "Help",
                        color = palette.value.foreground.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                TextButton(onClick = { showThemeSheet = true }) {
                    Text(
                        "Theme",
                        color = palette.value.foreground.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
            }

            AnimatedVisibility(visible = showHelp) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = palette.value.surface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Connection Formats:",
                            color = palette.value.accent,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val examples = listOf(
                            "user@hostname",
                            "user@ip -p 2222",
                            "Cloudflare: runner@host.trycloudflare.com"
                        )
                        examples.forEach { example ->
                            Text(
                                "  $example",
                                color = palette.value.foreground.copy(alpha = 0.7f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        if (showThemeSheet) {
            ThemeBottomSheet(
                onDismiss = { showThemeSheet = false },
                onSelect = { palette ->
                    setCurrentPalette(palette)
                    showThemeSheet = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeBottomSheet(
    onDismiss: () -> Unit,
    onSelect: (TerminalPalette) -> Unit
) {
    val palette = remember { derivedStateOf { currentPalette() } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.value.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Select Theme",
                color = palette.value.foreground,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn {
                items(TerminalThemes.allPalettes) { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (theme.name == palette.value.name) theme.accent.copy(alpha = 0.2f)
                                else Color.Transparent
                            )
                            .clickable { onSelect(theme) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(theme.background)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                theme.name,
                                color = palette.value.foreground,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                theme.accent.toHexString(),
                                color = palette.value.foreground.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun Color.toHexString(): String {
    val r = (red * 255).toInt()
    val g = (green * 255).toInt()
    val b = (blue * 255).toInt()
    return String.format("#%02X%02X%02X", r, g, b)
}

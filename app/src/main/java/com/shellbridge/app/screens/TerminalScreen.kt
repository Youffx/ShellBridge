package com.shellbridge.app.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shellbridge.app.ssh.SSHConnection
import com.shellbridge.app.ssh.SSHConnectionParams
import com.shellbridge.app.terminal.ComposeBar
import com.shellbridge.app.terminal.KeyboardBar
import com.shellbridge.app.terminal.TerminalBuffer
import com.shellbridge.app.terminal.TerminalView
import com.shellbridge.app.ui.theme.TerminalPalette
import com.shellbridge.app.ui.theme.TerminalThemes
import com.shellbridge.app.ui.theme.currentPalette
import com.shellbridge.app.ui.theme.setCurrentPalette
import kotlinx.coroutines.delay

@Composable
fun TerminalScreen(
    sshInput: String,
    onDisconnect: () -> Unit
) {
    val palette = remember { derivedStateOf { currentPalette() } }
    val terminal = remember { TerminalBuffer(rows = 24, cols = 80) }
    val sshConnection = remember { SSHConnection() }

    var connectionState by remember { mutableStateOf(ConnectionState.IDLE) }
    var statusText by remember { mutableStateOf("") }
    var showComposeBar by remember { mutableStateOf(false) }
    var showThemePicker by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showPasswordDialog by remember { mutableStateOf(false) }

    val params = remember(sshInput) { parseSSHInput(sshInput) }

    LaunchedEffect(sshInput) {
        connectionState = ConnectionState.CONNECTING
        statusText = if (params.useCloudflare) {
            "Opening tunnel to ${params.host}..."
        } else {
            "Connecting to ${params.host}:${params.port}..."
        }

        sshConnection.onTunnelOutput = { line ->
            statusText = line.take(60)
        }
        sshConnection.onTunnelReady = { port ->
            statusText = "Tunnel ready on port $port, authenticating..."
        }
        sshConnection.onDisconnect = {
            connectionState = ConnectionState.DISCONNECTED
            statusText = "Disconnected"
        }
        sshConnection.onError = { error ->
            errorMessage = error
            showError = true
            connectionState = ConnectionState.ERROR
        }
        sshConnection.onPasswordRequired = {
            statusText = "Password required"
            showPasswordDialog = true
        }

        sshConnection.connect(params, terminal)

        var attempts = 0
        while (attempts < 300 && !sshConnection.isConnected() && connectionState != ConnectionState.ERROR) {
            delay(100)
            attempts++
        }

        if (sshConnection.isConnected()) {
            connectionState = ConnectionState.CONNECTED
            statusText = "Connected"
        } else if (connectionState != ConnectionState.ERROR) {
            connectionState = ConnectionState.ERROR
            errorMessage = "Connection timed out"
            showError = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            sshConnection.disconnect()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.value.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.value.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            when (connectionState) {
                                ConnectionState.CONNECTED -> Color(0xFFA6E3A1)
                                ConnectionState.CONNECTING -> Color(0xFFF9E2AF)
                                else -> Color(0xFFF38BA8)
                            },
                            RoundedCornerShape(4.dp)
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = params.host.take(30),
                        color = palette.value.foreground,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                    if (connectionState != ConnectionState.CONNECTED) {
                        Text(
                            text = statusText,
                            color = palette.value.foreground.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1
                        )
                    }
                }
            }

            Row {
                IconButton(onClick = { showComposeBar = !showComposeBar }) {
                    Icon(
                        Icons.Filled.Keyboard,
                        contentDescription = "Toggle Compose Bar",
                        tint = palette.value.foreground.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = { showThemePicker = true }) {
                    Icon(
                        Icons.Filled.Palette,
                        contentDescription = "Theme",
                        tint = palette.value.foreground.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = {
                    sshConnection.disconnect()
                    onDisconnect()
                }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Disconnect",
                        tint = palette.value.red,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = palette.value.accent.copy(alpha = 0.2f))

        when (connectionState) {
            ConnectionState.CONNECTED -> {
                TerminalView(
                    terminal = terminal,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    onUserInput = { sshConnection.sendInput(it) }
                )
            }
            ConnectionState.CONNECTING -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = palette.value.accent,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            statusText,
                            color = palette.value.foreground.copy(alpha = 0.7f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            else -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = palette.value.red,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Connection Failed",
                            color = palette.value.red,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            errorMessage,
                            color = palette.value.foreground.copy(alpha = 0.7f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { onDisconnect() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.value.accent
                            )
                        ) {
                            Text("Go Back", color = palette.value.background)
                        }
                    }
                }
            }
        }

        if (showComposeBar && connectionState == ConnectionState.CONNECTED) {
            HorizontalDivider(color = palette.value.accent.copy(alpha = 0.2f))
            ComposeBar(onSend = { sshConnection.sendInput(it) })
        }

        if (connectionState == ConnectionState.CONNECTED) {
            KeyboardBar(
                onKey = { sshConnection.sendInput(it) },
                onDisconnect = {
                    sshConnection.disconnect()
                    onDisconnect()
                },
                onToggleTheme = { showThemePicker = true }
            )
        }
    }

    if (showError) {
        AlertDialog(
            onDismissRequest = { showError = false },
            title = {
                Text("Error", color = palette.value.red)
            },
            text = {
                Text(errorMessage, color = palette.value.foreground)
            },
            confirmButton = {
                TextButton(onClick = {
                    showError = false
                    onDisconnect()
                }) {
                    Text("OK", color = palette.value.accent)
                }
            },
            containerColor = palette.value.surface,
            titleContentColor = palette.value.red,
            textContentColor = palette.value.foreground
        )
    }

    if (showThemePicker) {
        ThemePickerDialog(
            onDismiss = { showThemePicker = false },
            onSelect = { selectedPalette ->
                setCurrentPalette(selectedPalette)
                showThemePicker = false
            }
        )
    }

    if (showPasswordDialog) {
        var passwordInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { },
            title = {
                Text("Password Required", color = palette.value.foreground)
            },
            text = {
                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    label = { Text("Password", color = palette.value.foreground.copy(alpha = 0.7f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = palette.value.foreground,
                        unfocusedTextColor = palette.value.foreground,
                        cursorColor = palette.value.accent,
                        focusedBorderColor = palette.value.accent,
                        unfocusedBorderColor = palette.value.surfaceVariant
                    ),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPasswordDialog = false
                    sshConnection.providePassword(passwordInput)
                }) {
                    Text("Connect", color = palette.value.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPasswordDialog = false
                    sshConnection.disconnect()
                    onDisconnect()
                }) {
                    Text("Cancel", color = palette.value.foreground.copy(alpha = 0.5f))
                }
            },
            containerColor = palette.value.surface
        )
    }
}

enum class ConnectionState {
    IDLE, CONNECTING, CONNECTED, DISCONNECTED, ERROR
}

@Composable
fun ThemePickerDialog(
    onDismiss: () -> Unit,
    onSelect: (TerminalPalette) -> Unit
) {
    val palette = remember { derivedStateOf { currentPalette() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Select Theme", color = palette.value.foreground)
        },
        text = {
            Column {
                TerminalThemes.allPalettes.forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(
                                if (theme.name == palette.value.name) theme.accent.copy(alpha = 0.15f)
                                else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(theme.accent, RoundedCornerShape(6.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            theme.name,
                            color = palette.value.foreground,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        if (theme.name == palette.value.name) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = palette.value.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = palette.value.accent)
            }
        },
        containerColor = palette.value.surface
    )
}

private fun parseSSHInput(input: String): SSHConnectionParams {
    val trimmed = input.trim()
    var host = ""
    var port = 22
    var username = "runner"
    var useCloudflare = false

    val cleaned = trimmed
        .replace(Regex("^ssh\\s+"), "")
        .replace(Regex("-o\\s+ProxyCommand=\"[^\"]*\""), "")
        .replace(Regex("-o\\s+ProxyCommand='[^']*'"), "")
        .replace(Regex("-o\\s+ProxyCommand=[^\\s]+"), "")
        .trim()

    val parts = cleaned.split("\\s+".toRegex()).filter { it.isNotEmpty() }
    var i = 0
    while (i < parts.size) {
        when {
            parts[i] == "-p" && i + 1 < parts.size -> {
                port = parts[i + 1].toIntOrNull() ?: 22
                i += 2
            }
            parts[i].contains("@") -> {
                val userHost = parts[i].split("@")
                if (userHost.size == 2) {
                    username = userHost[0]
                    host = userHost[1]
                } else {
                    host = parts[i]
                }
                i++
            }
            parts[i].matches(Regex("^\\d+$")) && host.isEmpty() -> {
                port = parts[i].toIntOrNull() ?: 22
                i++
            }
            !parts[i].startsWith("-") -> {
                if (host.isEmpty()) host = parts[i]
                i++
            }
            else -> i++
        }
    }

    if (host.isEmpty()) host = trimmed

    if (host.contains("trycloudflare.com")) {
        useCloudflare = true
    }

    return SSHConnectionParams(
        host = host,
        port = port,
        username = username,
        useCloudflare = useCloudflare
    )
}

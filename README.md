<div align="center">

# ShellBridge

**A native Android SSH terminal client with built-in Cloudflare tunnel support.**

Connect to any SSH server from your phone - including Cloudflare Quick Tunnels - with a single command. No root required. No third-party apps needed.

[![Build APK](https://github.com/Youffx/ShellBridge/actions/workflows/build.yml/badge.svg)](https://github.com/Youffx/ShellBridge/actions/workflows/build.yml)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://developer.android.com/studio/releases/platforms)

</div>

---

## Features

- **Direct SSH connections** - Connect to any SSH server with `user@host` or full command syntax
- **Cloudflare Quick Tunnel** - Connect to `*.trycloudflare.com` hosts natively via WebSocket tunnel
- **No dependencies** - No Termux, no cloudflared binary, no root required
- **Full terminal emulation** - VT100/VT220 compatible with ANSI escape code support
- **8 built-in themes** - Catppuccin Mocha, Tokyo Night, Dracula, Nord, Gruvbox, Monokai, One Dark, Rose Pine
- **Custom keyboard bar** - Esc, Tab, Ctrl, Alt, arrows, F1-F12, compose mode
- **Swipe-to-scroll** - Navigate through terminal history
- **PTY allocation** - Full interactive shell support

## Quick Start

1. Download the APK from [Releases](https://github.com/Youffx/ShellBridge/releases)
2. Install on your Android device (enable "Unknown sources" if needed)
3. Paste your SSH command and tap **CONNECT**

## Connection Examples

### Direct SSH
```
user@hostname
user@hostname -p 2222
ssh user@hostname
```

### Cloudflare Quick Tunnel
```
ssh -o ProxyCommand="cloudflared access ssh --hostname %h" runner@adjacent-img-ice-yours.trycloudflare.com
```

Or simplified:
```
runner@adjacent-img-ice-yours.trycloudflare.com
```

The app auto-detects `.trycloudflare.com` hosts and establishes a WebSocket tunnel to the Cloudflare edge automatically.

## How It Works

```
┌─────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  Android     │────▶│  Cloudflare Edge  │────▶│  Remote SSH     │
│  App         │◀────│  (wss://)         │◀────│  Server         │
└─────────────┘     └──────────────────┘     └─────────────────┘

1. App parses SSH input (strips ProxyCommand wrapper)
2. Detects .trycloudflare.com host
3. Opens WebSocket to wss://<hostname>/
4. Bridges SSH traffic through WebSocket binary frames
5. SSHJ connects to local bridge port
6. Terminal renders output with VT100 emulation
```

## Building

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34

### Build APK
```bash
git clone https://github.com/Youffx/ShellBridge.git
cd ShellBridge
./gradlew assembleDebug
```

APK outputs to `app/build/outputs/apk/debug/app-debug.apk`

### GitHub Actions
Push to `main` to trigger automatic APK build. Download from Actions artifacts.

## Architecture

```
app/src/main/java/com/shellbridge/app/
├── ShellBridgeApp.kt          # Application class
├── MainActivity.kt           # Entry point, navigation
├── screens/
│   ├── SSHInputScreen.kt     # Login screen (SSH Input field + Connect)
│   └── TerminalScreen.kt     # Terminal session with keyboard, themes
├── ssh/
│   ├── SSHConnection.kt      # SSHJ client with PTY allocation
│   └── CloudflareTunnel.kt   # WebSocket tunnel to Cloudflare edge
├── terminal/
│   ├── TerminalBuffer.kt     # VT100/VT220 ANSI parser + cursor
│   ├── TerminalView.kt       # Canvas-based terminal renderer
│   └── KeyboardBar.kt        # Modifier keys, arrows, F-keys, compose
└── ui/theme/
    ├── TerminalThemes.kt     # 8 color palettes
    └── Theme.kt              # Material3 theming
```

## Tech Stack

| Component | Library |
|-----------|---------|
| SSH Client | [SSHJ](https://github.com/hierynomus/sshj) 0.40.0 |
| WebSocket | [OkHttp](https://square.github.io/okhttp/) 4.12.0 |
| UI | Jetpack Compose + Material3 |
| Terminal | Custom VT100/VT220 parser |
| Build | Gradle 8.4 + Kotlin 1.9.22 |

## Permissions

| Permission | Purpose |
|------------|---------|
| `INTERNET` | SSH connections and WebSocket tunnel |
| `ACCESS_NETWORK_STATE` | Network state detection |
| `WAKE_LOCK` | Keep screen awake during active sessions |

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Credits

- **[SSHJ](https://github.com/hierynomus/sshj)** - SSH client library for Java/Kotlin
- **[OkHttp](https://square.github.io/okhttp/)** - HTTP & WebSocket client
- **[Cloudflare](https://www.cloudflare.com/)** - Cloudflare Quick Tunnel protocol
- **[Conduit](https://github.com/gwitko/Conduit)** - Flutter SSH terminal (UI inspiration)
- **[Termux](https://github.com/termux/termux-app)** - Android terminal emulator
- **[Catppuccin](https://catppuccin.com/)** - Color palette
- **[Tokyo Night](https://github.com/enkia/tokyo-night-vscode-theme)** - Color palette
- **[Dracula](https://draculatheme.com/)** - Color palette
- **[Nord](https://www.nordtheme.com/)** - Color palette
- **[Gruvbox](https://github.com/morhetz/gruvbox)** - Color palette
- **[Monokai](https://monokai.pro/)** - Color palette
- **[One Dark](https://github.com/atom/one-dark-ui)** - Color palette
- **[Rose Pine](https://rosepinetheme.com/)** - Color palette

## License

MIT License - see [LICENSE](LICENSE) for details.

---

<div align="center">

**Built for developers who work from anywhere.**

</div>

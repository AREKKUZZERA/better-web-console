# Better-WebConsole — Minecraft Server Web Console Plugin

Secure, browser-based server console for Paper 1.21–1.21.11.

---

## Features

- 🔐 **BCrypt password hashing** (cost 12)
- 🔒 **HttpOnly, SameSite=Strict session cookies**
- 🛡️ **CSRF protection** (HMAC-SHA256 tokens)
- 🚫 **IP brute-force lockout** (configurable)
- ⏱️ **Per-session command rate limiting**
- 🌐 **IP whitelist** (supports CIDR notation)
- 🔑 **Constant-time password comparison** (timing attack safe)
- 📋 **Strict security HTTP headers** (CSP, X-Frame-Options, etc.)
- 🔌 **WebSocket live console** with buffered history
- 🎨 **Modern terminal-style UI** (dark theme, log filtering, command history)

---

## Building

Requirements: JDK 21, Maven 3.8+

```bash
cd better-webconsole-plugin
mvn clean package -q
# Output: target/Better-WebConsole-1.0.0.jar
```

Copy `target/Better-WebConsole-1.0.0.jar` to your server's `plugins/` directory.

---

## First-time Setup

1. Start the server once to generate `plugins/Better-WebConsole/config.yml`
2. Add a user via console or in-game:
   ```
   /better-webconsole adduser admin YourStrongPassword123
   ```
3. Open `http://your-server-ip:8080` in your browser
4. Log in with the credentials you set

---

## Commands

| Command | Description |
|---|---|
| `/better-webconsole status` | Show plugin status |
| `/better-webconsole adduser <user> <pass>` | Add a web user |
| `/better-webconsole removeuser <user>` | Remove a web user |
| `/better-webconsole listusers` | List registered users |
| `/better-webconsole reload` | Reload config |

Permission: `webconsole.admin` (op by default)

---

## Configuration (`plugins/Better-WebConsole/config.yml`)

```yaml
web:
  port: 8080
  bind-address: "0.0.0.0"   # "127.0.0.1" for local-only
  log-buffer-size: 500        # Lines sent to new connections

security:
  session-timeout: 60         # Minutes
  max-login-attempts: 5       # Before IP lockout
  lockout-duration: 15        # Minutes
  command-rate-limit: 30      # Commands per minute per session
  ip-whitelist:               # Empty = allow all
    - "192.168.1.0/24"
    - "10.0.0.1"
```

---

## Production Hardening Tips

- **Bind to localhost only** and put Nginx/Caddy in front with HTTPS:
  ```yaml
  bind-address: "127.0.0.1"
  ```
  Then proxy with Nginx + Let's Encrypt for TLS.

- **Restrict IP whitelist** to your admin IPs only.

- **Use a strong password** — minimum 16 characters recommended.

- **Firewall** port 8080 so only your reverse proxy can reach it.

---

## Architecture

```
Browser ──HTTPS──► Nginx ──HTTP──► Jetty (plugin)
                                     ├── GET /          → FrontendServlet (HTML)
                                     ├── POST /api/login → ApiServlet
                                     ├── POST /api/logout
                                     ├── GET  /api/csrf
                                     ├── GET  /api/status
                                     └── WS  /ws        → ConsoleWebSocket
                                                              ↕
                                                         ConsoleLogHandler
                                                         (Java logging Handler)
```

---

## Security Notes

- Passwords are **never stored in plaintext** — BCrypt hash only
- Session tokens are **256-bit cryptographically random** values
- All cookies are **HttpOnly** (no JS access) + **SameSite=Strict**
- CSRF tokens are **HMAC-SHA256** signed, validated on every login
- Failed login responses are **artificially delayed** (500ms) to slow brute-force
- The `stop` and `restart` commands are **blocked by default** via web console
  (edit `ConsoleWebSocket.isBlockedCommand()` to allow if desired)


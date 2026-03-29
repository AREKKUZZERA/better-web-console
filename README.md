# Better-WebConsole — Minecraft Server Web Console Plugin

Secure, browser-based server console for Paper 1.21+

---

## Features

* 🔐 **BCrypt password hashing** (cost 12)
* 🔒 **HttpOnly, SameSite=Strict session cookies**
* 🛡️ **CSRF protection** (HMAC-SHA256 tokens)
* 🚫 **IP brute-force lockout** (configurable)
* ⏱️ **Per-session command rate limiting**
* 🌐 **IP whitelist** (supports CIDR notation)
* 🔑 **Constant-time password comparison** (timing attack safe)
* 📋 **Strict security HTTP headers** (CSP, X-Frame-Options, etc.)
* 🔌 **WebSocket live console** with buffered history
* 🎨 **Modern terminal-style UI** (dark theme, log filtering, command history)
* 📊 **Basic server stats endpoint**

---

## Building

Requirements: **JDK 21**, **Maven 3.8+**

```bash
git clone <repo>
cd better-web-console-main
mvn clean package
```

Output:

```
target/Better-WebConsole-2.0.0.jar
```

Copy the JAR into your server's `plugins/` directory.

---

## First-time Setup

1. Start the server once to generate config:

   ```
   plugins/Better-WebConsole/config.yml
   ```

2. Add a user:

   ```
   /betterwebconsole adduser admin YourStrongPassword123
   ```

3. Open in browser:

   ```
   http://your-server-ip:4242
   ```

4. Log in

---

## Commands

| Command                                   | Description        |
| ----------------------------------------- | ------------------ |
| `/betterwebconsole status`                | Show plugin status |
| `/betterwebconsole adduser <user> <pass>` | Add a web user     |
| `/betterwebconsole removeuser <user>`     | Remove a web user  |
| `/betterwebconsole listusers`             | List users         |
| `/betterwebconsole reload`                | Reload config      |

Aliases:

```
/bwc
/webconsole
```

Permission:

```
betterwebconsole.admin
```

---

## Configuration (`plugins/Better-WebConsole/config.yml`)

```yaml
web:
  port: 4242
  bind-address: "0.0.0.0"
  log-buffer-size: 500

security:
  session-timeout: 60
  max-login-attempts: 5
  lockout-duration: 15
  command-rate-limit: 30
  ip-whitelist: []
```

---

## Web API (internal)

| Endpoint           | Description   |
| ------------------ | ------------- |
| `POST /api/login`  | Login         |
| `POST /api/logout` | Logout        |
| `GET /api/status`  | Server status |
| `GET /api/csrf`    | CSRF token    |
| `WS /ws`           | Live console  |

---

## Architecture

```
Browser ──HTTP/HTTPS──► Plugin Web Server
                             ├── GET /           → FrontendServlet
                             ├── POST /api/*     → ApiServlet
                             ├── WS  /ws         → ConsoleWebSocket
                             │                        ↕
                             │                 ConsoleLogHandler
                             └── Filters (IP whitelist, CSRF, sessions)
```

---

## Security Notes

* Passwords are stored as **BCrypt hashes only**
* Sessions use **secure random tokens**
* Cookies are **HttpOnly + SameSite=Strict**
* CSRF tokens are **HMAC-SHA256 signed**
* Built-in **rate limiting and IP lockouts**
* Supports **IP whitelist filtering**
* Sensitive commands can be restricted in code

---

## Production Recommendations

* Bind to localhost:

  ```yaml
  bind-address: "127.0.0.1"
  ```

* Use reverse proxy (Nginx / Caddy) with HTTPS

* Restrict access via firewall

* Use strong passwords (16+ chars)

* Limit IP whitelist to admin addresses

---

## Notes

This plugin runs an embedded web server inside Minecraft and is designed with security-first principles, but **should still be deployed behind a reverse proxy in production**.


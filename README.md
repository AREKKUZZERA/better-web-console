# Better-WebConsole

Secure browser console and admin dashboard for Paper-compatible servers.

Supported server lines:

- Paper/Purpur `1.21` - `1.21.11`: use the `1.21.X` jar.
- Paper/Purpur `26.1` - `26.1.2`: use the `26.1.X` jar.

## Features

### Web Console

- Embedded Jetty web server with one built-in React/Vite web UI.
- Live console log streaming over WebSocket with buffered history for new sessions.
- Console command execution through the server command map, including commands from plugins such as CMI.
- Command history, filtering, log export and clear action in the browser.
- Configurable web aliases through `!alias`, including chained aliases with `&&`.
- Audit log for auth events, command execution, player actions, config saves and log exports.
- Searchable audit table with action/user/IP filters and CSV/JSON export.

### Dashboard

- Desktop-focused two-column dashboard layout for server administration.
- Server health: TPS, JVM heap, online players, worlds, loaded chunks, entities and session errors.
- Machine health: host CPU load, Java process CPU load, physical RAM, server disk usage and JVM thread counts.
- Performance history charts for TPS, JVM RAM, online players and host CPU.
- Machine details: CPU model, cores/threads, memory, disk mount, OS, Java runtime, PID and JVM uptime.
- Analytics blocks for log levels, per-world chunks/entities and recent activity.
- Compatibility diagnostics for BWC, Java and supported Paper/Purpur server lines.
- Error grouping for recent console ERROR/SEVERE/exception lines.
- Centered dashboard panels with responsive mobile layouts.
- Player list with quick kick, ban, message, gamemode and teleport actions using vanilla/Paper commands.
- Player profile drawer with UUID, location, gamemode, health and recent activity for online players.
- Player join/leave and command history grouped by day, retained across the plugin data file lifetime.
- Player activity summaries and top active players in the dashboard.
- Read-only web sessions panel support for active admin session metadata.
- Web config editor for aliases, blocked commands, logging and system stats settings.

### Security

- Web users stored in `plugins/Better-WebConsole/users.dat` with BCrypt hashes.
- HttpOnly + SameSite session cookies, optional Secure cookies for HTTPS reverse proxies.
- CSRF protection for login.
- IP whitelist with CIDR support.
- Login lockout and command rate limit.
- Optional command block list for dangerous console commands.

## Build

Requirements:

- JDK 25 for the full two-artifact build.
- Maven 3.9+.
- Node.js 22+ and npm when rebuilding frontend assets from source.

Both produced plugin jars use Java 21 bytecode. The 26.1 build still needs a JDK that can read the current Paper 26.1 API during compilation.
Published plugin jars contain prebuilt static frontend assets. Node.js is only needed for source builds.

```bash
mvn clean package
```

Output:

```text
target/bwc-2.4.7-paper-1.21.X.jar
target/bwc-2.4.7-paper-26.1.X.jar
```

Use `bwc-2.4.7-paper-1.21.X.jar` on Paper/Purpur `1.21` through `1.21.11`.
Use `bwc-2.4.7-paper-26.1.X.jar` on Paper `26.1` through `26.1.2`.

## First Setup

1. Put the correct `bwc-...jar` into the server `plugins/` folder.
2. Start the server once to generate `plugins/Better-WebConsole/config.yml`.
3. Create a web user:

```text
/bwc adduser admin YourStrongPassword123
```

4. Open:

```text
http://your-server-ip:4242
```

Production recommendation: bind to `127.0.0.1` and expose the panel through Nginx, Caddy, a VPN or a tunnel with HTTPS.

## Reverse Proxy / HTTPS

Recommended production settings:

```yaml
web:
  bind-address: "127.0.0.1"

security:
  secure-cookies: true
```

Use `secure-cookies: true` only when admins open the panel through HTTPS.

### Nginx

```nginx
map $http_upgrade $connection_upgrade {
    default upgrade;
    '' close;
}

server {
    listen 443 ssl http2;
    server_name bwc.example.com;

    ssl_certificate /etc/letsencrypt/live/bwc.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/bwc.example.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:4242;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $connection_upgrade;
        proxy_read_timeout 1h;
    }
}
```

### Caddy

```caddyfile
bwc.example.com {
    reverse_proxy 127.0.0.1:4242
}
```

### Cloudflare Tunnel

```yaml
tunnel: <tunnel-uuid>
credentials-file: /etc/cloudflared/<tunnel-uuid>.json

ingress:
  - hostname: bwc.example.com
    service: http://127.0.0.1:4242
  - service: http_status:404
```

Reference docs: [Nginx WebSocket proxying](https://nginx.org/en/docs/http/websocket.html), [Caddy reverse_proxy](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy), [Cloudflare Tunnel routing](https://developers.cloudflare.com/tunnel/routing/).

## Commands

Main command aliases: `/betterwebconsole`, `/bwc`, `/webconsole`, `/bwconsole`, `/betterconsole`.

Permission: `betterwebconsole.admin` (default: op).

| Command | Description |
| --- | --- |
| `/bwc status` | Show web server, user, session and config status |
| `/bwc reload` | Reload config values that do not require web server restart |
| `/bwc adduser <user> <password>` | Add a web user |
| `/bwc removeuser <user>` | Remove a web user and invalidate sessions |
| `/bwc listusers` | List web users |
| `/bwc setpassword <user> <new-password>` | Change password and invalidate sessions |
| `/bwc logoutall <user>` | Invalidate active sessions for a user |

Extra command aliases: `useradd`, `createuser`, `deluser`, `deleteuser`, `users`, `passwd`, `password`, `killsessions`.

## Configuration

The default config is intentionally small and only contains implemented behavior.

```yaml
web:
  port: 4242
  bind-address: "0.0.0.0"
  log-buffer-size: 1000

security:
  session-timeout-minutes: 60
  max-login-attempts: 5
  lockout-duration-minutes: 15
  command-rate-limit-per-minute: 30
  ip-whitelist: []
  secure-cookies: false

logging:
  log-commands: true
  log-auth: true
  audit-log: true

system-stats:
  # Adds host CPU, machine RAM, disk, OS and JVM details to the dashboard.
  enabled: true

  # OS-level polling interval. Keep this above 2 seconds for production servers.
  update-interval-seconds: 5

  # Reports disk usage for the Minecraft server folder.
  show-disk: true

commands:
  blocked: []
  aliases:
    tps: "tps"
    list: "list"
    save: "save-all"
    day: "time set day"
    night: "time set night"
    clear-weather: "weather clear"
```

`system-stats` can be disabled if the host does not allow OS-level metrics or if you only need Minecraft/JVM data.

Player activity history is stored in:

```text
plugins/Better-WebConsole/player-command-history.tsv
```

It stores join/leave events and player-entered slash commands over time. The web UI loads a bounded recent day window so large history files do not flood the browser.

Use `commands.blocked` to prevent risky commands from web access, for example:

```yaml
commands:
  blocked: ["stop", "restart", "op", "deop"]
```

Use an alias by typing `!name` in the web console. Aliases can chain up to 10 commands with `&&`.

`/bwc reload` updates aliases, logging, system stats settings and command block rules. Restart the Minecraft server after changing `web.port`, `web.bind-address`, `security.ip-whitelist`, session timeout or rate-limit settings.

## Security Notes

- Do not expose `0.0.0.0:4242` directly to the internet unless firewall/IP whitelist/VPN rules are in place.
- Set `secure-cookies: true` only when users access the panel through HTTPS.
- Block or avoid destructive commands such as `stop`, `restart`, `op`, `deop`, `ban-ip` and `whitelist`.
- Player command history can include sensitive commands from other plugins. Restrict panel access to trusted administrators.
- Keep aliases short and auditable.
- Keep `commands.blocked` empty only when every web user is trusted as a full console administrator.
- Treat machine metrics as operational data: expose the panel only to trusted administrators.

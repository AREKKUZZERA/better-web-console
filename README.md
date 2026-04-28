# Better-WebConsole

Secure browser console and admin dashboard for Paper/Purpur/Spigot 1.21.x servers.

## Features

### Web Console

- Embedded Jetty web server with one built-in web UI.
- Live console log streaming over WebSocket with buffered history for new sessions.
- Console command execution through the server command map, including commands from plugins such as CMI.
- Command history, filtering, log export and clear action in the browser.
- Configurable web aliases through `!alias`, including chained aliases with `&&`.
- Audit log for auth events, command execution, player actions and log exports.

### Dashboard

- Desktop-focused two-column dashboard layout for server administration.
- Server health: TPS, JVM heap, online players, worlds, loaded chunks, entities and session errors.
- Machine health: host CPU load, Java process CPU load, physical RAM, server disk usage and JVM thread counts.
- Performance history charts for TPS, JVM RAM, online players and host CPU.
- Machine details: CPU model, cores/threads, memory, disk mount, OS, Java runtime, PID and JVM uptime.
- Analytics blocks for log levels, per-world chunks/entities and recent activity.
- Player list with quick kick/ban actions.

### Security

- Web users stored in `plugins/Better-WebConsole/users.dat` with BCrypt hashes.
- HttpOnly + SameSite session cookies, optional Secure cookies for HTTPS reverse proxies.
- CSRF protection for login.
- IP whitelist with CIDR support.
- Login lockout and command rate limit.
- Optional command block list for dangerous console commands.

## Build

Requirements: JDK 21 and Maven 3.8+.

```bash
mvn clean package
```

Output:

```text
target/Better-WebConsole-2.3.0.jar
```

## First Setup

1. Put the JAR into the server `plugins/` folder.
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
- Keep aliases short and auditable.
- Keep `commands.blocked` empty only when every web user is trusted as a full console administrator.
- Treat machine metrics as operational data: expose the panel only to trusted administrators.

## Useful Future Features

Compared with public WebConsole/WebPanel/RCON-style tools, useful next features are:

- Roles: admin vs read-only viewer, and per-user command allow/deny lists.
- Built-in HTTPS or documented reverse-proxy templates.
- Multi-language UI.
- File manager and config editor with strict path sandboxing.
- Scheduled commands/tasks.
- Multi-server dashboard.
- 2FA or one-time recovery tokens.
- Discord/webhook notifications for login, errors and blocked commands.
- Plugin/server lifecycle buttons with confirmation policies.

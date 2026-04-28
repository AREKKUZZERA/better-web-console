# Better-WebConsole

Secure browser console for Paper/Purpur/Spigot 1.21.x servers.

## Current Features

- Embedded Jetty web server with a single built-in web UI.
- Live console log streaming over WebSocket.
- Console command execution, command history, filtering and log export.
- Dashboard with TPS, RAM, uptime, worlds, loaded chunks, entities and online players.
- Player quick actions: kick and ban.
- Web users stored in `plugins/Better-WebConsole/users.dat` with BCrypt hashes.
- HttpOnly + SameSite session cookies, optional Secure cookies for HTTPS reverse proxies.
- CSRF protection for login, IP whitelist with CIDR support, login lockout and command rate limit.
- Audit log for auth events, command execution, player actions and log exports.
- Configurable web command aliases through `!alias`.

## Build

Requirements: JDK 21 and Maven 3.8+.

```bash
mvn clean package
```

Output:

```text
target/Better-WebConsole-2.2.1.jar
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

commands:
  blocked: []
  aliases:
    tps: "tps"
    list: "list"
    save: "save-all"
```

Use `commands.blocked` to prevent risky commands from web access, for example:

```yaml
commands:
  blocked: ["stop", "restart", "op", "deop"]
```

Use an alias by typing `!name` in the web console. Aliases can chain up to 10 commands with `&&`.

## Security Notes

- Do not expose `0.0.0.0:4242` directly to the internet unless firewall/IP whitelist/VPN rules are in place.
- Set `secure-cookies: true` only when users access the panel through HTTPS.
- Avoid putting destructive commands into aliases.
- Keep `commands.blocked` empty only when every web user is trusted as a full console administrator.

## Competitor Notes / Useful Future Features

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

Sources reviewed: Modrinth Better WebConsole, `mesacarlos/WebConsole`, Modrinth WebPanel.

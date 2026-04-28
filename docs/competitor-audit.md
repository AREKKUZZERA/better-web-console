# Competitor Audit

Reviewed on 2026-04-27:

- https://modrinth.com/plugin/better-webconsole
- https://github.com/mesacarlos/WebConsole
- https://modrinth.com/plugin/webpanel

Observed common/user-valued features:

- Browser console with WebSocket log streaming and command execution.
- Multiuser support.
- Viewer/read-only roles.
- Command whitelist/blacklist per user.
- Command history and log filtering.
- CPU/RAM/player status.
- Localization.
- Optional SSL/HTTPS support.
- File management in broader web panels.

Recommended roadmap for this plugin:

1. User roles: `admin`, `operator`, `viewer`.
2. Per-user command rules and audit labels.
3. HTTPS reverse-proxy examples for Nginx/Caddy.
4. Two-factor authentication.
5. Scheduled command runner.
6. Optional file/config manager with path sandboxing.
7. Discord/webhook alerts.
8. Multi-server saved connections.

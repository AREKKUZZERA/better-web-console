# Changelog

## 2.2.1 - 2026-04-28

### Fixed

- Fixed web console command execution for plugins that require a real console sender, including CMI commands such as `/cmi`.
- Command responses from plugin commands now flow through the server console/log capture into the web console instead of using a synthetic callback sender.

## 2.2.0 - 2026-04-28

### Added

- Added simplified production-ready `config.yml` with clearer names and comments.
- Added `commands.blocked` to block risky commands from the web console and aliases.
- Added `commands.aliases` while keeping legacy `aliases` compatibility.
- Added command aliases: `/bwconsole`, `/betterconsole`.
- Added management subcommands:
  - `/bwc setpassword <user> <new-password>`
  - `/bwc logoutall <user>`
- Added extra subcommand aliases: `useradd`, `createuser`, `deluser`, `deleteuser`, `users`, `passwd`, `password`, `killsessions`.
- Added JUnit tests for config parsing, safe fallback values and blocked commands.
- Added competitor audit and roadmap notes in `docs/competitor-audit.md`.

### Changed

- Removed default destructive `restart` web alias from the generated config.
- Moved aliases under `commands.aliases` for a clearer config structure.
- Invalidating user sessions now happens when removing a user or changing their password.
- `/bwc status` now shows alias and blocked-command counts.
- `/bwc reload` message now clearly states which settings still need a server restart.
- API reads the latest plugin config after reload for aliases, logging and cookie settings.
- Maven compiler annotation processing is disabled to avoid noisy JDK warnings.

### Security

- Web command blocking now applies both to direct commands and alias-expanded commands.
- Alias chains are limited to 10 commands.
- Kick/ban web actions validate player names and sanitize reasons.
- `logging.audit-log: false` now disables all audit writes consistently.
- Optional `security.secure-cookies` adds the `Secure` cookie flag for HTTPS deployments.

### Fixed

- Fixed broken encoding text in source/config/docs touched by this update.
- Fixed stale API config snapshot after `/bwc reload`.
- Fixed sessions remaining active after password change or user removal.

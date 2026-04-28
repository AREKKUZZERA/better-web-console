# Changelog

## 2.3.0 - 2026-04-28

### Added

- Added machine/system metrics to the dashboard:
  - host CPU load and Java process CPU load
  - CPU model and core/thread count
  - physical RAM total/used/free
  - JVM heap and non-heap memory
  - JVM uptime
  - server disk total/used/free
  - OS, Java version, process id and JVM thread count
- Added `system-stats` config section to enable/disable system metrics, control polling interval and disk reporting.

### Changed

- Reworked the dashboard desktop layout into a wider two-column admin view with grouped health, charts, machine details and analytics.
- Added hover states to Machine Details cards to match KPI and chart cards.

### Fixed

- Fixed repeated Players tab entrance animation loops by preventing re-animation on active-tab clicks and unchanged player lists.
- Fixed OSHI configuration warning by keeping OSHI package/resource names unrelocated in the shaded jar.

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

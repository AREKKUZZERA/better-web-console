# Changelog

## Unreleased

### Added

- Added a React + Vite frontend build pipeline for the web UI.
- Added a read-only web sessions metadata API and React sessions panel scaffold.
- Added a Sessions tab to the current web UI.
- Added vanilla/Paper player actions for message, gamemode and teleport.
- Added all-time player activity storage with day-grouped history in the Players tab.
- Added dashboard player activity summaries and top active players.

### Changed

- Improved Players tab filtering and reduced repeated history rendering.
- Switched the main web UI entrypoint from the legacy HTML resource to the Vite-built React bundle.

### Performance

- Cached player activity summary counters instead of recalculating them for every stats payload.

## 2.4.2 - 2026-05-21

### Performance

- Reduced shaded jar size by excluding unused JNA native binaries and Maven/package metadata.
- Reduced WebSocket broadcast overhead by preparing stats and log payloads once per broadcast.
- Reduced small runtime allocations in stats collection, API body parsing, log export filtering and validation helpers.

## 2.4.1 - 2026-05-16

### Added

- Added visible Server Health reason badges for low TPS, session errors and high player capacity.
- Added dashboard Health History for Watch/Critical state transitions with start time, duration and reasons.

### Fixed

- Kept the Server Health tooltip reason text synchronized after changing the web UI language.

### Build

- Moved shared module resources into generated module-local resource directories to avoid IDE warnings about resources outside the module base directory.
- Added Maven m2e lifecycle mapping for the shared-resource generation step to silence Java project import warnings.
- Kept filtered `plugin.yml` generation module-aware so Paper `1.21` and `26.1` jars retain the correct API version.

## 2.4.0 - 2026-05-15

### Added

- Added a persistent 24-hour player activity history store for join, leave and player command events.
- Added player activity history to the stats API and Players tab.
- Added recent player command count and activity feed updates to the dashboard overview.
- Added web UI localization with language selection for English, Russian, Chinese, Polish, German and French.

### Changed

- Improved dashboard overview layout and centering.
- Improved Players tab responsive layout for smaller screens.
- Updated README installation guidance for the new Paper build matrix.

### Build

- Added separate Paper build artifacts for Minecraft/Paper `1.21` - `1.21.11` and `26.1` - `26.1.2`.
- Shortened generated jar names to `bwc-<version>-paper-1.21.X.jar` and `bwc-<version>-paper-26.1.X.jar`.
- Ignored module `target` directories.

### Fixed

- Protected player activity history compaction from recursive pruning.
- Cleared stale activity history UI while stats are unavailable or reset.
- Handled empty activity history timestamps in the web UI.
- Handled unknown player capacity values in dashboard cards.
- Escaped activity feed text before rendering user/server-provided content.
- Limited activity history payload size returned by the stats API.
- Collected API stats on the main server thread.

### Docs

- Added reverse proxy / HTTPS examples for Nginx, Caddy and Cloudflare Tunnel.
- Documented player command history storage and the related security note.

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

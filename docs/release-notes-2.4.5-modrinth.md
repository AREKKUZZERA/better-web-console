# Better-WebConsole 2.4.5

Security and dashboard maintenance update.

## Added

- New Audit tab for recent authentication, command and player-action audit events.
- Search and refresh controls for audit history.
- Built-in favicon asset.

## Improved

- Web sessions are now persisted as SHA-256 token hashes instead of raw session tokens.
- Existing session storage is migrated automatically on next startup.
- Dashboard stats now use cached snapshots, reducing extra Bukkit main-thread work from web clients.
- Chart.js is bundled with the plugin frontend instead of loaded from a CDN.

## Compatibility

- `1.21.X` jar: Paper/Purpur `1.21` - `1.21.11`
- `26.1.X` jar: Paper `26.1` - `26.1.2`

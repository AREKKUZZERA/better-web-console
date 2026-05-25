# Better-WebConsole 2.4.5

## Highlights

- Added an authenticated Audit tab for recent web console audit events.
- Hardened persisted web sessions by storing SHA-256 token hashes instead of raw cookie tokens.
- Reduced stats API and WebSocket main-thread pressure by serving cached `ServerStats` snapshots.
- Removed the CDN Chart.js dependency from the web UI and bundled Chart.js through Vite.

## Changes

- Added `/api/audit?limit=...` for authenticated recent audit log reads.
- Added search, refresh and localized labels for the new Audit UI.
- Added favicon packaging and static SVG favicon serving.
- Migrated existing raw `sessions.tsv` entries to hashed session identifiers on next startup.
- Refactored the React app shell so the legacy UI is mounted through an isolated component instead of direct `dangerouslySetInnerHTML`.

## Compatibility

- Use `bwc-2.4.5-paper-1.21.X.jar` for Paper/Purpur `1.21` through `1.21.11`.
- Use `bwc-2.4.5-paper-26.1.X.jar` for Paper `26.1` through `26.1.2`.

## Validation

- `npm run typecheck`
- `npm run build`
- `mvn -DskipTests package`

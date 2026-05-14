# Dashboard and Players Improvements Design

## Scope

Improve the existing single-file web UI and add persistent 24-hour player activity data.

This work covers:

- Center dashboard content on the screen.
- Fix dashboard and Players responsiveness on small screens.
- Redesign Players as a two-column view: player list on the left, history on the right.
- Track Minecraft player joins/leaves and player-entered slash commands for the last 24 hours.
- Store player command history in a simple file-backed format.
- Add small dashboard quality improvements and fixes that fit the current UI.

This work does not cover:

- Commands run by admins from the web console.
- A full SQL database.
- New tests unless a risky backend parser/store change needs focused coverage.
- Starting a dev server.

## Recommended Approach

Use a TSV append-only file with an in-memory 24-hour cache.

File:

```text
plugins/Better-WebConsole/player-command-history.tsv
```

Command row format:

```text
command<TAB>epochMillis<TAB>uuid<TAB>playerName<TAB>commandText
```

Join/leave row format:

```text
join<TAB>epochMillis<TAB>uuid<TAB>playerName<TAB>
leave<TAB>epochMillis<TAB>uuid<TAB>playerName<TAB>
```

Tabs and newlines inside values will be escaped before writing. On startup, the store reads the file, keeps only entries newer than 24 hours, and rewrites the file compactly. During runtime, new entries are appended and old in-memory entries are pruned.

## Backend Design

Add a `PlayerActivityStore` class under `dev.webconsole.stats`.

Responsibilities:

- Load persisted rows on plugin enable.
- Record player join, leave, and command events.
- Keep only the last 24 hours.
- Return JSON arrays for the frontend.
- Compact the file on startup and when pruning removes expired rows.

Register Bukkit listeners in the plugin:

- `PlayerJoinEvent` records `join`.
- `PlayerQuitEvent` records `leave`.
- `PlayerCommandPreprocessEvent` records the raw slash command typed by the player.

Expose the data through `ServerStats.toJson()`:

- `playerEvents`: recent join/leave rows.
- `playerCommands`: recent player command rows.

The existing WebSocket stats path already pushes `ServerStats.toJson()`, so the frontend can update without adding a new REST endpoint.

## Frontend Design

Keep the current `webconsole.html` structure, but replace the Players panel content with:

- `.players-layout`
- `.players-list-card` on the left
- `.players-history-card` on the right

Left side:

- Current online player table.
- Empty state when nobody is online.
- Existing Kick/Ban actions retained.

Right side:

- Join/leave timeline.
- Command history grouped by player for commands seen in the last 24 hours.
- Empty states for no joins and no commands.

Small screens:

- Tabs become horizontally scrollable and each tab has a stable minimum width.
- `Players (0)` remains a single compact tab and does not overflow badly.
- Players layout collapses to one column.
- Tables can scroll horizontally inside their card instead of breaking the viewport.

Dashboard:

- Center `.dash-layout` with horizontal auto margins.
- Keep the existing metric cards and charts.
- Add small summary/status improvements using existing live data:
  - Server health status derived from TPS, RAM, and error count.
  - Online player capacity percentage.
  - Uptime visibility if not already shown clearly.
- Improve mobile grid constraints and prevent card text overflow.

## Data Flow

1. Minecraft player joins/leaves or enters a slash command.
2. Listener records the event in `PlayerActivityStore`.
3. Store appends to TSV and updates memory.
4. `ServerStats.toJson()` includes recent events and commands.
5. WebSocket sends stats to browser.
6. Browser updates the Players history panel and dashboard summaries.

## Error Handling

- If the TSV file cannot be read, log a warning and continue with empty history.
- If a malformed row is found, skip that row.
- If append fails, log a warning and keep the in-memory event for the current session.
- The UI treats missing arrays as empty arrays.

## Verification

Run static/build verification only:

```powershell
mvn -q -DskipTests package
```

No dev server should be started.

Manual review points:

- Confirm Players tab no longer breaks on narrow widths.
- Confirm Players panel has left player list and right history.
- Confirm commands typed by Minecraft players appear, while web-console admin commands do not.
- Confirm history survives plugin/server restart through the TSV file.

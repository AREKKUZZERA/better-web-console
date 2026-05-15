# Dashboard Players Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Center and improve dashboard panels, fix Players responsiveness, and add persistent 24-hour Minecraft player join/leave and command history.

**Architecture:** Add a focused file-backed `PlayerActivityStore` that owns TSV persistence and JSON export. Register Bukkit player listeners in the plugin, include activity data in existing stats JSON, and render the new two-column Players panel in the current single-file frontend.

**Tech Stack:** Java 21, Bukkit/Paper events, Gson, Jetty servlet/WebSocket path already present, plain HTML/CSS/JS, Maven.

---

## File Structure

- Create `src/main/java/dev/webconsole/stats/PlayerActivityStore.java`
  - Owns TSV file loading, escaping, appending, pruning, compaction, and JSON export.
- Modify `src/main/java/dev/webconsole/BetterWebConsolePlugin.java`
  - Creates the activity store, registers Bukkit event listeners, exposes a getter.
- Modify `src/main/java/dev/webconsole/stats/ServerStats.java`
  - Adds `playerEvents` and `playerCommands` arrays to `toJson()`.
- Modify `src/main/resources/webconsole.html`
  - Centers dashboard layout.
  - Fixes small-screen tabs and Players layout.
  - Renders join/leave history and player command history.
  - Adds focused dashboard summary fields using existing stats data.

## Task 1: Add Player Activity Store

**Files:**
- Create: `src/main/java/dev/webconsole/stats/PlayerActivityStore.java`

- [ ] **Step 1: Create the store class**

Use `apply_patch` to add this file:

```java
package dev.webconsole.stats;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class PlayerActivityStore {
    private static final long RETENTION_MS = Duration.ofHours(24).toMillis();
    private static final String TYPE_COMMAND = "command";
    private static final String TYPE_JOIN = "join";
    private static final String TYPE_LEAVE = "leave";

    private final Logger logger;
    private final File file;
    private final List<Entry> entries = new ArrayList<>();

    public PlayerActivityStore(File dataFolder, Logger logger) {
        this.logger = logger;
        this.file = new File(dataFolder, "player-command-history.tsv");
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            logger.warning("[BWC] Failed to create data folder for player activity history");
        }
        load();
        compact();
    }

    public synchronized void recordJoin(UUID uuid, String playerName) {
        record(TYPE_JOIN, System.currentTimeMillis(), uuid, playerName, "");
    }

    public synchronized void recordLeave(UUID uuid, String playerName) {
        record(TYPE_LEAVE, System.currentTimeMillis(), uuid, playerName, "");
    }

    public synchronized void recordCommand(UUID uuid, String playerName, String command) {
        record(TYPE_COMMAND, System.currentTimeMillis(), uuid, playerName, command);
    }

    public synchronized JsonArray recentEventsJson() {
        pruneExpired(false);
        JsonArray array = new JsonArray();
        for (Entry entry : entries) {
            if (!TYPE_JOIN.equals(entry.type) && !TYPE_LEAVE.equals(entry.type)) continue;
            array.add(entry.toJson());
        }
        return array;
    }

    public synchronized JsonArray recentCommandsJson() {
        pruneExpired(false);
        JsonArray array = new JsonArray();
        for (Entry entry : entries) {
            if (TYPE_COMMAND.equals(entry.type)) array.add(entry.toJson());
        }
        return array;
    }

    private void record(String type, long timestamp, UUID uuid, String playerName, String detail) {
        pruneExpired(true);
        Entry entry = new Entry(type, timestamp, uuid.toString(), playerName, detail);
        entries.add(entry);
        append(entry);
    }

    private void load() {
        if (!file.isFile()) return;
        long cutoff = System.currentTimeMillis() - RETENTION_MS;
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Entry entry = parse(line);
                if (entry != null && entry.timestamp >= cutoff) entries.add(entry);
            }
        } catch (IOException e) {
            logger.warning("[BWC] Failed to read player activity history: " + e.getMessage());
        }
    }

    private Entry parse(String line) {
        String[] parts = line.split("\t", -1);
        if (parts.length != 5) return null;
        String type = parts[0];
        if (!TYPE_COMMAND.equals(type) && !TYPE_JOIN.equals(type) && !TYPE_LEAVE.equals(type)) return null;
        try {
            long timestamp = Long.parseLong(parts[1]);
            return new Entry(type, timestamp, unescape(parts[2]), unescape(parts[3]), unescape(parts[4]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void pruneExpired(boolean compactAfterPrune) {
        long cutoff = System.currentTimeMillis() - RETENTION_MS;
        boolean removed = false;
        Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().timestamp < cutoff) {
                iterator.remove();
                removed = true;
            }
        }
        if (removed && compactAfterPrune) compact();
    }

    private void append(Entry entry) {
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
            writer.write(entry.toLine());
            writer.newLine();
        } catch (IOException e) {
            logger.warning("[BWC] Failed to append player activity history: " + e.getMessage());
        }
    }

    private void compact() {
        pruneExpired(false);
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {
            for (Entry entry : entries) {
                writer.write(entry.toLine());
                writer.newLine();
            }
        } catch (IOException e) {
            logger.warning("[BWC] Failed to compact player activity history: " + e.getMessage());
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String unescape(String value) {
        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                if (c == 't') out.append('\t');
                else if (c == 'r') out.append('\r');
                else if (c == 'n') out.append('\n');
                else out.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                out.append(c);
            }
        }
        if (escaped) out.append('\\');
        return out.toString();
    }

    private record Entry(String type, long timestamp, String uuid, String playerName, String detail) {
        String toLine() {
            return type + "\t" + timestamp + "\t" + escape(uuid) + "\t" + escape(playerName) + "\t" + escape(detail);
        }

        JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("type", type);
            obj.addProperty("timestamp", timestamp);
            obj.addProperty("uuid", uuid);
            obj.addProperty("player", playerName);
            if (TYPE_COMMAND.equals(type)) obj.addProperty("command", detail);
            return obj;
        }
    }
}
```

- [ ] **Step 2: Commit the store**

Run:

```powershell
git add src/main/java/dev/webconsole/stats/PlayerActivityStore.java
git commit -m "feat(players): add activity history store"
```

Expected: commit succeeds.

## Task 2: Wire Bukkit Player Events

**Files:**
- Modify: `src/main/java/dev/webconsole/BetterWebConsolePlugin.java`

- [ ] **Step 1: Inspect plugin lifecycle**

Run:

```powershell
rg -n "onEnable|onDisable|ServerStats|AuditLog|getServerStats" src\main\java\dev\webconsole\BetterWebConsolePlugin.java
```

Expected: see where fields are initialized and getters live.

- [ ] **Step 2: Add imports**

Add imports:

```java
import dev.webconsole.stats.PlayerActivityStore;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
```

- [ ] **Step 3: Add field and initialization**

Add field near `ServerStats`:

```java
private PlayerActivityStore playerActivityStore;
```

Initialize in `onEnable()` after `getDataFolder()` is usable and before `ServerStats` starts:

```java
this.playerActivityStore = new PlayerActivityStore(getDataFolder(), getLogger());
```

- [ ] **Step 4: Register listener**

In `onEnable()`, register:

```java
getServer().getPluginManager().registerEvents(new PlayerActivityListener(), this);
```

Add inner listener class inside `BetterWebConsolePlugin`:

```java
private final class PlayerActivityListener implements Listener {
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        playerActivityStore.recordJoin(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerActivityStore.recordLeave(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        playerActivityStore.recordCommand(event.getPlayer().getUniqueId(), event.getPlayer().getName(), event.getMessage());
    }
}
```

- [ ] **Step 5: Add getter**

Add near existing getters:

```java
public PlayerActivityStore getPlayerActivityStore() { return playerActivityStore; }
```

- [ ] **Step 6: Commit event wiring**

Run:

```powershell
git add src/main/java/dev/webconsole/BetterWebConsolePlugin.java
git commit -m "feat(players): track minecraft player activity"
```

Expected: commit succeeds.

## Task 3: Expose Activity in Stats JSON

**Files:**
- Modify: `src/main/java/dev/webconsole/stats/ServerStats.java`

- [ ] **Step 1: Add JSON arrays to `toJson()`**

Near the existing `playerList` output, add:

```java
if (plugin.getPlayerActivityStore() != null) {
    obj.add("playerEvents", plugin.getPlayerActivityStore().recentEventsJson());
    obj.add("playerCommands", plugin.getPlayerActivityStore().recentCommandsJson());
} else {
    obj.add("playerEvents", new JsonArray());
    obj.add("playerCommands", new JsonArray());
}
```

- [ ] **Step 2: Commit stats export**

Run:

```powershell
git add src/main/java/dev/webconsole/stats/ServerStats.java
git commit -m "feat(players): expose activity history in stats"
```

Expected: commit succeeds.

## Task 4: Redesign Players Panel and Responsive Tabs

**Files:**
- Modify: `src/main/resources/webconsole.html`

- [ ] **Step 1: Replace Players CSS**

Replace the current Players CSS block with card-based layout:

```css
#panel-players{overflow-y:auto;padding:12px;align-items:center}
.players-layout{width:min(100%,1360px);display:grid;grid-template-columns:minmax(0,1fr) minmax(320px,.78fr);gap:12px;align-items:start}
.players-card{background:linear-gradient(180deg,rgba(255,255,255,.018),transparent 28%),var(--bg2);border:1px solid var(--border2);border-radius:18px;padding:12px;min-width:0;box-shadow:var(--shadow-soft)}
.players-card-header{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:10px}
.players-card-title{font-size:11px;text-transform:uppercase;letter-spacing:.08em;color:var(--text-dim)}
.players-card-badge{font-size:10px;padding:2px 7px;border-radius:20px;background:var(--bg3);border:1px solid var(--border2);color:var(--text-muted);white-space:nowrap}
.player-table-wrap{overflow-x:auto;scrollbar-width:thin;scrollbar-color:var(--border) transparent}
.player-table{width:100%;min-width:520px;border-collapse:collapse}
.player-table th{text-align:left;font-size:10px;text-transform:uppercase;letter-spacing:.08em;color:var(--text-dim);padding:6px 10px;border-bottom:1px solid var(--border);position:sticky;top:0;background:var(--bg2)}
.player-table td{padding:7px 10px;border-bottom:1px solid var(--border2);font-size:12.5px;vertical-align:middle;transition:background-color var(--fast)}
.player-table tbody tr:hover td{background:rgba(242,57,135,.08)}
.player-op{display:inline-block;font-size:9px;background:rgba(255,176,32,.15);color:var(--warn);border:1px solid rgba(255,176,32,.3);border-radius:3px;padding:1px 5px;margin-left:5px}
.ping-good{color:var(--accent)}.ping-ok{color:var(--warn)}.ping-bad{color:var(--danger)}
.action-btn{background:none;border:1px solid var(--border2);border-radius:var(--r-xs);font-family:var(--font);font-size:11px;color:var(--text-dim);padding:4px 8px;cursor:pointer;transition:all var(--fast)}
.action-btn:hover{transform:translateY(-1px)}
.action-btn.kick:hover{border-color:var(--warn);color:var(--warn)}
.action-btn.ban:hover{border-color:var(--danger);color:var(--danger)}
.no-players{text-align:center;color:var(--text-muted);padding:34px 0;font-size:13px}
.players-history{display:grid;gap:12px}
.history-list{display:flex;flex-direction:column;gap:6px;max-height:260px;overflow-y:auto;scrollbar-width:thin;scrollbar-color:var(--border) transparent}
.history-item{display:grid;grid-template-columns:auto 1fr auto;gap:8px;align-items:start;padding:7px 9px;background:var(--bg3);border-left:2px solid var(--border);border-radius:var(--r-sm)}
.history-item.join{border-left-color:var(--success)}.history-item.leave{border-left-color:var(--text-muted)}.history-item.command{border-left-color:var(--accent)}
.history-kind{font-size:10px;color:var(--text-muted);text-transform:uppercase;letter-spacing:.05em}
.history-main{min-width:0;color:var(--text-dim);font-size:11px;word-break:break-word}
.history-main strong{color:var(--text)}
.history-command{display:block;color:var(--text);margin-top:2px;font-family:var(--font)}
.history-time{font-size:10px;color:var(--text-muted);white-space:nowrap}
.history-empty{color:var(--text-muted);font-size:12px;text-align:center;padding:18px 0;background:var(--bg3);border-radius:var(--r-sm)}
```

- [ ] **Step 2: Add responsive tab CSS**

Add below existing tab styles or media rules:

```css
#tabs{overflow-x:auto;scrollbar-width:thin;scrollbar-color:var(--border) transparent}
.tab{white-space:nowrap;flex:0 0 auto}
@media (max-width:620px){
  header{gap:7px;padding:0 8px}
  .hlogo{font-size:12px}
  .huser,.hsep{display:none}
  .hbtn{padding:4px 7px;font-size:10px}
  #tabs{padding:0 6px}
  .tab{padding:7px 10px;font-size:11px;min-width:max-content}
}
@media (max-width:920px){
  #panel-players{align-items:stretch}
  .players-layout{grid-template-columns:1fr}
  .history-list{max-height:220px}
}
```

- [ ] **Step 3: Replace Players HTML**

Replace the Players panel block with:

```html
<div class="panel" id="panel-players">
  <div class="players-layout">
    <section class="players-card players-list-card">
      <div class="players-card-header">
        <div class="players-card-title">Online Players</div>
        <span class="players-card-badge" id="players-online-badge">0 online</span>
      </div>
      <p class="no-players" id="no-players-msg">No players online.</p>
      <div class="player-table-wrap">
        <table class="player-table" id="player-table" style="display:none">
          <thead><tr><th>Name</th><th>World</th><th>Ping</th><th>Actions</th></tr></thead>
          <tbody id="player-tbody"></tbody>
        </table>
      </div>
    </section>
    <aside class="players-history">
      <section class="players-card">
        <div class="players-card-header">
          <div class="players-card-title">Join History</div>
          <span class="players-card-badge">24h</span>
        </div>
        <div class="history-list" id="player-event-history"></div>
      </section>
      <section class="players-card">
        <div class="players-card-header">
          <div class="players-card-title">Player Commands</div>
          <span class="players-card-badge">24h</span>
        </div>
        <div class="history-list" id="player-command-history"></div>
      </section>
    </aside>
  </div>
</div>
```

- [ ] **Step 4: Update Players animation selector**

Change `animatePlayersPanel()` to animate cards and rows:

```js
function animatePlayersPanel(force=false){
  const signature=lastPlayersStructureSignature;
  if(!force&&signature===lastAnimatedPlayersSignature) return;
  lastAnimatedPlayersSignature=signature;
  const nodes=[...document.querySelectorAll('#panel-players .players-card'),...document.querySelectorAll('#player-tbody tr')];
  staggerAnimate(nodes.length?nodes:[$('no-players-msg')],26);
}
```

- [ ] **Step 5: Commit layout changes**

Run:

```powershell
git add src/main/resources/webconsole.html
git commit -m "fix(players): improve responsive layout"
```

Expected: commit succeeds.

## Task 5: Render Player Activity History

**Files:**
- Modify: `src/main/resources/webconsole.html`

- [ ] **Step 1: Add state signatures**

Near existing player signatures, add:

```js
let lastPlayerEventsSignature='__init__';
let lastPlayerCommandsSignature='__init__';
```

- [ ] **Step 2: Call history renderers from stats**

In `handleStats(data)`, after `renderPlayers(data.playerList)`, add:

```js
renderPlayerEvents(data.playerEvents||[]);
renderPlayerCommands(data.playerCommands||[]);
```

- [ ] **Step 3: Update online badge in `renderPlayers()`**

At the start of `renderPlayers(list)`, after `safeList`, add:

```js
setText('players-online-badge', safeList.length+' online');
```

- [ ] **Step 4: Add history render functions**

Add after `renderPlayers()`:

```js
function renderPlayerEvents(events){
  const box=$('player-event-history'); if(!box) return;
  const safe=Array.isArray(events)?events.slice(-80).reverse():[];
  const sig=JSON.stringify(safe);
  if(sig===lastPlayerEventsSignature) return;
  lastPlayerEventsSignature=sig;
  if(!safe.length){ box.innerHTML='<div class="history-empty">No join history in the last 24 hours.</div>'; return; }
  box.innerHTML=safe.map(e=>{
    const type=e.type==='leave'?'leave':'join';
    const label=type==='leave'?'Left':'Joined';
    return `<div class="history-item ${type}"><span class="history-kind">${label}</span><span class="history-main"><strong>${esc(e.player||'Unknown')}</strong></span><span class="history-time">${fmtTime(e.timestamp)}</span></div>`;
  }).join('');
}

function renderPlayerCommands(commands){
  const box=$('player-command-history'); if(!box) return;
  const safe=Array.isArray(commands)?commands.slice(-120).reverse():[];
  const sig=JSON.stringify(safe);
  if(sig===lastPlayerCommandsSignature) return;
  lastPlayerCommandsSignature=sig;
  if(!safe.length){ box.innerHTML='<div class="history-empty">No player commands in the last 24 hours.</div>'; return; }
  box.innerHTML=safe.map(c=>`<div class="history-item command"><span class="history-kind">CMD</span><span class="history-main"><strong>${esc(c.player||'Unknown')}</strong><span class="history-command">${esc(c.command||'')}</span></span><span class="history-time">${fmtTime(c.timestamp)}</span></div>`).join('');
}
```

- [ ] **Step 5: Add time formatter**

Near `fmtDuration()`, add:

```js
function fmtTime(timestamp){
  const d=new Date(Number(timestamp)||0);
  if(Number.isNaN(d.getTime())) return '--:--';
  return d.toLocaleTimeString('en-GB',{hour:'2-digit',minute:'2-digit'});
}
```

- [ ] **Step 6: Reset history signatures on logout**

In `doLogout()`, near player signature resets, add:

```js
lastPlayerEventsSignature='__init__'; lastPlayerCommandsSignature='__init__';
```

- [ ] **Step 7: Commit history rendering**

Run:

```powershell
git add src/main/resources/webconsole.html
git commit -m "feat(players): show activity history"
```

Expected: commit succeeds.

## Task 6: Center and Improve Dashboard

**Files:**
- Modify: `src/main/resources/webconsole.html`

- [ ] **Step 1: Center dashboard layout**

Change `.dash-layout` CSS to include horizontal auto margins:

```css
.dash-layout{width:min(100%,1760px);margin:0 auto;display:grid;grid-template-columns:minmax(0,1.36fr) minmax(360px,.64fr);gap:14px;align-items:start}
```

- [ ] **Step 2: Add dashboard summary cards**

In the Dashboard panel, before `Server Health`, add:

```html
<div class="dash-section">
  <div class="dash-section-title">Overview</div>
  <div class="dash-grid machine">
    <div class="kpi green" id="kpi-health-card"><div class="kpi-label">Server Health</div><div class="kpi-value" id="kpi-health">--</div><div class="kpi-sub" id="kpi-health-sub">Waiting for stats</div></div>
    <div class="kpi purple"><div class="kpi-label">Player Capacity</div><div class="kpi-value" id="kpi-capacity">--</div><div class="kpi-sub" id="kpi-capacity-sub">Online slots used</div></div>
    <div class="kpi blue"><div class="kpi-label">Plugin Uptime</div><div class="kpi-value" id="kpi-uptime">--</div><div class="kpi-sub">Since plugin enable</div></div>
    <div class="kpi warn"><div class="kpi-label">Recent Commands</div><div class="kpi-value" id="kpi-player-cmds">0</div><div class="kpi-sub">Player commands / 24h</div></div>
  </div>
</div>
```

- [ ] **Step 3: Update dashboard summary in `handleStats()`**

After `const players=...`, add:

```js
const capacity=Math.round(players/(maxPlayers||1)*100);
setText('kpi-capacity', capacity+'%');
setText('kpi-capacity-sub', players+' of '+maxPlayers+' slots');
setText('kpi-uptime', fmtDuration(data.uptimeSeconds));
setText('kpi-player-cmds', Array.isArray(data.playerCommands)?data.playerCommands.length:0);
const healthBad=tps<15||sessionErrors>0;
const healthWarn=!healthBad&&(tps<18||capacity>=90);
setText('kpi-health', healthBad?'Critical':healthWarn?'Watch':'Good');
setText('kpi-health-sub', healthBad?'Needs attention':healthWarn?'Monitor server':'All core signals normal');
const healthCard=$('kpi-health-card');
if(healthCard) healthCard.className='kpi '+(healthBad?'red':healthWarn?'warn':'green');
```

- [ ] **Step 4: Improve mobile dashboard CSS**

Update media rules:

```css
@media (max-width:760px){
  #panel-dash{padding:12px;align-items:stretch}
  .dash-layout{width:100%}
  .dash-charts,.machine-grid{grid-template-columns:1fr}
  .dash-grid,.dash-grid.primary,.dash-grid.machine{grid-template-columns:repeat(auto-fit,minmax(145px,1fr))}
  .kpi{padding:12px}
  .kpi-value{font-size:22px;overflow-wrap:anywhere}
  .metric-v{white-space:normal;word-break:break-word}
}
```

- [ ] **Step 5: Commit dashboard work**

Run:

```powershell
git add src/main/resources/webconsole.html
git commit -m "feat(dashboard): improve overview and centering"
```

Expected: commit succeeds.

## Task 7: Build Verification

**Files:**
- Verify whole project.

- [ ] **Step 1: Run package build**

Run:

```powershell
mvn -q -DskipTests package
```

Expected: build exits with code 0. If dependency download fails with `ECONNRESET`, timeout, or another network error, report that generation is not verified and only static review was completed.

- [ ] **Step 2: Inspect git status**

Run:

```powershell
git status --short
```

Expected: only intentionally uncommitted plan docs remain, or clean if the plan was committed.

- [ ] **Step 3: Final report**

Report:

- Commits created.
- Build result.
- Dev server was not started.

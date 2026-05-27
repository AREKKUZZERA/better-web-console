import { useCallback, useEffect, useState } from 'react';
import type { CompatibilityInfo, ErrorGroup } from '../types';
import { getCompatibility, getErrorGroups } from '../webconsole/api';
import { useActivePanel, useWebConsoleLanguage } from './useWebConsoleRuntime';

function DiagnosticsBlocks() {
  const active = useActivePanel('dash');
  const { t } = useWebConsoleLanguage();
  const [compat, setCompat] = useState<CompatibilityInfo | null>(null);
  const [errors, setErrors] = useState<ErrorGroup[]>([]);

  const load = useCallback(async () => {
    const [compatData, errorData] = await Promise.allSettled([getCompatibility(), getErrorGroups()]);
    if (compatData.status === 'fulfilled') setCompat(compatData.value as CompatibilityInfo);
    if (errorData.status === 'fulfilled') setErrors(Array.isArray(errorData.value.groups) ? errorData.value.groups : []);
  }, []);

  useEffect(() => {
    if (!active) return;
    void load();
    const timer = window.setInterval(() => void load(), 15000);
    return () => window.clearInterval(timer);
  }, [active, load]);

  return (
    <div className="dash-section">
      <div className="dash-section-title">{t('diag.title')}</div>
      <div className="machine-grid">
        <div className="machine-card">
          <h3>{t('diag.compatibility')}</h3>
          <div className="metric-row"><span className="metric-k">BWC</span><span className="metric-v">{compat?.pluginVersion || '--'}</span></div>
          <div className="metric-row"><span className="metric-k">Jar</span><span className="metric-v">{compat?.jarLine || '--'}</span></div>
          <div className="metric-row"><span className="metric-k">Server</span><span className="metric-v">{compat?.detectedServerLine || '--'}</span></div>
          <div className="metric-row"><span className="metric-k">Java</span><span className="metric-v">{compat?.javaVersion || '--'}</span></div>
          <div className={`compat-banner ${compat && !compat.compatible ? 'bad' : 'good'}`}>{compat && !compat.compatible ? t('diag.compatWarn') : t('diag.compatOk')}</div>
        </div>
        <div className="machine-card">
          <h3>{t('diag.errorGroups')}</h3>
          <div className="error-group-list">
            {errors.length ? errors.slice(0, 5).map(group => (
              <div className="error-group" key={group.signature}>
                <span className="error-count">{group.count}</span>
                <span className="error-sig" title={group.sample}>{group.signature}</span>
              </div>
            )) : <div className="history-empty">{t('diag.noErrors')}</div>}
          </div>
        </div>
      </div>
    </div>
  );
}

export function DashboardPanel() {
  return (
    <div className="panel" id="panel-dash">
        <div className="dash-layout">
          <div className="dash-main">
            <div className="dash-section">
              <div className="dash-section-title" data-i18n="dash.overview">Overview</div>
              <div className="dash-grid machine">
                <div className="kpi green" id="kpi-health-card"><div className="kpi-label" data-i18n="dash.serverHealth">Server Health</div><div className="kpi-value" id="kpi-health">--</div><div className="kpi-sub" id="kpi-health-sub" data-i18n="dash.waitingStats">Waiting for stats</div><div className="health-reasons" id="kpi-health-reasons"></div></div>
                <div className="kpi purple"><div className="kpi-label" data-i18n="dash.playerCapacity">Player Capacity</div><div className="kpi-value" id="kpi-capacity">--</div><div className="kpi-sub" id="kpi-capacity-sub" data-i18n="dash.onlineSlotsUsed">Online slots used</div></div>
                <div className="kpi blue"><div className="kpi-label" data-i18n="dash.pluginUptime">Plugin Uptime</div><div className="kpi-value" id="kpi-uptime">--</div><div className="kpi-sub" data-i18n="dash.sincePluginEnable">Since plugin enable</div></div>
                <div className="kpi warn"><div className="kpi-label" data-i18n="dash.recentCommands">Recent Commands</div><div className="kpi-value" id="kpi-player-cmds">0</div><div className="kpi-sub" data-i18n="dash.playerCommands24h">Player commands / 24h</div></div>
              </div>
            </div>
            <div className="dash-metric-groups">
              <div className="dash-section">
                <div className="dash-section-title" data-i18n="dash.serverHealth">Server Health</div>
                <div className="dash-grid primary" id="kpi-grid">
                  <div className="kpi green"><div className="kpi-label">TPS (1 min)</div><div className="kpi-value" id="kpi-tps">&#8212;</div><div className="kpi-sub" id="kpi-tps-sub" data-i18n="dash.tickRate">Server tick rate</div></div>
                  <div className="kpi blue"><div className="kpi-label" data-i18n="dash.ramUsed">RAM Used</div><div className="kpi-value" id="kpi-ram">&#8212;</div><div className="kpi-sub" id="kpi-ram-sub" data-i18n="dash.heapMemory">Heap memory</div></div>
                  <div className="kpi purple"><div className="kpi-label" data-i18n="dash.playersOnline">Players Online</div><div className="kpi-value" id="kpi-players">&#8212;</div><div className="kpi-sub" id="kpi-players-sub">of &#8212; max</div></div>
                  <div className="kpi warn"><div className="kpi-label" data-i18n="dash.worlds">Worlds</div><div className="kpi-value" id="kpi-worlds">&#8212;</div><div className="kpi-sub" id="kpi-worlds-sub">Loaded chunks: &#8212;</div></div>
                  <div className="kpi teal"><div className="kpi-label" data-i18n="dash.totalEntities">Total Entities</div><div className="kpi-value" id="kpi-entities">&#8212;</div><div className="kpi-sub" data-i18n="dash.acrossWorlds">Across all worlds</div></div>
                  <div className="kpi red"><div className="kpi-label" data-i18n="dash.sessionErrors">Errors (session)</div><div className="kpi-value" id="kpi-errors">0</div><div className="kpi-sub" id="kpi-errors-sub" data-i18n="dash.errorLines">SEVERE / ERROR lines</div></div>
                </div>
              </div>
              <div className="dash-section">
                <div className="dash-section-title" data-i18n="dash.machineHealth">Machine Health</div>
                <div className="dash-grid machine">
                  <div className="kpi green"><div className="kpi-label" data-i18n="dash.hostCpu">Host CPU</div><div className="kpi-value" id="kpi-cpu">&#8212;</div><div className="kpi-sub" id="kpi-cpu-sub" data-i18n="dash.systemLoad">System load</div></div>
                  <div className="kpi blue"><div className="kpi-label" data-i18n="dash.machineRam">Machine RAM</div><div className="kpi-value" id="kpi-host-ram">&#8212;</div><div className="kpi-sub" id="kpi-host-ram-sub" data-i18n="dash.physicalMemory">Physical memory</div></div>
                  <div className="kpi warn"><div className="kpi-label" data-i18n="dash.serverDisk">Server Disk</div><div className="kpi-value" id="kpi-disk">&#8212;</div><div className="kpi-sub" id="kpi-disk-sub" data-i18n="dash.worldContainer">World container</div></div>
                  <div className="kpi purple"><div className="kpi-label" data-i18n="dash.jvmThreads">JVM Threads</div><div className="kpi-value" id="kpi-threads">&#8212;</div><div className="kpi-sub" id="kpi-threads-sub" data-i18n="dash.javaProcess">Java process</div></div>
                </div>
              </div>
            </div>
            <div className="dash-section">
              <div className="dash-section-title" data-i18n="dash.performanceHistory">Performance History (5 min)</div>
              <div className="dash-charts">
                <div className="chart-card">
                  <div className="chart-card-header"><h3>TPS</h3><span className="chart-badge" id="badge-tps">&#8212;</span></div>
                  <div className="chart-wrap"><canvas id="chart-tps"></canvas></div>
                </div>
                <div className="chart-card">
                  <div className="chart-card-header"><h3>CPU (%)</h3><span className="chart-badge" id="badge-cpu">&#8212;</span></div>
                  <div className="chart-wrap"><canvas id="chart-cpu"></canvas></div>
                </div>
                <div className="chart-card">
                  <div className="chart-card-header"><h3>RAM (MB)</h3><span className="chart-badge" id="badge-ram">&#8212;</span></div>
                  <div className="chart-wrap"><canvas id="chart-ram"></canvas></div>
                </div>
                <div className="chart-card">
                  <div className="chart-card-header"><h3 data-i18n="stat.players">Players</h3><span className="chart-badge" id="badge-players">&#8212;</span></div>
                  <div className="chart-wrap"><canvas id="chart-players"></canvas></div>
                </div>
              </div>
            </div>
            <div className="dash-details-group">
              <div className="dash-section">
                <div className="dash-section-title" data-i18n="dash.machineDetails">Machine Details</div>
                <div className="machine-grid">
                  <div className="machine-card">
                    <h3>CPU</h3>
                    <div className="metric-row"><span className="metric-k" data-i18n="metric.model">Model</span><span className="metric-v" id="sys-cpu-model">&#8212;</span></div>
                    <div className="metric-row"><span className="metric-k" data-i18n="metric.coresThreads">Cores / Threads</span><span className="metric-v" id="sys-cpu-cores">&#8212;</span></div>
                    <div className="metric-row"><span className="metric-k" data-i18n="metric.processLoad">Process Load</span><span className="metric-v" id="sys-cpu-process">&#8212;</span></div>
                    <div className="meter"><div className="meter-fill" id="sys-cpu-meter"></div></div>
                  </div>
                  <div className="machine-card">
                    <h3 data-i18n="metric.memory">Memory</h3>
                    <div className="metric-row"><span className="metric-k" data-i18n="metric.machineUsed">Machine Used</span><span className="metric-v" id="sys-ram-used">&#8212;</span></div>
                    <div className="metric-row"><span className="metric-k" data-i18n="metric.machineFree">Machine Free</span><span className="metric-v" id="sys-ram-free">&#8212;</span></div>
                    <div className="metric-row"><span className="metric-k">JVM Heap</span><span className="metric-v" id="sys-jvm-heap">&#8212;</span></div>
                    <div className="meter"><div className="meter-fill" id="sys-ram-meter"></div></div>
                  </div>
                  <div className="machine-card">
                    <h3 data-i18n="metric.disk">Disk</h3>
                    <div className="metric-row"><span className="metric-k" data-i18n="metric.used">Used</span><span className="metric-v" id="sys-disk-used">&#8212;</span></div>
                    <div className="metric-row"><span className="metric-k" data-i18n="metric.free">Free</span><span className="metric-v" id="sys-disk-free">&#8212;</span></div>
                    <div className="metric-row"><span className="metric-k" data-i18n="metric.mount">Mount</span><span className="metric-v" id="sys-disk-mount">&#8212;</span></div>
                    <div className="meter"><div className="meter-fill" id="sys-disk-meter"></div></div>
                  </div>
                  <div className="machine-card">
                    <h3 data-i18n="metric.runtime">Runtime</h3>
                    <div className="metric-row"><span className="metric-k">OS</span><span className="metric-v" id="sys-os">&#8212;</span></div>
                    <div className="metric-row"><span className="metric-k">Java</span><span className="metric-v" id="sys-java">&#8212;</span></div>
                    <div className="metric-row"><span className="metric-k">PID</span><span className="metric-v" id="sys-pid">&#8212;</span></div>
                    <div className="metric-row"><span className="metric-k">JVM Uptime</span><span className="metric-v" id="sys-jvm-uptime">&#8212;</span></div>
                  </div>
                </div>
              </div>
              <DiagnosticsBlocks />
            </div>
          </div>
          <div className="dash-side">
            <div className="dash-section">
              <div className="dash-section-title" data-i18n="dash.analytics">Analytics</div>
              <div className="dash-charts">
                <div className="chart-card">
                  <div className="chart-card-header"><h3 data-i18n="dash.logBreakdown">Log Level Breakdown</h3><span className="chart-badge" id="badge-logcount">0 lines</span></div>
                  <div style={{ paddingTop: '6px' }}>
                    <div className="level-bar-row"><span className="level-bar-label" style={{ color: 'var(--danger)' }}>ERROR</span><div className="level-bar-track"><div className="level-bar-fill" id="bar-error" style={{ background: 'var(--danger)', width: '0%' }}></div></div><span className="level-bar-count" id="cnt-error">0</span></div>
                    <div className="level-bar-row"><span className="level-bar-label" style={{ color: 'var(--warn)' }}>WARN</span><div className="level-bar-track"><div className="level-bar-fill" id="bar-warn" style={{ background: 'var(--warn)', width: '0%' }}></div></div><span className="level-bar-count" id="cnt-warn">0</span></div>
                    <div className="level-bar-row"><span className="level-bar-label" style={{ color: 'var(--info)' }}>INFO</span><div className="level-bar-track"><div className="level-bar-fill" id="bar-info" style={{ background: 'var(--info)', width: '0%' }}></div></div><span className="level-bar-count" id="cnt-info">0</span></div>
                    <div className="level-bar-row"><span className="level-bar-label" style={{ color: 'var(--text-dim)' }}>DEBUG</span><div className="level-bar-track"><div className="level-bar-fill" id="bar-debug" style={{ background: 'var(--text-dim)', width: '0%' }}></div></div><span className="level-bar-count" id="cnt-debug">0</span></div>
                  </div>
                </div>
                <div className="chart-card">
                  <div className="chart-card-header"><h3 data-i18n="dash.worldBreakdown">World Breakdown</h3></div>
                  <div style={{ overflowY: 'auto', maxHeight: '150px', scrollbarWidth: 'thin' }}>
                    <table className="world-table">
                      <thead><tr><th data-i18n="dash.world">World</th><th data-i18n="dash.chunks">Chunks</th><th data-i18n="dash.entities">Entities</th></tr></thead>
                      <tbody id="world-tbody"><tr><td colSpan={3} style={{ color: 'var(--text-muted)', textAlign: 'center', padding: '12px 0' }} data-i18n="dash.noData">No data yet</td></tr></tbody>
                    </table>
                  </div>
                </div>
                <div className="chart-card">
                  <div className="chart-card-header"><h3 data-i18n="dash.activityFeed">Activity Feed</h3><span className="chart-badge" style={{ color: 'var(--success)' }} data-i18n="dash.live">live</span></div>
                  <div className="activity-feed" id="activity-feed">
                    <div className="activity-item">
                      <span className="activity-icon">&#128564;</span>
                      <span className="activity-text" style={{ color: 'var(--text-muted)' }} data-i18n="dash.waitingActivity">Waiting for activity&#8230;</span>
                    </div>
                  </div>
                </div>
                <div className="chart-card">
                  <div className="chart-card-header"><h3 data-i18n="players.topPlayers">Top Active Players</h3><span className="chart-badge" data-i18n="players.loadedHistory">loaded</span></div>
                  <div className="activity-feed" id="top-active-players"></div>
                </div>
                <div className="chart-card">
                  <div className="chart-card-header"><h3 data-i18n="dash.healthHistory">Health History</h3><span className="chart-badge" id="health-history-count">0</span></div>
                  <div className="health-history" id="health-history">
                    <div className="health-event-empty" data-i18n="dash.noHealthEvents">No health events yet</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
  );
}

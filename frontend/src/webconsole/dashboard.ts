// @ts-nocheck
import { esc } from './formatters';

export function getHealthStatus(snapshot) {
  if (!snapshot) return 'good';
  if (snapshot.tps < 15 || snapshot.sessionErrors > 0) return 'critical';
  if (snapshot.tps < 18 || (snapshot.hasMaxPlayers && snapshot.capacity >= 90)) return 'watch';
  return 'good';
}

export function getHealthReasons(snapshot, t) {
  if (!snapshot) return [];
  const reasons = [];
  if (snapshot.tps < 15) reasons.push({ code: 'tps', level: 'red', label: t('health.tpsLow'), title: 'TPS: ' + snapshot.tps.toFixed(1) + ' < 15' });
  else if (snapshot.tps < 18) reasons.push({ code: 'tps', level: 'warn', label: t('health.tpsDegraded'), title: 'TPS: ' + snapshot.tps.toFixed(1) + ' < 18' });
  if (snapshot.sessionErrors > 0) reasons.push({ code: 'errors', level: 'red', label: t('health.errors') + ': ' + snapshot.sessionErrors, title: t('dash.sessionErrors') + ': ' + snapshot.sessionErrors });
  if (snapshot.hasMaxPlayers && snapshot.capacity >= 90) reasons.push({ code: 'capacity', level: 'warn', label: t('health.capacity') + ': ' + snapshot.capacity + '%', title: t('dash.playerCapacity') + ': ' + snapshot.capacity + '%' });
  return reasons;
}

export function renderWorldRows(worlds) {
  const colors = ['var(--accent)', 'var(--info)', 'var(--purple)', 'var(--success)', 'var(--warn)'];
  const worldsSorted = [...(worlds || [])].sort((a, b) => ((b.entities || 0) + (b.chunks || 0)) - ((a.entities || 0) + (a.chunks || 0)));
  return worldsSorted.map((w, i) =>
    `<tr><td><span class="world-dot" style="background:${colors[i % colors.length]}"></span>${esc(w.name || '?')}</td><td>${w.chunks || 0}</td><td>${w.entities || 0}</td></tr>`
  ).join('');
}

export function renderTopPlayersHtml(summary, t) {
  const list = Array.isArray(summary.topPlayers) ? summary.topPlayers : [];
  if (!list.length) return `<div class="history-empty">${t('dash.noData')}</div>`;
  return list.map(p => `<div class="activity-item cmd"><span class="activity-icon">&#9889;</span><span class="activity-text"><strong class="notranslate" translate="no">${esc(p.player || t('players.unknown'))}</strong> ${p.score || 0} total</span><span class="activity-time">CMD ${p.commands || 0}</span></div>`).join('');
}

export function renderActivityHtml(activityLog, t) {
  if (!activityLog.length) return `<div class="activity-item"><span class="activity-icon">&#128564;</span><span class="activity-text" style="color:var(--text-muted)">${t('dash.waitingActivity')}</span></div>`;
  return activityLog.slice(0, 18).map(a => `<div class="activity-item ${a.type}"><span class="activity-icon">${a.icon}</span><span class="activity-text">${a.html ? a.text : esc(a.text)}</span><span class="activity-time">${a.t}</span></div>`).join('');
}

export function updateLevelBarElements(levelCounts, t) {
  const total = Math.max(1, levelCounts.INFO + levelCounts.WARN + levelCounts.SEVERE + levelCounts.DEBUG);
  const set = (fid, cid, v) => {
    const f = document.getElementById(fid), c = document.getElementById(cid);
    if (f) f.style.width = Math.round(v / total * 100) + '%';
    if (c) c.textContent = v;
  };
  set('bar-error', 'cnt-error', levelCounts.SEVERE);
  set('bar-warn', 'cnt-warn', levelCounts.WARN);
  set('bar-info', 'cnt-info', levelCounts.INFO);
  set('bar-debug', 'cnt-debug', levelCounts.DEBUG);
  const badge = document.getElementById('badge-logcount');
  if (badge) badge.textContent = t('fmt.lines', { count: levelCounts.INFO + levelCounts.WARN + levelCounts.SEVERE + levelCounts.DEBUG });
}

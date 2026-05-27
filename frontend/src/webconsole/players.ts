// @ts-nocheck
import { esc, fmtTime } from './formatters';

export function playerRowKey(p) {
  return String(p.uuid || p.name || '');
}

export function renderPlayerRow(row, p, currentLang, t) {
  const sig = JSON.stringify({ n: p.name || '', u: p.uuid || '', w: p.world || '', o: !!p.op, p: p.ping || 0 }) + '|' + currentLang;
  if (row.dataset.sig === sig) return;
  const ping = p.ping || 0;
  const pc = ping < 80 ? 'ping-good' : ping < 200 ? 'ping-ok' : 'ping-bad';
  const uuidButton = p.uuid ? `<button class="uuid-copy-btn" type="button" aria-label="${t('players.copyUuid')}" onclick="copyPlayerUuid('${esc(p.uuid)}')">UUID</button>` : '';
  row.dataset.sig = sig;
  row.innerHTML = `<td data-label="${t('players.name')}"><span class="player-name-cell"><span class="player-name-text notranslate" translate="no">${esc(p.name)}</span>${uuidButton}${p.op ? '<span class="player-op">OP</span>' : ''}</span></td><td data-label="${t('players.world')}">${esc(p.world || '\u2014')}</td><td data-label="${t('players.ping')}" class="${pc}">${ping}ms</td><td data-label="${t('players.actions')}"><div class="player-actions"><button class="action-btn kick" onclick="openModal('kick','${esc(p.name)}')">${t('players.kick')}</button><button class="action-btn ban" onclick="openModal('ban','${esc(p.name)}')">${t('players.ban')}</button><button class="action-btn neutral" onclick="openModal('msg','${esc(p.name)}')">${t('players.message')}</button><button class="action-btn neutral" onclick="openModal('gamemode','${esc(p.name)}')">${t('players.gamemode')}</button><button class="action-btn neutral" onclick="openModal('tp','${esc(p.name)}')">${t('players.teleport')}</button></div></td>`;
}

export function renderPlayerSummaryHtml(summary, t) {
  const joins = summary.joins || 0;
  const leaves = summary.leaves || 0;
  const commands = summary.commands || 0;
  return `<div class="player-summary-card"><span>${t('players.joined')}</span><strong>${joins}</strong></div><div class="player-summary-card"><span>${t('players.left')}</span><strong>${leaves}</strong></div><div class="player-summary-card"><span>CMD</span><strong>${commands}</strong></div>`;
}

export function buildActivityPlayerGroups(items, t) {
  const groups = [];
  items.forEach(item => {
    const player = item.player || t('players.unknown');
    const uuid = item.uuid || '';
    const key = player + '\u0000' + uuid;
    let group = groups.find(g => g.key === key);
    if (!group) {
      group = { key, player, uuid, items: [] };
      groups.push(group);
    }
    group.items.push(item);
  });
  return groups.sort((a, b) => {
    const at = Math.max(...a.items.map(item => Number(item.timestamp) || 0));
    const bt = Math.max(...b.items.map(item => Number(item.timestamp) || 0));
    return bt - at;
  });
}

export function renderActivityPlayerGroup(group, groupKind, dayKey, context) {
  const { activityScopedKey, expandedActivityPlayers, t } = context;
  const uuid = group.uuid || '';
  const uuidButton = uuid ? `<button class="uuid-copy-btn history-uuid-copy" type="button" aria-label="${t('players.copyUuid')}" onclick="copyPlayerUuid('${esc(uuid)}')">UUID</button>` : '';
  const playerKey = activityScopedKey(groupKind, dayKey + ':' + group.key);
  const encodedDay = encodeURIComponent(dayKey);
  const encodedPlayer = encodeURIComponent(group.key);
  const expanded = expandedActivityPlayers.has(playerKey);
  const latest = group.items.reduce((best, item) => (Number(item.timestamp) || 0) > (Number(best.timestamp) || 0) ? item : best, group.items[0]);
  const joins = group.items.filter(item => item.type === 'join').length;
  const leaves = group.items.filter(item => item.type === 'leave').length;
  const meta = groupKind === 'commands'
    ? `<span class="history-player-meta">CMD ${group.items.length}</span>`
    : `<span class="history-player-meta"><span class="history-join-count">${joins}</span>/<span class="history-leave-count">${leaves}</span></span>`;
  return `<div class="history-player-group${expanded ? ' expanded' : ' collapsed'}"><div class="history-player-name"><button class="history-player-toggle" type="button" onclick="toggleActivityPlayer('${groupKind}','${encodedDay}','${encodedPlayer}')"><span class="history-player-caret">${expanded ? '▾' : '▸'}</span><span class="history-player-label notranslate" translate="no">${esc(group.player)}</span></button>${uuidButton}${meta}<span class="history-player-count">${group.items.length}</span><span class="history-player-last">${fmtTime(latest && latest.timestamp)}</span></div><div class="history-player-items">${group.items.map(item => renderActivityItem(item, t)).join('')}</div></div>`;
}

export function renderActivityItem(item, t) {
  if (item.type === 'command') return `<div class="history-item command"><span class="history-kind">CMD</span><span class="history-main"><span class="history-command">${esc(item.command || '')}</span></span><span class="history-time">${fmtTime(item.timestamp)}</span></div>`;
  const type = item.type === 'leave' ? 'leave' : 'join';
  const label = type === 'leave' ? t('players.left') : t('players.joined');
  return `<div class="history-item ${type}"><span class="history-kind">${label}</span><span class="history-main"></span><span class="history-time">${fmtTime(item.timestamp)}</span></div>`;
}

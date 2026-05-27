// @ts-nocheck
import { esc } from './formatters';
import { buildActivityPlayerGroups, renderActivityPlayerGroup } from './players';

export function createPlayerActivityController({ $, t, getCurrentLang }) {
  let lastSectionSignatures = { events: '__init__', commands: '__init__' };
  let expandedDays = new Set();
  let expandedGroups = new Set();
  let expandedPlayers = new Set();
  let initializedDays = new Set();
  let lastDays = [];
  const heightAnimations = new WeakMap();

  function renderDays(days) {
    const eventsBox = $('player-event-history'), commandsBox = $('player-command-history');
    if (!eventsBox || !commandsBox) return;
    const safe = Array.isArray(days) ? days : [];
    lastDays = safe;
    ensureInitialDay(safe, item => item.type === 'join' || item.type === 'leave', 'events');
    ensureInitialDay(safe, item => item.type === 'command', 'commands');
    renderSection(eventsBox, safe, item => item.type === 'join' || item.type === 'leave', t('players.noJoinHistory'), 'events');
    renderSection(commandsBox, safe, item => item.type === 'command', t('players.noCommands'), 'commands');
  }

  function invalidateLanguage() {
    lastSectionSignatures = { events: '__lang__', commands: '__lang__' };
  }

  function reset() {
    lastSectionSignatures = { events: '__init__', commands: '__init__' };
    expandedDays = new Set();
    expandedGroups = new Set();
    expandedPlayers = new Set();
    initializedDays = new Set();
    lastDays = [];
    const eventHistory = $('player-event-history'); if (eventHistory) eventHistory.innerHTML = '';
    const commandHistory = $('player-command-history'); if (commandHistory) commandHistory.innerHTML = '';
  }

  function dayKey(day, idx) {
    return day.date || day.label || String(idx);
  }

  function scopedKey(groupKind, key) {
    return groupKind + ':' + key;
  }

  function ensureInitialDay(days, predicate, groupKind) {
    if (initializedDays.has(groupKind)) return;
    const idx = days.findIndex(day => Array.isArray(day.items) && day.items.some(predicate));
    if (idx < 0) return;
    expandedDays.add(scopedKey(groupKind, dayKey(days[idx], idx)));
    initializedDays.add(groupKind);
  }

  function scopedState(groupKind, set) {
    return [...set].filter(key => key.startsWith(groupKind + ':')).sort().join(',');
  }

  function renderSection(box, days, predicate, emptyText, groupKind) {
    const sig = JSON.stringify(days) + '|' + getCurrentLang() + '|' + scopedState(groupKind, expandedDays) + '|' + scopedState(groupKind, expandedGroups) + '|' + scopedState(groupKind, expandedPlayers);
    if (sig === lastSectionSignatures[groupKind]) return;
    const animate = lastSectionSignatures[groupKind] !== '__init__';
    lastSectionSignatures[groupKind] = sig;
    const html = days.length ? renderGroups(days, predicate, emptyText, groupKind) : `<div class="history-empty">${emptyText}</div>`;
    renderHtml(box, html, animate);
  }

  function renderHtml(box, html, animate) {
    const canAnimate = animate && window.matchMedia && !window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (!canAnimate) { box.innerHTML = html; return; }
    cancelHeightAnimation(box);
    const from = box.getBoundingClientRect().height;
    box.innerHTML = html;
    const to = box.getBoundingClientRect().height;
    if (Math.abs(from - to) < 1) return;
    box.style.overflow = 'hidden';
    box.style.height = from + 'px';
    box.getBoundingClientRect();
    const anim = box.animate([{ height: from + 'px' }, { height: to + 'px' }], { duration: 180, easing: 'cubic-bezier(.22,1,.36,1)' });
    heightAnimations.set(box, { cancel: () => anim.cancel() });
    box.style.height = to + 'px';
    anim.onfinish = () => finishHeightAnimation(box, true);
    anim.oncancel = () => finishHeightAnimation(box, true);
  }

  function motionEnabled() {
    return !!(window.matchMedia && !window.matchMedia('(prefers-reduced-motion: reduce)').matches);
  }

  function cancelHeightAnimation(el) {
    const active = heightAnimations.get(el);
    if (active) active.cancel();
    heightAnimations.delete(el);
  }

  function finishHeightAnimation(el, expanded) {
    heightAnimations.delete(el);
    el.style.transition = '';
    el.style.opacity = '';
    el.style.height = expanded ? '' : '0px';
    el.style.overflow = expanded ? '' : 'hidden';
  }

  function animateHeight(el, expanded, applyState) {
    if (!el) { applyState(); return; }
    if (!motionEnabled()) {
      cancelHeightAnimation(el);
      applyState();
      finishHeightAnimation(el, expanded);
      return;
    }
    cancelHeightAnimation(el);
    const from = el.getBoundingClientRect().height;
    el.style.transition = 'none';
    el.style.height = from + 'px';
    el.style.overflow = 'hidden';
    el.style.opacity = from > 0 ? '1' : '0';
    el.getBoundingClientRect();
    applyState();
    const to = expanded ? el.scrollHeight : 0;
    if (Math.abs(from - to) < 1) {
      finishHeightAnimation(el, expanded);
      return;
    }
    const onEnd = e => {
      if (e.target !== el || e.propertyName !== 'height') return;
      clearTimeout(timer);
      el.removeEventListener('transitionend', onEnd);
      finishHeightAnimation(el, expanded);
    };
    const timer = window.setTimeout(() => {
      el.removeEventListener('transitionend', onEnd);
      finishHeightAnimation(el, expanded);
    }, 240);
    heightAnimations.set(el, { cancel: () => {
      clearTimeout(timer);
      el.removeEventListener('transitionend', onEnd);
    } });
    requestAnimationFrame(() => {
      el.addEventListener('transitionend', onEnd);
      el.style.transition = 'height 180ms cubic-bezier(.22,1,.36,1), opacity 120ms var(--ease)';
      el.style.height = to + 'px';
      el.style.opacity = expanded ? '1' : '0';
    });
  }

  function renderGroups(days, predicate, emptyText, groupKind) {
    const html = days.map((day, idx) => {
      const items = (day.items || []).filter(predicate);
      if (!items.length) return '';
      const key = dayKey(day, idx);
      const dayScopedKey = scopedKey(groupKind, key);
      const groupKey = scopedKey(groupKind, key);
      const isGroupExpanded = expandedGroups.has(groupKey);
      const playerGroups = buildActivityPlayerGroups(items, t);
      const visibleGroups = isGroupExpanded ? playerGroups : playerGroups.slice(0, 4);
      const hiddenCount = playerGroups.length - visibleGroups.length;
      const collapsed = expandedDays.has(dayScopedKey) ? '' : ' collapsed';
      const encodedKey = encodeURIComponent(key);
      const moreButton = hiddenCount > 0 || isGroupExpanded ? `<button class="history-more" onclick="toggleActivityGroup('${groupKind}','${encodedKey}')">${isGroupExpanded ? t('players.showLess') : t('players.showMore', { count: hiddenCount })}</button>` : '';
      return `<div class="history-day${collapsed}" data-day="${esc(key)}" data-day-key="${encodedKey}"><button class="history-day-head" onclick="toggleActivityDay('${groupKind}','${encodedKey}')"><span>${esc(day.label || key)}</span><span>${items.length}</span></button><div class="history-day-body"><div class="history-day-content">${visibleGroups.map(group => renderActivityPlayerGroup(group, groupKind, key, { activityScopedKey: scopedKey, expandedActivityPlayers: expandedPlayers, t })).join('')}${moreButton}</div></div></div>`;
    }).join('');
    return html || `<div class="history-empty">${emptyText}</div>`;
  }

  function rerenderLastDays() {
    lastSectionSignatures = { events: '__force__', commands: '__force__' };
    renderDays(lastDays);
  }

  function toggleDay(groupKind, encodedKey) {
    const key = scopedKey(groupKind, decodeURIComponent(encodedKey));
    const isExpanded = expandedDays.has(key);
    const box = $(groupKind === 'commands' ? 'player-command-history' : 'player-event-history');
    const day = box && box.querySelector(`.history-day[data-day-key="${encodedKey}"]`);
    const body = day && day.querySelector('.history-day-body');
    if (isExpanded) expandedDays.delete(key); else expandedDays.add(key);
    if (!day || !body) {
      rerenderLastDays();
      return;
    }
    animateHeight(body, !isExpanded, () => { if (day) day.classList.toggle('collapsed', isExpanded); });
  }

  function toggleGroup(groupKind, encodedKey) {
    const key = groupKind + ':' + decodeURIComponent(encodedKey);
    if (expandedGroups.has(key)) expandedGroups.delete(key); else expandedGroups.add(key);
    rerenderLastDays();
  }

  function togglePlayer(groupKind, encodedDay, encodedPlayer) {
    const playerKey = scopedKey(groupKind, decodeURIComponent(encodedDay) + ':' + decodeURIComponent(encodedPlayer));
    const isExpanded = expandedPlayers.has(playerKey);
    if (isExpanded) expandedPlayers.delete(playerKey); else expandedPlayers.add(playerKey);
    const box = $(groupKind === 'commands' ? 'player-command-history' : 'player-event-history');
    const day = box && box.querySelector(`.history-day[data-day-key="${encodedDay}"]`);
    const group = day && [...day.querySelectorAll('.history-player-group')].find(el => {
      const btn = el.querySelector('.history-player-toggle');
      return btn && btn.getAttribute('onclick') === `toggleActivityPlayer('${groupKind}','${encodedDay}','${encodedPlayer}')`;
    });
    const items = group && group.querySelector('.history-player-items');
    if (!group || !items) {
      rerenderLastDays();
      return;
    }
    animateHeight(items, !isExpanded, () => {
      if (!group) return;
      group.classList.toggle('expanded', !isExpanded);
      group.classList.toggle('collapsed', isExpanded);
      const caret = group.querySelector('.history-player-caret');
      if (caret) caret.textContent = isExpanded ? '\u25b8' : '\u25be';
    });
  }

  window.toggleActivityDay = toggleDay;
  window.toggleActivityGroup = toggleGroup;
  window.toggleActivityPlayer = togglePlayer;

  return { renderDays, invalidateLanguage, reset };
}

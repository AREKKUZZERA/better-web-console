import { useMemo, useState } from 'react';
import type { PlayerActivitySummary, PlayerInfo, PlayerProfile } from '../types';
import { getPlayerProfile } from '../webconsole/api';
import { useWebConsoleEvent, useWebConsoleLanguage } from './useWebConsoleRuntime';

function playerKey(player: PlayerInfo) {
  return String(player.uuid || player.name || '');
}

function openPlayerAction(action: string, player: string) {
  const opener = (window as typeof window & { openModal?: (action: string, player: string) => void }).openModal;
  if (opener) opener(action, player);
}

function copyPlayerUuid(uuid: string, t: (key: string, params?: Record<string, string | number>) => string) {
  if (!uuid) return;
  if (navigator.clipboard?.writeText) {
    void navigator.clipboard.writeText(uuid);
  } else {
    const input = document.createElement('textarea');
    input.value = uuid;
    input.style.position = 'fixed';
    input.style.opacity = '0';
    document.body.appendChild(input);
    input.select();
    document.execCommand('copy');
    input.remove();
  }
  const toast = (window as typeof window & { showToast?: (message: string, type?: string) => void }).showToast;
  if (toast) toast(t('players.uuidCopied'), 'success');
}

export function PlayersPanel() {
  const { t } = useWebConsoleLanguage();
  const [players, setPlayers] = useState<PlayerInfo[]>([]);
  const [summary, setSummary] = useState<PlayerActivitySummary>({});
  const [search, setSearch] = useState('');
  const [world, setWorld] = useState('');
  const [profile, setProfile] = useState<PlayerProfile | null>(null);
  const [profileError, setProfileError] = useState('');

  useWebConsoleEvent('webconsole:players', detail => {
    setPlayers(Array.isArray(detail.players) ? detail.players : []);
    setSummary((detail.summary || {}) as PlayerActivitySummary);
  });

  const worlds = useMemo(() => {
    return [...new Set(players.map(player => player.world).filter(Boolean))].sort();
  }, [players]);

  const filteredPlayers = useMemo(() => {
    const query = search.trim().toLowerCase();
    return players.filter(player => {
      if (query && !(player.name || '').toLowerCase().includes(query)) return false;
      if (world && (player.world || '') !== world) return false;
      return true;
    });
  }, [players, search, world]);

  const selectedWorld = worlds.includes(world) ? world : '';
  const joins = summary.joins || 0;
  const leaves = summary.leaves || 0;
  const commands = summary.commands || 0;

  const openProfile = async (player: PlayerInfo) => {
    setProfileError('');
    try {
      setProfile(await getPlayerProfile(player.uuid || player.name));
    } catch {
      setProfileError(t('players.profileError'));
    }
  };

  return (
    <div className="panel" id="panel-players">
        <div className="players-layout">
          <section className="players-card players-list-card">
            <div className="players-card-header">
              <div className="players-card-title" data-i18n="players.onlinePlayers">{t('players.onlinePlayers')}</div>
              <span className="players-card-badge" id="players-online-badge">{t('players.online', { count: players.length })}</span>
            </div>
            <div className="players-toolbar">
              <input id="player-search" name="playerSearch" className="player-filter" type="search" placeholder={t('players.searchPlaceholder')} data-i18n-placeholder="players.searchPlaceholder" value={search} onChange={event => setSearch(event.target.value)} />
              <select id="player-world-filter" name="playerWorldFilter" className="player-filter" value={selectedWorld} onChange={event => setWorld(event.target.value)}>
                <option value="" data-i18n="players.allWorlds">{t('players.allWorlds')}</option>
                {worlds.map(worldName => <option value={worldName} key={worldName}>{worldName}</option>)}
              </select>
            </div>
            <div className="player-summary" id="player-summary">
              <div className="player-summary-card"><span>{t('players.joined')}</span><strong>{joins}</strong></div>
              <div className="player-summary-card"><span>{t('players.left')}</span><strong>{leaves}</strong></div>
              <div className="player-summary-card"><span>CMD</span><strong>{commands}</strong></div>
            </div>
            <p className="no-players" id="no-players-msg" data-i18n="players.noneOnline" style={{ display: filteredPlayers.length ? 'none' : '' }}>{t('players.noneOnline')}</p>
            <div className="player-table-wrap">
              <table className="player-table" id="player-table" style={{ display: filteredPlayers.length ? '' : 'none' }}>
                <thead><tr><th data-i18n="players.name">{t('players.name')}</th><th data-i18n="players.world">{t('players.world')}</th><th data-i18n="players.ping">{t('players.ping')}</th><th data-i18n="players.actions">{t('players.actions')}</th></tr></thead>
                <tbody id="player-tbody">
                  {filteredPlayers.map(player => {
                    const ping = player.ping || 0;
                    const pingClass = ping < 80 ? 'ping-good' : ping < 200 ? 'ping-ok' : 'ping-bad';
                    return (
                      <tr key={playerKey(player)} data-player-key={playerKey(player)} onClick={() => void openProfile(player)}>
                        <td data-label={t('players.name')}>
                          <span className="player-name-cell">
                            <span className="player-name-text notranslate" translate="no">{player.name}</span>
                            {player.uuid ? <button className="uuid-copy-btn" type="button" aria-label={t('players.copyUuid')} onClick={() => copyPlayerUuid(player.uuid, t)}>UUID</button> : null}
                            {player.op ? <span className="player-op">OP</span> : null}
                          </span>
                        </td>
                        <td data-label={t('players.world')}>{player.world || '—'}</td>
                        <td data-label={t('players.ping')} className={pingClass}>{ping}ms</td>
                        <td data-label={t('players.actions')}>
                          <div className="player-actions" onClick={event => event.stopPropagation()}>
                            <button className="action-btn kick" type="button" onClick={() => openPlayerAction('kick', player.name)}>{t('players.kick')}</button>
                            <button className="action-btn ban" type="button" onClick={() => openPlayerAction('ban', player.name)}>{t('players.ban')}</button>
                            <button className="action-btn neutral" type="button" onClick={() => openPlayerAction('msg', player.name)}>{t('players.message')}</button>
                            <button className="action-btn neutral" type="button" onClick={() => openPlayerAction('gamemode', player.name)}>{t('players.gamemode')}</button>
                            <button className="action-btn neutral" type="button" onClick={() => openPlayerAction('tp', player.name)}>{t('players.teleport')}</button>
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </section>
          <aside className="players-history">
            <section className="players-card">
              <div className="players-card-header">
                <div className="players-card-title" data-i18n="players.joinHistory">{t('players.joinHistory')}</div>
                <span className="players-card-badge" data-i18n="players.groupedByDay">{t('players.groupedByDay')}</span>
              </div>
              <div className="history-list" id="player-event-history"></div>
            </section>
            <section className="players-card">
              <div className="players-card-header">
                <div className="players-card-title" data-i18n="players.playerCommands">{t('players.playerCommands')}</div>
                <span className="players-card-badge" data-i18n="players.groupedByDay">{t('players.groupedByDay')}</span>
              </div>
              <div className="history-list" id="player-command-history"></div>
            </section>
          </aside>
        </div>
        {profile || profileError ? (
          <div className="player-drawer-backdrop" onClick={() => { setProfile(null); setProfileError(''); }}>
            <aside className="player-drawer" onClick={event => event.stopPropagation()}>
              <div className="players-card-header">
                <div className={`players-card-title${profile?.name ? ' notranslate' : ''}`} translate={profile?.name ? 'no' : undefined}>{profile?.name || t('players.profile')}</div>
                <button className="action-btn neutral" type="button" onClick={() => { setProfile(null); setProfileError(''); }}>X</button>
              </div>
              {profileError ? <div className="session-empty session-error">{profileError}</div> : null}
              {profile ? (
                <>
                  <div className="profile-grid">
                    <div><span>UUID</span><strong>{profile.uuid}</strong></div>
                    <div><span>{t('players.world')}</span><strong>{profile.world}</strong></div>
                    <div><span>{t('players.gamemode')}</span><strong>{profile.gamemode}</strong></div>
                    <div><span>{t('players.ping')}</span><strong>{profile.ping}ms</strong></div>
                    <div><span>HP</span><strong>{profile.health}/{profile.maxHealth}</strong></div>
                    <div><span>XYZ</span><strong>{profile.x} / {profile.y} / {profile.z}</strong></div>
                    <div><span>{t('players.food')}</span><strong>{profile.food}</strong></div>
                    <div><span>{t('players.level')}</span><strong>{profile.level}</strong></div>
                  </div>
                  <div className="players-card-title profile-history-title">{t('players.history')}</div>
                  <div className="history-list profile-history">
                    {profile.history?.length ? profile.history.slice().reverse().map((item, index) => (
                      <div className={`history-item ${item.type}`} key={`${item.timestamp}-${index}`}>
                        <span className="history-kind">{item.type === 'command' ? 'CMD' : item.type}</span>
                        <span className={`history-main${item.command ? '' : ' notranslate'}`} translate={item.command ? undefined : 'no'}>{item.command || item.player}</span>
                        <span className="history-time">{new Date(item.timestamp).toLocaleTimeString()}</span>
                      </div>
                    )) : <div className="history-empty">{t('dash.noData')}</div>}
                  </div>
                </>
              ) : null}
            </aside>
          </div>
        ) : null}
      </div>
  );
}

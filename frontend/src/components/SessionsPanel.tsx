import { useCallback, useEffect, useState } from 'react';
import type { SessionInfo } from '../types';
import { getSessions } from '../webconsole/api';
import { fmtDateTime, fmtDuration } from '../webconsole/formatters';
import { useActivePanel, useWebConsoleLanguage } from './useWebConsoleRuntime';

export function SessionsPanel() {
  const active = useActivePanel('sessions');
  const { language, t } = useWebConsoleLanguage();
  const [sessions, setSessions] = useState<SessionInfo[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);

  const loadSessions = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getSessions();
      setSessions(Array.isArray(data.sessions) ? data.sessions : []);
      setLoaded(true);
      setError(false);
    } catch {
      setSessions([]);
      setLoaded(true);
      setError(true);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (!active) return;
    void loadSessions();
    const timer = window.setInterval(() => void loadSessions(), 10000);
    return () => window.clearInterval(timer);
  }, [active, loadSessions]);

  const showTable = !error && sessions.length > 0;
  const emptyText = error ? t('sessions.error') : loading && !loaded ? t('sessions.loading') : t('sessions.empty');

  return (
    <div className="panel" id="panel-sessions">
      <div className="sessions-layout">
        <section className="players-card">
          <div className="players-card-header">
            <div className="players-card-title" data-i18n="sessions.active">{t('sessions.active')}</div>
            <span className="players-card-badge" id="sessions-count">{error ? '!' : sessions.length}</span>
          </div>
          <div className="player-table-wrap">
            <table className="sessions-table" id="sessions-table" style={{ display: showTable ? '' : 'none' }}>
              <thead><tr><th data-i18n="sessions.user">{t('sessions.user')}</th><th data-i18n="sessions.ip">{t('sessions.ip')}</th><th data-i18n="sessions.lastSeen">{t('sessions.lastSeen')}</th><th data-i18n="sessions.expires">{t('sessions.expires')}</th></tr></thead>
              <tbody id="sessions-tbody">
                {sessions.map((session, index) => (
                  <tr key={`${session.username}-${session.remoteIp}-${session.createdAt}-${index}-${language}`}>
                    <td data-label={t('sessions.user')}>{session.username || ''}</td>
                    <td data-label={t('sessions.ip')}><span className="session-ip">{session.remoteIp || '--'}</span></td>
                    <td data-label={t('sessions.lastSeen')}>{fmtDateTime(session.lastSeenAt)}</td>
                    <td data-label={t('sessions.expires')}>{fmtDuration(session.expiresInSeconds || 0)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className={`session-empty${error ? ' session-error' : ''}`} id="sessions-empty" data-i18n={error ? undefined : 'sessions.empty'} style={{ display: showTable ? 'none' : '' }}>{emptyText}</div>
        </section>
      </div>
    </div>
  );
}

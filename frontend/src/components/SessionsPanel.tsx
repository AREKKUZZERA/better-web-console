import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { SessionInfo } from '../types';

function formatSeconds(value: number) {
  if (value < 60) return `${value}s`;
  if (value < 3600) return `${Math.floor(value / 60)}m`;
  return `${Math.floor(value / 3600)}h ${Math.floor((value % 3600) / 60)}m`;
}

export function SessionsPanel({ active }: { active: boolean }) {
  const [sessions, setSessions] = useState<SessionInfo[]>([]);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!active) return;
    let cancelled = false;

    async function load() {
      try {
        const data = await api.sessions();
        if (!cancelled) {
          setSessions(data.sessions || []);
          setError('');
        }
      } catch (err) {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load sessions');
      }
    }

    void load();
    const timer = window.setInterval(load, 10000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [active]);

  if (error) return <div className="empty-state">{error}</div>;
  if (!sessions.length) return <div className="empty-state">No active sessions.</div>;

  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            <th>User</th>
            <th>IP</th>
            <th>Last activity</th>
            <th>Expires</th>
          </tr>
        </thead>
        <tbody>
          {sessions.map((session, index) => (
            <tr key={`${session.username}-${session.remoteIp}-${session.createdAt}-${index}`}>
              <td>{session.username}</td>
              <td>{session.remoteIp || '-'}</td>
              <td>{new Date(session.lastSeenAt).toLocaleString()}</td>
              <td>{formatSeconds(session.expiresInSeconds)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

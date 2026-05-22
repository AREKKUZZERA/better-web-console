import type { SessionInfo, StatusResponse } from '../types';

async function json<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init);
  if (!response.ok) throw new Error(`${response.status} ${response.statusText}`);
  return response.json() as Promise<T>;
}

export const api = {
  csrf: () => json<{ token: string }>('/api/csrf'),
  status: () => json<StatusResponse>('/api/status'),
  login: (username: string, password: string, csrf: string) =>
    json<{ success: boolean; username: string }>('/api/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password, csrf })
    }),
  logout: () => json<{ success: boolean }>('/api/logout', { method: 'POST' }),
  aliases: () => json<{ aliases: Array<{ name: string; command: string }> }>('/api/aliases'),
  sessions: () => json<{ sessions: SessionInfo[] }>('/api/sessions')
};

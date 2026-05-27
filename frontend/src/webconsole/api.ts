// @ts-nocheck
async function json(url, init) {
  const response = await fetch(url, init);
  if (!response.ok) throw new Error(String(response.status));
  return response.json();
}

export async function getStatus(init) {
  return json('/api/status', init);
}

export async function getAliases() {
  const response = await fetch('/api/aliases');
  if (!response.ok) return null;
  return response.json();
}

export function getSessions() {
  return json('/api/sessions');
}

export function getAuditEntries(params = {}) {
  const query = new URLSearchParams();
  query.set('limit', String(params.limit || 200));
  ['q', 'action', 'username', 'ip'].forEach(key => {
    if (params[key]) query.set(key, params[key]);
  });
  return json('/api/audit?' + query.toString());
}

export function auditExportUrl(format, params = {}) {
  const query = new URLSearchParams();
  query.set('format', format);
  query.set('limit', String(params.limit || 500));
  ['q', 'action', 'username', 'ip'].forEach(key => {
    if (params[key]) query.set(key, params[key]);
  });
  return '/api/audit/export?' + query.toString();
}

export function getCompatibility() {
  return json('/api/compat');
}

export function getErrorGroups() {
  return json('/api/errors');
}

export function getPlayerProfile(id) {
  return json('/api/player?id=' + encodeURIComponent(id));
}

export function getEditableConfig() {
  return json('/api/config');
}

export function saveEditableConfig(config) {
  return json('/api/config', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(config)
  });
}

export async function logout() {
  await fetch('/api/logout', { method: 'POST' });
}

export function getCsrf() {
  return json('/api/csrf');
}

export async function login(username, password, csrf) {
  const response = await fetch('/api/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password, csrf })
  });
  const data = await response.json();
  return { ok: response.ok, data };
}

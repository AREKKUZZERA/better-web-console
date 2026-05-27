// @ts-nocheck
export function byId(id) {
  return document.getElementById(id);
}

export function setText(id, value) {
  const el = byId(id);
  if (el) el.textContent = value;
}

export function setWidth(id, value) {
  const el = byId(id);
  if (el) el.style.width = Math.max(0, Math.min(100, value || 0)) + '%';
}

export function setKpiState(valueId, value, warnAt, critAt) {
  const el = byId(valueId);
  if (!el || !el.parentElement) return;
  el.parentElement.className = 'kpi ' + (value >= critAt ? 'red' : value >= warnAt ? 'warn' : 'green');
}

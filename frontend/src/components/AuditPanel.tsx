import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import type { AuditEntry } from '../types';
import { auditExportUrl, getAuditEntries } from '../webconsole/api';
import { useActivePanel, useWebConsoleLanguage } from './useWebConsoleRuntime';

function auditEntryText(entry: AuditEntry) {
  return [entry.timestamp, entry.action, entry.username, entry.ip, entry.detail, entry.raw].filter(Boolean).join(' ').toLowerCase();
}

const INITIAL_AUDIT_LIMIT = 5;

export function AuditPanel() {
  const active = useActivePanel('audit');
  const { t } = useWebConsoleLanguage();
  const [entries, setEntries] = useState<AuditEntry[]>([]);
  const [search, setSearch] = useState('');
  const [expanded, setExpanded] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(false);
  const tableRevealRef = useRef<HTMLDivElement>(null);
  const previousTableHeightRef = useRef<number | null>(null);

  const loadAudit = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getAuditEntries({ q: search.trim(), limit: 300 });
      setEntries(Array.isArray(data.entries) ? data.entries : []);
      setLoaded(true);
      setError(false);
    } catch {
      setEntries([]);
      setLoaded(true);
      setError(true);
    } finally {
      setLoading(false);
    }
  }, [search]);

  useEffect(() => {
    if (!active) return;
    void loadAudit();
    const timer = window.setInterval(() => void loadAudit(), 15000);
    return () => window.clearInterval(timer);
  }, [active, loadAudit]);

  const normalizedSearch = search.trim().toLowerCase();
  const filtered = useMemo(() => {
    if (!normalizedSearch) return entries;
    return entries.filter(entry => auditEntryText(entry).includes(normalizedSearch));
  }, [entries, normalizedSearch]);
  const orderedEntries = useMemo(() => filtered.slice().reverse(), [filtered]);
  const visibleEntryCount = expanded ? orderedEntries.length : Math.min(orderedEntries.length, INITIAL_AUDIT_LIMIT);
  const visibleEntries = orderedEntries.slice(0, visibleEntryCount);
  const canToggleEntries = orderedEntries.length > INITIAL_AUDIT_LIMIT;

  const showTable = !error && filtered.length > 0;
  const emptyText = error ? t('audit.error') : loading && !loaded ? t('audit.loading') : t('audit.empty');

  useLayoutEffect(() => {
    const el = tableRevealRef.current;
    if (!el || !showTable) {
      previousTableHeightRef.current = null;
      return;
    }
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const from = previousTableHeightRef.current ?? el.getBoundingClientRect().height;
    const to = el.scrollHeight;
    previousTableHeightRef.current = to;
    if (reducedMotion || Math.abs(from - to) < 1) {
      el.style.height = 'auto';
      el.style.opacity = '1';
      return;
    }
    el.style.transition = 'none';
    el.style.height = `${from}px`;
    el.style.overflow = 'hidden';
    el.style.opacity = from > 0 ? '1' : '0';
    el.getBoundingClientRect();
    const frame = window.requestAnimationFrame(() => {
      el.style.transition = 'height 180ms cubic-bezier(.22,1,.36,1), opacity 120ms var(--ease)';
      el.style.height = `${to}px`;
      el.style.opacity = '1';
    });
    const timer = window.setTimeout(() => {
      el.style.transition = '';
      el.style.height = 'auto';
      el.style.overflow = '';
    }, 240);
    return () => {
      window.cancelAnimationFrame(frame);
      window.clearTimeout(timer);
    };
  }, [visibleEntryCount, showTable]);

  return (
    <div className="panel" id="panel-audit">
      <div className="audit-layout">
        <section className="players-card">
          <div className="players-card-header">
            <div className="players-card-title" data-i18n="audit.title">{t('audit.title')}</div>
            <span className="players-card-badge" id="audit-count">{error ? '!' : filtered.length}</span>
          </div>
          <div className="audit-toolbar">
            <input id="audit-search" name="auditSearch" className="player-filter" type="search" placeholder={t('audit.searchPlaceholder')} data-i18n-placeholder="audit.searchPlaceholder" value={search} onChange={event => setSearch(event.target.value)} />
            <button className="alias-run" id="audit-refresh" type="button" data-i18n="audit.refresh" onClick={() => void loadAudit()}>{t('audit.refresh')}</button>
            <a className="alias-run" href={auditExportUrl('csv', { q: search.trim() })}>CSV</a>
            <a className="alias-run" href={auditExportUrl('json', { q: search.trim() })}>JSON</a>
          </div>
          <div className="player-table-wrap audit-table-reveal" ref={tableRevealRef}>
            <table className="audit-table" id="audit-table" style={{ display: showTable ? '' : 'none' }}>
              <thead><tr><th data-i18n="audit.time">{t('audit.time')}</th><th data-i18n="audit.action">{t('audit.action')}</th><th data-i18n="audit.user">{t('audit.user')}</th><th data-i18n="audit.ip">{t('audit.ip')}</th><th data-i18n="audit.detail">{t('audit.detail')}</th></tr></thead>
              <tbody id="audit-tbody">
                {visibleEntries.map((entry, index) => {
                  const detail = entry.detail || entry.raw || '';
                  return (
                    <tr key={`${entry.timestamp}-${entry.action}-${entry.username}-${index}`}>
                      <td data-label={t('audit.time')}>{entry.timestamp || '--'}</td>
                      <td data-label={t('audit.action')}><span className="audit-action">{entry.action || 'RAW'}</span></td>
                      <td data-label={t('audit.user')}>{entry.username || '--'}</td>
                      <td data-label={t('audit.ip')}><span className="session-ip">{entry.ip || '--'}</span></td>
                      <td className="audit-detail" data-label={t('audit.detail')} title={detail}>{detail}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          {showTable && canToggleEntries && (
            <div className="audit-more">
              <button className="history-more" type="button" data-i18n={expanded ? 'audit.showLess' : 'audit.showMore'} onClick={() => setExpanded(value => !value)}>{expanded ? t('audit.showLess') : t('audit.showMore')}</button>
            </div>
          )}
          <div className={`session-empty${error ? ' session-error' : ''}`} id="audit-empty" data-i18n={error ? undefined : 'audit.empty'} style={{ display: showTable ? 'none' : '' }}>{emptyText}</div>
        </section>
      </div>
    </div>
  );
}

import { useCallback, useEffect, useState } from 'react';
import type { EditableConfig } from '../types';
import { getEditableConfig, saveEditableConfig } from '../webconsole/api';
import { useActivePanel, useWebConsoleLanguage } from './useWebConsoleRuntime';

const EMPTY_CONFIG: EditableConfig = {
  logging: { logCommands: true, logAuth: true, auditLog: true },
  systemStats: { enabled: true, updateIntervalSeconds: 5, showDisk: true },
  blockedCommands: [],
  aliases: []
};

function normalizeConfig(config: EditableConfig): EditableConfig {
  return {
    logging: { ...EMPTY_CONFIG.logging, ...(config.logging || {}) },
    systemStats: { ...EMPTY_CONFIG.systemStats, ...(config.systemStats || {}) },
    blockedCommands: Array.isArray(config.blockedCommands) ? config.blockedCommands : [],
    aliases: Array.isArray(config.aliases) ? config.aliases : []
  };
}

export function ConfigPanel() {
  const active = useActivePanel('config');
  const { t } = useWebConsoleLanguage();
  const [config, setConfig] = useState<EditableConfig>(EMPTY_CONFIG);
  const [blockedText, setBlockedText] = useState('');
  const [status, setStatus] = useState('');
  const [saving, setSaving] = useState(false);

  const loadConfig = useCallback(async () => {
    try {
      const next = normalizeConfig(await getEditableConfig());
      setConfig(next);
      setBlockedText(next.blockedCommands.join('\n'));
      setStatus('');
    } catch {
      setStatus(t('config.loadError'));
    }
  }, [t]);

  useEffect(() => {
    if (active) void loadConfig();
  }, [active, loadConfig]);

  const updateAlias = (index: number, field: 'name' | 'command', value: string) => {
    setConfig(current => {
      const aliases = current.aliases.map((alias, i) => i === index ? { ...alias, [field]: value } : alias);
      return { ...current, aliases };
    });
  };

  const save = async () => {
    setSaving(true);
    setStatus('');
    try {
      const payload = {
        ...config,
        blockedCommands: blockedText.split(/\r?\n|,/).map(item => item.trim()).filter(Boolean),
        aliases: config.aliases.filter(alias => alias.name.trim() || alias.command.trim())
      };
      await saveEditableConfig(payload);
      setConfig(normalizeConfig(payload));
      setStatus(t('config.saved'));
    } catch {
      setStatus(t('config.saveError'));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="panel" id="panel-config">
      <div className="config-layout">
        <section className="players-card">
          <div className="players-card-header">
            <div className="players-card-title">{t('config.title')}</div>
            <span className="players-card-badge">{t('config.reloadApplies')}</span>
          </div>
          <div className="config-grid">
            <label className="config-toggle"><input id="config-log-commands" name="logCommands" type="checkbox" checked={config.logging.logCommands} onChange={event => setConfig({ ...config, logging: { ...config.logging, logCommands: event.target.checked } })} />{t('config.logCommands')}</label>
            <label className="config-toggle"><input id="config-log-auth" name="logAuth" type="checkbox" checked={config.logging.logAuth} onChange={event => setConfig({ ...config, logging: { ...config.logging, logAuth: event.target.checked } })} />{t('config.logAuth')}</label>
            <label className="config-toggle"><input id="config-audit-log" name="auditLog" type="checkbox" checked={config.logging.auditLog} onChange={event => setConfig({ ...config, logging: { ...config.logging, auditLog: event.target.checked } })} />{t('config.auditLog')}</label>
            <label className="config-toggle"><input id="config-system-stats" name="systemStatsEnabled" type="checkbox" checked={config.systemStats.enabled} onChange={event => setConfig({ ...config, systemStats: { ...config.systemStats, enabled: event.target.checked } })} />{t('config.systemStats')}</label>
            <label className="config-toggle"><input id="config-show-disk" name="showDisk" type="checkbox" checked={config.systemStats.showDisk} onChange={event => setConfig({ ...config, systemStats: { ...config.systemStats, showDisk: event.target.checked } })} />{t('config.showDisk')}</label>
            <label className="config-number">{t('config.interval')}<input id="config-stats-interval" name="statsIntervalSeconds" type="number" min={2} max={60} value={config.systemStats.updateIntervalSeconds} onChange={event => setConfig({ ...config, systemStats: { ...config.systemStats, updateIntervalSeconds: Number(event.target.value) } })} /></label>
          </div>
        </section>

        <section className="players-card">
          <div className="players-card-header">
            <div className="players-card-title">{t('config.blocked')}</div>
            <span className="players-card-badge">{t('config.onePerLine')}</span>
          </div>
          <textarea id="config-blocked-commands" name="blockedCommands" className="config-textarea" value={blockedText} onChange={event => setBlockedText(event.target.value)} />
        </section>

        <section className="players-card">
          <div className="players-card-header">
            <div className="players-card-title">{t('config.aliases')}</div>
            <button className="alias-run" type="button" onClick={() => setConfig({ ...config, aliases: [...config.aliases, { name: '', command: '' }] })}>{t('config.addAlias')}</button>
          </div>
          <div className="config-alias-list">
            {config.aliases.map((alias, index) => (
              <div className="config-alias-row" key={index}>
                <input id={`config-alias-name-${index}`} name={`aliasName${index}`} className="player-filter" value={alias.name} placeholder={t('config.aliasName')} onChange={event => updateAlias(index, 'name', event.target.value)} />
                <input id={`config-alias-command-${index}`} name={`aliasCommand${index}`} className="player-filter" value={alias.command} placeholder={t('config.aliasCommand')} onChange={event => updateAlias(index, 'command', event.target.value)} />
                <button className="action-btn ban" type="button" onClick={() => setConfig({ ...config, aliases: config.aliases.filter((_alias, i) => i !== index) })}>X</button>
              </div>
            ))}
          </div>
        </section>

        <div className="config-actions">
          <button className="alias-run" type="button" onClick={() => void loadConfig()}>{t('audit.refresh')}</button>
          <button className="btn-send" type="button" disabled={saving} onClick={() => void save()}>{saving ? t('config.saving') : t('config.save')}</button>
          <span className="config-status">{status}</span>
        </div>
      </div>
    </div>
  );
}

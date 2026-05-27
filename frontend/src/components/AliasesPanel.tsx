import { useCallback, useEffect, useState } from 'react';
import type { AliasInfo } from '../types';
import { getAliases } from '../webconsole/api';
import { useActivePanel, useWebConsoleLanguage } from './useWebConsoleRuntime';

function dispatchAliasCommand(command: string, run: boolean) {
  window.dispatchEvent(new CustomEvent('webconsole:alias-command', { detail: { command, run } }));
}

export function AliasesPanel() {
  const active = useActivePanel('aliases');
  const { t } = useWebConsoleLanguage();
  const [aliases, setAliases] = useState<AliasInfo[]>([]);
  const [loaded, setLoaded] = useState(false);

  const loadAliases = useCallback(async () => {
    if (loaded) return;
    try {
      const data = await getAliases();
      setAliases(Array.isArray(data?.aliases) ? data.aliases : []);
      setLoaded(true);
    } catch {
      setAliases([]);
      setLoaded(true);
    }
  }, [loaded]);

  useEffect(() => {
    if (!active) return;
    void loadAliases();
  }, [active, loadAliases]);

  return (
    <div className="panel" id="panel-aliases">
      <p className="alias-hint" data-i18n="aliases.hint">{t('aliases.hint')}</p>
      <div className="alias-list" id="alias-list">
        {loaded && aliases.length === 0 ? (
          <p style={{ color: 'var(--text-muted)', padding: '20px 0' }}>{t('aliases.empty')}</p>
        ) : aliases.map(alias => (
          <div className="alias-card" key={alias.name} onClick={() => dispatchAliasCommand(`!${alias.name}`, false)}>
            <span className="alias-name">!{alias.name}</span>
            <span className="alias-sep">→</span>
            <span className="alias-cmd" title={alias.command}>{alias.command}</span>
            <button className="alias-run" type="button" onClick={event => {
              event.stopPropagation();
              dispatchAliasCommand(`!${alias.name}`, true);
            }}>{t('console.run')}</button>
          </div>
        ))}
      </div>
    </div>
  );
}

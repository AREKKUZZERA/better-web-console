import { useCallback, useEffect, useState } from 'react';
import { I18N, LANG_KEY, LANGS } from '../webconsole/i18n';

type PanelName = 'console' | 'dash' | 'players' | 'aliases' | 'sessions' | 'audit' | 'config';
type Vars = Record<string, string | number>;

function isLanguage(value: unknown): value is string {
  return typeof value === 'string' && LANGS.includes(value);
}

function activePanel() {
  return document.querySelector('.panel.active')?.id.replace(/^panel-/, '') || 'console';
}

function initialLanguage() {
  const htmlLang = document.documentElement.lang;
  if (isLanguage(htmlLang)) return htmlLang;
  try {
    const saved = localStorage.getItem(LANG_KEY);
    if (isLanguage(saved)) return saved;
  } catch {
    // Ignore storage errors; controller has the same fallback behavior.
  }
  return 'en';
}

export function useActivePanel(panel: PanelName) {
  const [active, setActive] = useState(() => activePanel() === panel);

  useEffect(() => {
    const sync = (event?: Event) => {
      const next = event instanceof CustomEvent && event.detail?.panel ? event.detail.panel : activePanel();
      setActive(next === panel);
    };

    sync();
    window.addEventListener('webconsole:panel', sync);
    return () => window.removeEventListener('webconsole:panel', sync);
  }, [panel]);

  return active;
}

export function useWebConsoleLanguage() {
  const [language, setLanguage] = useState(initialLanguage);

  useEffect(() => {
    const sync = (event?: Event) => {
      const next = event instanceof CustomEvent && event.detail?.lang ? event.detail.lang : initialLanguage();
      setLanguage(isLanguage(next) ? next : 'en');
    };

    window.addEventListener('webconsole:language', sync);
    return () => window.removeEventListener('webconsole:language', sync);
  }, []);

  const t = useCallback((key: string, vars: Vars = {}) => {
    const table = I18N as Record<string, Record<string, string>>;
    const text = table[language]?.[key] || table.en?.[key] || key;
    return text.replace(/\{(\w+)\}/g, (_match: string, name: string) => String(vars[name] ?? ''));
  }, [language]);

  return { language, t };
}

export function useWebConsoleEvent<TDetail = Record<string, unknown>>(
  name: string,
  handler: (detail: TDetail) => void
) {
  useEffect(() => {
    const listener = (event: Event) => {
      handler(event instanceof CustomEvent ? event.detail as TDetail : {} as TDetail);
    };

    window.addEventListener(name, listener);
    return () => window.removeEventListener(name, listener);
  }, [handler, name]);
}

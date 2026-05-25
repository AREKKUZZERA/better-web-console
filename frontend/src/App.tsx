import { useEffect, useLayoutEffect, useRef } from 'react';
import { legacyHtml } from './legacyHtml';
import { mountLegacyWebConsole } from './legacyWebconsole';

export function App() {
  useEffect(() => {
    mountLegacyWebConsole();
  }, []);

  return <LegacyShell />;
}

function LegacyShell() {
  const rootRef = useRef<HTMLDivElement>(null);

  useLayoutEffect(() => {
    const root = rootRef.current;
    if (!root) return;

    const template = document.createElement('template');
    template.innerHTML = legacyHtml;
    root.replaceChildren(template.content.cloneNode(true));

    return () => {
      root.replaceChildren();
    };
  }, []);

  return <div ref={rootRef} />;
}

import { useEffect } from 'react';
import { legacyHtml } from './legacyHtml';
import { mountLegacyWebConsole } from './legacyWebconsole';

export function App() {
  useEffect(() => {
    mountLegacyWebConsole();
  }, []);

  return <div dangerouslySetInnerHTML={{ __html: legacyHtml }} />;
}

import { useEffect } from 'react';
import { WebConsoleShell } from './components/WebConsoleShell';
import { mountWebConsole } from './webConsoleController';

export function App() {
  useEffect(() => {
    mountWebConsole();
  }, []);

  return <WebConsoleShell />;
}

import { useState } from 'react';
import { SessionsPanel } from './components/SessionsPanel';
import type { Panel } from './types';

const panels: Panel[] = ['console', 'dash', 'players', 'aliases', 'sessions'];

export function App() {
  const [panel, setPanel] = useState<Panel>('console');

  return (
    <main className="app">
      <header className="app-header">
        <h1>Better-WebConsole</h1>
        <nav className="tabs" aria-label="Main panels">
          {panels.map((name) => (
            <button
              key={name}
              className={panel === name ? 'tab active' : 'tab'}
              type="button"
              onClick={() => setPanel(name)}
            >
              {name}
            </button>
          ))}
        </nav>
      </header>
      {panel === 'sessions' ? (
        <section className="panel">
          <div className="panel-header">
            <h2>Web Sessions</h2>
            <span>read-only</span>
          </div>
          <SessionsPanel active={panel === 'sessions'} />
        </section>
      ) : (
        <section className="panel">
          <div className="panel-header">
            <h2>{panel}</h2>
            <span>migration placeholder</span>
          </div>
        </section>
      )}
    </main>
  );
}

import { ActionModal } from './ActionModal';
import { ConsolePanel } from './ConsolePanel';
import { ConfigPanel } from './ConfigPanel';
import { DashboardPanel } from './DashboardPanel';
import { Header } from './Header';
import { LoginScreen } from './LoginScreen';
import { PlayersPanel } from './PlayersPanel';
import { SessionsPanel } from './SessionsPanel';
import { Tabs } from './Tabs';
import { Toast } from './Toast';

export function WebConsoleShell() {
  return (
    <>
      <LoginScreen />
      <div id="app">
        <Header />
        <Tabs />
        <ConsolePanel />
        <DashboardPanel />
        <PlayersPanel />
        <SessionsPanel />
        <ConfigPanel />
      </div>
      <ActionModal />
      <Toast />
    </>
  );
}

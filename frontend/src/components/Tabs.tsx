export function Tabs() {
  return (
    <div id="tabs">
        <div className="tab active" data-panel="console" data-i18n="tab.console">Console</div>
        <div className="tab" data-panel="dash" data-i18n="tab.dashboard">Dashboard</div>
        <div className="tab" data-panel="players"><span data-i18n="tab.players">Players</span> <span id="player-count-tab">(0)</span></div>
        <div className="tab" data-panel="aliases" data-i18n="tab.aliases">Aliases</div>
        <div className="tab" data-panel="sessions" data-i18n="tab.sessions">Sessions</div>
        <div className="tab" data-panel="audit" data-i18n="tab.audit">Audit</div>
        <div className="tab" data-panel="config" data-i18n="tab.config">Config</div>
      </div>
  );
}

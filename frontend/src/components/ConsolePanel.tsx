export function ConsolePanel() {
  return (
    <div className="panel active" id="panel-console">
        <div id="stats-bar">
          <div className="stat" id="stat-tps"><span>TPS</span><span className="v" id="sv-tps">&#8212;</span></div>
          <div className="stat"><span>RAM</span><span className="v" id="sv-ram">&#8212;</span></div>
          <div className="stat"><span data-i18n="stat.players">Players</span><span className="v" id="sv-players">&#8212;</span></div>
          <div className="stat"><span data-i18n="stat.lines">Lines</span><span className="v" id="sv-lines">0</span></div>
          <div className="stat"><span data-i18n="stat.session">Session</span><span className="v" id="sv-session">&#8212;</span></div>
        </div>
        <div id="console-wrap">
          <div id="console-output"></div>
          <button id="scroll-anchor">&#8595; <span data-i18n="console.bottom">Bottom</span></button>
        </div>
        <div id="search-bar">
          <span className="slbl" data-i18n="console.filter">Filter</span>
          <input id="search-input" name="consoleSearch" type="text" placeholder="Search logs&#8230; (Ctrl+F)" data-i18n-placeholder="console.searchPlaceholder" spellCheck="false" />
          <span id="search-count"></span>
          <button className="ftog INFO active" data-lv="INFO">Info</button>
          <button className="ftog WARN active" data-lv="WARN">Warn</button>
          <button className="ftog SEVERE active" data-lv="SEVERE" data-i18n="console.error">Error</button>
          <button className="ftog DEBUG" data-lv="DEBUG">Debug</button>
        </div>
        <div id="input-bar">
          <span className="prompt">&#10095;</span>
          <input id="cmd-input" name="consoleCommand" type="text" placeholder="Type command or !alias&#8230; (Tab to complete)" data-i18n-placeholder="console.commandPlaceholder" autoComplete="off" spellCheck="false" disabled />
          <div id="autocomplete"></div>
          <button className="btn-send" id="btn-send" disabled data-i18n="console.run">Run</button>
        </div>
      </div>
  );
}

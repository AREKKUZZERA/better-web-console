export function Header() {
  return (
    <header>
        <div className="hlogo">&#x2B21; BWC</div>
        <div className="hsep"></div>
        <div className="status-dot connecting" id="sdot"></div>
        <span id="status-text">Connecting&#8230;</span>
        <div className="hsep"></div>
        <div className="huser"><span data-i18n="header.as">as</span> <strong id="ulabel"></strong></div>
        <div className="spacer"></div>
        <select className="lang-select" id="app-lang" aria-label="Language">
          <option value="en">English</option>
          <option value="ru">Русский</option>
          <option value="zh">中文</option>
          <option value="pl">Polski</option>
          <option value="de">Deutsch</option>
          <option value="fr">Français</option>
        </select>
        <button className="hbtn" id="notif-btn" title="Toggle error notifications" data-i18n-title="header.notifications">&#128276; <span className="notif-badge" id="nbadge"></span></button>
        <button className="hbtn" id="btn-export" data-i18n="header.export">Export Log</button>
        <button className="hbtn" id="btn-clear" data-i18n="header.clear">Clear</button>
        <button className="hbtn danger" id="btn-logout" data-i18n="header.logout">Logout</button>
      </header>
  );
}

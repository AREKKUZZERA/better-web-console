import { useWebConsoleLanguage } from './useWebConsoleRuntime';

export function LoginScreen() {
  const { t } = useWebConsoleLanguage();

  return (
    <div id="login-screen">
      <div className="lcard">
        <div className="llogo">&#x2B21; B-WebConsole <small>V2</small></div>
        <h2 data-i18n="login.title">Sign in</h2>
        <div className="login-lang">
          <select className="lang-select" id="login-lang" name="loginLanguage" aria-label={t('lang.label')}>
            <option value="en">English</option>
            <option value="ru">Русский</option>
            <option value="zh">中文</option>
            <option value="pl">Polski</option>
            <option value="de">Deutsch</option>
            <option value="fr">Français</option>
            <option value="es">Español</option>
          </select>
        </div>
        <form id="login-form" action="#">
          <div className="field">
            <label htmlFor="lu" data-i18n="login.username">Username</label>
            <input id="lu" name="username" type="text" autoComplete="username" placeholder="admin" spellCheck="false" />
          </div>
          <div className="field">
            <label htmlFor="lp" data-i18n="login.password">Password</label>
            <input id="lp" name="password" type="password" autoComplete="current-password" placeholder="&#8226;&#8226;&#8226;&#8226;&#8226;&#8226;&#8226;&#8226;" />
          </div>
          <button className="btn-login" id="btn-login" type="submit" data-i18n="login.button">Sign In</button>
        </form>
        <div id="login-error" role="alert"></div>
      </div>
    </div>
  );
}

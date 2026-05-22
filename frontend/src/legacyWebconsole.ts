// @ts-nocheck
let mounted = false;

export function mountLegacyWebConsole() {
  if (mounted) return;
  mounted = true;

(function(){
'use strict';

// ── State ──────────────────────────────────────────────────────────────────
let ws=null, autoScroll=true, lineCount=0;
let reconnectTimer=null, reconnectDelay=1000, reconnectAttempt=0, manualDisconnect=false;
let chartsInitialized=false;
let cmdHistory=[], histIdx=-1, draft='';
try{ cmdHistory=JSON.parse(localStorage.getItem('bwc_hist')||'[]'); }catch(_){}
let sessionUser='', sessionStart=Date.now();
let activeFilters=new Set(['INFO','WARN','WARNING','SEVERE','ERROR']);
let filterText='';
let searchQuery='', searchMatches=[], searchIdx=0;
let notifEnabled=false, notifCount=0;
let modalAction=null;
let acItems=[], acIdx=-1, acReqId=0;
let tpsChart=null, ramChart=null, playersChart=null, cpuChart=null;
let activePanelName='console';
let lastPlayersStructureSignature='__init__';
let lastPlayersVisualSignature='__init__';
let lastPlayerEventsSignature='__init__';
let lastPlayerCommandsSignature='__init__';
let lastAnimatedPlayersSignature='';
let currentPlayerList=[];
let playerSearchText='';
let playerWorldFilter='';
let lastWorldFilterSignature='__init__';
let lastActivityDaysSignature='__init__';
let expandedActivityDays=new Set();
let lastStatsData=null;
let lastSummarySignature='__init__';
let lastTopPlayersSignature='__init__';
let lastSessionsSignature='__init__';
let sessionsLoaded=false;
let sessionsLoading=false;
const panelOrder=['console','dash','players','aliases','sessions'];
const MAX_LINES=3000;

// Dashboard counters
const levelCounts={INFO:0,WARN:0,SEVERE:0,DEBUG:0};
let sessionErrors=0;
const activityLog=[];
const MAX_ACTIVITY=40;
const healthHistory=[];
const MAX_HEALTH_HISTORY=12;
let lastHealthSnapshot=null;
let currentHealthEvent=null;
let prevPlayerNames=new Set();

// ── DOM ────────────────────────────────────────────────────────────────────
const $=id=>document.getElementById(id);
const loginScreen=$('login-screen'), app=$('app');
const lu=$('lu'), lp=$('lp'), loginErr=$('login-error'), btnLogin=$('btn-login');
const sdot=$('sdot'), statusText=$('status-text');
const output=$('console-output'), cmdInput=$('cmd-input'), btnSend=$('btn-send');
const scrollAnchor=$('scroll-anchor'), searchInput=$('search-input'), searchCount=$('search-count');
const acDropdown=$('autocomplete');
const ulabel=$('ulabel'), svLines=$('sv-lines'), svSession=$('sv-session');
const svTps=$('sv-tps'), svRam=$('sv-ram'), svPlayers=$('sv-players'), statTpsEl=$('stat-tps');
const modal=$('modal'), modalTitle=$('modal-title'), modalPlayer=$('modal-player');
const modalReason=$('modal-reason'), modalCancel=$('modal-cancel'), modalConfirm=$('modal-confirm');
const toast=$('toast'), nbadge=$('nbadge'), loginLang=$('login-lang'), appLang=$('app-lang');

const I18N={
  en:{
    'login.title':'Sign in','login.username':'Username','login.password':'Password','login.button':'Sign In','login.signing':'Signing in...','login.missing':'Enter username and password.','login.invalid':'Invalid credentials.','login.unreachable':'Server unreachable.',
    'header.as':'as','header.notifications':'Toggle error notifications','header.export':'Export Log','header.clear':'Clear','header.logout':'Logout',
    'tab.console':'Console','tab.dashboard':'Dashboard','tab.players':'Players','tab.aliases':'Aliases',
    'stat.players':'Players','stat.lines':'Lines','stat.session':'Session',
    'status.connected':'Connected','status.disconnected':'Disconnected','status.connecting':'Connecting...',
    'console.bottom':'Bottom','console.filter':'Filter','console.searchPlaceholder':'Search logs... (Ctrl+F)','console.commandPlaceholder':'Type command or !alias... (Tab to complete)','console.run':'Run','console.error':'Error','console.matches':'{count} matches','console.match':'{count} match','console.acHint':'Tab / ↑↓ to navigate, Enter to select',
    'dash.overview':'Overview','dash.serverHealth':'Server Health','dash.waitingStats':'Waiting for stats','dash.playerCapacity':'Player Capacity','dash.onlineSlotsUsed':'Online slots used','dash.pluginUptime':'Plugin Uptime','dash.sincePluginEnable':'Since plugin enable','dash.recentCommands':'Recent Commands','dash.playerCommands24h':'Player commands / 24h','dash.tickRate':'Server tick rate','dash.ramUsed':'RAM Used','dash.heapMemory':'Heap memory','dash.playersOnline':'Players Online','dash.worlds':'Worlds','dash.totalEntities':'Total Entities','dash.acrossWorlds':'Across all worlds','dash.sessionErrors':'Errors (session)','dash.errorLines':'SEVERE / ERROR lines','dash.machineHealth':'Machine Health','dash.hostCpu':'Host CPU','dash.systemLoad':'System load','dash.machineRam':'Machine RAM','dash.physicalMemory':'Physical memory','dash.serverDisk':'Server Disk','dash.worldContainer':'World container','dash.jvmThreads':'JVM Threads','dash.javaProcess':'Java process','dash.performanceHistory':'Performance History (5 min)','dash.machineDetails':'Machine Details','dash.analytics':'Analytics','dash.logBreakdown':'Log Level Breakdown','dash.worldBreakdown':'World Breakdown','dash.world':'World','dash.chunks':'Chunks','dash.entities':'Entities','dash.noData':'No data yet','dash.activityFeed':'Activity Feed','dash.live':'live','dash.waitingActivity':'Waiting for activity...',
    'metric.model':'Model','metric.coresThreads':'Cores / Threads','metric.processLoad':'Process Load','metric.memory':'Memory','metric.machineUsed':'Machine Used','metric.machineFree':'Machine Free','metric.disk':'Disk','metric.used':'Used','metric.free':'Free','metric.mount':'Mount','metric.runtime':'Runtime',
    'players.onlinePlayers':'Online Players','players.noneOnline':'No players online.','players.name':'Name','players.world':'World','players.ping':'Ping','players.actions':'Actions','players.joinHistory':'Join History','players.playerCommands':'Player Commands','players.online':'{count} online','players.joined':'Joined','players.left':'Left','players.unknown':'Unknown','players.noJoinHistory':'No join history in the last 24 hours.','players.noCommands':'No player commands in the last 24 hours.','players.kick':'Kick','players.ban':'Ban','players.kickPlayer':'Kick Player','players.banPlayer':'Ban Player','players.kicked':'Kicked','players.banned':'Banned','players.noReason':'No reason given',
    'aliases.hint':'Click an alias to insert it into the console, or press Run to execute immediately.','aliases.empty':'No aliases configured. Add them to config.yml.',
    'modal.action':'Action','modal.player':'Player','modal.reason':'Reason','modal.reasonPlaceholder':'Enter reason...','modal.cancel':'Cancel','modal.confirm':'Confirm',
    'toast.connected':'Console connected','toast.lost':'Connection lost - reconnecting...','toast.sessionExpired':'Session expired','toast.rateLimited':'Rate limited - slow down','toast.blocked':'Command blocked','toast.notificationsEnabled':'Notifications enabled','toast.notificationsDenied':'Notifications denied','toast.notificationsDisabled':'Notifications disabled','toast.cleared':'Console cleared',
    'health.critical':'Critical','health.watch':'Watch','health.good':'Good','health.needsAttention':'Needs attention','health.monitor':'Monitor server','health.normal':'All core signals normal','health.excellent':'Excellent ✓','health.degraded':'Degraded',
    'fmt.ofSlots':'{players} of {max} slots','fmt.maxUnknown':'{players} online, max unknown','fmt.ofMax':'of {max} max','fmt.loadedChunks':'Loaded chunks: {count}','fmt.chunksLoaded':'Chunks loaded: {count}','fmt.ofRam':'of {max}MB ({pct}%)','fmt.process':'Process {value}','fmt.free':'{value} free','fmt.daemon':'{value} daemon','fmt.lines':'{count} lines'
  },
  ru:{'login.title':'Вход','login.username':'Логин','login.password':'Пароль','login.button':'Войти','login.signing':'Вход...','login.missing':'Введите логин и пароль.','login.invalid':'Неверные данные.','login.unreachable':'Сервер недоступен.','header.as':'как','header.notifications':'Переключить уведомления об ошибках','header.export':'Экспорт логов','header.clear':'Очистить','header.logout':'Выйти','tab.console':'Консоль','tab.dashboard':'Панель','tab.players':'Игроки','tab.aliases':'Алиасы','stat.players':'Игроки','stat.lines':'Строки','stat.session':'Сессия','status.connected':'Подключено','status.disconnected':'Отключено','status.connecting':'Подключение...','console.bottom':'Вниз','console.filter':'Фильтр','console.searchPlaceholder':'Поиск логов... (Ctrl+F)','console.commandPlaceholder':'Введите команду или !алиас... (Tab для подсказок)','console.run':'Выполнить','console.error':'Ошибка','console.matches':'{count} совпадений','console.match':'{count} совпадение','console.acHint':'Tab / ↑↓ навигация, Enter выбрать','dash.overview':'Обзор','dash.serverHealth':'Состояние сервера','dash.waitingStats':'Ожидание статистики','dash.playerCapacity':'Заполненность','dash.onlineSlotsUsed':'Использовано слотов','dash.pluginUptime':'Аптайм плагина','dash.sincePluginEnable':'С момента запуска плагина','dash.recentCommands':'Недавние команды','dash.playerCommands24h':'Команды игроков / 24ч','dash.tickRate':'Скорость тиков','dash.ramUsed':'RAM использовано','dash.heapMemory':'Память heap','dash.playersOnline':'Игроки онлайн','dash.worlds':'Миры','dash.totalEntities':'Всего сущностей','dash.acrossWorlds':'Во всех мирах','dash.sessionErrors':'Ошибки сессии','dash.errorLines':'Строки SEVERE / ERROR','dash.machineHealth':'Состояние машины','dash.hostCpu':'CPU хоста','dash.systemLoad':'Нагрузка системы','dash.machineRam':'RAM машины','dash.physicalMemory':'Физическая память','dash.serverDisk':'Диск сервера','dash.worldContainer':'Папка миров','dash.jvmThreads':'Потоки JVM','dash.javaProcess':'Процесс Java','dash.performanceHistory':'История производительности (5 мин)','dash.machineDetails':'Детали машины','dash.analytics':'Аналитика','dash.logBreakdown':'Уровни логов','dash.worldBreakdown':'Миры','dash.world':'Мир','dash.chunks':'Чанки','dash.entities':'Сущности','dash.noData':'Данных пока нет','dash.activityFeed':'Активность','dash.live':'live','dash.waitingActivity':'Ожидание активности...','metric.model':'Модель','metric.coresThreads':'Ядра / Потоки','metric.processLoad':'Нагрузка процесса','metric.memory':'Память','metric.machineUsed':'Использовано','metric.machineFree':'Свободно','metric.disk':'Диск','metric.used':'Использовано','metric.free':'Свободно','metric.mount':'Точка','metric.runtime':'Runtime','players.onlinePlayers':'Игроки онлайн','players.noneOnline':'Нет игроков онлайн.','players.name':'Имя','players.world':'Мир','players.ping':'Пинг','players.actions':'Действия','players.joinHistory':'История входов','players.playerCommands':'Команды игроков','players.online':'{count} онлайн','players.joined':'Вошел','players.left':'Вышел','players.unknown':'Неизвестно','players.noJoinHistory':'Нет истории входов за 24 часа.','players.noCommands':'Нет команд игроков за 24 часа.','players.kick':'Кик','players.ban':'Бан','players.kickPlayer':'Кик игрока','players.banPlayer':'Бан игрока','players.kicked':'Кикнут','players.banned':'Забанен','players.noReason':'Причина не указана','aliases.hint':'Нажмите алиас, чтобы вставить его в консоль, или Run для запуска.','aliases.empty':'Алиасы не настроены. Добавьте их в config.yml.','modal.action':'Действие','modal.player':'Игрок','modal.reason':'Причина','modal.reasonPlaceholder':'Введите причину...','modal.cancel':'Отмена','modal.confirm':'Подтвердить','toast.connected':'Консоль подключена','toast.lost':'Соединение потеряно - переподключение...','toast.sessionExpired':'Сессия истекла','toast.rateLimited':'Лимит - медленнее','toast.blocked':'Команда заблокирована','toast.notificationsEnabled':'Уведомления включены','toast.notificationsDenied':'Уведомления запрещены','toast.notificationsDisabled':'Уведомления выключены','toast.cleared':'Консоль очищена','health.critical':'Критично','health.watch':'Следить','health.good':'Хорошо','health.needsAttention':'Требует внимания','health.monitor':'Наблюдайте за сервером','health.normal':'Основные показатели в норме','health.excellent':'Отлично ✓','health.degraded':'Просадка','fmt.ofSlots':'{players} из {max} слотов','fmt.maxUnknown':'{players} онлайн, максимум неизвестен','fmt.ofMax':'из {max} максимум','fmt.loadedChunks':'Загружено чанков: {count}','fmt.chunksLoaded':'Чанков загружено: {count}','fmt.ofRam':'из {max}MB ({pct}%)','fmt.process':'Процесс {value}','fmt.free':'{value} свободно','fmt.daemon':'{value} daemon','fmt.lines':'{count} строк'},
  zh:{'login.title':'登录','login.username':'用户名','login.password':'密码','login.button':'登录','login.signing':'正在登录...','login.missing':'请输入用户名和密码。','login.invalid':'凭据无效。','login.unreachable':'服务器不可达。','header.as':'身份','header.notifications':'切换错误通知','header.export':'导出日志','header.clear':'清空','header.logout':'退出','tab.console':'控制台','tab.dashboard':'仪表盘','tab.players':'玩家','tab.aliases':'别名','stat.players':'玩家','stat.lines':'行','stat.session':'会话','status.connected':'已连接','status.disconnected':'已断开','status.connecting':'连接中...','console.bottom':'底部','console.filter':'筛选','console.searchPlaceholder':'搜索日志... (Ctrl+F)','console.commandPlaceholder':'输入命令或 !别名... (Tab 补全)','console.run':'运行','console.error':'错误','console.matches':'{count} 个匹配','console.match':'{count} 个匹配','console.acHint':'Tab / ↑↓ 导航，Enter 选择','dash.overview':'概览','dash.serverHealth':'服务器健康','dash.waitingStats':'等待统计','dash.playerCapacity':'玩家容量','dash.onlineSlotsUsed':'在线槽位使用','dash.pluginUptime':'插件运行时间','dash.sincePluginEnable':'自插件启用以来','dash.recentCommands':'最近命令','dash.playerCommands24h':'玩家命令 / 24小时','dash.tickRate':'服务器 tick 率','dash.ramUsed':'RAM 使用','dash.heapMemory':'堆内存','dash.playersOnline':'在线玩家','dash.worlds':'世界','dash.totalEntities':'实体总数','dash.acrossWorlds':'所有世界','dash.sessionErrors':'会话错误','dash.errorLines':'SEVERE / ERROR 行','dash.machineHealth':'机器健康','dash.hostCpu':'主机 CPU','dash.systemLoad':'系统负载','dash.machineRam':'机器 RAM','dash.physicalMemory':'物理内存','dash.serverDisk':'服务器磁盘','dash.worldContainer':'世界目录','dash.jvmThreads':'JVM 线程','dash.javaProcess':'Java 进程','dash.performanceHistory':'性能历史 (5 分钟)','dash.machineDetails':'机器详情','dash.analytics':'分析','dash.logBreakdown':'日志级别','dash.worldBreakdown':'世界统计','dash.world':'世界','dash.chunks':'区块','dash.entities':'实体','dash.noData':'暂无数据','dash.activityFeed':'活动','dash.live':'实时','dash.waitingActivity':'等待活动...','metric.model':'型号','metric.coresThreads':'核心 / 线程','metric.processLoad':'进程负载','metric.memory':'内存','metric.machineUsed':'已用','metric.machineFree':'可用','metric.disk':'磁盘','metric.used':'已用','metric.free':'可用','metric.mount':'挂载','metric.runtime':'运行时','players.onlinePlayers':'在线玩家','players.noneOnline':'没有玩家在线。','players.name':'名称','players.world':'世界','players.ping':'延迟','players.actions':'操作','players.joinHistory':'加入历史','players.playerCommands':'玩家命令','players.online':'{count} 在线','players.joined':'加入','players.left':'离开','players.unknown':'未知','players.noJoinHistory':'过去 24 小时没有加入历史。','players.noCommands':'过去 24 小时没有玩家命令。','players.kick':'踢出','players.ban':'封禁','players.kickPlayer':'踢出玩家','players.banPlayer':'封禁玩家','players.kicked':'已踢出','players.banned':'已封禁','players.noReason':'未提供原因','aliases.hint':'点击别名插入控制台，或按运行立即执行。','aliases.empty':'未配置别名。请添加到 config.yml。','modal.action':'操作','modal.player':'玩家','modal.reason':'原因','modal.reasonPlaceholder':'输入原因...','modal.cancel':'取消','modal.confirm':'确认','toast.connected':'控制台已连接','toast.lost':'连接丢失 - 正在重连...','toast.sessionExpired':'会话已过期','toast.rateLimited':'频率受限 - 请慢一点','toast.blocked':'命令已阻止','toast.notificationsEnabled':'通知已启用','toast.notificationsDenied':'通知被拒绝','toast.notificationsDisabled':'通知已禁用','toast.cleared':'控制台已清空','health.critical':'严重','health.watch':'关注','health.good':'良好','health.needsAttention':'需要关注','health.monitor':'监控服务器','health.normal':'核心指标正常','health.excellent':'优秀 ✓','health.degraded':'下降','fmt.ofSlots':'{players} / {max} 槽位','fmt.maxUnknown':'{players} 在线，最大未知','fmt.ofMax':'最大 {max}','fmt.loadedChunks':'已加载区块：{count}','fmt.chunksLoaded':'已加载区块：{count}','fmt.ofRam':'共 {max}MB ({pct}%)','fmt.process':'进程 {value}','fmt.free':'{value} 可用','fmt.daemon':'{value} daemon','fmt.lines':'{count} 行'},
  pl:{},de:{},fr:{}
};
Object.assign(I18N.pl,I18N.en,{'login.title':'Logowanie','login.username':'Użytkownik','login.password':'Hasło','login.button':'Zaloguj','header.export':'Eksport logu','header.clear':'Wyczyść','header.logout':'Wyloguj','tab.console':'Konsola','tab.dashboard':'Panel','tab.players':'Gracze','tab.aliases':'Aliasy','status.connected':'Połączono','status.disconnected':'Rozłączono','status.connecting':'Łączenie...','console.run':'Uruchom','console.filter':'Filtr','console.error':'Błąd','dash.overview':'Przegląd','dash.serverHealth':'Stan serwera','dash.machineHealth':'Stan maszyny','dash.analytics':'Analityka','players.onlinePlayers':'Gracze online','players.noneOnline':'Brak graczy online.','players.joinHistory':'Historia wejść','players.playerCommands':'Komendy graczy','players.kick':'Wyrzuć','players.ban':'Zbanuj','modal.cancel':'Anuluj','modal.confirm':'Potwierdź','toast.connected':'Konsola połączona','toast.cleared':'Konsola wyczyszczona'});
Object.assign(I18N.de,I18N.en,{'login.title':'Anmelden','login.username':'Benutzername','login.password':'Passwort','login.button':'Anmelden','header.export':'Log exportieren','header.clear':'Leeren','header.logout':'Abmelden','tab.console':'Konsole','tab.dashboard':'Dashboard','tab.players':'Spieler','tab.aliases':'Aliase','status.connected':'Verbunden','status.disconnected':'Getrennt','status.connecting':'Verbinden...','console.run':'Ausführen','console.filter':'Filter','console.error':'Fehler','dash.overview':'Übersicht','dash.serverHealth':'Serverzustand','dash.machineHealth':'Systemzustand','dash.analytics':'Analyse','players.onlinePlayers':'Spieler online','players.noneOnline':'Keine Spieler online.','players.joinHistory':'Login-Verlauf','players.playerCommands':'Spielerbefehle','players.kick':'Kicken','players.ban':'Bannen','modal.cancel':'Abbrechen','modal.confirm':'Bestätigen','toast.connected':'Konsole verbunden','toast.cleared':'Konsole geleert'});
Object.assign(I18N.fr,I18N.en,{'login.title':'Connexion','login.username':'Utilisateur','login.password':'Mot de passe','login.button':'Se connecter','header.export':'Exporter le log','header.clear':'Effacer','header.logout':'Déconnexion','tab.console':'Console','tab.dashboard':'Tableau de bord','tab.players':'Joueurs','tab.aliases':'Alias','status.connected':'Connecté','status.disconnected':'Déconnecté','status.connecting':'Connexion...','console.run':'Exécuter','console.filter':'Filtre','console.error':'Erreur','dash.overview':'Vue générale','dash.serverHealth':'Santé du serveur','dash.machineHealth':'Santé machine','dash.analytics':'Analyse','players.onlinePlayers':'Joueurs en ligne','players.noneOnline':'Aucun joueur en ligne.','players.joinHistory':'Historique des connexions','players.playerCommands':'Commandes des joueurs','players.kick':'Expulser','players.ban':'Bannir','modal.cancel':'Annuler','modal.confirm':'Confirmer','toast.connected':'Console connectée','toast.cleared':'Console effacée'});
Object.assign(I18N.pl,{'login.signing':'Logowanie...','login.missing':'Podaj użytkownika i hasło.','login.invalid':'Nieprawidłowe dane.','login.unreachable':'Serwer niedostępny.','header.as':'jako','header.notifications':'Przełącz powiadomienia o błędach','stat.players':'Gracze','stat.lines':'Linie','stat.session':'Sesja','console.bottom':'Dół','console.searchPlaceholder':'Szukaj w logach... (Ctrl+F)','console.commandPlaceholder':'Wpisz komendę lub !alias... (Tab uzupełnia)','console.matches':'{count} trafień','console.match':'{count} trafienie','console.acHint':'Tab / ↑↓ nawigacja, Enter wybór','dash.waitingStats':'Oczekiwanie na statystyki','dash.playerCapacity':'Pojemność graczy','dash.onlineSlotsUsed':'Użyte sloty','dash.pluginUptime':'Czas pracy pluginu','dash.sincePluginEnable':'Od włączenia pluginu','dash.recentCommands':'Ostatnie komendy','dash.playerCommands24h':'Komendy graczy / 24h','dash.tickRate':'Tempo ticków','dash.ramUsed':'Użycie RAM','dash.heapMemory':'Pamięć heap','dash.playersOnline':'Gracze online','dash.worlds':'Światy','dash.totalEntities':'Wszystkie encje','dash.acrossWorlds':'We wszystkich światach','dash.sessionErrors':'Błędy sesji','dash.errorLines':'Linie SEVERE / ERROR','dash.hostCpu':'CPU hosta','dash.systemLoad':'Obciążenie systemu','dash.machineRam':'RAM maszyny','dash.physicalMemory':'Pamięć fizyczna','dash.serverDisk':'Dysk serwera','dash.worldContainer':'Folder światów','dash.jvmThreads':'Wątki JVM','dash.javaProcess':'Proces Java','dash.performanceHistory':'Historia wydajności (5 min)','dash.machineDetails':'Szczegóły maszyny','dash.logBreakdown':'Poziomy logów','dash.worldBreakdown':'Światy','dash.world':'Świat','dash.chunks':'Chunki','dash.entities':'Encje','dash.noData':'Brak danych','dash.activityFeed':'Aktywność','dash.live':'live','dash.waitingActivity':'Oczekiwanie na aktywność...','metric.model':'Model','metric.coresThreads':'Rdzenie / Wątki','metric.processLoad':'Obciążenie procesu','metric.memory':'Pamięć','metric.machineUsed':'Użyte','metric.machineFree':'Wolne','metric.disk':'Dysk','metric.used':'Użyte','metric.free':'Wolne','metric.mount':'Montowanie','metric.runtime':'Runtime','players.name':'Nazwa','players.world':'Świat','players.ping':'Ping','players.actions':'Akcje','players.online':'{count} online','players.joined':'Dołączył','players.left':'Wyszedł','players.unknown':'Nieznany','players.noJoinHistory':'Brak historii wejść z 24 godzin.','players.noCommands':'Brak komend graczy z 24 godzin.','players.kickPlayer':'Wyrzuć gracza','players.banPlayer':'Zbanuj gracza','players.kicked':'Wyrzucono','players.banned':'Zbanowano','players.noReason':'Brak powodu','aliases.hint':'Kliknij alias, aby wstawić go do konsoli, albo Run, aby wykonać.','aliases.empty':'Brak aliasów. Dodaj je w config.yml.','modal.action':'Akcja','modal.player':'Gracz','modal.reason':'Powód','modal.reasonPlaceholder':'Podaj powód...','toast.lost':'Utracono połączenie - ponawianie...','toast.sessionExpired':'Sesja wygasła','toast.rateLimited':'Limit - zwolnij','toast.blocked':'Komenda zablokowana','toast.notificationsEnabled':'Powiadomienia włączone','toast.notificationsDenied':'Powiadomienia odrzucone','toast.notificationsDisabled':'Powiadomienia wyłączone','health.critical':'Krytyczny','health.watch':'Obserwuj','health.good':'Dobry','health.needsAttention':'Wymaga uwagi','health.monitor':'Monitoruj serwer','health.normal':'Główne sygnały w normie','health.excellent':'Świetnie ✓','health.degraded':'Pogorszone','fmt.ofSlots':'{players} z {max} slotów','fmt.maxUnknown':'{players} online, maksimum nieznane','fmt.ofMax':'z {max} max','fmt.chunksLoaded':'Załadowane chunki: {count}','fmt.ofRam':'z {max}MB ({pct}%)','fmt.process':'Proces {value}','fmt.free':'{value} wolne','fmt.daemon':'{value} daemon','fmt.lines':'{count} linii'});
Object.assign(I18N.de,{'login.signing':'Anmelden...','login.missing':'Benutzername und Passwort eingeben.','login.invalid':'Ungültige Zugangsdaten.','login.unreachable':'Server nicht erreichbar.','header.as':'als','header.notifications':'Fehlerbenachrichtigungen umschalten','stat.players':'Spieler','stat.lines':'Zeilen','stat.session':'Sitzung','console.bottom':'Unten','console.searchPlaceholder':'Logs suchen... (Ctrl+F)','console.commandPlaceholder':'Befehl oder !Alias eingeben... (Tab vervollständigt)','console.matches':'{count} Treffer','console.match':'{count} Treffer','console.acHint':'Tab / ↑↓ navigieren, Enter auswählen','dash.waitingStats':'Warte auf Statistiken','dash.playerCapacity':'Spielerkapazität','dash.onlineSlotsUsed':'Genutzte Slots','dash.pluginUptime':'Plugin-Laufzeit','dash.sincePluginEnable':'Seit Plugin-Aktivierung','dash.recentCommands':'Letzte Befehle','dash.playerCommands24h':'Spielerbefehle / 24h','dash.tickRate':'Tickrate','dash.ramUsed':'RAM genutzt','dash.heapMemory':'Heap-Speicher','dash.playersOnline':'Spieler online','dash.worlds':'Welten','dash.totalEntities':'Entitäten gesamt','dash.acrossWorlds':'Über alle Welten','dash.sessionErrors':'Sitzungsfehler','dash.errorLines':'SEVERE / ERROR Zeilen','dash.hostCpu':'Host-CPU','dash.systemLoad':'Systemlast','dash.machineRam':'Maschinen-RAM','dash.physicalMemory':'Physischer Speicher','dash.serverDisk':'Server-Datenträger','dash.worldContainer':'Weltordner','dash.jvmThreads':'JVM-Threads','dash.javaProcess':'Java-Prozess','dash.performanceHistory':'Leistungsverlauf (5 min)','dash.machineDetails':'Maschinendetails','dash.logBreakdown':'Log-Level','dash.worldBreakdown':'Welten','dash.world':'Welt','dash.chunks':'Chunks','dash.entities':'Entitäten','dash.noData':'Noch keine Daten','dash.activityFeed':'Aktivität','dash.live':'live','dash.waitingActivity':'Warte auf Aktivität...','metric.model':'Modell','metric.coresThreads':'Kerne / Threads','metric.processLoad':'Prozesslast','metric.memory':'Speicher','metric.machineUsed':'Genutzt','metric.machineFree':'Frei','metric.disk':'Datenträger','metric.used':'Genutzt','metric.free':'Frei','metric.mount':'Mount','metric.runtime':'Runtime','players.name':'Name','players.world':'Welt','players.ping':'Ping','players.actions':'Aktionen','players.online':'{count} online','players.joined':'Beigetreten','players.left':'Verlassen','players.unknown':'Unbekannt','players.noJoinHistory':'Kein Login-Verlauf der letzten 24 Stunden.','players.noCommands':'Keine Spielerbefehle der letzten 24 Stunden.','players.kickPlayer':'Spieler kicken','players.banPlayer':'Spieler bannen','players.kicked':'Gekickt','players.banned':'Gebannt','players.noReason':'Kein Grund angegeben','aliases.hint':'Alias anklicken zum Einfügen oder Run zum Ausführen.','aliases.empty':'Keine Aliase konfiguriert. In config.yml hinzufügen.','modal.action':'Aktion','modal.player':'Spieler','modal.reason':'Grund','modal.reasonPlaceholder':'Grund eingeben...','toast.lost':'Verbindung verloren - verbinde neu...','toast.sessionExpired':'Sitzung abgelaufen','toast.rateLimited':'Rate limit - langsamer','toast.blocked':'Befehl blockiert','toast.notificationsEnabled':'Benachrichtigungen aktiviert','toast.notificationsDenied':'Benachrichtigungen verweigert','toast.notificationsDisabled':'Benachrichtigungen deaktiviert','health.critical':'Kritisch','health.watch':'Beobachten','health.good':'Gut','health.needsAttention':'Benötigt Aufmerksamkeit','health.monitor':'Server überwachen','health.normal':'Kernsignale normal','health.excellent':'Ausgezeichnet ✓','health.degraded':'Verschlechtert','fmt.ofSlots':'{players} von {max} Slots','fmt.maxUnknown':'{players} online, Maximum unbekannt','fmt.ofMax':'von {max} max','fmt.chunksLoaded':'Geladene Chunks: {count}','fmt.ofRam':'von {max}MB ({pct}%)','fmt.process':'Prozess {value}','fmt.free':'{value} frei','fmt.daemon':'{value} daemon','fmt.lines':'{count} Zeilen'});
Object.assign(I18N.fr,{'login.signing':'Connexion...','login.missing':'Saisir utilisateur et mot de passe.','login.invalid':'Identifiants invalides.','login.unreachable':'Serveur inaccessible.','header.as':'en tant que','header.notifications':'Basculer les notifications erreur','stat.players':'Joueurs','stat.lines':'Lignes','stat.session':'Session','console.bottom':'Bas','console.searchPlaceholder':'Rechercher les logs... (Ctrl+F)','console.commandPlaceholder':'Commande ou !alias... (Tab complète)','console.matches':'{count} résultats','console.match':'{count} résultat','console.acHint':'Tab / ↑↓ naviguer, Enter choisir','dash.waitingStats':'En attente des stats','dash.playerCapacity':'Capacité joueurs','dash.onlineSlotsUsed':'Slots utilisés','dash.pluginUptime':'Temps plugin','dash.sincePluginEnable':'Depuis activation du plugin','dash.recentCommands':'Commandes récentes','dash.playerCommands24h':'Commandes joueurs / 24h','dash.tickRate':'Rythme tick','dash.ramUsed':'RAM utilisée','dash.heapMemory':'Mémoire heap','dash.playersOnline':'Joueurs en ligne','dash.worlds':'Mondes','dash.totalEntities':'Entités totales','dash.acrossWorlds':'Tous les mondes','dash.sessionErrors':'Erreurs session','dash.errorLines':'Lignes SEVERE / ERROR','dash.hostCpu':'CPU hôte','dash.systemLoad':'Charge système','dash.machineRam':'RAM machine','dash.physicalMemory':'Mémoire physique','dash.serverDisk':'Disque serveur','dash.worldContainer':'Dossier mondes','dash.jvmThreads':'Threads JVM','dash.javaProcess':'Processus Java','dash.performanceHistory':'Historique perf (5 min)','dash.machineDetails':'Détails machine','dash.logBreakdown':'Niveaux de log','dash.worldBreakdown':'Mondes','dash.world':'Monde','dash.chunks':'Chunks','dash.entities':'Entités','dash.noData':'Pas encore de données','dash.activityFeed':'Activité','dash.live':'live','dash.waitingActivity':'En attente activité...','metric.model':'Modèle','metric.coresThreads':'Cœurs / Threads','metric.processLoad':'Charge processus','metric.memory':'Mémoire','metric.machineUsed':'Utilisée','metric.machineFree':'Libre','metric.disk':'Disque','metric.used':'Utilisé','metric.free':'Libre','metric.mount':'Montage','metric.runtime':'Runtime','players.name':'Nom','players.world':'Monde','players.ping':'Ping','players.actions':'Actions','players.online':'{count} en ligne','players.joined':'Connecté','players.left':'Parti','players.unknown':'Inconnu','players.noJoinHistory':'Aucun historique de connexion sur 24 h.','players.noCommands':'Aucune commande joueur sur 24 h.','players.kickPlayer':'Expulser joueur','players.banPlayer':'Bannir joueur','players.kicked':'Expulsé','players.banned':'Banni','players.noReason':'Aucune raison','aliases.hint':'Cliquez un alias pour l’insérer, ou Run pour exécuter.','aliases.empty':'Aucun alias configuré. Ajoutez-les dans config.yml.','modal.action':'Action','modal.player':'Joueur','modal.reason':'Raison','modal.reasonPlaceholder':'Entrer une raison...','toast.lost':'Connexion perdue - reconnexion...','toast.sessionExpired':'Session expirée','toast.rateLimited':'Limite atteinte - ralentir','toast.blocked':'Commande bloquée','toast.notificationsEnabled':'Notifications activées','toast.notificationsDenied':'Notifications refusées','toast.notificationsDisabled':'Notifications désactivées','health.critical':'Critique','health.watch':'Surveiller','health.good':'Bon','health.needsAttention':'Attention requise','health.monitor':'Surveiller le serveur','health.normal':'Signaux principaux normaux','health.excellent':'Excellent ✓','health.degraded':'Dégradé','fmt.ofSlots':'{players} sur {max} slots','fmt.maxUnknown':'{players} en ligne, max inconnu','fmt.ofMax':'sur {max} max','fmt.chunksLoaded':'Chunks chargés : {count}','fmt.ofRam':'sur {max}MB ({pct}%)','fmt.process':'Processus {value}','fmt.free':'{value} libre','fmt.daemon':'{value} daemon','fmt.lines':'{count} lignes'});
Object.assign(I18N.en,{'dash.healthHistory':'Health History','dash.noHealthEvents':'No health events yet','health.noReason':'No reason','health.tpsLow':'TPS low','health.tpsDegraded':'TPS degraded','health.errors':'Errors','health.capacity':'Capacity'});
Object.assign(I18N.en,{'players.searchPlaceholder':'Search players...','players.allWorlds':'All worlds','players.groupedByDay':'by day','players.message':'Msg','players.gamemode':'Mode','players.teleport':'TP','players.messagePlayer':'Message Player','players.changeGamemode':'Change Gamemode','players.teleportPlayer':'Teleport Player','players.messagePlaceholder':'Enter message...','players.loadedHistory':'Loaded history','players.topPlayers':'Top Active Players','players.targetPlaceholder':'Target player name','players.noJoinHistory':'No join history yet.','players.noCommands':'No player commands yet.'});
Object.assign(I18N.ru,{'players.searchPlaceholder':'Search players...','players.allWorlds':'All worlds','players.groupedByDay':'by day','players.message':'Msg','players.gamemode':'Mode','players.teleport':'TP','players.messagePlayer':'Message Player','players.changeGamemode':'Change Gamemode','players.teleportPlayer':'Teleport Player','players.messagePlaceholder':'Enter message...','players.loadedHistory':'Loaded history','players.topPlayers':'Top Active Players','players.targetPlaceholder':'Target player name','players.noJoinHistory':'No join history yet.','players.noCommands':'No player commands yet.'});
Object.assign(I18N.en,{'dash.playerCommands24h':'Loaded player commands'});
Object.assign(I18N.ru,{'dash.playerCommands24h':'Loaded player commands'});
Object.assign(I18N.zh,{'dash.playerCommands24h':'Loaded player commands'});
Object.assign(I18N.pl,{'dash.playerCommands24h':'Loaded player commands'});
Object.assign(I18N.de,{'dash.playerCommands24h':'Loaded player commands'});
Object.assign(I18N.fr,{'dash.playerCommands24h':'Loaded player commands'});
Object.assign(I18N.ru,{'dash.healthHistory':'История состояния','dash.noHealthEvents':'Событий состояния пока нет','health.noReason':'Причина не указана','health.tpsLow':'TPS низкий','health.tpsDegraded':'TPS просел','health.errors':'Ошибки','health.capacity':'Заполненность'});
Object.assign(I18N.zh,{'dash.healthHistory':'健康历史','dash.noHealthEvents':'暂无健康事件','health.noReason':'无原因','health.tpsLow':'TPS 过低','health.tpsDegraded':'TPS 下降','health.errors':'错误','health.capacity':'容量'});
Object.assign(I18N.pl,{'dash.healthHistory':'Historia stanu','dash.noHealthEvents':'Brak zdarzeń stanu','health.noReason':'Brak powodu','health.tpsLow':'Niski TPS','health.tpsDegraded':'Spadek TPS','health.errors':'Błędy','health.capacity':'Zapełnienie'});
Object.assign(I18N.de,{'dash.healthHistory':'Zustandsverlauf','dash.noHealthEvents':'Noch keine Zustandsereignisse','health.noReason':'Kein Grund','health.tpsLow':'TPS niedrig','health.tpsDegraded':'TPS verschlechtert','health.errors':'Fehler','health.capacity':'Kapazität'});
Object.assign(I18N.fr,{'dash.healthHistory':'Historique santé','dash.noHealthEvents':'Aucun événement santé','health.noReason':'Aucune raison','health.tpsLow':'TPS bas','health.tpsDegraded':'TPS dégradé','health.errors':'Erreurs','health.capacity':'Capacité'});
Object.assign(I18N.en,{'tab.sessions':'Sessions','sessions.active':'Active Web Sessions','sessions.user':'User','sessions.ip':'IP','sessions.lastSeen':'Last activity','sessions.expires':'Expires','sessions.empty':'No active sessions.','sessions.loading':'Loading sessions...','sessions.error':'Failed to load sessions'});
Object.assign(I18N.ru,{'tab.sessions':'Сессии','sessions.active':'Активные web-сессии','sessions.user':'Пользователь','sessions.ip':'IP','sessions.lastSeen':'Последняя активность','sessions.expires':'Истекает','sessions.empty':'Активных сессий нет.','sessions.loading':'Загрузка сессий...','sessions.error':'Не удалось загрузить сессии'});
Object.assign(I18N.zh,{'tab.sessions':'Sessions','sessions.active':'Active Web Sessions','sessions.user':'User','sessions.ip':'IP','sessions.lastSeen':'Last activity','sessions.expires':'Expires','sessions.empty':'No active sessions.','sessions.loading':'Loading sessions...','sessions.error':'Failed to load sessions'});
Object.assign(I18N.pl,{'tab.sessions':'Sessions','sessions.active':'Active Web Sessions','sessions.user':'User','sessions.ip':'IP','sessions.lastSeen':'Last activity','sessions.expires':'Expires','sessions.empty':'No active sessions.','sessions.loading':'Loading sessions...','sessions.error':'Failed to load sessions'});
Object.assign(I18N.de,{'tab.sessions':'Sessions','sessions.active':'Active Web Sessions','sessions.user':'User','sessions.ip':'IP','sessions.lastSeen':'Last activity','sessions.expires':'Expires','sessions.empty':'No active sessions.','sessions.loading':'Loading sessions...','sessions.error':'Failed to load sessions'});
Object.assign(I18N.fr,{'tab.sessions':'Sessions','sessions.active':'Active Web Sessions','sessions.user':'User','sessions.ip':'IP','sessions.lastSeen':'Last activity','sessions.expires':'Expires','sessions.empty':'No active sessions.','sessions.loading':'Loading sessions...','sessions.error':'Failed to load sessions'});
const LANGS=['en','ru','zh','pl','de','fr'];
const LANG_KEY='bwc_lang';
let savedLang='en';
try{ const rawLang=localStorage.getItem(LANG_KEY); if(LANGS.includes(rawLang)) savedLang=rawLang; }catch(_){}
let currentLang=savedLang;
function t(key,vars={}){
  const text=(I18N[currentLang]&&I18N[currentLang][key])||I18N.en[key]||key;
  return text.replace(/\{(\w+)\}/g,(_,k)=>vars[k] ?? '');
}
function applyTranslations(){
  document.documentElement.lang=currentLang;
  [loginLang,appLang].filter(Boolean).forEach(sel=>{ sel.value=currentLang; });
  document.querySelectorAll('[data-i18n]').forEach(el=>{ el.textContent=t(el.dataset.i18n); });
  document.querySelectorAll('[data-i18n-placeholder]').forEach(el=>{ el.placeholder=t(el.dataset.i18nPlaceholder); });
  document.querySelectorAll('[data-i18n-title]').forEach(el=>{ el.title=t(el.dataset.i18nTitle); });
  const state=['connected','disconnected','connecting'].find(s=>sdot.classList.contains(s));
  if(state) statusText.textContent=t('status.'+state);
  renderHealthState();
  renderHealthHistory();
  lastWorldFilterSignature='__lang__';
  lastSummarySignature='__lang__';
  lastTopPlayersSignature='__lang__';
  lastActivityDaysSignature='__lang__';
  updateWorldFilterOptions(currentPlayerList);
  renderPlayers(currentPlayerList,true);
  if(lastStatsData){
    renderPlayerSummary(lastStatsData.playerActivitySummary||{});
    renderTopActivePlayers(lastStatsData.playerActivitySummary||{});
  }
  if(sessionsLoaded) loadSessions(true);
}
function setLanguage(lang){
  if(!LANGS.includes(lang)) lang='en';
  currentLang=lang;
  try{ localStorage.setItem(LANG_KEY,lang); }catch(_){}
  applyTranslations();
}
[loginLang,appLang].filter(Boolean).forEach(sel=>sel.addEventListener('change',()=>setLanguage(sel.value)));
applyTranslations();
const playerSearch=$('player-search'), playerWorldFilterEl=$('player-world-filter');
if(playerSearch) playerSearch.addEventListener('input',()=>{ playerSearchText=playerSearch.value.trim().toLowerCase(); renderPlayers(currentPlayerList,true); });
if(playerWorldFilterEl) playerWorldFilterEl.addEventListener('change',()=>{ playerWorldFilter=playerWorldFilterEl.value; renderPlayers(currentPlayerList,true); });

// ── Toast ──────────────────────────────────────────────────────────────────
let toastT;
function showToast(msg,type=''){
  clearTimeout(toastT);
  toast.textContent=msg; toast.className='show '+(type||'');
  toastT=setTimeout(()=>toast.className='',3000);
}

// ── Tab switching ──────────────────────────────────────────────────────────
document.querySelectorAll('.tab').forEach(tab=>{
  tab.addEventListener('click',()=>switchToPanel(tab.dataset.panel));
});

function switchToPanel(name){
  if(!name) return;
  // FIX #3: aliases only load when tab is actually clicked (lazy), not on connect
  if(name==='aliases') loadAliases();
  if(name==='sessions') loadSessions();
  if(activePanelName===name) return;
  const prevIdx=panelOrder.indexOf(activePanelName);
  const nextIdx=panelOrder.indexOf(name);
  const enterClass=(prevIdx!==-1&&nextIdx<prevIdx)?'enter-from-left':'enter-from-right';
  document.querySelectorAll('.tab').forEach(t=>t.classList.toggle('active',t.dataset.panel===name));
  document.querySelectorAll('.panel').forEach(p=>p.classList.remove('active','enter-from-right','enter-from-left'));
  const nextPanel=$('panel-'+name);
  nextPanel.classList.add('active',enterClass);
  nextPanel.addEventListener('animationend',()=>nextPanel.classList.remove('enter-from-right','enter-from-left'),{once:true});
  activePanelName=name;
  requestAnimationFrame(()=>animatePanelContent(name));
}

function staggerAnimate(nodes,step=40){
  nodes.filter(Boolean).forEach((el,i)=>{
    el.classList.remove('ui-enter');
    el.style.setProperty('--delay',`${i*step}ms`);
    void el.offsetWidth;
    el.classList.add('ui-enter');
    el.addEventListener('animationend',()=>{ el.classList.remove('ui-enter'); el.style.removeProperty('--delay'); },{once:true});
  });
}

function animatePanelContent(name){
  if(name==='console'){ staggerAnimate([$('stats-bar'),$('console-wrap'),$('search-bar'),$('input-bar')],50); return; }
  if(name==='players'){ animatePlayersPanel(); return; }
  if(name==='aliases'){ staggerAnimate([$('panel-aliases').querySelector('.alias-hint'),...document.querySelectorAll('#alias-list > *')],26); return; }
  if(name==='sessions'){ staggerAnimate([...document.querySelectorAll('#sessions-tbody tr')],26); return; }
  if(name==='dash'){ staggerAnimate([...document.querySelectorAll('.dash-grid .kpi'),...document.querySelectorAll('.chart-card'),...document.querySelectorAll('.machine-card')],18); }
}

function animatePlayersPanel(force=false){
  const signature=lastPlayersStructureSignature;
  if(!force&&signature===lastAnimatedPlayersSignature) return;
  lastAnimatedPlayersSignature=signature;
  const nodes=[...document.querySelectorAll('#panel-players .players-card'),...document.querySelectorAll('#player-tbody tr')];
  staggerAnimate(nodes.length?nodes:[$('no-players-msg')],26);
}

// ── Status ─────────────────────────────────────────────────────────────────
function setStatus(state){
  sdot.className='status-dot '+state;
  statusText.textContent=t('status.'+state);
  const ok=state==='connected';
  cmdInput.disabled=!ok; btnSend.disabled=!ok;
}

setInterval(()=>{
  if(!sessionUser) return;
  const s=Math.floor((Date.now()-sessionStart)/1000);
  svSession.textContent=`${String(Math.floor(s/60)).padStart(2,'0')}:${String(s%60).padStart(2,'0')}`;
},1000);

// ── Helpers ────────────────────────────────────────────────────────────────
function levelOf(line){
  const m=line.match(/\[[\d:]+\s+(\w+)\]/);
  return m?m[1].toUpperCase():'INFO';
}
function filterGroup(lv){
  if(lv==='WARN'||lv==='WARNING') return 'WARN';
  if(lv==='SEVERE'||lv==='ERROR') return 'SEVERE';
  if(lv==='DEBUG'||lv==='FINE'||lv==='FINER'||lv==='FINEST') return 'DEBUG';
  return 'INFO';
}

// ── Log line rendering ─────────────────────────────────────────────────────
function appendLine(raw){
  lineCount++;
  svLines.textContent=lineCount;
  const lv=levelOf(raw);
  const fg=filterGroup(lv);

  if(fg==='SEVERE'){ levelCounts.SEVERE++; sessionErrors++; const e=$('kpi-errors'); if(e) e.textContent=sessionErrors; pushActivity('err','&#128308;',raw.substring(0,90)); }
  else if(fg==='WARN'){ levelCounts.WARN++; pushActivity('warn','&#128993;',raw.substring(0,90)); }
  else if(fg==='INFO') levelCounts.INFO++;
  else levelCounts.DEBUG++;
  updateLevelBars();

  if(fg==='SEVERE'&&notifEnabled){
    notifCount++; nbadge.classList.add('show');
    if(document.hidden) new Notification('BWC Error',{body:raw.substring(0,80)}).catch(()=>{});
    playSound();
  }

  const div=document.createElement('div');
  div.className='log-line '+lv+' log-enter';
  div.dataset.raw=raw;
  let html=raw.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
  html=html.replace(/(\[\d{2}:\d{2}:\d{2})/,'<span class="log-ts">$1</span>');
  if(searchQuery){ const re=new RegExp(escRe(searchQuery),'gi'); html=html.replace(re,m=>'<span class="hl">'+m+'</span>'); }
  div.innerHTML=html;
  applyVis(div);
  output.appendChild(div);
  div.addEventListener('animationend',()=>div.classList.remove('log-enter'),{once:true});
  while(output.children.length>MAX_LINES) output.removeChild(output.firstChild);
  if(autoScroll) output.scrollTop=output.scrollHeight;
}

function applyVis(div){
  const lv=div.dataset.raw?levelOf(div.dataset.raw):'INFO';
  const fg=filterGroup(lv);
  const ok=activeFilters.has(fg);
  const textOk=!filterText||div.dataset.raw.toLowerCase().includes(filterText);
  div.style.display=(ok&&textOk)?'':'none';
}

function reapply(){ output.querySelectorAll('.log-line').forEach(applyVis); }

// ── Search ─────────────────────────────────────────────────────────────────
function doSearch(){
  searchQuery=searchInput.value.trim();
  output.querySelectorAll('.log-line').forEach(div=>{
    let html=div.dataset.raw.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
    html=html.replace(/(\[\d{2}:\d{2}:\d{2})/,'<span class="log-ts">$1</span>');
    if(searchQuery){ const re=new RegExp(escRe(searchQuery),'gi'); html=html.replace(re,m=>'<span class="hl">'+m+'</span>'); }
    div.innerHTML=html;
  });
  searchMatches=Array.from(output.querySelectorAll('.hl'));
  searchCount.textContent=searchMatches.length?t(searchMatches.length>1?'console.matches':'console.match',{count:searchMatches.length}):'';
  if(searchMatches.length){ searchIdx=0; searchMatches[0].scrollIntoView({block:'center'}); }
}

searchInput.addEventListener('input',doSearch);
searchInput.addEventListener('keydown',e=>{
  if(e.key==='Enter'&&searchMatches.length){ searchIdx=(searchIdx+1)%searchMatches.length; searchMatches[searchIdx].scrollIntoView({block:'center'}); }
});
document.addEventListener('keydown',e=>{
  if((e.ctrlKey||e.metaKey)&&e.key==='f'){ e.preventDefault(); searchInput.focus(); searchInput.select(); }
});

// ── Filter toggles ─────────────────────────────────────────────────────────
document.querySelectorAll('.ftog').forEach(btn=>{
  btn.addEventListener('click',()=>{
    const lv=btn.dataset.lv;
    const rel={WARN:['WARN','WARNING'],SEVERE:['SEVERE','ERROR'],DEBUG:['DEBUG','FINE','FINER','FINEST'],INFO:['INFO']}[lv]||[lv];
    const on=btn.classList.toggle('active');
    rel.forEach(l=>on?activeFilters.add(l):activeFilters.delete(l));
    reapply();
  });
});

// ── Scroll tracking ────────────────────────────────────────────────────────
output.addEventListener('scroll',()=>{
  autoScroll=output.scrollHeight-output.scrollTop-output.clientHeight<50;
  scrollAnchor.classList.toggle('show',!autoScroll);
});
scrollAnchor.addEventListener('click',()=>{ output.scrollTop=output.scrollHeight; autoScroll=true; scrollAnchor.classList.remove('show'); });

// ── FIX #2: WebSocket always connects to the page's own host (no hardcoded URL) ──
function connectWs(force=false){
  if(manualDisconnect) return;
  if(ws&&(ws.readyState===WebSocket.OPEN||ws.readyState===WebSocket.CONNECTING)&&!force) return;
  clearTimeout(reconnectTimer);
  setStatus('connecting');
  const proto=location.protocol==='https:'?'wss:':'ws:';
  // Always use the same host+port the page was loaded from
  ws=new WebSocket(proto+'//'+location.host+'/ws');
  ws.onopen=()=>{ reconnectAttempt=0; reconnectDelay=1000; setStatus('connected'); showToast(t('toast.connected'),'success'); };
  ws.onmessage=e=>{
    try{
      const msg=JSON.parse(e.data);
      if(msg.type==='log')          appendLine(msg.line);
      else if(msg.type==='stats')   handleStats(msg);
      else if(msg.type==='completions') handleCompletions(msg);
      else if(msg.type==='control') handleControl(msg.event);
    }catch(_){}
  };
  ws.onclose=async()=>{
    if(manualDisconnect) return;
    setStatus('disconnected');
    const authed=await isStillAuthenticated();
    if(!authed){ handleControl('SESSION_EXPIRED'); return; }
    showToast(t('toast.lost'),'error');
    scheduleReconnect();
  };
  ws.onerror=()=>ws.close();
}

function scheduleReconnect(){
  clearTimeout(reconnectTimer);
  const delay=Math.min(reconnectDelay,10000)+Math.floor(Math.random()*250);
  reconnectTimer=setTimeout(()=>connectWs(true),delay);
  reconnectAttempt++;
  reconnectDelay=Math.min(Math.round(reconnectDelay*1.7),10000);
}

async function isStillAuthenticated(){
  try{ const r=await fetch('/api/status',{cache:'no-store'}); const d=await r.json(); return !!d.authenticated; }
  catch(_){ return true; }
}

function handleControl(event){
  if(event==='SESSION_EXPIRED'){ showToast(t('toast.sessionExpired'),'error'); setTimeout(doLogout,1500); }
  else if(event==='RATE_LIMITED') showToast(t('toast.rateLimited'),'warn');
  else if(event==='BLOCKED')      showToast(t('toast.blocked'),'warn');
}

// ── Stats ──────────────────────────────────────────────────────────────────
function getHealthStatus(snapshot){
  if(!snapshot) return 'good';
  if(snapshot.tps<15||snapshot.sessionErrors>0) return 'critical';
  if(snapshot.tps<18||(snapshot.hasMaxPlayers&&snapshot.capacity>=90)) return 'watch';
  return 'good';
}

function getHealthReasons(snapshot){
  if(!snapshot) return [];
  const reasons=[];
  if(snapshot.tps<15) reasons.push({code:'tps',level:'red',label:t('health.tpsLow'),title:'TPS: '+snapshot.tps.toFixed(1)+' < 15'});
  else if(snapshot.tps<18) reasons.push({code:'tps',level:'warn',label:t('health.tpsDegraded'),title:'TPS: '+snapshot.tps.toFixed(1)+' < 18'});
  if(snapshot.sessionErrors>0) reasons.push({code:'errors',level:'red',label:t('health.errors')+': '+snapshot.sessionErrors,title:t('dash.sessionErrors')+': '+snapshot.sessionErrors});
  if(snapshot.hasMaxPlayers&&snapshot.capacity>=90) reasons.push({code:'capacity',level:'warn',label:t('health.capacity')+': '+snapshot.capacity+'%',title:t('dash.playerCapacity')+': '+snapshot.capacity+'%'});
  return reasons;
}

function renderHealthState(){
  const snapshot=lastHealthSnapshot;
  if(!snapshot){
    const healthCard=$('kpi-health-card');
    if(healthCard) healthCard.removeAttribute('title');
    const badges=$('kpi-health-reasons');
    if(badges){ badges.className='health-reasons'; badges.innerHTML=''; }
    return;
  }
  const status=getHealthStatus(snapshot);
  const reasons=getHealthReasons(snapshot);
  setText('kpi-health', status==='critical'?t('health.critical'):status==='watch'?t('health.watch'):t('health.good'));
  setText('kpi-health-sub', status==='critical'?t('health.needsAttention'):status==='watch'?t('health.monitor'):t('health.normal'));
  const healthCard=$('kpi-health-card');
  if(healthCard){
    healthCard.className='kpi '+(status==='critical'?'red':status==='watch'?'warn':'green');
    if(status==='good') healthCard.removeAttribute('title');
    else healthCard.title=reasons.map(r=>r.title).join('\n');
  }
  const badges=$('kpi-health-reasons');
  if(!badges) return;
  if(status==='good'||!reasons.length){ badges.className='health-reasons'; badges.innerHTML=''; return; }
  badges.className='health-reasons show';
  badges.innerHTML=reasons.map(r=>`<span class="health-badge ${r.level}">${esc(r.label)}</span>`).join('');
}

function updateHealthHistory(status,snapshot){
  const now=Date.now();
  const reasonKey=getHealthReasons(snapshot).map(r=>r.code).join('|');
  if(status==='good'){
    if(currentHealthEvent){
      currentHealthEvent.end=now;
      healthHistory.unshift(currentHealthEvent);
      currentHealthEvent=null;
    }
  }else if(currentHealthEvent&&currentHealthEvent.status===status&&currentHealthEvent.reasonKey===reasonKey){
    currentHealthEvent.snapshot={...snapshot};
  }else{
    if(currentHealthEvent){
      currentHealthEvent.end=now;
      healthHistory.unshift(currentHealthEvent);
    }
    currentHealthEvent={status,reasonKey,start:now,end:null,snapshot:{...snapshot}};
  }
  while(healthHistory.length>MAX_HEALTH_HISTORY) healthHistory.pop();
  renderHealthHistory();
}

function renderHealthHistory(){
  const box=$('health-history'); if(!box) return;
  const events=[currentHealthEvent,...healthHistory].filter(Boolean).slice(0,8);
  setText('health-history-count', events.length);
  if(!events.length){ box.innerHTML=`<div class="health-event-empty">${t('dash.noHealthEvents')}</div>`; return; }
  box.innerHTML=events.map(ev=>{
    const reasons=getHealthReasons(ev.snapshot).map(r=>r.title).join('; ')||t('health.noReason');
    const start=new Date(ev.start).toLocaleTimeString('en-GB',{hour:'2-digit',minute:'2-digit',second:'2-digit'});
    const duration=fmtShortDuration((ev.end||Date.now())-ev.start);
    const state=ev.status==='critical'?t('health.critical'):t('health.watch');
    const cls=ev.status==='critical'?'red':'warn';
    return `<div class="health-event ${cls}"><div class="health-event-top"><span class="health-event-state">${esc(state)}</span><span class="health-event-time">${start} - ${duration}</span></div><div class="health-event-reasons">${esc(reasons)}</div></div>`;
  }).join('');
}

function handleStats(data){
  lastStatsData=data;
  const tps=data.tps||0, ramUsed=data.ramUsed||0, ramMax=data.ramMax||0;
  const players=data.players||0, maxPlayers=data.maxPlayers||0;
  const hasMaxPlayers=Number(maxPlayers)>0;
  const capacity=hasMaxPlayers ? Math.round(players/maxPlayers*100) : 0;
  setText('kpi-capacity', hasMaxPlayers ? capacity+'%' : '--');
  setText('kpi-capacity-sub', hasMaxPlayers ? t('fmt.ofSlots',{players,max:maxPlayers}) : t('fmt.maxUnknown',{players}));
  setText('kpi-uptime', fmtDuration(data.uptimeSeconds));
  setText('kpi-player-cmds', Array.isArray(data.playerCommands)?data.playerCommands.length:0);
  lastHealthSnapshot={tps,sessionErrors,hasMaxPlayers,capacity};
  const healthStatus=getHealthStatus(lastHealthSnapshot);
  updateHealthHistory(healthStatus,lastHealthSnapshot);
  renderHealthState();

  svTps.textContent=tps.toFixed(1);
  statTpsEl.className='stat '+(tps>=18?'tps-good':tps>=15?'tps-ok':'tps-bad');
  svRam.textContent=ramUsed+'MB';
  svPlayers.textContent=players;

  const kpiTps=$('kpi-tps');
  if(kpiTps){
    kpiTps.textContent=tps.toFixed(1);
    $('kpi-tps-sub').textContent=tps>=18?t('health.excellent'):tps>=15?t('health.degraded'):t('health.critical')+' \u26a0';
    kpiTps.parentElement.className='kpi '+(tps>=18?'green':tps>=15?'warn':'red');
  }
  const kpiRam=$('kpi-ram');
  if(kpiRam){ kpiRam.textContent=ramUsed+'MB'; $('kpi-ram-sub').textContent=t('fmt.ofRam',{max:ramMax,pct:Math.round(ramUsed/(ramMax||1)*100)}); }
  const kpiPlayers=$('kpi-players');
  if(kpiPlayers){ kpiPlayers.textContent=players; $('kpi-players-sub').textContent=t('fmt.ofMax',{max:maxPlayers}); $('player-count-tab').textContent='('+players+')'; }

  if(data.worlds){
    $('kpi-worlds').textContent=data.worlds.length;
    const chunks=data.worlds.reduce((a,w)=>a+(w.chunks||0),0);
    $('kpi-worlds-sub').textContent=t('fmt.chunksLoaded',{count:chunks});
    const entities=data.worlds.reduce((a,w)=>a+(w.entities||0),0);
    $('kpi-entities').textContent=entities;
    renderWorldTable(data.worlds);
  }

  const bt=$('badge-tps'); if(bt) bt.textContent=tps.toFixed(1)+' TPS';
  const br=$('badge-ram'); if(br) br.textContent=ramUsed+'MB';
  const bp=$('badge-players'); if(bp) bp.textContent=t('players.online',{count:players});

  if(data.tpsHistory)     updateChart(tpsChart,    data.tpsHistory);
  if(data.ramHistory)     updateChart(ramChart,     data.ramHistory);
  if(data.playersHistory) updateChart(playersChart, data.playersHistory);
  if(data.cpuHistory)     updateChart(cpuChart,     data.cpuHistory);
  if(data.system)         handleSystemStats(data.system);
  if(data.playerList!==undefined) renderPlayers(data.playerList);
  renderPlayerActivityDays(data.playerActivityDays||[]);
  renderPlayerSummary(data.playerActivitySummary||{});
  renderTopActivePlayers(data.playerActivitySummary||{});
}

function handleSystemStats(system){
  if(!system||!system.enabled) return;
  const cpu=system.cpu||{}, mem=system.memory||{}, disk=system.disk||{}, jvm=system.jvm||{}, os=system.os||{};
  const cpuLoad=Number(cpu.systemLoadPercent||0), procLoad=Number(cpu.processLoadPercent||0);
  const ramPct=Number(mem.usedPercent||0), diskPct=Number(disk.usedPercent||0);

  setText('kpi-cpu', fmtPct(cpuLoad));
  setText('kpi-cpu-sub', t('fmt.process',{value:fmtPct(procLoad)}));
  setKpiState('kpi-cpu', cpuLoad, 70, 90);
  setText('badge-cpu', fmtPct(cpuLoad));
  setWidth('sys-cpu-meter', cpuLoad);

  setText('kpi-host-ram', fmtPct(ramPct));
  setText('kpi-host-ram-sub', `${fmtBytes(mem.usedBytes)} / ${fmtBytes(mem.totalBytes)}`);
  setKpiState('kpi-host-ram', ramPct, 75, 90);
  setWidth('sys-ram-meter', ramPct);

  if(disk.totalBytes){
    setText('kpi-disk', fmtPct(diskPct));
    setText('kpi-disk-sub', t('fmt.free',{value:fmtBytes(disk.usableBytes)}));
    setKpiState('kpi-disk', diskPct, 80, 92);
    setWidth('sys-disk-meter', diskPct);
    setText('sys-disk-used', `${fmtBytes(disk.usedBytes)} of ${fmtBytes(disk.totalBytes)}`);
    setText('sys-disk-free', fmtBytes(disk.usableBytes));
    setText('sys-disk-mount', disk.mount||disk.path||'—');
  }

  setText('kpi-threads', jvm.threads??'—');
  setText('kpi-threads-sub', t('fmt.daemon',{value:jvm.daemonThreads??'—'}));
  setText('sys-cpu-model', cpu.model||'—');
  setText('sys-cpu-cores', `${cpu.physicalCores??'—'} / ${cpu.logicalCores??'—'}`);
  setText('sys-cpu-process', fmtPct(procLoad));
  setText('sys-ram-used', `${fmtBytes(mem.usedBytes)} of ${fmtBytes(mem.totalBytes)}`);
  setText('sys-ram-free', fmtBytes(mem.availableBytes));
  setText('sys-jvm-heap', `${fmtBytes(jvm.heapUsedBytes)} of ${fmtBytes(jvm.heapMaxBytes)}`);
  setText('sys-os', `${os.family||'—'} ${os.arch||''}`.trim());
  setText('sys-java', `${jvm.javaVersion||'—'} ${jvm.javaVendor||''}`.trim());
  setText('sys-pid', jvm.pid??'—');
  setText('sys-jvm-uptime', fmtDuration(jvm.uptimeSeconds));
}

function renderWorldTable(worlds){
  const tbody=$('world-tbody'); if(!tbody) return;
  const colors=['var(--accent)','var(--info)','var(--purple)','var(--success)','var(--warn)'];
  const worldsSorted=[...(worlds||[])].sort((a,b)=>((b.entities||0)+(b.chunks||0))-((a.entities||0)+(a.chunks||0)));
  tbody.innerHTML=worldsSorted.map((w,i)=>
    `<tr><td><span class="world-dot" style="background:${colors[i%colors.length]}"></span>${esc(w.name||'?')}</td><td>${w.chunks||0}</td><td>${w.entities||0}</td></tr>`
  ).join('');
}

function renderTopActivePlayers(summary){
  const box=$('top-active-players'); if(!box) return;
  const list=Array.isArray(summary.topPlayers)?summary.topPlayers:[];
  const sig=JSON.stringify(list)+'|'+currentLang;
  if(sig===lastTopPlayersSignature) return;
  lastTopPlayersSignature=sig;
  if(!list.length){ box.innerHTML=`<div class="history-empty">${t('dash.noData')}</div>`; return; }
  box.innerHTML=list.map(p=>`<div class="activity-item cmd"><span class="activity-icon">&#9889;</span><span class="activity-text"><strong>${esc(p.player||t('players.unknown'))}</strong> ${p.score||0} total</span><span class="activity-time">CMD ${p.commands||0}</span></div>`).join('');
}

function updateChart(chart,data){
  if(!chart) return;
  chart.data.labels=data.map((_,i)=>i);
  chart.data.datasets[0].data=data;
  chart.update('none');
}

// ── Level bars ─────────────────────────────────────────────────────────────
function updateLevelBars(){
  const total=Math.max(1,levelCounts.INFO+levelCounts.WARN+levelCounts.SEVERE+levelCounts.DEBUG);
  const set=(fid,cid,v)=>{ const f=$(fid),c=$(cid); if(f) f.style.width=Math.round(v/total*100)+'%'; if(c) c.textContent=v; };
  set('bar-error','cnt-error',levelCounts.SEVERE);
  set('bar-warn','cnt-warn',levelCounts.WARN);
  set('bar-info','cnt-info',levelCounts.INFO);
  set('bar-debug','cnt-debug',levelCounts.DEBUG);
  const badge=$('badge-logcount');
  if(badge) badge.textContent=t('fmt.lines',{count:levelCounts.INFO+levelCounts.WARN+levelCounts.SEVERE+levelCounts.DEBUG});
}

// ── Activity feed ──────────────────────────────────────────────────────────
function pushActivity(type,icon,text,html=false){
  const t=new Date().toLocaleTimeString('en-GB',{hour:'2-digit',minute:'2-digit',second:'2-digit'});
  activityLog.unshift({type,icon,text,html,t});
  if(activityLog.length>MAX_ACTIVITY) activityLog.pop();
  renderActivity();
}

function renderActivity(){
  const feed=$('activity-feed'); if(!feed) return;
  if(!activityLog.length){ feed.innerHTML=`<div class="activity-item"><span class="activity-icon">&#128564;</span><span class="activity-text" style="color:var(--text-muted)">${t('dash.waitingActivity')}</span></div>`; return; }
  feed.innerHTML=activityLog.slice(0,18).map(a=>`<div class="activity-item ${a.type}"><span class="activity-icon">${a.icon}</span><span class="activity-text">${a.html?a.text:esc(a.text)}</span><span class="activity-time">${a.t}</span></div>`).join('');
}

// ── Charts ─────────────────────────────────────────────────────────────────
function makeChart(id,label,color,max){
  const ctx=document.getElementById(id).getContext('2d');
  return new Chart(ctx,{
    type:'line',
    data:{labels:[],datasets:[{label,data:[],borderColor:color,backgroundColor:color+'18',borderWidth:1.8,pointRadius:0,fill:true,tension:.35}]},
    options:{responsive:true,maintainAspectRatio:false,animation:false,
      plugins:{legend:{display:false},tooltip:{mode:'index',intersect:false,backgroundColor:'#1e1e1e',borderColor:'rgba(255,255,255,.10)',borderWidth:1,titleColor:'#eee',bodyColor:'#eee',callbacks:{label:c=>label+': '+c.parsed.y}}},
      scales:{x:{display:false},y:{min:0,max:max||undefined,grid:{color:'rgba(255,255,255,.042)'},ticks:{color:'#aaa',font:{size:10},maxTicksLimit:4}}}}
  });
}

function initCharts(){
  if(chartsInitialized) return;
  tpsChart     = makeChart('chart-tps',    'TPS',     '#f23987', 20);
  ramChart     = makeChart('chart-ram',    'RAM MB',  '#4fc3f7');
  playersChart = makeChart('chart-players','Players', '#d05ce3');
  cpuChart     = makeChart('chart-cpu',    'CPU %',   '#00e676', 100);
  chartsInitialized=true;
}

// ── Player list ─────────────────────────────────────────────────────────────
function updateWorldFilterOptions(list){
  const el=$('player-world-filter'); if(!el) return;
  const worlds=[...new Set((list||[]).map(p=>p.world||'').filter(Boolean))].sort();
  const sig=worlds.join('|')+'|'+currentLang;
  if(sig===lastWorldFilterSignature) return;
  lastWorldFilterSignature=sig;
  const selected=playerWorldFilter;
  el.innerHTML=`<option value="">${t('players.allWorlds')}</option>`+worlds.map(w=>`<option value="${esc(w)}">${esc(w)}</option>`).join('');
  if(worlds.includes(selected)){ el.value=selected; } else { playerWorldFilter=''; el.value=''; }
}

function renderPlayers(list,force=false){
  const tbody=$('player-tbody'), tbl=$('player-table'), msg=$('no-players-msg');
  currentPlayerList=Array.isArray(list)?list:[];
  updateWorldFilterOptions(currentPlayerList);
  let safeList=currentPlayerList;
  if(playerSearchText) safeList=safeList.filter(p=>(p.name||'').toLowerCase().includes(playerSearchText));
  if(playerWorldFilter) safeList=safeList.filter(p=>(p.world||'')===playerWorldFilter);
  setText('players-online-badge', t('players.online',{count:currentPlayerList.length}));
  const structureSig=JSON.stringify(safeList.map(p=>({n:p.name||'',w:p.world||'',o:!!p.op})))+'|'+playerSearchText+'|'+playerWorldFilter+'|'+currentLang;
  const visualSig=JSON.stringify(safeList.map(p=>({n:p.name||'',w:p.world||'',o:!!p.op,p:p.ping||0})))+'|'+playerSearchText+'|'+playerWorldFilter+'|'+currentLang;
  const structureChanged=structureSig!==lastPlayersStructureSignature;
  if(!force&&visualSig===lastPlayersVisualSignature) return;
  lastPlayersStructureSignature=structureSig;
  lastPlayersVisualSignature=visualSig;

  const newNames=new Set(currentPlayerList.map(p=>p.name));
  newNames.forEach(n=>{ if(!prevPlayerNames.has(n)) pushActivity('join','&#128994;',`<strong>${esc(n)}</strong> ${t('players.joined').toLowerCase()}`,true); });
  prevPlayerNames.forEach(n=>{ if(!newNames.has(n)) pushActivity('leave','&#9899;',`<strong>${esc(n)}</strong> ${t('players.left').toLowerCase()}`,true); });
  prevPlayerNames=newNames;

  if(!safeList.length){ tbody.innerHTML=''; tbl.style.display='none'; msg.style.display=''; if(activePanelName==='players'&&structureChanged) animatePlayersPanel(true); return; }
  tbl.style.display=''; msg.style.display='none'; tbody.innerHTML='';
  safeList.forEach(p=>{
    const ping=p.ping||0, pc=ping<80?'ping-good':ping<200?'ping-ok':'ping-bad';
    const tr=document.createElement('tr');
    tr.innerHTML=`<td>${esc(p.name)}${p.op?'<span class="player-op">OP</span>':''}</td><td>${esc(p.world||'\u2014')}</td><td class="${pc}">${ping}ms</td><td><div class="player-actions"><button class="action-btn kick" onclick="openModal('kick','${esc(p.name)}')">${t('players.kick')}</button><button class="action-btn ban" onclick="openModal('ban','${esc(p.name)}')">${t('players.ban')}</button><button class="action-btn neutral" onclick="openModal('msg','${esc(p.name)}')">${t('players.message')}</button><button class="action-btn neutral" onclick="openModal('gamemode','${esc(p.name)}')">${t('players.gamemode')}</button><button class="action-btn neutral" onclick="openModal('tp','${esc(p.name)}')">${t('players.teleport')}</button></div></td>`;
    tbody.appendChild(tr);
  });
  if(activePanelName==='players'&&structureChanged) animatePlayersPanel(true);
}

function renderPlayerSummary(summary){
  const box=$('player-summary'); if(!box) return;
  const sig=JSON.stringify(summary||{});
  if(sig===lastSummarySignature) return;
  lastSummarySignature=sig;
  const joins=summary.joins||0, leaves=summary.leaves||0, commands=summary.commands||0;
  box.innerHTML=`<div class="player-summary-card"><span>${t('players.joined')}</span><strong>${joins}</strong></div><div class="player-summary-card"><span>${t('players.left')}</span><strong>${leaves}</strong></div><div class="player-summary-card"><span>CMD</span><strong>${commands}</strong></div>`;
}

function renderPlayerActivityDays(days){
  const eventsBox=$('player-event-history'), commandsBox=$('player-command-history');
  if(!eventsBox||!commandsBox) return;
  const safe=Array.isArray(days)?days:[];
  const sig=JSON.stringify(safe)+'|'+currentLang+'|'+[...expandedActivityDays].sort().join(',');
  if(sig===lastActivityDaysSignature) return;
  lastActivityDaysSignature=sig;
  if(!safe.length){
    eventsBox.innerHTML=`<div class="history-empty">${t('players.noJoinHistory')}</div>`;
    commandsBox.innerHTML=`<div class="history-empty">${t('players.noCommands')}</div>`;
    return;
  }
  eventsBox.innerHTML=renderActivityGroups(safe,item=>item.type==='join'||item.type==='leave',t('players.noJoinHistory'));
  commandsBox.innerHTML=renderActivityGroups(safe,item=>item.type==='command',t('players.noCommands'));
}

function renderActivityGroups(days,predicate,emptyText){
  const html=days.map((day,idx)=>{
    const items=(day.items||[]).filter(predicate);
    if(!items.length) return '';
    const key=day.date||day.label||String(idx);
    if(idx<2&&!expandedActivityDays.has(key)) expandedActivityDays.add(key);
    const collapsed=expandedActivityDays.has(key)?'':' collapsed';
    return `<div class="history-day${collapsed}" data-day="${esc(key)}"><button class="history-day-head" onclick="toggleActivityDay('${esc(key)}')"><span>${esc(day.label||key)}</span><span>${items.length}</span></button><div class="history-day-body">${items.map(renderActivityItem).join('')}</div></div>`;
  }).join('');
  return html||`<div class="history-empty">${emptyText}</div>`;
}

function renderActivityItem(item){
  if(item.type==='command') return `<div class="history-item command"><span class="history-kind">CMD</span><span class="history-main"><strong>${esc(item.player||t('players.unknown'))}</strong><span class="history-command">${esc(item.command||'')}</span></span><span class="history-time">${fmtTime(item.timestamp)}</span></div>`;
  const type=item.type==='leave'?'leave':'join';
  const label=type==='leave'?t('players.left'):t('players.joined');
  return `<div class="history-item ${type}"><span class="history-kind">${label}</span><span class="history-main"><strong>${esc(item.player||t('players.unknown'))}</strong></span><span class="history-time">${fmtTime(item.timestamp)}</span></div>`;
}

window.toggleActivityDay=function(key){
  if(expandedActivityDays.has(key)) expandedActivityDays.delete(key); else expandedActivityDays.add(key);
  lastActivityDaysSignature='__force__';
  if(lastStatsData) renderPlayerActivityDays(lastStatsData.playerActivityDays||[]);
};

function renderPlayerEvents(events){
  const box=$('player-event-history'); if(!box) return;
  const safe=Array.isArray(events)?events.slice(-80).reverse():[];
  const sig=JSON.stringify(safe);
  if(sig===lastPlayerEventsSignature) return;
  lastPlayerEventsSignature=sig;
  if(!safe.length){ box.innerHTML=`<div class="history-empty">${t('players.noJoinHistory')}</div>`; return; }
  box.innerHTML=safe.map(e=>{
    const type=e.type==='leave'?'leave':'join';
    const label=type==='leave'?t('players.left'):t('players.joined');
    return `<div class="history-item ${type}"><span class="history-kind">${label}</span><span class="history-main"><strong>${esc(e.player||t('players.unknown'))}</strong></span><span class="history-time">${fmtTime(e.timestamp)}</span></div>`;
  }).join('');
}

function renderPlayerCommands(commands){
  const box=$('player-command-history'); if(!box) return;
  const safe=Array.isArray(commands)?commands.slice(-120).reverse():[];
  const sig=JSON.stringify(safe);
  if(sig===lastPlayerCommandsSignature) return;
  lastPlayerCommandsSignature=sig;
  if(!safe.length){ box.innerHTML=`<div class="history-empty">${t('players.noCommands')}</div>`; return; }
  box.innerHTML=safe.map(c=>`<div class="history-item command"><span class="history-kind">CMD</span><span class="history-main"><strong>${esc(c.player||t('players.unknown'))}</strong><span class="history-command">${esc(c.command||'')}</span></span><span class="history-time">${fmtTime(c.timestamp)}</span></div>`).join('');
}

// ── Modal ──────────────────────────────────────────────────────────────────
window.openModal=function(action,player){
  modalAction=action; modalPlayer.value=player; modalReason.value='';
  const labels={
    kick:[t('players.kickPlayer'),t('modal.reasonPlaceholder'),t('players.kick')],
    ban:[t('players.banPlayer'),t('modal.reasonPlaceholder'),t('players.ban')],
    msg:[t('players.messagePlayer'),t('players.messagePlaceholder'),t('players.message')],
    gamemode:[t('players.changeGamemode'),'survival | creative | adventure | spectator',t('players.gamemode')],
    tp:[t('players.teleportPlayer'),t('players.targetPlaceholder'),t('players.teleport')]
  };
  const selected=labels[action]||labels.kick;
  modalTitle.textContent=selected[0];
  modalReason.placeholder=selected[1];
  modalConfirm.className='mbtn confirm '+(action==='kick'?'kick-mode':'');
  modalConfirm.textContent=selected[2];
  modal.classList.add('show'); modalReason.focus();
};
modalCancel.addEventListener('click',()=>modal.classList.remove('show'));
modal.addEventListener('click',e=>{ if(e.target===modal) modal.classList.remove('show'); });
modalConfirm.addEventListener('click',()=>{
  if(!ws||ws.readyState!==WebSocket.OPEN) return;
  const value=modalReason.value.trim();
  if(modalAction==='msg') ws.send(JSON.stringify({type:'msg',player:modalPlayer.value,message:value}));
  else if(modalAction==='gamemode') ws.send(JSON.stringify({type:'gamemode',player:modalPlayer.value,mode:value}));
  else if(modalAction==='tp') ws.send(JSON.stringify({type:'tp',player:modalPlayer.value,target:value}));
  else ws.send(JSON.stringify({type:modalAction,player:modalPlayer.value,reason:value||t('players.noReason')}));
  modal.classList.remove('show');
  const act=modalConfirm.textContent;
  showToast(`${act} ${modalPlayer.value}`,'warn');
  pushActivity('cmd','&#9889;',`<strong>${esc(act)}</strong> ${esc(modalPlayer.value)}${value?': '+esc(value):''}`,true);
});
modalReason.addEventListener('keydown',e=>{ if(e.key==='Enter') modalConfirm.click(); });

// ── FIX #3: Aliases — lazy load only on tab click, cache result ─────────────
let aliasesLoaded=false;
async function loadAliases(){
  if(aliasesLoaded) return;
  try{
    const r=await fetch('/api/aliases');
    // Guard: if server returned non-OK (e.g. 401 before login), skip silently
    if(!r.ok) return;
    const d=await r.json();
    const list=$('alias-list'); list.innerHTML='';
    (d.aliases||[]).forEach(a=>{
      const card=document.createElement('div'); card.className='alias-card';
      card.innerHTML=`<span class="alias-name">!${esc(a.name)}</span><span class="alias-sep">\u2192</span><span class="alias-cmd" title="${esc(a.command)}">${esc(a.command)}</span><button class="alias-run">${t('console.run')}</button>`;
      card.querySelector('.alias-run').addEventListener('click',e=>{ e.stopPropagation(); sendCommand('!'+a.name); switchToPanel('console'); });
      card.addEventListener('click',()=>{ cmdInput.value='!'+a.name; switchToPanel('console'); cmdInput.focus(); });
      list.appendChild(card);
    });
    if(!d.aliases||!d.aliases.length) list.innerHTML=`<p style="color:var(--text-muted);padding:20px 0">${t('aliases.empty')}</p>`;
    aliasesLoaded=true;
    if(activePanelName==='aliases') requestAnimationFrame(()=>animatePanelContent('aliases'));
  }catch(_){}
}

// ── Tab completion ──────────────────────────────────────────────────────────
async function loadSessions(force=false){
  if(sessionsLoading) return;
  if(sessionsLoaded&&!force&&activePanelName!=='sessions') return;
  const empty=$('sessions-empty');
  if(empty&&!sessionsLoaded) empty.textContent=t('sessions.loading');
  sessionsLoading=true;
  try{
    const r=await fetch('/api/sessions');
    if(!r.ok) throw new Error(String(r.status));
    const data=await r.json();
    renderSessions(data.sessions||[]);
    sessionsLoaded=true;
  }catch(_){
    renderSessions([],true);
  }finally{
    sessionsLoading=false;
  }
}

function renderSessions(sessions,isError=false){
  const tbody=$('sessions-tbody'), table=$('sessions-table'), empty=$('sessions-empty');
  if(!tbody||!table||!empty) return;
  const safe=Array.isArray(sessions)?sessions:[];
  const sig=JSON.stringify(safe)+'|'+currentLang+'|'+isError;
  if(sig===lastSessionsSignature) return;
  lastSessionsSignature=sig;
  setText('sessions-count', isError ? '!' : safe.length);
  if(isError){
    tbody.innerHTML='';
    table.style.display='none';
    empty.style.display='';
    empty.classList.add('session-error');
    empty.textContent=t('sessions.error');
    return;
  }
  empty.classList.remove('session-error');
  if(!safe.length){
    tbody.innerHTML='';
    table.style.display='none';
    empty.style.display='';
    empty.textContent=t('sessions.empty');
    return;
  }
  empty.style.display='none';
  table.style.display='';
  tbody.innerHTML=safe.map(s=>{
    const expires=Number(s.expiresInSeconds||0);
    return `<tr><td>${esc(s.username||'')}</td><td><span class="session-ip">${esc(s.remoteIp||'--')}</span></td><td>${fmtDateTime(s.lastSeenAt)}</td><td>${fmtDuration(expires)}</td></tr>`;
  }).join('');
}

setInterval(()=>{ if(activePanelName==='sessions') loadSessions(true); },10000);

let acTimer=null;
cmdInput.addEventListener('input',()=>{
  clearTimeout(acTimer); const v=cmdInput.value;
  if(!v||v.startsWith('!')){ hideAc(); return; }
  acTimer=setTimeout(()=>requestComplete(v),200);
});
cmdInput.addEventListener('keydown',e=>{
  if(e.key==='Tab'){ e.preventDefault(); if(acItems.length){ acIdx=(acIdx+1)%acItems.length; renderAc(); } else requestComplete(cmdInput.value); return; }
  if(e.key==='ArrowDown'&&acItems.length){ e.preventDefault(); acIdx=(acIdx+1)%acItems.length; renderAc(); return; }
  if(e.key==='ArrowUp'){ if(acItems.length){ e.preventDefault(); acIdx=(acIdx-1+acItems.length)%acItems.length; renderAc(); return; } if(histIdx===-1) draft=cmdInput.value; histIdx=Math.min(histIdx+1,cmdHistory.length-1); cmdInput.value=cmdHistory[cmdHistory.length-1-histIdx]||''; return; }
  if(e.key==='Escape'){ hideAc(); return; }
  if(e.key==='Enter'){ if(acItems.length&&acIdx>=0){ applyAc(acItems[acIdx]); return; } sendCommand(cmdInput.value); return; }
  if(e.key==='ArrowDown'&&!acItems.length){ histIdx=Math.max(histIdx-1,-1); cmdInput.value=histIdx===-1?draft:(cmdHistory[cmdHistory.length-1-histIdx]||''); }
});
function requestComplete(partial){ if(!ws||ws.readyState!==WebSocket.OPEN) return; acReqId++; ws.send(JSON.stringify({type:'complete',partial,reqId:acReqId})); }
function handleCompletions(msg){ if(msg.reqId!==acReqId) return; acItems=msg.suggestions||[]; acIdx=-1; renderAc(); }
function renderAc(){
  if(!acItems.length){ hideAc(); return; }
  acDropdown.innerHTML=`<div class="ac-hint">${t('console.acHint')}</div>`+acItems.map((s,i)=>`<div class="ac-item${i===acIdx?' sel':''}" data-i="${i}">${esc(s)}</div>`).join('');
  acDropdown.querySelectorAll('.ac-item').forEach(el=>el.addEventListener('mousedown',e=>{ e.preventDefault(); applyAc(acItems[+el.dataset.i]); }));
  acDropdown.style.display='block';
}
function applyAc(val){ const pts=cmdInput.value.split(' '); pts[pts.length-1]=val; cmdInput.value=pts.join(' ')+' '; hideAc(); cmdInput.focus(); }
function hideAc(){ acItems=[]; acIdx=-1; acDropdown.style.display='none'; }
document.addEventListener('click',e=>{ if(!cmdInput.contains(e.target)&&!acDropdown.contains(e.target)) hideAc(); });

// ── Send command ────────────────────────────────────────────────────────────
function sendCommand(cmd){
  cmd=cmd.trim();
  if(!cmd||!ws||ws.readyState!==WebSocket.OPEN) return;
  ws.send(JSON.stringify({type:'command',command:cmd}));
  pushActivity('cmd','&#9000;',`<strong>CMD:</strong> ${esc(cmd)}`,true);
  hideAc();
  if(cmdHistory[cmdHistory.length-1]!==cmd){
    cmdHistory.push(cmd);
    if(cmdHistory.length>100) cmdHistory.shift();
    try{ localStorage.setItem('bwc_hist',JSON.stringify(cmdHistory)); }catch(_){}
  }
  histIdx=-1; draft=''; cmdInput.value='';
}
btnSend.addEventListener('click',()=>sendCommand(cmdInput.value));

// ── Notifications ───────────────────────────────────────────────────────────
$('notif-btn').addEventListener('click',()=>{
  if(!notifEnabled){ Notification.requestPermission().then(p=>{ notifEnabled=p==='granted'; showToast(notifEnabled?t('toast.notificationsEnabled'):t('toast.notificationsDenied'),notifEnabled?'success':'error'); }); }
  else{ notifEnabled=false; showToast(t('toast.notificationsDisabled')); }
  notifCount=0; nbadge.classList.remove('show');
});

function playSound(){
  try{
    const ctx=new AudioContext(), o=ctx.createOscillator(), g=ctx.createGain();
    o.connect(g); g.connect(ctx.destination);
    o.frequency.value=440; o.type='sine';
    g.gain.setValueAtTime(.15,ctx.currentTime);
    g.gain.exponentialRampToValueAtTime(.001,ctx.currentTime+.3);
    o.start(); o.stop(ctx.currentTime+.3);
  }catch(_){}
}

// ── Export / Clear / Logout ─────────────────────────────────────────────────
$('btn-export').addEventListener('click',()=>window.open('/api/logs/export','_blank'));
$('btn-clear').addEventListener('click',()=>{
  output.innerHTML=''; lineCount=0; svLines.textContent='0';
  levelCounts.INFO=0; levelCounts.WARN=0; levelCounts.SEVERE=0; levelCounts.DEBUG=0;
  updateLevelBars(); showToast(t('toast.cleared'));
});
$('btn-logout').addEventListener('click',doLogout);
async function doLogout(){
  manualDisconnect=true; clearTimeout(reconnectTimer);
  try{ await fetch('/api/logout',{method:'POST'}); }catch(_){}
  if(ws) ws.close();
  app.classList.remove('show'); loginScreen.classList.remove('hide');
  lp.value=''; loginErr.textContent='';
  sessionUser=''; cmdInput.disabled=true; btnSend.disabled=true;
  output.innerHTML=''; lineCount=0; aliasesLoaded=false;
  lastPlayersStructureSignature='__init__'; lastPlayersVisualSignature='__init__';
  lastPlayerEventsSignature='__init__'; lastPlayerCommandsSignature='__init__';
  const eventHistory=$('player-event-history'); if(eventHistory) eventHistory.innerHTML='';
  const commandHistory=$('player-command-history'); if(commandHistory) commandHistory.innerHTML='';
  activePanelName=''; switchToPanel('console');
}

// ── Login ───────────────────────────────────────────────────────────────────
async function doLogin(){
  const username=lu.value.trim(), password=lp.value;
  loginErr.textContent='';
  if(!username||!password){ loginErr.textContent=t('login.missing'); return; }
  btnLogin.disabled=true; btnLogin.textContent=t('login.signing');
  try{
    const cr=await fetch('/api/csrf'), {token:csrf}=await cr.json();
    const res=await fetch('/api/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({username,password,csrf})});
    const data=await res.json();
    if(res.ok&&data.success){
      sessionUser=data.username; sessionStart=Date.now();
      ulabel.textContent=sessionUser;
      loginScreen.classList.add('hide'); app.classList.add('show');
      switchToPanel('console'); initCharts(); animatePanelContent('console');
      manualDisconnect=false; connectWs(true);
    } else {
      loginErr.textContent=data.error||t('login.invalid');
      lp.value=''; lp.focus();
    }
  }catch(_){ loginErr.textContent=t('login.unreachable'); }
  finally{ btnLogin.disabled=false; btnLogin.textContent=t('login.button'); }
}

// Bind login form — handles both button click and Enter key via form submit
$('login-form').addEventListener('submit',e=>{ e.preventDefault(); doLogin(); });
lu.addEventListener('keydown',e=>{ if(e.key==='Enter') lp.focus(); });

// ── Utils ───────────────────────────────────────────────────────────────────
function esc(s){ return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'); }
function escRe(s){ return s.replace(/[.*+?^${}()|[\]\\]/g,'\\$&'); }
function setText(id,value){ const el=$(id); if(el) el.textContent=value; }
function setWidth(id,value){ const el=$(id); if(el) el.style.width=Math.max(0,Math.min(100,value||0))+'%'; }
function fmtPct(value){ return Number.isFinite(Number(value)) ? Number(value).toFixed(1)+'%' : '—'; }
function fmtBytes(bytes){
  const n=Number(bytes);
  if(!Number.isFinite(n)||n<=0) return '—';
  const units=['B','KB','MB','GB','TB'];
  let v=n, i=0;
  while(v>=1024&&i<units.length-1){ v/=1024; i++; }
  return (v>=10||i===0?v.toFixed(0):v.toFixed(1))+units[i];
}
function fmtDuration(seconds){
  const s=Number(seconds);
  if(!Number.isFinite(s)||s<0) return '—';
  const d=Math.floor(s/86400), h=Math.floor(s%86400/3600), m=Math.floor(s%3600/60);
  if(d>0) return `${d}d ${h}h`;
  if(h>0) return `${h}h ${m}m`;
  return `${m}m`;
}
function fmtShortDuration(ms){
  const s=Math.max(0,Math.floor(Number(ms||0)/1000));
  const h=Math.floor(s/3600), m=Math.floor(s%3600/60), sec=s%60;
  if(h>0) return `${h}h ${m}m`;
  if(m>0) return `${m}m ${sec}s`;
  return `${sec}s`;
}
function fmtTime(timestamp){
  if(timestamp===null||timestamp===undefined||timestamp==='') return '--:--';
  const n=Number(timestamp);
  if(!Number.isFinite(n)||n<=0) return '--:--';
  const d=new Date(n);
  if(Number.isNaN(d.getTime())) return '--:--';
  return d.toLocaleTimeString('en-GB',{hour:'2-digit',minute:'2-digit'});
}
function fmtDateTime(timestamp){
  if(timestamp===null||timestamp===undefined||timestamp==='') return '--';
  const n=Number(timestamp);
  if(!Number.isFinite(n)||n<=0) return '--';
  const d=new Date(n);
  if(Number.isNaN(d.getTime())) return '--';
  return d.toLocaleString('en-GB',{day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'});
}
function setKpiState(valueId,value,warnAt,critAt){
  const el=$(valueId); if(!el||!el.parentElement) return;
  el.parentElement.className='kpi '+(value>=critAt?'red':value>=warnAt?'warn':'green');
}

// ── Init ────────────────────────────────────────────────────────────────────
(async()=>{
  try{
    const r=await fetch('/api/status'), d=await r.json();
    if(d.authenticated){
      sessionUser=d.username; sessionStart=Date.now();
      ulabel.textContent=sessionUser;
      loginScreen.classList.add('hide'); app.classList.add('show');
      switchToPanel('console'); initCharts(); animatePanelContent('console');
      manualDisconnect=false; connectWs(true); return;
    }
  }catch(_){}
  lu.focus();
})();

window.addEventListener('online',()=>{ if(!manualDisconnect) connectWs(true); });
document.addEventListener('visibilitychange',()=>{ if(!document.hidden&&!manualDisconnect&&(!ws||ws.readyState!==WebSocket.OPEN)) connectWs(true); });
setInterval(()=>{ if(!manualDisconnect&&(!ws||ws.readyState===WebSocket.CLOSED)) connectWs(); },5000);
})();
}


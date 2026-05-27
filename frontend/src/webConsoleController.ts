// @ts-nocheck
import { getCsrf, getStatus, login, logout } from './webconsole/api';
import { Chart } from './webconsole/chart';
import { getHealthReasons, getHealthStatus, renderActivityHtml as renderDashboardActivityHtml, renderTopPlayersHtml, renderWorldRows, updateLevelBarElements } from './webconsole/dashboard';
import { setKpiState, setText, setWidth } from './webconsole/dom';
import { esc, escRe, fmtBytes, fmtDateTime, fmtDuration, fmtPct, fmtShortDuration } from './webconsole/formatters';
import { I18N, LANG_KEY, LANGS } from './webconsole/i18n';
import { createPlayerActivityController } from './webconsole/playerActivityController';
let mounted = false;

export function mountWebConsole() {
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
let chartResizeTimer=null;
const chartSignatures=new WeakMap();
let activePanelName='console';
let lastPlayersStructureSignature='__init__';
let lastPlayersVisualSignature='__init__';
let lastWorldFilterSignature='__init__';
let lastAnimatedPlayersSignature='';
let currentPlayerList=[];
let lastStatsData=null;
let lastSummarySignature='__init__';
let lastTopPlayersSignature='__init__';
const panelOrder=['console','dash','players','aliases','sessions','audit','config'];
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

let savedLang='en';
try{ const rawLang=localStorage.getItem(LANG_KEY); if(LANGS.includes(rawLang)) savedLang=rawLang; }catch(_){}
let currentLang=savedLang;
function t(key,vars={}){
  const text=(I18N[currentLang]&&I18N[currentLang][key])||I18N.en[key]||key;
  return text.replace(/\{(\w+)\}/g,(_,k)=>vars[k] ?? '');
}
const playerActivity=createPlayerActivityController({$,t,getCurrentLang:()=>currentLang});
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
  playerActivity.invalidateLanguage();
  if(lastStatsData){
    emitPlayersChange(currentPlayerList,lastStatsData.playerActivitySummary||{});
    playerActivity.renderDays(lastStatsData.playerActivityDays||[]);
    renderTopActivePlayers(lastStatsData.playerActivitySummary||{});
  }
  emitLanguageChange();
}
function setLanguage(lang){
  if(!LANGS.includes(lang)) lang='en';
  currentLang=lang;
  try{ localStorage.setItem(LANG_KEY,lang); }catch(_){}
  applyTranslations();
  emitLanguageChange();
}
[loginLang,appLang].filter(Boolean).forEach(sel=>sel.addEventListener('change',()=>setLanguage(sel.value)));
applyTranslations();

function emitPanelChange(panel){
  window.dispatchEvent(new CustomEvent('webconsole:panel',{detail:{panel}}));
}

function emitLanguageChange(){
  window.dispatchEvent(new CustomEvent('webconsole:language',{detail:{lang:currentLang}}));
}

function emitPlayersChange(players,summary){
  window.dispatchEvent(new CustomEvent('webconsole:players',{detail:{players,summary}}));
}

// ── Toast ──────────────────────────────────────────────────────────────────
let toastT;
function showToast(msg,type=''){
  clearTimeout(toastT);
  toast.textContent=msg; toast.className='show '+(type||'');
  toastT=setTimeout(()=>toast.className='',3000);
}
window.showToast=showToast;

window.copyPlayerUuid=function(uuid){
  const value=String(uuid||'');
  if(!value) return;
  if(navigator.clipboard&&navigator.clipboard.writeText) navigator.clipboard.writeText(value).catch(()=>{});
  else {
    const input=document.createElement('textarea');
    input.value=value;
    input.style.position='fixed';
    input.style.opacity='0';
    document.body.appendChild(input);
    input.select();
    document.execCommand('copy');
    input.remove();
  }
  showToast(t('players.uuidCopied'),'success');
};

// ── Tab switching ──────────────────────────────────────────────────────────
document.querySelectorAll('.tab').forEach(tab=>{
  tab.addEventListener('click',()=>switchToPanel(tab.dataset.panel));
});

function switchToPanel(name){
  if(!name) return;
  if(activePanelName===name){
    if(name==='dash') ensureChartsReady();
    emitPanelChange(name);
    return;
  }
  const prevIdx=panelOrder.indexOf(activePanelName);
  const nextIdx=panelOrder.indexOf(name);
  const enterClass=(prevIdx!==-1&&nextIdx<prevIdx)?'enter-from-left':'enter-from-right';
  document.querySelectorAll('.tab').forEach(t=>t.classList.toggle('active',t.dataset.panel===name));
  document.querySelectorAll('.panel').forEach(p=>p.classList.remove('active','enter-from-right','enter-from-left'));
  const nextPanel=$('panel-'+name);
  nextPanel.classList.add('active',enterClass);
  nextPanel.addEventListener('animationend',()=>nextPanel.classList.remove('enter-from-right','enter-from-left'),{once:true});
  activePanelName=name;
  emitPanelChange(name);
  requestAnimationFrame(()=>{
    if(name==='dash') ensureChartsReady();
    animatePanelContent(name);
  });
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
  if(name==='audit'){ staggerAnimate([...document.querySelectorAll('#audit-tbody tr')],18); return; }
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
  try{ const d=await getStatus({cache:'no-store'}); return !!d.authenticated; }
  catch(_){ return true; }
}

function handleControl(event){
  if(event==='SESSION_EXPIRED'){ showToast(t('toast.sessionExpired'),'error'); setTimeout(doLogout,1500); }
  else if(event==='RATE_LIMITED') showToast(t('toast.rateLimited'),'warn');
  else if(event==='BLOCKED')      showToast(t('toast.blocked'),'warn');
}

// ── Stats ──────────────────────────────────────────────────────────────────
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
  const reasons=getHealthReasons(snapshot,t);
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

  syncChartsFromStats();
  if(data.system)         handleSystemStats(data.system);
  if(data.playerList!==undefined) updatePlayersState(data.playerList,data.playerActivitySummary||{});
  playerActivity.renderDays(data.playerActivityDays||[]);
  if(data.playerList===undefined) emitPlayersChange(currentPlayerList,data.playerActivitySummary||{});
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
  tbody.innerHTML=renderWorldRows(worlds);
}

function renderTopActivePlayers(summary){
  const box=$('top-active-players'); if(!box) return;
  const list=Array.isArray(summary.topPlayers)?summary.topPlayers:[];
  const sig=JSON.stringify(list)+'|'+currentLang;
  if(sig===lastTopPlayersSignature) return;
  lastTopPlayersSignature=sig;
  box.innerHTML=renderTopPlayersHtml(summary,t);
}

function updateChart(chart,data){
  if(!chart||!Array.isArray(data)) return;
  const points=data.map(v=>{
    const n=Number(v);
    return Number.isFinite(n)?n:null;
  });
  const signature=points.join('|');
  if(chartSignatures.get(chart)===signature) return;
  chartSignatures.set(chart,signature);
  chart.data.labels=points.map((_,i)=>i);
  chart.data.datasets[0].data=points;
  chart.update('none');
}

// ── Level bars ─────────────────────────────────────────────────────────────
function updateLevelBars(){
  updateLevelBarElements(levelCounts,t);
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
  feed.innerHTML=renderDashboardActivityHtml(activityLog,t);
}

// ── Charts ─────────────────────────────────────────────────────────────────
function chartSizeMode(){
  const width=window.innerWidth||document.documentElement.clientWidth||1024;
  return {tickFont:width<520?9:10,maxTicks:width<520?3:4,tooltipFont:width<520?10:11,tooltipPadding:width<520?6:8};
}

function makeChart(id,label,color,max){
  const canvas=document.getElementById(id);
  if(!canvas) return null;
  const ctx=canvas.getContext('2d');
  if(!ctx) return null;
  const size=chartSizeMode();
  return new Chart(ctx,{
    type:'line',
    data:{labels:[],datasets:[{label,data:[],borderColor:color,backgroundColor:color+'18',borderWidth:1.8,pointRadius:0,fill:true,tension:.35}]},
    options:{responsive:true,maintainAspectRatio:false,animation:false,
      interaction:{mode:'nearest',axis:'x',intersect:false},
      plugins:{legend:{display:false},tooltip:{mode:'index',intersect:false,displayColors:true,backgroundColor:'#1e1e1e',borderColor:'rgba(255,255,255,.10)',borderWidth:1,titleColor:'#eee',bodyColor:'#eee',caretSize:5,padding:size.tooltipPadding,titleFont:{size:size.tooltipFont,weight:'700'},bodyFont:{size:size.tooltipFont},callbacks:{label:c=>label+': '+c.parsed.y}}},
      scales:{x:{display:false},y:{min:0,max:max||undefined,bounds:'ticks',grid:{color:'rgba(255,255,255,.042)'},ticks:{color:'#aaa',font:{size:size.tickFont},maxTicksLimit:size.maxTicks,precision:0}}}}
  });
}

function resizeCharts(){
  if(!chartsInitialized) return;
  clearTimeout(chartResizeTimer);
  chartResizeTimer=setTimeout(()=>scheduleChartResize(),80);
}

function scheduleChartResize(){
  requestAnimationFrame(()=>{
    [tpsChart,ramChart,playersChart,cpuChart].filter(Boolean).forEach(chart=>{
      const size=chartSizeMode();
      chart.options.plugins.tooltip.titleFont.size=size.tooltipFont;
      chart.options.plugins.tooltip.bodyFont.size=size.tooltipFont;
      chart.options.plugins.tooltip.padding=size.tooltipPadding;
      chart.options.scales.y.ticks.font.size=size.tickFont;
      chart.options.scales.y.ticks.maxTicksLimit=size.maxTicks;
      chart.resize();
      chart.update('none');
    });
  });
}

function syncChartsFromStats(){
  if(!chartsInitialized||!lastStatsData) return;
  updateChart(tpsChart,     lastStatsData.tpsHistory);
  updateChart(ramChart,     lastStatsData.ramHistory);
  updateChart(playersChart, lastStatsData.playersHistory);
  updateChart(cpuChart,     lastStatsData.cpuHistory);
  resizeCharts();
}

function initCharts(){
  if(chartsInitialized){ resizeCharts(); return; }
  tpsChart     = makeChart('chart-tps',    'TPS',     '#f23987', 20);
  ramChart     = makeChart('chart-ram',    'RAM MB',  '#4fc3f7');
  playersChart = makeChart('chart-players','Players', '#d05ce3');
  cpuChart     = makeChart('chart-cpu',    'CPU %',   '#00e676', 100);
  chartsInitialized=true;
  syncChartsFromStats();
}

function ensureChartsReady(){
  initCharts();
  resizeCharts();
}

// ── Player list ─────────────────────────────────────────────────────────────
function updatePlayersState(list,summary={}){
  currentPlayerList=Array.isArray(list)?list:[];
  const newNames=new Set(currentPlayerList.map(p=>p.name));
  newNames.forEach(n=>{ if(!prevPlayerNames.has(n)) pushActivity('join','&#128994;',`<strong class="notranslate" translate="no">${esc(n)}</strong> ${t('players.joined').toLowerCase()}`,true); });
  prevPlayerNames.forEach(n=>{ if(!newNames.has(n)) pushActivity('leave','&#9899;',`<strong class="notranslate" translate="no">${esc(n)}</strong> ${t('players.left').toLowerCase()}`,true); });
  prevPlayerNames=newNames;
  emitPlayersChange(currentPlayerList,summary);
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
  pushActivity('cmd','&#9889;',`<strong>${esc(act)}</strong> <span class="notranslate" translate="no">${esc(modalPlayer.value)}</span>${value?': '+esc(value):''}`,true);
});
modalReason.addEventListener('keydown',e=>{ if(e.key==='Enter') modalConfirm.click(); });

// ── Tab completion ──────────────────────────────────────────────────────────
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
window.addEventListener('webconsole:alias-command',event=>{
  const detail=event.detail||{};
  const command=String(detail.command||'');
  if(!command) return;
  if(detail.run){
    sendCommand(command);
  } else {
    cmdInput.value=command;
    hideAc();
  }
  switchToPanel('console');
  cmdInput.focus();
});

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
  try{ await logout(); }catch(_){}
  if(ws) ws.close();
  app.classList.remove('show'); loginScreen.classList.remove('hide');
  lp.value=''; loginErr.textContent='';
  sessionUser=''; cmdInput.disabled=true; btnSend.disabled=true;
  output.innerHTML=''; lineCount=0;
  lastPlayersStructureSignature='__init__'; lastPlayersVisualSignature='__init__';
  playerActivity.reset();
  activePanelName=''; switchToPanel('console');
}

// ── Login ───────────────────────────────────────────────────────────────────
async function doLogin(){
  const username=lu.value.trim(), password=lp.value;
  loginErr.textContent='';
  if(!username||!password){ loginErr.textContent=t('login.missing'); return; }
  btnLogin.disabled=true; btnLogin.textContent=t('login.signing');
  try{
    const {token:csrf}=await getCsrf();
    const {ok,data}=await login(username,password,csrf);
    if(ok&&data.success){
      sessionUser=data.username; sessionStart=Date.now();
      ulabel.textContent=sessionUser;
      loginScreen.classList.add('hide'); app.classList.add('show');
      switchToPanel('console'); animatePanelContent('console');
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

// ── Init ────────────────────────────────────────────────────────────────────
(async()=>{
  try{
    const d=await getStatus();
    if(d.authenticated){
      sessionUser=d.username; sessionStart=Date.now();
      ulabel.textContent=sessionUser;
      loginScreen.classList.add('hide'); app.classList.add('show');
      switchToPanel('console'); animatePanelContent('console');
      manualDisconnect=false; connectWs(true); return;
    }
  }catch(_){}
  lu.focus();
})();

window.addEventListener('online',()=>{ if(!manualDisconnect) connectWs(true); });
document.addEventListener('visibilitychange',()=>{ if(!document.hidden&&!manualDisconnect&&(!ws||ws.readyState!==WebSocket.OPEN)) connectWs(true); });
window.addEventListener('resize',resizeCharts,{passive:true});
setInterval(()=>{ if(!manualDisconnect&&(!ws||ws.readyState===WebSocket.CLOSED)) connectWs(); },5000);
})();
}

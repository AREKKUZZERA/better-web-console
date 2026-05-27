// @ts-nocheck
export function esc(s){ return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'); }

export function escRe(s){ return s.replace(/[.*+?^${}()|[\]\\]/g,'\\$&'); }

export function fmtPct(value){ return Number.isFinite(Number(value)) ? Number(value).toFixed(1)+'%' : '—'; }

export function fmtBytes(bytes){
  const n=Number(bytes);
  if(!Number.isFinite(n)||n<=0) return '—';
  const units=['B','KB','MB','GB','TB'];
  let v=n, i=0;
  while(v>=1024&&i<units.length-1){ v/=1024; i++; }
  return (v>=10||i===0?v.toFixed(0):v.toFixed(1))+units[i];
}

export function fmtDuration(seconds){
  const s=Number(seconds);
  if(!Number.isFinite(s)||s<0) return '—';
  const d=Math.floor(s/86400), h=Math.floor(s%86400/3600), m=Math.floor(s%3600/60);
  if(d>0) return `${d}d ${h}h`;
  if(h>0) return `${h}h ${m}m`;
  return `${m}m`;
}

export function fmtShortDuration(ms){
  const s=Math.max(0,Math.floor(Number(ms||0)/1000));
  const h=Math.floor(s/3600), m=Math.floor(s%3600/60), sec=s%60;
  if(h>0) return `${h}h ${m}m`;
  if(m>0) return `${m}m ${sec}s`;
  return `${sec}s`;
}

export function fmtTime(timestamp){
  if(timestamp===null||timestamp===undefined||timestamp==='') return '--:--';
  const n=Number(timestamp);
  if(!Number.isFinite(n)||n<=0) return '--:--';
  const d=new Date(n);
  if(Number.isNaN(d.getTime())) return '--:--';
  return d.toLocaleTimeString('en-GB',{hour:'2-digit',minute:'2-digit'});
}

export function fmtDateTime(timestamp){
  if(timestamp===null||timestamp===undefined||timestamp==='') return '--';
  const n=Number(timestamp);
  if(!Number.isFinite(n)||n<=0) return '--';
  const d=new Date(n);
  if(Number.isNaN(d.getTime())) return '--';
  return d.toLocaleString('en-GB',{day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'});
}

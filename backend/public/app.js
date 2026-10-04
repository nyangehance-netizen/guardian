'use strict';

/* Guardian parent dashboard — talks to the backend REST API. */

const API = ''; // same origin
let token = localStorage.getItem('guardian_token') || null;
let parent = JSON.parse(localStorage.getItem('guardian_parent') || 'null');
let devices = [];
let selectedId = null;
let workingPolicy = null; // editable copy of the selected device's policy
let pollTimer = null;

/* ------------------------------ fetch helper ----------------------------- */
async function api(method, path, body) {
  const res = await fetch(API + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: 'Bearer ' + token } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || res.statusText);
  return data;
}

const $ = (id) => document.getElementById(id);
const fmtTime = (ts) => (ts ? new Date(ts).toLocaleString() : '—');
const minsAgo = (ts) => (ts ? Math.round((Date.now() - ts) / 60000) : null);

/* --------------------------------- auth ---------------------------------- */
function showAuth() { $('auth').classList.remove('hidden'); $('app').classList.add('hidden'); }
function showApp() {
  $('auth').classList.add('hidden'); $('app').classList.remove('hidden');
  $('whoami').textContent = parent ? (parent.name || parent.email) : '';
  loadDevices();
  startPolling();
}

async function doAuth(kind) {
  const email = $('email').value.trim();
  const password = $('password').value;
  const name = $('name').value.trim();
  $('authErr').classList.add('hidden');
  try {
    const path = kind === 'register' ? '/api/auth/register' : '/api/auth/login';
    const data = await api('POST', path, { email, password, name });
    token = data.token; parent = data.parent;
    localStorage.setItem('guardian_token', token);
    localStorage.setItem('guardian_parent', JSON.stringify(parent));
    showApp();
  } catch (e) {
    $('authErr').textContent = e.message;
    $('authErr').classList.remove('hidden');
  }
}

$('loginBtn').onclick = () => doAuth('login');
$('registerBtn').onclick = () => {
  document.querySelectorAll('.signup-only').forEach((el) => el.classList.toggle('hidden'));
  if ($('name').dataset.armed) doAuth('register');
  else { $('name').dataset.armed = '1'; $('registerBtn').textContent = 'Sign up'; }
};
$('logoutBtn').onclick = () => {
  localStorage.clear(); token = null; parent = null; stopPolling(); showAuth();
};

/* ------------------------------ device list ------------------------------ */
async function loadDevices() {
  try {
    devices = await api('GET', '/api/devices');
    renderDeviceList();
    if (selectedId) renderDetail();
  } catch (e) {
    if (String(e.message).includes('token')) { localStorage.clear(); showAuth(); }
  }
}

function renderDeviceList() {
  const ul = $('deviceList');
  ul.innerHTML = '';
  if (!devices.length) {
    ul.innerHTML = '<li class="muted" style="font-size:13px">No devices yet.</li>';
    return;
  }
  for (const d of devices) {
    const li = document.createElement('li');
    li.className = 'device-item' + (d.id === selectedId ? ' active' : '');
    const online = d.lastSeen && minsAgo(d.lastSeen) < 10;
    const status = !d.paired
      ? '<span class="badge offline">pairing…</span>'
      : online ? '<span class="badge online">online</span>' : '<span class="badge offline">offline</span>';
    const alerts = d.unreadAlerts ? ` <span class="badge alert">${d.unreadAlerts}</span>` : '';
    li.innerHTML = `<div class="name">${esc(d.childName)} ${status}${alerts}</div>
      <div class="sub">${d.platform} · ${d.lastSeen ? minsAgo(d.lastSeen) + 'm ago' : 'never seen'}</div>`;
    li.onclick = () => { selectedId = d.id; workingPolicy = null; renderDeviceList(); loadDetail(); };
    ul.appendChild(li);
  }
}

/* ------------------------------ device detail ---------------------------- */
async function loadDetail() {
  const d = devices.find((x) => x.id === selectedId);
  if (!d) return;
  const [{ policy }, events] = await Promise.all([
    api('GET', `/api/devices/${d.id}/policy`),
    api('GET', `/api/devices/${d.id}/events`),
  ]);
  workingPolicy = policy;
  d._events = events;
  renderDetail();
}

function renderDetail() {
  const d = devices.find((x) => x.id === selectedId);
  if (!d || !workingPolicy) return;
  const p = workingPolicy;
  const loc = d.location
    ? `<a href="https://maps.google.com/?q=${d.location.lat},${d.location.lng}" target="_blank">${d.location.lat.toFixed(4)}, ${d.location.lng.toFixed(4)}</a>`
    : '—';

  $('detail').innerHTML = `
    <h2>${esc(d.childName)}</h2>
    <div class="muted">${d.platform} · last seen ${fmtTime(d.lastSeen)}</div>
    ${!d.paired ? `<div class="err" style="margin-top:12px">Waiting for the phone to enter pairing code <b>${d.pairCode}</b></div>` : ''}

    <div class="grid">
      <div class="panel">
        <h3>Status</h3>
        <div class="kv"><span>Battery</span><b>${d.battery != null ? d.battery + '%' : '—'}</b></div>
        <div class="kv"><span>Location</span><b>${loc}</b></div>
        <div class="kv"><span>Foreground app</span><b>${esc(d.foregroundApp || '—')}</b></div>
      </div>

      <div class="panel">
        <h3>Web filtering</h3>
        ${toggle('wf_enabled', 'Enabled', p.webFilter.enabled)}
        ${toggle('wf_safe', 'Force SafeSearch', p.webFilter.safeSearch)}
        <div style="margin-top:10px">${['adult','gambling','violence','social','malware'].map((c)=>categoryChip(c, p.webFilter.blockCategories.includes(c))).join('')}</div>
        <p class="muted" style="margin:12px 0 4px;font-size:13px">Custom blocked sites</p>
        ${tagList('blocklist', p.webFilter.blocklist)}
        <input id="addBlock" placeholder="add domain, press Enter" />
      </div>

      <div class="panel">
        <h3>App blocking &amp; limits</h3>
        <p class="muted" style="font-size:13px;margin:0 0 4px">Always blocked</p>
        ${tagList('blockedApps', p.appRules.blocked)}
        <input id="addApp" placeholder="package/bundle id, press Enter" />
        <p class="muted" style="font-size:13px;margin:12px 0 4px">Daily limits (minutes)</p>
        <div id="limits">${limitRows(p.appRules.limits)}</div>
        <input id="addLimit" placeholder="com.app=60, press Enter" />
      </div>

      <div class="panel">
        <h3>Schedules</h3>
        ${scheduleBlock('bedtime', 'Bedtime (block all)', p.schedules.bedtime)}
        <hr style="border:none;border-top:1px solid var(--line);margin:12px 0" />
        ${scheduleBlock('school', 'School hours', p.schedules.school)}
      </div>

      <div class="panel" style="grid-column:1/-1">
        <h3>Alerts &amp; activity ${d.unreadAlerts ? `<button class="btn ghost xs" id="ackBtn">Mark all read</button>` : ''}</h3>
        <div>${(d._events||[]).length ? d._events.map(eventRow).join('') : '<span class="muted">No events yet.</span>'}</div>
      </div>
    </div>

    <div class="save-bar">
      <button class="btn primary" id="saveBtn">Save policy</button>
      <button class="btn danger" id="delBtn">Remove device</button>
      <span id="saveMsg" class="muted"></span>
    </div>
  `;

  wireDetailEvents(d);
}

/* --------------------------- detail sub-renderers ------------------------ */
function toggle(id, label, on) {
  return `<div class="toggle"><span>${label}</span>
    <label class="switch"><input type="checkbox" data-toggle="${id}" ${on?'checked':''}><span class="slider"></span></label></div>`;
}
function categoryChip(cat, on) {
  return `<span class="tag" style="${on?'border-color:var(--primary)':''}">
    <label style="display:flex;gap:6px;align-items:center;cursor:pointer;margin:0">
      <input type="checkbox" data-cat="${cat}" ${on?'checked':''} style="width:auto;margin:0">${cat}</label></span>`;
}
function tagList(kind, arr) {
  return `<div class="tag-input" data-taglist="${kind}">${arr.map((v)=>`<span class="tag">${esc(v)}<button data-del="${kind}" data-val="${esc(v)}">×</button></span>`).join('')}</div>`;
}
function limitRows(limits) {
  const keys = Object.keys(limits || {});
  if (!keys.length) return '<span class="muted" style="font-size:13px">None</span>';
  return keys.map((k)=>`<div class="kv"><span>${esc(k)}</span><span><b>${limits[k]}m</b> <button class="tag" data-dellimit="${esc(k)}" style="cursor:pointer">×</button></span></div>`).join('');
}
function scheduleBlock(id, label, s) {
  return `<div class="toggle"><b>${label}</b>
    <label class="switch"><input type="checkbox" data-sched="${id}" ${s.enabled?'checked':''}><span class="slider"></span></label></div>
    <div class="row"><input type="time" data-schedstart="${id}" value="${s.start}"><input type="time" data-schedend="${id}" value="${s.end}"></div>`;
}
function eventRow(e) {
  return `<div class="event ${e.kind}"><span class="dot"></span>
    <div><div>${esc(labelFor(e.kind))}${e.detail ? ': ' + esc(e.detail) : ''}</div>
    <div class="when">${fmtTime(e.ts)}</div></div></div>`;
}
function labelFor(k) {
  return { blocked_app:'Blocked app opened', blocked_site:'Blocked site',
    limit_reached:'Time limit reached', tamper:'⚠ Tamper attempt', info:'Info' }[k] || k;
}

/* ---------------------------- detail interactions ------------------------ */
function wireDetailEvents(d) {
  const p = workingPolicy;
  document.querySelectorAll('[data-toggle]').forEach((el)=>el.onchange=()=>{
    const id=el.dataset.toggle;
    if(id==='wf_enabled')p.webFilter.enabled=el.checked;
    if(id==='wf_safe')p.webFilter.safeSearch=el.checked;
  });
  document.querySelectorAll('[data-cat]').forEach((el)=>el.onchange=()=>{
    const c=el.dataset.cat, set=new Set(p.webFilter.blockCategories);
    el.checked?set.add(c):set.delete(c); p.webFilter.blockCategories=[...set];
  });
  document.querySelectorAll('[data-sched]').forEach((el)=>el.onchange=()=>{p.schedules[el.dataset.sched].enabled=el.checked;});
  document.querySelectorAll('[data-schedstart]').forEach((el)=>el.onchange=()=>{p.schedules[el.dataset.schedstart].start=el.value;});
  document.querySelectorAll('[data-schedend]').forEach((el)=>el.onchange=()=>{p.schedules[el.dataset.schedend].end=el.value;});
  document.querySelectorAll('[data-del]').forEach((el)=>el.onclick=()=>{
    const kind=el.dataset.del, val=el.dataset.val;
    if(kind==='blocklist')p.webFilter.blocklist=p.webFilter.blocklist.filter((x)=>x!==val);
    if(kind==='blockedApps')p.appRules.blocked=p.appRules.blocked.filter((x)=>x!==val);
    renderDetail();
  });
  document.querySelectorAll('[data-dellimit]').forEach((el)=>el.onclick=()=>{delete p.appRules.limits[el.dataset.dellimit];renderDetail();});

  onEnter($('addBlock'),(v)=>{if(v&&!p.webFilter.blocklist.includes(v)){p.webFilter.blocklist.push(v);renderDetail();}});
  onEnter($('addApp'),(v)=>{if(v&&!p.appRules.blocked.includes(v)){p.appRules.blocked.push(v);renderDetail();}});
  onEnter($('addLimit'),(v)=>{const m=v.split('=');if(m.length===2&&+m[1]>0){p.appRules.limits[m[0].trim()]=+m[1];renderDetail();}});

  if($('ackBtn'))$('ackBtn').onclick=async()=>{await api('POST',`/api/devices/${d.id}/events/ack`);loadDevices();loadDetail();};
  $('saveBtn').onclick=async()=>{
    try{const r=await api('PUT',`/api/devices/${d.id}/policy`,{policy:p});
      $('saveMsg').textContent=`Saved. The phone will apply it shortly (rev ${r.rev}).`;loadDevices();}
    catch(e){$('saveMsg').textContent=e.message;}
  };
  $('delBtn').onclick=async()=>{
    if(!confirm('Remove this device? Enforcement on the phone stops after it next syncs.'))return;
    await api('DELETE',`/api/devices/${d.id}`);selectedId=null;workingPolicy=null;
    $('detail').innerHTML='<div class="empty muted">Device removed.</div>';loadDevices();
  };
}
function onEnter(input,fn){if(!input)return;input.onkeydown=(e)=>{if(e.key==='Enter'){fn(input.value.trim());input.value='';}};}

/* ------------------------------ add device ------------------------------- */
$('addDeviceBtn').onclick=()=>{$('addModal').classList.remove('hidden');$('pairResult').classList.add('hidden');$('childName').value='';};
$('cancelAddBtn').onclick=()=>$('addModal').classList.add('hidden');
$('createDeviceBtn').onclick=async()=>{
  const childName=$('childName').value.trim(), platform=$('platform').value;
  if(!childName)return;
  const d=await api('POST','/api/devices',{childName,platform});
  $('pairCode').textContent=d.pairCode;
  $('pairResult').classList.remove('hidden');
  loadDevices();
};

/* ------------------------------- polling --------------------------------- */
function startPolling(){stopPolling();pollTimer=setInterval(loadDevices,15000);}
function stopPolling(){if(pollTimer)clearInterval(pollTimer);}

/* -------------------------------- utils ---------------------------------- */
function esc(s){return String(s==null?'':s).replace(/[&<>"]/g,(c)=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));}

/* ------------------------------- bootstrap ------------------------------- */
if(token&&parent)showApp();else showAuth();

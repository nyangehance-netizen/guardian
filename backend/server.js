'use strict';

/*
 * Guardian backend — zero-dependency Node.js HTTP server.
 * ------------------------------------------------------
 * Parent-facing REST API + static dashboard + child-device sync endpoints.
 *
 * Two kinds of caller:
 *   1. Parents  -> authenticate with email/password, get a token, manage devices & policy.
 *   2. Devices  -> authenticate with a per-device bearer secret, pull policy & push telemetry.
 *
 * Nothing here hides itself from the child. Enrollment is explicit (a 6-digit pairing
 * code), and enforcement on the phone relies on the OS supervision APIs documented in
 * the per-platform READMEs (Android Device Admin / iOS Family Controls).
 *
 * Run:  node server.js     (needs Node >= 18, no npm install)
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const S = require('./store');

const PORT = process.env.PORT || 4000;
const PUBLIC_DIR = path.join(__dirname, 'public');
const now = () => Date.now();

const DEFAULT_POLICY = {
  webFilter: { enabled: true, blockCategories: ['adult', 'gambling', 'malware'], blocklist: [], allowlist: [], safeSearch: true },
  appRules: { blocked: [], limits: {} },
  schedules: {
    bedtime: { enabled: false, start: '21:00', end: '07:00', days: [0, 1, 2, 3, 4, 5, 6] },
    school: { enabled: false, start: '08:00', end: '15:00', days: [1, 2, 3, 4, 5] },
  },
  location: { reportIntervalSec: 300 },
};

/* ------------------------------ data helpers ----------------------------- */
const findParentByEmail = (e) => S.data.parents.find((p) => p.email === e);
const findParentById = (idv) => S.data.parents.find((p) => p.id === idv);
const findDevice = (idv) => S.data.devices.find((d) => d.id === idv);
const findDeviceBySecret = (sec) => S.data.devices.find((d) => d.device_secret === sec);
const lastHeartbeat = (idv) => S.data.heartbeats.filter((h) => h.device_id === idv).sort((a, b) => b.ts - a.ts)[0];

function logEvent(deviceId, kind, detail) {
  S.data.events.push({ id: S.id(), device_id: deviceId, ts: now(), kind, detail: detail || null, acknowledged: 0 });
  S.save();
}

/* ------------------------------ tiny router ------------------------------ */
const routes = [];
const route = (method, pattern, handler) => routes.push({ method, pattern, handler });

function match(pattern, pathname) {
  const pk = pattern.split('/').filter(Boolean);
  const ak = pathname.split('/').filter(Boolean);
  if (pk.length !== ak.length) return null;
  const params = {};
  for (let i = 0; i < pk.length; i++) {
    if (pk[i].startsWith(':')) params[pk[i].slice(1)] = decodeURIComponent(ak[i]);
    else if (pk[i] !== ak[i]) return null;
  }
  return params;
}

const json = (res, code, obj) => {
  const body = JSON.stringify(obj);
  res.writeHead(code, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' });
  res.end(body);
};

function bearer(req) {
  const h = req.headers.authorization || '';
  return h.startsWith('Bearer ') ? h.slice(7) : null;
}
function parentFromReq(req) {
  const claims = S.verifyToken(bearer(req));
  return claims ? findParentById(claims.sub) : null;
}

/* ------------------------------ parent API ------------------------------- */
route('POST', '/api/auth/register', (req, res, _p, body) => {
  const { email, password, name } = body || {};
  if (!email || !password) return json(res, 400, { error: 'email and password required' });
  const em = String(email).toLowerCase().trim();
  if (findParentByEmail(em)) return json(res, 409, { error: 'email already registered' });
  const parent = { id: S.id(), email: em, pw_hash: S.hashPassword(password), name: name || null, created_at: now() };
  S.data.parents.push(parent); S.save();
  json(res, 200, { token: tokenFor(parent), parent: pub(parent) });
});

route('POST', '/api/auth/login', (req, res, _p, body) => {
  const { email, password } = body || {};
  const parent = findParentByEmail(String(email || '').toLowerCase().trim());
  if (!parent || !S.verifyPassword(password || '', parent.pw_hash)) return json(res, 401, { error: 'invalid credentials' });
  json(res, 200, { token: tokenFor(parent), parent: pub(parent) });
});

route('POST', '/api/devices', (req, res, _p, body) => {
  const parent = parentFromReq(req); if (!parent) return json(res, 401, { error: 'unauthorized' });
  const { childName, platform } = body || {};
  if (!childName || !['android', 'ios'].includes(platform)) return json(res, 400, { error: 'childName and platform (android|ios) required' });
  const device = {
    id: S.id(), parent_id: parent.id, child_name: childName, platform,
    device_secret: S.secret(), pair_code: S.sixDigit(), paired: 0,
    policy: structuredClone(DEFAULT_POLICY), policy_rev: 1, created_at: now(),
  };
  S.data.devices.push(device); S.save();
  json(res, 200, { id: device.id, childName, platform, pairCode: device.pair_code });
});

route('GET', '/api/devices', (req, res) => {
  const parent = parentFromReq(req); if (!parent) return json(res, 401, { error: 'unauthorized' });
  const out = S.data.devices.filter((d) => d.parent_id === parent.id)
    .sort((a, b) => b.created_at - a.created_at)
    .map((d) => {
      const last = lastHeartbeat(d.id);
      const unread = S.data.events.filter((e) => e.device_id === d.id && !e.acknowledged).length;
      return {
        id: d.id, childName: d.child_name, platform: d.platform, paired: !!d.paired,
        pairCode: d.paired ? null : d.pair_code, policyRev: d.policy_rev, unreadAlerts: unread,
        lastSeen: last ? last.ts : null, battery: last ? last.battery : null,
        location: last && last.lat != null ? { lat: last.lat, lng: last.lng, ts: last.ts } : null,
        foregroundApp: last ? last.foreground_app : null,
      };
    });
  json(res, 200, out);
});

route('GET', '/api/devices/:id/policy', (req, res, p) => {
  const parent = parentFromReq(req); if (!parent) return json(res, 401, { error: 'unauthorized' });
  const d = findDevice(p.id);
  if (!d || d.parent_id !== parent.id) return json(res, 404, { error: 'not found' });
  json(res, 200, { policy: d.policy, rev: d.policy_rev });
});

route('PUT', '/api/devices/:id/policy', (req, res, p, body) => {
  const parent = parentFromReq(req); if (!parent) return json(res, 401, { error: 'unauthorized' });
  const d = findDevice(p.id);
  if (!d || d.parent_id !== parent.id) return json(res, 404, { error: 'not found' });
  if (!body || typeof body.policy !== 'object') return json(res, 400, { error: 'policy object required' });
  d.policy = body.policy; d.policy_rev += 1; S.save();
  json(res, 200, { ok: true, rev: d.policy_rev });
});

route('GET', '/api/devices/:id/events', (req, res, p) => {
  const parent = parentFromReq(req); if (!parent) return json(res, 401, { error: 'unauthorized' });
  const d = findDevice(p.id);
  if (!d || d.parent_id !== parent.id) return json(res, 404, { error: 'not found' });
  const rows = S.data.events.filter((e) => e.device_id === p.id).sort((a, b) => b.ts - a.ts).slice(0, 200);
  json(res, 200, rows);
});

route('POST', '/api/devices/:id/events/ack', (req, res, p) => {
  const parent = parentFromReq(req); if (!parent) return json(res, 401, { error: 'unauthorized' });
  const d = findDevice(p.id);
  if (!d || d.parent_id !== parent.id) return json(res, 404, { error: 'not found' });
  S.data.events.forEach((e) => { if (e.device_id === p.id) e.acknowledged = 1; }); S.save();
  json(res, 200, { ok: true });
});

route('DELETE', '/api/devices/:id', (req, res, p) => {
  const parent = parentFromReq(req); if (!parent) return json(res, 401, { error: 'unauthorized' });
  const before = S.data.devices.length;
  S.data.devices = S.data.devices.filter((d) => !(d.id === p.id && d.parent_id === parent.id));
  S.save();
  json(res, 200, { ok: S.data.devices.length < before });
});

/* ------------------------------ device API ------------------------------- */
route('POST', '/api/enroll', (req, res, _p, body) => {
  const d = S.data.devices.find((x) => x.pair_code === String((body || {}).pairCode || '') && !x.paired);
  if (!d) return json(res, 404, { error: 'invalid or used pairing code' });
  d.paired = 1; d.pair_code = null; S.save();
  logEvent(d.id, 'info', 'Device enrolled');
  json(res, 200, { deviceId: d.id, deviceSecret: d.device_secret, childName: d.child_name, platform: d.platform, policy: d.policy, rev: d.policy_rev });
});

route('GET', '/api/sync/policy', (req, res) => {
  const d = findDeviceBySecret(bearer(req)); if (!d) return json(res, 401, { error: 'unknown device' });
  json(res, 200, { policy: d.policy, rev: d.policy_rev });
});

route('POST', '/api/sync/heartbeat', (req, res, _p, body) => {
  const d = findDeviceBySecret(bearer(req)); if (!d) return json(res, 401, { error: 'unknown device' });
  const { battery, lat, lng, foregroundApp, policyRev } = body || {};
  S.data.heartbeats.push({ id: S.id(), device_id: d.id, ts: now(), battery: battery ?? null, lat: lat ?? null, lng: lng ?? null, foreground_app: foregroundApp ?? null, policy_rev: policyRev ?? null });
  // keep heartbeat log bounded
  if (S.data.heartbeats.length > 5000) S.data.heartbeats = S.data.heartbeats.slice(-3000);
  S.save();
  json(res, 200, { ok: true, serverRev: d.policy_rev });
});

route('POST', '/api/sync/event', (req, res, _p, body) => {
  const d = findDeviceBySecret(bearer(req)); if (!d) return json(res, 401, { error: 'unknown device' });
  const { kind, detail } = body || {};
  if (!['blocked_app', 'blocked_site', 'limit_reached', 'tamper', 'info'].includes(kind)) return json(res, 400, { error: 'bad kind' });
  logEvent(d.id, kind, detail || null);
  json(res, 200, { ok: true });
});

route('GET', '/api/health', (_req, res) => json(res, 200, { ok: true, ts: now() }));

/* -------------------------------- helpers -------------------------------- */
const pub = (p) => ({ id: p.id, email: p.email, name: p.name });
const tokenFor = (p) => S.signToken({ sub: p.id, email: p.email, exp: now() + 30 * 864e5 });

/* ----------------------------- static files ------------------------------ */
const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.svg': 'image/svg+xml' };
function serveStatic(req, res, pathname) {
  let rel = pathname === '/' ? '/index.html' : pathname;
  const file = path.normalize(path.join(PUBLIC_DIR, rel));
  if (!file.startsWith(PUBLIC_DIR)) return json(res, 403, { error: 'forbidden' });
  fs.readFile(file, (err, buf) => {
    if (err) return json(res, 404, { error: 'not found' });
    res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'application/octet-stream' });
    res.end(buf);
  });
}

/* -------------------------------- server --------------------------------- */
const server = http.createServer((req, res) => {
  const { pathname } = new URL(req.url, 'http://localhost');
  if (req.method === 'OPTIONS') {
    res.writeHead(204, { 'Access-Control-Allow-Origin': '*', 'Access-Control-Allow-Methods': 'GET,POST,PUT,DELETE,OPTIONS', 'Access-Control-Allow-Headers': 'Content-Type,Authorization' });
    return res.end();
  }

  if (pathname.startsWith('/api/')) {
    let raw = '';
    req.on('data', (c) => { raw += c; if (raw.length > 262144) req.destroy(); });
    req.on('end', () => {
      let body = {};
      if (raw) { try { body = JSON.parse(raw); } catch { return json(res, 400, { error: 'invalid JSON' }); } }
      for (const r of routes) {
        if (r.method !== req.method) continue;
        const params = match(r.pattern, pathname);
        if (params) { try { return r.handler(req, res, params, body); } catch (e) { return json(res, 500, { error: e.message }); } }
      }
      json(res, 404, { error: 'no such endpoint' });
    });
    return;
  }
  serveStatic(req, res, pathname);
});

server.listen(PORT, () => {
  console.log(`Guardian backend listening on http://localhost:${PORT}`);
  console.log(`Open the dashboard at  http://localhost:${PORT}/`);
});

# Backend — API reference

Plain Node.js (≥18), zero dependencies. `node server.js`. Data in `guardian-data.json`.

## Auth (parent)
| Method | Path | Body | Returns |
|--------|------|------|---------|
| POST | `/api/auth/register` | `{email,password,name?}` | `{token, parent}` |
| POST | `/api/auth/login` | `{email,password}` | `{token, parent}` |

Send the token as `Authorization: Bearer <token>` on all parent endpoints below.

## Devices (parent)
| Method | Path | Body | Notes |
|--------|------|------|-------|
| POST | `/api/devices` | `{childName, platform}` | creates device, returns `{id, pairCode}` |
| GET | `/api/devices` | — | list with live status, location, unread alerts |
| GET | `/api/devices/:id/policy` | — | `{policy, rev}` |
| PUT | `/api/devices/:id/policy` | `{policy}` | bumps `rev`; phone picks it up on next sync |
| GET | `/api/devices/:id/events` | — | last 200 alerts/events |
| POST | `/api/devices/:id/events/ack` | — | mark all read |
| DELETE | `/api/devices/:id` | — | remove device |

## Device sync (child phone)
Authenticated with `Authorization: Bearer <deviceSecret>`.

| Method | Path | Body | Notes |
|--------|------|------|-------|
| POST | `/api/enroll` | `{pairCode}` | exchanges 6-digit code → `{deviceId, deviceSecret, policy, rev}` |
| GET | `/api/sync/policy` | — | `{policy, rev}` |
| POST | `/api/sync/heartbeat` | `{battery,lat,lng,foregroundApp,policyRev}` | status + location |
| POST | `/api/sync/event` | `{kind, detail}` | kind ∈ `blocked_app·blocked_site·limit_reached·tamper·info` |

## Hardening for production
- Set `TOKEN_KEY` to a long random secret (token signing).
- Terminate TLS (put it behind a reverse proxy / managed host); the phones should
  talk HTTPS, and you should pin the certificate on-device.
- Replace the JSON store in `store.js` with Postgres/SQLite; the rest of the server
  only uses the exported helpers, so it's a contained change.
- Add rate limiting on `/api/auth/*` and `/api/enroll`.
- Switch polling to push (FCM for Android, APNs for iOS) so rule changes apply instantly.

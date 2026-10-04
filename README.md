# 🛡️ Guardian — Parental Control Starter

A working starter for a cross-platform parental control system: a **parent dashboard**,
a **backend**, and **child-device apps** for **Android** and **iOS** that do web
filtering, app blocking + time limits, schedules (bedtime / school), and location
reporting with alerts.

The design goal you asked for — *hard for kids to crack* — is met the way the
commercial products meet it: by building on the **official OS supervision APIs**
(Android **Device Admin / Device Owner**, iOS **Family Controls / Screen Time**)
rather than trying to hide the app. Those APIs tie the controls to the parent's
credential (a Device Owner provisioning or the Screen Time passcode), so a child
can't quietly switch them off, and the app warns you when someone tries.

> **Use it responsibly.** This is for a parent/guardian managing a device they own
> or are legally responsible for, with the child aware the controls are there. Keep
> it transparent (the apps show a persistent "protection is active" notice), and
> check the monitoring/consent laws where you live before deploying it. Don't use it
> to covertly track anyone — that's not what this is, and in most places it's illegal.

---

## Architecture

```
          ┌─────────────────────┐
          │   Parent dashboard  │  (browser — backend/public)
          │  set rules, see map │
          └──────────┬──────────┘
                     │  REST + token
          ┌──────────▼──────────┐
          │      Backend        │  (backend/ — plain Node.js, no deps)
          │  auth · policy · DB │
          └──────────┬──────────┘
                     │  REST + per-device secret
        ┌────────────┴────────────┐
        │                         │
┌───────▼────────┐        ┌───────▼────────┐
│ Android child  │        │  iOS child     │
│ Device Admin   │        │ Family Controls│
│ Accessibility  │        │ ManagedSettings│
│ VPN web filter │        │ DeviceActivity │
│ UsageStats     │        │ CoreLocation   │
└────────────────┘        └────────────────┘
```

One **policy** object is the contract between all three parts:

```jsonc
{
  "webFilter": { "enabled": true, "blockCategories": ["adult","gambling","malware"],
                 "blocklist": ["example.com"], "allowlist": [], "safeSearch": true },
  "appRules":  { "blocked": ["com.app.x"], "limits": { "com.instagram.android": 45 } },
  "schedules": { "bedtime": { "enabled": true, "start": "21:00", "end": "07:00", "days": [0,1,2,3,4,5,6] },
                 "school":  { "enabled": false, "start": "08:00", "end": "15:00", "days": [1,2,3,4,5] } },
  "location":  { "reportIntervalSec": 300 }
}
```

The parent edits it in the dashboard; each phone pulls it on a schedule and enforces
it locally (so enforcement keeps working offline), and pushes back heartbeats +
alert events.

---

## 1. Run the backend (works immediately — no `npm install`)

```bash
cd backend
node server.js
# → http://localhost:4000   (dashboard + API)
```

Needs only Node.js ≥ 18. Data is stored in `backend/guardian-data.json`. For
production set env vars `PORT`, `TOKEN_KEY` (token signing secret), and swap the
JSON store in `store.js` for a real database.

**Try the whole flow in the browser:**
1. Open `http://localhost:4000/`, create a parent account.
2. Click **+ Add**, choose a name and platform → you get a **6-digit pairing code**.
3. Enter that code in the child app (below). The device appears with live status.
4. Set rules; the phone applies them within ~15 min (or instantly on next sync).

See [`backend/README.md`](backend/README.md) for the full API reference.

---

## 2. Android child app

```bash
# Open android-child/ in Android Studio (Giraffe or newer). Let Gradle sync.
# Set your backend URL in app/build.gradle.kts -> API_BASE
#   emulator → http://10.0.2.2:4000 ; real device → http://<your-LAN-ip>:4000
# Run on a device/emulator (Android 8.0+).
```

In the app, walk the numbered setup steps with the child present. Each is a visible
OS consent screen — that's what makes it sturdy:

1. **Pair** with the parent code.
2. **Device admin** → uninstall protection + tamper alerts.
3. **Accessibility** → lets Guardian detect and close blocked apps.
4. **Usage access** → powers daily time limits.
5. **Display over apps** → the block screen.
6. **Web filter (VPN)** → on-device DNS/site filtering (no traffic leaves the phone).
7. **Start protection.**

For the strongest lock-down (block uninstall entirely, block safe mode & factory
reset, force the VPN always-on), provision the app as **Device Owner** — see
[`android-child/README.md`](android-child/README.md).

---

## 3. iOS child app

iOS enforcement uses **Family Controls**, which requires a special Apple entitlement.

1. Open `ios-child/` in Xcode, create an iOS App target "Guardian" from these files.
2. Add the **Family Controls** capability (and request the distribution entitlement
   from Apple for App Store release).
3. Add a **DeviceActivityMonitor** extension target using `GuardianMonitor/`, and put
   both targets in an **App Group** (`group.com.guardian.child`).
4. Add the Info.plist keys in [`ios-child/INFO_PLIST_KEYS.md`](ios-child/INFO_PLIST_KEYS.md).
5. Run on a real device (Family Controls doesn't work in the Simulator). Authorize
   Screen Time, pair with the code, pick apps to limit.

---

## What's fully implemented vs. scoped-for-you

| Piece | State |
|-------|-------|
| Backend API, auth, policy sync, heartbeats, events | ✅ complete & tested |
| Parent dashboard (rules, map link, live status, alerts) | ✅ complete |
| Android: Device Admin, Accessibility app-blocking, schedules, time limits, location, boot-restart | ✅ working logic |
| Android: VPN web filter | ✅ TUN set up + decision logic; DNS packet parse/forward marked `TODO` |
| iOS: authorization, pairing, shields, schedules, location | ✅ scaffold with real API calls |
| Device Owner provisioning, push (FCM/APNs) instead of polling, managed category blocklists | 🔜 documented next steps |

Nothing is faked — the stubs are clearly marked and the surrounding machinery is real,
so you can fill them in incrementally.

---

## Tamper-resistance checklist

- [x] Built on official supervision APIs, not app-hiding
- [x] Device Admin blocks casual uninstall; disable attempt fires a **tamper alert**
- [x] Enforcement uses cached policy → keeps working **offline**
- [x] Foreground service + `BOOT_COMPLETED` restart → survives reboot
- [ ] **Device Owner** for uninstall/safe-mode/factory-reset lock (recommended; see Android README)
- [ ] Always-on VPN lock (Device Owner `setAlwaysOnVpnPackage`)
- [ ] Certificate pinning on the device→backend connection

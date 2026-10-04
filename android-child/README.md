# Android child app

Kotlin, Android Studio, minSdk 26 (Android 8.0). Open `android-child/` in Android
Studio and let Gradle generate the wrapper on first sync. Set `API_BASE` in
`app/build.gradle.kts`.

## How enforcement works
- **AppBlockerService** (AccessibilityService) gets a callback whenever a new app
  comes to the foreground, checks it against the cached policy, and if it must be
  blocked, sends the user home and shows `BlockActivity`. Handles the always-blocked
  list, active schedules (bedtime/school), and exceeded daily limits.
- **UsageTracker** reads `UsageStatsManager` for today's per-app foreground minutes.
- **WebFilterVpnService** stands up a local TUN interface and decides per-domain via
  `shouldBlock()`. The DNS parse/forward loop is marked `TODO` (a contained piece).
- **LocationReporter** + **GuardianForegroundService** keep status/location flowing.
- **SyncWorker** (WorkManager, ~15 min) pulls policy and sends heartbeats.
- **GuardianAdminReceiver** (Device Admin) blocks casual uninstall and fires a
  `tamper` alert on `onDisableRequested`.

## Standard lock-down (any device)
The numbered setup flow in `MainActivity` grants: pairing, Device Admin,
Accessibility, Usage access, overlay, VPN. This already makes removal awkward and
alerts you to attempts.

## Strong lock-down — Device Owner (recommended)
Device Owner can be set **only on a freshly factory-reset device with no accounts**.
It lets Guardian block uninstall outright, block safe-mode and factory reset, and
force the VPN always-on.

1. Factory reset the child's phone; skip account sign-in during setup.
2. Install the APK (e.g. `adb install app-release.apk`).
3. Set Guardian as Device Owner:
   ```bash
   adb shell dpm set-device-owner com.guardian.child/.admin.GuardianAdminReceiver
   ```
4. Extend `GuardianAdminReceiver` to call, once admin is active:
   ```kotlin
   val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
   val admin = GuardianAdminReceiver.component(ctx)
   dpm.setUninstallBlocked(admin, ctx.packageName, true)
   dpm.addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)
   dpm.addUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
   dpm.addUserRestriction(admin, UserManager.DISALLOW_ADD_USER)
   dpm.setAlwaysOnVpnPackage(admin, ctx.packageName, /*lockdown=*/true)
   ```
   For managed fleets you can provision Device Owner via a QR code at the setup
   wizard instead of ADB.

## Notes / limits
- Accessibility + Usage access are special grants; on non-Device-Owner devices a
  determined teen can revoke them in Settings — but Guardian sees the app stop
  syncing and you still get the gap in heartbeats. Device Owner closes this.
- The app keeps a persistent notification (required, and intentionally honest).

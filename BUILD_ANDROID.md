# Build the Android app into an installable APK

You have three ways to turn the `android-child/` source into an app you can
install on a phone. Pick one. **No option can be done from a chat window — an
Android app is compiled with Google's Android toolchain, which has to run on a
real build machine (your computer or a cloud runner).**

---

## Option A — Cloud build with GitHub Actions (no local install) ✅ easiest

This builds the APK on GitHub's servers and hands you a file to download.

1. Create a free GitHub account if you don't have one.
2. Make a new repository and upload this whole `guardian/` folder to it
   (drag-and-drop in the browser works, or use `git`). The folder already
   contains `.github/workflows/android-build.yml`.
3. Open the **Actions** tab in your repo. You'll see **Build Android APK**.
   It runs automatically on upload; if not, click it → **Run workflow**.
4. When the run finishes (green check, ~5 min), open it and scroll to
   **Artifacts** → download **guardian-debug-apk**. Inside is `app-debug.apk`.
5. Copy the APK to the phone, tap it, and allow "install unknown apps" when
   prompted. Open Guardian and follow the on-screen setup.

> First run sometimes surfaces a compile error or two (normal for a fresh
> project). Paste the red log back to me and I'll fix it.

---

## Option B — Android Studio (free, on your computer)

1. Install **Android Studio** (Windows/Mac/Linux) from developer.android.com.
2. **File → Open** → choose the `android-child` folder. Let Gradle sync (it
   downloads the SDK pieces automatically the first time).
3. Plug in an Android phone with **USB debugging** on (or use the built-in
   emulator) and press **Run ▶**. The app installs and launches.
4. To get a shareable file instead: **Build → Build Bundle(s)/APK(s) → Build APK**.

---

## Option C — Command line (if you already have the Android SDK)

```bash
cd android-child
# one-time: generate the Gradle wrapper with your local Gradle
gradle wrapper --gradle-version 8.9
./gradlew assembleDebug
# APK lands at app/build/outputs/apk/debug/app-debug.apk
```

---

## Point the app at your backend

The app needs to reach the Guardian backend. Set the address in
`android-child/app/build.gradle.kts`:

```kotlin
buildConfigField("String", "API_BASE", "\"http://10.0.2.2:4000\"")  // emulator → your laptop
```

- **Emulator + backend on your laptop:** keep `http://10.0.2.2:4000`.
- **Real phone + backend on your laptop (same Wi-Fi):** use your laptop's LAN IP,
  e.g. `http://192.168.1.20:4000`.
- **Hosted backend:** use its `https://…` URL (and you can then remove
  `usesCleartextTraffic` from the manifest).

Run the backend first: `cd backend && node server.js`.

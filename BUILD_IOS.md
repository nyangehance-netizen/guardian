# Build the iOS app

iOS parental controls use Apple's **Family Controls / Screen Time** framework.
Unlike Android, there is **no way around needing a Mac and an Apple account** —
not a limitation of this project, but Apple's rules for this kind of app:

1. **A Mac with Xcode.** iOS apps can only be compiled on macOS. There is no
   Windows, Linux, or cloud path that ends in an iPhone-installable Family
   Controls app.
2. **An Apple Developer account.** Free for building onto your own iPhone for
   7-day test installs; **$99/year** (Apple Developer Program) to keep it
   installed and to distribute it.
3. **The Family Controls entitlement.** The capability that lets the app shield
   apps and filter the web must be requested from Apple:
   - *Development:* add the **Family Controls** capability in Xcode →
     Signing & Capabilities. This works immediately for testing on your device.
   - *Distribution (App Store / TestFlight):* request the distribution
     entitlement at
     https://developer.apple.com/contact/request/family-controls-distribution
     Approval is required before release.

## Steps once you have a Mac

1. Open Xcode → **File → New → Project → iOS App**, name it "Guardian",
   interface **SwiftUI**, language **Swift**.
2. Delete the template's `ContentView`/App files and **drag in** everything from
   `ios-child/Guardian/`.
3. **Signing & Capabilities:** select your team, then **+ Capability → Family
   Controls**. Also add **App Groups** and create `group.com.guardian.child`.
4. Add a second target: **File → New → Target → Device Activity Monitor
   Extension**, name it "GuardianMonitor", and replace its file with
   `ios-child/GuardianMonitor/GuardianMonitor.swift`. Add that target to the same
   App Group.
5. Add the Info.plist keys listed in `ios-child/INFO_PLIST_KEYS.md`, and set
   `base` in `APIClient.swift` to your backend URL.
6. Plug in an iPhone (Family Controls does **not** work in the Simulator) and
   press **Run ▶**. Approve Screen Time when prompted, then pair with the code
   from the dashboard.

If you don't have a Mac, options are: borrow/rent one (including cloud Mac
services like MacStadium or a rented Mac mini), or focus on Android first — the
backend, dashboard, and Android app are a complete working system on their own.

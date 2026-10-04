import SwiftUI

/// Guardian (iOS child app) entry point.
///
/// iOS parental controls work differently from Android: enforcement goes through
/// Apple's **Family Controls** framework. The parent authorizes Guardian once
/// (confirmed with the device's Screen Time passcode), after which the app can
/// shield apps/categories and block web domains via `ManagedSettings`, and run
/// time/schedule rules via `DeviceActivity`. Because these are Apple-blessed
/// supervision APIs tied to the Screen Time passcode, the child cannot quietly
/// turn them off — which is the iOS answer to "hard for kids to crack".
///
/// IMPORTANT ENTITLEMENT: the `com.apple.developer.family-controls` capability
/// requires a special request to Apple (it is not granted by a normal developer
/// account). See the top-level README, "iOS" section.
@main
struct GuardianChildApp: App {
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(model)
                .task { await model.bootstrap() }
        }
    }
}

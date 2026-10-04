import DeviceActivity
import ManagedSettings

/// DeviceActivityMonitor extension (this file belongs to a *separate* app-extension
/// target named e.g. "GuardianMonitor"). iOS calls these when a schedule window
/// starts/ends, letting us apply the shield during bedtime/school and lift it after.
///
/// The extension and the main app share a ManagedSettingsStore by name and talk
/// through an App Group, so create an App Group (group.com.guardian.child) and add
/// both targets to it.
final class GuardianMonitor: DeviceActivityMonitor {
    private let store = ManagedSettingsStore(named: .init("guardian"))

    override func intervalDidStart(for activity: DeviceActivityName) {
        super.intervalDidStart(for: activity)
        // Block everything during the window. For "school" you might shield only
        // a category; adjust to taste / to the policy.
        store.shield.applicationCategories = .all()
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        super.intervalDidEnd(for: activity)
        // Restore the normal, policy-driven shield (nil = no category-wide block).
        store.shield.applicationCategories = nil
    }
}

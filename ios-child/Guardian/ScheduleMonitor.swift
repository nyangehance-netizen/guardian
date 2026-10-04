import Foundation
import DeviceActivity

/// Sets up DeviceActivity schedules (bedtime / school) from the backend policy.
/// A DeviceActivityMonitor extension (separate target) receives intervalDidStart/
/// intervalDidEnd callbacks and applies/removes the shield for those windows.
enum ScheduleMonitor {
    static let center = DeviceActivityCenter()

    static func sync(policy: Policy) {
        // Pull schedule times out of the raw policy JSON (kept loose on iOS).
        // In a full build, decode these into typed values like the Android side.
        stopAll()
        applySchedule(named: "bedtime", policy: policy)
        applySchedule(named: "school", policy: policy)
    }

    private static func applySchedule(named name: String, policy: Policy) {
        // Example fixed window; replace start/end with values from policy.schedules[name].
        // Here we show the DeviceActivity wiring, which is the part people get stuck on.
        guard let (startH, startM, endH, endM) = Defaults.scheduleWindow(name) else { return }
        let schedule = DeviceActivitySchedule(
            intervalStart: DateComponents(hour: startH, minute: startM),
            intervalEnd: DateComponents(hour: endH, minute: endM),
            repeats: true
        )
        let activity = DeviceActivityName(name)
        try? center.startMonitoring(activity, during: schedule)
    }

    static func stopAll() {
        center.stopMonitoring()
    }
}

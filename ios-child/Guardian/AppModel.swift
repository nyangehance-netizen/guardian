import Foundation
import FamilyControls
import ManagedSettings
import DeviceActivity

/// Observable app state + the glue between the backend policy and Apple's
/// enforcement frameworks.
@MainActor
final class AppModel: ObservableObject {
    @Published var authorized = false
    @Published var enrolled = false
    @Published var childName = ""
    @Published var statusLine = "Not set up"

    private let store = ManagedSettingsStore(named: .init("guardian"))
    private let api = APIClient.shared
    private let center = AuthorizationCenter.shared
    private var locationReporter: LocationReporter?

    func bootstrap() async {
        enrolled = Keychain.deviceSecret != nil
        childName = Defaults.childName ?? ""
        authorized = center.authorizationStatus == .approved
        if enrolled { await refreshPolicyAndApply() }
        startLocationIfNeeded()
        updateStatus()
    }

    /// Step 1 — ask the parent to approve Guardian as the Screen Time agent.
    func requestAuthorization() async {
        do {
            try await center.requestAuthorization(for: .child)
            authorized = center.authorizationStatus == .approved
            updateStatus()
        } catch {
            statusLine = "Authorization failed: \(error.localizedDescription)"
        }
    }

    /// Step 2 — pair with the 6-digit code from the parent dashboard.
    func enroll(pairCode: String) async {
        do {
            let e = try await api.enroll(pairCode: pairCode)
            Keychain.deviceSecret = e.deviceSecret
            Defaults.deviceId = e.deviceId
            Defaults.childName = e.childName
            childName = e.childName
            enrolled = true
            apply(policy: e.policy)
            startLocationIfNeeded()
            updateStatus()
        } catch {
            statusLine = "Pairing failed: \(error.localizedDescription)"
        }
    }

    func refreshPolicyAndApply() async {
        guard let secret = Keychain.deviceSecret else { return }
        if let p = try? await api.fetchPolicy(secret: secret) { apply(policy: p) }
    }

    /// Translate the backend policy into ManagedSettings + DeviceActivity rules.
    private func apply(policy: Policy) {
        // --- Web content filtering ---
        if policy.webFilter.enabled {
            // Apple auto-filters adult content + lets us add explicit allow/deny lists.
            store.webContent.blockedByFilter = .auto(
                except: Set(policy.webFilter.allowlist.compactMap { URL(string: "https://\($0)") }),
                denied: Set(policy.webFilter.blocklist.compactMap { URL(string: "https://\($0)") })
            )
        } else {
            store.webContent.blockedByFilter = .all()  // no-op placeholder; see note below
            store.webContent.blockedByFilter = nil
        }

        // --- App shields ---
        // iOS identifies apps by opaque ApplicationTokens chosen via FamilyActivityPicker,
        // NOT by bundle id. ShieldStore persists the parent's picker selection; we apply it:
        store.shield.applications = ShieldStore.load()?.applicationTokens
        store.shield.applicationCategories = ShieldStore.load()?.categoryTokens.map { .specific($0) } ?? ShieldSettings.ActivityCategoryPolicy.none

        // --- Schedules (bedtime / school) via DeviceActivity ---
        ScheduleMonitor.sync(policy: policy)

        Defaults.policyRev = policy.rev
        updateStatus()
    }

    private func startLocationIfNeeded() {
        guard enrolled, locationReporter == nil else { return }
        locationReporter = LocationReporter()
        locationReporter?.start()
    }

    private func updateStatus() {
        statusLine = [
            enrolled ? "✓ Paired as \(childName)" : "✗ Not paired",
            authorized ? "✓ Screen Time authorized" : "✗ Not authorized",
        ].joined(separator: "\n")
    }
}

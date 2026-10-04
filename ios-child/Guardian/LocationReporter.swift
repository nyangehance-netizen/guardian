import Foundation
import CoreLocation
import UIKit

/// Reports battery + location heartbeats to the backend so the parent dashboard
/// shows live status and the child's location.
final class LocationReporter: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var last: CLLocation?

    func start() {
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        manager.allowsBackgroundLocationUpdates = true
        manager.requestAlwaysAuthorization()
        manager.startMonitoringSignificantLocationChanges()
        Task { await beat() }
    }

    func locationManager(_ m: CLLocationManager, didUpdateLocations locs: [CLLocation]) {
        last = locs.last
        Task { await beat() }
    }

    private func beat() async {
        guard let secret = Keychain.deviceSecret else { return }
        UIDevice.current.isBatteryMonitoringEnabled = true
        let battery = UIDevice.current.batteryLevel >= 0 ? Int(UIDevice.current.batteryLevel * 100) : nil
        await APIClient.shared.heartbeat(
            secret: secret, battery: battery,
            lat: last?.coordinate.latitude, lng: last?.coordinate.longitude,
            rev: Defaults.policyRev
        )
    }
}

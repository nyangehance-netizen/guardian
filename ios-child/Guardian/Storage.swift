import Foundation
import Security

/// Small helpers for persisting non-secret prefs (UserDefaults) and the one secret
/// (device bearer token) in the Keychain.
enum Defaults {
    private static let d = UserDefaults.standard
    static var deviceId: String? { get { d.string(forKey: "deviceId") } set { d.set(newValue, forKey: "deviceId") } }
    static var childName: String? { get { d.string(forKey: "childName") } set { d.set(newValue, forKey: "childName") } }
    static var policyRev: Int { get { d.integer(forKey: "policyRev") } set { d.set(newValue, forKey: "policyRev") } }
    static var shieldSelection: Data? { get { d.data(forKey: "shieldSelection") } set { d.set(newValue, forKey: "shieldSelection") } }

    /// Stub schedule window lookup; a full build reads these from the policy JSON.
    static func scheduleWindow(_ name: String) -> (Int, Int, Int, Int)? {
        switch name {
        case "bedtime": return (21, 0, 7, 0)
        case "school":  return (8, 0, 15, 0)
        default: return nil
        }
    }
}

enum Keychain {
    private static let account = "guardian.deviceSecret"
    static var deviceSecret: String? {
        get { read() }
        set { if let v = newValue { write(v) } else { delete() } }
    }
    private static func write(_ value: String) {
        delete()
        let q: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: account,
            kSecValueData as String: Data(value.utf8),
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock,
        ]
        SecItemAdd(q as CFDictionary, nil)
    }
    private static func read() -> String? {
        let q: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var out: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess,
              let data = out as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }
    private static func delete() {
        SecItemDelete([kSecClass as String: kSecClassGenericPassword,
                       kSecAttrAccount as String: account] as CFDictionary)
    }
}

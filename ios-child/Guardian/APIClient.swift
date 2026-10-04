import Foundation

/// Minimal HTTP client for the Guardian backend, mirroring the Android ApiClient.
/// Uses async/await URLSession, no third-party dependencies.
struct Policy: Decodable {
    struct WebFilter: Decodable {
        var enabled: Bool
        var blockCategories: [String]
        var blocklist: [String]
        var allowlist: [String]
        var safeSearch: Bool
    }
    var webFilter: WebFilter
    var rev: Int = 0
    // appRules / schedules are decoded loosely where needed by consumers.
    var raw: [String: Any] = [:]

    enum CodingKeys: String, CodingKey { case webFilter }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        webFilter = try c.decode(WebFilter.self, forKey: .webFilter)
    }
    init(webFilter: WebFilter, rev: Int) { self.webFilter = webFilter; self.rev = rev }
}

final class APIClient {
    static let shared = APIClient()

    // Point at your backend. Use your machine's LAN IP when testing on a real device.
    private let base = URL(string: "http://localhost:4000")!

    struct Enrollment { let deviceId: String; let deviceSecret: String; let childName: String; let policy: Policy }

    func enroll(pairCode: String) async throws -> Enrollment {
        let body = try JSONSerialization.data(withJSONObject: ["pairCode": pairCode])
        let json = try await post("/api/enroll", body: body, bearer: nil)
        let polData = try JSONSerialization.data(withJSONObject: json["policy"] as Any)
        var policy = try JSONDecoder().decode(Policy.self, from: polData)
        policy.rev = json["rev"] as? Int ?? 0
        return Enrollment(
            deviceId: json["deviceId"] as? String ?? "",
            deviceSecret: json["deviceSecret"] as? String ?? "",
            childName: json["childName"] as? String ?? "child",
            policy: policy
        )
    }

    func fetchPolicy(secret: String) async throws -> Policy {
        let json = try await get("/api/sync/policy", bearer: secret)
        let polData = try JSONSerialization.data(withJSONObject: json["policy"] as Any)
        var policy = try JSONDecoder().decode(Policy.self, from: polData)
        policy.rev = json["rev"] as? Int ?? 0
        return policy
    }

    func heartbeat(secret: String, battery: Int?, lat: Double?, lng: Double?, rev: Int) async {
        let payload: [String: Any] = [
            "battery": battery as Any, "lat": lat as Any, "lng": lng as Any,
            "foregroundApp": NSNull(), "policyRev": rev,
        ]
        _ = try? await post("/api/sync/heartbeat", body: try? JSONSerialization.data(withJSONObject: payload), bearer: secret)
    }

    func reportEvent(secret: String, kind: String, detail: String?) async {
        let payload: [String: Any] = ["kind": kind, "detail": detail as Any]
        _ = try? await post("/api/sync/event", body: try? JSONSerialization.data(withJSONObject: payload), bearer: secret)
    }

    // MARK: transport
    private func get(_ path: String, bearer: String?) async throws -> [String: Any] {
        try await request("GET", path, body: nil, bearer: bearer)
    }
    private func post(_ path: String, body: Data?, bearer: String?) async throws -> [String: Any] {
        try await request("POST", path, body: body, bearer: bearer)
    }
    private func request(_ method: String, _ path: String, body: Data?, bearer: String?) async throws -> [String: Any] {
        var req = URLRequest(url: base.appendingPathComponent(path))
        req.httpMethod = method
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let bearer { req.setValue("Bearer \(bearer)", forHTTPHeaderField: "Authorization") }
        req.httpBody = body
        let (data, resp) = try await URLSession.shared.data(for: req)
        guard let http = resp as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw NSError(domain: "api", code: (resp as? HTTPURLResponse)?.statusCode ?? -1,
                          userInfo: [NSLocalizedDescriptionKey: String(data: data, encoding: .utf8) ?? "request failed"])
        }
        return (try JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
    }
}

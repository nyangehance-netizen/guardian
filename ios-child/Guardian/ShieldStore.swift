import Foundation
import ManagedSettings
import FamilyControls

/// Persists the parent's app/category selection from FamilyActivityPicker.
/// On iOS you cannot block by bundle id; the parent picks apps/categories in a
/// system picker and you store the opaque tokens it returns.
struct ShieldStore {
    static func load() -> FamilyActivitySelection? {
        guard let data = Defaults.shieldSelection else { return nil }
        return try? JSONDecoder().decode(FamilyActivitySelection.self, from: data)
    }
    static func save(_ selection: FamilyActivitySelection) {
        Defaults.shieldSelection = try? JSONEncoder().encode(selection)
    }
}

extension FamilyActivitySelection {
    var applicationTokens: Set<ApplicationToken> { applications.compactMap { $0.token }.reduce(into: []) { $0.insert($1) } }
    var categoryTokens: Set<ActivityCategoryToken> { categories.compactMap { $0.token }.reduce(into: []) { $0.insert($1) } }
}

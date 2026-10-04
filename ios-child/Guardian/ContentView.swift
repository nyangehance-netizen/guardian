import SwiftUI
import FamilyControls

struct ContentView: View {
    @EnvironmentObject var model: AppModel
    @State private var pairCode = ""
    @State private var showPicker = false
    @State private var selection = FamilyActivitySelection()

    var body: some View {
        NavigationStack {
            Form {
                Section("Status") {
                    Text(model.statusLine).font(.system(.body, design: .monospaced))
                }

                Section("Setup") {
                    Button("1. Authorize Screen Time") {
                        Task { await model.requestAuthorization() }
                    }

                    HStack {
                        TextField("6-digit pairing code", text: $pairCode)
                            .keyboardType(.numberPad)
                        Button("Pair") { Task { await model.enroll(pairCode: pairCode) } }
                            .disabled(pairCode.count != 6)
                    }

                    Button("Choose apps to limit") { showPicker = true }
                        .disabled(!model.authorized)
                }

                Section {
                    Button("Refresh rules now") { Task { await model.refreshPolicyAndApply() } }
                } footer: {
                    Text("Guardian applies the limits your parent sets. Turning off Screen Time needs the Screen Time passcode your parent controls.")
                }
            }
            .navigationTitle("🛡️ Guardian")
            .familyActivityPicker(isPresented: $showPicker, selection: $selection)
            .onChange(of: selection) { _, newValue in
                ShieldStore.save(newValue)
                Task { await model.refreshPolicyAndApply() }
            }
        }
    }
}

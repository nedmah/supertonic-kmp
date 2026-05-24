import SwiftUI
import SupertonicKMP

@main
struct iOSApp: App {

    init() {
        // Initialize Supertonic bridge before any SupertonicTts usage
        SupertonicHolder.shared.bridge = SupertonicBridge()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

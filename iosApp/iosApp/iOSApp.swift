import SwiftUI
import GoogleMobileAds

@main
struct iOSApp: App {
    init() {
        GADMobileAds.sharedInstance().start(completionHandler: nil)
        AdMobBridge.shared.register()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

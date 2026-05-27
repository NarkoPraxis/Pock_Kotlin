import Foundation
import UIKit
import GoogleMobileAds
import ComposeApp

final class AdMobBridge: NSObject, GADFullScreenContentDelegate {

    static let shared = AdMobBridge()

    private var rewardedAd: GADRewardedAd?
    private var onDismissed: (() -> Void)?

    func register() {
        let provider = IosAdProvider.shared
        provider.loadHandler = { [weak self] (adUnitId: String, onLoaded: @escaping () -> KotlinUnit, onFailed: @escaping () -> KotlinUnit) -> Void in
            guard let self = self else { _ = onFailed(); return }
            self.loadRewarded(adUnitId: adUnitId, onLoaded: onLoaded, onFailed: onFailed)
        }
        provider.showHandler = { [weak self] (onEarned: @escaping () -> KotlinUnit, onDismissed: @escaping () -> KotlinUnit) -> Void in
            guard let self = self else { _ = onDismissed(); return }
            self.showRewarded(onEarned: onEarned, onDismissed: onDismissed)
        }
        provider.readyProvider = { [weak self] in
            KotlinBoolean(value: self?.rewardedAd != nil)
        }
    }

    private func loadRewarded(adUnitId: String, onLoaded: @escaping () -> KotlinUnit, onFailed: @escaping () -> KotlinUnit) {
        let unitId = Bundle.main.infoDictionary?["AdMobRewardedUnitID"] as? String ?? adUnitId
        let request = GADRequest()
        GADRewardedAd.load(withAdUnitID: unitId, request: request) { [weak self] ad, error in
            if let error = error {
                NSLog("[AdMob] rewarded load failed: \(error.localizedDescription)")
                _ = onFailed()
                return
            }
            self?.rewardedAd = ad
            self?.rewardedAd?.fullScreenContentDelegate = self
            _ = onLoaded()
        }
    }

    private func showRewarded(onEarned: @escaping () -> KotlinUnit, onDismissed: @escaping () -> KotlinUnit) {
        guard let ad = rewardedAd, let root = topViewController() else {
            _ = onDismissed()
            return
        }
        self.onDismissed = { _ = onDismissed() }
        ad.present(fromRootViewController: root) {
            _ = onEarned()
        }
    }

    func adDidDismissFullScreenContent(_ ad: GADFullScreenPresentingAd) {
        rewardedAd = nil
        let cb = onDismissed
        onDismissed = nil
        cb?()
    }

    func ad(_ ad: GADFullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        NSLog("[AdMob] present failed: \(error.localizedDescription)")
        rewardedAd = nil
        let cb = onDismissed
        onDismissed = nil
        cb?()
    }

    private func topViewController() -> UIViewController? {
        let scene = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first { $0.activationState == .foregroundActive }
            ?? UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        let window = scene?.windows.first(where: { $0.isKeyWindow }) ?? scene?.windows.first
        var top = window?.rootViewController
        while let presented = top?.presentedViewController {
            top = presented
        }
        return top
    }
}

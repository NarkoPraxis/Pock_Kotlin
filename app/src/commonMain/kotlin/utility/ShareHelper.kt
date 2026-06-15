package utility

/**
 * Cross-platform share sheet.
 *
 * [shareAppPromo] presents the native OS share UI (Android chooser / iOS
 * `UIActivityViewController`) with an already-localized [message] and invokes [onShared]
 * once the user actually engages with the share (picks a target on Android / completes the
 * share on iOS) — never on a plain dismiss/cancel. The share-bonus reward is granted from
 * that callback.
 *
 * [storeUrl] is the public store-listing URL for this build's platform and should be embedded
 * in [message] by the caller (via the `share_message` string resource). Both platform values
 * are placeholders until the listings exist — see
 * `Plans/STEPS_TO_LAUNCH/connect share functionality.md` for how to obtain the real links
 * before launch.
 */
expect object ShareHelper {
    val storeUrl: String
    fun shareAppPromo(message: String, onShared: () -> Unit)
}

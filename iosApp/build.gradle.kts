// iOS app is built via Xcode (iosApp/iosApp.xcodeproj).
// This module exists to enable :iosApp tasks from Gradle (e.g. CI triggers).

tasks.register("buildDebug") {
    group = "build"
    description = "Build iOS Debug via xcodebuild (requires macOS)."
    doLast {
        exec {
            commandLine(
                "xcodebuild",
                "-project", "iosApp.xcodeproj",
                "-scheme", "iosApp",
                "-configuration", "Debug",
                "-destination", "platform=iOS Simulator,name=iPhone 15",
                "build"
            )
        }
    }
}

tasks.register("buildRelease") {
    group = "build"
    description = "Build iOS Release via xcodebuild (requires macOS)."
    doLast {
        exec {
            commandLine(
                "xcodebuild",
                "-project", "iosApp.xcodeproj",
                "-scheme", "iosApp",
                "-configuration", "Release",
                "-allowProvisioningUpdates",
                "build"
            )
        }
    }
}

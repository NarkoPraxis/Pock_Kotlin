import SwiftUI
import ComposeApp

struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all)
            .onChange(of: scenePhase) { phase in
                switch phase {
                case .background, .inactive:
                    Sounds.shared.pauseAll()
                case .active:
                    Sounds.shared.resumeAll()
                default:
                    break
                }
            }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

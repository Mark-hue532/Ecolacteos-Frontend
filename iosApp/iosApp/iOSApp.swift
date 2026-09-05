import SwiftUI
import shared

/// Contenedor delgado (CLAUDE.md §3.5): solo arranca Koin y monta `ContentView`. Sin UI de Compose
/// Multiplatform todavía -- eso es Fase 7; por ahora `ContentView` es SwiftUI puro con un placeholder.
///
/// NOTA: este archivo no se puede compilar todavía -- no existe `iosApp.xcodeproj` en este repo
/// (se genera en una Mac, ver `iosApp/README.md`). El `import shared` asume que el framework de
/// `shared` está enlazado como en cualquier proyecto KMP estándar.
@main
struct IOSApp: App {

    init() {
        KoinKt.initKoin(appDeclaration: { _ in })
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

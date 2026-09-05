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
        // El binding de SecureTokenStorage (Keychain) es especifico de esta plataforma -- ver
        // `security/SecureTokenStorage.kt` y `di/IosSecurityModule.kt` (Fase 3). En Android es
        // `MainActivity.kt` quien hace el equivalente porque ahi si hace falta un Context.
        KoinKt.initKoin(appDeclaration: { app in
            app.modules([IosSecurityModuleKt.moduloSeguridadIos()])
        })
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

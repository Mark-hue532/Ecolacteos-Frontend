# iosApp

Este directorio **no contiene un `.xcodeproj`**. Los targets de Kotlin/Native para iOS solo compilan en
macOS (toolchain de Xcode -- restricción de Apple, no de Kotlin), y este equipo desarrolla en Windows
(ver `CLAUDE.md §8`). Lo que hay acá es la estructura mínima de Swift para cuando alguien con una Mac
retome esta carpeta.

## Qué existe ya

- `iosApp/iOSApp.swift` -- `@main` de la app SwiftUI. Llama a `KoinKt.initKoin()` (equivalente Swift del
  `initKoin()` de `shared/di/Koin.kt`) antes de montar `ContentView`, pasándole el módulo de
  `IosSecurityModuleKt.moduloSeguridadIos()` (Fase 3) que registra `SecureTokenStorage` sobre Keychain --
  ver `shared/src/iosMain/kotlin/.../di/IosSecurityModule.kt`.
- `iosApp/ContentView.swift` -- placeholder de SwiftUI puro. Compose Multiplatform para iOS no entra hasta
  la Fase 7; hasta entonces este archivo se reemplaza pantalla por pantalla.

Ninguno de los dos se pudo compilar ni verificar desde Windows. La única verificación real de que `shared`
sigue siendo consumible desde iOS es el CI (`.github/workflows/verificacion-ios.yml`), que corre en un
runner macOS.

## Qué falta hacer en una Mac

1. **Instalar Xcode** (versión que soporte deployment target iOS 15.0) y sus command line tools.
2. **Generar el `.xcodeproj`**: crear un proyecto iOS App (SwiftUI, sin Core Data, sin tests de UI) llamado
   `iosApp` en este mismo directorio, con:
   - Deployment target: **iOS 15.0** (`CLAUDE.md §4`).
   - Bundle identifier sugerido: `com.ecolacteos.acopio.ios` (a confirmar con el equipo; no hay convención
     todavía documentada en los tres documentos de diseño).
   - Reemplazar el `ContentView.swift`/`iOSApp.swift` que genera el asistente de Xcode por los dos archivos
     que ya están en esta carpeta (o copiarles el contenido si Xcode exige regenerarlos).
3. **Configurar el `Framework Search Path`** del target de la app apuntando al output de
   `shared` para el framework `shared.framework` (Build Settings → Framework Search Paths), típicamente:
   ```text
   $(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)
   ```
   (la ruta exacta depende de si se usa el plugin `kotlin("multiplatform")` con `embedAndSign` directo o el
   plugin de CocoaPods -- este proyecto usa la integración directa de Gradle, sin CocoaPods, ver
   `shared/build.gradle.kts`).
4. **Agregar una fase de build ("Run Script")** en el target de la app, **antes** de "Compile Sources", que
   invoque la tarea de Gradle que genera y firma el framework:
   ```bash
   cd "$SRCROOT/.."
   ./gradlew :shared:embedAndSignAppleFrameworkForXcode
   ```
   Esta tarea la provee automáticamente el plugin de Kotlin Multiplatform cuando el target declara
   `binaries.framework { baseName = "shared" }` (ya configurado en `shared/build.gradle.kts`). Necesita las
   variables de entorno que Xcode inyecta (`CONFIGURATION`, `SDK_NAME`, etc.), por lo que **no se puede
   invocar a mano fuera de una build de Xcode** con resultados equivalentes.
5. **Deployment target 15.0** también en el target de la app (Build Settings → iOS Deployment Target),
   coherente con `shared/build.gradle.kts`.
6. Verificar que el esquema (`Scheme`) del proyecto compila y corre en el simulador
   (`iosSimulatorArm64`, ya que los runners de CI son Apple Silicon).

## Qué NO hacer

- No declarar los targets de `shared` condicionalmente por host -- ya están declarados siempre
  (`androidTarget`, `iosX64`, `iosArm64`, `iosSimulatorArm64`) precisamente para que este paso, cuando
  llegue, no requiera tocar `shared/build.gradle.kts`.
- No intentar generar el `.xcodeproj` con herramientas de línea de comandos desde Windows (`xcodegen`,
  plantillas, etc.) sin haberlo probado antes en una Mac real -- el CI ya es la fuente de verdad de que el
  framework se genera y enlaza correctamente; un `.xcodeproj` generado a ciegas puede quedar inconsistente
  con lo que Xcode espera.

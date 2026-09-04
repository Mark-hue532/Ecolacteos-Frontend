# FASE 1 — Core y configuración

> **Cómo usar este prompt**: guardalo en el repo como `docs/prompts/FASE_01.md` y en Claude Code escribí:
> *"Leé `CLAUDE.md` y ejecutá `docs/prompts/FASE_01.md`."*
> También podés pegar todo el contenido de abajo directamente. Lo primero es preferible: queda versionado
> junto al código y el checkpoint puede referenciarlo.

---

## Objetivo

Levantar el esqueleto del proyecto Kotlin Multiplatform y los tipos base que todas las fases siguientes van
a usar. **Al terminar, el proyecto debe compilar y tener tests corriendo — pero todavía no hace nada
visible.** Eso es correcto y esperado.

## ⚠️ Entorno de desarrollo: Windows, sin Mac

El equipo desarrolla en **Windows**. Los targets iOS de Kotlin/Native **solo compilan en macOS** (requieren
la toolchain de Xcode; es una restricción de Apple, no de Kotlin). Esto condiciona la fase:

- ✅ **Los targets iOS se declaran igual** en `shared/build.gradle.kts`. El código de `commonMain` debe ser
  multiplataforma correcto desde el día 1 — descubrir en la Fase 8 que medio `shared/` usa APIs de JVM
  sería carísimo de revertir.
- ✅ Lo que compila y se testea **localmente** es Android y JVM.
- ✅ La verificación de iOS corre en **CI con runner macOS** (entregable §9 de esta fase). No es opcional:
  es la única forma de saber si `bignum` funciona en iOS, que es un riesgo CRITICAL del proyecto.
- ❌ **No** intentes ejecutar tareas `*Ios*` en la máquina local ni las declares condicionalmente según el
  host: se declaran siempre y simplemente no se corren acá.

## Antes de escribir código: qué leer

1. `CLAUDE.md` **completo** (son las reglas duras del proyecto).
2. `docs/MOBILE_ARCHITECTURE.md`, **solo** estas secciones:
   - §2 (arquitectura por capas)
   - §10 (responsabilidades del API Client — para entender qué tipos de error hacen falta, **sin
     implementar nada de red todavía**)
   - §13 (qué se comparte y qué es específico de plataforma)
   - §14 (elección de herramientas y por qué)
   - §15 (estructura del proyecto — es el mapa exacto de carpetas a crear)
3. `docs/MOBILE_DATA_MAPPING.md`, **solo**:
   - §1.2 a §1.6 (convenciones de UUID, fechas, `BigDecimal`, enums)
   - §5.12 (`ErrorResponse` — la forma única de error de toda la API)

**No leas** `MOBILE_SCREENS.md` en esta fase. No hay UI todavía.

---

## Alcance: qué SÍ entra en esta fase

### 1. Estructura Gradle del proyecto

Según `MOBILE_ARCHITECTURE.md §15`:

```text
acopio-mobile/
├── gradle/libs.versions.toml
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── shared/
│   ├── build.gradle.kts
│   └── src/{commonMain,commonTest,androidMain,iosMain}/kotlin/
├── androidApp/
│   ├── build.gradle.kts
│   └── src/main/kotlin/
└── iosApp/
    └── iosApp/
```

Targets de `shared`: `androidTarget()`, `iosX64()`, `iosArm64()`, `iosSimulatorArm64()`, con el framework
iOS configurado (estático, `baseName = "shared"`).

### 2. `libs.versions.toml` — usar exactamente estas versiones

Verificadas a septiembre de 2026. **No las cambies.** Si alguna no resuelve o entra en conflicto, **pará y
reportalo** en vez de bajar o subir versiones por tu cuenta.

```toml
[versions]
kotlin = "2.4.10"
agp = "9.4.0"
composeMultiplatform = "1.12.0"
coroutines = "1.11.0"
serialization = "1.11.0"
datetime = "0.8.0"
bignum = "0.3.10"
koin = "4.2.2"
ktor = "3.5.2"
sqldelight = "2.3.2"
lifecycle = "2.11.0"
turbine = "1.2.1"
androidMinSdk = "26"
androidCompileSdk = "36"
androidTargetSdk = "36"

[libraries]
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "datetime" }
bignum = { module = "com.ionspin.kotlin:bignum", version.ref = "bignum" }
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-test = { module = "io.insert-koin:koin-test", version.ref = "koin" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }

[plugins]
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
androidLibrary = { id = "com.android.library", version.ref = "agp" }
androidApplication = { id = "com.android.application", version.ref = "agp" }
```

Las entradas de Ktor, SQLDelight, Compose y lifecycle quedan **declaradas en el catálogo pero sin usar
todavía** — entran en las Fases 2, 4 y 7. Declararlas ahora evita tocar el catálogo en cada fase.

**Requisitos de entorno**: Gradle `9.6.0` (mínimo de AGP 9.4.0) y JDK 17. Verificá ambos antes de empezar y
reportá si el entorno no los tiene.

### 3. Paquete `shared/core/` — los tipos base

Crear en `commonMain/kotlin/.../core/`:

**a) `ApiResult<T>`** — el envoltorio que devuelve todo lo que puede fallar. La UI y los UseCases nunca ven
excepciones de red.

```kotlin
sealed interface ApiResult<out T> {
    data class Exito<T>(val datos: T) : ApiResult<T>
    data class Error(val error: ApiError) : ApiResult<Nothing>
}
```

Incluí operadores de conveniencia (`map`, `flatMap`, `onExito`, `onError`, `getOrNull`) con tests.

**b) `ApiError`** — jerarquía cerrada que cubre **toda** forma de fallo. Se deriva de dos fuentes: los
códigos HTTP que el backend realmente devuelve (`MOBILE_DATA_MAPPING.md §9`) y los fallos de red que no
llegan a tener código.

La propiedad más importante es **`esTransitorio`**, porque es la que el Sync Engine de la Fase 5 va a usar
para decidir si reintenta o si marca el registro como fallido permanente:

| Caso | `esTransitorio` | Origen |
|---|---|---|
| Sin conectividad / timeout | `true` | no hay respuesta HTTP |
| 5xx | `true` | servidor caído o error interno |
| 400 / 422 (validación o regla de negocio) | `false` | el usuario debe corregir |
| 401 no autorizado | `false` | dispara re-login |
| 403 sin permiso | `false` | el rol no alcanza |
| 404 no encontrado | `false` | — |
| 409 conflicto (solo `RecepcionPlanta`) | `false` | manejo específico, nunca reintento ciego |
| Desconocido | `false` | por seguridad, nunca reintentar a ciegas algo que no entendemos |

Cada variante lleva el `mensaje` que vino del backend, porque `MOBILE_SCREENS.md §10.4` exige mostrarlo
literal en los errores de validación.

**c) `ErrorResponse`** — el `data class` que refleja la forma única de error de la API
(`{timestamp, status, error, mensaje}`, `MOBILE_DATA_MAPPING.md §5.12`). Anotado `@Serializable`. **Solo el
modelo**; el interceptor que lo decodifica es de la Fase 2.

**d) Tipo decimal del proyecto** — un alias y utilidades sobre `com.ionspin.kotlin.bignum.decimal.BigDecimal`:

```kotlin
typealias Decimal = com.ionspin.kotlin.bignum.decimal.BigDecimal
```

Con helpers de parseo desde `String` y formateo a `String` **con escala fija**, sin pasar nunca por
`Double`. La tabla de escalas por campo está en `MOBILE_SCREENS.md §10.1`; en esta fase alcanza con que el
helper reciba la escala como parámetro.

> ⚠️ **`bignum 0.3.10` es el riesgo CRITICAL de esta fase.** Está publicada contra una versión anterior de
> Kotlin y la compatibilidad de klibs en Kotlin/Native no siempre es hacia adelante. Es la dependencia que
> sostiene toda la estrategia decimal del proyecto (`DATA-002`).
>
> Como iOS no compila en Windows, la verificación ocurre en el CI del §9 y **es lo primero que hay que
> dejar andando**: el workflow debe existir y haber corrido al menos una vez antes de dar la fase por
> cerrada. Si `bignum` no compila o no pasa el test en el target nativo iOS, **pará y reportalo** — no la
> reemplaces por `Double` ni por otra librería sin aprobación explícita.

**e) Utilidades de UUID** — generación de UUID v4 multiplataforma para los `uuidCliente`. Si usás
`kotlin.uuid.Uuid` (experimental) o una implementación propia, **justificalo en el checkpoint**; el
contrato de red usa `String` en todos los casos (`MOBILE_DATA_MAPPING.md §1.2`).

**f) Utilidades de fecha** — helpers sobre `kotlinx.datetime` para obtener "ahora" como `LocalDateTime`
(hora de pared local, sin conversión) y formatear a ISO-8601. **Ninguna función que convierta un
`LocalDateTime` a `Instant` ni a otra zona** — ver regla 3.2 de `CLAUDE.md`.

**g) `Async<T>`** — el envoltorio de carga de `MOBILE_SCREENS.md §3.2`. Solo el tipo, sin uso todavía;
definirlo ahora evita que cada pantalla invente su variante en la Fase 7.

### 4. Dispatchers

`expect`/`actual` para los dispatchers de coroutines, para que los tests puedan inyectar
`UnconfinedTestDispatcher`. Nada de `Dispatchers.Main` acoplado dentro de la lógica.

### 5. Inyección de dependencias (Koin)

- Módulo raíz `coreModule` con lo de esta fase (dispatchers, utilidades).
- Función de arranque `initKoin()` en `commonMain`, invocable desde `androidApp` e `iosApp`.
- Un test que verifique que el grafo **se resuelve completo** (`checkModules()` o equivalente). Este test
  vale oro: cada fase agrega dependencias y este test avisa al instante si algo quedó sin declarar.

### 6. Estructura de carpetas vacías

Crear los paquetes de `MOBILE_ARCHITECTURE.md §15` que todavía no tienen contenido (`domain/model`,
`domain/usecase`, `data/remote/dto`, `data/local`, `data/repository`, `network`, `synchronization`,
`security`, `presentation`, `ui`), con un `.gitkeep` o un `package-info` mínimo. Es para que las fases
siguientes no tengan que decidir dónde va cada cosa.

### 7. Apps contenedoras mínimas

- **`androidApp`**: una `MainActivity` que arranque, llame a `initKoin()` y muestre un texto placeholder.
  No Compose UI real todavía (eso es Fase 7), solo que la app abra.
- **`iosApp`**: **no se puede generar el `.xcodeproj` desde Windows** y no hay que intentarlo. Creá
  únicamente la estructura de carpetas y los archivos Swift mínimos (`iOSApp.swift`, `ContentView.swift`
  con un placeholder), más un `iosApp/README.md` que documente exactamente qué pasos hay que hacer en una
  Mac para completar la integración: abrir el proyecto, configurar el `Framework Search Path` hacia el
  output de `shared`, agregar la fase de build que invoca `embedAndSignAppleFrameworkForXcode`, y el
  deployment target `15.0`. Ese README es el entregable; el `.xcodeproj` se genera cuando haya Mac.

### 8. CI: verificación de iOS en runner macOS

Como iOS no compila localmente, esta es la única forma de saber que `shared` sigue siendo multiplataforma
de verdad. Crear `.github/workflows/verificacion-ios.yml`:

```yaml
name: Verificación iOS

on:
  push:
    branches: [main]
  pull_request:
  workflow_dispatch:      # permite dispararlo a mano, para no gastar minutos de más

jobs:
  ios:
    runs-on: macos-14     # Apple Silicon → el target de simulador es iosSimulatorArm64
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - uses: gradle/actions/setup-gradle@v4

      - name: Compilar shared para iOS
        run: ./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64

      - name: Tests en target nativo iOS (incluye el roundtrip decimal de bignum)
        run: ./gradlew :shared:iosSimulatorArm64Test

      - name: Enlazar framework
        run: ./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

Agregar también un workflow `verificacion-android.yml` equivalente en `ubuntu-latest` que corra
`./gradlew :shared:jvmTest :shared:testDebugUnitTest :androidApp:assembleDebug`, para que el CI cubra las
dos plataformas.

> **Nota de costos**: los runners macOS de GitHub Actions son gratis en repos públicos, pero en repos
> privados consumen minutos a una tasa **10×** la de Linux. Por eso el workflow incluye
> `workflow_dispatch` y se limita a `main` y a los pull requests, en vez de correr en cada push a
> cualquier rama. Si el repo es privado y los minutos son un problema, dejalo solo en `workflow_dispatch` y
> corrélo a mano al cerrar cada fase — pero **no lo elimines**: sin él, un problema de compatibilidad iOS
> se descubre recién en la Fase 8, con todo el código ya escrito.

### 9. Tests (`commonTest`)

Como mínimo:

- `ApiResult`: los operadores se comportan bien en éxito y en error.
- `ApiError`: cada variante tiene el `esTransitorio` correcto según la tabla de arriba.
- `Decimal`: parseo y formateo **preservan la escala exacta** — `"12.50"` sigue siendo `"12.50"` y no se
  convierte en `"12.5"`; un roundtrip `String → Decimal → String` es idéntico. **Ningún test debe pasar por
  `Double`.**
- UUID: genera v4 válidos y distintos.
- Fechas: el helper de "ahora" devuelve hora local sin conversión.
- Koin: el grafo se resuelve.

---

## Fuera de alcance: qué NO entra en esta fase

No lo implementes, ni siquiera "dejándolo listo":

- ❌ Ktor, `HttpClient`, interceptores, DTOs de red → **Fase 2**
- ❌ SQLDelight, archivos `.sq`, esquema, DAOs → **Fase 4**
- ❌ `SecureTokenStorage`, Keystore, Keychain → **Fase 3**
- ❌ Sync Engine, estados de sincronización → **Fase 5**
- ❌ Repositories, UseCases de negocio → **Fase 6**
- ❌ Pantallas, ViewModels concretos, navegación → **Fase 7**
- ❌ `ConnectivityObserver`, WorkManager, BGTaskScheduler → **Fases 5 y 9**

Si al construir algo de esta fase te parece que necesitás adelantar una de esas piezas, **es señal de que
estás sobrediseñando**. Anotalo en el checkpoint y seguí.

---

## Criterios de aceptación

### Verificables localmente (Windows)

1. `./gradlew :shared:jvmTest` corre y todos los tests pasan.
2. `./gradlew :shared:assemble :androidApp:assembleDebug` termina sin errores.
3. `androidApp` genera un APK de debug que abre sin crashear.
4. El test de resolución del grafo de Koin pasa.
5. No hay ni un `Double` ni un `Float` en `shared/` (verificable con un grep; si aparece alguno, tiene que
   estar justificado en el checkpoint).
6. No hay dependencias fuera de las declaradas en el `libs.versions.toml` de arriba.
7. `shared/build.gradle.kts` declara los cuatro targets (`androidTarget`, `iosX64`, `iosArm64`,
   `iosSimulatorArm64`) **sin condicionales por host**.
8. Ningún archivo de `commonMain` importa una API exclusiva de JVM (`java.*`, `javax.*`). Verificable con
   un grep, y es la salvaguarda de que el código siga siendo portable aunque iOS no se compile acá.

### Verificables solo en CI (runner macOS)

9. El workflow `verificacion-ios.yml` existe, está commiteado y **corrió al menos una vez con éxito**.
10. `shared` compila para `iosArm64` y `iosSimulatorArm64`.
11. **`bignum` compila para iOS** y el test de roundtrip decimal pasa en el target nativo, no solo en JVM.
    Este es el criterio CRITICAL de la fase.

**La fase no se cierra sin los tres criterios de CI.** Si el repo todavía no está en GitHub, el checkpoint
debe decirlo explícitamente y la fase queda **abierta** hasta poder correrlos: dar por buena una base
multiplataforma sin haberla compilado nunca para una de sus dos plataformas es exactamente el tipo de deuda
que este proyecto no puede permitirse.

---

## Checkpoint de cierre

Al terminar, **pará y entregá el checkpoint del §5 de `CLAUDE.md`**, con estos puntos específicos de la
Fase 1:

- **El resultado del workflow de iOS** (criterios 9 a 11), con el link al run. Si `bignum` falló en iOS,
  **no sigas**: es un bloqueante CRITICAL y hay que decidir la estrategia antes de la Fase 2.
- Si el CI todavía no pudo correr (repo no subido aún), decilo explícitamente y dejá la fase **abierta**.
- Las versiones que efectivamente resolvieron, si alguna difiere del catálogo y por qué.
- El contenido de `iosApp/README.md`: qué queda pendiente de hacer en una Mac.
- La decisión que hayas tomado sobre la generación de UUID y su justificación.
- Cualquier contradicción que hayas encontrado entre los tres documentos de diseño.

**Después del checkpoint, esperá aprobación explícita antes de empezar la Fase 2.**

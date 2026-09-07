# CLAUDE.md — acopio-mobile

Contexto permanente del proyecto. Se lee en **toda** sesión de Claude Code. Si algo de este archivo
contradice una instrucción de un prompt de fase, **manda este archivo** y hay que avisarlo antes de seguir.

---

## 1. Qué es este proyecto

Frontend móvil **Kotlin Multiplatform (Android + iOS)** del Sistema de Acopio Lechero. Consume un backend
Spring Boot 3.3.4 / Java 17 + PostgreSQL que **ya existe y no se modifica desde aquí**.

Es una app **offline-first real**: los operarios capturan datos en campo sin señal, la UI siempre lee y
escribe contra SQLite local, y un Sync Engine explícito reconcilia con el backend cuando hay conectividad.
El backend está diseñado para esto: los 4 recursos de captura aceptan un `uuidCliente` generado en el
dispositivo y son idempotentes por ese campo.

**Roles de campo/planta (móvil)**: ACOPIADOR, CALIDAD, PRODUCCION, VENTAS, RECEPCION.
**Administración (panel web, fuera de alcance)**: ADMIN, catálogos, usuarios, reportes económicos.

---

## 2. Los tres documentos de diseño

Están en `docs/`. **Son la fuente de verdad del proyecto.** Fueron construidos leyendo el código real del
backend campo por campo, no son borradores ni sugerencias.

| Documento | Qué contiene | Cuándo leerlo |
|---|---|---|
| `docs/MOBILE_ARCHITECTURE.md` | Capas, offline-first, Sync Engine, conflictos, esquema SQLite, herramientas, estructura, gaps del backend | Antes de cada fase |
| `docs/MOBILE_DATA_MAPPING.md` | Contrato campo por campo: tipos, nullability, JSON ↔ Kotlin ↔ SQLite, catálogo `DATA-001..013` | Al escribir DTOs (Fase 2) y `.sq` (Fase 4) |
| `docs/MOBILE_SCREENS.md` | 33 pantallas: `UiState`, eventos, validaciones, estados vacío/carga/error/offline | Desde la Fase 7 |

**Regla de precedencia entre documentos**

1. Tipo de dato, nullability o nombre de campo → **`MOBILE_DATA_MAPPING.md`**
2. Estructura de tablas, capas, flujo de sync → **`MOBILE_ARCHITECTURE.md`**
3. Comportamiento de pantalla → **`MOBILE_SCREENS.md`**

**No leas los tres enteros en cada sesión.** Cada prompt de fase indica qué secciones concretas leer. Son
~3.600 líneas en total; leerlas completas cada vez gasta contexto que hace falta para el código.

---

## 3. Reglas duras (no negociables)

Estas reglas existen porque romperlas produce bugs caros o silenciosos. Si una parece equivocada, **parás y
preguntás**; no la cambiás por tu cuenta.

### 3.1 Decimales: nunca `Double`, nunca `REAL`

Todo campo que en el backend es `java.math.BigDecimal` (litros, precios, totales, porcentajes, GPS,
medidas de laboratorio) viaja así:

```text
JSON number  →  com.ionspin.kotlin.bignum.decimal.BigDecimal  →  SQLite TEXT  →  y de vuelta
```

- ❌ **Nunca** un `Double` en el camino, ni siquiera intermedio ni "solo para mostrar".
- ❌ **Nunca** una columna `REAL` en SQLite. `REAL` es un float IEEE-754 de 8 bytes: reintroduce
  exactamente el error que `BigDecimal` existe para evitar.
- ✅ El `KSerializer` lee y escribe el número **crudo** vía `JsonPrimitive.content`, sin pasar por `Double`.
- ✅ En SQLite, `TEXT` con la representación decimal exacta (`"1234.56"`), vía `ColumnAdapter`.

Motivo: el sistema liquida pagos a proveedores. Las cifras del móvil deben coincidir centavo a centavo con
las columnas `GENERATED` de Postgres. Ver `DATA-002` (CRITICAL).

### 3.2 Fechas: hora de pared, sin convertir

- Todo `LocalDateTime` del backend se modela como `kotlinx.datetime.LocalDateTime` y **se muestra tal cual
  llega**. Nunca se convierte a `Instant` ni a la zona del dispositivo.
- El **único** campo `Instant` real de todo el contrato es `CambiosResponse.generadoEn` (lleva `Z`).
- ❌ **Prohibido** comparar, restar u ordenar mezclando `fechaHora` (la genera el dispositivo) con
  `creadoEn`/`sincronizadoEn` (los genera el servidor). Están en marcos temporales posiblemente distintos.
- ✅ Filtros y ordenamientos usan **solo** `fechaHora`.

Motivo: no sabemos aún si la JVM del servidor corre en UTC o en `America/Lima`. Asumir mal desplaza todo
~5 horas. Ver `DATA-001` y `DATA-012`.

### 3.3 Nada de inventar contrato

- ❌ No inventes endpoints, campos, parámetros de query, códigos de respuesta ni paginación.
- **No existe paginación en ningún endpoint MOBILE.** No implementes scroll infinito ni "cargar más".
- **No existe ningún parámetro de orden.** El orden lo fija el servidor.
- Si algo que necesitás no existe en el backend, **no lo parchees en el cliente en silencio**: se
  documenta como gap (igual que la §18 de la arquitectura) y se avisa en el checkpoint.
- El campo `uuidCliente` es la clave de idempotencia y va **en el body**, nunca en un header. No existe
  `Idempotency-Key`.

### 3.4 Arquitectura de capas

```text
UI (Compose) → ViewModel → UseCase → Repository → SQLite / Ktor
```

- ❌ Un `@Composable` **nunca** inyecta un `Repository`, un `UseCase` ni un `HttpClient`.
- ❌ Un `ViewModel` **nunca** importa un DTO de `data/remote/dto/`. Trabaja con modelos de dominio.
- ❌ La UI **nunca** ve `HttpClient`, excepciones de Ktor ni JSON de error. Todo se traduce antes a
  `ApiResult`/`ApiError`.
- ✅ El `Repository` es la **única** clase que ve a la vez el origen local y el remoto.

### 3.5 Todo lo compartible vive en `shared/`

Incluida la UI y los ViewModels. La decisión está tomada: **Compose Multiplatform para Android e iOS**.

`androidApp/` e `iosApp/` son contenedores delgados. Lo único `expect`/`actual` es: almacenamiento seguro,
conectividad, scheduling de background y permisos del SO.

❌ Un `ViewModel` en `androidApp/` es un error de diseño, no una simplificación: obligaría a reescribir
toda la lógica de presentación en Swift.

### 3.6 El trabajo no confirmado no se borra nunca

Filas con `sync_status` distinto de `SYNCED` no se borran por logout, ni por retención, ni por cambio de
usuario. Solo salen de la base por sincronización exitosa o por descarte explícito del usuario.

---

## 4. Toolchain fijado

Versiones verificadas a **septiembre 2026**. **No las cambies sin avisar**; si alguna no resuelve o es
incompatible, parás y lo reportás en vez de "actualizar a la que funcione".

| Componente | Versión |
|---|---|
| Kotlin | `2.4.10` |
| Compose Multiplatform | `1.12.0` |
| Android Gradle Plugin | `9.4.0` |
| Gradle | `9.6.0` (mínimo exigido por AGP 9.4.0) |
| JDK | `17` |
| Ktor | `3.5.2` |
| SQLDelight | `2.3.2` |
| Koin | `4.2.2` |
| kotlinx-coroutines | `1.11.0` |
| kotlinx-serialization | `1.11.0` |
| kotlinx-datetime | `0.8.0` |
| ionspin bignum | `0.3.10` |
| androidx.lifecycle (grupo Google, Android-only) | `2.11.0` |
| org.jetbrains.androidx.lifecycle (`ViewModel` multiplataforma, Fase 7) | `2.9.6` — grupo y tren de versiones distinto del anterior; no lo actualices junto con `androidx.lifecycle` |
| navigation-compose (Fase 7) | `2.9.0-alpha16` — versión exigida por `org.jetbrains.androidx.lifecycle 2.9.6` de este Compose Multiplatform |
| koin-compose / koin-compose-viewmodel (Fase 7) | `4.2.2` — mismo tren que `Koin` |
| androidx-activity-compose (Fase 7, solo `androidApp`) | `1.11.0` |
| Turbine | `1.2.1` |
| minSdk / androidCompileSdk / androidTargetSdk | `26` / `37` / `36` — `compileSdk` subió en la Fase 7 (lo exigen los artefactos Android de Compose 1.12.0); `targetSdk` no cambió |
| iOS deployment target | `15.0` |

---

## 5. Protocolo de fases

El proyecto avanza en 12 fases (`MOBILE_DATA_MAPPING.md §13`). **Una fase por sesión.**

Al terminar cada fase, **parás** y entregás un checkpoint con exactamente esto:

1. **Qué se construyó** — lista de archivos creados o modificados, con una línea cada uno.
2. **Compila** — el comando que corriste y su resultado.
3. **Tests** — cuántos, cuáles pasan, cuáles no y por qué.
4. **Regresiones** — qué de lo anterior podría haberse roto y cómo lo verificaste.
5. **Decisiones tomadas** — todo lo que tuviste que decidir que no estaba en los documentos.
6. **Problemas encontrados** — incluyendo cualquier contradicción entre documentos.
7. **`DATA CONTRACT ISSUES` nuevos** — si aparece algo que los documentos no anticiparon, en el mismo
   formato de `MOBILE_DATA_MAPPING.md §10`.
8. **¿Requiere cambio de backend?** — sí/no y cuál.
9. **Qué falta para la fase siguiente.**

**Después del checkpoint, esperás aprobación explícita.** No arranques la fase siguiente por tu cuenta.

**No se pasa de fase con un `CRITICAL` sin resolver.** `DATA-001` y `DATA-002` deben tener su estrategia
decidida y aprobada antes de cerrar la Fase 2.

---

## 6. Estilo de trabajo esperado

- **Preguntá antes de asumir.** Si un documento no cubre algo, decilo; no rellenes con una suposición
  razonable y sigas.
- **Cambios mínimos y acotados a la fase.** No refactorices lo que no te toca, no agregues features que
  nadie pidió, no "mejores" código de fases anteriores sin avisar.
- **Tests en `commonTest`**, corriendo en JVM sin emulador. Reservá tests instrumentados solo para lo
  realmente `actual`-specific.
- **Comentarios**: solo donde el *por qué* no sea obvio. Los documentos ya explican el diseño; el código no
  necesita repetirlos. Sí conviene una referencia corta (`// ver DATA-002`) donde una decisión rara tenga
  una razón documentada.
- **Nombres en español** para el dominio (`RegistroAcopio`, `litros`, `proveedorId`), coherentes con el
  backend. Nombres en inglés para lo técnico (`Repository`, `UseCase`, `SyncEngine`).
- No agregues dependencias que no estén en §4 sin justificarlo en el checkpoint.

---

## 7. Pendientes conocidos (no los resuelvas por tu cuenta)

| Tema | Estado | Dónde impacta |
|---|---|---|
| Timezone de la JVM del servidor (`DATA-001`) | **Abierto** — esperando a DevOps | Bloquea el cierre de la Fase 2. Mitigación: tratar todo `LocalDateTime` como hora de pared |
| `androidx.security-crypto` (`EncryptedSharedPreferences`) está **deprecado** por Google | **Resuelto en Fase 3** — Android implementado con AES/256/GCM directo sobre `AndroidKeyStore` (`SecureTokenStorage.android.kt`). Falta solo corregir la mención a `EncryptedSharedPreferences` en `MOBILE_ARCHITECTURE.md` §4 (línea ~216) y §13 (línea ~735) | Documentación pendiente de editar aparte |
| Límite de items por lote en `/api/sync/*` | **Resuelto en Fase 5** — se fijó fragmento de **50** ítems (extremo bajo del rango recomendado) y backoff **15s→30s→1m→5m→15m, tope 8 intentos**, en `synchronization/PoliticaDeSync.kt` | Cerrado; los valores están cubiertos por `PoliticaDeSyncTest` |
| El lote de sync no devuelve el `server_id` (`DATA-014`) | **Abierto, bloquea la cadena offline cruzada** — `SyncResultResponse.confirmados` es solo `List<String>` de `uuidCliente`. Un `AnalisisCalidad`/`LoteProduccion` cuyo `RegistroAcopio` padre se capturó en **este mismo dispositivo** no puede resolver `registroAcopioId` nunca: no hay endpoint por `uuidCliente` y el listado por proveedor no lo trae (`DATA-013`). Decisión aprobada en el checkpoint de Fase 5 (opción A): el motor marca `SYNCED` sin `server_id` y deja al hijo en `PENDING_DEPENDENCY` con un `sync_error` que nombra el issue, en vez de oscilar en silencio | Bloquea a CALIDAD/PRODUCCION capturando offline contra un padre propio. Los demás caminos funcionan. Se cierra con el cambio de backend de §18.1 (o devolviendo el id en `confirmados[]`) |
| `/v3/api-docs` real nunca capturado | Recomendado verificar levantando el backend | Doble chequeo antes de Fase 2 |
| Evidencia fotográfica | **Diferida a v2** por decisión de producto | No implementar captura de foto en v1 |
| Roundtrip real de Keychain en CI (`SecureTokenStorageIosTest`, iOS) | **Abierto, aceptado a propósito en el checkpoint de Fase 3** — los 4 tests están en `@Ignore`. `SecItemAdd`/`SecItemUpdate` fallaron en `iosSimulatorArm64Test` en 3 intentos (runs #7, #8, #11 de `verificacion-ios.yml`); la causa raíz nunca se identificó, el `@Ignore` (commit `0e2c148`) solo puso el CI en verde. El criterio de aceptación #2 de `PROMPT_FASE_03.md` sigue sin cumplirse de fondo | Bloquea la confianza real en persistencia de sesión en iOS. Retomar cuando alguien tenga acceso a los logs del runner o a un Mac para reproducir |
| Roundtrip real de `NativeSqliteDriver` en CI (`crearDriverDeTest`/iOS, Fase 4) | **Abierto, sin verificar en esta sesión (Windows)** — en Windows, `compileTestKotlinIos{Arm64,X64,SimulatorArm64}` **sí corrieron y compilaron en verde** (Kotlin/Native genera klibs contra su propio SDK de Apple embebido, sin necesitar Xcode). Lo que **no** corrió, por falta de toolchain de Xcode/macOS: `linkDebugFrameworkIos*`/`linkReleaseFrameworkIos*` quedaron `SKIPPED`, y las tareas `iosX64Test`/`iosSimulatorArm64Test` (las que de verdad *ejecutan* el `commonTest` de CRUD/roundtrip/restart contra SQLite nativo) quedaron `disabled` — nunca se intentaron. Es decir: la compilación (errores de tipos, firmas `expect`/`actual`) está verificada; el comportamiento real de `NativeSqliteDriver` en tiempo de ejecución, no | Bloquea la confianza real en la capa SQLite en iOS hasta que `verificacion-ios.yml` corra `iosSimulatorArm64Test` en verde. Retomar revisando ese run de CI para la Fase 4 |
| `venta_local` no persiste `total` ni `tipoQuesoNombre` (`DATA-015`) | **Abierto, mitigado en Fase 7** — ambos son de solo lectura del servidor (`total` es columna `GENERATED ALWAYS` de Postgres; `tipoQuesoNombre` no viaja en el Request). `VentaDetalle` los modela `nullable` y `V-03` los muestra como "No disponible" hasta confirmar contra el servidor — **nunca** un cálculo local `cantidad × precioUnitario` | `V-01`/`V-03` (Fase 7) y cualquier pantalla futura que liste `Venta` desde `venta_local`. No bloquea; documentar en `MOBILE_DATA_MAPPING.md §10` sigue pendiente |

---

## 8. Entorno de desarrollo: Windows, sin Mac

El equipo desarrolla en **Windows**. Los targets iOS de Kotlin/Native **solo compilan en macOS** (necesitan
la toolchain de Xcode; es restricción de Apple, no de Kotlin). Consecuencias permanentes del proyecto:

- Los cuatro targets (`androidTarget`, `jvm`, `iosArm64`, `iosSimulatorArm64`) se declaran **siempre** en
  `shared/build.gradle.kts`, sin condicionales por host. El código de `commonMain` tiene que ser
  multiplataforma correcto desde el día 1. `iosX64` (Mac Intel) se retiró en la Fase 7: Compose
  Multiplatform 1.12.0 no publica esa variante para sus artefactos.
- ❌ **`commonMain` nunca importa `java.*` ni `javax.*`.** Compilaría local y rompería en iOS sin que nadie
  se entere hasta meses después. Ante la duda, buscá la alternativa de `kotlin.*` o `kotlinx.*`, o hacelo
  `expect`/`actual`.
- ❌ No intentes correr tareas `*Ios*` localmente ni las declares condicionalmente.
- ✅ La verificación de iOS corre en **CI con runner macOS** (`.github/workflows/verificacion-ios.yml`).
  **Ninguna fase se cierra sin que ese workflow pase.**
- `iosApp/.xcodeproj` no existe todavía; `iosApp/README.md` documenta qué falta hacer en una Mac.

## 9. Comandos

```bash
# Ciclo normal de desarrollo (Windows)
./gradlew :shared:jvmTest             # tests rápidos en JVM
./gradlew :shared:assemble            # compila shared (targets disponibles en este host)
./gradlew :androidApp:assembleDebug   # APK de debug

# NO correr en Windows — fallan por falta de toolchain de Xcode:
# ./gradlew build                      (intenta todos los targets, incluidos los de iOS)
# ./gradlew :shared:allTests
# ./gradlew :shared:iosSimulatorArm64Test
```

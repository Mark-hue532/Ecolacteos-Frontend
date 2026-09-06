# Prompt — Fase 7: Primera funcionalidad vertical (Venta) + primera UI real

> Alcance fijado por `MOBILE_DATA_MAPPING.md §13`: **"Primera funcionalidad vertical de bajo riesgo
> (candidata: Venta — sin dependencia cruzada de ids, offline-first simple, ver DATA-002/DATA-010 como los
> dos puntos a validar primero). PRIMERA FASE CON UI: aquí entra `MOBILE_SCREENS.md` (pantallas S-01..S-04
> + V-01..V-03)"**.
>
> Es la primera fase que dibuja algo. El criterio de "terminado" no es "se ve lindo" — es: *un VENTAS
> abre la app sin señal, registra una venta, la ve aparecer con su badge de pendiente, cierra la app, y
> cuando vuelve la señal la venta se sube sola y el badge desaparece — sin que ninguna línea de la UI haya
> tocado un `Repository`, un `SyncEngine` ni un `HttpClient`.*

## 0. Contexto que esta fase hereda (no lo repitas, pero diseñá sabiendo esto)

**Lo que ya está construido y esta fase solo consume.** Las Fases 1–6 dejaron la API pública completa:
7 Repository, 15 UseCase, `SyncEngine` real con backoff y `PENDING_DEPENDENCY`, SQLDelight con sus
`ColumnAdapter` de `BigDecimal`/fechas, `GestorSesion` sobre `SecureTokenStorage`, y el wiring de Koin
armado en `di/` (`CoreModule`, `NetworkModule`, `SecurityModule`, `LocalModule`, `SyncModule`,
`RepositoryModule`, `UseCaseModule`) con su entrada real desde `androidApp/MainActivity.kt` e
`IosPlatformModule.kt`.

**No asumas ninguna firma.** Este prompt nombra archivos y responsabilidades, no signaturas. Antes de
escribir un `ViewModel`, abrí el `UseCase` que va a llamar y leé su firma real. Si un UseCase no expone lo
que la pantalla necesita, **se agrega el UseCase que falta** (sección 2.4) — no se salta la capa
(`CLAUDE.md §3.4`).

**Los dos directorios donde vas a trabajar están vacíos.** `shared/src/commonMain/kotlin/.../presentation/`
(con sus 6 subcarpetas por rol) y `.../ui/` (`components/`, `screens/`, `theme/`, `navigation/`) existen
desde la Fase 1 pero contienen solo `.gitkeep`. La estructura ya está decidida en
`MOBILE_ARCHITECTURE.md §15`: respetala, no inventes una nueva.

**⚠️ Esta es la primera fase que toca el build.** Compose Multiplatform no está aplicado en ningún módulo
todavía. Ver sección 6 — es el riesgo técnico #1 de la fase y lo primero que tenés que resolver, antes de
escribir una sola pantalla.

**`DATA-014` no afecta a esta fase.** Venta no tiene dependencia cruzada de ids: se captura, se encola, se
sube. Por eso `MOBILE_DATA_MAPPING.md §13` la eligió como vertical de estreno y no `RegistroAcopio`. Los
dos hallazgos que **sí** tenés que validar acá son `DATA-002` (BigDecimal de punta a punta, ahora también
en el formateo de UI) y `DATA-010` (`tipoCliente` → 500). Ver secciones 7 y 8.

## 1. Qué leer antes de empezar (no releas los documentos enteros)

| Documento | Sección | Por qué |
|---|---|---|
| `MOBILE_SCREENS.md` | §3 completo (3.1 a 3.4) | El contrato obligatorio de la capa: `UiState`/`Event`/`Effect`, el envoltorio `Async<T>`, la regla dura de dependencias y los borradores de formulario. Es la sección más importante de esta fase |
| `MOBILE_SCREENS.md` | §2 y §2.1 completos | El grafo de navegación y las 6 reglas — en particular la #1 (Home es la raíz del back stack), la #2 (los formularios son destinos, no diálogos) y la #3 (guardar navega **hacia atrás**, con `Snackbar`, sin pantalla de confirmación) |
| `MOBILE_SCREENS.md` | §4, entradas `S-01`, `S-02`, `S-03`, `S-04` | Las 4 pantallas comunes de esta fase, con su `UiState` ya escrito en el documento. **No lo rediseñes**: si un campo está ahí, va |
| `MOBILE_SCREENS.md` | §8 completo (`V-01`, `V-02`, `V-03`) | La vertical de Venta, incluido el recuadro de `DATA-010` y la regla del `total` |
| `MOBILE_SCREENS.md` | §10 completo | Las reglas transversales: §10.1 escalas de formateo, §10.2 formatos de fecha, §10.3 marcos temporales, §10.4 mapeo de errores HTTP → mensaje, §10.5 indicadores de sync, §10.6 los cuatro estados obligatorios |
| `MOBILE_SCREENS.md` | §11 | Las mitigaciones obligatorias derivadas de la auditoría. De la tabla, aplican a esta fase: `DATA-010`, `DATA-001`/`DATA-012`, `DATA-002`, nullability y "sin paginación" |
| `MOBILE_SCREENS.md` | §13 | Los 11 componentes reutilizables y en qué pantallas se usan. Esta fase construye los que sus 7 pantallas necesitan (sección 2.3), no los 11 |
| `MOBILE_SCREENS.md` | §15 | Accesibilidad e idioma: tamaños de toque para uso con guantes, español, contraste bajo sol |
| `MOBILE_SCREENS.md` | §17 completo | Qué se prueba de la capa de presentación y con qué. Es la base de la sección 9 de este prompt |
| `MOBILE_SCREENS.md` | §18, filas `S-01`..`S-04` y `V-01`..`V-03` | Trazabilidad pantalla ↔ endpoint ↔ tabla. **Ojo con la fila `V-01`**: contradice a §8 — ver sección 12 |
| `MOBILE_ARCHITECTURE.md` | §15 | La estructura de carpetas exacta de `presentation/` y `ui/`. Ya está decidida |
| `MOBILE_ARCHITECTURE.md` | §16.1 y §16.4 | El flujo de creación offline (la UI observa el `Flow` de SQLite y el registro aparece de inmediato con badge) y el de lectura offline |
| `MOBILE_ARCHITECTURE.md` | §4 completo | Autenticación: qué se guarda al login, la expiración local, el refresh oportunista y la política de logout. Es el contrato de `S-01` y `S-02` |
| `MOBILE_DATA_MAPPING.md` | §5.5 (Venta) completo | Los 6 campos del `VentaRequest` con sus validaciones exactas, y el `VentaResponse` con `total` read-only |
| `MOBILE_DATA_MAPPING.md` | Entradas `DATA-010` y `DATA-002` completas (§10) | Texto exacto de los dos hallazgos que esta vertical existe para validar |
| `CLAUDE.md` | §3.1, §3.4, §3.5, §4, §6, §8 | Decimales, capas, "todo lo compartible vive en `shared/`", el toolchain fijado, el estilo de trabajo y la restricción de Windows-sin-Mac |

## 2. Alcance — qué SÍ se construye en esta fase

### 2.1 Infraestructura de Compose Multiplatform (primero que todo)

- Aplicar Compose Multiplatform y el plugin de compilador de Compose en `shared/`, y lo mínimo en
  `androidApp/` para montar la raíz. Ver sección 6: **las dependencias son una decisión, no un detalle**.
- `shared/ui/theme/` — colores, tipografía y espaciado mínimos y funcionales. `MOBILE_SCREENS.md §14` dice
  explícitamente que la capa visual la entrega diseño y todavía no existe: construí un tema sobrio y
  legible que cumpla §15 (contraste alto, toques grandes), no un design system completo.
- `shared/ui/navigation/` — el grafo con los 7 destinos de esta fase y las reglas de §2.1.
- Un `@Composable` raíz en `shared/` y su punto de montaje en `androidApp/`. Para iOS, la función de
  entrada que `iosApp` va a consumir cuando exista el `.xcodeproj` (hoy no existe, `CLAUDE.md §8`):
  **tiene que compilar y linkear**, aunque nadie pueda correrla todavía.

### 2.2 ViewModels (`shared/presentation/`)

Un `ViewModel` por pantalla, con sus tres tipos, siguiendo `MOBILE_SCREENS.md §3.1` al pie de la letra:

| Carpeta | ViewModel | Pantalla |
|---|---|---|
| `presentation/comun/` | Bootstrap / Splash | `S-01` |
| `presentation/comun/` | Login | `S-02` |
| `presentation/comun/` | Home | `S-03` |
| `presentation/comun/` | EstadoSincronizacion | `S-04` |
| `presentation/ventas/` | HomeVentas | `V-01` |
| `presentation/ventas/` | RegistrarVenta | `V-02` |
| `presentation/ventas/` | DetalleVenta | `V-03` |

Más los tipos compartidos de presentación, que se escriben **una sola vez** y los usan todas las pantallas
de esta fase y las 26 de la Fase 8:

- El mapeo de `ApiError`/`ErrorDominio` → mensaje + reintentabilidad de `MOBILE_SCREENS.md §10.4`.
- Los formateadores de decimales por escala (§10.1) y de fechas (§10.2), operando desde `BigDecimal` de
  `bignum`, **nunca** pasando por `Double`. Revisá primero `core/Decimal.kt` y `core/FechaUtils.kt`: parte
  de esto puede existir ya desde la Fase 1 y no hay que duplicarlo.
- `core/Async.kt` **ya existe**. Leelo antes de escribir nada: si ya es el `Async<T>` de §3.2, se reutiliza
  tal cual; si le falta algo (`desdeCache`, `datosPrevios`), se completa ahí, no se crea un tipo paralelo.

### 2.3 Componentes reutilizables (`shared/ui/components/`)

De los 11 de `MOBILE_SCREENS.md §13`, esta fase construye los 10 que sus pantallas usan: `CampoDecimal`,
`SelectorCatalogo`, `IndicadorSync`, `BadgeEstadoSync`, `BannerSinConexion`, `BloqueoOnlineOnly`,
`EstadoVacio`, `EstadoError`, `FechaEtiquetada` y `BotonAccionPrincipal`. `DialogoConfirmacion` queda para
la Fase 8 (sus tres consumidores — `S-05`, `S-07`, `C-06` — están fuera de esta fase); si te resulta más
barato hacerlo ahora, hacelo, pero decilo en el checkpoint.

> Nota sobre `BloqueoOnlineOnly`: la columna "Usado en" de §13 no lista `S-02`, pero `S-02` **es**
> online-only (§4) y §10.6 punto 4 exige bloqueo con explicación para ese caso. Construilo acá y usalo en
> `S-02`; es una omisión de la tabla, no una decisión de diseño.

Se construyen **como componentes**, no inline dentro de una pantalla. El objetivo declarado de §13 es que
las 26 pantallas de la Fase 8 los reciban hechos.

### 2.4 UseCases que faltan

La Fase 6 construyó 15 UseCases, pero ninguno pensado para estas pantallas concretas. Reutilizá los que
sirven (`CrearVentaUseCase`, `ObservarCatalogosUseCase`, `ObservarPendientesUseCase`,
`ReintentarManualUseCase`, `LogoutUseCase`, `VerificarPendientesUseCase`) y agregá los que falten. Como
mínimo, revisá si existe algo que cubra:

- **Login** (`S-02`) — hoy hay `GestorSesion`/`GestorSesionImpl` pero **no hay `LoginUseCase`**. Un
  `ViewModel` no puede llamar a `GestorSesion` salteando la capa de UseCase: o se crea el UseCase, o
  documentás por qué `GestorSesion` cuenta como capa de dominio. Decidilo explícitamente, no por omisión.
- **Decisión de arranque** (`S-01`) — token presente / expirado / por vencer, sin bloquear en red.
- **Ventas del día** (`V-01`) y **detalle de una venta** (`V-03`).
- **Resumen de sync por recurso** (`S-03`, `S-04`) — `ObservarPendientesUseCase` puede alcanzar; verificá
  si expone el desglose por recurso y por estado que `S-04` necesita.
- **Sincronizar ahora** (`S-04`, evento `SincronizarAhoraPresionado`) — un ciclo forzado, expuesto como
  UseCase; la UI no llama a `SyncEngine`.

### 2.5 Borradores de formulario (`MOBILE_SCREENS.md §3.4`)

`V-02` es uno de los 5 formularios de captura, así que le aplica la persistencia de borrador. **La tabla
`borrador_formulario` no existe en el esquema de la Fase 4** — no hay ningún `.sq` para ella. Tenés dos
caminos y hay que elegir uno explícitamente en el checkpoint:

1. Implementarla ahora (nuevo `.sq` + data source + persistencia con debounce ~500 ms), dejando el
   mecanismo listo para los otros 4 formularios de la Fase 8.
2. Diferirla a la Fase 8, dejando `V-02` sin borrador en esta fase y anotándolo como deuda explícita.

**Recomendación: la opción 1.** Es una tabla trivial (3 columnas) y hacerla acá significa que la Fase 8
hereda el patrón resuelto en vez de repetirlo cinco veces. Pero es una decisión con costo, así que
decidila a conciencia y no la implementes en silencio si elegís lo contrario.

Recordá la regla de §3.4: **un borrador no es un registro pendiente**. No aparece en `S-05`, no se
sincroniza y no cuenta en ningún contador.

## 3. Alcance — qué NO se construye en esta fase

- **Las otras 26 pantallas.** `S-05`, `S-06`, `S-07` y todo `A-`, `C-`, `P-`, `R-` son Fase 8, aunque
  algunas sean triviales y estés tentado. La navegación puede dejar el destino declarado y sin pantalla,
  pero no inventes media pantalla "de paso".
- **Background sync** (`WorkManager`/`BGTaskScheduler`) — Fase 9. Los disparadores siguen siendo los de la
  Fase 5/6: `solicitarSyncOportunista()` tras un `crear()`, la reconexión, y ahora el botón de `S-04`.
- **Compose UI tests.** `MOBILE_SCREENS.md §17` los reserva para los 11 componentes y las 5 pantallas de
  captura, pero esta fase ya arrastra el riesgo de estrenar Compose en el build (sección 6): sumarle un
  runtime de UI test en tres plataformas es demasiado para una sola fase. Los `ViewModel` se prueban
  enteros en `commonTest` (sección 9), y los tests de UI se planifican en la Fase 10. **Si discrepás,
  decilo en el checkpoint en vez de agregarlos por tu cuenta.**
- **`iosApp/.xcodeproj`.** No hay Mac (`CLAUDE.md §8`). El framework tiene que linkear en CI; la app de
  iOS no se arma en esta fase.
- **Diseño visual definitivo.** Ver 2.1.
- **i18n / localización.** `MOBILE_SCREENS.md §15`: español, y el separador decimal es punto, no coma
  (§10.1 regla 4).
- **El `total` calculado en el cliente.** Ver sección 8.

## 4. El contrato de presentación (no negociable)

Todo esto sale de `MOBILE_SCREENS.md §3` y se repite acá porque es el corazón de la fase:

1. **Tres tipos por pantalla, siempre los mismos tres**: `XxxUiState` (inmutable, con *todo* lo que la
   pantalla dibuja), `XxxEvent` (lo que el usuario hace) y `XxxEffect` (acciones de una sola vez).
2. **`Effect` nunca es un campo del `UiState`.** Se emite por `Channel`/`SharedFlow` y se consume
   exactamente una vez. Un booleano `navegar` en el estado se vuelve a disparar al rotar la pantalla o al
   volver del background: es el error clásico que este contrato existe para prevenir.
3. **La UI solo emite eventos.** Un `@Composable` no llama a un `UseCase`, no inyecta un `Repository`, no
   ve un `HttpClient` ni un DTO.
4. **El `UiState` no lleva `BigDecimal` para mostrar**: lleva el `String` ya formateado con su escala. Si
   además hace falta operar, el `BigDecimal` va aparte. Formatear dentro del `@Composable` disemina la
   lógica de escala por toda la UI.
5. **El `ViewModel` vive en `shared/`**, no en `androidApp/`. `CLAUDE.md §3.5` es explícito: un `ViewModel`
   en `androidApp/` obligaría a reescribir toda la presentación en Swift.
6. **Los cuatro estados de §10.6 son obligatorios en toda pantalla que muestre datos**: cargando, vacío,
   error y sin conexión. Una pantalla sin sus cuatro estados definidos no está terminada. En particular:
   nunca un spinner a pantalla completa tapando datos que ya tenés en cache, y nunca una pantalla de error
   entera tapando datos válidos — para eso están `desdeCache` y `datosPrevios` de `Async`.

## 5. Las 7 pantallas — lo que no podés inventar

`MOBILE_SCREENS.md` ya trae el `UiState` escrito para `S-02` y `S-03`. Usalos. Para el resto, respetá al
menos esto:

- **`S-01` Splash**: decide sin preguntar nada. **Nunca bloquea esperando red** — un VENTAS sin señal llega
  a Home igual de rápido que con señal. El refresh proactivo (< 30 min para vencer y con conexión) es
  oportunista, silencioso y en background. Máximo ~800 ms antes de mostrar indicador.
- **`S-02` Login**: **ONLINE-ONLY** — sin conexión el botón se deshabilita con el texto de §4, no se
  intenta la llamada para no hacer esperar 30 s a un timeout inevitable. Un 401 dice "Correo o contraseña
  incorrectos": **nunca** se distingue cuál de los dos falló. La contraseña no se guarda jamás, ni cifrada.
  Al éxito se navega a `S-03` **limpiando el back stack**. Y el caso crítico de C-09: si hay filas locales
  de **otro** `usuario_id`, no se borran ni se muestran — quedan invisibles, se registra en el log local,
  no en la UI.
- **`S-03` Home**: la acción principal del rol es un botón grande y **primero en el orden de lectura**
  (para VENTAS: registrar venta). El estado de sync siempre visible. Sin conexión → banner discreto y
  persistente, **no un diálogo**. El único caso donde la app no puede operar offline es catálogos vacíos en
  el primer arranque: ahí sí, estado vacío explícito con botón sincronizar, dicho sin rodeos.
- **`S-04` Estado de sincronización**: desglose **por recurso**, no una barra global indeterminada — el
  usuario quiere saber *qué* se está subiendo. "Todo al día" es un estado vacío **positivo**. "N registros
  esperando señal" **no** es un error.
- **`V-01` Home ventas**: ventas del día con total y estado de sync. Sin paginación, sin scroll infinito,
  sin "cargar más" (`CLAUDE.md §3.3`). Ver la contradicción de la sección 12 antes de decidir la fuente.
- **`V-02` Registrar venta ★**: la pantalla central de la fase. Destino de pantalla completa, no diálogo.
  Al guardar: vuelve **hacia atrás** con la fila ya visible y un `Snackbar` "Guardado — se enviará cuando
  haya señal". Sin pantalla de confirmación intermedia. Ver secciones 7 y 8.
- **`V-03` Detalle de venta**: muestra el `total` **tal cual lo devolvió el servidor**, sin recalcular
  nunca.

## 6. Dependencias nuevas — decisión obligatoria ANTES de escribir código

`CLAUDE.md §4` fija el toolchain y dice "no las cambies sin avisar"; `CLAUDE.md §6` dice "no agregues
dependencias que no estén en §4 sin justificarlo en el checkpoint". Esta fase no puede cumplirse sin tocar
eso, así que la decisión es parte del trabajo:

**Estado real hoy** (verificalo vos mismo, no lo tomes de acá):

- `gradle/libs.versions.toml` declara `composeMultiplatform = "1.12.0"` y `lifecycle = "2.11.0"` en
  `[versions]`, pero **ninguna de las dos tiene entrada en `[libraries]` ni en `[plugins]`**, y ningún
  módulo las aplica. Son versiones reservadas, no dependencias activas.
- `shared/build.gradle.kts` aplica `kotlinMultiplatform`, `kotlinSerialization`, `androidLibrary` y
  `sqldelight`. **No hay plugin de Compose ni de compilador de Compose.**
- `androidApp/build.gradle.kts` es un módulo Android hoja normal, sin Compose y sin `activity-compose`.
- **Navigation para Compose Multiplatform y `koin-compose` no están declarados en ningún lado.**

**Lo que tenés que decidir y justificar en el checkpoint:**

1. Qué plugins de Compose aplicar y en qué módulos (Kotlin 2.x necesita el plugin de compilador de Compose
   además del de Compose Multiplatform).
2. `androidx.lifecycle` 2.11.0 para el `ViewModel` multiplataforma: qué artefactos exactos.
3. **Navegación**: agregar la librería de navegación de Compose Multiplatform (dependencia nueva, fuera de
   `CLAUDE.md §4`) o resolver la navegación a mano para 7 destinos. Ambas son defendibles; elegí una y
   decí por qué. Recordá §2.1 regla 1 (Home como raíz del back stack) y regla 6 (al morir el proceso se
   reabre Home, no la última pantalla).
4. **Inyección en Compose**: `koin-compose` (dependencia nueva) o pasar los `ViewModel` desde la raíz.
5. `activity-compose` en `androidApp` para montar la raíz.

> ### ⚠️ Riesgo técnico #1: Compose sobre AGP 9.4.0
>
> `gradle.properties` usa `android.newDsl=false` + `android.builtInKotlin=false` como bypass documentado
> para que `org.jetbrains.kotlin.multiplatform` y `com.android.library` convivan en `shared/` bajo AGP 9.x
> (decisión de la Fase 1). El build **ya emite** el warning `"The 'org.jetbrains.kotlin.multiplatform'
> plugin is not compatible with 'com.android.library' starting with Android Gradle Plugin 9.0.0"`.
>
> Agregar Compose encima de esa combinación es exactamente donde esto puede romperse. **Si se rompe, no
> "actualices a la versión que funcione"** (`CLAUDE.md §4`): parás, documentás qué falló con el error
> exacto, y proponés las opciones (migrar a `com.android.kotlin.multiplatform.library`, mover la versión de
> AGP, o lo que corresponda) para que se apruebe. Una migración de plugin de Android **no** es una decisión
> que esta fase pueda tomar sola.
>
> Empezá por acá: aplicá Compose con **una** pantalla trivial y verificá que `:shared:assemble`,
> `:androidApp:assembleDebug` y `compileKotlinIos*` siguen en verde **antes** de escribir las 7 pantallas.
> Descubrir esto con 40 archivos nuevos encima es mucho más caro.

## 7. `DATA-010` a través de la UI (no negociable)

`tipoCliente` viaja como `String` y el backend hace `TipoClienteVenta.valueOf(...)` a mano, sin
`@ExceptionHandler` que lo capture. Un valor que no sea **exactamente** `MAYORISTA`, `PROVEEDOR` o
`PUBLICO` provoca **500 Internal Server Error**, no un 400.

Por lo tanto, en `V-02`:

- El campo se implementa como **selector cerrado de exactamente 3 opciones**. Nunca texto libre, nunca
  autocompletado, nunca un valor por defecto escrito a mano en un `String`.
- El enum `TipoClienteVenta` ya existe en `data/remote/dto/`. La UI no importa DTOs (`CLAUDE.md §3.4`), así
  que si el modelo de dominio no tiene su propio tipo cerrado para esto, resolvelo — y decí cómo.
- La Fase 6 ya puso la primera línea de defensa en `CrearVentaUseCase` (test 8 de `PROMPT_FASE_06.md §9`).
  Esta fase agrega la segunda, que es la que realmente evita el 500 en producción: **que el valor inválido
  no se pueda ni tipear**.

Es la única mitigación de `MOBILE_SCREENS.md §11` que es responsabilidad exclusiva de la UI, y por eso está
en su propia sección.

## 8. Decimales, total y fechas

**`DATA-002` en la capa de UI.** Del `BigDecimal` de `bignum` al `String` directamente. Nunca un `Double`,
ni siquiera "solo para mostrar". Las escalas que aplican a esta fase (`MOBILE_SCREENS.md §10.1`):

| Campo | Escala | Ejemplo |
|---|---|---|
| `precioUnitario`, `total` (Venta) | 2 | `S/ 18.00` |

Y la regla 3, que aplica a todas las pantallas: **un `null` nunca se muestra como `0`**. Se omite el campo
o se escribe "No disponible" — son afirmaciones distintas.

**El `total` no se calcula ni se muestra antes de guardar.** Es una columna `GENERATED ALWAYS` de Postgres
(`MOBILE_DATA_MAPPING.md §5.5`): el servidor la calcula y la relee. Un `cantidad × precioUnitario` local
con otro redondeo puede mostrar un centavo de diferencia contra el valor real y minar la confianza en las
cifras — que es exactamente lo que `DATA-002` existe para evitar. Se puede mostrar un **subtotal estimado**
claramente etiquetado como referencia; jamás rotulado "Total".

**Validaciones de `V-02`** (`MOBILE_DATA_MAPPING.md §5.5`): `fecha` obligatoria; `tipoCliente` selector de
3; `tipoQuesoId` desde `tipo_queso_cache`; `cantidad` entero **`>= 1`** (no 0 — es `@Min(1)`);
`precioUnitario` `>= 0` con `precision=8, scale=2`.

**Fechas (`MOBILE_SCREENS.md §10.2` y §10.3).** `LocalDate` → `dd/MM/yyyy`. `LocalDateTime` →
`dd/MM/yyyy HH:mm`, **mostrado tal cual llega**, sin convertir a la zona del dispositivo. Y mientras
`DATA-001` siga abierto está **prohibido** comparar, restar u ordenar mezclando `fechaHora` (la genera el
dispositivo) con `creadoEn`/`sincronizadoEn` (las genera el servidor). Filtrar "las ventas de hoy" en
`V-01` usa **solo** `fecha`/`fechaHora`, nunca un timestamp de servidor.

## 9. Testing requerido

Todo en `commonTest`, corriendo en JVM sin emulador (`MOBILE_SCREENS.md §17`, `CLAUDE.md §6`), con
Turbine 1.2.1 para los `Flow` y los fakes que ya dejaron las Fases 5 y 6 (`FakesDeSync.kt`,
`FixtureDeSync.kt`, `FixtureRepositorios.kt` — reutilizalos, no escribas fixtures paralelos):

1. **Transición completa de `V-02`**: evento por evento hasta guardar; el estado intermedio (`guardando`)
   se emite y se limpia; el `UiState` final refleja la venta creada.
2. **Validación `cantidad = 0`**: inválida, con el mensaje correspondiente y el botón de guardar
   deshabilitado. `cantidad = 1` válida (el borde exacto de `@Min(1)`).
3. **`DATA-010`**: el `UiState` de `V-02` no admite representar un `tipoCliente` fuera de los 3 valores —
   el test debe fallar a nivel de tipo o de validación, nunca depender de que el usuario "no escriba mal".
4. **`Effect` de una sola vez**: el efecto de navegación tras guardar se consume exactamente una vez y no
   se reemite al recrear el `ViewModel`.
5. **Offline-first de `V-02`**: con `ConnectivityObserver` en `false`, guardar **funciona** y la venta
   aparece en el `Flow` con estado pendiente. Con `S-02` (online-only) y el mismo observer en `false`, la
   pantalla se **bloquea** con el mensaje de §10.4 y no se intenta la llamada.
6. **Aparición optimista**: crear una venta y ver que llega por el `Flow` de `V-01` sin volver a consultar
   a mano (`MOBILE_ARCHITECTURE.md §16.1`), con su badge; tras un ciclo de sync exitoso, el badge
   desaparece por el mismo `Flow`.
7. **Formateo (`DATA-002`)**: `precioUnitario` y `total` con exactamente 2 decimales desde `BigDecimal`,
   sin pasar por `Double`; un valor nulo no se formatea como `0`.
8. **`total` no calculado**: antes de guardar, el `UiState` no expone ningún campo llamado "total"
   proveniente de un cálculo local; lo que muestre `V-03` es el valor del servidor tal cual.
9. **Mapeo de errores (§10.4)**: 401 → cierre de sesión y mensaje de sesión vencida; 400/422 → el
   `mensaje` del backend **literal**; 5xx/timeout → mensaje reintentable. Uno por fila relevante de la
   tabla.
10. **Regla de fechas (§10.3)**: el orden de la lista de `V-01` usa solo el campo del marco temporal del
    dispositivo; ningún cálculo mezcla marcos.
11. **`S-01` no bloquea en red**: con el observer en `false` y un token válido, el bootstrap llega a Home
    sin esperar ninguna llamada.
12. **Borrador de `V-02`** — solo si elegís la opción 1 de la sección 2.5: se persiste, sobrevive a la
    recreación del `ViewModel`, se borra al guardar, y **no** aparece como pendiente en ningún contador.

Reportá el conteo total leyendo `shared/build/test-results/jvmTest/` (no de memoria), igual que en los
checkpoints de las Fases 5 y 6.

## 10. Trampas conocidas

| # | Trampa | Por qué importa |
|---|---|---|
| 1 | Escribir las 7 pantallas y recién después verificar que Compose compila en los 4 targets | Sección 6. Si el plugin rompe sobre AGP 9.x, querés saberlo con 1 archivo nuevo, no con 40 |
| 2 | Un `@Composable` que inyecta un `UseCase` o un `Repository` "porque es una pantalla chiquita" | `CLAUDE.md §3.4` y `MOBILE_SCREENS.md §3.3`. La excepción de hoy es el patrón de mañana en 33 pantallas |
| 3 | Poner el `ViewModel` en `androidApp/` | `CLAUDE.md §3.5`: obliga a reescribir toda la presentación en Swift. Es un error de diseño, no una simplificación |
| 4 | `Effect` de navegación modelado como campo booleano del `UiState` | §3.1: se redispara al rotar o al volver del background. El documento lo llama "error clásico" por algo |
| 5 | Formatear decimales dentro del `@Composable`, o pasando por `Double` para "redondear" | `DATA-002` + §10.1 regla 2. Es la regla más cara de romper de todo el proyecto |
| 6 | Mostrar el `total` calculado localmente en `V-02` | Sección 8. Un centavo de diferencia contra Postgres mina la confianza en todas las cifras |
| 7 | `tipoCliente` como texto libre, o con un default escrito a mano | `DATA-010` → 500 en producción. Sección 7 |
| 8 | Spinner a pantalla completa tapando datos que ya están en cache | §10.6 punto 1 y el `desdeCache`/`datosPrevios` de `Async`. Es el antipatrón que §3.2 existe para prevenir |
| 9 | Pintar `PENDING_DEPENDENCY` con estética de error en `S-04` o en un badge | §10.5: es una espera legítima del diseño. En rojo, el usuario intenta "arreglar" algo que se resuelve solo |
| 10 | Un 401 que distingue si falló el mail o la contraseña | `S-02`: es una fuga de enumeración de usuarios, además de estar prohibido por el documento |
| 11 | Splash que espera la red para decidir a dónde entrar | `S-01`: un VENTAS sin señal debe llegar a Home igual de rápido. El refresh es oportunista |
| 12 | Reemplazar el `mensaje` del backend por un texto genérico en un 400/422 | §10.4: lo escribe el Service, en español, y suele ser accionable. Genericarlo destruye información útil |
| 13 | Agregar scroll infinito o "cargar más" en `V-01` | `CLAUDE.md §3.3`: **no existe paginación en ningún endpoint MOBILE**. La UI no promete lo que el backend no tiene |
| 14 | Borrar filas locales de otro `usuario_id` al loguearse | `S-02` caso crítico + `CLAUDE.md §3.6`: el trabajo no confirmado no se borra nunca |
| 15 | `commonMain` importando `java.*` para formatear (`DecimalFormat`, `SimpleDateFormat`) | `CLAUDE.md §8`: compila en Windows y rompe en iOS meses después. Es la trampa más fácil de pisar formateando |
| 16 | Nombres de test con coma | Ilegales en Kotlin/Native: rompen `compileTestKotlinIos*`. Ya pasó en las Fases 2 y 6 |

## 11. Criterios de aceptación

**Verificable localmente en Windows** (lo único que el checkpoint puede citar como "pasó"):

- `./gradlew :shared:assemble :androidApp:assembleDebug` → `BUILD SUCCESSFUL`, ya con Compose aplicado.
- `./gradlew :shared:jvmTest :androidApp:testDebugUnitTest` → todos verdes, incluyendo los tests de la
  sección 9, con el conteo leído de `shared/build/test-results/jvmTest/`.
- `compileKotlinIos*` / `compileTestKotlinIos*` en verde — evidencia de tipos y firmas `expect`/`actual`,
  nada más (`CLAUDE.md §8`).
- Sin regresiones: la suite de las Fases 1–6 sigue entera en verde (222 tests, 0 fallos, según el último
  checkpoint de la Fase 6 — confirmá el número contra el XML antes de citarlo).

**No verificable en Windows — requiere CI macOS** (`verificacion-ios.yml`):

- `linkDebugFrameworkIosSimulatorArm64` — **es la primera vez que el framework de iOS incluye Compose**.
  Que compile no garantiza que linkee. No lo des por bueno.
- `iosSimulatorArm64Test` real.

`CLAUDE.md §8` es explícito: **ninguna fase se cierra sin que `verificacion-ios.yml` pase.** No mezcles lo
verificado en Windows con lo que espera CI en el resumen del checkpoint.

## 12. Contradicciones a resolver (no las resuelvas en silencio)

1. **`V-01`, fuente de datos.** `MOBILE_SCREENS.md §8` dice *"Modo offline OK · Fuente `venta_local`"*; la
   tabla de trazabilidad §18 dice *"`GET /api/ventas` · `venta_local` · online+cache"*. Son dos pantallas
   distintas. La regla de precedencia de `CLAUDE.md §2` no ayuda acá porque ambas están en el mismo
   documento. Elegí una, decí cuál y por qué, y anotá la corrección que habría que hacerle al documento.
   (Criterio sugerido: un VENTAS mirando lo que acaba de cargar en el día necesita que funcione sin señal.)
2. **`borrador_formulario` no existe en el esquema.** Sección 2.5.
3. **No hay `LoginUseCase`.** Sección 2.4.

Si aparece cualquier otra, va al checkpoint con el mismo formato de `MOBILE_DATA_MAPPING.md §10` si es un
`DATA-0xx` nuevo.

## 13. Formato del checkpoint esperado

El de `CLAUDE.md §5`, mismo que las Fases 4, 5 y 6: qué se construyó, compila, tests con conteo leído de
archivo, regresiones, decisiones tomadas (acá son varias y pesadas — las dependencias nuevas de la sección
6, la contradicción de `V-01`, el borrador, el `LoginUseCase`), problemas encontrados, `DATA-0xx` nuevos si
aparecen, si requiere cambio de backend, y qué falta para la fase siguiente. Más el estado de iOS con la
distinción de `CLAUDE.md §8`, que en esta fase importa más que nunca porque el framework estrena Compose.

Detenete ahí y esperá aprobación explícita antes de la Fase 8.

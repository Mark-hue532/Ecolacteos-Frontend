# Prompt — Fase 8A: ACOPIADOR (captura en campo)

> Primera de las cinco sub-fases de la Fase 8. Leé antes `PROMPT_FASE_08.md` (el plan): sus secciones §4
> (reglas comunes), §6 (criterios de aceptación) y §7 (checkpoint) **aplican a esta sub-fase y no se
> repiten acá**.
>
> Alcance: las 6 pantallas de captura de ACOPIADOR — `A-01` Ruta, `A-02` Escanear QR, `A-03` Buscar
> proveedor, `A-04` Registrar acopio ★, `A-05` Historial, `A-06` Detalle — más las **dos capacidades de
> plataforma** que ninguna fase construyó todavía: cámara con lectura de QR, y ubicación.
>
> `A-07` (confirmar comunicado) **no** entra acá: va en `8B`, junto a `S-06`, porque su efecto visible es
> una marca en esa lista.
>
> El criterio de "terminado": *un ACOPIADOR camina la ruta del día sin señal, escanea el QR de un
> proveedor, registra 120.50 L con el GPS que haya (o sin ninguno), ve la entrega aparecer con su badge de
> pendiente, y repite eso cuarenta veces sin que la app le pida conexión ni una sola vez.*

## 0. Contexto que esta sub-fase hereda

**El andamio de la Fase 7 está entero y se reutiliza** (ver `PROMPT_FASE_08.md §2.4`): los 10 componentes,
`ErrorUi.kt`, `Formateadores.kt`, `Theme.kt`, la navegación, `PresentationModule.kt`, el patrón
`UiState`/`Event`/`Effect` de los 7 `ViewModel` ya escritos, y el patrón de borrador de formulario.
**Copiá ese patrón; no lo reinterpretes.** Antes de escribir el primer `ViewModel`, abrí
`presentation/ventas/RegistrarVentaViewModel.kt` — `A-04` es su hermano mayor y debería parecerse.

**No asumas ninguna firma.** Abrí cada `UseCase` antes de llamarlo. Si falta lo que la pantalla necesita,
se agrega el `UseCase` — no se salta la capa (`CLAUDE.md §3.4`).

**Los UseCases que esta sub-fase ya tiene**: `ObtenerRutaDelDiaUseCase` (`A-01`),
`ResolverProveedorPorQrUseCase` (`A-02`), `ObservarCatalogosUseCase` (unidades y motivos para `A-04`),
`CrearRegistroAcopioUseCase` (`A-04`), `ObservarHistorialProveedorUseCase` y
`ObtenerRegistrosDeProveedorUseCase` (`A-05`), `BorradorFormularioUseCase`, `ObservarConectividadUseCase`.

**Los que faltan y hay que crear**: buscar proveedor por nombre sobre `proveedor_cache` (`A-03`), detalle
de un registro de acopio (`A-06`), y el dato de "ya visitado hoy" para `A-01`.

**Esta es la última fase que agrega capacidades de plataforma nuevas.** Ver sección 3 — es el riesgo #1 y
lo primero que hay que resolver.

## 1. Qué leer antes de empezar

| Documento | Sección | Por qué |
|---|---|---|
| `MOBILE_SCREENS.md` | §5 completo (`A-01`..`A-06`) | Las 6 pantallas, con el `UiState` de `A-04` ya escrito. No lo rediseñes |
| `MOBILE_SCREENS.md` | §12 completo | Las reglas de solicitud de permisos: en contexto y nunca al arrancar, explicar antes de pedir, sin bucles ante denegación permanente, y que **ninguno de los dos es obligatorio para operar** |
| `MOBILE_SCREENS.md` | §3.4 | Borradores de formulario — aplica a `A-04` |
| `MOBILE_SCREENS.md` | §18, filas `A-01`..`A-06` | Trazabilidad pantalla ↔ endpoint ↔ tabla |
| `MOBILE_DATA_MAPPING.md` | §5.2 completo | El contrato de `RegistroAcopioDTO`, `RegistroAcopioResponse` y `RegistroAcopioResumenResponse`, con el `NAME_MISMATCH` de `motivoObservacion` y el `DATA-013` del resumen |
| `MOBILE_DATA_MAPPING.md` | §5.6 (Sync API — trae los catálogos, incluido `ProveedorPublicoResponse`) y §5.8 (Ruta de zona) | `codigoQr` nullable y `horaEstimada` nullable |
| `MOBILE_ARCHITECTURE.md` | §3.3 | Resolución de QR contra SQLite primero, red después |
| `MOBILE_ARCHITECTURE.md` | §16.1 | El flujo de creación offline de punta a punta — `A-04` lo implementa literal |
| `MOBILE_ARCHITECTURE.md` | §18.4, §18.5, §18.6 | Foto sin endpoint de subida; la ruta no viaja en `/sync/cambios`; `fotoUrl` ausente del Response |
| `CLAUDE.md` | §3.1, §3.4, §3.5, §4, §6, §8 | Decimales, capas, `shared/`, toolchain, estilo, Windows-sin-Mac |
| `PROMPT_FASE_08.md` | §4, §6, §7 | Reglas comunes, criterios de aceptación y formato del checkpoint |

## 2. Alcance

### Sí

- Las 6 pantallas `A-01`..`A-06` en `ui/screens/acopio/`, con sus `ViewModel` en `presentation/acopio/`.
- Las dos capacidades de plataforma de la sección 3, con sus `expect`/`actual`.
- Los 3 UseCases que faltan (§0).
- El borrador de formulario para `A-04`, reutilizando el mecanismo de la Fase 7.
- Registro de los nuevos `ViewModel` en `di/PresentationModule.kt` y de lo nuevo de plataforma en los
  módulos de Koin que correspondan.

### No

- `A-07` — va en `8B`.
- Cualquier pantalla de CALIDAD, PRODUCCION o RECEPCION, y `S-05`/`S-06`/`S-07`.
- **Evidencia fotográfica** (`DATA-008`): no hay botón de cámara para fotos en `A-04`. La cámara de esta
  sub-fase es **solo** para leer QR en `A-02`. Diferido a v2 por decisión de producto (`CLAUDE.md §7`).
- Edición de un registro de acopio: es inmutable en el dominio (`MOBILE_ARCHITECTURE.md §8`).
- La acción "Registrar corrección" que `A-06` ofrece al rol CALIDAD → esa pantalla (`C-06`) es de `8E`.
  Dejá el punto de entrada previsto, sin implementar el destino.
- Compose UI tests — siguen reservados para la Fase 10, igual que en la Fase 7.

## 3. Cámara/QR y ubicación — decisión obligatoria ANTES de escribir pantallas

Ninguna de las dos existe en el proyecto: ni permiso, ni preview, ni decodificador, ni proveedor de
posición. Y `CLAUDE.md §6` exige justificar toda dependencia nueva en el checkpoint. Así que esto es
trabajo de diseño, no de plomería.

### 3.1 La forma que sí o sí tiene que tener

Cualquiera sea la librería que elijas, **la abstracción va en `commonMain` y la implementación en
`androidMain`/`iosMain`**, con un `actual` de `jvmMain` que devuelva "no disponible" — exactamente el
patrón que la Fase 3 usó para `SecureTokenStorage` y la Fase 4 para `AcopioDriverFactory`.

Esto no es estética: **sin ese stub de JVM, `:shared:jvmTest` deja de correr**, y con él se acaba la
posibilidad de testear `A-02` y `A-04` sin emulador. Los `ViewModel` de esta sub-fase tienen que poder
probarse enteros en `commonTest` con un lector de QR falso y un proveedor de ubicación falso.

### 3.2 Lo que tenés que decidir y justificar

1. **Lector de QR.** Qué librería en Android, qué API en iOS (`AVFoundation` es lo nativo), y si existe
   algo multiplataforma que cubra las dos sin arrastrar peso. Es una dependencia nueva fuera de
   `CLAUDE.md §4`.
2. **Ubicación.** En Android, si vale la pena `play-services-location` (dependencia nueva, y ata la app a
   los servicios de Google) o alcanza el `LocationManager` de la plataforma (sin dependencia). En iOS,
   `CoreLocation` está disponible por cinterop sin agregar nada.
3. **Permisos.** Un solo mecanismo para los dos permisos, no dos ad-hoc: pedir, consultar estado, y
   distinguir "denegado" de "denegado permanentemente" (que es el caso que necesita el acceso a los
   ajustes del sistema, `MOBILE_SCREENS.md §12` regla 3).
4. **Los manifiestos y plists.** `androidApp/src/main/AndroidManifest.xml` necesita los permisos
   declarados; iOS necesita las claves de uso (`NSCameraUsageDescription`,
   `NSLocationWhenInUseUsageDescription`) — que hoy no tienen dónde ir, porque `iosApp/.xcodeproj` no
   existe (`CLAUDE.md §8`). **Documentá qué hay que agregar cuando ese proyecto exista**, en
   `iosApp/README.md`, en vez de dejarlo sin registrar.

> ### ⚠️ Riesgo #1: iOS no se puede verificar acá
>
> El decodificador de QR y el proveedor de ubicación de iOS solo se prueban en un simulador o dispositivo
> real. En Windows lo único que podés afirmar es que **compilan** (`compileKotlinIosArm64` /
> `compileKotlinIosSimulatorArm64` en verde) y que el framework enlaza en CI.
>
> Decilo así en el checkpoint, con la misma honestidad con que la Fase 3 documentó el Keychain
> (`CLAUDE.md §7`): *compilación verificada, comportamiento en runtime no verificado*. **No lo des por
> funcionando.** Y sumá la fila correspondiente a `CLAUDE.md §7` como pendiente conocido.
>
> Empezá por acá: dejá las dos capacidades compilando en los 4 targets con una pantalla mínima **antes**
> de escribir las 6 pantallas. Descubrir que la librería de QR no publica klib de iOS con 6 pantallas
> encima es el escenario caro.

## 4. Las 6 pantallas — lo que no podés inventar

### `A-01` Ruta del día — READ-CACHE

- Fuente `ruta_zona_cache`; endpoint `GET /api/zonas/{zonaId}/ruta`.
- `horaEstimada` es **nullable**: si viene nula **se omite**. Nunca `--:--`, nunca una hora inventada.
- Junto a cada proveedor, marca de "ya visitado hoy" — dato **local**, de `registro_acopio_local` del día.
- Vacío: *"No hay ruta definida para tu zona"*, **sin** ofrecer crearla: la ruta la define ADMIN desde el
  panel web.
- Sin conexión: se lee del cache con la marca de última descarga. **La ruta no viaja en `/sync/cambios`**
  (`§18.5`), así que hay que haberla abierto al menos una vez con señal — el estado vacío tiene que
  distinguir "no hay ruta" de "nunca la descargaste".

### `A-02` Escanear QR — OFFLINE REAL

El flujo es literal (`MOBILE_SCREENS.md §5`, `MOBILE_ARCHITECTURE.md §3.3`):

```text
QR escaneado
 → buscar en proveedor_cache por codigo_qr
      encontrado    → A-04 con el proveedor precargado
      no encontrado → ¿hay conexión?
                        sí → GET /api/proveedores/qr/{codigoQr}
                               200 → guardar en cache, ir a A-04
                               404 → "Este código no corresponde a ningún proveedor"
                        no → "No reconocemos este código y no hay señal para consultarlo.
                              Podés buscar al proveedor por nombre." → A-03
```

- **SQLite primero, red después.** Nunca al revés: en campo la red es la excepción.
- Permiso denegado: explicación + acceso a los ajustes del sistema. **Nunca** una pantalla negra sin
  explicación, nunca un bucle de re-pedido.
- `codigoQr` es nullable en el cache: hay proveedores que no lo tienen. Esos no se resuelven por QR y
  siempre queda `A-03`. No es un error.

### `A-03` Buscar proveedor — OFFLINE REAL

- Búsqueda por nombre sobre `proveedor_cache`, con la zona como dato de apoyo. **UseCase nuevo.**
- Dos estados vacíos distintos: con catálogo descargado → *"Ningún proveedor coincide"*; sin catálogo →
  remite a sincronizar, igual que `S-03`.

### `A-04` Registrar acopio ★ — OFFLINE-FIRST

**La pantalla central de todo el producto.** El `UiState` está escrito en `MOBILE_SCREENS.md §5`: usalo.

Campos y validaciones (`MOBILE_DATA_MAPPING.md §5.2`):

| Campo | Obligatorio | Validación |
|---|---|---|
| `proveedorId` | Sí | precargado por `A-02`/`A-03`, **no editable a mano**; debe existir en `proveedor_cache` |
| `unidadId` | Sí | selector desde `unidad_cache` |
| `fechaHora` | Sí | ahora por defecto, editable; **no futura**; aviso (no bloqueo) si > 24 h en el pasado |
| `litros` | Sí | `>= 0` **inclusive**, máx. 6 enteros + 2 decimales (`precision=8, scale=2`) |
| `gpsLat`/`gpsLng` | **No** | — |
| `motivoObservacionId` | **No** | selector desde `motivo_observacion_cache` |
| `litrosPorVoz` | **No** | interno, lo marca la app; `null` se resuelve a `false` en el mapper a SQLite |

**GPS — las cuatro reglas, ninguna negociable:**

1. Se pide al abrir la pantalla, **en paralelo** al llenado. Nunca bloquea.
2. Sin fix en **15 segundos** → `NoDisponible`, y **se guarda igual con GPS nulo**.
3. Permiso denegado → se guarda con GPS nulo y un aviso discreto **una sola vez**.
4. **Nunca** se impide guardar una entrega por falta de GPS. La leche ya se recibió; el registro es lo
   importante.

**Al guardar** (`MOBILE_ARCHITECTURE.md §16.1`): `uuidCliente` v4 en el dispositivo → fila en
`registro_acopio_local` con `PENDING` y el `usuario_id` de la sesión → **navegar atrás** con `Snackbar`
*"Guardado — se enviará cuando haya señal"* → sync oportunista, que puede fallar sin que al usuario le
importe ni se entere.

> **Nunca hay un spinner "enviando" bloqueante en esta pantalla.** Guardar es una escritura local:
> instantáneo, y no puede fallar por red. Ese es el punto entero del diseño offline-first.

**Borrador** (`§3.4`): con debounce ~500 ms, igual que `V-02`. Al volver, *"Tenés un registro sin
terminar, ¿lo retomás?"*. Un borrador **no** es un registro pendiente: no aparece en `S-05`, no se
sincroniza, no cuenta en ningún contador.

### `A-05` Historial de entregas del proveedor — ONLINE + CACHE

- Combina `registro_acopio_local` (propias) + `registro_acopio_cache` (ajenas); endpoint
  `GET /api/registros-acopio/proveedor/{proveedorId}`.
- Cada fila: `fechaHora`, `litros`, si tiene observación, y el estado de sync **solo si es local no
  confirmada**.
- **`DATA-013`**: si una entrega está local y descargada, se prioriza la fila local cuando
  `registro_acopio_local.server_id == registro_acopio_cache.id`. El resto de los solapamientos **no es
  detectable** — `RegistroAcopioResumenResponse` no trae `uuidCliente`. Se acepta para v1; no inventes una
  heurística de matching por `fechaHora`+`litros` (`PROMPT_FASE_06.md §3`).
- Sin conexión: lo local + lo cacheado, con aviso *"puede haber entregas más recientes"*.
- Sin paginación.

### `A-06` Detalle de registro de acopio — ONLINE + CACHE

- `GET /api/registros-acopio/{id}`. **UseCase nuevo.**
- ⚠️ `motivoObservacion` en el Response **es la descripción, no el id** — es el `NAME_MISMATCH` de
  `MOBILE_DATA_MAPPING.md §5.2`. No lo mapees como el mismo campo que `motivoObservacionId` del Request
  sin un mapper explícito.
- `fotoUrl` **no existe** en el Response aunque sí en el Request (`§18.6`). No inventes el campo.
- **Regla de fechas (`§10.3`)**: `fechaHora` y `sincronizadoEn` se muestran **etiquetados y por separado**
  ("Capturado" / "Sincronizado"), **nunca** como una duración entre ambos (`DATA-012`). Usá
  `FechaEtiquetada`, que existe desde la Fase 7 exactamente para esto.
- Sin edición. Si el rol es CALIDAD, aparece la acción "Registrar corrección" → destino previsto, pantalla
  de `8E`. Para ACOPIADOR no hay acción.

## 5. Testing requerido

Todo en `commonTest`, en JVM sin emulador, con lector de QR y proveedor de ubicación **falsos** (§3.1) y
los fixtures que ya existen (`FixtureRepositorios.kt`, `FakesDeSync.kt`, `FixtureDeSync.kt`):

1. **`A-04` guarda sin GPS**: con el proveedor de ubicación en `NoDisponible`, guardar funciona y la fila
   queda con `gpsLat`/`gpsLng` nulos — **no** `0`.
2. **`A-04` no espera al GPS**: un proveedor que nunca responde no impide guardar; a los 15 s el estado
   pasa a `NoDisponible` y `puedeGuardar` nunca dependió de él.
3. **`A-04` validación de `litros`**: negativo inválido; **`0` válido** (`@DecimalMin("0.0")` es
   inclusive); 7 dígitos enteros inválido.
4. **`A-04` validación de `fechaHora`**: futura inválida; más de 24 h en el pasado produce **aviso**, no
   bloqueo (`puedeGuardar` sigue en `true`).
5. **`A-04` campos opcionales**: sin motivo de observación, el registro se crea con
   `motivoObservacionId` nulo; `litrosPorVoz` nulo llega a SQLite como `false`, no como `null`.
6. **`A-04` borrador**: se persiste con debounce, sobrevive a la recreación del `ViewModel`, se borra al
   guardar, y **no** aparece en ningún contador de pendientes.
7. **`A-04` aparición optimista**: tras guardar, la entrega llega por el `Flow` de `A-05` sin volver a
   consultar a mano, con su badge de pendiente.
8. **`A-02` resuelve contra SQLite primero**: un QR presente en `proveedor_cache` **no** dispara ninguna
   llamada de red (verificable con el `MockEngine`, que no debe recibir request).
9. **`A-02` sin cache y sin conexión**: no llama a la red, produce el mensaje de `§5` y el efecto de
   navegación a `A-03`. Con conexión y 404: *"Este código no corresponde a ningún proveedor"*.
10. **`A-01` `horaEstimada` nula**: la fila se expone sin hora. Ningún `"--:--"`, ningún `"00:00"`.
11. **`A-05` `DATA-013`**: una entrega presente en local con `server_id` y en cache con el mismo `id`
    aparece **una sola vez**, y la fila que sobrevive es la local.
12. **`A-06` marcos temporales**: `fechaHora` y `sincronizadoEn` se exponen etiquetadas por separado y
    ningún campo del `UiState` es una duración entre ambas.
13. **Permisos**: denegación permanente de cámara produce el estado con salida a ajustes y **no** vuelve a
    disparar el diálogo; denegación de ubicación no impide que `A-04` guarde.

Reportá el conteo total leyendo `shared/build/test-results/jvmTest/`, no de memoria.

## 6. Trampas conocidas

| # | Trampa | Por qué importa |
|---|---|---|
| 1 | Escribir las 6 pantallas y recién después verificar que la librería de QR compila en iOS | Sección 3. Es el mismo error que la Fase 7 evitó con Compose, en la fase donde es más caro |
| 2 | Un `expect`/`actual` de cámara o ubicación sin `actual` de `jvmMain` | Rompe `:shared:jvmTest` entero y deja `A-02`/`A-04` sin poder testearse. Mirá cómo lo resolvió `SecureTokenStorage.jvm.kt` |
| 3 | Bloquear el guardado de `A-04` esperando el fix de GPS | Es la regla que más veces repite el documento. En campo, sin fix, la leche igual se recibió |
| 4 | Mostrar `gpsLat`/`gpsLng` nulos como `0.000000` | `§10.1` regla 3. Un `0,0` es una coordenada en el Golfo de Guinea, no un "sin dato" |
| 5 | Rechazar `litros = 0` | `@DecimalMin("0.0")` es **inclusive**. Una entrega de 0 L es rara pero válida |
| 6 | Consultar la red antes que `proveedor_cache` en `A-02` | `§3.3`. Invierte el diseño entero de la app |
| 7 | Reintentar el diálogo de permiso en bucle tras una denegación permanente | `§12` regla 3. Android e iOS ya ni siquiera lo muestran; el usuario ve una app rota |
| 8 | Pedir el permiso de cámara en el splash o al abrir Home | `§12` regla 1: en contexto, al tocar "Escanear QR". Fuera de contexto se deniega mucho más |
| 9 | Mapear `motivoObservacion` (Response) contra `motivoObservacionId` (Request) como el mismo campo | `NAME_MISMATCH` de `§5.2`: uno es la descripción, el otro un UUID |
| 10 | Calcular "sincronizado N minutos después de capturado" en `A-06` | `DATA-012`: son marcos temporales distintos. Está explícitamente prohibido |
| 11 | Deduplicar `A-05` con una heurística por `fechaHora`+`litros`+proveedor | `DATA-013`: puede fusionar en silencio dos entregas distintas. Peor que mostrar el duplicado |
| 12 | Agregar un botón de cámara para foto en `A-04` | `DATA-008`: diferido a v2, y no hay endpoint de subida (`§18.4`) |
| 13 | Poblar `registro_acopio_cache` con borrado masivo en cada refresh | `§11.2`: `INSERT OR REPLACE` fila por fila. Borrarla rompe la resolución de padres ajenos de `8C`/`8D` |
| 14 | Escribir un `ViewModel` de ACOPIADOR con un patrón distinto al de la Fase 7 | `PROMPT_FASE_08.md §8` riesgo 3. Quedan tres sub-fases más: la deriva se paga cinco veces |
| 15 | Un `@Composable` de `A-02` que inyecte el lector de QR directamente | `CLAUDE.md §3.4`: la cámara es una dependencia de plataforma, entra por el `ViewModel` como cualquier otra |

## 7. Decisiones que hay que tomar explícitamente

Anotalas en el checkpoint, no las resuelvas en silencio:

1. **Las dependencias de la sección 3.2** (QR, ubicación, permisos) — cuáles, por qué, y qué pasa en iOS.
2. **Dónde vive el "ya visitado hoy" de `A-01`**: es un cruce entre `ruta_zona_cache` y
   `registro_acopio_local`. ¿UseCase que combina los dos `Flow`, o consulta en el data source? Elegí y
   decí por qué.
3. **Qué hace `A-04` si el proveedor precargado no está en `proveedor_cache`** (cache limpiado entre `A-02`
   y `A-04`). El documento no lo cubre.
4. **Si `A-05` necesita `ObtenerRegistrosDeProveedorUseCase` además del observador**: uno refresca el cache
   contra el servidor, el otro lee. Definí quién dispara el refresco y cuándo — recordá la trampa 2 del
   `PROMPT_FASE_06.md §10` (no dispares red en cada recomposición).

Si aparece un hallazgo de contrato nuevo, va al checkpoint con el formato de `MOBILE_DATA_MAPPING.md §10`
como `DATA-016`.

## 8. Criterios de aceptación y checkpoint

Los de `PROMPT_FASE_08.md §6` y §7, sin cambios. Dos cosas propias de esta sub-fase para el checkpoint:

- **El estado real de iOS**, con la distinción de `CLAUDE.md §8` y la honestidad de la sección 3: qué
  compila, qué enlaza en CI, y qué queda sin verificar en runtime. Más la fila nueva para `CLAUDE.md §7`.
- **Qué del andamio compartido tocaste** (componentes, `ErrorUi`, `Formateadores`, navegación) y qué
  aprendiste que deba entrar en el prompt de `8B`.

Detenete ahí y esperá aprobación explícita antes de `8B`.

# Prompt — Fase 6: Repository + UseCases

> Alcance fijado por `MOBILE_DATA_MAPPING.md §13`: **"Repository + UseCases (integra Local + Remote +
> Sync)"**. Es la última fase sin UI (`MOBILE_SCREENS.md` recién entra en la Fase 7) — todo lo que
> construyas acá es la API pública que va a consumir la capa de presentación, así que el criterio de
> "terminado" es: *una futura pantalla nunca debería necesitar importar `SyncEngine`, un DAO de
> SQLDelight, ni `HttpClient` directamente*.

## 0. Contexto que esta fase hereda (no lo repitas, pero diseñá sabiendo esto)

`DATA-014` (HIGH, documentado en Fase 5) cambia lo que esta fase puede prometer: el lote de sync **nunca**
devuelve `server_id` (`confirmados[]` es `List<String>` de `uuidCliente`, nada más — ver
`MOBILE_DATA_MAPPING.md §10` y `MOBILE_ARCHITECTURE.md §6.1` corregido). Consecuencia directa para esta
fase: el **mecanismo 1** de `§18.1` (padre propio, mismo dispositivo, resuelto vía
`registro_acopio_local.server_id`) **no cierra hoy en la práctica** — un `AnalisisCalidad`/`LoteProduccion`
cuyo `RegistroAcopio` padre se capturó en este mismo dispositivo se queda en `PENDING_DEPENDENCY` con un
`sync_error` que nombra `DATA-014`, indefinidamente, hasta que el backend cambie. El **mecanismo 2** (padre
AJENO, vía `registro_acopio_cache`) no se ve afectado — sigue resolviendo normal.

Esto **no es un bug que esta fase deba arreglar** (no hay forma de arreglarlo sin backend). Es una realidad
de negocio que el Repository/UseCase tiene la obligación de **exponer honestamente** en vez de disfrazarla
de "esperando sincronizar" genérico. La sección 7 de este prompt es no negociable por eso.

## 1. Qué leer antes de empezar (no releas los documentos enteros)

| Documento | Sección | Por qué |
|---|---|---|
| `MOBILE_ARCHITECTURE.md` | §2 (Arquitectura) | El diagrama de capas exacto: UI → Presentation → **Domain/UseCases** → **Repository** → {LocalDataSource, SyncEngine→RemoteDataSource}. Fijate en la regla dura: "ningún ViewModel ni pantalla inyecta o llama un ApiClient directamente" |
| `MOBILE_ARCHITECTURE.md` | §5 (tabla offline-first completa) | Qué recurso es OFFLINE-FIRST (pasa por SQLite+SyncEngine) vs ONLINE-ONLY (Repository llama Remote directo, sin cola) vs READ-CACHE |
| `MOBILE_ARCHITECTURE.md` | §6.1–§6.7 (ya corregido con la nota de `DATA-014` en §6.1) | Los estados que el Repository va a leer/exponer tal cual los dejó el Sync Engine |
| `MOBILE_ARCHITECTURE.md` | §7 (idempotencia, paso 7 ya corregido) | El flujo de creación offline de 7 pasos — quién hace qué |
| `MOBILE_ARCHITECTURE.md` | §9 (Cache y lecturas) | El patrón `Repository → SQLite → UI` vía `Flow`; que `GET /api/sync/cambios` ya lo dispara el Sync Engine (Fase 5) al final de cada ciclo — este Repository **no reimplementa ese fetch**, solo lee las tablas `*_cache` que ya quedan pobladas |
| `MOBILE_ARCHITECTURE.md` | §10 (API Client) | El contrato de `ApiResult`/`ApiError` que el Repository recibe del Remote Data Source para las llamadas online-only |
| `MOBILE_ARCHITECTURE.md` | §16 completo (los 4 flujos, §16.2 ya corregido) | Vas a implementar exactamente estos flujos, no una variante |
| `MOBILE_ARCHITECTURE.md` | §18.1 completo | Los dos mecanismos de resolución de padre — mecanismo 1 y 2 — y el caso que ninguno cubre (bloqueo explícito en UI) |
| `MOBILE_ARCHITECTURE.md` | §11.2, bloque `registro_acopio_cache` y la nota debajo | Columnas exactas (`id`, `uuid_cliente` NULLABLE, `origen` RESUMEN\|DETALLE) y semántica `INSERT OR REPLACE`, nunca borrado masivo |
| `MOBILE_ARCHITECTURE.md` | §4 completo (Autenticación) | La tabla "Política de logout" y la regla multiusuario (`usuario_id` por fila) — es literalmente el contrato de la sección 7 de este prompt |
| `MOBILE_ARCHITECTURE.md` | §11.3 | Confirma que `CorreccionRegistro`/`ComunicadoConfirmacion`/`RecepcionPlanta` no tienen tabla local — son online-only de verdad, sin cola |
| `MOBILE_DATA_MAPPING.md` | Entrada `DATA-014` completa (§10) | Texto exacto del hallazgo y las dos soluciones de backend propuestas — para saber qué NO asumir |
| `CLAUDE.md` | §3.4 (arquitectura de capas), §7 (pendientes conocidos, con la fila nueva de `DATA-014`) | Reglas no negociables + qué sigue abierto del lado backend |

## 2. Alcance — qué SÍ se construye en esta fase

- `shared/domain/usecase/` — casos de uso (lista mínima en la sección 6).
- `shared/data/repository/` — interfaces + implementaciones, una por agregado (sección 5).
- Completar cualquier `domain/model/` que falte para representar los **estados de sync a nivel de
  dominio** (no reexportar las Entities de SQLDelight tal cual a la capa de dominio — ver sección 8).
- **Wiring de Koin real**: módulo que arma `SqlDriver` (con `Context` en Android), `ConnectivityObserver`
  de plataforma (con `Context` en Android), `SyncEngine`, los Repository y los UseCase. Hoy esto no existe
  — Fase 3/4/5 probaron sus piezas sueltas, nadie las conectó todavía.
- **Reemplazar el stub de `VerificadorPendientes` de Fase 3** por una implementación real contra las 4
  tablas `*_local` (sección 7).
- La población **on-demand** de `registro_acopio_cache` (mecanismo 2 de §18.1) — Fase 5 la excluyó
  explícitamente de su alcance; es de esta fase.

## 3. Alcance — qué NO se construye en esta fase

- Nada de UI, `ViewModel`, ni nada de `MOBILE_SCREENS.md` (Fase 7).
- Nada de scheduling real en background (`WorkManager`/`BGTaskScheduler`) — Fase 9. El único disparador de
  sync en esta fase sigue siendo `SyncEngine.solicitarSyncOportunista()` (oportunista, ya construido en
  Fase 5) más lo que el propio ciclo de vida de la app dispare manualmente.
- No implementes ningún cache local para `RecepcionPlanta` (ni siquiera el opcional que menciona §11.3) si
  nadie te lo pide explícitamente — no es crítico y no está en el alcance de esta fase.
- No intentes "arreglar" `DATA-014` con una heurística del lado cliente (por ejemplo, adivinar el
  `server_id` del padre buscando por `fechaHora`+`litros`+`proveedor`). Cualquier heurística de matching
  que no sea el `uuidCliente`/`id` exacto puede fusionar en silencio dos entregas distintas — más peligroso
  que dejar el hijo pendiente. Si se te ocurre una, documentala como propuesta y esperá aprobación, no la
  implementes.

## 4. Repository — diseño por agregado

### 4.1 `RegistroAcopioRepository` y `VentaRepository` (sin dependencia cruzada)

Los más simples — mismo patrón para los dos:

- `suspend fun crear(datos): String` (devuelve el `uuidCliente` generado) — inserta en SQLite con
  `status=PENDING`, `usuario_id` = el del JWT activo (leelo de `SecureTokenStorage`, no lo recibas como
  parámetro del caller: si el UseCase lo pasara, un bug de UI podría atribuir la captura al usuario
  equivocado — ver §4 "Multiusuario"), y **al final del mismo método** llama
  `syncEngine.solicitarSyncOportunista()`. Ver el punto de decisión más abajo sobre quién dispara esto.
- `fun observarPendientes(): Flow<List<...>>` y el/los métodos de lectura reactiva que `§16.4` pide, leyendo
  directo de las queries de Fase 4.
- `RegistroAcopioRepository` además expone `fun observarHistorialProveedor(proveedorId): Flow<List<...>>`
  que **combina** `registro_acopio_local` (propios) + `registro_acopio_cache` (ajenos, si ya se pobló) —
  ver la nota de deduplicación más abajo, no la saltees.

**Decisión de diseño que tenés que tomar y documentar explícitamente** (no la dejes implícita): ¿quién
llama `syncEngine.solicitarSyncOportunista()` — el Repository (al final de `crear()`) o el UseCase (después
de llamar a `repository.crear()`)? El diagrama de `§2` ("Repository... decide si... dispara una llamada
remota") y el de `§16.1` sugieren que es el Repository, no el UseCase — el UseCase no debería saber que
existe un `SyncEngine`. Tomá esa lectura salvo que tengas una razón concreta para la otra, y decila en el
checkpoint.

**Nota de deduplicación (interacción `DATA-013` × `DATA-014`, documentala en el checkpoint, no hace falta
un `DATA-0xx` nuevo)**: la mitigación de `DATA-013` para no mostrar un registro dos veces (propio +
descargado) depende de comparar `registro_acopio_cache.id` contra `registro_acopio_local.server_id`. Con
`DATA-014` ese `server_id` casi nunca está poblado en el lado local (el lote no lo devuelve), así que esa
deduplicación **va a fallar en silencio la mayoría de las veces** — no es un bug nuevo de esta fase, es la
mitigación de `DATA-013` perdiendo eficacia por el hallazgo posterior. No inventes un reemplazo — dejalo
documentado como limitación conocida y seguí.

### 4.2 `AnalisisCalidadRepository` y `LoteProduccionRepository` (con dependencia de padre)

Mismo patrón que 4.1 para `crear()`/lectura, más la resolución de padre. Extraé la lógica de resolución a
un componente compartido (algo como `ResolutorPadreRegistroAcopio`) — no la dupliques en los dos
Repository, los dos necesitan exactamente la misma máquina de decisión:

```text
Dado un registroAcopioRef (uuidCliente si es propio, o server_id si ya se conoce por ser ajeno resuelto):

1. ¿Es un padre que este dispositivo capturó (existe en registro_acopio_local)?
   → usar registro_acopio_local.server_id si está presente (mecanismo 1 — hoy casi nunca, por DATA-014)
   → si no está presente: guardar el hijo en PENDING_DEPENDENCY (el Sync Engine ya sabe promoverlo
     SOLO si server_id aparece — Fase 5 ya lo hizo así a propósito)

2. ¿Es un padre ajeno ya cacheado (existe en registro_acopio_cache)?
   → usar registro_acopio_cache.id directo (mecanismo 2) — el hijo nace con el server_id resuelto,
     nunca pasa por PENDING_DEPENDENCY

3. ¿Es un padre ajeno NO cacheado todavía?
   → esta fase debe poder poblarlo on-demand: llamar GET /api/registros-acopio/proveedor/{proveedorId}
     (o /{id} si ya se conoce el id), guardar con INSERT OR REPLACE en registro_acopio_cache
     (columna origen según qué DTO respondió), y recién ahí caer en el caso 2.
     Si la llamada falla por falta de conectividad: no hay nada que cachear todavía — devolver un
     error de dominio explícito (ver 4.2.a), NUNCA guardar el hijo en un estado que sugiera que se
     va a resolver solo, porque no hay ninguna referencia que lo permita (§18.1, "el caso que no
     cubre ninguna de las dos").
```

**4.2.a — Error de dominio requerido**: definí un caso de error específico (no reutilices un
`ApiError` genérico) para "padre ajeno no resoluble sin conectividad" — la futura pantalla de
`MOBILE_SCREENS.md §6.2/§7.2` necesita distinguirlo de un error de red genérico para mostrar el mensaje
explícito que `§18.1` pide.

### 4.3 `CatalogoRepository` (proveedores, unidades, motivos, tipos de queso, precio vigente,
comunicados, predicciones, ruta de zona)

Esta fase **no vuelve a implementar el fetch** — el Sync Engine (Fase 5) ya llama `GET /api/sync/cambios`
al final de cada ciclo y reemplaza las tablas `*_cache` (`§16.2`, corregido). `CatalogoRepository` es
prácticamente solo lectura reactiva:

- Un método `Flow<...>` por catálogo, leyendo directo de las tablas `*_cache` correspondientes.
- Un único método para forzar un refresco manual (para un futuro "pull to refresh"), que delega en
  `syncEngine.solicitarSyncOportunista()` — no reimplementa la llamada HTTP.
- `ruta_zona_cache` es la excepción (se descarga bajo demanda con `GET /zonas/{zonaId}/ruta`, no viaja en
  `/sync/cambios` — `§18.5`) — a esta sí le corresponde un método propio que llama al Remote Data Source
  directo y hace `INSERT OR REPLACE` por zona.

### 4.4 Repositories online-only, sin cola (`CorreccionRegistroRepository`,
`ComunicadoConfirmacionRepository`, `RecepcionPlantaRepository` si se necesita ya)

Sin tabla local (confirmado en `§11.3`). El método correspondiente llama al Remote Data Source directo y
traduce el `ApiResult` a un resultado de dominio — no hay PENDING, no hay reintento automático, no hay
`uuidCliente`. Si no hay conectividad, el error sube tal cual a la UI ("requiere conexión", ya clasificado
así en `§5`). No le agregues cola ni reintento por tu cuenta — `§18.2`/`§18.3` documentan que el backend no
es idempotente para estos todavía; encolarlos sin que el backend lo soporte generaría duplicados reales.

## 5. UseCases — lista mínima requerida

Un `UseCase` por operación de negocio, delgado (genera `uuidCliente` si aplica, llama un método de
Repository, traduce errores de dominio a lo que la futura UI necesita — nada de lógica de validación de
formulario, eso es de `MOBILE_SCREENS.md`/Fase 7):

| UseCase | Repository que usa | Nota |
|---|---|---|
| `CrearRegistroAcopioUseCase` | `RegistroAcopioRepository` | — |
| `CrearVentaUseCase` | `VentaRepository` | Recordá la restricción de UI de `DATA-010` no aplica acá (es de pantalla), pero el UseCase sí debe rechazar un `tipoCliente` fuera del enum antes de tocar la red, no confiar en que el 500 nunca llegue |
| `CrearAnalisisCalidadUseCase` | `AnalisisCalidadRepository` | Recibe la referencia al padre ya resuelta por la UI (propio o ajeno-elegido-de-lista) |
| `CrearLoteProduccionUseCase` | `LoteProduccionRepository` | Igual, con lista de padres |
| `ObtenerRegistrosDeProveedorUseCase` | `RegistroAcopioRepository` | Para poblar la lista de "elegí el registro padre" cuando es ajeno — dispara la población on-demand de `registro_acopio_cache` (4.2, caso 3) |
| `ObservarHistorialProveedorUseCase` | `RegistroAcopioRepository` | Lectura reactiva, `§16.4` |
| `ObservarPendientesUseCase` | los 4 Repository de escritura | Une los 4 `Flow` de pendientes para la futura pantalla `S-05` — expone el estado tal cual sección 7 exige, no lo resume |
| `ReintentarManualUseCase` | Repository correspondiente | Para un ítem `FAILED` (permanente o transitorio) — dispara un intento fuera del ciclo automático |
| `ObservarCatalogosUseCase` (o uno por catálogo) | `CatalogoRepository` | Lectura reactiva |
| `ObtenerRutaDelDiaUseCase` | `CatalogoRepository` | Bajo demanda, `§18.5` |
| `ResolverProveedorPorQrUseCase` | `CatalogoRepository` | Resuelve contra SQLite primero, `§5` fila "Escanear QR" |
| `ConfirmarComunicadoUseCase` | `ComunicadoConfirmacionRepository` | Online-only |
| `AnexarCorreccionUseCase` | `CorreccionRegistroRepository` | Online-only |
| `VerificarPendientesUseCase` | los 4 Repository de escritura | Sección 7 — reemplaza el stub de Fase 3 |
| `LogoutUseCase` | `VerificarPendientesUseCase` + `SecureTokenStorage` | Sección 7 |

No es obligatorio que sea exactamente esta lista palabra por palabra — si al implementar ves que dos se
fusionan naturalmente o falta uno, está bien, pero documentá el cambio en el checkpoint en vez de
simplemente no mencionarlo.

## 6. `VerificarPendientesUseCase` y `LogoutUseCase` (reemplaza el stub de Fase 3)

Implementación literal de la tabla "Política de logout" de `§4`:

1. `VerificarPendientesUseCase` cuenta filas en las 4 tablas `*_local` con `status IN (PENDING,
   PENDING_DEPENDENCY, SYNCING, FAILED)` **filtradas por `usuario_id` = el de la sesión activa** (no
   cuentes filas de otro usuario que comparte el dispositivo — `§4` "Multiusuario").
2. `LogoutUseCase`:
   - Si el conteo es 0: borra `SecureTokenStorage` + todas las tablas `*_cache` con datos personales
     (`proveedor_cache`, `ruta_zona_cache`, `registro_acopio_cache`) + las filas `SYNCED` de las tablas
     `*_local`. Termina.
   - Si el conteo es > 0: **no borra nada**, devuelve un resultado de dominio que la UI de Fase 7+ usa para
     mostrar "Tenés N registros sin enviar" con las dos opciones de `§4` (sincronizar ahora / cerrar sesión
     conservando los datos). Si el caller elige la segunda opción explícitamente (parámetro separado, no
     un segundo llamado ambiguo al mismo método), borra solo token + caches personales, **nunca** las
     tablas `*_local` con pendientes.
3. Escribí un test que arma exactamente el escenario multiusuario de `§4`: usuario A con una fila
   `PENDING`, usuario B loguea en el mismo dispositivo — `VerificarPendientesUseCase` para la sesión de B
   debe dar 0, y un ciclo de sync con la sesión de B activa no debe subir la fila de A.

## 7. Cómo debe verse `DATA-014` expuesto a través de esta capa (no negociable)

El dominio necesita poder distinguir, para un ítem en `PENDING_DEPENDENCY`, entre "esperando algo que va a
pasar solo" y "esperando algo que hoy no va a pasar nunca sin intervención". Ejemplo de forma aceptable
(no es obligatorio el nombre exacto, sí la distinción):

```kotlin
sealed class EstadoSincronizacion {
    object Pendiente : EstadoSincronizacion()
    data class EsperandoDependencia(val motivoConocido: String?) : EstadoSincronizacion()
    object Sincronizando : EstadoSincronizacion()
    object Sincronizado : EstadoSincronizacion()
    data class Fallido(val motivo: String, val reintentable: Boolean) : EstadoSincronizacion()
}
```

Donde `motivoConocido` es el `sync_error` tal cual lo dejó el Sync Engine cuando nombra `DATA-014` — el
Repository/UseCase **no lo descarta ni lo reemplaza por un texto genérico**. No hace falta resolver todavía
qué texto ve el usuario final (eso es Fase 7/8 + `MOBILE_SCREENS.md`); lo que esta fase no puede hacer es
perder la información o fingir que es un estado transitorio como cualquier otro. Un test que lo verifique:
crear un `RegistroAcopio` y su `AnalisisCalidad` hijo en el mismo dispositivo, correr un ciclo de sync
completo (el padre debe quedar `Sincronizado`), y verificar que el `UseCase` expone al hijo como
`EsperandoDependencia` con `motivoConocido` no nulo — nunca como `Pendiente` genérico, nunca oscilando.

## 8. Manejo de errores — mapeo de dominio

- El Repository nunca deja escapar `ApiError` (`§10`) tal cual hacia el UseCase, ni el UseCase tal cual
  hacia donde sea que lo llame la futura UI. Definí (o completá si ya existe de fases anteriores) un tipo
  de error de dominio por caso de uso, mapeando: 401/expirado → "requiere re-login" (nunca se resuelve
  reintentando silenciosamente); 403 → "sin permiso"; 404/422/400 de negocio → error permanente mostrable;
  5xx/timeout/sin red → error transitorio (para online-only) o, si el recurso es offline-first, ni siquiera
  debería llegar como error al UseCase — ya lo absorbe el Sync Engine como `FAILED`-transitorio.
- Los `domain/model/` de esta fase **no son las Entities de SQLDelight reexportadas**. Puede que hoy sean
  casi idénticas campo a campo, pero la capa de dominio no debería romperse si mañana cambia una columna
  interna de SQLite — mapeá explícitamente, aunque el mapper sea trivial.

## 9. Testing requerido

Todo corre en `commonTest`/`jvmTest` (SQLDelight en memoria de Fase 4 + `MockEngine` de Fase 2 + el
`SyncEngine` real de Fase 5 con su `ConnectivityObserver` fake) — nada de esto necesita Android/iOS real:

1. Crear offline → observar `Flow` → aparece con estado `Pendiente` (sin red simulada).
2. Ciclo de sync exitoso → el mismo ítem pasa a `Sincronizado` vía el `Flow` (sin que el test tenga que
   volver a consultar manualmente — es la gracia de exponer `Flow` de punta a punta).
3. El escenario completo de la sección 7 (padre + hijo mismo dispositivo → `EsperandoDependencia` con
   motivo, nunca resuelto en el mismo test run).
4. Padre ajeno no cacheado → `ObtenerRegistrosDeProveedorUseCase` puebla `registro_acopio_cache` → crear el
   hijo referenciándolo → nace ya con server_id resuelto, nunca pasa por `PENDING_DEPENDENCY` (mecanismo 2).
5. Padre ajeno no cacheado y sin conectividad → error de dominio explícito (4.2.a), no un estado pendiente
   engañoso.
6. El escenario multiusuario completo de la sección 6, punto 3.
7. Logout bloqueado con pendientes propios; logout permitido y limpio sin pendientes; "cerrar sesión
   conservando los datos" deja las filas `*_local` intactas y borra solo token + caches personales.
8. `CrearVentaUseCase` rechaza un `tipoCliente` fuera del enum **antes** de llegar al Repository (test de
   que el UseCase, no el backend, es la primera línea de defensa de `DATA-010`).
9. Un repositorio online-only (`ComunicadoConfirmacionRepository` o el que elijas) propaga un error 5xx
   simulado como error transitorio de dominio, sin reintentar por su cuenta.

## 10. Trampas conocidas

| # | Trampa | Por qué importa |
|---|---|---|
| 1 | Un `ViewModel` futuro terminaría importando `SyncEngine` o un DAO directo "porque es más rápido" | Rompe la regla dura de `§2`. Si un UseCase no cubre algo que la UI necesita, se agrega el UseCase que falta, no se salta la capa |
| 2 | Repository que dispara `solicitarSyncOportunista()` en cada lectura, no solo tras un `crear()` | Sync innecesario en cada recomposición de UI — decidí un único punto de disparo y documentalo (sección 4.1) |
| 3 | Reintentar "arreglar" la deduplicación `DATA-013`/`DATA-014` con matching heurístico | Ver sección 3 — riesgo de fusionar registros distintos en silencio |
| 4 | Tratar `registro_acopio_cache` como las demás tablas `*_cache` (borrado masivo en cada refresh) | `§11.2` es explícito: `INSERT OR REPLACE` fila por fila, nunca borrado masivo — borrarla rompe la resolución de padres ajenos ya resueltos |
| 5 | `VerificarPendientesUseCase` sin filtrar por `usuario_id` | Cuenta pendientes de otro usuario en un dispositivo compartido — bloquea (o peor, no bloquea) el logout equivocado |
| 6 | Sync de un recurso subiendo filas de `usuario_id` distinto al de la sesión activa | `§4` es explícito: el Sync Engine debe filtrar por `usuario_id` = sesión activa. Si esto quedó sin filtrar desde Fase 5, es esta fase la que lo nota al construir el Repository — si lo ves, avisá, no lo arregles en silencio dentro de otra capa |
| 7 | Exponer `PENDING_DEPENDENCY` genérico sin `motivoConocido` cuando el `sync_error` ya lo tiene | Sección 7 — es el punto central de esta fase, no un detalle |
| 8 | Poblar `registro_acopio_cache` con datos de un endpoint ADMIN (`ProveedorAdminResponse` o equivalente) | `§4` RNF-12 — el móvil solo usa los DTOs públicos, nunca los que traen DNI/teléfono |
| 9 | UseCase online-only (`ConfirmarComunicadoUseCase`, etc.) que agrega su propia cola/reintento "para que sea offline-first como el resto" | `§18.2`/`§18.3`: el backend no es idempotente para estos todavía — encolar sin que el backend lo soporte crea duplicados reales, no es una mejora |
| 10 | Koin wireando el `SqlDriver`/`ConnectivityObserver` de Android sin `Context` de aplicación (no de Activity) | Fuga de memoria clásica — usar `applicationContext`, nunca el `Context` de una pantalla |

## 11. Criterios de aceptación

**Verificable localmente en Windows** (esto es lo único que un checkpoint puede citar como "pasó"):
- `./gradlew :shared:assemble :androidApp:assembleDebug` → `BUILD SUCCESSFUL`.
- `./gradlew :shared:jvmTest :androidApp:testDebugUnitTest` → todos verdes, incluyendo los 9 tests de la
  sección 9. Reportá el conteo total leyendo `shared/build/test-results/jvmTest/` (no de memoria), igual
  que en el checkpoint de Fase 5.
- `compileKotlinIos*`/`compileTestKotlinIos*` (dentro de `:shared:assemble`) en verde — evidencia de tipos
  y firmas `expect`/`actual`, nada más (`CLAUDE.md §8`).

**No verificable en Windows — requiere CI macOS**:
- `linkDebugFrameworkIos*`/`iosSimulatorArm64Test` reales. No los des por buenos ni los mezcles con lo de
  arriba en el resumen del checkpoint.

## 12. Formato del checkpoint esperado

Mismo formato que Fases 4 y 5 (qué se construyó, ajustes a capas previas si los hubo, compila, tests con
conteo leído de archivo, decisiones tomadas — en particular la de la sección 4.1 sobre quién dispara
`solicitarSyncOportunista()` —, regresiones, problemas encontrados / `DATA-0xx` nuevos si aparecen, qué
falta para la fase siguiente, estado de iOS con la distinción de `CLAUDE.md §8`). Detenete ahí y esperá
aprobación antes de la Fase 7 — que ya es la primera fase con UI real.

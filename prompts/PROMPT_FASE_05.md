# PROMPT — Fase 5: Sync Engine

Sos Claude Code trabajando en `acopio-mobile`. Fases 0-4 completas. La Fase 4 construyó el esquema
SQLite completo, los `ColumnAdapter`, `domain/model/` y un `LocalDataSource` por tabla — pero nadie los usa
todavía. Esta fase les da uso real: el motor que decide qué se sube, cuándo, en qué orden, y qué hacer con
la respuesta.

Esta es, de las que quedan, la fase con más lógica de estados y más formas de fallar en silencio si algo
se hace apurado. Tomate el tiempo de leer §6 completo antes de escribir una sola línea — no es una fase
para improvisar sobre la marcha.

## Antes de empezar — qué leer

- **`CLAUDE.md`** completo — en particular la fila de la tabla de §7 sobre el límite de lote (te toca a
  vos decidir el número) y §3.6 (trabajo no confirmado no se borra, aplica directo a cómo reintentás).
- **`MOBILE_ARCHITECTURE.md` §6 completo** (6.1 a 6.7): la máquina de estados, por qué el batch tolera
  fallos parciales, backoff, idempotencia end-to-end, conectividad, recuperación tras reinicio, y las
  diferencias Android/iOS de background — esta última con una advertencia: **el scheduling real
  (`WorkManager`/`BGTaskScheduler`) es Fase 9, no esta.** Leé §6.7 solo para la regla de diseño que sí te
  toca (motor *stateless* e interrumpible), no para implementar el trigger de background en sí.
- **`MOBILE_ARCHITECTURE.md` §7** (tabla de idempotencia por recurso) y **§8** (conflictos — leé solo el
  primer bloque, `RecepcionPlanta` **no** es tuyo, ver más abajo).
- **`MOBILE_ARCHITECTURE.md` §18.1** completo — la mitigación de dependencia de ids (`PENDING_DEPENDENCY`
  + `registro_acopio_cache`) y su pseudocódigo de resolución. Es el corazón de esta fase.
- **`MOBILE_ARCHITECTURE.md` §17**, filas "Sincronización", "Reintentos / backoff", "Duplicados",
  "Pérdida/recuperación de conexión", "Timeout", "Errores HTTP" — tu lista de tests obligatorios.
- Los endpoints de sync que ya existen en `shared/.../network/Endpoints.kt` (Fase 2) — **no los
  redefinas**. Si no recordás la forma exacta del body/response de `/api/sync/{recurso}` o
  `/api/sync/cambios`, greppealo ahí antes de asumir la forma.
- Los `LocalDataSource` de Fase 4 (`data/local/datasource/`) — son tu única forma de tocar SQLite en esta
  fase. No escribas SQL nuevo acá; si te falta una query, es señal de que faltó en Fase 4 y hay que
  agregarla ahí, no saltearla escribiendo acceso directo a `AcopioDatabase` desde `synchronization/`.

## Alcance de esta fase (y qué NO es)

**Sí construye**: `SyncEngine` (o el nombre que seleccione, en `shared/synchronization/`) con su ciclo
completo para los **4 recursos offline-first** (`RegistroAcopio`, `AnalisisCalidad`, `LoteProduccion`,
`Venta`), la resolución de `PENDING_DEPENDENCY` de §18.1, retries con backoff, troceo defensivo del lote, y
`ConnectivityObserver` (`expect`/`actual`: Android `ConnectivityManager.NetworkCallback`, iOS
`NWPathMonitor`) como `Flow<Boolean>`.

**No construye**:
- El `Repository` que expone esto a `UseCase`s/ViewModels — es Fase 6. El `SyncEngine` de esta fase puede
  (y debe) tener una API pública clara (ej. `suspend fun ejecutarCiclo()`, `fun observarEstadoGlobal():
  Flow<...>`), pero nadie fuera de tests la llama todavía.
- El *scheduling* real de background (`WorkManager`/`BGTaskScheduler`) — Fase 9. Esta fase dispara el
  ciclo por: cambio de conectividad (`ConnectivityObserver`) y una llamada manual expuesta para tests. Un
  botón "sincronizar ahora" en UI es Fase 7+.
- `RecepcionPlanta`: es **online-only por diseño de backend** (§12 de la matriz de entidades) — no pasa
  por cola, no tiene `sync_status`, no es un recurso de este motor. Su 409 (§8) lo maneja quien la llame
  directamente cuando exista esa pantalla (Fase 8), no el Sync Engine.
- Ninguna llamada nueva a `GET /api/registros-acopio/{id}` o `.../proveedor/{id}` para poblar
  `registro_acopio_cache` bajo demanda (mecanismo 2 de §18.1) — esa es una acción disparada por la UI
  (CALIDAD buscando un proveedor), no algo que el Sync Engine haga proactivamente. Esta fase **solo lee**
  `registro_acopio_cache`, nunca la puebla.

## 1. La máquina de estados (§6.1) — implementación

Los 4 recursos comparten el mismo enum `SyncStatus` de Fase 4. Un ciclo (`ejecutarCiclo()`) hace, **en
este orden** (el orden importa, ver el punto siguiente):

1. Para cada uno de los 4 recursos, seleccionar filas `usuario_id = <sesión activa>` con `sync_status =
   PENDING`, o `FAILED` transitorio con `next_attempt_at <= ahora` (recordá el `OR next_attempt_at IS
   NULL` que ya resolvió Fase 4 para filas nunca intentadas).
2. Para `AnalisisCalidad`/`LoteProduccion`: aplicar la resolución de §18.1 antes de armar el lote —
   ```text
   si registro_acopio_server_id IS NOT NULL     → usarlo (padre ajeno, ya resuelto vía registro_acopio_cache)
   si registro_acopio_uuid_cliente IS NOT NULL  → buscar registro_acopio_local.server_id
         · si existe   → usarlo, entra al lote
         · si no existe → sync_status = PENDING_DEPENDENCY, no entra al lote esta vez
   ```
3. Trocear cada lote en fragmentos de tamaño fijo (elegí un número concreto entre 50 y 100 — `CLAUDE.md
   §7` te deja la decisión, no la dejes sin valor). Marcar `SYNCING` antes de enviar cada fragmento.
4. `POST /api/sync/{recurso}` por fragmento. Reconciliar la respuesta exactamente como dice §6.2: todo
   `uuidCliente` en `confirmados[]` → `SYNCED` + `server_id` + `sincronizadoEn`; todo en `errores[]` →
   `FAILED` + motivo, **sin reintento automático**; cualquier `uuidCliente` propio ausente de ambas listas
   → se queda `SYNCING`, se reintenta el próximo ciclo — **nunca asumas éxito por omisión.**
5. **Orden entre recursos dentro del mismo ciclo**: procesá `RegistroAcopio` completo (incluida su
   reconciliación) **antes** de evaluar la resolución de `PENDING_DEPENDENCY` de `AnalisisCalidad`/
   `LoteProduccion`. Es la única forma de que un padre confirmado en este mismo ciclo promueva a sus hijos
   de `PENDING_DEPENDENCY` a `PENDING` **en el mismo ciclo**, como pide §6.1 — si procesás los 4 recursos
   en paralelo o en el orden equivocado, la promoción queda un ciclo atrasada sin que ningún test simple lo
   note (el resultado final es el mismo, solo tarda un ciclo más — motivo por el cual esto necesita un
   test explícito de orden, no solo de resultado).
6. Al final del ciclo, si hubo algún `SYNCED` nuevo en `RegistroAcopio`, reevaluar las filas
   `PENDING_DEPENDENCY` de `AnalisisCalidad`/`LoteProduccion` que referencien esos `uuidCliente` y
   promoverlas a `PENDING` (para el próximo ciclo, no hace falta reenviarlas en este).
7. `GET /api/sync/cambios` y reemplazo de las tablas `*_cache` (Fase 4 ya tiene el `reemplazarTodo`
   transaccional) — este paso es independiente de los 4 anteriores, puede ir siempre al final del ciclo
   incluso si algún recurso falló.

## 2. Retries y backoff (§6.3)

- Solo fallos **transitorios** (timeout, error de red a mitad de camino, 5xx) incrementan
  `sync_attempts` y calculan `next_attempt_at`. Un fallo **permanente** (item en `errores[]`, o 400/404/422
  a nivel de fragmento completo si el backend llegara a responder así) marca `FAILED` sin
  `next_attempt_at` — no se reintenta solo.
- Elegí una secuencia concreta de backoff con techo y un límite de intentos (§6.3 da un ejemplo: 15s→30s→
  1m→5m→15m, tope 8 intentos) — documentá los valores que elijas en el checkpoint, no dejes la secuencia
  como "backoff exponencial genérico" sin números.
- Al detectar `false→true` en `ConnectivityObserver`, disparar un ciclo **inmediatamente**, sin esperar el
  próximo `next_attempt_at` programado.
- 401: no lo manejés vos. El cliente HTTP (Fase 2/3) ya intenta refresh antes de que la respuesta te
  llegue. Si el refresh también falla, vas a recibir un error de sesión inválida — tratalo como fallo
  **permanente** de todo el ciclo (no de un item), y propagalo de forma que quien integre esto en Fase 6
  pueda distinguir "hay que reintentar" de "hay que volver a loguear". No repitas la lógica de refresh acá.

## 3. Duplicados e idempotencia (§6.4) y recuperación (§6.6)

- No hay nada que "implementar" para evitar duplicados del lado del cliente más allá de: reenviar
  cualquier `uuidCliente` no confirmado como `SYNCED` es seguro, porque el backend ya resuelve por
  `uuidCliente`. El test relevante es de comportamiento, no de código nuevo: un lote reenviado que el
  backend ya tenía no debe generar una segunda fila local ni duplicar nada visualmente.
- Al arrancar el motor (proceso recién iniciado), cualquier fila que haya quedado `SYNCING` de una sesión
  anterior (la app se cerró a mitad de sync) se trata como candidata a reintento en el próximo ciclo, igual
  que `FAILED` transitorio — la idempotencia del backend cubre el caso "en realidad sí se había guardado".
  No hace falta un estado nuevo para esto.

## 4. `ConnectivityObserver`

`expect class`/`interface` en `shared/synchronization/`, con `actual`:

- **Android**: `ConnectivityManager.registerNetworkCallback` sobre una `NetworkRequest` con capacidad de
  Internet validada (no solo "hay wifi" — ver la advertencia de §6.5 de que la señal es "intentar", no
  "hay Internet garantizado").
- **iOS**: `NWPathMonitor`.
- Expuesto como `Flow<Boolean>` (o `StateFlow<Boolean>`) desde `commonMain` — el `SyncEngine` observa esto,
  nunca al revés.
- Para tests: un fake en `commonTest` que permita emitir `false`/`true` a voluntad (`MutableStateFlow` o
  similar) — no necesitás mockear nada de plataforma para testear el `SyncEngine`.

## Testing (§17, filas de sync)

Con `MockEngine` (Ktor, Fase 2) + driver SQLDelight en memoria (Fase 4) + el fake de conectividad de
arriba, todo en `commonTest`, sin emulador:

- **Lote mixto**: algunos `uuidCliente` en `confirmados[]`, otros en `errores[]` → cada fila termina en el
  estado correcto, incluida al menos una fila **ausente de ambas listas** que debe quedar `SYNCING` (no
  asumida exitosa).
- **`PENDING_DEPENDENCY` → promoción en el mismo ciclo**: un `RegistroAcopio` `PENDING` con un
  `AnalisisCalidad` hijo en `PENDING_DEPENDENCY` referenciándolo por `uuid_cliente`; correr un ciclo;
  confirmar que el hijo queda `PENDING` (no todavía `SYNCED` — eso es el próximo ciclo) **dentro del mismo
  `ejecutarCiclo()`**, no en uno posterior. Este es el test que se rompe silenciosamente si el orden del
  punto 5 de la sección 1 está mal.
- **Padre ajeno ya resuelto**: un `AnalisisCalidad` con `registro_acopio_server_id` (no `uuid_cliente`) va
  directo a `PENDING`/se sincroniza sin pasar nunca por `PENDING_DEPENDENCY`.
- **Backoff**: fallo transitorio incrementa `sync_attempts` y calcula `next_attempt_at` con la secuencia
  que elegiste; fallo permanente no toca `next_attempt_at`.
- **Reconexión dispara exactamente un ciclo**: fake de conectividad `false→true` una vez → un solo ciclo
  ejecutado, no cero, no dos (usá Turbine, ya está en el toolchain).
- **Timeout**: `MockEngine` con delay mayor al timeout configurado (Fase 2) → `FAILED` transitorio, nunca
  permanente.
- **Duplicado ya confirmado**: reenviar un lote que `MockEngine` responde como "ya estaba" → no se crea
  fila nueva ni se pierde la existente.
- **Restart con `SYNCING` huérfano**: una fila `SYNCING` al arrancar el motor se reintenta en el próximo
  ciclo, no queda trabada para siempre.
- **Troceo**: un lote más grande que el tamaño de fragmento elegido genera más de un `POST`, no uno solo
  con todo adentro.

## Trampas conocidas

| # | Trampa | Por qué importa |
|---|---|---|
| 1 | Procesar los 4 recursos en paralelo o en orden arbitrario | Rompe la promoción de `PENDING_DEPENDENCY` en el mismo ciclo (§6.1) sin que un test de resultado final lo note — solo un test de "mismo ciclo" lo agarra |
| 2 | Asumir éxito para un `uuidCliente` que no aparece ni en `confirmados[]` ni en `errores[]` | Contradice §6.2 explícitamente — el backend nunca debería omitir un ítem, pero el cliente debe ser defensivo igual |
| 3 | Reimplementar el manejo de 401/refresh dentro del Sync Engine | Ya existe en el cliente HTTP de Fase 2/3; duplicarlo es la clase de "mejora no pedida" que `CLAUDE.md §6` pide evitar |
| 4 | Tratar `RecepcionPlanta` como un recurso más del motor | Es online-only por diseño; no tiene `sync_status` ni cola |
| 5 | Que el Sync Engine llame a `GET /registros-acopio/...` para poblar `registro_acopio_cache` | Fuera de alcance de esta fase — el motor solo lee esa tabla, no la puebla |
| 6 | Dejar el troceo de lote o la secuencia de backoff como "configurable sin default" | `CLAUDE.md §7` pide una decisión concreta tuya, documentada, no un parámetro sin valor |
| 7 | Construir el *scheduling* de `WorkManager`/`BGTaskScheduler` en esta fase | Es Fase 9. Esta fase solo dispara por conectividad y por llamada manual/de test |
| 8 | Tratar una fila `SYNCING` huérfana al reiniciar como si necesitara un estado nuevo | La idempotencia del backend ya cubre "reintentar algo que quizás sí se guardó" — no hace falta lógica especial, solo no dejarla trabada |

## Criterios de aceptación

**Local (Windows, JVM)**: `./gradlew :shared:jvmTest` verde con todos los tests de esta fase, más los 177
preexistentes sin regresión. `./gradlew :shared:assemble` y `:androidApp:assembleDebug` sin errores — esto
compila **solo los targets disponibles en este host** (`CLAUDE.md §9`); no es evidencia de nada sobre iOS,
ni de compilación ni de ejecución, y no lo presentes como tal en el checkpoint.

**Solo CI**: igual que Fase 4, el mismo `commonTest` corriendo en verde sobre `verificacion-android.yml` y
`verificacion-ios.yml` — pedí el resultado real de esos runs, no solo que el build general haya dicho
`BUILD SUCCESSFUL`.

## Checkpoint de cierre

Formato de `CLAUDE.md §5`. Además, puntualmente:

- Los valores concretos que elegiste para tamaño de fragmento y secuencia de backoff, y por qué.
- Confirmación explícita (con el test que lo prueba) de que la promoción de `PENDING_DEPENDENCY` ocurre en
  el mismo ciclo que confirma al padre — no "debería funcionar", el resultado del test puntual.
- Qué exactamente devuelve `SyncEngine` cuando el ciclo entero no pudo correr por sesión inválida (401 con
  refresh fallido) — la forma que uses acá es la que Fase 6 va a tener que consumir.

**Detenete después del checkpoint y esperá aprobación antes de la Fase 6** (Repository + UseCases, la que
finalmente conecta esto con algo que una UI podría llamar).

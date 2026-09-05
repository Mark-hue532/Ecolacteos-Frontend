# PROMPT — Fase 4: SQLDelight (Local Data Source)

Sos Claude Code trabajando en `acopio-mobile`. Ya pasaron las Fases 0-3: documentos de diseño, Core/DI,
Network (DTOs + serializers), y Secure Storage/autenticación (cerrada con un gap documentado: el roundtrip
real de Keychain en iOS quedó en `@Ignore`, causa raíz sin identificar — ver `CLAUDE.md §7`. No es tu
problema en esta fase, no lo toques).

Esta fase construye el **Local Data Source**: el esquema SQLite completo vía SQLDelight, las queries
tipadas, y los mappers hacia `domain/model/`. Es la última pieza antes de que exista nada que lea o escriba
red y disco a la vez (eso es la Fase 6, Repository).

## Antes de empezar — qué leer (no los documentos completos)

- **`CLAUDE.md`** completo (es corto, ~225 líneas) — especialmente §3.1 (decimales), §3.6 (trabajo no
  confirmado no se borra), §4 (versión fijada de SQLDelight: `2.3.2`, no otra), §5 (protocolo de checkpoint).
- **`MOBILE_ARCHITECTURE.md` §11 completo** (11.1 a 11.4): el esquema entero, con las reglas duras de tipo de
  columna (C-01) ya declaradas una vez para las 4 tablas de escritura. Es tu fuente de verdad para cada
  `CREATE TABLE`. No la reinventes ni la resumas de memoria — copiala.
- **`MOBILE_ARCHITECTURE.md` §12** (matriz de entidades) — para confirmar qué entra en SQLite y qué no.
- **`MOBILE_ARCHITECTURE.md` §13** (estructura `shared/`) y **§15** (árbol de carpetas) — dónde va cada
  archivo.
- **`MOBILE_ARCHITECTURE.md` §14**, fila "Base de datos local" — por qué SQLDelight y no Room.
- **`MOBILE_ARCHITECTURE.md` §17 y §17.1** — qué se espera testear en esta capa, y la prueba end-to-end
  (esa prueba completa es de Fase 5+SyncEngine, pero la mitad que te toca a vos — crear offline, "cerrar y
  reabrir la app", seguir viendo el dato — sí es de esta fase).
- **`MOBILE_DATA_MAPPING.md` §1.5** (BigDecimal) y la nota operativa que dice literalmente "regla operativa
  para quien codee la Fase 4" — sos vos, ahora.
- **`MOBILE_DATA_MAPPING.md` §1.6** (enums, patrón `UNKNOWN`/`DESCONOCIDO`) y **§1.7** (booleanos).
- Los DTOs que ya existen en `shared/src/commonMain/kotlin/.../data/remote/dto/` (Fase 2). **No definas un
  tipo distinto para el mismo campo.** Si `RegistroAcopioRequest.fechaHora` es `LocalDateTime`, la columna
  local `fecha_hora` mapea a `LocalDateTime` — no a `LocalDate` ni a `Instant`. Si tenés dudas de qué tipo
  usó Fase 2 para un campo, greppeá el DTO antes de decidir, no asumas.

## Alcance de esta fase (y qué NO es)

**Sí construye**: el esquema SQLDelight completo (las 5 tablas de escritura + las 10 de solo lectura de
§11), los `ColumnAdapter` compartidos, los modelos de `domain/model/` que todavía no existen (Fase 1 y 2 no
los crearon — construyeron `core/` y `data/remote/dto/`, no `domain/`), los mappers fila-generada↔dominio, y
una clase "Local Data Source" por grupo de tablas con las queries que necesita el resto de la app.

**No construye** (aunque tu instinto sea "ya que estoy"): el Sync Engine (Fase 5), los `Repository` que
combinan local+remoto (Fase 6, `data/repository/`), ningún `UseCase` (`domain/usecase/`, Fase 6), la
UI (Fase 7+), ni el *scheduling* de la limpieza de retención (Fase 9 — pero sí escribís la query de borrado
en sí, solo que nadie la llama todavía). Si te encontrás escribiendo algo que "combina" SQLite con Ktor,
parate: eso es Fase 6.

## 1. Dependencias y configuración

- Plugin de Gradle `app.cash.sqldelight` versión que resuelva con SQLDelight **`2.3.2`** (fijado en
  `CLAUDE.md §4`). Si no resuelve esa versión exacta, parás y lo reportás — no "actualizás a la que ande".
- Drivers: `sqldelight-android-driver` (`androidMain`, `actual`), `sqldelight-native-driver` (`iosMain`,
  `actual`), y `sqldelight-sqlite-driver` (el JDBC, **`testImplementation`/`jvmTest` únicamente** — nunca
  como `implementation` del target real. Un JDBC driver empaquetado en el APK/IPA no sirve para nada en el
  dispositivo y es peso muerto).
- Los `.sq` van en un source set propio, no en `kotlin/`: `shared/src/commonMain/sqldelight/com/ecolacteos/
  acopio/data/local/*.sq` (la ruta exacta de carpeta la fija el paquete que configures en el plugin —
  confirmalo en el `build.gradle.kts` que generes, no lo des por sentado).
- Nombre de la base/clase generada: `AcopioDatabase` (o el que prefieras, pero documentalo en el checkpoint
  — es el nombre que va a aparecer en cada `actual` de driver y en los tests).

## 2. `domain/model/` — los modelos que todavía no existen

Definí data classes inmutables, sin lógica de negocio, para: `RegistroAcopio`, `AnalisisCalidad`,
`LoteProduccion`, `Venta`, `Proveedor`, `Unidad`, `MotivoObservacion`, `TipoQueso`, `Comunicado`,
`PrediccionProveedor`, `PrecioLitroVigente`, `RutaProveedorOrden`, `RegistroAcopioReferencia` (el que
mapea `registro_acopio_cache` — nombralo distinto de `RegistroAcopio` a propósito: uno es un registro
propio con ciclo de vida de sync, el otro es una referencia de solo lectura a un registro ajeno, y
confundirlos en el código sería el mismo error de diseño que `DATA-013` ya señaló a nivel de contrato).

Campo por campo: mismo tipo que ya decidió Fase 2 para el DTO equivalente donde exista overlap (BigDecimal
vía `com.ionspin.kotlin.bignum`, fechas vía `kotlinx.datetime`, enums los que ya definió Fase 2 —
**reusalos, no los redefinas** en `domain/`). Los enums locales que no tienen contraparte remota
(`SyncStatus`) se definen acá por primera vez.

## 3. `ColumnAdapter`s compartidos

Un solo lugar (`data/local/adapter/` o similar), reusado desde todos los `.sq`:

- **`BigDecimal ↔ TEXT`**: `ColumnAdapter<com.ionspin.kotlin.bignum.decimal.BigDecimal, String>` que llama
  `.toStringExpanded()` (nunca notación científica) al codificar y `BigDecimal.parseString(...)` al
  decodificar. Es la misma regla de `DATA-002` que ya aplicó el serializer JSON de Fase 2, ahora del otro
  lado del Repository.
- **Fechas**: un adapter por tipo real usado (`LocalDateTime ↔ TEXT`, `LocalDate ↔ TEXT`) — **sin
  conversión de zona en ningún sentido** (`DATA-001`). Si Fase 2 ya armó un helper de formato explícito
  para evitar el bug de "segundos omitidos si son cero" (`HoraFormato.kt`), reusalo acá en vez de escribir
  un segundo `.toString()` que puede repetir el mismo bug en el camino a SQLite.
- **Enums con contraparte remota** (`TipoClienteVenta`, `CicloCapital`, `Origen` de
  `registro_acopio_cache`): `ColumnAdapter<T, String>` que hace `.name` al guardar y, al leer, cae al valor
  `DESCONOCIDO`/`UNKNOWN` si el string no matchea ningún valor del enum — mismo criterio defensivo que la
  Fase 2 aplicó a la deserialización JSON, por la misma razón (una fila cacheada puede haber sido escrita
  por una versión anterior de la app con un enum más chico).
- **`SyncStatus`** (sin contraparte remota, la escribe solo esta app): adapter simple `.name`/`valueOf()`
  sin fallback — si esto lanza es un bug propio, no un dato ajeno inesperado. Documentá esta asimetría en
  el checkpoint para que no parezca inconsistencia accidental.
- **Boolean ↔ INTEGER**: confirmá si tu versión de SQLDelight ya lo resuelve nativo con `INTEGER AS
  Boolean` en el `.sq`, o si hace falta un adapter explícito — no lo asumas sin comprobarlo con un test.

## 4. Esquema — tablas de escritura (`MOBILE_ARCHITECTURE.md §11.1`)

Las 5 tablas (`registro_acopio_local`, `analisis_calidad_local`, `lote_produccion_local`,
`lote_produccion_registro_local`, `venta_local`) van **exactamente** como están en §11.1, incluidas:

- Las columnas decimales como `TEXT`, sin excepción — ni `gps_lat`/`gps_lng` porque "no son dinero" (la
  nota operativa de `MOBILE_DATA_MAPPING.md` ya lo advierte explícitamente).
- El `CHECK` de doble referencia al padre en `analisis_calidad_local` y `lote_produccion_registro_local`
  (C-02): **exactamente una** de las dos columnas (`registro_acopio_uuid_cliente` /
  `registro_acopio_server_id`) no-nula, nunca ambas, nunca ninguna.
- `sync_status` con los 5 valores (`PENDING | PENDING_DEPENDENCY | SYNCING | SYNCED | FAILED`) en las 4
  tablas que lo llevan — `lote_produccion_registro_local` **no** tiene columna de sync propia, sincroniza
  junto con su fila padre en `lote_produccion_local`.
- `usuario_id NOT NULL` en las 4 tablas (C-09, multiusuario por dispositivo) — toda query de lectura que
  use esta capa desde una sesión con un usuario activo debe poder filtrar por `usuario_id`; escribí esa
  variante de la query aunque quien la llame (Fase 6+) no exista todavía.

## 5. Esquema — tablas de solo lectura (`MOBILE_ARCHITECTURE.md §11.2`)

Las 10 tablas de catálogo, también exactamente como están. Dos excepciones al patrón general de "se
reemplaza completa en cada `/sync/cambios`" que **no** podés tratar igual que las otras 8:

- **`registro_acopio_cache`**: nunca se borra en masa. Se puebla fila por fila con `INSERT OR REPLACE`
  (o el equivalente `UPSERT` de SQLite) cuando el usuario consulta un proveedor o un registro puntual. Un
  borrado masivo en un ciclo de sync dejaría sin forma de resolver `server_id` a cualquier
  `analisis_calidad_local`/`lote_produccion_registro_local` que dependa de un padre ajeno todavía no
  sincronizado — la tabla existe justamente para que esa referencia sobreviva sin señal.
- **`ruta_zona_cache`**: se descarga bajo demanda por zona (`GET /zonas/{zonaId}/ruta`), no viaja en
  `/sync/cambios`. Su "reemplazo" es acotado a un `zona_id` (borrar las filas de esa zona, insertar las
  nuevas), no un `DELETE` de la tabla entera.

Las otras 8 (`proveedor_cache`, `unidad_cache`, `motivo_observacion_cache`, `tipo_queso_cache`,
`comunicado_cache`, `comunicado_zona_cache`, `prediccion_proveedor_cache`, `precio_litro_vigente_cache`) sí
siguen el patrón simple: `DELETE FROM tabla; INSERT ...` dentro de **una sola transacción** SQLDelight
(`database.transaction { ... }`), para que un fallo a mitad de camino no deje la tabla vacía a medias.

`precio_litro_vigente_cache` es de una sola fila (`id INTEGER PRIMARY KEY CHECK (id = 1)`, `precio`
nullable). Tratá "no hay fila" y "hay fila con `precio = NULL`" como el mismo caso en la query de lectura
(devolvé `PrecioLitroVigente(precio = null, ...)` o directamente `null`, pero elegí uno y documentalo — no
dejes que el llamador tenga que distinguir "tabla vacía" de "precio no configurado", porque el backend
tampoco distingue esos dos casos (`DATA`: `.orElse(null)`).

## 6. Local Data Source — queries mínimas por grupo

No hace falta una clase gigante: una por familia de tablas alcanza (ej. `RegistroAcopioLocalDataSource`,
`CatalogosLocalDataSource`). Cada una expone, como mínimo:

- **Tablas de escritura**: `insertar`, `actualizarEstadoSync(uuidCliente, status, ...)`,
  `actualizarServerId(uuidCliente, serverId, sincronizadoEn)`, `obtenerPorUuidCliente`,
  `obtenerPendientes(usuarioId)` (status `PENDING`/`FAILED` con `next_attempt_at <= ahora`), `observarTodos
  (usuarioId)` como `Flow<List<T>>` (SQLDelight lo expone nativo — no lo envuelvas en un `Flow` manual), y
  `eliminarSincronizadosAntesDe(fecha)` (la query de retención — no la llames desde ningún lado todavía).
- **Tablas de catálogo (las 8 simples)**: `reemplazarTodo(filas: List<T>)` transaccional, `observarTodos()`.
- **`registro_acopio_cache`**: `upsert(fila)`, `obtenerPorServerId(id)`, sin `reemplazarTodo`.
- **`ruta_zona_cache`**: `reemplazarPorZona(zonaId, filas)`.

## 7. Testing

- **Driver de test multiplataforma**: `expect fun crearDriverDeTest(): SqlDriver` en `commonTest`, con
  `actual` que use el driver JDBC en memoria (`JdbcSqliteDriver(IN_MEMORY)`, target JVM — cubre también el
  test unitario de Android, que corre sobre JVM sin contexto real) y `actual` para iOS con
  `NativeSqliteDriver` apuntando a una base en memoria/temporal. Confirmá con un `println`/assert temporal
  que ambos *actual* corren el mismo esquema (`AcopioDatabase.Schema.create(driver)`) antes de escribir el
  resto de los tests sobre esa base — si esto no compila igual en los dos lados, es más barato descubrirlo
  ahora que a mitad de la suite.
- **CRUD de cada tabla** (§17): insertar, leer, actualizar, borrar, sobre el driver en memoria.
- **Reemplazo transaccional**: para al menos una tabla de catálogo simple, verificá que un `reemplazarTodo`
  que falla a mitad de camino (ej. una fila que viola un `CHECK`) deja la tabla en su estado **anterior**,
  no vacía ni parcial — es el motivo de usar una transacción y hay que probar que efectivamente protege.
- **Roundtrip decimal**: `BigDecimal → TEXT → BigDecimal` preserva escala exacta (`"12.50"` no se vuelve
  `"12.5"`), igual que el roundtrip JSON de Fase 2 — ningún `Double` en el camino.
- **Roundtrip de fecha**: mismo espíritu que el bug que encontró Fase 2 (segundos omitidos si son cero) —
  probalo explícitamente acá también, no asumas que el adapter de SQLite hereda la corrección solo porque
  reusa el helper.
- **`CHECK` de doble referencia (C-02)**: un intento de insertar con ambas columnas nulas, o ambas no
  nulas, debe fallar. Es una prueba negativa — no la saltees porque "el `CHECK` ya está en el SQL".
- **Restart simulado** (mitad de §17.1 que te toca): con el driver en memoria, insertar una fila
  `PENDING`, destruir y recrear **solo** la clase `LocalDataSource`/`Database` de más arriba manteniendo
  vivo el mismo `SqlDriver`, y confirmar que la fila sigue ahí. Ojo: un driver JDBC en memoria pierde los
  datos si destruís el `SqlDriver` mismo, no solo la capa de arriba — si tu test recrea el driver también,
  vas a estar probando otra cosa sin darte cuenta.
- **Retención no borra pendientes**: `eliminarSincronizadosAntesDe(fecha)` no toca filas
  `PENDING`/`PENDING_DEPENDENCY`/`SYNCING`/`FAILED` sin importar su fecha (`CLAUDE.md §3.6`).

## Trampas conocidas (verificalas explícitamente, no las asumas resueltas)

| # | Trampa | Por qué importa |
|---|---|---|
| 1 | `REAL` en cualquier columna decimal, incluidas `gps_lat`/`gps_lng`/porcentajes | Reintroduce `DATA-002` en el peor lugar posible: silencioso, sin error de compilación |
| 2 | Confundir el `CHECK` de C-02: exigir ambas columnas no-nulas, o permitir ambas nulas | Rompe tanto el caso "padre propio" como "padre ajeno" |
| 3 | Dar `sync_status` a `lote_produccion_registro_local` | Esa tabla no sincroniza sola, es espejo de su padre |
| 4 | Reemplazo masivo (`DELETE` + `INSERT`) de `registro_acopio_cache` en un ciclo de catálogos | Rompe la resolución de `server_id` de hijos con padre ajeno pendiente — ver §18.1 |
| 5 | `ColumnAdapter` de fecha que vuelve a usar `.toString()` sin el fix de segundos de Fase 2 | Mismo bug, otra capa |
| 6 | Definir un tipo de fecha distinto al que ya usa el DTO equivalente de Fase 2 para el mismo campo | Fuerza una conversión extra en el futuro Repository que no debería existir |
| 7 | Empaquetar el driver JDBC (`sqlite-driver`) como dependencia del target Android/iOS real | Peso muerto en el binario, y en Android puede chocar con el driver real |
| 8 | Adapter de enum remoto sin fallback `DESCONOCIDO` | Una fila cacheada con un valor de enum más nuevo que la versión de la app rompe la lectura completa de esa tabla, no solo esa fila |
| 9 | Tratar "no hay fila en `precio_litro_vigente_cache`" distinto de "hay fila con `precio = NULL`" | El backend tampoco distingue los dos casos; duplicar la distinción en el cliente es inventar un estado que no existe |
| 10 | Recrear el `SqlDriver` completo al simular "cerrar y reabrir la app" en el test de restart | Con un driver en memoria, eso borra los datos por diseño — el test pasaría por la razón equivocada, o fallaría por la razón equivocada |

## Criterios de aceptación

**Verificables localmente en Windows (JVM, sin macOS)**:

- `./gradlew :shared:jvmTest` verde, incluidos todos los tests de §7 de este prompt.
- Los 15 `.sq` (5 de escritura + 10 de catálogo) compilan y generan código sin warnings de SQLDelight.
- `./gradlew :shared:assemble` y `:androidApp:assembleDebug` sin errores (el target Android compila en
  Windows; lo que no corre en Windows es *ejecutar* algo contra un dispositivo/emulador real).
- Ningún `Double` ni columna `REAL` aparece en el esquema generado — greppeable.

**Solo verificables en CI** (`verificacion-android.yml`, `verificacion-ios.yml` — no intentes correrlas
localmente, `CLAUDE.md §8`):

- El target iOS compila con el driver nativo, y el mismo `commonTest` (CRUD, roundtrip, restart simulado)
  corre en verde sobre `NativeSqliteDriver` en el simulador — este es el primer uso real de un driver de
  SQLDelight en iOS de todo el proyecto, no dependas de que "si compiló, funciona": necesitás el run en
  verde, no solo la compilación, antes de cerrar la fase.
- El job de Android corre el mismo `commonTest` vía su unit test JVM-based (no instrumentado, no necesita
  emulador — sigue la misma lógica que ya vale para Fases 1-3).

**No es criterio de esta fase** (aunque sería lindo tenerlo): un test instrumentado real de
`AndroidSqliteDriver` contra un emulador, o de `NativeSqliteDriver` contra un dispositivo físico iOS. Igual
que el Keystore de Fase 3, documentalo como "escrito el driver real, no verificado en dispositivo real" si
no hay forma de correrlo — no lo escondas ni lo des por probado porque el resto de la suite pasó.

## Checkpoint de cierre

Mismo formato de `CLAUDE.md §5` (9 puntos). Puntualmente para esta fase, además:

- Nombre final de la clase de base de datos generada y ruta exacta de los `.sq`.
- Tabla de qué `ColumnAdapter` se usó para cada tipo (decimal, cada tipo de fecha, cada enum, boolean) y si
  terminó siendo nativo de SQLDelight o explícito.
- Confirmación explícita, con el resultado real (no un resumen), de que el mismo `commonTest` corrió en
  JVM, Android (unit) e iOS (nativo) — con los tres verdes o con el gap nombrado si alguno no lo está.
- Si `litros_por_voz` terminó siendo `Boolean` o `Int` — su nombre sugiere flag, pero confirmalo contra
  `MOBILE_DATA_MAPPING.md` antes de decidir, no asumas por el nombre de la columna.

**Detenete después del checkpoint y esperá aprobación explícita antes de la Fase 5** (Sync Engine) — es la
fase que empieza a escribir contra ambas capas a la vez, y no arranca sin que esta quede confirmada.

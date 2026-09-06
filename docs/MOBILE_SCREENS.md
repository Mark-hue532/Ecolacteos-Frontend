# MOBILE_SCREENS.md — Inventario de Pantallas y Capa de Presentación

**Fase 0: Diseño. Ningún código de frontend fue escrito en esta fase.** Tercer documento del plano móvil
del Sistema de Acopio Lechero. Cubre la capa que `MOBILE_ARCHITECTURE.md` y `MOBILE_DATA_MAPPING.md`
deliberadamente no describían: **qué ve y qué hace el usuario**.

> **Los tres documentos de la Fase 0 y su división de trabajo**:
>
> | Documento | Responde | Se usa |
> |---|---|---|
> | [`MOBILE_ARCHITECTURE.md`](./MOBILE_ARCHITECTURE.md) | **cómo** se construye la app: capas, offline-first, sync, conflictos, herramientas, estructura | antes de cada fase, para saber qué construir |
> | [`MOBILE_DATA_MAPPING.md`](./MOBILE_DATA_MAPPING.md) | **qué exactamente** viaja por la red: contrato campo por campo, nullability, tipos KMP/SQLite, `DATA-0xx` | abierto al lado mientras se codean DTOs (Fase 2) y `.sq` (Fase 4) |
> | **`MOBILE_SCREENS.md`** (este) | **qué ve y hace el usuario**: inventario de pantallas por rol, `UiState`/eventos, validaciones, estados vacío/carga/error/offline | desde la Fase 7 (primera vertical) en adelante |
>
> **Regla de precedencia**: ante una discrepancia de **tipo de dato, nullability o nombre de campo**, manda
> `MOBILE_DATA_MAPPING.md`. Ante una de **estructura de tablas, capas o flujo de sync**, manda
> `MOBILE_ARCHITECTURE.md`. Ante una de **comportamiento de pantalla**, manda este documento.

---

## 0. Alcance, método y decisiones de partida

### 0.1 Qué es y qué no es este documento

**Es**: el inventario completo de pantallas, con el contrato de cada una — qué datos necesita, de dónde
salen, qué puede hacer el usuario, qué se valida en el cliente y qué se muestra en cada estado posible
(carga, vacío, error, sin conexión). Es la especificación funcional que un desarrollador necesita para
construir una pantalla sin tener que adivinar, y la que un diseñador necesita para saber qué hay que
dibujar.

**No es**: una guía visual. No define colores, tipografías, iconografía ni maquetación concreta — eso es
el entregable de diseño descrito en §14. Este documento define **estructura y comportamiento**; el diseño
define **apariencia**. Se pueden trabajar en paralelo.

### 0.2 Cómo se derivó

Cada pantalla sale de un endpoint MOBILE ya clasificado en `MOBILE_ARCHITECTURE.md §3.1` (34 endpoints) y
de una tabla local de §11. **No hay ninguna pantalla que consuma un endpoint que no exista**, ni que
escriba en una tabla no declarada. Las validaciones de cada formulario son la traducción literal de las
anotaciones Bean Validation auditadas en `MOBILE_DATA_MAPPING.md §5` — no son criterio propio, son el
contrato real del backend adelantado al cliente para que el usuario vea el error antes de gastar una
llamada de red (que en campo puede no existir).

### 0.3 Decisiones de partida (tomadas, no abiertas)

| Decisión | Valor | Origen |
|---|---|---|
| Tecnología de UI | **Compose Multiplatform**, Android e iOS | `MOBILE_ARCHITECTURE.md §14` (C-06) |
| Ubicación de los `ViewModel` | `shared/presentation/` — se escriben una sola vez | `MOBILE_ARCHITECTURE.md §2` (C-05) |
| RECEPCION | **Dentro** del alcance móvil, online-only | `MOBILE_ARCHITECTURE.md` intro (C-08) |
| Evidencia fotográfica | **Diferida a v2** — no hay captura de foto en v1 | `MOBILE_ARCHITECTURE.md §18.4` (C-07) |
| Idioma | Español (es-PE), única lengua en v1 | §15 |

### 0.4 Resumen del inventario

| Sección | Grupo | Pantallas | Prefijo |
|---|---|---|---|
| §4 | Comunes (todos los roles) | 7 | `S-` |
| §5 | ACOPIADOR | 7 | `A-` |
| §6 | CALIDAD | 8 | `C-` |
| §7 | PRODUCCION | 4 | `P-` |
| §8 | VENTAS | 3 | `V-` |
| §9 | RECEPCION | 4 | `R-` |
| | **Total** | **33** | |

De las 33, **5 son formularios de escritura offline-first** (A-04, C-03, P-03, V-02 y —online-only— R-01),
que son las que concentran el riesgo y donde conviene invertir el esfuerzo de diseño y de pruebas.

---

## 1. Roles y qué ve cada uno

El `rol` llega en `LoginResponse.rol` (`MOBILE_DATA_MAPPING.md §5.1`) y **decide el grafo de navegación
completo**: un ACOPIADOR nunca ve una pantalla de VENTAS, ni siquiera deshabilitada. No es una cuestión de
seguridad —el backend ya valida por `@PreAuthorize` y devuelve 403— sino de no mostrarle a un usuario de
campo funciones que no le corresponden.

| Rol | Pantallas propias | Comunes | Total visible |
|---|---|---|---|
| ACOPIADOR | §5 (7) | §4 (7) | 14 |
| CALIDAD | §6 (8) | §4 (7) | 15 |
| PRODUCCION | §7 (4) | §4 (7) | 11 |
| VENTAS | §8 (3) | §4 (7) | 10 |
| RECEPCION | §9 (4) | §4 (7) | 11 |
| ADMIN | — | §4 (7) | 7 — el móvil **no** es el panel de ADMIN (`MOBILE_ARCHITECTURE.md §3.2`) |

> **ADMIN en el móvil**: el backend autoriza a ADMIN en casi todos los endpoints MOBILE, pero este plano
> **no le construye pantallas propias**. Si un ADMIN inicia sesión en la app, ve el conjunto común y un
> mensaje que lo remite al panel web. Construir la administración en el móvil duplicaría el panel web sin
> razón (RNF-12 y `MOBILE_ARCHITECTURE.md §3.2`).

**Regla de un solo rol por sesión**: `LoginResponse` devuelve **un** `rol`, no una lista. La app no soporta
cambiar de rol sin cerrar sesión, y no hay pantalla de "cambiar de perfil".

---

## 2. Mapa de navegación

```text
S-01 Splash / Bootstrap
 │
 ├── (sin token válido) ──────────► S-02 Login ──┐
 │                                                │
 └── (token válido en Keychain/Keystore) ─────────┤
                                                  ▼
                                        S-03 Home (según rol)
                                                  │
        ┌─────────────────┬─────────────────┬─────┴───────┬──────────────┬──────────────┐
        ▼                 ▼                 ▼             ▼              ▼              ▼
   §5 ACOPIADOR      §6 CALIDAD       §7 PRODUCCION   §8 VENTAS    §9 RECEPCION    Comunes
        │                 │                 │             │              │         S-04 Sync
   A-01 Ruta         C-01 Home        P-01 Home     V-01 Home      R-01 Registrar  S-05 Pendientes
   A-02 Escanear QR  C-02 Elegir reg. P-02 Elegir   V-02 Registrar R-02 Resultado  S-06 Comunicados
   A-03 Buscar prov. C-03 Registrar   P-03 Registrar V-03 Detalle  R-03 Historial  S-07 Ajustes
   A-04 Registrar    C-04 Detalle      P-04 Detalle                R-04 Pagos
   A-05 Historial    C-05 Buscar folio
   A-06 Detalle      C-06 Corrección
   A-07 Confirmar    C-07 Alertas
        comunicado   C-08 Score
```

### 2.1 Reglas de navegación

1. **`S-03 Home` es la raíz del back stack** una vez autenticado. El botón "atrás" desde Home no vuelve al
   Login: minimiza la app. Volver al Login solo ocurre por logout explícito (`S-07`) o por sesión expirada
   detectada en un 401.
2. **Los formularios de captura son destinos, no diálogos.** `A-04`, `C-03`, `P-03`, `V-02` y `R-01` ocupan
   pantalla completa: se llenan con guantes, bajo sol, a veces con una sola mano. Un diálogo modal sobre
   una lista es la forma equivocada.
3. **Guardar navega hacia atrás, no hacia adelante.** Tras guardar una captura offline, se vuelve a la
   pantalla anterior con la fila ya visible (optimista, `MOBILE_ARCHITECTURE.md §16.1`) y un `Snackbar`
   "Guardado — se enviará cuando haya señal". No hay pantalla de confirmación intermedia: agregaría un
   toque a una tarea que se repite decenas de veces por jornada.
4. **`S-05 Pendientes` es accesible desde cualquier pantalla** a través del indicador de sync de la barra
   superior (§13). Es la pantalla a la que el usuario tiene que poder llegar siempre, porque es donde ve si
   su trabajo se está guardando de verdad.
5. **Deep links: no en v1.** No hay notificaciones push (`MOBILE_ARCHITECTURE.md §5`: "no hay push") ni
   URLs externas que abran la app, así que no hay caso de uso todavía.
6. **Restauración de estado**: al morir el proceso y volver, la app reabre `S-03 Home`, no la pantalla
   donde estaba. La excepción son los formularios de captura: su borrador se persiste (§3.4) y al volver se
   ofrece retomarlo.

---

## 3. Contrato de la capa de presentación

Todo lo de esta sección vive en `shared/presentation/` (`MOBILE_ARCHITECTURE.md §13`) y se escribe **una
sola vez** para Android e iOS.

### 3.1 El patrón: `UiState` / `Event` / `Effect`

```kotlin
// Un ViewModel por pantalla. Tres tipos por pantalla, siempre los mismos tres.

// 1. UiState — inmutable, contiene TODO lo necesario para dibujar la pantalla.
//    Si algo se ve en la pantalla y no está acá, está mal.
data class XxxUiState(
    val cargando: Boolean = false,
    val datos: List<Algo> = emptyList(),
    val error: MensajeError? = null,
    val hayConexion: Boolean = true,
    val pendientesDeSync: Int = 0,
)

// 2. Event — lo que el usuario hace. La UI solo emite eventos, nunca llama un UseCase.
sealed interface XxxEvent {
    data class LitrosCambiaron(val valor: String) : XxxEvent
    data object GuardarPresionado : XxxEvent
    data object ReintentarPresionado : XxxEvent
}

// 3. Effect — acciones de una sola vez, que NO son estado: navegar, mostrar un snackbar,
//    pedir un permiso, abrir la cámara. Se consumen una vez y se descartan.
sealed interface XxxEffect {
    data class Navegar(val destino: Destino) : XxxEffect
    data class MostrarMensaje(val texto: String) : XxxEffect
    data object PedirPermisoUbicacion : XxxEffect
}
```

**Por qué `Effect` es un tipo aparte y no un campo del `UiState`**: si "navegar a la pantalla siguiente"
fuera un booleano del estado, al rotar la pantalla o volver del background se volvería a disparar. Los
efectos se emiten por un `Channel`/`SharedFlow` y se consumen exactamente una vez. Es un error clásico y
la razón por la que este contrato es obligatorio y no una sugerencia.

### 3.2 Envoltorio de carga asíncrona

Para toda lectura remota se usa un único tipo, así todas las pantallas manejan los mismos cuatro casos y
ninguna se olvida del tercero:

```kotlin
sealed interface Async<out T> {
    data object Inicial : Async<Nothing>
    data object Cargando : Async<Nothing>
    data class Exito<T>(val datos: T, val desdeCache: Boolean) : Async<T>
    data class Fallo(val error: ApiError, val datosPrevios: Any? = null) : Async<Nothing>
}
```

`desdeCache` y `datosPrevios` existen por el carácter offline-first: una pantalla casi nunca está
"vacía por error" — normalmente tiene datos viejos de SQLite y encima un aviso de que no se pudieron
refrescar. **Mostrar una pantalla de error a pantalla completa tapando datos que sí tenemos en caché es el
antipatrón que este tipo previene.**

### 3.3 Regla dura de dependencias

```text
UI (Compose)  →  ViewModel  →  UseCase  →  Repository  →  SQLite / Ktor
     ✗ jamás salta un nivel     ✗ jamás ve un DTO       ✗ jamás lo ve la UI
```

- Un `@Composable` **nunca** inyecta un `Repository`, un `UseCase` ni un `HttpClient`.
- Un `ViewModel` **nunca** importa un DTO de `data/remote/dto/`. Trabaja con modelos de dominio.
- El `UiState` **nunca** contiene un `BigDecimal` crudo para mostrar: contiene el `String` ya formateado
  (§10.1) **y**, si hace falta operar, el `BigDecimal` aparte. Formatear dentro del `@Composable` disemina
  la lógica de escala por toda la UI.

### 3.4 Borradores de formulario

Los cinco formularios de captura persisten su borrador en SQLite (una tabla `borrador_formulario` con
`pantalla TEXT PRIMARY KEY, payload_json TEXT, actualizado_en TEXT`) en cada cambio de campo, con debounce
de ~500 ms. Si el proceso muere a mitad de una captura —lo normal en un teléfono de gama baja con la
cámara recién usada— al volver se ofrece "Tenés un registro sin terminar, ¿lo retomás?".

Un borrador se borra al guardar la captura o al descartarlo explícitamente. **Un borrador no es un
registro pendiente**: no aparece en `S-05`, no se sincroniza, no cuenta en los contadores. Es solo
protección contra perder lo tipeado.

---

## 4. Pantallas comunes (`S-`)

### S-01 · Splash / Bootstrap

**Rol** todos · **Modo** offline OK · **Fuente** `SecureTokenStorage`

Decide a dónde entrar sin pedirle nada al usuario. Lógica:

```text
¿hay token en SecureTokenStorage?
  no  → S-02 Login
  sí  → ¿expiración local (guardada como now + expiraEnSegundos) ya pasó?
          sí → S-02 Login, con aviso "tu sesión venció"
          no → ¿quedan < 30 min y hay conexión?
                 sí → intentar POST /api/auth/refresh en background, no bloquear
                 no → seguir
               → S-03 Home
```

**Estados**: solo carga. Máximo ~800 ms; si tarda más (abrir SQLite, migraciones), se muestra el indicador
en vez de una pantalla en blanco.

> **Importante**: el splash **nunca** bloquea esperando red. Un ACOPIADOR que abre la app sin señal debe
> llegar a Home igual de rápido que con señal. El refresh proactivo es oportunista y silencioso.

---

### S-02 · Login

**Rol** público · **Modo** ONLINE-ONLY · **Endpoint** `POST /api/auth/login`

| Campo | Validación cliente | Origen |
|---|---|---|
| `email` | no vacío + formato de email | `@NotBlank @Email` (`MOBILE_DATA_MAPPING.md §5.1`) |
| `password` | no vacío | `@NotBlank` |

```kotlin
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val verPassword: Boolean = false,
    val enviando: Boolean = false,
    val errorEmail: String? = null,
    val errorPassword: String? = null,
    val errorGeneral: String? = null,
    val hayConexion: Boolean = true,
    val puedeEnviar: Boolean = false,   // email y password no vacíos && !enviando
)
```

**Estados**
- **Sin conexión**: el botón se deshabilita con el texto "Necesitás conexión para iniciar sesión". No se
  intenta la llamada para no hacer esperar 30 s a un timeout inevitable.
- **401**: "Correo o contraseña incorrectos". **Nunca** se distingue cuál de los dos falló.
- **5xx / timeout**: "No pudimos conectarnos. Revisá tu señal e intentá de nuevo", con botón reintentar.

**Notas**
- La contraseña **jamás** se guarda, ni cifrada (`MOBILE_ARCHITECTURE.md §4`).
- Al éxito se guardan `token`, `rol`, `nombre` y `now + expiraEnSegundos` en `SecureTokenStorage`, y se
  navega a `S-03` limpiando el back stack.
- **Caso crítico**: si al iniciar sesión hay filas locales de **otro** `usuario_id` (C-09), no se borran ni
  se muestran; quedan invisibles hasta que ese usuario vuelva. Se registra en el log local, no en la UI.

---

### S-03 · Home (según rol)

**Rol** todos · **Modo** offline OK · **Fuente** SQLite (todo) 

Punto de partida de cada jornada. Contiene, para todos los roles:

- Saludo con `nombre` y `rol` de la sesión.
- **Estado de sincronización** siempre visible: nº de pendientes, si hay errores, hora del último sync
  exitoso. Toca → `S-04`.
- **Acción principal del rol**, como botón grande y primero en el orden de lectura: registrar acopio
  (ACOPIADOR), registrar análisis (CALIDAD), registrar lote (PRODUCCION), registrar venta (VENTAS),
  registrar recepción (RECEPCION).
- Accesos secundarios del rol y comunicados no leídos.

```kotlin
data class HomeUiState(
    val nombre: String = "",
    val rol: Rol = Rol.UNKNOWN,
    val accionPrincipal: AccionRol? = null,
    val accesosSecundarios: List<AccionRol> = emptyList(),
    val resumenSync: ResumenSync = ResumenSync(),   // pendientes, conDependencia, conError, ultimoSyncOk
    val comunicadosNoLeidos: Int = 0,
    val hayConexion: Boolean = true,
    val datosDesactualizados: Boolean = false,       // último /sync/cambios hace > 24 h
)
```

**Estados**
- **Sin conexión**: banner discreto y persistente, no un diálogo. La app funciona; el usuario solo debe
  saberlo. Nunca bloquea nada offline-first.
- **Catálogos vacíos** (primer arranque sin haber sincronizado nunca): estado vacío explícito, "Necesitás
  conectarte una primera vez para descargar proveedores y catálogos", con botón sincronizar. Es el único
  caso donde la app **no puede** operar offline, y hay que decirlo sin rodeos.
- **Datos desactualizados** (> 24 h sin `/sync/cambios`): aviso "Los catálogos se actualizaron hace X".
  No bloquea: un ACOPIADOR puede pasar dos días sin señal y debe poder seguir capturando.

---

### S-04 · Estado de sincronización

**Rol** todos · **Modo** offline OK · **Fuente** las 4 tablas `*_local` + marca de último sync

Panel de diagnóstico. Muestra por recurso: cuántos `PENDING`, `PENDING_DEPENDENCY`, `SYNCING` y `FAILED`,
la fecha del último ciclo exitoso, y si hay conexión ahora.

**Eventos**: `SincronizarAhoraPresionado` (fuerza un ciclo, `MOBILE_ARCHITECTURE.md §6.5`),
`VerPendientesPresionado` → `S-05`.

**Estados**
- **Todo sincronizado y con señal**: estado vacío positivo — "Todo al día", con la hora del último sync.
- **Sincronizando**: progreso por recurso, no una barra global indeterminada. El usuario quiere saber
  *qué* se está subiendo.
- **Sin conexión con pendientes**: "N registros esperando señal". Explícitamente **no** es un error.

---

### S-05 · Pendientes

**Rol** todos · **Modo** offline OK · **Fuente** las 4 tablas `*_local` con estado ≠ `SYNCED`

**La pantalla más importante del modo offline y la más fácil de subestimar.** Es donde el usuario ve que
su trabajo existe, y donde arregla lo que el servidor rechazó. Sin ella, un registro que falla por regla de
negocio desaparece en silencio y se pierde una entrega de leche.

Tres secciones, en este orden:

| Sección | Estados | Qué puede hacer el usuario |
|---|---|---|
| **Con error** (arriba) | `FAILED` permanente | Ver el `motivo` tal cual lo mandó el backend, editar el registro y reintentar, o descartarlo |
| **Esperando otra entrega** | `PENDING_DEPENDENCY` | Solo ver. Explica qué está esperando. Si lleva > 3 días, se marca como advertencia |
| **Por enviar** | `PENDING`, `SYNCING` | Ver. Forzar sync si hay señal |

```kotlin
data class PendientesUiState(
    val conError: List<ItemPendiente> = emptyList(),
    val esperandoDependencia: List<ItemPendiente> = emptyList(),
    val porEnviar: List<ItemPendiente> = emptyList(),
    val hayConexion: Boolean = true,
    val sincronizando: Boolean = false,
)

data class ItemPendiente(
    val uuidCliente: String,
    val recurso: Recurso,                 // ACOPIO | CALIDAD | LOTE | VENTA
    val resumen: String,                  // "120.50 L — Juan Pérez — 04/09 16:20"
    val estado: EstadoSync,
    val motivoError: String? = null,      // tal cual lo devolvió el backend en errores[].motivo
    val intentos: Int = 0,
    val diasEsperando: Int = 0,
)
```

**Reglas**
1. El `motivo` del backend se muestra **literal**, sin reinterpretar ni traducir. Es el mensaje que el
   Service escribió y suele ser accionable ("proveedor inactivo", "fecha fuera de tolerancia"). Inventar un
   texto genérico encima destruiría el único dato útil.
2. **Editar y reintentar** cambia el estado a `PENDING` y resetea `sync_attempts`. Mantiene el mismo
   `uuidCliente` — la idempotencia del backend (`MOBILE_ARCHITECTURE.md §7`) hace que sea seguro.
3. **Descartar** pide confirmación explícita nombrando lo que se pierde ("Se va a borrar el registro de
   120.50 L de Juan Pérez del 4/9. No se puede deshacer"). Es la única forma de que salga trabajo no
   confirmado de la base (`MOBILE_ARCHITECTURE.md §11.4`).
4. Esta pantalla es la que **desbloquea el logout** (`MOBILE_ARCHITECTURE.md §4`): mientras tenga filas,
   `S-07` no deja cerrar sesión sin avisar.

**Estados**: vacío = "No tenés registros pendientes" (positivo, no un error).

---

### S-06 · Comunicados

**Rol** todos · **Modo** READ-CACHE (offline real) · **Fuente** `comunicado_cache` + `comunicado_zona_cache`

Lista de comunicados descargados por `/api/sync/cambios`. Solo lectura para todos los roles; el ACOPIADOR
además puede confirmar que se lo mostró a un proveedor (`A-07`).

**Campos**: `mensaje`, `fecha` (⚠️ es `LocalDateTime`, no `LocalDate` — `MOBILE_DATA_MAPPING.md §5.6`),
`zonasNombres`.

**Estados**: vacío = "No hay comunicados"; sin conexión = se lee del cache normalmente, con la marca de
cuándo se actualizó.

---

### S-07 · Ajustes y sesión

**Rol** todos · **Modo** offline OK

Contiene: nombre y rol de la sesión, versión de la app, hora del último sync, y **cerrar sesión**.

**El logout es el punto delicado** (`MOBILE_ARCHITECTURE.md §4`, C-09):

```text
¿hay filas locales con estado ≠ SYNCED del usuario actual?
  no  → cerrar sesión normal: borra token + caches personales + filas SYNCED
  sí  → NO cerrar. Mostrar: "Tenés N registros sin enviar."
        Opciones:
          [Sincronizar ahora]        (si hay señal; al terminar, reevaluar)
          [Ver pendientes]           → S-05
          [Cerrar sesión igual]      → conserva las filas locales, borra solo token
                                        y caches personales. Advertencia explícita de que
                                        los registros siguen en este dispositivo y se
                                        enviarán cuando este mismo usuario vuelva a entrar.
          [Cancelar]
```

Nunca existe un camino que borre trabajo no sincronizado sin que el usuario lo haya elegido leyendo
exactamente qué se pierde.

---

## 5. ACOPIADOR (`A-`)

El rol de campo por excelencia: sin señal la mayor parte del tiempo, con guantes, a la intemperie, y con
decenas de capturas por jornada. **Es el rol que justifica que toda la app sea offline-first.**

### A-01 · Ruta del día

**Modo** READ-CACHE (offline real) · **Fuente** `ruta_zona_cache` · **Endpoint** `GET /api/zonas/{zonaId}/ruta`

Lista ordenada de proveedores a visitar, con `orden` y `horaEstimada` (**nullable** —
`MOBILE_DATA_MAPPING.md §5.8`). Junto a cada proveedor, si ya se le registró una entrega hoy, se marca
como visitado (dato local: `registro_acopio_local` de hoy).

**Estados**
- **Vacío**: "No hay ruta definida para tu zona" — la ruta la define ADMIN desde el panel web
  (`PUT /api/zonas/{zonaId}/ruta` es WEB/ADMIN), así que el móvil no ofrece crearla.
- **Sin conexión**: se lee del cache con la marca de última descarga. La ruta **no** viaja en
  `/sync/cambios` (gap §18.5), así que hay que haberla abierto al menos una vez con señal.
- **`horaEstimada` nula**: se omite la hora, no se muestra "--:--" ni se inventa.

---

### A-02 · Escanear QR de proveedor

**Modo** OFFLINE REAL · **Fuente** `proveedor_cache` (resolución local) · **Permiso** cámara

Escanea el QR y resuelve **contra SQLite primero** (`MOBILE_ARCHITECTURE.md §3.3`). Solo si el código no
está en cache y hay señal, consulta `GET /api/proveedores/qr/{codigoQr}`.

```text
QR escaneado
 → buscar en proveedor_cache por codigo_qr
      encontrado    → navegar a A-04 con el proveedor precargado
      no encontrado → ¿hay conexión?
                        sí → GET /api/proveedores/qr/{codigoQr}
                               200 → guardar en cache, ir a A-04
                               404 → "Este código no corresponde a ningún proveedor"
                        no → "No reconocemos este código y no hay señal para consultarlo.
                              Podés buscar al proveedor por nombre." → A-03
```

**Estados**
- **Permiso de cámara denegado**: explicación + botón a los ajustes del sistema. Nunca se queda en una
  pantalla negra sin explicación.
- **`codigoQr` nulo en el cache**: algunos proveedores pueden no tenerlo (`MOBILE_DATA_MAPPING.md §5.6`,
  NULLABILITY_NOTE). Esos simplemente no se resuelven por QR; siempre queda `A-03`.

---

### A-03 · Buscar proveedor

**Modo** OFFLINE REAL · **Fuente** `proveedor_cache`

Búsqueda por nombre sobre el cache local, con la zona como dato de apoyo. Es el camino alternativo cuando
el QR está roto, mojado o el proveedor no lo tiene.

**Estados**: vacío con catálogo descargado = "Ningún proveedor coincide"; vacío sin catálogo = remite a
sincronizar (mismo caso que `S-03`).

---

### A-04 · Registrar acopio ★

**Modo** OFFLINE-FIRST · **Escribe** `registro_acopio_local` · **Endpoint** `POST /api/sync/registros-acopio`

**La pantalla central de todo el producto.** Es la que más veces se usa por jornada y la que no puede
fallar sin señal.

| Campo | Fuente en UI | Obligatorio | Validación cliente | Origen de la regla |
|---|---|---|---|---|
| `proveedorId` | precargado por `A-02`/`A-03`, no editable a mano | Sí | debe existir en `proveedor_cache` | `@NotNull` |
| `unidadId` | selector desde `unidad_cache` | Sí | uno de la lista | `@NotNull` |
| `fechaHora` | ahora por defecto, editable | Sí | no futura; aviso si > 24 h en el pasado | `@NotNull` |
| `litros` | teclado decimal | Sí | `>= 0`, máx. 6 enteros + 2 decimales | `@DecimalMin("0.0")`, `precision=8, scale=2` |
| `gpsLat`/`gpsLng` | automático | **No** | — | sin `@NotNull` |
| `motivoObservacionId` | selector desde `motivo_observacion_cache` | **No** | — | nullable |
| `litrosPorVoz` | interno, lo marca la app | **No** | — | nullable, el Service trata `null` como `false` |

```kotlin
data class RegistrarAcopioUiState(
    val proveedor: ProveedorResumen? = null,
    val unidades: List<UnidadOpcion> = emptyList(),
    val unidadSeleccionada: UnidadOpcion? = null,
    val fechaHora: LocalDateTime = ahora(),
    val litrosTexto: String = "",              // texto crudo del input
    val litros: BigDecimal? = null,            // parseado, para validar
    val litrosFormateado: String = "",         // "120.50" para mostrar
    val motivos: List<MotivoOpcion> = emptyList(),
    val motivoSeleccionado: MotivoOpcion? = null,
    val gps: EstadoGps = EstadoGps.Buscando,   // Buscando | Obtenido(lat,lng) | NoDisponible | SinPermiso
    val errorLitros: String? = null,
    val errorFecha: String? = null,
    val avisoFechaPasada: String? = null,
    val guardando: Boolean = false,
    val puedeGuardar: Boolean = false,
    val hayConexion: Boolean = true,
)
```

**Comportamiento del GPS** — `gpsLat`/`gpsLng` son opcionales por contrato, y eso es deliberado: en campo
puede no haber fix. Reglas:

- Se pide la ubicación al abrir la pantalla, **en paralelo** al llenado del formulario. Nunca bloquea.
- Si no hay fix en **15 segundos**, se marca `NoDisponible` y **se guarda igual, con GPS nulo**.
- Si el permiso está denegado, se guarda con GPS nulo y se muestra un aviso discreto una sola vez.
- **Nunca** se impide guardar una entrega por falta de GPS. La leche ya se recibió; el registro es lo
  importante.

**Al guardar**
1. Se genera `uuidCliente` = UUID v4 en el dispositivo.
2. Se escribe en `registro_acopio_local` con `sync_status = PENDING` y `usuario_id` de la sesión.
3. Se navega atrás, con `Snackbar`: "Guardado — se enviará cuando haya señal".
4. Se dispara sync oportunista (`MOBILE_ARCHITECTURE.md §16.1`), que puede fallar sin que al usuario le
   importe ni se entere.

> **Nunca hay un spinner "enviando" bloqueante en esta pantalla.** Guardar es una escritura local: es
> instantáneo y no puede fallar por red. Ese es el punto entero del diseño offline-first.

**Sin foto en v1** (C-07): no hay botón de cámara para evidencia fotográfica. Ver
`MOBILE_ARCHITECTURE.md §18.4`.

---

### A-05 · Historial de entregas del proveedor

**Modo** ONLINE + CACHE · **Fuente** `registro_acopio_local` (propias) + `registro_acopio_cache` (ajenas) ·
**Endpoint** `GET /api/registros-acopio/proveedor/{proveedorId}`

Lista combinada de las entregas locales de este dispositivo y las descargadas del servidor. Cada fila
muestra `fechaHora`, `litros`, si tiene observación, y **el estado de sync** si es local no confirmada.

**Estados**
- **Sin conexión**: se muestra lo local + lo cacheado, con aviso "puede haber entregas más recientes".
- **Duplicados posibles** (`DATA-013`): si una entrega está tanto local como descargada, se prioriza la
  fila local cuando `registro_acopio_local.server_id == registro_acopio_cache.id`. El resto de solapamientos
  no es detectable sin `uuidCliente` en el DTO resumen; se acepta para v1.
- **Sin paginación**: no existe en el backend (`MOBILE_DATA_MAPPING.md §7`). No se inventa scroll infinito.

---

### A-06 · Detalle de registro de acopio

**Modo** ONLINE + CACHE · **Endpoint** `GET /api/registros-acopio/{id}`

Todos los campos del registro, incluido `motivoObservacion` (⚠️ es la **descripción**, no el id — es el
`NAME_MISMATCH` documentado en `MOBILE_DATA_MAPPING.md §5.2`).

**Regla de fechas (§10.3)**: `fechaHora` y `sincronizadoEn` se muestran **etiquetados y por separado**
("Capturado" / "Sincronizado"), nunca como una duración entre ambos (`DATA-012`).

**Sin edición**: `RegistroAcopio` es inmutable en el dominio (`MOBILE_ARCHITECTURE.md §8`). Si el usuario
tiene rol CALIDAD, aparece la acción "Registrar corrección" → `C-06`. Para ACOPIADOR, no hay acción.

---

### A-07 · Confirmar comunicado a proveedor

**Modo** ONLINE-ONLY · **Endpoint** `POST /api/comunicados/{id}/confirmaciones`

Registra que se le mostró un comunicado a un proveedor.

**Estados**
- **Sin conexión**: la acción se deshabilita con el texto "Requiere conexión". **No se encola.** El
  endpoint no es idempotente (`DATA-005` / `MOBILE_ARCHITECTURE.md §18.2`): un reintento crearía una
  confirmación duplicada. Es una limitación real del backend y la UI la refleja en vez de esconderla.
- **Éxito**: la confirmación queda marcada en la lista de `S-06`.

> Cuando §18.2 se implemente en el backend, esta pantalla pasa a OFFLINE-FIRST sin más cambios que
> encolarla como los otros cuatro recursos.

---

## 6. CALIDAD (`C-`)

Trabaja en planta o en campo, sobre entregas que **normalmente capturó otro dispositivo**. Por eso es el
rol donde la resolución de referencias (`MOBILE_ARCHITECTURE.md §18.1`) se vuelve visible al usuario.

### C-01 · Home calidad

**Modo** offline OK · **Fuente** `registro_acopio_cache` + `analisis_calidad_local`

Lista de entregas recientes con su estado de análisis (analizada / sin analizar), y acceso a la acción
principal.

---

### C-02 · Seleccionar registro de acopio a analizar ★

**Modo** parcialmente offline · **Fuente** `registro_acopio_local` + `registro_acopio_cache`

**Esta es la pantalla donde se materializa el gap §18.1.** El usuario elige a qué entrega le va a asociar
el análisis, y no todas las entregas son elegibles.

Cada fila cae en uno de tres casos:

| Caso | Origen | ¿Se puede analizar offline? | Qué muestra la UI |
|---|---|---|---|
| Entrega **ajena ya descargada** | `registro_acopio_cache` (tiene `id` de servidor) | **Sí** — se referencia por `registro_acopio_server_id` | Normal, seleccionable |
| Entrega **propia ya sincronizada** | `registro_acopio_local` con `server_id` | **Sí** | Normal, seleccionable |
| Entrega **propia sin sincronizar** | `registro_acopio_local` sin `server_id` | **Sí, pero el análisis queda retenido** | Seleccionable, con aviso: "Esta entrega todavía no se envió. El análisis se guardará y se enviará cuando la entrega se sincronice" → el hijo nace en `PENDING_DEPENDENCY` |

**El caso que no se puede resolver**: una entrega capturada offline en **otro** dispositivo que tampoco
sincronizó. No existe en ningún lado localmente, así que no aparece en la lista y no hay nada que
seleccionar. Si el usuario sabe que existe, el mensaje del estado vacío lo explica: *"Si la entrega que
buscás se registró recién en otro dispositivo, va a aparecer cuando ambos tengan señal."* Esa es la
limitación real de `DATA-003`, dicha con todas las letras en vez de disfrazada de error.

**Estados**
- **Sin conexión y sin cache**: estado vacío con la explicación de arriba.
- **Con conexión**: se refresca `GET /api/registros-acopio/proveedor/{id}` y se puebla
  `registro_acopio_cache`.

---

### C-03 · Registrar análisis de calidad ★

**Modo** OFFLINE-FIRST (con dependencia) · **Escribe** `analisis_calidad_local` ·
**Endpoint** `POST /api/sync/analisis-calidad`

| Campo | Obligatorio | Validación cliente | Origen |
|---|---|---|---|
| `registroAcopioId` | Sí | viene de `C-02`, no editable | `@NotNull` |
| `folioMuestra` | Sí | no vacío, máx. **40** caracteres | `@NotBlank`, `length=40` |
| `agua` | No | `precision=5, scale=2` | sin `@NotNull` |
| `proteina` | No | `precision=5, scale=2` | sin `@NotNull` |
| `lactosa` | No | `precision=5, scale=2` | sin `@NotNull` |
| `densidad` | No | `precision=6, scale=2` | sin `@NotNull` |
| `temperatura` | No | `precision=5, scale=2` | sin `@NotNull` |
| `ph` | No | `precision=4, scale=2` | sin `@NotNull` |
| `aguaAnadida` | No | switch, default `false` | nullable, Service trata `null` como `false` |

> **Los 6 parámetros de laboratorio son opcionales por contrato** y eso es intencional: el lactoscan puede
> no reportar todos los valores (`MOBILE_DATA_MAPPING.md §3`). La UI **no** debe exigirlos ni bloquear el
> guardado por un campo vacío. Un campo vacío se envía `null`, no `0` — son cosas distintas.

**`resultado` no se muestra en esta pantalla**: lo calcula el servidor (`APROBADO`/`RECHAZADO` según
`aguaAnadida`) y solo aparece cuando el análisis se sincroniza (`C-04`). Mostrar un resultado "previsto"
antes de que el servidor lo confirme sería inventar un dato del contrato.

---

### C-04 · Detalle de análisis

**Modo** ONLINE + CACHE · **Endpoint** `GET /api/analisis-calidad/registro/{registroAcopioId}`

Muestra los 6 parámetros (los nulos se omiten, no se muestran como `0`), `aguaAnadida` y `resultado`.

**`resultado` es un enum abierto**: la app debe soportar `APROBADO`, `RECHAZADO`, `OBSERVADO` y un
`UNKNOWN` de reserva (`MOBILE_DATA_MAPPING.md §1.6`). `OBSERVADO` existe en el dominio aunque hoy ningún
código lo asigne, y un valor no reconocido se muestra tal cual llegó en vez de romper la pantalla.

---

### C-05 · Buscar análisis por folio

**Modo** ONLINE-ONLY · **Endpoint** `GET /api/analisis-calidad/folio/{folio}`

Contraste contra el servidor. El path param **no es un UUID**: es texto libre de hasta 40 caracteres
(`MOBILE_DATA_MAPPING.md §6`).

**Estados**: sin conexión = deshabilitado con explicación; sin resultados = "No encontramos ese folio".

---

### C-06 · Registrar corrección de litros

**Modo** ONLINE-ONLY · **Endpoint** `POST /api/registros-acopio/{id}/correcciones`

| Campo | Obligatorio | Validación |
|---|---|---|
| `litrosCorregido` | Sí | `>= 0` (`@DecimalMin("0.0")`) |
| `motivo` | **No** | libre |

**Sin conexión**: bloqueado con explicación. El endpoint **no es idempotente** (`DATA-004` /
`MOBILE_ARCHITECTURE.md §18.7`): un reintento crearía una corrección duplicada, y una corrección duplicada
corrompe la trazabilidad de litros, que es justamente para lo que existe la tabla. Se prefiere bloquear a
arriesgar.

**Confirmación obligatoria** antes de enviar, mostrando el valor anterior y el nuevo: es una operación de
auditoría y no debe poder dispararse por un toque accidental.

---

### C-07 · Alertas de anomalías

**Modo** ONLINE-ONLY · **Endpoint** `GET /api/innovacion/alertas?zonaId={UUID}`

⚠️ **`zonaId` es un query param obligatorio** — sin él el backend responde 400
(`MOBILE_DATA_MAPPING.md §7`). La UI debe exigir elegir zona antes de consultar; nunca llamar sin él.

Muestra `tipo` (`VOLUMEN_ATIPICO`/`RIESGO_ADULTERACION`), `severidad` (`BAJA`/`MEDIA`/`ALTA`), `zScore`
(**nullable**) y el proveedor. Ambos enums con `UNKNOWN` de reserva.

---

### C-08 · Score de confianza del proveedor

**Modo** ONLINE-ONLY · **Endpoint** `GET /api/innovacion/score/{proveedorId}`

`score` (0–100) y sus tres componentes. **404 no es un error a mostrar como falla**: significa que ese
proveedor todavía no tiene histórico. Estado vacío: "Este proveedor aún no tiene score calculado".

---

## 7. PRODUCCION (`P-`)

### P-01 · Home producción

**Modo** offline OK · **Fuente** `lote_produccion_local` + cache

Lotes recientes con su rendimiento y estado de sync.

---

### P-02 · Seleccionar registros de acopio para el lote ★

**Modo** parcialmente offline · **Fuente** igual que `C-02`

Selección **múltiple** (`registroAcopioIds` es `@NotEmpty`: mínimo 1). Aplican exactamente las mismas tres
categorías de elegibilidad de `C-02`, con una diferencia importante:

> **Si cualquiera de los registros seleccionados es propio sin sincronizar, el lote entero queda en
> `PENDING_DEPENDENCY`** hasta que *todos* sus padres tengan `server_id`. La UI lo dice al seleccionar, no
> al guardar: "2 de las 5 entregas elegidas todavía no se enviaron; el lote se enviará cuando se
> sincronicen".

Muestra el total de litros de lo seleccionado como ayuda para llenar `litrosUsados`, pero **no lo
autocompleta**: son cosas distintas (se puede usar parte de una entrega).

---

### P-03 · Registrar lote de producción ★

**Modo** OFFLINE-FIRST (con dependencia) · **Escribe** `lote_produccion_local` +
`lote_produccion_registro_local` · **Endpoint** `POST /api/sync/lotes-produccion`

| Campo | Obligatorio | Validación | Origen |
|---|---|---|---|
| `fecha` | Sí | fecha válida, no futura | `@NotNull` |
| `tipoQuesoId` | Sí | selector desde `tipo_queso_cache` (solo `activo`) | `@NotNull` |
| `litrosUsados` | Sí | `>= 0`, `precision=9, scale=2` | `@DecimalMin("0.0")` |
| `unidadesObtenidas` | Sí | entero `>= 0` | `@NotNull @Min(0)` |
| `registroAcopioIds` | Sí | **mínimo 1** | `@NotEmpty` |

**`rendimientoPct` no se muestra al capturar**: lo calcula el servidor y **solo si `litrosUsados > 0`** —
con `litrosUsados = 0` (permitido) el campo vuelve `null` (`MOBILE_DATA_MAPPING.md §5.4`,
NULLABILITY_NOTE). La UI de detalle debe manejar ese nulo sin mostrar "0%".

---

### P-04 · Detalle de lote

**Modo** ONLINE + CACHE · **Endpoint** `GET /api/lotes-produccion/{id}`

Compara `rendimientoPct` (real, **nullable**) contra `rendimientoEsperadoPct` (del `TipoQueso`, siempre
presente). Si el real es `null`, se muestra "No calculado" y **no** se dibuja la comparación.

---

## 8. VENTAS (`V-`)

### V-01 · Home ventas

**Modo** offline OK · **Fuente** `venta_local`

Ventas del día con su total y estado de sync.

---

### V-02 · Registrar venta ★

**Modo** OFFLINE-FIRST · **Escribe** `venta_local` · **Endpoint** `POST /api/sync/ventas`

**Es la vertical candidata para la Fase 7** (`MOBILE_DATA_MAPPING.md §13`): offline-first puro, sin
dependencias cruzadas de ids.

| Campo | Obligatorio | Validación | Origen |
|---|---|---|---|
| `fecha` | Sí | fecha válida | `@NotNull` |
| `tipoCliente` | Sí | **selector cerrado de 3 opciones** | ver recuadro |
| `tipoQuesoId` | Sí | selector desde `tipo_queso_cache` | `@NotNull` |
| `cantidad` | Sí | entero **`>= 1`** (no 0) | `@NotNull @Min(1)` |
| `precioUnitario` | Sí | `>= 0`, `precision=8, scale=2` | `@DecimalMin("0.0")` |

> ### ⚠️ `tipoCliente` — requisito de UI no negociable (`DATA-010`)
>
> El backend recibe este campo como `String` y hace `TipoClienteVenta.valueOf(...)` a mano. Un valor que no
> sea **exactamente** `MAYORISTA`, `PROVEEDOR` o `PUBLICO` lanza `IllegalArgumentException`, que no está
> capturada en el `GlobalExceptionHandler` → el cliente recibe **500 Internal Server Error**, no un 400.
>
> **Por lo tanto**: este campo se implementa como selector cerrado de exactamente 3 opciones. **Nunca** un
> campo de texto libre, nunca un autocompletado, nunca un valor por defecto escrito a mano. Es la
> mitigación de cliente que evita un 500 en producción, y está aquí porque es una decisión de UI, no de
> capa de red.

**El `total` no se calcula ni se muestra antes de guardar.** Es una columna `GENERATED ALWAYS` de Postgres
(`MOBILE_DATA_MAPPING.md §5.5`): el servidor la calcula y la relee. Un cálculo local `cantidad ×
precioUnitario` con otro redondeo podría mostrar un centavo de diferencia respecto del valor real y minar
la confianza en las cifras. Se puede mostrar un **subtotal estimado** claramente etiquetado como
referencia, nunca como "Total".

---

### V-03 · Detalle de venta

**Modo** ONLINE + CACHE · **Endpoint** `GET /api/ventas/{id}`

Muestra el `total` **tal cual lo devolvió el servidor**, sin recalcular.

---

## 9. RECEPCION (`R-`)

Opera en planta, con conectividad asumida (C-08). **Ningún flujo de este rol es offline**: el backend no lo
diseñó para eso (`RecepcionPlantaRequest` no tiene `uuidCliente` y `/api/sync` no lo incluye).

### R-01 · Registrar recepción en planta ★

**Modo** ONLINE-ONLY · **Endpoint** `POST /api/recepcion-planta`

| Campo | Obligatorio | Validación | Origen |
|---|---|---|---|
| `fecha` | Sí | fecha válida | `@NotNull` |
| `turno` | **No** | libre; el servidor aplica `"UNICO"` si viene vacío | comentario explícito del DTO |
| `unidadId` | Sí | selector desde `unidad_cache` | `@NotNull` |
| `litrosCampo` | Sí | `>= 0`, `precision=9, scale=2` | `@DecimalMin("0.0")` |
| `litrosPlanta` | Sí | `>= 0`, `precision=9, scale=2` | `@DecimalMin("0.0")` |

**`turno` es nullable en el request y no-nulo en la respuesta** (`MOBILE_DATA_MAPPING.md §5.9`): el modelo
KMP del formulario debe permitir vacío. Si la UI forzara un valor, estaría inventando un dato que el
servidor sabe resolver mejor.

**Sin conexión**: la pantalla se bloquea por completo con explicación. No se guarda borrador de envío ni se
encola: encolar aquí crearía el problema de `DATA-006` (409 en duplicado, sin idempotencia).

---

### R-02 · Resultado de conciliación

**Modo** ONLINE-ONLY · resultado de `R-01` o de `GET /api/recepcion-planta/{id}`

Muestra `litrosCampo`, `litrosPlanta`, `diferenciaPct` (columna `GENERATED`) y `estado`
(`OK`/`ALERTA`, enum con `UNKNOWN` de reserva), más `litrosRegistradosAcopio` como dato de referencia.

**`litrosRegistradosAcopio` es nullable** — viene de un `SUM()` que sobre cero filas devuelve `NULL`, no
`0` (`MOBILE_DATA_MAPPING.md §3`). La UI muestra "Sin registros de acopio para esta unidad y fecha", nunca
"0 L", porque son afirmaciones distintas.

**Los tres campos son 100% calculados server-side** y de solo lectura.

---

### R-02b · Conflicto: la recepción ya existe (409)

No es una pantalla propia sino un estado de `R-01`, documentado aparte por ser el único conflicto real del
sistema (`MOBILE_ARCHITECTURE.md §8`).

```text
POST /api/recepcion-planta → 409 Conflict
 → la clave natural (fecha, unidadId, turno) ya existe
 → NO reintentar: el endpoint no es idempotente (DATA-006)
 → GET /api/recepcion-planta?unidadId={id}, buscar esa fecha/turno
 → mostrar el registro existente y explicar:
      "Ya existe una recepción para esta unidad, fecha y turno."
      [Ver el registro existente]   [Cambiar el turno]   [Cancelar]
```

**No hay resolución automática ni edición**: el backend no ofrece endpoint de modificación, así que la
única salida honesta es mostrar lo que hay y dejar decidir al usuario.

---

### R-03 · Historial de recepciones

**Modo** ONLINE + CACHE opcional · **Endpoint** `GET /api/recepcion-planta` (query `unidadId` opcional)

Lista con filtro opcional por unidad. Sin paginación (no existe en el backend).

---

### R-04 · Pagos de proveedor

**Modo** ONLINE-ONLY · **Endpoint** `GET /api/pagos/proveedor/{proveedorId}`

Solo lectura. El móvil **no** genera pagos (`POST /api/pagos/generar` es WEB/ADMIN).

⚠️ **`precioLitro` tiene 3 decimales** (`precision=6, scale=3`), a diferencia del resto de campos
monetarios del sistema que tienen 2. Ver §10.1 — formatearlo con 2 decimales sería mostrar un precio
incorrecto.

---

## 10. Reglas transversales de UI

Estas reglas aplican a **todas** las pantallas y se implementan una sola vez, en componentes compartidos
(§13). No son sugerencias de estilo: varias derivan directamente de hallazgos de la auditoría de datos.

### 10.1 Formateo de decimales

Todo valor decimal se formatea con **la escala exacta de su columna en el backend**, ni más ni menos. Un
formateador genérico de "2 decimales" produciría datos incorrectos en al menos dos campos.

| Campo | Escala | Ejemplo |
|---|---|---|
| `litros`, `litrosUsados`, `litrosCampo`, `litrosPlanta`, `litrosTotales` | 2 | `120.50 L` |
| `precioUnitario`, `total` (Venta y Pago) | 2 | `S/ 18.00` |
| **`precioLitro` (Pago)** | **3** | `S/ 1.850` ⚠️ distinto del resto |
| `agua`, `proteina`, `lactosa`, `temperatura`, `ph`, `densidad` | 2 | `3.25 %` |
| `rendimientoPct`, `rendimientoEsperadoPct`, `score` | 2 | `12.40 %` |
| **`zScore`** | **3** | `2.145` ⚠️ |
| `gpsLat`, `gpsLng` | 6 | `-12.046374` |
| `capacidadTon` | 2 | `3.50 t` |
| `diferenciaPct` | 2 | `-1.20 %` |

**Reglas**
1. El formateo ocurre en el `ViewModel`, no en el `@Composable` (§3.3).
2. **Nunca** se formatea pasando por `Double`. Del `BigDecimal` de `bignum` al `String` directamente
   (`DATA-002`).
3. Un valor `null` **nunca** se muestra como `0`. Se omite el campo o se escribe "No disponible" — son
   afirmaciones distintas y varios campos del contrato son legítimamente nulos.
4. Separador decimal: punto, coherente con el formato del backend y con el teclado numérico. No se
   localiza a coma en v1.

### 10.2 Formateo de fechas

| Tipo del contrato | Formato en UI | Ejemplo |
|---|---|---|
| `LocalDate` (`fecha`, `semanaInicio`, `fechaPrevista`) | `dd/MM/yyyy` | `04/09/2026` |
| `LocalDateTime` (`fechaHora`, `creadoEn`, `sincronizadoEn`, `confirmadoEn`) | `dd/MM/yyyy HH:mm` | `04/09/2026 16:20` |
| `LocalTime` (`horaEstimada`) | `HH:mm` | `14:30` |
| `Instant` (solo `CambiosResponse.generadoEn`) | relativo | "hace 5 min" |

### 10.3 Marcos temporales: regla obligatoria (`DATA-001` / `DATA-012`)

**Los campos `LocalDateTime` se muestran tal cual llegan, sin convertir a la zona del dispositivo.** Son
hora de pared sin zona; asumir UTC y convertir desplazaría todo ~5 horas si el servidor corre en
`America/Lima` (y hoy no sabemos cuál de los dos es — `DATA-001` sigue abierto).

Y, mientras eso siga abierto, **está prohibido**:

- ❌ Comparar o restar `fechaHora` (generada por el dispositivo) contra `creadoEn`/`sincronizadoEn`
  (generadas por el servidor). Nada de "sincronizado 5 min después de capturado".
- ❌ Ordenar una lista mezclando campos de ambos orígenes.
- ❌ Filtrar "de hoy" comparando contra un timestamp de servidor.

Y **está permitido**:

- ✅ Mostrar cada uno por separado y etiquetado: "Capturado 04/09 16:20" / "Sincronizado 04/09 21:20".
- ✅ Ordenar y filtrar usando **solo** `fechaHora` (todos los valores vienen del mismo marco: los
  dispositivos, todos en `America/Lima`).
- ✅ Calcular tiempos relativos sobre `CambiosResponse.generadoEn`, que es `Instant` real con `Z` y no
  tiene ambigüedad.

Cuando DevOps confirme el timezone del servidor, esta sección se relaja. Hasta entonces, es una regla dura.

### 10.4 Mapeo de errores a mensajes

Toda respuesta de error de la API tiene la misma forma `ErrorResponse {timestamp, status, error, mensaje}`
(`MOBILE_DATA_MAPPING.md §5.12`), así que hay **un solo** mapeo, en `shared/presentation/`:

| Situación | Qué ve el usuario | ¿Reintentable? |
|---|---|---|
| Sin conexión detectada | "Sin conexión. Tu trabajo se guarda igual." | Automático al reconectar |
| Timeout / 5xx | "No pudimos conectarnos. Reintentamos solos en un momento." | Sí, con backoff |
| 401 | Cierre de sesión + "Tu sesión venció, ingresá de nuevo" | No — exige login |
| 403 | "No tenés permiso para esta acción." | No |
| 400 / 422 | El `mensaje` del backend, **literal** | No — el usuario debe corregir |
| 404 | Depende: dato inexistente vs. sin histórico (ver `C-08`) | No |
| 409 (solo `R-01`) | Flujo específico de conflicto (§9, `R-02b`) | No |
| 500 | "Algo salió mal del lado del servidor." + registrar en log | No automático |

**El `mensaje` del backend se muestra literal en los errores de validación y de negocio.** Está escrito por
el Service, en español, y suele ser accionable. Reemplazarlo por un texto genérico destruye información
útil para el usuario de campo.

### 10.5 Indicadores de sincronización

Un único componente, presente en la barra superior de todas las pantallas autenticadas:

| Estado | Indicador |
|---|---|
| Todo sincronizado, con señal | Discreto o ausente. El éxito no necesita anuncio |
| N pendientes, con señal | "N por enviar" + animación de subida durante `SYNCING` |
| N pendientes, sin señal | "N por enviar · sin conexión" |
| Hay `FAILED` | **Destacado**, con badge de atención → `S-05` |
| Hay `PENDING_DEPENDENCY` | "N esperando" — informativo, **no** con estética de error |

**`PENDING_DEPENDENCY` nunca se pinta como error.** Es una espera legítima del diseño; mostrarla en rojo
haría que el usuario intente "arreglar" algo que se resuelve solo.

En cada fila de una lista, un registro no sincronizado lleva su propio badge. Un registro `SYNCED` no lleva
ninguno: el estado normal no se decora.

### 10.6 Estados de pantalla: los cuatro obligatorios

Toda pantalla que muestre datos define explícitamente los cuatro. Una pantalla sin sus cuatro estados
definidos **no está terminada**.

1. **Cargando** — solo cuando no hay nada que mostrar. Si hay datos en cache, se muestran esos con un
   indicador de refresco discreto encima. Nunca un spinner a pantalla completa tapando datos válidos.
2. **Vacío** — con causa y salida. "No hay entregas registradas todavía" + acción para crear la primera.
   Nunca una lista en blanco sin explicación.
3. **Error** — distinguiendo error de red (reintentable, con botón) de error de negocio (requiere acción
   del usuario). Si hay datos en cache, se muestran con un banner de error encima, no en su lugar.
4. **Sin conexión** — para pantallas offline-first, un banner informativo que no bloquea nada. Para
   pantallas online-only, bloqueo con explicación de por qué esa función necesita señal.

---

## 11. Requisitos de UI derivados de la auditoría de datos

Estos no son criterios de diseño: son **mitigaciones obligatorias** de hallazgos de
`MOBILE_DATA_MAPPING.md §10`. Si se implementan mal, se rompe algo concreto.

| Hallazgo | Requisito de UI | Pantalla | Si no se cumple |
|---|---|---|---|
| `DATA-010` | `tipoCliente` como selector cerrado de 3 opciones, jamás texto libre | `V-02` | **500 Internal Server Error** en producción |
| `DATA-001` / `DATA-012` | No comparar ni ordenar mezclando fechas de dispositivo y de servidor (§10.3) | todas | Fechas desplazadas ~5 h y duraciones falsas |
| `DATA-002` | Formatear desde `BigDecimal`, con la escala exacta por campo (§10.1) | todas | Cifras que no cuadran centavo a centavo con Postgres |
| `DATA-003` | Explicar la retención por dependencia al **seleccionar**, no al guardar | `C-02`, `P-02` | El usuario cree que su análisis se envió cuando está retenido |
| `DATA-004` | Bloquear correcciones sin conexión, con confirmación explícita | `C-06` | Correcciones duplicadas que corrompen la trazabilidad de litros |
| `DATA-005` | Bloquear confirmación de comunicado sin conexión | `A-07` | Confirmaciones duplicadas en la auditoría |
| `DATA-006` | Flujo específico de 409, sin reintento automático | `R-01`/`R-02b` | Reintentos ciegos contra un endpoint no idempotente |
| `DATA-008` | Sin botón de cámara para evidencia fotográfica en v1 | `A-04` | Fotos capturadas que nunca se suben y llenan el dispositivo |
| `DATA-013` | Priorizar la fila local cuando el `server_id` coincide | `A-05`, `C-02`, `P-02` | Registros duplicados visualmente en la selección |
| Nullability (§3) | `null` nunca se muestra como `0` | todas | Afirmaciones falsas: "0 L registrados" cuando en realidad no hay dato |
| Enums (§1.6) | Todo enum con valor `UNKNOWN` de reserva, mostrado tal cual llegó | `C-04`, `C-07`, `R-02` | Crash o pantalla rota ante un valor nuevo del backend |
| Sin paginación (§7) | Listas completas, sin scroll infinito ni "cargar más" | todos los historiales | UI que promete algo que el backend no soporta |

---

## 12. Permisos del sistema

Solo dos, y ninguno bloquea una funcionalidad esencial.

| Permiso | Para qué | Pantalla | Si se deniega |
|---|---|---|---|
| **Cámara** | Escanear el QR del proveedor | `A-02` | `A-03` (búsqueda por nombre) sigue funcionando. La app **no** queda inutilizable |
| **Ubicación** | `gpsLat`/`gpsLng` del registro de acopio | `A-04` | Se guarda con GPS nulo. **Nunca** impide registrar una entrega |

**Reglas de solicitud**
1. **En contexto, nunca al arrancar.** El permiso de cámara se pide al tocar "Escanear QR", no en el
   splash. Un permiso pedido sin contexto se deniega mucho más.
2. **Explicar antes de pedir**, en una pantalla propia: qué se va a usar y por qué. Recién después se
   dispara el diálogo del sistema.
3. **Denegación permanente**: explicación + acceso directo a los ajustes del sistema. Nunca reintentar el
   diálogo en bucle.
4. **Ninguno de los dos es obligatorio para operar.** Es una decisión deliberada: en campo, un permiso
   denegado no puede significar que no se pueda registrar la leche que ya se recibió.

Sin permiso de almacenamiento en v1 (no hay fotos, C-07). Sin notificaciones (no hay push).

---

## 13. Componentes reutilizables

Se construyen una vez en `shared/ui/components/` y los usan todas las pantallas. Esta lista es también el
alcance mínimo del sistema de diseño (§14).

| Componente | Qué resuelve | Usado en |
|---|---|---|
| `CampoDecimal` | Input numérico con escala fija por campo, teclado decimal, validación y formateo (§10.1) | `A-04`, `C-03`, `P-03`, `V-02`, `R-01` |
| `SelectorCatalogo` | Desplegable sobre una tabla `*_cache`, con búsqueda, estado vacío y "requiere sincronizar" | todos los formularios |
| `IndicadorSync` | El indicador de la barra superior (§10.5) | todas las autenticadas |
| `BadgeEstadoSync` | Badge por fila: pendiente / esperando / error | todas las listas |
| `BannerSinConexion` | Banner informativo no bloqueante | todas las offline-first |
| `BloqueoOnlineOnly` | Bloqueo con explicación para pantallas que exigen señal | `A-07`, `C-05`, `C-06`, `C-07`, `C-08`, `R-*` |
| `EstadoVacio` | Vacío con causa y acción de salida (§10.6) | todas las listas |
| `EstadoError` | Error distinguiendo red de negocio, con reintento cuando aplica | todas |
| `FechaEtiquetada` | Fecha con su etiqueta de origen, cumpliendo §10.3 | todos los detalles |
| `BotonAccionPrincipal` | Botón grande de acción primaria, dimensionado para uso con guantes (§15) | `S-03` y formularios |
| `DialogoConfirmacion` | Confirmación que nombra explícitamente lo que se pierde | `S-07`, `S-05`, `C-06` |

---

## 14. Qué debe entregar diseño

Este documento define estructura y comportamiento. Falta la capa visual, y se puede trabajar **en
paralelo** al desarrollo de las Fases 1–6 (que no tienen UI).

**Directrices clave de UI/UX** (a incorporar en los tokens de diseño — *Colors*, *Theme*, *Type* — y en los
prototipos/componentes de la Fase 7, antes de generar las tareas):

1. **Paleta de colores (tema)**:
   * Usar tonalidades verdosas suaves / pastel / orgánicas (inspiradas en el rubro lácteo/agrícola),
     evitando verdes fosforescentes, chillones o saturados.
   * Los colores deben ser intuitivos (verde suave para estados de éxito/sincronizado, tonos
     neutros/cálidos para alertas o datos pendientes).
2. **Diseño de interfaz (UI/UX)**:
   * Diseñar una interfaz moderna y fresca (no la clásica app empresarial tosca/antigua), pero manteniendo
     una alta usabilidad y simplicidad (fácil de usar en campo).
   * Priorizar tarjetas visuales (*Cards*), componentes limpios, tipografía legible y buena jerarquía
     visual con Jetpack Compose / Compose Multiplatform.

**Entregables mínimos, en orden de necesidad:**

1. **Tokens del sistema**: paleta (con estados de éxito/atención/error para los badges de sync),
   tipografía con escala, espaciado, radios, elevación. Con soporte de tema claro y oscuro.
2. **Los 11 componentes de §13**, cada uno en todos sus estados (normal, foco, error, deshabilitado,
   cargando).
3. **Las 5 pantallas de captura** (`A-04`, `C-03`, `P-03`, `V-02`, `R-01`) en alta fidelidad, incluyendo
   los estados de error de cada campo. Son el 80% del uso real.
4. **`S-05 Pendientes`** en sus tres secciones. Es la pantalla más difícil de diseñar bien y la que decide
   si el usuario confía en la app.
5. **Los cuatro estados de §10.6** como patrón visual, no pantalla por pantalla.
6. El resto del inventario puede resolverse por composición de lo anterior.

**Restricciones de contexto de uso, no negociables** — el usuario está en campo:

- **Objetivos táctiles grandes** (mínimo 48 dp, preferentemente más en las acciones primarias): se usa con
  guantes y en movimiento.
- **Alto contraste**: se lee bajo sol directo. Los grises tenues de un dashboard de escritorio no sirven.
- **Una sola mano**: las acciones principales, en la mitad inferior de la pantalla.
- **Poco texto**: el usuario está trabajando, no leyendo.
- **Estado de sync siempre visible**: la pregunta "¿se guardó?" tiene que responderse sin navegar.

---

## 15. Accesibilidad e idioma

- **Idioma**: español (es-PE) únicamente en v1. Todos los textos en un archivo de recursos compartido desde
  el día 1 — no es para traducir ahora, es para no tener strings dispersos cuando haga falta.
- **Escala de fuente del sistema**: la UI debe sobrevivir a un aumento del 200% sin cortar texto ni romper
  la maquetación. Es frecuente en usuarios de campo.
- **Contraste**: mínimo WCAG AA (4.5:1 en texto normal). Bajo sol directo, AA es el piso, no la meta.
- **Etiquetas para lectores de pantalla** en todos los controles interactivos, especialmente iconos sin
  texto (el indicador de sync es el caso obvio).
- **El color nunca es el único portador de información**: los estados de sync llevan icono y texto además
  de color, tanto por daltonismo como por legibilidad bajo sol.

---

## 16. Fuera de alcance de v1

Se documenta explícitamente para que nadie lo asuma incluido:

| Fuera de v1 | Por qué | Cuándo se retoma |
|---|---|---|
| Evidencia fotográfica | Sin endpoint de subida (`DATA-008`) | v2, tras §18.4 (C-07) |
| Notificaciones push | El backend no tiene mecanismo de push | Sin fecha |
| Pantallas de ADMIN | Es el panel web (`MOBILE_ARCHITECTURE.md §3.2`) | No aplica |
| Edición de registros ya creados | `RegistroAcopio` es inmutable por dominio; el resto no tiene endpoint de edición | No aplica |
| Paginación / scroll infinito | No existe en ningún endpoint MOBILE | Cuando el backend la agregue |
| Modo oscuro obligatorio | Deseable, no bloqueante | Se define con los tokens (§14) |
| Múltiples idiomas | Un solo mercado en v1 | Sin fecha |
| Captura por voz (`litrosPorVoz`) | El campo existe en el contrato, pero la funcionalidad de reconocimiento no está especificada ni evaluada | Requiere decisión de producto |

> **`litrosPorVoz` merece una aclaración**: el campo existe en el request y la app **debe enviarlo**
> (`false` en v1). Lo que queda fuera de alcance es la *funcionalidad* de dictar los litros por voz, que
> nunca fue especificada. Si producto la quiere, es una feature propia con sus propios permisos, su
> evaluación de precisión y su diseño; no es un checkbox.

---

## 17. Pruebas de la capa de presentación

Como los `ViewModel` viven en `shared/presentation/`, **se prueban una sola vez en `commonTest`, corriendo
en JVM sin emulador** — igual que el resto de `shared/` (`MOBILE_ARCHITECTURE.md §17`).

| Área | Qué se prueba | Herramienta |
|---|---|---|
| Transiciones de `UiState` | Cada `Event` produce el `UiState` esperado; los estados intermedios (`guardando`) se emiten y se limpian | kotlin.test + Turbine |
| Validaciones de formulario | Cada regla de §5–§9 rechaza lo que debe: `cantidad = 0` inválido, `folioMuestra` de 41 caracteres inválido, `litros` negativo inválido, campos de laboratorio vacíos **válidos** | kotlin.test |
| `Effect` de una sola vez | Un `Effect` de navegación se consume exactamente una vez y no se reemite al recrear el `ViewModel` | Turbine |
| Mapeo de errores | Cada código HTTP produce el mensaje y la reintentabilidad de §10.4 | kotlin.test + fakes |
| Formateo | Cada campo se formatea con su escala exacta (§10.1); `precioLitro` con 3 decimales y `litros` con 2; ningún `null` se muestra como `0` | kotlin.test |
| Regla de fechas | Ningún cálculo mezcla marcos temporales (§10.3); el orden de listas usa solo `fechaHora` | kotlin.test |
| Estados offline | Con `ConnectivityObserver` en `false`: las pantallas offline-first permiten guardar, las online-only se bloquean con el mensaje correcto | kotlin.test + fake |
| Dependencias retenidas | Seleccionar un padre sin `server_id` en `C-02`/`P-02` produce el aviso y el hijo nace `PENDING_DEPENDENCY` | kotlin.test |
| Borradores | Un borrador se persiste, sobrevive a la recreación del `ViewModel` y se borra al guardar | kotlin.test + SQLDelight en memoria |
| Logout con pendientes | Con filas ≠ `SYNCED`, el logout no procede sin elección explícita del usuario | kotlin.test |

**Pruebas de UI propiamente dichas** (Compose UI tests) se reservan para los 11 componentes de §13 y las 5
pantallas de captura. El resto queda cubierto por las pruebas de `ViewModel`, que son más rápidas y menos
frágiles.

---

## 18. Trazabilidad: pantalla ↔ endpoint ↔ tabla

Verificación de que ninguna pantalla inventa un endpoint ni una tabla. Los 34 endpoints MOBILE de
`MOBILE_ARCHITECTURE.md §3.1` están cubiertos.

| Pantalla | Endpoint(s) | Tabla local | Modo |
|---|---|---|---|
| S-01 | — | `SecureTokenStorage` | offline |
| S-02 | `POST /api/auth/login`, `POST /api/auth/refresh` | `SecureTokenStorage` | online-only |
| S-03 | — | todas (lectura) | offline |
| S-04 | — | 4 × `*_local` | offline |
| S-05 | `POST /api/sync/*` (reintento) | 4 × `*_local` | offline |
| S-06 | `GET /api/comunicados/zona/{zonaId}` | `comunicado_cache` | read-cache |
| S-07 | — | todas (verificación) | offline |
| A-01 | `GET /api/zonas/{zonaId}/ruta` | `ruta_zona_cache` | read-cache |
| A-02 | `GET /api/proveedores/qr/{codigoQr}` | `proveedor_cache` | offline real |
| A-03 | `GET /api/proveedores/operativo` | `proveedor_cache` | offline real |
| A-04 | `POST /api/registros-acopio`, `POST /api/sync/registros-acopio` | `registro_acopio_local` | **offline-first** |
| A-05 | `GET /api/registros-acopio/proveedor/{id}` | `registro_acopio_local` + `_cache` | online+cache |
| A-06 | `GET /api/registros-acopio/{id}` | `registro_acopio_cache` | online+cache |
| A-07 | `POST /api/comunicados/{id}/confirmaciones` | — | online-only |
| C-01 | — | `registro_acopio_cache`, `analisis_calidad_local` | offline |
| C-02 | `GET /api/registros-acopio/proveedor/{id}` | `registro_acopio_local` + `_cache` | parcial |
| C-03 | `POST /api/analisis-calidad`, `POST /api/sync/analisis-calidad` | `analisis_calidad_local` | **offline-first** |
| C-04 | `GET /api/analisis-calidad/registro/{id}` | `analisis_calidad_local` | online+cache |
| C-05 | `GET /api/analisis-calidad/folio/{folio}` | — | online-only |
| C-06 | `POST` y `GET /api/registros-acopio/{id}/correcciones` | — | online-only |
| C-07 | `GET /api/innovacion/alertas?zonaId` | — | online-only |
| C-08 | `GET /api/innovacion/score/{proveedorId}` | — | online-only |
| P-01 | `GET /api/lotes-produccion` | `lote_produccion_local` | online+cache |
| P-02 | `GET /api/registros-acopio/proveedor/{id}` | `registro_acopio_local` + `_cache` | parcial |
| P-03 | `POST /api/lotes-produccion`, `POST /api/sync/lotes-produccion` | `lote_produccion_local` (+ N:M) | **offline-first** |
| P-04 | `GET /api/lotes-produccion/{id}` | `lote_produccion_local` | online+cache |
| V-01 | `GET /api/ventas` | `venta_local` | online+cache |
| V-02 | `POST /api/ventas`, `POST /api/sync/ventas` | `venta_local` | **offline-first** |
| V-03 | `GET /api/ventas/{id}` | `venta_local` | online+cache |
| R-01 | `POST /api/recepcion-planta` | — | online-only |
| R-02 | `GET /api/recepcion-planta/{id}` | — | online-only |
| R-03 | `GET /api/recepcion-planta` | cache opcional | online+cache |
| R-04 | `GET /api/pagos/proveedor/{id}`, `GET /api/pagos/{id}` | — | online-only |
| (transversal) | `GET /api/sync/cambios` | todas las `*_cache` | motor de sync |
| (transversal) | `GET /api/innovacion/prediccion/{id}` | `prediccion_proveedor_cache` | read-cache |

---

*Documento derivado de `MOBILE_ARCHITECTURE.md` (Rev. 2) y `MOBILE_DATA_MAPPING.md` (Rev. 2). Ninguna
pantalla consume un endpoint que no exista en la auditoría de 73 endpoints, ni escribe en una tabla no
declarada en §11 de la arquitectura; ninguna validación de formulario es criterio propio — todas son la
traducción literal de las anotaciones Bean Validation verificadas en el backend real. Donde una limitación
del backend impide un flujo (captura offline con dependencias sin resolver, endpoints no idempotentes), la
UI lo comunica explícitamente al usuario en vez de ocultarlo. Fase 0 completada con este documento: la
capa de presentación queda especificada y el desarrollo del frontend puede comenzar.*

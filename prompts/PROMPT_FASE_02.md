# FASE 2 — Network

> **Cómo usar este prompt**: guardalo como `prompts/PROMPT_FASE_02.md` y en Claude Code escribí:
> *"Leé `CLAUDE.md` y ejecutá `prompts/PROMPT_FASE_02.md`."*

---

## Objetivo

Construir la capa de red completa: los serializers que hacen que los tipos del backend sobrevivan el viaje
a Kotlin sin perder precisión, los ~30 DTOs del contrato, el cliente HTTP con su manejo de errores, y las
definiciones de los 34 endpoints MOBILE.

**Al terminar, la app puede hablar con el backend — pero todavía nadie la llama.** No hay repositorios, no
hay base de datos, no hay pantallas. Eso es correcto.

## Estado al arrancar esta fase

La Fase 1 cerró con los 11 criterios cumplidos. Lo relevante para vos:

- `shared/core/` ya tiene `ApiResult`, `ApiError` (con `esTransitorio` por variante), `ErrorResponse`,
  `Decimal` (alias de `bignum` + helpers de escala), `Uuid`, `FechaUtils`, `Async` y `DispatcherProvider`.
  **Usalos, no los reimplementes.**
- **`bignum` está verificado en target nativo iOS** (CI en verde). La estrategia decimal de `DATA-002` es
  viable y no hay que reevaluarla.
- El CI corre en cada push: Android en `ubuntu-latest`, iOS en `macos-14`. Con `testLogging` activo, los
  nombres de test aparecen en el log.

## Antes de escribir código: qué leer

1. `CLAUDE.md` completo.
2. `docs/MOBILE_DATA_MAPPING.md` — **es EL documento de esta fase**:
   - §1 completo (convenciones globales: nombres JSON, UUID, fechas, `BigDecimal`, enums, booleanos)
   - §2 (tabla general de tipos)
   - §3 (nullability por campo)
   - §4 (Request vs Response, sin reutilización de DTOs)
   - **§5 completo** (el diccionario campo por campo — es largo, y es exactamente lo que hay que codear)
   - §6, §7, §8, §9 (path params, query params, headers, códigos de respuesta)
   - §10 (`DATA-001` a `DATA-013`)
3. `docs/MOBILE_ARCHITECTURE.md`, solo:
   - §3.1 (los 34 endpoints MOBILE y su clasificación)
   - §4 (autenticación — solo el mecanismo del header y el manejo del 401)
   - §10 (responsabilidades del API Client)

**No leas** `MOBILE_SCREENS.md`. Todavía no hay UI.

---

## Orden de trabajo sugerido

Esta fase es grande. Hacela en este orden, porque lo difícil va primero y lo mecánico después:

1. Serializers (§1) — es donde está el riesgo real
2. Configuración de `Json` y del `HttpClient` (§3)
3. Los 4 DTOs offline-first + sus tests (§2) — valida el patrón end-to-end
4. Los ~26 DTOs restantes (§2) — ya es repetición del patrón probado
5. Definición de endpoints (§4) y mapeo de errores (§5)
6. Tests que faltan (§6)

Si la sesión se hace muy larga, **el punto natural de corte es después del paso 3**: ahí el patrón está
probado y lo que queda es volumen. Avisame y seguimos en otra sesión, en vez de apurar el resto.

---

## Alcance: qué SÍ entra

### 1. Serializers custom — lo crítico de la fase

En `shared/data/remote/serializer/`:

**a) `BigDecimalSerializer`** — el más importante del proyecto.

- Deserializa leyendo el **literal crudo** del JSON vía `JsonDecoder` → `decodeJsonElement()` →
  `JsonPrimitive.content`, y construyendo el `Decimal` desde ese `String`.
- Serializa escribiendo el número **sin comillas** (es un `number` JSON, no un `string`) y **sin notación
  científica**, usando `JsonUnquotedLiteral`.
- ❌ **Jamás** pasa por `Double`, ni siquiera intermedio. `decodeDouble()` está prohibido.
- Preserva la escala: si el JSON trae `12.50`, el `Decimal` resultante formateado da `"12.50"`, no `"12.5"`.

Esta última propiedad es la que hay que testear con más saña. Es la diferencia entre mostrar `S/ 18.50` y
`S/ 18.5` en una boleta.

**b) Serializers de fecha** — cuatro tipos distintos, **no intercambiables** (`MOBILE_DATA_MAPPING.md §1.4`):

| Tipo del contrato | Formato JSON | Tipo KMP |
|---|---|---|
| `LocalDate` | `"2026-09-04"` | `kotlinx.datetime.LocalDate` |
| `LocalDateTime` | `"2026-09-04T10:15:30"` **o** `"2026-09-04T10:15:30.123456"` — sin offset, sin `Z` | `kotlinx.datetime.LocalDateTime` |
| `LocalTime` | `"14:30:00"` | `kotlinx.datetime.LocalTime` |
| `Instant` | `"2026-09-04T15:30:00.123456Z"` — **con** `Z` | `kotlin.time.Instant` |

- El de `LocalDateTime` debe aceptar **ambas** formas (con y sin fracción de segundo). El backend emite la
  fracción solo cuando existe.
- ❌ **Ningún serializer convierte un `LocalDateTime` a `Instant`** ni le asume una zona. Ver §7.
- `Instant` aparece en **un solo campo de todo el contrato**: `CambiosResponse.generadoEn`. Si lo estás
  usando en otro lado, algo está mal.

**c) Serializer genérico de enums con valor de reserva** — todos los enums del contrato viajan como
`String` libre, sin garantía de conjunto cerrado (`MOBILE_DATA_MAPPING.md §1.6`). Un valor no reconocido
**decodifica a `UNKNOWN`**, nunca lanza excepción.

Los enums a crear, con sus valores reales:

| Enum | Valores | Dónde |
|---|---|---|
| `ResultadoCalidad` | `APROBADO`, `RECHAZADO`, `OBSERVADO`, `UNKNOWN` | `AnalisisCalidadResponse.resultado` |
| `TipoClienteVenta` | `MAYORISTA`, `PROVEEDOR`, `PUBLICO`, `UNKNOWN` | `Venta*.tipoCliente` |
| `CicloCapital` | `RAPIDO`, `MADURACION`, `UNKNOWN` | `TipoQuesoResponse.cicloCapital` |
| `EstadoConciliacion` | `OK`, `ALERTA`, `UNKNOWN` | `RecepcionPlantaResponse.estado` |
| `Severidad` | `BAJA`, `MEDIA`, `ALTA`, `UNKNOWN` | `AlertaAnomaliaResponse.severidad` |
| `TipoAlerta` | `VOLUMEN_ATIPICO`, `RIESGO_ADULTERACION`, `UNKNOWN` | `AlertaAnomaliaResponse.tipo` |
| `Rol` | `ADMIN`, `ACOPIADOR`, `CALIDAD`, `PRODUCCION`, `VENTAS`, `RECEPCION`, `UNKNOWN` | `LoginResponse.rol` |

> `OBSERVADO` existe en el dominio y en el `CHECK` de `schema.sql`, pero **hoy ningún código del backend lo
> asigna**. Se modela igual: es un valor válido que puede empezar a llegar sin aviso.

### 2. DTOs — los ~30 del contrato

En `shared/data/remote/dto/`. Reglas que aplican a todos:

- **Request y Response son clases separadas, siempre.** El backend nunca reutiliza un DTO para las dos
  direcciones (`MOBILE_DATA_MAPPING.md §4`). Una sola clase con campos opcionales para cubrir ambos casos
  es un error, no una simplificación.
- **`@SerialName` no hace falta.** El backend no tiene ninguna personalización de Jackson: el nombre JSON
  es idéntico al nombre Java, en camelCase. Si nombrás el campo Kotlin igual, no hace falta anotarlo
  (§1.1).
- **La nullability se copia exactamente de §5**, campo por campo. Un `String?` donde §5 dice nullable, un
  `String` donde dice que siempre está presente. No "por las dudas" en ninguna de las dos direcciones.
- Todo campo opcional de un **Request** lleva `= null` como default.

DTOs a crear, agrupados por sección de §5:

```text
§5.1  LoginRequest · LoginResponse
§5.2  RegistroAcopioDTO · RegistroAcopioResponse · RegistroAcopioResumenResponse
      CorreccionRegistroRequest · CorreccionRegistroResponse
§5.3  AnalisisCalidadRequest · AnalisisCalidadResponse
§5.4  CrearLoteRequest · LoteProduccionResponse
§5.5  VentaRequest · VentaResponse
§5.6  SyncResultResponse · SyncErrorItem · CambiosResponse
      ProveedorPublicoResponse · ComunicadoResponse · PrediccionProveedorResponse
      MotivoObservacionResponse · TipoQuesoResponse · UnidadResponse
§5.7  ConfirmarComunicadoRequest · ComunicadoConfirmacionResponse
§5.8  RutaProveedorOrdenResponse
§5.9  RecepcionPlantaRequest · RecepcionPlantaResponse
§5.10 PagoResponse
§5.11 ScoreConfianzaResponse · AlertaAnomaliaResponse
```

`ErrorResponse` ya existe de la Fase 1 — no lo dupliques.

**Trampas concretas de §5 que hay que respetar** (están documentadas, se pasan por alto fácil):

- `ComunicadoResponse.fecha` es **`LocalDateTime`**, no `LocalDate`, pese al nombre.
- `RegistroAcopioDTO.motivoObservacionId` (request, UUID) y `RegistroAcopioResponse.motivoObservacion`
  (response, **la descripción en texto**) son campos **distintos**, no el mismo renombrado.
- `RegistroAcopioResponse` **no expone `fotoUrl`** aunque el request lo acepte (`DATA-007`). No lo agregues.
- `RegistroAcopioResumenResponse` **no trae `uuidCliente`** (`DATA-013`). No lo inventes.
- `litrosPorVoz` y `aguaAnadida` son **nullable en el Request** y **no-nulos en el Response**.
- `turno` es nullable en `RecepcionPlantaRequest` y no-nulo en `RecepcionPlantaResponse`.
- `VentaResponse.total`, `PagoResponse.total` y `RecepcionPlantaResponse.diferenciaPct` son columnas
  `GENERATED` de Postgres: existen **solo** en el Response y son de lectura.
- Los 6 parámetros de laboratorio de `AnalisisCalidad` son **todos opcionales**.
- `CambiosResponse.precioLitroVigente` es **nullable** (`.orElse(null)` en el backend).
- `LoteProduccionResponse.rendimientoPct` es **nullable** (solo se calcula si `litrosUsados > 0`).
- `RecepcionPlantaResponse.litrosRegistradosAcopio` es **nullable** (`SUM()` sobre cero filas da `NULL`).

### 3. Configuración de `Json` y del `HttpClient`

En `shared/network/`:

```kotlin
val json = Json {
    ignoreUnknownKeys = true     // el backend puede agregar campos; no queremos que rompa la app
    isLenient = false            // el backend emite JSON estricto; ser laxo escondería errores reales
    encodeDefaults = true
    explicitNulls = false        // omite los null en el body; el backend los trata igual que ausentes
    coerceInputValues = false    // NUNCA convertir un null en el default en silencio
}
```

`HttpClient` con:

- `ContentNegotiation` usando ese `Json`.
- **Timeouts explícitos**, pensados para redes rurales: connect 10 s, request 30 s, socket 30 s
  (`MOBILE_ARCHITECTURE.md §10`). Configurables, no hardcodeados en el medio del código.
- Engines por plataforma vía `expect`/`actual`: OkHttp en Android, Darwin en iOS. En `jvm()` usá CIO o el
  motor que corresponda para poder testear.
- **URL base por entorno**, no una constante suelta: `dev` / `staging` / `prod`, seleccionable. Elegí un
  mecanismo simple (un objeto de configuración inyectado por Koin) y documentalo.
- Logging de requests **solo en debug**, y **nunca** logueando el header `Authorization` ni el body del
  login. Si no podés garantizar eso, no agregues logging.

**Interceptor de autenticación** — acá hay una dependencia con la Fase 3:

`MOBILE_ARCHITECTURE.md §10` dice que el cliente adjunta `Authorization: Bearer <token>` leyendo del
`SecureTokenStorage`. Pero `SecureTokenStorage` es de la Fase 3 y todavía no existe.

**Resolvelo así, no de otra manera**: definí en esta fase la **interfaz** `TokenProvider` (algo como
`suspend fun tokenActual(): String?`), inyectala en el cliente, y proveé una implementación en memoria para
los tests. La Fase 3 provee la implementación real sobre Keystore/Keychain. ❌ No implementes
almacenamiento seguro acá, y ❌ no hardcodees el token.

El header va en **todos** los requests **excepto** `POST /api/auth/login`, que es el único endpoint público
de toda la API.

### 4. Definición de endpoints

Los 34 endpoints MOBILE de `MOBILE_ARCHITECTURE.md §3.1`, como funciones tipadas o constantes — no strings
sueltos repartidos por el código.

Detalles del contrato que hay que respetar:

- **El body de los 4 endpoints `POST /api/sync/*` es un array JSON crudo**: `[ {...}, {...} ]`. **No** un
  objeto envolvente tipo `{"items": [...]}`. Confirmado en la firma del Controller
  (`MOBILE_DATA_MAPPING.md §5.6`). Es el error más caro de esta fase si se hace mal, porque falla recién
  contra el backend real.
- `GET /api/innovacion/alertas` tiene `zonaId` como query param **obligatorio** — sin él, 400.
- `GET /api/sync/cambios` acepta `desde` opcional, pero **no tiene efecto en v1**. Podés no enviarlo.
- `GET /api/recepcion-planta` acepta `unidadId` opcional.
- `GET /api/analisis-calidad/folio/{folio}` — el path param **no es un UUID**, es texto libre de hasta 40
  caracteres.
- `GET /api/proveedores/qr/{codigoQr}` — texto libre de hasta 64 caracteres; **no** asumas forma de UUID
  aunque hoy se genere así.
- **No hay paginación ni parámetros de orden en ningún endpoint.** No los agregues.
- No existe ningún header custom. Solo `Authorization` y `Content-Type`.

### 5. Mapeo uniforme de errores

Toda respuesta de error de la API tiene la misma forma `ErrorResponse` — incluidos los 401/403 que genera
`SecurityConfig` antes del `DispatcherServlet` (`MOBILE_DATA_MAPPING.md §5.12`). Un solo punto de
traducción alcanza para todo el cliente.

Un interceptor/plugin que convierte cualquier fallo en el `ApiError` correspondiente **ya definido en la
Fase 1**:

| Situación | `ApiError` | `esTransitorio` |
|---|---|---|
| Sin conectividad, timeout, socket cerrado | `Red` | `true` |
| 5xx | `Servidor` | `true` |
| 400 / 422 | `Validacion` (con el `mensaje` literal del backend) | `false` |
| 401 | `NoAutorizado` | `false` |
| 403 | `SinPermiso` | `false` |
| 404 | `NoEncontrado` | `false` |
| 409 | `Conflicto` | `false` |
| Cualquier otra cosa | `Desconocido` | `false` |

- El `mensaje` del backend se **conserva literal**. No lo reemplaces por texto propio: en los errores de
  validación suele ser accionable y la UI lo va a mostrar tal cual.
- Si el cuerpo del error no es un `ErrorResponse` parseable (proxy caído, HTML de error, respuesta vacía),
  **no revientes**: producí un `Desconocido` con lo que haya.
- ❌ Ninguna excepción de Ktor escapa de esta capa. Todo lo que sale es `ApiResult`.

### 6. Tests con `MockEngine`

En `commonTest`. Como mínimo:

**Serializers** (lo más importante):

- `12.50` deserializa y vuelve a serializar como `12.50`, no `12.5`.
- Un decimal grande (ej. `99999999.99`, dentro de `precision=10`) no se convierte a notación científica.
- `gpsLat` con 6 decimales (`-12.046374`) preserva los 6.
- ❌ Un test que verifique que **no** se puede colar un `Double`: por ejemplo, que el resultado de
  deserializar `0.1` y `0.2` y sumarlos dé exactamente `0.3`.
- `LocalDateTime` parsea **con y sin** fracción de segundo, y el resultado **no lleva zona**.
- `Instant` con `Z` parsea como UTC.
- Un valor de enum desconocido (ej. `"VALOR_NUEVO"`) decodifica a `UNKNOWN` **sin lanzar excepción**.

**DTOs**: cada uno deserializa desde un JSON de ejemplo construido **según §5**, con al menos un caso con
los campos nullable presentes y otro con esos campos ausentes.

**Cliente**:

- El header `Authorization: Bearer <token>` va en un request cualquiera, y **no** va en el de login.
- El body de `POST /api/sync/registros-acopio` se serializa como **array crudo**. Testealo comparando el
  JSON emitido: debe empezar con `[`, no con `{`.
- Cada código HTTP produce el `ApiError` de la tabla de §5, con el `esTransitorio` correcto.
- Un `MockEngine` que demora más que el timeout produce un error **transitorio**, no permanente.
- Un cuerpo de error que no es JSON válido no rompe el cliente.
- Un campo desconocido en una respuesta no rompe la deserialización (`ignoreUnknownKeys`).

---

## Fuera de alcance

- ❌ SQLDelight, tablas, persistencia → **Fase 4**
- ❌ `SecureTokenStorage` real (Keystore/Keychain) → **Fase 3**. Acá solo la interfaz `TokenProvider`.
- ❌ Lógica de refresh automático del token → **Fase 3**
- ❌ Sync Engine, reintentos, backoff, estados → **Fase 5**
- ❌ Repositories y UseCases → **Fase 6**
- ❌ Cualquier cosa de UI → **Fase 7**
- ❌ Llamadas reales contra un backend levantado. Todo se testea con `MockEngine`.

---

## Criterios de aceptación

### Localmente (Windows)

1. `./gradlew :shared:jvmTest` pasa, incluidos todos los tests nuevos.
2. `./gradlew :shared:assemble :androidApp:assembleDebug` sin errores.
3. **Cero `Double` y cero `Float`** como tipo en `shared/` (grep). En esta fase es más fácil que se cuele
   que en la anterior: `decodeDouble()` está a un autocompletado de distancia.
4. Cero `java.*` / `javax.*` en `commonMain`.
5. Los ~30 DTOs de §5 existen, **cada uno con al menos un test de deserialización**.
6. No hay dependencias nuevas fuera del `libs.versions.toml`, salvo los artefactos de Ktor que ya están
   declarados en el catálogo.
7. Ningún string de URL hardcodeado fuera del módulo de definición de endpoints.

### En CI

8. El workflow de iOS pasa, con los tests de serialización corriendo en target nativo. Es donde se
   confirma que los serializers de `bignum` funcionan en iOS, no solo en JVM.

---

## Checkpoint de cierre

El checkpoint del §5 de `CLAUDE.md`, con estos puntos específicos de la Fase 2:

- **`DATA-002` (CRITICAL)** — confirmá que el `BigDecimalSerializer` no toca `Double` en ningún camino, y
  mostrá la salida del test de preservación de escala corriendo en **iOS nativo**.
- **`DATA-001` (CRITICAL)** — sigue abierto (esperando el timezone real de la JVM del servidor, respuesta
  de DevOps). El roadmap exige que **al menos su estrategia de mitigación quede decidida y aprobada antes
  de cerrar esta fase**. Documentá explícitamente: los `LocalDateTime` se tratan como hora de pared, sin
  conversión, y no hay ni un `toInstant()` en el código. Decí también qué habría que cambiar el día que
  DevOps responda, para que ese cambio esté acotado.
- La decisión concreta sobre **URL base por entorno**: qué mecanismo elegiste y cómo se cambia de entorno.
- Si encontraste alguna discrepancia entre §5 y lo que era razonable implementar, listala como
  `DATA CONTRACT ISSUE` nuevo en el formato de `MOBILE_DATA_MAPPING.md §10`.
- Cuántos DTOs quedaron y cuántos tests suma la fase.

**Después del checkpoint, esperá aprobación antes de la Fase 3.**

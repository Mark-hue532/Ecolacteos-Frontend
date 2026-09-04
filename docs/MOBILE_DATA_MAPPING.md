# MOBILE_DATA_MAPPING.md — Auditoría Exhaustiva de Compatibilidad de Datos y Contratos

**Fase de Diseño / Plano Definitivo. Sin implementación de frontend.** Documento complementario de
[`MOBILE_ARCHITECTURE.md`](./MOBILE_ARCHITECTURE.md) (arquitectura general). Este documento es el **contrato
de datos campo por campo** entre el backend real y el frontend KMP propuesto.

> **Los tres documentos de la Fase 0 y su división de trabajo**:
>
> | Documento | Responde | Se usa |
> |---|---|---|
> | [`MOBILE_ARCHITECTURE.md`](./MOBILE_ARCHITECTURE.md) | **cómo** se construye la app: capas, offline-first, sync, conflictos, herramientas, estructura | antes de cada fase, para saber qué construir |
> | **`MOBILE_DATA_MAPPING.md`** (este) | **qué exactamente** viaja por la red: contrato campo por campo, nullability, tipos KMP/SQLite, `DATA-0xx` | abierto al lado mientras se codean DTOs (Fase 2) y `.sq` (Fase 4) |
> | [`MOBILE_SCREENS.md`](./MOBILE_SCREENS.md) | **qué ve y hace el usuario**: inventario de pantallas por rol, `UiState`/eventos, validaciones, estados vacío/carga/error/offline | desde la Fase 7 (primera vertical) en adelante |
>
> **Regla de precedencia entre documentos**: ante cualquier discrepancia de **tipo de dato, nullability o
> nombre de campo**, manda **este documento** (es la auditoría campo por campo contra el código real).
> Ante una discrepancia de **estructura de tablas, capas o flujo de sync**, manda `MOBILE_ARCHITECTURE.md`.
> Ante una discrepancia de **comportamiento de pantalla**, manda `MOBILE_SCREENS.md`.

## Revisión 2 — cambios aplicados

| # | Cambio | Dónde |
|---|---|---|
| M-01 | Nuevo hallazgo **`DATA-012`** (HIGH): marcos temporales mezclados dentro de un mismo registro — `fechaHora` la genera el dispositivo, `creadoEn`/`sincronizadoEn` el servidor | §10 |
| M-02 | Nuevo hallazgo **`DATA-013`** (MEDIUM): `RegistroAcopioResumenResponse` no incluye `uuidCliente`, lo que limita la mitigación de `MOBILE_ARCHITECTURE.md §18.1` | §10, §5.2 |
| M-03 | Confirmado que `MOBILE_ARCHITECTURE.md` Rev. 2 corrigió sus columnas SQLite de `REAL` a `TEXT`, alineándose con `DATA-002`. La contradicción entre ambos documentos queda cerrada | §1.5 |
| M-04 | Cerrados 2 de los 5 `UNKNOWN`: alcance de RECEPCION (confirmado móvil) y evidencia fotográfica (diferida a v2) | §12 |
| M-05 | Roadmap actualizado con las decisiones de UI tomadas y la incorporación de `MOBILE_SCREENS.md` | §13 |

## 0. Metodología y fuentes verificadas

**Jerarquía de confianza aplicada** (según lo pedido): comportamiento real del backend (código fuente:
Entities + `schema.sql` + Services) > DTOs/Controllers/validaciones Bean Validation > OpenAPI generado >
documentación manual. Fuentes inspeccionadas directamente en esta auditoría:

- 15 `@RestController`, sus 43 DTOs (`record` de Java) completos, campo por campo.
- 24 `@Entity` con sus anotaciones `@Column`/`@Enumerated`/`precision`/`scale`/`nullable` completas.
- `schema.sql` (fuente de verdad del esquema — `ddl-auto: validate`, Hibernate solo valida contra él, no lo
  genera).
- `GlobalExceptionHandler`, `SecurityConfig`, `application.yml`.
- Búsqueda explícita de personalización de Jackson (`@JsonProperty`, `@JsonNaming`, `ObjectMapper` custom,
  `spring.jackson.*`): **ninguna encontrada** en todo el proyecto.
- Búsqueda explícita de configuración de timezone de JVM (`-Duser.timezone`, `TZ`, `Dockerfile`,
  `docker-compose`): **ninguna encontrada** — ver §3 (Fechas), hallazgo CRITICAL.

**Lo que esta auditoría NO hizo**: no se levantó el backend contra una instancia PostgreSQL viva para
capturar el JSON real de `/v3/api-docs` (el entorno de este análisis no tiene la base de datos
`acopio_lechero` provisionada). Esto es aceptable dentro de la jerarquía de confianza pedida: los DTOs,
Controllers, Services y Entities — que sí fueron leídos íntegramente — están **por encima** del OpenAPI
generado en esa jerarquía, y springdoc-openapi genera su spec mecánicamente a partir de exactamente esas
mismas clases, sin lógica adicional propia. Donde algo depende de un comportamiento de despliegue no visible
en el repo (timezone de la JVM en producción), se marca **UNKNOWN** explícitamente en vez de asumirse — no
se inventa. Si se requiere, se puede levantar el backend con Docker Compose (Postgres) para capturar y
diffear el JSON real de `/v3/api-docs` contra este documento como paso de verificación adicional.

---

## 1. Convenciones globales verificadas (aplican a TODOS los DTOs)

Estas reglas se verificaron una sola vez contra el código y se aplican consistentemente en toda la
auditoría campo por campo de la §5, para no repetir la misma evidencia 200 veces.

### 1.1 Nombres JSON

**Verificado**: cero `@JsonProperty`, cero `@JsonNaming`, cero `PropertyNamingStrategy` configurado (ni en
`application.yml` ni en ninguna `@Configuration`). Los DTOs son `record` de Java — Jackson serializa cada
componente del record usando exactamente su nombre de declaración.

> **Regla**: `nombreJSON == nombreJava`, siempre camelCase, sin traducción. `uuidCliente` en el record es
> `"uuidCliente"` en el JSON — **no** `"uuid_cliente"** (a diferencia de la columna SQL, que sí usa
> snake_case: `uuid_cliente`). Este es el mapeo que debe usar el `@SerialName` de kotlinx.serialization
> **solo si** el nombre Kotlin difiere del JSON — en la práctica, si el campo KMP se llama igual que el
> Java (recomendado), no hace falta `@SerialName` en absoluto.

### 1.2 UUID

`java.util.UUID` → Jackson lo serializa con su `toString()` estándar → JSON `string`,
`"550e8400-e29b-41d4-a716-446655440000"` (RFC 4122, minúsculas, con guiones). Sin serializer custom.

> **KMP**: `String`. Kotlin no tiene un tipo UUID nativo estable y ampliamente soportado en todos los
> engines Ktor/SQLDelight a la fecha (existe `kotlin.uuid.Uuid`, experimental desde Kotlin 2.0.20) — se
> evaluó y se descarta para el MVP por ser experimental; usar `String` y validar formato solo si se
> necesita, evitando atar el contrato de red a una API todavía inestable del lenguaje.

### 1.3 `id` (servidor) vs `uuidCliente` (cliente) — NO MEZCLAR

| | `id` | `uuidCliente` |
|---|---|---|
| Quién lo genera | PostgreSQL (`DEFAULT uuid_generate_v4()`) / Hibernate `@GeneratedValue` | El dispositivo móvil (UUID v4 generado localmente) |
| Cuándo existe | Solo después de `INSERT` exitoso en el servidor | Antes de cualquier llamada de red — en el momento de la captura offline |
| Aparece en | Solo en **Response** (`RegistroAcopioResponse.id`, etc.) | En **Request** de creación y también se devuelve en el **Response** correspondiente |
| Es la PK local en SQLite | No debe serlo mientras el registro esté `PENDING` (no existe aún) | **Sí** — es la PK natural de la tabla `_local` (ver `MOBILE_ARCHITECTURE.md §11.1`) |
| Único (constraint) | `PRIMARY KEY` en Postgres | `UNIQUE` en Postgres (`schema.sql`, confirmado en las 4 tablas offline-first) |

Ambos se transmiten como el mismo tipo de dato en JSON (`string`), lo cual es precisamente lo que hace fácil
confundirlos si no se documenta — de ahí esta sección explícita.

### 1.4 Fechas y horas — ⚠️ hallazgo CRITICAL de timezone

Cuatro tipos Java distintos aparecen en los DTOs MOBILE, y **no son intercambiables**:

| Tipo Java | Aparece en (ejemplos) | Serialización JSON verificada | Zona horaria |
|---|---|---|---|
| `java.time.LocalDate` | `fecha`, `fechaPrevista`, `semanaInicio`, `periodo`, `vigenteDesde` | `"2026-09-04"` (ISO_LOCAL_DATE) | N/A (solo fecha, sin hora) |
| `java.time.LocalDateTime` | `fechaHora`, `creadoEn`, `sincronizadoEn`, `confirmadoEn`, `generadoEn` (en `PrediccionProveedorResponse`), `fecha` (en `ComunicadoResponse`, ¡es `LocalDateTime`, no `LocalDate`!) | `"2026-09-04T10:15:30"` o `"2026-09-04T10:15:30.123456"` si hay fracción de segundo — **sin** offset, **sin** `Z` | **⚠️ Ambigua — ver hallazgo abajo** |
| `java.time.LocalTime` | `horaEstimada` (`RutaProveedorOrdenResponse`) | `"14:30:00"` | N/A |
| `java.time.Instant` | **Solo `CambiosResponse.generadoEn`** | `"2026-09-04T15:30:00.123456Z"` — **con** `Z` (UTC explícito) | UTC, sin ambigüedad |

**Hallazgo CRITICAL — timezone de `LocalDateTime` no verificable desde el código**: `LocalDateTime` no
lleva zona horaria por definición. Varias entidades usan `private LocalDateTime creadoEn =
LocalDateTime.now();` como valor por defecto (`RegistroAcopio`, `AnalisisCalidad`, `LoteProduccion`,
`Comunicado`, `ComunicadoConfirmacion`, `RecepcionPlanta`), y `LocalDateTime.now()` en Java devuelve la hora
de pared **según el timezone por defecto de la JVM que ejecuta el proceso**, no necesariamente UTC.
`application.yml` sí fija `hibernate.jdbc.time_zone: UTC` — pero esa propiedad solo controla cómo Hibernate
envía/lee el valor `TIMESTAMP` al driver JDBC, **no** cambia lo que devuelve `LocalDateTime.now()` en el
código Java del lado del servidor. No se encontró en el repositorio ningún `-Duser.timezone=UTC`, variable
de entorno `TZ`, `Dockerfile` ni `docker-compose.yml` que fije el timezone del proceso Java en despliegue.

```text
UNKNOWN: si el servidor de producción corre con TZ=UTC o con la hora local de Perú (America/Lima, UTC-5).
Esto determina si "2026-09-04T10:15:30" en el JSON es UTC o América/Lima -- información que no está
en el repositorio y debe confirmarse con el equipo de DevOps/infra antes de fijar la estrategia de
parseo en el móvil.
```

**Recomendación para el móvil (mitigación sin cambio de backend)**: tratar todo campo `LocalDateTime` como
**hora de pared sin zona** (`kotlinx.datetime.LocalDateTime`, que modela exactamente esta semántica —
**no** convertir a `Instant`/`kotlin.time.Instant` asumiendo una zona, porque asumir UTC cuando el servidor
en realidad corre en `America/Lima` desplazaría todas las fechas ~5 horas). Mostrar estos valores en la UI
"tal cual" (son, de hecho, la hora que el ACOPIADOR/CALIDAD/etc. ya vio y capturó en su propio dispositivo
para `fechaHora`, así que mostrarlos sin reinterpretar es lo seguro). El único campo genuinamente UTC sin
ambigüedad es `CambiosResponse.generadoEn` (`Instant`), y debe ser el único mapeado a
`kotlinx.datetime.Instant`/`kotlin.time.Instant` en KMP.

### 1.5 `BigDecimal` — ⚠️ hallazgo CRITICAL multiplataforma

Todos los campos monetarios/de medición (`litros`, `precio`, `gpsLat/Lng`, `agua/proteina/lactosa/...`,
`rendimientoPct`, `score`, `total`, etc.) son `java.math.BigDecimal` con `precision`/`scale` explícitos en
cada `@Column` (verificado entidad por entidad, tabla en §5). Jackson serializa `BigDecimal` como **número
JSON literal** (no como string), preservando la escala tal como esté en el objeto Java (ej. `12.50`, no
`12.5`, si `scale=2`) — sin notación científica para las magnitudes de este dominio.

**Problema real para KMP** (esto es lo que la consigna pide no ocultar): `java.math.BigDecimal` **no existe
fuera de la JVM**. El target Kotlin/Native (iOS) no tiene acceso a esa clase. Convertir directamente a
`Double` (como haría un mapeo ingenuo) introduce error de punto flotante en valores monetarios y de litros
— inaceptable para un sistema que liquida pagos a proveedores.

**Alternativas evaluadas**:

| Opción | Multiplataforma real | Precisión exacta | Veredicto |
|---|---|---|---|
| `Double` | Sí | **No** (IEEE-754 binario, error de redondeo) | Descartado — riesgo de errores en `Pago`, `Venta`, `RegistroAcopio.litros` |
| `String` crudo (pasar el número tal cual, sin operar) | Sí | Sí (mientras no se opere sobre él) | Válido solo para campos que el móvil nunca calcula ni compara, ej. mostrar `precioLitroVigente` en una pantalla sin hacer aritmética — insuficiente para `litros`/`precioUnitario` que sí se usan en cálculos locales (ej. total estimado de una venta antes de enviarla) |
| `com.ionspin.kotlin:bignum` (`BigDecimal` multiplataforma real, con aritmética) | Sí (JVM, Native, JS) | Sí | **Recomendado** |

**Recomendación**: usar `com.ionspin.kotlin:bignum` como el tipo `BigDecimal` de KMP, con un
`KSerializer` custom que lea/escriba el valor **como número JSON crudo** (vía `JsonElement`/
`JsonPrimitive.content`, nunca pasando por `Double`) para no perder precisión ni en la deserialización ni
en la serialización. Este mismo tipo se persiste en SQLite como `TEXT` (la representación decimal exacta en
texto, ej. `"1234.56"`) vía un `ColumnAdapter` de SQLDelight — **nunca** como `REAL` (que es un float IEEE-754
de 8 bytes en SQLite, reintroduciendo el mismo problema de precisión que se busca evitar).

> **Nota de Rev. 2 (M-03)**: la Rev. 1 de `MOBILE_ARCHITECTURE.md §11` declaraba estas mismas columnas como
> `REAL` (`litros REAL`, `precio_unitario REAL`, `gps_lat REAL`, …), contradiciendo esta regla en el
> documento hermano. Como la Fase 4 construye el esquema SQLDelight a partir de §11, se habría implementado
> exactamente el bug que `DATA-002` advierte. **Corregido en `MOBILE_ARCHITECTURE.md` Rev. 2 (C-01)**: las
> 4 tablas `*_local` y las `*_cache` usan `TEXT` para todo campo decimal. Ambos documentos están alineados.
>
> **Regla operativa para quien codee la Fase 4**: si una columna guarda un valor que en el backend es
> `BigDecimal`, es `TEXT`. Sin excepciones, ni siquiera para porcentajes o coordenadas GPS que "no son
> dinero" — la uniformidad evita tener que recordar cuál sí y cuál no, y `gpsLat` con `scale=6` pierde
> precisión en `REAL` igual que un precio.

### 1.6 Enums

**Verificado**: todo enum de dominio (`ResultadoCalidad`, `TipoClienteVenta`, `CicloCapital`,
`EstadoConciliacion`, `Severidad`, `TipoAlerta`) está anotado `@Enumerated(EnumType.STRING)` en la Entity —
pero **ningún Response DTO expone el tipo enum directamente**: todos lo aplanan a `String` en el Service
(`.name()`), confirmado en `VentaService`, `AnalisisCalidadService`, `ConciliacionService`,
`AnomaliaService`/`AlertaAnomaliaResponse`, `SyncService`/`TipoQuesoResponse`. Consecuencia práctica:
**el contrato JSON de estos campos es `string` genérico, no un enum acotado documentado por OpenAPI** (el
tipo de campo del DTO es `String`, por lo que springdoc no tiene forma de inferir ni anotar un `enum:[...]`
en el schema generado — sección "OpenAPI" queda **UNKNOWN/no verificable sin capturar el spec real**, pero
es razonable asumir que aparece como `type: string` sin restricción, dado el tipo Java del campo).

| Enum | Valores reales (Entity/schema.sql) | Dónde aparece como `String` en el contrato | Productor real hoy |
|---|---|---|---|
| `ResultadoCalidad` | `APROBADO`, `RECHAZADO`, `OBSERVADO` | `AnalisisCalidadResponse.resultado` | Solo `APROBADO`/`RECHAZADO` (`AnalisisCalidadService`: ternario `aguaAnadida ? RECHAZADO : APROBADO`) — **`OBSERVADO` existe en el dominio y el `CHECK` de `schema.sql` pero ningún código lo asigna hoy** |
| `TipoClienteVenta` | `MAYORISTA`, `PROVEEDOR`, `PUBLICO` | Request: `VentaRequest.tipoCliente` (`String`, validado en runtime); Response: `VentaResponse.tipoCliente` | Los 3, según lo que envíe el cliente (ver riesgo abajo) |
| `CicloCapital` | `RAPIDO`, `MADURACION` | `TipoQuesoResponse.cicloCapital` | Los 2, ADMIN los define |
| `EstadoConciliacion` | `OK`, `ALERTA` | `RecepcionPlantaResponse.estado` | Los 2, calculado por `ConciliacionService` |
| `Severidad` | `BAJA`, `MEDIA`, `ALTA` | `AlertaAnomaliaResponse.severidad` | Los 3, calculado por `AnomaliaService` |
| `TipoAlerta` | `VOLUMEN_ATIPICO`, `RIESGO_ADULTERACION` | `AlertaAnomaliaResponse.tipo` | Los 2, calculado por `AnomaliaService` |

**Riesgo concreto encontrado en `VentaRequest.tipoCliente`**: el campo es `@NotBlank String`, **no** un tipo
enum — `VentaService` hace `TipoClienteVenta.valueOf(dto.tipoCliente())` manualmente. Si el móvil (o
cualquier cliente) envía un valor que no sea exactamente `MAYORISTA`/`PROVEEDOR`/`PUBLICO` (ej. typo,
minúsculas, un valor futuro), `valueOf()` lanza `IllegalArgumentException`, que **no está capturada** por
ningún `@ExceptionHandler` específico en `GlobalExceptionHandler` — cae al handler genérico
`Exception.class` → **`500 Internal Server Error`**, no `400 Bad Request`. Ver `DATA-010` en el catálogo de
inconsistencias (§9).

**Estrategia KMP recomendada para todos los enums de este contrato**: modelarlos como `enum class` de
Kotlin **con un valor `DESCONOCIDO`/`UNKNOWN` de reserva** y un `KSerializer` custom que, ante un valor no
reconocido, decodifique a ese valor de reserva en vez de lanzar una excepción de deserialización —
precisamente porque el contrato JSON real es un `String` libre, sin garantía de OpenAPI de que el conjunto
esté cerrado, y porque ya existe evidencia (`OBSERVADO`) de un valor de dominio válido que la app debe
poder recibir aunque hoy no se produzca.

### 1.7 Booleanos

`Boolean`/`boolean` ↔ JSON `true`/`false` ↔ SQLite `INTEGER` (`0`/`1`, SQLite no tiene tipo boolean nativo
— convención estándar de SQLDelight). Sin sorpresas, salvo la nullability caso por caso (ver §4).

---

## 2. Auditoría de tipos — tabla general

| Tipo Java | JSON | KMP recomendado | SQLite | ¿Compatible? |
|---|---|---|---|---|
| `UUID` | `string` | `String` | `TEXT` | Sí (§1.2) |
| `String` | `string` | `String` | `TEXT` | Sí |
| `Integer` | `number` (entero) | `Int` | `INTEGER` | Sí |
| `Boolean`/`boolean` | `boolean` | `Boolean` | `INTEGER` (0/1) | Sí, con `ColumnAdapter` trivial |
| `BigDecimal` | `number` (decimal) | `com.ionspin.kotlin.bignum.decimal.BigDecimal` (**no** `Double`) | `TEXT` (decimal exacto en texto, **no** `REAL`) | Sí, solo con el tipo/adapter correctos — ver §1.5 |
| `LocalDate` | `string` (`yyyy-MM-dd`) | `kotlinx.datetime.LocalDate` | `TEXT` (ISO) | Sí |
| `LocalDateTime` | `string` (`yyyy-MM-ddTHH:mm:ss[.SSSSSS]`, sin zona) | `kotlinx.datetime.LocalDateTime` (**no** `Instant`) | `TEXT` (ISO) | Sí, con la advertencia de zona horaria de §1.4 |
| `LocalTime` | `string` (`HH:mm:ss`) | `kotlinx.datetime.LocalTime` | `TEXT` (ISO) | Sí |
| `Instant` | `string` (ISO-8601 con `Z`) | `kotlin.time.Instant` / `kotlinx.datetime.Instant` | `TEXT` (ISO) | Sí — único campo: `CambiosResponse.generadoEn` |
| `Enum` (aplanado a `String` en el DTO) | `string` | `enum class` con valor `UNKNOWN` de reserva + serializer custom | `TEXT` (guardar el `.name` string) | Sí, con la estrategia de §1.6 |
| `List<UUID>` | `array` de `string` | `List<String>` | tabla puente N:M (no columna) | Sí |
| `List<T>` (nested DTO) | `array` de `object` | `List<T_KMP>` | tabla hija o cache separada | Sí |
| `Object`/DTO anidado | `object` | `data class` anidado | columnas propias o tabla propia | Sí |

No se detectó ningún campo `Long`, `Float`, `Set<T>` expuesto directamente en un DTO JSON (los `Set<T>` de
las Entities — ej. `LoteProduccion.registrosVinculados` — se serializan como `List<UUID>` en el DTO de
Response, nunca como `Set` crudo).

---

## 3. Nullability — auditoría obligatoria por campo

Todas las tablas de la §5 incluyen una columna `Nullable` verificada contra: (a) la anotación Bean
Validation del Request DTO (`@NotNull`/`@NotBlank`/`@NotEmpty` → obligatorio), (b) el tipo real que asigna
el Service antes de `.save()` (si siempre asigna un valor no-null aunque el DTO lo permita nulo, el
Response es no-nulo en la práctica), y (c) el `nullable`/ausencia de `nullable=false` en la `@Column` de la
Entity (contrastado con `schema.sql`). Casos de **nullability real** que un mapeo ingenuo fácilmente pasa
por alto, encontrados en esta auditoría:

| Campo | Por qué es nullable (evidencia) |
|---|---|
| `CambiosResponse.precioLitroVigente` | `precioLitroRepository.findVigenteHoy().map(...).orElse(null)` — explícitamente `null` si no hay precio vigente configurado |
| `LoteProduccionResponse.rendimientoPct` | Solo se calcula `if (litrosUsados > 0)`; con `litrosUsados == 0` (permitido por `@DecimalMin("0.0" inclusive=true)`) el campo queda `null` |
| `RecepcionPlantaResponse.litrosRegistradosAcopio` | Viene de `SUM(...)` en SQL (`sumLitrosPorUnidadYFecha`) — un `SUM()` sobre cero filas devuelve `NULL` en SQL, no `0` |
| `ProveedorPublicoResponse.codigoQr` | Entity: `@Column(name="codigo_qr", unique=true, length=64)` sin `nullable=false`; `schema.sql`: `codigo_qr VARCHAR(64) UNIQUE` sin `NOT NULL` — en la práctica `ProveedorService.crear` siempre asigna un valor, pero el contrato no lo garantiza |
| `UnidadResponse.capacidadTon` | Entity sin `nullable=false`; `schema.sql`: `capacidad_ton NUMERIC(5,2)` sin `NOT NULL` |
| `UnidadResponse.zonaId` | `@ManyToOne` sin `optional=false`; `schema.sql`: `zona_id UUID REFERENCES zona(id)` sin `NOT NULL` |
| `RegistroAcopioResponse.gpsLat`/`gpsLng` | Sin `@NotNull` en el Request, sin `nullable=false` en la Entity — captura offline sin GPS disponible es un caso real |
| `RegistroAcopioResponse.motivoObservacion` | Solo presente si `motivoObservacionId` vino en el request |
| `AnalisisCalidadResponse.agua/proteina/lactosa/densidad/temperatura/ph` (6 campos) | Ninguno tiene `@NotNull` en el Request ni `nullable=false` en la Entity — el lactoscan puede no reportar todos los valores |
| `AlertaAnomaliaResponse.zScore` | `schema.sql`: `z_score NUMERIC(6,3)` sin `NOT NULL` |
| `RutaProveedorOrdenResponse.horaEstimada` | `RutaItemRequest.horaEstimada` sin `@NotNull`; `schema.sql`: `hora_estimada TIME` sin `NOT NULL` |
| `RecepcionPlantaRequest.turno` | Comentario explícito en el DTO: "opcional, default UNICO"; `ConciliacionService` aplica el default si viene `null`/blank |

**Ejemplo del tipo de error que esto previene** (el que da la consigna): si el Request real es
`RecepcionPlantaRequest.turno: String?` (nullable) y el modelo KMP propuesto fuera
`val turno: String` (no-nulo, sin default), cualquier formulario que no complete el turno fallaría la
serialización o forzaría un valor inventado en el cliente — **NULLABILITY_MISMATCH**. La tabla de §5 marca
esto explícitamente para cada campo.

---

## 4. Request vs Response — sin reutilización de DTOs

Verificado explícitamente que el backend **nunca** reutiliza un mismo DTO para request y response de un
mismo recurso — son siempre tipos distintos con campos distintos. Asimetrías reales encontradas (no
inventadas, confirmadas leyendo ambos DTOs lado a lado):

| Recurso | Solo en Request | Solo en Response | Nota |
|---|---|---|---|
| RegistroAcopio | — | `id`, `proveedorNombre`, `sincronizadoEn` | `motivoObservacionId` (Request, UUID) vs `motivoObservacion` (Response, **String** — la descripción, no el id) — nombre **y** tipo distintos, no es el mismo campo renombrado |
| AnalisisCalidad | — | `id`, `resultado`, `creadoEn` | `resultado` es 100% calculado server-side, nunca lo envía el cliente |
| LoteProduccion | — | `id`, `tipoQuesoNombre`, `rendimientoPct`, `rendimientoEsperadoPct` | `registroAcopioIds` sí aparece en ambos lados (mismo nombre, mismo tipo) |
| Venta | — | `id`, `tipoQuesoNombre`, `total` | `total` es columna `GENERATED` en Postgres, nunca se envía, se relee post-`INSERT` (`entityManager.refresh`) |
| RecepcionPlanta | — | `id`, `diferenciaPct`, `estado`, `litrosRegistradosAcopio` | Los 3 últimos son 100% calculados server-side |
| Comunicado (confirmación) | `proveedorId` únicamente | `id`, `proveedorNombre`, `acopiadorId`, `acopiadorNombre`, `confirmadoEn` | El `acopiador` se resuelve del JWT, nunca del body — coincide con `API_DOCUMENTATION.md §7` |
| CorreccionRegistro | — | `id`, `registroAcopioId`, `litrosAnterior`, `usuarioNombre`, `creadoEn` | `litrosAnterior` lo copia el Service del registro original, el cliente nunca lo envía |

**Regla para KMP**: cada Request y cada Response debe ser una `data class` **separada**, nunca una sola
clase con campos opcionales para cubrir ambos casos — replicar exactamente esta asimetría evita que la UI
intente leer un campo que solo existe en el otro sentido.

---

## 5. Data Dictionary — matriz campo por campo (DTOs MOBILE)

Formato: `Campo | Backend (tipo, ¿obligatorio en request / nullable en response?) | JSON | KMP propuesto |
SQLite | Estado`. Estados usados: `MATCH`, `MISSING_IN_KMP` (se documenta pero aún no está en ningún
modelo KMP porque este es el plano, no el código — se usa para marcar "debe existir"), `NULLABILITY_NOTE`
(nullability real que debe respetarse), `NAME_MISMATCH`, `AMBIGUOUS`, `UNKNOWN`.

### 5.1 Autenticación

**`LoginRequest`** (Request — único endpoint público)

| Campo | Backend | JSON | KMP | SQLite | Estado |
|---|---|---|---|---|---|
| `email` | `String`, `@NotBlank @Email`, obligatorio | `"email"`: string | `val email: String` | N/A | MATCH |
| `password` | `String`, `@NotBlank`, obligatorio | `"password"`: string | `val password: String` | N/A | MATCH |

**`LoginResponse`**

| Campo | Backend | JSON | KMP | SQLite | Estado |
|---|---|---|---|---|---|
| `token` | `String`, siempre presente | string | `val token: String` | `SecureTokenStorage`, no SQLite | MATCH |
| `rol` | `String`, siempre presente (uno de los 6 roles fijos) | string | `val rol: String` (o enum `Rol` con `UNKNOWN` — ver §1.6) | `SecureTokenStorage` | MATCH |
| `nombre` | `String`, siempre presente | string | `val nombre: String` | `SecureTokenStorage` | MATCH |
| `expiraEnSegundos` | `long` primitivo, siempre presente | number (entero) | `val expiraEnSegundos: Long` | `SecureTokenStorage` (para calcular expiración local) | MATCH |

### 5.2 RegistroAcopio (OFFLINE-FIRST)

**`RegistroAcopioDTO`** (Create Request)

| Campo | Backend | JSON | KMP | SQLite (`registro_acopio_local`) | Estado |
|---|---|---|---|---|---|
| `uuidCliente` | `String`, `@NotBlank`, obligatorio, `length=36` | string | `val uuidCliente: String` | `TEXT PRIMARY KEY` | MATCH |
| `proveedorId` | `UUID`, `@NotNull`, obligatorio | string (UUID) | `val proveedorId: String` | `TEXT NOT NULL` | MATCH |
| `unidadId` | `UUID`, `@NotNull`, obligatorio | string (UUID) | `val unidadId: String` | `TEXT NOT NULL` | MATCH |
| `fechaHora` | `LocalDateTime`, `@NotNull`, obligatorio | string, sin zona (§1.4) | `val fechaHora: kotlinx.datetime.LocalDateTime` | `TEXT NOT NULL` | MATCH, con nota de zona §1.4 |
| `litros` | `BigDecimal`, `@NotNull @DecimalMin("0.0" inclusive=true)`, obligatorio, `precision=8,scale=2` | number | `val litros: BigDecimal` (bignum, §1.5) | `TEXT NOT NULL` | MATCH, con nota §1.5 |
| `gpsLat` | `BigDecimal`, **sin** `@NotNull` → opcional, `precision=9,scale=6` | number \| null | `val gpsLat: BigDecimal? = null` | `TEXT NULL` | NULLABILITY_NOTE |
| `gpsLng` | ídem `gpsLat` | number \| null | `val gpsLng: BigDecimal? = null` | `TEXT NULL` | NULLABILITY_NOTE |
| `motivoObservacionId` | `UUID`, comentario explícito "nullable" en el código, opcional | string \| null | `val motivoObservacionId: String? = null` | `TEXT NULL` | NULLABILITY_NOTE |
| `litrosPorVoz` | `Boolean`, **sin** `@NotNull` → opcional (`Service` trata `null` como `false`) | boolean \| null | `val litrosPorVoz: Boolean? = null` | `INTEGER NOT NULL DEFAULT 0` (resolver el default en el mapper KMP→SQLite, no dejar `null` en la tabla local) | NULLABILITY_NOTE |
| `fotoUrl` | `String`, opcional, `length=255` | string \| null | `val fotoUrl: String? = null` | ver nota | Ver `MOBILE_ARCHITECTURE.md §18.4` — no hay endpoint de subida; el campo del contrato existe pero no tiene backend de almacenamiento real detrás |

**`RegistroAcopioResponse`**

| Campo | Backend | JSON | KMP | Estado |
|---|---|---|---|---|
| `id` | `UUID`, generado por servidor, siempre presente | string | `val id: String` | MATCH — es el `server_id`, ver §1.3 |
| `uuidCliente` | `String`, siempre presente (eco del request) | string | `val uuidCliente: String` | MATCH — clave de reconciliación local |
| `proveedorId` | `UUID`, siempre presente | string | `val proveedorId: String` | MATCH |
| `proveedorNombre` | `String`, siempre presente | string | `val proveedorNombre: String` | MATCH — **no existe en el Request**, es enriquecido por el Service |
| `unidadId` | `UUID`, siempre presente | string | `val unidadId: String` | MATCH |
| `fechaHora` | `LocalDateTime`, siempre presente | string, sin zona | `val fechaHora: kotlinx.datetime.LocalDateTime` | MATCH |
| `litros` | `BigDecimal`, siempre presente | number | `val litros: BigDecimal` | MATCH |
| `gpsLat` | `BigDecimal`, nullable (eco) | number \| null | `val gpsLat: BigDecimal?` | NULLABILITY_NOTE |
| `gpsLng` | `BigDecimal`, nullable (eco) | number \| null | `val gpsLng: BigDecimal?` | NULLABILITY_NOTE |
| `motivoObservacion` | `String`, nullable — **es la descripción**, no el id | string \| null | `val motivoObservacion: String?` | **NAME_MISMATCH** frente al request (`motivoObservacionId: UUID` → `motivoObservacion: String`) — no mapear como el mismo campo en un modelo de dominio compartido sin un mapper explícito |
| `litrosPorVoz` | `Boolean`, siempre no-nulo en response (`Boolean.TRUE.equals(...)` normaliza) | boolean | `val litrosPorVoz: Boolean` | MATCH — nótese que en el Request es nullable pero en el Response nunca lo es |
| `sincronizadoEn` | `LocalDateTime`, siempre presente | string, sin zona | `val sincronizadoEn: kotlinx.datetime.LocalDateTime` | MATCH |
| — | `fotoUrl` **no existe en este DTO** | — | — | **MISSING_IN_BACKEND** (ver `MOBILE_ARCHITECTURE.md §18.6`) — no inventar el campo en KMP aunque exista en el Request |

**`RegistroAcopioResumenResponse`** (historial liviano, RF-TRA-01)

| Campo | Backend | JSON | KMP | Estado |
|---|---|---|---|---|
| `id` | `UUID`, siempre presente | string | `val id: String` | MATCH |
| `fechaHora` | `LocalDateTime`, siempre presente | string | `val fechaHora: kotlinx.datetime.LocalDateTime` | MATCH |
| `litros` | `BigDecimal`, siempre presente | number | `val litros: BigDecimal` | MATCH |
| `tieneObservacion` | `boolean` primitivo, siempre presente (derivado: `motivoObservacion != null`) | boolean | `val tieneObservacion: Boolean` | MATCH |
| — | `uuidCliente` **no existe en este DTO** (sí en `RegistroAcopioResponse`) | — | — | **`DATA-013`** — impide deduplicar un registro ajeno contra las filas propias y obliga a pedir el detalle uno por uno si se necesita el `uuidCliente`. No bloquea la resolución del padre (que usa `id`) |
| — | `proveedorId`/`proveedorNombre` **no existen en este DTO** | — | — | El listado es por proveedor, así que el dato es implícito en el path param — no es un gap, se anota para que el mapper no lo busque |

**`CorreccionRegistroRequest`**

| Campo | Backend | JSON | KMP | Estado |
|---|---|---|---|---|
| `litrosCorregido` | `BigDecimal`, `@NotNull @DecimalMin("0.0")`, obligatorio | number | `val litrosCorregido: BigDecimal` | MATCH |
| `motivo` | `String`, sin constraint → opcional | string \| null | `val motivo: String? = null` | NULLABILITY_NOTE |

Sin `uuidCliente` — ver `MOBILE_ARCHITECTURE.md §18.7` (gap de idempotencia, agregado en esta auditoría).

**`CorreccionRegistroResponse`**

| Campo | Backend | JSON | KMP | Estado |
|---|---|---|---|---|
| `id` | `UUID`, siempre presente | string | `val id: String` | MATCH |
| `registroAcopioId` | `UUID`, siempre presente | string | `val registroAcopioId: String` | MATCH |
| `litrosAnterior` | `BigDecimal`, siempre presente (copiado del registro original por el Service) | number | `val litrosAnterior: BigDecimal` | MATCH — **no existe en el Request** |
| `litrosCorregido` | `BigDecimal`, siempre presente | number | `val litrosCorregido: BigDecimal` | MATCH |
| `motivo` | `String`, nullable (eco) | string \| null | `val motivo: String?` | NULLABILITY_NOTE |
| `usuarioNombre` | `String`, siempre presente | string | `val usuarioNombre: String` | MATCH — resuelto del JWT, no del body |
| `creadoEn` | `LocalDateTime`, siempre presente | string | `val creadoEn: kotlinx.datetime.LocalDateTime` | MATCH |

### 5.3 AnalisisCalidad (OFFLINE-FIRST, con dependencia — ver `MOBILE_ARCHITECTURE.md §18.1`)

**`AnalisisCalidadRequest`**

| Campo | Backend | JSON | KMP | SQLite (`analisis_calidad_local`) | Estado |
|---|---|---|---|---|---|
| `uuidCliente` | `String`, `@NotBlank`, obligatorio | string | `val uuidCliente: String` | `TEXT PRIMARY KEY` | MATCH |
| `registroAcopioId` | `UUID`, `@NotNull`, obligatorio — **es el `id` de servidor del RegistroAcopio, no su `uuidCliente`** | string | `val registroAcopioId: String` (resuelto en el cliente antes de enviar — ver §1.3 y arquitectura §18.1) | Localmente se referencia por `registro_acopio_uuid_cliente TEXT NOT NULL`, resuelto a `registroAcopioId` real recién al armar el request de red | **AMBIGUOUS** — requiere la capa de indirección documentada en `MOBILE_ARCHITECTURE.md §11.1/§18.1`, no un mapeo 1:1 directo |
| `folioMuestra` | `String`, `@NotBlank`, obligatorio, `length=40` | string | `val folioMuestra: String` | `TEXT NOT NULL` | MATCH |
| `agua` | `BigDecimal`, opcional, `precision=5,scale=2` | number \| null | `val agua: BigDecimal? = null` | `TEXT NULL` | NULLABILITY_NOTE |
| `proteina` | ídem, `precision=5,scale=2` | number \| null | `val proteina: BigDecimal? = null` | `TEXT NULL` | NULLABILITY_NOTE |
| `lactosa` | ídem, `precision=5,scale=2` | number \| null | `val lactosa: BigDecimal? = null` | `TEXT NULL` | NULLABILITY_NOTE |
| `densidad` | ídem, `precision=6,scale=2` | number \| null | `val densidad: BigDecimal? = null` | `TEXT NULL` | NULLABILITY_NOTE |
| `temperatura` | ídem, `precision=5,scale=2` | number \| null | `val temperatura: BigDecimal? = null` | `TEXT NULL` | NULLABILITY_NOTE |
| `ph` | ídem, `precision=4,scale=2` | number \| null | `val ph: BigDecimal? = null` | `TEXT NULL` | NULLABILITY_NOTE |
| `aguaAnadida` | `Boolean`, sin `@NotNull` → opcional (Service trata `null` como `false`) | boolean \| null | `val aguaAnadida: Boolean? = null` | `INTEGER NOT NULL DEFAULT 0` | NULLABILITY_NOTE |

**`AnalisisCalidadResponse`**

| Campo | Backend | JSON | KMP | Estado |
|---|---|---|---|---|
| `id` | `UUID`, siempre presente | string | `val id: String` | MATCH |
| `registroAcopioId` | `UUID`, siempre presente | string | `val registroAcopioId: String` | MATCH |
| `folioMuestra` | `String`, siempre presente | string | `val folioMuestra: String` | MATCH |
| `agua`…`ph` (6 campos) | `BigDecimal`, nullable (eco) | number \| null | `BigDecimal?` cada uno | NULLABILITY_NOTE |
| `aguaAnadida` | `Boolean`, siempre no-nulo en response | boolean | `val aguaAnadida: Boolean` | MATCH |
| `resultado` | `String`, siempre presente, valores reales `APROBADO`/`RECHAZADO` hoy, `OBSERVADO` posible por dominio pero sin productor actual | string | `enum class ResultadoCalidad { APROBADO, RECHAZADO, OBSERVADO, UNKNOWN }` (§1.6) | AMBIGUOUS — KMP debe soportar `OBSERVADO` aunque hoy nunca llegue |
| `creadoEn` | `LocalDateTime`, siempre presente | string | `val creadoEn: kotlinx.datetime.LocalDateTime` | MATCH |

### 5.4 LoteProduccion (OFFLINE-FIRST, con dependencia)

**`CrearLoteRequest`**

| Campo | Backend | JSON | KMP | SQLite (`lote_produccion_local`) | Estado |
|---|---|---|---|---|---|
| `uuidCliente` | `String`, `@NotBlank`, obligatorio | string | `val uuidCliente: String` | `TEXT PRIMARY KEY` | MATCH |
| `fecha` | `LocalDate`, `@NotNull`, obligatorio | string (`yyyy-MM-dd`) | `val fecha: kotlinx.datetime.LocalDate` | `TEXT NOT NULL` | MATCH |
| `tipoQuesoId` | `UUID`, `@NotNull`, obligatorio | string | `val tipoQuesoId: String` | `TEXT NOT NULL` | MATCH |
| `litrosUsados` | `BigDecimal`, `@NotNull @DecimalMin("0.0" inclusive)`, obligatorio, `precision=9,scale=2` | number | `val litrosUsados: BigDecimal` | `TEXT NOT NULL` | MATCH |
| `unidadesObtenidas` | `Integer`, `@NotNull @Min(0)`, obligatorio | number (entero) | `val unidadesObtenidas: Int` | `INTEGER NOT NULL` | MATCH |
| `registroAcopioIds` | `List<UUID>`, `@NotEmpty`, obligatorio (mín. 1 elemento) | array de string, no vacío | `val registroAcopioIds: List<String>` (mismo problema de indirección que §5.3) | tabla puente `lote_produccion_registro_local` | AMBIGUOUS — misma dependencia de `server_id` que AnalisisCalidad |

**`LoteProduccionResponse`**

| Campo | Backend | JSON | KMP | Estado |
|---|---|---|---|---|
| `id` | `UUID`, siempre presente | string | `val id: String` | MATCH |
| `fecha` | `LocalDate`, siempre presente | string | `val fecha: kotlinx.datetime.LocalDate` | MATCH |
| `tipoQuesoNombre` | `String`, siempre presente | string | `val tipoQuesoNombre: String` | MATCH — no existe en el Request |
| `litrosUsados` | `BigDecimal`, siempre presente | number | `val litrosUsados: BigDecimal` | MATCH |
| `unidadesObtenidas` | `Integer`, siempre presente | number | `val unidadesObtenidas: Int` | MATCH |
| `rendimientoPct` | `BigDecimal`, **nullable** — solo calculado si `litrosUsados > 0` | number \| null | `val rendimientoPct: BigDecimal?` | **NULLABILITY_NOTE** (caso real detectado, fácil de pasar por alto) |
| `rendimientoEsperadoPct` | `BigDecimal`, siempre presente (viene de `TipoQueso`) | number | `val rendimientoEsperadoPct: BigDecimal` | MATCH |
| `registroAcopioIds` | `List<UUID>`, siempre presente, no vacío en la práctica | array de string | `val registroAcopioIds: List<String>` | MATCH |

### 5.5 Venta (OFFLINE-FIRST)

**`VentaRequest`**

| Campo | Backend | JSON | KMP | SQLite (`venta_local`) | Estado |
|---|---|---|---|---|---|
| `uuidCliente` | `String`, `@NotBlank`, obligatorio | string | `val uuidCliente: String` | `TEXT PRIMARY KEY` | MATCH |
| `fecha` | `LocalDate`, `@NotNull`, obligatorio | string | `val fecha: kotlinx.datetime.LocalDate` | `TEXT NOT NULL` | MATCH |
| `tipoCliente` | `String`, `@NotBlank`, obligatorio — **validado solo en runtime** vía `TipoClienteVenta.valueOf(...)`, no por Bean Validation | string | `enum class TipoClienteVenta { MAYORISTA, PROVEEDOR, PUBLICO }` — **el cliente KMP debe restringir a estos 3 valores en la UI** (ej. un selector, nunca texto libre) para evitar el 500 documentado en `DATA-010` | `TEXT NOT NULL CHECK(...)` | **AMBIGUOUS/riesgo** — ver `DATA-010` |
| `tipoQuesoId` | `UUID`, `@NotNull`, obligatorio | string | `val tipoQuesoId: String` | `TEXT NOT NULL` | MATCH |
| `cantidad` | `Integer`, `@NotNull @Min(1)`, obligatorio | number | `val cantidad: Int` | `INTEGER NOT NULL` | MATCH |
| `precioUnitario` | `BigDecimal`, `@NotNull @DecimalMin("0.0")`, obligatorio, `precision=8,scale=2` | number | `val precioUnitario: BigDecimal` | `TEXT NOT NULL` | MATCH |

**`VentaResponse`**

| Campo | Backend | JSON | KMP | Estado |
|---|---|---|---|---|
| `id` | `UUID`, siempre presente | string | `val id: String` | MATCH |
| `fecha` | `LocalDate`, siempre presente | string | `val fecha: kotlinx.datetime.LocalDate` | MATCH |
| `tipoCliente` | `String`, siempre presente (eco, aplanado) | string | `TipoClienteVenta` | MATCH |
| `tipoQuesoNombre` | `String`, siempre presente | string | `val tipoQuesoNombre: String` | MATCH — no existe en el Request |
| `cantidad` | `Integer`, siempre presente | number | `val cantidad: Int` | MATCH |
| `precioUnitario` | `BigDecimal`, siempre presente | number | `val precioUnitario: BigDecimal` | MATCH |
| `total` | `BigDecimal`, siempre presente — **columna `GENERATED ALWAYS` de Postgres**, nunca se envía en el Request | number | `val total: BigDecimal` | MATCH, pero **read-only**: KMP no debe intentar calcular/enviar este valor, ni asumir que coincide con un cálculo local `cantidad × precioUnitario` hecho con redondeo distinto |

### 5.6 Sync API

**`SyncResultResponse`** (Response de los 4 endpoints `POST /api/sync/*`)

| Campo | Backend | JSON | KMP | Estado |
|---|---|---|---|---|
| `confirmados` | `List<String>`, siempre presente, puede ser lista vacía | array de string (uuidCliente) | `val confirmados: List<String>` | MATCH |
| `errores` | `List<SyncErrorItem>`, siempre presente, puede ser lista vacía | array de object | `val errores: List<SyncErrorItem>` | MATCH |
| `SyncErrorItem.uuidCliente` | `String`, siempre presente dentro de cada item de error | string | `val uuidCliente: String` | MATCH |
| `SyncErrorItem.motivo` | `String`, siempre presente | string | `val motivo: String` | MATCH — mensaje de error legible, mismo texto que devolvería el endpoint síncrono equivalente |

**Forma exacta del Request de los 4 endpoints de sync** (verificado en `SyncController`): el body es
**un array JSON crudo**, no un objeto envolvente:

```text
POST /api/sync/registros-acopio
Content-Type: application/json

[ { "uuidCliente": "...", "proveedorId": "...", ... }, { ... } ]
```

**No** `{ "items": [...] }` ni `{ "registros": [...] }` — confirmado leyendo la firma del método
(`@RequestBody List<RegistroAcopioDTO> lote`). Mismo patrón para `analisis-calidad`, `lotes-produccion`,
`ventas`. **KMP debe serializar el lote como `List<T>` directo**, no envolverlo en un objeto.

**Tamaño de lote**: no se encontró ningún `@Size` ni límite explícito sobre estas listas en el Controller ni
el Service. `UNKNOWN`: no hay evidencia de un límite máximo de items por lote soportado por el backend —
no asumir que es ilimitado en la práctica (timeout HTTP, memoria); se recomienda que el cliente trocee en
lotes conservadores (ej. 50–100 items) como medida defensiva propia, no porque el backend lo exija
documentalmente.

**`CambiosResponse`** (Response de `GET /api/sync/cambios`)

| Campo | Backend | JSON | KMP | Estado |
|---|---|---|---|---|
| `generadoEn` | **`Instant`** (¡el único campo `Instant` de todo el contrato MOBILE!), siempre presente | string, con `Z` (UTC real) | `val generadoEn: kotlin.time.Instant` | MATCH — **no confundir con los demás timestamps del contrato, que son `LocalDateTime`** (§1.4) |
| `proveedores` | `List<ProveedorPublicoResponse>`, siempre presente, puede ser vacía | array | `val proveedores: List<ProveedorPublicoResponse>` | MATCH |
| `precioLitroVigente` | `BigDecimal`, **nullable** (`.orElse(null)`) | number \| null | `val precioLitroVigente: BigDecimal?` | **NULLABILITY_NOTE** |
| `comunicados` | `List<ComunicadoResponse>`, siempre presente | array | `val comunicados: List<ComunicadoResponse>` | MATCH |
| `prediccionesProveedor` | `List<PrediccionProveedorResponse>`, siempre presente | array | `val prediccionesProveedor: List<PrediccionProveedorResponse>` | MATCH |
| `motivosObservacion` | `List<MotivoObservacionResponse>`, siempre presente (solo activos, filtrado server-side) | array | `val motivosObservacion: List<MotivoObservacionResponse>` | MATCH |
| `tiposQueso` | `List<TipoQuesoResponse>`, siempre presente (solo activos, filtrado server-side) | array | `val tiposQueso: List<TipoQuesoResponse>` | MATCH — nota: dentro de este endpoint, `activo` siempre llegará `true` (filtro `findByActivoTrue()`), aunque el campo del DTO admite `false` en otros contextos hipotéticos |
| `unidades` | `List<UnidadResponse>`, siempre presente (**sin** filtrar por ningún estado — `unidad` no tiene columna `activo`) | array | `val unidades: List<UnidadResponse>` | MATCH |

Query param: `desde` (`Instant`, **opcional**, `@RequestParam(required = false)`) — **confirmado sin efecto
en v1** (el comentario del propio código lo dice explícitamente: siempre se devuelve el estado completo).
KMP no debe implementar lógica de filtrado incremental basada en este parámetro todavía — enviarlo o no es
irrelevante para el resultado hoy.

**Nested DTOs de `CambiosResponse`**:

`ProveedorPublicoResponse`

| Campo | Backend | JSON | KMP | Estado |
|---|---|---|---|---|
| `id` | `UUID`, siempre presente | string | `val id: String` | MATCH |
| `nombre` | `String`, siempre presente | string | `val nombre: String` | MATCH |
| `zonaActualId` | `UUID`, **nullable** (`@ManyToOne` sin `optional=false`, `zona_actual_id` sin `NOT NULL`) | string \| null | `val zonaActualId: String?` | NULLABILITY_NOTE |
| `zonaActualNombre` | `String`, nullable (eco de la relación) | string \| null | `val zonaActualNombre: String?` | NULLABILITY_NOTE |
| `codigoQr` | `String`, **nullable** por schema (§3), en la práctica casi siempre presente | string \| null | `val codigoQr: String?` | NULLABILITY_NOTE — la resolución de QR offline (`MOBILE_ARCHITECTURE.md §3.3`) debe manejar el caso `null` |

`ComunicadoResponse`

| Campo | Backend | JSON | KMP | Estado |
|---|---|---|---|---|
| `id` | `UUID`, siempre presente | string | `val id: String` | MATCH |
| `mensaje` | `String`, siempre presente | string | `val mensaje: String` | MATCH |
| `fecha` | **`LocalDateTime`** (no `LocalDate` — atención al nombre engañoso) | string, sin zona | `val fecha: kotlinx.datetime.LocalDateTime` | MATCH — cuidado, fácil de asumir `LocalDate` por el nombre del campo |
| `zonasNombres` | `List<String>`, siempre presente, no vacía en la práctica (`CrearComunicadoRequest.zonaIds` exige `@NotEmpty`) | array de string | `val zonasNombres: List<String>` | MATCH |

`PrediccionProveedorResponse` (idéntico si se consulta también vía `GET /api/innovacion/prediccion/{id}`)

| Campo | Backend | JSON | KMP | Estado |
|---|---|---|---|---|
| `proveedorId` | `UUID`, siempre presente | string | `val proveedorId: String` | MATCH |
| `fechaPrevista` | `LocalDate`, siempre presente | string | `val fechaPrevista: kotlinx.datetime.LocalDate` | MATCH |
| `litrosEstimadosMin` | `BigDecimal`, siempre presente, `precision=8,scale=2` | number | `val litrosEstimadosMin: BigDecimal` | MATCH |
| `litrosEstimadosMax` | `BigDecimal`, siempre presente | number | `val litrosEstimadosMax: BigDecimal` | MATCH |

`MotivoObservacionResponse`: `id: UUID` (siempre), `descripcion: String` (siempre) — MATCH trivial.

`TipoQuesoResponse`

| Campo | Backend | JSON | KMP | Estado |
|---|---|---|---|---|
| `id` | `UUID`, siempre presente | string | `val id: String` | MATCH |
| `nombre` | `String`, siempre presente | string | `val nombre: String` | MATCH |
| `rendimientoEsperadoPct` | `BigDecimal`, siempre presente, `precision=5,scale=2` | number | `val rendimientoEsperadoPct: BigDecimal` | MATCH |
| `cicloCapital` | `String`, siempre presente (aplanado) | string | `enum class CicloCapital { RAPIDO, MADURACION, UNKNOWN }` | MATCH, con estrategia §1.6 |
| `activo` | `Boolean`, siempre presente (`true` siempre en el contexto de `/sync/cambios`) | boolean | `val activo: Boolean` | MATCH |

`UnidadResponse`

| Campo | Backend | JSON | KMP | Estado |
|---|---|---|---|---|
| `id` | `UUID`, siempre presente | string | `val id: String` | MATCH |
| `placa` | `String`, siempre presente, `unique` | string | `val placa: String` | MATCH |
| `capacidadTon` | `BigDecimal`, **nullable** | number \| null | `val capacidadTon: BigDecimal?` | NULLABILITY_NOTE |
| `zonaId` | `UUID`, **nullable** | string \| null | `val zonaId: String?` | NULLABILITY_NOTE |
| `responsableId` | `UUID`, siempre presente (`optional=false`) | string | `val responsableId: String` | MATCH |
| `responsableNombre` | `String`, siempre presente | string | `val responsableNombre: String` | MATCH |

### 5.7 Comunicados (confirmación — ver `MOBILE_ARCHITECTURE.md §18.2`)

**`ConfirmarComunicadoRequest`**: `proveedorId: UUID`, `@NotNull`, obligatorio → `val proveedorId: String`.
Sin `uuidCliente`.

**`ComunicadoConfirmacionResponse`**

| Campo | Backend | JSON | KMP | Estado |
|---|---|---|---|---|
| `id` | `UUID`, siempre presente | string | `val id: String` | MATCH |
| `proveedorId` | `UUID`, siempre presente | string | `val proveedorId: String` | MATCH |
| `proveedorNombre` | `String`, siempre presente | string | `val proveedorNombre: String` | MATCH — no existe en el Request |
| `acopiadorId` | `UUID`, siempre presente | string | `val acopiadorId: String` | MATCH — resuelto del JWT |
| `acopiadorNombre` | `String`, siempre presente | string | `val acopiadorNombre: String` | MATCH |
| `confirmadoEn` | `LocalDateTime`, siempre presente | string | `val confirmadoEn: kotlinx.datetime.LocalDateTime` | MATCH |

### 5.8 Ruta de zona (lectura móvil)

**`RutaProveedorOrdenResponse`**

| Campo | Backend | JSON | KMP | Estado |
|---|---|---|---|---|
| `id` | `UUID`, siempre presente | string | `val id: String` | MATCH |
| `proveedorId` | `UUID`, siempre presente | string | `val proveedorId: String` | MATCH |
| `proveedorNombre` | `String`, siempre presente | string | `val proveedorNombre: String` | MATCH |
| `orden` | `Integer`, siempre presente | number | `val orden: Int` | MATCH |
| `horaEstimada` | `LocalTime`, **nullable** | string \| null | `val horaEstimada: kotlinx.datetime.LocalTime?` | NULLABILITY_NOTE |

### 5.9 RecepcionPlanta (ONLINE-ONLY, RECEPCION)

**`RecepcionPlantaRequest`**

| Campo | Backend | JSON | KMP | Estado |
|---|---|---|---|---|
| `fecha` | `LocalDate`, `@NotNull`, obligatorio | string | `val fecha: kotlinx.datetime.LocalDate` | MATCH |
| `turno` | `String`, **opcional**, default server-side `"UNICO"` si viene `null`/blank | string \| null | `val turno: String? = null` | NULLABILITY_NOTE |
| `unidadId` | `UUID`, `@NotNull`, obligatorio | string | `val unidadId: String` | MATCH |
| `litrosCampo` | `BigDecimal`, `@NotNull @DecimalMin("0.0")`, obligatorio, `precision=9,scale=2` | number | `val litrosCampo: BigDecimal` | MATCH |
| `litrosPlanta` | `BigDecimal`, `@NotNull @DecimalMin("0.0")`, obligatorio, `precision=9,scale=2` | number | `val litrosPlanta: BigDecimal` | MATCH |

**`RecepcionPlantaResponse`**

| Campo | Backend | JSON | KMP | Estado |
|---|---|---|---|---|
| `id` | `UUID`, siempre presente | string | `val id: String` | MATCH |
| `fecha` | `LocalDate`, siempre presente | string | `val fecha: kotlinx.datetime.LocalDate` | MATCH |
| `turno` | `String`, siempre presente (con default ya resuelto) | string | `val turno: String` | MATCH — nótese: nullable en Request, no-nulo en Response |
| `unidadId` | `UUID`, siempre presente | string | `val unidadId: String` | MATCH |
| `litrosCampo` | `BigDecimal`, siempre presente | number | `val litrosCampo: BigDecimal` | MATCH |
| `litrosPlanta` | `BigDecimal`, siempre presente | number | `val litrosPlanta: BigDecimal` | MATCH |
| `diferenciaPct` | `BigDecimal`, columna `GENERATED` de Postgres, releída con `entityManager.refresh()` antes de responder — **en la práctica siempre presente en la respuesta**, pero el tipo JPA es nullable por ser `insertable=false` | number | `val diferenciaPct: BigDecimal` | MATCH (no-nulo en la práctica), no existe en el Request |
| `estado` | `String`, siempre presente (aplanado) | string | `enum class EstadoConciliacion { OK, ALERTA, UNKNOWN }` | MATCH — calculado 100% server-side, no existe en el Request |
| `litrosRegistradosAcopio` | `BigDecimal`, **nullable** (`SUM()` sobre 0 filas → `NULL`) | number \| null | `val litrosRegistradosAcopio: BigDecimal?` | **NULLABILITY_NOTE** — no existe en el Request, es un dato de referencia agregado |

### 5.10 Pago (lectura móvil, RECEPCION)

**`PagoResponse`**

| Campo | Backend | JSON | KMP | Estado |
|---|---|---|---|---|
| `id` | `UUID`, siempre presente | string | `val id: String` | MATCH |
| `proveedorId` | `UUID`, siempre presente | string | `val proveedorId: String` | MATCH |
| `proveedorNombre` | `String`, siempre presente | string | `val proveedorNombre: String` | MATCH |
| `semanaInicio` | `LocalDate`, siempre presente | string | `val semanaInicio: kotlinx.datetime.LocalDate` | MATCH |
| `semanaFin` | `LocalDate`, siempre presente | string | `val semanaFin: kotlinx.datetime.LocalDate` | MATCH |
| `litrosTotales` | `BigDecimal`, siempre presente, `precision=9,scale=2` | number | `val litrosTotales: BigDecimal` | MATCH |
| `precioLitro` | `BigDecimal`, siempre presente, `precision=6,scale=3` (⚠️ **3 decimales**, no 2 — distinto del resto de campos monetarios) | number | `val precioLitro: BigDecimal` | MATCH, cuidado con la escala al formatear en UI |
| `total` | `BigDecimal`, siempre presente, columna `GENERATED`, `precision=10,scale=2` | number | `val total: BigDecimal` | MATCH, read-only |
| `comprobanteGenerado` | `Boolean`, siempre presente | boolean | `val comprobanteGenerado: Boolean` | MATCH |
| `registroAcopioIds` | `List<UUID>`, siempre presente | array | `val registroAcopioIds: List<String>` | MATCH |

No hay Request de creación en el alcance móvil (`POST /api/pagos/generar` es WEB/ADMIN).

### 5.11 Innovación (lectura móvil, CALIDAD)

**`ScoreConfianzaResponse`**

| Campo | Backend | JSON | KMP | Estado |
|---|---|---|---|---|
| `proveedorId` | `UUID`, siempre presente | string | `val proveedorId: String` | MATCH |
| `periodo` | `LocalDate`, siempre presente | string | `val periodo: kotlinx.datetime.LocalDate` | MATCH |
| `score` | `BigDecimal`, siempre presente, `CHECK (score BETWEEN 0 AND 100)`, `precision=5,scale=2` | number | `val score: BigDecimal` | MATCH |
| `componenteCalidad` | `BigDecimal`, siempre presente (`NOT NULL` en schema) | number | `val componenteCalidad: BigDecimal` | MATCH |
| `componenteRegularidad` | `BigDecimal`, siempre presente | number | `val componenteRegularidad: BigDecimal` | MATCH |
| `componenteAnomalias` | `BigDecimal`, siempre presente | number | `val componenteAnomalias: BigDecimal` | MATCH |

**`AlertaAnomaliaResponse`**

| Campo | Backend | JSON | KMP | Estado |
|---|---|---|---|---|
| `id` | `UUID`, siempre presente | string | `val id: String` | MATCH |
| `registroAcopioId` | `UUID`, siempre presente | string | `val registroAcopioId: String` | MATCH |
| `proveedorId` | `UUID`, siempre presente | string | `val proveedorId: String` | MATCH |
| `proveedorNombre` | `String`, siempre presente | string | `val proveedorNombre: String` | MATCH |
| `tipo` | `String`, siempre presente (aplanado) | string | `enum class TipoAlerta { VOLUMEN_ATIPICO, RIESGO_ADULTERACION, UNKNOWN }` | MATCH |
| `zScore` | `BigDecimal`, **nullable** (`schema.sql`: `z_score NUMERIC(6,3)` sin `NOT NULL`) | number \| null | `val zScore: BigDecimal?` | **NULLABILITY_NOTE** |
| `severidad` | `String`, siempre presente (`NOT NULL` en schema, aplanado) | string | `enum class Severidad { BAJA, MEDIA, ALTA, UNKNOWN }` | MATCH |
| `creadoEn` | `LocalDateTime`, siempre presente | string | `val creadoEn: kotlinx.datetime.LocalDateTime` | MATCH |

Query param obligatorio: `GET /api/innovacion/alertas?zonaId={UUID}` — `@RequestParam UUID zonaId` **sin**
`required = false` → obligatorio, `400` si se omite (comportamiento estándar de Spring para un
`@RequestParam` no marcado opcional, sin necesidad de un `@ExceptionHandler` propio).

### 5.12 Modelo de error — `ErrorResponse` (aplica a TODOS los endpoints, todos los códigos de error)

| Campo | Backend | JSON | KMP | Estado |
|---|---|---|---|---|
| `timestamp` | `String` — ya es texto en el propio record, construido con `Instant.now().toString()` (ISO-8601 UTC con `Z`, no ambiguo) | string | `val timestamp: String` (o parsear a `Instant` si se necesita, es seguro porque siempre lleva `Z`) | MATCH |
| `status` | `int` primitivo, siempre presente | number | `val status: Int` | MATCH |
| `error` | `String`, siempre presente (`HttpStatus.getReasonPhrase()`, ej. `"Not Found"`) | string | `val error: String` | MATCH |
| `mensaje` | `String`, siempre presente | string | `val mensaje: String` | MATCH |

Confirmado: es la **única** forma de error de toda la API (incluidos los 401/403 generados antes del
`DispatcherServlet` por `SecurityConfig.authenticationEntryPoint`, y el 403 de `AccessDeniedException`) — un
único `KSerializer`/parser de error alcanza para todo el cliente KMP.

---

## 6. Path parameters (endpoints MOBILE)

| Endpoint | Path param | Tipo backend | Formato | Obligatorio |
|---|---|---|---|---|
| `GET /api/registros-acopio/{id}` | `id` | `UUID` | UUID string | Sí (path) |
| `GET /api/registros-acopio/proveedor/{proveedorId}` | `proveedorId` | `UUID` | UUID string | Sí |
| `POST/GET /api/registros-acopio/{id}/correcciones` | `id` | `UUID` | UUID string | Sí |
| `GET /api/analisis-calidad/folio/{folio}` | `folio` | `String` | texto libre (`folioMuestra`, `length=40`) | Sí — **no es UUID**, es un código de muestra alfanumérico |
| `GET /api/analisis-calidad/registro/{registroAcopioId}` | `registroAcopioId` | `UUID` | UUID string | Sí |
| `GET/{id} /api/lotes-produccion/{id}` | `id` | `UUID` | UUID string | Sí |
| `GET/{id} /api/ventas/{id}` | `id` | `UUID` | UUID string | Sí |
| `POST /api/comunicados/{id}/confirmaciones` | `id` | `UUID` | UUID string | Sí |
| `GET /api/comunicados/zona/{zonaId}` | `zonaId` | `UUID` | UUID string | Sí |
| `GET /api/zonas/{zonaId}/ruta` | `zonaId` | `UUID` | UUID string | Sí |
| `GET /api/proveedores/qr/{codigoQr}` | `codigoQr` | `String` | texto libre (`length=64`, generado como `UUID.randomUUID().toString()` hoy, pero el contrato **no garantiza** que siempre tenga forma de UUID — es un `String` sin `@Pattern`) | Sí |
| `GET /api/recepcion-planta/{id}` | `id` | `UUID` | UUID string | Sí |
| `GET /api/pagos/proveedor/{proveedorId}`, `GET /api/pagos/{id}` | `proveedorId`/`id` | `UUID` | UUID string | Sí |
| `GET /api/innovacion/score/{proveedorId}`, `.../prediccion/{proveedorId}` | `proveedorId` | `UUID` | UUID string | Sí |

Ninguno tiene un formato distinto al que indica su tipo Java — no se encontró ningún `@Pattern`/regex
adicional sobre un path variable en los Controllers MOBILE.

## 7. Query parameters (endpoints MOBILE)

| Endpoint | Param | Tipo | Obligatorio | Default | Notas |
|---|---|---|---|---|---|
| `GET /api/sync/cambios` | `desde` | `Instant` | No (`required=false`) | ninguno | **Sin efecto en v1** (confirmado en código) |
| `GET /api/recepcion-planta` | `unidadId` | `UUID` | No (`required=false`) | ninguno (sin filtro → todas) | — |
| `GET /api/innovacion/alertas` | `zonaId` | `UUID` | **Sí** (`@RequestParam` sin `required=false`) | — | 400 si se omite |

**No existe paginación en ningún endpoint MOBILE** (sin `page`/`size`/`limit`/`offset` en ningún
Controller) y **no existe ningún parámetro de ordenamiento** (`sort`) — el orden de cada listado está
fijo server-side (ej. `findAllByOrderByFechaDesc`). No inventar soporte de paginación/orden en el cliente.

## 8. Headers

| Header | Uso real | Endpoints |
|---|---|---|
| `Authorization: Bearer <token>` | Autenticación JWT | Todos excepto `POST /api/auth/login` |
| `Content-Type: application/json` | Requerido para todo body JSON (`@RequestBody`) | Todos los `POST`/`PUT`/`PATCH` con body |
| Header de idempotencia | **No existe** — la idempotencia se implementa vía el campo `uuidCliente` en el **body**, nunca vía header (ej. no hay `Idempotency-Key`) | — |
| Header custom del backend | **No se encontró ninguno** (sin `X-*` propio en ningún Controller/Filter salvo el estándar `Authorization`) | — |

## 9. Response codes por endpoint MOBILE (verificados en `@ApiResponses`/`@ApiResponse` reales, más 401/403/500 universales de `SecurityConfig`/`GlobalExceptionHandler`)

| Endpoint | Método | 200 | 201 | 204 | 400 | 401 | 403 | 404 | 409 | 422 | 500 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `/api/auth/login` | POST | ✔ | | | ✔ | ✔ (credenciales inválidas) | | | | | |
| `/api/auth/refresh` | POST | ✔ | | | | ✔ | | | | | |
| `/api/registros-acopio` | POST | | ✔ | | ✔ | ✔ | ✔ | ✔ | | ✔ | |
| `/api/registros-acopio/{id}` | GET | ✔ | | | | ✔ | ✔ | ✔ | | | |
| `/api/registros-acopio/proveedor/{id}` | GET | ✔ | | | | ✔ | ✔ | | | | |
| `/api/registros-acopio/{id}/correcciones` | POST | | ✔ | | ✔ | ✔ | ✔ | ✔ | | | |
| `/api/registros-acopio/{id}/correcciones` | GET | ✔ | | | | ✔ | ✔ | | | | |
| `/api/analisis-calidad` | POST | | ✔ | | ✔ | ✔ | ✔ | ✔ | | | |
| `/api/analisis-calidad/folio/{folio}` | GET | ✔ | | | | ✔ | ✔ | | | | |
| `/api/analisis-calidad/registro/{id}` | GET | ✔ | | | | ✔ | ✔ | | | | |
| `/api/lotes-produccion` | POST | | ✔ | | ✔ | ✔ | ✔ | ✔ | | | |
| `/api/lotes-produccion` / `{id}` | GET | ✔ | | | | ✔ | ✔ | ✔ (solo `/{id}`) | | | |
| `/api/ventas` | POST | | ✔ | | ✔ | ✔ | ✔ | ✔ | | ✔ | ✔ (`DATA-010`) |
| `/api/ventas` / `{id}` | GET | ✔ | | | | ✔ | ✔ | ✔ (solo `/{id}`) | | | |
| `/api/comunicados/zona/{id}` | GET | ✔ | | | | ✔ | | | | | |
| `/api/comunicados/{id}/confirmaciones` | POST | | ✔ | | ✔ | ✔ | ✔ | ✔ | | | |
| `/api/sync/{recurso}` (4 endpoints) | POST | ✔ (**siempre 200**, incluso con ítems fallidos dentro) | | | | ✔ | ✔ | | | | |
| `/api/sync/cambios` | GET | ✔ | | | | ✔ | | | | | |
| `/api/zonas/{id}/ruta` | GET | ✔ | | | | ✔ | ✔ | ✔ | | | |
| `/api/proveedores/operativo` | GET | ✔ | | | | ✔ | ✔ | | | | |
| `/api/proveedores/qr/{codigo}` | GET | ✔ | | | | ✔ | ✔ | ✔ | | | |
| `/api/recepcion-planta` | POST | | ✔ | | ✔ | ✔ | ✔ | ✔ | ✔ | | |
| `/api/recepcion-planta` / `{id}` | GET | ✔ | | | | ✔ | ✔ | ✔ (solo `/{id}`) | | | |
| `/api/pagos/proveedor/{id}` / `{id}` | GET | ✔ | | | | ✔ | ✔ | ✔ (solo `/{id}`) | | | |
| `/api/innovacion/score/{id}` | GET | ✔ | | | | ✔ | ✔ | ✔ (sin histórico) | | | |
| `/api/innovacion/prediccion/{id}` | GET | ✔ | | | | ✔ | ✔ | ✔ (sin histórico) | | | |
| `/api/innovacion/alertas` | GET | ✔ | | | ✔ (`zonaId` ausente) | ✔ | ✔ | | | | |

Ningún endpoint MOBILE devuelve `204` (el único `204` de toda la API es `DELETE /api/proveedores/{id}`,
WEB/ADMIN). El único `500` documentado con causa raíz identificada es `DATA-010` (§10) — cualquier otro
`500` debe tratarse en el cliente como error genérico no clasificado, nunca reintentado automáticamente
como si fuera un error de negocio conocido.

---

## 10. DATA CONTRACT ISSUES

```text
ID: DATA-001
Severidad: CRITICAL
Endpoint: todos los que llevan campos LocalDateTime (RegistroAcopio, AnalisisCalidad, LoteProduccion,
          Venta*, Comunicado, ComunicadoConfirmacion, RecepcionPlanta, y sus equivalentes /sync/*)
Campo: creadoEn, fechaHora, sincronizadoEn, confirmadoEn, fecha (Comunicado), generadoEn (PrediccionProveedor)
Backend: java.time.LocalDateTime, sin zona horaria explícita
JSON: "2026-09-04T10:15:30" (sin offset, sin 'Z')
KMP propuesto: kotlinx.datetime.LocalDateTime
Problema: no se puede verificar desde el repositorio si el proceso Java que genera estos valores
          (vía LocalDateTime.now()) corre con TZ=UTC o con la hora local del servidor de despliegue.
          hibernate.jdbc.time_zone=UTC en application.yml NO resuelve esto -- solo afecta el binding
          JDBC, no LocalDateTime.now(). No se encontró ninguna configuración de timezone de JVM
          (-Duser.timezone, variable TZ, Dockerfile) en el repositorio.
Impacto: si el móvil asumiera incorrectamente que estos valores son UTC y los convirtiera a Instant
         para mostrarlos en la hora local del dispositivo, todas las fechas quedarían desplazadas
         (~5h si el servidor corre en America/Lima).
Solución: (a) confirmar con el equipo de infra/DevOps el timezone real del proceso en producción;
          (b) mientras tanto, tratar estos campos como "hora de pared", nunca convertir a Instant/zona
          del dispositivo sin esa confirmación.
¿Backend change required?: RECOMENDADO -- fijar explícitamente -Duser.timezone=UTC (o documentar cuál es)
                            en el arranque del proceso, y opcionalmente migrar estos campos a Instant/
                            OffsetDateTime para que el JSON sea auto-descriptivo (como ya lo es
                            CambiosResponse.generadoEn).
```

```text
ID: DATA-002
Severidad: CRITICAL
Endpoint: todos los que llevan campos BigDecimal (litros, precios, porcentajes, coordenadas GPS, medidas
          de laboratorio -- prácticamente todo endpoint de escritura/lectura MOBILE)
Campo: litros, precioUnitario, total, precioLitro, agua/proteina/lactosa/densidad/temperatura/ph,
       rendimientoPct, score, gpsLat/gpsLng, etc.
Backend: java.math.BigDecimal con precision/scale explícitos por columna
JSON: number (literal decimal plano, ej. 12.50)
KMP propuesto: NO Double -- com.ionspin.kotlin:bignum (BigDecimal multiplataforma) con serializer
               custom que lea/escriba el número crudo sin pasar por Double
Problema: java.math.BigDecimal no existe en Kotlin/Native (iOS). Un mapeo ingenuo a Double introduce
          error de redondeo IEEE-754 en campos monetarios (Venta.total, Pago.total, Pago.precioLitro con
          3 decimales) y de medición (RegistroAcopio.litros, que además determina montos de pago).
Impacto: montos de pago/venta calculados o comparados en el cliente podrían no coincidir centavo a
         centavo con lo que calcula PostgreSQL (columnas GENERATED), generando desconfianza en cifras
         mostradas al usuario y potenciales descuadres visibles al conciliar.
Solución: adoptar com.ionspin.kotlin:bignum desde la Fase 2 (Network/serialización), nunca introducir
          Double en el camino de estos campos, y persistir en SQLite como TEXT (no REAL).
¿Backend change required?: No.
```

```text
ID: DATA-003
Severidad: HIGH
Endpoint: POST /api/analisis-calidad, POST /api/sync/analisis-calidad, POST /api/lotes-produccion,
          POST /api/sync/lotes-produccion
Campo: AnalisisCalidadRequest.registroAcopioId, CrearLoteRequest.registroAcopioIds
Backend: UUID = id real de Postgres (server-generated), no el uuidCliente del RegistroAcopio padre
OpenAPI: (no verificado en vivo -- inferido del tipo UUID del campo, sin metadata adicional)
KMP propuesto: requiere resolución de server_id local antes de poder enviarse (ver
               MOBILE_ARCHITECTURE.md §11.1/§18.1)
Problema: un RegistroAcopio capturado offline no tiene id de servidor hasta sincronizar; no existe
          endpoint para buscar un RegistroAcopio por su uuidCliente. Ya documentado en profundidad en
          MOBILE_ARCHITECTURE.md §18.1 -- se repite aquí porque es, ante todo, un problema de contrato
          de datos (tipo de referencia incorrecto para el caso de uso offline), no solo de arquitectura.
Impacto: bloquea la creación 100% offline de AnalisisCalidad/LoteProduccion cuando el RegistroAcopio
         referenciado aún no sincronizó.
Solución: ver MOBILE_ARCHITECTURE.md §18.1 (campo alternativo registroAcopioUuidCliente resuelto
          server-side, o mitigación 100% cliente con estado PENDING_DEPENDENCY).
¿Backend change required?: Sí, si se requiere soporte completo -- ver detalle en MOBILE_ARCHITECTURE.md.
```

```text
ID: DATA-004
Severidad: HIGH
Endpoint: POST /api/registros-acopio/{id}/correcciones
Campo: CorreccionRegistroRequest (todo el DTO)
Backend: sin uuidCliente ni ninguna clave natural UNIQUE en correccion_registro
Problema: no idempotente -- un reintento ante una respuesta perdida crea una fila duplicada.
Impacto: corrompe la trazabilidad de litros de un registro (RNF-03/05), que es el propósito mismo de
         esta tabla, si en algún momento se habilita para uso offline.
Solución: agregar uuidCliente + patrón registrarOIgnorarSiDuplicado, igual que los 4 recursos
          offline-first. Detalle completo en MOBILE_ARCHITECTURE.md §18.7 (agregado en esta auditoría).
¿Backend change required?: Recomendado, no bloqueante mientras el endpoint se mantenga ONLINE-ONLY.
```

```text
ID: DATA-005
Severidad: MEDIUM
Endpoint: POST /api/comunicados/{id}/confirmaciones
Campo: ConfirmarComunicadoRequest (todo el DTO)
Backend: sin uuidCliente ni UNIQUE(comunicado_id, proveedor_id) en comunicado_confirmacion
Problema: no idempotente -- mismo patrón que DATA-004, impacto menor (solo ensucia auditoría de
          "quién confirmó", no montos).
Impacto: confirmaciones duplicadas visibles en GET /api/comunicados/{id}/confirmaciones (WEB/ADMIN).
Solución: ver MOBILE_ARCHITECTURE.md §18.2.
¿Backend change required?: Recomendado.
```

```text
ID: DATA-006
Severidad: MEDIUM
Endpoint: POST /api/recepcion-planta
Campo: RecepcionPlantaRequest (todo el DTO)
Backend: sin uuidCliente; clave natural UNIQUE(fecha, unidad_id, turno) -- duplicado devuelve 409, no
         el patrón "devolver el existente" de los otros 4 recursos
Problema: un reintento ante respuesta perdida no es transparente para el cliente -- debe interpretar
          el 409 y hacer un GET adicional para confirmar si su envío anterior sí se guardó.
Impacto: bajo si RECEPCION se mantiene ONLINE-ONLY (supuesto de este plano); relevante solo si se
         decide llevarlo a offline-first en el futuro.
Solución: ver MOBILE_ARCHITECTURE.md §18.3.
¿Backend change required?: Recomendado solo condicionalmente.
```

```text
ID: DATA-007
Severidad: LOW
Endpoint: GET /api/registros-acopio/{id} (y su equivalente devuelto por /sync/registros-acopio)
Campo: fotoUrl
Backend: RegistroAcopioResponse NO expone este campo, pese a que RegistroAcopioDTO (request) sí lo
         acepta y la Entity/schema.sql sí lo persiste (foto_url)
Problema: asimetría Request/Response -- el móvil no puede releer la foto de un registro ya sincronizado.
Impacto: bajo, cosmético, condicionado a que exista backend de subida real (ver DATA-008).
Solución: agregar fotoUrl a RegistroAcopioResponse.
¿Backend change required?: Opcional, atado a DATA-008.
```

```text
ID: DATA-008
Severidad: HIGH (condicional a que la evidencia fotográfica sea requisito de producto)
Endpoint: N/A -- ausencia de endpoint, no bug de uno existente
Campo: RegistroAcopioDTO.fotoUrl / RegistroAcopioResponse (ausente, ver DATA-007)
Backend: fotoUrl es solo un String (una URL ya resuelta) -- no existe ningún endpoint con
         MultipartFile en todo el backend (búsqueda exhaustiva confirmada, cero resultados)
Problema: no hay forma de que el móvil suba el archivo de la foto capturada en campo.
Impacto: si la evidencia fotográfica es un requisito real, el flujo de ACOPIADOR queda incompleto.
Solución: ver MOBILE_ARCHITECTURE.md §18.4.
¿Backend change required?: Sí, condicional.
```

```text
ID: DATA-009
Severidad: LOW
Endpoint: GET /api/sync/cambios
Campo: (ausencia de) rutaZona / RutaProveedorOrdenResponse[]
Backend: CambiosResponse no incluye la ruta de la zona del ACOPIADOR autenticado
Problema: el móvil necesita una llamada adicional (GET /api/zonas/{zonaId}/ruta) para tener la ruta
          del día disponible offline.
Impacto: bajo, una llamada extra, no bloqueante.
Solución: ver MOBILE_ARCHITECTURE.md §18.5.
¿Backend change required?: Opcional.
```

```text
ID: DATA-010
Severidad: HIGH
Endpoint: POST /api/ventas, POST /api/sync/ventas
Campo: VentaRequest.tipoCliente
Backend: String (@NotBlank), validado en runtime vía TipoClienteVenta.valueOf(dto.tipoCliente()) dentro
         de VentaService -- NO es un campo de tipo enum a nivel de Bean Validation/OpenAPI
OpenAPI: (no verificado en vivo; el tipo del campo en el DTO es String, por lo que springdoc no puede
         inferir un enum documentado -- UNKNOWN si el spec real lo restringe de otra forma)
KMP propuesto: enum class TipoClienteVenta { MAYORISTA, PROVEEDOR, PUBLICO } -- restringir en la UI
               (selector, no texto libre) para nunca emitir un valor fuera de estos 3
Problema: un valor que no sea exactamente uno de los 3 (typo, mayúsculas/minúsculas distintas, un
          valor futuro) provoca que TipoClienteVenta.valueOf(...) lance IllegalArgumentException, la
          cual NO está capturada por ningún @ExceptionHandler específico en GlobalExceptionHandler --
          cae al handler genérico Exception.class.
Impacto: el cliente recibe 500 Internal Server Error en vez de 400 Bad Request ante un dato de
         entrada simplemente inválido -- rompe la expectativa (documentada en API_DOCUMENTATION.md §7)
         de que todo error de validación es 400 con ErrorResponse legible.
Solución: mitigación en el cliente: restringir tipoCliente a un selector cerrado de 3 opciones, nunca
          campo de texto libre, para que este caso no ocurra en la práctica desde la app KMP.
¿Backend change required?: RECOMENDADO -- capturar IllegalArgumentException explícitamente en
                            GlobalExceptionHandler (o validar con un enum real + @NotNull en el DTO)
                            para devolver 400 en vez de 500 ante cualquier cliente (móvil, web, o un
                            tercero futuro) que envíe un valor inválido.
```

```text
ID: DATA-011
Severidad: LOW
Endpoint: N/A -- hallazgo transversal
Campo: N/A
Backend: springdoc-openapi 2.6.0 genera /v3/api-docs dinámicamente desde estas mismas clases
Problema: esta auditoría no capturó el JSON real de /v3/api-docs (no hay una instancia de PostgreSQL
          provisionada en este entorno de análisis para levantar el backend). Todo lo documentado aquí
          proviene de la lectura directa de DTOs/Entities/Services/schema.sql -- que, según la jerarquía
          de confianza pedida, es una fuente MÁS autoritativa que el OpenAPI generado, no menos.
Impacto: ninguno sobre la validez de este documento; sí implica que la columna "OpenAPI" de las tablas
         de §5 está marcada como "no verificado en vivo" en los pocos casos donde se menciona
         explícitamente, en vez de afirmarse sin evidencia.
Solución: si se desea una verificación adicional, levantar el backend con Docker (docker-compose con
          Postgres, o testcontainers ya presentes en el proyecto para tests) y diffear /v3/api-docs
          contra este documento antes de la Fase 2.
¿Backend change required?: No.
```

```text
ID: DATA-012
Severidad: HIGH
Endpoint: POST /api/registros-acopio, POST /api/sync/registros-acopio (y por extensión todo recurso
          offline-first, que mezcla una fecha del cliente con timestamps del servidor)
Campo: fechaHora (generado por el DISPOSITIVO) vs. creadoEn / sincronizadoEn (generados por el SERVIDOR)
Backend: fechaHora viene del request -- lo elige el cliente. creadoEn y sincronizadoEn los asigna el
         servidor con LocalDateTime.now(), en el timezone de la JVM que DATA-001 deja como UNKNOWN.
JSON: los tres se serializan idénticos -- "2026-09-04T10:15:30", sin offset y sin 'Z'. Nada en el JSON
      distingue cuál fue generado en Lima y cuál en el servidor.
KMP propuesto: kotlinx.datetime.LocalDateTime para los tres, PERO tratados como magnitudes NO comparables
               entre sí mientras DATA-001 siga abierto.
Problema: DATA-001 plantea el riesgo de mostrar mal una fecha; este hallazgo es distinto y más sutil --
          dos campos DEL MISMO REGISTRO pueden estar en marcos temporales diferentes. El dispositivo
          escribe fechaHora en hora de pared de America/Lima (es la hora que el ACOPIADOR ve en su
          teléfono al capturar). Si el servidor corre en UTC, sincronizadoEn queda 5 horas "adelante"
          de fechaHora sin que ningún dato lo indique.
Impacto: (a) una entrega capturada a las 16:00 y sincronizada al instante mostraría "capturado 16:00 /
         sincronizado 21:00", pareciendo un retraso de 5 horas que no existe; (b) cualquier cálculo de
         "cuánto tardó en sincronizar" da un valor falso; (c) ordenar una lista mezclando fechaHora de
         unos registros con creadoEn de otros produce un orden incorrecto; (d) un filtro "entregas de
         hoy" que compare contra un timestamp de servidor puede incluir o excluir el día equivocado.
Solución: mitigación en el cliente para v1 -- NUNCA comparar, restar ni ordenar mezclando un campo
          generado por el dispositivo con uno generado por el servidor. Mostrarlos siempre por separado
          y etiquetados ("capturado" / "sincronizado"). Todo filtro y todo orden de listados usa
          EXCLUSIVAMENTE fechaHora (marco del dispositivo, consistente entre sí). Ver la regla de UI en
          MOBILE_SCREENS.md §10.3.
¿Backend change required?: Es el mismo cambio de DATA-001 (fijar/documentar el timezone de la JVM, o
                            migrar estos campos a Instant/OffsetDateTime). Resolver DATA-001 cierra
                            también este hallazgo; hasta entonces, la mitigación de arriba es obligatoria.
```

```text
ID: DATA-013
Severidad: MEDIUM
Endpoint: GET /api/registros-acopio/proveedor/{proveedorId}
Campo: (ausencia de) uuidCliente en RegistroAcopioResumenResponse
Backend: el DTO resumen expone solo id, fechaHora, litros, tieneObservacion. El DTO completo
         (RegistroAcopioResponse, devuelto por GET /api/registros-acopio/{id}) sí incluye uuidCliente.
Problema: la mitigación documentada en MOBILE_ARCHITECTURE.md §18.1 se apoya en este endpoint para que
          CALIDAD/PRODUCCION resuelvan registros de acopio AJENOS (capturados en otro dispositivo). Para
          el uso principal alcanza -- el hijo necesita el id de servidor, que sí viene. Pero sin
          uuidCliente el móvil no puede deduplicar esas filas contra las suyas propias en
          registro_acopio_local, ni reconciliar un registro que él mismo capturó y que le vuelve desde
          el servidor.
Impacto: bajo-medio. En la pantalla de selección de registro (MOBILE_SCREENS.md §6.2, §7.2) un mismo
         registro puede aparecer dos veces si el dispositivo lo capturó Y lo descargó. No corrompe datos
         -- ambas entradas apuntan al mismo registro de servidor -- pero es confuso. Obtener el
         uuidCliente exige N llamadas al detalle, una por registro, lo cual es inviable en campo.
Solución: mitigación en el cliente -- registro_acopio_cache lleva una columna `origen` (RESUMEN|DETALLE)
          y la UI prioriza la fila local (propia) cuando el id de servidor coincide con
          registro_acopio_local.server_id, que es el único caso de solapamiento detectable sin llamadas
          extra. El resto se acepta para v1.
¿Backend change required?: RECOMENDADO, trivial -- agregar uuidCliente a RegistroAcopioResumenResponse
                            (el dato ya está en la Entity, es un campo más en el record). Bajo costo,
                            cierra el hueco por completo.
```

---

## 11. Matriz maestra de compatibilidad

| Endpoint | Request | Response | Campos | Tipos | Nullability | JSON naming | Auth | Offline | Sync | Estado |
|---|---|---|---|---|---|---|---|---|---|---|
| `POST /api/auth/login` | `LoginRequest` | `LoginResponse` | OK | OK | OK | OK | público | No | N/A | **COMPATIBLE** |
| `POST /api/auth/refresh` | — | `LoginResponse` | OK | OK | OK | OK | JWT vigente | No | N/A | **COMPATIBLE** |
| `POST /api/registros-acopio` + `/sync/registros-acopio` | `RegistroAcopioDTO` | `RegistroAcopioResponse` | OK (asimetría documentada, DATA-007) | OK, con BigDecimal (DATA-002) y LocalDateTime (DATA-001) | OK (documentada) | OK | ACOPIADOR/ADMIN | Sí | Sí | **COMPATIBLE**, sujeto a resolver DATA-001/002 en la capa de red antes de Fase 2 |
| `GET /api/registros-acopio/{id}`, `/proveedor/{id}` | — | `RegistroAcopioResponse`, `RegistroAcopioResumenResponse` | OK (resumen sin `uuidCliente`, DATA-013) | OK | OK | OK | ACOPIADOR/ADMIN/CALIDAD | Parcial (cache, C-04) | — | **COMPATIBLE**, con la limitación de deduplicación de DATA-013 |
| `POST/GET /api/registros-acopio/{id}/correcciones` | `CorreccionRegistroRequest` | `CorreccionRegistroResponse` | OK | OK | OK | OK | CALIDAD/ADMIN | No | No | **COMPATIBLE** para uso online; **REQUIRES BACKEND CHANGE** (DATA-004) si se quiere offline |
| `POST /api/analisis-calidad` + `/sync/analisis-calidad` | `AnalisisCalidadRequest` | `AnalisisCalidadResponse` | OK | OK, BigDecimal (DATA-002) | OK (6 campos nullable documentados) | OK | CALIDAD/ADMIN | Sí, con dependencia | Sí | **REQUIRES BACKEND CHANGE** (DATA-003) para offline cruzado real; **COMPATIBLE** con la mitigación cliente documentada |
| `GET /api/analisis-calidad/folio/{folio}`, `/registro/{id}` | — | `List<AnalisisCalidadResponse>` | OK | OK | OK | OK | CALIDAD/ADMIN | No | — | **COMPATIBLE** |
| `POST /api/lotes-produccion` + `/sync/lotes-produccion` | `CrearLoteRequest` | `LoteProduccionResponse` | OK | OK | OK (`rendimientoPct` nullable documentado) | OK | PRODUCCION/ADMIN | Sí, con dependencia | Sí | **REQUIRES BACKEND CHANGE** (DATA-003), misma nota que AnalisisCalidad |
| `GET /api/lotes-produccion`, `/{id}` | — | `LoteProduccionResponse` | OK | OK | OK | OK | PRODUCCION/ADMIN | Parcial | — | **COMPATIBLE** |
| `POST /api/ventas` + `/sync/ventas` | `VentaRequest` | `VentaResponse` | OK | OK | OK | OK | VENTAS/ADMIN | Sí | Sí | **COMPATIBLE**, con mitigación de UI obligatoria para `tipoCliente` (DATA-010) |
| `GET /api/ventas`, `/{id}` | — | `VentaResponse` | OK | OK | OK | OK | VENTAS/ADMIN | Parcial | — | **COMPATIBLE** |
| `GET /api/comunicados/zona/{id}` | — | `List<ComunicadoResponse>` | OK | OK (`fecha` es `LocalDateTime`, no `LocalDate`) | OK | OK | cualquiera | Sí (cache) | — | **COMPATIBLE** |
| `POST /api/comunicados/{id}/confirmaciones` | `ConfirmarComunicadoRequest` | `ComunicadoConfirmacionResponse` | OK | OK | OK | OK | ACOPIADOR/ADMIN | No | No | **COMPATIBLE** online; **REQUIRES BACKEND CHANGE** (DATA-005) para offline |
| `GET /api/comunicados/{id}/confirmaciones` | — | — | — | — | — | — | ADMIN | — | — | WEB/ADMIN, fuera de alcance móvil |
| `POST /api/sync/*` (4) | `List<T>` (array crudo) | `SyncResultResponse` | OK | OK | OK | OK | por recurso | Sí | Sí | **COMPATIBLE** |
| `GET /api/sync/cambios` | — (query `desde` sin efecto) | `CambiosResponse` | OK | OK (`generadoEn` es `Instant`, único caso — DATA-001 no aplica a este campo) | OK (`precioLitroVigente` nullable documentado) | OK | cualquiera | — | Sí | **COMPATIBLE** |
| `GET /api/zonas/{id}/ruta` | — | `List<RutaProveedorOrdenResponse>` | OK | OK | OK (`horaEstimada` nullable) | OK | ADMIN/ACOPIADOR | Sí (cache) | — | **COMPATIBLE** |
| `GET /api/proveedores/operativo`, `/qr/{codigo}` | — | `ProveedorPublicoResponse` | OK | OK | OK (`zonaActualId/Nombre`, `codigoQr` nullable) | OK | según endpoint | Sí | — | **COMPATIBLE** |
| `POST/GET /api/recepcion-planta`, `/{id}` | `RecepcionPlantaRequest` | `RecepcionPlantaResponse` | OK | OK | OK (`litrosRegistradosAcopio` nullable documentado) | OK | RECEPCION/ADMIN | No | No | **COMPATIBLE** online-only; **REQUIRES BACKEND CHANGE** (DATA-006) si se quiere offline |
| `GET /api/pagos/proveedor/{id}`, `/{id}` | — | `PagoResponse` | OK | OK (`precioLitro` escala 3, distinta al resto — documentado) | OK | OK | ADMIN/RECEPCION | No | — | **COMPATIBLE** |
| `GET /api/innovacion/score/{id}` | — | `ScoreConfianzaResponse` | OK | OK | OK | OK | ADMIN/CALIDAD | No | — | **COMPATIBLE** |
| `GET /api/innovacion/prediccion/{id}` | — | `PrediccionProveedorResponse` | OK | OK | OK | OK | ADMIN/CALIDAD | Sí (ya en `/sync/cambios`) | — | **COMPATIBLE** |
| `GET /api/innovacion/alertas` | — (query `zonaId` obligatorio) | `List<AlertaAnomaliaResponse>` | OK | OK | OK (`zScore` nullable documentado) | OK | ADMIN/CALIDAD | No | — | **COMPATIBLE** |

**Resumen**: de los endpoints MOBILE con contrato de escritura, **4 son `REQUIRES BACKEND CHANGE`** para
alcanzar el comportamiento offline-first que su propia naturaleza sugeriría (`DATA-003` AnalisisCalidad/
LoteProduccion, `DATA-004` correcciones, `DATA-005` confirmación de comunicado, `DATA-006` condicional para
RecepcionPlanta) — todos documentados también en `MOBILE_ARCHITECTURE.md §18`. **Ninguno queda
`INCOMPATIBLE`**: todo endpoint auditado tiene un mapeo KMP viable, sea directamente o con la mitigación de
cliente descrita. Ningún hallazgo fue ocultado ni marcado compatible sin resolver su discrepancia.

**Estado del catálogo tras la Rev. 2** — 13 hallazgos, ninguno bloqueante para arrancar el desarrollo:

| Severidad | IDs | Qué exigen antes de codear |
|---|---|---|
| CRITICAL | `DATA-001` (timezone), `DATA-002` (BigDecimal) | Ambos deben tener su **estrategia decidida** antes de cerrar la Fase 2. `DATA-002` ya está resuelto en el plano (bignum + `TEXT`, C-01); `DATA-001` sigue esperando respuesta de DevOps, con mitigación segura definida mientras tanto |
| HIGH | `DATA-003` (ids cruzados), `DATA-008` (subida de foto), `DATA-010` (`tipoCliente` → 500), `DATA-012` (marcos temporales) | `DATA-008` cerrado por decisión (diferido a v2). Los otros tres tienen mitigación de cliente obligatoria ya especificada |
| MEDIUM | `DATA-005`, `DATA-006`, `DATA-013` | Ninguno bloquea; los tres son "recomendado" del lado backend |
| LOW | `DATA-007`, `DATA-009`, `DATA-011` | Cosméticos u opcionales |
| HIGH (backend) | `DATA-004` (correcciones no idempotentes) | No bloquea mientras el endpoint siga ONLINE-ONLY, que es el estado en v1 |

---

## 12. UNKNOWN — información que no se pudo verificar desde el repositorio

Por la regla contra suposiciones (no inventar), se listan explícitamente:

### Abiertos (bloquean o condicionan alguna fase)

1. **Timezone real de la JVM en el servidor de despliegue** (`DATA-001`, y por extensión `DATA-012`) —
   determina la interpretación correcta de todo campo `LocalDateTime`. Debe confirmarse con infra/DevOps.
   **Es el único `UNKNOWN` que bloquea el cierre de una fase** (la 2, Network), según §13.
2. **JSON real generado por `/v3/api-docs`** — no capturado en vivo en este análisis (sin instancia
   PostgreSQL disponible en el entorno de auditoría). Se recomienda una verificación adicional levantando
   el backend antes de comenzar la Fase 2 (Network), como paso de doble-chequeo, no como bloqueante (los
   DTOs/Entities ya leídos son la fuente de mayor jerarquía según la propia regla de prioridad).
3. **Límite máximo de items por lote en `/api/sync/*`** — no hay evidencia de un límite server-side
   documentado ni impuesto (`@Size` ausente); se recomienda un troceo defensivo en el cliente sin que esto
   implique que el backend lo exige.
4. **Si `OBSERVADO` (valor del enum `ResultadoCalidad`) llegará a producirse en una fase futura** — existe
   en el dominio y en el `CHECK` de `schema.sql`, pero ningún código actual lo asigna; no se puede saber
   desde el repositorio si hay un flujo manual planeado que lo use. La estrategia de §1.6 (enum con valor
   `UNKNOWN` de reserva) hace que la app lo soporte igual si aparece, así que no bloquea nada.

### Cerrados en Rev. 2 (M-04)

5. ~~**Si RECEPCION realmente opera desde el móvil o desde el panel web**~~ → **RESUELTO: opera desde el
   móvil**, en modo online-only. Confirmado con producto; ver `MOBILE_ARCHITECTURE.md` (introducción, C-08)
   y sus pantallas en `MOBILE_SCREENS.md §9`.
6. ~~**Si la evidencia fotográfica es requisito de v1**~~ (era una pregunta abierta implícita en
   `DATA-008`) → **RESUELTO: diferida a v2.** No hay captura de foto en v1; `fotoUrl` se envía siempre
   `null` y no existe columna local. Ver `MOBILE_ARCHITECTURE.md §18.4` (C-07).

Ninguno de estos puntos fue completado con un valor inventado — los abiertos se documentan como abiertos,
y los cerrados lo fueron por decisión explícita de producto, no por suposición.

---

## 13. Roadmap de fases (definitivo, ver checkpoints obligatorios en cada una)

Confirmado el mismo roadmap de 12 fases pedido, sin modificaciones de alcance (solo se referencia aquí,
el detalle de qué implementa cada una es el que se especificó en la consigna de esta auditoría):

```text
FASE 0  — Auditoría + Data Mapping + Arquitectura + Pantallas
          [ESTE DOCUMENTO + MOBILE_ARCHITECTURE.md + MOBILE_SCREENS.md]                            ✅
FASE 1  — Core y configuración (estructura KMP, Result/Error, modelos base, coroutines, DI)
FASE 2  — Network (Ktor Client, serializers -- incluye el serializer de BigDecimal/fechas de DATA-001/002,
          DTOs, headers, JWT, error handling, timeouts)
FASE 3  — Secure Storage y autenticación (Keystore/Keychain, access token, sesión, logout con la
          política de pendientes de MOBILE_ARCHITECTURE.md §4)
FASE 4  — SQLDelight (schema, queries, DAOs, mappers -- usando las tablas de MOBILE_ARCHITECTURE.md §11
          Rev. 2, con TEXT para todo decimal y la doble referencia al padre de C-02)
FASE 5  — Sync Engine (queue, estados, retries, backoff, uuid_cliente, procesamiento parcial,
          PENDING_DEPENDENCY, resolución de padres ajenos vía registro_acopio_cache)
FASE 6  — Repository + UseCases (integra Local + Remote + Sync)
FASE 7  — Primera funcionalidad vertical de bajo riesgo (candidata: Venta -- sin dependencia cruzada
          de ids, offline-first simple, ver DATA-002/DATA-010 como los dos puntos a validar primero).
          PRIMERA FASE CON UI: aquí entra MOBILE_SCREENS.md (pantallas S-01..S-04 + V-01..V-03)
FASE 8  — Funcionalidades restantes, una por una (RegistroAcopio, luego AnalisisCalidad/LoteProduccion
          con la mitigación PENDING_DEPENDENCY, luego las lecturas online-only), cada una con sus
          pantallas del inventario
FASE 9  — Background Sync (WorkManager / BGTaskScheduler)
FASE 10 — Testing integral (unit, integración, API, SQLite, offline, sync, recovery, presentación)
FASE 11 — Auditoría final: repetir esta comparación Backend ↔ OpenAPI (esta vez capturado en vivo,
          cerrando el UNKNOWN #2 de §12) ↔ KMP ↔ SQLite, verificando cero MISSING/TYPE_MISMATCH/
          NULLABILITY_MISMATCH/NAME_MISMATCH/FORMAT_MISMATCH sin resolver.
```

**Decisiones de UI ya tomadas** (no quedan para la Fase 7): **Compose Multiplatform** para Android e iOS,
con los `ViewModel` en `shared/presentation/`. Ver `MOBILE_ARCHITECTURE.md §14` (C-06) y el detalle de la
capa de presentación en `MOBILE_SCREENS.md §3`. Esto significa que la **Fase 1** ya debe crear
`shared/ui/` y `shared/presentation/` con la estructura de `MOBILE_ARCHITECTURE.md §15`, no agregarlos
después.

Cada fase sigue la regla de checkpoint obligatorio ya acordada: compilar, testear, revisar regresiones,
actualizar documentación, listar archivos, listar problemas y `DATA CONTRACT ISSUES` nuevos si aparecen,
indicar si se requiere cambio de backend, y **detenerse a esperar aprobación** antes de continuar. No se
pasa de fase con `CRITICAL` sin resolver: **`DATA-001` y `DATA-002` deben resolverse (o al menos su
estrategia de mitigación quedar decidida y aprobada) antes de cerrar la Fase 2**, porque ambos son
transversales a la capa de Network/serialización que esa fase construye.

---

*Documento generado a partir de la lectura íntegra de 43 DTOs, 24 Entities, `schema.sql`, `SecurityConfig`,
`GlobalExceptionHandler` y `application.yml` del backend real. Ningún campo, tipo, endpoint o comportamiento
fue inventado; donde la información no pudo verificarse desde el repositorio, se marcó `UNKNOWN`
explícitamente (§12) en vez de asumirse. Fase 0 (auditoría de datos) completada.*

*Rev. 2 (M-01 a M-05): agrega `DATA-012` y `DATA-013`, cierra 2 de los 5 `UNKNOWN` por decisión de
producto, y confirma la alineación con `MOBILE_ARCHITECTURE.md` Rev. 2 en el punto que ambos documentos
contradecían (tipo de columna SQLite para decimales). Se incorpora `MOBILE_SCREENS.md` como tercer
documento de la Fase 0.*

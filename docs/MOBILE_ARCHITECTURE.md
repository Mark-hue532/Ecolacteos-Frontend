# MOBILE_ARCHITECTURE.md — Plano Técnico del Frontend Móvil (KMP)

**Fase 0: Análisis + Arquitectura + Diseño. Ningún código de frontend fue escrito en esta fase.**

Este documento es el plano técnico oficial para construir el frontend móvil del Sistema de Acopio Lechero
con **Kotlin Multiplatform (KMP)**, targets Android + iOS. Está basado en la inspección directa del código
real del backend (`acopio-backend`, Spring Boot 3.3.4 / Java 17): 15 `@RestController`, sus DTOs, Services,
Entities, `schema.sql`, `SecurityConfig`, `GlobalExceptionHandler` y los documentos ya existentes
`API_DOCUMENTATION.md` y los comentarios `CORE_DESIGN.md §n` dejados en el código. No contiene endpoints,
campos ni comportamientos inventados; donde el backend no soporta algo que el modo offline-first necesita,
se documenta explícitamente en la §18 (Cambios necesarios en backend) en vez de diseñar un parche en el
cliente.

> **Los tres documentos de la Fase 0 y su división de trabajo**:
>
> | Documento | Responde | Se usa |
> |---|---|---|
> | **`MOBILE_ARCHITECTURE.md`** (este) | **cómo** se construye la app: capas, offline-first, sync, conflictos, herramientas, estructura | antes de cada fase, para saber qué construir |
> | [`MOBILE_DATA_MAPPING.md`](./MOBILE_DATA_MAPPING.md) | **qué exactamente** viaja por la red: contrato campo por campo, nullability, tipos KMP/SQLite, `DATA-0xx` | abierto al lado mientras se codean DTOs (Fase 2) y `.sq` (Fase 4) |
> | [`MOBILE_SCREENS.md`](./MOBILE_SCREENS.md) | **qué ve y hace el usuario**: inventario de pantallas por rol, `UiState`/eventos, validaciones, estados vacío/carga/error/offline | desde la Fase 7 (primera vertical) en adelante |
>
> **Regla de precedencia entre documentos**: ante cualquier discrepancia de **tipo de dato, nullability o
> nombre de campo**, `MOBILE_DATA_MAPPING.md` es la autoridad (es la auditoría campo por campo contra el
> código real del backend). Ante una discrepancia de **estructura de tablas, capas o flujo de sync**, manda
> este documento. Ante una discrepancia de **comportamiento de pantalla**, manda `MOBILE_SCREENS.md`.

## Revisión 2 — correcciones aplicadas

Esta revisión corrige inconsistencias internas detectadas en una relectura cruzada de los dos documentos de
Fase 0, y cierra tres decisiones que la Rev. 1 dejaba abiertas. Ninguna cambia la arquitectura de fondo;
todas evitan que un error quede congelado en el código de las Fases 4–8.

| # | Corrección | Dónde | Por qué |
|---|---|---|---|
| C-01 | Columnas `BigDecimal` en SQLite pasan de `REAL` a `TEXT` | §11.1, §11.2 | La Rev. 1 declaraba `litros REAL`, `precio_unitario REAL`, etc., contradiciendo directamente `DATA-002` (CRITICAL), que prohíbe `REAL` por reintroducir el error IEEE-754 que el tipo `BigDecimal` existe para evitar |
| C-02 | Nueva tabla `registro_acopio_cache` + columna `registro_acopio_server_id` en las tablas hijas | §11.1, §11.2 | La Rev. 1 referenciaba al `RegistroAcopio` padre **solo** por `uuid_cliente`, lo que hace imposible representar el caso normal: CALIDAD/PRODUCCION trabajando sobre un registro capturado por **otro** dispositivo, del que solo se conoce el `id` de servidor |
| C-03 | `PENDING_DEPENDENCY` incorporado al enum `sync_status` y a la máquina de estados | §6.1, §11.1 | La Rev. 1 lo usaba en §16.2 y §18.1 pero no lo declaraba en el esquema ni en el diagrama de estados |
| C-04 | Resuelta la contradicción sobre cachear registros ajenos | §3.1, §11.2, §12 | §3.1 decía `SQLite: Sí`, §12 decía "No (consulta puntual)" y §11 no declaraba la tabla |
| C-05 | Los ViewModel viven en `shared/presentation/`, no en `androidApp/` | §2, §13, §15 | §2 decía "ViewModel — shared" mientras §13/§15 los ubicaban en `androidApp` con `androidx.lifecycle` (dependencia Android-only), lo que obligaría a reescribirlos en Swift |
| C-06 | Decisión de UI cerrada: **Compose Multiplatform** para Android **e** iOS | §14, §15 | §14 la dejaba "a decidir"; es la decisión que determina si la UI se escribe una o dos veces |
| C-07 | Evidencia fotográfica **diferida a v2**; `foto_local_path` sale del esquema v1 | §11.1, §18.4 | Sin endpoint de subida (`DATA-008`), capturar fotos que nunca se suben acumula archivos sin política de limpieza y promete al usuario algo que el backend no soporta |
| C-08 | RECEPCION **confirmado dentro** del alcance móvil v1, online-only | intro, §3.1 | Era un supuesto explícito sin confirmar; se confirma para poder cerrar el inventario de pantallas |
| C-09 | Nuevas políticas: logout con pendientes, multiusuario por dispositivo, retención local | §4, §11.4 | Tres huecos que no estaban cubiertos y que producen pérdida de datos o atribución incorrecta |
| C-10 | Nuevo riesgo documentado: marcos temporales mezclados dentro de un mismo registro | §20 | `fechaHora` la genera el dispositivo y `creadoEn`/`sincronizadoEn` el servidor; si el servidor no corre en `America/Lima`, compararlos entre sí es incorrecto (ver `DATA-012`) |

> **Evidencia clave que fija el alcance MOBILE vs WEB**: el propio backend ya distingue las dos audiencias
> en sus comentarios de diseño — `SecurityConfig.java`: *"API stateless consumida por Flutter/panel web"* —
> y el tag de Swagger de `SyncController`: *"Consumido por la app móvil offline-first"*, cubriendo
> exactamente los 4 recursos con `uuidCliente` (RegistroAcopio, AnálisisCalidad, LoteProduccion, Venta),
> capturados por los roles ACOPIADOR, CALIDAD, PRODUCCION y VENTAS respectivamente. Este documento adopta
> esa misma frontera, reemplazando "Flutter" por "KMP": **el móvil es la app operativa de campo/planta**
> (ACOPIADOR, CALIDAD, PRODUCCION, VENTAS, y RECEPCION — ver supuesto explícito abajo), **la web es el
> panel administrativo** (ADMIN, catálogos, configuración, reportes).
>
> **RECEPCION: confirmado dentro del alcance móvil** (Rev. 2, C-08 — en la Rev. 1 era un supuesto abierto):
> RECEPCION opera desde el móvil KMP (tablet en el punto de pesaje de planta), en modo **online-only**
> porque el backend no lo diseñó para captura offline (`RecepcionPlantaRequest` no tiene `uuidCliente`, y
> `/api/sync` no incluye este recurso). Sus pantallas están en `MOBILE_SCREENS.md §9`. Si más adelante se
> decidiera moverlo al panel web, los 3 endpoints de `RecepcionPlantaController` pasan de MOBILE a WEB/ADMIN
> sin que cambie nada más de este plano.

---

## 1. Objetivo

Construir la base compartida (Android + iOS) de una app **offline-first real**: la UI nunca llama a la API
directamente, siempre lee/escribe contra SQLite local, y un Sync Engine explícito reconcilia con el backend
Spring Boot cuando hay conectividad. La app consume **solo** los endpoints que corresponden a las
funcionalidades operativas de campo/planta (ACOPIADOR, CALIDAD, RECEPCION, PRODUCCION, VENTAS) — nunca los
de administración de catálogos, usuarios o reportes económicos, que son responsabilidad del panel web.

## 2. Arquitectura

```text
UI (Compose Multiplatform — Android e iOS, ver §14/C-06)
 ↓
Presentation (ViewModel — shared/presentation/, ver C-05)
 ↓
Domain / UseCases (shared, sin dependencias de plataforma)
 ↓
Repository (shared) — única puerta de entrada a datos para la UI
 ↓                 ↘
Local Data Source    Sync Engine (shared) — orquesta cuándo hablar con la red
(SQLDelight/SQLite)      ↓
 ↑                  Remote Data Source (Ktor Client)
 └──────────────────────↓
                    Spring Boot (acopio-backend)
                         ↓
                    PostgreSQL
```

**Los ViewModel viven en `shared/`, no en `androidApp/`** (C-05). Se implementan sobre
`androidx.lifecycle.ViewModel` en su **variante multiplataforma** (`lifecycle-viewmodel` de Compose
Multiplatform, que sí compila a iOS) o, si se prefiere no depender de ella, sobre una clase propia con un
`CoroutineScope` cancelable — la decisión concreta se toma en la Fase 1, pero la **ubicación no es
negociable**: si un `ViewModel` quedara en `androidApp/`, toda la lógica de presentación (validaciones de
formulario, mapeo de errores a mensajes, estados de carga) tendría que reescribirse en Swift, que es
exactamente lo que este plano busca evitar. El contrato `UiState`/`Event`/`Effect` de cada pantalla está en
`MOBILE_SCREENS.md §3`.

Regla dura de esta arquitectura: **ningún ViewModel ni pantalla inyecta o llama un `ApiClient` directamente.**
Todo pasa por un `Repository`, que decide si lee de SQLite, dispara una llamada remota, o encola una
operación pendiente. Sin conexión:

```text
UI → Repository → SQLite
```

Con conexión (lectura con refresco, o sync de pendientes):

```text
UI → Repository → SQLite (lectura optimista)
                → SyncEngine → RemoteDataSource → API → SQLite (confirmación)
```

## 3. Clasificación de endpoints: MOBILE / WEB-ADMIN / COMPARTIDO

Auditoría completa de los **73 endpoints** reales (14 Controllers + `AdminCatalogosController` con 7 tags).
`Offline` = capturable/legible sin señal; `SQLite` = necesita tabla local; `Sync` = participa del Sync Engine.

### 3.1 Módulos MOBILE (consumidos por la app KMP)

| # | Endpoint | Rol(es) | Offline | SQLite | Sync | Clasificación funcional |
|---|---|---|---|---|---|---|
| 1 | `POST /api/auth/login` | público | No | No (token va a secure storage) | N/A | ONLINE-ONLY |
| 2 | `POST /api/auth/refresh` | cualquiera autenticado | No | No | N/A | ONLINE-ONLY |
| 3 | `POST /api/registros-acopio` | ACOPIADOR, ADMIN | **Sí** | Sí | Sí | **OFFLINE-FIRST** |
| 4 | `GET /api/registros-acopio/{id}` | ACOPIADOR, ADMIN, CALIDAD | Parcial (si ya está en SQLite) | Sí — `registro_acopio_cache` (C-04) | — | READ-CACHE |
| 5 | `GET /api/registros-acopio/proveedor/{proveedorId}` | ACOPIADOR, ADMIN, CALIDAD | Parcial | Sí — `registro_acopio_cache` (C-04) | — | READ-CACHE |
| 6 | `POST /api/registros-acopio/{id}/correcciones` | CALIDAD, ADMIN | **No (gap backend, ver §18.7)** | No | No | ONLINE-ONLY |
| 7 | `GET /api/registros-acopio/{id}/correcciones` | CALIDAD, ADMIN | No | Opcional cache | — | ONLINE + CACHE |
| 8 | `POST /api/analisis-calidad` | CALIDAD, ADMIN | **Sí** (con matiz, ver §18.1) | Sí | Sí | **OFFLINE-FIRST** |
| 9 | `GET /api/analisis-calidad/folio/{folio}` | CALIDAD, ADMIN | No (contraste contra servidor) | No | — | ONLINE-ONLY |
| 10 | `GET /api/analisis-calidad/registro/{registroAcopioId}` | CALIDAD, ADMIN | Parcial | Sí | — | ONLINE + CACHE |
| 11 | `POST /api/recepcion-planta` | RECEPCION, ADMIN | No (gap backend + supuesto) | No | No | ONLINE-ONLY |
| 12 | `GET /api/recepcion-planta` | RECEPCION, ADMIN | No | Opcional cache | — | ONLINE + CACHE |
| 13 | `GET /api/recepcion-planta/{id}` | RECEPCION, ADMIN | No | Opcional cache | — | ONLINE + CACHE |
| 14 | `POST /api/lotes-produccion` | PRODUCCION, ADMIN | **Sí** (con matiz, ver §18.1) | Sí | Sí | **OFFLINE-FIRST** |
| 15 | `GET /api/lotes-produccion` | PRODUCCION, ADMIN | Parcial | Sí | — | ONLINE + CACHE |
| 16 | `GET /api/lotes-produccion/{id}` | PRODUCCION, ADMIN | Parcial | Sí | — | ONLINE + CACHE |
| 17 | `POST /api/ventas` | VENTAS, ADMIN | **Sí** | Sí | Sí | **OFFLINE-FIRST** |
| 18 | `GET /api/ventas` | VENTAS, ADMIN | Parcial | Sí | — | ONLINE + CACHE |
| 19 | `GET /api/ventas/{id}` | VENTAS, ADMIN | Parcial | Sí | — | ONLINE + CACHE |
| 20 | `GET /api/comunicados/zona/{zonaId}` | cualquiera autenticado | Sí (vía `/sync/cambios`) | Sí | — | READ-CACHE |
| 21 | `POST /api/comunicados/{id}/confirmaciones` | ACOPIADOR, ADMIN | **No (gap backend, ver §18.2)** | No | No | ONLINE-ONLY (debería ser OFFLINE-FIRST) |
| 22 | `POST /api/sync/registros-acopio` | ACOPIADOR, ADMIN | — (es el propio mecanismo de sync) | — | **Sí** | motor de sync |
| 23 | `POST /api/sync/analisis-calidad` | CALIDAD, ADMIN | — | — | **Sí** | motor de sync |
| 24 | `POST /api/sync/lotes-produccion` | PRODUCCION, ADMIN | — | — | **Sí** | motor de sync |
| 25 | `POST /api/sync/ventas` | VENTAS, ADMIN | — | — | **Sí** | motor de sync |
| 26 | `GET /api/sync/cambios` | cualquiera autenticado | — (alimenta las tablas READ-CACHE) | Sí | **Sí** | descarga incremental de catálogos |
| 27 | `GET /api/zonas/{zonaId}/ruta` | ADMIN, ACOPIADOR | Sí (una vez descargada) | Sí | — | READ-CACHE |
| 28 | `GET /api/proveedores/operativo` | ADMIN, ACOPIADOR, RECEPCION, CALIDAD | Sí (redundante con `/sync/cambios`) | Sí | — | READ-CACHE |
| 29 | `GET /api/proveedores/qr/{codigoQr}` | ADMIN, ACOPIADOR | **Sí, resuelto localmente** (ver §3.3) | Sí | — | OFFLINE (lookup local) |
| 30 | `GET /api/pagos/proveedor/{proveedorId}` | ADMIN, RECEPCION | No | Opcional cache | — | ONLINE + CACHE |
| 31 | `GET /api/pagos/{id}` | ADMIN, RECEPCION | No | Opcional cache | — | ONLINE + CACHE |
| 32 | `GET /api/innovacion/score/{proveedorId}` | ADMIN, CALIDAD | No | No | — | ONLINE-ONLY |
| 33 | `GET /api/innovacion/prediccion/{proveedorId}` | ADMIN, CALIDAD | Sí (ya viaja en `/sync/cambios`) | Sí | — | READ-CACHE |
| 34 | `GET /api/innovacion/alertas` | ADMIN, CALIDAD | No | Opcional cache | — | ONLINE + CACHE |

**34 endpoints MOBILE** (de los cuales 4 son verdaderamente OFFLINE-FIRST de escritura, 4 son el motor de
sync, y el resto son lecturas online-only, cacheadas u online+cache).

> **Aclaración C-04 — registros de acopio ajenos**: los endpoints 4 y 5 devuelven registros que **este
> dispositivo no capturó** (los creó el dispositivo del ACOPIADOR; los consulta CALIDAD o PRODUCCION). La
> Rev. 1 se contradecía: §3.1 marcaba `SQLite: Sí`, §12 decía "No (consulta puntual)" y §11 no declaraba
> ninguna tabla. Se resuelve **a favor de cachear**: existe `registro_acopio_cache` (§11.2), de solo
> lectura, poblada bajo demanda por estos dos endpoints. Es lo que hace viable la mitigación de §18.1 —
> sin ella, CALIDAD/PRODUCCION no tendrían de dónde sacar el `server_id` del padre estando sin señal.
>
> ⚠️ **Ojo con el endpoint 5**: `GET /api/registros-acopio/proveedor/{id}` devuelve
> `RegistroAcopioResumenResponse`, que **no incluye `uuidCliente`** (solo `id`, `fechaHora`, `litros`,
> `tieneObservacion`). Para obtener el `uuidCliente` de un registro ajeno hay que llamar al endpoint 4
> (`GET /api/registros-acopio/{id}`, que devuelve el DTO completo) uno por uno. Ver `DATA-013` en
> `MOBILE_DATA_MAPPING.md §10` y la nota de diseño en §11.2.

### 3.2 WEB/ADMIN — NO se incorporan al móvil (39 endpoints)

Todo `AdminCatalogosController` (16 endpoints: zonas, unidades, motivos de observación, tipos de queso,
precios por litro, roles, configuración del sistema), todo `AdminUsuarioController` (5 endpoints), la mayor
parte de `ProveedorController` (crear, listar completo con DNI/teléfono, obtener por id completo, editar,
desactivar, historial de zona, reasignar zona — 7 endpoints), `PUT /api/zonas/{zonaId}/ruta` (definir ruta,
1), `POST /api/comunicados` y `GET /api/comunicados/{id}/confirmaciones` (autoría y auditoría, 2),
`POST /api/pagos/generar` y `PATCH /api/pagos/{id}/comprobante` (2), y `GET /api/innovacion/panel-economico`
(1). Razón: son operaciones de administración/configuración/gestión — exactamente el criterio que la
consigna pide excluir del móvil salvo razón explícita, y aquí no la hay.

### 3.3 Nota sobre QR offline

`GET /api/proveedores/qr/{codigoQr}` está catalogado MOBILE-offline porque `ProveedorPublicoResponse`
(el mismo DTO que viaja en `CambiosResponse.proveedores`) ya incluye `codigoQr`. El escaneo de QR en campo
**no necesita ir a la red**: el Repository debe primero resolver contra la tabla local `proveedor_cache`
(poblada por `/api/sync/cambios`) y solo golpear el endpoint si el código no aparece en caché (proveedor
nuevo aún no descargado).

## 4. Autenticación

- **Mecanismo real**: JWT HS256, firmado con `app.security.jwt.secret`, expiración **480 min (8h)**
  (`app.security.jwt.expiration-minutes`). Claims: `sub`=email, `rol`, `usuarioId`.
- **Login**: `POST /api/auth/login` `{email, password}` → `LoginResponse {token, rol, nombre,
  expiraEnSegundos}`. Único endpoint público de toda la API.
- **Envío**: header `Authorization: Bearer <token>` en todos los demás requests.
- **Renovación**: `POST /api/auth/refresh` — requiere el JWT **todavía vigente** en el header; no existe
  refresh token separado ni tabla de refresh tokens en `schema.sql` (confirmado en `AuthService.refrescar`,
  comentado explícitamente como "reemisión simple y sin estado"). Un token ya expirado no se puede
  renovar: exige login nuevo (con conectividad).
- **Revalidación server-side**: el rol se revalida contra la BD en cada request (`UsuarioDetailsService`),
  no solo al hacer login — si ADMIN desactiva un usuario, su próximo request (no solo el login) falla con 401.
- **Errores de auth**: 401 = token ausente/inválido/expirado (`AuthenticationEntryPoint` de
  `SecurityConfig`, antes del `DispatcherServlet`); 403 = rol sin permiso para ese endpoint
  (`@PreAuthorize`). Ambos devuelven el mismo `ErrorResponse {timestamp, status, error, mensaje}` que
  cualquier otro error de la API — un único interceptor HTTP en el cliente basta.

**Diseño para el móvil**:
- Guardar `token`, `rol`, `nombre` y la hora de expiración calculada (`now + expiraEnSegundos`) en
  **almacenamiento seguro nativo**, nunca en SQLite ni en `localStorage`-equivalente: Android
  `EncryptedSharedPreferences`/Keystore, iOS `Keychain`, expuestos a `shared/` vía un `expect/actual`
  `SecureTokenStorage`.
- El JWT **no es necesario para escribir en SQLite** (creación offline no llama red). Solo se necesita un
  token válido en el momento en que el Sync Engine intenta subir el lote. Si el token expiró mientras el
  dispositivo estaba offline, el Sync Engine detecta el 401, pausa la sincronización, y pide re-login
  la próxima vez que haya UI activa — nunca pierde ni corrompe los datos ya guardados localmente.
- Refrescar proactivamente (ej. al abrir la app, si quedan &lt; 30 min de vigencia y hay conectividad) para
  minimizar los re-logins.
- Contraseña: nunca se persiste en el dispositivo, ni siquiera cifrada.

**Política de logout (C-09)** — la Rev. 1 decía solo "limpia `SecureTokenStorage` y las tablas con datos
personales", lo que dejaba sin definir el caso peligroso. Reglas explícitas:

| Qué | Al hacer logout |
|---|---|
| `SecureTokenStorage` (token, rol, nombre, expiración) | **Se borra siempre** |
| Tablas `*_cache` con datos personales de proveedores (`proveedor_cache`, `ruta_zona_cache`, `registro_acopio_cache`) | **Se borran siempre** (RNF-12) |
| Tablas `*_local` con filas `SYNCED` | Se borran (ya están a salvo en el servidor) |
| Tablas `*_local` con filas **`PENDING`, `PENDING_DEPENDENCY`, `SYNCING` o `FAILED`** | **NO se borran.** El logout se **bloquea** y la UI exige resolverlas primero |

> **Por qué se bloquea**: con un token de 8h y rutas rurales largas, el re-login forzado no es el caso raro
> sino el esperado (§20). Un logout que borrara capturas sin sincronizar sería pérdida de datos silenciosa
> del trabajo de una jornada completa. La pantalla `S-05 Pendientes` (`MOBILE_SCREENS.md §4`) es la que
> permite ver, corregir y reintentar esos registros; hasta que la cola quede vacía, el logout muestra
> "Tenés N registros sin enviar" y ofrece **sincronizar ahora** o **cerrar sesión conservando los datos**
> (que mantiene las filas locales y solo borra token y caches personales, para que el mismo usuario las
> recupere al volver a entrar).

**Multiusuario en un mismo dispositivo (C-09)**: una tablet compartida entre turnos puede acumular
pendientes de un usuario y sincronizarlos con el token de otro, atribuyendo el trabajo a quien no fue. Por
eso **las 4 tablas `*_local` llevan `usuario_id TEXT NOT NULL`** (§11.1) con el `usuarioId` del JWT vigente
al momento de la captura, y el Sync Engine **solo sube las filas cuyo `usuario_id` coincide con la sesión
activa**. Las filas de otro usuario quedan intactas y visibles solo cuando ese usuario vuelve a iniciar
sesión. El backend resuelve el autor desde el JWT, no desde el body, así que subirlas con la sesión
equivocada las registraría a nombre del usuario equivocado sin ningún error visible.

**Privacidad**:

- **RNF-12 (privacidad)**: el móvil solo debe pedir/usar `ProveedorPublicoResponse` (sin DNI/teléfono). El
  backend ya separa esto en dos DTOs — el móvil **nunca** debe llamar los endpoints ADMIN-only que
  devuelven `ProveedorAdminResponse`.

## 5. Estrategia offline-first: qué pasa online / offline / al sincronizar

| Funcionalidad | ONLINE | OFFLINE | SYNC (al reconectar) | Clasificación |
|---|---|---|---|---|
| Registrar entrega de leche (ACOPIADOR) | Guarda local + intenta sync inmediato | Guarda local, `PENDING`, visible al instante | `POST /api/sync/registros-acopio` en lote | **OFFLINE-FIRST** |
| Registrar análisis de calidad (CALIDAD) | Igual | Igual, **solo si el `registroAcopioId` referenciado ya tiene id de servidor** (ver §18.1) | `POST /api/sync/analisis-calidad` | **OFFLINE-FIRST (con dependencia)** |
| Registrar lote de producción (PRODUCCION) | Igual | Igual, misma dependencia sobre los `registroAcopioIds` | `POST /api/sync/lotes-produccion` | **OFFLINE-FIRST (con dependencia)** |
| Registrar venta (VENTAS) | Igual | Igual, sin dependencias externas | `POST /api/sync/ventas` | **OFFLINE-FIRST** |
| Anexar corrección a un registro (CALIDAD) | Llamada directa | Bloqueado, se muestra "requiere conexión" | N/A | **ONLINE-ONLY** (gap backend) |
| Confirmar comunicado recibido (ACOPIADOR) | Llamada directa | Debería poder encolarse, pero el backend no es idempotente aún | N/A por ahora | **ONLINE-ONLY** (debería ser OFFLINE-FIRST, ver §18.2) |
| Registrar recepción/conciliación en planta (RECEPCION) | Llamada directa | Bloqueado | N/A | **ONLINE-ONLY** (por diseño de backend + supuesto de conectividad en planta) |
| Ver historial de entregas de un proveedor | Refresca desde red y actualiza SQLite | Lee SQLite (puede estar desactualizado) | Se refresca en cada sync exitoso | **ONLINE + CACHE** |
| Ver catálogos (proveedores, unidades, motivos, tipos de queso, precio vigente, comunicados, predicciones) | `GET /api/sync/cambios` refresca todo de una vez | Lee SQLite | Disparado manual o automáticamente al reconectar | **READ-CACHE** |
| Escanear QR de proveedor | Resuelve contra SQLite primero | Resuelve contra SQLite | Se refresca cuando `/sync/cambios` trae proveedores nuevos | **READ-CACHE (offline real)** |
| Ver ruta del día (ACOPIADOR) | `GET /api/zonas/{zonaId}/ruta` | Lee SQLite (última ruta descargada) | Se refresca al reconectar (no hay push) | **READ-CACHE** |
| Login / refresh de sesión | Requerido | Bloqueado — sin sesión válida no se puede *sincronizar*, pero sí se puede *seguir capturando* si el usuario ya estaba autenticado | N/A | **ONLINE-ONLY**, no bloquea la captura |

## 6. Sync Engine

### 6.1 Máquina de estados por operación pendiente

```text
                    ┌──────────────────────┐
                    │ PENDING_DEPENDENCY   │  (solo AnalisisCalidad y LoteProduccion)
                    │ el padre aún no      │
                    │ tiene server_id      │
                    └──────────┬───────────┘
                               │ el RegistroAcopio padre pasa a SYNCED
                               ↓
PENDING → SYNCING → SYNCED
              ↓
           FAILED (error de red/timeout) → RETRY → SYNCING
              ↓
           FAILED (error de negocio permanente, ej. 400/404/422) → requiere intervención del usuario
```

- **PENDING_DEPENDENCY** (C-03): creado en SQLite, **retenido a propósito** porque referencia un
  `RegistroAcopio` que todavía no tiene `server_id` resuelto (ni propio ya sincronizado, ni ajeno presente
  en `registro_acopio_cache`). No se envía ni cuenta como error: es una espera legítima. Aplica **solo** a
  `AnalisisCalidad` y `LoteProduccion` — ver §18.1. Al confirmarse el padre, el Sync Engine reevalúa estas
  filas y las promueve a `PENDING` en el mismo ciclo. La UI lo muestra distinto de un error: "esperando que
  se sincronice la entrega asociada", no "falló". Si un ítem lleva más de N días en este estado (ej. 3), se
  eleva a la pantalla de pendientes como advertencia, porque puede indicar que el padre nunca llegará (fue
  capturado por un dispositivo que se perdió, se reinstaló, etc.).
- **PENDING**: creado en SQLite, sin dependencias sin resolver, aún no enviado. Visible en la UI de
  inmediato con badge "pendiente de sync".
- **SYNCING**: en vuelo. Un `sync_attempts` y `next_attempt_at` controlan reintentos.
- **SYNCED**: `POST /api/sync/*` devolvió el item dentro de `confirmados[]`, y el registro pasa a ser de
  solo lectura local (coherente con que `RegistroAcopio` es inmutable en el propio dominio del backend).
  ⚠️ **Corregido en Fase 5 (`DATA-014`)**: la Rev. 2 de este documento afirmaba acá que se guardaban el
  `server_id` y el `sincronizadoEn` "que devuelve el backend". El lote **no devuelve ninguno de los dos**:
  `confirmados[]` es `List<String>` de `uuidCliente` y nada más (verificado en `MOBILE_DATA_MAPPING.md
  §5.6`, que manda sobre este documento para forma de campo, ver `CLAUDE.md §2`). En consecuencia una fila
  puede quedar legítimamente `SYNCED` con `server_id` nulo, y `sincronizado_en` es la hora **del
  dispositivo** al recibir la confirmación, no la del servidor. El id de Postgres solo aparece hoy en la
  respuesta del POST individual (`RegistroAcopioResponse.id`), que el Sync Engine no usa.
- **FAILED (transitorio)**: timeout, sin red a mitad de subida, 5xx del servidor. Reintentable con backoff.
- **FAILED (permanente)**: el backend devolvió el item dentro de `errores[]` de `SyncResultResponse`
  (validación Bean Validation o regla de negocio — ej. proveedor inactivo, `fechaHora` fuera de tolerancia).
  **No se reintenta automáticamente**: se marca `FAILED` con el `motivo` tal cual lo manda el backend, y
  se expone en una pantalla de "pendientes con error" para que el usuario corrija y reintente manualmente.

### 6.2 Por qué el batch es tolerante a fallos parciales (y qué implica para el cliente)

`SyncService.procesarLote` (backend) procesa cada ítem del lote en **su propia transacción física** — un
ítem inválido nunca aborta el resto — y siempre responde `200` con `{confirmados[], errores[]}`, nunca
`4xx` a nivel de lote. El cliente **debe**:
1. Enviar todo el lote de `PENDING` de un recurso de una sola vez (no ítem por ítem) — es el diseño
   pensado por el backend.
2. Leer la respuesta y **reconciliar por `uuidCliente`**: todo lo que aparece en `confirmados[]` pasa a
   `SYNCED`; todo lo que aparece en `errores[]` pasa a `FAILED` con el motivo devuelto; cualquier
   `uuidCliente` propio que no aparece en ninguna de las dos listas (no debería pasar, pero el cliente debe
   ser defensivo) se queda en `SYNCING` y se reintenta en el próximo ciclo, nunca se asume éxito por omisión.
3. Nunca interpretar un `200` de lote como "todo salió bien" sin inspeccionar `errores[]`.

### 6.3 Reintentos y backoff

- Reintentos automáticos solo para fallos **transitorios** (timeout, sin conectividad detectada a mitad de
  camino, 5xx). Backoff exponencial con techo, ej. 15s → 30s → 1m → 5m → 15m (techo), con un límite de
  reintentos automáticos razonable (ej. 8) antes de pasar a "requiere revisión manual" para no drenar
  batería reintentando indefinidamente un servidor caído.
- Al detectar conectividad (ver §6.5), se dispara sync inmediatamente sin esperar el próximo backoff
  programado.
- Cada intento incrementa `sync_attempts` y actualiza `sync_error` con el último mensaje, para
  trazabilidad y para que la pantalla de diagnóstico del usuario muestre algo útil.

### 6.4 Duplicados, timeouts y respuestas perdidas (idempotencia end-to-end)

Escenario que la consigna pide cubrir explícitamente:

```text
Móvil → POST /api/sync/registros-acopio → servidor procesa OK → respuesta se pierde (timeout de red)
Móvil cree que falló → marca FAILED transitorio → reintenta el mismo lote
```

Esto **no genera duplicados** porque `RegistroAcopioService.registrarOIgnorarSiDuplicado` (y los 3
services análogos) buscan primero por `uuidCliente`: si ya existe, devuelven el registro existente en vez
de crear uno nuevo. El cliente puede reenviar con total seguridad cualquier `uuidCliente` que no esté
confirmado como `SYNCED` — es el comportamiento por el que este backend fue diseñado
(`API_DOCUMENTATION.md §7`: *"Reenviar un POST con el mismo uuidCliente no devuelve 409: devuelve 201 con
el registro ya existente"*). Esto cubre los 4 recursos OFFLINE-FIRST reales (RegistroAcopio, AnálisisCalidad,
LoteProduccion, Venta). **No cubre** `RecepcionPlanta` (409 en duplicado, no idempotente) ni las
confirmaciones de comunicado ni las correcciones — ver §18.

### 6.5 Detección de conectividad

Capa compartida (`shared/synchronization/ConnectivityObserver` como `expect` con `actual` por plataforma:
Android `ConnectivityManager.NetworkCallback`, iOS `NWPathMonitor`), expuesta como un `Flow<Boolean>`
(o `StateFlow`) que el Sync Engine observa. Al pasar de `false → true`, dispara un ciclo de sync completo
(subir pendientes de los 4 recursos + refrescar `/api/sync/cambios`). No basta con "hay wifi": debe
tratarse como una señal de "intentar", no de "hay Internet garantizado" — el propio intento de red
(timeout/error) es la fuente de verdad final.

### 6.6 Recuperación tras reiniciar la app / perder conexión a mitad de sync

- Todo el estado de sincronización vive en SQLite (no en memoria): si la app se cierra a mitad de
  `SYNCING`, al reabrir el Sync Engine debe tratar cualquier ítem que quedó en `SYNCING` por más de un
  timeout razonable como "estado desconocido, no reintentar ciegamente sin verificar" — dado que el
  backend es idempotente por `uuidCliente` para los 4 recursos, la estrategia segura es simplemente
  **reintentar** (la idempotencia server-side ya cubre el caso "en realidad sí se había guardado").
- Un `WorkManager` (Android) o `BGTaskScheduler`/`BGAppRefreshTask` (iOS) debe re-encolar el trabajo de
  sync pendiente al arrancar el proceso, no solo confiar en que la UI dispare el primer intento.

### 6.7 Background sync: diferencias Android/iOS

La consigna pide no asumir que un proceso de background corre indefinidamente en ambas plataformas — y en
efecto no corre:

- **Android**: `WorkManager` con un `CoroutineWorker` compartido (la lógica del worker vive en `shared/`,
  solo el registro del `Worker` es Android-specific) + `Constraints.NETWORK_CONNECTED`. Puede ejecutarse en
  background de forma razonablemente confiable con `setExpedited`/foreground service si el lote es grande.
- **iOS**: `BGAppRefreshTask` (y opcionalmente `BGProcessingTask` para lotes grandes) — el sistema decide
  cuándo lo ejecuta y con qué frecuencia (no hay garantía de "cada X minutos"), y tiene una ventana de
  ejecución corta. La lógica de negocio (qué sincronizar, cómo reconciliar) es 100% compartida; solo el
  *scheduling* es `actual` por plataforma.
- **Regla de diseño**: el Sync Engine (parte compartida) debe ser **stateless entre invocaciones e
  interrumpible en cualquier punto** — no puede asumir que corre hasta terminar. Cada ítem se sincroniza
  como una unidad atómica (una llamada HTTP + una actualización de SQLite); si el proceso muere entre dos
  ítems del lote, el próximo arranque simplemente continúa desde `PENDING`/`SYNCING` sin necesitar lógica
  de recuperación especial (ver §6.6).
- En ambas plataformas, además del trigger en background, la sync también se dispara: al abrir la app, al
  detectar reconexión mientras la app está en foreground, y manualmente (botón "sincronizar ahora").

## 7. Idempotencia — qué soporta el backend hoy

| Recurso | Campo idempotente | Comportamiento en duplicado |
|---|---|---|
| `RegistroAcopio` | `uuidCliente` (UNIQUE en `schema.sql`) | Devuelve el existente, 201, nunca duplica |
| `AnalisisCalidad` | `uuidCliente` (UNIQUE) | Igual |
| `LoteProduccion` | `uuidCliente` (UNIQUE) | Igual |
| `Venta` | `uuidCliente` (UNIQUE) | Igual |
| `RecepcionPlanta` | ninguno — clave natural `(fecha, unidad_id, turno)` UNIQUE | **409 Conflict** en duplicado (no idempotente: el cliente debe manejar el 409, no reintentar a ciegas) |
| `CorreccionRegistro` | ninguno | Cada `POST` crea una fila nueva — un reintento crea una corrección duplicada |
| `ComunicadoConfirmacion` | ninguno | Cada `POST` crea una fila nueva — un reintento duplica la confirmación |

Para los 4 recursos con `uuidCliente`, el flujo de creación offline es exactamente el que pide la consigna:

```text
1. UI genera uuidCliente = UUID v4 en el dispositivo
2. Repository guarda en SQLite, estado PENDING
3. UI muestra el registro de inmediato (optimista)
4. Sync Engine espera conectividad
5. POST /api/sync/{recurso} con el lote de PENDING
6. Backend responde {confirmados, errores} — busca primero por uuidCliente
7. Reconciliación: confirmados → SYNCED; errores → FAILED (+ motivo)
   (sin server_id: el lote no lo devuelve — ver DATA-014 y la nota de §6.1)
```

El móvil **nunca depende de que el servidor genere primero el id** para estos 4 recursos — exactamente lo
que pide la consigna — con la excepción documentada en §18.1 (referencias cruzadas entre estos recursos).

## 8. Conflictos

Superficie de conflicto real entre SQLite y Postgres, dado el dominio:

- **RegistroAcopio es inmutable** (ni el backend ni por tanto el móvil ofrecen edición) — el único "cambio"
  es una `CorreccionRegistro` anexada, nunca una sobreescritura. **No hay conflicto de última-escritura**
  porque no hay escritura concurrente sobre el mismo campo: se descarta la necesidad de last-write-wins
  aquí, no porque no se pensó, sino porque el dominio ya lo evita por diseño.
- **Creación con `uuidCliente`**: no hay conflicto posible entre dos dispositivos (cada uno genera su
  propio UUID v4; colisión de UUID v4 es estadísticamente irrelevante).
- **`RecepcionPlanta` por clave natural `(fecha, unidad_id, turno)`**: **sí** puede haber conflicto real —
  dos usuarios (o el mismo, reintentando) intentando registrar la misma combinación. Estrategia: **rechazo**
  (ya es lo que hace el backend con 409) — el cliente debe, ante un 409, refrescar `GET
  /api/recepcion-planta` para esa unidad/fecha/turno, mostrar el registro existente, y dejar que el usuario
  decida (no hay endpoint de edición, así que no hay "resolución" real posible desde el móvil salvo avisar).
- **Reasignación de zona de un proveedor / catálogos** (ADMIN, vía web): el móvil solo lee estos datos
  (`/sync/cambios`), nunca los edita — no hay conflicto de escritura posible desde el móvil sobre estos
  recursos, solo el caso trivial de "mi caché quedó desactualizada", resuelto por el próximo `GET
  /api/sync/cambios` que sobreescribe la copia local (server siempre gana en datos de solo lectura).
- **No se implementa** ninguna estrategia de fusión de campo a campo (merge) porque el dominio no la
  necesita: cada entidad offline-first es o bien de solo-creación (nunca editada) o de solo-lectura desde
  el móvil.

## 9. Cache y lecturas

```text
API → Repository → SQLite → UI
```

- Toda pantalla lee de SQLite vía `Flow`/reactive query (SQLDelight expone `Flow<List<T>>` nativamente) —
  la UI se recompone sola cuando el Repository actualiza la fila tras un sync exitoso, sin necesidad de
  "refrescar" manualmente después de un `POST`.
- El **Remote Data Source** (Ktor) y el **Local Data Source** (SQLDelight) son módulos separados en
  `shared/data/`; el `Repository` es el único que conoce a ambos. Ningún ViewModel importa Ktor.
- `GET /api/sync/cambios` es la única fuente de refresco masivo de catálogos — se llama al abrir sesión, al
  reconectar, y opcionalmente en un refresh periódico en background (ver §6.7). El parámetro `desde` existe
  en el backend mas **no tiene efecto en v1** (siempre devuelve el estado completo) — el cliente no debe
  asumir filtrado incremental real todavía; simplemente reemplaza las tablas READ-CACHE completas en cada
  descarga (volumen bajo, confirmado por el propio comentario del backend).

## 10. API Client

Capa `shared/network/` (Ktor Client + `ContentNegotiation`/`kotlinx.serialization`), responsable de:

- Serialización/deserialización de los DTOs (mapeados 1:1 a los records Kotlin — ver §12 para el detalle
  de tipos).
- Adjuntar `Authorization: Bearer <token>` leyendo del `SecureTokenStorage` en cada request (interceptor).
- Timeout explícito (razonable para redes rurales — ej. connect 10s / request 30s, configurable, más
  generoso que un backend típico de oficina).
- Mapeo uniforme de errores: dado que **toda** respuesta de error de esta API (400/401/403/404/409/422/500)
  tiene la misma forma `ErrorResponse {timestamp, status, error, mensaje}`, un único interceptor decodifica
  cualquier error HTTP a un `ApiError` de dominio compartido — la UI nunca parsea JSON de error a mano.
- 401 → dispara el flujo de refresh/re-login (ver §4); 403 → error de dominio "sin permiso", no se
  reintenta; 404/422/400 de negocio → se propagan como error permanente (no reintentable) al Sync Engine o
  al UseCase que lo llamó; 5xx/timeout/sin red → error transitorio, reintentable.
- La UI y los UseCases **nunca** ven `HttpClient` ni excepciones de Ktor directamente — todo se traduce a
  un `Result`/sealed class de dominio (`ApiResult.Success`, `ApiResult.Error(tipo, mensaje)`) antes de
  cruzar al Repository.

## 11. Modelo SQLite

Diseño deliberadamente distinto de una copia 1:1 de `schema.sql`: solo lo que el móvil necesita para operar
offline y para los catálogos de referencia que resuelve localmente. Motor: SQLDelight (multiplataforma real
Android/iOS sobre SQLite nativo, ver justificación en §13).

### 11.1 Tablas de escritura offline (con ciclo de vida de sync)

> **Tipos de columna — reglas duras (C-01)**. Se declaran una vez y aplican a las 4 tablas:
>
> - Todo campo `BigDecimal` del contrato se persiste como **`TEXT`** (la representación decimal exacta,
>   ej. `"1234.56"`), vía un `ColumnAdapter` de SQLDelight sobre `com.ionspin.kotlin.bignum`. **Nunca
>   `REAL`**: `REAL` es un float IEEE-754 de 8 bytes, exactamente el error de precisión que `DATA-002`
>   (CRITICAL) obliga a evitar en litros, precios y totales. La Rev. 1 de este documento declaraba estas
>   columnas como `REAL`, contradiciendo su propio documento hermano — corregido aquí.
> - Todo campo de fecha/hora se persiste como **`TEXT`** en ISO-8601, con la semántica de `DATA-001`
>   (hora de pared sin zona para `LocalDateTime`; UTC explícito solo para `Instant`).
> - Todo `Boolean` se persiste como **`INTEGER`** (`0`/`1`).
> - Todo `UUID` se persiste como **`TEXT`**.

```text
registro_acopio_local
  uuid_cliente        TEXT PRIMARY KEY        -- generado en el dispositivo, clave de idempotencia
  server_id           TEXT NULL               -- UUID real de Postgres, se llena al confirmar SYNCED
  usuario_id          TEXT NOT NULL           -- usuarioId del JWT vigente al capturar (C-09, multiusuario)
  proveedor_id        TEXT NOT NULL
  unidad_id           TEXT NOT NULL
  fecha_hora          TEXT NOT NULL           -- ISO-8601 SIN zona (hora de pared del dispositivo, DATA-001)
  litros              TEXT NOT NULL           -- decimal exacto en texto (C-01), NUNCA REAL
  gps_lat             TEXT NULL               -- decimal exacto en texto
  gps_lng             TEXT NULL               -- decimal exacto en texto
  motivo_observacion_id TEXT NULL
  litros_por_voz      INTEGER NOT NULL DEFAULT 0
  sync_status         TEXT NOT NULL           -- PENDING | SYNCING | SYNCED | FAILED
  sync_attempts       INTEGER NOT NULL DEFAULT 0
  sync_error          TEXT NULL
  next_attempt_at     TEXT NULL
  creado_en           TEXT NOT NULL DEFAULT (datetime('now'))
  sincronizado_en     TEXT NULL               -- viene del servidor al confirmar
  -- foto_local_path: NO existe en v1 (C-07). Se agrega cuando exista el endpoint de subida (§18.4).

analisis_calidad_local
  uuid_cliente         TEXT PRIMARY KEY
  server_id            TEXT NULL
  usuario_id           TEXT NOT NULL
  -- Referencia al RegistroAcopio padre: EXACTAMENTE UNA de las dos columnas siguientes (C-02).
  registro_acopio_uuid_cliente TEXT NULL      -- padre capturado en ESTE dispositivo; su server_id se
                                              -- resuelve antes de enviar desde registro_acopio_local
  registro_acopio_server_id    TEXT NULL      -- padre AJENO (otro dispositivo): solo se conoce su id de
                                              -- servidor, vía registro_acopio_cache. Caso normal para
                                              -- CALIDAD, que no captura acopios.
  CHECK ((registro_acopio_uuid_cliente IS NULL) <> (registro_acopio_server_id IS NULL))
  folio_muestra        TEXT NOT NULL
  agua, proteina, lactosa, densidad, temperatura, ph   TEXT NULL   -- decimales exactos (C-01)
  agua_anadida         INTEGER NOT NULL DEFAULT 0
  sync_status, sync_attempts, sync_error, next_attempt_at, creado_en   -- igual que arriba

lote_produccion_local
  uuid_cliente          TEXT PRIMARY KEY
  server_id             TEXT NULL
  usuario_id            TEXT NOT NULL
  fecha                 TEXT NOT NULL          -- ISO-8601 (yyyy-MM-dd)
  tipo_queso_id         TEXT NOT NULL
  litros_usados         TEXT NOT NULL          -- decimal exacto (C-01)
  unidades_obtenidas    INTEGER NOT NULL
  sync_status, sync_attempts, sync_error, next_attempt_at, creado_en

lote_produccion_registro_local           -- N:M local, espejo de lote_registro_acopio
  lote_uuid_cliente              TEXT NOT NULL
  -- Mismo patrón de doble referencia que analisis_calidad_local (C-02): un lote consume registros
  -- propios y ajenos en la misma operación, así que ambas columnas conviven en la misma tabla.
  registro_acopio_uuid_cliente   TEXT NULL
  registro_acopio_server_id      TEXT NULL
  CHECK ((registro_acopio_uuid_cliente IS NULL) <> (registro_acopio_server_id IS NULL))
  -- Corregido en Fase 4 (esta tabla no tiene PRIMARY KEY propia, usa el rowid implícito de SQLite): un
  -- PRIMARY KEY declarativo no admite una expresión como columna -- COALESCE(...) ahí no es SQL válido de
  -- SQLite. La Rev. 2 de este documento lo declaraba así; la unicidad pretendida (un mismo registro de
  -- acopio, propio o ajeno, no puede entrar dos veces al mismo lote) se logra en cambio con un índice de
  -- expresión (soportado desde SQLite 3.9), fuera de la CREATE TABLE:
  CREATE UNIQUE INDEX ux_lote_produccion_registro_local ON lote_produccion_registro_local
    (lote_uuid_cliente, COALESCE(registro_acopio_uuid_cliente, registro_acopio_server_id))

venta_local
  uuid_cliente          TEXT PRIMARY KEY
  server_id             TEXT NULL
  usuario_id            TEXT NOT NULL
  fecha                 TEXT NOT NULL
  tipo_cliente          TEXT NOT NULL           -- MAYORISTA | PROVEEDOR | PUBLICO (DATA-010)
  tipo_queso_id         TEXT NOT NULL
  cantidad              INTEGER NOT NULL
  precio_unitario       TEXT NOT NULL           -- decimal exacto (C-01)
  sync_status, sync_attempts, sync_error, next_attempt_at, creado_en
```

`sync_status` es el mismo enum en las 4 tablas → el Sync Engine puede iterarlas de forma genérica
(una función `sincronizar<T>(tabla, endpoint)` parametrizada, no 4 implementaciones copiadas):

```text
PENDING | PENDING_DEPENDENCY | SYNCING | SYNCED | FAILED
```

`PENDING_DEPENDENCY` (C-03) solo lo usan `analisis_calidad_local` y `lote_produccion_local`; en
`registro_acopio_local` y `venta_local` el valor existe en el enum por uniformidad pero nunca se asigna,
porque esos dos recursos no dependen de ningún id ajeno.

**Resolución de la referencia al padre antes de enviar** (§18.1, C-02). Al armar el request de red, el Sync
Engine resuelve `registroAcopioId` así:

```text
si registro_acopio_server_id IS NOT NULL        → usarlo directamente (padre ajeno, ya resuelto)
si registro_acopio_uuid_cliente IS NOT NULL     → buscar registro_acopio_local.server_id
      · si tiene server_id                       → usarlo, el ítem entra al lote
      · si NO tiene server_id todavía             → sync_status = PENDING_DEPENDENCY, no entra al lote
```

### 11.2 Tablas de solo lectura (catálogos descargados por `/api/sync/cambios`)

```text
proveedor_cache
  id                TEXT PRIMARY KEY     -- server id real (nunca se crea localmente)
  nombre            TEXT NOT NULL
  zona_actual_id    TEXT NULL
  zona_actual_nombre TEXT NULL
  codigo_qr         TEXT NULL            -- permite resolver el escaneo de QR 100% offline
  actualizado_en    TEXT NOT NULL

unidad_cache
  id TEXT PRIMARY KEY, placa TEXT, capacidad_ton TEXT NULL, zona_id TEXT NULL,
  responsable_id TEXT, responsable_nombre TEXT, actualizado_en TEXT NOT NULL

motivo_observacion_cache
  id TEXT PRIMARY KEY, descripcion TEXT NOT NULL, actualizado_en TEXT NOT NULL

tipo_queso_cache
  id TEXT PRIMARY KEY, nombre TEXT, rendimiento_esperado_pct TEXT, ciclo_capital TEXT,
  activo INTEGER, actualizado_en TEXT NOT NULL

comunicado_cache
  id TEXT PRIMARY KEY, mensaje TEXT NOT NULL, fecha TEXT NOT NULL, actualizado_en TEXT NOT NULL
comunicado_zona_cache
  comunicado_id TEXT NOT NULL, zona_nombre TEXT NOT NULL, PRIMARY KEY (comunicado_id, zona_nombre)

prediccion_proveedor_cache
  proveedor_id TEXT PRIMARY KEY, fecha_prevista TEXT, litros_estimados_min TEXT,
  litros_estimados_max TEXT, actualizado_en TEXT NOT NULL

precio_litro_vigente_cache
  id INTEGER PRIMARY KEY CHECK (id = 1)   -- fila única, valor escalar
  precio TEXT NULL, actualizado_en TEXT NOT NULL   -- NULLABLE: /sync/cambios devuelve null si no hay
                                                   -- precio vigente configurado (DATA: .orElse(null))
ruta_zona_cache                            -- descargado bajo demanda por GET /zonas/{zonaId}/ruta,
  zona_id TEXT NOT NULL                    -- NO viaja en /sync/cambios (ver gap opcional §18.5)
  proveedor_id TEXT NOT NULL, proveedor_nombre TEXT, orden INTEGER NOT NULL,
  hora_estimada TEXT NULL, actualizado_en TEXT NOT NULL
  PRIMARY KEY (zona_id, proveedor_id)

registro_acopio_cache                      -- NUEVA en Rev. 2 (C-02/C-04). Registros de acopio AJENOS,
  id                  TEXT PRIMARY KEY     -- capturados por otro dispositivo. Solo lectura, nunca se
                                           -- crea ni edita localmente.
  uuid_cliente        TEXT NULL            -- NULLABLE a propósito: GET /registros-acopio/proveedor/{id}
                                           -- devuelve RegistroAcopioResumenResponse, que NO lo trae
                                           -- (DATA-013). Se llena solo si se consultó el detalle.
  proveedor_id        TEXT NULL            -- ausente en el DTO resumen
  proveedor_nombre    TEXT NULL
  fecha_hora          TEXT NOT NULL
  litros              TEXT NOT NULL        -- decimal exacto (C-01)
  tiene_observacion   INTEGER NULL         -- solo viene en el DTO resumen
  origen              TEXT NOT NULL        -- RESUMEN | DETALLE — qué DTO pobló esta fila
  actualizado_en      TEXT NOT NULL
```

Todas las tablas `_cache` **excepto `registro_acopio_cache`** se **reemplazan por completo** en cada
`GET /api/sync/cambios` exitoso (borrar + reinsertar dentro de una transacción SQLite), consistente con que
el propio backend no ofrece filtrado incremental real en v1 — no tiene sentido que el cliente implemente un
merge fino sobre datos que el servidor manda completos.

> **`registro_acopio_cache` es la excepción** y se comporta distinto: **no** viaja en `/sync/cambios`, se
> puebla bajo demanda (al abrir el historial de un proveedor, o al consultar un registro puntual) y se
> actualiza fila por fila con `INSERT OR REPLACE`, nunca con un borrado masivo — borrarla en un ciclo de
> sync dejaría a `analisis_calidad_local` sin poder resolver el `server_id` de sus padres ajenos. La
> columna `origen` existe porque los dos endpoints que la pueblan devuelven DTOs distintos: el resumen
> (`id`, `fechaHora`, `litros`, `tieneObservacion`) y el detalle (todo, incluido `uuidCliente`). Una fila
> `RESUMEN` **sirve igual** para el caso de uso principal — referenciar el padre por su `server_id` —
> porque `id` es justo lo que el request necesita; el detalle solo hace falta si la UI quiere mostrar
> proveedor y observación. Ver `DATA-013`.

### 11.3 Qué se deja fuera a propósito

- No hay tabla local para `Pago`, `RecepcionPlanta` (más allá de un cache de solo-lectura opcional para la
  pantalla de historial, no crítico), `CorreccionRegistro`, `ComunicadoConfirmacion`, ni ningún dato
  ADMIN-only (`Usuario`, `Rol`, `ConfiguracionSistema`, catálogos completos con datos personales). El móvil
  no los necesita para operar offline y replicarlos sería una copia ciega de Postgres, exactamente lo que
  la consigna pide evitar.
- **No hay `foto_local_path` en v1** (C-07). La evidencia fotográfica se difiere a v2: sin endpoint de
  subida (`DATA-008`) el móvil solo podría acumular archivos que nunca salen del dispositivo, sin política
  de limpieza y prometiéndole al usuario una funcionalidad que el backend no respalda. Cuando §18.4 se
  resuelva, se agregan la columna, la pantalla y la política de almacenamiento juntas. `RegistroAcopioResponse`
  tampoco expone `fotoUrl` (§18.6), así que hoy no hay ni ida ni vuelta que cachear.

### 11.4 Retención y crecimiento de la base local (C-09)

La Rev. 1 no definía cuándo se borra nada, lo que en un dispositivo de gama baja con meses de operación
termina en una base que solo crece. Reglas:

| Tabla | Política |
|---|---|
| `*_local` con `sync_status = SYNCED` | Se conservan **90 días** desde `sincronizado_en`, luego se borran. El servidor ya es la fuente de verdad; lo local es solo para que el usuario vea su propio historial reciente sin señal |
| `*_local` con `PENDING`/`PENDING_DEPENDENCY`/`SYNCING`/`FAILED` | **Nunca se borran automáticamente.** Son trabajo no confirmado; solo salen por sincronización exitosa o por descarte explícito del usuario en `S-05 Pendientes` |
| `registro_acopio_cache` | Se conservan **30 días** desde `actualizado_en`, **salvo** las filas referenciadas por algún `analisis_calidad_local`/`lote_produccion_registro_local` que no esté `SYNCED` (esas se conservan hasta que su hijo sincronice) |
| `*_cache` de catálogos | Se reemplazan completas en cada `/sync/cambios`; no crecen |

La limpieza corre en el mismo worker de background sync (§6.7), una vez por ciclo, dentro de su propia
transacción. Es una operación local pura: nunca borra nada que no esté confirmado en el servidor.

## 12. Matriz de entidades

| Entidad | SQLite | Offline (escritura) | Sync | Endpoint(s) MOBILE | Observaciones |
|---|---|---|---|---|---|
| RegistroAcopio (propio) | Sí | Sí | Sí | `POST /registros-acopio`, `POST /sync/registros-acopio` | OFFLINE-FIRST puro |
| AnalisisCalidad | Sí | Sí* | Sí | `POST /analisis-calidad`, `POST /sync/analisis-calidad` | *depende de que el RegistroAcopio referenciado tenga `server_id` (§18.1) |
| LoteProduccion | Sí | Sí* | Sí | `POST /lotes-produccion`, `POST /sync/lotes-produccion` | *misma dependencia sobre `registroAcopioIds` |
| Venta | Sí | Sí | Sí | `POST /ventas`, `POST /sync/ventas` | Sin dependencias externas |
| Proveedor (público) | Sí (cache) | No | Descarga (`/sync/cambios`) | `GET /proveedores/operativo`, `GET /proveedores/qr/{codigoQr}` | Sin DNI/teléfono (RNF-12) |
| Unidad | Sí (cache) | No | Descarga | vía `/sync/cambios` | Dropdown para RegistroAcopio/RecepcionPlanta |
| MotivoObservacion | Sí (cache) | No | Descarga | vía `/sync/cambios` | Dropdown para RegistroAcopio |
| TipoQueso | Sí (cache) | No | Descarga | vía `/sync/cambios` | Dropdown para LoteProduccion/Venta |
| Comunicado (+zonas) | Sí (cache) | No | Descarga | `GET /comunicados/zona/{zonaId}` o vía `/sync/cambios` | Confirmación NO offline (§18.2) |
| PrediccionProveedor | Sí (cache) | No | Descarga | vía `/sync/cambios` | Solo lectura |
| Precio por litro (vigente) | Sí (cache, 1 fila) | No | Descarga | vía `/sync/cambios` | Serie histórica completa es solo ADMIN |
| RutaProveedorOrden | Sí (cache) | No | Descarga bajo demanda | `GET /zonas/{zonaId}/ruta` | No incluida en `/sync/cambios` (gap opcional §18.5) |
| RegistroAcopio (ajenos, referencia) | **Sí — `registro_acopio_cache`** (C-04) | No | Descarga bajo demanda | `GET /registros-acopio/{id}`, `GET /registros-acopio/proveedor/{id}` | Resolución de `server_id` para AnalisisCalidad/LoteProduccion. Se cachea para que la referencia siga resolviéndose sin señal; el endpoint de listado no trae `uuidCliente` (`DATA-013`) |
| RecepcionPlanta | No (o cache opcional de solo lectura) | No | No | `GET/POST /recepcion-planta` | ONLINE-ONLY por diseño de backend |
| Pago | No | No | No | `GET /pagos/*` | Solo lectura ocasional (RECEPCION) |
| Usuario, Rol, ConfiguracionSistema, catálogos completos | No | No | No | — | WEB/ADMIN, fuera de alcance móvil |

## 13. Kotlin Multiplatform: qué se comparte y qué es específico

```text
shared/
├── core/              -- Result/ApiResult, errores de dominio, utilidades de fecha/UUID
├── domain/             -- modelos de dominio (no DTOs de red), UseCases (CrearRegistroAcopio,
│                          SincronizarPendientes, DescargarCambios, ResolverProveedorPorQr, ...)
├── data/
│   ├── remote/          -- DTOs (kotlinx.serialization, espejo de los records Java) + ApiClient (Ktor)
│   ├── local/            -- SQLDelight (.sq), DAOs generados, mappers Entity↔Dominio
│   └── repository/       -- implementaciones de los Repository de domain/, únicas que ven remote+local
├── network/             -- HttpClient config, interceptores (auth, errores), timeouts
├── synchronization/     -- SyncEngine, máquina de estados, ConnectivityObserver (expect),
│                          BackgroundSyncScheduler (expect)
├── security/            -- SecureTokenStorage (expect)
├── presentation/        -- ViewModels + UiState/Event/Effect de cada pantalla (C-05).
│                           Uno por pantalla del inventario de MOBILE_SCREENS.md §4-§9.
│                           NO vive en androidApp: se escribe una sola vez para las dos plataformas.
└── ui/                  -- Compose Multiplatform: pantallas, componentes, tema, navegación (C-06).
                            Es UI compartida real, no un "androidMain" disfrazado.

androidApp/              -- contenedor delgado
├── MainActivity (monta el grafo Compose de shared/ui/) + wiring de DI
├── actual ConnectivityObserver (ConnectivityManager)
├── actual BackgroundSyncScheduler (WorkManager)
├── actual SecureTokenStorage (EncryptedSharedPreferences / Keystore)
└── Permisos: cámara (QR), ubicación (GPS)

iosApp/                  -- contenedor delgado
├── SwiftUI App que monta el UIViewController de Compose Multiplatform
├── actual ConnectivityObserver (NWPathMonitor)
├── actual BackgroundSyncScheduler (BGTaskScheduler)
├── actual SecureTokenStorage (Keychain)
└── Permisos: cámara, ubicación (Info.plist)
```

**Todo lo de negocio, repositorios, modelos, cliente API, SQLite, sincronización, presentación y UI vive en
`shared/`.** Lo específico de plataforma se limita a: ciclo de vida del proceso en background,
almacenamiento seguro nativo, detección de conectividad nativa y los permisos del SO — exactamente las
áreas donde Android e iOS no ofrecen una API común. Con la decisión C-06 (Compose Multiplatform), **la UI
dejó de ser una de ellas**.

## 14. Elección de herramientas (KMP, 2025-2026)

| Necesidad | Elección | Por qué / multiplataforma | Alternativa evaluada | Por qué se descartó |
|---|---|---|---|---|
| Base de datos local | **SQLDelight** | Genera código type-safe desde `.sq` (SQL real), un solo driver Android (`AndroidSqliteDriver`) y uno iOS (`NativeSqliteDriver`) sobre SQLite nativo en ambos, expone `Flow` reactivo nativamente — encaja directamente con el patrón Repository→UI reactivo de §9 | Room (KMP support en beta/experimental a esta fecha) | SQLDelight tiene soporte KMP maduro y estable desde antes; Room KMP es más reciente y menos probado en producción iOS |
| Cliente HTTP | **Ktor Client** | Del mismo ecosistema JetBrains que Kotlin/KMP, engines nativos por plataforma (OkHttp en Android, Darwin en iOS), soporta interceptores/plugins para el interceptor de auth+errores de §10 | java.net.http / URLSession directos | No son multiplataforma; reimplementar dos veces el mismo cliente HTTP viola el objetivo de compartir lógica |
| Serialización | **kotlinx.serialization** | Multiplataforma nativa de Kotlin, se integra directo con Ktor `ContentNegotiation`, sin reflection (importante en iOS/Kotlin Native) | Gson/Jackson | JVM-only, no compilan a Kotlin Native |
| Concurrencia | **Kotlin Coroutines + Flow** | Estándar de facto en KMP, ya asumido por SQLDelight y Ktor | RxJava/RxSwift por plataforma | Duplicaría lógica reactiva por plataforma sin necesidad |
| Inyección de dependencias | **Koin** | DSL Kotlin puro, sin generación de código (a diferencia de Dagger/Hilt que dependen fuertemente de anotaciones JVM), soporte KMP directo | Dagger/Hilt | Atados al procesador de anotaciones de Android; no compilan para iOS |
| Almacenamiento seguro de credenciales | **`expect`/`actual` propio** sobre Android Keystore/`EncryptedSharedPreferences` y iOS `Keychain` | No hay una librería KMP madura y ampliamente adoptada que abstraiga Keystore+Keychain con las garantías de seguridad que requiere un JWT; una capa fina propia (pocas funciones: `guardar/leer/borrar token`) es más auditable que una dependencia de terceros para este dato sensible | `multiplatform-settings` (para preferencias no sensibles) | `multiplatform-settings` no cifra por sí solo — sirve para preferencias sí, pero no reemplaza Keystore/Keychain para el JWT |
| Conectividad | **`expect`/`actual` propio** (`ConnectivityManager` / `NWPathMonitor`) | API nativa de plataforma, sin capa de abstracción de terceros que agregue riesgo para algo tan crítico como "¿debo intentar sincronizar ahora?" | Librerías de terceros de conectividad KMP | Superficie pequeña (un `Flow<Boolean>`), no justifica una dependencia externa |
| **UI y navegación** | **Compose Multiplatform** (Android **e** iOS) + Compose Multiplatform Navigation (C-06) | Una sola implementación de cada pantalla para las dos plataformas, coherente con la tesis del plano (maximizar `shared/`). Permite que los `ViewModel` y todo el `UiState` vivan en `shared/presentation/` (C-05), en vez de reescribir la lógica de presentación en Swift. Mapa de navegación en `MOBILE_SCREENS.md §2` | Compose en Android + SwiftUI nativo en iOS | Duplica cada pantalla **y** su lógica de presentación; con un equipo chico es el mayor multiplicador de esfuerzo del proyecto. Se acepta a cambio un look&feel que no es 100% nativo en iOS — trade-off explícito y asumido |
| Background sync | **WorkManager** (Android) / **BGTaskScheduler** (iOS) | APIs nativas recomendadas por cada plataforma para trabajo diferido/periódico respetando restricciones del SO (batería, Doze, App Refresh) | Un scheduler KMP genérico | No existe una abstracción confiable de background execution entre Android/iOS — las garantías del SO son demasiado distintas (ver §6.7) para fingir una API común |
| Testing | **kotlin.test** + `kotlinx-coroutines-test` (shared), Turbine (test de `Flow`), un driver SQLDelight en memoria para tests de Local Data Source, `MockEngine` de Ktor para tests de Remote Data Source | Todos multiplataforma, permiten testear `shared/` una sola vez y correrlo en JVM (rápido) sin necesitar un emulador/simulador | Robolectric / XCTest por separado | Duplicaría la suite de tests de dominio/repository/sync en dos plataformas sin necesidad — se reservan XCTest/Instrumented tests solo para lo realmente `actual`-specific (§15) |

## 15. Estructura del proyecto

```text
acopio-mobile/                       (nuevo repo o módulo, fuera de acopio-backend-fase2)
├── shared/
│   ├── src/
│   │   ├── commonMain/kotlin/
│   │   │   ├── core/
│   │   │   ├── domain/
│   │   │   │   ├── model/
│   │   │   │   └── usecase/
│   │   │   ├── data/
│   │   │   │   ├── remote/dto/
│   │   │   │   ├── local/          -- .sq files + mappers
│   │   │   │   └── repository/
│   │   │   ├── network/
│   │   │   ├── synchronization/
│   │   │   ├── security/
│   │   │   ├── presentation/       -- ViewModels + UiState/Event/Effect (C-05)
│   │   │   │   ├── acopio/
│   │   │   │   ├── calidad/
│   │   │   │   ├── produccion/
│   │   │   │   ├── ventas/
│   │   │   │   ├── recepcion/
│   │   │   │   └── comun/          -- login, home, pendientes, comunicados, ajustes
│   │   │   └── ui/                 -- Compose Multiplatform (C-06)
│   │   │       ├── screens/        -- una carpeta por rol, espejo de presentation/
│   │   │       ├── components/     -- componentes reutilizables (§9 de MOBILE_SCREENS.md)
│   │   │       ├── theme/          -- colores, tipografía, espaciado
│   │   │       └── navigation/     -- grafo de navegación
│   │   ├── commonTest/kotlin/       -- tests de domain/data/synchronization/presentation (§17)
│   │   ├── androidMain/kotlin/      -- actuals Android
│   │   └── iosMain/kotlin/          -- actuals iOS
│   └── build.gradle.kts
├── androidApp/
│   └── src/main/kotlin/...          -- MainActivity + DI wiring (contenedor delgado)
├── iosApp/
│   └── iosApp/...                    -- SwiftUI App que monta Compose + DI wiring
└── settings.gradle.kts
```

Estructura plana, sin capas extra que el proyecto no necesita (ej. no hay un módulo `feature-*` por
pantalla en esta fase — se evalúa si se justifica cuando exista más de un feature grande compitiendo por
límites claros; prematuro definirlo ahora).

## 16. Flujos principales

### 16.1 Crear offline (ej. RegistroAcopio)

```text
Usuario (ACOPIADOR) completa el formulario
 ↓
UI → CrearRegistroAcopioUseCase(datos)
 ↓
UseCase genera uuidCliente = UUID.v4()
 ↓
Repository.guardarLocal(registro, uuidCliente, status=PENDING)
 ↓
SQLite (registro_acopio_local)
 ↓
UI observa el Flow de SQLite → aparece de inmediato, con badge "pendiente"
 ↓
SyncEngine.solicitarSyncOportunista()   -- intenta ya mismo si hay red; si no, espera §6.5
```

### 16.2 Sincronizar

```text
ConnectivityObserver emite true
 ↓
SyncEngine.ejecutarCiclo()
 ↓
Por cada recurso (RegistroAcopio, AnalisisCalidad*, LoteProduccion*, Venta):
   lote = SQLite.seleccionar(status=PENDING, SYNCING huérfano, o FAILED-transitorio con next_attempt_at <= now)
   trocear en fragmentos de 50 (decisión de Fase 5)
   marcar SYNCING
   POST /api/sync/{recurso}(fragmento)
   Spring Boot → procesarLote → {confirmados[], errores[]}
   por uuidCliente en confirmados → SQLite: status=SYNCED, sincronizado_en=hora del dispositivo
                                    (NO llega server_id en el lote — DATA-014, ver §6.1)
   por uuidCliente en errores → SQLite: status=FAILED, sync_error=motivo (no reintentable automático)
   uuidCliente ausente de ambas listas → se queda SYNCING, se reintenta el próximo ciclo (§6.2)
 ↓
GET /api/sync/cambios → reemplaza las tablas *_cache (§11.2)
```
*AnalisisCalidad/LoteProduccion solo entran al lote si su(s) `registroAcopioId` referenciado(s) ya tienen
`server_id` resuelto localmente (ver §18.1) — si no, quedan retenidos con un estado distinguible
(`PENDING_DEPENDENCY`) hasta que su padre sincronice.

### 16.3 Error / reintento

```text
PENDING → SYNCING → (timeout / sin red a mitad de camino / 5xx) → FAILED-transitorio
 ↓
backoff (§6.3) → next_attempt_at
 ↓
próximo ciclo de sync (por reconexión, background trigger, o backoff cumplido) → SYNCING de nuevo
 ↓
… hasta SYNCED, o hasta agotar reintentos automáticos → requiere revisión manual del usuario
```

### 16.4 Lectura offline

```text
UI → Repository.observar(query)
 ↓
SQLite (Flow reactivo)
 ↓
Datos (pueden estar desactualizados si no hubo sync reciente — la UI puede mostrar
       "actualizado hace X" a partir de la última fecha de sync exitosa)
```

## 17. Pruebas

Todo lo testeable vive en `shared/commonTest/`, corre en JVM sin emulador (rápido, en CI):

| Área | Qué se prueba | Herramienta |
|---|---|---|
| SQLite (Local Data Source) | CRUD de cada tabla, queries de `sync_status`, reemplazo transaccional de tablas `_cache` | SQLDelight driver en memoria + kotlin.test |
| Repository | Prioriza SQLite sobre red; dispara sync oportunista sin bloquear la escritura local; reconciliación por `uuidCliente` | kotlin.test + fakes de Remote/Local |
| UseCases | Reglas de negocio del cliente (ej. bloquear AnalisisCalidad si el padre no tiene `server_id`) | kotlin.test |
| API Client / serialización | DTOs deserializan igual que los records reales del backend (fixtures JSON tomados de `API_DOCUMENTATION.md`/Swagger); manejo de `ErrorResponse` uniforme; timeout | Ktor `MockEngine` |
| Autenticación | Guardar/leer/borrar token; detección de 401 → flujo de refresh; token expirado no se refresca (falla, exige login) | kotlin.test + fake `SecureTokenStorage` |
| Creación offline | Ver §17.1 (prueba end-to-end obligatoria) | kotlin.test + SQLDelight en memoria + `MockEngine` |
| Lectura offline | UI (fake) observando SQLite sin red disponible devuelve datos previamente cacheados | kotlin.test |
| Sincronización | Lote mixto (algunos confirmados, algunos con error) reconcilia correctamente cada `uuidCliente` | kotlin.test + `MockEngine` |
| Reintentos / backoff | Fallo transitorio incrementa `sync_attempts` y calcula `next_attempt_at`; fallo permanente NO se reintenta | kotlin.test |
| Duplicados | Reenviar un lote ya `SYNCED` (`MockEngine` simulando que el backend ya lo tenía) no genera una segunda fila local ni visual | kotlin.test |
| Pérdida/recuperación de conexión | `ConnectivityObserver` fake alterna `false→true`; el Sync Engine dispara un ciclo exactamente una vez por transición | kotlin.test + Turbine |
| Timeout | `MockEngine` con delay > timeout configurado → `FAILED`-transitorio, no `FAILED`-permanente | kotlin.test |
| Errores HTTP | 400/404/422 → permanente; 401 → dispara refresh; 403 → error de dominio sin reintento; 409 (`RecepcionPlanta`) → manejo específico, no genérico | kotlin.test |
| Conflictos | 409 de `RecepcionPlanta` no se reintenta como si fuera transitorio | kotlin.test |
| **Presentación (ViewModels)** | Cada `ViewModel` de `shared/presentation/`: transiciones de `UiState`, validaciones de formulario, mapeo de `ApiResult.Error` a mensaje, y que un `Event` no dispare dos veces el mismo `Effect` | kotlin.test + Turbine (`MOBILE_SCREENS.md §17`) |
| **Precisión decimal** | Roundtrip `JSON → bignum → TEXT → bignum → JSON` preserva la escala exacta (`12.50` no se convierte en `12.5`), y ningún `Double` aparece en el camino | kotlin.test |
| **Retención y logout** | La limpieza de 90/30 días no borra filas no-`SYNCED`; el logout con pendientes se bloquea; el Sync Engine ignora filas de otro `usuario_id` | kotlin.test + SQLDelight en memoria |

### 17.1 Prueba end-to-end obligatoria (offline-first real)

```text
SIN INTERNET (ConnectivityObserver fake = false)
 ↓
UseCase crea un RegistroAcopio → uuidCliente generado, guardado en SQLite, status=PENDING
 ↓
"Cerrar la app" → se destruye y recrea el grafo de DI/SQLDelight driver (simula reinicio de proceso)
 ↓
"Abrir la app" → Repository.observar(id) sigue devolviendo el registro, status=PENDING
 ↓
Internet vuelve (ConnectivityObserver fake → true)
 ↓
SyncEngine dispara ciclo → MockEngine responde 200 {confirmados:[uuidCliente]}
 ↓
SQLite: status=SYNCED, server_id presente
```
Esta prueba corre 100% en `commonTest` (SQLDelight en memoria + `MockEngine`), sin necesitar backend real
ni emulador — verificable en CI en segundos.

## 18. Cambios necesarios en backend

Ordenados por impacto. Ninguno es implementado en esta fase (regla de la consigna); se documentan para que
el equipo decida prioridad.

### 18.1 [OBLIGATORIO si se necesita cadena offline cruzada] Dependencia de ids de servidor entre AnalisisCalidad/LoteProduccion y RegistroAcopio

**Problema**: `AnalisisCalidadRequest.registroAcopioId` y `CrearLoteRequest.registroAcopioIds` son
`UUID` del **id real de Postgres** (`RegistroAcopioService` hace `registroAcopioRepository.findById(...)`).
Si un `RegistroAcopio` fue capturado offline y todavía no sincronizó, su `server_id` no existe aún — no hay
forma de que un `AnalisisCalidad`/`LoteProduccion` capturado offline lo referencie por su `uuidCliente`
local. Además, **no existe ningún endpoint para buscar un `RegistroAcopio` por `uuidCliente`** (solo por
`id`, o el historial completo de un proveedor), así que un dispositivo de CALIDAD/PRODUCCION que no
comparte el mismo backlog local que el dispositivo de ACOPIADOR no tiene manera de resolver la referencia
sin conectividad puntual.

**Endpoint(s) afectados**: `POST /api/analisis-calidad`, `POST /api/sync/analisis-calidad`,
`POST /api/lotes-produccion`, `POST /api/sync/lotes-produccion`.

**Impacto**: si CALIDAD o PRODUCCION intentan registrar su dato antes de que el `RegistroAcopio`
correspondiente haya sincronizado (verosímil: distinto dispositivo, distinta persona, ambos en campo), la
única opción hoy es bloquear esa captura hasta tener conectividad — contradice el objetivo offline-first
para esos dos módulos específicamente (que sí tienen `uuidCliente` propio, dando la falsa impresión de que
son 100% offline-first cuando en realidad dependen de un id ajeno).

**Solución propuesta** (mínima, no rompe compatibilidad): agregar un campo opcional
`registroAcopioUuidCliente` (String) a `AnalisisCalidadRequest` y a `CrearLoteRequest` (como lista, para
`CrearLoteRequest`), resuelto server-side vía `registroAcopioRepository.findByUuidCliente(...)` cuando
`registroAcopioId` viene nulo. El cliente entonces puede enviar el lote completo (padre + hijos) confiando
en que, mientras el padre se procese en el mismo o anterior ciclo de sync, el backend resuelve la
referencia sin que el móvil necesite conocer nunca un id de servidor.

**Mitigación sin cambio de backend** (la que adopta este plano para no bloquear el desarrollo), con dos
mecanismos complementarios (Rev. 2, C-02/C-03):

1. **Padre propio, aún no sincronizado** → el Sync Engine retiene el hijo en `PENDING_DEPENDENCY` (§6.1)
   hasta que `registro_acopio_local.server_id` exista. Se resuelve solo, en el mismo ciclo en que el padre
   se confirma.
2. **Padre ajeno (otro dispositivo)** → el móvil lo trae con `GET /api/registros-acopio/proveedor/{id}`
   apenas hay conectividad puntual y lo guarda en **`registro_acopio_cache`** (§11.2). A partir de ahí, el
   hijo se crea referenciando `registro_acopio_server_id` directamente y **no pasa nunca por
   `PENDING_DEPENDENCY`**: ya nace con la referencia resuelta y puede sincronizarse sin que su padre tenga
   nada que ver con este dispositivo.

El caso que **no** cubre ninguna de las dos: un padre ajeno capturado offline en otro dispositivo que
tampoco sincronizó todavía. Ahí no existe en ningún lado un id que referenciar, y la captura del hijo debe
bloquearse en la UI con un mensaje explícito (`MOBILE_SCREENS.md §6.2` y `§7.2`). Es la razón por la que §18.1 sigue
marcado como OBLIGATORIO si el negocio confirma que ese escenario es frecuente.

⚠️ **Limitación práctica del mecanismo 2** (`DATA-013`): el endpoint de listado devuelve
`RegistroAcopioResumenResponse`, sin `uuidCliente`. No es un problema para referenciar al padre (el `id` es
justo lo que el request necesita), pero sí impide **deduplicar** contra las filas propias:
si el mismo registro está en `registro_acopio_local` (capturado aquí) y en `registro_acopio_cache`
(descargado), el móvil no puede saber que son el mismo sin pedir el detalle uno por uno. Para v1 se acepta:
el ACOPIADOR y el CALIDAD son roles distintos en dispositivos distintos, así que el solapamiento es raro;
cuando ocurre, la UI puede mostrar el registro dos veces en la pantalla de selección, lo cual es feo pero
no rompe nada (ambas referencias apuntan al mismo registro de servidor).

**Prioridad**: OBLIGATORIO si el producto necesita que CALIDAD/PRODUCCION capturen offline sin depender de
que su propio dispositivo haya generado el `RegistroAcopio`; RECOMENDABLE en cualquier caso porque simplifica
mucho el Sync Engine del cliente.

### 18.2 [RECOMENDADO] Confirmación de comunicado no es idempotente

**Problema**: `ConfirmarComunicadoRequest {proveedorId}` no tiene `uuidCliente`; cada `POST
/api/comunicados/{id}/confirmaciones` crea una fila nueva sin verificar duplicados.

**Endpoint afectado**: `POST /api/comunicados/{id}/confirmaciones`.

**Por qué afecta al modo offline**: es exactamente el tipo de acción que un ACOPIADOR realiza en campo sin
señal (confirmar que le mostró el comunicado a un proveedor) — hoy debe ser online-only para evitar
duplicar confirmaciones ante un reintento.

**Impacto**: bajo (una confirmación duplicada no corrompe datos de negocio críticos, solo ensucia el
registro de auditoría "quién confirmó"), pero bloquea que esta acción sea offline-first pese a que su
naturaleza lo permitiría.

**Solución propuesta**: agregar `UNIQUE(comunicado_id, proveedor_id)` en `comunicado_confirmacion` (clave
natural, no necesita `uuidCliente`) y que el service devuelva la confirmación existente en vez de fallar/
duplicar ante un repetido — mismo patrón que los 4 recursos con `uuidCliente`, pero más simple porque la
clave natural ya alcanza.

**Prioridad**: RECOMENDADO (no bloqueante para el MVP si se acepta dejar esta acción como online-only en
la v1 del móvil).

### 18.3 [RECOMENDADO] `RecepcionPlanta` no soporta reintento seguro

**Problema**: `RecepcionPlantaRequest` no tiene `uuidCliente`; un reintento ante una respuesta perdida
devuelve `409 Conflict` en vez de devolver el registro existente.

**Endpoint afectado**: `POST /api/recepcion-planta`.

**Por qué afecta al modo offline**: si en el futuro se decide que RECEPCION sí debe operar offline (hoy
este plano lo asume online-only, ver supuesto en la introducción), el cliente tendría que tratar un `409`
como "posiblemente ya se envió" y hacer un `GET` adicional para confirmar — más frágil que el patrón
idempotente ya probado en el resto de la API.

**Solución propuesta**: agregar `uuidCliente` a `RecepcionPlantaRequest` y aplicar el mismo patrón
`registrarOIgnorarSiDuplicado` que los demás services.

**Prioridad**: RECOMENDADO solo si se decide llevar RECEPCION a offline-first en una fase futura;
INNECESARIO si se confirma el supuesto de este plano (RECEPCION = online-only en planta con conectividad).

### 18.4 [OBLIGATORIO si se requiere evidencia fotográfica] No existe endpoint de subida de archivos

**Problema**: `RegistroAcopioDTO.fotoUrl` es un `String` (una URL ya resuelta) — no existe ningún
`@RequestMapping` con `MultipartFile` en todo el backend. No hay forma de que el móvil suba la foto que
captura en campo (mencionada como parte del flujo de campo); el campo `fotoUrl` no tiene ningún backend de
almacenamiento (S3, disco, etc.) que lo respalde.

**Endpoint afectado**: ninguno existe — es una ausencia, no un bug de uno existente. Afecta a
`POST /api/registros-acopio` y `POST /api/sync/registros-acopio` (el campo `fotoUrl` que ya aceptan queda
sin uso real).

**Impacto**: si la evidencia fotográfica es un requisito real del producto, el móvil no tiene manera de
cumplirlo hoy más allá de guardar la foto localmente sin nunca subirla.

**Solución propuesta**: un endpoint de subida (`POST /api/archivos` o similar) que reciba `multipart/form-data`,
devuelva una URL, y esa URL sea la que se envíe en `fotoUrl`. Requiere además decidir el backend de
almacenamiento (S3/GCS/disco local con servidor estático) — fuera del alcance de este documento.

**Prioridad**: **DIFERIDO A v2 — decisión tomada en Rev. 2 (C-07)**. La evidencia fotográfica queda fuera
del alcance de v1: no hay botón de cámara para foto en la pantalla de registrar acopio, no existe la
columna `foto_local_path` (§11.1) y el campo `fotoUrl` del request se envía siempre `null`. La alternativa
—capturar fotos y guardarlas localmente a la espera del endpoint— se evaluó y se descartó: acumularía
archivos sin política de limpieza en dispositivos de gama baja y le mostraría al usuario una funcionalidad
que no cumple lo que aparenta (la foto nunca saldría del teléfono, y se perdería al reinstalar).

**Para reactivarla en v2 hace falta, en este orden**: (1) decidir el backend de almacenamiento (S3/GCS/
disco), (2) implementar el endpoint de subida, (3) agregar `fotoUrl` al `RegistroAcopioResponse` (§18.6),
(4) recién entonces agregar en el móvil la columna, la pantalla, la política de retención de archivos y la
subida como paso extra del ciclo de sync (una foto pendiente de subir es otro estado sincronizable, no un
campo más del registro).

### 18.5 [OPCIONAL] Ruta de zona no viaja en la descarga incremental

**Problema**: `CambiosResponse` (`/api/sync/cambios`) no incluye `RutaProveedorOrden` — el ACOPIADOR debe
pedir `GET /api/zonas/{zonaId}/ruta` por separado para tener la ruta del día disponible offline.

**Impacto**: bajo — es una llamada adicional, no un bloqueo; el móvil ya puede cachearla localmente tras
esa llamada puntual.

**Solución propuesta**: agregar `rutaZona: List<RutaProveedorOrdenResponse>` (para la zona del acopiador
autenticado, resuelta server-side a partir de su usuario) a `CambiosResponse`, evitando una llamada
adicional en cada sync.

**Prioridad**: OPCIONAL / mejora de UX, no bloqueante.

### 18.6 [OPCIONAL] `RegistroAcopioResponse` no devuelve `fotoUrl`

**Problema**: el campo se acepta en el request y se persiste en `schema.sql` (`foto_url`), pero
`RegistroAcopioResponse` no lo expone — el móvil no puede releer la foto de un registro ya sincronizado
(ej. tras reinstalar la app) sin ese campo.

**Impacto**: bajo, cosmético — relevante solo si además se resuelve §18.4.

**Solución propuesta**: agregar `fotoUrl` a `RegistroAcopioResponse`.

**Prioridad**: OPCIONAL, atado a §18.4.

### 18.7 [RECOMENDADO] `CorreccionRegistro` no es idempotente

**Problema**: `CorreccionRegistroRequest {litrosCorregido, motivo}` no tiene `uuidCliente` ni ninguna otra
clave natural — `CorreccionRegistroService` (backend) crea una fila nueva en cada `POST`, sin verificar
duplicados. Confirmado también a nivel de columnas: `correccion_registro` en `schema.sql` no tiene ningún
`UNIQUE` más allá de la `id` autogenerada.

**Endpoint afectado**: `POST /api/registros-acopio/{id}/correcciones`.

**Por qué afecta al modo offline**: es una escritura típica de CALIDAD (rol móvil en este plano) — si en el
futuro se habilita offline para este endpoint, un reintento tras una respuesta perdida duplicaría la
corrección (dos filas con distinto `litrosCorregido`/`motivo` posiblemente idénticos, imposible de
distinguir cuál es la "real" después del hecho).

**Impacto**: medio — a diferencia de una confirmación de comunicado duplicada (§18.2, solo ensucia
auditoría), una `CorreccionRegistro` duplicada afecta directamente la trazabilidad de litros de un
registro (RNF-03/05), que es justamente el propósito de la tabla.

**Solución propuesta**: agregar `uuidCliente` a `CorreccionRegistroRequest` y aplicar el mismo patrón
`registrarOIgnorarSiDuplicado` que ya usan los 4 recursos offline-first.

**Prioridad**: RECOMENDADO — no bloqueante mientras este endpoint se mantenga ONLINE-ONLY (estado actual
en la clasificación de §3.1), pero es un prerrequisito obligatorio si en una fase futura se decide llevarlo
a offline-first.

## 19. Decisiones técnicas (resumen)

1. **Un solo módulo `shared/` para dominio+datos+red+sync+seguridad**, UI y background scheduling
   específicos por plataforma — minimiza superficie nativa a exactamente lo que Android/iOS no comparten.
2. **SQLite (SQLDelight) como única fuente de verdad para la UI** — nunca un ViewModel llama a Ktor
   directamente; toda lectura es un `Flow` reactivo sobre SQLite.
3. **Idempotencia por `uuidCliente` generado en el cliente**, apoyada en el diseño ya existente del backend
   para los 4 recursos offline-first — no se inventa un mecanismo propio de deduplicación en el cliente
   porque el servidor ya lo resuelve correctamente.
4. **El Sync Engine trata cada ítem de un lote de forma independiente** (reconciliación por `uuidCliente`
   contra `confirmados[]`/`errores[]`), reflejando exactamente cómo el backend procesa el lote
   (transacciones físicas independientes por ítem).
5. **JWT en almacenamiento seguro nativo, nunca en SQLite** — separa datos de sesión (sensibles) de datos
   de negocio (replicables, no secretos).
6. **Solo 34 de los 73 endpoints se clasifican MOBILE** — el resto es responsabilidad exclusiva del panel
   web administrativo, evitando que el móvil cargue lógica/datos que no le corresponden (RNF-12 incluido).
7. **Ninguna estrategia de resolución de conflictos "de fusión"** — el dominio evita conflictos de
   escritura concurrente por diseño (inmutabilidad de `RegistroAcopio`, `uuidCliente` único por
   dispositivo); donde sí puede haber conflicto real (`RecepcionPlanta`), se opta por rechazo (ya
   implementado server-side) en vez de inventar una resolución automática no solicitada.
8. **Gaps del backend documentados, no parcheados en el cliente** (§18) — en particular, la dependencia de
   ids de servidor entre AnalisisCalidad/LoteProduccion y RegistroAcopio (§18.1) se resuelve con una
   mitigación explícita y acotada en el cliente (`PENDING_DEPENDENCY` + `registro_acopio_cache`) en vez de
   una solución silenciosa que oculte la limitación real.
9. **Compose Multiplatform para las dos plataformas** (C-06) — cada pantalla y su `ViewModel` se escriben
   una sola vez, en `shared/`. Se acepta explícitamente el trade-off de no tener look&feel 100% nativo en
   iOS a cambio de no duplicar toda la capa de presentación.
10. **Los decimales nunca tocan un `Double` ni un `REAL`** (C-01) — del JSON a `bignum`, de `bignum` a
    `TEXT` en SQLite, y de vuelta. Es la única forma de que las cifras del móvil coincidan centavo a
    centavo con las columnas `GENERATED` de Postgres.
11. **El trabajo no confirmado nunca se borra solo** (C-09) — ni por logout, ni por retención, ni por
    cambio de usuario. Solo sale de la base por sincronización exitosa o por descarte explícito del
    usuario.

## 20. Riesgos y consideraciones

- **§18.1 es el riesgo más alto**: si en la práctica CALIDAD/PRODUCCION trabajan en dispositivos distintos
  al de ACOPIADOR y con conectividad tan escasa que nunca coincide una ventana en la que ambos estén
  online, la cadena RegistroAcopio→AnalisisCalidad/LoteProduccion puede quedar bloqueada por días. Vale la
  pena validar con el negocio qué tan real es este escenario antes de decidir si §18.1 es obligatorio.
- **Ausencia de refresh token persistente** (§4): un dispositivo que pasa más de 8h offline no podrá subir
  su backlog hasta hacer login de nuevo (aunque sí puede seguir capturando localmente sin límite). Riesgo
  de fricción de UX en rutas rurales largas, no riesgo de pérdida de datos.
  Debe validarse si el producto de negocio prevé una necesidad de ir mas alla de las 8 horas de trabajo por dispositivo/turno, si es asi valdria la pena que
  se evalúe extender la expiración o introducir un refresh token real en una fase posterior.
- **Marcos temporales mezclados dentro de un mismo registro** (nuevo en Rev. 2, C-10 — ver `DATA-012`):
  `fechaHora` la genera **el dispositivo** (hora de pared de `America/Lima`), mientras `creadoEn` y
  `sincronizadoEn` los genera **el servidor** con `LocalDateTime.now()` en un timezone que `DATA-001` deja
  como `UNKNOWN`. Si el servidor corre en UTC, dos campos del mismo registro están en marcos distintos y
  compararlos u ordenarlos entre sí da resultados incorrectos por ~5 horas — por ejemplo, un registro
  capturado a las 16:00 de Lima mostraría `sincronizadoEn` "21:00", pareciendo sincronizado 5 horas
  después de una captura que fue instantánea. Mitigación en v1: **nunca comparar `fechaHora` contra
  `creadoEn`/`sincronizadoEn`**, ni calcular duraciones entre ellos, ni ordenar listas mezclando ambos;
  cada uno se muestra por separado y etiquetado ("capturado" vs. "sincronizado"). Se resuelve de verdad
  cuando DevOps confirme el timezone (`DATA-001`).
- **Evidencia fotográfica diferida a v2** (§18.4, C-07): decidido y cerrado para v1. Si producto revierte
  esta decisión, no es un cambio de UI sino de backend + sync + retención — hay que replanificarlo, no
  agregarlo sobre la marcha.
- **Volumen bajo asumido por el propio backend** (`/sync/cambios` sin paginación real, comentado
  explícitamente como aceptable "en v1"): si el número de proveedores/comunicados crece significativamente,
  el `reemplazo completo de las tablas cache` en cada sync (§11.2/§9) podría volverse costoso en
  dispositivos de gama baja — no es un problema hoy, pero conviene no perder de vista si el catálogo crece.
- **RECEPCION confirmado como móvil** (C-08): resuelto, ya no es un supuesto abierto. Queda como
  online-only por diseño de backend; sus pantallas están en `MOBILE_SCREENS.md §9`. El riesgo residual es
  de conectividad física en planta: si la señal ahí resulta inestable, el flujo se bloquea sin alternativa
  offline, y habría que reabrir §18.3.
- **No hay endpoint de "eliminar mi cuenta / borrar mis datos"** ni de logout server-side (el JWT es
  stateless, sin blacklist) — un logout es puramente local (borra `SecureTokenStorage` + tablas
  sensibles); si se requiere invalidación server-side real, es otro cambio de backend fuera de alcance
  detectado aquí, y no se documenta como gap con más detalle porque no está claro que el producto lo necesite.

---

*Documento generado en base a la inspección directa del código fuente real del backend `acopio-backend`
(15 `@RestController`, 43 DTOs, `schema.sql`, `SecurityConfig`, `GlobalExceptionHandler`, y los Services de
los 4 recursos offline-first), sin inventar endpoints, campos ni comportamientos no verificados. Fase 0
completada: análisis + arquitectura + diseño + documentación. Ningún código de frontend fue escrito.*

*Rev. 2 (correcciones C-01 a C-10): resuelve las contradicciones internas entre este documento y
`MOBILE_DATA_MAPPING.md`, cierra las decisiones de UI (Compose Multiplatform), alcance de RECEPCION y
evidencia fotográfica, y agrega las políticas de logout, multiusuario y retención que faltaban. Se
incorpora `MOBILE_SCREENS.md` como tercer documento de la Fase 0, que cubre la capa de presentación que
este plano deliberadamente no describía.*

# Prompt — Fase 8B: pantallas comunes (pendientes, comunicados, sesión)

> Segunda de las cinco sub-fases de la Fase 8. Leé antes `PROMPT_FASE_08.md`: sus secciones §4 (reglas
> comunes), §6 (criterios de aceptación) y §7 (checkpoint) **aplican acá y no se repiten**.
>
> Alcance: `S-05` Pendientes, `S-06` Comunicados, `S-07` Ajustes y sesión, y `A-07` Confirmar comunicado
> a proveedor. Más el componente `DialogoConfirmacion` — el número 11 de `MOBILE_SCREENS.md §13`, el
> único que sigue sin construirse.
>
> El criterio de "terminado": *un ACOPIADOR que cerró la jornada con 12 entregas sin enviar abre Ajustes,
> intenta cerrar sesión, y la app no lo deja perder nada: le dice cuántas quedan, lo lleva a verlas, le
> deja arreglar la que el servidor rechazó, reintentarla, y recién entonces salir — o salir conservando
> todo, sabiendo exactamente qué se queda en el dispositivo.*

## 0. Contexto que esta sub-fase hereda

**`S-05` es la pantalla más importante del modo offline y la más fácil de subestimar**
(`MOBILE_SCREENS.md §4`). Es donde el usuario ve que su trabajo existe y donde arregla lo que el servidor
rechazó. Sin ella, un registro que falla por regla de negocio desaparece en silencio y se pierde una
entrega de leche. Tratala como la pantalla central de esta sub-fase, no como una lista más.

**Todo el dominio que `S-07` necesita ya existe y nadie lo usó todavía.** La Fase 6 construyó
`VerificarPendientesUseCase` y `LogoutUseCase` implementando literalmente la tabla "Política de logout" de
`MOBILE_ARCHITECTURE.md §4`, incluido el modo "cerrar sesión conservando los datos" como parámetro
separado. Esta sub-fase es la primera que lo expone en pantalla. **Leé `LogoutUseCase.kt` antes de diseñar
`S-07`**: el flujo de la UI tiene que calzar con la forma que ese UseCase ya tiene, no al revés.

**Con Venta (Fase 7) y Acopio (`8A`) capturables, `S-05` por fin tiene datos reales que mostrar.** Pero
las 4 tablas `*_local` existen desde la Fase 4: `S-05` tiene que manejar los **cuatro** recursos
(`ACOPIO`, `CALIDAD`, `LOTE`, `VENTA`), no solo los dos que hoy se pueden capturar. Los de CALIDAD y LOTE
llegan en `8C`/`8D` y no deben requerir tocar `S-05` de nuevo.

**Lo que ya existe y se reutiliza**: los 10 componentes de `ui/components/`, `presentation/ErrorUi.kt`,
`presentation/Formateadores.kt`, el patrón `UiState`/`Event`/`Effect` de los 13 `ViewModel` ya escritos,
`ObservarPendientesUseCase`, `ReintentarManualUseCase`, `SincronizarAhoraUseCase`,
`ObservarResumenSyncUseCase`, `ObservarConectividadUseCase`, `VerificarPendientesUseCase`,
`LogoutUseCase`, `ConfirmarComunicadoUseCase` y `ComunicadoConfirmacionRepository`.

**No asumas ninguna firma.** Abrí cada UseCase antes de llamarlo.

## 1. Pendientes que arrastra `8A` (resolvelos o anotalos, no los ignores)

1. **`verificacion-ios.yml` no corrió para `8A`.** El workflow dispara con `push` **solo a `main`**, más
   `pull_request` y `workflow_dispatch`. `8A` está en la rama `fase-8a-acopiador`: empujarla no lo
   ejecuta. Y `8A` es justo la sub-fase que más lo necesita — `EscanerQr.ios.kt`,
   `SolicitanteDePermiso.ios.kt` y `ProveedorUbicacion.ios.kt` son código nativo de iOS que en Windows
   solo se sabe que compila. **Disparalo a mano (`workflow_dispatch`) o abrí el PR antes de seguir.**
2. **`CLAUDE.md §7` está incompleta.** Faltan dos filas que el checkpoint de `8A` dejó anotadas y sin
   escribir: `DATA-016` (sin forma de resolver la `zonaId` del ACOPIADOR) y el gap de runtime de
   cámara/ubicación/permisos en iOS. Es el mismo tipo de deuda que `PROMPT_FASE_08.md §1.2` ya señaló una
   vez: `CLAUDE.md` se lee en **toda** sesión, y hoy no menciona ninguno de los dos.
3. **`MOBILE_DATA_MAPPING.md §10` no tiene ni `DATA-015` ni `DATA-016`.** Ambos viven solo en checkpoints
   y en `CLAUDE.md` (el primero) — el catálogo canónico de hallazgos no los conoce.
4. **Estado de ramas**: `main` está en el commit de la Fase 7; `8A` vive en `fase-8a-acopiador`. Definí de
   dónde sale la rama de `8B` antes de empezar (de `8A`, o de `main` tras mergear) — no arranques a
   escribir sin eso resuelto.

## 2. Qué leer antes de empezar

| Documento | Sección | Por qué |
|---|---|---|
| `MOBILE_SCREENS.md` | §4, entradas `S-05`, `S-06`, `S-07` | Las tres pantallas, con el `UiState` de `S-05` y el árbol de decisión de logout de `S-07` ya escritos. No los rediseñes |
| `MOBILE_SCREENS.md` | §5, entrada `A-07` | La cuarta pantalla, online-only |
| `MOBILE_SCREENS.md` | §13 | El contrato de `DialogoConfirmacion`: *"confirmación que nombra explícitamente lo que se pierde"* |
| `MOBILE_SCREENS.md` | §10.5 | Indicadores de sync — `S-05` es el destino de todos ellos |
| `MOBILE_ARCHITECTURE.md` | §4 completo | La tabla "Política de logout" y la regla multiusuario. Es el contrato literal de `S-07` |
| `MOBILE_ARCHITECTURE.md` | §11.4 | Retención: qué se borra solo y qué **nunca**. Define lo que "descartar" significa y por qué es la única salida |
| `MOBILE_ARCHITECTURE.md` | §7 | Idempotencia por `uuidCliente` — es lo que hace seguro reintentar un registro editado |
| `MOBILE_ARCHITECTURE.md` | §18.2 | Por qué `A-07` no se puede encolar |
| `MOBILE_DATA_MAPPING.md` | §5.7 | `ConfirmarComunicadoRequest` (solo `proveedorId`, **sin** `uuidCliente`) y su Response |
| `MOBILE_DATA_MAPPING.md` | §5.6, bloque de comunicados | `mensaje`, `fecha` (⚠️ `LocalDateTime`, no `LocalDate`) y `zonasNombres` |
| `PROMPT_FASE_06.md` | §6 y §7 | Cómo quedaron `VerificarPendientesUseCase`/`LogoutUseCase` y cómo hay que exponer `PENDING_DEPENDENCY` con su motivo |
| `PROMPT_FASE_08.md` | §4, §6, §7 | Reglas comunes, criterios y checkpoint |

## 3. Alcance

### Sí

- `S-05`, `S-06`, `S-07`, `A-07`: `ViewModel` en `presentation/comun/` (y `presentation/acopio/` para
  `A-07`), pantallas en `ui/screens/`.
- `DialogoConfirmacion` en `ui/components/` — cierra los 11 de `§13`.
- Los UseCases nuevos de la sección 5.
- Cablear `comunicadosNoLeidos` en `S-03`: el `HomeUiState` de la Fase 7 lo declara y hasta ahora no tiene
  nada detrás. Ahora que `S-06` existe, se conecta.

### No

- Pantallas de CALIDAD, PRODUCCION o RECEPCION (`8C`, `8D`, `8E`).
- La limpieza automática por retención de `§11.4` (90 días / 30 días): corre en el worker de background
  sync, y ese worker es **Fase 9**. Esta sub-fase implementa solo el **descarte explícito del usuario**.
- Compose UI tests — siguen para la Fase 10.

## 4. Las cuatro pantallas

### `S-05` Pendientes — la central de esta sub-fase

Fuente: las 4 tablas `*_local` con estado ≠ `SYNCED`. El `UiState` (`PendientesUiState` + `ItemPendiente`)
está escrito en `MOBILE_SCREENS.md §4`: usalo tal cual, incluidos `intentos` y `diasEsperando`.

**Tres secciones, en este orden**, y el orden importa: lo que exige acción va arriba.

| Sección | Estados | Qué puede hacer el usuario |
|---|---|---|
| **Con error** | `FAILED` permanente | Ver el `motivo` del backend, **editar y reintentar**, o **descartar** |
| **Esperando otra entrega** | `PENDING_DEPENDENCY` | Solo ver. Explica qué espera. Si lleva > 3 días → advertencia |
| **Por enviar** | `PENDING`, `SYNCING` | Ver. Forzar sync si hay señal |

**Las cuatro reglas:**

1. El `motivo` del backend se muestra **literal**, sin reinterpretar ni traducir. Lo escribió el Service,
   en español, y suele ser accionable ("proveedor inactivo", "fecha fuera de tolerancia"). Un texto
   genérico encima destruye el único dato útil que tiene el usuario para arreglarlo.
2. **Editar y reintentar** vuelve el estado a `PENDING` y resetea `sync_attempts`, **manteniendo el mismo
   `uuidCliente`** — la idempotencia del backend (`§7`) es lo que lo hace seguro. Ver la decisión 1 de la
   sección 7: hay que definir cómo se edita.
3. **Descartar** pide confirmación explícita **nombrando lo que se pierde**: *"Se va a borrar el registro
   de 120.50 L de Juan Pérez del 4/9. No se puede deshacer"*. Es la **única** forma de que trabajo no
   confirmado salga de la base (`§11.4`), así que trátala como una operación destructiva de verdad:
   `DialogoConfirmacion`, texto específico con los datos reales de la fila, y nada de un "¿Estás seguro?"
   genérico.
4. Esta pantalla es la que **desbloquea el logout** de `S-07`. Mientras tenga filas, `S-07` no cierra
   sesión sin avisar.

`PENDING_DEPENDENCY` **nunca** se pinta como error (`§10.5`): es una espera legítima del diseño. En rojo,
el usuario intenta arreglar algo que se resuelve solo. Y cuando el `sync_error` nombra `DATA-014`, el
motivo se muestra tal cual lo dejó la Fase 6 (`PROMPT_FASE_06.md §7`) — esa honestidad es el punto.

Estado vacío: *"No tenés registros pendientes"*, **positivo**, no un error.

**`S-05` es accesible desde cualquier pantalla** por el `IndicadorSync` de la barra superior
(`§2.1` regla 4). Verificá que ese componente, que existe desde la Fase 7, efectivamente navegue acá.

### `S-06` Comunicados — READ-CACHE

Fuente `comunicado_cache` + `comunicado_zona_cache`, poblados por `/api/sync/cambios`. Solo lectura para
todos los roles.

- Campos: `mensaje`, `fecha` (⚠️ **es `LocalDateTime`, no `LocalDate`** — formatear `dd/MM/yyyy HH:mm`,
  `§10.2`) y `zonasNombres`.
- Vacío: *"No hay comunicados"*. Sin conexión: se lee del cache normalmente, con la marca de cuándo se
  actualizó.
- ⚠️ **Leé del cache, no del endpoint.** La tabla de trazabilidad `§18` asocia `S-06` a
  `GET /api/comunicados/zona/{zonaId}`, pero ese endpoint exige la `zonaId` — que es exactamente
  `DATA-016`, el gap que `8A` tuvo que resolver con una heurística. El cache lo puebla `/sync/cambios`
  **sin** necesitar `zonaId`, así que leerlo evita el problema por completo. Es la misma clase de tensión
  interna que ya se resolvió para `V-01` en la Fase 7: decidí, dejalo escrito, y anotá la corrección
  pendiente en `§18`.

### `S-07` Ajustes y sesión — el punto delicado

Contiene nombre y rol de la sesión, versión de la app, hora del último sync, y **cerrar sesión**.

El árbol de decisión está en `MOBILE_SCREENS.md §4` y hay que implementarlo **literal**:

```text
¿hay filas locales con estado ≠ SYNCED del usuario actual?
  no  → cerrar sesión normal: borra token + caches personales + filas SYNCED
  sí  → NO cerrar. Mostrar: "Tenés N registros sin enviar."
        Opciones:
          [Sincronizar ahora]        (si hay señal; al terminar, reevaluar)
          [Ver pendientes]           → S-05
          [Cerrar sesión igual]      → conserva las filas locales, borra solo token
                                        y caches personales. Advertencia explícita.
          [Cancelar]
```

- **"Al terminar, reevaluar"** es parte del contrato: tras sincronizar, se vuelve a contar. Si quedaron
  fallidas, el diálogo sigue.
- **Nunca existe un camino que borre trabajo no sincronizado sin que el usuario lo haya elegido leyendo
  exactamente qué se pierde.** Esa frase es el criterio de aceptación de la pantalla.
- El conteo sale de `VerificarPendientesUseCase`, que **filtra por `usuario_id` de la sesión activa**
  (`§4`, multiusuario). En una tablet compartida, los pendientes de otro usuario no cuentan ni se muestran.
- La **versión de la app** no existe hoy en ningún lado de `shared/`. Ver decisión 2 de la sección 7.

### `A-07` Confirmar comunicado a proveedor — ONLINE-ONLY

`POST /api/comunicados/{id}/confirmaciones`, body con `proveedorId` únicamente (`§5.7`).

- **Sin conexión: la acción se deshabilita** con el texto "Requiere conexión". **No se encola.** El
  endpoint no es idempotente (`DATA-005` / `§18.2`): un reintento crearía una confirmación duplicada en la
  auditoría. Es una limitación real del backend y la UI la refleja en vez de esconderla. Usá
  `BloqueoOnlineOnly`, que existe desde la Fase 7.
- Éxito: la confirmación queda marcada en la lista de `S-06` — por eso `A-07` va en esta sub-fase y no
  en `8A`.
- ⚠️ **Esa marca no sobrevive a un reinicio, y eso no tiene arreglo limpio hoy.** No hay tabla local para
  `ComunicadoConfirmacion` (`§11.3`), y el único endpoint que las lista —
  `GET /api/comunicados/{id}/confirmaciones` — es **WEB/ADMIN, fuera de alcance móvil**
  (`MOBILE_DATA_MAPPING.md §11`). Es decir: la app no tiene forma de saber si ya se confirmó. Ver decisión
  5.
- No inventes cola ni reintento automático "para que sea offline-first como el resto"
  (`PROMPT_FASE_06.md §10`, trampa 9).

## 5. UseCases nuevos

| Para | Qué hace | Nota |
|---|---|---|
| Descartar un pendiente | Borra la fila `*_local` no sincronizada, por `uuidCliente` | La única operación del proyecto que borra trabajo no confirmado (`§11.4`). Que sea explícita, atómica y filtrada por `usuario_id` |
| Editar y reintentar | Vuelve la fila a `PENDING` y resetea `sync_attempts`, conservando `uuidCliente` | Ver decisión 1 |
| Observar comunicados | `Flow` sobre `comunicado_cache` + `comunicado_zona_cache` | Read-cache puro |
| Contar comunicados no leídos | Para `comunicadosNoLeidos` de `S-03` | Ver decisión 3: "leído" no existe en el contrato |
| Datos de sesión para `S-07` | Nombre, rol, y la versión de la app | `GestorSesion` ya tiene lo primero |

`ReintentarManualUseCase` ya existe (Fase 6): fijate si cubre el "reintentar" sin edición antes de escribir
uno nuevo.

## 6. Testing requerido

Todo en `commonTest`, en JVM sin emulador, con los fixtures existentes:

1. **`S-05` agrupa en las tres secciones correctas**: `FAILED` arriba, `PENDING_DEPENDENCY` en el medio,
   `PENDING`/`SYNCING` abajo — con filas de al menos dos recursos distintos (acopio y venta).
2. **`S-05` muestra el `motivo` literal**: el texto del backend llega al `UiState` sin traducir ni
   reemplazar.
3. **`PENDING_DEPENDENCY` no es un error**: el `UiState` lo expone con una categoría distinta de la de
   error, y un motivo que nombra `DATA-014` se muestra tal cual.
4. **`diasEsperando > 3`** produce la advertencia; `<= 3` no.
5. **Descartar**: exige confirmación (no borra sin el evento de confirmación), borra solo la fila objetivo,
   y **no** toca filas de otro `usuario_id`.
6. **Editar y reintentar**: la fila vuelve a `PENDING`, `sync_attempts` queda en 0 y el `uuidCliente` es el
   mismo que antes.
7. **`S-07` logout bloqueado**: con filas ≠ `SYNCED` del usuario activo, el logout **no** procede y el
   `UiState` expone el conteo y las cuatro opciones.
8. **`S-07` logout limpio**: sin pendientes, procede y borra token + caches personales + filas `SYNCED`.
9. **`S-07` "cerrar sesión igual"**: borra token y caches personales y **deja intactas** las filas
   `*_local` no sincronizadas.
10. **`S-07` reevalúa tras sincronizar**: si tras un ciclo quedan filas `FAILED`, el diálogo sigue
    apareciendo en vez de dejar salir.
11. **`S-07` multiusuario**: con pendientes de otro `usuario_id`, el conteo para la sesión activa es 0 y el
    logout procede.
12. **`S-06` fecha**: `fecha` se formatea como `LocalDateTime` (`dd/MM/yyyy HH:mm`), no como `LocalDate`.
13. **`S-06` sin conexión**: lee del cache y expone la marca de última actualización, sin estado de error.
14. **`A-07` sin conexión**: la acción queda deshabilitada, **no** se dispara ninguna llamada de red
    (verificable con `MockEngine`) y no se encola nada.
15. **`A-07` con conexión**: al confirmar, la marca aparece en el `UiState` de `S-06`.

Reportá el conteo total leyendo `shared/build/test-results/jvmTest/`, no de memoria.

> ### ⚠️ Cómo testear un `ViewModel` que hace trabajo async en `init {}`
>
> Aprendido en `8A`, a costa de un ciclo entero de debugging: un `ViewModel` que lanza trabajo en
> `init {}` con `viewModelScope.launch` **no resuelve sincrónicamente** bajo `UnconfinedTestDispatcher`.
> Leer `uiState.value` justo después de construirlo devuelve el estado inicial, no el resuelto.
>
> **Esperá el estado con Turbine** — `uiState.test { }` y `awaitItem()` hasta que la condición se cumpla
> (por ejemplo, hasta `!cargando`) — **nunca** leas `.value` a ciegas después del constructor. Casi todos
> los `ViewModel` de esta sub-fase cargan en `init`, así que esto aplica a la mayoría de los 15 tests.

## 7. Decisiones que hay que tomar explícitamente

1. **Cómo se edita un pendiente desde `S-05`.** El documento dice "editar el registro y reintentar" pero
   no dice dónde. Las opciones razonables: reabrir la pantalla de captura correspondiente (`A-04` o
   `V-02`) en modo edición, precargada y con el mismo `uuidCliente`; o una edición acotada dentro de
   `S-05`. La primera reutiliza validaciones y evita duplicar reglas, pero obliga a tocar dos pantallas ya
   entregadas — y `A-04`/`V-02` hoy solo saben **crear**. Elegí, justificá, y si tocás esas pantallas
   decilo en el checkpoint (`PROMPT_FASE_08.md §7`).
2. **De dónde sale la versión de la app** para `S-07`. No existe en `shared/`. ¿`expect`/`actual` que lea
   `BuildConfig`/`Info.plist`, o una constante en `shared/` mantenida a mano? La primera es más correcta y
   suma dos `actual` más (y un stub de `jvmMain`, sin el cual `:shared:jvmTest` deja de compilar — trampa
   2 de `PROMPT_FASE_08A.md §6`).
3. **Qué significa "no leído"** en `comunicadosNoLeidos` de `S-03`. El contrato **no tiene** ese concepto:
   `ComunicadoResponse` no trae estado de lectura y no hay endpoint para marcarlo. O es estado puramente
   local (una tabla o una marca de "visto por última vez" contra `fecha`), o el campo se deja en 0 y se
   documenta como no implementable sin backend. **No inventes un campo del contrato** (`CLAUDE.md §3.3`).
4. **`S-06` lee del cache y no del endpoint** (sección 4). Confirmalo o rebatilo, y anotá la corrección
   de la fila `S-06` de `§18` si corresponde.

Si aparece un hallazgo de contrato nuevo, va al checkpoint con el formato de `MOBILE_DATA_MAPPING.md §10`
como `DATA-017`.

## 8. Trampas conocidas

| # | Trampa | Por qué importa |
|---|---|---|
| 1 | `S-05` que solo maneja los recursos capturables hoy (acopio y venta) | Las 4 tablas existen desde la Fase 4. Si no contempla `CALIDAD` y `LOTE`, `8C` y `8D` tienen que volver a abrirla |
| 2 | Descartar sin `DialogoConfirmacion` o con un "¿Estás seguro?" genérico | `§13` y la regla 3 de `S-05`: la confirmación tiene que **nombrar** lo que se pierde, con los datos reales de la fila |
| 3 | Descartar sin filtrar por `usuario_id` | Borra trabajo no confirmado de otro usuario en una tablet compartida. Es la peor pérdida de datos posible en este proyecto |
| 4 | Reemplazar el `motivo` del backend por un texto propio | Regla 1 de `S-05`. Es el único dato accionable que tiene el usuario |
| 5 | Pintar `PENDING_DEPENDENCY` en rojo o como fallo | `§10.5`. El usuario intenta arreglar algo que se resuelve solo |
| 6 | Un logout que borre filas ≠ `SYNCED` por cualquier camino | `§4` y `CLAUDE.md §3.6`. Es **la** regla que esta sub-fase existe para hacer cumplir |
| 7 | No reevaluar los pendientes después de "Sincronizar ahora" en `S-07` | El árbol de decisión lo pide explícitamente. Sin eso, el usuario sale creyendo que se envió todo |
| 8 | Cambiar el `uuidCliente` al editar y reintentar | Rompe la idempotencia de `§7`: el backend crearía un registro duplicado en vez de reconocer el mismo |
| 9 | Encolar `A-07` sin conexión | `DATA-005`/`§18.2`: confirmaciones duplicadas en una tabla de auditoría |
| 10 | Formatear `fecha` de `S-06` como `LocalDate` | Es `LocalDateTime`. Se pierde la hora y la lista queda ambigua |
| 11 | Inventar un campo de "leído" en el contrato de comunicados | Decisión 3. `CLAUDE.md §3.3`: nada de inventar contrato |
| 12 | Leer `uiState.value` justo después de construir el `ViewModel` | El recuadro de la sección 6. Costó un ciclo de debugging en `8A` |
| 13 | Un `expect`/`actual` de versión de app sin `actual` de `jvmMain` | Rompe `:shared:jvmTest` entero |
| 14 | Implementar la limpieza por retención de `§11.4` acá | Es Fase 9, en el worker de background. Esta sub-fase hace solo el descarte explícito |

## 9. Criterios de aceptación y checkpoint

Los de `PROMPT_FASE_08.md §6` y §7, sin cambios. Para el checkpoint, además:

- **Si tocaste `A-04` o `V-02`** para el modo edición (decisión 1), decilo explícitamente: son pantallas
  ya entregadas en sub-fases anteriores y es la vía más probable de regresión.
- **Qué aprendiste que deba entrar en `PROMPT_FASE_08C.md`** — `8C` es CALIDAD, donde `DATA-014` se vuelve
  visible al usuario por primera vez.

Detenete ahí y esperá aprobación explícita antes de `8C`.

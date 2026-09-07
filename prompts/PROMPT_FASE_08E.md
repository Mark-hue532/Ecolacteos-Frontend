# Prompt — Fase 8E: online-only (RECEPCION y las lecturas de CALIDAD)

> **Última de las cinco sub-fases de la Fase 8.** Al cerrarla quedan implementadas las **33 pantallas** de
> `MOBILE_SCREENS.md`: 7 (Fase 7) + 6 (`8A`) + 4 (`8B`) + 4 (`8C`) + 4 (`8D`) + **8 (esta)**.
>
> Leé antes `PROMPT_FASE_08.md`: sus secciones §4 (reglas comunes), §6 (criterios de aceptación) y §7
> (checkpoint) **aplican acá y no se repiten**.
>
> Alcance: `C-05` Buscar análisis por folio, `C-06` Registrar corrección de litros, `C-07` Alertas de
> anomalías, `C-08` Score de confianza, `R-01` Registrar recepción ★, `R-02` Resultado de conciliación
> (+ `R-02b`, el flujo de 409), `R-03` Historial de recepciones, `R-04` Pagos de proveedor. Más los **3
> Repository que faltan desde la Fase 6**.
>
> El criterio de "terminado": *ninguna de las 33 pantallas del inventario queda sin implementar, y las 8
> de esta sub-fase son honestas sobre que necesitan señal — bloquean con explicación en vez de fingir que
> encolan, y el único conflicto real del sistema (el 409 de `R-01`) se resuelve mostrando lo que hay, no
> reintentando a ciegas.*

## 0. Contexto que esta sub-fase hereda

**Es la sub-fase más independiente de las cinco.** Ninguna de sus 8 pantallas es offline-first, ninguna
toca el Sync Engine, ninguna tiene dependencia de padre. No hay `uuidCliente`, no hay `PENDING`, no hay
cola. Después de tres sub-fases peleando con `DATA-014`, esto es un cambio de registro: acá el trabajo es
llamar, mapear el error y bloquear con dignidad cuando no hay red.

**Pero trae los 3 Repository que faltan desde la Fase 6**, y eso es fundación nueva, no UI sobre
fundación existente. Empezá por ahí (§4).

**Lo que ya existe y se reutiliza**: los 11 componentes —en particular `BloqueoOnlineOnly` y
`DialogoConfirmacion`, que en esta sub-fase por fin se usan a fondo—, `ErrorUi.kt` (el mapeo único de
`§10.4`), `Formateadores.kt`, `CorreccionRegistroRepository` y `AnexarCorreccionUseCase` (Fase 6, para
`C-06`), `ObtenerZonaAsignadaUseCase` (`8A`, para `C-07`), `ContextoDeViewModelsDePrueba` (`8D`) y 25
`ViewModel` como referencia de estilo.

**Estado del proyecto**: 343 tests, 25 `ViewModel`, cinco roles con Home. **No asumas ninguna firma.**

## 1. Pendientes acumulados

### 1.1 El hallazgo de `8D` sobre `lifecycle` sigue abierto — y es el más importante del proyecto ahora

`8D` lo diagnosticó con `javap` sobre los jars reales: `jvmTestCompileClasspath` resuelve
`lifecycle-viewmodel-desktop 2.9.4/2.9.6` con `ViewModelStore.put(String, ViewModel)`, mientras
`jvmTestRuntimeClasspath` resuelve **2.11.0** con `put(Object, ViewModel)`. Es un cambio de ABI real, y
la consecuencia es doble:

1. **`ContextoDeViewModelsDePrueba.registrar()` es hoy un no-op documentado.** Los `ViewModel` de los
   tests siguen sin liberarse. La flakiness de `8C` no reapareció en 15 corridas, pero `8D` fue explícito
   en que **no puede atribuirse el arreglo**: un cambio ajeno en `FixtureRepositorios` (`MockEngine` con
   `dispatcher = Dispatchers.Unconfined`) es un candidato más directo. **La causa raíz sigue viva.**
2. **El pin de `CLAUDE.md §4` no se sostiene.** El documento dice
   `org.jetbrains.androidx.lifecycle 2.9.6`, y en el classpath de runtime de `jvmTest` hay 2.11.0. O sea:
   `CLAUDE.md` afirma algo que el build contradice, que es exactamente el problema que
   `PROMPT_FASE_08.md §1.2` ya obligó a corregir una vez.

**Qué hacer en esta sub-fase**: nada por tu cuenta. Es una decisión de toolchain (`CLAUDE.md §4`:
*"si alguna no resuelve o es incompatible, parás y lo reportás"*), y `8D` hizo bien en no tomarla. Pero
**si volvés a ver `Dispatchers.Main was accessed... test dispatcher was unset` o `CoroutinesInternalError`
en cualquier corrida, la sospecha va primero acá y no a tu código nuevo.** No pierdas una hora buscando
en el lugar equivocado: está diagnosticado.

### 1.2 La trampa de la coma ya se pisó cuatro veces — cerrala

Fases 2, 6, `8C` y `8D`: un nombre de test con coma rompiendo `compileTestKotlinIosArm64`. Cuatro veces
el mismo error, descubierto siempre tarde (después de una compilación de iOS que tarda minutos) y siempre
trivial de arreglar.

**Agregá una verificación barata que lo detecte en segundos**, antes de compilar para iOS: un test de
`commonTest` que recorra los nombres de función anotados, un check de Gradle, o lo que resulte más simple
en este proyecto. No es alcance de las pantallas, pero es la quinta vez que va a pasar si nadie lo corta,
y esta es la última sub-fase que puede dejarlo resuelto para la Fase 9 en adelante. Si te parece que no
corresponde, decilo en el checkpoint — pero decidilo, no lo dejes pasar por omisión.

### 1.3 Correcciones de documentación propuestas y nunca aplicadas

Van tres acumuladas, todas aprobadas conceptualmente en su checkpoint y ninguna en el documento:

- `MOBILE_SCREENS.md §6`, caso 3 de `C-02` (`8C`): el texto es falso por `DATA-014`.
- `MOBILE_SCREENS.md §7`, recuadro de `P-02` (`8D`): el mismo problema.
- `MOBILE_DATA_MAPPING.md §10`, `DATA-015` (`8D`): acotarlo a `total`, porque `tipoQuesoNombre` **sí** se
  resuelve vía `tipo_queso_cache` y hoy el hallazgo dice que no.

Son fuente de verdad del proyecto. Al cerrar la Fase 8 conviene que estén aplicadas: la Fase 9 y la
auditoría final de la Fase 11 las van a leer como si fueran ciertas.

### 1.4 Ramas

`main` tenía `8b`+`8c` mergeados en `e1112a8` y `8D` salió limpio de ahí. Confirmá dónde está `8D` antes
de arrancar y sacá `8E` del mismo lugar.

## 2. Qué leer antes de empezar

| Documento | Sección | Por qué |
|---|---|---|
| `MOBILE_SCREENS.md` | §6, entradas `C-05` a `C-08` | Las cuatro de CALIDAD online-only |
| `MOBILE_SCREENS.md` | §9 completo (`R-01`..`R-04` y `R-02b`) | RECEPCION entera, incluido el flujo de 409 |
| `MOBILE_SCREENS.md` | §10.4 y §10.6 | El mapeo de errores y los cuatro estados — en esta sub-fase el estado "sin conexión" es **bloqueante**, no informativo |
| `MOBILE_DATA_MAPPING.md` | §5.9, §5.10, §5.11 | Los contratos exactos de RecepcionPlanta, Pago e Innovación. Los tres Repository nuevos salen de acá |
| `MOBILE_DATA_MAPPING.md` | §1.6 | Enums con `UNKNOWN`: `estado` (`R-02`), `tipo` y `severidad` (`C-07`) |
| `MOBILE_ARCHITECTURE.md` | §11.3 | Confirma que estos recursos **no tienen tabla local** — son online-only de verdad, sin cola |
| `MOBILE_ARCHITECTURE.md` | §8 | El 409 de `R-01` es el **único conflicto real del sistema** |
| `MOBILE_ARCHITECTURE.md` | §18.3 y §18.7 | Por qué `R-01` y `C-06` no se pueden encolar |
| `PROMPT_FASE_06.md` | §4.4 y §8 | El patrón de Repository online-only sin cola, y el mapeo a errores de dominio |
| `PROMPT_FASE_08.md` | §4, §6, §7 | Reglas comunes, criterios y checkpoint |

## 3. Alcance

### Sí

- Los 3 Repository nuevos (§4) con sus UseCases.
- Las 8 pantallas: `ViewModel` en `presentation/calidad/` y `presentation/recepcion/`, pantallas en
  `ui/screens/`.
- La rama `Rol.RECEPCION` en `S-03` Home — es el quinto y último rol.
- Los accesos secundarios de CALIDAD a `C-05`..`C-08` desde `C-01`.
- La acción "Registrar corrección" de `A-06`/`C-04` → `C-06`, cuyo destino quedó previsto y sin
  implementar en `8A` y `8C`.
- La verificación de §1.2, si decidís hacerla.

### No

- **Ninguna cola, ni reintento automático, ni tabla local para estos recursos.** `§18.3`/`§18.7`: el
  backend no es idempotente para ninguno. Encolarlos crea duplicados reales — no es una mejora, es un bug
  (`PROMPT_FASE_06.md §10`, trampa 9).
- Background sync — Fase 9.
- Compose UI tests — Fase 10.
- `POST /api/pagos/generar` y cualquier endpoint ADMIN. `R-04` es **solo lectura** (RNF-12).

## 4. Los 3 Repository nuevos — empezá por acá

Mismo patrón que `CorreccionRegistroRepository` (Fase 6, `PROMPT_FASE_06.md §4.4`): sin tabla local, el
método llama al remoto directo y traduce el `ApiResult` a un resultado de dominio. Sin `PENDING`, sin
`uuidCliente`, sin reintento propio. Sin conectividad, el error sube tal cual a la UI.

| Repository | Endpoints | Contrato |
|---|---|---|
| `RecepcionPlantaRepository` | `POST /api/recepcion-planta`, `GET /api/recepcion-planta` (query `unidadId` opcional), `GET /api/recepcion-planta/{id}` | `§5.9` |
| Pagos | `GET /api/pagos/proveedor/{proveedorId}` | `§5.10` |
| Innovación | `GET /api/innovacion/alertas?zonaId={UUID}`, `GET /api/innovacion/score/{proveedorId}` | `§5.11` |

Los DTOs ya existen desde la Fase 2 (`RecepcionPlantaDto.kt`, `PagoResponse.kt`, `InnovacionDto.kt`):
**verificá que cubran los contratos de arriba antes de asumirlo** — nunca se usaron.

Dos casos de error que **no** son errores genéricos y hay que modelar como resultados de dominio propios:

- **409 en `POST /api/recepcion-planta`** → no es un fallo, es un conflicto con un flujo propio (§5.3).
- **404 en `GET /api/innovacion/score/{proveedorId}`** → no es un fallo, significa "este proveedor todavía
  no tiene histórico" (§6.4).

Si suben como `ApiError` genérico, la UI no puede distinguirlos y las dos pantallas quedan mal. Es el
mismo criterio del error 4.2.a de la Fase 6.

## 5. RECEPCION

> Opera en planta, con conectividad asumida. **Ningún flujo de este rol es offline**: el backend no lo
> diseñó para eso — `RecepcionPlantaRequest` no tiene `uuidCliente` y `/api/sync` no lo incluye.

### `R-01` Registrar recepción en planta ★ — ONLINE-ONLY

| Campo | Obligatorio | Validación |
|---|---|---|
| `fecha` | Sí | fecha válida |
| `turno` | **No** | libre; el servidor aplica `"UNICO"` si viene vacío |
| `unidadId` | Sí | selector desde `unidad_cache` |
| `litrosCampo` | Sí | `>= 0` inclusive, `precision=9, scale=2` |
| `litrosPlanta` | Sí | `>= 0` inclusive, `precision=9, scale=2` |

- **`turno` es nullable en el Request y no-nulo en el Response** (`§5.9`). El modelo del formulario debe
  permitir vacío. **Si la UI forzara un valor, estaría inventando un dato que el servidor sabe resolver
  mejor** — y además cambiaría la clave natural del conflicto de §5.3.
- **Sin conexión: la pantalla se bloquea por completo**, con explicación (`BloqueoOnlineOnly`). No se
  guarda borrador de envío ni se encola: encolar acá crea el problema de `DATA-006`.

### `R-02` Resultado de conciliación — ONLINE-ONLY

Resultado de `R-01` o de `GET /api/recepcion-planta/{id}`. Muestra `litrosCampo`, `litrosPlanta`,
`diferenciaPct` (columna `GENERATED`), `estado` (`OK`/`ALERTA`, con `UNKNOWN` de reserva) y
`litrosRegistradosAcopio` como referencia.

> ⚠️ **`litrosRegistradosAcopio` es nullable**: viene de un `SUM()` que sobre cero filas devuelve `NULL`,
> no `0`. La UI muestra *"Sin registros de acopio para esta unidad y fecha"*, **nunca "0 L"** — son
> afirmaciones distintas y confundirlas hace que un operario crea que se perdió leche.

Los tres campos calculados son **100% server-side y de solo lectura**. No recalcules `diferenciaPct`.

### `R-02b` Conflicto 409 — el único conflicto real del sistema

No es una pantalla: es un estado de `R-01`. El flujo es literal (`MOBILE_SCREENS.md §9`):

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
única salida honesta es mostrar lo que hay y dejar decidir. Y ojo con la interacción con `turno`: como el
servidor resuelve `null` → `"UNICO"`, un formulario con turno vacío choca contra el `"UNICO"` existente
aunque el usuario nunca haya escrito esa palabra. La explicación tiene que nombrar el turno **real** que
colisionó, no el que el usuario dejó en blanco.

### `R-03` Historial de recepciones — ONLINE + CACHE opcional

`GET /api/recepcion-planta`, con filtro opcional por `unidadId`. Sin paginación. Es el mismo endpoint que
usa `R-02b` para buscar la recepción existente: reutilizá el método del Repository, no escribas dos.

### `R-04` Pagos de proveedor — ONLINE-ONLY, solo lectura

`GET /api/pagos/proveedor/{proveedorId}`.

> ⚠️ **`precioLitro` tiene 3 decimales** (`precision=6, scale=3`), a diferencia de todos los demás campos
> monetarios del sistema, que tienen 2 (`§10.1`). Formatearlo con 2 decimales es mostrar un precio
> incorrecto en una pantalla de pagos a proveedores. `litrosTotales` y `total` van con 2.

El móvil **no** genera pagos.

## 6. CALIDAD online-only

### `C-05` Buscar análisis por folio — ONLINE-ONLY

`GET /api/analisis-calidad/folio/{folio}`. **El path param no es un UUID**: es texto libre de hasta 40
caracteres. Sin conexión, deshabilitado con explicación. Sin resultados: *"No encontramos ese folio"* —
estado vacío, no error.

### `C-06` Registrar corrección de litros — ONLINE-ONLY

`POST /api/registros-acopio/{id}/correcciones`. `litrosCorregido` obligatorio (`>= 0`), `motivo`
opcional y libre.

- **Sin conexión: bloqueado** con explicación. El endpoint **no es idempotente** (`DATA-004` / `§18.7`) y
  una corrección duplicada corrompe la trazabilidad de litros, que es justo para lo que existe la tabla.
  Se prefiere bloquear a arriesgar.
- **Confirmación obligatoria antes de enviar, mostrando el valor anterior y el nuevo.** Es una operación
  de auditoría: no puede dispararse por un toque accidental. `DialogoConfirmacion` (`8B`) es el
  componente, y acá tiene que nombrar los dos números.
- El Response trae `litrosAnterior` y `usuarioNombre` que no están en el Request (`§5.2`) — el
  `litrosAnterior` del diálogo sale del registro que estás corrigiendo, no de una respuesta que todavía
  no llegó.

### `C-07` Alertas de anomalías — ONLINE-ONLY, y el problema de la zona

`GET /api/innovacion/alertas?zonaId={UUID}`. **`zonaId` es query param obligatorio**: sin él el backend
responde 400.

Y acá vuelve `DATA-016`: el contrato **no expone la zona del usuario autenticado**. `8A` resolvió el caso
del ACOPIADOR con una heurística sobre `unidad_cache` (`ObtenerZonaAsignadaUseCase`), pero **esa
heurística fue diseñada para ACOPIADOR y no tiene por qué valer para CALIDAD**, que trabaja sobre varias
zonas.

El documento ya da la respuesta correcta: *"La UI debe exigir elegir zona antes de consultar; nunca llamar
sin él."* Es decir, **un selector de zona**, no una heurística. De dónde sale la lista: `proveedor_cache`
guarda `zonaActualId`/`zonaActualNombre` de cada proveedor (`ProveedorPublicoResponse`, `§5.6`) —
**verificá que `ProveedorCache.sq` los persista** y armá el selector con las zonas distintas que haya ahí,
usando `ObtenerZonaAsignadaUseCase` solo como preselección cuando resuelva. Ver decisión 1.

Muestra `tipo` (`VOLUMEN_ATIPICO`/`RIESGO_ADULTERACION`), `severidad` (`BAJA`/`MEDIA`/`ALTA`), `zScore` y
el proveedor. Ambos enums con `UNKNOWN` de reserva, mostrados tal cual llegaron.

> ⚠️ **`zScore` es nullable y tiene 3 decimales** (`NUMERIC(6,3)`), como `precioLitro`. Nulo se omite,
> nunca `0.000`.

### `C-08` Score de confianza del proveedor — ONLINE-ONLY

`GET /api/innovacion/score/{proveedorId}`. `score` (0–100) y sus tres componentes, todos con 2 decimales.

> ⚠️ **Un 404 no es un error a mostrar como falla**: significa que ese proveedor todavía no tiene
> histórico. Estado vacío: *"Este proveedor aún no tiene score calculado"*. Si el 404 cae en el mapeo
> genérico de `§10.4`, la pantalla va a decir que algo salió mal cuando en realidad no pasó nada malo.

## 7. UseCases nuevos

| Para | Nota |
|---|---|
| Registrar recepción (`R-01`) | Debe distinguir el 409 como resultado propio, no como error |
| Buscar recepciones (`R-03`, y la búsqueda de `R-02b`) | Un solo método, dos consumidores |
| Detalle de recepción (`R-02`) | — |
| Pagos por proveedor (`R-04`) | Solo lectura |
| Alertas por zona (`C-07`) | Recibe la `zonaId` elegida; nunca la inventa |
| Score de proveedor (`C-08`) | Debe distinguir el 404 como "sin histórico", no como fallo |
| Buscar análisis por folio (`C-05`) | Verificá si `AnalisisCalidadRepository` ya expone algo servible |
| Zonas disponibles para el selector (`C-07`) | Desde `proveedor_cache`. Ver decisión 1 |

`AnexarCorreccionUseCase` (`C-06`) ya existe.

## 8. Testing requerido

Todo en `commonTest`, en JVM sin emulador, con `MockEngine` y `ContextoDeViewModelsDePrueba`:

1. **`R-01` sin conexión**: la pantalla se bloquea, **no** se dispara ninguna request y **no** se encola
   nada.
2. **`R-01` `turno` vacío**: se envía `null`, no `""` ni `"UNICO"` puesto por el cliente.
3. **`R-01` validaciones**: `litrosCampo`/`litrosPlanta` en `0` válidos; negativos inválidos.
4. **`R-02b` 409**: un 409 produce el estado de conflicto con sus tres opciones, dispara **una sola**
   búsqueda del registro existente, y **no reintenta el POST**.
5. **`R-02b` nombra el turno real**: con turno vacío y colisión contra `"UNICO"`, el mensaje dice
   `"UNICO"`, no vacío.
6. **`R-02` `litrosRegistradosAcopio` nulo**: se muestra el texto de "sin registros", nunca `0 L`.
7. **`R-02` enum desconocido**: un `estado` no reconocido se expone tal cual sin romper la pantalla.
8. **`R-04` escalas**: `precioLitro` con **3** decimales, `litrosTotales` y `total` con 2, todos desde
   `BigDecimal` sin pasar por `Double`.
9. **`C-05`**: folio de 40 caracteres válido; sin resultados produce estado vacío, no error; sin conexión
   queda deshabilitado.
10. **`C-06` sin conexión**: bloqueado, sin request.
11. **`C-06` confirmación obligatoria**: sin el evento de confirmación no se envía nada, y el diálogo
    expone el valor anterior y el nuevo.
12. **`C-07` nunca llama sin `zonaId`**: sin zona elegida no se dispara request; con zona elegida se
    dispara una.
13. **`C-07` `zScore` nulo y 3 decimales**: nulo se omite; presente se formatea con 3.
14. **`C-08` 404 = vacío**: un 404 produce el estado vacío con su texto, **no** un estado de error.
15. **Los 3 Repository no encolan nada**: tras un fallo de red, ninguna tabla `*_local` cambió y no hay
    reintento automático.

> ### ⚠️ Turbine y compilación: las lecciones acumuladas
>
> - **`8A`**: no leas `uiState.value` justo después del constructor — esperá con `uiState.test { }` +
>   `awaitItem()`.
> - **`8B`**: `while (condición) awaitItem()` falla con "Unconsumed events" si el flujo tiene pasos async
>   encadenados. Usá verdad de terreno y `cancelAndIgnoreRemainingEvents()`.
> - **`8C` y `8D`**: nombres de test con coma rompen `compileTestKotlinIos*`. Ver §1.2.
> - **`8D`**: si aparece `Dispatchers.Main was accessed...`, la sospecha va al hallazgo de `lifecycle`
>   (§1.1), no a tu código.

Reportá el conteo total leyendo `shared/build/test-results/jvmTest/`, y corré la suite completa **al menos
3 veces** dado que §1.1 sigue abierto.

## 9. Decisiones que hay que tomar explícitamente

1. **De dónde sale la lista de zonas de `C-07`** (§6). El selector desde `proveedor_cache` es la salida
   que el documento pide; si `ProveedorCache.sq` no persiste `zonaActualId`/`zonaActualNombre`, decilo —
   agregarlas es un cambio de esquema con migración, y es una decisión con costo.
2. **Si `ObtenerZonaAsignadaUseCase` se usa como preselección para CALIDAD** o si esa heurística queda
   restringida a ACOPIADOR. `DATA-016` la definió sobre `Unidad.responsableId`, que es una premisa de
   negocio del rol de campo.
3. **Si se implementa la verificación anti-coma de §1.2**, y con qué mecanismo.
4. **Qué pasa con `R-03` y el "CACHE opcional"** que menciona `MOBILE_SCREENS.md §9`: no hay tabla local
   para recepciones y `§11.3` lo confirma. Lo honesto es online puro; si decidís lo contrario, es una
   tabla nueva y hay que justificarla.
5. **Las tres correcciones de documentación de §1.3**: si las aplicás en esta sub-fase (en commit aparte)
   o si quedan para la Fase 9.

Si aparece un hallazgo de contrato nuevo, va al checkpoint con el formato de `MOBILE_DATA_MAPPING.md §10`,
numerado a continuación del último.

## 10. Trampas conocidas

| # | Trampa | Por qué importa |
|---|---|---|
| 1 | Encolar `R-01` o `C-06` "para que sean offline-first como el resto" | `DATA-006`/`DATA-004`: el backend no es idempotente. Crea duplicados reales en tablas de auditoría y conciliación |
| 2 | Reintentar automáticamente tras un 409 | `§8`: es un conflicto de clave natural, no un fallo transitorio. Reintentar no puede funcionar nunca |
| 3 | Forzar un `turno` desde el cliente cuando el usuario lo dejó vacío | `§5.9`: inventa un dato que el servidor resuelve, y cambia la clave del conflicto |
| 4 | Mostrar `litrosRegistradosAcopio` nulo como `0 L` | `§10.1` regla 3. Hace creer que se perdió leche |
| 5 | Formatear `precioLitro` con 2 decimales | Tiene 3. Es una pantalla de pagos a proveedores |
| 6 | Formatear `zScore` con 2 decimales, o mostrarlo como `0.000` si es nulo | Tiene 3 y es nullable |
| 7 | Tratar el 404 de `C-08` como error | Significa "sin histórico todavía". La pantalla diría que algo falló cuando no falló nada |
| 8 | Llamar a `GET /innovacion/alertas` sin `zonaId` | 400 garantizado. La UI exige elegir zona antes |
| 9 | Reutilizar la heurística de zona de ACOPIADOR para CALIDAD sin pensarlo | Decisión 2. `DATA-016` la definió sobre una premisa del rol de campo |
| 10 | `C-06` que envía sin confirmación explícita | Operación de auditoría; un toque accidental corrompe la trazabilidad |
| 11 | Recalcular `diferenciaPct` en el cliente | Columna `GENERATED`. Mismo error que el `total` de `V-02` y el `rendimientoPct` de `P-03` |
| 12 | Dos métodos distintos para `R-03` y la búsqueda de `R-02b` | Es el mismo endpoint. Duplicarlo garantiza que diverjan |
| 13 | Un banner informativo "sin conexión" en pantallas que en realidad no funcionan sin red | `§10.6` punto 4: para online-only es **bloqueo con explicación**, no un aviso que sugiere que igual se puede |
| 14 | Nombres de test con coma | §1.2. Cuatro veces ya |

## 11. Criterios de aceptación, checkpoint y cierre de la Fase 8

Los de `PROMPT_FASE_08.md §6` y §7, con la suite completa corrida **3 veces** (§1.1 sigue abierto).

Como es la última sub-fase, el checkpoint cierra además la Fase 8 entera:

- **Confirmá el inventario completo**: las 33 pantallas de `MOBILE_SCREENS.md` implementadas, y los 11
  componentes de `§13` en uso real. Si alguna quedó afuera o a medias, esta es la línea donde se dice.
- **El estado de `verificacion-ios.yml`** para toda la Fase 8. Cinco sub-fases de UI, cámara, GPS y
  Compose en el framework de iOS, y `CLAUDE.md §8` sigue diciendo que ninguna fase cierra sin ese run en
  verde.
- **La lista de deuda abierta que hereda la Fase 9**: el hallazgo de `lifecycle` (§1.1), las correcciones
  de documentación (§1.3), `DATA-014` y `DATA-016` sin resolver del lado backend, y el gap de runtime de
  cámara/GPS en iOS de `8A`.
- **Qué necesita la Fase 9** (Background Sync: `WorkManager` / `BGTaskScheduler`): en particular, que
  `§11.4` dejó la limpieza por retención (90 días / 30 días) explícitamente para ese worker, y que `8B`
  implementó solo el descarte explícito del usuario.

Detenete ahí y esperá aprobación explícita antes de la Fase 9.

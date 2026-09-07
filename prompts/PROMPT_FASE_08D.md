# Prompt — Fase 8D: PRODUCCION (lotes con dependencia múltiple)

> Cuarta de las cinco sub-fases de la Fase 8. Leé antes `PROMPT_FASE_08.md`: sus secciones §4 (reglas
> comunes), §6 (criterios de aceptación) y §7 (checkpoint) **aplican acá y no se repiten**.
>
> Alcance: `P-01` Home producción, `P-02` Seleccionar registros para el lote ★, `P-03` Registrar lote ★,
> `P-04` Detalle de lote.
>
> `8C` ya resolvió la parte conceptualmente difícil (la clasificación de padres y cómo mostrar
> `DATA-014` sin mentir). Esta sub-fase es su hermana con **selección múltiple** — y esa diferencia trae
> dos problemas propios: la regla de retención **agregada** (§4) y un dato que la app no puede mostrar
> offline (§6).
>
> El criterio de "terminado": *un PRODUCCION elige cinco entregas para un lote, ve antes de guardar
> cuántas de ellas van a retener el lote entero y por qué, carga los litros que realmente usó (no los que
> la app le sugiere), y nunca ve un rendimiento que el servidor no calculó.*

## 0. Contexto que esta sub-fase hereda

**El clasificador de padres de `8C` sirve tal cual.** Su checkpoint lo confirmó explícitamente:
`ClasificarPadresRegistroAcopioUseCase` opera sobre `RegistroAcopioRepository.observarHistorialProveedor`
y no sabe si el hijo va a ser un `AnalisisCalidad` o un `LoteProduccion`; `aReferenciaPadre()` devuelve el
mismo `ReferenciaRegistroAcopio` que `ResolutorPadreRegistroAcopio` comparte entre ambos repositorios
desde la Fase 6. **`P-02` no reimplementa la clasificación**: la consume y le agrega dos cosas —
selección múltiple y la regla de agregación de §4.

**`RegistrarAnalisisViewModel` (`8C`) es la referencia de estilo para `P-03`**, y
`SeleccionarRegistroAnalisisViewModel` para `P-02`. Copiá esos patrones.

**Lo que ya existe del lado de datos**: `LoteProduccionRepository` y `CrearLoteProduccionUseCase`
(Fase 6), las tablas `lote_produccion_local` y `lote_produccion_registro_local` con su índice único de
expresión (Fase 4), y `tipo_queso_cache`. El Sync Engine ya sabe retener lotes en `PENDING_DEPENDENCY`.

**Estado del proyecto**: 328 tests, 21 `ViewModel`, 11 componentes, cuatro roles con pantallas (VENTAS,
ACOPIADOR, CALIDAD parcial, comunes). **No asumas ninguna firma** — abrí cada UseCase antes de llamarlo.

## 1. Pendientes que arrastra `8C`

### 1.1 La flakiness de `:shared:jvmTest` ya no es teórica, y esta sub-fase la empeora

El checkpoint de `8C` lo midió: **2 de 5 corridas completas de la suite fallaron**, cada vez en una clase
distinta y no relacionada (`ComunicadosViewModelTest` de `8B`, `LoginViewModelTest` de la Fase 7, y uno de
los propios de `8C`), con `Dispatchers.Main was accessed... test dispatcher was unset` o
`CoroutinesInternalError`. Cada test pasó al correr aislado. La causa está identificada: **ningún test del
proyecto libera el `ViewModel` que construye**, así que corrutinas lanzadas en `viewModelScope` sobreviven
al test que las creó y se topan con el `Dispatchers.Main` que el test siguiente ya reseteó.

`8C` hizo bien en no arreglarlo dentro de su alcance (`CLAUDE.md §6`). Pero el problema **crece con cada
sub-fase**: `8D` suma 4 `ViewModel` y `8E` suma 8. Una suite de 350+ tests que falla 2 de cada 5 corridas
deja de ser una red de seguridad — es ruido que enseña a ignorar los rojos, justo cuando `8E` va a tocar
tres Repository nuevos.

**Arreglalo ahora, en un commit aparte y ANTES del código de esta sub-fase.** La forma:

- Un helper de test compartido que fije el dispatcher de `Main` antes de cada test y lo resetee después.
- Y que **libere el `ViewModel` al terminar**. En `androidx.lifecycle` un `ViewModel` no se limpia
  llamando `onCleared()` a mano (es `protected`): se limpia a través del `ViewModelStore` que lo
  contiene. Verificá la API real de la versión que usa el proyecto (`org.jetbrains.androidx.lifecycle
  2.9.6`) antes de decidir el mecanismo — no copies una receta de Android puro sin comprobarla.
- Aplicalo a los tests nuevos **y** a los existentes que construyan un `ViewModel`.

Es un cambio mecánico sobre 300+ tests, así que es exactamente lo que `CLAUDE.md §6` pide reportar:
commit propio, antes del código, y una línea en el checkpoint diciendo cuántos archivos tocó y si la
suite pasó a ser determinística (corré la suite completa **al menos 5 veces seguidas** para poder
afirmarlo — una sola corrida verde no prueba nada acá).

Si al intentarlo descubrís que el arreglo es más grande de lo que parece, **parás y lo reportás** en vez
de dejarlo a medias.

### 1.2 Correcciones de documentación propuestas y no aplicadas

- `MOBILE_SCREENS.md §6`, caso 3 de la tabla de `C-02`: `8C` propuso reemplazar el texto (falso por
  `DATA-014`) por la constante `AVISO_ANALISIS_RETENIDO_POR_DEPENDENCIA`. Sigue sin aplicarse.
- `MOBILE_SCREENS.md §7`, el recuadro de `P-02`, tiene **el mismo problema** — ver §4.2 de este prompt.

Son fuente de verdad del proyecto: se proponen en el checkpoint, no se editan por cuenta propia.

### 1.3 Ramas

`main` sigue en la Fase 7 y las sub-fases se vienen encadenando (`fase-8a-acopiador` → `8b` → `8c`).
Definí de dónde sale la rama de `8D` antes de empezar. Cinco ramas encadenadas sobre un `main` viejo es
un merge cada vez más caro.

## 2. Qué leer antes de empezar

| Documento | Sección | Por qué |
|---|---|---|
| `MOBILE_SCREENS.md` | §7 completo (`P-01`..`P-04`) | Las cuatro pantallas, con el recuadro de la regla de retención agregada de `P-02` |
| `MOBILE_SCREENS.md` | §6, `C-02` y `C-03` | La sub-fase gemela, ya implementada. Leela para no divergir del patrón |
| `MOBILE_ARCHITECTURE.md` | §18.1 completo | Los dos mecanismos, el caso que ninguno cubre, `DATA-013` |
| `MOBILE_ARCHITECTURE.md` | §11.1, bloques `lote_produccion_local` y `lote_produccion_registro_local` | Las columnas reales y el índice único de expresión — en particular **qué NO guarda** la tabla (§6 de este prompt) |
| `MOBILE_ARCHITECTURE.md` | §11.4 | Por qué `registro_acopio_cache` conserva las filas referenciadas por un `lote_produccion_registro_local` no sincronizado |
| `MOBILE_DATA_MAPPING.md` | §5.4 completo | `CrearLoteRequest`/`LoteProduccionResponse`, con `rendimientoPct` nullable y `unidadesObtenidas @Min(0)` |
| `MOBILE_DATA_MAPPING.md` | §5.6, bloque `tiposQueso` | Los tipos de queso llegan **ya filtrados a activos** por el servidor |
| `PROMPT_FASE_08C.md` | §3 completo | Cómo se resolvió el problema de honestidad de `DATA-014`. `P-02` tiene el mismo, agravado |
| `PROMPT_FASE_08.md` | §4, §6, §7 | Reglas comunes, criterios y checkpoint |

## 3. Alcance

### Sí

- `P-01`..`P-04`: `ViewModel` en `presentation/produccion/`, pantallas en `ui/screens/produccion/`.
- Los UseCases de la sección 7.
- El borrador de formulario para `P-03` (cuarto de los cinco formularios de captura).
- La rama `Rol.PRODUCCION` en `S-03` Home — hoy resuelve VENTAS, ACOPIADOR y CALIDAD.
- El arreglo de la suite de la sección 1.1, en commit aparte y primero.

### No

- RECEPCION y las online-only de CALIDAD (`8E`).
- Reimplementar la clasificación de padres (§0).
- Cualquier heurística para "arreglar" `DATA-014` (`PROMPT_FASE_06.md §3`).
- Compose UI tests — Fase 10.

## 4. `P-02` Seleccionar registros para el lote ★ — la pantalla central

Selección **múltiple** sobre la misma lista de tres categorías que `C-02`
(`ClasificarPadresRegistroAcopioUseCase`). `registroAcopioIds` es `@NotEmpty`: **mínimo 1**.

### 4.1 La regla de retención agregada

`MOBILE_SCREENS.md §7` es explícito:

> **Si cualquiera de los registros seleccionados es propio sin sincronizar, el lote entero queda en
> `PENDING_DEPENDENCY`** hasta que *todos* sus padres tengan `server_id`.

Esto es lógica **nueva** de esta sub-fase: el clasificador dice en qué categoría cae cada fila, pero la
agregación —"basta uno para retener todo"— no existe todavía. Va en un UseCase o en el modelo de dominio,
**no en el `ViewModel`** (`8C` puso el clasificador en dominio por la misma razón, y acertó).

Y el aviso va **al seleccionar, no al guardar** (`DATA-003`), con el conteo real:
*"2 de las 5 entregas elegidas todavía no se enviaron"*.

### 4.2 El mismo problema de honestidad que `C-02`, agravado

El recuadro de `MOBILE_SCREENS.md §7` termina diciendo *"el lote se enviará cuando se sincronicen"*.
**Es la misma frase falsa que `8C` ya tuvo que reescribir** para el caso 3 de `C-02`: por `DATA-014` el
`server_id` de un padre propio no aparece nunca, así que el lote no se envía cuando el padre sincroniza —
se queda retenido.

Y acá es **peor que en `C-02`**, por aritmética: con cinco padres seleccionados, la probabilidad de que al
menos uno sea propio-sin-sincronizar es mucho más alta que con uno solo. El caso degradado no es el raro;
con poca señal es el esperado.

**Reusá el criterio y el texto de `8C`** (la constante `AVISO_ANALISIS_RETENIDO_POR_DEPENDENCIA` y su
razonamiento en `PROMPT_FASE_08C.md §3`), adaptado a: (a) el plural, (b) el conteo "N de M", y (c) que
alcanza **una** entrega para retener el lote entero. Y ofrecé la misma salida real: con señal, refrescar
el historial del proveedor trae las entregas ya sincronizadas como **padres ajenos resueltos** con su `id`
real, y el lote nace sin retención.

Transcribí el texto final literal en el checkpoint, igual que hizo `8C`.

### 4.3 El total de litros: se muestra, no se autocompleta

`MOBILE_SCREENS.md §7`: se muestra el total de litros de lo seleccionado **como ayuda** para llenar
`litrosUsados`, pero **no lo autocompleta**. Son cosas distintas: se puede usar parte de una entrega. Un
autocompletado convierte una decisión del operario en un default que nadie revisa.

## 5. `P-03` Registrar lote ★ — OFFLINE-FIRST con dependencia

| Campo | Obligatorio | Validación |
|---|---|---|
| `fecha` | Sí | fecha válida, **no futura** |
| `tipoQuesoId` | Sí | selector desde `tipo_queso_cache` (solo `activo`) |
| `litrosUsados` | Sí | `>= 0` **inclusive**, `precision=9, scale=2` |
| `unidadesObtenidas` | Sí | entero **`>= 0`** (`@Min(0)` — **`0` es válido**, no `1`) |
| `registroAcopioIds` | Sí | **mínimo 1**, viene de `P-02` |

Dos detalles que es fácil equivocar:

- **`unidadesObtenidas = 0` es válido.** Es `@Min(0)`, no `@Min(1)` como la `cantidad` de `V-02`. Un lote
  que no rindió nada es un dato legítimo y hay que poder registrarlo.
- **El filtro por `activo` es defensivo, no funcional.** Los tipos de queso llegan por `/sync/cambios`
  **ya filtrados server-side** (`findByActivoTrue()`), así que `activo` siempre vale `true` en el cache
  (`MOBILE_DATA_MAPPING.md §5.6`). Filtrá igual, pero sabiendo que hoy es un no-op: si el selector aparece
  vacío, el problema es el cache sin sincronizar, no el filtro.

**`rendimientoPct` no se muestra al capturar.** Lo calcula el servidor, y **solo si `litrosUsados > 0`**:
con `litrosUsados = 0` (que es válido) el campo vuelve `null`. Mostrar un rendimiento previsto sería
inventar un dato del contrato — mismo error que el `total` de `V-02` y el `resultado` de `C-03`.

Borrador de formulario (`§3.4`), como `A-04`, `V-02` y `C-03`. Al guardar: navegar **atrás** con
`Snackbar`, sin pantalla intermedia.

## 6. `P-01` y `P-04` — y el dato que no existe offline

### El hueco

`lote_produccion_local` guarda `uuid_cliente`, `server_id`, `usuario_id`, `fecha`, `tipo_queso_id`,
`litros_usados`, `unidades_obtenidas` y las columnas de sync. **No guarda `rendimientoPct`, ni
`rendimientoEsperadoPct`, ni `tipoQuesoNombre`** — los tres son campos de solo respuesta
(`MOBILE_DATA_MAPPING.md §5.4`), y **no existe ninguna tabla `lote_produccion_cache`**.

Pero los tres no son iguales:

- **`tipoQuesoNombre` sí es resoluble localmente**: `lote_produccion_local.tipo_queso_id` +
  `tipo_queso_cache`. No hay gap.
- **`rendimientoEsperadoPct` probablemente también**: viene de `TipoQueso`. **Verificá si
  `tipo_queso_cache` lo persiste**; si sí, `P-04` puede mostrar el esperado aun offline.
- **`rendimientoPct` no es resoluble**: lo calcula el servidor. Offline no hay forma de saberlo, y
  **calcularlo local sería inventarlo**.

Es exactamente el patrón de `DATA-015` (`venta_local` sin `total`). Y nótese que la conclusión sobre
`tipoQuesoNombre` sugiere que **`DATA-015` puede estar de más en esa mitad**: `venta_local.tipo_queso_id`
+ `tipo_queso_cache` también da el nombre. Si al implementar confirmás eso, vale la pena anotarlo — sería
acotar un hallazgo existente, que es tan útil como abrir uno nuevo.

### `P-01` Home producción

`MOBILE_SCREENS.md §7` pide "lotes recientes con su **rendimiento** y estado de sync", y la trazabilidad
`§18` lo marca `GET /api/lotes-produccion` · `lote_produccion_local` · online+cache. Con lo de arriba:
**con señal** el rendimiento sale del GET; **sin señal** no se puede mostrar y hay que decirlo
("No calculado" / omitido), nunca un `0` ni un cálculo local (`§10.1` regla 3). Ver decisión 2.

Sin paginación (`CLAUDE.md §3.3`). Los cuatro estados de `§10.6`.

### `P-04` Detalle de lote — ONLINE + CACHE

`GET /api/lotes-produccion/{id}`. Compara `rendimientoPct` (**real, nullable**) contra
`rendimientoEsperadoPct` (siempre presente en el Response).

> **Si el real es `null`, se muestra "No calculado" y NO se dibuja la comparación.** Ni una barra a 0, ni
> un "0.00 %", ni una flecha hacia abajo. Una comparación contra un valor que no existe es una
> afirmación falsa sobre la producción de la planta.

Ambos porcentajes con **2 decimales** (`§10.1`).

## 7. UseCases nuevos

| Para | Qué hace | Nota |
|---|---|---|
| Retención agregada (`P-02`) | Dado un conjunto de padres elegibles, decide si el lote nace retenido y con qué conteo "N de M" | §4.1. Va en dominio, no en el `ViewModel` |
| Observar lotes recientes (`P-01`) | `lote_produccion_local` + nombre del tipo de queso desde `tipo_queso_cache`, con refresco desde el GET cuando hay señal | §6 |
| Detalle de lote (`P-04`) | `GET /api/lotes-produccion/{id}` con degradación a lo local | Mismo patrón que `ObtenerDetalleAnalisisCalidadUseCase` de `8C` |

`ClasificarPadresRegistroAcopioUseCase` y `CrearLoteProduccionUseCase` ya existen.

## 8. Testing requerido

Todo en `commonTest`, en JVM sin emulador, con los fixtures existentes:

1. **`P-02` selección múltiple**: seleccionar y deseleccionar mantiene el conjunto correcto; con 0
   seleccionados no se puede continuar (`@NotEmpty`).
2. **`P-02` retención agregada**: con 4 padres resueltos y 1 propio-sin-sincronizar, el lote se marca como
   retenido y el conteo dice "1 de 5". Con los 5 resueltos, no hay aviso.
3. **`P-02` avisa al seleccionar** (`DATA-003`): el aviso está en el `UiState` antes de cualquier evento
   de navegación a `P-03`.
4. **`P-02` total de litros**: se expone el total de lo seleccionado y **no** se escribe en el campo
   `litrosUsados` del formulario.
5. **`P-02` deduplicación (`DATA-013`)**: igual que `C-02`/`A-05` — una entrega en local con `server_id` y
   en cache con el mismo `id` aparece una sola vez.
6. **`P-02` no permite el mismo registro dos veces** en un lote (el índice único de expresión de
   `lote_produccion_registro_local` lo prohíbe; la UI no debería dejar llegar hasta ahí).
7. **`P-03` `unidadesObtenidas = 0` es válido**; `-1` inválido.
8. **`P-03` `litrosUsados = 0` es válido** (`@DecimalMin("0.0")` inclusive); negativo inválido; 10 dígitos
   enteros inválido.
9. **`P-03` `fecha` futura inválida.**
10. **`P-03` no expone `rendimientoPct`**: ningún campo del `UiState` de captura lo contiene.
11. **`P-03` borrador**: se persiste, sobrevive a la recreación del `ViewModel`, se borra al guardar, no
    cuenta como pendiente.
12. **Retención de punta a punta (`DATA-014`)**: crear dos `RegistroAcopio` propios, sincronizar solo el
    contexto que corresponda, crear el lote referenciando uno sin `server_id`, correr un ciclo completo, y
    verificar que el lote queda **esperando dependencia con `motivoConocido` no nulo** — nunca pendiente
    genérico, nunca error, y sin prometer que se resuelve solo.
13. **La salida real**: tras sincronizar los padres y refrescar el historial del proveedor, un lote nuevo
    sobre las mismas entregas (ahora ajenas resueltas) nace **sin** pasar por `PENDING_DEPENDENCY`.
14. **`P-04` `rendimientoPct` nulo**: se expone como "no calculado" y **no** hay comparación en el
    `UiState`; con valor presente, la comparación aparece con 2 decimales.
15. **`P-01` offline**: los lotes locales se listan con nombre de tipo de queso resuelto desde el cache, y
    sin rendimiento — nunca `0`.

> ### ⚠️ Turbine: las lecciones acumuladas
>
> - **`8A`**: nunca leas `uiState.value` justo después del constructor — un `ViewModel` que lanza trabajo
>   en `init {}` no resuelve sincrónicamente bajo `UnconfinedTestDispatcher`. Esperá con
>   `uiState.test { }` + `awaitItem()`.
> - **`8B`**: `while (condición) estado = awaitItem()` falla con "Unconsumed events" cuando el flujo tiene
>   pasos async encadenados. Usá **verdad de terreno** (la fila real en SQLite) y cerrá con
>   `cancelAndIgnoreRemainingEvents()`.
> - **`8C`**: un nombre de test con coma rompe `compileTestKotlinIos*`. Pasó de verdad, no en teoría.
>
> Los tests 12 y 13 son los que combinan las dos primeras.

Reportá el conteo total leyendo `shared/build/test-results/jvmTest/`, no de memoria. Y por la sección
1.1, **corré la suite completa al menos 5 veces** antes de afirmar que está verde.

## 9. Decisiones que hay que tomar explícitamente

1. **El texto del aviso de retención de `P-02`** (§4.2), literal en el checkpoint. Tiene que decir que
   basta una entrega para retener el lote entero, y no puede prometer que se resuelve solo.
2. **Qué muestra `P-01` en el lugar del rendimiento cuando no hay señal** (§6). "No calculado", omitirlo,
   o mostrar el esperado del tipo de queso claramente etiquetado como esperado. Cualquiera es defendible;
   `0` no lo es.
3. **Si `tipo_queso_cache` persiste `rendimientoEsperadoPct`** — y si no, si vale la pena agregarlo (sería
   un cambio de esquema, con su migración: decisión con costo, no la tomes en silencio).
4. **Si `DATA-015` se puede acotar** (§6): `venta_local.tipo_queso_id` + `tipo_queso_cache` probablemente
   ya resuelve `tipoQuesoNombre`, y el hallazgo hoy afirma que no está disponible. Si lo confirmás,
   proponé la corrección; no edites `MOBILE_DATA_MAPPING.md` por tu cuenta.
5. **El resultado del arreglo de la sección 1.1**: qué mecanismo usaste, cuántos archivos tocó, y si la
   suite quedó determinística en 5 corridas.

Si aparece un hallazgo de contrato nuevo, va al checkpoint con el formato de `MOBILE_DATA_MAPPING.md §10`,
numerado a continuación del último.

## 10. Trampas conocidas

| # | Trampa | Por qué importa |
|---|---|---|
| 1 | Implementar literal *"el lote se enviará cuando se sincronicen"* | §4.2. Es la misma frase falsa que `8C` ya reescribió para `C-02`, y acá pega más seguido |
| 2 | Poner la regla de agregación en el `ViewModel` | §4.1. Es lógica de dominio; `8C` puso el clasificador en dominio por la misma razón |
| 3 | Autocompletar `litrosUsados` con el total seleccionado | §4.3. Convierte una decisión del operario en un default que nadie revisa |
| 4 | Rechazar `unidadesObtenidas = 0` | Es `@Min(0)`, no `@Min(1)`. Un lote que no rindió nada es un dato real |
| 5 | Rechazar `litrosUsados = 0` | `@DecimalMin("0.0")` es inclusive. Y además es el caso que hace `rendimientoPct` nulo |
| 6 | Calcular `rendimientoPct` local | Lo calcula el servidor y solo si `litrosUsados > 0`. Mismo error que el `total` de `V-02` |
| 7 | Dibujar la comparación de `P-04` con `rendimientoPct` nulo | §6. Una comparación contra un valor inexistente es una afirmación falsa sobre la producción |
| 8 | Mostrar `0 %` donde el rendimiento es nulo | `§10.1` regla 3: `null` nunca es `0` |
| 9 | Reimplementar la clasificación de padres | Existe y está testeada desde `8C` |
| 10 | Heurística de matching padre-hijo que no sea `uuidCliente`/`id` exacto | `PROMPT_FASE_06.md §3` |
| 11 | Borrado masivo de `registro_acopio_cache` al refrescar | `§11.2`, y acá con más razón: `§11.4` conserva a propósito las filas referenciadas por un `lote_produccion_registro_local` sin sincronizar |
| 12 | Permitir el mismo registro dos veces en un lote | El índice único de expresión de la tabla puente lo rechaza; que no llegue hasta ahí |
| 13 | Avisar de la retención al guardar en vez de al seleccionar | `DATA-003` |
| 14 | Empezar por el código de las pantallas y dejar el arreglo de la suite para el final | §1.1. Si la suite es flaky mientras desarrollás, no vas a saber si un rojo es tuyo |
| 15 | Nombres de test con coma | Rompe `compileTestKotlinIos*`. Pasó en las Fases 2, 6 y `8C` |

## 11. Criterios de aceptación y checkpoint

Los de `PROMPT_FASE_08.md §6` y §7, con un agregado propio de esta sub-fase: **la suite completa corrida
5 veces seguidas, toda verde**, como evidencia del arreglo de la sección 1.1. Una corrida no alcanza.

Para el checkpoint, además:

- El **texto literal** del aviso de `P-02` (decisión 1).
- **Qué del andamio compartido tocaste** — `S-03` Home suma su cuarto rol, y el arreglo de la sección 1.1
  toca todos los tests de `ViewModel` del proyecto.
- **Qué aprendiste que deba entrar en `PROMPT_FASE_08E.md`** — `8E` es la última: 8 pantallas online-only
  y los 3 Repository que faltan.

Detenete ahí y esperá aprobación explícita antes de `8E`.

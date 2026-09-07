# Prompt — Fase 8C: CALIDAD (análisis con dependencia de padre)

> Tercera de las cinco sub-fases de la Fase 8. Leé antes `PROMPT_FASE_08.md`: sus secciones §4 (reglas
> comunes), §6 (criterios de aceptación) y §7 (checkpoint) **aplican acá y no se repiten**.
>
> Alcance: `C-01` Home calidad, `C-02` Seleccionar registro a analizar ★, `C-03` Registrar análisis ★,
> `C-04` Detalle de análisis. Las cuatro online-only de CALIDAD (`C-05` a `C-08`) van en `8E`.
>
> **Esta es la sub-fase donde `DATA-014` deja de ser una nota en un documento y se convierte en algo que
> un usuario ve.** Todo lo demás de acá es rutina comparado con eso. Leé la sección 3 antes que nada.
>
> El criterio de "terminado": *un CALIDAD elige una entrega de la lista y sabe, **antes de tocar el
> formulario**, si su análisis se va a enviar o va a quedar retenido y por qué; captura los parámetros que
> el lactoscan le dio y solo esos; y nunca ve un `resultado` que el servidor no confirmó.*

## 0. Contexto que esta sub-fase hereda

**Toda la máquina de resolución de padres ya está construida.** La Fase 6 dejó
`data/repository/ResolutorPadreRegistroAcopio.kt` con la máquina de decisión completa de
`PROMPT_FASE_06.md §4.2`, `AnalisisCalidadRepository`, `CrearAnalisisCalidadUseCase`,
`ObtenerRegistrosDeProveedorUseCase` (que puebla `registro_acopio_cache` on-demand) y el error de dominio
específico de "padre ajeno no resoluble sin conectividad" (4.2.a). El Sync Engine de la Fase 5 ya sabe
retener en `PENDING_DEPENDENCY` y promover solo cuando aparece el `server_id`.

**Esta sub-fase no reimplementa nada de eso: lo muestra.** Si te encontrás escribiendo lógica de
resolución de padre en un `ViewModel`, parás — ya existe, y está testeada por
`ResolucionDePadreTest.kt` (Fase 6).

**Lo que hay que leer del código antes de diseñar**: `ResolutorPadreRegistroAcopio.kt`,
`domain/model/EstadoSincronizacion.kt` (en particular `EsperandoDependencia(motivoConocido)`),
`domain/ErrorDominio.kt` y `CrearAnalisisCalidadUseCase.kt`. **No asumas ninguna firma.**

**El andamio de UI está completo**: los 11 componentes de `§13` (`DialogoConfirmacion` se cerró en `8B`),
`ErrorUi.kt`, `Formateadores.kt`, el patrón de borrador, y 17 `ViewModel` como referencia de estilo. El
más parecido a `C-03` es `RegistrarAcopioViewModel` (`8A`): formulario offline-first con borrador y
campos opcionales. Copiá ese patrón.

## 1. Qué leer antes de empezar

| Documento | Sección | Por qué |
|---|---|---|
| `MOBILE_SCREENS.md` | §6, entradas `C-01` a `C-04` | Las cuatro pantallas, con la tabla de tres casos de `C-02` |
| `MOBILE_ARCHITECTURE.md` | §18.1 **completo** | Los dos mecanismos de resolución, el caso que ninguno cubre, y la limitación práctica del mecanismo 2 (`DATA-013`). Es el documento fundacional de esta sub-fase |
| `MOBILE_ARCHITECTURE.md` | §11.2, bloque `registro_acopio_cache` | Columnas exactas (`id`, `uuid_cliente` NULLABLE, `origen` RESUMEN\|DETALLE) y semántica `INSERT OR REPLACE`, **nunca borrado masivo** |
| `MOBILE_ARCHITECTURE.md` | §6.1 | Qué significa `PENDING_DEPENDENCY` y cuándo el motor promueve |
| `MOBILE_DATA_MAPPING.md` | §5.3 completo | `AnalisisCalidadRequest`/`Response`: los 6 parámetros opcionales, `folioMuestra` de 40, `aguaAnadida` nullable, y `resultado` como enum abierto |
| `MOBILE_DATA_MAPPING.md` | §1.6 | Enums con `UNKNOWN` de reserva — `resultado` es el caso de esta sub-fase |
| `MOBILE_SCREENS.md` | §11, filas `DATA-003` y `DATA-013` | Las dos mitigaciones obligatorias que caen acá |
| `PROMPT_FASE_06.md` | §4.2 y §7 | Cómo quedó el resolutor y **cómo hay que exponer `DATA-014` sin genericarlo** |
| `CLAUDE.md` | §7, fila de `DATA-014` | El estado real del gap, y por qué la decisión de la Fase 5 fue la opción A |
| `PROMPT_FASE_08.md` | §4, §6, §7 | Reglas comunes, criterios y checkpoint |

## 2. Alcance

### Sí

- `C-01`..`C-04`: `ViewModel` en `presentation/calidad/`, pantallas en `ui/screens/calidad/`.
- Los UseCases de la sección 6.
- El borrador de formulario para `C-03` (tercero de los cinco formularios de captura).
- Cablear el acceso desde `S-03` Home para el rol CALIDAD — hoy Home resuelve VENTAS y ACOPIADOR.
- Los destinos nuevos en `Rutas.kt`/`NavGraph.kt`.

### No

- `C-05` a `C-08` (online-only) — van en `8E`.
- PRODUCCION (`8D`) y RECEPCION (`8E`).
- **Cualquier heurística para "arreglar" `DATA-014`.** Ver sección 3. `PROMPT_FASE_06.md §3` lo prohíbe
  explícitamente: adivinar el `server_id` del padre por `fechaHora`+`litros`+proveedor puede fusionar en
  silencio dos entregas distintas, que es peor que dejar el hijo retenido.
- La acción "Registrar corrección" que `C-04`/`A-06` ofrecen → `C-06` es de `8E`. Dejá el punto de entrada
  previsto, sin destino.
- Compose UI tests — Fase 10.

## 3. `DATA-014` en pantalla: el documento promete algo que hoy no se cumple

**Esto es lo más importante de la sub-fase. Leelo dos veces.**

`MOBILE_SCREENS.md §6`, caso 3 de la tabla de `C-02`, dice que para una entrega **propia sin sincronizar**
la UI muestre:

> *"Esta entrega todavía no se envió. El análisis se guardará y se enviará cuando la entrega se
> sincronice"*

**Ese texto se escribió antes de que se descubriera `DATA-014`, y hoy es falso.** El lote de sync nunca
devuelve el `server_id` (`confirmados[]` es solo `List<String>` de `uuidCliente`), así que
`registro_acopio_local.server_id` **no se puebla nunca** por esa vía. El mecanismo 1 de `§18.1` no cierra:
la entrega padre sincroniza, queda `SYNCED`, y el hijo se queda en `PENDING_DEPENDENCY`
**indefinidamente**, hasta que cambie el backend. Está documentado en `CLAUDE.md §7` y es la razón entera
de `PROMPT_FASE_06.md §7`.

Prometer "se enviará cuando la entrega se sincronice" es exactamente el tipo de mentira amable que este
proyecto viene evitando desde la Fase 0. **No lo implementes literal.**

### 3.1 Lo que sí hay que hacer

1. **Mostrar el `motivoConocido` que el dominio ya trae.** La Fase 6 dejó
   `EstadoSincronizacion.EsperandoDependencia(motivoConocido)` con el `sync_error` tal cual, y ese texto
   nombra `DATA-014`. No lo descartes ni lo reemplaces por un genérico — traducilo a algo que un operario
   entienda, sin borrar la información.
2. **Ofrecer la salida real, que existe.** Una vez que la entrega propia **ya sincronizó**, refrescar el
   historial del proveedor (`ObtenerRegistrosDeProveedorUseCase` → `GET /api/registros-acopio/proveedor/{id}`)
   la trae de vuelta desde el servidor con su `id` real y la guarda en `registro_acopio_cache`. A partir de
   ahí es un **padre ajeno resuelto** (mecanismo 2) y el análisis nace con la referencia lista, sin pasar
   nunca por `PENDING_DEPENDENCY`.

   Es decir: **con conectividad, el caso 3 casi no debería ocurrir**, y la UI puede evitarlo en vez de
   caer en él. Ver decisión 1 de la sección 8.
3. **Cuando el caso 3 igual ocurre** (sin señal, o el padre todavía no subió), decir la verdad: el
   análisis se guarda, queda retenido, y **puede quedar retenido incluso después de que la entrega se
   envíe**, por una limitación conocida del servidor. Con la salida del punto 2 explicada: *"cuando tengas
   señal, volvé a esta pantalla y actualizá la lista"*.
4. **`PENDING_DEPENDENCY` nunca se pinta como error** (`§10.5`), tampoco acá. Es una espera legítima. Pero
   una espera que no se va a resolver sola tampoco es "todo bien": el matiz correcto es informativo con
   advertencia, no rojo de fallo. `S-05` (`8B`) ya marca como advertencia lo que lleva > 3 días — mirá cómo
   lo resolvió y sé coherente.

**`DATA-003` (`MOBILE_SCREENS.md §11`) exige que esto se explique al SELECCIONAR, no al guardar.** Si el
usuario se entera después de llenar los seis campos, ya perdió el trabajo de decidir. El aviso va en la
fila de `C-02`, antes de navegar a `C-03`.

## 4. `C-02` Seleccionar registro a analizar ★ — la pantalla central

Fuente: `registro_acopio_local` + `registro_acopio_cache`. Cada fila cae en uno de tres casos
(`MOBILE_SCREENS.md §6`):

| Caso | Origen | ¿Analizable offline? | Qué muestra |
|---|---|---|---|
| Entrega **ajena ya descargada** | `registro_acopio_cache` (tiene `id` de servidor) | **Sí** — se referencia por `registro_acopio_server_id` | Normal, seleccionable |
| Entrega **propia ya sincronizada** | `registro_acopio_local` con `server_id` | **Sí** | Normal, seleccionable |
| Entrega **propia sin sincronizar** | `registro_acopio_local` sin `server_id` | Sí, **pero el análisis queda retenido** | Seleccionable, con el aviso honesto de la sección 3 |

**El caso que no se puede resolver**: una entrega capturada offline en **otro** dispositivo que tampoco
sincronizó. No existe en ningún lado localmente, así que **no aparece en la lista y no hay nada que
seleccionar**. El estado vacío lo explica con todas las letras:

> *"Si la entrega que buscás se registró recién en otro dispositivo, va a aparecer cuando ambos tengan
> señal."*

Esa es la limitación real de `DATA-003`, dicha sin disfrazarla de error.

**Con conexión**, se refresca `GET /api/registros-acopio/proveedor/{id}` y se puebla
`registro_acopio_cache` — con `INSERT OR REPLACE` fila por fila, **nunca borrado masivo** (`§11.2`):
borrarla rompe los padres ajenos ya resueltos de análisis que todavía no sincronizaron.

**`DATA-013`**: el listado devuelve `RegistroAcopioResumenResponse`, **sin `uuidCliente`**, así que una
entrega propia ya sincronizada puede aparecer dos veces (la fila local y la descargada). Se prioriza la
local cuando `local.server_id == cache.id`; el resto de los solapamientos **no es detectable** y `§18.1`
lo acepta explícitamente para v1: *"feo pero no rompe nada"*. `8A` ya resolvió esta misma deduplicación
para `A-05` — reutilizá ese criterio, no inventes otro.

## 5. `C-03`, `C-01` y `C-04`

### `C-03` Registrar análisis ★ — OFFLINE-FIRST con dependencia

| Campo | Obligatorio | Validación |
|---|---|---|
| `registroAcopioId` | Sí | viene de `C-02`, **no editable** |
| `folioMuestra` | Sí | no vacío, máx. **40** caracteres |
| `agua`, `proteina`, `lactosa`, `temperatura` | **No** | `precision=5, scale=2` |
| `densidad` | **No** | `precision=6, scale=2` |
| `ph` | **No** | `precision=4, scale=2` |
| `aguaAnadida` | **No** | switch, default `false` |

> **Los 6 parámetros de laboratorio son opcionales por contrato y eso es intencional**: el lactoscan puede
> no reportar todos los valores. La UI **no** debe exigirlos ni bloquear el guardado por un campo vacío. Un
> campo vacío se envía **`null`, no `0`** — son cosas distintas, y confundirlas mete un dato falso en un
> análisis de laboratorio.

**`resultado` no se muestra en esta pantalla.** Lo calcula el servidor (`APROBADO`/`RECHAZADO` según
`aguaAnadida`) y solo aparece en `C-04`, cuando el análisis sincronizó. Mostrar un resultado "previsto"
sería inventar un dato del contrato — el mismo error que el `total` de `V-02` en la Fase 7.

Borrador de formulario (`§3.4`), igual que `A-04` y `V-02`. Al guardar: navegar **atrás** con `Snackbar`,
sin pantalla de confirmación intermedia (`§2.1` regla 3).

### `C-01` Home calidad — offline OK

Fuente `registro_acopio_cache` + `analisis_calidad_local`. Lista de entregas recientes con su estado de
análisis (analizada / sin analizar) y el acceso a la acción principal (`C-02`). Los cuatro estados de
`§10.6` como siempre.

### `C-04` Detalle de análisis — ONLINE + CACHE

`GET /api/analisis-calidad/registro/{registroAcopioId}`.

- Los 6 parámetros: **los nulos se omiten**, nunca se muestran como `0`.
- `resultado` es un **enum abierto**: `APROBADO`, `RECHAZADO`, `OBSERVADO` y `UNKNOWN` de reserva
  (`§1.6`). `OBSERVADO` existe en el dominio aunque hoy ningún código lo asigne, y **un valor no
  reconocido se muestra tal cual llegó** en vez de romper la pantalla.
- Escalas: los parámetros de laboratorio van con **2 decimales** (`§10.1`).

## 6. UseCases nuevos

| Para | Qué hace | Nota |
|---|---|---|
| Elegibilidad de padres (`C-02`) | Combina `registro_acopio_local` + `registro_acopio_cache` y clasifica cada fila en uno de los 3 casos, ya deduplicada por `DATA-013` | El clasificador va acá, **no** en el `ViewModel`. Es lógica de dominio y `8D` la va a necesitar igual para `P-02` |
| Observar análisis por registro (`C-04`) | Lectura del análisis de una entrega | — |
| Entregas recientes con estado de análisis (`C-01`) | `registro_acopio_cache` + `analisis_calidad_local` | — |

`ObtenerRegistrosDeProveedorUseCase` (refresco on-demand) y `CrearAnalisisCalidadUseCase` ya existen.
**Diseñá el clasificador pensando en `8D`**: `P-02` usa exactamente las mismas tres categorías, con
selección múltiple. Si sale reutilizable, `8D` es media sub-fase menos.

## 7. Testing requerido

Todo en `commonTest`, en JVM sin emulador, con los fixtures de las Fases 5–6
(`FixtureRepositorios.kt`, `FakesDeSync.kt`, `FixtureDeSync.kt`) y los de `8A`/`8B`:

1. **`C-02` clasifica los tres casos**: una fila de cache con `id`, una local con `server_id` y una local
   sin `server_id` caen cada una en su categoría, con el aviso solo en la tercera.
2. **`C-02` avisa al seleccionar, no al guardar** (`DATA-003`): el `UiState` expone el aviso en la fila,
   antes de cualquier evento de navegación a `C-03`.
3. **`C-02` estado vacío del caso no cubierto**: sin filas locales ni cacheadas, el mensaje es el de la
   sección 4, no un error genérico.
4. **`C-02` deduplicación (`DATA-013`)**: una entrega en local con `server_id` y en cache con el mismo
   `id` aparece una sola vez, y sobrevive la local.
5. **`C-02` refresco con conexión**: dispara `ObtenerRegistrosDeProveedorUseCase` una sola vez (no en cada
   recomposición — `PROMPT_FASE_06.md §10` trampa 2) y el `registro_acopio_cache` queda poblado por
   `INSERT OR REPLACE`, sin borrado masivo de filas preexistentes.
6. **`C-03` los 6 opcionales vacíos son válidos**: se guarda con los seis en `null` y `folioMuestra`
   presente; ningún `0` aparece en la fila escrita.
7. **`C-03` `folioMuestra`**: vacío inválido; 40 caracteres válido; 41 inválido.
8. **`C-03` `aguaAnadida`**: sin tocar el switch, la fila se escribe con `false`, no con `null`.
9. **`C-03` no expone `resultado`**: ningún campo del `UiState` de captura contiene un resultado previsto.
10. **`C-03` borrador**: se persiste, sobrevive a la recreación del `ViewModel`, se borra al guardar y no
    cuenta como pendiente.
11. **`DATA-014` de punta a punta** — el test que justifica la sub-fase: crear un `RegistroAcopio` propio,
    crear su `AnalisisCalidad` hijo desde `C-03`, correr un ciclo de sync completo (el padre queda
    `Sincronizado`), y verificar que el `UiState` de la lista expone al hijo como **esperando dependencia
    con el `motivoConocido` no nulo** — nunca como pendiente genérico, nunca como error, y **nunca
    prometiendo que se va a resolver solo**.
12. **La salida del punto 3.1.2**: tras sincronizar el padre y refrescar el historial del proveedor, la
    misma entrega aparece como **ajena resuelta** (caso 1) y un análisis nuevo sobre ella nace **sin**
    pasar por `PENDING_DEPENDENCY`.
13. **`C-04` nulos y enum abierto**: los parámetros nulos se omiten (ni `0` ni `"0.00"`); un `resultado`
    desconocido se expone tal cual llegó, sin romper el estado.
14. **`C-01`**: entregas con y sin análisis se distinguen correctamente.

> ### ⚠️ Turbine: las dos lecciones que ya costaron caro
>
> **De `8A`** — un `ViewModel` que lanza trabajo en `init {}` con `viewModelScope.launch` **no resuelve
> sincrónicamente** bajo `UnconfinedTestDispatcher`. Nunca leas `uiState.value` justo después del
> constructor: esperá el estado con `uiState.test { }` y `awaitItem()`.
>
> **De `8B`** — el patrón `while (condición) estado = awaitItem()` **falla con "Unconsumed events"**
> cuando el flujo tiene pasos async encadenados, que es exactamente el caso del test 11 (ciclo de sync
> completo). Usá **verdad de terreno** — consultá la fila real en SQLite para saber cuándo el paso
> terminó — y cerrá con `cancelAndIgnoreRemainingEvents()`.
>
> Los tests 11 y 12 de esta sub-fase son justo los que combinan ambas trampas. Escribilos con este
> recuadro a la vista.

Reportá el conteo total leyendo `shared/build/test-results/jvmTest/`, no de memoria.

## 8. Decisiones que hay que tomar explícitamente

1. **Cuánto empuja `C-02` hacia el mecanismo 2.** Dado que refrescar el historial del proveedor convierte
   un padre propio ya sincronizado en un padre ajeno resuelto (sección 3.1.2), ¿`C-02` refresca solo al
   abrir, o además ofrece un "actualizar" explícito cuando detecta filas en el caso 3? Lo segundo
   convierte un callejón sin salida en una acción de un toque. Elegí y justificá.
2. **El texto exacto del caso 3.** Tenés que escribirlo vos: el del documento es falso hoy (sección 3).
   Que sea honesto, corto, y accionable — y **transcribilo literal en el checkpoint** para que quede
   revisable.
3. **Si el clasificador de padres queda reutilizable para `P-02`** (`8D`). Si sí, decilo; si no, decí por
   qué y qué cambiaría.
4. **La corrección pendiente de `MOBILE_SCREENS.md §6`**, caso 3 de `C-02`: el texto del documento
   contradice `DATA-014`. Anotala como corrección de documentación, igual que se hizo con la fila `V-01`
   de `§18` en la Fase 7. **No edites `MOBILE_SCREENS.md` por tu cuenta** — es fuente de verdad del
   proyecto; proponé el cambio en el checkpoint.

Si aparece un hallazgo de contrato nuevo, va al checkpoint con el formato de `MOBILE_DATA_MAPPING.md §10`,
numerado a continuación del último.

## 9. Trampas conocidas

| # | Trampa | Por qué importa |
|---|---|---|
| 1 | Implementar literal el texto del caso 3 de `MOBILE_SCREENS.md §6` | Sección 3. Promete algo que `DATA-014` impide hoy. Es la trampa central de esta sub-fase |
| 2 | Reimplementar la resolución de padre en el `ViewModel` | `ResolutorPadreRegistroAcopio` existe desde la Fase 6 y está testeado. La UI clasifica y muestra; no resuelve |
| 3 | Cualquier heurística de matching padre-hijo que no sea `uuidCliente`/`id` exacto | `PROMPT_FASE_06.md §3`: puede fusionar en silencio dos entregas distintas. Peor que el hijo retenido |
| 4 | Avisar de la retención al guardar en vez de al seleccionar | `DATA-003`. El usuario se entera después de llenar seis campos |
| 5 | Pintar `PENDING_DEPENDENCY` en rojo | `§10.5`. Y acá con más razón: no hay nada que el usuario pueda "arreglar" |
| 6 | Descartar el `motivoConocido` y poner un texto genérico | `PROMPT_FASE_06.md §7`. Es la información que costó dos fases documentar |
| 7 | Exigir alguno de los 6 parámetros de laboratorio | Son opcionales por contrato. El lactoscan puede no reportarlos |
| 8 | Enviar `0` en un parámetro que el usuario dejó vacío | `§10.1` regla 3. En un análisis de laboratorio, `0` y "sin dato" son afirmaciones distintas y ambas significan algo |
| 9 | Mostrar un `resultado` previsto en `C-03` | Lo calcula el servidor. Mismo error que el `total` de `V-02` |
| 10 | `resultado` desconocido que rompe la pantalla | `§1.6`: enum abierto con `UNKNOWN`, mostrado tal cual llegó |
| 11 | Borrado masivo de `registro_acopio_cache` al refrescar | `§11.2`: `INSERT OR REPLACE` fila por fila. Borrarla rompe padres ajenos ya resueltos de hijos sin sincronizar |
| 12 | Refrescar el historial del proveedor en cada recomposición | `PROMPT_FASE_06.md §10` trampa 2 |
| 13 | `folioMuestra` sin límite de 40, o con coma en el nombre de un test | Lo primero rompe en el backend; lo segundo rompe `compileTestKotlinIos*` |
| 14 | Leer `uiState.value` tras el constructor, o `while (cond) awaitItem()` en un flujo async encadenado | El recuadro de la sección 7. Ya costó dos ciclos de debugging |

## 10. Criterios de aceptación y checkpoint

Los de `PROMPT_FASE_08.md §6` y §7. Para el checkpoint, además:

- **El texto literal del caso 3** que escribiste (decisión 2), para que se pueda revisar como se revisa
  una decisión de producto, no como un detalle de implementación.
- **Si el clasificador de padres quedó reutilizable para `8D`**.
- **Qué del andamio compartido tocaste** — en particular `S-03` Home, que ya resuelve tres roles.

Detenete ahí y esperá aprobación explícita antes de `8D`.

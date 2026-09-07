# Plan — Fase 8: funcionalidades restantes (26 pantallas, 5 sub-fases)

> Alcance fijado por `MOBILE_DATA_MAPPING.md §13`: **"Funcionalidades restantes, una por una
> (RegistroAcopio, luego AnalisisCalidad/LoteProduccion con la mitigación `PENDING_DEPENDENCY`, luego las
> lecturas online-only), cada una con sus pantallas del inventario"**.
>
> **Este archivo no se ejecuta.** Es el plan de la fase: qué falta, en qué orden y bajo qué reglas
> comunes. Cada sub-fase tiene su propio prompt ejecutable (`PROMPT_FASE_08A.md`, `08B`, …), y cada una
> cierra con el checkpoint de `CLAUDE.md §5` y espera aprobación explícita antes de la siguiente.

## 0. Por qué la Fase 8 se parte en cinco

Quedan **26 pantallas**: 7 de ACOPIADOR (`A-01`..`A-07`), 8 de CALIDAD (`C-01`..`C-08`), 4 de PRODUCCION
(`P-01`..`P-04`), 4 de RECEPCION (`R-01`..`R-04`, más el estado `R-02b`) y 3 comunes (`S-05`, `S-06`,
`S-07`).

`CLAUDE.md §5` fija **una fase por sesión**, y la Fase 7 usó una sesión entera para 7 pantallas — con la
mitad del esfuerzo puesto en estrenar Compose. 26 pantallas en una sesión no es una fase ambiciosa, es una
fase que no termina. El propio roadmap dice **"una por una"**, así que partirla no contradice el plan: lo
cumple.

Cada sub-fase entrega valor por sí sola (un rol completo, operable), y ninguna deja la app en un estado
intermedio que no compile o no se pueda usar.

## 1. Antes de arrancar `8A` — tres pendientes de la Fase 7

Ninguna es opcional, y las tres son baratas comparadas con lo que cuestan si se arrastran.

### 1.1 Confirmar `verificacion-ios.yml` en verde (bloqueante)

`CLAUDE.md §8`: *"Ninguna fase se cierra sin que ese workflow pase."* La Fase 7 se dio por completa sin
ese run confirmado, y es precisamente el run que más importa hasta ahora:

- `linkDebugFrameworkIosSimulatorArm64` es **la primera vez que el framework de iOS incluye Compose**. Que
  `compileKotlinIos*` esté en verde prueba tipos y firmas, no que el framework enlace.
- `iosSimulatorArm64Test` es la primera vez que corre con `ViewModel` reales de `androidx.lifecycle`
  multiplataforma.
- Se retiró el target `iosX64`. Ese cambio hay que verlo pasar por CI una vez.

Si algo de esto falla, se descubre ahora con 7 pantallas encima o dentro de cinco sub-fases con 33. **No
arranques `8A` sin ese run confirmado.**

### 1.2 Corregir `CLAUDE.md` (se lee en toda sesión, y hoy afirma cosas falsas)

`CLAUDE.md` es el contexto permanente que toda sesión lee antes de trabajar. Después de la Fase 7 tiene
cuatro afirmaciones desactualizadas, y cada sesión de la Fase 8 va a arrancar creyéndolas:

| Dónde | Dice hoy | La realidad tras la Fase 7 |
|---|---|---|
| `CLAUDE.md §4` | `compileSdk = 36` | `36` sigue en `androidTargetSdk`, pero `androidCompileSdk = 37` (lo exigen los artefactos Android de Compose 1.12.0) |
| `CLAUDE.md §4` | `androidx.lifecycle 2.11.0` | Ese número es del grupo de Google. El `ViewModel` multiplataforma usa `org.jetbrains.androidx.lifecycle 2.9.6` — otro grupo, otro tren de versiones |
| `CLAUDE.md §4` | (no menciona) | Faltan las 3 dependencias nuevas aprobadas en la Fase 7: `navigation-compose`, `koin-compose`/`koin-compose-viewmodel`, `activity-compose` |
| `CLAUDE.md §8` y `PROMPT_FASE_01.md` | "los cuatro targets" / los 3 targets iOS | `iosX64` se retiró (Compose Multiplatform 1.12.0 no lo publica). Hoy son `androidTarget`, `jvm`, `iosArm64`, `iosSimulatorArm64` |
| `CLAUDE.md §7` | (no tiene fila) | Falta `DATA-015` (`venta_local` sin `total` ni `tipoQuesoNombre`) |

Nota de proceso: `CLAUDE.md` es el archivo de instrucciones fijo del proyecto, así que **la corrección la
aprueba y la aplica una persona, no la sesión que está implementando** — pero tiene que estar hecha antes
de `8A`, no anotada como deuda.

### 1.3 Corregir `MOBILE_SCREENS.md §18`, fila `V-01`

Decisión 7 del checkpoint de la Fase 7: `V-01` es offline sobre `venta_local`. La fila de trazabilidad
debe decir `— · venta_local · offline`, no `GET /api/ventas · venta_local · online+cache`.

## 2. Inventario: qué NO existe todavía

Verificado contra el repo después del commit `feat(fase-7)`. Confirmalo vos mismo al arrancar cada
sub-fase — este inventario envejece.

### 2.1 Repositories que faltan (3, todos online-only sin cola)

| Repository | Pantallas | DTO que ya existe | Nota |
|---|---|---|---|
| `RecepcionPlantaRepository` | `R-01`, `R-02`, `R-02b`, `R-03` | `data/remote/dto/RecepcionPlantaDto.kt` | `PROMPT_FASE_06.md §4.4` lo dejó como *"si se necesita ya"* y no se construyó |
| Pagos (`PagoRepository` o equivalente) | `R-04` | `data/remote/dto/PagoResponse.kt` | Solo lectura. El móvil **no** genera pagos |
| Innovación (alertas + score) | `C-07`, `C-08` | `data/remote/dto/InnovacionDto.kt` | Dos endpoints distintos, un solo agregado |

Los tres siguen el patrón de `CorreccionRegistroRepository`: sin tabla local
(`MOBILE_ARCHITECTURE.md §11.3`), sin `uuidCliente`, sin `PENDING`, sin reintento automático. **No les
agregues cola** — `§18.2`/`§18.3` documentan que el backend no es idempotente para estos todavía.

### 2.2 UseCases que faltan

Los que ya existen y hay que reutilizar: `CrearRegistroAcopioUseCase`, `CrearAnalisisCalidadUseCase`,
`CrearLoteProduccionUseCase`, `ObtenerRegistrosDeProveedorUseCase`, `ObservarHistorialProveedorUseCase`,
`ObservarCatalogosUseCase`, `ObtenerRutaDelDiaUseCase`, `ResolverProveedorPorQrUseCase`,
`ConfirmarComunicadoUseCase`, `AnexarCorreccionUseCase`, `ObservarPendientesUseCase`,
`ReintentarManualUseCase`, `VerificarPendientesUseCase`, `LogoutUseCase`, `BorradorFormularioUseCase`,
`SincronizarAhoraUseCase`, `ObservarConectividadUseCase`.

Lo que hay que agregar, por sub-fase:

| Sub-fase | Falta |
|---|---|
| `8A` | Buscar proveedor por nombre (`A-03`); detalle de registro de acopio (`A-06`); marcar "ya visitado hoy" para `A-01` |
| `8B` | Editar-y-reintentar y **descartar** un pendiente (`S-05`); observar comunicados (`S-06`); datos de sesión y versión para `S-07` |
| `8C` | Elegibilidad de padres para `C-02` (los 3 casos); observar análisis por registro (`C-04`) |
| `8D` | Observar lotes (`P-01`); detalle de lote (`P-04`); total de litros de la selección para `P-02` |
| `8E` | Buscar análisis por folio (`C-05`); alertas (`C-07`); score (`C-08`); las 4 operaciones de recepción; pagos por proveedor (`R-04`) |

`ObservarPendientesUseCase` ya une los 4 `Flow` para `S-05`, pero **editar-y-reintentar y descartar no
existen** — y "descartar" es, según `MOBILE_ARCHITECTURE.md §11.4`, la única forma de que trabajo no
confirmado salga de la base. Trátalo con el cuidado que eso implica (`CLAUDE.md §3.6`).

### 2.3 Capacidades de plataforma que no existen — el riesgo #1 de la fase

| Capacidad | Pantalla | Estado |
|---|---|---|
| Cámara + lectura de QR | `A-02` | **No existe nada.** Ni permiso, ni preview, ni decodificador |
| Ubicación / GPS | `A-04` | **No existe nada.** Ni permiso, ni proveedor de posición |

`CLAUDE.md §3.5` dice que lo único `expect`/`actual` del proyecto es almacenamiento seguro, conectividad,
scheduling de background y **permisos del SO**. Estas dos son exactamente eso y nunca se construyeron.
Ambas necesitan implementación en Android y en iOS, y la de iOS **no se puede probar sin una Mac**
(`CLAUDE.md §8`). Van enteras en `8A`, con su propia sección de decisión de dependencias — ver
`PROMPT_FASE_08A.md §3`.

### 2.4 Lo que ya está resuelto y NO se reconstruye

La Fase 7 dejó el andamio completo. Toda sub-fase lo reutiliza:

- Los 10 componentes de `ui/components/` (falta solo `DialogoConfirmacion`, que entra en `8B` con `S-05`
  y `S-07`).
- `presentation/ErrorUi.kt` (mapeo único `ApiError` → mensaje, `§10.4`) y `presentation/Formateadores.kt`
  (escalas de `§10.1`, fechas de `§10.2`).
- `ui/theme/Theme.kt`, `ui/navigation/{Rutas,NavGraph}.kt`, `ui/App.kt`, `di/PresentationModule.kt`.
- El patrón `UiState`/`Event`/`Effect` tal como quedó en los 7 `ViewModel` de la Fase 7 — **copiá el
  patrón, no lo reinventes por rol**.
- El patrón de borrador de formulario (`borrador_formulario` + `BorradorFormularioUseCase`), que la Fase 7
  construyó pensando en los 4 formularios restantes: `A-04`, `C-03`, `P-03` y `R-01`.
- 8 Repository, ~25 UseCase y las 16 tablas `.sq` de las Fases 4–7.

## 3. Orden de las sub-fases

| Sub-fase | Pantallas | Por qué va acá |
|---|---|---|
| **8A — ACOPIADOR: captura** | `A-01`..`A-06` (6) | El roadmap pone `RegistroAcopio` primero. Es el rol que justifica que toda la app sea offline-first, y trae las dos capacidades de plataforma que faltan. La más pesada de las cinco |
| **8B — Comunes** | `S-05`, `S-06`, `S-07`, `A-07` (4) | Con dos recursos ya capturables (Venta + Acopio), `S-05` tiene algo real que mostrar. `S-07` expone el logout completo que la Fase 6 dejó listo en el dominio y todavía nadie usó. `A-07` va acá porque su efecto visible es una marca en la lista de `S-06` |
| **8C — CALIDAD** | `C-01`..`C-04` (4) | Necesita `A-04` hecho: los padres propios sin sincronizar salen de ahí. Es donde `§18.1` y `DATA-014` se vuelven visibles al usuario, y el motivo por el que la Fase 6 tiene su sección 7 |
| **8D — PRODUCCION** | `P-01`..`P-04` (4) | Mismo patrón que `8C` pero con selección múltiple, relación N:M y la regla de que **un solo padre sin sincronizar retiene el lote entero** |
| **8E — Online-only** | `C-05`, `C-06`, `C-07`, `C-08`, `R-01`, `R-02`(+`R-02b`), `R-03`, `R-04` (8) | La más independiente de las cinco: ninguna es offline-first, ninguna toca el Sync Engine. Trae los 3 Repository nuevos de §2.1. Se puede reordenar si hace falta priorizar RECEPCION |

Total: 6 + 4 + 4 + 4 + 8 = **26**.

## 4. Reglas comunes a las cinco sub-fases

Se leen una vez, acá, y los prompts de cada sub-fase no las repiten:

1. **El contrato de presentación** de `MOBILE_SCREENS.md §3`: tres tipos por pantalla, `Effect` por
   `Channel`/`SharedFlow` nunca como campo del estado, la UI solo emite eventos, el `UiState` lleva el
   `String` ya formateado y no un `BigDecimal` crudo.
2. **Los cuatro estados de `§10.6`** en toda pantalla que muestre datos. Sin los cuatro, la pantalla no
   está terminada.
3. **Formateo (`§10.1`)** desde `BigDecimal`, nunca por `Double`, con la escala exacta por campo. Dos
   trampas específicas de esta fase: **`precioLitro` de `R-04` tiene 3 decimales** y **`zScore` de `C-07`
   tiene 3** — todo el resto tiene 2. Formatearlos con 2 es mostrar un número incorrecto.
4. **Fechas (`§10.2`/`§10.3`)**: `LocalDateTime` tal cual llega, sin convertir. Prohibido comparar, restar
   u ordenar mezclando `fechaHora` (dispositivo) con `creadoEn`/`sincronizadoEn` (servidor) mientras
   `DATA-001` siga abierto.
5. **Errores (`§10.4`)**: un solo mapeo, el de `presentation/ErrorUi.kt`. El `mensaje` del backend se
   muestra **literal** en 400/422.
6. **Indicadores (`§10.5`)**: `PENDING_DEPENDENCY` **nunca** con estética de error. Un registro `SYNCED`
   no lleva badge: el estado normal no se decora.
7. **Sin paginación, sin scroll infinito, sin "cargar más"** en ningún historial (`CLAUDE.md §3.3`).
8. **Un `null` nunca se muestra como `0`.** Aparece en casi todas las pantallas de esta fase:
   `horaEstimada` (`A-01`), los 6 parámetros de laboratorio (`C-03`/`C-04`), `rendimientoPct` (`P-04`),
   `litrosRegistradosAcopio` (`R-02`), `zScore` (`C-07`).
9. **Todo enum lleva `UNKNOWN` de reserva y se muestra tal cual llegó** (`MOBILE_DATA_MAPPING.md §1.6`):
   `resultado` (`C-04`), `tipo`/`severidad` (`C-07`), `estado` (`R-02`).
10. **`commonMain` nunca importa `java.*` ni `javax.*`** (`CLAUDE.md §8`).
11. **Nombres de test sin comas** — ilegales en Kotlin/Native, ya rompió el build en las Fases 2 y 6.
12. **Cambios mínimos y acotados a la sub-fase** (`CLAUDE.md §6`): no refactorices lo que no te toca, no
    "mejores" el código de la Fase 7 sin avisarlo.

## 5. Mitigaciones obligatorias todavía pendientes (`MOBILE_SCREENS.md §11`)

La Fase 7 cubrió `DATA-010` y `DATA-002`. Quedan seis, todas en esta fase:

| Hallazgo | Requisito | Pantalla | Sub-fase | Si no se cumple |
|---|---|---|---|---|
| `DATA-013` | Priorizar la fila local cuando `local.server_id == cache.id` | `A-05`, `C-02`, `P-02` | 8A, 8C, 8D | Registros duplicados visualmente |
| `DATA-008` | Sin botón de cámara para evidencia fotográfica en v1 | `A-04` | 8A | Fotos que nunca se suben y llenan el dispositivo |
| `DATA-003` | Explicar la retención por dependencia **al seleccionar**, no al guardar | `C-02`, `P-02` | 8C, 8D | El usuario cree que su análisis se envió cuando está retenido |
| `DATA-005` | Bloquear confirmación de comunicado sin conexión | `A-07` | 8B | Confirmaciones duplicadas en la auditoría |
| `DATA-004` | Bloquear correcciones sin conexión, con confirmación explícita | `C-06` | 8E | Correcciones duplicadas que corrompen la trazabilidad de litros |
| `DATA-006` | Flujo específico de 409, sin reintento automático | `R-01`/`R-02b` | 8E | Reintentos ciegos contra un endpoint no idempotente |

## 6. Criterios de aceptación (idénticos en las cinco sub-fases)

**Verificable en Windows** — lo único que un checkpoint puede citar como "pasó":

- `./gradlew :shared:assemble :androidApp:assembleDebug` → `BUILD SUCCESSFUL`.
- `./gradlew :shared:jvmTest` → todos verdes, con el conteo leído de
  `shared/build/test-results/jvmTest/` (no de memoria).
- `compileKotlinIos*` / `compileTestKotlinIos*` en verde — tipos y firmas `expect`/`actual`, nada más.
- Sin regresiones: la suite acumulada sigue entera (249 tests al cierre de la Fase 7).

**Requiere CI macOS** (`verificacion-ios.yml`): `linkDebugFrameworkIosSimulatorArm64` e
`iosSimulatorArm64Test`. No los mezcles con lo de arriba en el resumen. `CLAUDE.md §8`: ninguna sub-fase
se cierra sin ese run en verde.

## 7. Checkpoint de cada sub-fase

El formato de `CLAUDE.md §5`, más dos cosas propias de esta fase partida:

- **Qué del andamio compartido tuviste que tocar** (componentes, `ErrorUi`, `Formateadores`, navegación).
  Si una sub-fase modifica algo que las otras cuatro usan, hay que decirlo explícitamente: es la vía más
  probable de regresión entre sub-fases.
- **Qué aprendiste que cambie el prompt de la sub-fase siguiente.** Los prompts de `8B`..`8E` se escriben
  en cada checkpoint, no antes, justamente para incorporar esto.

Detenete al final de cada sub-fase y esperá aprobación explícita.

## 8. Riesgos de la fase

1. **Cámara y QR en iOS sin Mac.** Es la primera capacidad del proyecto que no se puede verificar ni
   siquiera parcialmente en Windows: el decodificador de QR en iOS solo se prueba en un simulador o
   dispositivo real. `8A` tiene que dejarlo compilando y honestamente marcado como no verificado, igual
   que la Fase 3 hizo con el Keychain (`CLAUDE.md §7`).
2. **Volumen.** 26 pantallas es cuatro veces lo que hizo la Fase 7. La disciplina de una sub-fase por
   sesión es lo que evita que la última quede a medias.
3. **Deriva del patrón.** Cinco sesiones distintas escribiendo `ViewModel` es la forma más fácil de
   terminar con cinco dialectos. Por eso §4 regla 1 y §2.4: el patrón de la Fase 7 se copia, no se
   reinterpreta.
4. **`DATA-014` se vuelve visible en `8C`/`8D`.** Un `AnalisisCalidad` cuyo padre se capturó en el mismo
   dispositivo se queda en `PENDING_DEPENDENCY` para siempre hasta que cambie el backend. La UI tiene que
   decirlo con todas las letras (`PROMPT_FASE_06.md §7`), no disfrazarlo de "esperando sincronizar".

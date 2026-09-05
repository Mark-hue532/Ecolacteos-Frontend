# FASE 3 — Secure Storage y autenticación

> **Cómo usar este prompt**: guardalo como `prompts/PROMPT_FASE_03.md` y en Claude Code escribí:
> *"Leé `CLAUDE.md` y ejecutá `prompts/PROMPT_FASE_03.md`."*

---

## Objetivo

Guardar la sesión del usuario de forma segura en cada plataforma, y conectar esa sesión al cliente HTTP
que la Fase 2 dejó esperándola.

Es una fase **corta pero delicada**: poco código, y todo el que hay maneja la credencial del usuario. Vale
más una implementación pequeña y auditable que una completa y opaca.

## Estado al arrancar

- La Fase 2 dejó el `HttpClient` funcionando con una **interfaz `TokenProvider`** y una implementación en
  memoria para tests. Esta fase provee la real. **No rehagas el cliente**: solo enchufá la implementación.
- `ApiConfig`/`Entorno` ya resuelven la URL base.
- `ApiError.NoAutorizado` (401) ya existe desde la Fase 1, con `esTransitorio = false`.
- Los DTOs `LoginRequest` y `LoginResponse` ya existen desde la Fase 2.

## Antes de escribir código: qué leer

1. `CLAUDE.md` completo.
2. `docs/MOBILE_ARCHITECTURE.md`:
   - **§4 completo** — es el documento de esta fase: mecanismo JWT, expiración, refresh, política de
     logout, multiusuario, RNF-12
   - §13 (dónde va `security/` y qué es `expect`/`actual`)
3. `docs/MOBILE_DATA_MAPPING.md`:
   - §5.1 (`LoginRequest` / `LoginResponse`)
   - §9 (dónde aparece 401 y 403)
4. `docs/MOBILE_SCREENS.md`, solo **§4** (pantallas `S-01 Splash`, `S-02 Login`, `S-07 Ajustes`) — para
   entender el comportamiento esperado de la sesión. **No construyas UI en esta fase.**

---

## Alcance: qué SÍ entra

### 1. `SecureTokenStorage` — `expect` en `shared/security/`

Superficie mínima, a propósito. Cuantas menos funciones, más fácil de auditar:

```kotlin
expect class SecureTokenStorage {
    suspend fun guardar(sesion: SesionPersistida)
    suspend fun leer(): SesionPersistida?
    suspend fun borrar()
}
```

`SesionPersistida` guarda: `token`, `rol`, `nombre`, `usuarioId`, y `expiraEnEpochMillis` (calculado como
"ahora + `expiraEnSegundos`" al momento del login).

❌ **La contraseña no se persiste nunca**, ni cifrada, ni "temporalmente". No debe existir ningún campo
donde pueda entrar.

### 2. `actual` Android — Keystore directo

⚠️ **`androidx.security-crypto` (`EncryptedSharedPreferences`) está deprecado por Google.** El
`MOBILE_ARCHITECTURE.md §14` lo menciona porque se escribió antes de esa deprecación; la decisión de fondo
de ese documento sigue vigente y es la que hay que seguir: *"una capa fina propia es más auditable que una
dependencia de terceros para este dato sensible"*.

Implementación: **AES/256/GCM con clave en `AndroidKeyStore`**, guardando el ciphertext en unas
`SharedPreferences` normales.

> La guía de migración que circula hoy recomienda **DataStore + Google Tink**. Es una buena recomendación
> para reemplazar un *almacén de preferencias cifrado completo* — proto schemas, migración, I/O asíncrono.
> Acá guardamos **cinco strings**. Esa maquinaria es desproporcionada y agrega dos dependencias al camino
> de la credencial. Si al implementarlo te convencés de lo contrario, **no lo cambies por tu cuenta**:
> argumentalo en el checkpoint y decidimos.

Detalles que hay que resolver bien (son los que se olvidan):

- **Alias de clave** fijo, generado en el primer uso si no existe.
- **`setUserAuthenticationRequired(false)`.** Si se exigiera autenticación del usuario, el sync en
  background no podría leer el token con la pantalla bloqueada — y eso rompe el caso de uso central de la
  app.
- **El IV de GCM se persiste junto al ciphertext.** GCM necesita un IV único por cifrado; el Keystore lo
  genera y hay que guardarlo para poder descifrar. Es el error clásico de esta implementación.
- **La clave puede invalidarse** (el usuario cambia el bloqueo de pantalla, restaura el dispositivo, etc.).
  Cuando eso pasa, descifrar lanza `KeyPermanentlyInvalidatedException` u otra `GeneralSecurityException`.
  **No debe crashear**: se trata como "no hay sesión", se limpia el almacenamiento y se pide login de
  nuevo. Testealo.
- **Excluir de la copia de seguridad automática** (`android:allowBackup` / `dataExtractionRules`). Una
  clave del Keystore no se restaura en otro dispositivo, así que un ciphertext restaurado sería basura
  indescifrable. Mejor que no viaje.
- StrongBox si el dispositivo lo tiene, con fallback silencioso si no. No todos los equipos de gama baja
  —que son los de esta app— lo soportan.

### 3. `actual` iOS — Keychain

`kSecClassGenericPassword`, con un detalle que importa:

- **`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`.**
  - `AfterFirstUnlock` y no `WhenUnlocked`, porque el sync en background necesita leer el token con el
    dispositivo bloqueado.
  - `ThisDeviceOnly` para que el token no viaje al Keychain de iCloud ni se restaure en otro dispositivo.
- Manejar `errSecItemNotFound` como "no hay sesión", no como error.
- Al guardar, `SecItemUpdate` si ya existe; no acumular entradas duplicadas.

### 4. Extraer `usuarioId` del JWT — necesario, y no es obvio

**`LoginResponse` no incluye `usuarioId`**: devuelve solo `token`, `rol`, `nombre` y `expiraEnSegundos`
(`MOBILE_DATA_MAPPING.md §5.1`). Pero `usuarioId` es un claim del JWT (`MOBILE_ARCHITECTURE.md §4`:
*"Claims: `sub`=email, `rol`, `usuarioId`"*), y **hace falta**: las 4 tablas `*_local` de la Fase 4 llevan
`usuario_id NOT NULL` para la política multiusuario (C-09).

Entonces esta fase debe **decodificar el payload del JWT**:

- Partir el token por `.`, tomar el segundo segmento, decodificar **base64url** (ojo: `-` y `_`, y padding
  opcional — no es base64 estándar), parsear el JSON, leer `usuarioId`.
- ❌ **No verifiques la firma.** El cliente no tiene el secreto HS256 ni debe tenerlo, y no es su trabajo:
  el backend valida en cada request. Implementar verificación acá sería inseguro (obligaría a embeber el
  secreto) e inútil.
- Un token malformado o sin ese claim **no debe crashear**: se trata como sesión inválida.
- Poné un comentario explicando por qué no se verifica la firma. Es lo primero que alguien va a querer
  "arreglar" al leer el código.

### 5. Gestión de sesión

Un `GestorSesion` (o `SesionRepository`) en `shared/domain/` que exponga:

- `iniciarSesion(email, password): ApiResult<Sesion>` — llama a `POST /api/auth/login`, decodifica el
  `usuarioId`, calcula la expiración absoluta, persiste y devuelve la sesión.
- `sesionActual(): Sesion?` — lee del almacenamiento seguro.
- `estaVigente(): Boolean` — compara la expiración contra el reloj del dispositivo.
- `refrescarSiHaceFalta(): ApiResult<Unit>` — si quedan **menos de 30 minutos** de vigencia, llama a
  `POST /api/auth/refresh`. **Un fallo acá no es fatal**: se registra y se sigue. Sin conectividad, el
  usuario puede seguir capturando offline (`MOBILE_ARCHITECTURE.md §4`).
- `cerrarSesion()` — ver §6.
- Un `Flow<Sesion?>` u observable, para que la UI de la Fase 7 reaccione a la expiración sin sondear.

**Un token ya expirado no se puede refrescar** — `/api/auth/refresh` exige el JWT todavía vigente. Si
expiró, el único camino es login nuevo. No intentes refrescar un token vencido.

### 6. Logout — parcial en esta fase

`MOBILE_ARCHITECTURE.md §4` define que el logout se **bloquea** si hay registros locales sin sincronizar
(C-09). Pero las tablas locales son de la Fase 4 y todavía no existen.

**Resolvelo con el mismo patrón que la Fase 2 usó para `TokenProvider`**: definí la interfaz

```kotlin
interface VerificadorPendientes {
    suspend fun hayTrabajoSinSincronizar(): Boolean
}
```

con una implementación por defecto que devuelve `false`, y hacé que `cerrarSesion()` la consulte. La Fase 6
provee la implementación real sobre SQLite. Así la política queda escrita ahora y se activa sola cuando
haya con qué.

Lo que **sí** hace esta fase al cerrar sesión: borrar `SecureTokenStorage`. Nada más — no hay caches que
limpiar todavía.

### 7. Conectar con el cliente HTTP

- Implementar `TokenProvider` sobre `GestorSesion` y registrarla en Koin, reemplazando la de memoria.
- El header va en **todos** los requests **menos** `POST /api/auth/login`. Verificá que la excepción esté
  bien puesta.
- **Al recibir un 401**: limpiar la sesión y emitir el evento correspondiente para que la UI mande a login.
  ❌ **No reintentar automáticamente**, ❌ **no intentar refrescar** dentro del interceptor del 401 (el
  refresh es proactivo, al abrir la app). Un 401 significa que la sesión ya no sirve — puede ser expiración
  o que ADMIN desactivó al usuario, que el backend revalida en **cada** request
  (`MOBILE_ARCHITECTURE.md §4`).

### 8. Tests

**En `commonTest`** (con un `SecureTokenStorage` fake, corren en JVM y en iOS):

- Login exitoso persiste la sesión con la expiración absoluta bien calculada.
- Decodificación de JWT: extrae `usuarioId` de un token real de ejemplo; un token malformado, con dos
  segmentos, o sin el claim, devuelve `null` sin lanzar.
- Base64url con `-`/`_` y sin padding decodifica bien (es el caso que rompe si usás base64 estándar).
- Un token expirado **no** se intenta refrescar.
- Con más de 30 minutos de vigencia no se refresca; con menos, sí.
- Un fallo de red en el refresh **no** invalida la sesión existente.
- 401 limpia la sesión.
- `cerrarSesion()` con `VerificadorPendientes` devolviendo `true` **no** borra el token.
- La contraseña no aparece en nada de lo que se persiste (testeá el objeto serializado).

**Los `actual` reales necesitan otro tipo de test**, y acá hay una asimetría de plataforma:

- **iOS**: el simulador tiene Keychain funcional, así que el `actual` **sí se puede testear en
  `iosSimulatorArm64Test`**, en el CI de macOS. Hacelo: guardar, leer, borrar, y leer después de borrar.
- **Android**: el Keystore necesita un test instrumentado con emulador o dispositivo, que el CI actual no
  tiene. Escribí el test en `androidInstrumentedTest` igual, documentá cómo correrlo a mano, y **decí en el
  checkpoint que quedó sin ejecutar en CI**. No lo declares verificado si no corrió.

---

## Fuera de alcance

- ❌ SQLite y la implementación real de `VerificadorPendientes` → **Fases 4 y 6**
- ❌ Limpieza de caches con datos personales en el logout (RNF-12) → **Fase 6**, cuando existan las tablas
- ❌ `ConnectivityObserver` → **Fase 5**. Acá el refresh simplemente intenta y maneja el fallo.
- ❌ Pantallas de login, splash o ajustes → **Fase 7**
- ❌ Biometría. No está en ningún documento; no la agregues.

---

## Criterios de aceptación

1. `./gradlew :shared:jvmTest` pasa.
2. El CI de iOS pasa, **incluyendo el test real de Keychain** en `iosSimulatorArm64Test`.
3. Cero `Double`/`Float` en `shared/`, cero `java.*`/`javax.*` en `commonMain` (los usos de Keystore van en
   `androidMain`, que es donde corresponde).
4. Ningún log, ni siquiera en debug, imprime el token, la contraseña o el header `Authorization`.
   Verificalo con un grep sobre las sentencias de log.
5. La contraseña no se persiste en ningún lado.
6. El `TokenProvider` real está registrado en Koin y el de memoria quedó solo para tests.
7. El test del grafo de Koin sigue pasando con las dependencias nuevas.

---

## Checkpoint de cierre

El del §5 de `CLAUDE.md`, más:

- **Qué implementación elegiste en Android** y por qué, con el manejo de invalidación de clave explicado.
- **Si el test instrumentado de Android corrió o no.** Si no corrió, decilo — es un criterio parcialmente
  no verificado, igual que el APK de la Fase 1.
- Cómo quedó la decodificación del JWT y qué pasa con un token malformado.
- Confirmación de que `MOBILE_ARCHITECTURE.md §14` menciona `EncryptedSharedPreferences`, que está
  deprecado, y **qué habría que corregir en ese documento** — lo actualizamos aparte, pero quiero el texto
  propuesto.
- Qué queda pendiente para que el logout completo funcione (la parte que depende de la Fase 4).

**Después del checkpoint, esperá aprobación antes de la Fase 4.**

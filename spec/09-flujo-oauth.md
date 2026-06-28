# Spec — Capa 9: Flujo OAuth y manejo de tokens (por red)

> Estado: **EN REVISIÓN**.
> Depende de: [[02-modelo-de-datos]] (social_accounts, account_credentials), [[03-contratos-api]]
> (connect/callback/refresh), [[01-definiciones-alto-nivel]] (onboarding por OAuth, nunca contraseñas).
> Datos de tokens verificados a jun-2026 (estas APIs cambian: revalidar en sandbox).

## Principios

- **Nunca contraseñas.** El cliente autoriza en la pantalla oficial de Meta/TikTok; la app recibe un código.
- **Todo el canje ocurre server-side.** El `app secret` / `client_secret` nunca va al front ni al binario.
- **`state` de un solo uso (anti-CSRF).** Mapea el callback a `client_id` + `platform` + usuario iniciador.
- **Tokens cifrados** en `account_credentials` (bytea). Nunca se serializan al front. [[02-modelo-de-datos]]
- **Refresh automático y proactivo** desde el job (campos `expires_at`, `last_refreshed_at`).

## Flujo común (mapea a [[03-contratos-api]])

1. `POST /clients/{clientId}/accounts/connect/{platform}` (opc `?accountId=` para **reconexión**) →
   backend arma la `authorizationUrl` oficial con los scopes y un `state` único (JWT firmado, TTL corto).
   En reconexión, el `state` lleva además el `external_account_id` **esperado** de esa cuenta. Devuelve
   `{authorizationUrl, state}`.
2. El cliente autoriza en la pantalla oficial (lo acompaña admin/empleado).
3. La red redirige a `GET /oauth/callback/{platform}?code=&state=`.
4. Backend valida `state` (firma, no usado, no expirado), **canjea el `code` server-side** por el
   token, detecta `account_type`/`capabilities`, crea `social_accounts` + `account_credentials`
   (cifrado) y redirige al front (`?accountId=` o `?error=`).
5. `state` se marca usado. Errores → no se crea cuenta. Tras un connect **exitoso**, el backend dispara
   un **escaneo inmediato SOLO de esa cuenta** (best-effort, asíncrono; ver [[10-job-diario]]).

> **Reconexión segura (no enganchar la cuenta equivocada).** Bug observado: el navegador queda logueado
> con la última cuenta de la red; al reconectar otra, el callback recibe el `open_id` de la **sesión
> activa** y linkearía la cuenta equivocada. Fix: en reconexión el connect recibe `accountId` y embebe el
> `external_account_id` esperado en el `state` firmado. Si el `open_id` devuelto **no coincide** → el
> callback rechaza con **`409`** ("Autorizaste con otra cuenta; cerrá sesión en la red e intentá de
> nuevo"), **sin linkear ni duplicar**. En connect nuevo (sin `accountId`) no hay esperado: no se puede
> validar. [[04-criterios-aceptacion]]

> **Multi-cuenta por red.** Un cliente puede conectar **varias cuentas de la misma red**. Hay dos
> caminos que producen multi-cuenta: (a) **un solo consentimiento de Meta** devuelve varias Páginas /
> IG vinculados (ver `GET /me/accounts`) → el front muestra un **paso de selección** para elegir
> cuáles dar de alta; se crea una fila en `social_accounts` por cada cuenta elegida, todas compartiendo
> el mismo token de usuario / Page tokens derivados; (b) el usuario **repite el flujo "Conectar"**
> para esa red con otra autorización (otra cuenta/login). En un connect **nuevo** el `state` mapea a
> `client_id` + `platform` (no a una cuenta puntual), así que admite ambos caminos sin cambios; en una
> **reconexión** (`accountId` presente) el `state` además fija la cuenta esperada (ver "Reconexión
> segura" arriba).

## Tiempos de vida de tokens

| Red / flujo | Token corto | Token largo | Refresh |
|---|---|---|---|
| Instagram (Instagram Login) | IG user token ~1 h | **long-lived 60 días** | `ig_refresh_token` (extiende 60 d; token debe tener ≥24 h y <60 d) |
| Instagram/Facebook (Facebook Login) | FB user token ~1-2 h | **long-lived 60 días** | re-exchange `fb_exchange_token`; Page tokens derivados son de larga duración |
| TikTok | access token **24 h** (`expires_in` 86400) | refresh token **365 días** (`refresh_expires_in` 31536000) | `grant_type=refresh_token` (sin consentimiento del usuario) |

---

## Instagram + Facebook (Meta)

Dos caminos según lo que tenga el cliente. **Recomendado: Facebook Login for Business** cuando el
cliente tiene Página de FB + IG profesional vinculada (un solo consentimiento cubre ambas). Usar
**Instagram Login** para clientes IG-only sin Página.

### Camino A — Facebook Login for Business (FB Pages e IG vía Page)
1. `authorizationUrl` a `facebook.com/.../dialog/oauth` con scopes:
   `pages_show_list`, `pages_read_engagement`, `read_insights`, `instagram_basic`,
   `instagram_manage_insights`, `business_management`. [[05-investigacion-metricas-apis]]
2. Callback con `code` → canjear por **user token corto** en `GET /oauth/access_token`.
3. Canjear corto → **long-lived user token (60 d)**: `GET /oauth/access_token?grant_type=fb_exchange_token&client_id=&client_secret=&fb_exchange_token=`.
4. `GET /me/accounts` (con el long-lived) → **Page access tokens** (larga duración) + IG Business
   account id vinculado a cada Página.
5. Guardar por cuenta: Page token (FB) y/o el acceso al IG user id (IG).
- **Refresh:** re-exchange del user token antes de 60 d; los Page tokens derivados se renuevan con él.

### Camino B — Instagram Login (Business Login for Instagram)
1. `authorizationUrl` de Instagram con scopes `instagram_business_basic`,
   `instagram_business_manage_insights`.
2. Callback con `code` → **IG user token corto (~1 h)** en `POST graph.instagram.com/oauth/access_token` (`grant_type=authorization_code`).
3. Canjear corto → **long-lived (60 d)**: `GET graph.instagram.com/access_token?grant_type=ig_exchange_token&client_secret=&access_token=`.
4. **Refresh:** `GET graph.instagram.com/refresh_access_token?grant_type=ig_refresh_token&access_token=`
   — el token debe tener **≥24 h** y **<60 d**; cada refresh extiende 60 d. Si pasan 60 d sin
   refrescar → expira → re-onboarding.

> App Review con **Advanced Access + Business Verification** es obligatorio para operar cuentas de
> terceros. En Development Mode solo cuentas propias de prueba. [[05-investigacion-metricas-apis]]

---

## TikTok

1. `authorizationUrl` a `tiktok.com/v2/auth/authorize/` con scopes `user.info.basic`,
   `user.info.profile`, `user.info.stats`, `video.list`, un `state`, **`code_challenge` +
   `code_challenge_method=S256` (PKCE, OBLIGATORIO)** y (en reconexión) `disable_auto_auth=1`.
2. Callback con `code` → `POST https://open.tiktokapis.com/v2/oauth/token/`
   (`grant_type=authorization_code`, `client_key`, `client_secret`, `code`, `redirect_uri`,
   **`code_verifier`**).

> **PKCE (OBLIGATORIO en TikTok) — gap detectado jun-2026.** La app de TikTok rechaza la autorización
> con `param_error / errCode 10007 / error_type=code_challenge` si falta PKCE. Flujo: al armar la
> `authorizationUrl` se genera un `code_verifier` aleatorio (43–128 chars `[A-Za-z0-9-._~]`), se manda
> `code_challenge = BASE64URL(SHA256(code_verifier))` + `code_challenge_method=S256`, y el `code_verifier`
> se **guarda server-side asociado al `state`** (nonce). En el canje (`exchangeCode`) se envía el
> `code_verifier`. El verifier NUNCA viaja en la `authorizationUrl` ni en el `state` (solo el challenge).
> Implica: store en memoria `nonce→verifier` (TTL corto, patrón del `OAuthStateService`) y pasar el
> `state`/verifier al `exchangeCode`. Aplica a TikTok; Meta no usa este PKCE.

> **Config requerida.** `TIKTOK_CLIENT_KEY` y `TIKTOK_CLIENT_SECRET` deben estar seteadas en el entorno
> del backend (en `application.yml` son `${TIKTOK_CLIENT_KEY:}` / `${TIKTOK_CLIENT_SECRET:}`, default
> vacío). Con `client_key` vacío la `authorizationUrl` sale inválida.
3. Respuesta: `access_token` (**24 h**), `refresh_token` (**365 d**), `open_id`, `scope`.
4. **Refresh:** `POST /v2/oauth/token/` con `grant_type=refresh_token` → nuevo access token (y
   posible nuevo refresh token → **rotarlo** y guardarlo). Sin consentimiento del usuario.

> **`disable_auto_auth` (anti auto-grant de la sesión activa).** Sin este parámetro TikTok
> **auto-autoriza en silencio** la cuenta logueada en el navegador y salta el consentimiento: en una
> **reconexión** esto devuelve el `open_id` de la cuenta equivocada y dispara el `409`. Por eso la
> `authorizationUrl` de TikTok lleva **`disable_auto_auth=1`** cuando hay cuenta esperada
> (reconexión), forzando que TikTok muestre la pantalla para poder cambiar/loguear la cuenta correcta.
> Configurable vía `oauth.tiktok.disable-auto-auth` para forzarlo en **cualquier** connect (útil en
> **dev**, donde se prueban varias cuentas desde un mismo navegador). Doc oficial: *"When set to 1,
> always displays the authorization page."* Es un paliativo de UX/dev: **no** reemplaza al link
> compartible (abajo), que es la solución de producto. Verificado jun-2026 ([Login Kit for Web](https://developers.tiktok.com/doc/login-kit-web/)).

> App client **auditado** para producción (sandbox fuerza contenido privado). [[05-investigacion-metricas-apis]]

---

## Link compartible de conexión (onboarding self-service del cliente)

> **Problema que resuelve.** Cuando la **agencia** autoriza desde su propio navegador, la red usa la
> **sesión activa** (la última cuenta logueada), no la que se quiere conectar → hay que cerrar sesión
> en la red cada vez. **No existe forma de forzar una cuenta puntual desde el backend** (OAuth autoriza
> la sesión activa del proveedor; TikTok web no tiene `login_hint`/`prompt=select_account`). La solución
> de la industria (Metricool, etc., verificado jun-2026) **no** fuerza la cuenta: cambia **quién**
> autoriza. El **dueño de la cuenta** abre un link en **su** navegador (ya logueado con su cuenta) y
> autoriza. La agencia nunca toca el diálogo OAuth ni cierra sesión de nada. Alinea con
> [[01-definiciones-alto-nivel]] (el cliente autoriza la app; nunca contraseñas).

**Concepto.** La agencia genera un **link temporal** ligado a un cliente (y opcionalmente a una red y/o
a una cuenta puntual a reconectar). Se lo pasa al cliente; el cliente lo abre **sin login en Fil-Grama**,
elige la red y autoriza en la pantalla oficial de Meta/TikTok desde su sesión. El **callback es el mismo**
de siempre: el `state` ya lleva `client_id` + `platform` (+ `external_account_id` esperado si es reconexión).

**Flujo**
1. Agencia (autenticada): `POST /clients/{clientId}/connect-links` `{platform?, accountId?}` → backend
   crea una fila en `connect_links` (token de alta entropía, TTL por defecto **72 h**) y devuelve
   `{token, url, expiresAt}`. `url` = página pública del front (`/connect/{token}`).
2. El cliente abre `url` en su navegador. El front llama `GET /public/connect-links/{token}` →
   `{clientName, platform?, expiresAt}` y muestra "Conectá la red de <cliente>".
3. El cliente toca "Conectar <red>" → `POST /public/connect-links/{token}/connect/{platform}` (público) →
   backend valida el token (existe, vigente, no revocado, red permitida), emite el `state` firmado (con
   `client_id`/`platform` del link y `external_account_id` esperado si aplica; `connected_by = created_by`
   del link) y devuelve `{authorizationUrl}`. El front redirige.
4. El cliente autoriza en la pantalla oficial **desde su propia sesión**.
5. Callback estándar `GET /oauth/callback/{platform}` → canjea, crea/actualiza `social_accounts` +
   `account_credentials` ligadas al `client_id` del link, y **devuelve al cliente a la lista**
   (`/connect/{token}`) para que pueda **conectar otra cuenta** — NO a un callejón sin salida (ver
   "Onboarding multi-cuenta" abajo). El `state` marca `origin=link`; en error vuelve con `?error=`.

**Reglas**
- **Multi-uso hasta expirar/revocar** (como Metricool): un mismo link puede conectar varias redes
  mientras esté vigente. `used_at` registra el último uso (auditoría); **no** es de un solo uso.
- **Vigencia:** `expires_at` (default 72 h) y revocación manual (`revoked_at`). Vencido/revocado → los
  endpoints públicos responden `410`; token inexistente → `404`.
- **Seguridad:** el token va **hasheado** en DB (como un token de reset; el raw se devuelve **una sola
  vez** al crearlo y nunca se loguea). Endpoints `/public/**` son `permitAll` pero **acotados al
  `client_id` del token** (no enumeran otros clientes) y **rate-limited**.
- **Reconexión vía link:** si el link lleva `expected_account_id`, el callback aplica el **mismo guard
  `409`** (open_id esperado); en la práctica no se dispara porque el cliente entra con su cuenta.
- **Atribución:** `connected_by = created_by` (el empleado/admin que generó el link).

Contrato en [[03-contratos-api]] · tabla `connect_links` en [[02-modelo-de-datos]] · criterios en
[[04-criterios-aceptacion]] (CU9).

### Onboarding multi-cuenta (lista abierta + "conectar otra")

> **Problema.** Un cliente puede querer conectar **varias cuentas** —incluso de la misma red (2 TikTok,
> 3 Facebook, 2 Instagram)—. Un "checklist por red" (un ✓ por red) sería falso: no sabemos cuántas
> cuentas quiere. Y si el callback termina en un "¡Listo!" sin salida, el cliente cree que terminó tras
> la primera y abandona el resto.

**Modelo: lista abierta, no checklist con metas.** La página pública `/connect/{token}` es una **lista de
cuentas conectadas + "conectar otra" + "terminé"**:
- **"Cuentas conectadas hasta ahora"**: las cuentas `CONNECTED` del cliente (handle + red + ✓). El
  `GET /public/connect-links/{token}` devuelve esa lista — **mínimo** (handle + platform; **sin** métricas
  ni tokens; acotada al `client_id` del token).
- **"Conectar otra cuenta"**: botones por red (las habilitadas por el link). El cliente conecta **las que
  quiera, de cualquier red y cantidad**.
- Tras cada conexión, el callback **vuelve a esta lista** (no a un "listo" muerto): la cuenta nueva aparece
  y el cliente sigue o cierra con **"Terminé"**.

**Multi-cuenta de la misma red.** Conectar una **segunda** cuenta de la misma red choca con OAuth (autoriza
la sesión activa): el cliente debe **cambiar de cuenta en la pantalla de esa red** (Meta/TikTok tienen
selector). No se automatiza → se muestra un **aviso** ("para conectar otra cuenta de la misma red, cambiá
de cuenta en la pantalla de la red"). Es **idempotente**: reautorizar la misma cuenta hace upsert por
`UNIQUE(platform, external_account_id)`, no duplica.

**Token client-side, no en el `state`.** Para volver a `/connect/{token}` tras el callback sin filtrar el
token por la URL de TikTok, el front guarda el token en `sessionStorage` antes de redirigir al OAuth y el
callback vuelve a una ruta pública que retoma la lista con ese token. El token **no** viaja en el `state`.

**Tipos de link + QR.** Link **genérico** (multi-red, el cliente elige) → checklist abierto + QR **azul
Fil-Grama**. Link de **una red** (la agencia la elige al generar) → esa red + QR **del color de la red**
(§QR). Conviven. Criterios en [[04-criterios-aceptacion]] (CU9).

### QR del link de conexión (presentación; frontend)

> El connect-link ya es una URL; el QR sólo la **encodea en imagen** para compartir (WhatsApp,
> presencial). **Complementa** al link, no lo reemplaza. **Frontend-only** (librería `qr-code-styling`,
> client-side, MIT); no toca backend/API/datos. Para WhatsApp el link de texto suele ganarle al QR (el
> cliente recibe y toca en su teléfono); el QR brilla en presencial / dos pantallas.

**Estilo: auto-detección por red + override azul (DECIDIDO).**
- Connect-link **con `platform`** (link por red / reconexión) → QR con el **estilo de esa red**.
- Connect-link **sin `platform`** (multi-red, el cliente elige) → QR **azul Fil-Grama** (neutro,
  `--fg-primary` = `#1E66BC`).
- **Override manual:** el operador puede forzar "azul Fil-Grama" en cualquier caso.
- **Criterio:** auto-por-red es el **default** —la red casi siempre se conoce y el color/ícono ayuda al
  cliente a reconocer qué va a conectar—; el azul cubre el multi-red y la consistencia de marca cuando
  se prefiera.

**Mapa de estilos** (módulos · ojos · ícono central):

| Red | Módulos | Ojos | Ícono | Nota |
|---|---|---|---|---|
| Fil-Grama (neutro / multi-red) | azul `--fg-primary` `#1E66BC` | azul oscuro `#0F3F78` | **isotipo** `brand/isotipo.svg` | default cuando no hay red |
| Instagram | degradado rosa→naranja→violeta | ídem | cámara | |
| TikTok | casi negro `#16181F` | cian `#25F4EE` + magenta `#FE2C55` (desfasados) | nota ♪ | |
| Facebook | azul FB `#1877F2` | azul FB | glyph "f" | **FB azul ≈ azul Fil-Grama → el ícono "f" del centro es lo que desambigua** |

**QR por red (aprobado).** Si un cliente tiene varias redes, se genera **un QR/tarjeta por red** (cada
uno con el connect-link de esa red y su estilo), en vez de un único link multi-red ambiguo.

**Tarjeta para compartir (frame).** Encabezado "Conectá las redes de {Cliente}", QR al centro, pie con
**aviso de vencimiento** ("vence en 72 h") + marca Fil-Grama, y el **link en texto debajo** (fallback si
la cámara no engancha).

**Acciones en el modal:** Descargar QR (PNG/SVG) · Copiar imagen (para pegar en WhatsApp) · Copiar link.

**Legibilidad (no negociable).** `errorCorrectionLevel='H'` siempre que haya ícono/logo; ícono ≤ ~22 %
del área; quiet zone presente; alto contraste módulos/fondo; las 3 esquinas (finder) y el margen deben
sobrevivir a cualquier estilo. **Generación 100 % local: el token del link es una credencial — NUNCA
mandarlo a un servicio externo de QR.** El **isotipo** del centro (QR neutro) es del mismo azul que los
módulos → va con un **knockout blanco** (halo redondeado detrás) para que no se funda con el patrón.

**Diferido:** frames tipo póster ("SCAN ME"), branding por cliente (logo del cliente al centro; por
ahora marca de agencia). Criterios en [[04-criterios-aceptacion]] (CU9).

---

## Ciclo de vida de la cuenta (estados, reconexión y baja)

> **Por qué importa.** "Desconectar" y "reconectar" **no** son una sola cosa. El mismo botón mezcla
> casos muy distintos (token vivo vs muerto vs cuenta dada de baja). Pinchar siempre OAuth es el origen
> del `409` innecesario. Acá se fija el ciclo de vida completo para no improvisar en código.

### Estados (`social_accounts.status`)

| Estado | Significado | Credencial | ¿La sincroniza el job? |
|---|---|---|---|
| `CONNECTED` | Activa, token vivo | presente y válida | **Sí** (el job procesa solo `CONNECTED`) |
| `DISCONNECTED` | **Pausada a propósito** por la agencia | **se conserva** (token sigue ahí) | No |
| `ERROR` | Token **murió** (cliente revocó, cambió contraseña, refresh vencido) | presente pero inválida | No |
| `UNSUPPORTED` | IG/FB personal (sin insights) | sin credencial | No |
| `REMOVED` | **Dada de baja** (offboard); credencial **borrada/revocada** | **eliminada** | No |

> El job ya filtra `findByStatus(CONNECTED)`: todos los demás estados quedan fuera del sync **sin
> perder su historia** (snapshots/posts son append-only y se conservan para reportes pasados).

### Operaciones y transiciones

- **Conectar (nuevo):** OAuth ok → `CONNECTED`. Upsert por `UNIQUE(platform, external_account_id)`:
  re-conectar una cuenta ya existente (incluida una `REMOVED`) **reusa la misma fila** y crea credencial nueva.
- **Desconectar (pausar):** `CONNECTED → DISCONNECTED`. **No** borra ni revoca el token; solo frena el sync.
- **Reconectar inteligente** (`POST /accounts/{id}/reconnect`): decide solo según el estado/credencial:
  1. Hay credencial y el `refreshToken()` **funciona** → **reactivar**: `→ CONNECTED`, **sin OAuth, sin
     sesión del navegador, sin molestar al cliente**. (Cubre la pausa con token vivo.)
  2. El refresh **falla** (token revocado/expirado) o no hay credencial → marca `ERROR` y responde
     **`requiresReauth`**: la cuenta necesita re-autorización fresca (abajo).
- **Re-autorización** (cuando hace falta OAuth nuevo): dos vías que elige la agencia:
  - **B1 — la agencia la hace:** está logueada con esa cuenta → connect `?accountId=` (+ `disable_auto_auth`
    en TikTok). El guard `409` protege si autoriza otra cuenta.
  - **B2 — la hace el cliente:** la agencia **no** está logueada con esa cuenta → genera un
    **connect-link** (ver arriba) y se lo manda; el cliente re-habilita desde su navegador.
- **Eliminar / dar de baja** (`DELETE /accounts/{id}`, **solo `[ADMIN]`**; empleado → `403`): `* → REMOVED`. **Revoca el token en la red**
  (best-effort) + **borra la credencial**; **conserva** la fila y la historia (snapshots/posts) para
  reportes pasados. Invalida cualquier `connect_links` pendiente atado a esa cuenta. Re-agregarla luego
  = connect nuevo (reusa la fila vía upsert).

> **Revocar requiere un método nuevo en `OAuthProvider`** (`revokeToken`, best-effort): TikTok
> `POST /v2/oauth/revoke/`; Meta `DELETE /{user-id}/permissions`. Si la revocación remota falla, igual
> se borra la credencial local (no bloquea la baja). El `MockOAuthProvider` lo no-opea en dev/test.

### Matriz de escenarios (preparación)

| Escenario | Estado origen | Manejo |
|---|---|---|
| Agencia pausa una cuenta | `CONNECTED → DISCONNECTED` | guarda token; el job la saltea |
| Reconectar pausada, **token vivo** | `DISCONNECTED` | reconnect: refresh → `CONNECTED` (sin cliente, sin OAuth) |
| Reconectar pausada, **token murió** mientras pausada | `DISCONNECTED → ERROR` | reconnect detecta la falla → `requiresReauth` |
| Cliente revocó acceso / cambió contraseña | `CONNECTED → ERROR` (en sync/refresh) | re-auth: B1 (agencia) o B2 (link) |
| Refresh token vencido (TikTok 365 d / IG 60 d) | `→ ERROR` | re-auth |
| Re-auth con la agencia logueada en esa cuenta | `ERROR`/`DISCONNECTED` | connect `?accountId=` (+ `disable_auto_auth`) |
| Re-auth que hace el cliente | `ERROR`/`DISCONNECTED` | **connect-link** |
| En la re-auth el cliente autoriza **otra** cuenta | — | **guard `409`**, no linkea ni duplica |
| Fin de contrato / dar de baja | `* → REMOVED` | delete: revoca + borra credencial, **conserva historia** |
| Re-agregar una cuenta dada de baja | `REMOVED → CONNECTED` | connect nuevo (upsert misma fila), credencial nueva |
| Cuenta personal IG/FB | `→ UNSUPPORTED` | sin credencial; pasar a profesional y reconectar |
| Cuenta cambia personal↔business | re-detección en sync | ajusta métricas desde ese día, sin backfill (CU7) |
| Multi-cuenta: baja/pausa de una | — | no afecta las otras cuentas de la misma red |
| Link expira sin que el cliente lo use | — | regenerar link |

Contrato de `reconnect`/`disconnect`/`delete` en [[03-contratos-api]] · estados en [[02-modelo-de-datos]]
· criterios en [[04-criterios-aceptacion]] (CU10).

---

## Estrategia de refresh (en el job diario)

- Antes de consultar insights de cada cuenta, el job revisa `expires_at`:
  - **TikTok:** el access token dura 24 h → en la práctica se **refresca en cada corrida** con el
    refresh token; si la API devuelve nuevo refresh token, **rotarlo**. Refresh token expira a 365 d
    → si el cliente no tuvo actividad en ~1 año, re-onboarding.
  - **Instagram (IG Login):** refrescar cuando el token tenga ≥24 h y falten pocos días para los 60;
    en la práctica un refresh periódico (ej. semanal o cuando `expires_at` < 7 días) mantiene vivo el 60-d.
  - **Meta (FB Login):** re-exchange del user token antes de los 60 d; renueva los Page tokens.
- Actualizar `last_refreshed_at` y `expires_at` tras cada refresh. Todo cifrado.

## Casos de borde

- **Token revocado por el cliente / cambió contraseña:** la API responde error de auth → marcar la
  cuenta `ERROR` (o `DISCONNECTED`) y avisar para re-onboardear. El job sigue con las demás cuentas (`PARTIAL`). [[04-criterios-aceptacion]]
- **Cuenta personal de IG/FB:** detectada en el callback → `UNSUPPORTED`, sin crear credencial. [[02-modelo-de-datos]]
- **`state` inválido/expirado/reusado:** callback responde error, no crea nada. [[04-criterios-aceptacion]] (CU1)
- **Refresh token TikTok vencido (365 d) / IG no refrescado en 60 d:** expira → requiere re-onboarding del cliente.

## Decisiones abiertas

- ~~¿`state` en tabla dedicada (con TTL) o JWT firmado de un solo uso?~~ → **RESUELTO:** JWT firmado
  (HMAC-SHA256) de un solo uso; nonce/jti consumido en memoria + TTL corto. En reconexión lleva el
  `external_account_id` esperado como claim.
- Política exacta de cuándo refrescar IG (umbral de días) — afinar en implementación.
- ~~Manejo de un cliente con varias Páginas/cuentas bajo un mismo consentimiento de Meta~~ →
  **RESUELTO:** multi-cuenta por red soportado; paso de selección cuando el consentimiento devuelve
  varias cuentas. Ver "Multi-cuenta por red" arriba.
- ~~Reconectar/conectar una cuenta sin tener que cerrar sesión en la red en el navegador de la agencia~~ →
  **RESUELTO (jun-2026):** dos medidas complementarias — (a) producto: **link compartible de conexión**
  (el cliente conecta desde su propio navegador; ver sección arriba); (b) dev/UX: `disable_auto_auth=1`
  en TikTok para forzar la pantalla y evitar el auto-grant silencioso. Premisa descartada: **no** se
  puede "forzar la cuenta desde el backend" (OAuth autoriza la sesión activa del proveedor).

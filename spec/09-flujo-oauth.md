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

1. `POST /clients/{clientId}/accounts/connect/{platform}` → backend arma la `authorizationUrl`
   oficial con los scopes y un `state` único (persistido, TTL corto). Devuelve `{authorizationUrl, state}`.
2. El cliente autoriza en la pantalla oficial (lo acompaña admin/empleado).
3. La red redirige a `GET /oauth/callback/{platform}?code=&state=`.
4. Backend valida `state` (existe, no usado, no expirado), **canjea el `code` server-side** por el
   token, detecta `account_type`/`capabilities`, crea `social_accounts` + `account_credentials`
   (cifrado) y redirige al front (`?accountId=` o `?error=`).
5. `state` se marca usado. Errores → no se crea cuenta.

> **Multi-cuenta por red.** Un cliente puede conectar **varias cuentas de la misma red**. Hay dos
> caminos que producen multi-cuenta: (a) **un solo consentimiento de Meta** devuelve varias Páginas /
> IG vinculados (ver `GET /me/accounts`) → el front muestra un **paso de selección** para elegir
> cuáles dar de alta; se crea una fila en `social_accounts` por cada cuenta elegida, todas compartiendo
> el mismo token de usuario / Page tokens derivados; (b) el usuario **repite el flujo "Conectar"**
> para esa red con otra autorización (otra cuenta/login). El `state` mapea a `client_id` + `platform`
> (no a una cuenta puntual), así que admite ambos caminos sin cambios.

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
   `user.info.profile`, `user.info.stats`, `video.list` y un `state`.
2. Callback con `code` → `POST https://open.tiktokapis.com/v2/oauth/token/`
   (`grant_type=authorization_code`, `client_key`, `client_secret`, `code`, `redirect_uri`).
3. Respuesta: `access_token` (**24 h**), `refresh_token` (**365 d**), `open_id`, `scope`.
4. **Refresh:** `POST /v2/oauth/token/` con `grant_type=refresh_token` → nuevo access token (y
   posible nuevo refresh token → **rotarlo** y guardarlo). Sin consentimiento del usuario.

> App client **auditado** para producción (sandbox fuerza contenido privado). [[05-investigacion-metricas-apis]]

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

- ¿`state` en tabla dedicada (con TTL) o JWT firmado de un solo uso? (impl.)
- Política exacta de cuándo refrescar IG (umbral de días) — afinar en implementación.
- ~~Manejo de un cliente con varias Páginas/cuentas bajo un mismo consentimiento de Meta~~ →
  **RESUELTO:** multi-cuenta por red soportado; paso de selección cuando el consentimiento devuelve
  varias cuentas. Ver "Multi-cuenta por red" arriba.

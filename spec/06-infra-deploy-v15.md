# Spec — Infraestructura y Deploy (v1.5)

> Estado: **PLANIFICADO** (se ejecuta al llegar a v1.5; v1 corre 100% local).
> Decisión cerrada: **Vultr (São Paulo) + Cloudflare**. Ver [[01-definiciones-alto-nivel]].
> Criterio rector: **velocidad para Paraguay** (estático cerca del usuario, dinámico en São Paulo).

## Topología

```
Usuario (Paraguay)
   │
   ├──► Cloudflare (PoP Asunción)
   │       ├─ Pages  → frontend React (estático, cacheado)
   │       └─ R2     → miniaturas (cacheado, sin egress)
   │
   └──► Cloudflare DNS/proxy ──► VPS Vultr (São Paulo)
                                   └─ Docker Compose:
                                        ├─ Caddy (reverse proxy + HTTPS)
                                        ├─ backend Spring Boot (API + job @Scheduled)
                                        └─ Postgres (self-managed + volumen)
```

Regla de oro: **estático (frontend, imágenes) → Cloudflare/Asunción; dinámico (backend, DB) → São Paulo.**

## Costo estimado

| Componente | Plan | Costo |
|---|---|---|
| VPS Vultr São Paulo | Regular 1 vCPU / **2 GB** / 55 GB SSD | ~$10/mes |
| Cloudflare Pages (frontend) | Free | $0 |
| Cloudflare R2 (miniaturas) | Free tier (10 GB; uso ~2-3 GB/año) | $0 |
| Dominio | .com anual | ~$1/mes |
| Backups Vultr (opcional) | +20% del VPS | ~$2/mes |
| **Total** | | **~$11-13/mes** |

Notas: el plan de 1 GB ($5) es muy justo para JVM + Postgres juntos → usar **2 GB**. **No** usar la
DB manejada de Vultr ($15+/mes aparte): Postgres self-managed en el Compose. Free tier de Cloudflare
NO degrada velocidad (mismos PoPs que el pago).

## Desarrollo: túnel cloudflared (puente a HTTPS) — y qué cambia en prod

> **DEV ACTUAL** (no v1.5). Documentado porque al desplegar en Vultr **hay que cambiarlo**, y tiene
> implicancias de seguridad.

**Qué es y por qué existe.** En dev el backend corre en `localhost:8080` y el frontend (Vite) en
`localhost:5173`, en la MacBook. TikTok exige `redirect_uri` **HTTPS** y testear el link compartible
desde el celular necesita una URL pública. Para eso hay un **túnel cloudflared** (tunnel `filgrama`)
que publica:
- `api.fil-grama.com` → `http://localhost:8080` (backend)
- `app.fil-grama.com` → `http://localhost:5173` (frontend Vite)

> **Los hostnames se gestionan en el dashboard de Cloudflare** (Networks → Tunnels → `filgrama` →
> *Published application routes*), **no** en el `~/.cloudflared/config.yml` local: el túnel es
> remotely-managed, así que editar el archivo local **no surte efecto** (gotcha que costó un rato).

> ⚠️ **SEGURIDAD — el túnel NO es "solo tu MacBook".** El connector corre en tu Mac, pero
> `api.fil-grama.com` / `app.fil-grama.com` son **públicos en internet**: cualquiera con la URL llega a
> tu backend/Vite de dev. Exposición real (modesta — data de dev + sandbox + mayormente con auth, pero
> existe): `/auth/login` es brute-forceable, Swagger/OpenAPI está `permitAll`, los `/public/**` del
> connect-link están expuestos (rate-limited), y un perfil dev podría filtrar stack traces.
> **Mitigaciones en dev:** (a) **Cloudflare Access** delante de `app.fil-grama.com` (login por tu
> identidad) para que solo entres vos — **OJO:** NO sobre `/api/v1/oauth/callback/**`, porque ese
> callback lo golpea el server de TikTok, no un humano; (b) mantener TikTok en **sandbox** (solo target
> users autorizan); (c) **apagar el túnel cuando no testeás**; (d) no exponer Swagger ni stack traces.

**El túnel es DEV-ONLY.** En producción **desaparece**: el backend vive en el VPS Vultr (Fases 1-2) y el
frontend en Cloudflare Pages (Fase 4). `api.*` pasa a ser un registro **A** a la IP del VPS (Fase 3), no
un túnel desde tu laptop.

### Matriz de config: dev (túnel) → prod (Vultr)

| Qué | Dev (hoy, túnel) | Prod (Vultr) | Dónde se setea |
|---|---|---|---|
| Backend | `localhost:8080` vía túnel | VPS São Paulo detrás de Caddy | DNS Cloudflare + Caddy |
| Frontend | Vite `localhost:5173` vía túnel | Cloudflare Pages | Cloudflare Pages |
| `api.fil-grama.com` | túnel → localhost:8080 | **A** → IP del VPS (proxy Cloudflare) | DNS Cloudflare |
| `app.fil-grama.com` | túnel → localhost:5173 | Cloudflare Pages | DNS / Pages |
| `OAUTH_REDIRECT_BASE_URI` | `https://api.fil-grama.com` | `https://api.fil-grama.com` (mismo, ahora apunta al VPS) | env backend |
| `app.connect-link-base-url` | `https://app.fil-grama.com/connect` | ídem | env backend |
| `app.connect-done-url` | `https://app.fil-grama.com/connect/done` | ídem | env backend |
| `VITE_API_BASE_URL` (front) | `https://api.fil-grama.com/api/v1` | ídem | `.env` dev / env de Pages |
| `cors.allowed-origins` | `https://app.fil-grama.com` + localhost | **solo** `https://app.fil-grama.com` | env backend |
| TikTok | **sandbox** (`sb…` key, target users) | **producción** (App Review, key prod, redirect prod registrado) | TikTok portal + env |
| Secrets | `docker-compose.override.yml` / `.env` (gitignored) | env del Compose **en el VPS** (nunca en git) | VPS |

> **PKCE/state en memoria.** El `code_verifier` (PKCE) y el nonce del `state` viven **en memoria del
> proceso** que arma la URL → el callback debe volver a la **misma instancia**. Dev (un proceso) y prod
> single-instance: OK. Si algún día hay >1 instancia → store compartido (Redis) o sticky sessions.
> [[09-flujo-oauth]]

> El dominio real es **`fil-grama.com`** (con guion); los ejemplos `filgrama.com` de la Fase 3 valen como
> el real con guion.

## Plan de ejecución v1.5 (fases)

### Fase 1 — Provisionar el VPS
- [ ] Crear instancia **Ubuntu LTS** en región **São Paulo (BR)**, plan 2 GB.
- [ ] Cargar **SSH key**; primer login.
- [ ] Hardening: usuario no-root con sudo, deshabilitar login SSH de root, `ufw` (permitir 22/80/443), `fail2ban`.
- [ ] Actualizar el SO (`apt update && upgrade`); habilitar `unattended-upgrades`.

### Fase 2 — Runtime
- [ ] Instalar **Docker + Docker Compose plugin**.
- [ ] Portar el `docker-compose.yml` de v1 (backend + Postgres) y adaptar para producción.
- [ ] Agregar **Caddy** al Compose como reverse proxy con HTTPS automático (Let's Encrypt).
- [ ] Definir **volúmenes**: datos de Postgres + buffer temporal de miniaturas antes de subir a R2.

### Fase 3 — Dominio, DNS y TLS
- [ ] **Registrar dominio** (ej. `filgrama.com`) en **Cloudflare Registrar** (a precio de costo,
  ~$10-12/año un `.com`; integrado con el DNS). Activar **renovación automática**.
- [ ] Gestionar DNS en Cloudflare. Subdominios:
  - `app.filgrama.com` → frontend en Cloudflare Pages.
  - `api.filgrama.com` → registro **A** a la **IP del VPS Vultr** (proxy Cloudflare on).
- [ ] Verificar HTTPS end-to-end (Caddy emite/renueva cert; o SSL de Cloudflare en el borde).

> **Qué es un dominio:** el nombre legible (`filgrama.com`) que apunta a la IP del servidor.
> Se alquila por año en un registrador. Regla de uso: estático (Pages) y backend (VPS) en
> subdominios distintos; HTTPS automático. Un `.com.py` se registra en el NIC de Paraguay (más caro
> y burocrático) — para v1.5 un `.com` es lo práctico.

### Fase 4 — Frontend y Storage en Cloudflare
- [ ] Deploy del frontend en **Cloudflare Pages** (build desde el repo Git).
- [ ] Crear bucket **R2** para miniaturas; configurar credenciales S3-compatibles.
- [ ] Implementar el `StoragePort` con cliente S3 apuntando a R2 (en v1 era carpeta local). [[02-modelo-de-datos]]
- [ ] Configurar dominio/CDN público de R2 para servir miniaturas.

### Fase 5 — Secrets, backups y deploy
- [ ] Cargar **secrets/env**: clave de cifrado de tokens, credenciales app Meta/TikTok, password Postgres, claves R2.
- [ ] **Backups**: `pg_dump` por cron subiendo el dump a R2 (+ snapshots automáticos de Vultr opcional).
- [ ] **Deploy**: build de imagen → push → `docker compose up -d`. Opcional: automatizar con GitHub Actions.
- [ ] Probar **restore** de un backup (un backup sin restore probado no es backup).

### Fase 6 — Observabilidad
- [ ] Logs centralizados del Compose; rotación de logs.
- [ ] Uptime check externo (ping al healthcheck del backend).
- [ ] Alerta básica si el **job diario** falla o no corre.

## Implicancias / responsabilidades

- Vos sos el **sysadmin**: updates de SO, seguridad, backups, deploy. A cambio: control total + costo
  mínimo + mejor latencia para Paraguay. El worker 24/7 corre sin "dormir".
- **Ventaja de continuidad:** en v1 ya vas a tener el `docker-compose.yml` corriendo local → en v1.5
  es "el mismo Compose, pero en São Paulo" + Caddy + R2. Migración de fricción baja.

## Decisiones abiertas para v1.5 (no bloquean v1)

- ¿CI/CD con GitHub Actions desde el inicio o deploy manual al principio?
- ¿Snapshots de Vultr además de `pg_dump`, o solo dumps a R2?
- Estrategia de migraciones de DB en prod (Flyway/Liquibase) — definir en la capa de implementación.

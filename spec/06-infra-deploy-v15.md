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

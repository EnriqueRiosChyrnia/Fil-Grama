# Reporte — Track G: Reportes (`feat/reports`)

Generación síncrona de reportes exportables (SUMMARY / EXTENDED) en **MARKDOWN** y **PDF** (CU5,
spec/03, spec/08, spec/07). Sin IA en v1 (`narrative_md` nullable, listo para el MCP de v2).

## 0. Estado

- `git rebase main` ejecutado al inicio (la rama estaba al día con `main` — 0/0; no hubo conflictos).
- **Dueño de `V4__reports.sql`** (única migración del track). V1–V3 intactas.
- Toqué **solo** `com.filgrama.reports.**` + `V4`. Cero cambios en archivos ajenos (ver §6).

## 1. Build — `mvn clean package` (Docker arriba)

```
BUILD SUCCESS
Tests run: 154, Failures: 0, Errors: 0, Skipped: 0
```

- **21 tests nuevos** del track (antes 133). V4 aplica limpio sobre V1–V3 (Flyway migra
  V1→V2→V3→V4 al arrancar el contexto de integración contra Postgres real).
- Artefacto: `target/filgrama-backend-0.1.0-SNAPSHOT.jar`.

> Los logs `PNGConverter: Not enough bytes` en la corrida son benignos: openhtmltopdf intenta la
> ruta "lossless" sobre la **miniatura de test de 1×1 px** y cae al embebido normal; el PDF se
> genera igual (los tests verifican la cabecera `%PDF-` y el tamaño). Con miniaturas reales no aparece.

## 2. Tests (con datos de snapshot/posts sembrados)

`ReportFlowIntegrationTest` (Testcontainers + storage local, app completa por MockMvc) siembra
cliente + cuenta IG + 2 Reels + 1 Feed + snapshots de cuenta y post + 1 miniatura cacheada y cubre:

| Criterio (DoD) | Test | Resultado |
|---|---|---|
| SUMMARY/MARKDOWN → `201`, `status=COMPLETED`, archivo en storage | `summaryMarkdownGeneratesAndDownloads` | ✅ fila con `storage_path`, MD descargable |
| EXTENDED/PDF → PDF no vacío con grilla/agrupación | `extendedPdfGeneratesAndDownloads` | ✅ `%PDF-`, > 1 KB |
| `download` con `Content-Type` correcto y refleja período/redes/tipo | ambos + `ReportControllerWebTest` | ✅ `text/markdown` / `application/pdf`, MD contiene período + `INSTAGRAM` + KPIs |
| Rango inválido → `400/422` | `invalidRangeIsRejected` | ✅ 4xx (`from>to` → 400) |
| Reporte de otro cliente → `404` (multi-tenant) | `reportOfAnotherClientIsNotFound` | ✅ `findByIdAndClientId` |
| `narrative_md` ausente → sin sección "Análisis del mes" | `summaryMarkdownGeneratesAndDownloads` + renderers | ✅ `doesNotContain("Análisis del mes")` |
| Sin auth → `401` | `unauthenticatedIsRejected` | ✅ |

Tests de unidad (sin DB/Spring) que respaldan el layout y la orquestación:

- `PdfRendererTest` (4): el XHTML tiene grilla, agrupación por tipo (Reels/Feed), estrella en
  destacadas, métrica sobre la imagen, fecha debajo, miniatura base64 y escape XML; el PDF sale `%PDF-`.
- `MarkdownRendererTest` (3): SUMMARY/EXTENDED, secciones por red/tipo, regla de la narrativa.
- `ReportServiceTest` (4): `COMPLETED` + `storage.put` con key `reports/{clientId}/{id}.{ext}`;
  `FAILED` + re-throw si el render falla; `get` de otro cliente → 404; `download` con headers.
- `ReportControllerWebTest` (5): `201` + forma del recurso, `400` por campo faltante, metadatos,
  `404` problem+json, descarga con `Content-Disposition`.

## 3. Prueba manual (Bearer)

Con la app y Postgres arriba (`docker compose up`) y el admin de dev sembrado
(`admin@filgrama.local` / `Admin123!`):

```bash
# 1) Login → access token
TOKEN=$(curl -s localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@filgrama.local","password":"Admin123!"}' | jq -r .accessToken)

# 2) Generar (cliente {id} con datos capturados)
REPORT=$(curl -s -X POST localhost:8080/api/v1/clients/1/reports \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"reportType":"EXTENDED","format":"PDF","from":"2026-05-01","to":"2026-05-31",
       "platforms":["INSTAGRAM","TIKTOK"],"rankBy":"reach"}')
echo "$REPORT"      # {id, reportType, status:"COMPLETED", format, downloadUrl, createdAt}
ID=$(echo "$REPORT" | jq -r .id)

# 3) Descargar (abre el PDF / MD)
curl -s -L -H "Authorization: Bearer $TOKEN" \
  localhost:8080/api/v1/clients/1/reports/$ID/download -o reporte.pdf
```

El mismo flujo está ejercitado de punta a punta (HTTP → Flyway → Postgres → storage → descarga) por
`ReportFlowIntegrationTest`.

## 4. Archivos creados

**Migración (única del track):**
- `src/main/resources/db/migration/V4__reports.sql`

**`com.filgrama.reports` (paquete dueño):**
- `Report.java` (entidad), `ReportRepository.java`, `ReportType/ReportFormat/ReportStatus.java`
- `ReportQueryRepository.java` — queries de reporte nivel cliente (posts + último valor de métrica)
- `ReportService.java` — orquesta generación síncrona (PENDING→COMPLETED/FAILED), get, download
- `data/ReportData.java` — contrato compartido (mismo que consumirá el MCP en v2)
- `data/ReportDataAssembler.java` — arma `ReportData` reusando D + queries propias
- `data/RankMetricResolver.java` — `rankBy` lógico → metric_key por red/historia
- `render/MarkdownRenderer.java`, `render/PdfRenderer.java` (openhtmltopdf), `render/ThumbnailLoader.java`
  (base64 vía StoragePort de E), `render/ReportFormatting.java`
- `web/ReportController.java`, `web/GenerateReportRequest.java`, `web/ReportResource.java`

**Tests:** `ReportFlowIntegrationTest`, `ReportServiceTest`, `web/ReportControllerWebTest`,
`render/{PdfRendererTest, MarkdownRendererTest, RenderFixtures}`.

## 5. Reúso de D/E y métodos faltantes (coordinación)

**Reusado tal cual (beans públicos, sin editar):**
- **D — `SummaryService.summary(...)`**: KPIs por red, `engagementRate`, `followerGrowth` y
  desglose por red. Los **deltas vs período anterior** los derivo llamando `summary()` dos veces
  (período actual + período anterior del mismo largo) y restando en mi assembler. La métrica del
  catálogo la resuelvo con `MetricCatalogService` (mismo bean que usa D).
- **E — `StoragePort`**: `put()` para guardar el archivo generado (`reports/{clientId}/{reportId}.{ext}`)
  y `get()` para leer las miniaturas cacheadas y embeberlas en base64. Repos compartidos de solo
  lectura: `ClientRepository`, `SocialAccountRepository`, `MediaAssetRepository`.

**No me faltó ningún método para terminar v1** (nada bloqueante). Notas para una posible centralización
futura — **opcionales**, no requeridas:

1. **D no expone una query de posts a nivel cliente** (sus servicios son per-cuenta:
   `AccountPostsService` pagina por `accountId`). El reporte necesita los posts del **cliente**
   cruzando todas sus cuentas y **agrupados por red/tipo** con top/bottom. Como el track lo permite,
   lo resolví con `ReportQueryRepository` en **mi** paquete. Si en el futuro se quiere centralizar,
   D podría exponer `clientPosts(clientId, from, to, platforms, rankBy)`.
2. **Delta vs período anterior**: hoy lo calculo con dos llamadas a `summary()`. Si se repite en otros
   consumidores, D podría ofrecer un `summaryWithDelta(clientId, from, to)` que ya traiga el delta.
3. **E — bytes de miniatura**: `MediaService.getThumbnailUrl()` devuelve una URL (presigned/remoto),
   pero para el **PDF necesito los bytes** (data-URI base64), así que uso `StoragePort.get()`
   directamente (bean público, permitido). Un `MediaService.getThumbnailBytes(asset)` sería un
   conveniente futuro, no imprescindible.

## 6. Confirmaciones

- **No toqué** `pom.xml`, `application.yml`, `SecurityConfig`, `com.filgrama.domain.**`,
  `com.filgrama.repository.**`, V1–V3, ni paquetes de otros tracks. `git status` solo muestra
  archivos nuevos bajo `com/filgrama/reports/` y `V4__reports.sql` (cero `M`).
- **V4 es mi única migración.**
- **Cero IA en v1**: `narrative_md` (y `narrative_source/model/generated_at`) quedan nullable y nunca
  se setean; los renderers muestran "Análisis del mes" **solo si** `narrative_md` existe.
- **Multi-tenant**: toda lectura/escritura filtra por `client_id`; el lookup de reporte es
  `findByIdAndClientId` → un reporte de otro cliente da 404.
- **Errores**: uso el handler compartido `com.filgrama.error` + `ApiException` (sin advice propio).
  Rango inválido → 400; cliente/reporte inexistente → 404; `rankBy` desconocido → 422.

## 7. Nota de deploy (PDF en prod)

openhtmltopdf resuelve `sans-serif` contra las **fuentes del sistema** (en dev/host las hay). En una
imagen Linux mínima sin fuentes, el texto podría no embeberse. Sin cambiar código: dejar un TTF en el
classpath en **`/fonts/report-font.ttf`** y `PdfRenderer` lo registra automáticamente como
`"Filgrama Sans"` (o instalar un paquete de fuentes en la imagen). A coordinar con infra cuando se
arme el contenedor de prod.

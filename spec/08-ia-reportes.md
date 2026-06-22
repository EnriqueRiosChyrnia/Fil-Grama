# Spec — Capa 8: Análisis con IA en Reportes (planificación)

> Estado: **PLANIFICADO** — feature de **v2** ("análisis con IA, recomendaciones, benchmarking").
> Se diseña la arquitectura ahora para no condicionarla mal en v1.
> Depende de: [[04-criterios-aceptacion]] (CU5 reporte), [[03-contratos-api]], [[07-principios-ux]].

## Principio rector: IA como capa opcional y desacoplada

La app es **100% funcional sin Claude**. El reporte siempre se arma con datos + gráficos. El
**análisis con IA es un bloque adicional** ("Análisis del mes / Reflexión") que aparece solo si fue
generado. Si no hay IA, el reporte sale igual, sin huecos. **Nunca** la app depende de Claude para operar.

## Aclaración crítica: Max vs API (estado jun-2026)

- La suscripción **Claude Max no se usa como API key** en el backend. Alimenta el Claude
  **interactivo** (claude.ai, Claude Code, desktop/Cowork).
- **Backend con API key (Console):** facturación por token, aparte del Max. Barato (~centavos/reporte).
- **Uso interactivo bajo Max:** pedirle a Claude (desde el desktop, conectado por MCP a la app) que
  genere el reporte → corre bajo Max.
- Desde el **15-jun-2026** cada plan trae crédito mensual para uso programático (Max 20x ≈ $200/mes a
  precio API) para automatizaciones (Agent SDK), separado de los límites de la suscripción.

→ **Para exprimir el Max:** exponer la app como **MCP** que Claude consume de forma interactiva.

## Dos modos de generación (mismo contrato de datos)

### Modo A — MCP (recomendado; aprovecha Max). Construir primero.
Servidor MCP con tools **de solo lectura** sobre el backend:
- `list_clients()`
- `get_client_report_data(client_id, period)` → KPIs + deltas vs período anterior + top posts + desglose por red (JSON estructurado).
- `get_metric_series(account_id, metric, from, to)`
- `compare_periods(client_id, period_a, period_b)`
- `get_posting_performance(client_id, by)` → rendimiento (alcance/engagement promedio) agrupado por
  **hora** o **día de la semana** (en timezone del cliente), sobre toda la historia → "mejores horas/días para publicar".
- *(opcional, escritura)* `save_report_narrative(client_id, period, markdown, model)` → guarda el texto en la app.

Flujo: desde Claude desktop/Cowork (bajo Max) → "generá el reporte de X en tono ameno" → Claude
trae datos por MCP → escribe la reflexión → (opcional) la guarda vía tool. **Costo:** cubierto por Max.

> Reutilización: este MCP es el mismo que habilita "consultar métricas de un cliente desde Claude"
> (idea previa del usuario). Encaja con la decisión de **JWT** para clientes programáticos. [[03-contratos-api]]

### Modo B — Botón in-app vía API (automático; para empleados sin Claude). Segunda etapa.
Botón "Generar análisis con IA" en la pantalla de reporte → backend llama a la **API de Anthropic**
(api key; Opus 4.8 / Fable cuando esté disponible) con el `ReportData` → devuelve el texto → se
guarda en el reporte. Self-service, sin humano. Costo: por token (~centavos) o crédito programático.

## Contrato de datos compartido (`ReportData`)

Ambos modos consumen el mismo JSON que el backend ya sabe armar (KPIs CORE, deltas, series, top
posts, por red). La IA **solo redacta**; los números los provee la app, nunca el modelo. Esto evita
alucinaciones de cifras.

## Modelo de datos (impacto mínimo)

En el recurso de reporte (ver `reports` en [[02-modelo-de-datos]]), agregar narrativa opcional:
- `narrative_md` (text, NULL) — el análisis en Markdown.
- `narrative_source` (`MCP` | `API` | `MANUAL`, NULL)
- `narrative_model` (text, NULL — ej. `claude-opus-4-8`)
- `narrative_generated_at` (timestamptz, NULL)

El render del reporte muestra la sección "Análisis del mes" solo si `narrative_md` existe.

## Lineamientos del texto (prompt)

- **Idioma:** español. **Tono:** cálido y profesional, para el **cliente final**, no técnico.
- **Sin saturar:** 150-250 palabras; destacar 3-4 cosas que importan, no enumerar todo.
- **Sin jerga** (o explicada): nada de `ig_reach`; "alcance", "engagement" con contexto.
- **Comparar** contra el período anterior y explicar el probable porqué **con cautela** (no afirmar causas inventadas).
- **Cerrar con una recomendación accionable.**
- **Guardrails:** usar solo los números provistos; no inventar cifras ni métricas; si falta dato, decirlo.
- **Transparencia:** decisión de agencia si se etiqueta como "generado con asistencia de IA".

## Privacidad

Se envían **métricas agregadas** (sin PII). Aun así, documentar que el cliente acepta el uso de IA
sobre sus datos de rendimiento si se etiqueta o se le entrega.

## Decisión del usuario (jun-2026)

- **Solo Modo A (MCP).** El Modo B (botón in-app vía API) queda descartado por ahora; se reconsidera
  muy a futuro si hace falta automatizar para empleados sin Claude.
- **Costo del MCP interactivo: $0 extra.** El servidor MCP corre en el propio VPS (Vultr) → sin
  cargo de Anthropic. Consumirlo desde Claude desktop/Cowork de forma **interactiva** está cubierto
  por la suscripción **Max** (solo aplican los límites normales del plan, no cobro por token/llamada).
- Solo habría costo separado si se **automatizara** (Agent SDK / `claude -p` / scripts), que saldría
  del crédito programático del plan ($200/mes en Max 20x). No es el caso.

## Roadmap

- **v1:** dejar listo el `ReportData` (el backend ya lo produce para el reporte normal) y el campo
  `narrative_md` opcional en el modelo. Nada de IA todavía.
- **v2:** construir el **MCP** (Modo A), consumido interactivamente bajo Max. Sumar comparaciones y
  benchmarking competitivo sobre la misma base. (Modo B / API → solo si se decide muy a futuro.)

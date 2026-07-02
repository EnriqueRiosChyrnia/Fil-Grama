package com.filgrama.mcp;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.stereotype.Component;

import com.filgrama.error.ApiException;
import com.filgrama.mcp.dto.ClientView;
import com.filgrama.mcp.dto.CompareView;
import com.filgrama.mcp.dto.DemographicsView;
import com.filgrama.mcp.dto.NarrativeSaved;
import com.filgrama.mcp.dto.PostingPerformanceView;
import com.filgrama.mcp.dto.ReportView;
import com.filgrama.metrics.dto.AccountReportResponse;

/**
 * Las 7 tools MCP de Fil-Grama (spec/08 §Decisiones T5), expuestas por el starter de Spring AI vía
 * anotaciones {@link McpTool} sobre este {@code @Component} (el scanner las registra solo). Cada método
 * es un adaptador fino: resuelve la identidad autenticada del token con {@link McpIdentity#from} y
 * delega en {@link McpToolService}, donde vive el scope y la reutilización de los servicios de reporte.
 *
 * <p>Las descripciones están en español porque Claude las usa para decidir cuándo llamar cada tool.
 * Solo {@code save_report_narrative} escribe; todo lo demás es de lectura. El parámetro
 * {@link McpSyncRequestContext} lo inyecta el runtime (no forma parte del schema de la tool).
 */
@Component
public class FilgramaMcpTools {

    private final McpToolService tools;

    public FilgramaMcpTools(McpToolService tools) {
        this.tools = tools;
    }

    @McpTool(name = "list_clients", description = """
            Lista los clientes de la agencia a los que el usuario del token tiene acceso (un empleado
            ve solo sus clientes asignados; un admin ve todos). Devuelve id, nombre, timezone, plan y
            estado. Úsala primero para ubicar el client_id de un cliente por su nombre.""")
    public List<ClientView> listClients(McpSyncRequestContext context) {
        return tools.listClients(McpIdentity.from(context));
    }

    @McpTool(name = "get_client_report_data", description = """
            Datos del reporte de un cliente para un período: KPIs por red con su variación (delta) vs el
            período anterior, engagement, crecimiento de seguidores, evolución del alcance, y los bloques
            del reporte mensual (demografía de seguidores, split de visualizaciones seguidor/no-seguidor,
            interacciones por acción, visualizaciones por tipo de contenido, actividad de perfil), más
            los posts destacados. Es la base para redactar el "Análisis del mes". 'period' es un mes
            'YYYY-MM' o un rango 'YYYY-MM-DD..YYYY-MM-DD'. Los números los provee la app: no inventes
            cifras.""")
    public ReportView getClientReportData(
            McpSyncRequestContext context,
            @McpToolParam(description = "id del cliente (de list_clients)", required = true) long client_id,
            @McpToolParam(description = "mes 'YYYY-MM' o rango 'YYYY-MM-DD..YYYY-MM-DD'", required = true) String period) {
        return tools.getClientReportData(McpIdentity.from(context), client_id, period);
    }

    @McpTool(name = "get_metric_series", description = """
            Serie temporal de una métrica de una cuenta (para graficar o ver tendencia). 'metric' es una
            metric_key del catálogo con prefijo de red (ej. 'ig_reach', 'fb_page_views', 'tt_view_count').
            'from'/'to' son fechas ISO 'YYYY-MM-DD'; si se omiten, usa los últimos 90 días. Devuelve un
            punto por día. Rango sin datos → serie vacía (no es error).""")
    public AccountReportResponse getMetricSeries(
            McpSyncRequestContext context,
            @McpToolParam(description = "id de la cuenta social", required = true) long account_id,
            @McpToolParam(description = "metric_key del catálogo (ej. 'ig_reach')", required = true) String metric,
            @McpToolParam(description = "fecha desde 'YYYY-MM-DD' (opcional)", required = false) String from,
            @McpToolParam(description = "fecha hasta 'YYYY-MM-DD' (opcional)", required = false) String to) {
        return tools.getMetricSeries(McpIdentity.from(context), account_id, metric,
                parseDate("from", from), parseDate("to", to));
    }

    @McpTool(name = "get_audience_demographics", description = """
            Demografía de la audiencia (seguidores) de un cliente en el período: ciudades, países,
            rangos de edad y géneros, por red. Solo Instagram/Facebook exponen demografía; puede venir
            vacía si la cuenta no la tiene capturada. 'period' es un mes 'YYYY-MM' o un rango
            'YYYY-MM-DD..YYYY-MM-DD'.""")
    public DemographicsView getAudienceDemographics(
            McpSyncRequestContext context,
            @McpToolParam(description = "id del cliente", required = true) long client_id,
            @McpToolParam(description = "mes 'YYYY-MM' o rango 'YYYY-MM-DD..YYYY-MM-DD'", required = true) String period) {
        return tools.getAudienceDemographics(McpIdentity.from(context), client_id, period);
    }

    @McpTool(name = "compare_periods", description = """
            Compara dos períodos de un mismo cliente: por red, el valor de cada KPI en el período A y en
            el B con su variación (delta = B − A y deltaPct). Útil para "este mes vs el anterior". Cada
            'period' es un mes 'YYYY-MM' o un rango 'YYYY-MM-DD..YYYY-MM-DD'. Son solo números: explicá
            causas con cautela, sin inventarlas.""")
    public CompareView comparePeriods(
            McpSyncRequestContext context,
            @McpToolParam(description = "id del cliente", required = true) long client_id,
            @McpToolParam(description = "período A (base), 'YYYY-MM' o rango", required = true) String period_a,
            @McpToolParam(description = "período B (a comparar), 'YYYY-MM' o rango", required = true) String period_b) {
        return tools.comparePeriods(McpIdentity.from(context), client_id, period_a, period_b);
    }

    @McpTool(name = "get_posting_performance", description = """
            Rendimiento promedio de las publicaciones de un cliente agrupado por hora del día o por día
            de la semana (en la timezone del cliente), sobre toda la historia → "mejores horas/días para
            publicar". 'by' = 'hour' o 'weekday'. Devuelve, por bucket, la cantidad de posts y el alcance
            y el engagement promedio.""")
    public PostingPerformanceView getPostingPerformance(
            McpSyncRequestContext context,
            @McpToolParam(description = "id del cliente", required = true) long client_id,
            @McpToolParam(description = "'hour' (hora del día) o 'weekday' (día de la semana)", required = true) String by) {
        return tools.getPostingPerformance(McpIdentity.from(context), client_id, by);
    }

    @McpTool(name = "save_report_narrative", description = """
            ÚNICA tool de escritura: guarda el "Análisis del mes" (texto en Markdown) del cliente para el
            período, para que aparezca en la pantalla del reporte y en el PDF. Se asocia al reporte del
            período (lo crea si no existe). 'period' es un mes 'YYYY-MM' o un rango
            'YYYY-MM-DD..YYYY-MM-DD'. 'model' es opcional (ej. 'claude-opus-4-8'). Escribí en español,
            tono cálido y profesional para el cliente final, usando solo los números provistos.""")
    public NarrativeSaved saveReportNarrative(
            McpSyncRequestContext context,
            @McpToolParam(description = "id del cliente", required = true) long client_id,
            @McpToolParam(description = "mes 'YYYY-MM' o rango 'YYYY-MM-DD..YYYY-MM-DD'", required = true) String period,
            @McpToolParam(description = "narrativa en Markdown (español)", required = true) String markdown,
            @McpToolParam(description = "modelo que la redactó (opcional)", required = false) String model) {
        return tools.saveReportNarrative(McpIdentity.from(context), client_id, period, markdown, model);
    }

    private static LocalDate parseDate(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw ApiException.badRequest("'%s' inválida: '%s' (formato 'YYYY-MM-DD')".formatted(field, value));
        }
    }
}

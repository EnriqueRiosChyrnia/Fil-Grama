package com.filgrama.mcp;

import java.util.Map;

import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.function.ServerRequest;

import com.filgrama.auth.JwtService;
import com.filgrama.auth.JwtService.ParsedToken;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.json.JsonMapper;

/**
 * Puente de identidad entre la cadena JWT de Spring Security y las tools MCP (spec/08 §Decisiones T5,
 * spec/11 §aislamiento). El endpoint {@code /mcp} ya exige un JWT válido (no está en la whitelist de
 * {@link com.filgrama.config.SecurityConfig}); acá reemplazamos el {@code WebMvcStreamableServerTransportProvider}
 * autoconfigurado por Spring AI para inyectarle un {@link McpTransportContextExtractor} que lee el
 * {@code Authorization: Bearer} de la request, lo valida con el {@link JwtService} existente y deja
 * {@code userId} + {@code role} en el {@link McpTransportContext}. Cada tool los recupera con
 * {@link McpIdentity#from} y filtra su scope en el backend — <b>nunca por el prompt</b>.
 *
 * <p>Por qué el extractor y no {@code SecurityContextHolder} dentro de la tool: el extractor recibe el
 * {@link ServerRequest} y resuelve la identidad de forma independiente del hilo donde el SDK ejecute la
 * tool. El bean autoconfigurado es {@code @ConditionalOnMissingBean}, así que definir el nuestro lo
 * sustituye sin desactivar el resto de la autoconfig (router function, scanner de {@code @McpTool}).
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = "protocol", havingValue = "STREAMABLE")
@Slf4j
public class McpSecurityContextConfig {

    private static final String BEARER_PREFIX = "Bearer ";

    @Bean
    WebMvcStreamableServerTransportProvider webMvcStreamableServerTransportProvider(
            JsonMapper jsonMapper, McpServerStreamableHttpProperties properties, JwtService jwtService) {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .mcpEndpoint(properties.getMcpEndpoint())
                .keepAliveInterval(properties.getKeepAliveInterval())
                .disallowDelete(properties.isDisallowDelete())
                .contextExtractor(jwtContextExtractor(jwtService))
                .build();
    }

    /**
     * Extrae la identidad del Bearer de la request. La cadena de seguridad ya rechazó (401) los tokens
     * ausentes o inválidos antes de llegar acá, pero somos defensivos: ante cualquier fallo de parseo
     * devolvemos el contexto vacío y la tool responderá "no autenticado".
     */
    private McpTransportContextExtractor<ServerRequest> jwtContextExtractor(JwtService jwtService) {
        return request -> {
            String header = request.headers().firstHeader(HttpHeaders.AUTHORIZATION);
            if (header == null || !header.startsWith(BEARER_PREFIX)) {
                return McpTransportContext.EMPTY;
            }
            try {
                ParsedToken token = jwtService.parse(header.substring(BEARER_PREFIX.length()).trim());
                return McpTransportContext.create(Map.of(
                        McpTransportKeys.USER_ID, token.userId(),
                        McpTransportKeys.ROLE, token.role()));
            } catch (RuntimeException e) {
                log.debug("MCP: token Bearer no parseable en el extractor de contexto: {}", e.toString());
                return McpTransportContext.EMPTY;
            }
        };
    }
}

package com.filgrama.connectlink.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.filgrama.connectlink.ConnectLinkService;
import com.filgrama.connectlink.dto.ConnectLinkResponse;
import com.filgrama.connectlink.dto.ConnectLinkSummary;
import com.filgrama.connectlink.dto.CreateConnectLinkRequest;
import com.filgrama.domain.enums.Platform;
import com.filgrama.error.ApiException;
import com.filgrama.oauth.Platforms;

/**
 * Connect-links para la <b>agencia</b> (autenticada): crear, listar (vigentes) y revocar.
 * El público vive en {@link PublicConnectLinkController}. Base {@code /api/v1}.
 */
@RestController
@RequestMapping("/api/v1")
public class ConnectLinkController {

    private final ConnectLinkService service;

    public ConnectLinkController(ConnectLinkService service) {
        this.service = service;
    }

    /** Crea un link temporal para el cliente. El token raw va sólo en esta respuesta. */
    @PostMapping("/clients/{clientId}/connect-links")
    @ResponseStatus(HttpStatus.CREATED)
    public ConnectLinkResponse create(@PathVariable Long clientId,
                                      @RequestBody(required = false) CreateConnectLinkRequest body) {
        CreateConnectLinkRequest req = body != null ? body : new CreateConnectLinkRequest(null, null);
        Platform platform = parsePlatform(req.platform());
        return service.create(clientId, platform, req.accountId(), currentUserId());
    }

    /** Lista los links vigentes del cliente (sin token raw). */
    @GetMapping("/clients/{clientId}/connect-links")
    public List<ConnectLinkSummary> list(@PathVariable Long clientId) {
        return service.listVigentes(clientId);
    }

    /** Revoca un link. */
    @DeleteMapping("/connect-links/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable Long id) {
        service.revoke(id);
    }

    private static Platform parsePlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            return null;
        }
        return Platforms.fromPath(platform)
                .orElseThrow(() -> ApiException.badRequest("Plataforma inválida: " + platform));
    }

    /** userId del JWT (claim {@code sub}) que pone el filtro de Auth; pasa a {@code created_by}. */
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        try {
            return Long.valueOf(auth.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

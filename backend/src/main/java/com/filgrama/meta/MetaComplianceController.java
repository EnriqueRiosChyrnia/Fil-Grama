package com.filgrama.meta;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.filgrama.meta.dto.DataDeletionResponse;

/**
 * Callbacks de compliance que invoca Meta durante App Review y en producción (sin auth — Security los
 * deja en {@code permitAll}, igual que el callback OAuth). La autenticidad la da el {@code signed_request}
 * firmado con el {@code app_secret}; firma/payload inválido ⇒ {@code 400}.
 *
 * <p><b>Configurar tras el deploy en el App Dashboard de Meta</b> (Facebook Login → Settings):
 * <ul>
 *   <li><b>Deauthorize callback URL</b> = {@code <api>/api/v1/meta/deauthorize}</li>
 *   <li><b>Data Deletion request URL</b> = {@code <api>/api/v1/meta/data-deletion}</li>
 * </ul>
 * donde {@code <api>} es la base pública del backend (la misma de {@code oauth.redirect-base-uri}).
 */
@RestController
@RequestMapping("/api/v1/meta")
public class MetaComplianceController {

    private final MetaComplianceService service;

    public MetaComplianceController(MetaComplianceService service) {
        this.service = service;
    }

    /** Deauthorize callback: marca las cuentas Meta del usuario como {@code ERROR}. Responde {@code 200}. */
    @PostMapping("/deauthorize")
    @ResponseStatus(HttpStatus.OK)
    public void deauthorize(@RequestParam(name = "signed_request", required = false) String signedRequest) {
        service.deauthorize(signedRequest);
    }

    /** Data Deletion request: borra los datos del usuario y devuelve {@code {url, confirmation_code}}. */
    @PostMapping("/data-deletion")
    public DataDeletionResponse dataDeletion(
            @RequestParam(name = "signed_request", required = false) String signedRequest) {
        return service.requestDataDeletion(signedRequest);
    }
}

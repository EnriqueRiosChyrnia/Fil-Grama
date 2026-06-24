package com.filgrama.account.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.filgrama.account.dto.AccountResponse;
import com.filgrama.account.dto.ConnectResponse;
import com.filgrama.account.service.AccountService;

/**
 * Endpoints de cuentas sociales y onboarding OAuth (base {@code /api/v1}).
 * El callback OAuth vive en {@code com.filgrama.oauth.web.OAuthCallbackController}
 * (responde redirect, no JSON).
 *
 * <p>Seguridad: permisiva temporal (la endurece el track Auth al integrarse). El
 * {@code refresh-token} es {@code [ADMIN]} en el contrato; se aplicará cuando Auth
 * habilite method-security.
 */
@RestController
@RequestMapping("/api/v1")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping("/clients/{clientId}/accounts")
    public List<AccountResponse> listByClient(@PathVariable Long clientId) {
        return service.listByClient(clientId);
    }

    @GetMapping("/accounts/{id}")
    public AccountResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    /**
     * Inicia el OAuth de una red. {@code accountId} opcional = reconexión de una cuenta conocida:
     * el callback exigirá que la red autorice esa misma cuenta (TAREA B).
     */
    @PostMapping("/clients/{clientId}/accounts/connect/{platform}")
    public ConnectResponse connect(@PathVariable Long clientId, @PathVariable String platform,
                                   @RequestParam(required = false) Long accountId) {
        return service.connect(clientId, platform, currentUserId(), accountId);
    }

    @PostMapping("/accounts/{id}/disconnect")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@PathVariable Long id) {
        service.disconnect(id);
    }

    /** [ADMIN] — fuerza el refresh del token de la cuenta. */
    @PostMapping("/accounts/{id}/refresh-token")
    public AccountResponse refreshToken(@PathVariable Long id) {
        return service.refreshToken(id);
    }

    /** userId del JWT (claim {@code sub} = id de usuario) que pone el filtro de Auth. */
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        try {
            return Long.valueOf(auth.getName());
        } catch (NumberFormatException e) {
            return null; // anónimo / principal no numérico (pre-integración con Auth)
        }
    }
}

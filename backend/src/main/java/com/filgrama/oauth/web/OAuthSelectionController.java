package com.filgrama.oauth.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.filgrama.account.dto.AccountResponse;
import com.filgrama.account.dto.SelectionRequest;
import com.filgrama.account.dto.SelectionResponse;
import com.filgrama.account.service.AccountService;

/**
 * Paso de selección multi-Página/cuenta (spec/09 §Multi-cuenta por red). Públicos (Security los deja en
 * {@code permitAll}): la credencial es el {@code selectionToken} de alta entropía, no la sesión. El
 * {@code GET} lista los candidatos (sin tokens); el {@code POST} crea las cuentas elegidas y consume el
 * token (un solo uso). Inválido/expirado → {@code 404}.
 */
@RestController
@RequestMapping("/api/v1/oauth/select")
public class OAuthSelectionController {

    private final AccountService accountService;

    public OAuthSelectionController(AccountService accountService) {
        this.accountService = accountService;
    }

    /** Lista los candidatos del consentimiento (nunca incluye tokens). */
    @GetMapping("/{selectionToken}")
    public SelectionResponse list(@PathVariable String selectionToken) {
        return accountService.getSelection(selectionToken);
    }

    /** Da de alta las cuentas elegidas y consume el token. Devuelve las cuentas creadas. */
    @PostMapping("/{selectionToken}")
    public List<AccountResponse> apply(@PathVariable String selectionToken,
                                       @RequestBody SelectionRequest body) {
        return accountService.applySelection(selectionToken, body.externalAccountIds());
    }
}

package com.filgrama.oauth.web;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.filgrama.account.service.AccountService;

/**
 * Callback OAuth que invoca la red social (sin auth — Auth lo deja en {@code permitAll}).
 * Canja el {@code code} server-side y <b>redirige al front</b> ({@code 302}) con
 * {@code ?accountId=} o {@code ?error=}. No usa problem+json porque lo consume el navegador.
 */
@RestController
@RequestMapping("/api/v1/oauth")
public class OAuthCallbackController {

    private final AccountService accountService;

    public OAuthCallbackController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/callback/{platform}")
    public ResponseEntity<Void> callback(@PathVariable String platform,
                                         @RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state) {
        String target = accountService.completeCallback(platform, code, state);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(target)).build();
    }
}

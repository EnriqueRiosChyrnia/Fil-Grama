package com.filgrama.oauth;

import java.util.List;

import org.springframework.stereotype.Component;

import com.filgrama.domain.enums.Platform;

/**
 * Resuelve el {@link OAuthProvider} de cada red. Spring inyecta los proveedores
 * ordenados por {@code @Order}; el primero que {@link OAuthProvider#supports}
 * la red gana. {@code MockOAuthProvider} (perfil local/test) tiene precedencia
 * máxima, así que en dev intercepta todas las redes sin tocar las impl. reales.
 */
@Component
public class OAuthProviderRegistry {

    private final List<OAuthProvider> providers;

    public OAuthProviderRegistry(List<OAuthProvider> providers) {
        this.providers = providers;
    }

    public OAuthProvider forPlatform(Platform platform) {
        return providers.stream()
                .filter(p -> p.supports(platform))
                .findFirst()
                .orElseThrow(() -> new OAuthException("No hay OAuthProvider para " + platform));
    }
}

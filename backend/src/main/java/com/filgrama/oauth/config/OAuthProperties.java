package com.filgrama.oauth.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Config del flujo OAuth (prefijo {@code oauth.*}). Los secrets de apps los completa
 * la central; acá hay defaults seguros para que la app levante con el mock en dev.
 * Scopes por red según spec/09.
 */
@Component
@ConfigurationProperties(prefix = "oauth")
@Getter
@Setter
public class OAuthProperties {

    /** URL del front a la que redirige el callback (con {@code ?accountId=} o {@code ?error=}). */
    private String frontRedirectUrl = "http://localhost:5173/oauth/result";

    /** Base pública del backend para armar el {@code redirect_uri} del callback. */
    private String redirectBaseUri = "http://localhost:8080";

    private Meta meta = new Meta();
    private TikTok tiktok = new TikTok();

    @Getter
    @Setter
    public static class Meta {
        private String appId = "";
        private String appSecret = "";
        private String authorizeUrl = "https://www.facebook.com/v21.0/dialog/oauth";
        private String graphUrl = "https://graph.facebook.com/v21.0";
        /** Facebook Login for Business (cubre FB Pages + IG profesional vía Page). spec/09 §Meta. */
        private List<String> scopes = List.of(
                "pages_show_list", "pages_read_engagement", "read_insights",
                "instagram_basic", "instagram_manage_insights", "business_management");
    }

    @Getter
    @Setter
    public static class TikTok {
        private String clientKey = "";
        private String clientSecret = "";
        private String authorizeUrl = "https://www.tiktok.com/v2/auth/authorize/";
        private String tokenUrl = "https://open.tiktokapis.com/v2/oauth/token/";
        /** user-info para resolver display_name/username/avatar reales (scope {@code user.info.profile}). */
        private String userInfoUrl = "https://open.tiktokapis.com/v2/user/info/";
        /** spec/09 §TikTok. */
        private List<String> scopes = List.of(
                "user.info.basic", "user.info.profile", "user.info.stats", "video.list");
    }
}
